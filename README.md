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
`app.jar` into a non-root Java 25 runtime. It sets a container memory limit for
the Java heap and supports graceful shutdown.

## Production variables

Set these in Render's Environment settings. Leave `SPRING_PROFILES_ACTIVE` unset,
so local development defaults are disabled.

| Variable | Value |
| --- | --- |
| `DATABASE_URL` | `jdbc:postgresql://<neon-host>/<database>?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory&channelBinding=require` |
| `DATABASE_USERNAME` | Neon database role |
| `DATABASE_PASSWORD` | Neon role password, stored only in Render |
| `FRONTEND_URL` | Exact frontend HTTPS origin, without a trailing slash or path |
| `PASSWORD_RESET_FRONTEND_URL` | Optional frontend origin for password-reset emails; defaults to `FRONTEND_URL`. No trailing slash or path. |
| `PORT` | Supplied by Render; defaults to 8080 |
| `SESSION_COOKIE_SECURE` | Defaults to `true`. The local `dev` profile defaults to `false` for HTTP. |
| `SESSION_COOKIE_SAME_SITE` | Defaults to `lax`. |
| `AUTH_TRUSTED_PROXY_CIDRS` | Optional comma-separated CIDRs for explicitly trusted reverse proxies. By default, forwarding headers are ignored. |

The database URL must be JDBC format, not a `postgresql://user:password@...` URI.
Keep credentials in the separate variables. PostgreSQL TLS verifies the server
certificate and hostname using the JRE's trusted CA store. The backend limits its connection pool to five.
`GET /actuator/health` is public and checks database health without exposing details.
The server binds to `0.0.0.0` and honors Render's port.

Set `VITE_API_URL` in Vercel to the public HTTPS origin of the Render backend.
Vite embeds it at build time, so changing it requires a rebuild.
All `VITE_*` values are public browser configuration: never put secrets there.
A missing API URL fails the frontend build.

For production sign-in, configure HTTPS custom domains on the same site, such
as `app.example.com` on Vercel and `api.example.com` on Render. Point
`VITE_API_URL` and `FRONTEND_URL` at those origins. The default Vercel and Render
hostnames are cross-site. Setting `SESSION_COOKIE_SAME_SITE=none` with Secure
cookies allows cross-site sessions only in browsers that accept third-party
cookies for that deployment.

The authentication rate limiter uses in-memory counters per backend instance.
See the [API guide](backend/README.md#authentication-rate-limits) for proxy
configuration and limits when deploying multiple instances.

## Deployment instructions (when ready)

1. **Neon:** create a Free project and PostgreSQL database, preferably near the
   chosen Render region. Open **Connect**, select Java/JDBC, and record the
   host, database, role, and password privately. A pooled Neon hostname is supported.
   Use the JDBC URL format and TLS options above.
2. **Vercel project setup:** import the repository, set **Root Directory** to
   `frontend`, framework to **Vite**, Node.js to **22.x**, install command to
   `npm ci`, build command to `npm run build`, and output directory to `dist`.
   Identify the stable production domain for the backend's `FRONTEND_URL`.
   If deploying the frontend first to obtain that domain, update its API URL and
   redeploy after the backend exists.
3. **Render:** create a **Web Service** from the repository, runtime **Docker**,
   **Root Directory** `backend`, **Dockerfile Path** `./Dockerfile`, Docker build
   context `.`, and instance type **Free**. Set the four required variables in
   the table. Set **Health Check Path** to `/actuator/health`. Use the Dockerfile
   entrypoint; no separate start/build command is needed.
4. **Vercel:** set `VITE_API_URL` for Production to the Render HTTPS origin,
   then deploy/redeploy. `frontend/vercel.json` provides history fallback to
   `index.html`; Vercel serves existing static assets normally. The scaffold
   has no router dependency, but the fallback supports future client-side routes.
5. Verify backend health returns HTTP 200 and `status: UP`, then load the
   frontend. Requests from the configured frontend must receive
   `Access-Control-Allow-Origin` matching that exact origin.

CORS accepts a single exact frontend origin. Wildcard Vercel preview domains
are not enabled; previews need a separately configured backend to use another origin.
Keep real credentials in provider dashboards and ignored local `.env` files.
These preparation changes do not deploy or push anything.

## Free plan constraints

[Render Free](https://render.com/docs/free) sleeps after 15 minutes without inbound
traffic and can take about a minute to wake. It has monthly resource limits;
Render recommends paid instances for workloads requiring production availability.
[Vercel Hobby](https://vercel.com/docs/plans/hobby) is for personal, non-commercial use.
Check [Neon's current plans](https://neon.com/pricing) for storage and compute quotas.
This configuration supports a personal project within those limits; it does not
guarantee an always-on or universally free commercial deployment.

References: [Vite on Vercel](https://vercel.com/docs/frameworks/frontend/vite),
[Neon connections](https://neon.com/docs/connect/connect-from-any-app),
[Gradle compatibility](https://docs.gradle.org/9.1.0/userguide/compatibility.html).
