import {
  useState,
  type FormEvent,
  type InputHTMLAttributes,
  type MouseEvent,
} from "react";
import { CircleUserRound } from "lucide-react";
import {
  authErrorMessage,
  login,
  register,
  registrationFieldErrors,
  type AuthUser,
} from "../api/auth";

export type AuthPagePath = "/login" | "/register";

function fieldError(input: HTMLInputElement, registering: boolean): string {
  const { name, value } = input;
  switch (name) {
    case "username":
      if (!value.trim()) return "Introdu numele de utilizator.";
      if (registering && !/^[A-Za-z0-9_]{3,32}$/.test(value)) {
        return "Folosește între 3 și 32 de litere latine, cifre sau caractere _.";
      }
      return value.length > 32 ? "Numele de utilizator poate avea cel mult 32 de caractere." : "";
    case "email":
      if (!value.trim()) return "Introdu adresa de e-mail.";
      if (value.length > 254) return "Adresa de e-mail poate avea cel mult 254 de caractere.";
      return input.validity.typeMismatch ? "Introdu o adresă de e-mail validă." : "";
    case "password":
      if (!value.trim()) return "Introdu parola.";
      if (registering && value.length < 8) return "Parola trebuie să aibă cel puțin 8 caractere.";
      return new TextEncoder().encode(value).length > 72
        ? "Parola poate avea cel mult 72 de octeți."
        : "";
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
  registeredUsername,
  onNavigate,
  onRegistered,
  onLogin,
}: {
  path: AuthPagePath;
  user: AuthUser | null | undefined;
  registeredUsername: string;
  onNavigate: (event: MouseEvent<HTMLAnchorElement>, path: string) => void;
  onRegistered: (username: string) => void;
  onLogin: (user: AuthUser) => void;
}) {
  const registering = path === "/register";
  const title = registering ? "Creează cont" : "Autentificare";
  const alternate = registering ? "/login" : "/register";
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
    const username = String(data.get("username"));
    const password = String(data.get("password"));
    setPending(true);
    try {
      if (registering) {
        const account = await register({ username, password, email: String(data.get("email")) });
        onRegistered(account.username);
      } else {
        onLogin(await login({ username, password }));
      }
    } catch (cause) {
      const conflicts = Object.entries(registrationFieldErrors(cause));
      if (conflicts.length) {
        const current = new FormData(form);
        const unchanged = conflicts.filter(([field]) =>
          String(current.get(field)).toLowerCase() === String(data.get(field)).toLowerCase(),
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
          <p>Ești conectat ca <strong>{user.username}</strong>.</p>
        ) : (
          <>
            {!registering && registeredUsername && (
              <p role="status">Contul a fost creat. Autentifică-te pentru a continua.</p>
            )}
            <form noValidate onSubmit={submit}>
              <AuthField
                name="username"
                label="Nume de utilizator"
                autoComplete="username"
                defaultValue={registering ? "" : registeredUsername}
                error={fieldErrors.username}
                onChange={validateChange}
              />
              {registering && (
                <AuthField
                  name="email"
                  label="E-mail"
                  type="email"
                  autoComplete="email"
                  error={fieldErrors.email}
                  onChange={validateChange}
                />
              )}
              <AuthField
                name="password"
                label="Parolă"
                type="password"
                autoComplete={registering ? "new-password" : "current-password"}
                hint={registering ? "Cel puțin 8 caractere." : undefined}
                error={fieldErrors.password}
                onChange={validateChange}
              />
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
