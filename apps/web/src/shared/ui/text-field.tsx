import { useId } from "react"
import { Input as InputPrimitive } from "@base-ui/react/input"

import { cn } from "@/shared/lib/utils"

type TextFieldCounter = {
  current: number
  max?: number
}

type TextFieldProps = Omit<
  React.ComponentProps<"input">,
  "id" | "placeholder"
> & {
  label: string
  id?: string
  variant?: "outlined" | "filled" | "stealth"
  invalid?: boolean
  leading?: React.ReactNode
  trailing?: React.ReactNode
  supporting?: React.ReactNode
  counter?: TextFieldCounter
  wrapperClassName?: string
  shellClassName?: string
}

function TextField({
  label,
  id,
  variant = "outlined",
  invalid,
  disabled,
  leading,
  trailing,
  supporting,
  counter,
  className,
  wrapperClassName,
  shellClassName,
  ...props
}: TextFieldProps) {
  const autoId = useId()
  const fieldId = id ?? autoId

  const shell = {
    outlined: cn(
      "rounded-shape-xs border border-outline-variant",
      "focus-within:border-2 focus-within:border-primary",
      invalid &&
        "border-destructive focus-within:border-2 focus-within:border-destructive"
    ),
    filled: cn(
      "rounded-t-shape-xs border-0 border-b border-outline bg-surface-variant",
      "focus-within:border-b-2 focus-within:border-primary",
      invalid && "border-destructive focus-within:border-destructive"
    ),
    stealth: cn(
      "rounded-3xl border border-transparent bg-input/50",
      "focus-within:border-ring focus-within:ring-3 focus-within:ring-ring/30",
      invalid &&
        "border-destructive ring-3 ring-destructive/20 focus-within:border-destructive"
    ),
  }[variant]

  const padStart = leading ? "ps-10" : "ps-4"
  const padEnd = trailing ? "pe-10" : "pe-4"

  return (
    <div
      data-slot="text-field"
      data-variant={variant}
      data-invalid={invalid || undefined}
      data-disabled={disabled || undefined}
      className={cn("flex w-full flex-col gap-1", wrapperClassName)}
    >
      <div
        className={cn(
          "duration-short-3 relative flex h-10 items-center transition-colors ease-standard",
          shell,
          disabled && "pointer-events-none opacity-40",
          shellClassName
        )}
      >
        {leading ? (
          <span
            aria-hidden
            className="pointer-events-none absolute start-3 top-1/2 z-10 flex -translate-y-1/2 items-center text-on-surface-variant *:size-5"
          >
            {leading}
          </span>
        ) : null}

        <InputPrimitive
          id={fieldId}
          data-slot="text-field-input"
          placeholder=" "
          disabled={disabled}
          aria-invalid={invalid || undefined}
          className={cn(
            "peer text-body-m absolute inset-0 h-full w-full rounded-[inherit] bg-transparent text-foreground outline-none placeholder:text-transparent disabled:cursor-not-allowed",
            padStart,
            padEnd,
            className
          )}
          {...props}
        />

        {variant === "stealth" ? (
          <label htmlFor={fieldId} className="sr-only">
            {label}
          </label>
        ) : (
          <label
            htmlFor={fieldId}
            className={cn(
              "text-body-m duration-short-4 pointer-events-none absolute top-1/2 z-10 -translate-y-1/2 text-on-surface-variant transition-[top,font-size,color,background-color,padding] ease-standard",
              leading ? "start-10" : "start-4",
              "peer-focus:text-label-m peer-focus:top-0 peer-focus:text-primary",
              "peer-[:not(:placeholder-shown)]:text-label-m peer-[:not(:placeholder-shown)]:top-0",
              variant === "outlined" && [
                "peer-focus:bg-background peer-focus:px-1",
                "peer-[:not(:placeholder-shown)]:bg-background peer-[:not(:placeholder-shown)]:px-1",
              ],
              variant === "filled" && [
                "peer-focus:start-4",
                "peer-[:not(:placeholder-shown)]:start-4",
              ],
              invalid && [
                "peer-focus:text-destructive",
                "peer-[:not(:placeholder-shown)]:text-destructive",
                "text-destructive",
              ]
            )}
          >
            {label}
          </label>
        )}

        {trailing ? (
          <span
            aria-hidden
            className="absolute end-3 top-1/2 z-10 flex -translate-y-1/2 items-center text-on-surface-variant *:size-5"
          >
            {trailing}
          </span>
        ) : null}
      </div>

      {supporting || counter ? (
        <div
          className={cn(
            "text-label-s flex items-start justify-between gap-4 px-4",
            invalid ? "text-destructive" : "text-on-surface-variant"
          )}
        >
          <span className="flex-1">{supporting}</span>
          {counter ? (
            <span className="shrink-0 tabular-nums">
              {counter.current}
              {counter.max === undefined ? null : ` / ${counter.max}`}
            </span>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}

export { TextField }
export type { TextFieldProps }
