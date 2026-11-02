# 07. Project Plan

## 1. Approach

Security-first and risk-first. The brief's own rule applies: **prioritize security gates over schedule**; if setup, testing, or the 16 h estimate fails, report remaining work and **do not silently drop isolation or tests**. The riskiest items (token verification against the real Supabase project, two-owner isolation, advisory-lock idempotency/cap, pooler/TLS connectivity from Render) are proven *before* UI work. Everything is built and tested locally against real PostgreSQL first; Supabase and Render come last and only with approval.

## 2. Gates

| Gate | Condition | Blocks |
|---|---|---|
| **G0: Approved to implement** | Owner reviews this doc set, explicitly approves implementation, confirms D1–D12 (or defaults). The brief is currently **design only**. Preflight items from the brief are scheduled: confirm ES256/audience/JWKS in the chosen project; test dedicated role and direct-REST denial; verify DB TLS and reachability; confirm UX for separate accounts + fixed Kolkata timezone; assess demand with café staff using synthetic examples | Any implementation |
| **G1: External resources authorized** | Owner authorizes creating the GitHub repo, Supabase project/users, Render service; accounts verified eligible; billing/overage settings checked | Provisioning, deploy |
| **G2: Release candidate** | **All High tests pass** with recorded results; security checklist (`04` §6) done; Medium results reported | Any shared demo |
| **G3: Live verified** | Smoke test observed; free plans confirmed | Reporting a URL as "done" |

## 3. Estimate

### 3.1 Brief: ~16 h (unverified, one intermediate developer, 1–2 days)

| Work package | Hours |
|---|---|
| Scope/setup and provider feasibility | 2 |
| Auth, DB migration, server-side isolation | 3 |
| Entry API + transactional idempotency | 3 |
| Aggregation, comparison, export | 2 |
| React screens | 3 |
| Security/integration/concurrency tests | 2 |
| Docker/deployment/README | 1 |
| **Total** | **16** |

### 3.2 Planner's risk-adjusted view (judgement, not data)

| Driver | Why it exceeds the allocation | Added hours (rough) |
|---|---|---|
| Auth test harness (ES256 test JWKS + 12 negative cases + rotation) | Needs a real decoder under test, not Spring's `jwt()` helper | +2–3 |
| Setup gate and provider provisioning (ES256 migrate/rotate, REST denial, role grants, pooler format, TLS CA) | Manual, unverified, elapsed-time heavy | +2–4 |
| Idempotency + cap concurrency tests (barriers, interleavings, fault injection) | Fiddly to make deterministic | +2–3 |
| Frontend state: pending-submission store, re-auth rules, `beforeunload`, date handling, export Blob flow | More than "3 h of screens" | +3–5 |
| CSP-compatible Vite build, SPA fallback, two-stage Docker, Boot 4/Jackson 3 learning | Integration friction | +2–3 |
| Render connectivity to Supabase (IPv4 pooler vs IPv6 direct) | Unverified | +1–3 |
| **Plausible range** | | **≈ 28–40 h** |

Plan with 16 h as the brief's target, **~32 h as the reporting threshold**. Report overruns; never drop High tests to fit time.

## 4. Work breakdown structure

### M0. Preflight and decisions *(before G0)*
- [ ] M0.1 Owner review; confirm D1–D12
- [ ] M0.2 Choose Boot patch (start.spring.io) and Testcontainers/Postgres versions
- [ ] M0.3 Draft demo account list and credential delivery plan

### M1. Setup gate and skeleton *(G0 + G1 for provider parts)* ~3–5 h
- [ ] M1.1 **Dedicated Supabase project**: disable signup/anonymous; migrate/rotate to ES256; create demo users; record token/JWKS facts (T-OPS-06)
- [ ] M1.2 Create private schema/table/grants via migration role; verify REST denial and app-role privileges (T-ISO-04/05)
- [ ] M1.3 **Connectivity spike**: local run against the real session pooler with `verify-full`; decide D1; note pooler username format
- [ ] M1.4 Generate backend (Boot 4.1.x, Java 21) and frontend (Vite React) skeletons; `.gitignore`, `.dockerignore`, `.env.example` (names only)
- [ ] M1.5 Testcontainers base + local ES256 test-JWKS fixture
- **Exit:** facts recorded; any failed gate reported before further work.

### M2. Auth and security core *(M1)* ~3–4 h  ← **highest risk**
- [ ] M2.1 Tests first: T-AUTH-01..12
- [ ] M2.2 `SecurityConfig`, decoder (ES256 allow-list, fixed JWKS URI), composed validators incl. required `exp`
- [ ] M2.3 Owner extraction (`sub` → UUID), error handler with request IDs, headers/CSP, CORS off
- [ ] M2.4 Pre-auth limiter + size/content-type filter
- **Exit:** all T-AUTH green.

