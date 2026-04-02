import {
  createColumnHelper,
  getCoreRowModel,
  useReactTable,
} from "@tanstack/react-table"
import { createFileRoute } from "@tanstack/react-router"

import type { Location } from "@/shared/types/location"
import { useLocations } from "@/entities/location/query-hooks/use-locations"
import { DataTable } from "@/shared/components/data-table"
import { PageHeader } from "@/shared/components/page-header"
import { Skeleton } from "@/components/ui/skeleton"
import { m } from "@/paraglide/messages.js"

export const Route = createFileRoute("/_app/locations")({
  component: LocationsPage,
})

const columnHelper = createColumnHelper<Location>()

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
  columnHelper.accessor("locationType", {
    header: "Type",
  }),
  columnHelper.accessor("pickingSequence", {
    header: "Picking Sequence",
    cell: (info) => info.getValue() ?? "-",
  }),
  columnHelper.accessor("zoneId", {
    header: "Zone",
    cell: (info) => (
      <span className="font-mono text-xs">
        {info.getValue()?.slice(0, 8) ?? "-"}
      </span>
    ),
  }),
]

function LocationsPage() {
  const { data, isLoading } = useLocations()

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
      <PageHeader title={m.page_title_locations()} />
      <DataTable
        table={table}
        emptyTitle={m.empty_state_title({
          entity: "locations",
        })}
      />
    </div>
  )
}
