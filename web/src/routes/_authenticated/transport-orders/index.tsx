import { createFileRoute } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { useMemo, useState } from "react"
import type { ColumnDef } from "@tanstack/react-table"
import type { TransportOrder } from "@/shared/api/transport-orders"
import { transportOrderQueries } from "@/shared/api/transport-orders"
import { DataGrid } from "@/shared/data-grid/data-grid"
import { DataGridFilterMenu } from "@/shared/data-grid/data-grid-filter-menu"
import { DataGridRowHeightMenu } from "@/shared/data-grid/data-grid-row-height-menu"
import { getDataGridSelectColumn } from "@/shared/data-grid/data-grid-select-column"
import { DataGridSortMenu } from "@/shared/data-grid/data-grid-sort-menu"
import { DataGridViewMenu } from "@/shared/data-grid/data-grid-view-menu"
import { useDataGrid } from "@/shared/hooks/use-data-grid"
import { PageHeader } from "@/shared/ui/page-header"

export const Route = createFileRoute(
  "/_authenticated/transport-orders/",
)({
  component: TransportOrdersPage,
  loader: ({ context }) =>
    context.queryClient.ensureQueryData(
      transportOrderQueries.all(),
    ),
})

function TransportOrdersPage() {
  const { data: orders } = useSuspenseQuery(
    transportOrderQueries.all(),
  )
  const [data, setData] = useState(orders)

  const columns = useMemo<ColumnDef<TransportOrder>[]>(
    () => [
      getDataGridSelectColumn({ readOnly: true }),
      {
        accessorKey: "id",
        header: "ID",
        meta: { cell: { variant: "short-text" as const }, label: "ID" },
        size: 120,
      },
      {
        accessorKey: "handlingUnitId",
        header: "Handling Unit",
        meta: {
          cell: { variant: "short-text" as const },
          label: "Handling Unit",
        },
        size: 150,
      },
      {
        accessorKey: "destination",
        header: "Destination",
        meta: {
          cell: { variant: "short-text" as const },
          label: "Destination",
        },
        size: 150,
      },
      {
        accessorKey: "state",
        header: "State",
        meta: {
          cell: {
            options: [
              { label: "Pending", value: "Pending" },
              { label: "Confirmed", value: "Confirmed" },
              { label: "Cancelled", value: "Cancelled" },
            ],
            variant: "select" as const,
          },
          label: "State",
        },
        size: 130,
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
        description="Material movement between locations"
        title="Transport Orders"
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
