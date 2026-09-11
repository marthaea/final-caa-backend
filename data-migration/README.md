# Production data migration toolkit

This directory contains source-preserving tooling for the 17 tables described by
`database/railway-backup-2026-08-13.sql`. It does not contain production data,
credentials, or a claim that a production rehearsal has run.

## Safety model

- The MySQL exporter starts one `REPEATABLE READ`, `READ ONLY`, consistent
  transaction, sets the session to UTC, and streams every table in deterministic
  primary-key order. It never writes to MySQL.
- Exported values are RFC 4180 CSV. `NULL` is represented by an accompanying
  null bitmap column (`__nulls`) so SQL `NULL`, an empty string, and the text
  `NULL` remain distinct.
- PostgreSQL import writes only to `migration_stage`, whose columns are text.
  It never disables constraints and never writes to canonical application tables.
- Checksums and row counts are verified before import. Staging is reconciled
  before any separately reviewed canonical transformation/cutover.
- Run all steps from a locked-down operator host. Export directories can contain
  PII and must be encrypted at rest, mode `0700`, access logged, and securely
  destroyed after the retention period.

## Prerequisites

- Node.js and the repository's installed `mysql2` dependency.
- MySQL 8+ account with only `SELECT` and metadata access.
- PostgreSQL `psql` client and a target role restricted to
  `migration_stage` during staging.
- GNU/Linux permissions semantics.

## 1. Fresh consistent production export

Create a MySQL option file outside the repository and restrict it:

```ini
[client]
host=production.example.invalid
port=3306
user=migration_reader
password=REDACTED
database=railway
ssl-mode=VERIFY_IDENTITY
ssl-ca=/secure/path/mysql-ca.pem
```

```bash
install -m 600 /dev/null /secure/path/mysql-client.cnf
${EDITOR} /secure/path/mysql-client.cnf
install -d -m 700 /secure/export/caa-$(date -u +%Y%m%dT%H%M%SZ)

# Data artifact and manifest, all from one read-only consistent snapshot.
node data-migration/bin/export-mysql.mjs \
  --config /secure/path/mysql-client.cnf \
  --output /secure/export/caa-TIMESTAMP

# Schema artifact: password comes from the option file, never argv. Run this
# after the exporter because the exporter deliberately requires an empty output.
mysqldump \
  --defaults-extra-file=/secure/path/mysql-client.cnf \
  --single-transaction --quick --skip-lock-tables --tz-utc \
  --no-data --routines=false --events=false --triggers \
  --set-gtid-purged=OFF --result-file=/secure/export/caa-TIMESTAMP/schema.sql

(cd /secure/export/caa-TIMESTAMP &&
  sha256sum schema.sql > schema.sql.sha256 &&
  sha256sum --check schema.sql.sha256)
```

`--defaults-extra-file` must immediately follow `mysqldump`. Do not use a MySQL
URI or `--password=...`. The exporter refuses a config file readable by group or
others, a non-empty output directory, unexpected schema columns, and non-InnoDB
tables (a consistent transaction cannot snapshot non-transactional tables).

Run preflight in a separate read-only session and spool its result to the same
encrypted evidence location:

```bash
mysql --defaults-extra-file=/secure/path/mysql-client.cnf \
  --init-command="SET SESSION time_zone='+00:00'; SET SESSION TRANSACTION READ ONLY" \
  --table < data-migration/mysql/preflight.sql \
  > /secure/export/caa-TIMESTAMP/mysql-preflight.txt
```

The preflight is diagnostic. Any result row with a non-zero issue count must be
resolved or accepted in a signed exception before cutover.

## 2. Verify and stage in PostgreSQL

Keep PostgreSQL connection details in a service file and password file:

```ini
# /secure/path/pg_service.conf
[caa_migration]
host=target.example.invalid
port=5432
dbname=caa
user=caa_migration_stage
sslmode=verify-full
sslrootcert=/secure/path/postgres-ca.pem
```

```text
# /secure/path/pgpass (mode 0600)
target.example.invalid:5432:caa:caa_migration_stage:REDACTED
```

```bash
node data-migration/bin/manifest.mjs verify /secure/export/caa-TIMESTAMP
(cd /secure/export/caa-TIMESTAMP && sha256sum --check schema.sql.sha256)

PGSERVICE=caa_migration \
PGSERVICEFILE=/secure/path/pg_service.conf \
PGPASSFILE=/secure/path/pgpass \
  bash data-migration/bin/import-postgres.sh /secure/export/caa-TIMESTAMP
```

The import script verifies the manifest again, recreates only
`migration_stage`, imports through `\copy` over the authenticated client
connection, and commits atomically. It rejects unsafe paths and permissive
credential files.

## 3. Reconcile

After staging:

```bash
PGSERVICE=caa_migration PGSERVICEFILE=/secure/path/pg_service.conf \
PGPASSFILE=/secure/path/pgpass \
psql -X -v ON_ERROR_STOP=1 -f data-migration/postgres/reconcile-staging.sql
```

`reconcile-staging.sql` checks staged counts, PK ranges, duplicate keys, logical
FK anti-joins, domains, booleans, dates, numbers, and JSON. All issue counts must
be zero unless formally waived.

The branch does not yet contain canonical PostgreSQL DDL. Therefore this toolkit
does **not** guess enum names, destination constraints, or transformation rules,
and does not load `public.*`. Once canonical DDL is approved, add a reviewed,
transactional staging-to-canonical transformation and run
`postgres/reconcile-canonical.sql` for counts, PK ranges, sequences, FK
anti-joins, domain distributions, and canonical validation.

## 4. Cutover and rollback

Follow `CUTOVER.md`. Keep the MySQL source unchanged and available throughout
the rollback window. A rollback switches application traffic back; it does not
attempt a destructive reverse migration.

## Tests

```bash
node --test data-migration/test/*.test.mjs
```
