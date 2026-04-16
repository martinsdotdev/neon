import { useState } from "react"
import { createFileRoute, redirect, useNavigate } from "@tanstack/react-router"
import { useForm } from "@tanstack/react-form"
import { z } from "zod"
import { Eye, EyeOff } from "lucide-react"
import { useLogin, authQueries } from "@/shared/api/auth"
import { Alert, AlertDescription } from "@/shared/ui/alert"
import { Button } from "@/shared/ui/button"
import { Card, CardContent } from "@/shared/ui/card"
import { Input } from "@/shared/ui/input"
import { Label } from "@/shared/ui/label"
import { Spinner } from "@/shared/ui/spinner"
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

  const [showPassword, setShowPassword] = useState(false)

  return (
    <div className="flex min-h-svh items-center justify-center bg-background px-4">
      <div className="w-full max-w-sm">
        {/* Brand */}
        <div className="mb-8 text-center">
          <h1 className="font-heading text-4xl font-bold tracking-widest">
            NEON
          </h1>
          <p className="mt-2 text-sm text-muted-foreground">
            {m.auth_sign_in_description()}
          </p>
        </div>

        <Card>
          <CardContent className="pt-6">
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
                    <Label htmlFor="login">
                      {m.auth_login_label()}
                    </Label>
                    <Input
                      id="login"
                      type="text"
                      autoComplete="username"
                      autoFocus
                      value={field.state.value}
                      onBlur={field.handleBlur}
                      onChange={(e) =>
                        field.handleChange(e.target.value)
                      }
                      className="h-11 font-mono"
                    />
                  </div>
                )}
              </form.Field>

              <form.Field name="password">
                {(field) => (
                  <div className="space-y-2">
                    <Label htmlFor="password">
                      {m.auth_password_label()}
                    </Label>
                    <div className="relative">
                      <Input
                        id="password"
                        type={showPassword ? "text" : "password"}
                        autoComplete="current-password"
                        value={field.state.value}
                        onBlur={field.handleBlur}
                        onChange={(e) =>
                          field.handleChange(e.target.value)
                        }
                        className="h-11 pe-10 font-mono"
                      />
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon-sm"
                        className="absolute end-1.5 top-1/2 -translate-y-1/2 text-muted-foreground"
                        onClick={() =>
                          setShowPassword((prev) => !prev)
                        }
                        tabIndex={-1}
                      >
                        {showPassword ? (
                          <EyeOff className="size-4" />
                        ) : (
                          <Eye className="size-4" />
                        )}
                      </Button>
                    </div>
                  </div>
                )}
              </form.Field>

              {login.isError && (
                <Alert variant="destructive">
                  <AlertDescription>
                    {m.auth_invalid_credentials()}
                  </AlertDescription>
                </Alert>
              )}

              <Button
                type="submit"
                disabled={login.isPending}
                className="h-11 w-full"
              >
                {login.isPending ? (
                  <Spinner />
                ) : (
                  m.auth_submit()
                )}
              </Button>
            </form>
          </CardContent>
        </Card>

        {/* Version */}
        <p className="mt-8 text-center font-mono text-2xs text-muted-foreground/40 tracking-wider">
          v0.1.0
        </p>
      </div>
    </div>
  )
}
