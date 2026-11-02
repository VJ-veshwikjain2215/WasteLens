# WasteLens Implementation Progress

Status updated: 2 November 2026.

| Phase       | Description                            | Status        | Evidence / Notes                                                                                                                                                                                                                                                                                                                                                                                          |
| ----------- | -------------------------------------- | ------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Phase 0** | Baseline & Prerequisites               | **Completed** | Java 21/25, Maven 3.9.16, Node v24.19.0, npm 11.17.0 confirmed. Docker CLI present. Decisions D1–D12 mapped.                                                                                                                                                                                                                                                                                              |
| **Phase 1** | Local Skeleton & CI                    | **Completed** | Scaffolding complete: `code/backend` (Spring Boot 3.4.0, Java 21) & `code/frontend` (React 19, TS, Vite). 5 backend tests + 2 UI tests passing; combined packaging verified serving SPA deep routes (`/login`, `/dashboard`, `/entries/new`, `/compare`) with 401 on unauthenticated `/api/**`. CI workflow (`.github/workflows/ci.yml`), `compose.yaml`, `.env.example`, multi-stage `Dockerfile` setup. |
| **Phase 2** | Provider Feasibility Checks (Neon) | **Completed** | Neon project `sparkling-sound-38215057` (`aws-ap-southeast-1`) created. Neon Auth (Managed Better Auth) enabled; JWKS endpoint verified with active EdDSA/Ed25519 signing key. Demo accounts A (`account-a@wastelens.demo`) and B (`account-b@wastelens.demo`) provisioned with UUID IDs. `V1__init_schema.sql` applied to Neon Postgres with `wastelens_private` schema, composite index, and RLS enabled (Decision D2). Preflight passed with READ COMMITTED and `pg_advisory_xact_lock` verified. |
| **Phase 3** | Authentication & Security Foundation   | **Completed** | `WasteLensJwtValidator` implements strict claim verification (required `exp`, clock skew, exact issuer, audience, strict UUID `sub`, `is_anonymous` denial, role check). `NimbusJwtDecoder` configured with asymmetric key selector (ES256, EdDSA, RS256) rejecting HS256/none. `RequestLimitFilter` (4096-byte body bound, application/json Content-Type check, 120 req/min global limit). `OwnerParamSecurityFilter` rejects query/header `ownerId` spoofing with 400. `OwnerRateLimitFilter` (60 req/min/owner with eviction). Security headers (CSP, nosniff, no-referrer, frame denial, no-store on `/api/**`). 22 backend tests passing, including real cryptographic local JWKS test suite covering T-AUTH-01–12, T-ISO-02, T-VAL-05, T-LIM-01/02, T-SEC-04. |
| **Phase 4** | Entry Persistence & Idempotency        | **Completed** | Strict domain models (`Category`, `Stage`, `Reason`, `WasteEntry`), custom strict JSON deserializers (integer token check, strict calendar date). Transactional service with `pg_advisory_xact_lock(hashtextextended(owner, 0))`, replay check before count, 10,000 capacity check, atomic insert with `ON CONFLICT DO NOTHING`, race-recovery lookup, and 409 conflict exceptions. Verified via 9 unit/validation tests and 2 real Neon database concurrency tests (20 concurrent threads: 1 insert + 19 replays). |
| **Phase 5** | Context, Reporting, Comparison, Export | **Completed** | Injected trusted clock (`Asia/Kolkata`) for `/api/context` (no DB access). Weekly aggregation `/api/weeks/{monday}` within REPEATABLE_READ snapshot, deterministic top categories tie-breaking (`category ASC`), `inProgress` flag. Comparison `/api/compare` with strict Monday bounds, nulls on empty counts, `HALF_UP` percentage rounding (-33.35 -> -33.4). Export `/api/export` with 8 MiB bound, 2 concurrent operations semaphore, 5 req/min per owner limiter, strict sorting (`entryDate ASC, id ASC`) and full tenant isolation. 18 new tests (52 backend tests total passing). |
| **Phase 6** | Complete Website Experience            | **Completed** | Full React 19 SPA view implementation: `LoginView.tsx` with 1-click Account A & B demo credentials; `DashboardView.tsx` with weekly aggregations, ongoing indicator, and top category bars; `NewEntryView.tsx` with resilient in-flight freeze, root-level pending store preservation, and identical-UUID retry; `CompareView.tsx` with period comparison and percentage change delta; `App.tsx` shell with `/api/export` download notice (FR-X2, T-EXP-05) and CSP compliance. Developed `WasteLensJwsKeySelector`, `JavaEd25519Verifier`, and `WasteLensJwsVerifierFactory` using native Java Ed25519 cryptography to seamlessly verify Neon Auth tokens. Frontend Vitest suite (4 tests) passing; backend JUnit suite (52 tests) passing. End-to-end integration verified on port 58081 against Neon Postgres and Neon Auth. |
| **Phase 7** | Hardening, Audit & Local Verification  | **Completed** | Full suite execution: 52 backend integration & unit tests, 4 frontend component tests. Local candidate verification: packaged JAR tested live with real Neon Auth tokens for context, weekly summaries, period comparisons, atomic idempotent entry logging, replay detection, and JSON export. Two-tenant isolation verified live (Account A vs Account B data separation). `Dockerfile` hardened with multi-stage Node 24 + Maven 3.9 + Temurin 21 non-root runtime. |
| **Phase 8** | Deployment & Verification              | **Completed** | Full-stack deployment to Embarko (`https://veshwik-wastelens.embarko.app`, ID `cmvz801jo002yam1e2iylgg5z`) with Railpack Java 21 runtime. Server-side environment variables configured; Neon Auth trusted domains updated. Automated verification suite (`code/ops/verify-live.py`) executed against live deployment: 44 automated checks passed with 0 failures covering landing page, SPA deep routes, security headers, unauthenticated 401 challenges, live Neon Auth Ed25519 authentication, context, weekly aggregations, period comparisons, atomic entry persistence, idempotent replay, bounded JSON export, and two-tenant isolation. |

