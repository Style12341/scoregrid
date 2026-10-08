import { Link } from "react-router-dom";
import { ChevronRight } from "lucide-react";
import type { Breadcrumb } from "@/components/layout/page-header";
import { cn } from "@/lib/utils";

/**
 * A trail from the section to the current page. The last crumb is the current
 * page: it is not a link and carries aria-current.
 *
 * Usually set through usePageHeader({ breadcrumbs }) so the topbar renders it;
 * screens rarely need this component directly.
 */
export function Breadcrumbs({
  items,
  className,
}: {
  items: Breadcrumb[];
  className?: string;
}) {
  if (items.length === 0) return null;

  return (
    <nav aria-label="Ruta de navegación" className={className}>
      <ol className="flex flex-wrap items-center gap-1 text-[13px] text-muted-foreground">
        {items.map((item, index) => {
          const isLast = index === items.length - 1;
          return (
            <li key={`${item.label}-${index}`} className="flex items-center gap-1">
              {item.to && !isLast ? (
                <Link
                  to={item.to}
                  className="rounded-sm font-semibold transition-colors hover:text-primary"
                >
                  {item.label}
                </Link>
              ) : (
                <span
                  aria-current={isLast ? "page" : undefined}
                  className={cn(isLast && "font-semibold text-foreground")}
                >
                  {item.label}
                </span>
              )}
              {!isLast && <ChevronRight className="size-3.5" aria-hidden="true" />}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
