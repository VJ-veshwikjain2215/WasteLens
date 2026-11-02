# 06. Deployment Runbook

> **Nothing here may be executed until the owner explicitly approves implementation (Gate G0) and authorizes provisioning (Gate G1).** The brief says: design only, not approved for implementation. If provider signup, plan eligibility, networking, TLS, auth configuration, or quotas block a step, **stop and report**. Do not buy an add-on, switch plans, or change the architecture silently. None of this has been tested.

## 1. Provider facts (checked 1 Nov 2026; re-verify at G0 and on deploy day)

### Render Free web service ([render.com/docs/free](https://render.com/docs/free), primary)

| Fact | Consequence |
|---|---|
| Spins down after **15 min** with no inbound traffic; wake takes **about a minute** | First request after idle is slow; UI needs visible retry states |
| **750 free instance-hours per workspace/month**, shared across Free web services | One always-awake service ≈ 720–744 h; other Free services compete |
| **Ephemeral filesystem**: lost on restart, redeploy, and spin-down; Render may restart a Free service at any time | Never store entries or exports on disk; sessions are in memory only anyway |
| Outbound **bandwidth** and **build minutes** are metered; overage is **billed** if a payment method exists, otherwise Free services are suspended / builds disabled | "Free compute" is not an unconditional zero-cost guarantee; check billing first |
| May suspend a Free service with **uncommonly high service-initiated traffic** (e.g. accessing an external database) | Keep DB traffic modest; no polling or keep-alives |
| No SSH/shell, no persistent disk, single instance, rollbacks to last two deploys only; cannot send outbound SMTP ports | Migrations run **outside** Render |
| Render says Free is not for production | Reflected in all copy |

### Supabase Free (pausing: primary; other limits: secondary, re-check)

| Fact | Consequence |
|---|---|
| Free projects with low activity over **7 days** are paused; a warning email is sent about a week before; restore is possible for **90 days** from Supabase Studio; paid projects are not paused | Plan for pause; restore is a platform feature, **not** an application backup |
| Free plan reported (secondary sources, May–Jul 2026): 2 active projects, ~500 MB database, ~5 GB egress, ~50,000 MAU, no backups, community support | Dataset is tiny by design; confirm numbers in the console |
| **Direct DB connection is IPv6-only** unless the paid **IPv4 add-on** is used; the **shared pooler (Supavisor)** supports IPv4: **session mode** (persistent servers) and **transaction mode** (transient clients; turn off prepared statements) | See §3; choose session mode unless IPv6 egress from Render is proven |
| Asymmetric JWT signing is **opt-in** (migrate legacy secret, then rotate); JWKS exposes public keys | Setup gate (§4) |
| Supabase applies its own Auth rate controls | Provision/test accounts early |

## 2. Preflight checklist

- [ ] **G0** explicit written approval to implement (this brief is "design only")
- [ ] Decisions D1–D12 confirmed or defaults accepted
- [ ] Render account eligible for a Free web service (**unverified**)
- [ ] Supabase account eligible for a Free project; the project is **dedicated** and contains no real data (**unverified**)
- [ ] Render **Billing** page reviewed: payment method on file? included usage noted
- [ ] No other Free Render services unexpectedly using the 750 h
- [ ] Regions chosen (nearby for Render and Supabase)
- [ ] Re-read Render Free and Supabase pause/pricing/connection pages for changes since 1 Nov 2026
- [ ] Demo user list agreed; credential delivery channel decided (out of band)
- [ ] All High tests run locally; results recorded (G2)

## 3. Supabase project setup (operator, not the app)

