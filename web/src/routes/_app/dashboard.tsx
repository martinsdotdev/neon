import { createFileRoute } from "@tanstack/react-router"

import { PageHeader } from "@/shared/components/page-header"
import { m } from "@/paraglide/messages.js"

export const Route = createFileRoute("/_app/dashboard")({
  component: DashboardPage,
})

function DashboardPage() {
  return (
    <div className="space-y-6">
      <PageHeader title={m.nav_dashboard()} />
      <p className="text-muted-foreground">
        Dashboard coming soon.
      </p>
    </div>
  )
}
