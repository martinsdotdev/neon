import { Link, createFileRoute, useNavigate } from "@tanstack/react-router"
import { useQueryClient, useSuspenseQuery } from "@tanstack/react-query"
import { useCallback, useMemo, useState } from "react"
import {
  AlertTriangle,
  ArrowLeft,
  ArrowRight,
  Check,
  Loader2,
  Search,
  Truck,
} from "lucide-react"
import { toast } from "sonner"
import type { Carrier } from "@/shared/api/carriers"
import type { Location } from "@/shared/api/locations"
import type { Order } from "@/shared/api/orders"
import type { Wave } from "@/shared/api/waves"
import { carrierQueries } from "@/shared/api/carriers"
import { locationQueries } from "@/shared/api/locations"
import { orderQueries } from "@/shared/api/orders"
import { waveMutations, waveQueries } from "@/shared/api/waves"
import { useIsMobile } from "@/shared/hooks/use-mobile"
import { cn } from "@/shared/lib/utils"
import { Badge } from "@/shared/ui/badge"
import { Button } from "@/shared/ui/button"
import { Card, CardContent } from "@/shared/ui/card"
import { Input } from "@/shared/ui/input"
import { PageHeader } from "@/shared/ui/page-header"
import { PriorityIndicator } from "@/shared/ui/priority-indicator"
import { RadioGroup, RadioGroupItem } from "@/shared/ui/radio-group"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/shared/ui/select"
import {
  Stepper,
  StepperContent,
  StepperIndicator,
  StepperItem,
  StepperNav,
  StepperPanel,
  StepperSeparator,
  StepperTitle,
  StepperTrigger,
} from "@/shared/ui/stepper"
import { Tooltip, TooltipContent, TooltipTrigger } from "@/shared/ui/tooltip"

export const Route = createFileRoute("/_authenticated/waves/new")({
  component: WaveWizardPage,
  loader: ({ context }) => {
    const qc = context.queryClient
    return Promise.all([
      qc.ensureQueryData(orderQueries.all()),
      qc.ensureQueryData(waveQueries.all()),
      qc.ensureQueryData(carrierQueries.all()),
      qc.ensureQueryData(locationQueries.all()),
    ])
  },
})

type Grouping = Wave["orderGrouping"]
const STEPS = ["Orders", "Grouping", "Docks", "Review"] as const

// Order grid template — repeated in the header row and every body row, so it's
// hoisted to a constant rather than copied. The 5 columns are: checkbox,
// order ID, carrier, lines count, priority.
const ORDER_GRID_COLS = "grid-cols-[28px_minmax(0,1fr)_120px_60px_120px]"

// Selected-tile tint — applied to OrderRow and GroupingOption when they're
// the active selection. Same alpha across both so the wizard reads with one
// "selected" recipe instead of two near-identical ones.
const SELECTED_TILE_BG = "bg-primary/[0.05]"

// StepCard — every wizard step opens with the same Card + heading + subtitle
// + body shape. Centralizing it removes ~15 lines of duplicated chrome from
// each step component and keeps the visual rhythm consistent.
function StepCard({
  children,
  description,
  right,
  title,
}: {
  children: React.ReactNode
  description?: React.ReactNode
  right?: React.ReactNode
  title: string
}) {
  return (
    <Card>
      <CardContent className="flex flex-col gap-4 py-5">
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h2 className="font-heading text-base font-semibold">{title}</h2>
            {description ? (
              <p className="text-label-m text-on-surface-variant">
                {description}
              </p>
            ) : null}
          </div>
          {right ? (
            <div className="flex items-center gap-2">{right}</div>
          ) : null}
        </div>
        {children}
      </CardContent>
    </Card>
  )
}

