import { createFileRoute } from "@tanstack/react-router"
import { PageHeader } from "@/shared/ui/page-header"
import * as m from "@/paraglide/messages.js"

export const Route = createFileRoute("/_authenticated/dashboard")({
  component: DashboardPage,
})

function DashboardPage() {
  return (
    <div>
      <PageHeader
        title={m.page_dashboard_title()}
        description={m.page_dashboard_description()}
      />
      <div className="bg-muted/30 text-muted-foreground flex h-64 items-center justify-center rounded-lg border border-dashed font-mono text-sm">
        {m.page_coming_soon()}
      </div>
    </div>
  )
}
