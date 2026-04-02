import {
  createColumnHelper,
  getCoreRowModel,
  useReactTable,
} from "@tanstack/react-table"
import { createFileRoute } from "@tanstack/react-router"

import type { User } from "@/shared/types/user"
import { useUsers } from "@/entities/user/query-hooks/use-users"
import { DataTable } from "@/shared/components/data-table"
import { PageHeader } from "@/shared/components/page-header"
import { Skeleton } from "@/components/ui/skeleton"
import { m } from "@/paraglide/messages.js"

export const Route = createFileRoute("/_app/users")({
  component: UsersPage,
})

const columnHelper = createColumnHelper<User>()

const columns = [
  columnHelper.accessor("id", {
    header: () => m.column_id(),
    cell: (info) => (
      <span className="font-mono text-xs">
        {info.getValue().slice(0, 8)}
      </span>
    ),
  }),
  columnHelper.accessor("login", {
    header: "Login",
  }),
  columnHelper.accessor("name", {
    header: "Name",
  }),
  columnHelper.accessor("active", {
    header: "Active",
    cell: (info) => (info.getValue() ? "Yes" : "No"),
  }),
]

function UsersPage() {
  const { data, isLoading } = useUsers()

  const table = useReactTable({
    data: data ?? [],
    columns,
    getCoreRowModel: getCoreRowModel(),
  })

  if (isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-64 w-full" />
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <PageHeader title={m.page_title_users()} />
      <DataTable
        table={table}
        emptyTitle={m.empty_state_title({ entity: "users" })}
      />
    </div>
  )
}