### M3. Persistence, idempotency, capacity *(M2)* ~4–5 h  ← **highest risk**
- [ ] M3.1 Migration SQL; repositories (owner explicit everywhere)
- [ ] M3.2 Tests first: T-ISO-01..03/05, T-IDEM-01..08, T-CAP-01..03
- [ ] M3.3 Create-entry transaction per `03` §7; commit-failure mapping outside the proxy
- **Exit:** all High persistence tests green on real PostgreSQL.

### M4. Reports and export *(M3)* ~2–3 h
- [ ] M4.1 `/api/context`, weekly, compare (single snapshot), `BigDecimal` percent
- [ ] M4.2 Export (bounded in-memory, 8 MiB, semaphore, limits)
- [ ] M4.3 T-TIME, T-AGG, T-CMP, T-EXP, T-LIM
- **Exit:** Medium backend tests green.

### M5. Frontend *(M2; can overlap M4)* ~5–7 h
- [ ] M5.1 In-memory Supabase session, route guards, login screen (no signup link)
- [ ] M5.2 Pending-submission root store; entry form; unknown-outcome retry; `beforeunload`; wrong-account block + discard
- [ ] M5.3 Dashboard (with honest empty/zero/error states), compare, export (Bearer `fetch` → Blob)
- [ ] M5.4 Date handling via server context and integer-day maths; accessibility/mobile; UI tests T-UI-*
- [ ] M5.5 Verify the built bundle under the CSP

### M6. Hardening and QA *(M3–M5)* ~2–3 h
- [ ] M6.1 Run full automated suite; fix; record results
- [ ] M6.2 Security checklist; log inspection; dependency audit; secret scan (T-SEC-*, T-REPO-*)

### M7. Containerize *(M6)* ~1–2 h
- [ ] M7.1 Three-stage Dockerfile; SPA fallback; non-root; local run
- [ ] M7.2 CI workflow running Testcontainers ITs (D5)

### M8. Deploy *(G1 + G2)* ~2–4 h
- [ ] M8.1 Preflight (runbook §2); new GitHub repo; push
- [ ] M8.2 Render Free service; runtime env; public build config
- [ ] M8.3 Smoke test (runbook §8); manual T-ISO-04, T-RES-*, T-OPS-*
- **Exit:** G3.

### M9. Docs and handover ~1–2 h
- [ ] M9.1 README per outline; observed results; limitations
- [ ] M9.2 Report overruns, unresolved items, and unverified claims

## 5. Critical path

M1 (setup gate) → M2 → M3 → M4 → M6 → M7 → M8. M5 starts after M2. **Do not start UI polish before M2/M3 High tests are green.** If M1 fails any gate (ES256, REST denial, connectivity), stop and report.

## 6. Definition of Done

1. All specified screens/endpoints work on a clean build and on an explicitly authorized demo using only synthetic data.
2. **All High tests pass** with environment recorded; Medium results reported; no test marked pass without observation.
3. Isolation demonstrated with two accounts, including direct REST denial.
4. README lets a newcomer set up safely and states every limitation (no backup/restore/retention/uptime/durability claims; no revocation guarantee).
5. No credentials or real data in repo, image, logs, or screenshots; service-role key never used in the app.
6. Free plans/overages verified, not assumed; provider facts re-checked on deploy day.
7. Actual deployment and URL are reported only after observation.

*This documentation set does not itself satisfy implementation or deployment.*

## 7. README outline (to be written in M9; commands only after code exists)

1. Scope and **synthetic-only warning**
2. Architecture and prerequisites (Java 21, Node LTS; exact versions after the code exists)
3. Configuration names and safe example values (placeholders)
4. Account provisioning and Auth settings (ES256, signup/anonymous disabled)
5. Secure DB grants and migration procedure; local setup
6. Timezone (Asia/Kolkata) and limitations
7. Demo seed procedure (via authenticated API; never auto-seed a real account)
8. Test commands and **known failures / unresolved items**
9. Docker and Render setup; cold starts
10. JSON export instructions; safe handling and deletion of exported files; **no backup guarantee**
11. Troubleshooting: Supabase pause, JWKS, DB connection, CSP
12. Provider notes with check date; live URL only if it exists

## 8. Reporting cadence

At each milestone exit: work done, tests run **with observed results**, hours vs plan, new risks, decisions needed. Surface blockers immediately (token algorithm, REST denial, connectivity, quotas, billing).
