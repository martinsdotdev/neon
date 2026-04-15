import { Fragment } from "react"
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
  ChevronsUpDown,
  ClipboardCheck,
  ClipboardList,
  Database,
  Layers,
  LayoutDashboard,
  LogOut,
  MapPin,
  Monitor,
  Moon,
  Package,
  PackagePlus,
  ScanBarcode,
  Settings,
  Ship,
  ShoppingCart,
  Sun,
  Truck,
  Users,
  Waves,
} from "lucide-react"
import type { LucideIcon } from "lucide-react"
import { motion } from "motion/react"
import { useTheme } from "next-themes"
import { authQueries, useLogout } from '@/shared/api/auth';
import type { AuthUser } from '@/shared/api/auth';
import { Avatar, AvatarFallback } from "@/shared/ui/avatar"
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/shared/ui/breadcrumb"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/shared/ui/dropdown-menu"
import {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarInset,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarProvider,
  SidebarTrigger,
  useSidebar,
} from "@/shared/ui/sidebar"
import { Tabs, TabsList, TabsTrigger } from "@/shared/ui/tabs"
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

const navLabels = new Map(
  navigation.flatMap((g) => g.items.map((i) => [i.to.slice(1), i.label])),
)

// ---------------------------------------------------------------------------
// Layout
// ---------------------------------------------------------------------------

function AuthenticatedLayout() {
  const { user } = Route.useRouteContext()
  const pathname = useRouterState({
    select: (s) => s.location.pathname,
  })
  const segments = pathname.split("/").filter(Boolean)

  return (
    <SidebarProvider>
      <AppSidebar user={user} />
      <SidebarInset>
        <header className="flex h-12 items-center gap-2 border-b px-4">
          <SidebarTrigger className="-ml-1" />
          <div className="bg-border h-4 w-px" />
          <Breadcrumb>
            <BreadcrumbList className="sm:gap-1">
              {segments.map((segment, index) => {
                const href = `/${segments.slice(0, index + 1).join("/")}`
                const isLast = index === segments.length - 1
                const label = navLabels.get(segment) ?? segment

                return (
                  <Fragment key={href}>
                    {index > 0 && <BreadcrumbSeparator />}
                    <BreadcrumbItem>
                      {isLast ? (
                        <BreadcrumbPage>{label}</BreadcrumbPage>
                      ) : (
                        <BreadcrumbLink render={<Link to={href} />}>
                          {label}
                        </BreadcrumbLink>
                      )}
                    </BreadcrumbItem>
                  </Fragment>
                )
              })}
            </BreadcrumbList>
          </Breadcrumb>
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
  const { state } = useSidebar()
  const { theme, setTheme } = useTheme()
  const logout = useLogout()

  const initials = user.name
    .split(" ")
    .map((n) => n[0])
    .join("")
    .slice(0, 2)
    .toUpperCase()

  return (
    <Sidebar collapsible="icon" variant="inset">
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <DropdownMenu>
              <DropdownMenuTrigger render={<SidebarMenuButton size="lg" />}>
                <Avatar className="size-8 shrink-0">
                  <AvatarFallback className="font-mono text-xs font-medium">
                    {initials}
                  </AvatarFallback>
                </Avatar>
                <span className="min-w-0 flex-1 truncate text-sm font-medium">
                  {user.name}
                </span>
                <ChevronsUpDown className="text-muted-foreground ml-auto size-4 shrink-0" />
              </DropdownMenuTrigger>
              <DropdownMenuContent
                className="w-60"
                side={state === "collapsed" ? "right" : "bottom"}
                align="start"
                sideOffset={8}
              >
                <div className="flex items-center gap-3 px-1 pt-1.5">
                  <Avatar className="size-8">
                    <AvatarFallback className="font-mono text-xs font-medium">
                      {initials}
                    </AvatarFallback>
                  </Avatar>
                  <div className="flex flex-col">
                    <span className="text-foreground text-sm font-medium">
                      {user.name}
                    </span>
                    <span className="text-muted-foreground text-xs">
                      {user.role}
                    </span>
                  </div>
                </div>
                <div className="py-2.5">
                  <Tabs value={theme ?? "system"} onValueChange={setTheme}>
                    <TabsList className="w-full">
                      <TabsTrigger value="light" className="h-6 flex-1">
                        <Sun className="size-4" />
                      </TabsTrigger>
                      <TabsTrigger value="dark" className="h-6 flex-1">
                        <Moon className="size-4" />
                      </TabsTrigger>
                      <TabsTrigger value="system" className="h-6 flex-1">
                        <Monitor className="size-4" />
                      </TabsTrigger>
                    </TabsList>
                  </Tabs>
                </div>
                <DropdownMenuSeparator />
                <DropdownMenuItem>
                  <Settings />
                  Settings
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem
                  variant="destructive"
                  onClick={() => logout.mutate()}
                >
                  <LogOut />
                  Logout
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
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
                    {isActive && (
                      <motion.div
                        layoutId="sidebar-indicator"
                        className="absolute inset-0 rounded-xl bg-sidebar-primary/12 group-data-[collapsible=icon]:m-auto group-data-[collapsible=icon]:size-8 group-data-[collapsible=icon]:rounded-full"
                        transition={{ type: "spring", stiffness: 500, damping: 30 }}
                      />
                    )}
                    <SidebarMenuButton
                      isActive={isActive}
                      render={<Link to={item.to} />}
                      className="relative z-10"
                    >
                      <item.icon strokeWidth={isActive ? 2.4 : 1.75} />
                      <span>{item.label}</span>
                    </SidebarMenuButton>
                  </SidebarMenuItem>
                )
              })}
            </SidebarMenu>
          </SidebarGroup>
        ))}
      </SidebarContent>
    </Sidebar>
  )
}
