package neon.common

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

opaque type SkuId = UUID

object SkuId:
  def apply(): SkuId = UuidCreator.getTimeOrderedEpoch()
  def apply(value: UUID): SkuId = value
  extension (id: SkuId) def value: UUID = id
