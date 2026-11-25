# Super Pont

React + TypeScript + Vite on Vercel, Java 25 + Spring Boot + Gradle on Render,
and PostgreSQL on Neon. The backend supports countries, competitions, teams,
seasons, configurable phases, rounds and matches. See the
[football API guide](backend/README.md) for endpoints, validation and development sample data.

## Local Docker Compose

Install Docker with Compose v2. From the repository root:

```bash
cp .env.example .env
```

On PowerShell use `Copy-Item .env.example .env`. Set a non-empty
`POSTGRES_PASSWORD` in the ignored root `.env`, then start:

```bash
docker compose up --build
```

- Frontend: http://localhost:5173
- Frontend static health file: http://localhost:5173/health.json
- Backend health: http://localhost:8080/actuator/health
- PostgreSQL: 127.0.0.1:5433
- Mailpit inbox: http://localhost:8025 (SMTP: 127.0.0.1:1025)

Compose runs Java 25 through the Gradle Wrapper and Node 22 through Vite.
The first run downloads build dependencies. The browser API URL uses localhost;
the backend database connection uses the Compose hostname `db`.
With Podman, start the machine if needed and use `podman compose up -d` in place
of `docker compose up --build`.
The frontend container polls mounted source files so edits on Windows/WSL update
Vite reliably. After changing Compose configuration, use `podman compose up -d`
to apply it; restarting an existing container alone does not update its environment.
Stop with `docker compose down`. Adding `-v` deliberately deletes local database data.
Changing a password in `.env` does not change an existing database role's password;
update the role separately or recreate the disposable volume.

## Run applications on the host

Install JDK 25 and Node.js 22; set `JAVA_HOME` to JDK 25.
Complete the root `.env` setup above and run `docker compose up -d db mailpit`.

Frontend:

```bash
cd frontend
cp .env.example .env
npm ci
npm run dev
```

Backend: export `SPRING_PROFILES_ACTIVE=dev` and `DATABASE_PASSWORD` (matching the
root `.env`) in your shell, then run:

```bash
cd backend
./gradlew bootRun
```

PowerShell: use `$env:SPRING_PROFILES_ACTIVE='dev'`, set
`$env:DATABASE_PASSWORD` to your chosen value, and run `.\gradlew.bat bootRun`.
`backend/.env.example` lists the variables. Spring Boot does not automatically
load `.env` files; export them or configure them in your IDE.
Localhost database and CORS defaults exist only in the `dev` profile.

## Test the frontend and API together

1. Start the local Compose stack. Its backend uses `SPRING_PROFILES_ACTIVE=dev`,
   so Flyway applies the existing schema and loads the development sample season.
