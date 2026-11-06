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

Compose runs Java 25 through the Gradle Wrapper and Node 22 through Vite.
The first run downloads build dependencies. The browser API URL uses localhost;
the backend database connection uses the Compose hostname `db`.
With Podman, start the machine if needed and use `podman compose up -d` in place
of `docker compose up --build`.
Stop with `docker compose down`. Adding `-v` deliberately deletes local database data.
Changing a password in `.env` does not change an existing database role's password;
update the role separately or recreate the disposable volume.

## Run applications on the host

Install JDK 25 and Node.js 22; set `JAVA_HOME` to JDK 25.
Complete the root `.env` setup above and run `docker compose up -d db`.

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
4. Save a score, change rounds, and return or reload. Predictions are stored
   locally by backend match UUID under `super-pont:predictions:api-v1`. Old mock
   predictions remain in their original storage key and are not mapped to API matches.
5. To check recovery, stop the backend, reload the frontend, and confirm it shows
   an error. Start the backend again and click **Reîncearcă**.

For a host-run frontend, set `VITE_API_URL=http://localhost:8080` in its ignored
`.env` (or shell) before starting Vite. Use the backend origin without `/api`.
Spring already allows the exact origin in `FRONTEND_URL`; for the default Vite
URL use `http://localhost:5173`. If you change the frontend hostname or port,
update `FRONTEND_URL` and restart the backend. No Vite proxy is needed.

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
| `PORT` | Supplied by Render; defaults to 8080 |

The database URL must be JDBC format, not a `postgresql://user:password@...` URI.
Keep credentials in the separate variables. PostgreSQL TLS verifies the server
certificate and hostname using the JRE's trusted CA store. The backend limits its connection pool to five.
`GET /actuator/health` is public and checks database health without exposing details.
The server binds to `0.0.0.0` and honors Render's port.

Set `VITE_API_URL` in Vercel to the public HTTPS origin of the Render backend.
Vite embeds it at build time, so changing it requires a rebuild.
All `VITE_*` values are public browser configuration: never put secrets there.
A missing API URL fails the frontend build.

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
