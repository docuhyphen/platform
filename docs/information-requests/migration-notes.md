# Information Requests migration notes

The Information Requests program owns Flyway migrations V76 to V150. The full per-migration record
(task, date, and what each one changes) is the migration ledger in
`plans/DOCUMENT-DRIVEN-INFORMATION-REQUESTS-IMPLEMENTATION-PLAN.md`. This file tells an operator how
to apply them and what Phase 12 changed.

## Applying

- The platform is in development and has no production users, so no migration keeps a
  compatibility path for an older application version. Deploy the application and its migrations
  together; never run an older image against a newer schema.
- `CleanSchemaMigrationContractTest` proves that the whole set applies to an empty PostgreSQL
  database in one pass, and that the final constraints refuse every shape they replaced.
- Never edit a migration once any database's Flyway history lists it. Add a new migration instead.

## Program ranges

| Range | Area |
|---|---|
| V76 to V84 | Fields: provenance, stable bindings, instants, value sets, canonical principals, revisions, and audit owner columns. V84's triggers, which filled audit owners for legacy writers, were removed by V149. |
| V85 to V102 | Subjects, Templates, requirement and evidence policies, Version capabilities, Blueprint Versions, command receipts, runtime persistence (V96, including the guard that a request pins a published Version held by its own owner), External Participants, delegated authority. |
| V103 to V131 | Requests, responses, evidence, access sessions, submissions, and supporting evidence links. |
| V132 to V148 | Lineage, review, accepted facts and business decisions, event consumption, clocks, notices, record preservation and disposal, privacy, author actions, reusable facts, recertification, and external sources. |
| V149 to V150 | Phase 12, below. |

## Phase 12

- `V149__audit_explicit_owner_writers.sql` drops the five V84 triggers (and their function) that
  filled `owner_type` and `owner_id` for an audit writer naming only an organization. Such a write
  is now refused, so every audit writer must name its owner.
- `V150__request_execution_quotas.sql`:
  - Renames `request_execution_grant.additional_recipient_cap` to `acting_party_cap`.
  - Adds `evidence_file_allowance` and `evidence_byte_allowance` (non-negative when present).
  - Changes the reservation usage kind to `ACTING_PARTY`: existing `ADDITIONAL_RECIPIENT` rows are
    updated in place, and the check admits only the new kind.
  - Adds the `request_execution_grant_frozen` trigger. It refuses any update that changes a frozen
    column, and it allows `revoked_at` and `revoked_reason` to be written once, never rewritten or
    cleared. Deleting a grant during record disposal is unaffected.
- Neither migration had been applied to the local development database when it was written.
  `quarkus:dev` applies them on its next reload.

## After upgrading

- Grants issued before V150 keep their old recipient cap value as `acting_party_cap` and have no
  evidence allowances, so their uploads are bounded by the platform ceiling alone. Grants issued
  afterwards freeze the plan's allowances while enforcement refuses.
- Read `GET /platform/information-request-health`. `RESERVATIONS_ABOVE_CAP` and
  `REQUESTS_WITHOUT_EXECUTION_GRANT` must read zero after any backfill or restore.
