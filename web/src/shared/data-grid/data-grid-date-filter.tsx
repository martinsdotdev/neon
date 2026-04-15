"use client"

import type { Column } from "@tanstack/react-table"
import {
  endOfMonth,
  endOfYear,
  format,
  startOfMonth,
  startOfYear,
  subDays,
  subMonths,
  subYears,
} from "date-fns"
import { CalendarIcon, X } from "lucide-react"
import { useState } from "react"
import type { DateRange } from "react-day-picker"
import { Button } from "@/shared/ui/button"
import { Calendar } from "@/shared/ui/calendar"
import { Card, CardContent } from "@/shared/ui/card"
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/shared/ui/popover"
import { cn } from "@/shared/lib/utils"

interface DataGridDateFilterProps<TData, TValue> {
  className?: string
  column?: Column<TData, TValue>
  title?: string
}

const DataGridDateFilter = <TData, TValue>({
  className,
  column,
  title = "Date",
}: DataGridDateFilterProps<TData, TValue>) => {
  const today = new Date()
  const presets = {
    lastMonth: {
      from: startOfMonth(subMonths(today, 1)),
      to: endOfMonth(subMonths(today, 1)),
    },
    last7Days: { from: subDays(today, 6), to: today },
    last30Days: { from: subDays(today, 29), to: today },
    lastYear: {
      from: startOfYear(subYears(today, 1)),
      to: endOfYear(subYears(today, 1)),
    },
    monthToDate: { from: startOfMonth(today), to: today },
    yearToDate: { from: startOfYear(today), to: today },
    yesterday: { from: subDays(today, 1), to: subDays(today, 1) },
  }

  const value = column?.getFilterValue() as DateRange | undefined
  const [month, setMonth] = useState(value?.to ?? today)

  const setRange = (range: DateRange | undefined) => {
    column?.setFilterValue(range?.from ? range : undefined)
    if (range?.to) setMonth(range.to)
  }

  const onClear = (event: React.MouseEvent) => {
    event.stopPropagation()
    column?.setFilterValue(undefined)
  }

  const formatted =
    value?.from && value.to
      ? `${format(value.from, "LLL d")} - ${format(value.to, "LLL d, y")}`
      : value?.from
        ? format(value.from, "LLL d, y")
        : null

  return (
    <Popover>
      <PopoverTrigger
        render={
          <Button
            size="sm"
            variant="outline"
            className={cn("h-8", className)}
          >
            {formatted ? (
              <>
                <button
                  aria-label={`Clear ${title} filter`}
                  className="text-muted-foreground hover:text-foreground -ms-1 me-0.5 inline-flex size-4 items-center justify-center rounded transition-colors"
                  onClick={onClear}
                  type="button"
                >
                  <X className="size-3.5" />
                </button>
                <span className="font-mono text-xs">{formatted}</span>
              </>
            ) : (
              <>
                <CalendarIcon className="text-muted-foreground/80 size-3.5" />
                <span>{title}</span>
              </>
            )}
          </Button>
        }
      />
      <PopoverContent align="start" className="w-auto p-0">
        <Card className="p-0">
          <CardContent className="p-0">
            <div className="flex max-sm:flex-col">
              <div className="relative py-4 max-sm:order-1 max-sm:border-t sm:w-32">
                <div className="h-full sm:border-e">
                  <div className="flex flex-col px-2">
                    <Button
                      className="w-full justify-start"
                      onClick={() => setRange({ from: today, to: today })}
                      size="sm"
                      variant="ghost"
                    >
                      Today
                    </Button>
                    <Button
                      className="w-full justify-start"
                      onClick={() => setRange(presets.yesterday)}
                      size="sm"
                      variant="ghost"
                    >
                      Yesterday
                    </Button>
                    <Button
                      className="w-full justify-start"
                      onClick={() => setRange(presets.last7Days)}
                      size="sm"
                      variant="ghost"
                    >
                      Last 7 days
                    </Button>
                    <Button
                      className="w-full justify-start"
                      onClick={() => setRange(presets.last30Days)}
                      size="sm"
                      variant="ghost"
                    >
                      Last 30 days
                    </Button>
                    <Button
                      className="w-full justify-start"
                      onClick={() => setRange(presets.monthToDate)}
                      size="sm"
                      variant="ghost"
                    >
                      Month to date
                    </Button>
                    <Button
                      className="w-full justify-start"
                      onClick={() => setRange(presets.lastMonth)}
                      size="sm"
                      variant="ghost"
                    >
                      Last month
                    </Button>
                    <Button
                      className="w-full justify-start"
                      onClick={() => setRange(presets.yearToDate)}
                      size="sm"
                      variant="ghost"
                    >
                      Year to date
                    </Button>
                    <Button
                      className="w-full justify-start"
                      onClick={() => setRange(presets.lastYear)}
                      size="sm"
                      variant="ghost"
                    >
                      Last year
                    </Button>
                  </div>
                </div>
              </div>
              <Calendar
                disabled={[{ after: today }]}
                mode="range"
                month={month}
                numberOfMonths={2}
                onMonthChange={setMonth}
                onSelect={setRange}
                selected={value}
              />
            </div>
          </CardContent>
        </Card>
      </PopoverContent>
    </Popover>
  )
}

export { DataGridDateFilter }
