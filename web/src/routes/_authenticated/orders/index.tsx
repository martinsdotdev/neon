import { createFileRoute } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { useMemo, useState } from "react"
import type { ColumnDef } from "@tanstack/react-table"
import type { Order } from "@/shared/api/orders"
import { orderQueries } from "@/shared/api/orders"
import { DataGrid } from "@/shared/data-grid/data-grid"
import { DataGridFilterMenu } from "@/shared/data-grid/data-grid-filter-menu"
import { DataGridRowHeightMenu } from "@/shared/data-grid/data-grid-row-height-menu"
import { getDataGridSelectColumn } from "@/shared/data-grid/data-grid-select-column"
import { DataGridSortMenu } from "@/shared/data-grid/data-grid-sort-menu"
import { DataGridViewMenu } from "@/shared/data-grid/data-grid-view-menu"
import { useDataGrid } from "@/shared/hooks/use-data-grid"
import { PageHeader } from "@/shared/ui/page-header"

export const Route = createFileRoute("/_authenticated/orders/")({
  component: OrdersPage,
  loader: ({ context }) =>
    context.queryClient.ensureQueryData(orderQueries.all()),
})

function OrdersPage() {
  const { data: orders } = useSuspenseQuery(orderQueries.all())
  const [data, setData] = useState(orders)

  const columns = useMemo<ColumnDef<Order>[]>(
    () => [
      getDataGridSelectColumn({ readOnly: true }),
      {
        accessorKey: "id",
        header: "Order ID",
        meta: {
          cell: { variant: "short-text" as const },
          label: "Order ID",
        },
        size: 140,
      },
      {
        accessorKey: "priority",
        header: "Priority",
        meta: {
          cell: {
            options: [
              { label: "Low", value: "Low" },
              { label: "Normal", value: "Normal" },
              { label: "High", value: "High" },
              { label: "Critical", value: "Critical" },
            ],
            variant: "select" as const,
          },
          label: "Priority",
        },
        size: 120,
      },
      {
        accessorFn: (row) => row.lines.length,
        header: "Lines",
        id: "lineCount",
        meta: {
          cell: { variant: "number" as const },
          label: "Lines",
        },
        size: 80,
      },
      {
        accessorKey: "carrierId",
        header: "Carrier",
        meta: {
          cell: { variant: "short-text" as const },
          label: "Carrier",
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
        description="Customer orders for fulfillment"
        title="Orders"
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
