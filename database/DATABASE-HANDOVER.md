# Database Handover — What to Send, What to Get Back

**Situation:** The Railway subscription has expired. Someone else is provisioning a new database server. This document tells you exactly what to hand them, and exactly what to get back from them so you can reconnect the backend.

---

## 1. What to send them

**Send the real dump file — the one with data, not the schema-only copy in this folder.**

- File: `recruitment_portal_backup.sql` (the one you extracted from Railway on 2026-08-13, ~1000 applications, all users, staff, jobs, scores, audit log).
- This file contains **real applicant PII** (names, emails, National ID numbers, bcrypt password hashes). Send it over a private channel (encrypted drive, direct transfer) — **not** Slack/email as a plain attachment, and never commit it to GitHub.
- `database/railway-backup-2026-08-13.sql` in this repo is a **schema-only** version of the same file (table structures, no data) — safe to keep in git for reference, but it will NOT restore any candidates/jobs/staff. Don't hand that one over expecting a working system; it just documents the shape of the tables.

Tell them to run:
```bash
mysql -h <new-host> -u <new-user> -p <new-database-name> < recruitment_portal_backup.sql
```
That one command recreates every table (all 17: `users`, `jobs`, `applications`, `criteria`, `candidate_scores`, `assessments`, `audit_log`, `staff`, `departments`, `job_templates`, `cv_profiles`, `settings`, `permission_overrides`, `notifications`, `sent_emails`, `chatbot_queries`, `analytics_events`) and reloads all existing data in one shot.

---

## 2. What to get back from them

Once they've provisioned the server and imported the dump, you need exactly **5 values**:

| Value | Example | Used as |
|---|---|---|
| Host / IP or hostname | `db.example.com` or `41.xxx.xxx.xxx` | `DB_HOST` |
| Port | `3306` | `DB_PORT` |
| Database name | `caa_recruitment` or `railway` (whatever they named it on import) | `DB_NAME` |
| Username | `caa_recruitment_user` | `DB_USER` |
| Password | (strong password they set) | `DB_PASSWORD` |

**Also ask them to confirm:**
- Whether the port is reachable from the internet (your backend needs to connect to it remotely — it's not on the same machine as the backend unless they're hosting both together). If it's firewalled to specific IPs only, you need your backend's outbound IP allow-listed.
- Whether SSL/TLS is required to connect (some managed MySQL hosts require it — if so, ask for the CA certificate too).

That's it — you do **not** need table names, schema details, or anything else back from them; the dump already defines all of that, and the backend code already knows the table structure.

---

## 3. Where those 5 values go (backend `.env`)

Open (or create) `.env` in the root of `caa-recruitment-backend` and set:

```bash
DB_HOST=<value from them>
DB_PORT=<value from them>
DB_NAME=<value from them>
DB_USER=<value from them>
DB_PASSWORD=<value from them>
```

These are read directly by `config/db.js` — no other file needs editing for the database connection itself. If they told you SSL is required, you'll additionally need to pass SSL options into the `mysql.createPool()` call in `config/db.js` (ask them for the exact requirement — "require SSL" vs. "verify CA" — since the code change differs slightly).

---

## 4. How this connects the rest of the system (frontend ↔ backend ↔ database)

The three pieces and how they find each other:

```
Candidate's browser
     │
     ▼
Netlify (frontend) — aviation-careers-hub-live.netlify.app
     │  calls API_URL (baked into frontend build config)
     ▼
Backend (Node/Express) — wherever it's hosted (Railway app, Render, VPS, etc.)
     │  connects using DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD
     ▼
Database (new server)
```

**Only the bottom arrow changes** in this handover — the database connection. Nothing else needs to move:

- ✅ **Backend app hosting** stays wherever it currently runs. Only its `.env` database values change.
- ✅ **Frontend (Netlify)** doesn't know or care where the database is — it only talks to the backend's `API_URL`, which is unchanged.
- ✅ **CORS** (`FRONTEND_URL` env var on the backend) is unaffected — that's about which frontend origin the backend accepts, unrelated to the database.

**If the backend app itself is also moving** (i.e., you're leaving Railway app hosting too, not just the database), that's a separate, bigger handover — you'd additionally need a new `API_URL` and would have to update the frontend's build-time API URL config in Netlify, plus re-add `FRONTEND_URL` on whatever now hosts the backend. Confirm with whoever's doing this work which case you're actually in before assuming it's DB-only.

---

## 5. After you update `.env` — verification steps

```bash
# 1. Restart the backend so it picks up the new .env values
npm start

# 2. Confirm it can reach the new database
curl http://localhost:5000/api/settings
# Expected: JSON with "org_name": "Uganda Civil Aviation Authority" — proves
# the connection works AND the settings row survived the import.

# 3. Confirm real data came across
mysql -h <new-host> -u <new-user> -p <new-database-name> -e "
  SELECT COUNT(*) AS users FROM users;
  SELECT COUNT(*) AS jobs FROM jobs;
  SELECT COUNT(*) AS applications FROM applications;
  SELECT COUNT(*) AS staff FROM staff;
"
# Expected roughly: 16+ users, 40+ jobs, ~1000 applications, 30 staff
# (matches counts in the original Railway dump)

# 4. Test an actual login against the migrated data
curl -X POST http://localhost:5000/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@caa.go.ug","password":"<the real admin password>"}'
# Expected: 200 with a token — proves password hashes migrated correctly
```

If step 2 fails with a connection error, it's almost always one of:
- Wrong `DB_HOST`/`DB_PORT` (typo, or the server isn't publicly reachable)
- Your backend's outbound IP isn't allow-listed on their firewall
- Password/user mismatch (double-check for copy-paste trailing spaces)

If step 2 succeeds but step 3 shows 0 rows everywhere, the import didn't run against the file with data — re-check they imported `recruitment_portal_backup.sql`, not the schema-only reference copy.

---

## 6. Do NOT do

- Don't ask them to recreate the schema from scratch by hand — the dump file already has exact `CREATE TABLE` statements matching what the backend code expects (column names, types, foreign keys, enums). Recreating it manually risks a mismatch (e.g. missing a column like `jobs.job_ref` that Phase 3 code reads) that silently breaks features instead of throwing a clear error.
- Don't run `scripts/migrate.js` on top of an imported dump "just to be safe" — it's meant for building a schema from nothing. Running it after `recruitment_portal_backup.sql` is redundant at best; only run it if they need a fresh, empty database instead of the historical one.
- Don't send the full dump file over an unencrypted or public channel — it contains real names, emails, and NIN numbers of applicants.

---

**Document Version:** 1.0
**Created:** 2026-08-19 — in response to Railway subscription expiry
