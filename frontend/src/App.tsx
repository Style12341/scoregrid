import { BrowserRouter, Route, Routes } from "react-router-dom";
import { AuthProvider } from "./auth/AuthContext";
import { RequireAuth } from "./auth/RequireAuth";
import { AppLayout } from "./components/layout/AppLayout";
import { EmptyState } from "./components/common/states";
import { LoginPage } from "./features/auth/LoginPage";
import { RegisterPage } from "./features/auth/RegisterPage";
import { DashboardPage } from "./features/dashboard/DashboardPage";
import { GlobalRankingPage } from "./features/rankings/GlobalRankingPage";
import { TournamentRankingPage } from "./features/rankings/TournamentRankingPage";
import { PredictionPage } from "./features/predictions/PredictionPage";
import { MyPredictionsPage } from "./features/predictions/MyPredictionsPage";
import { AdminResultsPage } from "./features/admin/AdminResultsPage";
import { TournamentListPage } from "./features/tournaments/pages/TournamentListPage";
import { TournamentDetailPage } from "./features/tournaments/pages/TournamentDetailPage";
import { AdminDashboardPage } from "./features/admin/pages/AdminDashboardPage";
import { AdminTournamentManagePage } from "./features/admin/pages/AdminTournamentManagePage";

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          {/* Public — Stream A (Bernard). These render outside the app shell. */}
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />

          {/* Everything below sits inside the sidebar shell. */}
          <Route element={<RequireAuth />}>
            <Route element={<AppLayout />}>
              <Route path="/" element={<DashboardPage />} />
              <Route path="/rankings/global" element={<GlobalRankingPage />} />
              <Route
                path="/rankings/tournament/:tournamentId"
                element={<TournamentRankingPage />}
              />

              <Route path="/tournaments" element={<TournamentListPage />} />
              <Route
                path="/tournaments/:tournamentId"
                element={<TournamentDetailPage />}
              />

              <Route
                path="/predictions"
                element={<MyPredictionsPage />}
              />
              <Route
                path="/matches/:matchId/predict"
                element={<PredictionPage />}
              />
            </Route>
          </Route>

          {/* Admin only */}
          <Route element={<RequireAuth role="ADMIN" />}>
            <Route element={<AppLayout />}>
              <Route path="/admin" element={<AdminDashboardPage />} />
              <Route
                path="/admin/results"
                element={<AdminResultsPage />}
              />
              <Route
                path="/admin/tournaments/:tournamentId"
                element={<AdminTournamentManagePage />}
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
