import { paraglideMiddleware } from "./paraglide/server.js"
import handler from "@tanstack/react-start/server-entry"

export default {
  fetch(request: Request): Promise<Response> {
    return paraglideMiddleware(request, ({ request }) =>
      handler.fetch(request)
    )
  },
}
