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
import { carrierQueries, type Carrier } from "@/shared/api/carriers"
import { Badge } from "@/shared/ui/badge"
import { PageHeader } from "@/shared/ui/page-header"
import { DataGrid } from "@/shared/reui/data-grid/data-grid"
import { DataGridTable } from "@/shared/reui/data-grid/data-grid-table"
import { DataGridColumnHeader } from "@/shared/reui/data-grid/data-grid-column-header"
import { DataGridPagination } from "@/shared/reui/data-grid/data-grid-pagination"

export const Route = createFileRoute("/_authenticated/carriers/")({
  component: CarriersPage,
  loader: ({ context }) =>
    context.queryClient.ensureQueryData(carrierQueries.all()),
})

function CarriersPage() {
  const { data: carriers } = useSuspenseQuery(carrierQueries.all())
  const navigate = useNavigate()
  const [sorting, setSorting] = useState<SortingState>([])
  const [pagination, setPagination] = useState<PaginationState>({
    pageIndex: 0,
    pageSize: 20,
  })

  const columns = useMemo<ColumnDef<Carrier>[]>(
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
        size: 120,
      },
      {
        accessorKey: "name",
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="Name" />
        ),
      },
      {
        accessorKey: "active",
        cell: ({ row }) => (
          <Badge variant={row.original.active ? "default" : "secondary"}>
            {row.original.active ? "Active" : "Inactive"}
          </Badge>
        ),
        enableSorting: true,
        header: ({ column }) => (
          <DataGridColumnHeader column={column} title="Status" />
        ),
        size: 100,
      },
    ],
    [],
  )

  const table = useReactTable({
    columns,
    data: carriers,
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
        description="Shipping carriers for outbound fulfillment"
        title="Carriers"
      />
      <DataGrid
        onRowClick={(carrier) =>
          navigate({
            params: { carrierId: carrier.id },
            to: "/carriers/$carrierId",
          })
        }
        recordCount={carriers.length}
        table={table}
        tableLayout={{
          headerSticky: true,
        }}
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
