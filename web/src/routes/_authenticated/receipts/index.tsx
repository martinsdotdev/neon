import { createFileRoute } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { useMemo, useState } from "react"
import type { ColumnDef } from "@tanstack/react-table"
import type { Receipt } from "@/shared/api/receipts"
import { receiptQueries } from "@/shared/api/receipts"
import { DataGrid } from "@/shared/data-grid/data-grid"
import { DataGridFilterMenu } from "@/shared/data-grid/data-grid-filter-menu"
import { DataGridRowHeightMenu } from "@/shared/data-grid/data-grid-row-height-menu"
import { getDataGridSelectColumn } from "@/shared/data-grid/data-grid-select-column"
import { DataGridSortMenu } from "@/shared/data-grid/data-grid-sort-menu"
import { DataGridViewMenu } from "@/shared/data-grid/data-grid-view-menu"
import { useDataGrid } from "@/shared/hooks/use-data-grid"
import { PageHeader } from "@/shared/ui/page-header"
import { StateBadge } from "@/shared/ui/state-badge"

export const Route = createFileRoute("/_authenticated/receipts/")({
  component: ReceiptsPage,
  loader: ({ context }) =>
    context.queryClient.ensureQueryData(receiptQueries.all()),
})

function ReceiptsPage() {
  const { data: receipts } = useSuspenseQuery(receiptQueries.all())
  const [data, setData] = useState(receipts)

  const columns = useMemo<ColumnDef<Receipt>[]>(
    () => [
      getDataGridSelectColumn({ readOnly: true }),
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
        accessorKey: "deliveryId",
        cell: ({ row }) => (
          <span className="font-mono text-xs font-medium">
            {row.original.deliveryId}
          </span>
        ),
        header: "Delivery",
        meta: {
          cell: { variant: "short-text" as const },
          label: "Delivery",
        },
        size: 150,
      },
      {
        accessorFn: (row) => row.lines.length,
        cell: ({ row }) => (
          <span className="font-mono text-xs">
            {row.original.lines.length}
          </span>
        ),
        header: "Lines",
        id: "lineCount",
        meta: {
          cell: { variant: "number" as const },
          label: "Lines",
        },
        size: 80,
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
              { label: "Open", value: "Open" },
              { label: "Confirmed", value: "Confirmed" },
            ],
            variant: "select" as const,
          },
          label: "State",
        },
        size: 120,
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
        description="Goods receipt recording and confirmation"
        title="Receipts"
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
