# Deployment

Three free services, connected in this order: **Neon** (database) → **Render** (backend) →
**Vercel** (frontend). Do them in that order, because each needs a value from the one before.

Total cost: nothing. No card required for any of the three.

---

## 1 · Database — Neon

1. Create a project at <https://neon.tech>. Any region; pick one near Render's.
2. On the dashboard, open **Connection details** and copy the **pooled** connection string — the
   host contains `-pooler`. Use the pooled one: Neon's free tier allows few direct connections, and
   the pooler is what keeps a restarting service from exhausting them.
3. Convert it to JDBC form. **This is the step that trips everyone up**, so here it is exactly.

   Neon gives you a libpq URL with the credentials inside it:

   ```
   postgresql://neondb_owner:npg_XXXX@ep-cool-name-12345-pooler.c-3.ap-southeast-1.aws.neon.tech/neondb?sslmode=require&channel_binding=require
                └────────── delete this whole part, including the @ ──────────┘
   ```

   The JDBC driver takes the credentials as *separate* properties, so you split it into three:

   | Variable | Value |
   |---|---|
   | `DATABASE_URL` | `jdbc:postgresql://ep-cool-name-12345-pooler.c-3.ap-southeast-1.aws.neon.tech/neondb?sslmode=require&channel_binding=require` |
   | `DATABASE_USERNAME` | `neondb_owner` |
   | `DATABASE_PASSWORD` | `npg_XXXX` |

   Exactly two edits: **add `jdbc:` at the front**, and **delete `neondb_owner:npg_XXXX@`**.
   Everything after the host stays — the database name and the whole query string, `sslmode` and
   `channel_binding` included. Both are fine for the JDBC driver; only the embedded credentials
   are not.

   Leaving the credentials in produces a misleading error that never mentions them:
   *"Driver org.postgresql.Driver claims to not accept jdbcUrl"*. The application now detects this
   at startup and prints the corrected URL for you instead, but it is quicker to get it right here.

**Nothing else to do here.** Do not create tables. On its first start the backend runs the Flyway
migrations and seeds the six sample processes and sixteen research sources itself.

---

## 2 · Backend — Render

Render reads [`render.yaml`](render.yaml) automatically. **New → Blueprint**, point it at the repo,
and it creates the service with the right Docker settings. Or create a Web Service manually with:

| Setting | Value |
|---|---|
| Runtime | Docker |
| Dockerfile path | `./backend/Dockerfile` |
| Docker context | `./backend` |
| Health check path | `/actuator/health` |
| Plan | Free |

### Environment variables

Set these in **Environment** on the Render dashboard. The blueprint marks the secrets `sync: false`,
which means Render will prompt for them rather than take them from the repo.

| Variable | Required | Value | Notes |
|---|---|---|---|
| `DATABASE_URL` | **yes** | `jdbc:postgresql://<host>/neondb?sslmode=require&channel_binding=require` — **host only, no credentials** | From step 1 |
| `DATABASE_USERNAME` | **yes** | `neondb_owner` | From step 1 |
| `DATABASE_PASSWORD` | **yes** | *(the `npg_…` password)* | From step 1 |
| `AUTH_JWT_SECRET` | **yes** | *(auto-generated)* | The blueprint generates one. Setting it by hand? `openssl rand -base64 48`. Changing it later signs everyone out. |
| `GROQ_API_KEY` | **yes** | *(from Groq)* | <https://console.groq.com/keys>. The primary provider — a thousand requests a day per model, which is what makes ten stages affordable |
| `GEMINI_API_KEY` | recommended | *(from AI Studio)* | <https://aistudio.google.com/apikey>. The second quota. Its per-minute ceiling is separate from Groq's, so the high-volume stages alternate into it and a run finishes noticeably sooner |
| `APP_CORS_ALLOWED_ORIGINS` | **yes** | `https://your-app.vercel.app` | Fill in after step 3. Wildcards work — `https://*.vercel.app` also covers preview builds. A trailing slash is harmless |
| `AI_PROVIDER` | — | `groq` | Matches the application default. Set it anyway: it is the one variable whose being wrong is invisible — the app starts, runs, and quietly spends the smaller quota |
| `AI_FALLBACK_PROVIDERS` | — | `gemini` | Tried in order when the primary refuses |
| `ANALYSIS_PIPELINE` | — | `staged` | The ten-stage pipeline. `single` falls back to one prompt — worth doing if a daily quota is nearly spent |
| `RESEARCH_ENABLED` | — | `true` | Eleven connectors, none needing a key |
| `RESEARCH_MAX_DOCUMENTS` | — | `6` | The main lever on run time. Lower it to trade evidence for speed |
| `GROQ_MODEL` | — | `openai/gpt-oss-120b` | The fallback when a per-task route names a model that has been decommissioned |
| `GEMINI_MODEL` | — | `gemini-3.1-flash-lite` | Set by the blueprint |
| `GEMINI_THINKING_BUDGET` | — | `0` | Set by the blueprint — thinking tokens come out of the same free allowance |
| `AUTH_DEMO_ACCOUNT_ENABLED` | — | `false` | Set by the blueprint. **Leave it off and there is no demo login** — register an account in the UI instead. Set `true` to have `demo@assesswise.test` / `demo12345` created on boot |
| `DATABASE_POOL_SIZE` | — | `3` | Set by the blueprint. Keep small for Neon's free tier |
| `PORT` | — | *(injected by Render)* | Do not set |

