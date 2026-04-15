import { createFileRoute } from "@tanstack/react-router"
import { Button } from "@/shared/ui/button"
import { ModeToggle } from "@/shared/ui/mode-toggle"
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
          <div className="flex items-center gap-2">
            <Button className="mt-2">{m.button_label()}</Button>
            <ModeToggle />
          </div>
        </div>
      </div>
    </div>
  )
}
