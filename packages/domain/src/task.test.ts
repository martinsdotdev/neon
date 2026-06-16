import { describe, expect, it } from "vitest"
import { ALL_TASK_STATES, LEGAL_TRANSITIONS, type TaskState } from "./task"

const TERMINAL_STATES: ReadonlyArray<TaskState> = ["Completed", "Cancelled"]

describe("LEGAL_TRANSITIONS", () => {
  it("covers every task state", () => {
    for (const state of ALL_TASK_STATES) {
      expect(LEGAL_TRANSITIONS[state]).toBeDefined()
    }
  })

  it("only transitions to valid task states", () => {
    for (const targets of Object.values(LEGAL_TRANSITIONS)) {
      for (const target of targets) {
        expect(ALL_TASK_STATES).toContain(target)
      }
    }
  })

  it("has no exits from terminal states", () => {
    for (const state of TERMINAL_STATES) {
      expect(LEGAL_TRANSITIONS[state]).toEqual([])
    }
  })

  it("never allows a state to transition to itself", () => {
    for (const state of ALL_TASK_STATES) {
      expect(LEGAL_TRANSITIONS[state]).not.toContain(state)
    }
  })

  it("keeps the happy-path chain reachable (Planned to Completed)", () => {
    expect(LEGAL_TRANSITIONS.Planned).toContain("Allocated")
    expect(LEGAL_TRANSITIONS.Allocated).toContain("Assigned")
    expect(LEGAL_TRANSITIONS.Assigned).toContain("Completed")
  })

  it("lets every non-terminal state be cancelled", () => {
    for (const state of ALL_TASK_STATES) {
      if (!TERMINAL_STATES.includes(state)) {
        expect(LEGAL_TRANSITIONS[state]).toContain("Cancelled")
      }
    }
  })
})
