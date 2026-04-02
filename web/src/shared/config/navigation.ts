import {
  Boxes,
  Building2,
  ClipboardList,
  GitBranch,
  LayoutDashboard,
  type LucideIcon,
  MapPin,
  Package,
  PackageCheck,
  ShoppingCart,
  Truck,
  Users,
  Warehouse,
  Waves,
} from "lucide-react"

export interface NavItem {
  label: string
  href: string
  icon: LucideIcon
}

export interface NavSection {
  label: string
  items: NavItem[]
}

export const navigation: NavSection[] = [
  {
    label: "nav_overview",
    items: [
      {
        label: "nav_dashboard",
        href: "/dashboard",
        icon: LayoutDashboard,
      },
    ],
  },
  {
    label: "nav_operations",
    items: [
      { label: "nav_waves", href: "/waves", icon: Waves },
      {
        label: "nav_tasks",
        href: "/tasks",
        icon: ClipboardList,
      },
      {
        label: "nav_transport_orders",
        href: "/transport-orders",
        icon: Truck,
      },
      {
        label: "nav_consolidation_groups",
        href: "/consolidation-groups",
        icon: GitBranch,
      },
    ],
  },
  {
    label: "nav_workstations_section",
    items: [
      {
        label: "nav_workstations",
        href: "/workstations",
        icon: Building2,
      },
      { label: "nav_slots", href: "/slots", icon: Boxes },
    ],
  },
  {
    label: "nav_inventory_section",
    items: [
      {
        label: "nav_inventory",
        href: "/inventory",
        icon: Package,
      },
      {
        label: "nav_handling_units",
        href: "/handling-units",
        icon: PackageCheck,
      },
    ],
  },
  {
    label: "nav_reference_data",
    items: [
      {
        label: "nav_locations",
        href: "/locations",
        icon: MapPin,
      },
      {
        label: "nav_skus",
        href: "/skus",
        icon: Warehouse,
      },
      {
        label: "nav_carriers",
        href: "/carriers",
        icon: Truck,
      },
      {
        label: "nav_orders",
        href: "/orders",
        icon: ShoppingCart,
      },
      { label: "nav_users", href: "/users", icon: Users },
    ],
  },
]
