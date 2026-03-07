package neon.core

import neon.common.PackagingLevel

/** Configuration that determines which [[PackagingLevel]] values require verification during task
  * completion.
  *
  * @param requiredFor
  *   the set of packaging levels that require verification
  */
case class VerificationProfile(requiredFor: Set[PackagingLevel]):

  /** Checks whether the given packaging level requires verification.
    *
    * @param packagingLevel
    *   the packaging level to check
    * @return
    *   `true` if verification is required, `false` otherwise
    */
  def requiresVerification(packagingLevel: PackagingLevel): Boolean =
    requiredFor.contains(packagingLevel)

/** Factory methods for [[VerificationProfile]]. */
object VerificationProfile:

  /** A profile that disables verification for all packaging levels. */
  val disabled: VerificationProfile = VerificationProfile(Set.empty)
