# 01. Product Requirements Document (PRD)

## 1. Problem and evidence

Café and canteen teams may not know where measured food waste occurs. A lightweight log can make *recorded* weight visible. It does **not** prove a venue's total waste, nor that any change is caused by logging.

| Evidence | Supports | Does **not** support |
|---|---|---|
| UNEP Food Waste Index Report 2024: 1.05 billion tonnes wasted in 2022 (incl. inedible parts); 60% households, **28% food service**, 12% retail | Food service is a material source globally | The "billion meals a day" headline concerns **households**, not cafés; nothing about this app's efficacy, or demand |
| US EPA material on assessing/tracking wasted food | Tracking can inform prevention | Demand for this app or any reduction it might cause |

**Unverified and must not be claimed:** customer demand, willingness of staff to log waste, adoption, reduction, effort fit, auth configuration in the chosen project, and deployment feasibility.

## 2. Purpose

A **private, synthetic-data demo** showing how an account-isolated waste log with honest reporting (counts, incompleteness warnings, "not comparable" states) could work. A technical and UX demonstration, not a validated product.

## 3. Users

| Persona | Need |
|---|---|
| Demo staff user (individually provisioned account) | Log synthetic entries, see weekly totals/top categories, compare two weeks, export own JSON |
| Operator (project owner) | Provision accounts out of band, verify auth/DB setup, reset synthetic data when asked |

Multiple demo accounts may exist, but each is a **separate** data owner; nothing is shared or aggregated.

## 4. Scope

### In scope
Sign-in (provisioned accounts) · add entry · weekly dashboard · two-week comparison · JSON export of own records · sign-out · visible synthetic-data notice.

### Explicit non-goals
Production use · real café/customer data · offline sync · edit/delete/import · shared multi-user venue · automatic food recognition · cost/CO₂ estimates · alerts · inventory · public analytics · charts beyond totals/ranked list · CSV · mobile app · backups/automated restore · Python · public registration · hard-coded common login · tenants/roles/venue-wide view · destructive reset.

### Fixed choices
Timezone **Asia/Kolkata** for all users · English UI · one account = one isolated owner · no free-text fields.

## 5. Requirements

### 5.1 Functional

| ID | Requirement |
|---|---|
| FR-A1 | `/login`: email + password; generic failure message *"Sign-in failed"* (no enumeration); *"Sign-in unavailable; try later."* when Auth is unreachable; **no signup link** |
| FR-A2 | Session held **in memory only**; reload or tab close signs out |
| FR-A3 | Sign-out clears client state and attempts Supabase sign-out; cannot guarantee immediate revocation of issued access tokens |
| FR-A4 | Unauthenticated navigation to app routes redirects to `/login` |
| FR-E1 | `/entries/new`: date (default = **server-reported** Kolkata today), category, whole grams, stage, reason, Save; inline validation; no free text |
| FR-E2 | Save is idempotent per `(owner, submissionId)`: exact retry returns the original entry (`replayed:true`); same ID with different payload → 409 |
| FR-E3 | On save: disable button, retain form + random client UUID in memory; success confirms persistence; clear UUID/form **only after** a confirmed response |
| FR-E4 | On timeout/network failure show *"Save status unknown; Retry safely"* and retry the **exact** UUID + payload; never silently mint a new UUID; if edits are wanted while unknown, retry until resolved first |
| FR-E5 | Pending submission (form, UUID, exact payload, **original verified subject**) survives navigation to `/login` in app-level in-memory state. After re-auth, retry only if the current `sub` equals the original; otherwise block, show *"This pending entry belongs to a different account; sign back into that account or discard the pending draft."*; discard requires explicit action and warns an earlier attempt may have committed |
| FR-E6 | Warn on navigation/close while an unknown outcome is pending; state plainly that crash-proof retry is not claimed |
| FR-E7 | Entries are immutable: no edit/delete endpoints; UI shows the "No correction in demo" notice |
| FR-D1 | `/dashboard`: prominent *"Synthetic demo data only"* and timezone; current-week card (Mon–Sun range, logged grams, entry count) |
| FR-D2 | Top 3 categories by grams for the current week; ties by category code ascending |
| FR-D3 | No entries → *"No entries logged: not proof of zero waste."* Show `0 g` only with `0 entries` and this warning |
| FR-D4 | On API failure show error + retry, **never zeros**; expired session → sign-in after preserving unsent form data in the open tab |
| FR-C1 | `/compare`: two independent Monday week-start selectors; defaults = previous completed week (B) and the week before (A); distinct weeks; no future week start |
| FR-C2 | Show each week's inclusive range, logged g, entry count; difference `B − A` in g; percent only if both weeks have ≥1 entry (A>0 follows) |
| FR-C3 | If either week has 0 entries: difference and percent = *"Not comparable: no entries logged"*; counts/totals still shown separately |
| FR-C4 | Current week, if selected, labelled *in progress*; every week labelled *"logged data only, completeness unknown"* |
| FR-X1 | Export JSON: authenticated download of `waste-lens-export.json` with **only** the owner's rows; success shown only after a complete valid response; failures reported and **no empty file saved** |
| FR-X2 | UI notes the exported file is sensitive to anyone with device access and gives safe-handling/deletion guidance; no backup claim |

### 5.2 Non-functional

