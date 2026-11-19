import { useEffect, useRef, useState } from "react";
import { LoaderCircle, RotateCw } from "lucide-react";
import { getAccountSettings, saveAccountSettings, type AccountSettings } from "../api/settings";
import { useResource } from "../hooks/useResource";

const load = (_userId: string, signal: AbortSignal) => getAccountSettings(signal);

export function PredictionSettings({ userId }: { userId: string }) {
  const { data, loading, error, retry } = useResource(userId, load);
  const [saved, setSaved] = useState<AccountSettings>();
  const [draft, setDraft] = useState<boolean>();
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState("");
  const [savedMessage, setSavedMessage] = useState("");
  const pending = useRef<AbortController | null>(null);
  const settings = saved ?? data;

  useEffect(() => () => pending.current?.abort(), []);

  async function toggle(value: boolean) {
    if (!settings || pending.current) return;
    const controller = new AbortController();
    pending.current = controller;
    setDraft(value);
    setSaving(true);
    setSaveError("");
    setSavedMessage("");
    try {
      const updated = await saveAccountSettings({ hidePredictionsUntilKickoff: value },
        AbortSignal.any([controller.signal, AbortSignal.timeout(15_000)]));
      if (!controller.signal.aborted) {
        setSaved(updated);
        setSavedMessage("Setarea a fost salvată.");
      }
    } catch {
      if (!controller.signal.aborted) {
        setSaveError("Nu am putut salva setarea. Am păstrat ultima valoare salvată. Încearcă din nou.");
      }
    } finally {
      if (!controller.signal.aborted) {
        pending.current = null;
        setDraft(undefined);
        setSaving(false);
      }
    }
  }

  return (
      <section className="auth-card settings-card" aria-labelledby="prediction-settings-title">
        <h2 id="prediction-settings-title">Pronosticuri</h2>
        {loading && <p role="status">Se încarcă setările…</p>}
        {error && <div role="alert">
          <p>Nu am putut încărca setările pronosticurilor. Încearcă din nou.</p>
          <button className="text-button" onClick={retry}><RotateCw size={15} aria-hidden="true" /> Încearcă din nou</button>
        </div>}
        {settings && <>
          <label className="settings-toggle">
            <span>Ascunde pronosticurile mele până la start</span>
            <input type="checkbox" role="switch" checked={draft ?? settings.hidePredictionsUntilKickoff}
              disabled={saving} aria-describedby="visibility-explanation"
              onChange={(event) => void toggle(event.target.checked)} />
          </label>
          <p id="visibility-explanation" className="settings-explanation">Când opțiunea este activată, ceilalți utilizatori conectați pot vedea pronosticurile tale după începerea fiecărui meci. Când este dezactivată, le pot vedea imediat după salvare. Pentru conturile noi, opțiunea este dezactivată implicit.</p>
          <p className="settings-explanation">Opțiunea se aplică tuturor pronosticurilor tale, inclusiv celor deja salvate. Tu le poți vedea oricând.</p>
        </>}
        <p className="settings-feedback" role="status" aria-live="polite">
          {saving ? <><LoaderCircle className="schedule-spinner" size={16} aria-hidden="true" /> Se salvează…</> : savedMessage}
        </p>
        {saveError && <p className="auth-error" role="alert">{saveError}</p>}
      </section>
  );
}
