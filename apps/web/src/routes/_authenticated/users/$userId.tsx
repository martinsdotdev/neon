import { Link, createFileRoute } from "@tanstack/react-router"
import { useSuspenseQuery } from "@tanstack/react-query"
import { ArrowLeft } from "lucide-react"
import { userQueries } from "@/shared/api/users-api"
import { Badge } from "@/shared/ui/badge"
import { Button } from "@/shared/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/shared/ui/card"
import { PageHeader } from "@/shared/ui/page-header"

export const Route = createFileRoute("/_authenticated/users/$userId")({
  component: UserDetailPage,
  loader: ({ context, params }) =>
    context.queryClient.ensureQueryData(userQueries.detail(params.userId)),
})

function UserDetailPage() {
  const { userId } = Route.useParams()
  const { data: user } = useSuspenseQuery(userQueries.detail(userId))

  if (!user) {
    return (
      <div>
        <PageHeader title="User not found" />
        <Button render={<Link to="/users" />} variant="ghost">
          <ArrowLeft className="size-4" />
          Back to users
        </Button>
      </div>
    )
  }

  return (
    <div>
      <PageHeader
        actions={
          <div className="flex items-center gap-2">
            <Badge variant="secondary">{user.role}</Badge>
            <Badge variant={user.active ? "default" : "secondary"}>
              {user.active ? "Active" : "Inactive"}
            </Badge>
          </div>
        }
        title={user.name}
      />

      <div className="mb-6">
        <Button render={<Link to="/users" />} size="sm" variant="ghost">
          <ArrowLeft className="size-4" />
          All users
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="font-heading text-sm tracking-wider">
            Details
          </CardTitle>
        </CardHeader>
        <CardContent>
          <dl className="grid grid-cols-2 gap-4 text-sm">
            <div>
              <dt className="mb-1 text-muted-foreground">Name</dt>
              <dd className="font-medium">{user.name}</dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Login</dt>
              <dd className="font-mono">{user.login}</dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Role</dt>
              <dd>{user.role}</dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">Status</dt>
              <dd>{user.active ? "Active" : "Inactive"}</dd>
            </div>
            <div>
              <dt className="mb-1 text-muted-foreground">ID</dt>
              <dd className="font-mono text-xs text-muted-foreground">
                {user.id}
              </dd>
            </div>
          </dl>
        </CardContent>
      </Card>
    </div>
  )
}
