import { createFileRoute } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { useMemo, useState } from "react"
import type { ColumnDef } from "@tanstack/react-table"
import type { CycleCount } from "@/shared/api/cycle-counts"
import { cycleCountQueries } from "@/shared/api/cycle-counts"
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

export const Route = createFileRoute(
  "/_authenticated/cycle-counts/",
)({
  component: CycleCountsPage,
  loader: ({ context }) =>
    context.queryClient.ensureQueryData(cycleCountQueries.all()),
})

function CycleCountsPage() {
  const { data: counts } = useSuspenseQuery(cycleCountQueries.all())
  const [data, setData] = useState(counts)

  const columns = useMemo<ColumnDef<CycleCount>[]>(
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
        accessorKey: "countType",
        cell: ({ row }) => (
          <Badge variant="secondary">
            {row.original.countType}
          </Badge>
        ),
        header: "Type",
        meta: {
          cell: {
            options: [
              { label: "Planned", value: "Planned" },
              { label: "Random", value: "Random" },
              { label: "Triggered", value: "Triggered" },
              { label: "Recount", value: "Recount" },
            ],
            variant: "select" as const,
          },
          label: "Type",
        },
        size: 120,
      },
      {
        accessorKey: "countMethod",
        cell: ({ row }) => (
          <Badge variant="secondary">
            {row.original.countMethod}
          </Badge>
        ),
        header: "Method",
        meta: {
          cell: {
            options: [
              { label: "Blind", value: "Blind" },
              { label: "Informed", value: "Informed" },
            ],
            variant: "select" as const,
          },
          label: "Method",
        },
        size: 120,
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
        accessorKey: "taskCount",
        cell: ({ row }) => (
          <span className="font-mono text-xs">
            {row.original.taskCount}
          </span>
        ),
        header: "Tasks",
        meta: {
          cell: { variant: "number" as const },
          label: "Tasks",
        },
        size: 100,
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
              { label: "Created", value: "Created" },
              { label: "InProgress", value: "InProgress" },
              { label: "Completed", value: "Completed" },
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
        description="Inventory accuracy verification"
        title="Cycle Counts"
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
