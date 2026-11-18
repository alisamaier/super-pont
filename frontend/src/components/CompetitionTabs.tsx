import type { KeyboardEvent } from "react";
import type { CompetitionView } from "../api/types";

export function CompetitionTabs({
  competitions,
  activeId,
  panelId,
  onSelect,
}: {
  competitions: readonly CompetitionView[];
  activeId: string;
  panelId: string;
  onSelect: (id: string) => void;
}) {
  const tabId = (id: string) => `${panelId}-${id}`;

  function moveFocus(event: KeyboardEvent<HTMLButtonElement>, index: number) {
    const last = competitions.length - 1;
    let target: number;
    switch (event.key) {
      case "ArrowLeft":
        target = index === 0 ? last : index - 1;
        break;
      case "ArrowRight":
        target = index === last ? 0 : index + 1;
        break;
      case "Home":
        target = 0;
        break;
      case "End":
        target = last;
        break;
      default:
        return;
    }
    event.preventDefault();
    document.getElementById(tabId(competitions[target].id))?.focus();
  }

  return (
    <div className="competition-tabs" role="tablist" aria-label="Competiții">
      {competitions.map((competition, index) => {
        const selected = competition.id === activeId;
        return (
          <button
            key={competition.id}
            id={tabId(competition.id)}
            type="button"
            role="tab"
            aria-selected={selected}
            aria-controls={panelId}
            tabIndex={selected ? 0 : -1}
            onClick={() => onSelect(competition.id)}
            onKeyDown={(event) => moveFocus(event, index)}
          >
            {competition.name}
          </button>
        );
      })}
    </div>
  );
}
