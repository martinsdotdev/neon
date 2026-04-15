import { createFileRoute, useNavigate } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import type { ColumnDef } from "@tanstack/react-table"
import {
  getCoreRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  useReactTable,
} from "@tanstack/react-table"
import type { PaginationState, SortingState } from "@tanstack/react-table"
import { useMemo, useState } from "react"
import { taskQueries, type Task } from "@/shared/api/tasks"
import { Badge } from "@/shared/ui/badge"
import { PageHeader } from "@/shared/ui/page-header"
import { DataGrid } from "@/shared/reui/data-grid/data-grid"
import { DataGridTable } from "@/shared/reui/data-grid/data-grid-table"
import { DataGridColumnHeader } from "@/shared/reui/data-grid/data-grid-column-header"
import { DataGridPagination } from "@/shared/reui/data-grid/data-grid-pagination"
import { StateBadge } from "@/shared/ui/state-badge"

export const Route = createFileRoute("/_authenticated/tasks/")({
  component: TasksPage,
  loader: ({ context }) =>
    context.queryClient.ensureQueryData(taskQueries.all()),
})

function TasksPage() {
  const { data: tasks } = useSuspenseQuery(taskQueries.all())
  const navigate = useNavigate()
  const [sorting, setSorting] = useState<SortingState>([])
  const [pagination, setPagination] = useState<PaginationState>({
    pageIndex: 0,
    pageSize: 20,
  })

  const columns = useMemo<ColumnDef<Task>[]>(
    () => [
      {
        accessorKey: "id",
        cell: ({ row }) => (
          <span className="font-mono text-xs font-medium">
            {row.original.id}
          </span>
        ),
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="ID" />
        ),
        size: 120,
      },
      {
        accessorKey: "taskType",
        cell: ({ row }) => <Badge>{row.original.taskType}</Badge>,
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="Type" />
        ),
        size: 120,
      },
      {
        accessorKey: "skuId",
        cell: ({ row }) => (
          <span className="font-mono text-xs">
            {row.original.skuId}
          </span>
        ),
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="SKU" />
        ),
      },
      {
        accessorKey: "state",
        cell: ({ row }) => <StateBadge state={row.original.state} />,
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="State" />
        ),
        size: 120,
      },
      {
        accessorKey: "requestedQuantity",
        cell: ({ row }) => (
          <span className="font-mono text-xs">
            {row.original.requestedQuantity}
          </span>
        ),
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="Requested Qty" />
        ),
        meta: { align: "right" },
        size: 140,
      },
      {
        accessorKey: "assignedTo",
        cell: ({ row }) => (
          <span className="font-mono text-xs">
            {row.original.assignedTo ?? "-"}
          </span>
        ),
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="Assigned To" />
        ),
      },
    ],
    [],
  )

  const table = useReactTable({
    columns,
    data: tasks,
    getCoreRowModel: getCoreRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
    getRowId: (row) => row.id,
    getSortedRowModel: getSortedRowModel(),
    onPaginationChange: setPagination,
    onSortingChange: setSorting,
    state: { pagination, sorting },
  })

  return (
    <div>
      <PageHeader
        description="Pick, putaway, replenish, and transfer tasks"
        title="Tasks"
      />
      <DataGrid
        onRowClick={(task) =>
          navigate({
            params: { taskId: task.id },
            to: "/tasks/$taskId",
          })
        }
        recordCount={tasks.length}
        table={table}
        tableLayout={{
          headerSticky: true,
        }}
      >
        <div className="w-full space-y-2.5">
          <div className="rounded-lg border">
            <DataGridTable />
          </div>
          <DataGridPagination sizes={[10, 20, 50]} />
        </div>
      </DataGrid>
    </div>
  )
}
