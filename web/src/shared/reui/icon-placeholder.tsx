"use client"

import * as LucideIcons from "lucide-react"

/**
 * Resolves an icon from the lucide prop name.
 * Simplified from reUI's multi-library IconPlaceholder.
 */
const IconPlaceholder = ({
  lucide,
  className,
}: {
  className?: string
  lucide?: string
  tabler?: string
}) => {
  if (!lucide) return null
  const Icon = (LucideIcons as Record<string, LucideIcons.LucideIcon>)[lucide]
  if (!Icon) return null
  return <Icon className={className} />
}

export { IconPlaceholder }
