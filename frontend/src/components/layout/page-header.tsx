import { createContext, useContext, useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";

interface PageHeader {
  title: string;
  subtitle?: string;
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
 * Set the title shown in the shared topbar from inside a page.
 *
 * This exists so a screen owns its own heading without editing AppLayout — a
 * route-to-title map in the shared shell would mean Streams B and C editing
 * Stream A's file every time they add a screen.
 *
 *   usePageHeader("Torneos", "Elegí un torneo para pronosticar.");
 */
export function usePageHeader(title: string, subtitle?: string) {
  const context = useContext(PageHeaderContext);
  const setHeader = context?.setHeader;

  useEffect(() => {
    setHeader?.({ title, subtitle });
  }, [setHeader, title, subtitle]);
}
