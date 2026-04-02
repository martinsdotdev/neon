import {
  createColumnHelper,
  getCoreRowModel,
  useReactTable,
} from "@tanstack/react-table"
import { createFileRoute } from "@tanstack/react-router"

import type { Workstation } from "@/shared/types/workstation"
import { useWorkstations } from "@/entities/workstation/query-hooks/use-workstations"
import { DataTable } from "@/shared/components/data-table"
import { PageHeader } from "@/shared/components/page-header"
import { StatusBadge } from "@/shared/components/status-badge"
import { Skeleton } from "@/components/ui/skeleton"
import { m } from "@/paraglide/messages.js"

export const Route = createFileRoute(
  "/_app/workstations",
)({
  component: WorkstationsPage,
})

const columnHelper = createColumnHelper<Workstation>()

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
  columnHelper.accessor("workstationType", {
    header: "Type",
  }),
  columnHelper.accessor("slotCount", {
    header: "Slots",
  }),
  columnHelper.accessor("consolidationGroupId", {
    header: "Consolidation Group",
    cell: (info) => (
      <span className="font-mono text-xs">
        {info.getValue()?.slice(0, 8) ?? "-"}
      </span>
    ),
  }),
]

function WorkstationsPage() {
  const { data, isLoading } = useWorkstations()

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
      <PageHeader title={m.page_title_workstations()} />
      <DataTable
        table={table}
        emptyTitle={m.empty_state_title({
          entity: "workstations",
        })}
      />
    </div>
  )
}
