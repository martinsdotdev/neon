import { createFileRoute } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { useMemo, useState } from "react"
import type { ColumnDef } from "@tanstack/react-table"
import type { Delivery } from "@/shared/api/deliveries"
import { deliveryQueries } from "@/shared/api/deliveries"
import { DataGrid } from "@/shared/data-grid/data-grid"
import { DataGridFilterMenu } from "@/shared/data-grid/data-grid-filter-menu"
import { DataGridRowHeightMenu } from "@/shared/data-grid/data-grid-row-height-menu"
import { getDataGridSelectColumn } from "@/shared/data-grid/data-grid-select-column"
import { DataGridSortMenu } from "@/shared/data-grid/data-grid-sort-menu"
import { DataGridViewMenu } from "@/shared/data-grid/data-grid-view-menu"
import { useDataGrid } from "@/shared/hooks/use-data-grid"
import { PageHeader } from "@/shared/ui/page-header"

export const Route = createFileRoute("/_authenticated/deliveries/")({
  component: DeliveriesPage,
  loader: ({ context }) =>
    context.queryClient.ensureQueryData(deliveryQueries.all()),
})

function DeliveriesPage() {
  const { data: deliveries } = useSuspenseQuery(deliveryQueries.all())
  const [data, setData] = useState(deliveries)

  const columns = useMemo<ColumnDef<Delivery>[]>(
    () => [
      getDataGridSelectColumn({ readOnly: true }),
      {
        accessorKey: "id",
        header: "ID",
        meta: { cell: { variant: "short-text" as const }, label: "ID" },
        size: 120,
      },
      {
        accessorKey: "skuId",
        header: "SKU",
        meta: {
          cell: { variant: "short-text" as const },
          label: "SKU",
        },
        size: 150,
      },
      {
        accessorKey: "expectedQuantity",
        header: "Expected Qty",
        meta: {
          cell: { variant: "number" as const },
          label: "Expected Qty",
        },
        size: 130,
      },
      {
        accessorKey: "receivedQuantity",
        header: "Received Qty",
        meta: {
          cell: { variant: "number" as const },
          label: "Received Qty",
        },
        size: 130,
      },
      {
        accessorKey: "state",
        header: "State",
        meta: {
          cell: {
            options: [
              { label: "Expected", value: "Expected" },
              {
                label: "PartiallyReceived",
                value: "PartiallyReceived",
              },
              { label: "Received", value: "Received" },
              { label: "Closed", value: "Closed" },
            ],
            variant: "select" as const,
          },
          label: "State",
        },
        size: 160,
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
        description="Inbound delivery scheduling and receiving"
        title="Deliveries"
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
