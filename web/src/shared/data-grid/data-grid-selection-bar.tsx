"use client"

import type { Table } from "@tanstack/react-table"
import { X } from "lucide-react"
import { cn } from "@/shared/lib/utils"

interface DataGridSelectionBarProps<TData> {
  className?: string
  actions?: React.ReactNode
  table: Table<TData>
}

const DataGridSelectionBar = <TData,>({
  className,
  actions,
  table,
}: DataGridSelectionBarProps<TData>) => {
  const selectedRows = table.getFilteredSelectedRowModel().rows
  const selectedCount = selectedRows.length
  const totalCount = table.getFilteredRowModel().rows.length

  if (selectedCount === 0) {
    return null
  }

  return (
    <div
      className={cn(
        "fixed bottom-6 left-1/2 z-50 -translate-x-1/2",
        "data-open:animate-in data-open:fade-in-0 data-open:slide-in-from-bottom-4",
        "duration-200",
        className,
      )}
      data-open=""
      role="status"
      aria-live="polite"
    >
      <div className="bg-foreground text-background flex items-center gap-1 rounded-full p-1 shadow-2xl ring-1 ring-foreground/10">
        {/* Count pill */}
        <div className="flex items-center gap-2 rounded-full px-4 py-1.5">
          <span
            aria-hidden="true"
            className="text-primary font-mono text-sm font-semibold tabular-nums"
          >
            {selectedCount}
          </span>
          <span className="font-heading text-background/70 text-[0.6875rem] tracking-[0.15em] uppercase">
            of {totalCount} selected
          </span>
        </div>

        {/* Domain-specific actions */}
        {actions && (
          <>
            <div
              aria-hidden="true"
              className="bg-background/15 mx-1 h-5 w-px"
            />
            <div className="flex items-center gap-1 px-1">{actions}</div>
          </>
        )}

        {/* Clear button */}
        <div
          aria-hidden="true"
          className="bg-background/15 mx-1 h-5 w-px"
        />
        <button
          type="button"
          onClick={() => table.resetRowSelection()}
          className="text-background/70 hover:bg-background/10 hover:text-background flex size-7 items-center justify-center rounded-full transition-colors"
          aria-label="Clear selection"
        >
          <X className="size-4" />
        </button>
      </div>
    </div>
  )
}

export { DataGridSelectionBar }
