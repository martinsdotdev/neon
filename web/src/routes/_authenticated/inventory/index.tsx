import { createFileRoute } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { useMemo, useState } from "react"
import type { ColumnDef } from "@tanstack/react-table"
import type { InventoryRecord } from "@/shared/api/inventory"
import { inventoryQueries } from "@/shared/api/inventory"
import { DataGrid } from "@/shared/data-grid/data-grid"
import { DataGridFilterMenu } from "@/shared/data-grid/data-grid-filter-menu"
import { DataGridRowHeightMenu } from "@/shared/data-grid/data-grid-row-height-menu"
import { getDataGridSelectColumn } from "@/shared/data-grid/data-grid-select-column"
import { DataGridSortMenu } from "@/shared/data-grid/data-grid-sort-menu"
import { DataGridViewMenu } from "@/shared/data-grid/data-grid-view-menu"
import { useDataGrid } from "@/shared/hooks/use-data-grid"
import { PageHeader } from "@/shared/ui/page-header"
import { StateBadge } from "@/shared/ui/state-badge"

export const Route = createFileRoute("/_authenticated/inventory/")({
  component: InventoryPage,
  loader: ({ context }) =>
    context.queryClient.ensureQueryData(inventoryQueries.all()),
})

function InventoryPage() {
  const { data: records } = useSuspenseQuery(inventoryQueries.all())
  const [data, setData] = useState(records)

  const columns = useMemo<ColumnDef<InventoryRecord>[]>(
    () => [
      getDataGridSelectColumn({ readOnly: true }),
      {
        accessorKey: "skuId",
        cell: ({ row }) => (
          <span className="font-mono text-xs font-medium">
            {row.original.skuId}
          </span>
        ),
        header: "SKU",
        meta: {
          cell: { variant: "short-text" as const },
          label: "SKU",
        },
        size: 150,
      },
      {
        accessorKey: "locationId",
        cell: ({ row }) => (
          <span className="font-mono text-xs font-medium">
            {row.original.locationId}
          </span>
        ),
        header: "Location",
        meta: {
          cell: { variant: "short-text" as const },
          label: "Location",
        },
        size: 150,
      },
      {
        accessorKey: "status",
        cell: ({ row }) => (
          <StateBadge state={row.original.status} />
        ),
        header: "Status",
        meta: {
          cell: {
            options: [
              { label: "Available", value: "Available" },
              { label: "QualityHold", value: "QualityHold" },
              { label: "Damaged", value: "Damaged" },
              { label: "Blocked", value: "Blocked" },
              { label: "Expired", value: "Expired" },
            ],
            variant: "select" as const,
          },
          label: "Status",
        },
        size: 130,
      },
      {
        accessorKey: "onHand",
        cell: ({ row }) => (
          <span className="font-mono text-xs">
            {row.original.onHand}
          </span>
        ),
        header: "On Hand",
        meta: {
          cell: { variant: "number" as const },
          label: "On Hand",
        },
        size: 120,
      },
      {
        accessorKey: "available",
        cell: ({ row }) => (
          <span className="font-mono text-xs">
            {row.original.available}
          </span>
        ),
        header: "Available",
        meta: {
          cell: { variant: "number" as const },
          label: "Available",
        },
        size: 120,
      },
      {
        accessorKey: "reserved",
        cell: ({ row }) => (
          <span className="font-mono text-xs">
            {row.original.reserved}
          </span>
        ),
        header: "Reserved",
        meta: {
          cell: { variant: "number" as const },
          label: "Reserved",
        },
        size: 120,
      },
    ],
    [],
  )

  const gridProps = useDataGrid({
    columns,
    data,
    enableSearch: true,
    onDataChange: setData,
    readOnly: true,
    rowHeight: "short",
  })

  return (
    <div>
      <PageHeader
        description="Location-level inventory records"
        title="Inventory"
      />
      <div className="flex items-center gap-2 pb-2">
        <DataGridFilterMenu table={gridProps.table} />
        <DataGridSortMenu table={gridProps.table} />
        <DataGridRowHeightMenu table={gridProps.table} />
        <DataGridViewMenu table={gridProps.table} />
      </div>
      <DataGrid {...gridProps} height={500} />
    </div>
  )
}
