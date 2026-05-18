// Spacing scale matching the web's Tailwind defaults. Values are in
// React Native logical pixels (no `px` suffix). Mobile screens use these via
// Unistyles `theme.spacing[N]`.

export const SPACING = {
  0: 0,
  1: 4,
  2: 8,
  3: 12,
  4: 16,
  5: 20,
  6: 24,
  8: 32,
  10: 40,
  12: 48,
  16: 64,
  20: 80,
  24: 96,
} as const

export type SpacingKey = keyof typeof SPACING

export const RADIUS = {
  none: 0,
  sm: 6,
  md: 8,
  lg: 10,
  xl: 14,
  full: 9999,
} as const

export type RadiusKey = keyof typeof RADIUS
