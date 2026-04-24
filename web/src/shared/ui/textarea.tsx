import { cva } from "class-variance-authority"
import type { VariantProps } from "class-variance-authority"

import { cn } from "@/shared/lib/utils"

const textareaVariants = cva(
  "text-body-m duration-short-3 flex field-sizing-content min-h-20 w-full resize-none py-3 text-foreground transition-[color,box-shadow,background-color,border-color] ease-standard outline-none placeholder:text-on-surface-variant disabled:cursor-not-allowed disabled:opacity-40",
  {
    variants: {
      variant: {
        outlined:
          "rounded-shape-xs border border-outline-variant bg-input/50 px-4 focus-visible:border-2 focus-visible:border-primary focus-visible:px-[15px] focus-visible:py-[11px] aria-invalid:border-destructive aria-invalid:focus-visible:border-destructive",
        filled:
          "rounded-t-shape-xs border-0 border-b border-outline bg-surface-variant px-4 focus-visible:border-b-2 focus-visible:border-primary aria-invalid:border-destructive",
        stealth:
          "rounded-2xl border border-transparent bg-input/50 px-3 focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/30 aria-invalid:border-destructive aria-invalid:ring-3 aria-invalid:ring-destructive/20",
      },
    },
    defaultVariants: {
      variant: "outlined",
    },
  }
)

type TextareaProps = React.ComponentProps<"textarea"> &
  VariantProps<typeof textareaVariants>

function Textarea({ className, variant, ...props }: TextareaProps) {
  return (
    <textarea
      data-slot="textarea"
      data-variant={variant ?? "outlined"}
      className={cn(textareaVariants({ variant }), className)}
      {...props}
    />
  )
}

export { Textarea, textareaVariants }
