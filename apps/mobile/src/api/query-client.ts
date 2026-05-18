import { QueryClient } from "@tanstack/react-query"

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // The mobile app is "resilient online": a failed query retries up to
      // twice with exponential backoff (handled by TanStack Query default
      // exponential strategy). On wifi flap, the in-flight request fails and
      // refetches when network is back via the focus listener.
      retry: 2,
      // 30 s matches web's QueryClient and balances staleness against
      // re-fetch churn on a handheld scanner that switches screens often.
      staleTime: 30_000,
    },
  },
})
