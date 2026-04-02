import { createRouter as createTanStackRouter } from "@tanstack/react-router"
import { routerWithQueryClient } from "@tanstack/react-router-with-query"
import { routeTree } from "./routeTree.gen"
import { createQueryClient } from "@/shared/api/query-client"

export function getRouter() {
  const queryClient = createQueryClient()

  const router = routerWithQueryClient(
    createTanStackRouter({
      routeTree,
      context: { queryClient },
      scrollRestoration: true,
      defaultPreload: "intent",
      defaultPreloadStaleTime: 0,
    }),
    queryClient
  )

  return router
}

declare module "@tanstack/react-router" {
  interface Register {
    router: ReturnType<typeof getRouter>
  }
}
