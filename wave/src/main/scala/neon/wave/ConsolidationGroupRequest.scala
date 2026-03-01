package neon.wave

import neon.common.{OrderId, WaveId}

case class ConsolidationGroupRequest(waveId: WaveId, orderIds: List[OrderId])
