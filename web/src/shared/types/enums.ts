export const Priority = {
  Low: "Low",
  Normal: "Normal",
  High: "High",
  Critical: "Critical",
} as const
export type Priority = (typeof Priority)[keyof typeof Priority]

export const PackagingLevel = {
  Pallet: "Pallet",
  Case: "Case",
  InnerPack: "InnerPack",
  Each: "Each",
} as const
export type PackagingLevel =
  (typeof PackagingLevel)[keyof typeof PackagingLevel]

export const TaskType = {
  Pick: "Pick",
  Putaway: "Putaway",
  Replenish: "Replenish",
  Transfer: "Transfer",
} as const
export type TaskType = (typeof TaskType)[keyof typeof TaskType]

export const OrderGrouping = {
  Single: "Single",
  Multi: "Multi",
} as const
export type OrderGrouping =
  (typeof OrderGrouping)[keyof typeof OrderGrouping]

export const LocationType = {
  Pick: "Pick",
  Reserve: "Reserve",
  Buffer: "Buffer",
  Staging: "Staging",
  Packing: "Packing",
  Dock: "Dock",
} as const
export type LocationType =
  (typeof LocationType)[keyof typeof LocationType]

export const WorkstationType = {
  PutWall: "PutWall",
  PackStation: "PackStation",
} as const
export type WorkstationType =
  (typeof WorkstationType)[keyof typeof WorkstationType]

export const WaveStatus = {
  Planned: "Planned",
  Released: "Released",
  Completed: "Completed",
  Cancelled: "Cancelled",
} as const
export type WaveStatus = (typeof WaveStatus)[keyof typeof WaveStatus]

export const TaskStatus = {
  Planned: "Planned",
  Allocated: "Allocated",
  Assigned: "Assigned",
  Completed: "Completed",
  Cancelled: "Cancelled",
} as const
export type TaskStatus = (typeof TaskStatus)[keyof typeof TaskStatus]

export const TransportOrderStatus = {
  Pending: "Pending",
  Confirmed: "Confirmed",
  Cancelled: "Cancelled",
} as const
export type TransportOrderStatus =
  (typeof TransportOrderStatus)[keyof typeof TransportOrderStatus]

export const ConsolidationGroupStatus = {
  Created: "Created",
  Picked: "Picked",
  ReadyForWorkstation: "ReadyForWorkstation",
  Assigned: "Assigned",
  Completed: "Completed",
  Cancelled: "Cancelled",
} as const
export type ConsolidationGroupStatus =
  (typeof ConsolidationGroupStatus)[keyof typeof ConsolidationGroupStatus]

export const WorkstationStatus = {
  Disabled: "Disabled",
  Idle: "Idle",
  Active: "Active",
} as const
export type WorkstationStatus =
  (typeof WorkstationStatus)[keyof typeof WorkstationStatus]

export const SlotStatus = {
  Available: "Available",
  Reserved: "Reserved",
  Completed: "Completed",
} as const
export type SlotStatus = (typeof SlotStatus)[keyof typeof SlotStatus]

export const HandlingUnitStatus = {
  PickCreated: "PickCreated",
  ShipCreated: "ShipCreated",
  InBuffer: "InBuffer",
  Empty: "Empty",
  Packed: "Packed",
  ReadyToShip: "ReadyToShip",
  Shipped: "Shipped",
} as const
export type HandlingUnitStatus =
  (typeof HandlingUnitStatus)[keyof typeof HandlingUnitStatus]
