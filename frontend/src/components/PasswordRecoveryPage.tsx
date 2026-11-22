import { useEffect, useRef, useState, type FormEvent, type MouseEvent } from "react";
import { Eye, EyeOff, KeyRound } from "lucide-react";
import { invalidResetLink, InvalidResetLinkError, requestPasswordReset, resetPassword, validatePasswordReset } from "../api/passwordRecovery";
import { normalizeEmail, passwordError } from "../lib/accountValidation";

type Navigate = (event: MouseEvent<HTMLAnchorElement>, path: string) => void;
const failure = (error: unknown) => error instanceof Error && error.name === "Error"
  ? error.message : "Cererea nu a putut fi trimisă. Încearcă din nou.";
const requestSignal = (controller: AbortController) => AbortSignal.any([controller.signal, AbortSignal.timeout(15_000)]);

export function PasswordRecoveryPage({ onNavigate }: { onNavigate: Navigate }) {
  const [email, setEmail] = useState("");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const pending = useRef<AbortController | null>(null);
  useEffect(() => () => pending.current?.abort(), []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending.current) return;
    setError("");
    const input = event.currentTarget.elements.namedItem("email") as HTMLInputElement;
    if (!normalizeEmail(email) || input.validity.typeMismatch || normalizeEmail(email).length > 254) {
      setError("Introdu o adresă de email validă."); return;
    }
    const controller = new AbortController();
    pending.current = controller;
    setBusy(true);
    setMessage("");
    try {
      const result = await requestPasswordReset(email, requestSignal(controller));
      if (!controller.signal.aborted) setMessage(result);
    } catch (cause) {
      if (!controller.signal.aborted) setError(failure(cause));
    } finally {
      if (!controller.signal.aborted) { pending.current = null; setBusy(false); }
    }
  }

  return <main id="auth-content" className="main-container auth-main" tabIndex={-1}>
    <section className="auth-card" aria-labelledby="recovery-title">
      <KeyRound className="auth-icon" size={30} aria-hidden="true" />
      <h1 id="recovery-title">Ai uitat parola?</h1>
      <p>Introdu adresa de email pentru a primi un link de resetare a parolei.</p>
      <form noValidate onSubmit={submit} aria-busy={busy}>
        <div className="auth-field">
          <label htmlFor="recovery-email">Adresă de email</label>
          <input id="recovery-email" name="email" type="email" autoComplete="email" required disabled={busy}
            value={email} onChange={event => { setEmail(event.target.value); setError(""); setMessage(""); }} />
        </div>
        {error && <p className="auth-error" role="alert">{error}</p>}
        <button className="primary-button" disabled={busy}>{busy ? "Se trimite…" : "Trimite linkul"}</button>
      </form>
      {message && <p role="status">{message}</p>}
      <a href="/login" onClick={event => onNavigate(event, "/login")}>Înapoi la autentificare</a>
    </section>
  </main>;
}

const emptyPasswords = { newPassword: "", confirmation: "" };
const fields = [
  { id: "newPassword", label: "Parola nouă", visibility: "parola nouă" },
  { id: "confirmation", label: "Confirmă parola nouă", visibility: "confirmarea parolei noi" },
] as const;

