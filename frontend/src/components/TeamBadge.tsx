import { useState, type CSSProperties } from "react";
import type { Team } from "../types";
import { teamLogoUrl } from "../lib/teamLogos";

export function TeamBadge({ team, compact = false }: { team: Team; compact?: boolean }) {
  const url = teamLogoUrl(team);
  const [failed, setFailed] = useState<string | null>(null);
  const showLogo = url !== null && url !== failed;
  return (
    <span className="team-emblem">
      <span
        className={`team-badge${showLogo ? " has-logo" : ""}`}
        aria-hidden={showLogo ? undefined : true}
        style={{ "--team-color": team.color, "--team-accent": team.accent } as CSSProperties}
      >
        {showLogo ? <img src={url} alt={`Sigla ${team.name}`} width="56" height="64"
          loading="lazy" decoding="async" onError={() => setFailed(url)} /> : <span>{team.initials}</span>}
      </span>
      {!compact && url && team.logoCredit && <small className="team-logo-credit">{team.logoCredit}</small>}
    </span>
  );
}
