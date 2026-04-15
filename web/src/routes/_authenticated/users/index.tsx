import { createFileRoute } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { useCallback, useMemo, useState } from "react"
import type { ColumnDef } from "@tanstack/react-table"
import type { User } from "@/shared/api/users-api"
import { userQueries } from "@/shared/api/users-api"
import { DataGrid } from "@/shared/data-grid/data-grid"
import { DataGridRowHeightMenu } from "@/shared/data-grid/data-grid-row-height-menu"
import { DataGridSelectionBar } from "@/shared/data-grid/data-grid-selection-bar"
import { getDataGridSelectColumn } from "@/shared/data-grid/data-grid-select-column"
import { DataGridSortMenu } from "@/shared/data-grid/data-grid-sort-menu"
import { DataGridViewMenu } from "@/shared/data-grid/data-grid-view-menu"
import { useDataGrid } from "@/shared/hooks/use-data-grid"
import type { Filter, FilterFieldConfig } from "@/shared/reui/filters"
import { Filters } from "@/shared/reui/filters"
import { Badge } from "@/shared/ui/badge"
import { PageHeader } from "@/shared/ui/page-header"

const filterFields: FilterFieldConfig[] = [
  {
    key: "login",
    label: "Login",
    type: "text",
  },
  {
    key: "name",
    label: "Name",
    type: "text",
  },
  {
    key: "role",
    label: "Role",
    options: [
      { label: "Admin", value: "Admin" },
      { label: "Supervisor", value: "Supervisor" },
      { label: "Operator", value: "Operator" },
      { label: "Viewer", value: "Viewer" },
    ],
    type: "select",
  },
]

export const Route = createFileRoute("/_authenticated/users/")({
  component: UsersPage,
  loader: ({ context }) =>
    context.queryClient.ensureQueryData(userQueries.all()),
})

function UsersPage() {
  const { data: users } = useSuspenseQuery(userQueries.all())
  const [data, setData] = useState(users)
  const [filters, setFilters] = useState<Filter[]>([])

  const filteredData = useMemo(() => {
    if (filters.length === 0) return data
    return data.filter((row) => {
      for (const f of filters) {
        const value = String((row as Record<string, unknown>)[f.field] ?? "")
        if (f.operator === "is" || f.operator === "is_any_of") {
          if (!f.values.some((v) => value === String(v))) return false
        } else if (f.operator === "contains") {
          if (!f.values.some((v) => value.toLowerCase().includes(String(v).toLowerCase()))) return false
        }
      }
      return true
    })
  }, [data, filters])

  const onFiltersChange = useCallback((newFilters: Filter[]) => {
    setFilters(newFilters)
  }, [])

  const columns = useMemo<ColumnDef<User>[]>(
    () => [
      getDataGridSelectColumn<User>({
        detailHref: (row) => `/users/${row.original.id}`,
        enableRowMarkers: true,
      }),
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
        cell: ({ row }) => (
          <span className="font-mono text-xs font-medium">
            {row.original.login}
          </span>
        ),
        header: "Login",
        meta: {
          cell: { variant: "short-text" as const },
          label: "Login",
        },
        size: 140,
      },
      {
        accessorKey: "role",
        cell: ({ row }) => (
          <Badge variant="secondary">{row.original.role}</Badge>
        ),
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
        cell: ({ row }) => (
          <Badge
            variant={
              row.original.active ? "default" : "secondary"
            }
          >
            {row.original.active ? "Active" : "Inactive"}
          </Badge>
        ),
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
    data: filteredData,
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
        <Filters
          fields={filterFields}
          filters={filters}
          onChange={onFiltersChange}
          size="sm"
        />
        <div className="ml-auto flex items-center gap-2">
          <DataGridSortMenu table={gridProps.table} />
          <DataGridRowHeightMenu table={gridProps.table} />
          <DataGridViewMenu table={gridProps.table} />
        </div>
      </div>
      <DataGrid {...gridProps} height={500} />
      <DataGridSelectionBar table={gridProps.table} />
    </div>
  )
}
