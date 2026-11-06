import { X } from "lucide-react";
import type { SaveResult } from "../lib/predictions";
import type { Fixture } from "../types";

function matchLabel(fixtures: readonly Fixture[], id: string) {
  const fixture = fixtures.find((item) => item.id === id);
  return fixture ? `${fixture.home.name} – ${fixture.away.name}` : id;
}

export function SaveReport({
  result,
  fixtures,
  onClose,
}: {
  result: SaveResult;
  fixtures: readonly Fixture[];
  onClose: () => void;
}) {
  const accepted = Object.entries(result.saved);
  const rejected = Object.entries(result.rejected);
  return (
    <section
      className="save-report"
      aria-labelledby="save-report-title"
      aria-live="polite"
    >
      <div className="save-report-heading">
        <h3 id="save-report-title">Ultima salvare</h3>
        <button
          className="icon-button"
          type="button"
          title="Închide rezultatul salvării"
          aria-label="Închide rezultatul salvării"
          onClick={onClose}
        >
          <X size={16} />
        </button>
      </div>
      <div className="save-report-saved">
        <h4>Salvate ({accepted.length})</h4>
        {accepted.length ? (
          <ul>
            {accepted.map(([id, prediction]) => (
              <li key={id}>
                <strong>{matchLabel(fixtures, id)}</strong>: {prediction.home}–
                {prediction.away}
              </li>
            ))}
          </ul>
        ) : (
          <p>Niciun pronostic salvat.</p>
        )}
      </div>
      {rejected.length > 0 && (
        <div className="save-report-rejected">
          <h4>Respinse ({rejected.length})</h4>
          <ul>
            {rejected.map(([id, reason]) => (
              <li key={id}>
                <strong>{matchLabel(fixtures, id)}</strong>: {reason}
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
}
