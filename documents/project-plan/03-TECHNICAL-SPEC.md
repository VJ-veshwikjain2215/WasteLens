# 03. Technical Specification

> Names and property keys are intent. Confirm against the generated project and current provider docs at implementation time.

## 1. Repository layout (proposed, not created)

```
wastelens/
├─ frontend/                       React + Vite UI, API client, UI tests
│  └─ src/ pages, components, auth, api, state (pending-submission store)
├─ backend/                        Spring Boot
│  └─ src/main/java/.../ config, security, web, service, repository, domain
│  └─ src/main/resources/db/migration/   versioned SQL (applied OUTSIDE the app)
│  └─ src/test/ API, auth, isolation, concurrency tests
├─ Dockerfile                      multi-stage: Node build → Maven build → Java 21 runtime
├─ .dockerignore  .gitignore
├─ .env.example                    NAMES/placeholders only, no values
└─ README.md
```
Never commit `.env*`, credentials, generated build output, or exports.

## 2. Configuration

| Name | Where | Purpose |
|---|---|---|
| `PORT` | runtime | `server.port=${PORT:8080}`; bind `0.0.0.0` |
| `DB_JDBC_URL` | runtime secret store | JDBC URL **without credentials**; TLS with hostname verification; use the provider's currently documented endpoint (do not guess host/port) |
| `DB_USER`, `DB_PASSWORD` | runtime secrets | Dedicated **app** login only. With Supabase's pooler the username has the form `<role>.<project-ref>` (verify in the console) |
| `SUPABASE_ISSUER` | runtime | `https://<project-ref>.supabase.co/auth/v1` (exact string) |
| `SUPABASE_JWKS_URI` | runtime | Fixed, from config, **never from a token**; confirm real path in the project (see §4) |
| `SUPABASE_AUDIENCE` | runtime | `authenticated` |
| `VITE_SUPABASE_URL`, `VITE_SUPABASE_PUBLISHABLE_KEY` | **build time** (public) | Public by design; changing them requires rebuilding the image |
| Limits (optional env) | runtime | per-owner 60/min, global 120/min, export 5/min, export concurrency 2 |

Hikari: `maximum-pool-size=4`, `connection-timeout=3000`, `minimum-idle` low, statement timeout 5 s (role-level `ALTER ROLE … SET statement_timeout='5s'`, plus `lock_timeout` ~2 s; `SET LOCAL` inside transactions is also acceptable). Default isolation READ COMMITTED.

JVM: container-aware heap (`-XX:MaxRAMPercentage≈60`), `-XX:+UseSerialGC`, `-XX:+ExitOnOutOfMemoryError`; measure on the 512 MB instance.

## 3. HTTP API contract

All `/api/*` require a valid Bearer token → otherwise **401**. JSON UTF-8; dates ISO 8601; timestamps UTC RFC 3339; **no owner parameter accepted**. Limits: **413** body > 4 KiB, **415** non-JSON, **429** + `Retry-After` over limit, **503** dependency unavailable, **500** generic.

Error body: `{"error":{"code":"INVALID_INPUT","message":"…","requestId":"…"}}` (field errors may add a `fields` map). Codes include `INVALID_INPUT`, `IDEMPOTENCY_CONFLICT`, `ENTRY_LIMIT_REACHED`, `RATE_LIMITED`, `UNAVAILABLE`, `UNAUTHORIZED`. Never stack traces.

| Endpoint | Behaviour | Responses |
|---|---|---|
| `GET /api/context` | `{"today","timezone":"Asia/Kolkata","currentWeekStart"}` from the server clock; **no DB access** | 200 |
| `POST /api/entries` | Body: `submissionId, entryDate, category, grams, stage, reason`. New → `{"entry":{id,submissionId,entryDate,category,grams,stage,reason,createdAt},"replayed":false}` | **201** new; **200** exact retry (`replayed:true`, same entry); **409** `IDEMPOTENCY_CONFLICT` (same ID, different payload) or `ENTRY_LIMIT_REACHED`; 400 invalid/unknown field/`ownerId` |
| `GET /api/weeks/{monday}` | `{weekStart,weekEnd,timezone,totalGrams,entryCount,inProgress,topCategories:[{category,grams}]}` | 200; 400 non-Monday/future/before-start |
| `GET /api/compare?weekA=&weekB=` | `{weekA:{…},weekB:{…},differenceGrams,percentChange,comparable}`; nulls + `comparable:false` if either count is 0; both weeks from **one snapshot** | 200; 400 invalid/identical/future |
| `GET /api/export` | `{"schemaVersion":1,"exportedAt","timezone","entries":[…]}`; sorted `entryDate` then `id`; headers `Content-Type: application/json`, `Content-Disposition: attachment; filename="waste-lens-export.json"`, `Cache-Control: no-store` | 200; 429; 503 (incl. > 8 MiB) |

