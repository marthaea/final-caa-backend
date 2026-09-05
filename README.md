# CAA Recruitment Portal — Backend

Node.js / Express API backed by raw `mysql2` queries (no ORM/query builder),
matching the MySQL schema you created in phpMyAdmin.

## Setup

1. **Install dependencies**
   ```bash
   npm install
   ```

2. **Configure environment** — edit `.env` (already created) with your real
   MySQL credentials and a long random string for `JWT_SECRET` /
   `JWT_REFRESH_SECRET`. `.env.example` shows the required shape without
   real values, and is safe to commit; `.env` itself is gitignored.

3. **Run the server**
   ```bash
   npm run dev     # nodemon, auto-restarts on file changes
   # or
   npm start
   ```

4. **Confirm the DB connection**
   ```
   GET http://localhost:5000/api/v1/health
   ```
   Should return `{ "status": "ok", "db_result": 2 }`.

## Deploying to a server

These steps take a fresh Linux (or similar) server from nothing to a running
API. For full database provisioning/migration detail (schema creation,
restoring a real data dump, seeding), see
**[`DEPLOYMENT-DATABASE-SETUP.md`](DEPLOYMENT-DATABASE-SETUP.md)** — this
section just covers getting the Node app itself running.

1. **Prerequisites on the server**
   - Node.js 20+ and npm 10+
   - MySQL 8.0+ or MariaDB 10.5+ (can be on the same server or remote)
   - Git

2. **Get the code**
   ```bash
   git clone <this-repo-url>
   cd caa-recruitment-backend
   npm install --omit=dev
   ```

3. **Create the database and schema** — follow
   [`DEPLOYMENT-DATABASE-SETUP.md`](DEPLOYMENT-DATABASE-SETUP.md) to create
   the database/user and run `node scripts/migrate.js` (or restore an
   existing dump). Do this before starting the app — `index.js` fails fast
   on missing required env vars but does not create the schema for you.

4. **Configure environment** — copy `.env.example` to `.env` and fill in
   real values:
   ```bash
   cp .env.example .env
   ```
   At minimum set: `DB_HOST`, `DB_USER`, `DB_PASSWORD`, `DB_NAME`, `DB_PORT`,
   a unique `JWT_SECRET` and `JWT_REFRESH_SECRET` (generate with
   `node -e "console.log(require('crypto').randomBytes(64).toString('hex'))"`),
   `PORT`, `NODE_ENV=production`, and `FRONTEND_URL` (for CORS). Cloudinary
   and SMTP values are only required if you need file uploads / email
   sending respectively. **Never commit `.env`** — it's gitignored.

5. **Seed initial data (optional, first deploy only)**
   ```bash
   node scripts/seed-departments.js
   node scripts/seed-admins.js
   node scripts/seed-staff.js
   node scripts/seed-job-templates.js
   ```

6. **Start the process under a supervisor** — don't run `npm start` directly
   in production, since a crash won't restart it. Either:
   - **PM2**
     ```bash
     npm install -g pm2
     pm2 start index.js --name caa-recruitment-backend
     pm2 save
     pm2 startup   # follow the printed instructions to run pm2 on boot
     ```
   - **systemd** — create `/etc/systemd/system/caa-recruitment-backend.service`
     running `node /path/to/caa-recruitment-backend/index.js` with
     `Restart=on-failure`, `EnvironmentFile=/path/to/.env`, then
     `systemctl enable --now caa-recruitment-backend`.

7. **Put a reverse proxy in front** (Nginx, Caddy, etc.) to terminate
   TLS/HTTPS and forward to `http://127.0.0.1:$PORT`. Don't expose the raw
   Node port publicly, and make sure the MySQL port (3306) is **not**
   exposed to the internet either.

8. **Verify the deployment**
   ```bash
   curl http://localhost:5000/api/v1/health
   ```
   Should return `{ "status": "ok", "db_result": 2 }`. Then confirm the
   reverse-proxied public URL returns the same, and that `FRONTEND_URL`
   matches wherever the frontend is actually hosted (CORS will reject it
   otherwise).

## Project structure

```
config/       db.js (mysql2 pool), upload.js (multer disk storage)
middleware/   auth.js (JWT + role checks), errorHandler.js
utils/        jwt.js, deptId.js (DEPT-001 generator), asyncHandler.js
controllers/  one file per module (auth, users, departments, candidate
              profiles, education, certificates, referees, vacancies,
              applications, shortlist, interviews, offers, dashboard, admin)
routes/       one file per module + index.js mounting everything under /api/v1
```

## Key design notes

- **No ORM / query builder** — every query is raw SQL via `mysql2/promise`,
  matching your phpMyAdmin workflow. Multi-step writes (e.g. register +
  create profile, apply + auto-shortlist) use a transaction via
  `pool.getConnection()` so they roll back cleanly on failure.
- **DEPT-XXX codes** are generated in `utils/deptId.js` / the departments
  controller — not by a DB trigger — per your call to keep that logic in
  the Express service layer.
- **Auto-shortlisting** lives in `controllers/applicationController.js`
  (`evaluateAutoShortlist`). On `POST /vacancies/:id/apply`, it checks the
  vacancy's `shortlist_criteria` against the candidate's education/certificates
  and flips `applications.status` straight to `'shortlisted'` if all criteria
  match — no numeric score is stored, per your decision. The matching logic
  is a simple keyword check as a starting point; tell me if you want it
  smarter (e.g. weighted scoring, GPA thresholds).
- **Interview panels** are many-to-many (`interview_panel` join table),
  since panels are common in hiring — pass `interviewer_ids: [1,2,3]` when
  scheduling.
- **File uploads** (resume, cover letter) use `multer` with local disk
  storage under `/uploads`. Swap the `storage` engine in `config/upload.js`
  for an S3/GCS adapter later without touching any controller.
- **Auth**: `bcrypt` for password hashing, short-lived JWT access tokens +
  longer-lived refresh tokens (separate secrets).

## Not yet implemented (flagged, not silently skipped)

- **Email sending** (password reset, application status notifications) —
  `forgotPassword` currently returns the reset token directly in the
  response instead of emailing it. Wire up Nodemailer + a transactional
  provider and swap that out.
- **Audit log writing** — the `audit_logs` table and `GET /admin/audit-logs`
  read endpoint exist, but no controller currently *writes* to it yet.
  Cleanest approach: a small `logAudit(pool, { userId, action, entityType,
  entityId, details })` helper called from the controllers that mutate
  state (status changes, role changes, etc.) — say the word and I'll wire
  it through all of them.
- **Granular per-role permissions** (`PUT /admin/permissions/:role`) —
  the current schema uses a fixed `ENUM` for `users.role`, so this endpoint
  returns `501 Not Implemented` with an explanation. Only needed if you
  want admins to customize permissions beyond the 5 fixed roles.

## Testing endpoints

Import the routes into Postman (same pattern as your CAA Biostar API
testing). Suggested first pass:
1. `POST /api/v1/auth/register` → register an external candidate
2. `POST /api/v1/auth/login` → grab the `accessToken`
3. `POST /api/v1/departments` (as HR/Admin — you'll need to manually flip
   a test user's role to `hr_director` in phpMyAdmin first) → confirms
   DEPT-001 generation
4. `POST /api/v1/vacancies` → create a vacancy, then `POST /:id/publish`
5. `POST /api/v1/vacancies/:id/apply` (as the candidate) → confirms
   application creation + auto-shortlist evaluation
