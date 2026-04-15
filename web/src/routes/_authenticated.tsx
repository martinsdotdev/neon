import {
  createFileRoute,
  Link,
  Outlet,
  redirect,
  useRouterState,
} from "@tanstack/react-router"
import {
  Archive,
  Barcode,
  ClipboardCheck,
  ClipboardList,
  Database,
  Layers,
  LayoutDashboard,
  LogOut,
  MapPin,
  Monitor,
  Package,
  PackagePlus,
  ScanBarcode,
  Ship,
  ShoppingCart,
  Truck,
  Users,
  Waves,
} from "lucide-react"
import type { LucideIcon } from "lucide-react"
import { authQueries, useLogout } from '@/shared/api/auth';
import type { AuthUser } from '@/shared/api/auth';
import { Badge } from "@/shared/ui/badge"
import { ModeToggle } from "@/shared/ui/mode-toggle"
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarInset,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarProvider,
  SidebarTrigger,
} from "@/shared/ui/sidebar"
import * as m from "@/paraglide/messages.js"

export const Route = createFileRoute("/_authenticated")({
  beforeLoad: async ({ context, location }) => {
    const user = await context.queryClient.ensureQueryData(authQueries.me())
    if (!user) {
      throw redirect({
        search: { redirect: location.href },
        to: "/login",
      })
    }
    return { user }
  },
  component: AuthenticatedLayout,
})

// ---------------------------------------------------------------------------
// Navigation definition
// ---------------------------------------------------------------------------

interface NavItem { to: string; icon: LucideIcon; label: string }
interface NavGroup { label: string; items: NavItem[] }

const navigation: NavGroup[] = [
  {
    items: [
      { icon: LayoutDashboard, label: m.nav_dashboard(), to: "/dashboard" },
    ],
    label: "",
  },
  {
    items: [
      { icon: Waves, label: m.nav_waves(), to: "/waves" },
      { icon: ClipboardList, label: m.nav_tasks(), to: "/tasks" },
      { icon: Layers, label: m.nav_consolidation_groups(), to: "/consolidation-groups" },
      { icon: Truck, label: m.nav_transport_orders(), to: "/transport-orders" },
      { icon: Package, label: m.nav_handling_units(), to: "/handling-units" },
      { icon: Monitor, label: m.nav_workstations(), to: "/workstations" },
    ],
    label: m.nav_outbound(),
  },
  {
    items: [
      { icon: PackagePlus, label: m.nav_deliveries(), to: "/deliveries" },
      { icon: ClipboardCheck, label: m.nav_receipts(), to: "/receipts" },
    ],
    label: m.nav_inbound(),
  },
  {
    items: [
      { icon: Database, label: m.nav_stock_positions(), to: "/stock-positions" },
      { icon: Archive, label: m.nav_inventory_records(), to: "/inventory" },
      { icon: ScanBarcode, label: m.nav_cycle_counts(), to: "/cycle-counts" },
    ],
    label: m.nav_inventory(),
  },
  {
    items: [
      { icon: ShoppingCart, label: m.nav_orders(), to: "/orders" },
      { icon: Barcode, label: m.nav_skus(), to: "/skus" },
      { icon: Users, label: m.nav_users(), to: "/users" },
      { icon: MapPin, label: m.nav_locations(), to: "/locations" },
      { icon: Ship, label: m.nav_carriers(), to: "/carriers" },
    ],
    label: m.nav_reference_data(),
  },
]

// ---------------------------------------------------------------------------
// Layout
// ---------------------------------------------------------------------------

function AuthenticatedLayout() {
  const { user } = Route.useRouteContext()

  return (
    <SidebarProvider>
      <AppSidebar user={user} />
      <SidebarInset>
        <header className="flex h-12 items-center gap-2 border-b px-4">
          <SidebarTrigger className="-ml-1" />
          <div className="flex-1" />
          <ModeToggle />
        </header>
        <main className="flex-1 p-6">
          <Outlet />
        </main>
      </SidebarInset>
    </SidebarProvider>
  )
}

// ---------------------------------------------------------------------------
// Sidebar
// ---------------------------------------------------------------------------

function AppSidebar({ user }: { user: AuthUser }) {
  const pathname = useRouterState({
    select: (s) => s.location.pathname,
  })
  const logout = useLogout()

  return (
    <Sidebar collapsible="icon" variant="inset">
      {/* Header: brand */}
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton
              size="lg"
              render={<Link to="/dashboard" />}
            >
              <div className="bg-primary text-primary-foreground flex size-7 shrink-0 items-center justify-center rounded-md font-mono text-xs font-bold">
                N
              </div>
              <span className="font-heading truncate text-sm font-semibold tracking-[0.15em]">
                NEON WES
              </span>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>

      {/* Navigation */}
      <SidebarContent>
        {navigation.map((group) => (
          <SidebarGroup key={group.label || "top"}>
            {group.label && (
              <SidebarGroupLabel className="font-heading text-[0.625rem] tracking-[0.2em] uppercase">
                {group.label}
              </SidebarGroupLabel>
            )}
            <SidebarMenu>
              {group.items.map((item) => {
                const isActive =
                  pathname === item.to ||
                  pathname.startsWith(`${item.to}/`)

                return (
                  <SidebarMenuItem key={item.to}>
                    <SidebarMenuButton
                      isActive={isActive}
                      render={<Link to={item.to} />}
                    >
                      <item.icon />
                      <span>{item.label}</span>
                    </SidebarMenuButton>
                  </SidebarMenuItem>
                )
              })}
            </SidebarMenu>
          </SidebarGroup>
        ))}
      </SidebarContent>

      {/* Footer: user */}
      <SidebarFooter>
        <div className="flex items-center gap-3 overflow-hidden px-1 py-1">
          <div className="bg-muted flex size-8 shrink-0 items-center justify-center rounded-full font-mono text-xs font-medium">
            {user.name
              .split(" ")
              .map((n) => n[0])
              .join("")
              .slice(0, 2)
              .toUpperCase()}
          </div>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium">{user.name}</p>
            <div className="flex items-center gap-1.5">
              <Badge
                variant="secondary"
                className="font-heading h-4 px-1 text-[0.5625rem] tracking-wider"
              >
                {user.role}
              </Badge>
            </div>
          </div>
          <button
            type="button"
            onClick={() => logout.mutate()}
            className="text-muted-foreground hover:text-foreground shrink-0 p-1 transition-colors"
            title={m.auth_sign_out()}
          >
            <LogOut className="size-4" />
          </button>
        </div>
      </SidebarFooter>
    </Sidebar>
  )
}
