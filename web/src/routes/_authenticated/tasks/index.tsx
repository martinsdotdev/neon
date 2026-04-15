import { createFileRoute } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { useMemo, useState } from "react"
import type { ColumnDef } from "@tanstack/react-table"
import type { Task } from "@/shared/api/tasks"
import { taskQueries } from "@/shared/api/tasks"
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

export const Route = createFileRoute("/_authenticated/tasks/")({
  component: TasksPage,
  loader: ({ context }) =>
    context.queryClient.ensureQueryData(taskQueries.all()),
})

function TasksPage() {
  const { data: tasks } = useSuspenseQuery(taskQueries.all())
  const [data, setData] = useState(tasks)

  const columns = useMemo<ColumnDef<Task>[]>(
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
        accessorKey: "taskType",
        cell: ({ row }) => (
          <Badge variant="secondary">
            {row.original.taskType}
          </Badge>
        ),
        header: "Type",
        meta: {
          cell: {
            options: [
              { label: "Pick", value: "Pick" },
              { label: "Putaway", value: "Putaway" },
              { label: "Replenish", value: "Replenish" },
              { label: "Transfer", value: "Transfer" },
            ],
            variant: "select" as const,
          },
          label: "Type",
        },
        size: 120,
      },
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
        accessorKey: "state",
        cell: ({ row }) => (
          <StateBadge state={row.original.state} />
        ),
        header: "State",
        meta: {
          cell: {
            options: [
              { label: "Planned", value: "Planned" },
              { label: "Allocated", value: "Allocated" },
              { label: "Assigned", value: "Assigned" },
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
        accessorKey: "requestedQuantity",
        cell: ({ row }) => (
          <span className="font-mono text-xs">
            {row.original.requestedQuantity}
          </span>
        ),
        header: "Requested Qty",
        meta: {
          cell: { variant: "number" as const },
          label: "Requested Qty",
        },
        size: 140,
      },
      {
        accessorKey: "assignedTo",
        cell: ({ row }) => (
          <span className="font-mono text-xs text-muted-foreground">
            {row.original.assignedTo ?? "\u2014"}
          </span>
        ),
        header: "Assigned To",
        meta: {
          cell: { variant: "short-text" as const },
          label: "Assigned To",
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
        description="Pick, putaway, replenish, and transfer tasks"
        title="Tasks"
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
