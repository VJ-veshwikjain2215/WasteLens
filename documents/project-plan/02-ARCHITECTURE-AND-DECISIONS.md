# 02. Architecture and Decisions

## 1. Technology stack

| Layer | Choice | Notes |
|---|---|---|
| Frontend | **React + Vite** (TypeScript recommended) compiled to static assets | Served by Spring Boot; no separate frontend host |
| Backend | **Java 21**, **Spring Boot 4.1.x** (latest patch at build time) | Spring Security resource server; JDBC (no JPA required) |
| Auth | **Supabase Auth** (browser signs in directly with project URL + publishable key) | Self-signup and anonymous auth disabled |
| Database | **Supabase-hosted PostgreSQL**, private schema `wastelens_private` | Accessed **only** by the API over verified TLS |
| Hosting | **Render Free** web service (Docker), one image serving UI + `/api` | Same HTTPS origin; CORS disabled |
| Build | Maven wrapper (backend), npm lockfile (frontend), multi-stage Docker | |
| Tests | JUnit 5, MockMvc, Testcontainers PostgreSQL, a local ES256 test JWKS; Vitest for UI | Manual tests for provider/browser cases |
| **Excluded** | Python; a second DB; Render disk state; Supabase service-role/secret key anywhere in the app | |

Boot 4 specifics (verify against the generated project): `spring-boot-starter-webmvc`, `spring-boot-starter-security-oauth2-resource-server`, `spring-boot-starter-jdbc`, test starters per the Boot 4 migration guide, PostgreSQL driver.

## 2. System context

```
 Browser (React SPA, same origin as API)
   │ 1. sign in ──────────────────────────────► Supabase Auth  (https://<ref>.supabase.co/auth/v1)
   │ 2. access token (ES256 JWT, in memory)  ◄──────────────────┘
   │ 3. Authorization: Bearer <token>  ──HTTPS──┐
   ▼                                              ▼
 ┌──────────────────── Render Free web service (Docker) ───────────────────────┐
 │ Limit filters (size, global rate)  → Spring Security resource server        │
 │   (ES256 JWKS verify + explicit claim validators; fail closed)              │
 │ → per-owner limiter → Controllers → Services (owner = verified sub)         │
 │ → JDBC repositories (bind params; owner predicate in EVERY query)           │
 │ + serves static SPA and explicit SPA fallback for 4 routes                  │
 └─────────────────────────────────────────────┬───────────────────────────────┘
                                               │ JDBC, TLS verify-full, pool max 4
                                               ▼
                     Supabase PostgreSQL  (private schema, not in exposed API schemas;
                     dedicated least-privilege login; PUBLIC/anon/authenticated revoked)
```

The API verifies tokens **locally** using the project's published JWKS. It does not call Supabase to validate each request.

## 3. Components

| Component | Responsibility | Must not |
|---|---|---|
| `SecurityConfig` | Resource-server chain: `/api/**` requires auth; explicit `JwtDecoder` (ES256 allow-list, fixed JWKS URI) + validator chain; headers/CSP; CORS off | Decode payloads without verifying; read issuer/JWKS from a token; accept HS256/none |
| `RequestLimitFilter` (pre-auth) | Global limiter (120/min), body ≤ 4 KiB, content-type check | Trust `X-Forwarded-For` |
| `OwnerRateLimitFilter` (post-auth) | 60/min per verified `sub`; export limits | Hold unbounded per-key state |
| `ContextController` | `GET /api/context` (no DB) | Query DB |
| `EntryController/Service` | Validate, lock, replay-check, cap, insert | Accept owner from client; return success before commit |
| `ReportService` | Weekly totals/top categories/comparison in one snapshot | Return zeros on failure |
| `ExportService` | Bounded in-memory JSON build, then respond | Stream a partial "success" file |
| `ErrorHandler` | Uniform error body with `requestId`; generic 500 | Leak stack/SQL/secrets |
| SPA `auth` module | In-memory Supabase session; token provider for API client | Persist tokens to storage |
| SPA `pending-submission` store | Root-level in-memory store (form, UUID, payload, original `sub`) | Live in a routed component |
| SPA `api` client | Attach Bearer, normalize errors, map statuses | Log tokens |

## 4. Key flows

1. **Sign-in:** browser → Supabase Auth → access token held in memory only.
2. **Create entry:** `POST /api/entries` → limits → JWT verify → validate (shape, enums, date vs Kolkata today) → **tx (READ COMMITTED)**: owner advisory lock → replay lookup → cap check → `INSERT … ON CONFLICT DO NOTHING RETURNING` → commit → 201/200.
3. **Unknown outcome → 401 → re-login → retry:** pending state in root store; retry only if `sub` matches the original.
4. **Weekly/compare:** one read-only REPEATABLE READ snapshot (or one SQL statement).
5. **Export:** snapshot read → build in memory (≤ 8 MiB) → respond with headers → browser `fetch` + Blob download.

