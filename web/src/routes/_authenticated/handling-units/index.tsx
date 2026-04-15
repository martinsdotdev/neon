import { createFileRoute } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { useMemo, useState } from "react"
import type { ColumnDef } from "@tanstack/react-table"
import type { HandlingUnit } from "@/shared/api/handling-units"
import { handlingUnitQueries } from "@/shared/api/handling-units"
import { DataGrid } from "@/shared/data-grid/data-grid"
import { DataGridFilterMenu } from "@/shared/data-grid/data-grid-filter-menu"
import { DataGridRowHeightMenu } from "@/shared/data-grid/data-grid-row-height-menu"
import { getDataGridSelectColumn } from "@/shared/data-grid/data-grid-select-column"
import { DataGridSortMenu } from "@/shared/data-grid/data-grid-sort-menu"
import { DataGridViewMenu } from "@/shared/data-grid/data-grid-view-menu"
import { useDataGrid } from "@/shared/hooks/use-data-grid"
import { Badge } from "@/shared/ui/badge"
import { PageHeader } from "@/shared/ui/page-header"
import { StateBadge } from "@/shared/ui/state-badge"

export const Route = createFileRoute(
  "/_authenticated/handling-units/",
)({
  component: HandlingUnitsPage,
  loader: ({ context }) =>
    context.queryClient.ensureQueryData(handlingUnitQueries.all()),
})

function HandlingUnitsPage() {
  const { data: units } = useSuspenseQuery(handlingUnitQueries.all())
  const [data, setData] = useState(units)

  const columns = useMemo<ColumnDef<HandlingUnit>[]>(
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
        accessorKey: "packagingLevel",
        cell: ({ row }) => (
          <Badge variant="secondary">
            {row.original.packagingLevel}
          </Badge>
        ),
        header: "Packaging Level",
        meta: {
          cell: {
            options: [
              { label: "Pallet", value: "Pallet" },
              { label: "Case", value: "Case" },
              { label: "InnerPack", value: "InnerPack" },
              { label: "Each", value: "Each" },
            ],
            variant: "select" as const,
          },
          label: "Packaging Level",
        },
        size: 150,
      },
      {
        accessorKey: "currentLocation",
        cell: ({ row }) => (
          <span className="font-mono text-xs text-muted-foreground">
            {row.original.currentLocation ?? "\u2014"}
          </span>
        ),
        header: "Location",
        meta: {
          cell: { variant: "short-text" as const },
          label: "Location",
        },
        size: 150,
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
              { label: "PickCreated", value: "PickCreated" },
              { label: "InBuffer", value: "InBuffer" },
              { label: "Empty", value: "Empty" },
              { label: "ShipCreated", value: "ShipCreated" },
              { label: "Packed", value: "Packed" },
              { label: "ReadyToShip", value: "ReadyToShip" },
              { label: "Shipped", value: "Shipped" },
            ],
            variant: "select" as const,
          },
          label: "State",
        },
        size: 150,
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
        description="Physical containers for picking and shipping"
        title="Handling Units"
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