function WaveWizardPage() {
  const { data: orders } = useSuspenseQuery(orderQueries.all())
  const { data: waves } = useSuspenseQuery(waveQueries.all())
  const { data: carriers } = useSuspenseQuery(carrierQueries.all())
  const { data: locations } = useSuspenseQuery(locationQueries.all())
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const isMobile = useIsMobile()

  const [step, setStep] = useState(0)
  const [selected, setSelected] = useState<Set<string>>(() => new Set())
  const [grouping, setGrouping] = useState<Grouping | null>(null)
  const [dockByCarrier, setDockByCarrier] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)

  // Derived: which order IDs are already attached to a non-cancelled wave.
  // Selecting these is forbidden — the backend would 409.
  const wavedOrderIds = useMemo(() => {
    const ids = new Set<string>()
    for (const w of waves) {
      if (w.state === "Cancelled") continue
      for (const id of w.orderIds) ids.add(id)
    }
    return ids
  }, [waves])

  const carrierById = useMemo(() => {
    const map = new Map<string, Carrier>()
    for (const c of carriers) map.set(c.id, c)
    return map
  }, [carriers])

  const dockOptions = useMemo(
    () => locations.filter((l) => l.locationType === "Dock"),
    [locations]
  )

  const selectedOrders = useMemo(
    () => orders.filter((o) => selected.has(o.id)),
    [orders, selected]
  )

  // Unique carrier IDs across the selected orders. Each needs a dock pairing.
  const requiredCarriers = useMemo(() => {
    const ids = new Set<string>()
    for (const o of selectedOrders) {
      if (o.carrierId) ids.add(o.carrierId)
    }
    return [...ids]
  }, [selectedOrders])

  // Per-step validity. We re-derive on every render — cheap, and prevents
  // drift between "Next disabled" and the final submit gate.
  const ordersValid =
    selected.size > 0 && [...selected].every((id) => !wavedOrderIds.has(id))

  const groupingValid = grouping !== null

  const docksValid = useMemo(() => {
    if (requiredCarriers.length === 0) return true
    if (requiredCarriers.some((c) => !dockByCarrier[c])) return false
    const used = new Set<string>()
    for (const c of requiredCarriers) {
      const d = dockByCarrier[c]
      if (used.has(d)) return false
      used.add(d)
    }
    return true
  }, [requiredCarriers, dockByCarrier])

  // Tuple keyed by step index. Order matches STEPS so adding a step or
  // reordering them is caught at compile time (the `as const` length must
  // match STEPS.length, and the indexer below is always type-safe).
  const stepValidity = [
    ordersValid,
    groupingValid,
    docksValid,
    true,
  ] as const satisfies Readonly<Record<number, boolean>>
  const stepValid = stepValidity[step]
  const allValid = ordersValid && groupingValid && docksValid

  // The Stepper gates *forward* navigation: a user can always jump back to a
  // completed step (to revise) but can't skip past the first invalid step.
  // Index `i` is reachable iff every prior step is valid.
  const canVisit = (index: number) =>
    stepValidity.slice(0, index).every(Boolean)

  const toggleOrder = useCallback(
    (id: string) => {
      if (wavedOrderIds.has(id)) return
      setSelected((prev) => {
        const next = new Set(prev)
        if (next.has(id)) next.delete(id)
        else next.add(id)
        return next
      })
    },
    [wavedOrderIds]
  )

  const handleNext = () => setStep((s) => Math.min(STEPS.length - 1, s + 1))
  const handleBack = () => setStep((s) => Math.max(0, s - 1))

  const handleSubmit = async () => {
    if (!grouping) return
    setSubmitting(true)
    try {
      const result = await waveMutations.planAndRelease({
        dockAssignments: Object.entries(dockByCarrier).map(
          ([carrierId, dockId]) => ({ carrierId, dockId })
        ),
        grouping,
        orderIds: [...selected],
      })
      result.match(
        (response) => {
          toast.success("Wave released", {
            description: `${response.tasksCreated} tasks and ${response.consolidationGroupsCreated} consolidation group${
              response.consolidationGroupsCreated === 1 ? "" : "s"
            } created.`,
          })
          queryClient.invalidateQueries({ queryKey: ["waves"] })
          navigate({
            params: { waveId: response.waveId },
            to: "/waves/$waveId",
          })
        },
        (err) => {
          toast.error(
            err.kind === "problem"
              ? (err.problem.title ?? "Wave release failed")
              : "Network error — please retry.",
            {
              description:
                err.kind === "problem" ? err.problem.detail : undefined,
            }
          )
        }
      )
    } finally {
      // Always re-enable the Submit button. If `navigate` or a toast handler
      // throws inside `.match`, the finally still runs so the button doesn't
      // get stuck disabled.
      setSubmitting(false)
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        actions={
          <Button render={<Link to="/waves" />} size="sm" variant="ghost">
            <ArrowLeft className="size-4" />
            Cancel
          </Button>
        }
        description="Select orders and release for picking"
        title="Plan wave"
      />

      <Stepper
        onValueChange={(value) => setStep(value - 1)}
        orientation={isMobile ? "vertical" : "horizontal"}
        value={step + 1}
      >
        <Card>
          <CardContent className="py-5">
            <StepperNav>
              {STEPS.map((label, index) => (
                <StepperItem
                  completed={index < step}
                  // Block forward jumps to a step whose preconditions aren't
                  // met yet. Going back to a completed step (to revise) is
                  // always allowed.
                  disabled={index > step && !canVisit(index)}
                  key={label}
                  step={index + 1}
                >
                  <StepperTrigger className="flex items-center gap-2 px-2">
                    <StepperIndicator>{index + 1}</StepperIndicator>
                    <StepperTitle>{label}</StepperTitle>
                  </StepperTrigger>
                  {index < STEPS.length - 1 ? <StepperSeparator /> : null}
                </StepperItem>
              ))}
            </StepperNav>
          </CardContent>
        </Card>

        <StepperPanel>
          <StepperContent value={1}>
            <OrdersStep
              carrierById={carrierById}
              orders={orders}
              ordersValid={ordersValid}
              selected={selected}
              toggleOrder={toggleOrder}
              wavedOrderIds={wavedOrderIds}
            />
          </StepperContent>

          <StepperContent value={2}>
            <GroupingStep
              grouping={grouping}
              onChange={setGrouping}
              orderCount={selected.size}
            />
          </StepperContent>

          <StepperContent value={3}>
            <DocksStep
              carrierById={carrierById}
              dockByCarrier={dockByCarrier}
              dockOptions={dockOptions}
              docksValid={docksValid}
              onChange={setDockByCarrier}
              requiredCarriers={requiredCarriers}
            />
          </StepperContent>

          <StepperContent value={4}>
            <ReviewStep
              carrierById={carrierById}
              dockByCarrier={dockByCarrier}
              dockOptions={dockOptions}
              grouping={grouping}
              selectedOrders={selectedOrders}
            />
          </StepperContent>
        </StepperPanel>
      </Stepper>

      <WizardFooter
        allValid={allValid}
        onBack={handleBack}
        onNext={handleNext}
        onSubmit={handleSubmit}
        step={step}
        stepValid={stepValid}
        submitting={submitting}
      />
    </div>
  )
}

