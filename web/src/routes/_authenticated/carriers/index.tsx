import { createFileRoute } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { useMemo, useState } from "react"
import type { ColumnDef } from "@tanstack/react-table"
import type { Carrier } from "@/shared/api/carriers"
import { carrierQueries } from "@/shared/api/carriers"
import { DataGrid } from "@/shared/data-grid/data-grid"
import { DataGridFilterMenu } from "@/shared/data-grid/data-grid-filter-menu"
import { DataGridRowHeightMenu } from "@/shared/data-grid/data-grid-row-height-menu"
import { DataGridSortMenu } from "@/shared/data-grid/data-grid-sort-menu"
import { DataGridViewMenu } from "@/shared/data-grid/data-grid-view-menu"
import { getDataGridSelectColumn } from "@/shared/data-grid/data-grid-select-column"
import { useDataGrid } from "@/shared/hooks/use-data-grid"
import { PageHeader } from "@/shared/ui/page-header"

export const Route = createFileRoute("/_authenticated/carriers/")({
  component: CarriersPage,
  loader: ({ context }) =>
    context.queryClient.ensureQueryData(carrierQueries.all()),
})

function CarriersPage() {
  const { data: carriers } = useSuspenseQuery(carrierQueries.all())
  const [data, setData] = useState(carriers)

  const columns = useMemo<ColumnDef<Carrier>[]>(
    () => [
      getDataGridSelectColumn({ readOnly: true }),
      {
        accessorKey: "code",
        header: "Code",
        meta: { cell: { variant: "short-text" as const }, label: "Code" },
        size: 120,
      },
      {
        accessorKey: "name",
        header: "Name",
        meta: { cell: { variant: "short-text" as const }, label: "Name" },
        size: 250,
      },
      {
        accessorKey: "active",
        header: "Status",
        meta: {
          cell: {
            options: [
              { label: "Active", value: "true" },
              { label: "Inactive", value: "false" },
            ],
            variant: "select" as const,
          },
          label: "Status",
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
        description="Shipping carriers for outbound fulfillment"
        title="Carriers"
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
