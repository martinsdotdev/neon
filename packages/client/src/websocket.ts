// Minimal reconnect-with-backoff WebSocket wrapper used by the mobile
// notifications hook and (eventually) by web dashboard live tickers.

export interface NotificationSocketConfig {
  url: string
  getToken: () => string | undefined | null | Promise<string | undefined | null>
  onEvent: (event: NotificationEvent) => void
  onStatus?: (status: SocketStatus) => void
  // Number of consecutive reconnect failures before consumers should switch
  // to polling. Default 3.
  failureThreshold?: number
}

export interface NotificationEvent {
  type: string
  [key: string]: unknown
}

export type SocketStatus =
  | "connecting"
  | "open"
  | "closed"
  | "reconnecting"
  | "exhausted"

export interface NotificationSocket {
  close: () => void
}

const INITIAL_BACKOFF_MS = 500
const MAX_BACKOFF_MS = 30_000

export const createNotificationSocket = (
  config: NotificationSocketConfig,
): NotificationSocket => {
  let closed = false
  let socket: WebSocket | undefined
  let backoffMs = INITIAL_BACKOFF_MS
  let consecutiveFailures = 0
  const threshold = config.failureThreshold ?? 3

  const setStatus = (status: SocketStatus): void => {
    config.onStatus?.(status)
  }

  const connect = async (): Promise<void> => {
    if (closed) return
    setStatus(consecutiveFailures > 0 ? "reconnecting" : "connecting")
    const token = await config.getToken()
    const separator = config.url.includes("?") ? "&" : "?"
    const url = token
      ? `${config.url}${separator}token=${encodeURIComponent(token)}`
      : config.url
    socket = new WebSocket(url)
    socket.onopen = () => {
      backoffMs = INITIAL_BACKOFF_MS
      consecutiveFailures = 0
      setStatus("open")
    }
    socket.onmessage = (event) => {
      try {
        const parsed = JSON.parse(event.data) as NotificationEvent
        config.onEvent(parsed)
      } catch {
        // Drop malformed payloads silently; backend contract is JSON.
      }
    }
    socket.onerror = () => {
      consecutiveFailures += 1
    }
    socket.onclose = () => {
      socket = undefined
      if (closed) {
        setStatus("closed")
        return
      }
      if (consecutiveFailures >= threshold) {
        setStatus("exhausted")
        return
      }
      const delay = backoffMs
      backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS)
      setTimeout(() => {
        void connect()
      }, delay)
    }
  }

  void connect()

  return {
    close: () => {
      closed = true
      socket?.close()
    },
  }
}