Everything marked "—" already has this value as its default, so an unset variable behaves correctly.
The reason to set them anyway is that a Render service created before a blueprint change does not
pick up later additions to `render.yaml` — the dashboard is the source of truth for a service that
already exists.

Deploy, then confirm:

```bash
curl https://your-api.onrender.com/actuator/health          # {"status":"UP"}
curl https://your-api.onrender.com/api/processes            # 401 — correct, it needs a token
```

A `401` on the second one is the right answer: it proves the API is up *and* that it is not open to
the public.

---

## 3 · Frontend — Vercel

**Add New → Project**, import the repo, then set:

| Setting | Value |
|---|---|
| **Root Directory** | `frontend` ← easy to miss, and the build fails without it |
| Framework preset | Next.js (auto-detected) |
| Build / install commands | leave as detected |

### Environment variables

| Variable | Value |
|---|---|
| `NEXT_PUBLIC_API_BASE_URL` | `https://your-api.onrender.com` — no trailing slash |

Add it for **Production**, **Preview** and **Development** so preview deployments work too.

> `NEXT_PUBLIC_*` values are baked into the browser bundle **at build time**. Changing this variable
> requires a redeploy, not just a restart. If the deployed site says it cannot reach
> `http://localhost:8080`, this variable was missing when the build ran.

---

## 4 · Close the loop

Go back to Render and set `APP_CORS_ALLOWED_ORIGINS` to the Vercel URL from step 3, then let it
redeploy. Until you do, the browser will block every API call and the site will look broken while
the backend looks healthy.

Then open the Vercel URL, create an account, and run an analysis end to end.

---

## Updating a deployment that is already running

Both hosts redeploy on a push to the tracked branch, so new code arrives by itself. What does **not**
arrive by itself is configuration: a Render service created earlier keeps exactly the variables it
was created with, and later additions to `render.yaml` are not applied to a service that already
exists. That is the whole checklist.

**Schema.** Nothing to do. Flyway runs on boot and the log says what it found:

```
Successfully validated 5 migrations
Current version of schema "public": 5
Schema "public" is up to date. No migration necessary.
```

A new migration would appear here as `Migrating schema "public" to version 6`. Neon needs no manual
step either way.

**Configuration.** Compare the dashboard against the table above and add what is missing. Read the
boot log to see what the service actually resolved — the two lines worth checking are:

```
AI provider chain: [groq(openai/gpt-oss-120b), gemini(gemini-3.1-flash-lite)]
Analysis pipeline 2-staged: [intake, diagnosis, research, …]
```

If the chain names Gemini first, `AI_PROVIDER` is unset or wrong. Nothing will fail — it will simply
run the whole pipeline on the smaller quota and exhaust it.

**Frontend.** `NEXT_PUBLIC_API_BASE_URL` is compiled into the bundle at build time, so changing it in
the Vercel dashboard does nothing until the project is redeployed.

**Verifying the new code is actually live**, rather than a cached image:

```bash
curl -i https://your-api.onrender.com/api/nothing-here     # 404 with a problem document, not 500
curl -s https://your-api.onrender.com/actuator/health      # {"status":"UP"}
```

