# Cutover and rollback runbook

## Roles and evidence

Assign named owners for incident command, MySQL export, PostgreSQL migration,
application deployment, validation, and rollback. Record timestamps in UTC.
Store approvals, tool versions, preflight output, `manifest.json`, checksum
verification output, reconciliation output, and change-ticket ID in an
access-controlled evidence location. Never paste row data or credentials into
the ticket or logs.

## Entry criteria

- Approved canonical PostgreSQL DDL exists and a staging-to-canonical
  transformation has been reviewed and tested with synthetic data.
- A fresh production export was produced from one read-only consistent MySQL
  snapshot in UTC; preflight has no unexplained issues.
- Manifest verification succeeds independently on the transfer source and
  destination.
- PostgreSQL backup/PITR is healthy and a restore test is current.
- Application release and configuration have passed non-production testing.
- Maintenance window, user notice, rollback owner, decision deadline, and
  acceptable outage/data-loss objectives are approved.
- Old MySQL remains intact and reachable for the full rollback window.

## Rehearsal

1. Use a newly sanitized or access-controlled production export, never the
   repository schema-only reference.
2. Verify checksums, import to `migration_stage`, and save staging reconciliation.
3. Apply the separately reviewed canonical transformation in one transaction.
4. Reconcile canonical counts, PK ranges, sequence values, FK anti-joins, domain
   distributions, and validation counts.
5. Run application smoke tests with notifications, scheduled jobs, and outbound
   email disabled.
6. Measure duration and update the cutover timeline.

No rehearsal has been performed by this toolkit implementation.

## Cutover

1. Announce start and freeze administrative/data-changing workflows.
2. Stop application writers and background jobs. Confirm MySQL active write
   sessions have drained; do not infer this merely from the UI being unavailable.
3. Record the freeze timestamp in UTC.
4. Produce a **new** consistent export and run MySQL preflight. Do not reuse the
   rehearsal export.
5. Transfer over an authenticated encrypted channel. Verify `manifest.json`
   checksums at both ends.
6. Import to `migration_stage`; run staging reconciliation. Stop on any checksum
   mismatch, count mismatch, orphan, duplicate, invalid domain, or invalid JSON
   without a signed exception.
7. Take/confirm the PostgreSQL restore point.
8. Apply the approved staging-to-canonical transformation in one transaction.
   Set each identity/sequence to at least the canonical maximum ID before commit.
9. Run `postgres/reconcile-canonical.sql` and compare enum/domain distributions
   to MySQL preflight. Require zero unexplained differences and sequence values
   not below maximum IDs.
10. Deploy application configuration using the secret manager; do not place a
    password-bearing URL in shell history, process arguments, source control, or
    logs.
11. Start one application instance with background jobs and outbound messaging
    disabled. Run read and write smoke tests using designated non-PII test
    records, then remove those records through the approved application path.
12. Gradually restore traffic. Enable workers and outbound messaging only after
    duplicate-delivery protections are confirmed.
13. Observe database errors, authentication, key workflows, job queues, and
    latency through the agreed stabilization period. Keep MySQL read-only and
    unchanged.

## Abort before traffic switch

If a gate fails before PostgreSQL receives production traffic:

1. Roll back the open PostgreSQL transaction.
2. Keep or drop only `migration_stage` according to evidence-retention policy.
3. Restart the old application against unchanged MySQL.
4. Re-enable writers/jobs, announce abort, and preserve aggregate diagnostics.

## Rollback after traffic switch

Rollback is a routing/application rollback, not a reverse data migration.
Post-cutover writes in PostgreSQL make rollback lossy unless a separately
approved, tested forward-sync process exists.

1. Incident commander stops PostgreSQL application writers and workers.
2. Record the stop time and last confirmed successful operation.
3. Decide explicitly whether post-cutover writes can be discarded. If not, stop:
   restoration requires an approved reconciliation/forward-fix plan.
4. Redeploy the prior application configuration and route traffic to the
   unchanged MySQL database.
5. Re-enable workers carefully to avoid duplicate email or scheduled actions.
6. Validate critical reads/authentication and designated test writes.
7. Preserve PostgreSQL and its logs for investigation; do not drop canonical
   data or overwrite the MySQL source.
8. Announce rollback and open follow-up reconciliation work.

## Completion

After the stabilization and rollback windows close, obtain owner sign-off,
rotate temporary credentials, revoke migration roles, and securely destroy
exports/staging according to retention policy. Retain only approved aggregate
evidence and checksums.
