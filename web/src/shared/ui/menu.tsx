"use client"

import { motion } from "motion/react"
import type { Variants } from "motion/react"
import type { HTMLAttributes } from "react"

import { cn } from "@/shared/lib/utils"

type MenuIconProps = HTMLAttributes<HTMLDivElement> & {
  size?: number
  morph?: boolean
}

const LINE_VARIANTS: Variants = {
  normal: {
    rotate: 0,
    y: 0,
    opacity: 1,
  },
  animate: (custom: number) => ({
    rotate: custom === 1 ? 45 : custom === 3 ? -45 : 0,
    y: custom === 1 ? 6 : custom === 3 ? -6 : 0,
    opacity: custom === 2 ? 0 : 1,
    transition: {
      type: "spring",
      stiffness: 260,
      damping: 20,
    },
  }),
}

function MenuIcon({
  className,
  size = 28,
  morph = false,
  ...props
}: MenuIconProps) {
  const animate = morph ? "animate" : "normal"

  return (
    <div className={cn(className)} {...props}>
      <svg
        fill="none"
        height={size}
        stroke="currentColor"
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="2"
        viewBox="0 0 24 24"
        width={size}
        xmlns="http://www.w3.org/2000/svg"
      >
        <motion.line
          initial="normal"
          animate={animate}
          custom={1}
          variants={LINE_VARIANTS}
          x1="4"
          x2="20"
          y1="6"
          y2="6"
        />
        <motion.line
          initial="normal"
          animate={animate}
          custom={2}
          variants={LINE_VARIANTS}
          x1="4"
          x2="20"
          y1="12"
          y2="12"
        />
        <motion.line
          initial="normal"
          animate={animate}
          custom={3}
          variants={LINE_VARIANTS}
          x1="4"
          x2="20"
          y1="18"
          y2="18"
        />
      </svg>
    </div>
  )
}

export { MenuIcon }
export type { MenuIconProps }
