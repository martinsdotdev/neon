import { Link, createFileRoute } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { ArrowLeft, XCircle } from "lucide-react"
import type { Wave } from "@/shared/api/waves"
import { waveQueries } from "@/shared/api/waves"
import { useIsMobile } from "@/shared/hooks/use-mobile"
import { Button } from "@/shared/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/shared/ui/card"
import { DateCell } from "@/shared/ui/date-cell"
import { PageHeader } from "@/shared/ui/page-header"
import { StateBadge } from "@/shared/ui/state-badge"
import {
  Stepper,
  StepperIndicator,
  StepperItem,
  StepperNav,
  StepperSeparator,
  StepperTitle,
  StepperTrigger,
} from "@/shared/ui/stepper"

export const Route = createFileRoute("/_authenticated/waves/$waveId")({
  component: WaveDetailPage,
  loader: ({ context, params }) =>
    context.queryClient.ensureQueryData(waveQueries.detail(params.waveId)),
})

// Happy-path states for the stepper. Cancelled is terminal-branch and
// rendered separately as a red note below the stepper.
const HAPPY_PATH = ["Planned", "Released", "Completed"] as const
type HappyState = (typeof HAPPY_PATH)[number]

function WaveDetailPage() {
  const { waveId } = Route.useParams()
  const { data: wave } = useSuspenseQuery(waveQueries.detail(waveId))

  if (!wave) {
    return (
      <div>
        <PageHeader title="Wave not found" />
        <Button render={<Link to="/waves" />} variant="ghost">
          <ArrowLeft className="size-4" />
          Back to waves
        </Button>
      </div>
    )
  }

  return (
    <div>
      <PageHeader
        actions={<StateBadge state={wave.state} />}
        title={`Wave ${wave.id}`}
      />

      <div className="mb-6">
        <Button render={<Link to="/waves" />} size="sm" variant="ghost">
          <ArrowLeft className="size-4" />
          All waves
        </Button>
      </div>

      <WaveStepperCard wave={wave} />

      <Card className="mt-6">
        <CardHeader>
          <CardTitle className="font-heading text-sm tracking-wider">
            Details
          </CardTitle>
        </CardHeader>
        <CardContent>
          <dl className="grid grid-cols-2 gap-4 text-sm">
            <div>
              <dt className="mb-1 text-muted-foreground">ID</dt>
              <dd className="font-mono text-xs text-muted-foreground">
                {wave.id}
              </dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Order Grouping</dt>
              <dd className="font-medium">{wave.orderGrouping}</dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Order Count</dt>
              <dd className="font-mono font-medium">{wave.orderCount}</dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">State</dt>
              <dd>
                <StateBadge state={wave.state} />
              </dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Created</dt>
              <dd>
                <DateCell value={wave.createdAt} variant="detail" />
              </dd>
            </div>
            <div className="col-span-2">
              <dt className="mb-1 text-muted-foreground">Order IDs</dt>
              <dd className="font-mono text-xs">
                {wave.orderIds.length > 0 ? wave.orderIds.join(", ") : "—"}
              </dd>
            </div>
          </dl>
        </CardContent>
      </Card>
    </div>
  )
}

function WaveStepperCard({ wave }: { wave: Wave }) {
  const isMobile = useIsMobile()
  const isCancelled = wave.state === "Cancelled"
  // activeStep is 1-based per ReUI Stepper. For cancelled waves we still
  // render the happy-path but highlight only the last confirmed state; the
  // CancelledNote takes the narrative from there.
  const activeStep = stateIndex(wave.state)
  // Vertical stepper on small screens; the 3-step horizontal layout cramps
  // when timestamps render below labels in a narrow viewport.
  const orientation = isMobile ? "vertical" : "horizontal"

  return (
    <Card>
      <CardContent className="py-6">
        <Stepper orientation={orientation} value={activeStep}>
          <StepperNav>
            {HAPPY_PATH.map((state, i) => (
              <StepperItem
                completed={i + 1 < activeStep}
                key={state}
                step={i + 1}
              >
                <StepperTrigger className="flex items-center gap-3 px-2">
                  <StepperIndicator>{i + 1}</StepperIndicator>
                  <div className="flex flex-col items-start gap-0.5">
                    <StepperTitle>{state}</StepperTitle>
                    <span className="text-label-s font-mono text-on-surface-variant">
                      {eventAt(wave, state) ?? "—"}
                    </span>
                  </div>
                </StepperTrigger>
                {i < HAPPY_PATH.length - 1 ? <StepperSeparator /> : null}
              </StepperItem>
            ))}
          </StepperNav>
        </Stepper>
        {isCancelled ? <CancelledNote wave={wave} /> : null}
      </CardContent>
    </Card>
  )
}

function CancelledNote({ wave }: { wave: Wave }) {
  return (
    <div
      className="mt-4 flex items-start gap-3 rounded-shape-sm border border-state-cancelled/30 bg-state-cancelled-soft px-3 py-2.5 text-state-cancelled"
      data-slot="cancelled-note"
    >
      <XCircle className="mt-0.5 size-4 shrink-0" />
      <div className="flex flex-col">
        <span className="text-label-s font-mono tracking-wider uppercase">
          Cancelled
        </span>
        <span className="text-label-m">
          This wave was cancelled and did not complete. Created{" "}
          <DateCell value={wave.createdAt} variant="detail" />.
        </span>
      </div>
    </div>
  )
}

// ---------------------------------------------------------------------------
// Happy-path state helpers
// ---------------------------------------------------------------------------

function stateIndex(state: Wave["state"]): number {
  switch (state) {
    case "Planned":
      return 1
    case "Released":
      return 2
    case "Completed":
      return 3
    // Cancelled slots against the last confirmed step; the CancelledNote
    // provides the narrative. Treat Cancelled-from-Planned the same as
    // still-Planned for the stepper position.
    case "Cancelled":
      return 1
  }
}

/**
 * Resolve a timestamp for `state`. The API only exposes `createdAt` today,
 * so we approximate and let the user wire real event sourcing.
 *
 * Candidate sources:
 *   - wave.events[] (if the aggregate exposes an events array client-side)
 *   - waveQueries.events(id) projection (if an event query exists)
 *   - denormalized wave.releasedAt / wave.completedAt fields
 *
 * TODO(user): replace with real sourcing — 3-5 lines.
 */
function eventAt(wave: Wave, state: HappyState): string | null {
  if (state === "Planned") return shortTime(wave.createdAt)
  return null
}

function shortTime(iso: string): string {
  const date = new Date(iso)
  return date.toLocaleTimeString(undefined, {
    hour: "2-digit",
    minute: "2-digit",
  })
}
