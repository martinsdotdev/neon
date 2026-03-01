package neon.wave

class WaveSuite extends munit.FunSuite:
  val id = WaveId()
  val orderIds = List(OrderId(), OrderId(), OrderId())

  test("releasing a wave authorizes work to begin"):
    val (released, event) = Wave.Planned(id, OrderGrouping.Multi, orderIds).release()
    assertEquals(event.waveId, id)
    assertEquals(event.orderGrouping, OrderGrouping.Multi)

  test("the release event carries order IDs for task creation"):
    val (_, event) = Wave.Planned(id, OrderGrouping.Multi, orderIds).release()
    assertEquals(event.orderIds, orderIds)

  test("completing a released wave marks all work as done"):
    val (released, _) = Wave.Planned(id, OrderGrouping.Multi, orderIds).release()
    val (completed, event) = released.complete()
    assertEquals(event.waveId, id)

  test("a planned wave can be discarded before any work starts"):
    val (cancelled, event) = Wave.Planned(id, OrderGrouping.Single, orderIds).cancel()
    assertEquals(event.waveId, id)

  test("a released wave can be cancelled to stop in-progress work"):
    val (released, _) = Wave.Planned(id, OrderGrouping.Multi, orderIds).release()
    val (cancelled, event) = released.cancel()
    assertEquals(event.waveId, id)

  test("order grouping is carried in events for downstream routing"):
    val (_, releaseEvent) = Wave.Planned(id, OrderGrouping.Multi, orderIds).release()
    assertEquals(releaseEvent.orderGrouping, OrderGrouping.Multi)
    val (released, _) = Wave.Planned(id, OrderGrouping.Single, orderIds).release()
    val (_, completeEvent) = released.complete()
    assertEquals(completeEvent.orderGrouping, OrderGrouping.Single)
