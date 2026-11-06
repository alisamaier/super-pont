import type { CSSProperties } from "react";
import type { Team } from "../types";

export function TeamBadge({ team }: { team: Team }) {
  return (
    <span
      className="team-badge"
      aria-hidden="true"
      style={
        {
          "--team-color": team.color,
          "--team-accent": team.accent,
        } as CSSProperties
      }
    >
      <span>{team.initials}</span>
    </span>
  );
}
