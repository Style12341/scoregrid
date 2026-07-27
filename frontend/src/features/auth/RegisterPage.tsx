import { useState } from "react";
import type { FormEvent } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { useAuth } from "@/auth/AuthContext";
import { FormField } from "@/components/common/FormField";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { toApiError } from "@/lib/api";
import { register } from "./api";

const USERNAME_MIN = 3;
const USERNAME_MAX = 30;
const PASSWORD_MIN = 8;

interface FieldErrors {
  username?: string;
  email?: string;
  password?: string;
  passwordConfirmation?: string;
}

/**
 * Mock reference: .login-screen — same hero panel as Login, so the two read as
 * one flow rather than two unrelated screens.
 *
 * Registering does not log you in: the contract returns a profile and no token.
 * This page registers, then logs in with the credentials it already has, so the
 * user lands inside the app rather than being sent back to a login form.
 */
export function RegisterPage() {
  const { login, isAuthenticated } = useAuth();
  const navigate = useNavigate();

  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [passwordConfirmation, setPasswordConfirmation] = useState("");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (isAuthenticated) {
    return <Navigate to="/" replace />;
  }

  /*
   * Client-side validation mirrors the server's rules so the user gets an
   * answer without a round trip. It is a convenience, never the enforcement —
   * auth-service validates all of this again, and it is the one that decides.
   */
  function validate(): FieldErrors {
    const errors: FieldErrors = {};

    if (username.trim().length < USERNAME_MIN || username.trim().length > USERNAME_MAX) {
      errors.username = `Elegí un nombre de entre ${USERNAME_MIN} y ${USERNAME_MAX} caracteres.`;
    }
    if (!email.includes("@") || email.startsWith("@") || email.endsWith("@")) {
      errors.email = "Escribí un email válido.";
    }
    if (password.length < PASSWORD_MIN) {
      errors.password = `La contraseña necesita al menos ${PASSWORD_MIN} caracteres.`;
    }
    if (passwordConfirmation !== password) {
      errors.passwordConfirmation = "Las contraseñas no coinciden.";
    }

    return errors;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);

    const errors = validate();
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }

    setSubmitting(true);
    try {
      await register({ username: username.trim(), email: email.trim(), password });
      await login(username.trim(), password);
      navigate("/", { replace: true });
    } catch (cause) {
      const apiError = toApiError(cause);

      // Branch on the contract's error code, not on the message text.
      if (apiError?.error === "DUPLICATE_USER") {
        setFormError("Ese usuario o email ya está registrado. Probá con otro.");
      } else if (apiError?.error === "VALIDATION_FAILED") {
        setFormError("Revisá los datos: alguno no cumple con lo que pedimos.");
      } else if (!apiError) {
        setFormError(
          "No pudimos contactar al servidor. Intentá de nuevo en unos segundos.",
        );
      } else {
        setFormError("Ocurrió un error inesperado. Intentá de nuevo.");
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

        <h1 className="mb-4 text-5xl font-bold">Sumate y empezá a pronosticar.</h1>
        <p className="max-w-xl text-lg leading-relaxed text-[#cbd5e1]">
          Creá tu cuenta, inscribite en un torneo y cargá tus pronósticos antes
          de que empiece cada partido.
        </p>
      </section>

      <section className="flex items-center justify-center bg-[#f8fafc] p-10">
        <div className="w-full max-w-[430px] rounded-xl bg-card p-8 shadow-card">
          <h2 className="mb-2 text-[28px] font-bold">Crear cuenta</h2>
          <p className="mb-6 text-sm text-muted-foreground">
            Es gratis y te toma menos de un minuto.
          </p>

          <form onSubmit={handleSubmit} className="flex flex-col gap-3.5" noValidate>
            <FormField
              label="Usuario"
              required
              error={fieldErrors.username}
              hint={`Entre ${USERNAME_MIN} y ${USERNAME_MAX} caracteres.`}
            >
              {(field) => (
                <Input
                  {...field}
                  type="text"
                  autoComplete="username"
                  value={username}
                  onChange={(event) => setUsername(event.target.value)}
                />
              )}
            </FormField>

            <FormField label="Email" required error={fieldErrors.email}>
              {(field) => (
                <Input
                  {...field}
                  type="email"
                  autoComplete="email"
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                />
              )}
            </FormField>

            <FormField
              label="Contraseña"
              required
              error={fieldErrors.password}
              hint={`Al menos ${PASSWORD_MIN} caracteres.`}
            >
              {(field) => (
                <Input
                  {...field}
                  type="password"
                  autoComplete="new-password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                />
              )}
            </FormField>

            <FormField
              label="Repetir contraseña"
              required
              error={fieldErrors.passwordConfirmation}
            >
              {(field) => (
                <Input
                  {...field}
                  type="password"
                  autoComplete="new-password"
                  value={passwordConfirmation}
                  onChange={(event) => setPasswordConfirmation(event.target.value)}
                />
              )}
            </FormField>

            {formError && (
              <p
                role="alert"
                className="rounded-md bg-destructive/10 px-3.5 py-3 text-sm font-bold text-destructive"
              >
                {formError}
              </p>
            )}

            <Button type="submit" size="block" className="mt-2" disabled={submitting}>
              {submitting ? "Creando cuenta…" : "Crear cuenta"}
            </Button>
          </form>

          <p className="mt-5 text-center text-sm text-muted-foreground">
            ¿Ya tenés cuenta?{" "}
            <Link to="/login" className="font-bold text-primary hover:underline">
              Ingresá
            </Link>
          </p>
        </div>
      </section>
    </div>
  );
}
