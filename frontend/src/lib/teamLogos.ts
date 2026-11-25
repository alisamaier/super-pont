import type { Team } from "../types";
import { apiUrl } from "../config";

// Only our own image route is accepted, even if an upstream DTO contains an arbitrary URL.
export function teamLogoUrl(team: Pick<Team, "id" | "logoUrl">): string | null {
  const path = team.logoUrl;
  const prefix = `/api/teams/${team.id}/logo?v=`;
  return path?.startsWith(prefix) && /^[a-f0-9]{64}$/.test(path.slice(prefix.length))
    ? `${apiUrl}${path}` : null;
}
