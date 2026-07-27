import * as React from "react"
import { cva, type VariantProps } from "class-variance-authority"
import { Slot } from "radix-ui"

import { cn } from "@/lib/utils"

const badgeVariants = cva(
  // Mock reference: .badge — pill, 5px/10px padding, 12px text, weight 700.
  "inline-flex w-fit shrink-0 items-center justify-center gap-1 overflow-hidden rounded-full border border-transparent px-2.5 py-[5px] text-xs font-bold whitespace-nowrap transition-[color,box-shadow] focus-visible:ring-[3px] focus-visible:ring-ring/50 [&>svg]:pointer-events-none [&>svg]:size-3",
  {
    variants: {
      /*
       * The four status variants are the mock's own palette, one per state a
       * tournament or match can be in. They are hard-coded hex-for-hex rather
       * than derived from the theme because they are a status vocabulary, not
       * a theme colour — ACTIVE must stay green if the brand blue changes.
       */
      variant: {
        // .badge.active — ACTIVE tournament.
        active: "bg-[#dcfce7] text-[#166534]",
        // .badge.draft — DRAFT tournament.
        draft: "bg-[#fef3c7] text-[#92400e]",
        // .badge.finished — FINISHED tournament or match.
        finished: "bg-[#e0e7ff] text-[#3730a3]",
        // .badge.closed — predictions locked, or CANCELLED.
        closed: "bg-[#fee2e2] text-[#991b1b]",

        default: "bg-primary text-primary-foreground [a&]:hover:bg-primary/90",
        secondary:
          "bg-secondary text-secondary-foreground [a&]:hover:bg-secondary/90",
        destructive:
          "bg-destructive text-destructive-foreground [a&]:hover:bg-destructive/90",
        outline:
          "border-border text-foreground [a&]:hover:bg-muted [a&]:hover:text-accent-foreground",
      },
    },
    defaultVariants: {
      variant: "default",
    },
  }
)

function Badge({
  className,
  variant = "default",
  asChild = false,
  ...props
}: React.ComponentProps<"span"> &
  VariantProps<typeof badgeVariants> & { asChild?: boolean }) {
  const Comp = asChild ? Slot.Root : "span"

  return (
    <Comp
      data-slot="badge"
      data-variant={variant}
      className={cn(badgeVariants({ variant }), className)}
      {...props}
    />
  )
}

export { Badge, badgeVariants }
