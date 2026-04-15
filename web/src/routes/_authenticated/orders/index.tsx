import { createFileRoute } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { useCallback, useMemo, useState } from "react"
import type { ColumnDef } from "@tanstack/react-table"
import type { Order } from "@/shared/api/orders"
import { orderQueries } from "@/shared/api/orders"
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
    key: "id",
    label: "ID",
    type: "text",
  },
  {
    key: "priority",
    label: "Priority",
    options: [
      { label: "Low", value: "Low" },
      { label: "Normal", value: "Normal" },
      { label: "High", value: "High" },
      { label: "Critical", value: "Critical" },
    ],
    type: "select",
  },
]

export const Route = createFileRoute("/_authenticated/orders/")({
  component: OrdersPage,
  loader: ({ context }) =>
    context.queryClient.ensureQueryData(orderQueries.all()),
})

function OrdersPage() {
  const { data: orders } = useSuspenseQuery(orderQueries.all())
  const [data, setData] = useState(orders)
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

  const columns = useMemo<ColumnDef<Order>[]>(
    () => [
      getDataGridSelectColumn<Order>({
        detailHref: (row) => `/orders/${row.original.id}`,
        enableRowMarkers: true,
      }),
      {
        accessorKey: "id",
        cell: ({ row }) => (
          <span className="font-mono text-xs font-medium">
            {row.original.id}
          </span>
        ),
        header: "Order ID",
        meta: {
          cell: { variant: "short-text" as const },
          label: "Order ID",
        },
        size: 140,
      },
      {
        accessorKey: "priority",
        cell: ({ row }) => {
          const v = row.original.priority
          const variantMap: Record<string, "destructive" | "default" | "secondary"> = {
            Critical: "destructive",
            High: "default",
          }
          return (
            <Badge variant={variantMap[v] ?? "secondary"}>
              {v}
            </Badge>
          )
        },
        header: "Priority",
        meta: {
          cell: {
            options: [
              { label: "Low", value: "Low" },
              { label: "Normal", value: "Normal" },
              { label: "High", value: "High" },
              { label: "Critical", value: "Critical" },
            ],
            variant: "select" as const,
          },
          label: "Priority",
        },
        size: 120,
      },
      {
        accessorFn: (row) => row.lines.length,
        cell: ({ row }) => (
          <span className="font-mono text-xs">
            {row.original.lines.length}
          </span>
        ),
        header: "Lines",
        id: "lineCount",
        meta: {
          cell: { variant: "number" as const },
          label: "Lines",
        },
        size: 80,
      },
      {
        accessorKey: "carrierId",
        cell: ({ row }) => (
          <span className="font-mono text-xs text-muted-foreground">
            {row.original.carrierId ?? "\u2014"}
          </span>
        ),
        header: "Carrier",
        meta: {
          cell: { variant: "short-text" as const },
          label: "Carrier",
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
        description="Customer orders for fulfillment"
        title="Orders"
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
