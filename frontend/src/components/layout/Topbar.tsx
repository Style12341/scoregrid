import { LogOut } from "lucide-react";
import { useAuth } from "@/auth/AuthContext";
import { Button } from "@/components/ui/button";
import { usePageHeaderValue } from "./page-header";

/** First letters of the username, for the avatar circle. Mock: .avatar */
function initials(username: string): string {
  return username
    .split(/[\s._-]+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? "")
    .join("");
}

/** Mock reference: .topbar — page heading left, user chip right. */
export function Topbar() {
  const { user, hasRole, logout } = useAuth();
  const { title, subtitle } = usePageHeaderValue();

  return (
    <header className="mb-7 flex items-center justify-between gap-4">
      <div>
        <h2 className="mb-1 text-[28px] font-bold">{title}</h2>
        {subtitle && <p className="text-sm text-muted-foreground">{subtitle}</p>}
      </div>

      {user && (
        <div className="flex items-center gap-3 rounded-full bg-card p-2.5 pr-3.5 shadow-card">
          <div className="grid size-9 place-items-center rounded-full bg-primary font-bold text-primary-foreground">
            {initials(user.username)}
          </div>
          <div>
            <strong className="text-sm">{user.username}</strong>
            <p className="text-xs text-muted-foreground">
              {hasRole("ADMIN") ? "Administrador" : "Jugador"}
            </p>
          </div>
          <Button
            variant="ghost"
            size="icon-sm"
            onClick={logout}
            aria-label="Cerrar sesión"
            title="Cerrar sesión"
          >
            <LogOut />
          </Button>
        </div>
      )}
    </header>
  );
}
