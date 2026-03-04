package neon.common

import com.github.f4b6a3.uuid.UuidCreator

import java.util.UUID

/** Unique identifier for a [[neon.task.Task]]. Backed by a time-ordered UUID v7. */
opaque type TaskId = UUID

object TaskId:
  /** Generates a new TaskId backed by a time-ordered UUID v7. */
  def apply(): TaskId = UuidCreator.getTimeOrderedEpoch()

  /** Wraps an existing UUID as a TaskId.
    *
    * @param value
    *   the UUID to wrap
    */
  def apply(value: UUID): TaskId = value

  /** Returns the underlying UUID value. */
  extension (id: TaskId) def value: UUID = id