## 5. Architecture Decision Records

**ADR-001: One image, one origin (Spring serves SPA + API).** *Why:* no CORS, one Render service, simpler CSP (`connect-src 'self'` + Supabase). *Trade-off:* the frontend rebuild is needed to change the (public) Supabase URL/key; SPA fallback must not shadow `/api/*` or static files.

**ADR-002: Verify JWTs locally against a fixed JWKS with an ES256 allow-list and explicit validators.** *Why:* public-key verification needs no shared secret; algorithm confusion (HS256/none) is blocked by allow-list. *Consequence:* the setup gate must prove the project actually issues ES256. Unknown `kid`, unsupported alg, malformed claims, or unavailable *uncached* JWKS → 401/503, never a fallback. Immediate user revocation is **not** guaranteed for unexpired tokens.

**ADR-003: Owner predicate in SQL is the isolation boundary; RLS is not relied on.** *Why:* the API holds one DB login (not per-owner). *Consequence:* every repository method takes an owner argument and is tested with two owners. DB-admin compromise or a missed predicate remains a risk. *Optional (D2):* enable RLS with a single policy for the app role to deny PostgREST roles even if grants slip.

**ADR-004: DB connection via session-mode pooler (or direct) with TLS verification; never transaction-mode pooling for the API.** *Why:* Supabase's direct endpoint is IPv6-only without a paid add-on, while the shared pooler offers IPv4; session mode keeps session semantics (pgjdbc's `REPEATABLE READ` switch is a session-level setting; Hikari persistent connections). Transaction mode is for transient clients and needs prepared statements off. *Status:* proposed (D1); Render Free's IPv6 egress is unverified.

**ADR-005: Per-owner serialization with a transaction-scoped advisory lock.** *Why:* the 10,000-row cap needs an atomic count+insert per owner, and retries must serialize with new writes. `pg_advisory_xact_lock` is released automatically at transaction end (safe with any pooling mode). Hash collisions only over-serialize; they never bypass the check. The `UNIQUE (owner_id, submission_id)` constraint is the final safety net.

**ADR-006: Create transaction runs at READ COMMITTED.** *Why:* after acquiring the lock, the replay lookup must see the previous writer's *committed* row (each statement gets a fresh snapshot). Handle the unique-conflict path with `ON CONFLICT DO NOTHING RETURNING` + re-read, never by continuing in an aborted transaction.

**ADR-007: Reports and export use one consistent snapshot.** *Why:* totals and rankings must agree under concurrent inserts. Use a `READ ONLY`, `REPEATABLE READ` transaction, or compute each report in a single SQL statement.

**ADR-008: Export is built fully in memory, bounded at 8 MiB, then sent.** *Why:* guarantees no "successful" partial file. 10,000 fixed-shape rows is on the order of a few MB (to be measured). Exceeding the bound → 503 with a clear export error.

**ADR-009: Access token in memory only; no `localStorage`/cookies for the session.** *Why:* reduces persistence of bearer tokens. *Consequence:* reload signs the user out; XSS could still steal a live token (CSP is defence-in-depth).

**ADR-010: Pending submission lives in a root-level store with the original `sub`.** *Why:* the login route replaces page components; a component-local state would be lost. Re-auth as a different account must never submit.

**ADR-011: Schema changes are applied outside the web app with elevated credentials.** *Why:* the runtime login must have only `USAGE` on the schema and `SELECT, INSERT` on the table. Migration role/credentials never reach Render.

**ADR-012: No keep-alive or uptime pings; accept sleep and pause.** *Why:* pinging burns Render hours, and would violate the "honest about limits" principle; Supabase pauses idle free projects and restoration is a platform feature, not a backup.

**ADR-013: Percent via `BigDecimal` `HALF_UP`, scale 1 (D6).** *Why:* avoids binary floating-point surprises.

**ADR-014: Real PostgreSQL in tests, a local ES256 JWKS for auth tests, never H2 and never `jwt()` post-processors for token-validation tests.** *Why:* concurrency semantics and decoder behaviour must be exercised for real.

## 6. Cross-cutting rules

- Fail closed and fail visibly; never zeros or success for failure.
- No secret, token, request body, or DB credential in logs, URLs, errors, analytics, image layers, or git.
- Logs: request ID, route, status, timing only.
- All times: stored UTC `created_at`; **entry dates are plain dates** and are never converted through browser timestamps.
