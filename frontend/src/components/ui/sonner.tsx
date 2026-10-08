import type { CSSProperties } from "react"
import { Toaster as Sonner, type ToasterProps } from "sonner"

/**
 * shadcn/ui's sonner wrapper, themed with the ScoreGrid tokens.
 *
 * The upstream version reads the colour scheme from next-themes; this app has a
 * single light theme, so it is fixed to "light". Colours come from index.css
 * through CSS variables — never literals here.
 *
 * Mount <Toaster /> once at the app root and call toast.success / toast.error
 * from anywhere.
 */
function Toaster(props: ToasterProps) {
  return (
    <Sonner
      theme="light"
      position="top-right"
      className="toaster group"
      style={
        {
          "--normal-bg": "var(--popover)",
          "--normal-text": "var(--popover-foreground)",
          "--normal-border": "var(--border)",
          "--border-radius": "var(--radius-md)",
        } as CSSProperties
      }
      toastOptions={{
        classNames: {
          toast: "font-sans shadow-card",
          title: "font-bold",
          description: "text-muted-foreground!",
          success: "[&_[data-icon]]:text-success",
          error: "[&_[data-icon]]:text-destructive",
        },
      }}
      {...props}
    />
  )
}

export { Toaster }
