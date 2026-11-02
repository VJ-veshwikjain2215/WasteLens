# WasteLens: Project Documentation Set

| | |
|---|---|
| **Document status** | Pre-development planning. **Design only. Not approved for implementation.** Nothing has been built, provisioned, deployed, or tested. |
| **Prepared** | 1 Nov 2026 |
| **Source** | "WasteLens developer-ready demo brief" plus a research pass on 1 Nov 2026 (see `08-REFERENCES.md`) |
| **Not to be confused with** | CountCheck (a separate project with its own doc set) |

## 1. What this project is

A **private, synthetic-data-only** demo of a café food-waste log. A small number of individually provisioned demo accounts each sign in and log their *own* waste entries (date, category, whole grams, stage, reason). Each account sees weekly logged weight, top categories, a two-week comparison with entry counts, and can export **only its own** records as JSON.

It is not a measurement of real waste. *No entries means not logged, not a verified zero.* Reduction and adoption are hypotheses; demand is untested.

## 2. Document map

| File | Purpose | Read it when |
|---|---|---|
| `00-README.md` | Index, constraints, research-driven changes, open decisions | First |
| `01-PRD.md` | Problem, scope, requirements (IDs), screens, field rules, calculations, acceptance criteria | Deciding *what* to build |
| `02-ARCHITECTURE-AND-DECISIONS.md` | Stack, components, flows, ADRs | Deciding *how* it fits |
| `03-TECHNICAL-SPEC.md` | API contract, JWT validation, schema, transaction algorithms, limits, frontend contracts, config | Writing code |
| `04-SECURITY-AND-RISK.md` | Threat model, controls, non-guarantees, risk register | Reviewing safety |
| `05-TEST-PLAN.md` | Test matrix with IDs and traceability (all **NOT RUN**) | Writing/running tests |
| `06-DEPLOYMENT-RUNBOOK.md` | Supabase, Render, Docker, provider limits, preflight, incident playbook | Deploying (after approval) |
| `07-PROJECT-PLAN.md` | WBS, estimate, milestones, gates, definition of done, README outline | Planning/tracking |
| `08-REFERENCES.md` | Sources, research log, claims ledger | Checking a claim |

## 3. Hard constraints (from the brief)

1. **Synthetic data only.** Visible "Synthetic demo data only" notice on the dashboard (and sensible elsewhere). No real café, customer, or personal data.
2. **One account = one isolated data owner.** No tenants, staff roles, shared venue view, or aggregation across accounts.
3. **Browsers never choose an owner.** All SQL uses the **verified JWT `sub`**. A browser-visible publishable key is not an authorization boundary.
4. **No public registration, no common/hard-coded login.** Accounts are provisioned by an operator out of band; credentials never appear in repo, UI, or docs.
5. **Single architecture:** React + Vite compiled into Spring Boot static resources; Java 21 Spring Boot API; one Docker image on **one Render Free web service**; **Supabase** for Auth + hosted PostgreSQL. No Python. No second database. **No state on Render disk.**
6. **Entries are immutable.** No edit/delete/import/reset. UI says: *"No correction in demo. Use a fresh demo account or ask an operator to reset synthetic data."* Compensating entries must **not** be suggested.
7. **Fixed timezone Asia/Kolkata**, English UI, whole-gram integers only, no free text.
8. **No claims** of backup, restore, retention, uptime, durability, demand, or waste reduction beyond what has been tested.
9. **Fail closed** on auth/JWKS/DB trouble; never show zeros or success for a failure.
10. **Release rule:** all High tests pass before any shared demo. A failed or unrun High test means **no security pass**.

## 4. What the research changed or added

The brief held up well. These items are what a planner should surface before coding (details in `02`, `03`, `08`):