// ---------------------------------------------------------------------------
// WizardFooter — sticky bottom on mobile so Back / Next stay reachable while
// scrolling through the order list. The Submit button replaces Next on the
// last step and shows a spinner during the mutation.
// ---------------------------------------------------------------------------

function WizardFooter({
  allValid,
  onBack,
  onNext,
  onSubmit,
  step,
  stepValid,
  submitting,
}: {
  allValid: boolean
  onBack: () => void
  onNext: () => void
  onSubmit: () => void
  step: number
  stepValid: boolean
  submitting: boolean
}) {
  const isLast = step === STEPS.length - 1
  return (
    <div
      className={cn(
        // Layout: a horizontal action bar with Back left, label center, Next/Submit right.
        "z-10 mt-2 flex items-center gap-2 py-3",
        // Mobile: stick to the bottom of the viewport so the footer stays
        // reachable while scrolling through a long order list.
        "sticky bottom-0 -mx-4 border-t border-outline-variant bg-background/95 px-4 backdrop-blur",
        // Desktop (sm+): plain card-styled inline bar — no stickiness needed.
        "sm:relative sm:mx-0 sm:rounded-shape-md sm:border sm:px-3"
      )}
    >
      <Button
        disabled={step === 0 || submitting}
        onClick={onBack}
        size="sm"
        variant="ghost"
      >
        <ArrowLeft className="size-4" />
        Back
      </Button>
      <span className="flex-1" />
      <span className="text-label-s font-mono text-on-surface-variant">
        Step {step + 1} of {STEPS.length}
      </span>
      {isLast ? (
        <Button disabled={!allValid || submitting} onClick={onSubmit} size="sm">
          {submitting ? (
            <Loader2 className="size-4 animate-spin" />
          ) : (
            <Check className="size-4" />
          )}
          {submitting ? "Releasing…" : "Create wave"}
        </Button>
      ) : (
        <Button disabled={!stepValid} onClick={onNext} size="sm">
          Next
          <ArrowRight className="size-4" />
        </Button>
      )}
    </div>
  )
}