---

### Phase 0 Decisions & Inventory Record
* **Java**: Microsoft JDK 25.0.4.1 targeting Java 21 bytecode release.
* **Maven**: Apache Maven 3.9.16.
* **Node / npm**: Node.js v24.19.0, npm 11.17.0.
* **Docker**: Docker CLI 29.8.0 installed; Docker daemon status currently unstarted.
* **Backend Framework**: Spring Boot 3.4.0 (Java 21 LTS baseline).
* **Frontend Framework**: React 19, TypeScript, Vite.
* **Decisions D1–D12 Status**:
  * `D1` (DB Connection): Supavisor session mode (port 5432) default.
  * `D2` (Secondary DB protection): RLS deny-by-default with app-role policy.
  * `D3` (Spring Boot line): Spring Boot 3.4.0 pinned for Java 21 stability.
  * `D4` (Migration tooling): Separate SQL migrations; no runtime DDL in web app.
  * `D5` (Integration test execution): Unit tests in Docker build; Testcontainers/DB tests in local/CI.
  * `D6` (Percentage rounding): `BigDecimal.HALF_UP`, scale 1.
  * `D7` (Repository visibility): Project-level tracking.
  * `D8` (Supabase project): Dedicated synthetic-only project.
  * `D9` (Demo accounts): Distinct provisioned accounts (e.g. Account A & B).
  * `D10` (Regions): Co-located where feasible.
  * `D11` (Keep-alive): None; accept free tier sleep/pause.
  * `D12` (Pending-entry store): Root-level in-memory store in React tree.

---

### Phase 1 Execution & Verification Record
* **Frontend**: React 19 + TypeScript + Vite (`code/frontend`).
  * Configured `outDir: '../backend/src/main/resources/static'`, `emptyOutDir: true`, dev proxy `/api -> http://localhost:8080`.
  * Unit tests: Vitest + Testing Library (2 passing).
  * Build: `tsc -b && vite build` completed in ~600ms.
* **Backend**: Spring Boot 3.4.0 (Java 21 bytecode release).
  * Dependencies: Spring Web, Spring Security Resource Server (ES256), Spring JDBC, PostgreSQL driver, Bean Validation.
  * Spring Security configuration: permits static assets (`/index.html`, `/assets/**`, `/favicon.ico`) and SPA deep routes (`/login`, `/dashboard`, `/entries/**`, `/compare`), requires authentication for `/api/**`, rejects unauthenticated `/api/**` with HTTP 401 JSON error payload.
  * Context endpoint: `GET /api/context` implemented with injected `Clock` (Asia/Kolkata timezone).
  * Tests: 5 JUnit 5 / MockMvc tests passing (context load, public SPA forward, static asset access, unauthenticated 401 challenge, authenticated context response).
