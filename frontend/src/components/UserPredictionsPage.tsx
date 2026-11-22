import { useEffect, useState, type MouseEvent } from "react";
import { ArrowLeft, LoaderCircle } from "lucide-react";
import { getPublicPredictions, type PublicPrediction } from "../api/publicPredictions";
import { useResource } from "../hooks/useResource";
import { matchStatusLabels } from "../lib/fixtures";
import { LoadError } from "./LoadError";
import { leaderboardPath } from "../lib/leaderboard";

const dateTime = new Intl.DateTimeFormat("ro-RO", { dateStyle: "medium", timeStyle: "short" });
const load = (key: string, signal: AbortSignal) => {
  const [userId, page, competitionId, seasonId] = key.split("/");
  return getPublicPredictions(userId, Number(page), signal,
    competitionId && seasonId ? { competitionId, seasonId } : undefined);
};

export function UserPredictionsPage({
  userId,
  leaderboardPage,
  competitionId,
  seasonId,
  onNavigate,
}: {
  userId: string;
  leaderboardPage: number;
  competitionId?: string;
  seasonId?: string;
  onNavigate: (event: MouseEvent<HTMLAnchorElement>, destination: string) => void;
}) {
  const [page, setPage] = useState(0);
  const { data, loading, error, retry } = useResource(`${userId}/${page}/${competitionId ?? ""}/${seasonId ?? ""}`, load);
  const returnPath = leaderboardPath(leaderboardPage, competitionId, seasonId);

  useEffect(() => {
    const refresh = () => { if (!document.hidden) retry(); };
    window.addEventListener("focus", refresh);
    document.addEventListener("visibilitychange", refresh);
    let timer: number | undefined;
    if (data) {
      const nextDeadline = Math.min(...data.predictions.items
        .filter((item) => item.visibility === "HIDDEN")
        .map((item) => Date.parse(item.kickoff) - Date.parse(data.serverTime)));
      timer = window.setTimeout(refresh, Math.min(60_000, Math.max(1000, nextDeadline + 100)));
    }
    return () => {
      window.clearTimeout(timer);
      window.removeEventListener("focus", refresh);
      document.removeEventListener("visibilitychange", refresh);
    };
  }, [data, retry]);

  const rounds = new Map<string, PublicPrediction[]>();
  for (const prediction of data?.predictions.items ?? []) {
    const group = rounds.get(prediction.roundId) ?? [];
    group.push(prediction);
    rounds.set(prediction.roundId, group);
  }

  return (
    <main id="user-predictions" className="main-container user-predictions-page">
      <a className="text-button public-back" href={returnPath} onClick={(event) => onNavigate(event, returnPath)}>
        <ArrowLeft size={16} aria-hidden="true" /> Înapoi la clasament
      </a>
      {loading && <div className="empty-state" role="status">
        <LoaderCircle className="schedule-spinner" size={28} aria-hidden="true" />
        <p>Se încarcă pronosticurile…</p>
      </div>}
      {error && <LoadError message="Pronosticurile nu sunt disponibile momentan." onRetry={retry} />}
      {data && <>
        <header className="public-predictions-heading">
          <div>
            <h1>Pronosticuri · {data.displayName}</h1>
            <p>{data.competitionName} · {data.seasonName}</p>
          </div>
          <p className="public-total"><strong>{data.totalPoints}</strong> puncte în sezon</p>
        </header>
        {data.predictions.totalElements === 0 && (
          <p className="empty-state">Niciun pronostic în acest sezon.</p>
        )}
        {[...rounds.entries()].map(([roundId, predictions]) => {
          const round = predictions[0];
          return (
            <section key={roundId} className="public-round" aria-label={`Etapa ${round.roundNumber} · ${round.phaseName}`}>
              <h2>Etapa {round.roundNumber} <small>{round.phaseName}</small></h2>
              <div className="public-predictions-grid">
                {predictions.map((prediction) => (
                  <article className="public-prediction-card" key={prediction.matchId}
                    aria-label={`${prediction.homeTeam} – ${prediction.awayTeam}`}>
                    <div className="public-match-meta">
                      <time dateTime={prediction.kickoff}>{dateTime.format(new Date(prediction.kickoff))}</time>
                      <span>{matchStatusLabels[prediction.status]}</span>
                    </div>
                    <h3>{prediction.homeTeam} – {prediction.awayTeam}</h3>
                    {prediction.visibility === "HIDDEN" ? (
                      <p className="public-hidden">Disponibil după începerea meciului</p>
                    ) : (
                      <>
                        <p className="public-score">Pronostic <strong>
                          {prediction.predictedScore.home}–{prediction.predictedScore.away}
                        </strong></p>
                        {prediction.result === null ? <p className="public-evaluation">În așteptare</p> : (
                          <div className="public-evaluation">
                            <span>Scor final {prediction.result.homeScore}–{prediction.result.awayScore}</span>
                            <strong>{prediction.result.points} {prediction.result.points === 1 ? "punct" : "puncte"}</strong>
                          </div>
                        )}
                      </>
                    )}
                  </article>
                ))}
              </div>
            </section>
          );
        })}
        {(data.predictions.totalPages > 1 || page > 0) && (
          <nav className="leaderboard-pages" aria-label="Pagini de pronosticuri">
            <button className="text-button" disabled={page === 0} onClick={() => setPage(page - 1)}>Anterioară</button>
            <span>Pagina {page + 1}</span>
            <button className="text-button" disabled={page + 1 >= data.predictions.totalPages}
              onClick={() => setPage(page + 1)}>Următoarea</button>
          </nav>
        )}
      </>}
    </main>
  );
}
