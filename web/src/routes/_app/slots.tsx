import {
  createColumnHelper,
  getCoreRowModel,
  useReactTable,
} from "@tanstack/react-table"
import { createFileRoute } from "@tanstack/react-router"

import type { Slot } from "@/shared/types/slot"
import { useSlots } from "@/entities/slot/query-hooks/use-slots"
import { DataTable } from "@/shared/components/data-table"
import { PageHeader } from "@/shared/components/page-header"
import { StatusBadge } from "@/shared/components/status-badge"
import { Skeleton } from "@/components/ui/skeleton"
import { m } from "@/paraglide/messages.js"

export const Route = createFileRoute("/_app/slots")({
  component: SlotsPage,
})

const columnHelper = createColumnHelper<Slot>()

const columns = [
  columnHelper.accessor("id", {
    header: () => m.column_id(),
    cell: (info) => (
      <span className="font-mono text-xs">
        {info.getValue().slice(0, 8)}
      </span>
    ),
  }),
  columnHelper.accessor("status", {
    header: () => m.column_status(),
    cell: (info) => <StatusBadge status={info.getValue()} />,
  }),
  columnHelper.accessor("workstationId", {
    header: "Workstation",
    cell: (info) => (
      <span className="font-mono text-xs">
        {info.getValue().slice(0, 8)}
      </span>
    ),
  }),
  columnHelper.accessor("orderId", {
    header: "Order",
    cell: (info) => (
      <span className="font-mono text-xs">
        {info.getValue()?.slice(0, 8) ?? "-"}
      </span>
    ),
  }),
]

function SlotsPage() {
  const { data, isLoading } = useSlots()

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
      <PageHeader title={m.page_title_slots()} />
      <DataTable
        table={table}
        emptyTitle={m.empty_state_title({ entity: "slots" })}
      />
    </div>
  )
}
