import { useEffect, useState, type MouseEvent } from "react";
import {
  ArrowRight,
  CalendarDays,
  Check,
  CheckCheck,
  ChevronRight,
  CircleDot,
  ClipboardList,
  Goal,
  LayoutGrid,
  LoaderCircle,
  RotateCw,
  TriangleAlert,
  Trophy,
  X,
} from "lucide-react";
import { useFootballSchedule } from "./hooks/useFootballSchedule";
import { usePredictions } from "./hooks/usePredictions";
import { fixtureDays } from "./lib/fixtures";
import { pendingDrafts, type SaveResult } from "./lib/predictions";
import { MatchCard } from "./components/MatchCard";
import { PredictionSummary } from "./components/PredictionSummary";
import { SaveReport } from "./components/SaveReport";
import { ScheduleSelect } from "./components/ScheduleSelect";
import type { ScoreDraft } from "./types";
import { authErrorMessage, getCurrentUser, logout, type AuthUser } from "./api/auth";
import { AuthPage } from "./components/AuthPage";
import "./App.css";
import "./components/Auth.css";

function App() {
  const [path, setPath] = useState(() => window.location.pathname);
  const authPage = path === "/login" || path === "/register" ? path : null;
  const [user, setUser] = useState<AuthUser | null>();
  const [registeredUsername, setRegisteredUsername] = useState("");
  const [signingOut, setSigningOut] = useState(false);

  useEffect(() => {
    const syncPath = () => setPath(window.location.pathname);
    window.addEventListener("popstate", syncPath);
    return () => window.removeEventListener("popstate", syncPath);
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    getCurrentUser(controller.signal)
      .catch(() => null)
      .then((account) => {
        if (!controller.signal.aborted) setUser(account);
      });
    return () => controller.abort();
  }, []);

  function navigate(destination: string) {
    if (window.location.pathname !== destination) {
      window.history.pushState(null, "", destination);
    }
    setPath(destination);
  }

  function followLink(event: MouseEvent<HTMLAnchorElement>, destination: string) {
    if (event.button || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
    event.preventDefault();
    navigate(destination);
  }

  async function signOut() {
    setSigningOut(true);
    try {
      await logout();
      setUser(null);
      navigate("/");
    } catch (cause) {
      notify(authErrorMessage(cause));
    } finally {
      setSigningOut(false);
    }
  }

  const catalog = useFootballSchedule();
  const { competition, season, round, phase } = catalog;
  const fixtures = catalog.matches.data ?? [];
  const days = fixtureDays(fixtures);
  const { saved, save, remove, error } = usePredictions(fixtures);
  const [draftEdits, setDraftEdits] = useState<Record<string, ScoreDraft>>({});
  const drafts = Object.fromEntries(
    fixtures.map((fixture) => [
      fixture.id,
      draftEdits[fixture.id] ?? {
        home: saved[fixture.id]?.home.toString() ?? "",
        away: saved[fixture.id]?.away.toString() ?? "",
      },
    ]),
  );
  const [view, setView] = useState<"all" | "saved">("all");
  const [day, setDay] = useState("all");
  const [validatedIds, setValidatedIds] = useState<Set<string>>(
    () => new Set(),
  );
  const [saveResult, setSaveResult] = useState<{
    roundId: string;
    result: SaveResult;
  } | null>(null);
  const [notification, setNotification] = useState<{
    text: string;
    id: number;
  } | null>(null);
  const count = Object.keys(saved).length;
  const activeDay = days.some((item) => item.id === day) ? day : "all";
  const visible = fixtures.filter(
    (fixture) =>
      (activeDay === "all" || fixture.day === activeDay) &&
      (view === "all" || saved[fixture.id]),
  );
  const unsaved = pendingDrafts(drafts, saved);
  const request = [
    { resource: catalog.competitions, label: "competițiile" },
    { resource: catalog.seasons, label: "sezoanele" },
    { resource: catalog.schedule, label: "etapele" },
    { resource: catalog.matches, label: "meciurile" },
  ].find(({ resource }) => resource.loading || resource.error);
  const empty = !competition
    ? "Nu sunt competiții disponibile."
    : !season
      ? "Această competiție nu are sezoane."
      : !round
        ? "Acest sezon nu are etape."
        : !fixtures.length
          ? "Această etapă nu are meciuri."
          : "";

  useEffect(() => {
    if (!notification) return;
    const timeout = window.setTimeout(() => setNotification(null), 5000);
    return () => window.clearTimeout(timeout);
  }, [notification]);

  function notify(text: string) {
    setNotification({ text, id: Date.now() });
  }
  function saveEntries(entries: Record<string, ScoreDraft>) {
    setValidatedIds(
      (current) => new Set([...current, ...Object.keys(entries)]),
    );
    const result = save(entries);
    if (round) setSaveResult({ roundId: round.id, result });
    setDraftEdits((current) => ({
      ...current,
      ...Object.fromEntries(
        Object.entries(result.saved).map(([id, prediction]) => [
          id,
          { home: String(prediction.home), away: String(prediction.away) },
        ]),
      ),
    }));
    const accepted = Object.keys(result.saved).length;
    const rejected = Object.keys(result.rejected).length;
    if (rejected)
      notify(
        `${accepted} salvate · ${rejected} respinse. Vezi rezultatul salvării.`,
      );
    else if (accepted)
      notify(
        accepted === 1
          ? "Pronosticul tău a fost salvat."
          : `${accepted} pronosticuri au fost salvate.`,
      );
  }
  function saveOne(id: string) {
    saveEntries({ [id]: drafts[id] });
  }
  function saveAll() {
    if (Object.keys(unsaved).length) saveEntries(unsaved);
  }
  function removeOne(id: string) {
    if (remove(id)) {
      setDraftEdits((current) => ({
        ...current,
        [id]: { home: "", away: "" },
      }));
      setValidatedIds(
        (current) => new Set([...current].filter((item) => item !== id)),
      );
      notify("Pronosticul a fost șters.");
    }
  }

  return (
    <div className="app-shell">
      <a className="skip-link" href={authPage ? "#auth-content" : "#matches"}>
        {authPage ? "Sari la cont" : "Sari la meciuri"}
      </a>
      <header className="site-header">
        <div className="header-inner">
          <a
            href="/"
            className="brand"
            aria-label="Super Pont, prima pagină"
            onClick={(event) => {
              followLink(event, "/");
              setView("all");
              setDay("all");
            }}
          >
            <span className="brand-icon">
              <Goal size={24} strokeWidth={2.4} />
            </span>
            <span>
              SUPER<span className="brand-light">PONT</span>
              <small>FOTBAL. INSTINCT. PRONOSTIC.</small>
            </span>
          </a>
          <nav className="main-nav" aria-label="Navigare principală">
            <button
              className={view === "all" ? "nav-item active" : "nav-item"}
              aria-pressed={view === "all"}
              onClick={() => {
                navigate("/");
                setView("all");
                setDay("all");
              }}
            >
              <LayoutGrid size={16} />
              Meciuri
            </button>
            <button
              className={view === "saved" ? "nav-item active" : "nav-item"}
              aria-pressed={view === "saved"}
              onClick={() => {
                navigate("/");
                setView("saved");
                setDay("all");
              }}
            >
              <ClipboardList size={16} />
              Pronosticurile mele<span className="nav-count">{count}</span>
            </button>
          </nav>
          <nav className="account-nav" aria-label="Cont" aria-busy={user === undefined}>
            {user ? (
              <>
                <span className="account-name" title={user.username}>{user.username}</span>
                <button type="button" disabled={signingOut} onClick={signOut}>
                  Deconectare
                </button>
              </>
            ) : user === null && (
              <>
                <a href="/login" onClick={(event) => followLink(event, "/login")}>Intră în cont</a>
                <a href="/register" onClick={(event) => followLink(event, "/register")}>Cont nou</a>
              </>
            )}
          </nav>
        </div>
      </header>

      {authPage && (
        <AuthPage
          key={authPage}
          path={authPage}
          user={user}
          registeredUsername={registeredUsername}
          onNavigate={followLink}
          onRegistered={(username) => {
            setRegisteredUsername(username);
            navigate("/login");
          }}
          onLogin={(account) => {
            setUser(account);
            setRegisteredUsername("");
            navigate("/");
          }}
        />
      )}
      <main className="main-container" hidden={authPage !== null}>
        <div className="breadcrumb">
          <span>Fotbal</span>
          <ChevronRight size={12} />
          <span>{competition?.name ?? "Competiții"}</span>
          <ChevronRight size={12} />
          <strong>{season?.name ?? "Program"}</strong>
        </div>
        <section className="round-banner" aria-labelledby="round-title">
          <img src="/stadium.jpg" alt="" className="stadium-image" />
          <div className="round-content">
            <div className="league-label">
              <Trophy size={15} />
              {competition?.name ?? "Fotbal"}
              {phase && (
                <>
                  <span>·</span>
                  {phase.name}
                </>
              )}
            </div>
            <h1 id="round-title">
              {round ? `Etapa ${round.number}` : "Programul meciurilor"}
              {round && <span>{round.name}</span>}
            </h1>
            <div className="round-dates">
              <CalendarDays size={15} />
              {season ? `SEZON ${season.name}` : "COMPETIȚII"}
              {days.length > 0 && (
                <>
                  <span className="banner-divider" />
                  {days[0].date}
                  {days.length > 1 && ` – ${days[days.length - 1].date}`}
                </>
              )}
            </div>
          </div>
          {round && (
            <span className="banner-round-number" aria-hidden="true">
              {String(round.number).padStart(2, "0")}
            </span>
          )}
        </section>

        <div
          className="schedule-selectors"
          role="group"
          aria-label="Programul competițiilor"
        >
          <ScheduleSelect
            label="Competiție"
            value={competition?.id}
            options={catalog.competitions.data}
            emptyLabel="Nicio competiție"
            onChange={(id) => {
              catalog.selectCompetition(id);
              setDay("all");
            }}
          />
          <ScheduleSelect
            label="Sezon"
            value={season?.id}
            options={catalog.seasons.data}
            emptyLabel="Niciun sezon"
            onChange={(id) => {
              catalog.selectSeason(id);
              setDay("all");
            }}
          />
          <ScheduleSelect
            label="Etapă"
            value={round?.id}
            options={catalog.schedule.data?.rounds.map((item) => ({
              id: item.id,
              name: `${catalog.schedule.data?.phases.find((candidate) => candidate.id === item.phaseId)?.name ?? "Etapă"} · ${item.name}`,
            }))}
            emptyLabel="Nicio etapă"
            onChange={(id) => {
              catalog.selectRound(id);
              setDay("all");
            }}
          />
        </div>

        <div className="content-layout">
          <section
            className="fixtures-section"
            id="matches"
            aria-labelledby="matches-title"
          >
            <div className="fixtures-heading">
              <div>
                <div className="section-eyebrow">
                  ALEGE SCORUL. SIMTE MECIUL.
                </div>
                <h2 id="matches-title">
                  {view === "all" ? "Meciurile etapei" : "Pronosticurile mele"}
                  <span>{view === "all" ? fixtures.length : count}</span>
                </h2>
              </div>
              {fixtures.length > 0 && (
                <span className="timezone">
                  <CircleDot size={13} />
                  Ora locală
                </span>
              )}
            </div>
            {days.length > 1 && (
              <div className="day-filters" aria-label="Filtrează după zi">
                <button
                  aria-pressed={activeDay === "all"}
                  className={
                    activeDay === "all" ? "day-filter selected" : "day-filter"
                  }
                  onClick={() => setDay("all")}
                >
                  Toate meciurile
                </button>
                {days.map((item) => (
                  <button
                    key={item.id}
                    aria-pressed={activeDay === item.id}
                    className={
                      activeDay === item.id
                        ? "day-filter selected"
                        : "day-filter"
                    }
                    onClick={() => setDay(item.id)}
                  >
                    <span className="full-day">{item.label}</span>
                    <span className="short-day">{item.short}</span>
                    <small>{item.date}</small>
                  </button>
                ))}
              </div>
            )}
            {error && (
              <div className="storage-error" role="alert">
                {error}
              </div>
            )}
            {saveResult &&
              saveResult.roundId === round?.id &&
              catalog.matches.data && (
                <SaveReport
                  result={saveResult.result}
                  fixtures={fixtures}
                  onClose={() => setSaveResult(null)}
                />
              )}
            <div className="matches-grid">
              {visible.map((fixture) => (
                <MatchCard
                  key={fixture.id}
                  fixture={fixture}
                  draft={drafts[fixture.id]}
                  saved={saved[fixture.id]}
                  showErrors={validatedIds.has(fixture.id)}
                  onChange={(draft) =>
                    setDraftEdits((current) => ({
                      ...current,
                      [fixture.id]: draft,
                    }))
                  }
                  onSave={() => saveOne(fixture.id)}
                  onRemove={() => removeOne(fixture.id)}
                />
              ))}
            </div>
            {request ? (
              <div
                className="empty-state"
                role={request.resource.error ? "alert" : "status"}
              >
                {request.resource.error ? (
                  <TriangleAlert size={30} />
                ) : (
                  <LoaderCircle className="schedule-spinner" size={30} />
                )}
                <h3>
                  {request.resource.error
                    ? "Program indisponibil"
                    : `Se încarcă ${request.label}…`}
                </h3>
                {request.resource.error && (
                  <>
                    <p>{request.resource.error}</p>
                    <button
                      className="text-button"
                      onClick={request.resource.retry}
                    >
                      <RotateCw size={15} />
                      Reîncearcă
                    </button>
                  </>
                )}
              </div>
            ) : empty ? (
              <div className="empty-state" role="status">
                <CalendarDays size={30} />
                <h3>{empty}</h3>
              </div>
            ) : (
              !visible.length && (
                <div className="empty-state">
                  <CheckCheck size={36} strokeWidth={1.3} />
                  <h3>
                    {view === "saved"
                      ? "Niciun pronostic salvat"
                      : "Niciun meci în această zi"}
                  </h3>
                  <p>
                    {activeDay !== "all"
                      ? "Niciun rezultat pentru filtrul selectat."
                      : "Biletul tău pentru această etapă este încă gol."}
                  </p>
                  <button
                    className="text-button"
                    onClick={() => {
                      setView("all");
                      setDay("all");
                    }}
                  >
                    Vezi toate meciurile
                    <ArrowRight size={15} />
                  </button>
                </div>
              )
            )}
          </section>
          {round && competition && season && (
            <PredictionSummary
              saved={saved}
              fixtures={fixtures}
              roundNumber={round.number}
              competitionName={competition.name}
              seasonName={season.name}
              unsavedCount={Object.keys(unsaved).length}
              onSaveAll={saveAll}
            />
          )}
        </div>
        <footer className="site-footer">
          <span className="footer-brand">
            SUPER<span>PONT</span>
          </span>
          <span>Din pasiune pentru fotbal.</span>
          <span>Fără mize. Doar fotbal.</span>
        </footer>
      </main>
      <div
        className="notification-region"
        aria-live="polite"
        aria-atomic="true"
      >
        {notification && (
          <div className="toast" key={notification.id}>
            <span className="toast-icon">
              <Check size={18} />
            </span>
            <span>{notification.text}</span>
            <button
              className="icon-button"
              title="Închide confirmarea"
              aria-label="Închide confirmarea"
              onClick={() => setNotification(null)}
            >
              <X size={16} />
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

export default App;