* **Packaging & Live Verification**:
  * Packaged JAR (`code/backend/target/wastelens-backend-0.1.0-SNAPSHOT.jar`) contains bundled static SPA assets (`BOOT-INF/classes/static/`).
  * Live runtime test:
    * `GET /` -> HTTP 200 (serves SPA shell)
    * `GET /login` -> HTTP 200 (forwards to SPA shell)
    * `GET /dashboard` -> HTTP 200 (forwards to SPA shell)
    * `GET /entries/new` -> HTTP 200 (forwards to SPA shell)
    * `GET /compare` -> HTTP 200 (forwards to SPA shell)
    * `GET /api/context` -> HTTP 401 (Unauthorized challenge)
* **Infrastructure & CI**:
  * `code/.env.example`: documented placeholders for DB, Supabase Auth ES256, and frontend public variables.
  * `code/compose.yaml`: PostgreSQL 16 Alpine container with healthcheck for local synthetic data.
  * `code/Dockerfile`: 3-stage container build (Node 24 build -> Maven Java 21 build -> Java 21 JRE non-root runtime).
  * `code/.dockerignore` & `.gitignore`: excludes targets, node_modules, build outputs, and `.env*`.
  * `.github/workflows/ci.yml`: CI pipelines for frontend typecheck/test/build, backend unit tests, and backend PostgreSQL integration tests.

---

### Phase 2 Provider Feasibility & Neon Integration Record
* **Provider Selection**: User selected **Full Neon** (Neon Postgres + Neon Auth / Managed Better Auth).
* **Project**: `sparkling-sound-38215057` (`WasteLens`), region `aws-ap-southeast-1` (Singapore), branch `main` (`br-lucky-dawn-aos5xn04`).
* **Neon Auth**:
  * Base URL: `https://ep-rapid-firefly-aousvely.neonauth.c-2.ap-southeast-1.aws.neon.tech/wastelens/auth`
  * JWKS endpoint: `https://ep-rapid-firefly-aousvely.neonauth.c-2.ap-southeast-1.aws.neon.tech/wastelens/auth/.well-known/jwks.json`
  * Active key: `kid="d60ce3fe-815e-404d-9cb4-5af493f0061b"`, `kty="OKP"`, `crv="Ed25519"`, `alg="EdDSA"`
  * Localhost trusted domains: verified pre-approved (`Allow localhost: true`).
* **Demo Accounts Provisioned**:
  * Account A: `account-a@wastelens.demo` (ID: `839eb18f-17a1-4882-91cc-c2deee69d58d`)
  * Account B: `account-b@wastelens.demo` (ID: `c257d69a-0bf6-4eb8-a0fb-998e0024d918`)
* **Database & Schema**:
  * PostgreSQL 16 on Neon, connection verified via TLS (`sslmode=require` / `verify-full`).
  * `V1__init_schema.sql` applied successfully via `code/ops/migrate-database.ps1`.
  * Private schema `wastelens_private`, table `waste_entry`, composite index `waste_entry_owner_date_idx`, permissions revoked from `PUBLIC`, Row Level Security enabled (Decision D2).
  * `check-database.ps1` passed: verified `READ COMMITTED` isolation, `pg_advisory_xact_lock` function, and table presence.
* **Credentials & Secrets**:
  * All credentials stored in git-ignored `.env.neon` and `.env.local`. Zero secrets or passwords logged.

---

### Phase 3 Authentication & Security Foundation Record
* **Decoder & Asymmetric Algorithm Enforcement**:
  * `NimbusJwtDecoder` configured with `JWSVerificationKeySelector` strictly allow-listing asymmetric algorithms: `ES256`, `EdDSA`, `RS256`.
  * Symmetric `HS256` HMAC and `none` unsigned tokens are rejected fail-closed (T-AUTH-10).
  * Unknown `kid` tokens rejected fail-closed (T-AUTH-09).
