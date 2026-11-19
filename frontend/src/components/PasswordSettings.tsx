import { useEffect, useRef, useState, type FormEvent } from "react";
import { Eye, EyeOff } from "lucide-react";
import { changePassword } from "../api/settings";
import { passwordError } from "../lib/accountValidation";

const fields = [
  { id: "currentPassword", label: "Parola actuală", visibilityLabel: "parola actuală", autoComplete: "current-password" },
  { id: "newPassword", label: "Parola nouă", visibilityLabel: "parola nouă", autoComplete: "new-password" },
  { id: "confirmation", label: "Confirmă parola nouă", visibilityLabel: "confirmarea parolei noi", autoComplete: "new-password" },
] as const;
const empty = { currentPassword: "", newPassword: "", confirmation: "" };

export function PasswordSettings({ onChanged }: { onChanged: () => void }) {
  const [values, setValues] = useState(empty);
  const [visible, setVisible] = useState<Record<string, boolean>>({});
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const pending = useRef<AbortController | null>(null);
  useEffect(() => () => pending.current?.abort(), []);

  async function save(event: FormEvent) {
    event.preventDefault();
    if (pending.current) return;
    const validation = {
      currentPassword: passwordError(values.currentPassword, false),
      newPassword: passwordError(values.newPassword),
      confirmation: values.newPassword !== values.confirmation ? "Parolele noi nu coincid." : "",
    };
    setErrors(validation);
    setError("");
    if (Object.values(validation).some(Boolean)) return;
    const controller = new AbortController();
    pending.current = controller;
    setSaving(true);
    try {
      await changePassword(values.currentPassword, values.newPassword, AbortSignal.any([controller.signal, AbortSignal.timeout(15_000)]));
      if (!controller.signal.aborted) {
        setValues(empty);
        setVisible({});
        onChanged();
      }
    } catch (cause) {
      if (!controller.signal.aborted) setError(cause instanceof Error && cause.name === "Error"
        ? cause.message : "Parola nu a putut fi schimbată. Încearcă din nou.");
    } finally {
      if (!controller.signal.aborted) { pending.current = null; setSaving(false); }
    }
  }

  return (
    <section className="auth-card settings-card" aria-labelledby="password-settings-title">
      <h2 id="password-settings-title">Schimbă parola</h2>
      <form noValidate onSubmit={save}>
        {fields.map(field => <div className="auth-field" key={field.id}>
          <label htmlFor={field.id}>{field.label}</label>
          <div className="password-input">
            <input id={field.id} type={visible[field.id] ? "text" : "password"} autoComplete={field.autoComplete}
              value={values[field.id]} disabled={saving} aria-invalid={!!errors[field.id]}
              aria-describedby={errors[field.id] ? `${field.id}-error` : field.id === "newPassword" ? "new-password-help" : undefined}
              onChange={event => { setValues(current => ({ ...current, [field.id]: event.target.value })); setErrors(current => ({ ...current, [field.id]: "" })); setError(""); }} />
            <button type="button" className="icon-button" aria-label={`${visible[field.id] ? "Ascunde" : "Arată"} ${field.visibilityLabel}`}
              aria-pressed={!!visible[field.id]} onClick={() => setVisible(current => ({ ...current, [field.id]: !current[field.id] }))}>
              {visible[field.id] ? <EyeOff size={18} aria-hidden="true" /> : <Eye size={18} aria-hidden="true" />}
            </button>
          </div>
          {field.id === "newPassword" && <small id="new-password-help">Cel puțin 8 caractere și cel mult 72 de octeți.</small>}
          {errors[field.id] && <p className="auth-field-error" id={`${field.id}-error`}>{errors[field.id]}</p>}
        </div>)}
        <p className="settings-explanation">După schimbare, toate sesiunile tale vor necesita o nouă autentificare.</p>
        <button className="primary-button" disabled={saving}>{saving ? "Se schimbă parola…" : "Schimbă parola"}</button>
      </form>
      {saving && <p className="settings-feedback" role="status">Se schimbă parola…</p>}
      {error && <p className="auth-error" role="alert">{error}</p>}
    </section>
  );
}
