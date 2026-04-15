"use client"

import * as LucideIcons from "lucide-react"

const IconPlaceholder = ({
  lucide,
  className,
}: {
  className?: string
  hugeicons?: string
  lucide?: string
  phosphor?: string
  remixicon?: string
  tabler?: string
}) => {
  if (!lucide) return null
  const Icon = (LucideIcons as Record<string, LucideIcons.LucideIcon>)[lucide]
  if (!Icon) return null
  return <Icon className={className} />
}

export { IconPlaceholder }
