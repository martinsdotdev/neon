package neon.app

import neon.common.PackagingLevel

case class VerificationProfile(requiredFor: Set[PackagingLevel]):
  def requiresVerification(packagingLevel: PackagingLevel): Boolean =
    requiredFor.contains(packagingLevel)

object VerificationProfile:
  val disabled: VerificationProfile = VerificationProfile(Set.empty)