// ---------------------------------------------------------------------------
// Step 1 — OrdersStep
// Search-filtered list. Already-waved orders render with a disabled checkbox
// + a tooltip explaining why. Selection state lives in the parent.
// ---------------------------------------------------------------------------

function OrdersStep({
  carrierById,
  orders,
  ordersValid,
  selected,
  toggleOrder,
  wavedOrderIds,
}: {
  carrierById: Map<string, Carrier>
  orders: Array<Order>
  ordersValid: boolean
  selected: Set<string>
  toggleOrder: (id: string) => void
  wavedOrderIds: Set<string>
}) {
  const [search, setSearch] = useState("")

  const filtered = useMemo(() => {
    if (!search) return orders
    const q = search.toLowerCase()
    return orders.filter(
      (o) =>
        o.id.toLowerCase().includes(q) ||
        (o.carrierId &&
          carrierById.get(o.carrierId)?.name.toLowerCase().includes(q))
    )
  }, [orders, search, carrierById])

  const eligibleCount = orders.filter((o) => !wavedOrderIds.has(o.id)).length

  return (
    <StepCard
      description="Each order's lines become tasks. Already-waved orders are disabled."
      right={
        <span className="text-label-s font-mono text-on-surface-variant">
          {selected.size} of {eligibleCount} selected
        </span>
      }
      title="Pick orders to include"
    >
      <div className="relative">
        <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-on-surface-variant" />
        <Input
          className="pl-9"
          onChange={(event) => setSearch(event.target.value)}
          placeholder="Filter by order ID or carrier"
          value={search}
        />
      </div>

      <div className="overflow-hidden rounded-shape-sm border border-outline-variant">
        <div
          className={cn(
            "grid items-center gap-2 border-b border-outline-variant bg-surface-container-low px-3 py-2 font-mono text-[10px] tracking-[0.08em] text-on-surface-variant uppercase",
            ORDER_GRID_COLS
          )}
        >
          <span />
          <span>Order</span>
          <span>Carrier</span>
          <span className="text-right">Lines</span>
          <span>Priority</span>
        </div>
        <div className="max-h-[420px] overflow-y-auto">
          {filtered.length === 0 ? (
            <div className="text-label-m px-4 py-10 text-center text-on-surface-variant">
              No orders match the search.
            </div>
          ) : (
            filtered.map((order) => (
              <OrderRow
                carrier={
                  order.carrierId
                    ? (carrierById.get(order.carrierId) ?? null)
                    : null
                }
                isInWave={wavedOrderIds.has(order.id)}
                isSelected={selected.has(order.id)}
                key={order.id}
                onToggle={() => toggleOrder(order.id)}
                order={order}
              />
            ))
          )}
        </div>
      </div>

      {!ordersValid && selected.size === 0 ? (
        <p className="text-label-m text-on-surface-variant">
          Select at least one order to continue.
        </p>
      ) : null}
    </StepCard>
  )
}

