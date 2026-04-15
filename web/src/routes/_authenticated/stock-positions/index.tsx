import { createFileRoute } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { useMemo, useState } from "react"
import type { ColumnDef } from "@tanstack/react-table"
import type { StockPosition } from "@/shared/api/stock-positions"
import { stockPositionQueries } from "@/shared/api/stock-positions"
import { DataGrid } from "@/shared/data-grid/data-grid"
import { DataGridFilterMenu } from "@/shared/data-grid/data-grid-filter-menu"
import { DataGridRowHeightMenu } from "@/shared/data-grid/data-grid-row-height-menu"
import { getDataGridSelectColumn } from "@/shared/data-grid/data-grid-select-column"
import { DataGridSortMenu } from "@/shared/data-grid/data-grid-sort-menu"
import { DataGridViewMenu } from "@/shared/data-grid/data-grid-view-menu"
import { useDataGrid } from "@/shared/hooks/use-data-grid"
import { PageHeader } from "@/shared/ui/page-header"

export const Route = createFileRoute(
  "/_authenticated/stock-positions/",
)({
  component: StockPositionsPage,
  loader: ({ context }) =>
    context.queryClient.ensureQueryData(stockPositionQueries.all()),
})

function StockPositionsPage() {
  const { data: positions } = useSuspenseQuery(
    stockPositionQueries.all(),
  )
  const [data, setData] = useState(positions)

  const columns = useMemo<ColumnDef<StockPosition>[]>(
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
        accessorKey: "warehouseAreaId",
        cell: ({ row }) => (
          <span className="font-mono text-xs font-medium">
            {row.original.warehouseAreaId}
          </span>
        ),
        header: "Area",
        meta: {
          cell: { variant: "short-text" as const },
          label: "Area",
        },
        size: 150,
      },
      {
        accessorKey: "onHandQuantity",
        cell: ({ row }) => (
          <span className="font-mono text-xs">
            {row.original.onHandQuantity}
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
        accessorKey: "availableQuantity",
        cell: ({ row }) => (
          <span className="font-mono text-xs">
            {row.original.availableQuantity}
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
        accessorKey: "blockedQuantity",
        cell: ({ row }) => (
          <span className="font-mono text-xs">
            {row.original.blockedQuantity}
          </span>
        ),
        header: "Blocked",
        meta: {
          cell: { variant: "number" as const },
          label: "Blocked",
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
        description="Stock levels by SKU and warehouse area"
        title="Stock Positions"
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
