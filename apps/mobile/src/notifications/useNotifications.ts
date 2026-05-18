import { createNotificationSocket } from "@neon/client/websocket"
import { useQueryClient } from "@tanstack/react-query"
import { useEffect } from "react"
import { getAuthToken } from "@/src/api/client"

const apiBaseUrl = process.env.EXPO_PUBLIC_API_BASE_URL ?? ""

// Convert http(s)://host/ → ws(s)://host/ for the WebSocket handshake.
const toWsUrl = (httpUrl: string): string =>
  httpUrl.replace(/^http/, "ws").replace(/\/$/, "")

/** Opens a persistent WebSocket to /ws/notifications while the
  * component is mounted. Each received event invalidates the React
  * Query cache so list views refetch automatically.
  *
  * Mount this once near the auth gate (after sign-in) so it lives for
  * the lifetime of the operator session. The hub auto-reconnects with
  * exponential backoff; after 3 failures in 30s the createNotification-
  * Socket emits status "exhausted" and consumers can fall back to
  * polling (`queryClient.refetchOnReconnect`).
  */
export const useNotifications = (enabled: boolean): void => {
  const queryClient = useQueryClient()

  useEffect(() => {
    if (!enabled) return
    const url = `${toWsUrl(apiBaseUrl)}/ws/notifications`
    const socket = createNotificationSocket({
      getToken: getAuthToken,
      onEvent: (event) => {
        if (event.type === "task.assigned") {
          // A new task arrived for this user — refresh any tasks list.
          queryClient.invalidateQueries({ queryKey: ["tasks"] })
        }
      },
      url,
    })
    return () => socket.close()
  }, [enabled, queryClient])
}
