package neon.core

import neon.common.{AllocationStrategy, InventoryStatus, LotAttributes, SkuId, StockPositionId}
import neon.stockposition.StockPosition

import java.time.LocalDate

/** A request to allocate stock for a single SKU.
  *
  * @param skuId
  *   the SKU to allocate
  * @param quantity
  *   the quantity needed
  */
case class AllocationRequest(skuId: SkuId, quantity: Int)

/** The result of allocating stock for a single request.
  *
  * @param request
  *   the original allocation request
  * @param allocations
  *   the stock positions and quantities allocated
  * @param shortQuantity
  *   the unfulfilled quantity (zero if fully allocated)
  */
case class AllocationResult(
    request: AllocationRequest,
    allocations: List[StockAllocation],
    shortQuantity: Int
)

/** A single allocation from a specific stock position.
  *
  * @param stockPositionId
  *   the stock position to draw from
  * @param quantity
  *   the quantity allocated from this position
  * @param lotAttributes
  *   the lot attributes for traceability
  */
case class StockAllocation(
    stockPositionId: StockPositionId,
    quantity: Int,
    lotAttributes: LotAttributes
)

/** Stateless policy that selects stock positions to fulfill allocation requests. Supports FEFO,
  * FIFO, and NearestLocation strategies per SAP EWM composable sort key pattern. Greedy first-fit
  * allocation with partial allocation support.
  */
object StockAllocationPolicy:

  /** Allocates stock to requests using the given strategy.
    *
    * @param requests
    *   allocation requests (one per SKU/quantity pair)
    * @param availableStock
    *   stock positions grouped by SKU
    * @param strategy
    *   allocation sort strategy (FEFO, FIFO, NearestLocation)
    * @param referenceDate
    *   date for shelf life computation
    * @param minimumShelfLifeDays
    *   minimum remaining shelf life required (0 = no filter)
    * @return
    *   allocation results or error
    */
  def apply(
      requests: List[AllocationRequest],
      availableStock: Map[SkuId, List[StockPosition]],
      strategy: AllocationStrategy,
      referenceDate: LocalDate,
      minimumShelfLifeDays: Int = 0
  ): Either[StockAllocationError, List[AllocationResult]] =
    val resultsBuilder = List.newBuilder[AllocationResult]
    val iterator = requests.iterator
    while iterator.hasNext do
      allocateOne(
        iterator.next(),
        availableStock,
        strategy,
        referenceDate,
        minimumShelfLifeDays
      ) match
        case Left(error)   => return Left(error)
        case Right(result) => resultsBuilder += result
    Right(resultsBuilder.result())

  private def allocateOne(
      request: AllocationRequest,
      availableStock: Map[SkuId, List[StockPosition]],
      strategy: AllocationStrategy,
      referenceDate: LocalDate,
      minimumShelfLifeDays: Int
  ): Either[StockAllocationError, AllocationResult] =
    val positions = availableStock.getOrElse(request.skuId, Nil)

    val eligible = positions
      .filter(_.status == InventoryStatus.Available)
      .filter(_.availableQuantity > 0)

    if eligible.isEmpty then
      return Left(StockAllocationError.InsufficientStock(request.skuId, request.quantity, 0))

    val withShelfLife =
      if minimumShelfLifeDays > 0 then
        eligible.filter(
          _.lotAttributes.remainingShelfLifeDays(referenceDate) >= minimumShelfLifeDays
        )
      else eligible

    if withShelfLife.isEmpty then
      val maxShelfLife = eligible.map(_.lotAttributes.remainingShelfLifeDays(referenceDate)).max
      return Left(
        StockAllocationError
          .InsufficientShelfLife(request.skuId, minimumShelfLifeDays, maxShelfLife)
      )

    val sorted = sortByStrategy(withShelfLife, strategy, referenceDate)
    val (allocations, remaining) = greedyAllocate(sorted, request.quantity)

    Right(AllocationResult(request, allocations, remaining))

  private def sortByStrategy(
      positions: List[StockPosition],
      strategy: AllocationStrategy,
      referenceDate: LocalDate
  ): List[StockPosition] =
    strategy match
      case AllocationStrategy.Fefo =>
        positions.sortBy(sp =>
          (
            sp.lotAttributes.expirationDate.getOrElse(LocalDate.MAX),
            sp.lotAttributes.productionDate.getOrElse(LocalDate.MAX),
            sp.availableQuantity
          )
        )
      case AllocationStrategy.Fifo =>
        positions.sortBy(sp =>
          (
            sp.lotAttributes.productionDate.getOrElse(LocalDate.MAX),
            sp.availableQuantity
          )
        )
      case AllocationStrategy.NearestLocation =>
        positions // location-aware sorting deferred to future implementation

  private def greedyAllocate(
      sorted: List[StockPosition],
      needed: Int
  ): (List[StockAllocation], Int) =
    var remaining = needed
    val allocations = scala.collection.mutable.ListBuffer[StockAllocation]()
    for sp <- sorted if remaining > 0 do
      val qty = math.min(remaining, sp.availableQuantity)
      allocations += StockAllocation(sp.id, qty, sp.lotAttributes)
      remaining -= qty
    (allocations.toList, remaining)
