import { BrowserRouter, Route, Routes } from "react-router-dom";
import { AuthProvider } from "./auth/AuthContext";
import { RequireAuth } from "./auth/RequireAuth";
import { AppLayout } from "./components/layout/AppLayout";
import { EmptyState } from "./components/common/states";
import { usePageHeader } from "./components/layout/page-header";
import { LoginPage } from "./features/auth/LoginPage";

/**
 * Route map for ScoreGrid.
 *
 * Each placeholder names its owning workstream (docs/workstreams.md). Replace a
 * placeholder with the real screen; do not add routes owned by another stream
 * without telling them — this file is shared.
 */
function Placeholder({ title, owner }: { title: string; owner: string }) {
  usePageHeader(title);

  return (
    <EmptyState
      title="Pantalla en construcción"
      description={`Todavía no está construida. Responsable: ${owner} (ver docs/workstreams.md).`}
    />
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          {/* Public — Stream A (Bernard). These render outside the app shell. */}
          <Route path="/login" element={<LoginPage />} />
          <Route
            path="/register"
            element={<Placeholder title="Crear cuenta" owner="Stream A — Bernard" />}
          />

          {/* Everything below sits inside the sidebar shell. */}
          <Route element={<RequireAuth />}>
            <Route element={<AppLayout />}>
              <Route
                path="/"
                element={<Placeholder title="Panel principal" owner="Stream A — Bernard" />}
              />
              <Route
                path="/rankings/global"
                element={<Placeholder title="Ranking global" owner="Stream A — Bernard" />}
              />
              <Route
                path="/rankings/tournament/:tournamentId"
                element={<Placeholder title="Ranking del torneo" owner="Stream A — Bernard" />}
              />

              <Route
                path="/tournaments"
                element={<Placeholder title="Torneos" owner="Stream B — Paggi" />}
              />
              <Route
                path="/tournaments/:tournamentId"
                element={<Placeholder title="Detalle de torneo" owner="Stream B — Paggi" />}
              />

              <Route
                path="/predictions"
                element={<Placeholder title="Mis pronósticos" owner="Stream C — Werlen" />}
              />
              <Route
                path="/matches/:matchId/predict"
                element={<Placeholder title="Cargar pronóstico" owner="Stream C — Werlen" />}
              />
            </Route>
          </Route>

          {/* Admin only */}
          <Route element={<RequireAuth role="ADMIN" />}>
            <Route element={<AppLayout />}>
              <Route
                path="/admin"
                element={<Placeholder title="Panel admin" owner="Stream B — Paggi" />}
              />
              <Route
                path="/admin/results"
                element={<Placeholder title="Cargar resultados" owner="Stream C — Werlen" />}
              />
            </Route>
          </Route>

          <Route
            path="*"
            element={
              <div className="grid min-h-screen place-items-center p-8">
                <EmptyState
                  title="Página no encontrada"
                  description="La dirección a la que intentaste entrar no existe."
                />
              </div>
            }
          />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}
