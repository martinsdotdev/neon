import { createFileRoute } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { useCallback, useMemo, useState } from "react"
import type { ColumnDef } from "@tanstack/react-table"
import type { Carrier } from "@/shared/api/carriers"
import type { Filter, FilterFieldConfig } from "@/shared/reui/filters"
import { carrierQueries } from "@/shared/api/carriers"
import { DataGrid } from "@/shared/data-grid/data-grid"
import { DataGridRowHeightMenu } from "@/shared/data-grid/data-grid-row-height-menu"
import { DataGridSelectionBar } from "@/shared/data-grid/data-grid-selection-bar"
import { DataGridSortMenu } from "@/shared/data-grid/data-grid-sort-menu"
import { DataGridViewMenu } from "@/shared/data-grid/data-grid-view-menu"
import { getDataGridSelectColumn } from "@/shared/data-grid/data-grid-select-column"
import { useDataGrid } from "@/shared/hooks/use-data-grid"
import { Filters } from "@/shared/reui/filters"
import { Badge } from "@/shared/ui/badge"
import { PageHeader } from "@/shared/ui/page-header"

const filterFields: Array<FilterFieldConfig> = [
  {
    key: "code",
    label: "Code",
    type: "text",
  },
  {
    key: "name",
    label: "Name",
    type: "text",
  },
  {
    key: "active",
    label: "Status",
    options: [
      { label: "Active", value: "true" },
      { label: "Inactive", value: "false" },
    ],
    type: "select",
  },
]

export const Route = createFileRoute("/_authenticated/carriers/")({
  component: CarriersPage,
  loader: ({ context }) =>
    context.queryClient.ensureQueryData(carrierQueries.all()),
})

function CarriersPage() {
  const { data: carriers } = useSuspenseQuery(carrierQueries.all())
  const [data, setData] = useState(carriers)
  const [filters, setFilters] = useState<Array<Filter>>([])

  const filteredData = useMemo(() => {
    if (filters.length === 0) return data
    return data.filter((row) => {
      for (const f of filters) {
        const value = String(row[f.field as keyof Carrier] ?? "")
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

  const columns = useMemo<Array<ColumnDef<Carrier>>>(
    () => [
      getDataGridSelectColumn<Carrier>({
        detailHref: (row) => `/carriers/${row.original.id}`,
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
        meta: { cell: { variant: "short-text" as const }, label: "Code" },
        size: 120,
      },
      {
        accessorKey: "name",
        header: "Name",
        meta: { cell: { variant: "short-text" as const }, label: "Name" },
        size: 250,
      },
      {
        accessorKey: "active",
        cell: ({ row }) => (
          <Badge variant={row.original.active ? "default" : "secondary"}>
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
        description="Shipping carriers for outbound fulfillment"
        title="Carriers"
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
