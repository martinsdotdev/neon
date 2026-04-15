import { cn } from "@/shared/lib/utils"

const PageHeader = ({
  title,
  description,
  actions,
  className,
}: {
  title: string
  description?: string
  actions?: React.ReactNode
  className?: string
}) => (
  <div className={cn("flex items-start justify-between gap-4 pb-6", className)}>
    <div className="space-y-1">
      <h1 className="font-heading text-2xl font-semibold tracking-tight">
        {title}
      </h1>
      {description && (
        <p className="text-muted-foreground text-sm">{description}</p>
      )}
    </div>
    {actions && <div className="flex items-center gap-2">{actions}</div>}
  </div>
)

export { PageHeader }
