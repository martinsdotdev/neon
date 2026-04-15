import {
  Fragment,
  useCallback,
  useEffect,
  useRef,
  useState,
} from "react"
import {
  createFileRoute,
  Link,
  Outlet,
  redirect,
  useNavigate,
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
  Moon,
  Package,
  PackagePlus,
  PanelLeft,
  ScanBarcode,
  Search,
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
  Command,
  CommandDialog,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
  CommandSeparator,
  CommandShortcut,
} from "@/shared/ui/command"
import { Button } from "@/shared/ui/button"
import {
  InputGroup,
  InputGroupAddon,
} from "@/shared/ui/input-group"
import { Kbd } from "@/shared/ui/kbd"
import { MenuIcon, type MenuIconHandle } from "@/shared/ui/menu"
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
  SidebarInset,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarProvider,
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
  const [commandOpen, setCommandOpen] = useState(false)

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (
        e.key === "k" &&
        (e.metaKey || e.ctrlKey)
      ) {
        e.preventDefault()
        setCommandOpen((prev) => !prev)
      }
    }
    window.addEventListener("keydown", handleKeyDown)
    return () =>
      window.removeEventListener("keydown", handleKeyDown)
  }, [])

  return (
    <SidebarProvider>
      <AppSidebar />
      <header className="fixed top-0 left-0 right-0 z-50 flex w-full items-center border-b border-sidebar-border bg-sidebar/95 backdrop-blur-xl backdrop-saturate-150">
        <div className="flex h-(--header-height) w-full items-center gap-2 px-4">
          <SidebarMenuTrigger />
          <Breadcrumb className="hidden sm:block">
            <BreadcrumbList>
              {segments.map((segment, index) => {
                const href = `/${segments.slice(0, index + 1).join("/")}`
                const isLast = index === segments.length - 1
                const label =
                  navLabels.get(segment) ?? segment

                return (
                  <Fragment key={href}>
                    {index > 0 && (
                      <BreadcrumbSeparator />
                    )}
                    <BreadcrumbItem>
                      {isLast ? (
                        <BreadcrumbPage className="font-medium">
                          {label}
                        </BreadcrumbPage>
                      ) : (
                        <BreadcrumbLink
                          render={<Link to={href} />}
                        >
                          {label}
                        </BreadcrumbLink>
                      )}
                    </BreadcrumbItem>
                  </Fragment>
                )
              })}
            </BreadcrumbList>
          </Breadcrumb>
          <div className="pointer-events-none absolute inset-0 flex items-center justify-center px-48">
            <InputGroup
              className="pointer-events-auto w-full max-w-md cursor-pointer rounded-full transition-colors duration-[180ms] ease-out hover:bg-input/70"
              onClick={() => setCommandOpen(true)}
            >
              <InputGroupAddon>
                <Search />
              </InputGroupAddon>
              <span className="hidden flex-1 text-sm text-muted-foreground lg:inline">
                Search...
              </span>
              <InputGroupAddon align="inline-end">
                <Kbd>Ctrl</Kbd>
                <Kbd>K</Kbd>
              </InputGroupAddon>
            </InputGroup>
          </div>
          <div className="ms-auto" />
          <UserMenu user={user} />
        </div>
      </header>
      <SidebarInset>
        <div className="mt-(--header-height) flex-1 p-6">
          <Outlet />
        </div>
      </SidebarInset>
      <CommandPalette
        open={commandOpen}
        onOpenChange={setCommandOpen}
      />
    </SidebarProvider>
  )
}

// ---------------------------------------------------------------------------
// Sidebar Menu Trigger
// ---------------------------------------------------------------------------

function SidebarMenuTrigger() {
  const { toggleSidebar, state } = useSidebar()
  const iconRef = useRef<MenuIconHandle>(null)

  useEffect(() => {
    if (state === "expanded") {
      iconRef.current?.startAnimation()
    } else {
      iconRef.current?.stopAnimation()
    }
  }, [state])

  return (
    <Button
      variant="ghost"
      size="icon-sm"
      className="-ml-1"
      onClick={toggleSidebar}
    >
      <MenuIcon ref={iconRef} size={18} />
    </Button>
  )
}

// ---------------------------------------------------------------------------
// Sidebar
// ---------------------------------------------------------------------------

