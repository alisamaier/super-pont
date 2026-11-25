import { Check, ClipboardList, Save } from "lucide-react";
import type { Fixture, SavedPredictions } from "../types";
import { TeamBadge } from "./TeamBadge";

export function PredictionSummary({
  saved,
  fixtures,
  roundLabel,
  competitionName,
  unsavedCount,
  canSave,
  onSaveAll,
}: {
  saved: SavedPredictions;
  fixtures: readonly Fixture[];
  roundLabel: string;
  competitionName: string;
  unsavedCount: number;
  canSave: boolean;
  onSaveAll: () => void;
}) {
  const count = Object.keys(saved).length;
  const credits = [...new Set(fixtures.filter((fixture) => saved[fixture.id])
    .flatMap((fixture) => [fixture.home, fixture.away])
    .filter((team) => team.logoUrl && team.logoCredit).map((team) => team.logoCredit!))];
  return (
    <aside
      className="prediction-sidebar"
      aria-label="Rezumatul pronosticurilor"
    >
      <div className="summary-heading">
        <ClipboardList size={18} />
        <h2>Biletul etapei</h2>
        <span>{roundLabel}</span>
      </div>
      <p className="summary-context">{competitionName}</p>
      <div className="summary-count">
        <strong>{String(count).padStart(2, "0")}</strong>
        <span>
          / {String(fixtures.length).padStart(2, "0")}
          <small>meciuri completate</small>
        </span>
      </div>
      <div
        className="progress-segments"
        role="progressbar"
        aria-label="Pronosticuri salvate"
        aria-valuemin={0}
        aria-valuemax={fixtures.length}
        aria-valuenow={count}
      >
        {fixtures.map((fixture, index) => (
          <span key={fixture.id} className={index < count ? "filled" : ""} />
        ))}
      </div>
      <div className="summary-message">
        {fixtures.length > 0 && count === fixtures.length ? (
          <>
            <Check size={14} /> Bilet complet. Mult succes!
          </>
        ) : (
          "Fiecare meci, o nouă inspirație."
        )}
      </div>
      <button
        className="primary-button save-all"
        onClick={onSaveAll}
        disabled={!canSave}
      >
        <Save size={16} />
        Salvează toate{unsavedCount > 0 && <span>{unsavedCount}</span>}
      </button>
      <p className="device-note">
        <span className="small-dot" />
        Pronosticurile salvate sunt disponibile în contul tău
      </p>
      <div className="sidebar-saved">
        <div className="section-eyebrow">PRONOSTICURILE TALE</div>
        {count === 0 ? (
          <div className="summary-empty">
            <ClipboardList size={26} strokeWidth={1.3} />
            <span>Niciun pronostic salvat încă</span>
          </div>
        ) : (
          fixtures
            .filter((fixture) => saved[fixture.id])
            .map((fixture) => {
              const { home, away, result } = saved[fixture.id];
              return (
                <div className="saved-line" key={fixture.id}>
                  <span className="saved-team"><TeamBadge team={fixture.home} compact />{fixture.home.shortName}</span>
                  <strong>{home}<span>:</span>{away}</strong>
                  <span className="saved-team"><TeamBadge team={fixture.away} compact />{fixture.away.shortName}</span>
                  <div className="saved-result">
                    {result === null ? (
                      <span>În așteptare</span>
                    ) : (
                      <>
                        <span>Scor final {result.home}–{result.away}</span>
                        <strong>{result.points} {result.points === 1 ? "punct" : "puncte"}</strong>
                      </>
                    )}
                  </div>
                </div>
              );
            })
        )}
      </div>
      {credits.length > 0 && <div className="summary-logo-credits">{credits.map((credit) => <p key={credit}>{credit}</p>)}</div>}
    </aside>
  );
}
