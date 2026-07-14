# Endpoint Testing Guide

This guide covers testing the API with **Thunder Client** (VS Code extension) and **Postman**. Both work the same way — examples use Thunder Client terminology.

---

## Setup

### Thunder Client (VS Code)
1. Open VS Code → Extensions (`Ctrl+Shift+X`)
2. Search for **Thunder Client** and install it
3. Click the thunder bolt icon in the left sidebar
4. Click **New Request**

### Postman
1. Download from [postman.com](https://www.postman.com/downloads/)
2. Create a new **Collection** called `CAA Recruitment API`
3. Add a **Collection Variable** `baseUrl` = `http://localhost:5000/api`
4. Add a **Collection Variable** `token` = (empty for now)

---

## Starting the Server

```bash
cd caa-recruitment-backend
npm run dev
```

You should see:
```
[2026-07-14T...] CAA Recruitment API running on port 5000
[2026-07-14T...] [cron] Scheduled: job-expiry (00:05), analytics-purge (00:10)
[startup] Swagger UI available at http://localhost:5000/api-docs
```

---

## Test 1 — Register a Candidate

**Method:** POST  
**URL:** `http://localhost:5000/api/auth/register`  
**Headers:** `Content-Type: application/json`  
**Body (JSON):**
```json
{
  "email": "test.candidate@gmail.com",
  "password": "TestPass1",
  "firstName": "Test",
  "lastName": "Candidate",
  "accountType": "external"
}
```

**Expected response (201):**
```json
{
  "success": true,
  "data": {
    "id": 1,
    "email": "test.candidate@gmail.com",
    "firstName": "Test",
    "lastName": "Candidate",
    "accountType": "external",
    "effectiveType": "external",
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  }
}
```

> **Save the token** — you'll use it for all authenticated requests.

---

## Test 2 — Login

**Method:** POST  
**URL:** `http://localhost:5000/api/auth/login`  
**Body (JSON):**
```json
{
  "email": "test.candidate@gmail.com",
  "password": "TestPass1"
}
```

**Expected response (200):** Same shape as register, with a fresh token.

> In Thunder Client: go to **Tests** tab → **Set Env Variable** → set `token` = `$.data.token`  
> In Postman: go to **Tests** tab → add: `pm.collectionVariables.set("token", pm.response.json().data.token);`

---

## Test 3 — Get Current User (authenticated)

**Method:** GET  
**URL:** `http://localhost:5000/api/auth/me`  
**Headers:**
```
Authorization: Bearer <paste token here>
```

**Expected response (200):**
```json
{
  "success": true,
  "data": {
    "id": 1,
    "email": "test.candidate@gmail.com",
    "firstName": "Test",
    "lastName": "Candidate",
    "accountType": "external",
    "effectiveType": "external",
    "adminRole": null
  }
}
```

---

## Test 4 — List Jobs (public)

**Method:** GET  
**URL:** `http://localhost:5000/api/jobs`  
**Headers:** none required

**Expected response (200):**
```json
{ "success": true, "data": [...], "total": 3 }
```

---

## Test 5 — Submit an Application

You need a valid `jobId` from Test 4.

**Method:** POST  
**URL:** `http://localhost:5000/api/applications`  
**Headers:** `Authorization: Bearer <token>`  
**Body (JSON):**
```json
{
  "jobId": 1,
  "completion": 90,
  "cgpa": 4.2,
  "university": "Makerere University",
  "screeningAnswers": {}
}
```

**Expected response (201):**
```json
{
  "success": true,
  "data": {
    "id": 1,
    "jobId": 1,
    "status": "Pending",
    "candidateName": "Test Candidate",
    ...
  }
}
```

---

## Test 6 — Upload a File (CV photo)

**Method:** POST  
**URL:** `http://localhost:5000/api/cv/upload`  
**Headers:** `Authorization: Bearer <token>`  
**Body:** Multipart Form  
- Field `file` → select any JPEG/PNG image from your computer
- Field `type` → `photo`

**Expected response (201):**
```json
{
  "success": true,
  "data": {
    "url": "https://res.cloudinary.com/your-cloud/image/upload/...",
    "publicId": "caa-recruitment/photos/...",
    "format": "webp",
    "bytes": 18432
  }
}
```

> **Note:** Requires `CLOUDINARY_*` variables set in `.env`. If not set, you'll get a 503 error.

---

## Test 7 — Refresh Token

After the 2-hour access token expires (or to test rotation):

**Method:** POST  
**URL:** `http://localhost:5000/api/auth/refresh-token`  
**Body:** none  
**Headers:** none (the `caa_refresh` cookie is sent automatically by the browser)

> **Thunder Client:** Cookie handling works if you're on the same origin. For isolated testing, first call `/login`, note the `Set-Cookie: caa_refresh=...` in the response headers, then paste it into the **Cookies** tab of the refresh request.

---

## Test 8 — Admin Login

First, check your database for a user with `account_type = 'admin'` (seeded by `seed.js`).

**Method:** POST  
**URL:** `http://localhost:5000/api/auth/login`  
**Body (JSON):**
```json
{
  "email": "admin@caa.go.ug",
  "password": "Admin1234!"
}
```

Save the admin token separately.

---

## Test 9 — Create a Job (admin)

**Method:** POST  
**URL:** `http://localhost:5000/api/jobs`  
**Headers:** `Authorization: Bearer <admin_token>`  
**Body (JSON):**
```json
{
  "title": "Air Traffic Controller",
  "dept": "Air Navigation Services",
  "deptKey": "ans",
  "location": "Entebbe International Airport",
  "salary": "UGX 4,000,000 – 5,500,000",
  "salaryBand": "UG5",
  "type": "Full-time",
  "closes": "31 Aug 2026",
  "closesAt": "2026-08-31",
  "visibility": "external",
  "minAge": 23,
  "requiredExperience": 2,
  "requiredQualification": "Degree",
  "description": "Responsible for safe and orderly flow of air traffic...",
  "featured": true
}
```

**Expected response (201):** The created job object.  
**If you get 403:** Your admin account doesn't have `canManageJobs` permission. Update `permission_overrides` in the database or use a `super` admin.

---

## Test 10 — Update Application Status

**Method:** PUT  
**URL:** `http://localhost:5000/api/applications/1/status`  
**Headers:** `Authorization: Bearer <admin_token>`  
**Body (JSON):**
```json
{
  "status": "Shortlisted",
  "notifyEmail": "test.candidate@gmail.com",
  "notifyMessage": "Congratulations! You have been shortlisted for the position of Air Traffic Controller. We will contact you with interview details shortly."
}
```

This will:
1. Update the application status in the database
2. Create an in-app notification for the candidate
3. Send a real email (if SMTP is configured)
4. Write to the audit log

---

## Test 11 — Export Applications as CSV

**Method:** GET  
**URL:** `http://localhost:5000/api/applications/export?status=Shortlisted`  
**Headers:** `Authorization: Bearer <admin_token>`

In Thunder Client/Postman: the response will be raw CSV text. Save it as a `.csv` file.

---

## Test 12 — Track an Analytics Event (public)

**Method:** POST  
**URL:** `http://localhost:5000/api/analytics/event`  
**Headers:** `Content-Type: application/json`  
**Body (JSON):**
```json
{
  "type": "job_view",
  "jobId": 1,
  "jobTitle": "Air Traffic Controller"
}
```

**Expected response (201):** `{ "success": true }`

---

## Validation Error Testing

### Test invalid registration (should return 400)
```json
{
  "email": "not-an-email",
  "password": "short",
  "firstName": "",
  "accountType": "unknown"
}
```

Expected:
```json
{
  "success": false,
  "error": "Validation failed",
  "errors": [
    { "field": "email", "message": "Invalid value" },
    { "field": "password", "message": "Password must be at least 8 characters" },
    ...
  ]
}
```

---

## Rate Limit Testing

Hit `POST /api/auth/login` 11 times in quick succession. On the 11th request you should get:

```json
{ "success": false, "error": "Too many login attempts. Please try again in 15 minutes." }
```

HTTP status: `429 Too Many Requests`

---

## Swagger UI (Interactive Testing)

Open your browser and go to: `http://localhost:5000/api-docs`

You'll see the branded Swagger interface with all 28 endpoint groups. You can:
1. Click any endpoint to expand it
2. Click **Try it out**
3. Fill in parameters
4. Click **Execute**

To authenticate in Swagger:
1. Click the green **Authorize** button (top right)
2. Enter your token in the field: `Bearer eyJ...`
3. Click **Authorize**

All requests will now include the Authorization header.

> Swagger UI is only available when `NODE_ENV=development`. It is automatically disabled in production.

---

## Common Errors and Fixes

| Error | Meaning | Fix |
|-------|---------|-----|
| `401 Unauthorized` | Missing or expired token | Login again and get a fresh token |
| `403 Forbidden` | Token valid but insufficient permissions | Use an admin account with the right role/permission |
| `409 Conflict` | Duplicate — email already registered, or already applied | Use a different email or check existing records |
| `429 Too Many Requests` | Rate limit hit | Wait 15 minutes (or restart server in dev) |
| `500 Internal Server Error` | Something broke server-side | Check the server console for the error details |
| `ECONNREFUSED` | Server not running | Run `npm run dev` first |
| `ER_ACCESS_DENIED_ERROR` | Wrong DB credentials | Check DB_USER / DB_PASSWORD in `.env` |
