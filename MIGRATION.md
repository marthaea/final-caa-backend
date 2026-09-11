# Java/PostgreSQL Migration Baseline

## Current status

API migration implementation is complete on `migration/java-postgresql`. The
old Node/Express/MySQL runtime has been removed from this repository. The Java
Spring Boot service at the repository root implements the frozen 66-handler
`/api` surface plus `GET /ping` against PostgreSQL.

Stack: Java 25, Spring Boot 4.1.1, Maven, PostgreSQL 18, Flyway, Spring
Security (JWT + refresh sessions), JdbcClient repositories, Actuator,
structured logging, Testcontainers, ArchUnit, Cloudinary, and SMTP outbox
delivery.

Verification on 2026-09-09:

- Node characterization/contract + migration toolkit: 21 passed.
- Full Java suite: **23 passed** against PostgreSQL 18 Testcontainers
  (identity, foundation, recruitment core, candidate workflows, support,
  architecture, and Cloudinary upload validation).
- Docker Compose configuration validation passed.
- Docker image `caa-recruitment-java:local` built successfully.
- Production MySQL → PostgreSQL rehearsal has **not** been executed.

## Audit summary

- Runtime: Node.js, Express 4, CommonJS, `mysql2`, JWT bearer access tokens, an HTTP-only refresh-token cookie, and Supertest/Node test runner.
- Surface: 66 handlers mounted under `/api` from 18 route modules, plus public `GET /ping`.
- Access model: public, optional bearer, bearer-authenticated, role-gated, and permission-gated operations coexist.
- Response model: most JSON endpoints use `{ success, data }`, list endpoints add `total`, and errors use `{ success: false, error }`; validation errors additionally expose `errors`.
- Non-JSON behavior: `GET /api/applications/export` returns a downloadable CSV.
- Stateful integrations: MySQL, SMTP, Cloudinary, and in-process rate limit/cache state.
- Contract source of truth: `contracts/openapi.yaml`; `tests/contracts.test.js` independently freezes the route inventory.

## Decisions

1. Treat observed Express behavior—not intended future Java design—as the migration contract.
2. Preserve camelCase API DTOs even where storage currently uses snake_case.
3. Preserve bearer-token and RBAC semantics, including the distinction between `Forbidden` and `Permission denied`.
4. Preserve the `caa_refresh` cookie name and its seven-day, HTTP-only rotation/clearing behavior.
5. Keep public and optional-auth routes public during migration.
6. Keep CSV headers, attachment disposition, and quoting behavior compatible.
7. Use strict queued DB stubs in Node tests so uncharacterized queries fail loudly.
8. Implement Java/PostgreSQL behind the frozen contract; intentional API changes require an explicit contract version/change record.
9. Use Spring Boot 4 technology starters (including `spring-boot-starter-flyway`) so modular auto-configuration is present.
10. Keep production data migration source-preserving: export and stage first, reconcile before canonical loading, and retain rollback evidence.
11. Shared PostgreSQL-backed API rate limits replace process-local Express limiters for multi-instance safety.
12. Where PostgreSQL check constraints reject partial Node writes (deployment pair, assessment result trio), return safe `400` instead of leaking constraint failures.

## Risks

- MySQL-specific SQL required semantic PostgreSQL rewrites (`ON CONFLICT`, `ILIKE`, `timestamptz`, JSONB).
- JSON columns may currently arrive as parsed arrays/objects depending on driver configuration; PostgreSQL JSONB mapping must retain DTO shapes.
- Boolean and date coercion differ across MySQL, PostgreSQL, JDBC, and Java serializers.
- Fire-and-forget audit/email paths can hide integration failures; Java persists through audit/outbox with best-effort semantics preserved for callers.
- Permission defaults can be overridden per email in the database; both lookup precedence and snake_case mapping are contractual.
- Route ordering currently prevents parameter routes from shadowing `/export` and `/bulk-status`.
- Upload behavior depends on multipart parsing and Cloudinary transformations.
- Existing status vocabularies are not fully uniform: the global status constants include values that application validators do not accept.
- Production hosting, secrets, and golden live-data parity remain external blockers.

## Phase checklist

### Phase 1 — baseline

