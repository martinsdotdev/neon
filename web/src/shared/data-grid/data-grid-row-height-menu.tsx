"use client";

import type { Table } from "@tanstack/react-table";
import { RectangleHorizontalIcon, SquareIcon } from "lucide-react";
import * as React from "react";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/shared/ui/select";

const rowHeights = [
  {
    icon: RectangleHorizontalIcon,
    label: "Compact",
    value: "short" as const,
  },
  {
    icon: SquareIcon,
    label: "Comfortable",
    value: "medium" as const,
  },
] as const;

interface DataGridRowHeightMenuProps<TData>
  extends React.ComponentProps<typeof SelectContent> {
  table: Table<TData>;
  disabled?: boolean;
}

export function DataGridRowHeightMenu<TData>({
  table,
  disabled,
  ...props
}: DataGridRowHeightMenuProps<TData>) {
  const rowHeight = table.options.meta?.rowHeight;
  const onRowHeightChange = table.options.meta?.onRowHeightChange;

  const selectedRowHeight = React.useMemo(() => {
    return (
      rowHeights.find((opt) => opt.value === rowHeight) ?? rowHeights[0]
    );
  }, [rowHeight]);

  return (
    <Select
      value={rowHeight}
      onValueChange={onRowHeightChange}
      disabled={disabled}
    >
      <SelectTrigger size="sm" className="[&_svg:nth-child(2)]:hidden">
        <SelectValue placeholder="Row height">
          <selectedRowHeight.icon />
          {selectedRowHeight.label}
        </SelectValue>
      </SelectTrigger>
      <SelectContent {...props}>
        {rowHeights.map((option) => {
          const OptionIcon = option.icon;
          return (
            <SelectItem key={option.value} value={option.value}>
              <OptionIcon className="size-4" />
              {option.label}
            </SelectItem>
          );
        })}
      </SelectContent>
    </Select>
  );
}
