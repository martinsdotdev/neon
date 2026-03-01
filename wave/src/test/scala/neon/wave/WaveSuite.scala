package neon.wave

class WaveIdSuite extends munit.FunSuite:
  test("each generated id is unique"):
    assert(WaveId() != WaveId())

  test("ids generated in sequence are time-ordered (UUIDv7)"):
    val first = WaveId()
    val second = WaveId()
    assert(first.value.compareTo(second.value) < 0)

class WaveSuite extends munit.FunSuite:
  val id = WaveId()

  test("releasing a planned wave produces a released wave"):
    val released: Wave.Released = Wave.Planned(id, OrderGrouping.Multi).release()
    assertEquals(released.id, id)

  test("completing a released wave produces a completed wave"):
    val completed: Wave.Completed =
      Wave.Planned(id, OrderGrouping.Multi).release().complete()
    assertEquals(completed.id, id)

  test("a planned wave can be cancelled before release"):
    val cancelled: Wave.Cancelled =
      Wave.Planned(id, OrderGrouping.Single).cancel()
    assertEquals(cancelled.id, id)

  test("a released wave can be cancelled before completion"):
    val cancelled: Wave.Cancelled =
      Wave.Planned(id, OrderGrouping.Multi).release().cancel()
    assertEquals(cancelled.id, id)

  test("a cancelled wave is the same regardless of when it was cancelled"):
    val fromPlanned = Wave.Planned(id, OrderGrouping.Multi).cancel()
    val fromReleased = Wave.Planned(id, OrderGrouping.Multi).release().cancel()
    assertEquals(fromPlanned, fromReleased)

  test("all four wave states are distinguishable by pattern match"):
    val waves: List[Wave] = List(
      Wave.Planned(id, OrderGrouping.Multi),
      Wave.Planned(id, OrderGrouping.Multi).release(),
      Wave.Planned(id, OrderGrouping.Multi).release().complete(),
      Wave.Planned(id, OrderGrouping.Multi).cancel()
    )
    val labels = waves.map:
      case _: Wave.Planned   => "planned"
      case _: Wave.Released  => "released"
      case _: Wave.Completed => "completed"
      case _: Wave.Cancelled => "cancelled"
    assertEquals(labels, List("planned", "released", "completed", "cancelled"))
