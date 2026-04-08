package neon.common

enum Permission(val key: String):
  case WavePlan extends Permission("wave:plan")
  case WaveCancel extends Permission("wave:cancel")
  case TaskComplete extends Permission("task:complete")
  case TransportOrderConfirm extends Permission("transport-order:confirm")
  case ConsolidationGroupComplete extends Permission("consolidation-group:complete")
  case WorkstationAssign extends Permission("workstation:assign")
  case UserManage extends Permission("user:manage")

object Permission:
  def fromKey(key: String): Option[Permission] =
    values.find(_.key == key)