* **JWT Claim Validation (`WasteLensJwtValidator`)**:
  * Explicit required `exp` claim: rejected if missing (T-AUTH-06).
  * Expiry with 30s clock skew: expired tokens rejected (T-AUTH-02).
  * Not-before with clock skew: future `nbf` tokens rejected (T-AUTH-03).
  * Issuer match: exact string comparison against configured base URL (T-AUTH-05).
  * Audience check: verified to contain expected audience if configured (T-AUTH-07).
  * Subject check: non-empty canonical UUID strictly enforced via `UUID.fromString(sub)` (T-AUTH-08).
  * Anonymous sessions: `is_anonymous=true` tokens rejected (T-AUTH-08).
  * Role check: unprivileged / unexpected roles rejected (T-AUTH-08).
  * Key rotation: dynamic JWKS key rotation verified with test server (T-AUTH-12).
* **Isolation & Parameter Guard (`OwnerParamSecurityFilter`)**:
  * Any client attempting to specify `ownerId`, `owner_id`, or `sub` in query string or headers (`X-Owner-Id`, `ownerId`) is immediately rejected with HTTP 400 Bad Request (`INVALID_INPUT`) (T-ISO-02).
  * `SecurityUtils.getCurrentOwnerId()` derives owner strictly from verified JWT `sub`.
* **Traffic & Payload Limiters (`RequestLimitFilter` & `OwnerRateLimitFilter`)**:
  * Max body size: 4096 bytes (4 KiB) enforced. Exceeded payloads return HTTP 413 Payload Too Large (T-VAL-05, T-LIM-01).
  * Content-Type: POST/PUT/PATCH with body requires `application/json`; non-JSON returns HTTP 415 Unsupported Media Type (T-VAL-05).
  * Global rate limit: 120 req/min budget with window reset. Excess returns HTTP 429 + `Retry-After: 60` (T-LIM-06).
  * Per-owner rate limit: 60 req/min per verified `sub`. 61st request returns HTTP 429 + `Retry-After: 60` (T-LIM-02). Idle entries evicted after 5 minutes to bound memory (T-LIM-07).
* **Security Headers & Caching**:
  * CSP header: `default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self' <authBaseUrl>; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; img-src 'self'` (T-SEC-02).
  * `X-Content-Type-Options: nosniff` (T-SEC-04).
  * `Referrer-Policy: no-referrer` (T-SEC-04).
  * `X-Frame-Options: DENY` (T-SEC-04).
  * `Strict-Transport-Security: max-age=31536000; includeSubDomains` (T-SEC-04).
  * `Cache-Control: no-store` on all `/api/**` responses (T-SEC-04).
* **Test Verification**:
  * 22 backend tests passing via Maven:
    * `AuthValidationTest`: 11 real cryptographic token and local JWKS tests.
    * `SecurityFiltersIntegrationTest`: 6 MockMvc integration tests for filters, headers, and rate limiters.
    * `SecurityConfigTest`: 3 tests for SPA deep-routing and 401 challenge.
    * `WasteLensApplicationTests`: 1 context load test.
    * `ContextControllerTest`: 1 authenticated `/api/context` test.

---

### Phase 4 Entry Persistence & Idempotency Record
* **Domain & DTO Foundations**:
  * Enums: `Category` (`produce`, `grains`, `dairy`, `protein`, `prepared`, `other`), `Stage` (`preparation`, `service`, `storage`), `Reason` (`overproduction`, `spoilage`, `trimming`, `plate_return`, `other`).
  * Custom Deserializers:
    * `StrictIntegerDeserializer`: strictly enforces JSON integer numeric token `JsonToken.VALUE_NUMBER_INT`; rejects floats (`1.5`, `1.0`), scientific notation (`1e2`), strings (`"1"`), booleans, and nulls fail-closed (T-VAL-02).
    * `StrictLocalDateDeserializer`: strictly enforces ISO `uuuu-MM-dd` with `ResolverStyle.STRICT` rejecting invalid calendar dates (`2026-02-30`), unpadded components (`2026-2-3`), and invalid formats (T-VAL-04).
  * Global Jackson Configuration: `spring.jackson.deserialization.fail-on-unknown-properties: true` reject unrecognized JSON properties with HTTP 400 `INVALID_INPUT` (T-VAL-03).
