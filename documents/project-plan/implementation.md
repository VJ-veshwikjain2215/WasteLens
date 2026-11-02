# WasteLens implementation plan

Prepared: 2 November 2026.

**Delivery goal:** build, test, and publish a working WasteLens website at a verified HTTPS URL. Completion includes the complete user journey working on the deployed site.

**Current state:** planning documents exist; `code` is empty. No application, provider resources, deployment, or passing tests are claimed. This file is the requested roadmap. Creating it does not start implementation or provision services.

## 1. First published release

Publish a responsive website where an individually provisioned user can sign in, add synthetic food-waste entries, view weekly logged weight and leading categories, compare two weeks, export their own JSON, and sign out.

The login page will be accessible on the internet. Application data stays private to each account. Publishing retains the existing scope: synthetic data only, separate account ownership, English UI, Asia/Kolkata business dates, and immutable entries. No public signup, common demo password, real operational data, shared café workspace, edit/delete/import/reset, or public analytics is included.

Use the Render-provided HTTPS address for the first release. A custom domain is optional follow-up work. Render provides an `onrender.com` subdomain for each web service. [Render web services](https://render.com/docs/web-services)

## 2. Source documents and planning resolutions

This document defines execution order. [01-PRD.md](01-PRD.md) defines product behaviour; [03-TECHNICAL-SPEC.md](03-TECHNICAL-SPEC.md) defines detailed contracts; [04-SECURITY-AND-RISK.md](04-SECURITY-AND-RISK.md) defines controls; [05-TEST-PLAN.md](05-TEST-PLAN.md) supplies test IDs. Read the remaining plans for supporting decisions and operations.

These explicit planning resolutions address inconsistencies in the earlier files. The earlier files remain unchanged; reconcile them when implementation decisions are adopted.

| Topic | Execution rule |
|---|---|
| Local work and provider setup | Once implementation is requested, start the local skeleton/test harness. Prove the actual Supabase configuration early, before relying on provider integration. Final Render deployment follows local verification. |
| Release gates | Separate local checks, provider checks, deployment for operator verification, and permission to share the demo. Deployed tests cannot be prerequisites to the first verification deployment. |
| Wrong-account retry | Promote T-UI-04/05 to High release checks alongside backend isolation. |
| Navigation warnings | Use an internal route blocker for SPA navigation and `beforeunload` for supported reload/close cases. `beforeunload` alone does not cover internal navigation. |
| Sign-out with pending save | Offer cancel or explicit discard with the possible-commit warning before intentional sign-out. Preserve pending state during a 401-driven login transition; clear sensitive state after confirmed intentional sign-out. |
| Reset wording | No reset feature, automatic deletion, or destructive reset procedure is included. Remove reset suggestions from release copy. Any later operator deletion requires a separately scoped instruction. |
| JWKS URL | Use the official `https://<project-ref>.supabase.co/auth/v1/.well-known/jwks.json` endpoint, fixed in configuration. Verify that it serves the chosen project's signing key; never obtain the URL from a token. |
| Publication scope | A published private demo does not imply public registration, real-data operation, or production availability. |

Supabase documents the discovery endpoint above. The selected project's ES256 issuance and actual token claims still require verification. [Supabase signing keys](https://supabase.com/docs/guides/auth/signing-keys)

## 3. Architecture and working defaults

```text
Browser: React + TypeScript + Vite
  | sign-in                    | authenticated same-origin API requests
  v                            v
Supabase Auth             Render Docker web service
                         Spring Boot + Java 21
                         serves SPA and /api
                                |
                                | JDBC with verified TLS
                                v
                         Supabase PostgreSQL
                         private application schema
```

| Area | Planned choice |
|---|---|
| Backend | Java 21, Maven wrapper, supported Spring Boot 4.1.x patch, Spring Security resource server, JDBC |
| Frontend | React, TypeScript, Vite, CSP-compatible CSS, Supabase JS client |
| Database | Supabase PostgreSQL; matching major version in local PostgreSQL/Testcontainers |
| Connection | Shared session pooler unless direct connectivity is verified suitable; no transaction pooler for this API |
| Ownership | Verified JWT subject passed explicitly to every service/repository and filtered in every SQL query |
| Secondary DB protection | Proposed D2 default: RLS with an app-role policy, plus revokes/private schema. Record the decision before applying it. This does not isolate owners using the same app role. |
| Migrations | Versioned SQL run separately using an elevated migration identity; no runtime migration credentials |
| Calculations | Integer sums/counts; `BigDecimal` percentage, `HALF_UP`, scale 1 |
| Testing | JUnit, MockMvc, real decoder/local ES256 JWKS, Testcontainers, Vitest/Testing Library, browser end-to-end checks |
| Hosting | One Docker image and one Render Free web service; Supabase supplies Auth and PostgreSQL |
| Sessions/drafts | Root-level memory only; no persistent browser storage for tokens or pending entries |
| Deployment | Manual release of a verified commit initially; CI-gated deployment only after that flow is tested |

Spring's project page currently identifies Boot 4.1.2. Recheck the supported stable patch when scaffolding, pin compatible versions, and record them. This roadmap does not create a dependency lock. [Spring Boot](https://spring.io/projects/spring-boot/)

Supabase documents the shared session pooler as an IPv4 alternative to direct connectivity. Copy the actual host, port, and custom-role username from the project's connection details. [Supabase database connections](https://supabase.com/docs/guides/database/connecting-to-postgres)

## 4. Folder and repository structure

All application source stays in `Wastelens/code`; project documentation stays in `Wastelens/documents`.

```text
Wastelens/
  .gitignore                         repository metadata, if standalone repo is used
  .github/workflows/ci.yml            repository CI configuration
  code/
    frontend/
      src/
        app/                         routing, auth, root pending-entry store
        pages/                       login, dashboard, new entry, compare
        components/                  shared controls, notices, error states
        lib/                         API client, date helpers, export helper
      package.json
      package-lock.json
    backend/
      pom.xml
      mvnw / mvnw.cmd
      src/main/java/.../             security, web, service, repository, domain
      src/main/resources/
        application.yml
        db/migration/                SQL applied outside the application
      src/test/                      unit, decoder, integration tests
    Dockerfile
    .dockerignore
    .env.example                     placeholders only
    compose.yaml                     local PostgreSQL only
  documents/
    project-plan/                    existing plans and this file
    README.md                        verified setup/build/test/deploy guide
    implementation-progress.md       milestones, decisions, remaining work
    release-verification.md          observed release evidence and final URL
```

Additional files above are planned deliverables, not files already created. Inspect parent Git tracking before choosing a standalone repository. Publish only this project, never the entire vault. Proposed repository root is `Wastelens`, with Render root directory `code`. CI uses explicit `code/...` paths.

Record repository visibility, provider accounts, region, and private credential delivery when needed. Local implementation does not depend on public repository visibility. Ignore `.env*` with an explicit exception for `.env.example`; exclude exports, credentials, generated output, and local data volumes from Git and Docker contexts.

## 5. Implementation phases

Every phase ends with observed evidence. Unrun checks remain unrun. Report blockers and continue useful independent work when possible.

### Phase 0 — Baseline and prerequisites

- [ ] Record D1–D12 as selected, proposed, or unresolved; do not silently mark defaults approved.
- [ ] Inventory Java 21, supported Node LTS, npm, Git, Maven wrapper support, and Docker availability.
- [ ] Select compatible framework, package, and container versions using primary documentation.
- [ ] Establish the project/repository boundary and exclude unrelated files.
- [ ] Create the progress record with all implementation phases pending.

**Exit:** prerequisites and missing capabilities are known. An unavailable Docker daemon blocks PostgreSQL integration tests; it does not justify marking them passed.

### Phase 1 — Local skeleton and CI

- [ ] Generate Java backend and React/TypeScript frontend in `code`.
- [ ] Add configuration validation, ignored local env files, and placeholder-only `.env.example`.
- [ ] Add local PostgreSQL configuration for synthetic data.
- [ ] Separate public static routes from authenticated `/api/**` routes.
- [ ] Configure the development proxy without weakening deployed security settings.
- [ ] Build Vite assets into Spring Boot static resources; verify the packaged application serves the SPA.
- [ ] Add CI for frontend type/build/tests, backend unit tests, and real PostgreSQL integration tests.
- [ ] Separate unit and integration test execution: image builds run unit tests; CI additionally runs integration tests.

**Exit:** a clean build starts locally, deep links serve the app shell, and unauthenticated API calls are rejected. No fake login or insecure auth fallback is shipped.

### Phase 2 — Early Supabase feasibility checks

Provider actions require authorization for the relevant account/resources when this phase is reached. Use a dedicated synthetic demo project; credentials are handled privately.

- [ ] Verify account access, project eligibility, plan, quotas, billing settings, and region.
- [ ] Disable signup and anonymous sign-in; provision separate demo accounts A and B.
- [ ] Verify a fresh token's ES256 algorithm, key ID, issuer, audience, expiration, role, UUID subject, and anonymous flag without logging the token.
- [ ] Confirm the configured JWKS endpoint serves the relevant public key.
- [ ] Create migration and restricted application identities; apply schema/indexes/revokes and the recorded D2 choice.
- [ ] Verify custom-role session-pooler access with certificate and hostname verification, including a wrong-host negative test.
- [ ] Verify restricted app privileges and denial of direct REST reads/writes with both unauthenticated and authenticated caller contexts.
- [ ] Record sanitized findings, with no complete JWTs, passwords, or private keys.

**Exit:** the local application can securely reach the intended provider database, and real Auth/DB assumptions have evidence. Render-to-Supabase reachability is checked from the deployed runtime later.

### Phase 3 — Authentication and security foundation

- [ ] Implement the real ES256 decoder and explicit claim validators, including required `exp` and strict UUID subject validation.
- [ ] Use fixed issuer/JWKS configuration; test bounded fetch behaviour, key caching, rotation, and failure handling.
- [ ] Derive ownership only from verified subject; reject client owner selectors in body/query and the defined spoofing headers.
- [ ] Add uniform error bodies/request IDs; log route template, status, and timing without tokens or bodies.
- [ ] Add size limits and strict JSON handling for requests with bodies. Ordinary GET requests do not need a JSON content-type header.
- [ ] Add bounded API limiters, security headers, CSP, and `no-store` for sensitive responses.
- [ ] Keep static assets and platform health probes outside API rate allowances.
- [ ] Distinguish invalid tokens from unavailable dependencies consistently; record the chosen JWKS failure status.

**Tests:** T-AUTH-01–12, relevant T-ISO-02/05, T-SEC and T-LIM cases. Decoder tests use real signed tokens and test JWKS; header parsing also needs HTTP/filter tests.

**Exit:** auth acceptance/rejection, owner extraction, privilege restrictions, and failure behaviour are verified.

### Phase 4 — Entry persistence and idempotency

- [ ] Implement strict parsing for required fields, enums, integer-literal grams, UUIDs, and Kolkata date validation.
- [ ] Implement advisory lock → replay lookup → capacity check → insert in one READ COMMITTED transaction.
- [ ] Compare every entry field for a replay; changed payload returns 409.
- [ ] Return 201 for a new committed entry and 200 for an exact committed replay.
- [ ] Check replay before the 10,000-entry cap; retain the unique constraint as the final duplicate guard.
- [ ] Catch commit failures outside the transaction boundary; report unknown outcome without clearing the submission UUID.
- [ ] Make every repository operation explicitly owner-scoped and parameterized.

**Tests:** T-IDEM-01–08, T-CAP-01/02, T-ISO-01–03/05, T-VAL and date-boundary cases using real PostgreSQL.

**Exit:** concurrent retries persist one entry, other-owner access fails, capacity cannot be bypassed, and uncertain commits never produce false success.

### Phase 5 — Context, reporting, comparison, export

- [ ] Implement `/api/context` from the trusted clock without a DB query.
- [ ] Implement weekly totals/counts/top three categories in one consistent snapshot.
- [ ] Implement comparison with strict Monday bounds, `inProgress`, deterministic ties, and specified percentage rounding.
- [ ] Return null differences/percentages when either week has no entries.
- [ ] Build complete, sorted owner-only JSON before responding; enforce the 8 MiB bound.
- [ ] Enforce export concurrency/rate limits and release permits on every outcome.
- [ ] Measure export size and memory at maximum account capacity.

**Tests:** T-TIME, T-AGG, T-CMP, T-EXP, T-CAP-03, relevant T-LIM/T-RES.

**Exit:** the worked example matches expected results, empty data is distinguishable from failure, and exports are complete and isolated.

### Phase 6 — Complete website experience

- [ ] Build a consistent responsive layout, visible focus states, labelled controls, accessible errors, and readable mobile spacing.
- [ ] Implement `/login`, `/dashboard`, `/entries/new`, `/compare`; redirect `/` to the appropriate route.
- [ ] Use server-reported today/week; keep plain dates independent of device timezone.
- [ ] Keep the session in memory, refresh while the tab is open, and preserve intended route through sign-in.
- [ ] Keep form, frozen submitted payload, UUID, original owner, and status outside routed components.
- [ ] Freeze editing while save outcome is unresolved; retry exact UUID/payload.
- [ ] Check original owner after reauthentication before any retry; wrong-account attempts send no save request.
- [ ] Add explicit discard with possible-commit warning, internal navigation blocking, and supported unload warnings.
- [ ] Prevent late responses from a previous account/session updating the current user's screen.
- [ ] Implement loading/empty/error states, incompleteness notices, and confirmed save states.
- [ ] Implement Bearer `fetch` export, response validation, Blob download/cleanup, and handling/deletion guidance.
- [ ] Verify the built site under its actual CSP; avoid blocked inline scripts/styles.
- [ ] Keep release copy consistent with synthetic-only scope and the no-reset decision.

**Tests:** T-UI-01–09, T-TIME-06, T-EXP-04/05, plus browser end-to-end flows. T-UI-04/05 are High checks in this plan. Cover sign-in, save, dashboard, comparison, export, sign-out, and account switching; at least one integrated browser run uses the real local API/PostgreSQL.

**Exit:** the complete website works against the backend, including recovery paths, without placeholder features or fabricated live statistics.

### Phase 7 — Container and release candidate

- [ ] Create a three-stage Docker build: locked frontend build, Java build with unit tests, non-root Java 21 runtime.
- [ ] Pass only public frontend configuration at build time; supply DB credentials only at runtime.
- [ ] Bind `0.0.0.0` on `${PORT:8080}` and test an overridden port.
- [ ] Copy only runtime requirements into the image; inspect for secrets and excluded files.
- [ ] Verify deep links, missing assets, API errors, headers, and cache behaviour from the packaged app.
- [ ] Use default TCP health probes initially. Verify dependencies separately; a listening port does not establish DB/Auth readiness.
- [ ] Run complete CI, dependency review, and repository/artifact secret checks for the exact candidate commit.
- [ ] Record versions, commit SHA, startup/memory measurements, observed tests, and known limitations.
- [ ] Write and verify build/run/test instructions in `documents/README.md` from a clean checkout.

Render supports repository Dockerfile builds and recommends Docker for JVM apps. Default health checks use TCP probes. [Render Docker](https://render.com/docs/docker), [Render health checks](https://render.com/docs/health-checks)

**Exit:** locally executable High checks pass for one candidate commit. Provider-only checks remain separately tracked until observed.

### Phase 8 — Deploy for operator verification

- [ ] Prepare the intended GitHub repository containing only project source, configuration, and safe documentation; confirm visibility and Render access.
- [ ] Push the verified candidate; confirm CI passes for that exact commit.
- [ ] Create one Render Docker web service, root directory `code`, Dockerfile `Dockerfile`, build context `.` relative to that root; verify the actual settings.
- [ ] Select Free plan and the chosen region; add no Render database or persistent disk.
- [ ] Configure public frontend build values and private backend runtime values in the correct provider settings.
- [ ] Set Supabase Auth Site URL to the observed HTTPS origin; restrict any redirect allowlist to needed addresses. Password sign-in remains the implemented flow.
- [ ] Deploy the verified commit manually; keep auto-deploy disabled until CI-to-deploy behaviour is verified.
- [ ] Observe build/start logs and terminal deployment status without exposing secrets.
- [ ] Verify DB TLS/pooler access and authenticated API responses from the running Render app.

This first deployment allows live verification. Limit accounts to operator-controlled testers and do not distribute credentials or announce the demo before Phase 9 passes.

**Exit:** the expected commit runs at an observed HTTPS URL; actual network/Auth/DB integration works. Provider deployment status alone is insufficient.

### Phase 9 — Live verification and publication

Run against the deployed site; record evidence without tokens or credentials.

- [ ] Open the real HTTPS URL in a fresh browser; verify login, styling, assets, and absence of mixed-content/CSP failures.
- [ ] Verify generic wrong-password errors and absence of signup/shared credentials.
- [ ] As A, enter the four synthetic examples from the test plan through the website.
- [ ] Select the example weeks explicitly: 2000 g/2 versus 1000 g/2, difference −1000 g/−50.0%. Dashboard uses the actual current server week.
- [ ] Download/parse A's JSON; verify fields, order, counts, and isolation.
- [ ] As B, verify A's entries/totals are absent; confirm direct REST denial with both caller contexts.
- [ ] Verify uncertain save recovery, same-ID retries, session expiry, and wrong-account retry blocking.
- [ ] Verify empty weeks/accounts, current-week notices, keyboard operation, and narrow-screen layouts.
- [ ] Open/reload deep links; app shell loads and sign-in is required again.
- [ ] Verify API errors, headers, 413/415/429 behaviour, and safe logs/URLs.
- [ ] Observe idle/cold-start recovery and record behaviour without extrapolating uptime guarantees.
- [ ] Finish all High checks, including manual/provider tests and promoted T-UI-04/05; report other failures and limitations.
- [ ] Record URL, commit SHA, deployment identifier, date, results, and unresolved items in `documents/release-verification.md`.
- [ ] Deliver the tested URL and account-access procedure; send credentials separately through the agreed private channel.

**Exit:** the complete core journey works on the published site and every High check has passed with evidence. A failed/unrun High check leaves publication for sharing incomplete and must be reported.

## 6. Configuration and limits

| Configuration | Destination | Rule |
|---|---|---|
| `VITE_SUPABASE_URL` | Frontend build | Public project URL; change requires rebuild |
| `VITE_SUPABASE_PUBLISHABLE_KEY` | Frontend build | Public key only; never service-role/secret key |
| `DB_JDBC_URL` | Backend runtime | Actual endpoint, verified TLS, no embedded credentials |
| `DB_USER`, `DB_PASSWORD` | Runtime secret settings | Restricted application identity only |
| `SUPABASE_ISSUER` | Backend runtime | Exact selected-project issuer |
| `SUPABASE_JWKS_URI` | Backend runtime | Fixed selected-project discovery URL |
| `SUPABASE_AUDIENCE` | Backend runtime | `authenticated` |
| `PORT` | Backend runtime | Consume platform value |
| Migration credentials | Trusted operator environment | Never supplied to the web application |
| Required public CA material | Runtime trust configuration | Verify provider certificate/hostname; no TLS bypass |

Keep specified limits: body 4 KiB; 10,000 entries/account; API 60/min/owner; unauthenticated API global 120/min; exports 5/min/owner and 2 concurrent; connection pool max 4/acquire timeout 3 s; DB statement timeout 5 s; export max 8 MiB. Verify bounded limiter state and restart-reset behaviour.

## 7. Completion gates

| Gate | Evidence | Enables |
|---|---|---|
| Implementation start | User instruction to build; selected scope recorded | Local implementation |
| Provider setup | Authorization for resource scope; intended account/project, region and plan confirmed | Provider changes within that scope |
| Local candidate | Local High tests, promoted retry tests, clean build and scans pass for one commit | Operator-verification deployment with resource authorization |
| Provider integration | Real token/JWKS, privilege/REST denial, DB TLS/connectivity observed | Confidence in selected provider integration |
| Publish | All High checks including deployed/manual tests pass; full browser journey verified | Share tested website and private account-access procedure |

Local development can continue while resource access is pending. Provider integration must pass before sharing. This separates the original circular requirement to pass deployed tests before the first verification deployment.

## 8. After publication

- Document operational ownership, provider status checks, paused-project recovery, credential rotation, and redeploying a known working commit.
- Keep durable application data in Supabase; never rely on Render disk for entries/exports.
- Use manual or tested CI-gated deployment. Re-run affected checks after changes and smoke checks after releases.
- Rehearse application rollback without promising data rollback. Keep migrations compatible; do not automatically delete/reverse data.
- Accept Free-tier sleep/pause; add no keep-alive pings. Record actual limits and billing settings.
- Treat custom domain purchase, public registration, real-data operation, and further features as follow-up scope.

Render documents idle spin-down after 15 minutes, ephemeral storage, and usage-based limits, and advises against Free instances for production applications. This publication is a hosted demo. Recheck provider terms/account settings on deployment day. [Render Free limitations](https://render.com/docs/free)

## 9. Tracking, estimates, and definition of done

Track phases as pending, in progress, blocked, or verified in `documents/implementation-progress.md`, with supporting commit/check results. Use the existing test-plan results log for observations and `documents/release-verification.md` for final release evidence. Reconcile older planning files when the decisions above are adopted.

The earlier 16-hour target and 28–40-hour range are estimates, not promises. Re-estimate after the baseline/provider spike; preserve required security checks when timing changes.

The project is complete when:

1. A clean checkout builds and runs using verified documentation.
2. The expected commit is deployed at a working HTTPS URL.
3. A provisioned user completes sign-in → entry → dashboard → comparison → export → sign-out on the deployed site.
4. Two-account isolation and safe retries/account switching are demonstrated.
5. Every High release check passes with environment/results recorded.
6. No credentials, private records, or unrelated vault content are published.
7. Setup, limitations, operations, and release evidence are documented.
8. The user receives the tested URL and a private route to account access.

A completed screen, Docker build, or successful deployment status is an intermediate milestone. The target is the verified, usable published website.
