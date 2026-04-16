import { createFileRoute, useNavigate } from "@tanstack/react-router"
import { z } from "zod"
import { useTheme } from "next-themes"
import {
  Info,
  LogOut,
  Monitor,
  Moon,
  Palette,
  Shield,
  Sun,
  User,
} from "lucide-react"
import type { LucideIcon } from "lucide-react"
import type { AuthUser } from "@/shared/api/auth"
import { useLogout } from "@/shared/api/auth"
import {
  PERMISSION_DOMAINS,
  formatPermissionLabel,
} from "@/shared/lib/permissions"
import { cn } from "@/shared/lib/utils"
import { Alert, AlertDescription } from "@/shared/ui/alert"
import { Avatar, AvatarFallback } from "@/shared/ui/avatar"
import { Badge } from "@/shared/ui/badge"
import { Button } from "@/shared/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/shared/ui/card"
import { useIsMobile } from "@/shared/hooks/use-mobile"
import { PageHeader } from "@/shared/ui/page-header"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/shared/ui/tabs"
import * as m from "@/paraglide/messages.js"

const settingsSearchSchema = z.object({
  tab: z
    .enum(["profile", "appearance", "account"])
    .catch("profile"),
})

export const Route = createFileRoute("/_authenticated/settings")({
  component: SettingsPage,
  validateSearch: settingsSearchSchema,
})

function SettingsPage() {
  const { user } = Route.useRouteContext()
  const { tab } = Route.useSearch()
  const navigate = useNavigate()
  const isMobile = useIsMobile()

  return (
    <div>
      <PageHeader
        title={m.settings_title()}
        description={m.settings_description()}
      />
      <Tabs
        value={tab}
        orientation={isMobile ? "horizontal" : "vertical"}
        onValueChange={(value) => {
          if (value === null) return
          navigate({
            search: { tab: value as string },
            replace: true,
          })
        }}
      >
        <TabsList
          className={cn(
            isMobile ? "mb-6" : "w-48 shrink-0 self-start",
          )}
        >
          <TabsTrigger value="profile">
            <User className="size-4" />
            {m.settings_tab_profile()}
          </TabsTrigger>
          <TabsTrigger value="appearance">
            <Palette className="size-4" />
            {m.settings_tab_appearance()}
          </TabsTrigger>
          <TabsTrigger value="account">
            <Shield className="size-4" />
            {m.settings_tab_account()}
          </TabsTrigger>
        </TabsList>
        <TabsContent value="profile" className="md:max-w-2xl">
          <ProfileSection user={user} />
        </TabsContent>
        <TabsContent value="appearance" className="md:max-w-2xl">
          <AppearanceSection />
        </TabsContent>
        <TabsContent value="account" className="md:max-w-2xl">
          <AccountSection />
        </TabsContent>
      </Tabs>
    </div>
  )
}

// ---------------------------------------------------------------------------
// Profile
// ---------------------------------------------------------------------------

function ProfileSection({ user }: { user: AuthUser }) {
  const initials = user.name
    .split(" ")
    .map((n) => n[0])
    .join("")
    .slice(0, 2)
    .toUpperCase()

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <CardHeader>
          <CardTitle>{m.settings_tab_profile()}</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="mb-6 flex items-center gap-4">
            <Avatar className="size-12">
              <AvatarFallback className="font-mono text-sm font-medium">
                {initials}
              </AvatarFallback>
            </Avatar>
            <div>
              <div className="font-medium">{user.name}</div>
              <div className="text-sm text-muted-foreground">
                {user.role}
              </div>
            </div>
          </div>
          <dl className="grid grid-cols-1 gap-4 text-sm sm:grid-cols-2">
            <div>
              <dt className="mb-1 text-muted-foreground">
                {m.settings_profile_name()}
              </dt>
              <dd className="font-medium">{user.name}</dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">
                {m.settings_profile_login()}
              </dt>
              <dd className="font-mono">{user.login}</dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">
                {m.settings_profile_role()}
              </dt>
              <dd>
                <Badge variant="secondary">{user.role}</Badge>
              </dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">
                {m.settings_profile_user_id()}
              </dt>
              <dd className="font-mono text-xs text-muted-foreground">
                {user.userId}
              </dd>
            </div>
          </dl>
        </CardContent>
      </Card>
      <PermissionsCard permissions={user.permissions} />
    </div>
  )
}

