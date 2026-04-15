import { createFileRoute, redirect, useNavigate } from "@tanstack/react-router"
import { useForm } from "@tanstack/react-form"
import { z } from "zod"
import { useLogin, authQueries } from "@/shared/api/auth"
import { Button } from "@/shared/ui/button"
import { Input } from "@/shared/ui/input"
import { Label } from "@/shared/ui/label"
import * as m from "@/paraglide/messages.js"

const loginSearchSchema = z.object({
  redirect: z.string().optional(),
})

export const Route = createFileRoute("/login")({
  beforeLoad: async ({ context, search }) => {
    const user = await context.queryClient.ensureQueryData(authQueries.me())
    if (user) {
      throw redirect({ to: search.redirect ?? "/dashboard" })
    }
  },
  component: LoginPage,
  validateSearch: loginSearchSchema,
})

function LoginPage() {
  const navigate = useNavigate()
  const search = Route.useSearch()
  const login = useLogin()

  const form = useForm({
    defaultValues: { login: "", password: "" },
    onSubmit: async ({ value }) => {
      await login.mutateAsync(value)
      navigate({ to: search.redirect ?? "/dashboard" })
    },
  })

  return (
    <div className="relative flex min-h-svh items-center justify-center overflow-hidden bg-background">
      {/* Ambient glow */}
      <div
        className="pointer-events-none absolute inset-0"
        style={{
          background:
            "radial-gradient(ellipse 600px 400px at 50% 40%, oklch(0.852 0.199 91.936 / 0.06), transparent)",
        }}
      />

      {/* Grid texture */}
      <div
        className="pointer-events-none absolute inset-0 opacity-[0.02]"
        style={{
          backgroundImage:
            "linear-gradient(rgba(255,255,255,0.1) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.1) 1px, transparent 1px)",
          backgroundSize: "48px 48px",
        }}
      />

      <div className="relative z-10 w-full max-w-sm px-6">
        {/* Brand */}
        <div className="mb-10 text-center">
          <h1
            className="font-heading text-4xl font-bold tracking-[0.2em]"
            style={{
              textShadow:
                "0 0 30px oklch(0.852 0.199 91.936 / 0.3), 0 0 60px oklch(0.852 0.199 91.936 / 0.1)",
            }}
          >
            NEON
          </h1>
          <p className="font-heading text-muted-foreground mt-2 text-xs tracking-[0.3em] uppercase">
            {m.auth_sign_in_description()}
          </p>
        </div>

        {/* Form */}
        <form
          onSubmit={(e) => {
            e.preventDefault()
            form.handleSubmit()
          }}
          className="space-y-5"
        >
          <form.Field name="login">
            {(field) => (
              <div className="space-y-2">
                <Label
                  htmlFor="login"
                  className="font-heading text-muted-foreground text-xs tracking-widest uppercase"
                >
                  {m.auth_login_label()}
                </Label>
                <Input
                  id="login"
                  type="text"
                  autoComplete="username"
                  autoFocus
                  value={field.state.value}
                  onBlur={field.handleBlur}
                  onChange={(e) => field.handleChange(e.target.value)}
                  className="h-11 bg-muted/30 font-mono border-border/50 focus:border-primary/50"
                />
              </div>
            )}
          </form.Field>

          <form.Field name="password">
            {(field) => (
              <div className="space-y-2">
                <Label
                  htmlFor="password"
                  className="font-heading text-muted-foreground text-xs tracking-widest uppercase"
                >
                  {m.auth_password_label()}
                </Label>
                <Input
                  id="password"
                  type="password"
                  autoComplete="current-password"
                  value={field.state.value}
                  onBlur={field.handleBlur}
                  onChange={(e) => field.handleChange(e.target.value)}
                  className="h-11 bg-muted/30 font-mono border-border/50 focus:border-primary/50"
                />
              </div>
            )}
          </form.Field>

          {login.isError && (
            <p className="text-destructive text-sm">
              {m.auth_invalid_credentials()}
            </p>
          )}

          <Button
            type="submit"
            disabled={login.isPending}
            className="h-11 w-full font-heading text-xs tracking-[0.15em] uppercase"
          >
            {login.isPending ? "..." : m.auth_submit()}
          </Button>
        </form>

        {/* Version */}
        <p className="text-muted-foreground/40 mt-12 text-center font-mono text-2xs tracking-wider">
          v0.1.0
        </p>
      </div>
    </div>
  )
}
