import {
  createColumnHelper,
  getCoreRowModel,
  useReactTable,
} from "@tanstack/react-table"
import { createFileRoute } from "@tanstack/react-router"

import type { Sku } from "@/shared/types/sku"
import { useSkus } from "@/entities/sku/query-hooks/use-skus"
import { DataTable } from "@/shared/components/data-table"
import { PageHeader } from "@/shared/components/page-header"
import { Skeleton } from "@/components/ui/skeleton"
import { m } from "@/paraglide/messages.js"

export const Route = createFileRoute("/_app/skus")({
  component: SkusPage,
})

const columnHelper = createColumnHelper<Sku>()

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
  columnHelper.accessor("description", {
    header: "Description",
  }),
  columnHelper.accessor("lotManaged", {
    header: "Lot Managed",
    cell: (info) => (info.getValue() ? "Yes" : "No"),
  }),
]

function SkusPage() {
  const { data, isLoading } = useSkus()

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
      <PageHeader title={m.page_title_skus()} />
      <DataTable
        table={table}
        emptyTitle={m.empty_state_title({ entity: "SKUs" })}
      />
    </div>
  )
}
