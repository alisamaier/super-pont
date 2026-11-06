# Super Pont

## Architecture

- `frontend/`: React 19, TypeScript, Vite, npm and Lucide icons. Vercel builds `dist/`; `vercel.json` provides SPA history fallback.
- `backend/`: Java 25, Spring Boot 4, Spring MVC, Bean Validation, Spring Data JPA, Flyway and Actuator. Use the Gradle 9.1 wrapper and Groovy `build.gradle`. Render runs the multi-stage, non-root Docker image.
- PostgreSQL 16 locally through Docker or Podman Compose; Neon is the documented production database. See [README.md](README.md) for setup and deployment and [backend/README.md](backend/README.md) for the API contract.

## Backend conventions

- Organize `ro.superpont.football` by feature: `country`, `competition`, `team`, `season`, `phase`, `round`, `match`. Within each feature use `controller`, `service`, `repository`, `model` (entities/enums), and `dto` (request/view records).
- `ro.superpont.common` owns shared UUID identity, paging, repository helpers and Problem Detail exception handling. Keep business rules in transactional services, using repositories for related resources instead of service-to-service dependencies. Reads are read-only transactions; writes use `@Transactional`.
- Controllers validate request DTOs and return view DTOs, not JPA entities. Preserve `/api` paths, camelCase JSON, and status codes: `201` with `Location` for creation, `200` for registration/result updates, `400` invalid input, `404` missing references, `409` constraint conflicts.
- Top-level lists use `PageView` (`items`, `page`, `size`, `totalElements`, `totalPages`), zero-based pages and sizes 1–100. Phase and season-participant lists are arrays.

## Database and football domain

- Flyway owns schema changes in `backend/src/main/resources/db/migration/`. Add versioned migrations for schema changes; keep Hibernate `ddl-auto: validate` and `open-in-view: false`.
- Only the `dev` profile includes `db/devdata/R__demo_superliga.sql`, the repeatable, idempotent sample seed. Keep sample data out of production migrations. Use separate dev and production databases; switching profiles does not remove seeded rows.
- JPA generates UUIDs for entities. Countries own competition and team records; teams enter seasons through `season_teams`, not a permanent competition link. A competition has seasons; phases belong to seasons; round numbers are unique within a phase.
- Configure phase codes, names, order, `LEAGUE`/`KNOCKOUT` format and points per season. Do not hardcode SuperLiga round counts or qualification rules. Phase configuration does not implement standings or automatic advancement.
- Matches require distinct teams registered in the same season as the round. Keep the composite foreign keys enforcing those boundaries. Kickoff has an offset and its UTC date must fall within the season's inclusive dates.
- Actual results are separate from predictions: both scores must be nonnegative integers and supplied together. `FINISHED` requires scores, `LIVE` permits both or neither, and other statuses require neither. A result update replaces status and both scores together.

## Frontend and predictions

- Use the typed, paginated read client in `frontend/src/api/` and the selection flow in `hooks/useFootballSchedule.ts`. Reuse API DTOs and map them to display fixtures in `lib/fixtures.ts`. Keep loading, empty, retry and stale-request handling; do not fall back to hardcoded matches.
- Preserve the responsive match cards and sticky ticket sidebar. Competition, season, phase and round labels come from API data; kickoff is displayed in the browser's local time.
- `lib/predictions.ts` is the shared validator for individual saves, bulk saves and storage loading. Both prediction scores are required, each an integer **0–99** inclusive. Text input accepts ASCII digits only: no signs, whitespace, letters, decimals or scientific notation. Leading zeros are allowed and normalize to integers.
- Predictions are browser-local under `super-pont:predictions:api-v1`, keyed by backend match UUID. Show only the selected round's entries while preserving other rounds in storage. Do not remap legacy mock IDs. Keep inline errors and explicit bulk-save results; valid drafts save while invalid or partial drafts are reported, and untouched empty matches are excluded.
- Authentication and prediction APIs are not implemented. The frontend reads football data and saves predictions locally; do not assume server-side prediction persistence.

## Build and test

- From root: `podman compose up -d` or `docker compose up --build`, with an ignored `.env` based on `.env.example` and a nonempty `POSTGRES_PASSWORD`. Frontend: `http://localhost:5173`; API: `http://localhost:8080`; health: `/actuator/health`.
- In `backend/`, set `JAVA_HOME` to JDK 25. Run `./gradlew build` or `.\gradlew.bat build`; `bootRun` runs the app with the environment from the README. Backend tests use JUnit and isolated H2 in PostgreSQL mode, including Flyway and dev-seed checks. Optional `TEST_DATABASE_*` overrides for API tests must point to a disposable PostgreSQL database.
- In `frontend/`, use Node 22 and run `npm ci`, `npm test`, `npm run lint`, `npm run build`. Use `npm run dev` for Vite. Vitest/React Testing Library mock the API and exercise selection, validation and persistence. Set `VITE_API_URL=http://localhost:8080` for local development and builds.

## Security and configuration

- Keep credentials in ignored `.env` files or provider environment settings; examples contain no secrets. Spring reads environment variables, not `.env` files directly. Use a JDBC `DATABASE_URL` with separate `DATABASE_USERNAME` and `DATABASE_PASSWORD`; retain documented TLS certificate and hostname verification for Neon.
- `VITE_API_URL` is a public HTTP(S) backend origin without `/api`, embedded at build time. Never put secrets in `VITE_*` variables. Use HTTPS in production.
- `FRONTEND_URL` is one exact frontend origin with no path or trailing slash. Preserve exact-origin CORS, not wildcard previews. CORS is not authorization; existing backend writes are unauthenticated.
- Keep production off the `dev` profile. Preserve the non-root runtime and health-only Actuator exposure with health details hidden. Never run tests against a production database.
