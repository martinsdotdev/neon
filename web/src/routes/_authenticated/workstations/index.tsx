import { createFileRoute } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { useCallback, useMemo, useState } from "react"
import type { ColumnDef } from "@tanstack/react-table"
import type { Workstation } from "@/shared/api/workstations"
import { workstationQueries } from "@/shared/api/workstations"
import { DataGrid } from "@/shared/data-grid/data-grid"
import { DataGridRowHeightMenu } from "@/shared/data-grid/data-grid-row-height-menu"
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
    key: "mode",
    label: "Mode",
    options: [
      { label: "Receiving", value: "Receiving" },
      { label: "Picking", value: "Picking" },
      { label: "Counting", value: "Counting" },
      { label: "Relocation", value: "Relocation" },
    ],
    type: "select",
  },
  {
    key: "state",
    label: "State",
    options: [
      { label: "Disabled", value: "Disabled" },
      { label: "Idle", value: "Idle" },
      { label: "Active", value: "Active" },
    ],
    type: "select",
  },
  {
    key: "workstationType",
    label: "Type",
    options: [
      { label: "PutWall", value: "PutWall" },
      { label: "PackStation", value: "PackStation" },
    ],
    type: "select",
  },
]

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

  const columns = useMemo<ColumnDef<Workstation>[]>(
    () => [
      getDataGridSelectColumn<Workstation>({
        detailHref: (row) => `/workstations/${row.original.id}`,
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
    data: filteredData,
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
    </div>
  )
}
