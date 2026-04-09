package neon.common

enum Permission(val key: String):
  case WavePlan extends Permission("wave:plan")
  case WaveCancel extends Permission("wave:cancel")
  case TaskComplete extends Permission("task:complete")
  case TransportOrderConfirm extends Permission("transport-order:confirm")
  case ConsolidationGroupComplete extends Permission("consolidation-group:complete")
  case WorkstationAssign extends Permission("workstation:assign")
  case TaskAllocate extends Permission("task:allocate")
  case TaskAssign extends Permission("task:assign")
  case TaskCancel extends Permission("task:cancel")
  case WorkstationManage extends Permission("workstation:manage")
  case TransportOrderCancel extends Permission("transport-order:cancel")
  case ConsolidationGroupCancel extends Permission("consolidation-group:cancel")
  case HandlingUnitManage extends Permission("handling-unit:manage")
  case SlotManage extends Permission("slot:manage")
  case InventoryManage extends Permission("inventory:manage")
  case StockManage extends Permission("stock:manage")
  case InboundManage extends Permission("inbound:manage")
  case CycleCountManage extends Permission("cycle-count:manage")
  case UserManage extends Permission("user:manage")

object Permission:
  def fromKey(key: String): Option[Permission] =
    values.find(_.key == key)
