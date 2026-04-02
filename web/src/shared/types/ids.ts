declare const __brand: unique symbol

type Brand<T, B extends string> = T & { readonly [__brand]: B }

export type WaveId = Brand<string, "WaveId">
export type TaskId = Brand<string, "TaskId">
export type OrderId = Brand<string, "OrderId">
export type SkuId = Brand<string, "SkuId">
export type LocationId = Brand<string, "LocationId">
export type ZoneId = Brand<string, "ZoneId">
export type UserId = Brand<string, "UserId">
export type CarrierId = Brand<string, "CarrierId">
export type WorkstationId = Brand<string, "WorkstationId">
export type SlotId = Brand<string, "SlotId">
export type HandlingUnitId = Brand<string, "HandlingUnitId">
export type TransportOrderId = Brand<string, "TransportOrderId">
export type ConsolidationGroupId = Brand<string, "ConsolidationGroupId">
export type InventoryId = Brand<string, "InventoryId">
