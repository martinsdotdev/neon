import { createFileRoute } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { useMemo, useState } from "react"
import type { ColumnDef } from "@tanstack/react-table"
import type { User } from "@/shared/api/users-api"
import { userQueries } from "@/shared/api/users-api"
import { DataGrid } from "@/shared/data-grid/data-grid"
import { DataGridFilterMenu } from "@/shared/data-grid/data-grid-filter-menu"
import { DataGridRowHeightMenu } from "@/shared/data-grid/data-grid-row-height-menu"
import { getDataGridSelectColumn } from "@/shared/data-grid/data-grid-select-column"
import { DataGridSortMenu } from "@/shared/data-grid/data-grid-sort-menu"
import { DataGridViewMenu } from "@/shared/data-grid/data-grid-view-menu"
import { useDataGrid } from "@/shared/hooks/use-data-grid"
import { PageHeader } from "@/shared/ui/page-header"

export const Route = createFileRoute("/_authenticated/users/")({
  component: UsersPage,
  loader: ({ context }) =>
    context.queryClient.ensureQueryData(userQueries.all()),
})

function UsersPage() {
  const { data: users } = useSuspenseQuery(userQueries.all())
  const [data, setData] = useState(users)

  const columns = useMemo<ColumnDef<User>[]>(
    () => [
      getDataGridSelectColumn({ readOnly: true }),
      {
        accessorKey: "name",
        header: "Name",
        meta: {
          cell: { variant: "short-text" as const },
          label: "Name",
        },
        size: 200,
      },
      {
        accessorKey: "login",
        header: "Login",
        meta: {
          cell: { variant: "short-text" as const },
          label: "Login",
        },
        size: 140,
      },
      {
        accessorKey: "role",
        header: "Role",
        meta: {
          cell: {
            options: [
              { label: "Admin", value: "Admin" },
              { label: "Supervisor", value: "Supervisor" },
              { label: "Operator", value: "Operator" },
              { label: "Viewer", value: "Viewer" },
            ],
            variant: "select" as const,
          },
          label: "Role",
        },
        size: 120,
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
        description="Warehouse operators, supervisors, and administrators"
        title="Users"
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
