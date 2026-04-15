import { createFileRoute } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { useMemo, useState } from "react"
import type { ColumnDef } from "@tanstack/react-table"
import type { Location } from "@/shared/api/locations"
import { locationQueries } from "@/shared/api/locations"
import { DataGrid } from "@/shared/data-grid/data-grid"
import { DataGridFilterMenu } from "@/shared/data-grid/data-grid-filter-menu"
import { DataGridRowHeightMenu } from "@/shared/data-grid/data-grid-row-height-menu"
import { getDataGridSelectColumn } from "@/shared/data-grid/data-grid-select-column"
import { DataGridSortMenu } from "@/shared/data-grid/data-grid-sort-menu"
import { DataGridViewMenu } from "@/shared/data-grid/data-grid-view-menu"
import { useDataGrid } from "@/shared/hooks/use-data-grid"
import { Badge } from "@/shared/ui/badge"
import { PageHeader } from "@/shared/ui/page-header"

export const Route = createFileRoute("/_authenticated/locations/")({
  component: LocationsPage,
  loader: ({ context }) =>
    context.queryClient.ensureQueryData(locationQueries.all()),
})

function LocationsPage() {
  const { data: locations } = useSuspenseQuery(locationQueries.all())
  const [data, setData] = useState(locations)

  const columns = useMemo<ColumnDef<Location>[]>(
    () => [
      getDataGridSelectColumn({ readOnly: true }),
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
        size: 140,
      },
      {
        accessorKey: "locationType",
        cell: ({ row }) => (
          <Badge variant="secondary">
            {row.original.locationType}
          </Badge>
        ),
        header: "Type",
        meta: {
          cell: {
            options: [
              { label: "Pick", value: "Pick" },
              { label: "Reserve", value: "Reserve" },
              { label: "Buffer", value: "Buffer" },
              { label: "Staging", value: "Staging" },
              { label: "Packing", value: "Packing" },
              { label: "Dock", value: "Dock" },
            ],
            variant: "select" as const,
          },
          label: "Type",
        },
        size: 120,
      },
      {
        accessorKey: "zoneId",
        cell: ({ row }) => (
          <span className="font-mono text-xs text-muted-foreground">
            {row.original.zoneId ?? "\u2014"}
          </span>
        ),
        header: "Zone",
        meta: {
          cell: { variant: "short-text" as const },
          label: "Zone",
        },
        size: 120,
      },
      {
        accessorKey: "pickingSequence",
        cell: ({ row }) => (
          <span className="font-mono text-xs text-muted-foreground">
            {row.original.pickingSequence ?? "\u2014"}
          </span>
        ),
        header: "Pick Seq",
        meta: {
          cell: { variant: "number" as const },
          label: "Pick Seq",
        },
        size: 100,
      },
    ],
    [],
  )

  const gridProps = useDataGrid({
    columns,
    data,
    enableSearch: true,
    onDataChange: setData,
    readOnly: true,
    rowHeight: "short",
  })

  return (
    <div>
      <PageHeader
        description="Warehouse locations by zone and type"
        title="Locations"
      />
      <div className="flex items-center gap-2 pb-2">
        <DataGridFilterMenu table={gridProps.table} />
        <DataGridSortMenu table={gridProps.table} />
        <DataGridRowHeightMenu table={gridProps.table} />
        <DataGridViewMenu table={gridProps.table} />
      </div>
      <DataGrid {...gridProps} height={500} />
    </div>
  )
}
