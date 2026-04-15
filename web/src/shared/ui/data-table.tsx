"use client"

import {
  type ColumnDef,
  type SortingState,
  type PaginationState,
  type OnChangeFn,
  flexRender,
  getCoreRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  useReactTable,
} from "@tanstack/react-table"
import { ArrowDown, ArrowUp, ArrowUpDown } from "lucide-react"
import { useState } from "react"
import { cn } from "@/shared/lib/utils"
import { Button } from "@/shared/ui/button"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/shared/ui/table"

// ---------------------------------------------------------------------------
// Column header with sort toggle
// ---------------------------------------------------------------------------

const DataTableColumnHeader = <TData, TValue>({
  column,
  title,
  className,
}: {
  column: import("@tanstack/react-table").Column<TData, TValue>
  title: string
  className?: string
}) => {
  if (!column.getCanSort()) {
    return <span className={className}>{title}</span>
  }

  const sorted = column.getIsSorted()

  return (
    <button
      type="button"
      className={cn(
        "text-muted-foreground hover:text-foreground -ml-2 inline-flex items-center gap-1 rounded px-2 py-1 text-xs font-medium transition-colors",
        sorted && "text-foreground",
        className,
      )}
      onClick={column.getToggleSortingHandler()}
    >
      {title}
      {sorted === "asc" && <ArrowUp className="size-3.5" />}
      {sorted === "desc" && <ArrowDown className="size-3.5" />}
      {!sorted && <ArrowUpDown className="size-3.5 opacity-40" />}
    </button>
  )
}

// ---------------------------------------------------------------------------
// Pagination
// ---------------------------------------------------------------------------

const DataTablePagination = <TData,>({
  table,
}: {
  table: import("@tanstack/react-table").Table<TData>
}) => (
  <div className="flex items-center justify-between gap-4 pt-4">
    <p className="text-muted-foreground font-mono text-xs">
      {table.getFilteredRowModel().rows.length} records
    </p>
    <div className="flex items-center gap-1">
      <Button
        disabled={!table.getCanPreviousPage()}
        size="sm"
        variant="outline"
        onClick={() => table.previousPage()}
      >
        Previous
      </Button>
      <span className="text-muted-foreground px-3 font-mono text-xs">
        {table.getState().pagination.pageIndex + 1} / {table.getPageCount()}
      </span>
      <Button
        disabled={!table.getCanNextPage()}
        size="sm"
        variant="outline"
        onClick={() => table.nextPage()}
      >
        Next
      </Button>
    </div>
  </div>
)

// ---------------------------------------------------------------------------
// Main data table
// ---------------------------------------------------------------------------

interface DataTableProps<TData, TValue> {
  columns: ColumnDef<TData, TValue>[]
  data: TData[]
  onRowClick?: (row: TData) => void
  pageSize?: number
}

const DataTable = <TData, TValue>({
  columns,
  data,
  onRowClick,
  pageSize = 20,
}: DataTableProps<TData, TValue>) => {
  const [sorting, setSorting] = useState<SortingState>([])
  const [pagination, setPagination] = useState<PaginationState>({
    pageIndex: 0,
    pageSize,
  })

  const table = useReactTable({
    columns,
    data,
    getCoreRowModel: getCoreRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
    getSortedRowModel: getSortedRowModel(),
    onPaginationChange: setPagination as OnChangeFn<PaginationState>,
    onSortingChange: setSorting as OnChangeFn<SortingState>,
    state: { pagination, sorting },
  })

  return (
    <div>
      <div className="rounded-lg border">
        <Table>
          <TableHeader>
            {table.getHeaderGroups().map((headerGroup) => (
              <TableRow key={headerGroup.id}>
                {headerGroup.headers.map((header) => (
                  <TableHead key={header.id}>
                    {header.isPlaceholder
                      ? null
                      : flexRender(
                          header.column.columnDef.header,
                          header.getContext(),
                        )}
                  </TableHead>
                ))}
              </TableRow>
            ))}
          </TableHeader>
          <TableBody>
            {table.getRowModel().rows.length > 0 ? (
              table.getRowModel().rows.map((row) => (
                <TableRow
                  key={row.id}
                  className={onRowClick ? "cursor-pointer" : ""}
                  onClick={() => onRowClick?.(row.original)}
                >
                  {row.getVisibleCells().map((cell) => (
                    <TableCell key={cell.id}>
                      {flexRender(
                        cell.column.columnDef.cell,
                        cell.getContext(),
                      )}
                    </TableCell>
                  ))}
                </TableRow>
              ))
            ) : (
              <TableRow>
                <TableCell
                  className="text-muted-foreground h-32 text-center"
                  colSpan={columns.length}
                >
                  No results
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>
      {table.getPageCount() > 1 && <DataTablePagination table={table} />}
    </div>
  )
}

export { DataTable, DataTableColumnHeader }
export type { DataTableProps }
