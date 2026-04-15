import { createFileRoute } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { useCallback, useMemo, useState } from "react"
import type { ColumnDef } from "@tanstack/react-table"
import type { Sku } from "@/shared/api/skus"
import { skuQueries } from "@/shared/api/skus"
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
    key: "code",
    label: "Code",
    type: "text",
  },
  {
    key: "description",
    label: "Description",
    type: "text",
  },
]

export const Route = createFileRoute("/_authenticated/skus/")({
  component: SkusPage,
  loader: ({ context }) =>
    context.queryClient.ensureQueryData(skuQueries.all()),
})

function SkusPage() {
  const { data: skus } = useSuspenseQuery(skuQueries.all())
  const [data, setData] = useState(skus)
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

  const columns = useMemo<ColumnDef<Sku>[]>(
    () => [
      getDataGridSelectColumn<Sku>({
        detailHref: (row) => `/skus/${row.original.id}`,
        enableRowMarkers: true,
      }),
      {
        accessorKey: "code",
        cell: ({ row }) => (
          <span className="font-mono text-xs font-medium">
            {row.original.code}
          </span>
        ),
        header: "Code",
        meta: {
          cell: { variant: "short-text" as const },
          label: "Code",
        },
        size: 160,
      },
      {
        accessorKey: "description",
        header: "Description",
        meta: {
          cell: { variant: "short-text" as const },
          label: "Description",
        },
        size: 300,
      },
      {
        accessorKey: "lotManaged",
        cell: ({ row }) => (
          <Badge
            variant={
              row.original.lotManaged ? "default" : "secondary"
            }
          >
            {row.original.lotManaged ? "Lot-managed" : "No"}
          </Badge>
        ),
        header: "Lot Managed",
        meta: {
          cell: { variant: "checkbox" as const },
          label: "Lot Managed",
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
        description="Stock keeping units in the warehouse"
        title="SKUs"
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
