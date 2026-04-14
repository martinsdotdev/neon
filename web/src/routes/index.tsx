import { createFileRoute } from "@tanstack/react-router"
import { Button } from "@/shared/ui/button"
import * as m from "@/paraglide/messages.js"

export const Route = createFileRoute("/")({ component: App })

function App() {
  return (
    <div className="flex min-h-svh p-6">
      <div className="flex max-w-md min-w-0 flex-col gap-4 text-sm leading-loose">
        <div>
          <h1 className="font-medium">{m.project_ready_heading()}</h1>
          <p>{m.project_ready_description()}</p>
          <p>{m.project_ready_hint()}</p>
          <Button className="mt-2">{m.button_label()}</Button>
        </div>
      </div>
    </div>
  )
}
