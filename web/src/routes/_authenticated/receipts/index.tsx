import { createFileRoute } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { useCallback, useMemo, useState } from "react"
import type { ColumnDef } from "@tanstack/react-table"
import type { Receipt } from "@/shared/api/receipts"
import { receiptQueries } from "@/shared/api/receipts"
import { DataGrid } from "@/shared/data-grid/data-grid"
import { DataGridRowHeightMenu } from "@/shared/data-grid/data-grid-row-height-menu"
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
    key: "deliveryId",
    label: "Delivery",
    type: "text",
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
      { label: "Open", value: "Open" },
      { label: "Confirmed", value: "Confirmed" },
    ],
    type: "select",
  },
]

export const Route = createFileRoute("/_authenticated/receipts/")({
  component: ReceiptsPage,
  loader: ({ context }) =>
    context.queryClient.ensureQueryData(receiptQueries.all()),
})

function ReceiptsPage() {
  const { data: receipts } = useSuspenseQuery(receiptQueries.all())
  const [data, setData] = useState(receipts)
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

  const columns = useMemo<ColumnDef<Receipt>[]>(
    () => [
      getDataGridSelectColumn({ enableRowMarkers: true }),
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
        accessorKey: "deliveryId",
        cell: ({ row }) => (
          <span className="font-mono text-xs font-medium">
            {row.original.deliveryId}
          </span>
        ),
        header: "Delivery",
        meta: {
          cell: { variant: "short-text" as const },
          label: "Delivery",
        },
        size: 150,
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
        accessorKey: "state",
        cell: ({ row }) => (
          <StateBadge state={row.original.state} />
        ),
        header: "State",
        meta: {
          cell: {
            options: [
              { label: "Open", value: "Open" },
              { label: "Confirmed", value: "Confirmed" },
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
        description="Goods receipt recording and confirmation"
        title="Receipts"
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