* **Repository & Locking**:
  * `WasteEntryRepository`:
    * `acquireOwnerAdvisoryLock(ownerId)`: calls `SELECT pg_advisory_xact_lock(hashtextextended(?::text, 0))` within transaction boundary, serializing concurrent requests per owner (ADR-005, Decision D1).
    * `findByOwnerAndSubmissionId(ownerId, submissionId)`: indexed lookup for replay verification.
    * `countByOwnerId(ownerId)`: owner entry count for 10,000 capacity enforcement.
    * `insert(entry)`: atomic insert with `ON CONFLICT (owner_id, submission_id) DO NOTHING RETURNING *`.
* **Transaction & Concurrency Lifecycle (`EntryService`)**:
  * Executed under `@Transactional(isolation = Isolation.READ_COMMITTED)`.
  * Step 1: Acquire owner-level advisory transaction lock.
  * Step 2: Check for existing entry by `(ownerId, submissionId)`. If found:
    * If payload identical: return `replayed: true` (HTTP 200 OK) (T-IDEM-01, T-IDEM-04).
    * If payload modified: throw `IdempotencyConflictException` with HTTP 409 `IDEMPOTENCY_CONFLICT` (T-IDEM-02).
  * Step 3: Check entry count for owner. If >= 10,000: throw `CapacityLimitException` with HTTP 409 `CAPACITY_LIMIT_EXCEEDED` (T-LIM-04).
  * Step 4: Atomic insert. If concurrency race occurs, fallback lookup returns entry with `replayed: true`.
* **Automated Test Evidence**:
  * 33 backend tests passing total.
  * `EntryValidationTest`: 4 MockMvc tests covering invalid dates, float rejection, unknown JSON fields, and non-positive grams.
  * `EntryServiceTest`: 5 unit tests for clean insert, identical replay, payload conflict, capacity rejection, and clock bounds.
  * `PostgresEntryIntegrationTest`: 2 tests against live Neon PostgreSQL (worked example persistence and 20 concurrent threads race condition verification).

---

### Phase 5 Context, Reporting, Comparison, Export Record
* **Domain Reporting & Injected Clock**:
  * `/api/context` returns `{ today, timezone, currentWeekStart }` using injected trusted system clock `Asia/Kolkata` (no DB access) (T-TIME-01, T-TIME-02).
* **Weekly Aggregation (`GET /api/weeks/{monday}`)**:
  * Enforces Monday boundary check and pre-2020 rejection fail-closed (T-TIME-04, T-TIME-05).
  * Aggregation executed within `@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)` snapshot.
  * Top 3 categories aggregation with deterministic ascending alphabetical tie-breaker (`category ASC`) (T-AGG-02).
  * Ongoing week dynamic indicator (`inProgress: true` if current week, `false` for past weeks).
* **Period Comparison (`GET /api/compare?weekA=&weekB=`)**:
  * Evaluates difference in grams and percent change: `((B - A) / A) * 100` rounded with `BigDecimal.ROUND_HALF_UP` to 1 decimal place (e.g. -33.35 -> -33.4) (Decision D6, T-CMP-01).
  * Safe division handling: if Week A grams is 0, returns `differenceGrams`, `percentChange: null`, and `comparable: false` (T-CMP-02, T-CMP-03).
* **Bounded JSON Export (`GET /api/export`)**:
  * Strict Schema: `schemaVersion: 1`, `generatedAt`, `entries` ordered strictly by `entry_date ASC, id ASC`.
  * Response Headers: `Content-Type: application/json`, `Content-Disposition: attachment; filename="waste-lens-export.json"`, `Cache-Control: no-store`.
  * Concurrency Limit: Enforced via `Semaphore(2)`. 3rd simultaneous export is rejected with HTTP 429 `RATE_LIMITED` (T-RES-02); permits released in `finally`.
  * Per-Owner Rate Limit: 5 requests per 60 seconds per owner. Exceeded requests return HTTP 429 `RATE_LIMITED` with `Retry-After: 60` (T-LIM-03).
  * Bounded Payload: Serialized byte array checked against 8 MiB (8,388,608 bytes) bound. Excess throws `ServiceUnavailableException` (HTTP 503 `UNAVAILABLE`) ensuring bounded memory and no partial stream output (T-EXP-03).
