import {
  HeadContent,
  Scripts,
  createRootRouteWithContext,
} from "@tanstack/react-router"
import { ReactQueryDevtoolsPanel } from "@tanstack/react-query-devtools"
import { TanStackRouterDevtoolsPanel } from "@tanstack/react-router-devtools"
import { TanStackDevtools } from "@tanstack/react-devtools"
import type { QueryClient } from "@tanstack/react-query"
import { getLocale } from "@/paraglide/runtime.js"
import * as m from "@/paraglide/messages.js"
import { ThemeProvider } from "@/shared/theme-provider"

import appCss from "../app/styles.css?url"

interface RouterContext {
  queryClient: QueryClient
}

export const Route = createRootRouteWithContext<RouterContext>()({
  head: () => ({
    links: [
      {
        href: appCss,
        rel: "stylesheet",
      },
    ],
    meta: [
      {
        charSet: "utf-8",
      },
      {
        content: "width=device-width, initial-scale=1",
        name: "viewport",
      },
      {
        title: m.app_title(),
      },
    ],
  }),
  shellComponent: RootDocument,
})

function RootDocument({ children }: { children: React.ReactNode }) {
  return (
    <html lang={getLocale()} suppressHydrationWarning>
      <head>
        <HeadContent />
      </head>
      <body>
        <ThemeProvider attribute="class" defaultTheme="system" enableSystem>
          {children}
        </ThemeProvider>
        <TanStackDevtools
          config={{
            position: "bottom-right",
          }}
          plugins={[
            {
              name: "Tanstack Router",
              render: <TanStackRouterDevtoolsPanel />,
            },
            {
              name: "React Query",
              render: <ReactQueryDevtoolsPanel />,
            },
          ]}
        />
        <Scripts />
      </body>
    </html>
  )
}
