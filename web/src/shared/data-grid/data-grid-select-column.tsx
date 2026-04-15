"use client";

import { Link } from "@tanstack/react-router";
import type {
  CellContext,
  ColumnDef,
  HeaderContext,
  Row,
} from "@tanstack/react-table";
import { ArrowUpRight } from "lucide-react";
import * as React from "react";
import { Checkbox } from "@/shared/ui/checkbox";
import { cn } from "@/shared/lib/utils";

type HitboxSize = "default" | "sm" | "lg";

interface DataGridSelectHitboxProps {
  htmlFor: string;
  children: React.ReactNode;
  size?: HitboxSize;
  debug?: boolean;
}

function DataGridSelectHitbox({
  htmlFor,
  children,
  size,
  debug,
}: DataGridSelectHitboxProps) {
  return (
    <div
      className={cn(
        "group relative flex h-full items-center",
        size === "default" && "-ms-3 -me-2 ps-3 pe-2",
        size === "sm" && "-ms-3 -me-1.5 ps-3 pe-1.5",
        size === "lg" && "-mx-3 px-3",
      )}
    >
      {children}
      <label
        htmlFor={htmlFor}
        className={cn(
          "absolute inset-0 cursor-pointer",
          debug && "border border-red-500 border-dashed bg-red-500/20",
        )}
      />
    </div>
  );
}

interface DataGridSelectCheckboxProps
  extends Omit<React.ComponentProps<typeof Checkbox>, "id"> {
  rowNumber?: number;
  hitboxSize?: HitboxSize;
  debug?: boolean;
  detailHref?: string;
}

function DataGridSelectCheckbox({
  rowNumber,
  hitboxSize,
  debug,
  checked,
  className,
  detailHref,
  ...props
}: DataGridSelectCheckboxProps) {
  const id = React.useId();

  if (rowNumber !== undefined) {
    return (
      <DataGridSelectHitbox htmlFor={id} size={hitboxSize} debug={debug}>
        <div className="flex items-center gap-1.5">
          <Checkbox
            id={id}
            className={cn(
              "relative transition-[shadow,border] hover:border-primary/40",
              className,
            )}
            checked={checked}
            {...props}
          />
          {detailHref ? (
            <>
              <span
                aria-hidden="true"
                className="text-muted-foreground text-xs tabular-nums group-hover/row:hidden"
              >
                {rowNumber}
              </span>
              <Link
                to={detailHref}
                aria-label="Open details"
                className="relative z-20 hidden size-4 items-center justify-center text-muted-foreground hover:text-foreground group-hover/row:inline-flex"
                onClick={(event) => event.stopPropagation()}
              >
                <ArrowUpRight className="size-3.5" />
              </Link>
            </>
          ) : (
            <span
              aria-hidden="true"
              className="text-muted-foreground text-xs tabular-nums"
            >
              {rowNumber}
            </span>
          )}
        </div>
      </DataGridSelectHitbox>
    );
  }

  return (
    <DataGridSelectHitbox htmlFor={id} size={hitboxSize} debug={debug}>
      <Checkbox
        id={id}
        className={cn(
          "relative transition-[shadow,border] hover:border-primary/40",
          className,
        )}
        checked={checked}
        {...props}
      />
    </DataGridSelectHitbox>
  );
}

interface DataGridSelectHeaderProps<TData>
  extends Pick<HeaderContext<TData, unknown>, "table"> {
  hitboxSize?: HitboxSize;
  readOnly?: boolean;
  debug?: boolean;
}

function DataGridSelectHeader<TData>({
  table,
  hitboxSize,
  readOnly,
  debug,
}: DataGridSelectHeaderProps<TData>) {
  const onCheckedChange = React.useCallback(
    (value: boolean) => table.toggleAllPageRowsSelected(value),
    [table],
  );

  if (readOnly) {
    return (
      <div className="mt-1 flex items-center ps-1 text-muted-foreground text-sm">
        #
      </div>
    );
  }

  return (
    <DataGridSelectCheckbox
      aria-label="Select all"
      checked={
        table.getIsAllPageRowsSelected() ||
        (table.getIsSomePageRowsSelected() && "indeterminate")
      }
      onCheckedChange={onCheckedChange}
      hitboxSize={hitboxSize}
      debug={debug}
    />
  );
}

interface DataGridSelectCellProps<TData>
  extends Pick<CellContext<TData, unknown>, "row" | "table"> {
  hitboxSize?: HitboxSize;
  enableRowMarkers?: boolean;
  readOnly?: boolean;
  debug?: boolean;
  detailHref?: (row: Row<TData>) => string | undefined;
}

function DataGridSelectCell<TData>({
  row,
  table,
  hitboxSize,
  enableRowMarkers,
  readOnly,
  debug,
  detailHref,
}: DataGridSelectCellProps<TData>) {
  const meta = table.options.meta;
  const rowNumber = enableRowMarkers
    ? (meta?.getVisualRowIndex?.(row.id) ?? row.index + 1)
    : undefined;
  const href = detailHref?.(row);

  const onCheckedChange = React.useCallback(
    (value: boolean) => {
      if (meta?.onRowSelect) {
        meta.onRowSelect(row.id, value, false);
      } else {
        row.toggleSelected(value);
      }
    },
    [meta, row],
  );

  const onClick = React.useCallback(
    (event: React.MouseEvent<HTMLButtonElement>) => {
      if (event.shiftKey) {
        event.preventDefault();
        meta?.onRowSelect?.(row.id, !row.getIsSelected(), true);
      }
    },
    [meta, row],
  );

  if (readOnly) {
    return (
      <div className="flex items-center ps-1 text-muted-foreground text-xs tabular-nums">
        {rowNumber ?? row.index + 1}
      </div>
    );
  }

  return (
    <DataGridSelectCheckbox
      aria-label={rowNumber ? `Select row ${rowNumber}` : "Select row"}
      checked={row.getIsSelected()}
      onCheckedChange={onCheckedChange}
      onClick={onClick}
      rowNumber={rowNumber}
      hitboxSize={hitboxSize}
      debug={debug}
      detailHref={href}
    />
  );
}

interface GetDataGridSelectColumnOptions<TData>
  extends Omit<Partial<ColumnDef<TData>>, "id" | "header" | "cell"> {
  enableRowMarkers?: boolean;
  readOnly?: boolean;
  hitboxSize?: HitboxSize;
  debug?: boolean;
  detailHref?: (row: Row<TData>) => string | undefined;
}

export function getDataGridSelectColumn<TData>({
  size = 40,
  hitboxSize = "default",
  enableHiding = false,
  enableResizing = false,
  enableSorting = false,
  enableRowMarkers = false,
  readOnly = false,
  debug = false,
  detailHref,
  ...props
}: GetDataGridSelectColumnOptions<TData> = {}): ColumnDef<TData> {
  return {
    id: "select",
    header: ({ table }) => (
      <DataGridSelectHeader
        table={table}
        hitboxSize={hitboxSize}
        readOnly={readOnly}
        debug={debug}
      />
    ),
    cell: ({ row, table }) => (
      <DataGridSelectCell
        row={row}
        table={table}
        enableRowMarkers={enableRowMarkers}
        readOnly={readOnly}
        detailHref={detailHref}
        hitboxSize={hitboxSize}
        debug={debug}
      />
    ),
    size,
    enableHiding,
    enableResizing,
    enableSorting,
    ...props,
  };
}
