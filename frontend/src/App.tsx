import { BrowserRouter, Link, Route, Routes } from "react-router-dom";
import { AuthProvider, useAuth } from "./auth/AuthContext";
import { RequireAuth } from "./auth/RequireAuth";

/**
 * Route map for ScoreGrid.
 *
 * Each placeholder names its owning workstream (docs/workstreams.md). Replace a
 * placeholder with the real screen; do not add routes owned by another stream
 * without telling them — this file is shared.
 */
function Placeholder({ title, owner }: { title: string; owner: string }) {
  return (
    <section>
      <h2>{title}</h2>
      <p>
        Not built yet — owned by <strong>{owner}</strong>. See{" "}
        <code>docs/workstreams.md</code>.
      </p>
    </section>
  );
}

function Shell() {
  const { user, isAuthenticated, hasRole, logout } = useAuth();

  return (
    <div>
      <header>
        <h1>
          <Link to="/">ScoreGrid</Link>
        </h1>
        <nav>
          <Link to="/tournaments">Tournaments</Link>{" "}
          <Link to="/predictions">My predictions</Link>{" "}
          <Link to="/rankings/global">Global ranking</Link>{" "}
          {hasRole("ADMIN") && <Link to="/admin">Admin</Link>}{" "}
          {isAuthenticated ? (
            <button onClick={logout}>Log out ({user?.username})</button>
          ) : (
            <Link to="/login">Log in</Link>
          )}
        </nav>
      </header>

      <main>
        <Routes>
          {/* Public — Stream A (Bernard) */}
          <Route path="/login" element={<Placeholder title="Log in" owner="Stream A — Bernard" />} />
          <Route path="/register" element={<Placeholder title="Register" owner="Stream A — Bernard" />} />

          {/* Authenticated */}
          <Route element={<RequireAuth />}>
            <Route path="/" element={<Placeholder title="Dashboard" owner="Stream A — Bernard" />} />
            <Route
              path="/rankings/global"
              element={<Placeholder title="Global ranking" owner="Stream A — Bernard" />}
            />
            <Route
              path="/rankings/tournament/:tournamentId"
              element={<Placeholder title="Tournament ranking" owner="Stream A — Bernard" />}
            />

            <Route
              path="/tournaments"
              element={<Placeholder title="Tournaments" owner="Stream B — Paggi" />}
            />
            <Route
              path="/tournaments/:tournamentId"
              element={<Placeholder title="Tournament detail" owner="Stream B — Paggi" />}
            />

            <Route
              path="/predictions"
              element={<Placeholder title="My predictions" owner="Stream C — Werlen" />}
            />
            <Route
              path="/matches/:matchId/predict"
              element={<Placeholder title="Prediction form" owner="Stream C — Werlen" />}
            />
          </Route>

          {/* Admin only */}
          <Route element={<RequireAuth role="ADMIN" />}>
            <Route path="/admin" element={<Placeholder title="Admin panel" owner="Stream B — Paggi" />} />
            <Route
              path="/admin/results"
              element={<Placeholder title="Load results" owner="Stream C — Werlen" />}
            />
          </Route>

          <Route path="*" element={<Placeholder title="Not found" owner="—" />} />
        </Routes>
      </main>
    </div>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Shell />
      </AuthProvider>
    </BrowserRouter>
  );
}