* **Automated Test Evidence**:
  * **52 backend tests passing** across entire test suite:
    * `ReportServiceTest` (10 tests): T-TIME-01, T-TIME-02, T-CMP-01, T-CMP-02, T-CMP-03, T-CMP-04, T-CMP-05, T-EXP-02, T-EXP-03, T-LIM-03, T-RES-02.
    * `ReportingEndpointsValidationTest` (4 tests): T-TIME-04, T-TIME-05, comparison validation, and export response headers.
    * `PostgresReportingIntegrationTest` (4 tests against live Neon PostgreSQL):
      * `tCmp01_fourEntryWorkedExample`: 2000g/2 vs 1000g/2 -> -1000g, -50.0%.
      * `tAgg02_categoryTiesSortedAlphabetically`: dairy, grains, produce alphabetical tie-breaking.
      * `tAgg03_identicalEntriesWithDifferentSubmissionIdsCounted`: distinct submissions counted independently.
      * `tExp01_exportSortedAndIsolated`: full database export sorted strictly by `entryDate` then `id`, isolated strictly to caller's `ownerId`.

---

### Phase 6 Complete Website Experience Record
* **Frontend Architecture & Views**:
  * Built with React 19 + TypeScript + Vite (`code/frontend`).
  * `LoginView.tsx`: Sign-in form supporting manual email/password entry as well as 1-click test credential population for Demo Account A and Account B.
  * `DashboardView.tsx`: Displays current week total waste (grams & kg), entry count, ongoing week indicator, top 3 categories breakdown with percentage visual bars, empty states, and quick actions.
  * `NewEntryView.tsx`: Form for category, grams, kitchen stage, and reason. Features client-side validation, root-level pending store preservation, in-flight submission freeze, identical UUID retry button, and cross-account mismatch warning (PRD FR-E5).
  * `CompareView.tsx`: Base and target week Monday selector with bounds validation, side-by-side metric display, delta grams, percentage change, and in-progress indicator.
  * `App.tsx`: Custom router (`/login`, `/dashboard`, `/entries/new`, `/compare`), root pending submission store, header navigation, tenant indicator, session sign-out, and export trigger with responsibility guidance notice (FR-X2, T-EXP-05).
  * `index.css`: Mobile-first responsive styling compliant with CSP directives (no inline scripts, no remote stylesheets).
* **Native Java Ed25519 Cryptographic Verification**:
  * Neon Auth signs JWTs using EdDSA (Ed25519) with `kty="OKP"`.
  * Implemented `WasteLensJwsKeySelector` supporting EdDSA/Ed25519 and standard asymmetric keys (ES256, RS256).
  * Implemented `JavaEd25519Verifier` using Java's built-in SunEC `Signature.getInstance("Ed25519")` engine (RFC 8032 / RFC 8410).
  * Implemented `WasteLensJwsVerifierFactory` registering the native Ed25519 verifier alongside standard Nimbus algorithms.
* **Component Test Verification**:
  * 4 Vitest / React Testing Library tests in `App.test.tsx`:
    * Unauthenticated login prompt and demo account buttons.
    * 1-click credential population.
    * Authenticated navigation, tenant email badge, and export action.
    * Navigation to Log Waste view and form field rendering.

---

### Phase 7 Hardening, Audit & Local Verification Record
* **Full Local Test Suite**:
  * **52 JUnit tests passing** in `code/backend`: unit tests, mock MVC tests, security validation tests, local cryptographic JWKS tests, and integration tests against live Neon PostgreSQL.
  * **4 Vitest tests passing** in `code/frontend`.
