import {
  useState,
  type FormEvent,
  type InputHTMLAttributes,
  type MouseEvent,
} from "react";
import { CircleUserRound } from "lucide-react";
import { displayNameError, passwordError, normalizeEmail } from "../lib/accountValidation";
import {
  authErrorMessage,
  login,
  isPendingRegistration,
  register,
  registrationFieldErrors,
  type AuthUser,
} from "../api/auth";

export type AuthPagePath = "/login" | "/register";

function fieldError(input: HTMLInputElement, registering: boolean): string {
  const { name, value } = input;
  switch (name) {
    case "displayName":
      return displayNameError(value);
    case "email":
      if (!value.trim()) return "Introdu adresa de email.";
      if (value.trim().length > 254) return "Adresa de email poate avea cel mult 254 de caractere.";
      return input.validity.typeMismatch ? "Introdu o adresă de email validă." : "";
    case "password":
      return passwordError(value, registering);
    default:
      return "";
  }
}

function AuthField({
  name,
  label,
  error,
  hint,
  ...inputProps
}: InputHTMLAttributes<HTMLInputElement> & {
  name: string;
  label: string;
  error?: string;
  hint?: string;
}) {
  const id = `auth-${name}`;
  const errorId = `${id}-error`;
  const hintId = `${id}-hint`;
  const description = error ? errorId : hint ? hintId : undefined;

  return (
    <div className="auth-field">
      <label htmlFor={id}>{label}</label>
      <input
        {...inputProps}
        id={id}
        name={name}
        required
        aria-invalid={error ? true : undefined}
        aria-describedby={description}
      />
      <p id={errorId} className="auth-field-error" aria-live="polite">{error}</p>
      {!error && hint && <small id={hintId}>{hint}</small>}
    </div>
  );
}

export function AuthPage({
  path,
  user,
  registeredEmail,
  returnTo,
  notice,
  onNavigate,
  onRegistered,
  onLogin,
}: {
  path: AuthPagePath;
  user: AuthUser | null | undefined;
  registeredEmail: string;
  returnTo?: string;
  notice?: string;
  onNavigate: (event: MouseEvent<HTMLAnchorElement>, path: string) => void;
  onRegistered: (email: string) => void;
  onLogin: (user: AuthUser) => void;
}) {
  const registering = path === "/register";
  const title = registering ? "Creează cont" : "Autentificare";
  const alternate = (registering ? "/login" : "/register")
    + (returnTo ? `?next=${encodeURIComponent(returnTo)}` : "");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  function validateChange(event: FormEvent<HTMLInputElement>) {
    const input = event.currentTarget;
    if (fieldErrors[input.name]) {
      setFieldErrors({ ...fieldErrors, [input.name]: fieldError(input, registering) });
    }
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const inputs = Array.from(form.querySelectorAll("input"));
    const errors = Object.fromEntries(
      inputs.map((input) => [input.name, fieldError(input, registering)]),
    );
    setFieldErrors(errors);
    setError("");
    if (Object.values(errors).some(Boolean)) return;

    const data = new FormData(form);
    const email = normalizeEmail(String(data.get("email")));
    const password = String(data.get("password"));
    setPending(true);
    try {
      if (registering) {
        const account = await register({ displayName: String(data.get("displayName")), password, email });
        onRegistered(account.email);
      } else {
        const account = await login({ email, password });
        if (isPendingRegistration(account)) onRegistered(account.email);
        else onLogin(account);
      }
    } catch (cause) {
      const conflicts = Object.entries(registrationFieldErrors(cause));
      if (conflicts.length) {
        const current = new FormData(form);
        const unchanged = conflicts.filter(([field]) =>
          normalizeEmail(String(current.get(field))) === normalizeEmail(String(data.get(field))),
        );
        setFieldErrors((previous) => ({ ...previous, ...Object.fromEntries(unchanged) }));
      } else {
        setError(authErrorMessage(cause));
      }
    } finally {
      setPending(false);
    }
  }

  return (
    <main className="main-container auth-main" id="auth-content">
      <section className="auth-card" aria-labelledby="auth-title">
        <CircleUserRound className="auth-icon" size={30} aria-hidden="true" />
        <h1 id="auth-title">{title}</h1>
        {user ? (
          <p>Ești conectat ca <strong>{user.displayName}</strong>.</p>
        ) : (
          <>
            {!registering && notice && <p role="status">{notice}</p>}
            {!registering && registeredEmail && !notice && (
              <p role="status">Contul a fost creat. Autentifică-te pentru a continua.</p>
            )}
            <form noValidate onSubmit={submit}>
              {registering && (
                <AuthField
                  name="displayName"
                  label="Nume"
                  autoComplete="nickname"
                  error={fieldErrors.displayName}
                  onChange={validateChange}
                />
              )}
              <AuthField name="email" label="Adresă de email" type="email" autoComplete="email"
                defaultValue={registering ? "" : registeredEmail} error={fieldErrors.email} onChange={validateChange} />
              <AuthField
                name="password"
                label="Parolă"
                type="password"
                autoComplete={registering ? "new-password" : "current-password"}
                hint={registering ? "Cel puțin 8 caractere." : undefined}
                error={fieldErrors.password}
                onChange={validateChange}
              />
              {!registering && <a href="/recuperare-parola" onClick={event => onNavigate(event, "/recuperare-parola")}>Ai uitat parola?</a>}
              {error && <p className="auth-error" role="alert">{error}</p>}
              <button className="primary-button" type="submit" disabled={pending || user === undefined}>
                {pending ? "Se trimite…" : registering ? "Creează cont" : "Intră în cont"}
              </button>
            </form>
            <p>
              {registering ? "Ai deja un cont? " : "Nu ai încă un cont? "}
              <a href={alternate} onClick={(event) => onNavigate(event, alternate)}>
                {registering ? "Autentifică-te" : "Creează cont"}
              </a>
            </p>
          </>
        )}
        <a href="/" onClick={(event) => onNavigate(event, "/")}>Înapoi la meciuri</a>
      </section>
    </main>
  );
}
