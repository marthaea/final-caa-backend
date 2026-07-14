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
