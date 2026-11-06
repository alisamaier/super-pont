import { ArrowUpRight, Check, ClipboardList, Save } from "lucide-react";
import type { Fixture, Predictions } from "../types";

export function PredictionSummary({
  saved,
  fixtures,
  roundNumber,
  competitionName,
  seasonName,
  unsavedCount,
  onSaveAll,
}: {
  saved: Predictions;
  fixtures: readonly Fixture[];
  roundNumber: number;
  competitionName: string;
  seasonName: string;
  unsavedCount: number;
  onSaveAll: () => void;
}) {
  const count = Object.keys(saved).length;
  return (
    <aside
      className="prediction-sidebar"
      aria-label="Rezumatul pronosticurilor"
    >
      <div className="summary-heading">
        <ClipboardList size={18} />
        <h2>Biletul etapei</h2>
        <span>#{roundNumber}</span>
      </div>
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
        disabled={!unsavedCount}
      >
        <Save size={16} />
        Salvează toate{unsavedCount > 0 && <span>{unsavedCount}</span>}
      </button>
      <p className="device-note">
        <span className="small-dot" />
        Salvate pe acest dispozitiv
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
            .map((fixture) => (
              <div className="saved-line" key={fixture.id}>
                <span>{fixture.home.shortName}</span>
                <strong>
                  {saved[fixture.id].home}
                  <span>:</span>
                  {saved[fixture.id].away}
                </strong>
                <span>{fixture.away.shortName}</span>
              </div>
            ))
        )}
      </div>
      <div className="round-note">
        <span className="round-note-icon">
          <ArrowUpRight size={20} />
        </span>
        <div>
          <strong>
            Fotbalul e mai frumos
            <br />
            când ai un pronostic.
          </strong>
          <span>
            {competitionName} · {seasonName}
          </span>
        </div>
      </div>
    </aside>
  );
}