// ---------------------------------------------------------------------------
// Permissions
// ---------------------------------------------------------------------------

function PermissionsCard({
  permissions,
}: {
  permissions: string[]
}) {
  const granted = new Set(permissions)

  return (
    <Card>
      <CardHeader>
        <CardTitle>{m.settings_profile_permissions()}</CardTitle>
        <CardDescription>
          {m.settings_profile_permissions_description()}
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-5">
        {PERMISSION_DOMAINS.map((domain) => (
          <div key={domain.key}>
            <div className="font-heading mb-2 text-xs tracking-widest text-muted-foreground uppercase">
              {domain.label}
            </div>
            <div className="flex flex-wrap gap-1.5">
              {domain.permissions.map((perm) => {
                const isGranted = granted.has(perm)
                return (
                  <Badge
                    key={perm}
                    variant={isGranted ? "default" : "outline"}
                    className={cn(
                      !isGranted && "opacity-40",
                    )}
                  >
                    {formatPermissionLabel(perm)}
                  </Badge>
                )
              })}
            </div>
          </div>
        ))}
      </CardContent>
    </Card>
  )
}

// ---------------------------------------------------------------------------
// Appearance
// ---------------------------------------------------------------------------

function AppearanceSection() {
  const { theme, setTheme } = useTheme()

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <CardHeader>
          <CardTitle>{m.settings_appearance_theme()}</CardTitle>
          <CardDescription>
            {m.settings_appearance_theme_description()}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            <ThemeCard
              active={theme === "light"}
              icon={Sun}
              label={m.settings_appearance_light()}
              onClick={() => setTheme("light")}
              preview={<LightPreview />}
            />
            <ThemeCard
              active={theme === "dark"}
              icon={Moon}
              label={m.settings_appearance_dark()}
              onClick={() => setTheme("dark")}
              preview={<DarkPreview />}
            />
            <ThemeCard
              active={theme === "system"}
              icon={Monitor}
              label={m.settings_appearance_system()}
              onClick={() => setTheme("system")}
              preview={<SystemPreview />}
            />
          </div>
        </CardContent>
      </Card>
    </div>
  )
}

function ThemeCard({
  active,
  icon: Icon,
  label,
  onClick,
  preview,
}: {
  active: boolean
  icon: LucideIcon
  label: string
  onClick: () => void
  preview: React.ReactNode
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "flex flex-col items-center gap-3 rounded-3xl border-2 p-3 transition-all",
        active
          ? "border-primary bg-primary/5 ring-2 ring-primary/20"
          : "border-border hover:border-primary/30",
      )}
    >
      <div className="w-full overflow-hidden rounded-2xl">
        {preview}
      </div>
      <div className="flex items-center gap-2 text-sm font-medium">
        <Icon className="size-4" />
        {label}
      </div>
    </button>
  )
}

function LightPreview() {
  return (
    <div
      className="flex h-16"
      style={{ background: "oklch(1 0 0)" }}
    >
      <div
        className="w-3.5 shrink-0"
        style={{ background: "oklch(0.986 0.002 67.8)" }}
      />
      <div className="flex flex-1 flex-col">
        <div
          className="h-2 shrink-0"
          style={{
            background: "oklch(0.986 0.002 67.8)",
            borderBottom: "1px solid oklch(0.922 0.005 34.3)",
          }}
        />
        <div className="flex-1 p-1.5">
          <div
            className="h-1.5 w-8 rounded-full"
            style={{
              background: "oklch(0.852 0.199 91.936)",
            }}
          />
          <div
            className="mt-1 h-1 w-12 rounded-full"
            style={{ background: "oklch(0.96 0.002 17.2)" }}
          />
          <div
            className="mt-1 h-1 w-10 rounded-full"
            style={{ background: "oklch(0.96 0.002 17.2)" }}
          />
        </div>
      </div>
    </div>
  )
}

