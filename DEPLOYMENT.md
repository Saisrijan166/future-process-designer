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
3. Convert it to JDBC form. Neon gives you something like:

   ```
   postgresql://neondb_owner:npg_XXXX@ep-cool-name-12345-pooler.ap-southeast-1.aws.neon.tech/neondb?sslmode=require
   ```

   Which becomes three separate values:

   | Variable | Value |
   |---|---|
   | `DATABASE_URL` | `jdbc:postgresql://ep-cool-name-12345-pooler.ap-southeast-1.aws.neon.tech/neondb?sslmode=require` |
   | `DATABASE_USERNAME` | `neondb_owner` |
   | `DATABASE_PASSWORD` | `npg_XXXX` |

   Note the three edits: prefix `jdbc:`, drop the `user:password@` part, keep `?sslmode=require`.

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
| `DATABASE_URL` | **yes** | `jdbc:postgresql://…-pooler….neon.tech/neondb?sslmode=require` | From step 1 |
| `DATABASE_USERNAME` | **yes** | `neondb_owner` | From step 1 |
| `DATABASE_PASSWORD` | **yes** | *(Neon password)* | From step 1 |
| `AUTH_JWT_SECRET` | **yes** | *(auto-generated)* | The blueprint generates one. Setting it by hand? `openssl rand -base64 48`. Changing it later signs everyone out. |
| `GEMINI_API_KEY` | **yes** | *(from AI Studio)* | <https://aistudio.google.com/apikey> |
| `GROQ_API_KEY` | recommended | *(from Groq)* | <https://console.groq.com/keys> — the fallback when Gemini's small free quota runs out |
| `APP_CORS_ALLOWED_ORIGINS` | **yes** | `https://your-app.vercel.app` | Fill in after step 3. Wildcards work: `https://*.vercel.app` also covers preview builds |
| `AUTH_DEMO_ACCOUNT_ENABLED` | — | `false` | Set by the blueprint. Leave off unless you want the shared demo login |
| `DATABASE_POOL_SIZE` | — | `3` | Set by the blueprint. Keep small for Neon's free tier |
| `GEMINI_MODEL` | — | `gemini-3.1-flash-lite` | Set by the blueprint |
| `GEMINI_THINKING_BUDGET` | — | `0` | Set by the blueprint — thinking tokens come out of the same free allowance |
| `PORT` | — | *(injected by Render)* | Do not set |

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

**Gemini's free tier is small.** `gemini-3.1-flash-lite` with thinking disabled is the default for
exactly this reason. If it still runs out, Groq answers instead and the result page says so.

**Neon suspends an idle database**, which adds a second or two to the first query. Harmless.

---

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Site loads, every request fails, console shows a CORS error | `APP_CORS_ALLOWED_ORIGINS` does not match the Vercel URL | Set it on Render exactly, including `https://`, no trailing slash |
| "Could not reach the API at http://localhost:8080" | `NEXT_PUBLIC_API_BASE_URL` was unset at build time | Set it on Vercel and **redeploy** |
| Backend deploy fails on start with a Flyway error | Pointed at a database that already has a different schema | Use an empty Neon database, or drop and recreate the `public` schema |
| Every request returns 401 straight after a deploy | `AUTH_JWT_SECRET` changed, invalidating existing tokens | Expected. Sign in again |
| `Analyse` returns 503 | No AI key configured | Set `GEMINI_API_KEY` (and ideally `GROQ_API_KEY`) |
| `Analyse` returns 502 saying the model was not found | Free-tier model availability changed | Check what your key can reach and set `GEMINI_MODEL` — see the note in the README |
| First request after idle takes ~60s | Render free tier cold start | Expected. Warm it before demoing |

---

## Running it locally instead

See the **Run it locally** section of the [README](README.md). Local development needs no cloud
account at all: `scripts/local-db.sh` starts a PostgreSQL in your home directory, and everything
except the `Analyse` button works without any AI key.
