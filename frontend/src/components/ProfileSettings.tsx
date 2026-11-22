import { useEffect, useRef, useState, type FormEvent } from "react";
import type { AuthUser } from "../api/auth";
import { saveDisplayName } from "../api/settings";
import { displayNameError } from "../lib/accountValidation";

export function ProfileSettings({ user, onSaved }: { user: AuthUser; onSaved: (user: AuthUser) => void }) {
  const [name, setName] = useState(user.displayName);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const pending = useRef<AbortController | null>(null);
  useEffect(() => () => pending.current?.abort(), []);

  async function save(event: FormEvent) {
    event.preventDefault();
    if (pending.current) return;
    const validation = displayNameError(name);
    setError(validation);
    setSuccess("");
    if (validation) return;
    const controller = new AbortController();
    pending.current = controller;
    setSaving(true);
    try {
      const updated = await saveDisplayName(name.trim(), AbortSignal.any([controller.signal, AbortSignal.timeout(15_000)]));
      if (!controller.signal.aborted) {
        setName(updated.displayName);
        setSuccess("Numele a fost salvat.");
        onSaved(updated);
      }
    } catch (cause) {
      if (!controller.signal.aborted) setError(cause instanceof Error && cause.name === "Error"
        ? cause.message : "Numele nu a putut fi salvat. Încearcă din nou.");
    } finally {
      if (!controller.signal.aborted) { pending.current = null; setSaving(false); }
    }
  }

  return (
    <section className="auth-card settings-card" aria-labelledby="profile-settings-title">
      <h2 id="profile-settings-title">Profil</h2>
      <form noValidate onSubmit={save}>
        <div className="auth-field">
          <label htmlFor="display-name">Nume</label>
          <input id="display-name" autoComplete="nickname" value={name} disabled={saving}
            aria-describedby={error ? "display-name-help display-name-error" : "display-name-help"} aria-invalid={!!error} onChange={event => { setName(event.target.value); setError(""); setSuccess(""); }} />
          {error && <p id="display-name-error" className="auth-field-error" role="alert">{error}</p>}
          <small id="display-name-help">Cel mult 64 de caractere. Acest nume apare în clasament și lângă pronosticuri.</small>
        </div>
        <button className="primary-button" disabled={saving}>{saving ? "Se salvează numele…" : "Salvează numele"}</button>
      </form>
      <p className="settings-feedback" role="status">{success}</p>
    </section>
  );
}
