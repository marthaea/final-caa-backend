# CAA Recruitment Portal — Project Overview

## What This Is

A production-grade REST API backend for the Uganda Civil Aviation Authority (CAA) e-Recruitment Portal. It serves a React (TanStack Start) frontend and handles ~5,000 real users.

## Technology Stack

| Layer | Technology |
|-------|-----------|
| Runtime | Node.js 20+ |
| Framework | Express 4.x |
| Database | MySQL 8 via mysql2 connection pool |
| Authentication | JWT (access token 2h + refresh token 7d httpOnly cookie) |
| File Storage | Cloudinary v2 (photos, CVs, documents) |
| Email | Nodemailer (SMTP — configurable provider) |
| Validation | express-validator |
| Rate Limiting | express-rate-limit |
| Logging | morgan |
| Compression | compression (gzip) |
| Security | helmet, bcrypt (cost 12), cors with credentials |
| Scheduled Jobs | node-cron |
| API Docs | swagger-ui-express (dev only) |

## Project Structure

```
caa-recruitment-backend/
├── index.js                    # App entry point, middleware stack, graceful shutdown
├── seed.js                     # Database seeder (run once to populate initial data)
├── package.json
├── .env                        # Your secrets — NEVER commit this
├── .env.example                # Safe template to share
│
├── config/
│   ├── db.js                   # MySQL connection pool
│   └── constants.js            # ROLE_DEFAULTS permission map
│
├── middleware/
│   ├── auth.js                 # verifyToken, optionalToken
│   ├── rbac.js                 # requireRole(...roles), requirePerm(permKey)
│   ├── rateLimiter.js          # authLimiter, forgotPasswordLimiter, generalLimiter
│   ├── validate.js             # express-validator error formatter
│   ├── requestId.js            # x-request-id header on every response
│   └── errorHandler.js        # Centralised error handler (no stack leaks in prod)
│
├── routes/
│   ├── index.js                # Mounts all 12 resource groups
│   ├── authRoutes.js           # /api/auth/*
│   ├── jobRoutes.js            # /api/jobs/*
│   ├── applicationRoutes.js    # /api/applications/*
│   ├── cvRoutes.js             # /api/cv/*
│   ├── criteriaRoutes.js       # /api/criteria/*
│   ├── settingsRoutes.js       # /api/settings
│   ├── permissionsRoutes.js    # /api/permissions/*
│   ├── notificationsRoutes.js  # /api/notifications/*
│   ├── emailRoutes.js          # /api/emails/*
│   ├── auditRoutes.js          # /api/audit
│   ├── analyticsRoutes.js      # /api/analytics/*
│   └── staffRoutes.js          # /api/staff/*
│
├── validators/
│   ├── authValidators.js
│   ├── jobValidators.js
│   ├── applicationValidators.js
│   └── commonValidators.js
│
├── utils/
│   ├── format.js               # ok(), fail(), okList(), toCamel(), logAudit(), checkPerm()
│   ├── jwt.js                  # signAccessToken/Refresh, verifyAccessToken/Refresh
│   ├── audit.js                # Structured audit logger (writes to audit_log table)
│   ├── mailer.js               # Nodemailer transport + HTML email templates
│   ├── cloudinary.js           # Cloudinary upload/delete + multer config
│   ├── cron.js                 # node-cron scheduled jobs
│   └── swagger.js              # OpenAPI 3.0 spec + swagger-ui-express setup
│
└── docs/                       # This folder
```

## Response Format

Every response follows this contract:

```json
// Success (single object)
{ "success": true, "data": { ... } }

// Success (list)
{ "success": true, "data": [ ... ], "total": 42 }

// Error
{ "success": false, "error": "Human-readable message" }

// Validation error
{ "success": false, "error": "Validation failed", "errors": [{ "field": "email", "message": "Invalid email" }] }
```

## Authentication Flow

```
1. POST /api/auth/login
   → Body: { email, password }
   ← Body: { success, data: { token, ...userInfo } }   ← store token in memory/localStorage
   ← Cookie: caa_refresh (httpOnly, 7d)               ← browser stores automatically

2. Every authenticated request:
   → Header: Authorization: Bearer <token>

3. When token expires (2h):
   POST /api/auth/refresh-token  (no body — cookie sent automatically)
   ← Body: { success, data: { token } }               ← new access token
   ← Cookie: caa_refresh (rotated)                    ← new refresh cookie

4. Logout:
   POST /api/auth/logout
   ← Cookie cleared
```

## RBAC (Role-Based Access Control)

Three account types:
- `external` — job applicants (public candidates)
- `internal` — CAA staff (see internal-only jobs)
- `admin` — has an `adminRole` of `super`, `hr`, or `recruiter`

Admin permissions are controlled by the `permission_overrides` table (per-admin) falling back to `ROLE_DEFAULTS` in `config/constants.js`.

## Quick Start

```bash
# 1. Install dependencies
npm install

# 2. Copy env template and fill in values
cp .env.example .env

# 3. Create MySQL database
# (via phpMyAdmin: create database named caa-recruit)

# 4. Run the CREATE TABLE statements from docs/04-database-schema.md

# 5. Seed initial data
node seed.js

# 6. Start development server
npm run dev
```

API will be available at `http://localhost:5000/api`
Swagger docs at `http://localhost:5000/api-docs` (development only)
