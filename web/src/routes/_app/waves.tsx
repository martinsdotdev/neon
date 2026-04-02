import {
  createColumnHelper,
  getCoreRowModel,
  useReactTable,
} from "@tanstack/react-table"
import { createFileRoute } from "@tanstack/react-router"

import type { Wave } from "@/shared/types/wave"
import { useWaves } from "@/entities/wave/query-hooks/use-waves"
import { DataTable } from "@/shared/components/data-table"
import { PageHeader } from "@/shared/components/page-header"
import { StatusBadge } from "@/shared/components/status-badge"
import { Skeleton } from "@/components/ui/skeleton"
import { m } from "@/paraglide/messages.js"

export const Route = createFileRoute("/_app/waves")({
  component: WavesPage,
})

const columnHelper = createColumnHelper<Wave>()

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
  columnHelper.accessor("orderGrouping", {
    header: "Grouping",
  }),
  columnHelper.accessor("orderIds", {
    header: "Orders",
    cell: (info) => info.getValue().length,
  }),
  columnHelper.accessor("createdAt", {
    header: () => m.column_created_at(),
    cell: (info) =>
      new Date(info.getValue()).toLocaleString(),
  }),
]

function WavesPage() {
  const { data, isLoading } = useWaves()

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
      <PageHeader title={m.page_title_waves()} />
      <DataTable
        table={table}
        emptyTitle={m.empty_state_title({ entity: "waves" })}
      />
    </div>
  )
}
