package neon.app

/** Provides current dispatch validation rules for wave planning.
  */
trait WaveDispatchRulesProvider:
  def current(): WaveDispatchRules
