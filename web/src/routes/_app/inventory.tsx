import {
  createColumnHelper,
  getCoreRowModel,
  useReactTable,
} from "@tanstack/react-table"
import { createFileRoute } from "@tanstack/react-router"

import type { Inventory } from "@/shared/types/inventory"
import { useInventory } from "@/entities/inventory/query-hooks/use-inventory"
import { DataTable } from "@/shared/components/data-table"
import { PageHeader } from "@/shared/components/page-header"
import { Skeleton } from "@/components/ui/skeleton"
import { m } from "@/paraglide/messages.js"

export const Route = createFileRoute("/_app/inventory")({
  component: InventoryPage,
})

const columnHelper = createColumnHelper<Inventory>()

const columns = [
  columnHelper.accessor("id", {
    header: () => m.column_id(),
    cell: (info) => (
      <span className="font-mono text-xs">
        {info.getValue().slice(0, 8)}
      </span>
    ),
  }),
  columnHelper.accessor("locationId", {
    header: "Location",
    cell: (info) => (
      <span className="font-mono text-xs">
        {info.getValue().slice(0, 8)}
      </span>
    ),
  }),
  columnHelper.accessor("skuId", {
    header: "SKU",
    cell: (info) => (
      <span className="font-mono text-xs">
        {info.getValue().slice(0, 8)}
      </span>
    ),
  }),
  columnHelper.accessor("packagingLevel", {
    header: "Packaging Level",
  }),
  columnHelper.accessor("onHand", {
    header: "On Hand",
  }),
  columnHelper.accessor("reserved", {
    header: "Reserved",
  }),
  columnHelper.accessor("available", {
    header: "Available",
  }),
]

function InventoryPage() {
  const { data, isLoading } = useInventory()

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
      <PageHeader title={m.page_title_inventory()} />
      <DataTable
        table={table}
        emptyTitle={m.empty_state_title({
          entity: "inventory",
        })}
      />
    </div>
  )
}
