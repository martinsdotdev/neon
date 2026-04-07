package neon.app

import neon.core.{WaveDispatchRules, WaveDispatchRulesProvider}

/** Default implementation returning standard dispatch rules. */
class DefaultWaveDispatchRulesProvider extends WaveDispatchRulesProvider:
  def current(): WaveDispatchRules = WaveDispatchRules()