function AppSidebar() {
  const pathname = useRouterState({
    select: (s) => s.location.pathname,
  })

  return (
    <Sidebar
      collapsible="icon"
      className="top-(--header-height) h-[calc(100svh-var(--header-height))]!"
    >
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

// ---------------------------------------------------------------------------
// Command Palette
// ---------------------------------------------------------------------------

function CommandPalette({
  open,
  onOpenChange,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const navigate = useNavigate()
  const { theme, setTheme } = useTheme()
  const { toggleSidebar } = useSidebar()
  const logout = useLogout()

  const runCommand = useCallback(
    (command: () => void) => {
      onOpenChange(false)
      command()
    },
    [onOpenChange],
  )

  return (
    <CommandDialog
      open={open}
      onOpenChange={onOpenChange}
      className="bg-popover/70 backdrop-blur-2xl backdrop-saturate-150"
    >
      <Command className="bg-transparent **:[[cmdk-group-heading]]:font-heading **:[[cmdk-group-heading]]:text-[0.625rem]! **:[[cmdk-group-heading]]:tracking-[0.2em] **:[[cmdk-group-heading]]:uppercase">
        <CommandInput placeholder="Search pages and actions..." />
        <CommandList>
          <CommandEmpty>No results found.</CommandEmpty>
          {navigation.map((group) => (
            <CommandGroup
              key={group.label || "top"}
              heading={group.label || "Navigation"}
            >
              {group.items.map((item) => (
                <CommandItem
                  key={item.to}
                  onSelect={() =>
                    runCommand(() =>
                      navigate({ to: item.to }),
                    )
                  }
                >
                  <item.icon strokeWidth={1.75} />
                  <span>{item.label}</span>
                </CommandItem>
              ))}
            </CommandGroup>
          ))}
          <CommandSeparator />
          <CommandGroup heading="Theme">
            <CommandItem
              data-checked={theme === "light"}
              onSelect={() =>
                runCommand(() => setTheme("light"))
              }
            >
              <Sun strokeWidth={1.75} />
              <span>Light</span>
            </CommandItem>
            <CommandItem
              data-checked={theme === "dark"}
              onSelect={() =>
                runCommand(() => setTheme("dark"))
              }
            >
              <Moon strokeWidth={1.75} />
              <span>Dark</span>
            </CommandItem>
            <CommandItem
              data-checked={theme === "system"}
              onSelect={() =>
                runCommand(() => setTheme("system"))
              }
            >
              <Monitor strokeWidth={1.75} />
              <span>System</span>
            </CommandItem>
          </CommandGroup>
          <CommandSeparator />
          <CommandGroup heading="Actions">
            <CommandItem
              onSelect={() => runCommand(toggleSidebar)}
            >
              <PanelLeft strokeWidth={1.75} />
              <span>Toggle Sidebar</span>
              <CommandShortcut>⌘B</CommandShortcut>
            </CommandItem>
            <CommandItem
              onSelect={() =>
                runCommand(() => logout.mutate())
              }
            >
              <LogOut strokeWidth={1.75} />
              <span>Log Out</span>
            </CommandItem>
          </CommandGroup>
        </CommandList>
        <div className="flex items-center gap-4 border-t border-border/50 px-3 py-2 text-xs text-muted-foreground">
          <span className="flex items-center gap-1.5">
            <kbd className="font-heading inline-flex size-4 items-center justify-center rounded bg-muted/80 text-[0.5625rem]">
              ↩
            </kbd>
            select
          </span>
          <span className="flex items-center gap-1.5">
            <kbd className="font-heading inline-flex h-4 items-center rounded bg-muted/80 px-1 text-[0.5625rem]">
              ↑↓
            </kbd>
            navigate
          </span>
          <span className="flex items-center gap-1.5">
            <kbd className="font-heading inline-flex h-4 items-center rounded bg-muted/80 px-1 text-[0.5625rem]">
              esc
            </kbd>
            close
          </span>
        </div>
      </Command>
    </CommandDialog>
  )
}

// ---------------------------------------------------------------------------
// User Menu
// ---------------------------------------------------------------------------

function UserMenu({ user }: { user: AuthUser }) {
  const { theme, setTheme } = useTheme()
  const logout = useLogout()

  const initials = user.name
    .split(" ")
    .map((n) => n[0])
    .join("")
    .slice(0, 2)
    .toUpperCase()

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        className="flex size-8 shrink-0 items-center justify-center rounded-full outline-none"
      >
        <Avatar className="size-8">
          <AvatarFallback className="font-mono text-xs font-medium">
            {initials}
          </AvatarFallback>
        </Avatar>
      </DropdownMenuTrigger>
      <DropdownMenuContent
        className="w-60"
        side="bottom"
        align="end"
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
          <Tabs
            value={theme ?? "system"}
            onValueChange={setTheme}
          >
            <TabsList className="w-full">
              <TabsTrigger
                value="light"
                className="h-6 flex-1"
              >
                <Sun className="size-4" />
              </TabsTrigger>
              <TabsTrigger
                value="dark"
                className="h-6 flex-1"
              >
                <Moon className="size-4" />
              </TabsTrigger>
              <TabsTrigger
                value="system"
                className="h-6 flex-1"
              >
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
  )
}