export function ResetPasswordPage({ token, onNavigate, onReset }: { token: string; onNavigate: Navigate; onReset: () => void }) {
  const [values, setValues] = useState(emptyPasswords);
  const [visible, setVisible] = useState<Record<string, boolean>>({});
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState("");
  const [status, setStatus] = useState<"checking" | "ready" | "invalid" | "failed">("checking");
  const [validationAttempt, setValidationAttempt] = useState(0);
  const [busy, setBusy] = useState(false);
  const pending = useRef<AbortController | null>(null);
  useEffect(() => () => pending.current?.abort(), []);

  useEffect(() => {
    const controller = new AbortController();
    if (!/^[A-Za-z0-9_-]{43}$/.test(token)) {
      setStatus("invalid"); return () => controller.abort();
    }
    setStatus("checking");
    setError("");
    validatePasswordReset(token, requestSignal(controller)).then(() => {
      if (!controller.signal.aborted) setStatus("ready");
    }).catch(cause => {
      if (controller.signal.aborted) return;
      if (cause instanceof InvalidResetLinkError) setStatus("invalid");
      else { setStatus("failed"); setError(failure(cause)); }
    });
    return () => controller.abort();
  }, [token, validationAttempt]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (pending.current || status !== "ready") return;
    const validation = { newPassword: passwordError(values.newPassword),
      confirmation: values.newPassword !== values.confirmation ? "Parolele noi nu coincid." : "" };
    setErrors(validation);
    setError("");
    if (Object.values(validation).some(Boolean)) return;
    const controller = new AbortController();
    pending.current = controller;
    setBusy(true);
    try {
      await resetPassword(token, values.newPassword, requestSignal(controller));
      if (!controller.signal.aborted) { setValues(emptyPasswords); setVisible({}); onReset(); }
    } catch (cause) {
      if (controller.signal.aborted) return;
      if (cause instanceof InvalidResetLinkError) { setValues(emptyPasswords); setStatus("invalid"); }
      else setError(failure(cause));
    } finally {
      if (!controller.signal.aborted) { pending.current = null; setBusy(false); }
    }
  }

  return <main id="auth-content" className="main-container auth-main" tabIndex={-1}>
    <section className="auth-card" aria-labelledby="reset-title">
      <KeyRound className="auth-icon" size={30} aria-hidden="true" />
      <h1 id="reset-title">Resetează parola</h1>
      {status === "checking" && <p role="status">Se verifică linkul…</p>}
      {status === "invalid" && <p className="auth-error" role="alert">{invalidResetLink}</p>}
      {status === "failed" && <button className="primary-button" onClick={() => setValidationAttempt(value => value + 1)}>Reîncearcă verificarea</button>}
      {status === "ready" && <form noValidate onSubmit={submit} aria-busy={busy}>
        {fields.map(field => <div className="auth-field" key={field.id}>
          <label htmlFor={`reset-${field.id}`}>{field.label}</label>
          <div className="password-input">
            <input id={`reset-${field.id}`} name={field.id} type={visible[field.id] ? "text" : "password"}
              autoComplete="new-password" required disabled={busy} value={values[field.id]}
              aria-invalid={!!errors[field.id]} aria-describedby={errors[field.id] ? `reset-${field.id}-error` : undefined}
              onChange={event => { setValues(current => ({ ...current, [field.id]: event.target.value })); setErrors(current => ({ ...current, [field.id]: "" })); setError(""); }} />
            <button type="button" className="icon-button" aria-label={`${visible[field.id] ? "Ascunde" : "Arată"} ${field.visibility}`}
              aria-pressed={!!visible[field.id]} onClick={() => setVisible(current => ({ ...current, [field.id]: !current[field.id] }))}>
              {visible[field.id] ? <EyeOff size={18} aria-hidden="true" /> : <Eye size={18} aria-hidden="true" />}
            </button>
          </div>
          {errors[field.id] && <p className="auth-field-error" id={`reset-${field.id}-error`}>{errors[field.id]}</p>}
        </div>)}
        <small>Cel puțin 8 caractere și cel mult 72 de octeți. După resetare, autentifică-te din nou.</small>
        <button className="primary-button" disabled={busy}>{busy ? "Se resetează…" : "Resetează parola"}</button>
      </form>}
      {error && <p className="auth-error" role="alert">{error}</p>}
      <a href="/recuperare-parola" onClick={event => onNavigate(event, "/recuperare-parola")}>Solicită un link nou</a>
      <a href="/login" onClick={event => onNavigate(event, "/login")}>Înapoi la autentificare</a>
    </section>
  </main>;
}
