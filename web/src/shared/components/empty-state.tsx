import { PackageOpen } from "lucide-react"

interface EmptyStateProps {
  title: string
  description?: string
}

export function EmptyState({
  title,
  description,
}: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center justify-center py-12">
      <PackageOpen className="text-muted-foreground mb-4 size-12" />
      <h3 className="text-lg font-medium">{title}</h3>
      {description ? (
        <p className="text-muted-foreground mt-1 text-sm">
          {description}
        </p>
      ) : null}
    </div>
  )
}
