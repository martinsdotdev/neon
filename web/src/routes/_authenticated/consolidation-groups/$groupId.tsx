import { createFileRoute } from "@tanstack/react-router"
import { PageHeader } from "@/shared/ui/page-header"

export const Route = createFileRoute("/_authenticated/consolidation-groups/$groupId")({
  component: ConsolidationGroupDetailPage,
})

function ConsolidationGroupDetailPage() {
  const { groupId } = Route.useParams()

  return (
    <div>
      <PageHeader title="Consolidation Group Detail" description={groupId} />
      <div className="bg-muted/30 text-muted-foreground flex h-64 items-center justify-center rounded-lg border border-dashed font-mono text-sm">
        Coming soon
      </div>
    </div>
  )
}
