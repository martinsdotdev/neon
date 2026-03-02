package neon.app

import neon.common.{GroupId, OrderId, PackagingLevel, Priority, SkuId, TaskId, UserId, WaveId}
import neon.consolidationgroup.ConsolidationGroup
import neon.order.{Order, OrderLine}
import neon.task.{Task, TaskType}
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant

class TaskDispatchPolicySuite extends AnyFunSpec:
  val skuId = SkuId()
  val userId = UserId()
  val at = Instant.now()

  def planned(
      orderId: OrderId,
      waveId: Option[WaveId] = None,
      packagingLevel: PackagingLevel = PackagingLevel.Each
  ): Task.Planned =
    Task.Planned(TaskId(), TaskType.Pick, skuId, packagingLevel, 1, orderId, waveId, None, None)

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
      candidates: List[Task.Planned],
      allTasks: List[Task] = List.empty,
      orders: List[Order] = List.empty,
      groups: List[ConsolidationGroup] = List.empty,
      criteria: List[DispatchCriterion] = List.empty
  ): List[Task.Planned] =
    TaskDispatchPolicy(candidates, allTasks, orders, groups, DispatchProfile(criteria))

  describe("TaskDispatchPolicy"):
    describe("with empty candidates"):
      it("returns empty list"):
        assert(dispatch(List.empty).isEmpty)

    describe("with empty profile"):
      it("returns candidates in original order"):
        val orderId = OrderId()
        val t1 = planned(orderId)
        val t2 = planned(orderId)
        val result = dispatch(List(t1, t2), orders = List(singleLineOrder(orderId)))
        assert(result == List(t1, t2))

    describe("returns all candidates"):
      it("does not truncate the result"):
        val orderId = OrderId()
        val order = singleLineOrder(orderId)
        val tasks = (1 to 5).map(_ => planned(orderId)).toList
        val result =
          dispatch(tasks, orders = List(order), criteria = List(DispatchCriterion.OrderPriority))
        assert(result.size == 5)

    describe("WaveSequence"):
      it("sorts oldest wave first — smaller UUID v7 = earlier wave"):
        val wave1 = WaveId()
        val wave2 = WaveId()
        val orderId = OrderId()
        val order = singleLineOrder(orderId)
        val tNewer = planned(orderId, Some(wave2))
        val tOlder = planned(orderId, Some(wave1))
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
        val tNoWave = planned(orderId, None)
        val tWithWave = planned(orderId, Some(wave1))
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
        val tLow = planned(orderId1)
        val tCritical = planned(orderId2)
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
        val tasks = ids.map(planned(_))
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
        val tKnown = planned(knownId)
        val tUnknown = planned(unknownId) // orderId not in orders
        val result = dispatch(
          List(tUnknown, tKnown),
          orders = List(singleLineOrder(knownId, Priority.Low)),
          criteria = List(DispatchCriterion.OrderPriority)
        )
        assert(result.head == tKnown) // Low(0) > unknown(-1)

    describe("OrderSimplicity"):
      it("sorts single-line orders before multi-line orders"):
        val orderId1 = OrderId()
        val orderId2 = OrderId()
        val tMulti = planned(orderId1)
        val tSingle = planned(orderId2)
        val result = dispatch(
          List(tMulti, tSingle),
          orders = List(multiLineOrder(orderId1), singleLineOrder(orderId2)),
          criteria = List(DispatchCriterion.OrderSimplicity)
        )
        assert(result.head == tSingle)

    describe("GroupCompletion"):
      it("sorts tasks with higher group completion percentage first"):
        val waveId = WaveId()
        val orderId1 = OrderId()
        val orderId2 = OrderId()
        // group1: orderId1 → 1 completed + 1 pending = 50%
        // group2: orderId2 → 0 completed + 1 pending = 0%
        val tPending1 = planned(orderId1, Some(waveId))
        val tPending2 = planned(orderId2, Some(waveId))
        val tCompleted1 = completedTask(orderId1, Some(waveId))
        val group1 = ConsolidationGroup.Created(GroupId(), waveId, List(orderId1))
        val group2 = ConsolidationGroup.Created(GroupId(), waveId, List(orderId2))
        val result = dispatch(
          List(tPending2, tPending1),
          allTasks = List(tCompleted1, tPending1, tPending2),
          orders = List(singleLineOrder(orderId1), singleLineOrder(orderId2)),
          groups = List(group1, group2),
          criteria = List(DispatchCriterion.GroupCompletion)
        )
        assert(result.head == tPending1)

      it("treats tasks with no group as 0% completion — places them last"):
        val waveId = WaveId()
        val orderId1 = OrderId()
        val orderId2 = OrderId()
        val tInGroup = planned(orderId1, Some(waveId))
        val tNoGroup = planned(orderId2, Some(waveId))
        val tCompleted = completedTask(orderId1, Some(waveId))
        val group = ConsolidationGroup.Created(GroupId(), waveId, List(orderId1))
        val result = dispatch(
          List(tNoGroup, tInGroup),
          allTasks = List(tCompleted, tInGroup, tNoGroup),
          orders = List(singleLineOrder(orderId1), singleLineOrder(orderId2)),
          groups = List(group),
          criteria = List(DispatchCriterion.GroupCompletion)
        )
        assert(result.head == tInGroup)
        assert(result.last == tNoGroup)

      it("treats tasks with waveId=None as 0% completion — groups require a waveId match"):
        val waveId = WaveId()
        val orderId = OrderId()
        val tNoWave = planned(orderId, None) // waveId absent → cannot match any group
        val tInGroup = planned(orderId, Some(waveId))
        val tCompleted = completedTask(orderId, Some(waveId))
        val group = ConsolidationGroup.Created(GroupId(), waveId, List(orderId))
        val result = dispatch(
          List(tNoWave, tInGroup),
          allTasks = List(tCompleted, tInGroup, tNoWave),
          orders = List(singleLineOrder(orderId)),
          groups = List(group),
          criteria = List(DispatchCriterion.GroupCompletion)
        )
        assert(result.head == tInGroup) // has a group match → higher %
        assert(result.last == tNoWave) // waveId=None → 0%

    describe("PackagingTier"):
      it("sorts Pallet before Case before Each"):
        val orderId = OrderId()
        val order = singleLineOrder(orderId)
        val tEach = planned(orderId, packagingLevel = PackagingLevel.Each)
        val tCase = planned(orderId, packagingLevel = PackagingLevel.Case)
        val tPallet = planned(orderId, packagingLevel = PackagingLevel.Pallet)
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
        // Both in same wave: WaveSequence is a tie; OrderSimplicity breaks it
        val tMulti = planned(orderId1, Some(waveId))
        val tSingle = planned(orderId2, Some(waveId))
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
        // task1: wave1, Critical  → should be first (oldest wave + highest priority)
        // task2: wave2, Critical  → second (newer wave)
        // task3: wave2, Normal    → last (newer wave + lower priority)
        val t1 = planned(orderId1, Some(wave1))
        val t2 = planned(orderId2, Some(wave2))
        val t3 = planned(orderId3, Some(wave2))
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