---

## What each service is doing

```
Browser ──── HTTPS ────► Vercel        static Next.js, no server-side data access
   │
   └──────── HTTPS ────► Render        Spring Boot API, JWT auth, the analysis pipeline
                            │
                            ├── TLS ─► Neon           PostgreSQL: processes, users, audit trail
                            └── HTTPS ► Gemini / Groq  the model calls
```

The browser talks to Render directly rather than through Vercel. That is why `APP_CORS_ALLOWED_ORIGINS`
matters and why the token is a bearer header rather than a cookie — the two are different sites, and
third-party cookies are increasingly blocked.

---

## Free-tier behaviour worth knowing before a demo

**Render sleeps after 15 minutes idle** and takes roughly a minute to wake. The UI handles this — it
says the backend is starting rather than showing an error — but open the site two or three minutes
before you present.

**A fresh analysis takes about four minutes.** Roughly 30,000 tokens against Groq's 8,000-per-minute
ceiling, which is shared across its models rather than being per model. That floor is arithmetic, not
slowness. The run reports itself stage by stage while it waits, which is the part worth showing.
Re-running an unchanged process is near-instant — the responses are cached in Postgres.

**Two provider keys are meaningfully faster than one.** Gemini's per-minute ceiling is separate from
Groq's, so the two highest-volume stages alternate between them. A second model on the same account
buys a second daily allowance, not extra throughput.

**Neon suspends an idle database**, which adds a second or two to the first query. Harmless.

**A run outlives the tab that started it.** Closing the page does not stop it; reopening the process
shows its progress again, and the dashboard badges it while it runs.

---

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Site loads, every request fails, console shows a CORS error | `APP_CORS_ALLOWED_ORIGINS` does not match the Vercel URL | Set it on Render including the `https://`. A trailing slash is tolerated; a missing scheme or the wrong subdomain is not |
| "Could not reach the API at http://localhost:8080" | `NEXT_PUBLIC_API_BASE_URL` was unset at build time | Set it on Vercel and **redeploy** |
| Deploy fails: *"Driver org.postgresql.Driver claims to not accept jdbcUrl"* | `DATABASE_URL` still has `user:password@` in it | Delete that part — see step 1. The startup check prints the corrected URL in the log |
| Deploy fails: *"DATABASE_URL must be a JDBC URL"* | Missing the `jdbc:` prefix | Add it |
| Backend deploy fails on start with a Flyway error | Pointed at a database that already has a different schema | Use an empty Neon database, or drop and recreate the `public` schema |
| Every request returns 401 straight after a deploy | `AUTH_JWT_SECRET` changed, invalidating existing tokens | Expected. Sign in again |
| Sign-in fails with the demo credentials | `AUTH_DEMO_ACCOUNT_ENABLED` is `false`, which is the blueprint's default, so no demo account was ever created | Register an account in the UI, or set it to `true` and redeploy. The boot log says which: it prints either "Created the demo account" or "already exists", and nothing at all when it is switched off |
| `Analyse` returns 503 | No AI key configured | Set `GROQ_API_KEY` (and ideally `GEMINI_API_KEY`) |
| `Analyse` returns 502 saying the model was not found | Free-tier model availability changed | Check what your key can reach: `curl -s https://api.groq.com/openai/v1/models -H "Authorization: Bearer $GROQ_API_KEY"` |
| Analyses are slow and the daily quota vanishes | The boot log shows `AI provider chain: [gemini…, groq…]` — `AI_PROVIDER` is unset, so Gemini's few dozen requests a day are carrying a ten-stage pipeline | Set `AI_PROVIDER=groq` and `AI_FALLBACK_PROVIDERS=gemini` |
| An unknown URL returns 500 rather than 404 | The running image predates the fix | Redeploy; the current code answers a problem document |
| First request after idle takes ~60s — and the first boot after a deploy took three minutes | Render free tier cold start, plus Neon waking up | Expected. Warm both before demoing: open the site, wait for the dashboard to fill in |

---

## Running it locally instead

See the **Run it locally** section of the [README](README.md). Local development needs no cloud
account at all: `scripts/local-db.sh` starts a PostgreSQL in your home directory, and everything
except the `Analyse` button works without any AI key.
