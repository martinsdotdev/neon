import { createFileRoute } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { useMemo, useState } from "react"
import type { ColumnDef } from "@tanstack/react-table"
import type { ConsolidationGroup } from "@/shared/api/consolidation-groups"
import { consolidationGroupQueries } from "@/shared/api/consolidation-groups"
import { DataGrid } from "@/shared/data-grid/data-grid"
import { DataGridFilterMenu } from "@/shared/data-grid/data-grid-filter-menu"
import { DataGridRowHeightMenu } from "@/shared/data-grid/data-grid-row-height-menu"
import { getDataGridSelectColumn } from "@/shared/data-grid/data-grid-select-column"
import { DataGridSortMenu } from "@/shared/data-grid/data-grid-sort-menu"
import { DataGridViewMenu } from "@/shared/data-grid/data-grid-view-menu"
import { useDataGrid } from "@/shared/hooks/use-data-grid"
import { PageHeader } from "@/shared/ui/page-header"
import { StateBadge } from "@/shared/ui/state-badge"

export const Route = createFileRoute(
  "/_authenticated/consolidation-groups/",
)({
  component: ConsolidationGroupsPage,
  loader: ({ context }) =>
    context.queryClient.ensureQueryData(
      consolidationGroupQueries.all(),
    ),
})

function ConsolidationGroupsPage() {
  const { data: groups } = useSuspenseQuery(
    consolidationGroupQueries.all(),
  )
  const [data, setData] = useState(groups)

  const columns = useMemo<ColumnDef<ConsolidationGroup>[]>(
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
        accessorKey: "waveId",
        cell: ({ row }) => (
          <span className="font-mono text-xs font-medium">
            {row.original.waveId}
          </span>
        ),
        header: "Wave",
        meta: {
          cell: { variant: "short-text" as const },
          label: "Wave",
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
              { label: "Created", value: "Created" },
              { label: "Picked", value: "Picked" },
              {
                label: "ReadyForWorkstation",
                value: "ReadyForWorkstation",
              },
              { label: "Assigned", value: "Assigned" },
              { label: "Completed", value: "Completed" },
              { label: "Cancelled", value: "Cancelled" },
            ],
            variant: "select" as const,
          },
          label: "State",
        },
        size: 180,
      },
      {
        accessorKey: "workstationId",
        cell: ({ row }) => (
          <span className="font-mono text-xs text-muted-foreground">
            {row.original.workstationId ?? "\u2014"}
          </span>
        ),
        header: "Workstation",
        meta: {
          cell: { variant: "short-text" as const },
          label: "Workstation",
        },
        size: 150,
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
        description="Order consolidation for workstation processing"
        title="Consolidation Groups"
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