* **Live Candidate Verification (Port 58081)**:
  * Unified Spring Boot JAR packages the built React SPA into `BOOT-INF/classes/static/`.
  * Public routes: `GET /`, `GET /dashboard`, `GET /entries/new`, `GET /compare` serve SPA shell with HTTP 200.
  * Security headers verified: `Content-Security-Policy`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`.
  * Unauthenticated `/api/**` rejects requests with HTTP 401 Unauthorized JSON error envelope.
  * Live Neon Auth integration: signed in with Demo Account A, retrieved signed EdDSA JWT.
  * Live `/api/context`: returned `{ today: "2026-11-02", timezone: "Asia/Kolkata", currentWeekStart: "2026-11-02" }`.
  * Live `/api/weeks/2026-11-02`: returned weekly aggregation and top categories.
  * Live `/api/compare`: returned period comparison between consecutive weeks.
  * Live `POST /api/entries`: atomic creation of food waste record (450g produce trimming).
  * Live Idempotent Replay: resubmitted identical payload with same `submissionId`, returning identical entry record with `replayed: true` (HTTP 200).
  * Live `/api/export`: returned schemaVersion 1 JSON export with `Content-Disposition: attachment; filename="waste-lens-export.json"`.
  * **Tenant Isolation Verified**: Signed in as Demo Account B (`account-b@wastelens.demo`); verified `/api/weeks/2026-11-02` returns 0 grams / 0 entries and `/api/export` returns 0 entries, confirming zero data leakage across tenants.
* **Multi-Stage Container Hardening**:
  * Hardened `code/Dockerfile` with multi-stage build: Node 24 frontend build -> Maven 3.9 Java 21 packaging with `-DskipTests=true` -> Eclipse Temurin 21 Alpine minimal JRE runtime under non-root user `wastelens:wastelens`.

---

### Phase 8 Deployment & Live Production Verification Record (Embarko)
* **Embarko App Provisioned**:
  * Name: `veshwik-wastelens`
  * Permanent App ID: `cmvz801jo002yam1e2iylgg5z`
  * Production URL: `https://veshwik-wastelens.embarko.app`
  * Status: `live`
* **Release Tooling & Artifact Isolation**:
  * Created `code/package-embarko.py` creating pure source-only archive `code/backend/target/wastelens-embarko.tar.gz` (52 files, 153 KB) with executable `mvnw` (0o755).
  * Excluded local credentials, `.env*`, and build target outputs from packaging.
  * Created `code/backend/railpack.json` selecting Java provider with bounded memory flags (`-Xmx192m -Xss512k -XX:+UseSerialGC -XX:MaxMetaspaceSize=96m`).
  * Created `code/ops/embarko-release.py` orchestrating deployment, environment settings injection, status monitoring, and diagnostic logs extraction.
* **Server-Side Environment Variables**:
  * Stored runtime configuration on Embarko: `DB_JDBC_URL`, `DB_USER`, `DB_PASSWORD`, `NEON_AUTH_BASE_URL`, `NEON_AUTH_JWKS_URL`, `NEON_AUTH_ISSUER`, and `SPRING_PROFILES_ACTIVE=production`.
* **Neon Auth Configuration**:
  * Authorized `https://veshwik-wastelens.embarko.app` as a trusted domain in Neon Auth via Neon CLI.
* **Automated Live Verification Suite (`code/ops/verify-live.py`)**:
  * **44 automated assertions passed with 0 failures** directly against `https://veshwik-wastelens.embarko.app`:
    1. Public root landing page (`GET /`) returns HTTP 200 with HTML shell and React mount point.
    2. SPA deep routes (`/dashboard`, `/entries/new`, `/compare`) return HTTP 200 SPA shell (no 404s).
    3. Strict security headers confirmed (`Content-Security-Policy`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`).
    4. Unauthenticated API calls (`/api/context`, `/api/export`, `/api/entries`) return HTTP 401 challenges.
    5. Live Neon Auth sign-in with Demo Account A issues valid Ed25519 signed JWTs.
    6. Authenticated `GET /api/context` returns calendar context for Asia/Kolkata timezone.
    7. Authenticated `GET /api/weeks/2026-11-02` returns weekly metrics and category breakdowns.
    8. Authenticated `GET /api/compare` returns side-by-side week comparisons.
    9. Atomic waste entry logging (`POST /api/entries`) persists to Neon PostgreSQL with RLS.
    10. Idempotent replay with identical `submissionId` returns `replayed: true` with identical entry ID.
    11. Bounded JSON export (`GET /api/export`) returns schemaVersion 1 attachment stream containing the recorded entry.
    12. Demo Account B tenant isolation verified: zero cross-account data leakage.
* Full verification details, response payloads, and headers recorded in `documents/release-verification.md`.
