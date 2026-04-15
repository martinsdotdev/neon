import { createRouter as createTanStackRouter } from "@tanstack/react-router"
import { setupRouterSsrQueryIntegration } from "@tanstack/react-router-ssr-query"
import { queryClient } from "@/shared/api/query-client"
import { routeTree } from "./routeTree.gen"

export function getRouter() {
  const router = createTanStackRouter({
    context: { queryClient },
    defaultPreload: "intent",

    defaultPreloadStaleTime: 0,
    routeTree,
    scrollRestoration: true,
  })

  setupRouterSsrQueryIntegration({ queryClient, router })

  return router
}

declare module "@tanstack/react-router" {
  interface Register {
    router: ReturnType<typeof getRouter>
  }
}
