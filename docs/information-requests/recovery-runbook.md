# Information Requests recovery runbook

This runbook covers detecting and recovering from failures that affect Information Requests. Every
statement about infrastructure was checked against `infra/cloudformation.yml` on 2026-09-30. It uses
only the services that template already declares; nothing here adds an AWS service.

## What protects the data

| Data | Where it lives | Protection declared in `infra/cloudformation.yml` |
|---|---|---|
| Requests, grants, reservations, answers, reviews, decisions, clocks, notices, holds, disposals, exports | RDS PostgreSQL `RDSInstance` | Automated backups kept 7 days (`BackupRetentionPeriod: 7`), so any point in the last 7 days can be restored. A final snapshot is taken if the instance is deleted or replaced (`DeletionPolicy: Snapshot`, `UpdateReplacePolicy: Snapshot`). `DeletionProtection: true`, `StorageEncrypted: true`. Single Availability Zone (`MultiAZ: false`). |
| Evidence file content | S3 documents bucket (`...-documents-prod`) | Versioning enabled, so an overwritten or deleted object keeps earlier versions. Retained if the stack is deleted (`DeletionPolicy: Retain`). The lifecycle rule only aborts incomplete multipart uploads after 1 day. |
| Signed audit ledger segments | S3 audit archive bucket (`...-audit-archive-prod`) | Versioning and Object Lock in `GOVERNANCE` mode for `AuditArchiveRetentionDays` (default 2555 days). The application role can write and read but cannot delete or change retention. |
| Application logs, including the health and abuse markers | CloudWatch log group `/ecs/<AppName>` | Kept 30 days (`RetentionInDays: 30`). |
| Credentials | Secrets Manager | Retained if the stack is deleted. |

Known limits of the current template, which need the user's approval to change because they add
cost: a single-AZ database; 7 days of point-in-time recovery; no cross-Region copy of backups or
buckets.

## Detecting trouble

- `GET /platform/information-request-health` (platform administrators only; every read is audited
  as `platform.information_request_health.view`) counts six invariant breaches:
  - `REQUESTS_WITHOUT_EXECUTION_GRANT`
  - `RESERVATIONS_ABOVE_CAP`
  - `NOTICE_INTENTS_OVERDUE`
  - `CONNECTOR_EXCHANGES_FAILED`
  - `DISPOSAL_CLAIMS_STALLED`
  - `EVENT_DELIVERY_BACKLOG`
- A scheduled check (every 15 minutes, `app.information-request.health.every`) logs
  `INFORMATION_REQUEST_HEALTH_BREACH indicator=<key> count=<n>` for each breach.
  `InformationRequestHealthBreachAlarm` fires on any breach in a 15-minute period.
- Abuse controls log `INFORMATION_REQUEST_ABUSE_REFUSED control=<control>`.
  `InformationRequestAbuseRefusalAlarm` fires at 50 refusals in 5 minutes. That usually means
  probing of contact codes or scripted exports, not a platform failure.

## Procedures

### A worker stopped

Symptoms: `NOTICE_INTENTS_OVERDUE`, `EVENT_DELIVERY_BACKLOG`, or `CONNECTOR_EXCHANGES_FAILED` stays
above zero.

1. Check the ECS service's running task count and the log group for exceptions from the notice,
   clock, connector, or outbox workers.
2. Restart the service (force a new deployment of the same image). Every worker resumes from the
   database, and notices and events are written once and retried idempotently.
3. Read the health report again. A connector exchange that exhausted its attempts stays failed by
   design. To try again, start a new exchange with `POST /information-requests/{id}/connector-exchanges`;
   external sources have no screen yet.

### A disposal stalled

Symptom: `DISPOSAL_CLAIMS_STALLED`.

1. A claim that deleted some objects but not all is finished by the disposal worker on its next run
   (hourly by default, `app.record-disposal.every`).
   Object deletion removes every S3 version and delete marker of a record's objects, so a retry is
   safe.
2. If the claim stays stalled, read the claim's objects and error in the record preservation page
   and the logs. Do not delete rows by hand: disposal writes a tombstone and audit events that the
   retention record depends on.

### The database must be restored to an earlier point

Use this after data corruption or a destructive mistake. Everything after the restore point is lost
from the database, so reconcile it afterwards.

1. Stop the application service so nothing writes to either database.
2. Restore `RDSInstance` to a new instance at the chosen time, within the last 7 days.
3. Point the application's database secret at the restored instance. Start one task and let Flyway
   validate: the history must match the migrations in the deployed image.
4. Reconcile work done after the restore point:
   - Disposals: records disposed after the restore point come back in the database, but their
     objects are gone, because disposal deletes every S3 version. Find the disposal audit events
     after the restore point in the audit archive, and dispose of those records again through
     record preservation, so the database matches the object store.
   - Uploads: evidence uploaded after the restore point has objects in S3 but no rows. Those objects
     are unreferenced. Keep them until the affected parties have uploaded again, then remove them
     under the normal object retention review.
   - Grants: a request issued after the restore point is a draft again, and its owner issues it
     again. `REQUESTS_WITHOUT_EXECUTION_GRANT` must read zero.
5. Read the health report. Every indicator must be zero before the service is scaled back up.

### An evidence object was deleted or overwritten by mistake

The documents bucket is versioned. Restore the previous version of the object key named in the
document version's storage locator, unless disposal deleted it; disposal removes every version on
purpose. The document version's recorded content hash confirms the restored bytes.

### A suspension or revocation was applied by mistake

- Operational suspension is the owner's `SUSPENDED` subscription status. Setting it back to its
  previous status resumes changes at once. Nothing was hidden while it applied.
- An execution grant revocation is written once and the database refuses to rewrite it (the
  `request_execution_grant_frozen` trigger). A revoked request cannot resume. Its owner creates a
  replacement request, for example by superseding it, and the revoked request's records stay
  readable and exportable.

## What cannot be recovered

- Records and objects removed by disposal. Disposal is the intended, audited end of a record.
- Log lines older than 30 days.
- Database states older than 7 days, apart from final snapshots taken when an instance was deleted
  or replaced.
