# API Reference

Base URL: `http://localhost:5000/api` (development)

All responses follow: `{ success: bool, data: any }` or `{ success: false, error: string }`

Authentication: `Authorization: Bearer <access_token>` header required on protected routes.

Legend: 🔓 Public | 🔑 Any logged-in user | 👑 Admin only (with permission noted)

---

## Auth — `/api/auth`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/auth/register` | 🔓 | Register new account. Rate limited: 10/15min. Sends a verification email (or logs the link when SMTP is unconfigured) |
| POST | `/auth/login` | 🔓 | Login. Rate limited: 10/15min |
| POST | `/auth/refresh-token` | 🔓 | Rotate refresh token, get new access token |
| GET | `/auth/verify-email?token=...` | 🔓 | Verify an email address (single-use 64-char token from the verification email) |
| POST | `/auth/resend-verification` | 🔑 | Re-send the verification email for the logged-in user |
| GET | `/auth/me` | 🔑 | Get current user profile |
| PUT | `/auth/profile` | 🔑 | Update own name/email |
| POST | `/auth/logout` | 🔑 | Clear refresh token cookie |

### POST /auth/register
```json
// Request body
{
  "email": "amara@example.com",
  "password": "SecurePass1",
  "firstName": "Amara",
  "lastName": "Nakato",
  "accountType": "external",
  "employeeNumber": "CAA-001"  // only required when accountType = "internal"
}

// Response 201
{
  "success": true,
  "data": {
    "id": 5, "email": "amara@example.com",
    "firstName": "Amara", "lastName": "Nakato",
    "accountType": "external", "effectiveType": "external",
    "emailVerified": false, "token": "eyJ..."
  }
}
```

Login responses also include `emailVerified` (boolean). Accounts created before the
verification feature are treated as verified.

### POST /auth/login
```json
// Request body
{ "email": "amara@example.com", "password": "SecurePass1" }

// Response 200
{
  "success": true,
  "data": {
    "id": 5, "email": "amara@example.com",
    "firstName": "Amara", "lastName": "Nakato",
    "accountType": "external", "effectiveType": "external",
    "adminRole": null, "employeeNumber": null, "token": "eyJ..."
  }
}
```

### POST /auth/refresh-token
No request body needed — reads the `caa_refresh` httpOnly cookie automatically.
```json
// Response 200
{ "success": true, "data": { "token": "eyJ..." } }
```

---

## Jobs — `/api/jobs`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/jobs` | 🔓 | List open jobs (filtered by visibility) |
| GET | `/jobs/:id` | 🔓 | Get single job |
| POST | `/jobs` | 👑 canManageJobs | Create job (starts as `draft` or `published`, depending on `status`) |
| PUT | `/jobs/:id` | 👑 canManageJobs | Update job |
| PUT | `/jobs/:id/submit-for-review` | 👑 canManageJobs | `draft` → `pending_review`; emails the department's HOD |
| PUT | `/jobs/:id/review` | 👑 canReviewJob | HOD approves (→ `pending_approval`, emails every DHRA) or declines (→ `declined`, emails the creator with `declineReason`) |
| PUT | `/jobs/:id/approve` | 👑 canApproveJob | DHRA approves (→ `published`) or declines (→ `declined`, emails the creator) |
| PUT | `/jobs/:id/publish` | 👑 super only | Force-publish, bypassing the review/approval chain |
| DELETE | `/jobs/:id` | 👑 canManageJobs | Delete job |

### POST /jobs (create)
```json
{
  "title": "Aviation Safety Inspector",
  "dept": "Safety Oversight",
  "deptKey": "safety",
  "departmentId": 3,
  "location": "Entebbe International Airport",
  "salary": "UGX 3,500,000 – 4,500,000",
  "salaryBand": "UG4",
  "type": "Full-time",
  "closes": "31 Aug 2026",
  "closesAt": "2026-08-31",
  "visibility": "external",
  "minAge": 25,
  "requiredExperience": 3,
  "requiredQualification": "Degree",
  "description": "Internal notes — not shown to candidates",
  "featured": true,

  "jobRef": "UCAA/ADV/EXT/09/2026",
  "reportsTo": "Director, Aviation Safety",
  "vacancies": 2,
  "aboutRole": "Inspect aircraft and facilities for compliance with ICAO standards...",
  "accountabilities": [
    { "area": "Airworthiness Oversight", "activities": ["Conduct scheduled inspections", "Issue compliance reports"] }
  ],
  "specialSkills": ["Attention to detail", "Report writing"]
}
```
`description` is admin-only working notes; `aboutRole` ("Job Purpose"), `accountabilities`, and `specialSkills` are the candidate-facing content rendered on `/job` and in the PDF advert. `salary` (the full range) is never sent to any candidate-facing endpoint or surface — only `salaryBand` ("Salary Scale") is.

