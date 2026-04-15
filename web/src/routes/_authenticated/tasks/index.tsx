import { createFileRoute } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { useCallback, useMemo, useState } from "react"
import type { ColumnDef } from "@tanstack/react-table"
import type { Task } from "@/shared/api/tasks"
import { taskQueries } from "@/shared/api/tasks"
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
import { StateBadge } from "@/shared/ui/state-badge"

const filterFields: FilterFieldConfig[] = [
  {
    key: "id",
    label: "ID",
    type: "text",
  },
  {
    key: "skuId",
    label: "SKU",
    type: "text",
  },
  {
    key: "state",
    label: "State",
    options: [
      { label: "Planned", value: "Planned" },
      { label: "Allocated", value: "Allocated" },
      { label: "Assigned", value: "Assigned" },
      { label: "Completed", value: "Completed" },
      { label: "Cancelled", value: "Cancelled" },
    ],
    type: "select",
  },
  {
    key: "taskType",
    label: "Type",
    options: [
      { label: "Pick", value: "Pick" },
      { label: "Putaway", value: "Putaway" },
      { label: "Replenish", value: "Replenish" },
      { label: "Transfer", value: "Transfer" },
    ],
    type: "select",
  },
]

export const Route = createFileRoute("/_authenticated/tasks/")({
  component: TasksPage,
  loader: ({ context }) =>
    context.queryClient.ensureQueryData(taskQueries.all()),
})

function TasksPage() {
  const { data: tasks } = useSuspenseQuery(taskQueries.all())
  const [data, setData] = useState(tasks)
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

  const columns = useMemo<ColumnDef<Task>[]>(
    () => [
      getDataGridSelectColumn<Task>({
        detailHref: (row) => `/tasks/${row.original.id}`,
        enableRowMarkers: true,
      }),
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
    data: filteredData,
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
