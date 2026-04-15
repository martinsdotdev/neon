import { createFileRoute } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { useMemo, useState } from "react"
import type { ColumnDef } from "@tanstack/react-table"
import type { Sku } from "@/shared/api/skus"
import { skuQueries } from "@/shared/api/skus"
import { DataGrid } from "@/shared/data-grid/data-grid"
import { DataGridFilterMenu } from "@/shared/data-grid/data-grid-filter-menu"
import { DataGridRowHeightMenu } from "@/shared/data-grid/data-grid-row-height-menu"
import { getDataGridSelectColumn } from "@/shared/data-grid/data-grid-select-column"
import { DataGridSortMenu } from "@/shared/data-grid/data-grid-sort-menu"
import { DataGridViewMenu } from "@/shared/data-grid/data-grid-view-menu"
import { useDataGrid } from "@/shared/hooks/use-data-grid"
import { PageHeader } from "@/shared/ui/page-header"

export const Route = createFileRoute("/_authenticated/skus/")({
  component: SkusPage,
  loader: ({ context }) =>
    context.queryClient.ensureQueryData(skuQueries.all()),
})

function SkusPage() {
  const { data: skus } = useSuspenseQuery(skuQueries.all())
  const [data, setData] = useState(skus)

  const columns = useMemo<ColumnDef<Sku>[]>(
    () => [
      getDataGridSelectColumn({ readOnly: true }),
      {
        accessorKey: "code",
        header: "Code",
        meta: {
          cell: { variant: "short-text" as const },
          label: "Code",
        },
        size: 160,
      },
      {
        accessorKey: "description",
        header: "Description",
        meta: {
          cell: { variant: "short-text" as const },
          label: "Description",
        },
        size: 300,
      },
      {
        accessorKey: "lotManaged",
        header: "Lot Managed",
        meta: {
          cell: { variant: "checkbox" as const },
          label: "Lot Managed",
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
        description="Stock keeping units in the warehouse"
        title="SKUs"
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
