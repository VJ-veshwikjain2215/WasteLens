# 08. References, Research Log, and Claims Ledger

Research performed 1 Nov 2026. "Primary" = provider/publisher documentation. "Secondary" = third-party summaries, used to orient and to be re-checked.

## 1. Sources

### Problem evidence
| Source | Type | Used for |
|---|---|---|
| UNEP, *Food Waste Index Report 2024* (press release and report page; co-authored with WRAP) | Primary (press release); figures cross-checked in several secondary summaries | 1.05 billion tonnes in 2022 incl. inedible parts; 60% households / 28% food service / 12% retail; "billion meals a day" concerns **households** |
| US EPA, tools for preventing and diverting wasted food | Primary (from brief; not re-fetched) | Assessment/tracking can inform prevention. No claim about demand or efficacy |

### Hosting and database
| Source | Type | Used for |
|---|---|---|
| Render, Deploy for Free: https://render.com/docs/free | Primary (fetched 1 Nov 2026) | Spin-down, 750 h/workspace, ephemeral FS, bandwidth/build metering and billing, service-initiated-traffic rule |
| Supabase, Project pausing: https://supabase.com/docs/guides/platform/free-project-pausing | Primary | 7-day low-activity pause, warning email, **90-day** restore window; paid projects not paused |
| Supabase, Connect to your database / pooling and limits: https://supabase.com/docs/guides/database/connecting-to-postgres | Primary | Direct = IPv6-only (IPv4 add-on), pooler session mode (5432) vs transaction mode (6543), prepared-statement caveat |
| Supabase, JWT Signing Keys (blog "Introducing JWT Signing Keys", 14 Jul 2025; feature page) | Primary | Asymmetric (opt-in) signing, JWKS endpoint, key states and rotation; legacy secret migration |
| Render, "How to use Supabase Auth with a backend on Render" | Primary (vendor article) | Verify locally with JWKS, `iss`/`aud` config, caching and refetch on unseen `kid`, ES256 vs legacy HS256 check |
| Supabase free-plan limits (jetadmin.io, cloudzero.com, focusreactive.com, automationatlas.io, metacto.com; 2026) | **Secondary** | ~500 MB DB, ~5 GB egress, 2 projects, ~50k MAU, no backups. **Re-check in the console** |

### Framework
| Source | Type | Used for |
|---|---|---|
| Spring Boot 4.0 Migration Guide; "Modularizing Spring Boot" (spring.io, 28 Oct 2025) | Primary | Starter renames, new test starters, Jackson 3 |
| Spring Security reference 7.1.2 (resource server / JWT / CSRF pages) | Primary | `NimbusJwtDecoder` with JWK Set URI, `jwsAlgorithm`, custom `OAuth2TokenValidator`, `JwtIssuerValidator`, `JwtTimestampValidator` pattern |
| Boot version/EOL summaries (herodevs.com, issoh.co.jp, marcobehler.com; Apr–Oct 2026) | **Secondary** | Boot 4.1 current line; 3.5 OSS support ended 30 Jun 2026; 4.0 ends 31 Dec 2026 |
| PostgreSQL docs (transaction isolation, INSERT/ON CONFLICT, advisory locks) | Primary (from brief; not re-fetched) | READ COMMITTED per-statement snapshots; `DO NOTHING … RETURNING`; transaction-scoped advisory locks |

## 2. Research log

| # | Question | Finding | Effect |
|---|---|---|---|
| 1 | Do Render Free limits in the brief hold? | Yes (15-min sleep, ~1-min wake, ephemeral disk, 750 h shared, billing for overage with a payment method). Extra: service-initiated-traffic suspension rule | `04` R-10/R-11, runbook §1 |
| 2 | Does the Supabase pause claim hold? | Yes: 7-day low-activity pause; 90-day restore window; paid not paused. It is a platform policy, not an app backup | R-09, ADR-012 |
| 3 | Can a Render Free backend reach Supabase Postgres? | Direct host is **IPv6-only** without the add-on; shared pooler supports IPv4. Render Free IPv6 egress is **unverified** | ADR-004, D1, R-03 |
| 4 | Which pooling mode suits the API? | Session mode for persistent servers; transaction mode is for transient clients and requires prepared statements off; JDBC isolation switching is session-level | ADR-004, T-OPS-03 |
| 5 | Is ES256/JWKS available by default? | **Opt-in**: migrate legacy secret then rotate to an asymmetric key; JWKS published; legacy HS256 projects exist. JWKS path appears in two forms in Supabase material | Setup gate, R-01/R-02 |
| 6 | Local verification pattern in Spring? | `NimbusJwtDecoder.withJwkSetUri(...).jwsAlgorithm(...)` with custom audience/issuer/timestamp validators; **required-claim behaviour for `exp` to be proven by test** | `03` §4, T-AUTH-06 |
| 7 | Do the UNEP numbers in the brief hold? | Yes: 1.05 bn t (2022), 60/28/12 split; household "billion meals/day" | `01-PRD` §1 |
| 8 | Boot line? | 4.1.x current; 3.5 EOL; starters renamed | ADR/D3 |
| 9 | Can ITs run in an image build? | No (no Docker daemon); reasoning, not a provider doc | D5 |

## 3. Claims ledger

### Verified on 1 Nov 2026 (against primary sources, at that date)
- Render Free: 15-min spin-down, ~1-min wake, ephemeral filesystem, 750 instance-hours per workspace, bandwidth/build metering with billing if a payment method exists, service-initiated-traffic suspension rule
- Supabase Free pausing: 7-day low-activity pause, email warnings, 90-day restore window, paid plans not paused
- Supabase direct connection IPv6-only (IPv4 add-on), pooler session/transaction mode behaviour and ports
- Supabase JWT signing keys exist, asymmetric keys publish a JWKS, rotation is staged, migration is opt-in
- UNEP 2024 headline numbers and the household-only scope of the "billion meals" statement
- Boot 4 starter renames and Security 7 resource-server decoder/validator API shape

### Reported by secondary sources only (re-check)
- Supabase Free plan numeric limits (DB size, egress, MAU, project count, no backups)
- Exact current Boot patch numbers and 4.2 timing

### From the brief, **not verified** here
- ES256, issuer, audience, role, and anonymous-flag values in the *chosen* project
- Which JWKS URL path the chosen project serves
- Direct REST denial for `anon`/`authenticated` on the private schema; app-role privileges (including `INSERT` sufficing for `gen_random_uuid()` defaults and identity-free design)
- That a pooler login with a custom role and `wastelens_app.<project-ref>` format works as assumed
- TLS `verify-full` against the provider CA from the runtime image
- Whether Spring's default timestamp validation requires `exp` (to be proven by T-AUTH-06)
- PostgreSQL concurrency claims (replay lookup sees the prior writer's committed row under READ COMMITTED after lock) **must be proven by T-IDEM-08 on real PostgreSQL**
- Render Free reachability to Supabase; Render proxy behaviour for oversized requests
- Export size bound practicality (10,000 rows ≪ 8 MiB expected; measure)

### Explicitly **unverifiable / untested** (never claim)
- Customer demand; staff willingness to log waste; adoption
- That logging reduces waste; that logged grams equal real waste
- Backup, restore, retention, uptime, durability
- Immediate revocation of issued tokens
- The 16-hour estimate

## 4. Re-verification schedule

Re-read the Render Free page, Supabase pausing/connection/JWT pages and pricing, and the Boot support page: (a) at **G0**, (b) on **deployment day**, (c) before reporting results. Record the date and any differences in the README.
