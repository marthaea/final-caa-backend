# CAA Recruitment Backend

Java 21 / Spring Boot 4 modular monolith for the CAA recruitment portal.
PostgreSQL is hosted separately; this service connects with `DB_URL`.

API contracts are frozen in `contracts/openapi.yaml`. MySQL → PostgreSQL
cutover tooling lives in `data-migration/`.

## Package layout

- `feature.identity` — auth, sessions, staff, admin users, permissions
- `feature.recruitment` — jobs, departments, criteria, templates, CVs,
  applications, scores, assessments
- `feature.support` — settings, notifications, emails, audit, analytics, chatbot
- `feature.system` — `/ping`
- `seed` — idempotent PostgreSQL demo/bootstrap seeder
- `shared.*` — web, security, persistence, integrations, observability

## Run

Requirements: JDK 21+ and a running PostgreSQL (separate host recommended).

```bash
cd ../final-caa-database && docker compose up -d
cd ../final-caa-backend && ./mvnw spring-boot:run
```

App: `http://localhost:8080`

## Database seed

Idempotent PostgreSQL seed so the frontend can drop hard-coded demo data.
Passwords are hashed with **BCrypt cost 12**. Demo passwords only load when
`SEED_DEMO=true`. Production profile seeding is refused unless
`SEED_ALLOW_PRODUCTION=true`.

Job `closesAt` values use **original date + 6 months**, floored so listings
stay publicly visible.

```bash
npm run seed:core      # departments, settings, admins, staff, 14 jobs
npm run seed:demo     # candidates, CVs, pinned applications, analytics
npm run seed:volume   # optional ~900 applications
npm run seed:all      # everything including volume
```

Equivalent: `./scripts/seed.sh {core|demo|volume|all}`

### Demo logins (dev only)

| Email | Password | Role |
| --- | --- | --- |
| admin@caa.go.ug | Admin@2026 | super |
| hrdirector@caa.go.ug | HrDir@2026 | hr |
| recruit@caa.go.ug | Recruit@2026 | recruiter |
| auditor@caa.go.ug | Auditor@2026 | auditor |
| hrofficer@caa.go.ug | HrOfficer@2026 | hr_officer |
| itadmin@caa.go.ug | ItAdmin@2026 | it_admin |
| dhra@caa.go.ug | Dhra@2026 | dhra |
| hod@caa.go.ug | Hod@2026 | hod |
| jbukenya@gmail.com | Candidate@2026 | external candidate |
| mauma@gmail.com | Candidate@2026 | external candidate |
| sarahnamutebi@caa.go.ug (and other staff) | Staff@2026 | internal |

RBAC defaults are code-backed in `RolePermissions` (`GET /api/permissions/roles/defaults`).

### Email (Brevo)

Transactional mail is sent by `OutboxEmailWorker` when `SMTP_ENABLED=true` (welcome,
password reset, application status, interview panel invites, etc.).

Production uses **Brevo SMTP**:

| Variable | Value |
| --- | --- |
| `SMTP_HOST` | `smtp-relay.brevo.com` |
| `SMTP_PORT` | `587` |
| `SMTP_USER` | Brevo SMTP login (shown when you create an SMTP key) |
| `SMTP_PASSWORD` | Brevo SMTP key (not your account password) |
| `SMTP_FROM` | Verified sender in Brevo (e.g. `noreply@yourdomain.com`) |
| `SMTP_SENDER_NAME` | Display name (defaults to `CAA HR Team`; HR can override in admin settings) |

After updating `/opt/caa-recruitment/.env`, run `docker compose up -d api`. Check the
admin **Email** panel or `GET /api/emails/status` (authenticated) for outbox pending/failed counts.

## Verify

```bash
curl -s http://localhost:8080/api/jobs | jq '.total'
curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@caa.go.ug","password":"Admin@2026"}'
```

## Schema

- `V1__baseline_schema.sql` / `V2__api_rate_limits.sql` under `src/main/resources/db/migration/`
