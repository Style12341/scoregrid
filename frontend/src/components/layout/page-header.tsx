import { createContext, useContext, useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";

export interface Breadcrumb {
  label: string;
  /** Omit on the last crumb: it is the current page, not a link. */
  to?: string;
}

export interface PageHeader {
  title: string;
  subtitle?: string;
  breadcrumbs?: Breadcrumb[];
}

interface PageHeaderContextValue {
  header: PageHeader;
  setHeader: (header: PageHeader) => void;
}

const PageHeaderContext = createContext<PageHeaderContextValue | null>(null);

const DEFAULT_HEADER: PageHeader = { title: "ScoreGrid" };

export function PageHeaderProvider({ children }: { children: ReactNode }) {
  const [header, setHeader] = useState<PageHeader>(DEFAULT_HEADER);
  const value = useMemo(() => ({ header, setHeader }), [header]);

  return <PageHeaderContext.Provider value={value}>{children}</PageHeaderContext.Provider>;
}

export function usePageHeaderValue(): PageHeader {
  const context = useContext(PageHeaderContext);
  return context?.header ?? DEFAULT_HEADER;
}

/**
 * Set the title (and optionally a breadcrumb trail) shown in the shared topbar
 * from inside a page.
 *
 * This exists so a screen owns its own heading without editing AppLayout — a
 * route-to-title map in the shared shell would mean Streams B and C editing
 * Stream A's file every time they add a screen.
 *
 *   usePageHeader("Torneos", "Elegí un torneo para pronosticar.");
 *   usePageHeader({
 *     title: tournament.name,
 *     breadcrumbs: [{ label: "Torneos", to: "/tournaments" }, { label: tournament.name }],
 *   });
 *
 * The topbar renders the title, so a page must not repeat it as an in-body
 * heading.
 */
export function usePageHeader(titleOrHeader: string | PageHeader, subtitle?: string) {
  const context = useContext(PageHeaderContext);
  const setHeader = context?.setHeader;

  const header: PageHeader =
    typeof titleOrHeader === "string" ? { title: titleOrHeader, subtitle } : titleOrHeader;

  // Callers pass inline object and array literals, which are new on every
  // render. Comparing the serialised value keeps the effect from looping.
  const key = JSON.stringify(header);

  useEffect(() => {
    setHeader?.(JSON.parse(key) as PageHeader);
  }, [setHeader, key]);
}
