# 04. Security and Risk

## 1. Posture

Unlike a public demo, WasteLens is **private per account**, so the central security goal is **tenant isolation**: one account must never read, infer, or affect another's data. The second concern is **honest failure**: no false zeros, no false success, no durability claims. A browser-visible publishable key is **not** an authorization boundary; the API's verified-JWT owner predicate is.

## 2. Trust boundaries

| Actor / component | Trusted to | Not trusted to |
|---|---|---|
| Browser JS | Hold an in-memory session; send requests | Choose an owner, compute totals, enforce limits, validate input |
| Supabase Auth | Issue signed tokens, enforce its own rate controls | Authorize data access (it doesn't see our table) |
| API (Spring) | Verify tokens, derive owner, enforce all rules | n/a. A missed owner predicate is the key remaining risk |
| App DB login | `USAGE` on schema; `SELECT, INSERT` on one table | DDL, UPDATE/DELETE, auth admin, bypass RLS |
| Supabase Data API (PostgREST) | Serve only exposed schemas | **Must not** expose `wastelens_private`; `anon`/`authenticated` revoked |
| DB admin / operator | Everything | n/a. Not bound by append-only; cannot be assumed benign in a threat model |
| Hosting providers | Run the service | Guarantee uptime, retention, or data survival on Free plans |

## 3. Threats and controls

| ID | Threat | Control | Tests |
|---|---|---|---|
| TH-01 | **Owner spoofing** (body `ownerId`, query selector, header) | No owner input accepted (400); owner = verified `sub`; owner passed explicitly to every repo method | T-ISO-02 |
| TH-02 | **Cross-account read** (dashboard, compare, export) | Owner predicate in all SQL; two-owner tests for every repository method | T-ISO-01/03 |
| TH-03 | **Direct Data API access** to the table | Private schema not exposed; `REVOKE` from PUBLIC/anon/authenticated; optional RLS deny; attempt REST access with both keys | T-ISO-04 |
| TH-04 | **Forged/tampered/alg-confused token** (none, HS256, wrong key) | ES256 allow-list; fixed JWKS URI; signature + iss + aud + exp + nbf + role + sub validation; unknown `kid` rejected | T-AUTH-01..10 |
| TH-05 | **Token replay after sign-out/revocation** | *Not preventable* for unexpired tokens; documented residual risk; short expiry preferred; no claim of immediate revocation | Doc review |
| TH-06 | **Anonymous / non-authenticated Supabase user** | Require `role=authenticated`, reject anonymous claim; anonymous sign-in disabled in the project | T-AUTH-08 |
| TH-07 | **XSS** (stored via enums is impossible; reflected via fields) | Enum-only inputs (400 on `<script>`), React escaping, no raw HTML, strict CSP; token theft by XSS still possible → defence-in-depth only | T-SEC-01..02 |
| TH-08 | **SQL injection** | Bind parameters everywhere; fixed queries; enum/UUID/date validation | T-SEC-03 |
| TH-09 | **Duplicate/lost writes under retry or concurrency** | `UNIQUE(owner_id, submission_id)`, owner advisory lock, replay-before-cap, READ COMMITTED | T-IDEM-* |
| TH-10 | **Cap bypass by concurrency** | Lock + count + insert in one transaction; all writers use same lock | T-CAP-01..02 |
| TH-11 | **Resource exhaustion** (large bodies, bursts, exports) | 4 KiB cap, global/owner/export limiters, export semaphore, statement/pool timeouts, bounded export size | T-LIM-* |
| TH-12 | **JWKS-driven DoS** (garbage `kid`s forcing refetch) | Library refetch rate limiting; global limiter before auth; fail closed | T-AUTH-09 |
| TH-13 | **Secret leakage** (DB creds, service-role key, JWTs) | Env/secret store only; frontend gets only public URL/publishable key; no tokens in URLs/logs; non-root image; `.dockerignore`; secret scan | T-REPO-* |
| TH-14 | **Exported file exposure** | `no-store`, explicit user-triggered download, on-screen sensitivity/deletion note; only synthetic data allowed | T-EXP-05 |
| TH-15 | **Wrong-account retry** (pending entry submitted after re-login as another user) | Pending state records original `sub`; mismatch blocks submission, explicit discard | T-UI-05 |
| TH-16 | **Clickjacking / MIME sniffing / referrer leakage** | `frame-ancestors 'none'`, `nosniff`, `no-referrer` | T-SEC-04 |
| TH-17 | **Supply-chain** (npm/Maven) | Lockfiles, clean installs, dependency audit, avoid brand-new releases, supported Boot patch | CI step |
| TH-18 | **Overprivileged DB login** | `SELECT, INSERT` only; attempt UPDATE/DELETE/DDL/auth access as the app role | T-ISO-05 |

## 4. Residual risks (must be written in the README)

- **DB admin compromise, or one missed owner predicate**, defeats isolation (RLS not relied upon; optional deny-RLS is only secondary).
- **No immediate token revocation** for locally verified, unexpired JWTs.
- **XSS can steal a live in-memory token**; CSP reduces but does not eliminate this.
- **In-memory limiters** reset on restart, are single-instance, and are not DDoS defence.
- **Supabase Free pause and Render Free sleep/restart** make the demo unavailable at times; no backup, restore, retention, or uptime claim.
- **Immutable entries:** mistakes cannot be corrected in-app.
- **Row ownership by `sub` only:** deleting an Auth user leaves orphan rows.

## 5. Risk register

L = likelihood, I = impact (H/M/L). Planning judgements, not measurements.

| ID | Risk | L | I | Mitigation / contingency | Signal |
|---|---|---|---|---|---|
| R-01 | Project not issuing **ES256** (still legacy HS256) | M | H | Setup gate: migrate + rotate to asymmetric key *before* build; API refuses HS256 | Token header |
| R-02 | **JWKS path/`kid` differs** from assumption (two documented path forms) | M | M | Confirm path in project; config-driven URI; test | 404 on JWKS |
| R-03 | Render Free **cannot reach Supabase DB** (IPv6-only direct; pooler hostname/IP issues) | M | H | Use IPv4 session pooler; spike early; stop and report rather than adding paid add-on silently | Connection refused/timeout |
| R-04 | Pooler username/role format or TLS CA trust fails for the dedicated role | M | M | Verify in console; mount CA bundle; never disable verification | Handshake/auth error |
| R-05 | Direct REST access to private schema **not** denied | L | H | Revoke, verify unexposed, optional RLS deny; test both keys | T-ISO-04 fails |
| R-06 | Isolation bug (missed owner predicate) | M | H | Repository convention + two-owner test per method; code review checklist | Test failure |
| R-07 | Idempotency/cap race implemented wrongly | M | H | Real-Postgres concurrency tests; single lock path | T-IDEM/T-CAP |
| R-08 | Unknown-outcome UX loses UUID (reload/close) | H | M | `beforeunload` warning; documented limitation; no crash-proof claim | Observed |
| R-09 | **Supabase pause after 7 idle days** (restore possible ~90 days via Studio) | H | M | Document; no keep-alive; operator restore runbook | Pause email |
| R-10 | Render Free sleep (~1 min wake), restarts, 750 h/workspace, bandwidth/build metering | H | M | Document; visible retry states; check billing | Observed |
| R-11 | Render suspends service for high **service-initiated traffic** (e.g. external DB access) | L | H | Keep DB access modest; no polling | Suspension notice |
| R-12 | Supabase Free quota limits (DB size, egress, MAU) change or are hit | L | M | Re-verify on deploy day; tiny dataset by design | Console usage |
| R-13 | Auth rate limits / email provisioning problems block demo access | M | M | Provision and test accounts early; no signup flow | Sign-in failures |
| R-14 | CSP breaks the built SPA (inline styles, preloads) | M | L | Test built bundle under CSP early (M4) | Console errors |
| R-15 | Date off-by-one (client parse) or wrong "today" | M | M | Server-supplied dates; integer-day maths; boundary tests | T-TIME-* |
| R-16 | Export memory/size bound exceeded | L | M | Fixed record size; measure; 503 path | T-EXP-03 |
| R-17 | Estimate (16 h) optimistic | H | M | Risk-adjusted range in `07`; never drop isolation tests | Burn-down |
| R-18 | Boot 4 and Jackson 3 / starter changes slow development | M | L | Generate from start.spring.io; follow migration guide | Compile errors |
| R-19 | Provider terms/limits change after 1 Nov 2026 | M | M | Re-verify at G0 and deploy day | Doc diffs |
| R-20 | Copy implies demand, reduction, or "actual waste" | L | M | Copy review; keep "logged data only" language | Review |

## 6. Pre-release security checklist

- [ ] ES256 + claims verified in the project; JWKS URI confirmed; legacy HS256 not accepted
- [ ] All High auth/isolation tests pass with recorded results
- [ ] Anonymous auth and self-signup disabled; accounts provisioned out of band; no credentials in repo/UI/docs
- [ ] `anon`/`authenticated` direct REST read **and** write attempts denied (both keys)
- [ ] App DB login limited to `USAGE` + `SELECT, INSERT`; UPDATE/DELETE/DDL attempts fail
- [ ] Service-role/secret key absent from image, source, browser bundle, logs
- [ ] CSP present and the built SPA functions under it
- [ ] No tokens in URLs/logs/errors (inspect logs during tests)
- [ ] `no-store` on API and export; export only contains the caller's rows
- [ ] Image runs as non-root; `docker history` has no secrets
- [ ] TLS `verify-full` to the DB actually verified (wrong-host negative test)
- [ ] Limits behave (413/415/429/503) and fail safe
