import { createFileRoute } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { useCallback, useMemo, useState } from "react"
import type { ColumnDef } from "@tanstack/react-table"
import type { Wave } from "@/shared/api/waves"
import { waveQueries } from "@/shared/api/waves"
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
    key: "orderGrouping",
    label: "Order Grouping",
    options: [
      { label: "Single", value: "Single" },
      { label: "Multi", value: "Multi" },
    ],
    type: "select",
  },
  {
    key: "state",
    label: "State",
    options: [
      { label: "Planned", value: "Planned" },
      { label: "Released", value: "Released" },
      { label: "Completed", value: "Completed" },
      { label: "Cancelled", value: "Cancelled" },
    ],
    type: "select",
  },
]

export const Route = createFileRoute("/_authenticated/waves/")({
  component: WavesPage,
  loader: ({ context }) =>
    context.queryClient.ensureQueryData(waveQueries.all()),
})

function WavesPage() {
  const { data: waves } = useSuspenseQuery(waveQueries.all())
  const [data, setData] = useState(waves)
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

  const columns = useMemo<ColumnDef<Wave>[]>(
    () => [
      getDataGridSelectColumn<Wave>({
        detailHref: (row) => `/waves/${row.original.id}`,
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
        accessorKey: "orderGrouping",
        cell: ({ row }) => (
          <Badge variant="secondary">
            {row.original.orderGrouping}
          </Badge>
        ),
        header: "Order Grouping",
        meta: {
          cell: {
            options: [
              { label: "Single", value: "Single" },
              { label: "Multi", value: "Multi" },
            ],
            variant: "select" as const,
          },
          label: "Order Grouping",
        },
        size: 150,
      },
      {
        accessorKey: "orderCount",
        cell: ({ row }) => (
          <span className="font-mono text-xs">
            {row.original.orderCount}
          </span>
        ),
        header: "Order Count",
        meta: {
          cell: { variant: "number" as const },
          label: "Order Count",
        },
        size: 120,
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
              { label: "Released", value: "Released" },
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
        accessorKey: "createdAt",
        header: "Created At",
        meta: {
          cell: { variant: "short-text" as const },
          label: "Created At",
        },
        size: 180,
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
        description="Wave planning and release management"
        title="Waves"
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
