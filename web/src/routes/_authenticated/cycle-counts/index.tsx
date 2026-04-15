import { createFileRoute } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { useCallback, useMemo, useState } from "react"
import type { ColumnDef } from "@tanstack/react-table"
import type { CycleCount } from "@/shared/api/cycle-counts"
import { cycleCountQueries } from "@/shared/api/cycle-counts"
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
    key: "countMethod",
    label: "Method",
    options: [
      { label: "Blind", value: "Blind" },
      { label: "Informed", value: "Informed" },
    ],
    type: "select",
  },
  {
    key: "countType",
    label: "Type",
    options: [
      { label: "Planned", value: "Planned" },
      { label: "Random", value: "Random" },
      { label: "Triggered", value: "Triggered" },
      { label: "Recount", value: "Recount" },
    ],
    type: "select",
  },
  {
    key: "id",
    label: "ID",
    type: "text",
  },
  {
    key: "state",
    label: "State",
    options: [
      { label: "Created", value: "Created" },
      { label: "InProgress", value: "InProgress" },
      { label: "Completed", value: "Completed" },
      { label: "Cancelled", value: "Cancelled" },
    ],
    type: "select",
  },
]

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

  const columns = useMemo<ColumnDef<CycleCount>[]>(
    () => [
      getDataGridSelectColumn<CycleCount>({
        detailHref: (row) => `/cycle-counts/${row.original.id}`,
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
    data: filteredData,
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