| # | Finding | Impact |
|---|---|---|
| 1 | **Supabase's direct DB connection is IPv6-only** unless the paid IPv4 add-on is used. The shared pooler (Supavisor) supports IPv4; **session mode (port 5432)** suits a persistent Hikari pool, **transaction mode (6543)** is for transient/serverless clients and needs prepared statements off. Whether **Render Free can reach IPv6 egress is unverified**. | D1; runbook preflight spike. The brief already says "do not guess URL/port"; this makes the choice concrete. |
| 2 | **REPEATABLE READ via JDBC issues a session-level isolation change.** That works on direct or **session-mode** pooling, but is unreliable on **transaction-mode** pooling. | ADR-004: use session mode (or direct), not transaction mode. |
| 3 | **ES256 signing is opt-in on Supabase** (migrate legacy secret, then *rotate* to an asymmetric key). A project still on the legacy HS256 secret will fail the brief's own gate. **The JWKS path appears as `/auth/v1/.well-known/jwks.json` in some Supabase material and `/auth/v1/jwks` in others.** | Setup gate G0 must confirm algorithm, `kid`, and the real JWKS URL from the chosen project. Never assume. |
| 4 | **Spring's JWT timestamp validator does not, by itself, require `exp`** (my understanding of its defaults; to be proven by test T-AUTH-06). | Add an explicit "required claims" validator (exp, sub-as-UUID, role, aud, not anonymous). |
| 5 | **`JwtDecoder`-level behaviour can't be tested with Spring's `jwt()` test post-processor** (it bypasses the decoder). | Auth tests need the real decoder against a **local test JWKS** with ES256 keys. |
| 6 | **A plain `<a href>` cannot send a Bearer header**, so "browser authenticated GET downloads the file" must be `fetch` with the token, then a Blob download. | Frontend contract in `03` §9; the `Content-Disposition` header alone won't name the file. |
| 7 | **Page-size limit ≤100 applies to nothing**: the contract has no list endpoint. | Treated as N/A; do not build a list endpoint. |
| 8 | **Client date handling is a classic off-by-one trap** (`new Date("2026-10-26")` parses as UTC). | Frontend must use pure date-string/UTC-integer arithmetic; server supplies "today". |
| 9 | **Percent rounding mode is unspecified** ("round to 1 decimal"). | Decision D6: `HALF_UP` on `BigDecimal`, tested at negative half-way cases. |
| 10 | **Supabase free projects pause after 7 days of low activity**; restore possible for **90 days** from Studio. This is a platform pause policy, **not a backup**. | Reinforces "no durability claim"; no keep-alive pings (D11). |
| 11 | **Testcontainers ITs cannot run inside a Docker image build** (no Docker daemon in the build stage). | D5: image build runs unit tests; ITs run locally/CI. |
| 12 | **Optional defence-in-depth:** enable RLS on the table with a single policy for the app role, so `anon`/`authenticated` are denied even if grants slip. Does **not** change "owner predicate is the isolation boundary". | D2 (needs approval, since the brief says RLS is not relied on). |
| 13 | **Estimate:** 16 h looks optimistic for this scope (React app + JWT/JWKS + two-owner isolation + advisory-lock idempotency + export + two-stage Docker + provider setup). | Planner range ≈ 28–40 h (judgement); report overruns, never drop isolation tests. |

## 5. Open decisions

Defaults are proposed so planning is not blocked. None is implemented; all require approval at Gate G0.

| ID | Decision | Proposed default |
|---|---|---|
| D1 | DB connection method | Supavisor **session mode (5432)** unless a spike proves Render Free can reach the direct IPv6 endpoint; never transaction mode for the API |
| D2 | RLS deny-by-default as secondary control | Yes: enable RLS + one policy for the app role; still "predicate is primary" |
| D3 | Spring Boot line | 4.1.x latest patch (3.5 is out of OSS support) |
| D4 | Migration tooling | Flyway CLI/Maven plugin run **outside the web app** with an elevated role over session-pooler/direct; app has no migration runtime |
| D5 | Where integration tests run | Unit tests in image build; Testcontainers ITs local + CI |
| D6 | Percentage rounding | `BigDecimal`, `RoundingMode.HALF_UP`, scale 1 |
| D7 | Repo visibility | Public (no secrets by design) or private; owner decides |
| D8 | Supabase project | One **dedicated** demo project; never reuse a project with real data |
| D9 | Demo accounts | A small named set (e.g. 2–3); credentials delivered out of band |
| D10 | Regions | Render and Supabase in nearby regions (verify availability at creation) |
| D11 | Keep-alive | **None.** Accept pause/sleep; document restore procedure |
| D12 | Pending-entry store | Root-level in-memory store (outside the router), not a page component |

## 6. Glossary

- **Owner**: the verified Auth user (`sub` UUID) who owns entries.
- **Entry**: one immutable waste record. **Submission ID**: client UUID used as an idempotency key per owner.
- **Logged grams**: the sum of entries; not actual waste.
- **Unknown outcome**: a save where commit may or may not have happened.
- **Week**: Monday–Sunday in Asia/Kolkata; SQL uses `[monday, monday+7)` on a `DATE` column.
