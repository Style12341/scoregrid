import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "./AuthContext";
import type { Role } from "./AuthContext";

interface Props {
  /** Optional role gate. Omit to require only that the user is logged in. */
  role?: Role;
}

/**
 * Route guard.
 *
 * This hides screens; it does not secure them. Every endpoint behind these
 * routes enforces its own authorization — see docs/contracts.md.
 */
export function RequireAuth({ role }: Props) {
  const { isAuthenticated, hasRole } = useAuth();
  const location = useLocation();

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (role && !hasRole(role)) {
    return <Navigate to="/" replace />;
  }

  return <Outlet />;
}
