import { useState } from "react";
import type { FormEvent } from "react";
import { Link, Navigate, useLocation, useNavigate } from "react-router-dom";
import { isAxiosError } from "axios";
import { useAuth } from "@/auth/AuthContext";
import { FormField } from "@/components/common/FormField";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

/** Mock reference: .login-screen — hero panel beside the credentials card. */
export function LoginPage() {
  const { login, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [usernameOrEmail, setUsernameOrEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (isAuthenticated) {
    return <Navigate to="/" replace />;
  }

  // Send the user back where RequireAuth interrupted them, not always to "/".
  // RequireAuth stores the whole Location object, so read pathname off it.
  const from =
    (location.state as { from?: { pathname?: string } } | null)?.from?.pathname ?? "/";

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);

    try {
      await login(usernameOrEmail, password);
      navigate(from, { replace: true });
    } catch (cause) {
      /*
       * The contract deliberately does not distinguish "no such user" from
       * "wrong password", so neither does this message. Anything else is a
       * user-enumeration oracle.
       */
      if (isAxiosError(cause) && cause.response?.status === 401) {
        setError("Usuario o contraseña incorrectos.");
      } else if (isAxiosError(cause) && !cause.response) {
        setError("No pudimos contactar al servidor. Intentá de nuevo en unos segundos.");
      } else {
        setError("Ocurrió un error inesperado. Intentá de nuevo.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="grid min-h-screen bg-sidebar lg:grid-cols-[1.1fr_0.9fr]">
      <section className="hidden flex-col justify-center bg-[radial-gradient(circle_at_20%_20%,rgba(37,99,235,0.45),transparent_35%),radial-gradient(circle_at_80%_80%,rgba(34,197,94,0.35),transparent_30%)] p-18 text-white lg:flex">
        <div className="mb-9 flex items-center gap-3">
          <div className="grid size-11 place-items-center rounded-[14px] bg-linear-to-br from-primary to-success text-xl font-black">
            SG
          </div>
          <div>
            <p className="text-[28px] leading-none font-bold">ScoreGrid</p>
            <span className="text-xs text-[#cbd5e1]">
              Sistema distribuido de pronósticos deportivos
            </span>
          </div>
        </div>

        <h1 className="mb-4 text-5xl font-bold">
          Pronosticá. Sumá puntos. Liderá el ranking.
        </h1>
        <p className="max-w-xl text-lg leading-relaxed text-[#cbd5e1]">
          Plataforma web para torneos deportivos con grupos, fases eliminatorias,
          pronósticos por partido, rankings por torneo y ranking global.
        </p>
      </section>

      <section className="flex items-center justify-center bg-[#f8fafc] p-10">
        <div className="w-full max-w-[430px] rounded-xl bg-card p-8 shadow-card">
          <h2 className="mb-2 text-[28px] font-bold">Iniciar sesión</h2>
          <p className="mb-6 text-sm text-muted-foreground">
            Ingresá para participar en torneos o administrar ScoreGrid.
          </p>

          <form onSubmit={handleSubmit} className="flex flex-col gap-3.5" noValidate>
            <FormField label="Usuario o email" required>
              {(field) => (
                <Input
                  {...field}
                  type="text"
                  autoComplete="username"
                  value={usernameOrEmail}
                  onChange={(event) => setUsernameOrEmail(event.target.value)}
                />
              )}
            </FormField>

            <FormField label="Contraseña" required>
              {(field) => (
                <Input
                  {...field}
                  type="password"
                  autoComplete="current-password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                />
              )}
            </FormField>

            {error && (
              <p
                role="alert"
                className="rounded-md bg-destructive/10 px-3.5 py-3 text-sm font-bold text-destructive"
              >
                {error}
              </p>
            )}

            <Button type="submit" size="block" className="mt-2" disabled={submitting}>
              {submitting ? "Ingresando…" : "Ingresar"}
            </Button>
          </form>

          <p className="mt-5 text-center text-sm text-muted-foreground">
            ¿No tenés cuenta?{" "}
            <Link to="/register" className="font-bold text-primary hover:underline">
              Registrate
            </Link>
          </p>
        </div>
      </section>
    </div>
  );
}
