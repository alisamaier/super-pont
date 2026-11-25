import { Check, MapPin, Save, Trash2 } from "lucide-react";
import { validatePrediction } from "../lib/predictions";
import { matchStatusLabels } from "../lib/fixtures";
import type { Fixture, Prediction, ScoreDraft } from "../types";
import { TeamBadge } from "./TeamBadge";

interface Props {
  fixture: Fixture;
  draft: ScoreDraft;
  saved?: Prediction;
  disabled: boolean;
  locked: boolean;
  actionError?: string;
  showErrors: boolean;
  onChange: (draft: ScoreDraft) => void;
  onSave: () => void;
  onRemove: () => void;
}

export function MatchCard({
  fixture,
  draft,
  saved,
  disabled,
  locked,
  actionError,
  showErrors,
  onChange,
  onSave,
  onRemove,
}: Props) {
  const { prediction: parsed, errors } = validatePrediction(draft);
  const homeError = showErrors || draft.home !== "" ? errors.home : null;
  const awayError = showErrors || draft.away !== "" ? errors.away : null;
  const unchanged =
    !!saved && saved.home === parsed?.home && saved.away === parsed?.away;
  const saveLabel = locked
    ? "Pronosticuri închise"
    : saved
      ? "Actualizează pronosticul"
      : "Salvează pronosticul";
  return (
    <article
      className={`match-card${unchanged ? " is-saved" : ""}`}
      aria-label={`${fixture.home.name} – ${fixture.away.name}`}
    >
      <div className="match-meta">
        <span>
          {fixture.date} <span className="meta-dot">·</span>{" "}
          <strong>{fixture.time}</strong>
          {(fixture.status === "FINISHED" || fixture.status === "LIVE") && fixture.homeScore !== null && fixture.awayScore !== null && (
            <>
              <span className="meta-dot">·</span>
              <strong>
                {fixture.homeScore}:{fixture.awayScore}
              </strong>
            </>
          )}
        </span>
        <span className={unchanged ? "match-state saved-state" : "match-state"}>
          {unchanged ? (
            <>
              <Check size={12} /> Salvat
            </>
          ) : (
            matchStatusLabels[fixture.status]
          )}
        </span>
      </div>
      <form
        noValidate
        onSubmit={(event) => {
          event.preventDefault();
          if (!disabled && !locked && !unchanged) onSave();
        }}
      >
        <div className="match-teams">
          <div className="team">
            <TeamBadge team={fixture.home} />
            <h3>{fixture.home.name}</h3>
            <span>Gazde</span>
          </div>
          <div className="score-entry">
            <span className="score-caption">SCORUL TĂU</span>
            <div className="score-inputs">
              <input
                type="text"
                disabled={disabled || locked}
                inputMode="numeric"
                required
                placeholder="–"
                aria-label={`Scor ${fixture.home.name}`}
                aria-invalid={!!homeError}
                aria-describedby={
                  homeError ? `${fixture.id}-home-error` : undefined
                }
                value={draft.home}
                onChange={(event) =>
                  onChange({ ...draft, home: event.target.value })
                }
              />
              <span className="score-separator">:</span>
              <input
                type="text"
                disabled={disabled || locked}
                inputMode="numeric"
                required
                placeholder="–"
                aria-label={`Scor ${fixture.away.name}`}
                aria-invalid={!!awayError}
                aria-describedby={
                  awayError ? `${fixture.id}-away-error` : undefined
                }
                value={draft.away}
                onChange={(event) =>
                  onChange({ ...draft, away: event.target.value })
                }
              />
            </div>
          </div>
          <div className="team">
            <TeamBadge team={fixture.away} />
            <h3>{fixture.away.name}</h3>
            <span>Oaspeți</span>
          </div>
        </div>
        {(homeError || awayError) && (
          <div className="score-errors" role="alert">
            {homeError && (
              <p id={`${fixture.id}-home-error`}>Gazde: {homeError}</p>
            )}
            {awayError && (
              <p id={`${fixture.id}-away-error`}>Oaspeți: {awayError}</p>
            )}
          </div>
        )}
        {actionError && <p className="score-errors" role="alert">{actionError}</p>}
        <div className="match-location">
          <MapPin size={12} />
          <span>{fixture.venue}</span>
        </div>
        <div className="match-actions">
          <button
            type="submit"
            className={`save-match${unchanged ? " saved-button" : ""}`}
            disabled={disabled || locked || unchanged}
          >
            {unchanged ? <Check size={15} /> : <Save size={15} />}
            {unchanged ? "Pronostic salvat" : saveLabel}
          </button>
          {saved && (
            <button
              className="icon-button remove-prediction"
              type="button"
              title={locked ? "Pronosticul este blocat după start." : "Șterge pronosticul"}
              aria-label={`Șterge pronosticul ${fixture.home.name} – ${fixture.away.name}`}
              disabled={disabled || locked}
              onClick={onRemove}
            >
              <Trash2 size={15} />
            </button>
          )}
        </div>
      </form>
    </article>
  );
}
