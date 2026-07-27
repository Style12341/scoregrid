import { Outlet } from "react-router-dom";
import { PageHeaderProvider } from "./page-header";
import { Sidebar } from "./Sidebar";
import { Topbar } from "./Topbar";

/**
 * Mock reference: .app — a 260px sidebar beside a scrolling main column.
 *
 * Rendered as a layout route, so every authenticated screen gets the shell
 * without knowing it exists.
 */
export function AppLayout() {
  return (
    <PageHeaderProvider>
      <div className="grid min-h-screen grid-cols-[260px_1fr]">
        <Sidebar />
        <main className="px-8 pt-6 pb-12">
          <Topbar />
          <Outlet />
        </main>
      </div>
    </PageHeaderProvider>
  );
}
