import { useEffect, useRef, useState, type FormEvent, type MouseEvent } from "react";
import { MailCheck } from "lucide-react";
import { confirmRegistration, correctRegistrationEmail, getPendingRegistration,
  resendRegistrationCode, verificationFailure, type PendingRegistration } from "../api/auth";
import { useResource } from "../hooks/useResource";

const load = (_key: string, signal: AbortSignal) => getPendingRegistration(signal);

export function VerificationPage({ onConfirmed, onNavigate, returnTo }: {
  onConfirmed: (email: string) => void;
  onNavigate: (event: MouseEvent<HTMLAnchorElement>, destination: string) => void;
  returnTo?: string;
}) {
  const resource = useResource("registration", load);
  const [updated, setUpdated] = useState<PendingRegistration>();
  const data = updated ?? resource.data;
  const [code, setCode] = useState("");
  const [email, setEmail] = useState("");
  const [editingEmail, setEditingEmail] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [attempts, setAttempts] = useState<number>();
  const [deadline, setDeadline] = useState(0);
  const [remaining, setRemaining] = useState(0);
  const alive = useRef(true);
  const confirmed = useRef(onConfirmed);
  const loginPath = `/login${returnTo ? `?next=${encodeURIComponent(returnTo)}` : ""}`;

  useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
  useEffect(() => { confirmed.current = onConfirmed; }, [onConfirmed]);
  useEffect(() => {
    if (!data) return;
    if (data.state === "VERIFIED") { confirmed.current(data.email); return; }
    const wait = Math.max(0, Date.parse(data.resendAvailableAt) - Date.parse(data.serverTime));
    setDeadline(performance.now() + wait);
    setAttempts(data.attemptsRemaining);
  }, [data]);
  useEffect(() => {
    const tick = () => setRemaining(Math.max(0, Math.ceil((deadline - performance.now()) / 1000)));
    tick();
    const timer = window.setInterval(tick, 250);
    return () => window.clearInterval(timer);
  }, [deadline]);

  async function act(action: () => Promise<PendingRegistration>, success?: string) {
    if (busy) return;
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const result = await action();
      if (!alive.current) return;
      setUpdated(result);
      setCode("");
      if (result.deliveryStatus === "SENT" && success) setNotice(success);
      setEditingEmail(false);
    } catch (cause) {
      if (!alive.current) return;
      const failure = verificationFailure(cause);
      setError(failure.message);
      if (failure.attemptsRemaining !== undefined) setAttempts(failure.attemptsRemaining);
      if (failure.retryAfter > 0) setDeadline(performance.now() + failure.retryAfter * 1000);
    } finally { if (alive.current) setBusy(false); }
  }

  function confirm(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!/^[0-9]{6}$/.test(code)) { setError("Introdu codul de 6 cifre din email."); return; }
    void act(() => confirmRegistration(code));
  }

  return <main className="main-container auth-main" id="auth-content">
    <section className="auth-card verification-card" aria-labelledby="verification-title" aria-busy={busy || resource.loading}>
      <MailCheck className="auth-icon" size={30} aria-hidden="true" />
      <h1 id="verification-title">Confirmă adresa de email</h1>
      {resource.loading && !data && <p role="status">Se încarcă înregistrarea…</p>}
      {resource.error && !data && <>
        <p className="auth-error" role="alert">Înregistrarea nu poate fi încărcată. Autentifică-te pentru a relua confirmarea sau încearcă din nou.</p>
        <button type="button" className="text-button" onClick={resource.retry}>Încearcă din nou</button>
      </>}
      {data && data.state === "PENDING" && <>
        <p>Adresa de confirmat: <strong className="verification-email">{data.email}</strong>.</p>
        <p>Codul de 6 cifre este valabil 10 minute. Verifică și folderul spam.</p>
        {data.deliveryStatus === "FAILED" && <p className="auth-error" role="alert">
          Nu am putut trimite emailul. Contul este păstrat. Poți corecta adresa sau retrimite codul după timpul de așteptare.
        </p>}
        {data.deliveryStatus === "RATE_LIMITED" && <p className="auth-error" role="alert">
          Limita de trimitere a fost atinsă. Contul este păstrat; poți solicita codul după timpul de așteptare.
        </p>}
        <form noValidate onSubmit={confirm}>
          <div className="auth-field">
            <label htmlFor="verification-code">Cod de verificare</label>
            <input id="verification-code" name="code" type="text" inputMode="numeric" autoComplete="one-time-code"
              pattern="[0-9]{6}" maxLength={6} value={code} required spellCheck={false}
              aria-describedby="verification-attempts" disabled={busy || attempts === 0}
              onChange={(event) => setCode(event.target.value.replace(/[^0-9]/g, "").slice(0, 6))} />
            <small id="verification-attempts">{attempts ?? data.attemptsRemaining} încercări rămase pentru acest cod.</small>
          </div>
          <button className="primary-button" type="submit" disabled={busy || attempts === 0}>
            {busy ? "Se procesează…" : "Confirmă contul"}
          </button>
        </form>
        <button className="text-button verification-resend" type="button" disabled={busy || remaining > 0}
          onClick={() => void act(resendRegistrationCode, "Am trimis un cod nou. Codul anterior nu mai este valabil.")}>
          Retrimite codul{remaining > 0 ? ` (${remaining}s)` : ""}
        </button>
        <button className="text-button" type="button" disabled={busy}
          onClick={() => { setEmail(data.email); setEditingEmail(!editingEmail); }}>Schimbă adresa de email</button>
        {editingEmail && <form onSubmit={(event) => {
          event.preventDefault();
          void act(() => correctRegistrationEmail(email), "Adresa a fost schimbată. Am trimis un cod nou.");
        }}>
          <div className="auth-field">
            <label htmlFor="verification-email">Noua adresă de email</label>
            <input id="verification-email" type="email" autoComplete="email" value={email} required maxLength={254}
              onChange={(event) => setEmail(event.target.value)} disabled={busy} />
          </div>
          <button className="primary-button" type="submit" disabled={busy || remaining > 0}>
            Salvează și trimite codul{remaining > 0 ? ` (${remaining}s)` : ""}
          </button>
        </form>}
      </>}
      {error && <p className="auth-error" role="alert">{error}</p>}
      {notice && <p role="status">{notice}</p>}
      <p><a href={loginPath} onClick={(event) => onNavigate(event, loginPath)}>Autentifică-te pentru a relua confirmarea</a></p>
      <a href="/" onClick={(event) => onNavigate(event, "/")}>Înapoi la meciuri</a>
    </section>
  </main>;
}
