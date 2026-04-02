import {
  createColumnHelper,
  getCoreRowModel,
  useReactTable,
} from "@tanstack/react-table"
import { createFileRoute } from "@tanstack/react-router"

import type { Carrier } from "@/shared/types/carrier"
import { useCarriers } from "@/entities/carrier/query-hooks/use-carriers"
import { DataTable } from "@/shared/components/data-table"
import { PageHeader } from "@/shared/components/page-header"
import { Skeleton } from "@/components/ui/skeleton"
import { m } from "@/paraglide/messages.js"

export const Route = createFileRoute("/_app/carriers")({
  component: CarriersPage,
})

const columnHelper = createColumnHelper<Carrier>()

const columns = [
  columnHelper.accessor("id", {
    header: () => m.column_id(),
    cell: (info) => (
      <span className="font-mono text-xs">
        {info.getValue().slice(0, 8)}
      </span>
    ),
  }),
  columnHelper.accessor("code", {
    header: "Code",
  }),
  columnHelper.accessor("name", {
    header: "Name",
  }),
  columnHelper.accessor("active", {
    header: "Active",
    cell: (info) => (info.getValue() ? "Yes" : "No"),
  }),
]

function CarriersPage() {
  const { data, isLoading } = useCarriers()

  const table = useReactTable({
    data: data ?? [],
    columns,
    getCoreRowModel: getCoreRowModel(),
  })

  if (isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-64 w-full" />
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <PageHeader title={m.page_title_carriers()} />
      <DataTable
        table={table}
        emptyTitle={m.empty_state_title({
          entity: "carriers",
        })}
      />
    </div>
  )
}
