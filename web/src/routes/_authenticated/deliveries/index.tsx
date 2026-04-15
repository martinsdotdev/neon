import { createFileRoute } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { useCallback, useMemo, useState } from "react"
import type { ColumnDef } from "@tanstack/react-table"
import type { Delivery } from "@/shared/api/deliveries"
import { deliveryQueries } from "@/shared/api/deliveries"
import { DataGrid } from "@/shared/data-grid/data-grid"
import { DataGridRowHeightMenu } from "@/shared/data-grid/data-grid-row-height-menu"
import { DataGridSelectionBar } from "@/shared/data-grid/data-grid-selection-bar"
import { getDataGridSelectColumn } from "@/shared/data-grid/data-grid-select-column"
import { DataGridSortMenu } from "@/shared/data-grid/data-grid-sort-menu"
import { DataGridViewMenu } from "@/shared/data-grid/data-grid-view-menu"
import { useDataGrid } from "@/shared/hooks/use-data-grid"
import type { Filter, FilterFieldConfig } from "@/shared/reui/filters"
import { Filters } from "@/shared/reui/filters"
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
      { label: "Expected", value: "Expected" },
      { label: "PartiallyReceived", value: "PartiallyReceived" },
      { label: "Received", value: "Received" },
      { label: "Closed", value: "Closed" },
    ],
    type: "select",
  },
]

export const Route = createFileRoute("/_authenticated/deliveries/")({
  component: DeliveriesPage,
  loader: ({ context }) =>
    context.queryClient.ensureQueryData(deliveryQueries.all()),
})

function DeliveriesPage() {
  const { data: deliveries } = useSuspenseQuery(deliveryQueries.all())
  const [data, setData] = useState(deliveries)
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

  const columns = useMemo<ColumnDef<Delivery>[]>(
    () => [
      getDataGridSelectColumn<Delivery>({
        detailHref: (row) => `/deliveries/${row.original.id}`,
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
        accessorKey: "expectedQuantity",
        cell: ({ row }) => (
          <span className="font-mono text-xs">
            {row.original.expectedQuantity}
          </span>
        ),
        header: "Expected Qty",
        meta: {
          cell: { variant: "number" as const },
          label: "Expected Qty",
        },
        size: 130,
      },
      {
        accessorKey: "receivedQuantity",
        cell: ({ row }) => (
          <span className="font-mono text-xs">
            {row.original.receivedQuantity}
          </span>
        ),
        header: "Received Qty",
        meta: {
          cell: { variant: "number" as const },
          label: "Received Qty",
        },
        size: 130,
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
              { label: "Expected", value: "Expected" },
              {
                label: "PartiallyReceived",
                value: "PartiallyReceived",
              },
              { label: "Received", value: "Received" },
              { label: "Closed", value: "Closed" },
            ],
            variant: "select" as const,
          },
          label: "State",
        },
        size: 160,
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
        description="Inbound delivery scheduling and receiving"
        title="Deliveries"
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
