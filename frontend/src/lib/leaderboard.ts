export interface LeaderboardScope {
  competitionId: string;
  seasonId: string;
}

export function leaderboardPath(page: number, competitionId?: string, seasonId?: string) {
  const query = new URLSearchParams();
  if (page) query.set("page", String(page));
  if (competitionId) query.set("competitionId", competitionId);
  if (seasonId) query.set("seasonId", seasonId);
  return `/clasament${query.size ? `?${query}` : ""}`;
}

// Zero-based page indexes, with at most seven slots including ellipses.
export function leaderboardPages(page: number, total: number): (number | "start-gap" | "end-gap")[] {
  if (total <= 7) return Array.from({ length: total }, (_, index) => index);
  if (page <= 3) return [0, 1, 2, 3, 4, "end-gap", total - 1];
  if (page >= total - 4) return [0, "start-gap", total - 5, total - 4, total - 3, total - 2, total - 1];
  return [0, "start-gap", page - 1, page, page + 1, "end-gap", total - 1];
}
