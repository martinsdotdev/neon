package neon.wave

sealed trait Wave:
  def id: WaveId
  def orderGrouping: OrderGrouping

object Wave:
  case class Planned(id: WaveId, orderGrouping: OrderGrouping) extends Wave:
    def release(): Released = Released(id, orderGrouping)
    def cancel(): Cancelled = Cancelled(id, orderGrouping)

  case class Released(id: WaveId, orderGrouping: OrderGrouping) extends Wave:
    def complete(): Completed = Completed(id, orderGrouping)
    def cancel(): Cancelled = Cancelled(id, orderGrouping)

  case class Completed(id: WaveId, orderGrouping: OrderGrouping) extends Wave

  case class Cancelled(id: WaveId, orderGrouping: OrderGrouping) extends Wave