function OrderRow({
  carrier,
  isInWave,
  isSelected,
  onToggle,
  order,
}: {
  carrier: Carrier | null
  isInWave: boolean
  isSelected: boolean
  onToggle: () => void
  order: Order
}) {
  return (
    <label
      className={cn(
        "grid cursor-pointer items-center gap-2 border-b border-outline-variant/40 px-3 py-2 transition-colors last:border-b-0 hover:bg-surface-container-low",
        ORDER_GRID_COLS,
        isInWave && "cursor-not-allowed opacity-55 hover:bg-transparent",
        isSelected && SELECTED_TILE_BG
      )}
    >
      <span className="flex items-center justify-center">
        <input
          checked={isSelected}
          className="size-3.5 accent-primary"
          disabled={isInWave}
          onChange={onToggle}
          type="checkbox"
        />
      </span>
      <span className="flex min-w-0 items-center gap-2">
        <span className="truncate font-mono text-sm">{order.id}</span>
        {isInWave ? (
          <Tooltip>
            <TooltipTrigger className="rounded-shape-xs" render={<span />}>
              <Badge className="px-1.5 py-0 text-[10px]" variant="outline">
                In wave
              </Badge>
            </TooltipTrigger>
            <TooltipContent>
              This order already belongs to an active wave and cannot be added
              to a new one.
            </TooltipContent>
          </Tooltip>
        ) : null}
      </span>
      <span className="text-label-s truncate font-mono text-on-surface-variant">
        {carrier ? carrier.name : "—"}
      </span>
      <span className="text-right font-mono text-sm tabular-nums">
        {order.lines.length}
      </span>
      <span>
        <PriorityIndicator level={order.priority} />
      </span>
    </label>
  )
}

// ---------------------------------------------------------------------------
// Step 2 — GroupingStep
// Two RadioGroup options with operational descriptions. Each grouping mode
// affects how consolidation groups are formed downstream.
// ---------------------------------------------------------------------------

function GroupingStep({
  grouping,
  onChange,
  orderCount,
}: {
  grouping: Grouping | null
  onChange: (value: Grouping) => void
  orderCount: number
}) {
  return (
    <StepCard
      description={`How should the planner group ${orderCount} order${orderCount === 1 ? "" : "s"} into consolidation groups?`}
      title="Choose order grouping"
    >
      <RadioGroup
        onValueChange={(value) => onChange(value as Grouping)}
        value={grouping ?? ""}
      >
        <GroupingOption
          checked={grouping === "Single"}
          description="All selected orders ship from one consolidation group. Best for small waves with a single drop or a shared carrier."
          label="Single"
          value="Single"
        />
        <GroupingOption
          checked={grouping === "Multi"}
          description="Each order gets its own consolidation group. Best when downstream packing has to keep orders separate (different customers, mixed carriers)."
          label="Multi"
          value="Multi"
        />
      </RadioGroup>
    </StepCard>
  )
}

function GroupingOption({
  checked,
  description,
  label,
  value,
}: {
  checked: boolean
  description: string
  label: string
  value: string
}) {
  return (
    <label
      className={cn(
        "flex cursor-pointer items-start gap-3 rounded-shape-sm border border-outline-variant px-4 py-3 transition-colors hover:bg-surface-container-low",
        checked && cn("border-primary/60", SELECTED_TILE_BG)
      )}
    >
      <RadioGroupItem className="mt-0.5" value={value} />
      <span className="flex flex-col gap-1">
        <span className="font-medium">{label}</span>
        <span className="text-label-m text-on-surface-variant">
          {description}
        </span>
      </span>
    </label>
  )
}

// ---------------------------------------------------------------------------
// Step 3 — DocksStep
// One Select per unique carrier from the selected orders. A dock can host
// only one carrier per wave; assigning the same dock twice surfaces an error.
// ---------------------------------------------------------------------------