| ID | Requirement |
|---|---|
| NFR-S1 | Verified JWT (ES256, issuer, audience, `exp`, `nbf`, `sub` UUID, role `authenticated`, not anonymous); fail closed |
| NFR-S2 | Owner derived only from verified `sub`; client-supplied owner rejected (400); all queries owner-filtered with bind parameters |
| NFR-S3 | Dedicated least-privilege DB role; private schema not exposed via Supabase Data API; PostgREST access with `anon`/`authenticated` denied |
| NFR-S4 | Strict CSP, `nosniff`, `no-referrer`, frame denial, `Cache-Control: no-store` on API/auth-sensitive/export responses; no tokens/secrets in URLs, logs, errors |
| NFR-L1 | Request body ≤ 4 KiB (413), JSON only (415) |
| NFR-L2 | ≤ 10,000 entries per owner enforced transactionally (409 `ENTRY_LIMIT_REACHED`) |
| NFR-L3 | Limits: 60 API req/min/owner; 120/min global for unauthenticated traffic; exports ≤ 5/min/user, ≤ 2 concurrent; DB statement timeout 5 s; pool max 4, acquire timeout 3 s → 429 + `Retry-After` / 503 |
| NFR-R1 | Dependency failure (DB, JWKS, Auth, pause) → visible 503/error, never false zeros or false success |
| NFR-A1 | Mobile-width usable; keyboard-operable; visible labels and errors; **no colour-only status** |
| NFR-D1 | README with limitations and *observed* test results |

## 6. Screens and flow

| Route | Behaviour (summary of FR) |
|---|---|
| `/login` | Email/password only; in-memory session |
| `/dashboard` | Notice + timezone; current-week card; top 3; links Add entry / Compare / Export JSON / Sign out |
| `/entries/new` | Form per FR-E*; confirmation links Dashboard / Add another |
| `/compare` | Two Monday selectors; per-week totals and counts; difference/percent or "Not comparable" |
| Export | Action on dashboard; Bearer `fetch` → Blob download |

After a reload the user signs in again; the route is preserved only as far as the URL is.

## 7. Field definitions

| Field | Rule |
|---|---|
| `entryDate` | Real ISO date `YYYY-MM-DD`, from `2020-01-01` through the server's Kolkata "today", inclusive; invalid/future rejected |
| `category` | `produce`, `grains`, `dairy`, `protein`, `prepared`, `other` (exact, lower-case) |
| `grams` | JSON **integer** 1–100000; reject strings, floats, booleans, null, NaN, negatives, 0, >100000 |
| `stage` | `preparation`, `service`, `storage` |
| `reason` | `overproduction`, `spoilage`, `trimming`, `plate_return`, `other` |
| `submissionId` | UUID, generated once per intentional submission |
| all | Required; **unknown fields and client `ownerId` rejected**; wrong capitalization rejected. Client validation is usability only; server and database enforce |

## 8. Time and calculations

- **Today** comes from a trusted server clock with `ZoneId Asia/Kolkata`, never the device. (UTC `18:29:59Z` = Nov 1 Kolkata; `18:30:00Z` = Nov 2.)
- **Week** = Monday 00:00 to next Monday 00:00 Kolkata; since `entry_date` is a `DATE`: `entry_date >= monday AND entry_date < monday + 7`. UI shows Monday–Sunday inclusive.
- Week-start parameters: must be a Monday, not in the future, ≥ the Monday containing 2020-01-01 (**2019-12-30**); comparison weeks must differ. Current week allowed, marked `inProgress`.
- **Aggregation:** bigint `SUM`, row `COUNT`; top categories by sum desc then code asc, max 3. **No** extrapolation or de-duplication by matching fields: two deliberate entries with different `submissionId`s count twice.
- **Difference** = `B total − A total`. **Percent** = `difference / A total × 100`, 1 decimal, signed, only when both counts > 0. Otherwise `null`.

**Worked example (synthetic):** A = 2026-10-19..25: produce 1200 + grains 800 = **2000 g, 2 entries**. B = 2026-10-26..11-01: produce 600 + dairy 400 = **1000 g, 2 entries**. B−A = **−1000 g, −50.0%**; B top = produce 600, dairy 400. If B has no entries: `0 g / 0 entries`, difference and percent are *not comparable*.

## 9. Acceptance criteria by area

- **Isolation:** B never sees A's rows/totals/exports; `ownerId` in body or an owner selector in the query → 400 and no insert; direct Supabase REST access to the private schema denied.
- **Idempotency:** 20 concurrent identical submissions → one row, one 201 and the rest 200 `replayed:true`; changed payload → 409; same UUID under B is independent.
- **Reporting honesty:** no zero is ever shown for a failure; "not comparable" appears whenever a week is empty.
- **Export:** valid parseable JSON, `schemaVersion:1`, only owner rows, sorted by `entryDate` then `id`; failure yields an error, never a partial file.
- **Release:** all High tests pass with recorded environment and results (see `05`).

## 10. Assumptions and open questions

| # | Assumption | Status |
|---|---|---|
| A1 | The chosen Supabase project can issue **ES256** tokens with the stated claims | **Unverified** (setup gate) |
| A2 | JWKS URL and `kid` behave as documented for this project | **Unverified** |
| A3 | Render Free can reach Supabase (IPv4 pooler or IPv6 direct) over verified TLS | **Unverified** |
| A4 | Direct REST access to the private schema is denied for `anon`/`authenticated` | **Unverified** (test T-ISO-04) |
| A5 | Free-tier quotas, email/Auth behaviour, and network work as stated | **Unverified** |
| A6 | Café staff would log waste / want this | **Untested** |
| A7 | 16 h estimate | Unverified; see `07` |
