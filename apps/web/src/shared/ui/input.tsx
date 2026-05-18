import { cva } from "class-variance-authority"
import { Input as InputPrimitive } from "@base-ui/react/input"
import type { VariantProps } from "class-variance-authority"

import { cn } from "@/shared/lib/utils"

const inputVariants = cva(
  "text-body-m duration-short-3 file:text-label-l h-10 w-full min-w-0 text-foreground transition-[color,box-shadow,background-color,border-color] ease-standard outline-none file:inline-flex file:h-8 file:border-0 file:bg-transparent file:font-medium file:text-foreground placeholder:text-on-surface-variant disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-40",
  {
    variants: {
      variant: {
        outlined:
          "rounded-shape-xs border border-outline-variant bg-input/50 px-4 focus-visible:border-2 focus-visible:border-primary focus-visible:px-[15px] aria-invalid:border-destructive aria-invalid:focus-visible:border-destructive",
        filled:
          "rounded-t-shape-xs border-0 border-b border-outline bg-surface-variant px-4 focus-visible:border-b-2 focus-visible:border-primary aria-invalid:border-destructive",
        stealth:
          "rounded-3xl border border-transparent bg-input/50 px-3 focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/30 aria-invalid:border-destructive aria-invalid:ring-3 aria-invalid:ring-destructive/20",
      },
    },
    defaultVariants: {
      variant: "outlined",
    },
  }
)

type InputProps = React.ComponentProps<"input"> &
  VariantProps<typeof inputVariants>

function Input({ className, variant, type, ...props }: InputProps) {
  return (
    <InputPrimitive
      type={type}
      data-slot="input"
      data-variant={variant ?? "outlined"}
      className={cn(inputVariants({ variant }), className)}
      {...props}
    />
  )
}

export { Input, inputVariants }