function DocksStep({
  carrierById,
  dockByCarrier,
  dockOptions,
  docksValid,
  onChange,
  requiredCarriers,
}: {
  carrierById: Map<string, Carrier>
  dockByCarrier: Record<string, string>
  dockOptions: Array<Location>
  docksValid: boolean
  onChange: (next: Record<string, string>) => void
  requiredCarriers: Array<string>
}) {
  // Find which dock IDs are duplicated across carriers — used to highlight
  // the offending Selects.
  const dockUsage = useMemo(() => {
    const counts = new Map<string, number>()
    for (const c of requiredCarriers) {
      const d = dockByCarrier[c]
      if (!d) continue
      counts.set(d, (counts.get(d) ?? 0) + 1)
    }
    return counts
  }, [requiredCarriers, dockByCarrier])

  if (requiredCarriers.length === 0) {
    return (
      <Card>
        <CardContent className="flex flex-col items-center gap-2 py-10 text-center">
          <Truck className="size-8 text-on-surface-variant" />
          <h2 className="font-heading text-base font-semibold">
            No dock assignments needed
          </h2>
          <p className="text-label-m max-w-md text-on-surface-variant">
            None of the selected orders has a carrier assigned, so the wave
            doesn't need any dock pairings.
          </p>
        </CardContent>
      </Card>
    )
  }

  // Edge case: carriers exist but the warehouse hasn't configured any Dock
  // locations. Without this callout the Selects render empty and the user
  // can't tell why "Next" is permanently disabled.
  if (dockOptions.length === 0) {
    return (
      <Card>
        <CardContent className="flex flex-col items-center gap-2 py-10 text-center">
          <AlertTriangle className="size-8 text-state-cancelled" />
          <h2 className="font-heading text-base font-semibold">
            No dock locations configured
          </h2>
          <p className="text-label-m max-w-md text-on-surface-variant">
            The selected orders need carrier-to-dock pairings, but no locations
            of type <span className="font-mono">Dock</span> exist yet. Ask a
            warehouse administrator to add dock locations before planning this
            wave.
          </p>
        </CardContent>
      </Card>
    )
  }

  return (
    <StepCard
      description="Each carrier needs exactly one dock for shipping. A dock can only host one carrier per wave."
      title="Assign carriers to docks"
    >
      <div className="flex flex-col gap-3">
        {requiredCarriers.map((carrierId) => {
          const carrier = carrierById.get(carrierId)
          const value = dockByCarrier[carrierId] ?? ""
          const isDuplicate = value !== "" && (dockUsage.get(value) ?? 0) > 1
          return (
            <div
              className="grid gap-2 sm:grid-cols-[minmax(0,200px)_minmax(0,1fr)] sm:items-center"
              key={carrierId}
            >
              <div className="flex items-center gap-2">
                <span className="grid size-8 place-items-center rounded-shape-sm bg-surface-container">
                  <Truck className="size-4" />
                </span>
                <span className="font-medium">
                  {carrier?.name ?? carrierId}
                </span>
              </div>
              <div className="flex flex-col gap-1">
                <Select
                  onValueChange={(next) => {
                    // Base UI emits `string | null` on clear; the wizard
                    // treats null as "no selection" by removing the entry.
                    const updated = { ...dockByCarrier }
                    if (next == null || next === "") {
                      delete updated[carrierId]
                    } else {
                      updated[carrierId] = next
                    }
                    onChange(updated)
                  }}
                  value={value}
                >
                  <SelectTrigger
                    className={cn(
                      "w-full",
                      isDuplicate && "border-state-cancelled/60"
                    )}
                  >
                    <SelectValue placeholder="Select a dock" />
                  </SelectTrigger>
                  <SelectContent>
                    {dockOptions.map((dock) => (
                      <SelectItem key={dock.id} value={dock.id}>
                        <span className="font-mono">{dock.code}</span>
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {isDuplicate ? (
                  <span className="text-label-s font-mono text-state-cancelled">
                    This dock is assigned to another carrier in this wave.
                  </span>
                ) : null}
              </div>
            </div>
          )
        })}
      </div>

      {!docksValid ? (
        <p className="text-label-m text-on-surface-variant">
          Each carrier needs a unique dock to continue.
        </p>
      ) : null}
    </StepCard>
  )
}

// ---------------------------------------------------------------------------
// Step 4 — ReviewStep
// Summary card. Click "Create wave" in the footer to commit. The warning
// callout makes the immediate-release behavior explicit so supervisors
// don't expect a separate Release step.
// ---------------------------------------------------------------------------

function ReviewStep({
  carrierById,
  dockByCarrier,
  dockOptions,
  grouping,
  selectedOrders,
}: {
  carrierById: Map<string, Carrier>
  dockByCarrier: Record<string, string>
  dockOptions: Array<Location>
  grouping: Grouping | null
  selectedOrders: Array<Order>
}) {
  const dockById = useMemo(() => {
    const map = new Map<string, Location>()
    for (const d of dockOptions) map.set(d.id, d)
    return map
  }, [dockOptions])

  const totalLines = selectedOrders.reduce(
    (sum, order) => sum + order.lines.length,
    0
  )

  return (
    <div className="flex flex-col gap-4">
      <Card>
        <CardContent className="flex flex-col gap-5 py-5">
          <ReviewSection
            count={`${selectedOrders.length} order${selectedOrders.length === 1 ? "" : "s"}`}
            title="Orders"
          >
            <div className="flex flex-wrap items-center gap-1.5">
              {selectedOrders.slice(0, 8).map((order) => (
                <Badge key={order.id} variant="outline">
                  <span className="font-mono">{order.id}</span>
                </Badge>
              ))}
              {selectedOrders.length > 8 ? (
                <Badge variant="outline">+{selectedOrders.length - 8}</Badge>
              ) : null}
            </div>
            <p className="text-label-m text-on-surface-variant">
              {totalLines} line{totalLines === 1 ? "" : "s"} across all orders.
            </p>
          </ReviewSection>

          <ReviewSection title="Grouping">
            {grouping ? (
              <Badge variant="secondary">{grouping}</Badge>
            ) : (
              <span className="text-on-surface-variant">—</span>
            )}
          </ReviewSection>

          <ReviewSection title="Dock assignments">
            {Object.keys(dockByCarrier).length === 0 ? (
              <span className="text-on-surface-variant">
                No dock pairings (none of the selected orders has a carrier).
              </span>
            ) : (
              <ul className="flex flex-col gap-1">
                {Object.entries(dockByCarrier).map(([carrierId, dockId]) => (
                  <li
                    className="text-label-l flex items-center gap-2 font-mono"
                    key={carrierId}
                  >
                    <span>{carrierById.get(carrierId)?.name ?? carrierId}</span>
                    <ArrowRight className="size-3 text-on-surface-variant" />
                    <span>{dockById.get(dockId)?.code ?? dockId}</span>
                  </li>
                ))}
              </ul>
            )}
          </ReviewSection>
        </CardContent>
      </Card>

      <div className="flex items-start gap-3 rounded-shape-sm border border-state-allocated/30 bg-state-allocated-soft px-4 py-3 text-state-allocated">
        <AlertTriangle className="mt-0.5 size-4 shrink-0" />
        <div className="flex flex-col gap-0.5">
          <span className="font-medium">Releases immediately</span>
          <span className="text-label-m">
            Creating this wave releases it for picking right away. The wave will
            be in <span className="font-mono">Released</span> state and tasks
            will be generated by the planner.
          </span>
        </div>
      </div>
    </div>
  )
}

function ReviewSection({
  children,
  count,
  title,
}: {
  children: React.ReactNode
  count?: string
  title: string
}) {
  return (
    <section>
      <div className="flex items-center justify-between gap-2">
        <span className="text-label-s font-mono tracking-wider text-on-surface-variant uppercase">
          {title}
        </span>
        {count ? (
          <span className="text-label-s font-mono text-on-surface-variant">
            {count}
          </span>
        ) : null}
      </div>
      <div className="mt-2">{children}</div>
    </section>
  )
}