- [x] Audit route modules, middleware, validators, DTO mappers, and response helpers.
- [x] Freeze all 66 `/api` handlers and `/ping` in machine-readable OpenAPI.
- [x] Record public, optional bearer, bearer, role, and permission access.
- [x] Record request bodies, enums, key DTO fields, statuses, cookie behavior, and CSV output.
- [x] Add database-free characterization and route-inventory tests.
- [x] Make unexpected test database queries fail loudly.
- [x] Run Node tests and local syntax/spec validation.

### Phase 2 — Java skeleton

- [x] Select Java 25, Spring Boot 4.1.1, Maven, and package namespace.
- [x] Create the parallel Spring Boot application and production/test configuration.
- [x] Add security, CORS, correlation IDs, safe exception mapping, observability, Docker, and CI foundations.
- [x] Reproduce the baseline response envelope and `/ping` contract.
- [x] Validate modular architecture rules.
- [x] Generate/implement controllers and DTOs from the frozen contract.
- [x] Reproduce response envelopes, validation errors, JWT, cookie, CORS, and exception mapping.
- [x] Add the baseline PostgreSQL Flyway migration without changing the Node runtime.

### Phase 3 — persistence and integrations

- [x] Add a source-preserving 17-table export, staging, manifest, and reconciliation toolkit.
- [x] Validate Flyway bootstrap and selected database integrity constraints with PostgreSQL Testcontainers.
- [x] Migrate registration, login, profile, verification, password recovery, and logout contracts.
- [x] Add BCrypt password hashing, signed access tokens, persistent hashed refresh sessions, rotation/replay prevention, and session revocation.
- [x] Migrate staff identity, admin-account management, role defaults, and database permission overrides.
- [x] Persist identity emails through the transactional outbox and security actions through audit records.
- [x] Migrate jobs, department administration, criteria, reusable templates, and the HOD/DHRA publication workflow.
- [x] Preserve public/optional-auth job visibility and candidate-safe public criteria responses.
- [x] Migrate CV profiles/upload, applications (including CSV export and auto-screening), candidate scores, and assessments.
- [x] Migrate settings, notifications, email history/outbox enqueue, audit, analytics, and chatbot APIs.
- [x] Port queries with explicit PostgreSQL semantics and transaction boundaries.
- [x] Port SMTP outbox worker, Cloudinary upload service, shared rate limits, and scheduled cleanup jobs.
- [x] Add Testcontainers-backed repository and integration tests.

### Phase 4 — parity and cutover

- [x] Confirm Java controllers cover the frozen OpenAPI/route inventory (66 `/api` + `/ping`).
- [x] Run Java regression suite against PostgreSQL Testcontainers.
- [x] Validate Docker Compose and container image build.
- [x] Remove the Node/Express runtime from this repository and promote Java to the root.
- [ ] Rehearse production MySQL export → PostgreSQL staging → reconcile → rollback evidence.
- [ ] Run live shadow/load/security gates against production-like data.
- [ ] Point production traffic at this Java service and decommission the old host.

## API coverage (Java)

| Domain | Handlers | Status |
| --- | ---: | --- |
| System | 1 | Done |
| Auth / identity | 10 | Done |
| Permissions | 3 | Done |
| Admin users | 2 | Done |
| Staff | 3 | Done |
| Jobs + workflow | 9 | Done |
| Departments | 3 | Done |
| Criteria | 3 | Done |
| Job templates | 3 | Done |
| CV (+ upload) | 4 | Done |
| Applications (+ export) | 7 | Done |
| Candidate scores | 2 | Done |
| Assessments | 3 | Done |
| Settings / notifications / emails / audit / analytics / chatbot | 14 | Done |
| **Total** | **67** | **Complete** |

## Current blockers

- Production PostgreSQL hosting and deployment constraints are not yet defined.
- Production migration rehearsal requires a fresh MySQL export and approved access; no production credentials are stored in this repository.
- No live-database golden dataset is defined for query/result parity.
- Live SMTP and Cloudinary credentials/sandboxes are not defined for end-to-end delivery verification (validation and configuration paths are covered).
- Ambiguous existing behavior (notably application status enum differences) needs a product decision before intentional cleanup.
