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
import { userQueries, type User } from "@/shared/api/users-api"
import { Badge } from "@/shared/ui/badge"
import { PageHeader } from "@/shared/ui/page-header"
import { DataGrid } from "@/shared/reui/data-grid/data-grid"
import { DataGridTable } from "@/shared/reui/data-grid/data-grid-table"
import { DataGridColumnHeader } from "@/shared/reui/data-grid/data-grid-column-header"
import { DataGridPagination } from "@/shared/reui/data-grid/data-grid-pagination"

export const Route = createFileRoute("/_authenticated/users/")({
  component: UsersPage,
  loader: ({ context }) =>
    context.queryClient.ensureQueryData(userQueries.all()),
})

function UsersPage() {
  const { data: users } = useSuspenseQuery(userQueries.all())
  const navigate = useNavigate()
  const [sorting, setSorting] = useState<SortingState>([])
  const [pagination, setPagination] = useState<PaginationState>({
    pageIndex: 0,
    pageSize: 20,
  })

  const columns = useMemo<ColumnDef<User>[]>(
    () => [
      {
        accessorKey: "name",
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="Name" />
        ),
      },
      {
        accessorKey: "login",
        cell: ({ row }) => (
          <span className="font-mono text-xs">{row.original.login}</span>
        ),
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="Login" />
        ),
        size: 140,
      },
      {
        accessorKey: "role",
        cell: ({ row }) => (
          <Badge variant="secondary">{row.original.role}</Badge>
        ),
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="Role" />
        ),
        size: 120,
      },
      {
        accessorKey: "active",
        cell: ({ row }) => (
          <Badge variant={row.original.active ? "default" : "secondary"}>
            {row.original.active ? "Active" : "Inactive"}
          </Badge>
        ),
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="Status" />
        ),
        size: 100,
      },
    ],
    [],
  )

  const table = useReactTable({
    columns,
    data: users,
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
        description="Warehouse operators, supervisors, and administrators"
        title="Users"
      />
      <DataGrid
        onRowClick={(user) =>
          navigate({ params: { userId: user.id }, to: "/users/$userId" })
        }
        recordCount={users.length}
        table={table}
        tableLayout={{ headerSticky: true }}
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
