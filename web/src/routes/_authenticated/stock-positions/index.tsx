import { createFileRoute } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { useCallback, useMemo, useState } from "react"
import type { ColumnDef } from "@tanstack/react-table"
import type { StockPosition } from "@/shared/api/stock-positions"
import type { Filter, FilterFieldConfig } from "@/shared/reui/filters"
import { stockPositionQueries } from "@/shared/api/stock-positions"
import { DataGrid } from "@/shared/data-grid/data-grid"
import { DataGridRowHeightMenu } from "@/shared/data-grid/data-grid-row-height-menu"
import { DataGridSelectionBar } from "@/shared/data-grid/data-grid-selection-bar"
import { getDataGridSelectColumn } from "@/shared/data-grid/data-grid-select-column"
import { DataGridSortMenu } from "@/shared/data-grid/data-grid-sort-menu"
import { DataGridViewMenu } from "@/shared/data-grid/data-grid-view-menu"
import { useDataGrid } from "@/shared/hooks/use-data-grid"
import { Filters } from "@/shared/reui/filters"
import { PageHeader } from "@/shared/ui/page-header"

const filterFields: Array<FilterFieldConfig> = [
  {
    key: "skuId",
    label: "SKU",
    type: "text",
  },
  {
    key: "warehouseAreaId",
    label: "Area",
    type: "text",
  },
]

export const Route = createFileRoute("/_authenticated/stock-positions/")({
  component: StockPositionsPage,
  loader: ({ context }) =>
    context.queryClient.ensureQueryData(stockPositionQueries.all()),
})

function StockPositionsPage() {
  const { data: positions } = useSuspenseQuery(stockPositionQueries.all())
  const [data, setData] = useState(positions)
  const [filters, setFilters] = useState<Array<Filter>>([])

  const filteredData = useMemo(() => {
    if (filters.length === 0) return data
    return data.filter((row) => {
      for (const f of filters) {
        const value = String((row as Record<string, unknown>)[f.field] ?? "")
        if (f.operator === "is" || f.operator === "is_any_of") {
          if (!f.values.some((v) => value === String(v))) return false
        } else if (f.operator === "contains") {
          if (
            !f.values.some((v) =>
              value.toLowerCase().includes(String(v).toLowerCase())
            )
          )
            return false
        }
      }
      return true
    })
  }, [data, filters])

  const onFiltersChange = useCallback((newFilters: Array<Filter>) => {
    setFilters(newFilters)
  }, [])

  const columns = useMemo<Array<ColumnDef<StockPosition>>>(
    () => [
      getDataGridSelectColumn<StockPosition>({
        detailHref: (row) => `/stock-positions/${row.original.id}`,
        enableRowMarkers: true,
      }),
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
        accessorKey: "onHandQuantity",
        cell: ({ row }) => (
          <span className="font-mono text-xs">
            {row.original.onHandQuantity}
          </span>
        ),
        header: "On Hand",
        meta: {
          cell: { variant: "number" as const },
          label: "On Hand",
        },
        size: 120,
      },
      {
        accessorKey: "availableQuantity",
        cell: ({ row }) => (
          <span className="font-mono text-xs">
            {row.original.availableQuantity}
          </span>
        ),
        header: "Available",
        meta: {
          cell: { variant: "number" as const },
          label: "Available",
        },
        size: 120,
      },
      {
        accessorKey: "blockedQuantity",
        cell: ({ row }) => (
          <span className="font-mono text-xs">
            {row.original.blockedQuantity}
          </span>
        ),
        header: "Blocked",
        meta: {
          cell: { variant: "number" as const },
          label: "Blocked",
        },
        size: 120,
      },
    ],
    []
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
        description="Stock levels by SKU and warehouse area"
        title="Stock Positions"
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
