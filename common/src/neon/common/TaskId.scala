package neon.common

import com.github.f4b6a3.uuid.UuidCreator

import java.util.UUID

opaque type TaskId = UUID

object TaskId:
  def apply(): TaskId = UuidCreator.getTimeOrderedEpoch()
  def apply(value: UUID): TaskId = value
  extension (id: TaskId) def value: UUID = id
