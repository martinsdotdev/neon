package neon.core

/** Provides current dispatch validation rules for wave planning.
  */
trait WaveDispatchRulesProvider:
  def current(): WaveDispatchRules
