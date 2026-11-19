import { useEffect, type MouseEvent } from "react";
import { LoaderCircle, Trophy } from "lucide-react";
import { footballApi } from "../api/football";
import { useResource } from "../hooks/useResource";
import { LoadError } from "./LoadError";

const loadLeaderboard = (page: string, signal: AbortSignal) =>
  footballApi.leaderboard(Number(page), signal);

export function LeaderboardPage({ currentUserId, page, onNavigate }: {
  currentUserId?: string;
  page: number;
  onNavigate: (event: MouseEvent<HTMLAnchorElement>, destination: string) => void;
}) {
  const { data, error, loading, retry } = useResource(String(page), loadLeaderboard);
  const pagePath = (number: number) => number ? `/clasament?page=${number}` : "/clasament";

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
          <p>SuperLiga · sezonul curent</p>
        </div>
      </div>
      <p className="scoring-help">
        <a href="/regulament#punctaj" onClick={(event) => onNavigate(event, "/regulament#punctaj")}>
          Cum se calculează punctele?
        </a>
      </p>
      {loading && (
        <div className="empty-state" role="status">
          <LoaderCircle className="schedule-spinner" size={28} aria-hidden="true" />
          <p>Se încarcă clasamentul…</p>
        </div>
      )}
      {error && <LoadError message="Clasamentul nu este disponibil momentan." onRetry={retry} />}
      {data && (
        <>
          {data.totalElements === 0 ? (
            <p className="empty-state">Încă nu există un clasament pentru sezonul curent.</p>
          ) : (
            <table className="leaderboard-table" aria-label="Clasament SuperLiga">
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
                          href={`/clasament/utilizatori/${row.userId}${page ? `?page=${page}` : ""}`}
                          onClick={(event) => onNavigate(event,
                            `/clasament/utilizatori/${row.userId}${page ? `?page=${page}` : ""}`)}>
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
          {(data.totalPages > 1 || page > 0) && (
            <nav className="leaderboard-pages" aria-label="Pagini de clasament">
              {page > 0 && <a className="text-button" href={pagePath(page - 1)}
                onClick={(event) => onNavigate(event, pagePath(page - 1))}>Anterioară</a>}
              <span>Pagina {page + 1}</span>
              {page + 1 < data.totalPages && <a className="text-button" href={pagePath(page + 1)}
                onClick={(event) => onNavigate(event, pagePath(page + 1))}>Următoarea</a>}
            </nav>
          )}
        </>
      )}
    </main>
  );
}
