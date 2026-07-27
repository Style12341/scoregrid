import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

/**
 * Mock reference: .page-title — section heading with an action on the right.
 *
 * This is the heading *inside* a page or card. The heading at the top of the
 * screen comes from usePageHeader instead.
 */
export function PageTitle({
  title,
  action,
  className,
}: {
  title: ReactNode;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("mb-4 flex items-center justify-between gap-4", className)}>
      <h3 className="text-[22px] font-bold">{title}</h3>
      {action}
    </div>
  );
}
