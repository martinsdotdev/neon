import { createFileRoute, useNavigate } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import type { ColumnDef } from "@tanstack/react-table"
import {
  getCoreRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  useReactTable,
} from "@tanstack/react-table"
import type { PaginationState, SortingState } from "@tanstack/react-table"
import { useMemo, useState } from "react"
import { locationQueries, type Location } from "@/shared/api/locations"
import { Badge } from "@/shared/ui/badge"
import { PageHeader } from "@/shared/ui/page-header"
import { DataGrid } from "@/shared/reui/data-grid/data-grid"
import { DataGridTable } from "@/shared/reui/data-grid/data-grid-table"
import { DataGridColumnHeader } from "@/shared/reui/data-grid/data-grid-column-header"
import { DataGridPagination } from "@/shared/reui/data-grid/data-grid-pagination"

export const Route = createFileRoute("/_authenticated/locations/")({
  component: LocationsPage,
  loader: ({ context }) =>
    context.queryClient.ensureQueryData(locationQueries.all()),
})

function LocationsPage() {
  const { data: locations } = useSuspenseQuery(locationQueries.all())
  const navigate = useNavigate()
  const [sorting, setSorting] = useState<SortingState>([])
  const [pagination, setPagination] = useState<PaginationState>({
    pageIndex: 0,
    pageSize: 20,
  })

  const columns = useMemo<ColumnDef<Location>[]>(
    () => [
      {
        accessorKey: "code",
        cell: ({ row }) => (
          <span className="font-mono text-xs font-medium">
            {row.original.code}
          </span>
        ),
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="Code" />
        ),
        size: 140,
      },
      {
        accessorKey: "locationType",
        cell: ({ row }) => (
          <Badge variant="secondary">{row.original.locationType}</Badge>
        ),
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="Type" />
        ),
        size: 120,
      },
      {
        accessorKey: "zoneId",
        cell: ({ row }) => (
          <span className="text-muted-foreground font-mono text-xs">
            {row.original.zoneId ?? "\u2014"}
          </span>
        ),
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="Zone" />
        ),
        size: 120,
      },
      {
        accessorKey: "pickingSequence",
        cell: ({ row }) => (
          <span className="font-mono text-xs">
            {row.original.pickingSequence ?? "\u2014"}
          </span>
        ),
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="Pick Seq" />
        ),
        size: 100,
      },
    ],
    [],
  )

  const table = useReactTable({
    columns,
    data: locations,
    getCoreRowModel: getCoreRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
    getRowId: (row) => row.id,
    getSortedRowModel: getSortedRowModel(),
    onPaginationChange: setPagination,
    onSortingChange: setSorting,
    state: { pagination, sorting },
  })

  return (
    <div>
      <PageHeader
        description="Warehouse locations by zone and type"
        title="Locations"
      />
      <DataGrid
        onRowClick={(location) =>
          navigate({
            params: { locationId: location.id },
            to: "/locations/$locationId",
          })
        }
        recordCount={locations.length}
        table={table}
        tableLayout={{ headerSticky: true }}
      >
        <div className="w-full space-y-2.5">
          <div className="rounded-lg border">
            <DataGridTable />
          </div>
          <DataGridPagination sizes={[10, 20, 50]} />
        </div>
      </DataGrid>
    </div>
  )
}
