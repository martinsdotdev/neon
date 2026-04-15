import { createFileRoute } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { useMemo, useState } from "react"
import type { ColumnDef } from "@tanstack/react-table"
import type { Wave } from "@/shared/api/waves"
import { waveQueries } from "@/shared/api/waves"
import { DataGrid } from "@/shared/data-grid/data-grid"
import { DataGridFilterMenu } from "@/shared/data-grid/data-grid-filter-menu"
import { DataGridRowHeightMenu } from "@/shared/data-grid/data-grid-row-height-menu"
import { getDataGridSelectColumn } from "@/shared/data-grid/data-grid-select-column"
import { DataGridSortMenu } from "@/shared/data-grid/data-grid-sort-menu"
import { DataGridViewMenu } from "@/shared/data-grid/data-grid-view-menu"
import { useDataGrid } from "@/shared/hooks/use-data-grid"
import { Badge } from "@/shared/ui/badge"
import { PageHeader } from "@/shared/ui/page-header"
import { StateBadge } from "@/shared/ui/state-badge"

export const Route = createFileRoute("/_authenticated/waves/")({
  component: WavesPage,
  loader: ({ context }) =>
    context.queryClient.ensureQueryData(waveQueries.all()),
})

function WavesPage() {
  const { data: waves } = useSuspenseQuery(waveQueries.all())
  const [data, setData] = useState(waves)

  const columns = useMemo<ColumnDef<Wave>[]>(
    () => [
      getDataGridSelectColumn({ enableRowMarkers: true }),
      {
        accessorKey: "id",
        cell: ({ row }) => (
          <span className="font-mono text-xs font-medium">
            {row.original.id}
          </span>
        ),
        header: "ID",
        meta: { cell: { variant: "short-text" as const }, label: "ID" },
        size: 120,
      },
      {
        accessorKey: "orderGrouping",
        cell: ({ row }) => (
          <Badge variant="secondary">
            {row.original.orderGrouping}
          </Badge>
        ),
        header: "Order Grouping",
        meta: {
          cell: {
            options: [
              { label: "Single", value: "Single" },
              { label: "Multi", value: "Multi" },
            ],
            variant: "select" as const,
          },
          label: "Order Grouping",
        },
        size: 150,
      },
      {
        accessorKey: "orderCount",
        cell: ({ row }) => (
          <span className="font-mono text-xs">
            {row.original.orderCount}
          </span>
        ),
        header: "Order Count",
        meta: {
          cell: { variant: "number" as const },
          label: "Order Count",
        },
        size: 120,
      },
      {
        accessorKey: "state",
        cell: ({ row }) => (
          <StateBadge state={row.original.state} />
        ),
        header: "State",
        meta: {
          cell: {
            options: [
              { label: "Planned", value: "Planned" },
              { label: "Released", value: "Released" },
              { label: "Completed", value: "Completed" },
              { label: "Cancelled", value: "Cancelled" },
            ],
            variant: "select" as const,
          },
          label: "State",
        },
        size: 130,
      },
      {
        accessorKey: "createdAt",
        header: "Created At",
        meta: {
          cell: { variant: "short-text" as const },
          label: "Created At",
        },
        size: 180,
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
        description="Wave planning and release management"
        title="Waves"
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