function DarkPreview() {
  return (
    <div
      className="flex h-16"
      style={{ background: "oklch(0.18 0.008 49.3)" }}
    >
      <div
        className="w-3.5 shrink-0"
        style={{ background: "oklch(0.214 0.009 43.1)" }}
      />
      <div className="flex flex-1 flex-col">
        <div
          className="h-2 shrink-0"
          style={{
            background: "oklch(0.214 0.009 43.1)",
            borderBottom:
              "1px solid oklch(1 0 0 / 10%)",
          }}
        />
        <div className="flex-1 p-1.5">
          <div
            className="h-1.5 w-8 rounded-full"
            style={{
              background: "oklch(0.795 0.184 86.047)",
            }}
          />
          <div
            className="mt-1 h-1 w-12 rounded-full"
            style={{
              background: "oklch(0.268 0.011 36.5)",
            }}
          />
          <div
            className="mt-1 h-1 w-10 rounded-full"
            style={{
              background: "oklch(0.268 0.011 36.5)",
            }}
          />
        </div>
      </div>
    </div>
  )
}

function SystemPreview() {
  return (
    <div className="flex h-16 overflow-hidden">
      {/* Left half: light */}
      <div
        className="flex flex-1"
        style={{ background: "oklch(1 0 0)" }}
      >
        <div
          className="w-2 shrink-0"
          style={{ background: "oklch(0.986 0.002 67.8)" }}
        />
        <div className="flex flex-1 flex-col">
          <div
            className="h-2 shrink-0"
            style={{
              background: "oklch(0.986 0.002 67.8)",
              borderBottom:
                "1px solid oklch(0.922 0.005 34.3)",
            }}
          />
          <div className="flex-1 p-1">
            <div
              className="h-1.5 w-6 rounded-full"
              style={{
                background: "oklch(0.852 0.199 91.936)",
              }}
            />
            <div
              className="mt-1 h-1 w-8 rounded-full"
              style={{
                background: "oklch(0.96 0.002 17.2)",
              }}
            />
          </div>
        </div>
      </div>
      {/* Right half: dark */}
      <div
        className="flex flex-1"
        style={{ background: "oklch(0.18 0.008 49.3)" }}
      >
        <div
          className="w-2 shrink-0"
          style={{ background: "oklch(0.214 0.009 43.1)" }}
        />
        <div className="flex flex-1 flex-col">
          <div
            className="h-2 shrink-0"
            style={{
              background: "oklch(0.214 0.009 43.1)",
              borderBottom:
                "1px solid oklch(1 0 0 / 10%)",
            }}
          />
          <div className="flex-1 p-1">
            <div
              className="h-1.5 w-6 rounded-full"
              style={{
                background: "oklch(0.795 0.184 86.047)",
              }}
            />
            <div
              className="mt-1 h-1 w-8 rounded-full"
              style={{
                background: "oklch(0.268 0.011 36.5)",
              }}
            />
          </div>
        </div>
      </div>
    </div>
  )
}

// ---------------------------------------------------------------------------
// Account
// ---------------------------------------------------------------------------

function AccountSection() {
  const logout = useLogout()

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <CardHeader>
          <CardTitle>{m.settings_tab_account()}</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <Alert>
            <Info className="size-4" />
            <AlertDescription>
              {m.settings_account_managed()}
            </AlertDescription>
          </Alert>
          <div className="flex flex-col gap-3 rounded-2xl border p-4 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <div className="text-sm font-medium">
                {m.settings_account_logout()}
              </div>
              <div className="text-sm text-muted-foreground">
                {m.settings_account_logout_description()}
              </div>
            </div>
            <Button
              variant="destructive"
              size="sm"
              className="w-full sm:w-auto"
              onClick={() => logout.mutate()}
              disabled={logout.isPending}
            >
              <LogOut className="size-4" />
              {m.settings_account_logout()}
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
