import { Link, useLocation } from "react-router-dom";
import {
  ClipboardCheck,
  LayoutDashboard,
  Settings,
  Target,
  Trophy,
  BarChart3,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { useAuth } from "@/auth/AuthContext";
import { cn } from "@/lib/utils";

interface NavItem {
  to: string;
  label: string;
  icon: LucideIcon;
  /** Exact match only — otherwise "/" would light up on every route. */
  end?: boolean;
  /**
   * Extra path prefixes that belong to this section, so nested screens keep
   * their section lit: a match's prediction page lives under Torneos even
   * though its URL starts with /matches.
   */
  matchPrefixes?: string[];
}

function matchesPrefix(pathname: string, prefix: string): boolean {
  return pathname === prefix || pathname.startsWith(`${prefix}/`);
}

function isItemActive(pathname: string, { to, end, matchPrefixes = [] }: NavItem): boolean {
  const ownMatch = end ? pathname === to : matchesPrefix(pathname, to);
  return ownMatch || matchPrefixes.some((prefix) => matchesPrefix(pathname, prefix));
}

const playerNav: NavItem[] = [
  { to: "/", label: "Panel principal", icon: LayoutDashboard, end: true },
  { to: "/tournaments", label: "Torneos", icon: Trophy, matchPrefixes: ["/matches"] },
  { to: "/predictions", label: "Mis pronósticos", icon: Target },
  { to: "/rankings/global", label: "Rankings", icon: BarChart3, matchPrefixes: ["/rankings"] },
];

const adminNav: NavItem[] = [
  {
    to: "/admin",
    label: "Panel admin",
    icon: Settings,
    end: true,
    matchPrefixes: ["/admin/tournaments"],
  },
  { to: "/admin/results", label: "Cargar resultados", icon: ClipboardCheck },
];

function NavGroup({ title, items }: { title: string; items: NavItem[] }) {
  const { pathname } = useLocation();

  return (
    <div className="mb-6">
      <p className="mb-2.5 text-[11px] uppercase tracking-[0.12em] text-sidebar-muted">
        {title}
      </p>
      {items.map((item) => {
        const { to, label, icon: Icon } = item;
        const isActive = isItemActive(pathname, item);
        return (
          <Link
            key={to}
            to={to}
            aria-current={isActive ? "page" : undefined}
            className={cn(
              "mb-1.5 flex items-center gap-2.5 rounded-md px-3.5 py-3 text-sm text-sidebar-foreground transition-colors duration-200",
              "hover:bg-sidebar-accent",
              isActive && "bg-sidebar-accent font-semibold",
            )}
          >
            <Icon className="size-4" aria-hidden="true" />
            {label}
          </Link>
        );
      })}
    </div>
  );
}

/**
 * Mock reference: .sidebar — fixed 260px column, dark gradient, grouped nav.
 *
 * The admin group is hidden for non-admins. That is a convenience, not access
 * control: every route is guarded by RequireAuth and every endpoint enforces
 * roles server-side.
 */
export function Sidebar() {
  const { hasRole } = useAuth();

  return (
    <aside className="sticky top-0 h-screen bg-linear-to-b from-sidebar to-sidebar-end px-5 py-6 text-white">
      <div className="mb-8 flex items-center gap-3">
        <div className="grid size-11 place-items-center rounded-[14px] bg-linear-to-br from-primary to-success text-xl font-black">
          SG
        </div>
        <div>
          <p className="text-[22px] leading-none font-bold">ScoreGrid</p>
          <span className="text-xs text-[#cbd5e1]">Prode deportivo</span>
        </div>
      </div>

      <nav>
        <NavGroup title="Usuario" items={playerNav} />
        {hasRole("ADMIN") && <NavGroup title="Administrador" items={adminNav} />}
      </nav>
    </aside>
  );
}
