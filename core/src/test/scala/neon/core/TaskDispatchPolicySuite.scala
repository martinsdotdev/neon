package neon.core

import neon.common.{
  ConsolidationGroupId,
  LocationId,
  OrderId,
  PackagingLevel,
  Priority,
  SkuId,
  TaskId,
  UserId,
  WaveId
}
import neon.consolidationgroup.ConsolidationGroup
import neon.order.{Order, OrderLine}
import neon.task.{Task, TaskType}
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant

class TaskDispatchPolicySuite extends AnyFunSpec:
  val skuId = SkuId()
  val userId = UserId()
  val sourceLocationId = LocationId()
  val destinationLocationId = LocationId()
  val at = Instant.now()

  def allocated(
      orderId: OrderId,
      waveId: Option[WaveId] = None,
      packagingLevel: PackagingLevel = PackagingLevel.Each
  ): Task.Allocated =
    Task.Allocated(
      TaskId(),
      TaskType.Pick,
      skuId,
      packagingLevel,
      1,
      orderId,
      waveId,
      None,
      None,
      None,
      sourceLocationId,
      destinationLocationId
    )

  def completedTask(orderId: OrderId, waveId: Option[WaveId] = None): Task.Completed =
    Task.Completed(
      TaskId(),
      TaskType.Pick,
      skuId,
      PackagingLevel.Each,
      1,
      1,
      orderId,
      waveId,
      None,
      None,
      None,
      sourceLocationId,
      destinationLocationId,
      userId
    )

  def singleLineOrder(orderId: OrderId, priority: Priority = Priority.Normal): Order =
    Order(orderId, priority, List(OrderLine(skuId, PackagingLevel.Each, 1)))

  def multiLineOrder(orderId: OrderId, priority: Priority = Priority.Normal): Order =
    Order(
      orderId,
      priority,
      List(
        OrderLine(skuId, PackagingLevel.Each, 1),
        OrderLine(SkuId(), PackagingLevel.Case, 1)
      )
    )

  def dispatch(
      candidates: List[Task.Allocated],
      allTasks: List[Task] = List.empty,
      orders: List[Order] = List.empty,
      consolidationGroups: List[ConsolidationGroup] = List.empty,
      criteria: List[DispatchCriterion] = List.empty
  ): List[Task.Allocated] =
    TaskDispatchPolicy(candidates, allTasks, orders, consolidationGroups, DispatchProfile(criteria))

  describe("TaskDispatchPolicy"):
    describe("with empty candidates"):
      it("returns empty list"):
        assert(dispatch(List.empty).isEmpty)

    describe("with empty profile"):
      it("returns candidates in original order"):
        val orderId = OrderId()
        val t1 = allocated(orderId)
        val t2 = allocated(orderId)
        val result = dispatch(List(t1, t2), orders = List(singleLineOrder(orderId)))
        assert(result == List(t1, t2))

    describe("count preservation"):
      it("returns all candidates — dispatch only reorders, never filters"):
        val orderId = OrderId()
        val order = singleLineOrder(orderId)
        val tasks = (1 to 5).map(_ => allocated(orderId)).toList
        val result =
          dispatch(tasks, orders = List(order), criteria = List(DispatchCriterion.OrderPriority))
        assert(result.size == 5)

    describe("WaveSequence"):
      it("sorts oldest wave first — smaller UUID v7 = earlier wave"):
        val wave1 = WaveId()
        val wave2 = WaveId()
        val orderId = OrderId()
        val order = singleLineOrder(orderId)
        val tNewer = allocated(orderId, Some(wave2))
        val tOlder = allocated(orderId, Some(wave1))
        val result = dispatch(
          List(tNewer, tOlder),
          orders = List(order),
          criteria = List(DispatchCriterion.WaveSequence)
        )
        assert(result.head == tOlder)

      it("puts tasks without a wave last"):
        val wave1 = WaveId()
        val orderId = OrderId()
        val order = singleLineOrder(orderId)
        val tNoWave = allocated(orderId, None)
        val tWithWave = allocated(orderId, Some(wave1))
        val result = dispatch(
          List(tNoWave, tWithWave),
          orders = List(order),
          criteria = List(DispatchCriterion.WaveSequence)
        )
        assert(result.head == tWithWave)
        assert(result.last == tNoWave)

    describe("OrderPriority"):
      it("sorts higher priority orders first"):
        val orderId1 = OrderId()
        val orderId2 = OrderId()
        val tLow = allocated(orderId1)
        val tCritical = allocated(orderId2)
        val result = dispatch(
          List(tLow, tCritical),
          orders = List(
            singleLineOrder(orderId1, Priority.Low),
            singleLineOrder(orderId2, Priority.Critical)
          ),
          criteria = List(DispatchCriterion.OrderPriority)
        )
        assert(result.head == tCritical)

      it("sorts Critical > High > Normal > Low"):
        val ids = List.fill(4)(OrderId())
        val priorities = List(Priority.Normal, Priority.High, Priority.Critical, Priority.Low)
        val tasks = ids.map(allocated(_))
        val orders = ids.zip(priorities).map((id, p) => singleLineOrder(id, p))
        val result =
          dispatch(tasks, orders = orders, criteria = List(DispatchCriterion.OrderPriority))
        val resultPriorities = result.map(t => orders.find(_.id == t.orderId).get.priority)
        assert(
          resultPriorities == List(Priority.Critical, Priority.High, Priority.Normal, Priority.Low)
        )

      it("sorts known orders before tasks whose orderId is absent from the orders list"):
        val knownId = OrderId()
        val unknownId = OrderId()
        val tKnown = allocated(knownId)
        val tUnknown = allocated(unknownId)
        val result = dispatch(
          List(tUnknown, tKnown),
          orders = List(singleLineOrder(knownId, Priority.Low)),
          criteria = List(DispatchCriterion.OrderPriority)
        )
        assert(result.head == tKnown)

    describe("OrderSimplicity"):
      it("sorts single-line orders before multi-line orders"):
        val orderId1 = OrderId()
        val orderId2 = OrderId()
        val tMulti = allocated(orderId1)
        val tSingle = allocated(orderId2)
        val result = dispatch(
          List(tMulti, tSingle),
          orders = List(multiLineOrder(orderId1), singleLineOrder(orderId2)),
          criteria = List(DispatchCriterion.OrderSimplicity)
        )
        assert(result.head == tSingle)

    describe("ConsolidationGroupCompletion"):
      it("sorts tasks with higher consolidation group completion percentage first"):
        val waveId = WaveId()
        val orderId1 = OrderId()
        val orderId2 = OrderId()
        val tPending1 = allocated(orderId1, Some(waveId))
        val tPending2 = allocated(orderId2, Some(waveId))
        val tCompleted1 = completedTask(orderId1, Some(waveId))
        val consolidationGroup1 =
          ConsolidationGroup.Created(ConsolidationGroupId(), waveId, List(orderId1))
        val consolidationGroup2 =
          ConsolidationGroup.Created(ConsolidationGroupId(), waveId, List(orderId2))
        val result = dispatch(
          List(tPending2, tPending1),
          allTasks = List(tCompleted1, tPending1, tPending2),
          orders = List(singleLineOrder(orderId1), singleLineOrder(orderId2)),
          consolidationGroups = List(consolidationGroup1, consolidationGroup2),
          criteria = List(DispatchCriterion.ConsolidationGroupCompletion)
        )
        assert(result.head == tPending1)

      it("treats tasks with no consolidation group as 0% completion — places them last"):
        val waveId = WaveId()
        val orderId1 = OrderId()
        val orderId2 = OrderId()
        val tWithConsolidationGroup = allocated(orderId1, Some(waveId))
        val tWithoutConsolidationGroup = allocated(orderId2, Some(waveId))
        val tCompleted = completedTask(orderId1, Some(waveId))
        val consolidationGroup =
          ConsolidationGroup.Created(ConsolidationGroupId(), waveId, List(orderId1))
        val result = dispatch(
          List(tWithoutConsolidationGroup, tWithConsolidationGroup),
          allTasks = List(tCompleted, tWithConsolidationGroup, tWithoutConsolidationGroup),
          orders = List(singleLineOrder(orderId1), singleLineOrder(orderId2)),
          consolidationGroups = List(consolidationGroup),
          criteria = List(DispatchCriterion.ConsolidationGroupCompletion)
        )
        assert(result.head == tWithConsolidationGroup)
        assert(result.last == tWithoutConsolidationGroup)

      it("treats tasks with waveId=None as 0% — consolidation groups require a waveId match"):
        val waveId = WaveId()
        val orderId = OrderId()
        val tNoWave = allocated(orderId, None)
        val tWithConsolidationGroup = allocated(orderId, Some(waveId))
        val tCompleted = completedTask(orderId, Some(waveId))
        val consolidationGroup =
          ConsolidationGroup.Created(ConsolidationGroupId(), waveId, List(orderId))
        val result = dispatch(
          List(tNoWave, tWithConsolidationGroup),
          allTasks = List(tCompleted, tWithConsolidationGroup, tNoWave),
          orders = List(singleLineOrder(orderId)),
          consolidationGroups = List(consolidationGroup),
          criteria = List(DispatchCriterion.ConsolidationGroupCompletion)
        )
        assert(result.head == tWithConsolidationGroup)
        assert(result.last == tNoWave)

    describe("PackagingTier"):
      it("sorts Pallet before Case before Each"):
        val orderId = OrderId()
        val order = singleLineOrder(orderId)
        val tEach = allocated(orderId, packagingLevel = PackagingLevel.Each)
        val tCase = allocated(orderId, packagingLevel = PackagingLevel.Case)
        val tPallet = allocated(orderId, packagingLevel = PackagingLevel.Pallet)
        val result = dispatch(
          List(tEach, tCase, tPallet),
          orders = List(order),
          criteria = List(DispatchCriterion.PackagingTier)
        )
        assert(result(0) == tPallet)
        assert(result(1) == tCase)
        assert(result(2) == tEach)

    describe("composite criteria"):
      it("uses second criterion to break ties from first"):
        val waveId = WaveId()
        val orderId1 = OrderId()
        val orderId2 = OrderId()
        val tMulti = allocated(orderId1, Some(waveId))
        val tSingle = allocated(orderId2, Some(waveId))
        val result = dispatch(
          List(tMulti, tSingle),
          orders = List(multiLineOrder(orderId1), singleLineOrder(orderId2)),
          criteria = List(DispatchCriterion.WaveSequence, DispatchCriterion.OrderSimplicity)
        )
        assert(result.head == tSingle)

      it("applies all criteria in order"):
        val wave1 = WaveId()
        val wave2 = WaveId()
        val orderId1 = OrderId()
        val orderId2 = OrderId()
        val orderId3 = OrderId()
        val t1 = allocated(orderId1, Some(wave1))
        val t2 = allocated(orderId2, Some(wave2))
        val t3 = allocated(orderId3, Some(wave2))
        val result = dispatch(
          List(t3, t2, t1),
          orders = List(
            singleLineOrder(orderId1, Priority.Critical),
            singleLineOrder(orderId2, Priority.Critical),
            singleLineOrder(orderId3, Priority.Normal)
          ),
          criteria = List(DispatchCriterion.WaveSequence, DispatchCriterion.OrderPriority)
        )
        assert(result(0) == t1)
        assert(result(1) == t2)
        assert(result(2) == t3)
