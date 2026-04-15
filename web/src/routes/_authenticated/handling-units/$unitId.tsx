import { createFileRoute } from "@tanstack/react-router"
import { PageHeader } from "@/shared/ui/page-header"

export const Route = createFileRoute("/_authenticated/handling-units/$unitId")({
  component: HandlingUnitDetailPage,
})

function HandlingUnitDetailPage() {
  const { unitId } = Route.useParams()

  return (
    <div>
      <PageHeader title="Handling Unit Detail" description={unitId} />
      <div className="bg-muted/30 text-muted-foreground flex h-64 items-center justify-center rounded-lg border border-dashed font-mono text-sm">
        Coming soon
      </div>
    </div>
  )
}