---

## Applications — `/api/applications`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/applications` | 🔑 | Own applications (candidates) / all with filters (admins) |
| GET | `/applications/export` | 👑 canViewApplications | Download CSV |
| POST | `/applications` | 🔑 | Submit application |
| PUT | `/applications/bulk-status` | 👑 canShortlist | Update multiple statuses |
| PUT | `/applications/:id/status` | 👑 canShortlist | Update one status + notify |
| DELETE | `/applications/:id` | 🔑 | Withdraw own application |

### GET /applications (admin query params)
```
?jobId=3&status=Shortlisted&fromDate=2026-01-01&toDate=2026-12-31&email=amara
```

### POST /applications
```json
{
  "jobId": 3,
  "completion": 85,
  "cgpa": 4.2,
  "university": "Makerere University",
  "screeningAnswers": { "q1": "Yes", "q2": "3" }
}
```

### PUT /applications/:id/status
```json
{
  "status": "Shortlisted",
  "notifyEmail": "amara@example.com",
  "notifyMessage": "Congratulations! You have been shortlisted for interview."
}
```
Sends a real email if SMTP is configured, creates an in-app notification, and writes to audit log.

### PUT /applications/bulk-status
```json
{
  "updates": [
    { "id": 12, "status": "Shortlisted" },
    { "id": 15, "status": "Declined" }
  ]
}
```

### GET /applications/export
Returns a `text/csv` file attachment named `applications_<timestamp>.csv`.

---

## CV — `/api/cv`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/cv` | 🔑 | Get own CV profile |
| PUT | `/cv` | 🔑 | Save/update own CV (full upsert) |
| POST | `/cv/upload` | 🔑 | Upload file to Cloudinary |
| GET | `/cv/by-email/:email` | 👑 canViewApplications | Get CV of any candidate |

### POST /cv/upload
Content-Type: `multipart/form-data`
```
field: file   (the file binary)
field: type   "photo" or "document"
```
Response:
```json
{ "success": true, "data": { "url": "https://res.cloudinary.com/...", "publicId": "caa-recruitment/photos/...", "format": "webp", "bytes": 24832 } }
```

### PUT /cv (save full CV)
```json
{
  "personal": { "phone": "+256 700 000000", "address": "Kampala" },
  "highestLevel": "Degree",
  "qualifications": [{ "institution": "Makerere", "award": "BSc", "year": 2020, "cgpa": 4.2 }],
  "skills": ["Python", "Air Traffic Control", "Safety Management"],
  "experience": [{ "employer": "Uganda Airlines", "role": "Safety Officer", "from": "2020", "to": "2023" }],
  "referees": [{ "name": "Dr. Kiggundu", "title": "Director", "phone": "+256 711 000000" }],
  "nextOfKin": { "name": "Sarah Nakato", "relationship": "Sister", "phone": "+256 772 000000" },
  "photoFile": "https://res.cloudinary.com/..."
}
```

---

## Criteria — `/api/criteria`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/criteria/:jobId/public` | 🔓 | Candidate-safe subset — strips any `requirements` entry with `usage: 'criteriaOnly'` |
| GET | `/criteria/:jobId` | 👑 canManageCriteria | Full criteria for a job (admin) |
| PUT | `/criteria/:jobId` | 👑 canManageCriteria | Save criteria for a job |

The public endpoint is what `/apply` and `/job` actually call — there is no other route a candidate can hit to get a job's screening questions or requirements list. Before it existed, candidates never received screening questions at all, since the only route that returned them required an admin permission no candidate account could ever hold.