1. **Create a dedicated demo project** (never reuse one with real data). Note region and project ref.
2. **Auth settings:** disable self-signup, disable anonymous sign-ins; confirm email/provider settings needed for password sign-in.
3. **Signing keys:** in the project's JWT Keys settings, migrate to the new signing keys and **rotate to an asymmetric ES256 key** if the project is still on the legacy secret. Confirm with the setup gate in §4.
4. **Create demo users privately** (names/emails chosen by the operator). Deliver credentials out of band. Never put them in the repo, UI, or docs.
5. **Database roles and schema** (as a privileged migration role, outside the app):
   - Create the private schema and table (see `03` §6). Do **not** add the schema to the project's exposed API schemas.
   - `REVOKE` all on schema/table from `PUBLIC`, `anon`, `authenticated` (and verify default privileges didn't re-grant).
   - Create a dedicated **app login** (e.g. `wastelens_app`, `LOGIN`, no `BYPASSRLS`, no `CREATEDB/CREATEROLE`) with a generated secret.
   - Grant only `USAGE` on the schema and `SELECT, INSERT` on the table.
   - Set role-level timeouts: `ALTER ROLE wastelens_app SET statement_timeout='5s'; ALTER ROLE wastelens_app SET lock_timeout='2s';`
   - Optional (D2): enable RLS and add a single policy for the app role.
6. **Connection details:** from the project's **Connect** panel copy the *session pooler* connection details (IPv4-capable, port 5432). The pooler username format typically includes the project ref (e.g. `role.<project-ref>`); confirm in the console. The *direct* host is IPv6-only without the add-on.
7. **Verify** (these are tests T-ISO-04/05, T-OPS-03):
   - `anon` and `authenticated` keys cannot read or write the table over REST.
   - The app login can `SELECT`/`INSERT` and nothing else.
   - TLS with **certificate and hostname verification** works with the provider's trusted CA; a wrong-host negative test fails.

## 4. Setup gate: prove the token before building anything

With a freshly signed-in demo user, inspect the **access token** (header and claims):

| Check | Required |
|---|---|
| Header `alg` | `ES256`, with a `kid` |
| `iss` | exactly `https://<project-ref>.supabase.co/auth/v1` |
| `aud` | contains `authenticated` |
| `role` | `authenticated` |
| `sub` | non-empty UUID |
| anonymous flag | absent/false |
| JWKS | reachable at the configured URI (confirm which documented path the project serves: `/auth/v1/.well-known/jwks.json` or `/auth/v1/jwks`) and lists the `kid` |

If any check fails (especially HS256), **stop**: fix the project settings or report. The API must never accept HS256 or fall back to insecure verification.

## 5. Schema migration (outside the web app)

- Versioned SQL lives in `backend/src/main/resources/db/migration/`.
- Apply with a migration tool (default: Flyway CLI/Maven plugin; D4) run from a trusted machine or CI with the **elevated** role over the **session pooler or direct** connection. Do not use transaction-mode pooling for migrations (session-level features/locks).
- Never run migrations from the app login or from Render. Never run a destructive reset.
- Re-run grants/revokes as part of the same operator procedure, then re-run T-ISO-04/05.

## 6. Docker image

```
Stage 1 (node:<lts>):   npm ci → vite build → /dist
Stage 2 (maven + JDK21): copy /dist into backend static resources → ./mvnw package (unit tests only)
Stage 3 (JRE 21):        non-root user; copy only the executable JAR; listen on $PORT
```

- `server.port=${PORT:8080}`; bind `0.0.0.0`; no extra ports.
- **No secrets** in build args, layers, or logs. `VITE_SUPABASE_URL` and `VITE_SUPABASE_PUBLISHABLE_KEY` are **public** and injected at build time (changing them requires a rebuild); backend secrets come only from runtime env.
- Explicit SPA fallback for `/login`, `/dashboard`, `/entries/new`, `/compare` that does **not** intercept `/api/*` or static files.
- Testcontainers ITs cannot run in the build stage (no Docker daemon); run them locally/CI (D5).
- Build and run locally against local PostgreSQL first; then `docker run` with placeholder env.

## 7. Render setup

1. Push the reviewed repo to a **new** GitHub repository (no overwrite); visibility per D7.
2. Render → **New → Web Service** → connect repo → **Docker**; **Instance type: Free** (verify before creating).
3. Runtime env vars (secrets stay server-side): `DB_JDBC_URL`, `DB_USER`, `DB_PASSWORD`, `SUPABASE_ISSUER`, `SUPABASE_JWKS_URI`, `SUPABASE_AUDIENCE=authenticated`. Build-time public config: `VITE_*` (as Docker build args are *not* secret).
4. Health check path: unset, or a static asset that bypasses the DB and limiters.
5. Auto-deploy off (or after CI passes) to avoid surprise rebuilds.
6. **No keep-alive pings.** (D11)

## 8. Post-deploy smoke test (synthetic data only)

Record observed results in `05-TEST-PLAN.md` §7.

1. HTTPS loads; `/login` shows no signup link; wrong password → generic "Sign-in failed".
2. Sign in as demo user A; dashboard shows the synthetic notice and timezone.
3. Add the four seed entries through the UI; verify dashboard, compare (−1000 g / −50.0%), and export JSON.
4. Sign in as demo user B; confirm none of A's data appears anywhere.
5. Attempt REST access with the publishable/anon key (T-ISO-04). Must be denied.
6. Force a failed/slow save; verify "Save status unknown; Retry safely" and same-UUID retry.
7. Observe and record: Render wake time, DB connect time after idle, behaviour when Supabase is paused (if testable).
8. Confirm plans are Free on both providers; check usage/billing; no unexpected charges.
9. Inspect logs for tokens/bodies/secrets (there should be none).
10. Only then report a live URL and results. Do **not** put a real URL or credentials in the README before they exist and were observed.

## 9. Operations and incident playbook

| Situation | Action |
|---|---|
| Supabase project paused | Restore from Studio (within 90 days of pause); verify data; note that this is not a backup guarantee |
| Render service slow/asleep | Expected on Free; wait ~1 min; no pings |
| Render hours/bandwidth/build limit reached | Service/builds suspended until next period or paid change. **Do not upgrade silently**; ask the owner |
| Need to correct/reset synthetic data | Operator action via migration owner role (e.g. delete a demo account's rows); never expose as an app feature; record it |
| Suspected credential leak | Rotate the app DB password, update the Render env var, redeploy; rotate/re-issue demo user passwords if needed; rotate publishable/secret keys if applicable |
| Suspected token theft/abuse | Sign the account out (scope) and, if needed, disable the user in Supabase Auth; remember **unexpired tokens may remain valid** until expiry |
| JWKS/signing-key change | Re-run the setup gate; confirm the API accepts the new key and still rejects unknown keys |
| Exported file concerns | Remind users the file is sensitive to device access; delete after use; no backup claim |
| Retire the demo | Delete Render service and Supabase project; update README to say it is offline |

## 10. Rollback

Render keeps only the last two prior deploys on Free. Prefer redeploying a prior commit. Migrations are forward-only; avoid destructive changes. No data rollback is promised.