No list, edit, delete, import, or reset endpoint exists. (The brief's "page size ≤ 100" is therefore not applicable.)

`inProgress` = server today lies within the week. `weekEnd` is the Sunday (inclusive); SQL uses the exclusive `[monday, monday+7)` form.

## 4. JWT verification (resource server)

**Decoder:** `NimbusJwtDecoder.withJwkSetUri(SUPABASE_JWKS_URI).jwsAlgorithm(ES256).build()` (algorithm allow-list = ES256 only).

**Validators (all required, composed):**
1. Issuer equals `SUPABASE_ISSUER` exactly.
2. Audience **contains** `authenticated`.
3. **`exp` present** and not expired; `nbf` honoured if present; explicit small clock skew (e.g. 30 s). *Add an explicit "claim present" check; do not assume the default timestamp validator rejects a missing `exp` (prove with T-AUTH-06).*
4. `sub` is a non-empty canonical **UUID**.
5. `role` claim equals `authenticated`.
6. Not anonymous (`is_anonymous` absent or false).

**Behaviour:** unsupported alg, unknown `kid`, malformed claims, tampered signature → **401**; JWKS unavailable with no cached key → fail closed (**401/503**, documented per test), **never** a fallback to insecure verification. JWKS cached with refetch on unseen `kid`, rate-limited (Nimbus/Spring defaults; confirm). Handle key rotation: standby→current keys appear in the JWKS; accept valid new key after refresh; never accept unknown keys.

**Setup gate (before any build):** in the chosen project, obtain a fresh access token and confirm: header `alg=ES256` with a `kid`; `iss` exactly the issuer; `aud` contains `authenticated`; `role=authenticated`; `sub` UUID; `is_anonymous` false; JWKS endpoint reachable at the configured URI. Supabase material shows two JWKS path forms (`/auth/v1/.well-known/jwks.json` and `/auth/v1/jwks`): **use whichever the project actually serves**. If the project still signs with the legacy HS256 secret it must be migrated and rotated to an asymmetric key first; the API will not accept HS256.

**Owner:** `ownerId = UUID.fromString(jwt.sub)` after validation, passed explicitly to every service/repository method. No `@RequestParam`/body/header owner.

## 5. Validation (centralized, server-side)

Order: size/content-type → authenticate → parse JSON strictly (reject unknown properties, duplicate keys, trailing tokens) → validate fields → date vs Kolkata today → transaction.

| Field | Rule |
|---|---|
| `submissionId` | canonical UUID string (strict parse) |
| `entryDate` | strict `YYYY-MM-DD` (reject `2026-2-3`, datetimes); real calendar date; `2020-01-01 ≤ date ≤ today(Asia/Kolkata)` |
| `category/stage/reason` | exact enum match (case-sensitive) |
| `grams` | JSON number token that is an **integer literal** 1–100000: reject `"1"`, `1.5`, `1.0`, `1e2`, `true`, `null`, missing, NaN |
| unknown keys / `ownerId` | 400 |

Week-start parameters: strict ISO date; `getDayOfWeek()==MONDAY`; `≤ currentWeekStart`; `≥ 2019-12-30`; `weekA != weekB`.

The clock is an injected `Clock` (`Clock.system(ZoneId.of("Asia/Kolkata"))`), frozen in tests at **2026-11-01 18:32 Asia/Kolkata**.

## 6. Schema (applied outside the app; illustrative)

```sql
CREATE SCHEMA IF NOT EXISTS wastelens_private;
REVOKE ALL ON SCHEMA wastelens_private FROM PUBLIC, anon, authenticated;

CREATE TABLE wastelens_private.waste_entry (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id      uuid NOT NULL,
  submission_id uuid NOT NULL,
  entry_date    date NOT NULL CHECK (entry_date >= DATE '2020-01-01'),
  category      text NOT NULL CHECK (category IN ('produce','grains','dairy','protein','prepared','other')),
  grams         integer NOT NULL CHECK (grams BETWEEN 1 AND 100000),
  stage         text NOT NULL CHECK (stage IN ('preparation','service','storage')),
  reason        text NOT NULL CHECK (reason IN ('overproduction','spoilage','trimming','plate_return','other')),
  created_at    timestamptz NOT NULL DEFAULT now(),
  UNIQUE (owner_id, submission_id)
);
CREATE INDEX waste_entry_owner_date_idx
  ON wastelens_private.waste_entry (owner_id, entry_date, id);
REVOKE ALL ON wastelens_private.waste_entry FROM PUBLIC, anon, authenticated;
```

Grants to the **app login** only: `USAGE` on schema, `SELECT, INSERT` on the table. No `BYPASSRLS`, no DDL, no auth-admin access. `owner_id` is a verified subject, **not** a foreign key into a privileged `auth` schema (consequence: rows can outlive a deleted Auth user). `gen_random_uuid()` is built into current PostgreSQL; confirm on the project. "Entry date ≤ today" is enforced in the service, not as a time-dependent CHECK.

*Optional hardening (D2):* `ALTER TABLE … ENABLE ROW LEVEL SECURITY;` plus one `CREATE POLICY … TO <app_role> USING (true) WITH CHECK (true)`. With RLS on and no policy for `anon`/`authenticated`, those roles are denied even if a grant slips. This is a secondary control; the owner predicate remains primary. Also confirm the schema is **not** listed in the project's exposed API schemas.

## 7. Create-entry transaction (authoritative)

```
POST /api/entries   (owner = verified sub; payload already validated)
 BEGIN  -- READ COMMITTED; SET LOCAL lock_timeout/statement_timeout as configured
  1. SELECT pg_advisory_xact_lock( hashtextextended(owner::text, 0) )   -- per-owner serialization
  2. row = SELECT … FROM waste_entry WHERE owner_id = :owner AND submission_id = :sid
     if row exists:
         if all five entry fields equal → COMMIT, return 200 {entry,row; replayed:true}
         else                          → ROLLBACK, return 409 IDEMPOTENCY_CONFLICT     (no write)
  3. n = SELECT count(*) FROM waste_entry WHERE owner_id = :owner
     if n >= 10000 → ROLLBACK, return 409 ENTRY_LIMIT_REACHED
  4. INSERT INTO waste_entry (owner_id, submission_id, entry_date, category, grams, stage, reason)
     VALUES (…) ON CONFLICT (owner_id, submission_id) DO NOTHING RETURNING …
     if a row returned → COMMIT, return 201 {entry; replayed:false}
     else (unexpected conflict) → re-read under the same tx and apply step-2 comparison
 END  -- never report success before commit completes
```

Rules:
- Lock, count and insert are **one** transaction; **all** writers use the same lock; a replay is checked **before** the cap (so a replay at capacity still succeeds).
- Do **not** run queries after a plain unique-violation exception in the same transaction (aborted). Use `DO NOTHING … RETURNING` or a savepoint.
- Hash collisions in the lock key only serialize more; they cannot bypass checks.
- Commit failures/timeouts are an **unknown outcome** → 503 (client keeps UUID+payload and retries). Commit exceptions are caught **outside** the transactional proxy so they cannot be mistaken for success.
- Pool-acquisition failure before any statement → 503 (safe to retry the same UUID).
- Per-owner locking implies ≥ 10,000 `count(*)` scans are cheap (indexed prefix); measure under T-CAP-01.

## 8. Reports and export

**Snapshot rule:** weekly totals/top categories and compare run in one `READ ONLY` `REPEATABLE READ` transaction (or a single SQL statement), so totals and rankings cannot disagree.

```sql
-- totals for a week
SELECT COALESCE(SUM(grams),0)::bigint AS total, COUNT(*) AS n
FROM wastelens_private.waste_entry
WHERE owner_id = :owner AND entry_date >= :monday AND entry_date < :monday + 7;

-- top categories
SELECT category, SUM(grams)::bigint AS g
FROM wastelens_private.waste_entry
WHERE owner_id = :owner AND entry_date >= :monday AND entry_date < :monday + 7
GROUP BY category ORDER BY g DESC, category ASC LIMIT 3;
```

Compare: compute A and B in the same snapshot; `difference = B−A`; `percent = (difference / A) × 100` via `BigDecimal` `HALF_UP` scale 1, only if both counts > 0.

Export: `READ ONLY` `REPEATABLE READ`; `SELECT … WHERE owner_id=:owner ORDER BY entry_date, id` (≤ 10,000 rows); serialize to a byte array; reject > 8 MiB with 503; **only then** send headers/body. At most 2 concurrent exports (semaphore with try-acquire → 429), 5/min per owner.

## 9. Frontend contracts

- **Supabase client:** `createClient(VITE_SUPABASE_URL, VITE_SUPABASE_PUBLISHABLE_KEY, { auth: { persistSession: false, detectSessionInUrl: false } })` with in-memory session. Token supplied to the API client via a getter. No service-role/secret key, ever.
- **Routes:** `/login`, `/dashboard`, `/entries/new`, `/compare`; guarded; redirect to `/login` when no session; return to the intended route after sign-in.
- **API client:** attaches Bearer; maps 401 → sign-in path (preserving pending state); 429/503 → retry UI; never converts errors into empty data.
- **Pending submission store (root-level, in memory):** `{ submissionId, payload, originalSub, status: unsent|inFlight|unknown }`. UUID created once per intentional submission; reused for every retry; cleared only after a confirmed 200/201. Mismatch of current `sub` vs `originalSub` → block, show the exact message in FR-E5, offer **Discard** with the "earlier attempt may have committed" warning. `beforeunload` warning while `unknown`.
- **Dates:** server `/api/context` supplies today and `currentWeekStart`. Never use device time or `new Date("YYYY-MM-DD")`. Do date maths on integer UTC days or strings (e.g. `Date.UTC(y,m-1,d)` then `toISOString().slice(0,10)`), and only produce Mondays from that.
- **Export:** `fetch('/api/export', {headers:{Authorization}})`; check `ok`, parse/validate (`schemaVersion===1`, `entries` is an array) → create a Blob → anchor with `download="waste-lens-export.json"` → revoke the object URL. On any failure show an error and **save nothing**. Show the device-sensitivity/deletion note.
- **Rendering:** React text rendering only; no `dangerouslySetInnerHTML`. Avoid inline `style` attributes and inline scripts (CSP). Status shown with text/icons, not colour alone.
- **Accessibility:** labelled controls, visible errors tied via `aria-describedby`, keyboard-operable selects/buttons, mobile-width layout.

## 10. Security headers and limits

- CSP: `default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self' https://<project-ref>.supabase.co; object-src 'none'; base-uri 'none'; frame-ancestors 'none'` (plus `img-src 'self'` if images appear). Test the built SPA under it (module preload, CSS, fonts).
- `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, framing denied, HSTS on HTTPS, `Cache-Control: no-store` on API, auth-sensitive and export responses.
- **Limiters:** pre-auth global (120/min, size 4 KiB, content type) before the security chain; post-auth per-owner (60/min) and export limits. Single-instance, in-memory, reset on restart, **not DDoS protection**; never trust client forwarded IPs. Bound limiter state (evict idle keys).
- SPA fallback serves `/login`, `/dashboard`, `/entries/new`, `/compare` and must not intercept `/api/*` or static files.

## 11. Dependency and build notes

Backend: Boot 4.1.x starters (webmvc, security + oauth2-resource-server, jdbc), PostgreSQL driver, Jackson (Boot 4 ships Jackson 3), test deps incl. Testcontainers PostgreSQL and a small JWKS test fixture (Nimbus JOSE is already on the classpath via the resource server). Frontend: React, Vite, `@supabase/supabase-js`, Vitest + Testing Library; commit the lockfile, install with a locked/clean install, audit dependencies, and avoid adopting package versions published only days ago.
