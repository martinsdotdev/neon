import { createFileRoute } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { useMemo, useState } from "react"
import type { ColumnDef } from "@tanstack/react-table"
import type { Workstation } from "@/shared/api/workstations"
import { workstationQueries } from "@/shared/api/workstations"
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
  "/_authenticated/workstations/",
)({
  component: WorkstationsPage,
  loader: ({ context }) =>
    context.queryClient.ensureQueryData(workstationQueries.all()),
})

function WorkstationsPage() {
  const { data: workstations } = useSuspenseQuery(
    workstationQueries.all(),
  )
  const [data, setData] = useState(workstations)

  const columns = useMemo<ColumnDef<Workstation>[]>(
    () => [
      getDataGridSelectColumn({ readOnly: true }),
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
        accessorKey: "workstationType",
        cell: ({ row }) => (
          <Badge variant="secondary">
            {row.original.workstationType}
          </Badge>
        ),
        header: "Type",
        meta: {
          cell: {
            options: [
              { label: "PutWall", value: "PutWall" },
              { label: "PackStation", value: "PackStation" },
            ],
            variant: "select" as const,
          },
          label: "Type",
        },
        size: 130,
      },
      {
        accessorKey: "mode",
        cell: ({ row }) => (
          <Badge variant="secondary">{row.original.mode}</Badge>
        ),
        header: "Mode",
        meta: {
          cell: {
            options: [
              { label: "Receiving", value: "Receiving" },
              { label: "Picking", value: "Picking" },
              { label: "Counting", value: "Counting" },
              { label: "Relocation", value: "Relocation" },
            ],
            variant: "select" as const,
          },
          label: "Mode",
        },
        size: 130,
      },
      {
        accessorKey: "slotCount",
        cell: ({ row }) => (
          <span className="font-mono text-xs">
            {row.original.slotCount}
          </span>
        ),
        header: "Slots",
        meta: {
          cell: { variant: "number" as const },
          label: "Slots",
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
              { label: "Disabled", value: "Disabled" },
              { label: "Idle", value: "Idle" },
              { label: "Active", value: "Active" },
            ],
            variant: "select" as const,
          },
          label: "State",
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
        description="Put walls and pack stations"
        title="Workstations"
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