### PUT /criteria/:jobId
```json
{
  "minCgpa": 3.5,
  "requiredKeywords": ["aviation", "safety"],
  "notes": "Prefer candidates from CAA-approved training institutions",
  "disqualifyingUniversities": ["XYZ Diploma Mill"],
  "assessmentTypes": ["written", "interview"],
  "screeningQuestions": [
    {
      "id": "q1",
      "text": "Do you hold a valid medical certificate?",
      "kind": "yesno",
      "qualifyingAnswer": "Yes"
    },
    {
      "id": "q2",
      "text": "How many years of aviation experience do you have?",
      "kind": "number",
      "min": 2,
      "max": 30
    }
  ],
  "requirements": [
    {
      "id": "r1",
      "kind": "minAge",
      "label": "Must be at least 23 years old",
      "numberValue": 23,
      "usage": "disqualifier",
      "mandatory": true
    },
    {
      "id": "r2",
      "kind": "specificDegree",
      "label": "Bachelor's degree in a relevant field is an advantage",
      "textValue": "BSc Aeronautical Engineering",
      "usage": "criteriaOnly",
      "mandatory": false
    }
  ]
}
```
`requirements` is the structured qualifier/disqualifier builder from the job-creation form — `kind` is one of `minAge`/`maxAge`/`flyingHours`/`experienceYears`/`sex`/`qualificationLevel`/`specificDegree`/`oLevelSubject`/`aLevelSubject`/`custom`; `usage` controls whether it becomes a candidate-facing qualifier question, a disqualifier question, or stays silent/backend-only (`criteriaOnly`); `mandatory` distinguishes essential from desirable. It's additive to `requiredKeywords`/`disqualifyingUniversities`/manual `screeningQuestions`, not a replacement.

---

## Settings — `/api/settings`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/settings` | 🔑 | Get portal settings |
| PUT | `/settings` | 👑 canManageSettings | Update portal settings |

### PUT /settings
```json
{
  "minAgeThreshold": 21,
  "allowExternalInternalJobs": false,
  "orgName": "Uganda Civil Aviation Authority",
  "sessionTimeoutMinutes": 30,
  "emailSenderName": "CAA Recruitment",
  "closingSoonDays": 7,
  "maxApplicationsPerCandidate": 5,
  "notifTemplates": {
    "shortlist": "Congratulations! You have been shortlisted for {jobTitle}.",
    "decline": "Thank you for your interest in {jobTitle}. Unfortunately...",
    "interview": "You are invited for interview for {jobTitle} on {date}.",
    "offer": "We are pleased to offer you the position of {jobTitle}."
  }
}
```

---

## Permissions — `/api/permissions`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/permissions/roles/defaults` | 🔑 | `ROLE_DEFAULTS` for every admin role — the frontend fetches this rather than hardcoding its own copy |
| GET | `/permissions` | 👑 canGrantPermissions | List every admin's permission overrides |
| PUT | `/permissions` | 👑 canGrantPermissions | Upsert one admin's permission overrides (by `email`) |

The full permission key set: `canViewApplications`, `canShortlist`, `canScreenInterns`, `canSendNotifications`, `canManageJobs`, `canManageCriteria`, `canViewStaff`, `canExport`, `canViewAudit`, `canManageSettings`, `canGrantPermissions`, `canReviewJob`, `canApproveJob`, `canManageDepartments`, `canManageAdmins`, `canAssignRights`, `canScheduleAssessment`, `canRecordAssessment`.

---

## Job Templates — `/api/job-templates`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/job-templates` | 👑 canManageJobs | List templates |
| POST | `/job-templates` | 👑 canManageJobs | Save the current job-creation draft as a new template |
| DELETE | `/job-templates/:id` | 👑 canManageJobs | Delete a template |

### POST /job-templates
```json
{
  "name": "Technical / Engineering role",
  "departmentId": 4,
  "content": {
    "aboutRole": "...", "accountabilities": [...], "requirements": [...],
    "specialSkills": [...], "requiredQualification": "Degree"
  }
}
```
`content` is a free-form snapshot of whichever job-creation fields matter for reuse — the "start from a template" step loads it back into the draft for HR to edit/extend, it doesn't create a job directly. Seeded with 3 starter templates via `scripts/seed-job-templates.js` (technical/engineering, professional/administrative, graduate-entry/intern).

---

## Candidate Scores — `/api/candidate-scores`

Multi-admin panel scoring for the `Shortlisted II` stage.

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/candidate-scores?jobId=&status=` | 👑 canShortlist | Per-application summary: every scorer's row + a computed average |
| PUT | `/candidate-scores/:applicationId` | 👑 canShortlist | Upsert the calling admin's own score+comment for that application |

### PUT /candidate-scores/:applicationId
```json
{ "score": 88, "comment": "Strong ATC licence, weak on radar procedures" }
```
Each admin can only ever write their own row (`UNIQUE(application_id, scorer_user_id)`) — unlike `assessments` (below), simultaneous reviewers never overwrite each other. Every panelist's score+comment is visible to every other panelist, never to the candidate. "Auto-shortlist by score" (frontend-side, `PUT /applications/bulk-status`) advances candidates averaging at or above an HR-chosen threshold to `Interview` and declines the rest.

---

## Departments — `/api/departments`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/departments` | 🔑 | List departments |
| POST | `/departments` | 👑 canManageDepartments | Create a department |
| PUT | `/departments/:id` | 👑 canManageDepartments | Update a department (incl. `headUserId`) |

