package neon.app

import neon.common.{OrderId, TaskId}
import neon.consolidationgroup.ConsolidationGroup
import neon.order.Order
import neon.task.Task

object TaskDispatchPolicy:
  def apply(
      candidates: List[Task.Allocated],
      allTasks: List[Task],
      orders: List[Order],
      consolidationGroups: List[ConsolidationGroup],
      profile: DispatchProfile
  ): List[Task.Allocated] =
    if profile.criteria.isEmpty then candidates
    else
      val orderMap = orders.map(o => o.id -> o).toMap
      val ratioMap: Map[TaskId, Double] =
        if profile.criteria.contains(DispatchCriterion.GroupCompletion) then
          candidates.map(t => t.id -> completionRatio(t, allTasks, consolidationGroups)).toMap
        else Map.empty
      candidates.sortWith: (a, b) =>
        profile.criteria.iterator
          .map(criterion => compare(a, b, criterion, orderMap, ratioMap))
          .find(_ != 0)
          .getOrElse(0) < 0

  private def compare(
      a: Task.Allocated,
      b: Task.Allocated,
      criterion: DispatchCriterion,
      orderMap: Map[OrderId, Order],
      ratioMap: Map[TaskId, Double]
  ): Int =
    criterion match
      case DispatchCriterion.WaveSequence =>
        (a.waveId, b.waveId) match
          case (None, None)         => 0
          case (None, _)            => 1
          case (_, None)            => -1
          case (Some(wa), Some(wb)) => wa.value.compareTo(wb.value)

      case DispatchCriterion.OrderPriority =>
        val pa = orderMap.get(a.orderId).fold(-1)(_.priority.ordinal)
        val pb = orderMap.get(b.orderId).fold(-1)(_.priority.ordinal)
        pb - pa

      case DispatchCriterion.OrderSimplicity =>
        val sa = orderMap.get(a.orderId).exists(_.lines.size == 1)
        val sb = orderMap.get(b.orderId).exists(_.lines.size == 1)
        if sa == sb then 0
        else if sa then -1
        else 1

      case DispatchCriterion.GroupCompletion =>
        val ra = ratioMap.getOrElse(a.id, 0.0)
        val rb = ratioMap.getOrElse(b.id, 0.0)
        rb.compareTo(ra)

      case DispatchCriterion.PackagingTier =>
        a.packagingLevel.ordinal - b.packagingLevel.ordinal

  private def completionRatio(
      task: Task.Allocated,
      allTasks: List[Task],
      consolidationGroups: List[ConsolidationGroup]
  ): Double =
    consolidationGroups
      .find(g => task.waveId.contains(g.waveId) && g.orderIds.contains(task.orderId))
      .map: g =>
        val groupTasks =
          allTasks.filter(t => t.waveId.contains(g.waveId) && g.orderIds.contains(t.orderId))
        if groupTasks.isEmpty then 0.0
        else groupTasks.count(_.isInstanceOf[Task.Completed]).toDouble / groupTasks.size
      .getOrElse(0.0)
