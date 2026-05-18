// Token source-of-truth for the mobile app. Values mirror the OKLch CSS
// variables in apps/web/src/app/styles.css. Web continues to consume those
// CSS variables directly; a future build step can regenerate styles.css from
// this object so the two never drift.
//
// Each theme is a flat record of named colors keyed by semantic role. Mobile
// passes the theme objects to react-native-unistyles. React Native cannot
// parse oklch() strings, so consumers must convert via the `oklchToRgba`
// helper in rn-theme.ts (uses culori).

export interface ThemeColors {
  background: string
  foreground: string
  card: string
  cardForeground: string
  primary: string
  primaryForeground: string
  secondary: string
  secondaryForeground: string
  muted: string
  mutedForeground: string
  accent: string
  accentForeground: string
  destructive: string
  destructiveForeground: string
  border: string
  input: string
  ring: string
  success: string
  warning: string
  info: string
  // Domain state colors — match StateBadge / kanban / stepper hues used by web.
  statePlanned: string
  stateAllocated: string
  stateAssigned: string
  stateCompleted: string
  stateCancelled: string
  statePicked: string
  stateReady: string
  stateActive: string
  stateIdle: string
  // Priority hues for task cards.
  priorityCritical: string
  priorityHigh: string
  priorityNormal: string
  priorityLow: string
}

export const LIGHT: ThemeColors = {
  accent: "oklch(0.963 0.002 197.1)",
  accentForeground: "oklch(0.218 0.008 223.9)",
  background: "oklch(1 0 0)",
  border: "oklch(0.925 0.005 214.3)",
  card: "oklch(1 0 0)",
  cardForeground: "oklch(0.148 0.004 228.8)",
  destructive: "oklch(0.577 0.245 27.325)",
  destructiveForeground: "oklch(0.45 0.18 27)",
  foreground: "oklch(0.148 0.004 228.8)",
  info: "oklch(0.6 0.18 290)",
  input: "oklch(0.925 0.005 214.3)",
  muted: "oklch(0.963 0.002 197.1)",
  mutedForeground: "oklch(0.56 0.021 213.5)",
  primary: "oklch(0.527 0.154 150.069)",
  primaryForeground: "oklch(0.982 0.018 155.826)",
  priorityCritical: "oklch(0.58 0.2 22)",
  priorityHigh: "oklch(0.68 0.15 50)",
  priorityLow: "oklch(0.75 0.02 230)",
  priorityNormal: "oklch(0.65 0.03 230)",
  ring: "oklch(0.723 0.014 214.4)",
  secondary: "oklch(0.967 0.001 286.375)",
  secondaryForeground: "oklch(0.21 0.006 285.885)",
  stateActive: "oklch(0.55 0.14 150)",
  stateAllocated: "oklch(0.68 0.13 80)",
  stateAssigned: "oklch(0.58 0.14 240)",
  stateCancelled: "oklch(0.58 0.17 22)",
  stateCompleted: "oklch(0.55 0.14 150)",
  stateIdle: "oklch(0.65 0.03 230)",
  statePicked: "oklch(0.62 0.13 195)",
  statePlanned: "oklch(0.65 0.03 230)",
  stateReady: "oklch(0.6 0.15 290)",
  success: "oklch(0.55 0.16 150)",
  warning: "oklch(0.78 0.14 80)",
}

export const DARK: ThemeColors = {
  accent: "oklch(0.275 0.011 216.9)",
  accentForeground: "oklch(0.987 0.002 197.1)",
  background: "oklch(0.148 0.004 228.8)",
  border: "oklch(0.35 0.005 220)",
  card: "oklch(0.218 0.008 223.9)",
  cardForeground: "oklch(0.987 0.002 197.1)",
  destructive: "oklch(0.704 0.191 22.216)",
  destructiveForeground: "oklch(0.93 0.07 27)",
  foreground: "oklch(0.987 0.002 197.1)",
  info: "oklch(0.72 0.16 290)",
  input: "oklch(0.35 0.01 220)",
  muted: "oklch(0.275 0.011 216.9)",
  mutedForeground: "oklch(0.723 0.014 214.4)",
  primary: "oklch(0.448 0.119 151.328)",
  primaryForeground: "oklch(0.982 0.018 155.826)",
  priorityCritical: "oklch(0.72 0.2 22)",
  priorityHigh: "oklch(0.78 0.15 50)",
  priorityLow: "oklch(0.58 0.02 230)",
  priorityNormal: "oklch(0.7 0.03 230)",
  ring: "oklch(0.56 0.021 213.5)",
  secondary: "oklch(0.274 0.006 286.033)",
  secondaryForeground: "oklch(0.985 0 0)",
  stateActive: "oklch(0.72 0.15 150)",
  stateAllocated: "oklch(0.78 0.13 80)",
  stateAssigned: "oklch(0.7 0.14 240)",
  stateCancelled: "oklch(0.72 0.16 22)",
  stateCompleted: "oklch(0.72 0.15 150)",
  stateIdle: "oklch(0.7 0.03 230)",
  statePicked: "oklch(0.74 0.12 195)",
  statePlanned: "oklch(0.7 0.03 230)",
  stateReady: "oklch(0.72 0.14 290)",
  success: "oklch(0.7 0.16 150)",
  warning: "oklch(0.82 0.14 80)",
}

export type ThemeName = "light" | "dark"

export const THEMES: Record<ThemeName, ThemeColors> = {
  dark: DARK,
  light: LIGHT,
}
