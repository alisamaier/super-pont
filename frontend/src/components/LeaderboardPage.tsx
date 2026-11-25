import { useEffect, type MouseEvent } from "react";
import { LoaderCircle, Trophy } from "lucide-react";
import { footballApi } from "../api/football";
import { useResource } from "../hooks/useResource";
import { LoadError } from "./LoadError";
import type { CompetitionView } from "../api/types";
import { leaderboardPages, leaderboardPath } from "../lib/leaderboard";

const loadLeaderboard = (key: string, signal: AbortSignal) => {
  const [page, competitionId, seasonId] = key.split("/");
  return footballApi.leaderboard(Number(page), signal,
    competitionId && seasonId ? { competitionId, seasonId } : undefined);
};

export function LeaderboardPage({ currentUserId, page, onNavigate, onRedirect,
  competitions, competitionId, seasonId }: {
  currentUserId?: string;
  page: number;
  competitions: CompetitionView[];
  competitionId?: string;
  seasonId?: string;
  onRedirect: (destination: string) => void;
  onNavigate: (event: MouseEvent<HTMLAnchorElement>, destination: string) => void;
}) {
  const selectedId = competitionId ?? competitions.find((item) => item.code === "SPORTSTATS-SUPERLIGA")?.id
    ?? competitions.find((item) => item.code === "RO-SUPERLIGA")?.id ?? "";
  const seasons = useResource(selectedId || undefined, footballApi.seasons);
  const today = new Date(Date.now()).toISOString().slice(0, 10);
  const season = seasons.data?.find((item) => item.id === seasonId) ?? seasons.data
    ?.filter((item) => item.startDate <= today && item.endDate >= today)
    .sort((a, b) => b.startDate.localeCompare(a.startDate) || a.id.localeCompare(b.id))[0];
  const competition = competitions.find((item) => item.id === selectedId);
  const { data, error, loading, retry } = useResource(
    competitionId ? (season ? `${page}/${competitionId}/${season.id}` : undefined) : String(page), loadLeaderboard);
  const selectedSeasonId = competitionId ? season?.id : undefined;
  const pagePath = (number: number) => leaderboardPath(number, competitionId, selectedSeasonId);
  const lastPage = data ? Math.max(0, data.totalPages - 1) : page;
  const outOfRange = !!data && page > lastPage;

  useEffect(() => {
    if (outOfRange) onRedirect(leaderboardPath(lastPage, competitionId, selectedSeasonId));
  }, [outOfRange, lastPage, competitionId, selectedSeasonId, onRedirect]);

  useEffect(() => {
    const refresh = () => { if (!document.hidden) retry(); };
    window.addEventListener("focus", refresh);
    document.addEventListener("visibilitychange", refresh);
    const timer = data ? window.setTimeout(refresh, 60_000) : undefined;
    return () => {
      window.clearTimeout(timer);
      window.removeEventListener("focus", refresh);
      document.removeEventListener("visibilitychange", refresh);
    };
  }, [data, retry]);

  return (
    <main id="clasament" className="main-container leaderboard-page">
      <div className="leaderboard-heading">
        <Trophy size={24} aria-hidden="true" />
        <div>
          <h1>Clasament</h1>
          <p>{competition?.name ?? "SuperLiga"} · {season?.name ?? "sezonul curent"}</p>
        </div>
      </div>
      {competitions.length > 1 && <label className="leaderboard-competition">
        Competiție
        <select aria-label="Competiție clasament" value={selectedId}
          onChange={(event) => onRedirect(leaderboardPath(0, event.target.value || undefined))}>
          {!selectedId && <option value="">SuperLiga</option>}
          {competitions.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
        </select>
      </label>}
      <p className="scoring-help">
        <a href="/regulament#punctaj" onClick={(event) => onNavigate(event, "/regulament#punctaj")}>
          Cum se calculează punctele?
        </a>
      </p>
      {(loading || seasons.loading || outOfRange) && (
        <div className="empty-state" role="status">
          <LoaderCircle className="schedule-spinner" size={28} aria-hidden="true" />
          <p>Se încarcă clasamentul…</p>
        </div>
      )}
      {(error || seasons.error) && <LoadError message="Clasamentul nu este disponibil momentan."
        onRetry={seasons.error ? seasons.retry : retry} />}
      {competitionId && seasons.data && !season && <p className="empty-state">
        Încă nu există un clasament pentru sezonul curent.
      </p>}
      {data && !outOfRange && (
        <>
          {data.totalElements === 0 ? (
            <p className="empty-state">Încă nu există un clasament pentru sezonul curent.</p>
          ) : (
            <table className="leaderboard-table" aria-label={`Clasament ${competition?.name ?? "SuperLiga"}`}>
              <thead>
                <tr>
                  <th scope="col">Loc</th>
                  <th scope="col">Utilizator</th>
                  <th scope="col">Puncte</th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((row) => {
                  const isCurrentUser = row.userId === currentUserId;
                  return (
                    <tr key={row.userId} aria-current={isCurrentUser ? "true" : undefined}>
                      <td>{row.rank}</td>
                      <th scope="row">
                        <a className="leaderboard-user"
                          href={pagePath(page).replace("/clasament", `/clasament/utilizatori/${row.userId}`)}
                          onClick={(event) => onNavigate(event,
                            pagePath(page).replace("/clasament", `/clasament/utilizatori/${row.userId}`))}>
                          {row.displayName}
                        </a>
                        {isCurrentUser && <span className="leaderboard-self">Tu</span>}
                      </th>
                      <td><strong>{row.totalPoints}</strong></td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
          <div className="leaderboard-footer">
            <p className="leaderboard-count" role="status">
              {data.totalElements ? `${data.page * data.size + 1}–${Math.min((data.page + 1) * data.size, data.totalElements)}` : "0"}
              {` din ${data.totalElements} de utilizatori`}
            </p>
            {data.totalPages > 1 && (
              <nav className="leaderboard-pages leaderboard-pagination" aria-label="Pagini de clasament">
                {page > 0 ? <a className="page-direction" href={pagePath(page - 1)}
                  onClick={(event) => onNavigate(event, pagePath(page - 1))}>Înapoi</a>
                  : <span className="page-direction" aria-disabled="true">Înapoi</span>}
                <div className="leaderboard-page-numbers">
                  {leaderboardPages(page, data.totalPages).map((number) => typeof number === "number"
                    ? <a key={number} href={pagePath(number)} aria-label={`Pagina ${number + 1}`}
                        aria-current={number === page ? "page" : undefined}
                        onClick={(event) => onNavigate(event, pagePath(number))}>{number + 1}</a>
                    : <span key={number} aria-hidden="true">…</span>)}
                </div>
                {page + 1 < data.totalPages ? <a className="page-direction" href={pagePath(page + 1)}
                  onClick={(event) => onNavigate(event, pagePath(page + 1))}>Înainte</a>
                  : <span className="page-direction" aria-disabled="true">Înainte</span>}
              </nav>
            )}
          </div>
        </>
      )}
    </main>
  );
}
