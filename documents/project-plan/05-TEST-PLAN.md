# 05. Test Plan

> **These are specifications, not results.** Every status is `NOT RUN`. Record observed outcomes only. **Release rule: all High tests pass before any shared demo.** A failed or unrun High test means **no security pass**. This document itself has run no tests.

## 1. Strategy and tooling

| Level | Tooling | Runs where |
|---|---|---|
| Unit | JUnit 5 (date/week maths, validation, rounding, limiter logic); Vitest (frontend logic) | Local, CI, **Docker image build** |
| Auth/decoder | Real `JwtDecoder` + validators against a **local test JWKS** (ES256 keys generated in test, served by an embedded HTTP server) | Local, CI |
| Controller/slice | MockMvc/Spring Security Test for routes, headers, status mapping | Local, CI |
| **Integration (real PostgreSQL)** | **Testcontainers PostgreSQL** (match Supabase's major version), two owners A/B | **Local and CI only** (needs Docker daemon; not inside the image build) |
| UI | Vitest + Testing Library; optional Playwright against a local stack | Local/CI |
| Manual | Provider and deployed-browser cases (Supabase Auth, REST denial, Render behaviour) | Deployed demo, synthetic data |

**Do not** use Spring's `jwt()` request post-processor for token-validation tests (it bypasses the decoder). H2 is never used.
**Clock:** inject a fixed `Clock`; freeze at **2026-11-01 18:32 Asia/Kolkata** unless a test needs a boundary.
**Concurrency:** separate connections per thread; `CyclicBarrier`/`CountDownLatch`; held-open transactions for deterministic interleavings; repeat races 50–200 times.
**Fault injection:** a decorating `DataSource`/transaction manager that can fail before insert, **fail on commit after executing**, and exhaust the pool.

## 2. High priority: identity, isolation, idempotency, capacity, security

| ID | Req | Level | Scenario | Expected | Status |
|---|---|---|---|---|---|
| T-AUTH-01 | NFR-S1 | Decoder | Missing / malformed / non-Bearer `Authorization` | 401; no SQL data | NOT RUN |
| T-AUTH-02 | NFR-S1 | Decoder | Expired token | 401 | NOT RUN |
| T-AUTH-03 | NFR-S1 | Decoder | Not-yet-valid (`nbf` in future) | 401 | NOT RUN |
| T-AUTH-04 | NFR-S1 | Decoder | Tampered signature / payload | 401 | NOT RUN |
| T-AUTH-05 | NFR-S1 | Decoder | Wrong issuer (incl. near-miss string) | 401 | NOT RUN |
| T-AUTH-06 | NFR-S1 | Decoder | **Missing `exp`** | 401 (proves explicit required-claim validator) | NOT RUN |
| T-AUTH-07 | NFR-S1 | Decoder | Wrong audience; missing audience; audience list containing `authenticated` | wrong/missing → 401; list containing it → accepted | NOT RUN |
| T-AUTH-08 | TH-06 | Decoder | `sub` not a UUID/empty; `is_anonymous=true`; `role` ≠ `authenticated` | 401 each | NOT RUN |
| T-AUTH-09 | TH-04/12 | Decoder | Unknown `kid`; JWKS unavailable with **uncached** key; flood of random `kid`s | 401/503 **fail closed**; no fallback; refetch rate-limited | NOT RUN |
| T-AUTH-10 | TH-04 | Decoder | `alg=HS256` (signed with a guessed secret), `alg=none` | 401 | NOT RUN |
| T-AUTH-11 | NFR-S1 | Decoder | Valid ES256 token with correct claims | Accepted | NOT RUN |
| T-AUTH-12 | NFR-S1 | Decoder | Key rotation: new key appears in JWKS after refresh | Valid new key accepted; unknown keys never accepted | NOT RUN |
| T-ISO-01 | NFR-S2 | Integration | A inserts rows; B calls dashboard, compare, export | No A rows or totals; B sees only B | NOT RUN |
| T-ISO-02 | TH-01 | Controller | B sends body `ownerId`=A; query owner selector; owner header | **400**, no insert (contract requires 400) | NOT RUN |
| T-ISO-03 | TH-02 | Integration | Matrix: every repository/service method with two owners | Never returns the other owner's data | NOT RUN |
| T-ISO-04 | NFR-S3 | **Manual** | With `anon` and `authenticated` credentials, attempt REST read **and** write on the private schema/table | Denied; schema not exposed | NOT RUN |
| T-ISO-05 | TH-18 | Integration/manual | As the app login: UPDATE, DELETE, DDL, access auth-admin objects | All fail; only `SELECT, INSERT` + schema `USAGE` work | NOT RUN |
| T-IDEM-01 | FR-E2 | Integration | 20 concurrent identical POSTs (same UUID/payload) for A | Exactly **1 row**; one **201**, rest **200** `replayed:true`; same entry id | NOT RUN |
| T-IDEM-02 | FR-E2 | Integration | Same UUID, changed `grams` | **409** `IDEMPOTENCY_CONFLICT`; no write | NOT RUN |
| T-IDEM-03 | NFR-S2 | Integration | Same UUID submitted by B | Independent row owned by B | NOT RUN |
| T-IDEM-04 | FR-E4 | Integration (fault) | Timeout **after commit**, then retry | **200**, same entry, no second row | NOT RUN |
| T-IDEM-05 | FR-E4 | Integration (fault) | Disconnect **before commit**, then retry | At most one row | NOT RUN |
| T-IDEM-06 | FR-E2 | Integration | Failed transaction (e.g. CHECK violation path) | Inserts nothing | NOT RUN |
| T-IDEM-07 | FR-E4 | Integration (fault) | Commit throws after insert | 503 unknown-outcome; **never** a 2xx; retry yields ≤1 row | NOT RUN |
| T-IDEM-08 | ADR-006 | Integration (interleaving) | Tx1 holds lock/uncommitted; Tx2 same key waits; Tx1 commits / rolls back | Tx2 sees Tx1's committed row (→ replay) / proceeds to insert on rollback | NOT RUN |
| T-CAP-01 | NFR-L2 | Integration | Owner at 9,999 rows; N concurrent inserts with different UUIDs | Exactly **one** commits; rest **409 `ENTRY_LIMIT_REACHED`** | NOT RUN |
| T-CAP-02 | NFR-L2 | Integration | Replay of an existing entry at 10,000 | **Succeeds** (200) | NOT RUN |
| T-CAP-03 | FR-X1 | Integration | Parallel exports and writes | Valid snapshot, no partial JSON, bounded memory | NOT RUN |
| T-SEC-01 | TH-07 | Controller/UI | `<script>` and HTML in category/reason/other fields | 400; nothing rendered/executed | NOT RUN |
| T-SEC-02 | NFR-S4 | Controller + manual | CSP header present; built SPA works under it | No violations in normal flows | NOT RUN |
| T-SEC-03 | TH-08 | Integration | SQL-like strings in every input | 400 / literal handling; table intact | NOT RUN |
| T-SEC-04 | NFR-S4 | Controller + log review | Headers (`nosniff`, `no-referrer`, frame denial, `no-store`); inspect logs, URLs, errors | No tokens/bodies/secrets anywhere | NOT RUN |
| T-SEC-05 | TH-13 | Repo/artifact scan | Image, bundle, source, history for service-role/secret key or DB creds | None present | NOT RUN |

## 3. Medium priority: correctness and resilience

| ID | Req | Level | Scenario | Expected | Status |
|---|---|---|---|---|---|
| T-VAL-01 | FR-E1 | Unit/controller | `grams` = 1 and 100000 | **201** | NOT RUN |
| T-VAL-02 | FR-E1 | Unit/controller | `grams` = 0, −1, 100001, 1.5, 1.0, `"1"`, null, true, missing, 1e2 | 400; zero rows | NOT RUN |
| T-VAL-03 | FR-E1 | Controller | Invalid enum (wrong case), unknown key, duplicate key, invalid UUID | 400 | NOT RUN |
| T-VAL-04 | FR-E1 | Unit | Invalid calendar dates (`2026-02-30`, `2026-2-3`), pre-2020, future Kolkata date | 400; zero rows | NOT RUN |
| T-VAL-05 | NFR-L1 | Controller | Body > 4 KiB; non-JSON content type | 413; 415 | NOT RUN |
| T-TIME-01 | FR-E1 | Unit | UTC `18:29:59Z` vs `18:30:00Z` on 1 Nov 2026 | Kolkata date Nov 1 vs Nov 2; Nov 2 rejected before boundary, allowed after; independent of browser tz | NOT RUN |
| T-TIME-02 | NFR-L | Unit | Sun Nov 1 vs Mon Nov 2 | Different weeks | NOT RUN |
| T-TIME-03 | NFR-L | Unit | Year-end, leap day weeks; exact `[Mon, next Mon)` bounds | Correct membership | NOT RUN |
| T-TIME-04 | FR-C1 | Controller | Wednesday start; future start; identical A/B; start before 2019-12-30 | 400 | NOT RUN |
| T-TIME-05 | FR-C1 | Unit | Earliest allowed week = **2019-12-30** | Accepted | NOT RUN |
| T-TIME-06 | R-15 | UI (browser tz matrix) | Run date UI with device tz set to UTC−12, UTC+14 | No off-by-one; server "today" used | NOT RUN |
| T-AGG-01 | FR-D2 | Integration | Large sums (bigint), many rows | Correct totals | NOT RUN |
| T-AGG-02 | FR-D2 | Integration | Category ties | Lexicographic by code ascending | NOT RUN |
| T-AGG-03 | NFR | Integration | Identical entries with different UUIDs | Counted twice | NOT RUN |
| T-CMP-01 | FR-C2 | Integration | Four-entry worked example | A 2000/2, B 1000/2, −1000 g, **−50.0%**, B top produce→dairy | NOT RUN |
| T-CMP-02 | FR-C3 | Integration | Empty A; empty B; both empty | Counts 0 as appropriate; diff/percent null; "Not comparable" shown | NOT RUN |
| T-CMP-03 | FR-C2 | Unit | Both non-empty, equal totals | `0 g / 0.0%` | NOT RUN |
| T-CMP-04 | FR-C4 | Controller/UI | Current week selected | `inProgress:true`, labelled in progress | NOT RUN |
| T-CMP-05 | ADR-013 | Unit | Rounding: e.g. −33.35 → one decimal, `HALF_UP`, negative values | Matches defined mode | NOT RUN |
| T-CMP-06 | ADR-007 | Integration | Concurrent inserts during compare/weekly | Totals and rankings mutually consistent | NOT RUN |
| T-RES-01 | NFR-R1 | Integration (fault) | DB down, statement timeout, no connection | 503; UI error + retry; **never zeros/false save** | NOT RUN |
| T-RES-02 | R-09 | Manual | Supabase project paused | Visible error; no false data | NOT RUN |
| T-RES-03 | FR-A1 | Manual/UI | Supabase Auth unavailable | "Sign-in unavailable; try later." | NOT RUN |
| T-RES-04 | R-10 | Manual | Render cold start | Retry UX works; state retained in open tab | NOT RUN |
| T-RES-05 | NFR-R1 | Controller/manual | Token expiry mid-session; shared-cache check | 401 → sign-in path; no cross-user caching; no fabricated success | NOT RUN |
| T-EXP-01 | FR-X1 | Integration | Export A | Parseable JSON; `schemaVersion:1`; sorted by `entryDate`,`id`; only A; no credentials | NOT RUN |
| T-EXP-02 | FR-X1 | Integration | Empty account | `entries:[]` | NOT RUN |
| T-EXP-03 | ADR-008 | Integration (fault) | Forced serialization/size error; >8 MiB bound | 503 with clear export error; **no partial success** | NOT RUN |
| T-EXP-04 | FR-X1 | UI | Export request fails / invalid body | Error shown; **no file saved** | NOT RUN |
| T-EXP-05 | FR-X2 | UI | Device-sensitivity/deletion note | Visible | NOT RUN |
| T-LIM-01 | NFR-L1 | Controller | 4 KiB boundary (4096 ok, 4097 → 413) | As stated | NOT RUN |
| T-LIM-02 | NFR-L3 | Controller | 61st request/min for one owner | 429 + `Retry-After`; other owner unaffected | NOT RUN |
| T-LIM-03 | NFR-L3 | Controller | 6th export in a minute | 429 | NOT RUN |
| T-LIM-04 | NFR-L3 | Integration | 3 concurrent exports | Max 2 run; third 429 | NOT RUN |
| T-LIM-05 | NFR-L3 | Integration | Pool exhaustion (max 4, acquire 3 s) | 503, safe | NOT RUN |
| T-LIM-06 | NFR-L3 | Controller | Unauthenticated/invalid-token flood > 120/min | Global limiter responds 429; no JWKS storm | NOT RUN |
| T-LIM-07 | NFR-L3 | Load | Bounded memory under load; limiter reset on restart documented | Within instance | NOT RUN |

## 4. Low priority and UI

| ID | Scenario | Expected | Status |
|---|---|---|---|
| T-UI-01 | Unknown outcome: retry | Same UUID + exact payload; at most one row | NOT RUN |
| T-UI-02 | Navigate/close with unknown outcome pending | `beforeunload` warning | NOT RUN |
| T-UI-03 | Session expiry with unsent form | Form preserved in open tab; sign-in path | NOT RUN |
| T-UI-04 | Start save as A → unknown → **401** → re-auth as **A** → retry | Same UUID/payload; ≤1 A row | NOT RUN |
| T-UI-05 | Same, re-auth as **B** | **No request sent**; no B row; original-account warning; Discard warns earlier write may have committed | NOT RUN |
| T-UI-06 | Keyboard-only use; labels; error association; screen-reader smoke | Pass | NOT RUN |
| T-UI-07 | Narrow mobile width; status not colour-only | Pass | NOT RUN |
| T-UI-08 | Synthetic notice on dashboard; no signup link; generic "Sign-in failed" | Present/absent as specified | NOT RUN |
| T-UI-09 | "No correction in demo" notice; no compensating-entry suggestion | Pass | NOT RUN |
| T-OPS-01 | Clean build and start locally | Starts; routes load | NOT RUN |
| T-OPS-02 | Docker image: non-root, listens on `$PORT`, SPA fallback only for the four routes, `/api/*` and assets untouched | Pass | NOT RUN |
| T-OPS-03 | DB TLS `verify-full`; wrong-host negative test | Success; wrong host fails | NOT RUN |
| T-OPS-04 | Reconnect after Render sleep and after Supabase pooler restart | Recovers | NOT RUN |
| T-OPS-05 | Render plan = Free; Supabase plan = Free; billing/overage settings reviewed | Recorded | NOT RUN |
| T-OPS-06 | **Setup gate:** ES256, `kid`, issuer, audience, role, `sub`, `is_anonymous`, JWKS URL in the real project | Recorded | NOT RUN |
| T-OPS-07 | Measure RSS/startup on 512 MB | Within instance | NOT RUN |
| T-REPO-01 | `.gitignore`/`.dockerignore`; `.env.example` has names only | Pass | NOT RUN |
| T-REPO-02 | `docker history` / filesystem inspection | No secrets | NOT RUN |
| T-REPO-03 | Secret scan on repo + history | None | NOT RUN |
| T-REPO-04 | README steps on a clean machine | Work as written | NOT RUN |

## 5. Seed data for tests (synthetic only)

With the clock frozen at 2026-11-01 18:32 Kolkata, insert via the **authenticated API** under test owner A (each with a unique `submissionId`):

| Date | Category | Grams | Stage / reason |
|---|---|---|---|
| 2026-10-19 | produce | 1200 | preparation / trimming |
| 2026-10-20 | grains | 800 | service / overproduction |
| 2026-10-26 | produce | 600 | preparation / trimming |
| 2026-10-27 | dairy | 400 | storage / spoilage |

Use owner B with different records for isolation tests. **Never auto-seed a real account.**

## 6. Traceability

| Requirement | Tests |
|---|---|
| FR-A1..A4 | T-RES-03, T-UI-08, T-OPS-06, T-AUTH-* |
| FR-E1/E2 | T-VAL-*, T-TIME-01, T-IDEM-01..08 |
| FR-E3..E6 | T-UI-01..05, T-IDEM-04/05/07 |
| FR-E7 | T-UI-09, controller probe for edit/delete routes (none exist) |
| FR-D1..D4 | T-AGG-*, T-RES-01, T-UI-03 |
| FR-C1..C4 | T-TIME-04/05, T-CMP-* |
| FR-X1/X2 | T-EXP-*, T-CAP-03 |
| NFR-S1..S4 | T-AUTH-*, T-ISO-*, T-SEC-* |
| NFR-L1..L3 | T-VAL-05, T-LIM-*, T-CAP-01/02 |
| NFR-R1 | T-RES-*, T-LIM-05 |
| NFR-A1 | T-UI-06/07 |
| NFR-D1 | T-REPO-04 |

## 7. Results log (observed outcomes only)

| Date | Commit/build | Environment | Suite | Passed | Failed | Not run | Notes / unresolved |
|---|---|---|---|---|---|---|---|
| | | | | | | | |

## 8. Exit criteria

All High tests pass with the environment recorded, **or** the item is reported unresolved and no security pass is claimed. Medium tests pass or are reported. Deployed manual cases (Supabase Auth, REST denial, TLS, Render behaviour, browser) are run with synthetic data. Overruns are reported; isolation and concurrency tests are never silently dropped.