2. Check [backend health](http://localhost:8080/actuator/health) and
   [competitions](http://localhost:8080/api/competitions). The latter should return
   a paginated JSON response with at least one item in a seeded development database.
3. Open [Super Pont](http://localhost:5173). Choose a competition, season, and
   round. The schedule comes from the backend's read endpoints, including its
   configured phases; an unseeded database shows an empty state.
4. Sign in and save a prediction for a future match. Change rounds and return, reload,
   or sign into the same account in another browser. The saved score appears in each
   session. Legacy browser-local scores stay in browser storage.
5. To check recovery, stop the backend, reload the frontend, and confirm it shows
   an error. Start the backend again and click **Reîncearcă**.

For a host-run frontend, set `VITE_API_URL=http://localhost:8080` in its ignored
`.env` (or shell) before starting Vite. Use the backend origin without `/api`.
Spring already allows the exact origin in `FRONTEND_URL`; for the default Vite
URL use `http://localhost:5173`. If you change the frontend hostname or port,
update `FRONTEND_URL` and restart the backend. No Vite proxy is needed.

## Test accounts locally

1. Start the stack and open Super Pont at `http://localhost:5173`. Use
   `localhost` for both the frontend and API.
2. Click **Cont nou**. Register with a public **Nume**, a unique email and a password
   of at least eight characters and at most 72 UTF-8 bytes. The app opens
   **Confirmă adresa de email**. Read the code in [Mailpit](http://localhost:8025),
   confirm it, then sign in. An unverified account cannot use protected features.
3. Sign in with that email and password. The public name appears in the header
   and persists when you reload. In the browser network panel, `GET /api/users/me`
   returns the profile and the API sets an HttpOnly `JSESSIONID` cookie.
4. Click **Deconectare**. Reload and confirm the account links return. A request
   to `GET /api/users/me` now returns `401`, and the football schedule still loads.
5. Try registering the same email with different capitalization or surrounding
   whitespace, or signing in with a wrong password. The form should display an
   error. Names are unique regardless of capitalization or surrounding whitespace;
   email login still ignores case and surrounding whitespace.

See the [authentication API](backend/README.md#authentication) for CSRF headers,
status codes, and local admin setup.

## Local email verification with Mailpit

From the repository root in PowerShell, use an ignored `.env` with a nonempty
`POSTGRES_PASSWORD`. Create it only if it does not already exist:

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
# Set POSTGRES_PASSWORD in .env before starting.
podman compose up -d --build
podman compose restart backend
```

With Docker, run `docker compose up -d --build` and `docker compose restart backend`.
Open [Super Pont registration](http://localhost:5173/register) and the
[Mailpit inbox](http://localhost:8025). Compose delivers SMTP to `mailpit:1025`;
it requires no mail credentials and sends nothing to the real recipient.
Any unique address such as `fan@example.test` works locally. For a host-run
backend, the `dev` profile defaults to `127.0.0.1:1025`; start just the dependencies
with `podman compose up -d db mailpit` and export the database settings above.

The six-digit code lasts 10 minutes and permits five incorrect attempts. Paste
it into the single code field and click **Confirmă contul**. Successful confirmation
opens login with **Contul a fost confirmat. Te poți autentifica.** Refreshing the
confirmation page resumes through an HttpOnly registration cookie. If the cookie
expires or is lost, sign in with the pending account's email and password to resume.
Login resumes the existing code and counters; it does not send a new email.

**Retrimite codul** and email corrections have a server-enforced 60-second cooldown.
They invalidate the previous code. Email sending is limited to six attempts per
registration per hour and 20 per client IP per rolling hour, including failed SMTP
attempts. Correcting the email or logging in again does not reset these limits.
Only a pending account's registration cookie can authorize an email correction.
Existing accounts are deliberately marked legacy verified by Flyway V9, and demo
accounts are inserted as verified. Passwords, predictions, points, and preferences
are preserved.

To test an actual delivery outage locally:

```powershell
podman compose stop mailpit
# Register a new address: the confirmation page explains that delivery failed.
podman compose start mailpit
# Wait for the displayed cooldown, then use Retrimite codul and read Mailpit.
```

The pending account survives the failure. You can also leave the page, sign in
with its correct credentials, and retry; no second registration is needed.
Mail delivery uses bounded SMTP timeouts. An SMTP outage does not mark the whole
application unhealthy; database health checks remain enabled.

## Local password recovery

Password recovery uses the same SMTP integration and Mailpit inbox as email
confirmation. From the repository root, start or update the local stack:

```powershell
podman compose up -d --build
podman compose restart backend
```

Docker equivalents: `docker compose up -d --build` and
`docker compose restart backend`. Open [login](http://localhost:5173/login),
choose **Ai uitat parola?**, enter an existing account's email, and click
**Trimite linkul**. Open [Mailpit](http://localhost:8025), read **Super Pont —
Resetează parola**, and open **Alege o parolă nouă**. Enter matching passwords
and click **Resetează parola**. Login then displays **Parola a fost resetată.
Autentifică-te cu noua parolă.** Use the new password to sign in.

`PASSWORD_RESET_FRONTEND_URL=http://localhost:5173` configures the frontend origin
used in reset links (HTTP(S), no trailing slash or path). It defaults to
`FRONTEND_URL` when omitted. Set it to the public HTTPS frontend origin in
production; no additional SMTP credentials or services are required beyond the
existing SMTP variables below. A host-run backend reads exported environment
variables; Compose reads the ignored root `.env`.

Each link contains 32 cryptographically random bytes and lasts 30 minutes. Only
its SHA-256 hash is stored. Opening or requesting a link changes neither the
password nor existing sessions. Requesting another link preserves earlier
unexpired links. Successful reset consumes every reset link for that account and
revokes its sessions through the persisted password version. Account name,
email-verification state, predictions, points, and preferences are preserved.
An unverified account still needs email confirmation after resetting its password.

Known and unknown emails always receive the same confirmation. SMTP failures
also return that message to avoid exposing whether an account exists; retry the
request after delivery is restored. To test locally, run `podman compose stop
mailpit`, request a link, then `podman compose start mailpit` and request again.
Allow about 35 seconds after restarting Mailpit for the backend's DNS caches to
expire if the local container receives a different IP address.
Previously delivered links remain usable during an outage.

Default rolling limits per backend instance: three requests per normalized email
per hour, ten requests per IP per hour, thirty validation/reset submissions per
IP per 15 minutes, and ten per token per 15 minutes. Failures count too. Exceeding
a limit returns `429` with `Retry-After`. These use the existing bounded in-memory
auth limiter and can be configured using `APP_AUTH_RATE_LIMIT_PASSWORD_RESET_EMAIL_ATTEMPTS`,
`APP_AUTH_RATE_LIMIT_PASSWORD_RESET_IP_ATTEMPTS`,
`APP_AUTH_RATE_LIMIT_PASSWORD_RESET_SUBMISSION_IP_ATTEMPTS`, and
`APP_AUTH_RATE_LIMIT_PASSWORD_RESET_TOKEN_ATTEMPTS` (corresponding `_WINDOW`
variables accept durations such as `1h` or `15m`).

Reset tokens stay in URL fragments, so page requests and access logs do not
receive them. The frontend uses `no-referrer` before loading resources and sends
the token only in CSRF-protected POST bodies to the backend. Do not add analytics
that capture reset fragments or log auth request bodies.

### Real SMTP delivery

Configure these environment variables in the backend host's settings (Spring
does not load `.env` files automatically). No SMTP provider credentials are included
in this repository. Supply the actual host, port, credentials, and a provider-approved
sender/domain before using real delivery. [backend/.env.example](backend/.env.example)
also includes placeholders.

```dotenv
SMTP_HOST=smtp.provider.example
SMTP_PORT=587
SMTP_USERNAME=your-smtp-username
SMTP_PASSWORD=your-smtp-password
SMTP_AUTH=true
SMTP_STARTTLS_ENABLED=true
SMTP_STARTTLS_REQUIRED=true
SMTP_SSL_ENABLED=false
SMTP_FROM=Super Pont <no-reply@your-verified-domain.example>
```

For provider-required implicit TLS on port 465, use `SMTP_SSL_ENABLED=true`,
`SMTP_STARTTLS_ENABLED=false`, and `SMTP_STARTTLS_REQUIRED=false`. TLS certificate
and hostname verification remain enabled. Keep production off the `dev` profile.
Compose is deliberately wired to local Mailpit; configure real SMTP on the
production backend rather than placing secrets in frontend `VITE_*` variables.
Messages include Romanian plain text and HTML alternatives, the code, and its
UTC expiration time. Codes and SMTP credentials are never returned in API JSON
or written to application logs.

## SportStatsAPI SuperLiga import

The backend imports `GET https://api.sportstatsapi.com/api/v1/soccer/matches`
with `league_name=Romania: Superliga`, `date_from`, `date_to`, `limit`, and
`offset`. It sends the key only as `Authorization: Bearer ...`. The frontend
continues reading our PostgreSQL-backed API; it never receives the key or calls
SportStatsAPI. No logo URLs are generated.

Set these in the ignored root `.env` for Compose (or export them in the backend
process/provider environment for a host-run or deployed backend):

```dotenv
SPORTSTATS_API_KEY=your-provider-key
SPORTSTATS_SYNC_ENABLED=false
SPORTSTATS_SYNC_INTERVAL=PT30M
SPORTSTATS_DAYS_BACK=7
SPORTSTATS_DAYS_AHEAD=14
```

Leave automatic synchronization disabled for the first import. Start/recreate
the backend so Flyway applies **V12 — sportstats import** and loads the settings:

```powershell
podman compose up -d db mailpit backend frontend
podman compose restart backend
```

V12 adds external provider identities, uniqueness constraints, a database-wide
sync lease/backoff row, the provider update timestamp, and non-scoring
`SUSPENDED`/`UNKNOWN` statuses. It preserves all existing UUIDs and predictions.
The development seed labels its competition **SuperLiga DEMO**; the imported
**SuperLiga** is a separate competition and season. Imported clubs also receive
separate UUIDs even when their display names match demo clubs. No existing
fixtures are deleted, recreated, or matched by names.

Use a verified **ADMIN** account (see [local admin setup](backend/README.md#authentication)).
From the repository root, the first manual import for the supplied sample is:

```powershell
.\backend\scripts\Sync-SportStats.ps1 -DateFrom 2026-11-15 -DateTo 2026-12-06
```

The command prompts for the administrator's email and password, logs in through
the existing cookie/CSRF flow, invokes `POST /api/admin/sportstats/sync`, prints
the summary, and logs out. It never asks for or sends the provider key. Optional
`-ApiUrl https://your-backend.example` selects a deployed backend. The endpoint
accepts JSON `dateFrom`/`dateTo`, with at most 367 inclusive days. Summary fields
are `pages`, `created`, `updated`, `ignored`, `errors`, `warnings`, and `completedAt`.
Unchanged records count as ignored; malformed records and unconfirmed results
are reported with controlled warning codes.

The supplied response identifies SuperLiga as `l60`, the current regular
division as `dx2`, and rounds as `Week 16`/`Week 17`. Plain `Week N`/`Round N`
labels use a configured division-to-phase mapping, initially `dx2=REGULAR`.
Explicit `Play-off N`/`Championship Round N` and `Play-out N`/`Relegation Round N`
labels identify their phases directly. Round identities include provider,
competition, season, division and phase, so equal round numbers remain separate.
If the provider sends another division with a plain round label, import reports
`PHASE_MAPPING_REQUIRED` and skips it until its verified code is configured:

```dotenv
# Replace placeholders only with actual codes from the provider.
SPORTSTATS_PHASE_DIVISIONS=dx2=REGULAR,verified_playoff_code=PLAY_OFF,verified_playout_code=PLAY_OUT
```

No phase is inferred from a hardcoded regular-season round count. The API does
not provide season date boundaries in this response: `2026/2027` defaults to
1 July 2026–30 June 2027, configurable with `SPORTSTATS_SEASON_START_MONTH` and
`SPORTSTATS_SEASON_START_DAY`. Out-of-season kickoffs are reported instead of
silently changing the season. A new match without a known `start_at` is skipped;
an existing postponed/cancelled match can retain its last known kickoff while
its status and scores are updated.

Scores come from `home_team.score`/`away_team.score`. Missing scores are null,
never invented as 0–0. Only a valid `FINISHED` result scores points. Corrected
results and a final changed back to live, suspended, cancelled or unconfirmed
are reflected by the existing scoring queries, without awarding points twice.
The existing prediction deadline and visibility rules use the imported kickoff
and the server clock. A result without both valid final scores becomes
`UNKNOWN`, without points. This integration does not change the scoring formula
or introduce a different extra-time/penalty scoring rule.

All pages are fetched before football data changes. HTTP/JSON/pagination errors
leave the saved snapshot intact. Requests have bounded timeouts, at most 50
pages, no automatic request retries and no redirect following. `Retry-After`
(seconds or HTTP date) is saved in PostgreSQL and blocks subsequent manual and
automatic attempts until that time. Other failures use a 30-second backoff.
`402`/`PAYMENT_REQUIRED` stops the run: there is no payment client or payment retry.
The database lease prevents overlapping runs across backend instances; it expires
after 15 minutes following a crash. During the import transaction its row remains
locked through commit. Provider error bodies and credentials are excluded from
logs and API errors; logs contain safe status warnings and summary counts.

After the first import, automatic synchronization is explicitly enabled with:

```dotenv
SPORTSTATS_SYNC_ENABLED=true
SPORTSTATS_SYNC_INTERVAL=PT30M
```

Then run `podman compose up -d backend` (or restart the configured deployed
backend). The scheduler first runs after one interval and uses today in UTC,
minus seven days through plus fourteen days by default. **Automatic synchronization
works only while the backend is running. If the hosting service sleeps, updates
stop until it resumes.** No external scheduler wakes a sleeping host.

Offline verification:

```powershell
cd backend
.\gradlew.bat test --tests '*SportStats*Tests'
cd ..\frontend
npm test -- src/sportstatsSchedule.test.tsx
npm run build
```

`backend/src/test/resources/sportstats/superliga-real-response.json` is a projection
of the supplied real 16-match response, preserving every imported field, codes,
scores, Unicode names, timestamps, periods and pagination metadata; unused
events, TV, highlights and referee details are omitted. Tests also synthesize
status changes, date changes, errors and separate phases from these rows. HTTP
tests use a loopback server, never the real provider. Builds force automatic
sync off even when the developer's environment enables it. Optional
`TEST_SPORTSTATS_DATABASE_URL` and `TEST_DATABASE_USERNAME`/`TEST_DATABASE_PASSWORD`
can run the same integration tests on a disposable PostgreSQL database; never
point them at the application or production database.

## Verify predictions in PostgreSQL

1. In the local Compose stack, sign in and save a score for a match whose kickoff
   is in the future. If the sample fixtures have already started, use a local admin
   to [create a future match](backend/README.md#endpoints) within its season dates.
2. Run `podman compose exec db psql -U superpont -d superpont` from the repository
   root. Use `docker compose` if applicable and substitute the local database user
   and name if you changed them.
3. Run the query below with your normalized email. Update the score and rerun the query to
   check that the same row has the new scores and a later `updated_at`. Delete
   the prediction before kickoff and rerun the query to confirm the row is gone.

```sql
SELECT p.id, p.match_id, p.home_score, p.away_score, m.kickoff,
       p.created_at, p.updated_at
FROM predictions AS p
JOIN users AS u ON u.id = p.user_id
JOIN football_matches AS m ON m.id = p.match_id
WHERE u.email = 'you@example.test'
ORDER BY p.updated_at DESC;
```

## Demo SuperLiga leaderboard

From the repository root, run these commands in PowerShell to opt in to the
leaderboard seed. The PostgreSQL volume must be your local development database.

```powershell
$env:LEADERBOARD_DEMO = 'true'
podman compose up -d
podman compose restart backend
```

Use `docker compose up -d` if you use Docker. Open
[Clasament](http://localhost:5173/clasament) and sign in as `demo_ana@leaderboard.example.test` with
`SuperPontDemo2026!` to highlight Ana's row. The sample ranks are Ana 18 (1),
Mara 13 and Radu 13 (2), Demo ascuns 10 (4), Demo public 4 (5),
39 numbered participants on 1 point (all rank 6), and Vlad 0 (45).
On a fresh demo database this gives 45 users with evaluated predictions:
20 on page 1, 20 on page 2, and 5 on page 3. Participant 15 at the end
of page 1 and Participant 16 at the start of page 2 both retain rank 6.
The numbered participants use `participant01@leaderboard.example.test` through
`participant39@leaderboard.example.test`, with the same demo password.
To see Ana's predictions in the ticket,
open matchday **Etapa 17** on the matches page.

All registered accounts appear in the current-season leaderboard. Accounts with
no evaluated predictions start at zero points and share a rank with other users
on the same total. Points continue to come only from finished matches.

Click **Ana** in the leaderboard to open her current-season predictions. You can
also open [Ana's predictions](http://localhost:5173/clasament/utilizatori/20000000-0000-0000-0000-000000000001)
directly. To compare accounts, sign in as `demo_radu@leaderboard.example.test` with the same demo password.
Use the numbered pages or **Înapoi** / **Înainte** below the table. Page indexes
in the URL are zero-based: `/clasament?page=2` opens the last demo page.
Refresh, open a user's predictions, and use **Înapoi la clasament** to check
page restoration. Selecting another competition resets to page 1; competition
and season IDs are retained in page and prediction links. With more registered
accounts, the counts and last page adjust automatically. The repeatable seed
preserves existing users, preferences, predictions, and corrected results.
Ana and Radu have hidden predictions for **Etapa 18**, a scheduled game on
20 June 2027. Their live game in **Etapa 17** shows the predicted score and
pending points. The two finished games show the same evaluated points as
their own tickets. The historical prediction is excluded from this page.

The page refreshes at the server's kickoff deadline, on returning to the browser
tab, and once per minute while showing predictions. Final-score corrections
also appear on refresh.

To try the visibility change immediately in the local demo, run
`podman compose exec db psql -U superpont -d superpont` (adjust the local user
and database if needed), then run this SQL and reopen Ana's page. After one
minute, the page fetches the revealed prediction and editing is locked.

```sql
UPDATE football_matches
SET kickoff = now() + INTERVAL '1 minute'
WHERE id = '20000000-0000-0000-0000-000000000044';
```

Restore the sample kickoff afterward with
`UPDATE football_matches SET kickoff = '2027-06-20T18:00:00Z' WHERE id = '20000000-0000-0000-0000-000000000044';`.

The opt-in seed places its fixtures in the 2026/2027 demo season and adds a
historical comparison. It preserves results and predictions already in the
database when rerun. Remove the environment variable to disable further seed
loading. Rows already inserted stay in the local database.

## Visibility settings demo

The public-name menu opens **Setările contului**, with independent
**Profil**, **Schimbă parola**, and **Pronosticuri** sections. Display names may
contain up to 64 characters, are unique regardless of capitalization or surrounding
whitespace, and do not change the login email. Saving your current name or changing
only its capitalization is allowed. Password changes
require the current password and a new registration-valid password, then require
login again on every session. New accounts have prediction hiding disabled;
existing accounts retain their saved choice.

The opt-in leaderboard seed also creates two visibility test accounts. In the
local development Compose stack, load the data and new migration with:

```powershell
$env:LEADERBOARD_DEMO = 'true'
podman compose up -d
podman compose restart backend
```

Use `docker compose` instead if applicable. Keep this seed on the `dev` profile
and a local development database. It is repeatable: existing accounts,
predictions, and changed preferences are preserved.

Both accounts use password `SuperPontDemo2026!`:

| Account | Initial preference | Future prediction (Etapa 18) | Started prediction (Etapa 17) |
| --- | --- | --- | --- |
| `demo_ascuns@leaderboard.example.test` | Hiding enabled | 2–0, hidden from others | 1–2, visible |
| `demo_public@leaderboard.example.test` | Hiding disabled | 3–1, visible immediately | 2–2, visible |

The remaining demo login emails are `demo_ana@leaderboard.example.test`,
`demo_radu@leaderboard.example.test`, `demo_mara@leaderboard.example.test`, and
`demo_vlad@leaderboard.example.test`, all with the same password above.

The future fixture is scheduled for 20 June 2027 at 18:00 UTC; the started
fixture is a `LIVE` demo game from 4 November 2026. These examples belong to
the 2026/2027 demo season, which must be the current season for public views.
Both accounts also have a prediction for the finished 0–0 match in Etapa 17:
Demo ascuns predicted 0–0 (10 points), and Demo public predicted 1–0 (4 points).
They appear in the leaderboard, where their names link to their predictions.
You can also use their direct pages:

- [Demo ascuns](http://localhost:5173/clasament/utilizatori/20000000-0000-0000-0000-000000000006)
- [Demo public](http://localhost:5173/clasament/utilizatori/20000000-0000-0000-0000-000000000007)

Sign in as `demo_ana@leaderboard.example.test` to compare both pages from another account. Leaderboard
and user-prediction pages require a session: signed-out direct visits go to login
and return to the requested page after authentication. "Meciuri" and "Regulament"
remain available to visitors. Sign in as either owner to see their own future score. Open the
name menu → **Setările contului** and toggle **Ascunde pronosticurile mele
până la start**. Reopen the owner's page from the other account (a separate
browser profile works), and confirm existing predictions follow the change.
Refresh settings and sign out/in to verify persistence. Started scores remain
visible with either setting; editing still locks at scheduled kickoff.

## Build and verification

With the frontend environment configured as above:

```bash
cd backend
./gradlew build
cd ../frontend
npm ci
npm test
npm run lint
npm run build
```

Windows uses `.\gradlew.bat build`. Backend integration tests use isolated
in-memory databases and verify the football API, Flyway migrations, development-seed
isolation, health, allowed CORS, preflight, and rejected origins.
They require no production credentials. Java 25 requires the included Gradle 9.1
wrapper; Spring Boot is upgraded to 4.0 for that supported build combination.

Build the production image from the repository root:

```bash
docker build -t super-pont-backend ./backend
```

The multi-stage Dockerfile runs the wrapper build and tests, then copies only
`app.jar` into a non-root Java 25 runtime. It limits the Java heap to a percentage of container memory and supports graceful shutdown.

## Hosted test deployment

See the complete [free deployment guide](docs/DEPLOY.md), checked against official
provider documentation on 25 November 2026. It covers Neon, Render Docker, Vercel,
Brevo HTTPS email, the first verified administrator, manual imports and hosted checks.
No resources are created or published by these preparation changes.

Use `SPRING_PROFILES_ACTIVE=prod` on Render. This profile excludes demo seeds,
uses PostgreSQL for logo bytes and disables both automatic match and logo imports.
SMTP ports are blocked on Render Free; use `MAIL_PROVIDER=brevo` and secrets only
in Render's environment. Local Compose continues to use Mailpit and the logo volume.

For the default free domains, route `/api` through Vercel to Render using
`frontend/vercel.json`. Configure its destination with
`backend/scripts/Configure-Vercel.ps1 -BackendUrl https://<service>.onrender.com`.
On Vercel set `VITE_API_URL` to the stable **frontend** HTTPS origin in this proxy
setup; local development still uses `http://localhost:8080`. Cookies remain
HttpOnly, Secure and SameSite=Lax with the existing session CSRF protection.
The guide includes a free Render Static Site alternative for Vercel Hobby restrictions.

## Team logos (SportStats integration)

Local filesystem storage remains the default outside `prod`; hosted `prod` stores validated PNG bytes in PostgreSQL through migration V14. Automatic logo checks default to off; run the manual script when needed.

Imported teams keep their existing UUIDs. Logos are matched by the provider's
team identity, never by their name or by the demo data. The real catalog currently
returns `code`, `logo_url` and `logo_thumb_url`; its numeric `id` is optional.
The documented `/soccer/teams/{id}/logo` route is used only if the catalog actually
supplies a numeric ID. A match code such as `t1nc` is never substituted for that ID.
See the [team catalog](https://sportstatsapi.com/docs/api/soccer-teams/) and
[logo endpoint](https://sportstatsapi.com/docs/api/soccer-teams-id-logo/).

Start or update the local backend (Flyway applies V13 automatically):

```powershell
Set-Location D:\Alisa\super-pont
podman compose up -d --force-recreate backend
.\backend\scripts\Sync-TeamLogos.ps1 -Email admin@example.test
```

The script asks for the administrator password securely. It authenticates using
the existing session and CSRF flow, then prints downloaded/cached/missing/error
counts. No provider key is entered in the script. Add `-Force` to refresh existing
images; server-side Retry-After and per-team failure cooldowns still apply.

The existing `SPORTSTATS_API_KEY` stays in the ignored root `.env`, backend only.
`TEAM_LOGO_AUTOMATIC_ENABLED=true` checks missing imported logos every
`TEAM_LOGO_CHECK_INTERVAL=PT5M` while the backend runs, starting one minute after
startup. Successfully cached logos are not downloaded again by this check or by
match synchronization. This is independent from `SPORTSTATS_SYNC_ENABLED`, which
still defaults to false. Set `TEAM_LOGO_AUTOMATIC_ENABLED=false` for manual only.

Compose mounts the named `team_logo_cache` volume at `/var/lib/super-pont/logos`.
It survives backend/container recreation and ordinary `compose down/up`; removing
volumes with `down -v` removes it. Outside Compose, configure a persistent writable
`TEAM_LOGO_CACHE_DIR` (default `./.local-cache/team-logos` relative to the backend
working directory). Retain both PostgreSQL metadata and this volume in backups.

Only HTTPS images on the provider's image host, under its soccer-logo path, are
accepted. Redirects are disabled; requests to that CDN never contain the API key.
PNG/JPEG/WebP images are decoded, limited to 2 MiB and 2048×2048/4 million pixels,
then stored as transparent-capable PNGs using immutable SHA-256 filenames. SVG
and other types are rejected. The source URL, numeric ID if supplied, and credit
remain in PostgreSQL; frontend DTOs contain only our backend URL and credit.

Missing/404 logos are checked again after 24 hours, other per-image failures after
an hour; provider Retry-After blocks both logo and match synchronization. Old images
survive failed updates. Images are served from the configured cache without upstream calls at
`GET /api/teams/{uuid}/logo?v={hash}` with ETag and public cache headers.
Missing images use the existing badge. Supplied image credits appear with logos.