A job's `department_id` determines who its HOD-review step routes to — whichever user is `head_user_id` on that department.

---

## Assessments — `/api/assessments`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/assessments?...` | 👑 canExport | List/query assessment records |
| GET | `/assessments/:applicationId` | 👑 canViewApplications | Get all assessment-type rows for one application |
| PUT | `/assessments/:applicationId/:type` | 🔑 | Schedule (`scheduledAt`, `venue`) or record (`score`, `passed`, `notes`) a result for one type |

`type` is one of `written`/`psychometric`/`interview`/`practical`. `UNIQUE(application_id, type)` means only one row per type per candidate — last write wins. That's fine for a single official result, but it's why panel scoring at `Shortlisted II` uses the separate `candidate_scores` table instead: this one can't hold more than one reviewer's input.

---

## Notifications — `/api/notifications`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/notifications` | 🔑 | Get own notifications (latest 100) |
| PUT | `/notifications/:id/read` | 🔑 | Mark notification as read |

---

## Emails — `/api/emails`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/emails` | 👑 canViewApplications | List sent email log |
| POST | `/emails` | 👑 canSendNotifications | Send + log a single email |
| POST | `/emails/bulk` | 👑 canSendNotifications | Send + log bulk emails |
| DELETE | `/emails` | 👑 super only | Clear email log |

### POST /emails
```json
{
  "to": "amara@example.com",
  "candidateName": "Amara Nakato",
  "subject": "Interview Invitation — Aviation Safety Inspector",
  "body": "Dear Amara,\n\nYou are invited for interview on Monday 14 July at 10:00 AM...",
  "trigger": "interview_invite",
  "jobTitle": "Aviation Safety Inspector"
}
```

---

## Audit Log — `/api/audit`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/audit` | 👑 canViewAudit | Get audit log (latest 500) |

Query params: `?actor=John&action=login&from=2026-01-01&to=2026-12-31&limit=100`

---

## Analytics — `/api/analytics`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/analytics/event` | 🔓 | Track an event (public, rate limited) |
| GET | `/analytics` | 👑 canViewAudit | Get analytics summary |

### POST /analytics/event
```json
{
  "type": "job_view",
  "jobId": 3,
  "jobTitle": "Aviation Safety Inspector"
}
// type must be one of: page_view, job_view, apply_click, save_job, search
```

### GET /analytics
```
?days=30   (default 30, max 365)
```

---

## Staff — `/api/staff`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/staff` | 👑 canViewStaff | List all staff |
| POST | `/staff` | 👑 super | Add staff record |
| PUT | `/staff/:id` | 👑 super | Update staff record |
| DELETE | `/staff/:id` | 👑 super | Delete staff record |

---

## Chatbot (Martha) — `/api/chatbot`

The frontend chatbot ("Martha") logs typed questions so HR can see which topics
need new FAQ content. Chip clicks and small talk are not logged.

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/chatbot/queries` | 🔓 | Log a chatbot question (public — guests use Martha too) |
| GET | `/chatbot/queries` | 👑 canViewAudit | List logged questions (powers the Martha panel in Site Analytics) |

### POST /chatbot/queries
```json
{
  "query": "can i bring my drone to the airport",   // required, truncated to 500 chars
  "matchedQuestion": "What are the drone regulations in Uganda?",  // optional
  "outcome": "suggested",   // one of: answered | suggested | fallback
  "persona": "guest"        // guest | external | internal | recruiter | hr | super
}
```

`outcome` meanings: `answered` — Martha gave a confident answer; `suggested` —
weak match, she offered "did you mean?" candidates; `fallback` — she had nothing.

### GET /chatbot/queries
```
?outcome=fallback   (optional filter: answered | suggested | fallback)
&days=30            (default 30, max 365)
&limit=200          (default 200, max 1000)
```

---

## Rate Limits

| Limit | Applies to |
|-------|-----------|
| 10 requests / 15 minutes | POST /auth/login, POST /auth/register |
| 3 requests / hour | POST /auth/forgot-password |
| 300 requests / 15 minutes | All other /api/* routes |
