# Information Requests migration notes

The Information Requests program owns Flyway migrations V76 to V150 and V152 to V191. V151 belongs to the
signup and sign-in security remediation. The full per-migration record (task, date, and what each
one changes) is the migration ledger in
`plans/DOCUMENT-DRIVEN-INFORMATION-REQUESTS-IMPLEMENTATION-PLAN.md`. This file tells an operator how
to apply them and what Phases 12, 13, and subsequent gap fixes changed.

## Applying

- The platform is in development and has no production users, so no migration keeps a
  compatibility path for an older application version. Deploy the application and its migrations
  together; never run an older image against a newer schema.
- `CleanSchemaMigrationContractTest` proves that the whole set applies to an empty PostgreSQL
  database in one pass, and that the final constraints refuse every shape they replaced.
- Never edit a migration once any database's Flyway history lists it. Add a new migration instead.

## Program ranges

| Range        | Area                                                                                                                                                                                                                                                                          |
|--------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| V76 to V84   | Fields: provenance, stable bindings, instants, value sets, canonical principals, revisions, and audit owner columns. V84's triggers, which filled audit owners for legacy writers, were removed by V149.                                                                      |
| V85 to V102  | Subjects, Templates, requirement and evidence policies, Version capabilities, Blueprint Versions, command receipts, runtime persistence (V96, including the guard that a request pins a published Version held by its own owner), External Participants, delegated authority. |
| V103 to V131 | Requests, responses, evidence, access sessions, submissions, and supporting evidence links.                                                                                                                                                                                   |
| V132 to V148 | Lineage, review, accepted facts and business decisions, event consumption, clocks, notices, record preservation and disposal, privacy, author actions, reusable facts, recertification, and external sources.                                                                 |
| V149 to V150 | Phase 12, below.                                                                                                                                                                                                                                                              |
| V152         | Phase 13, below.                                                                                                                                                                                                                                                              |
| V153         | Delegated authority history, below.                                                                                                                                                                                                                                           |
| V154         | Explicit access-link command history, below.                                                                                                                                                                                                                                  |
| V155         | Automatic access session cutoff history, below.                                                                                                                                                                                                                               |
| V156         | Contact proof, session issuance, and account link history, below.                                                                                                                                                                                                             |
| V157         | Linked account Shares on parties, below.                                                                                                                                                                                                                                      |
| V158         | Access session step-up, below.                                                                                                                                                                                                                                                |
| V159         | Template principal attribution, below.                                                                                                                                                                                                                                        |
| V160         | Request and party principal attribution, below.                                                                                                                                                                                                                               |
| V161         | Access link principal attribution, below.                                                                                                                                                                                                                                     |
| V162         | Access link state, below.                                                                                                                                                                                                                                                     |
| V163         | Privacy deletion outcomes, below.                                                                                                                                                                                                                                             |
| V164         | Response archive and condition effects, below.                                                                                                                                                                                                                                |
| V165         | Evidence sources and frozen supporting links, below.                                                                                                                                                                                                                          |
| V166         | Lifecycle records, below.                                                                                                                                                                                                                                                     |
| V167         | Bound Field values, below.                                                                                                                                                                                                                                                    |
| V168         | Template draft revisions and requirement intervals, below.                                                                                                                                                                                                                    |
| V169         | Retained owner reads, below.                                                                                                                                                                                                                                                  |
| V170         | Domain event owners and access session credentials, below.                                                                                                                                                                                                                    |
| V171         | Transition idempotency keys and receipts, below.                                                                                                                                                                                                                              |
| V172         | Removed requirement revisions, below.                                                                                                                                                                                                                                         |
| V173         | Refresh rule retry state, below.                                                                                                                                                                                                                                              |
| V174         | Disposal reference lifetime, below.                                                                                                                                                                                                                                           |
| V175         | Active party assignments, below.                                                                                                                                                                                                                                              |
| V176         | Request records under disposal, below.                                                                                                                                                                                                                                        |
| V177         | Document version storage cleanup, below.                                                                                                                                                                                                                                      |
| V178         | Archived files of conditional answers, below.                                                                                                                                                                                                                                 |
| V179         | Imported value Requirement revisions, below.                                                                                                                                                                                                                                  |
| V180         | Connector exchange history, below.                                                                                                                                                                                                                                            |
| V181         | Group notice recipients, below.                                                                                                                                                                                                                                               |
| V182         | Notice render failures, below.                                                                                                                                                                                                                                                |
| V183         | Superseding follow-ups under disposal, below.                                                                                                                                                                                                                                 |
| V184         | Account links of disposed requests, below.                                                                                                                                                                                                                                    |

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

## Phase 13

- `V152__information_request_placeholder_blueprint_source.sql` drops
  `information_request_document_placeholder_default_fkey` and the
  `source_blueprint_document_default_id` column. A request's document placeholder is a full
  snapshot, so it no longer names the Blueprint document default it was copied from. Saving a
  Blueprint after a request was created from it no longer fails on that foreign key.
- The local development database was at V151 when V152 was written. `quarkus:dev` applies it on
  its next reload.

## Delegated authority history

- `V153__information_request_delegated_authority_history.sql` adds `GRANT_DELEGATED_AUTHORITY`
  and `REVOKE_DELEGATED_AUTHORITY` to the transition mutation constraint. Existing history and
  its append-only guards are preserved. Apply this migration with the authority audit change;
  grants and revocations now append request history and atomically capture audit and domain events.

## Explicit access-link command history

- `V154__information_request_access_link_history.sql` adds `ISSUE_ACCESS_LINK`,
  `ROTATE_ACCESS_LINK`, `REPLACE_ACCESS_LINK`, and `REVOKE_ACCESS_LINK` to the transition mutation
  constraint. Existing history and its append-only guards remain intact. Apply it with the
  access-link recording change so explicit credential commands capture request history, audit,
  and domain events atomically. Clean-schema and populated V153 upgrade contracts cover this
  change.

## Automatic access session cutoff history

- `V155__information_request_access_session_history.sql` adds `REVOKE_ACCESS_SESSION` to the
  transition mutation constraint. Existing history and its append-only guards remain intact. Apply
  it with the automatic cutoff recording change: party revocation, reassignment, and member-removal
  revocation record each access link they cut off, and parent Exchange rejection, rescission, and
  deletion record each live access session they end. Clean-schema and populated V154 upgrade
  contracts cover this change.

## Contact proof, session issuance, and account link history

- `V156__information_request_access_credential_history.sql` adds `ISSUE_CONTACT_CHALLENGE`,
  `REFUSE_CONTACT_CHALLENGE`, `ISSUE_ACCESS_SESSION`, and `LINK_PARTICIPANT_ACCOUNT` to the
  transition mutation constraint. Existing history and its append-only guards remain intact. Apply
  it with the contact proof and account upgrade recording change. Clean-schema and populated V155
  upgrade contracts cover this change.

## Linked account Shares on parties

- `V157__information_request_party_linked_user_share.sql` adds the nullable
  `information_request_party.linked_user_share_id`, a foreign key to `share`, a check that only a
  Participant-held party can carry it, and a partial index by request. Existing parties keep a null
  value; there is no backfill. A party upgraded before V157 has no stored link, so revoking it
  leaves that account's Share active until it is revoked directly. Apply it with the account Share
  revocation change. Clean-schema and populated V156 upgrade contracts cover this change.

## Access session step-up

- `V158__request_access_session_step_up.sql` adds to `request_access_session` the pending step-up
  code (`step_up_code_hash` and `step_up_code_expires_at`, set together), the unused code count, the
  failed attempt count, `step_up_locked_until`, and `stepped_up_at`, with checks that the counts are
  not negative. It also adds `STEP_UP_ACCESS_SESSION` to the transition mutation check. Existing
  sessions start with no pending code and no step-up; their verification window counts from when they
  were issued. Apply it with the access session step-up change. Clean-schema and populated V157
  upgrade contracts cover this change.

## Template principal attribution

- `V159__information_request_template_principal_attribution.sql` replaces Template Definition
  creation and Template Version creation, publication, and retirement attribution with canonical
  principal kind and ID pairs. Recorded App User IDs become `USER` principals; missing attribution
  stays absent without inventing an actor. Both parts of each pair must be stated together, and
  the kind must belong to the canonical principal vocabulary.
- The same migration drops all four replaced App User-only columns and updates the Version freeze
  guard. Publication and retirement preserve creator and publisher provenance, publication
  completeness checks, and authorized disposal. Apply it with the Template attribution change.
  Clean-schema and populated V158 upgrade contracts cover this change.

## Request and party principal attribution

- `V160__information_request_runtime_principal_attribution.sql` replaces
  `information_request.created_by_app_user_id` and `information_request_party.assigned_by_app_user_id`
  with canonical principal kind and ID pairs. Recorded App User IDs become `USER` principals. A
  request with no recorded App User takes the actor of its draft creation in request history, and
  a party takes the actor of its latest assignment or reassignment in request history; anything
  else stays absent. Both parts of each pair must be stated together, and the kind must belong to
  the canonical principal vocabulary. The replaced columns are dropped in the same migration.
- Record exports now state `createdByPrincipalKind` and `createdByPrincipalId` for the request and
  `assignedByPrincipalKind` and `assignedByPrincipalId` for each party, under record schema version
    3. Clean-schema and populated V159 upgrade contracts cover this change.

## Access link principal attribution

- `V161__share_link_principal_attribution.sql` replaces `share_link.created_by_app_user_id` with a
  canonical principal kind and ID pair. Recorded App User IDs become `USER` principals; links with
  no recorded creator keep none. Both parts must be stated together, and the kind must belong to
  the canonical principal vocabulary. Clean-schema and populated V160 upgrade contracts cover this
  change.

## Access link state

- `V162__share_link_bootstrap_state.sql` drops the unused `share_link.password_hash` and
  `domain_allowlist` columns and adds `verification_strength`, `last_used_at`, and `revoked_at`.
  Existing verification bootstrap links are set to `EMAIL_OTP`; a bootstrap link must state its
  verification strength, `EMAIL_OTP` is the only accepted value, and a bootstrap link cannot require
  account MFA. Clean-schema and populated V161 upgrade contracts cover this change.

## Privacy deletion outcomes

- `V163__information_request_privacy_target_skipped_open.sql` widens the privacy target outcome
  check with `SKIPPED_OPEN`, recorded for a request a deletion leaves alone because it is still
  open. Existing outcomes are unchanged. Clean-schema and populated V162 upgrade contracts cover
  this change.

## Response archive and condition effects

- `V164__information_request_response_archive_and_condition_effect.sql` adds the append-only
  `information_request_response_archive` (the copy of an answer a condition hid under the archive
  policy) and `information_request_response_condition_effect` (one row for each hide, clear,
  archive, or reactivation, with its rule, expression version, evaluation state, and policy). Their
  checks tie the effect to the policy and state. Foreign keys cascade on request disposal, so the
  disposal function is unchanged. Clean-schema, disposal, and populated V162 upgrade contracts cover
  this change.

## Evidence sources and frozen supporting links

- `V165__information_request_evidence_source_and_supporting_link_scope.sql` drops the unused
  `external_reference_type` and `external_reference_value` columns, makes `document_version_id`
  required, and limits `source_kind` to `DOCUMENT_VERSION`, so every evidence version names exactly
  one uploaded Document Version and a declared file name. No release wrote external-reference
  evidence; a database holding such rows written outside the application stops at this migration.
  It also keys each frozen submission supporting link to its own request and to the exact pair of
  Requirements its supporting evidence link names. Clean-schema and populated V162 upgrade
  contracts cover this change.

## Lifecycle records

- `V166__information_request_lifecycle_records.sql` adds:
    - `exchange_ending_intent`, which keeps the cancel choice of an Exchange ending that waits for a
      Workflow until the Workflow confirms it;
    - `information_request_imported_value_reconciliation` and its ordered items (append-only), so a
      retried reconciliation returns its stored outcomes;
    - `information_request_lineage_source_package` (append-only, backfilled from each lineage's
      source package), recording every package a follow-up preserves; the carry-forward guard now
      accepts an item of any preserved package;
    - execution grant `policy_version`, `entitlement_source` (`PAID`, `TRIAL`, `OVERRIDE`, or
      `UNENFORCED`), `trial_grant_id`, `granted_features`, and `continuation_actions`, backfilled for
      existing grants and frozen with the rest of the grant.
      It restates `record_dispose_information_request` from its V148 body with the three new
      request-scoped tables. Clean-schema and populated V162 upgrade contracts cover this change.

## Bound Field values

- `V167__field_value_bound_binding_invariant.sql` drops `schema_field_binding_conflict`, deletes
  `field_value` rows whose Field contract is not bound in their assignment's Schema Version (their
  selections cascade; revision history is kept), points every remaining row at its bound binding,
  and makes `field_value.schema_field_binding_id` required. Back up Field values before applying it
  if any assignment held answers outside its Schema Version. Clean-schema and populated V163 upgrade
  contracts cover this change.

## Template draft revisions and requirement intervals

- `V168__information_request_template_draft_revision_requirement_interval.sql` adds a `revision`
  counter to each Template Version (restating the Version guard so a published Version's revision
  cannot change), replaces the append-only trigger on requirement revisions with a guard that lets
  `effective_to` move only once from empty to a value, relaxes the interval check to
  `effective_to >= effective_from`, and closes each superseded revision where its successor begins.
  Clean-schema and populated V163 upgrade contracts cover this change.

## Retained owner reads

- `V169__information_request_record_owner_share_role.sql` adds the read-only `RECORD_OWNER` role to
  the request branch of `share_role_name_check` and converts the earlier `SUBJECT` request Shares
  that only kept an owner's history readable into `RECORD_OWNER`. Party Shares are unchanged.
  Clean-schema and populated V163 upgrade contracts cover this change.

## Domain event owners and access session credentials

- `V170__domain_event_owner_and_access_session_credential.sql` drops the unread
  `workflow_event_outbox.organization_id` column and its index; the owner check now reads only
  `owner_kind` and `owner_id`. It deletes access sessions that never held a credential or an expiry (V117 had already
  revoked them), makes `request_access_session.credential_hash` and `expires_at`
  required, replaces the partial unique index on the credential with a unique constraint, and keeps
  the guard that a session's link, participant, and credential never change. Clean-schema and
  populated V169 upgrade contracts cover this change.

## Transition idempotency keys and receipts

- `V171__information_request_transition_idempotency_key.sql` adds a required, non-blank
  `idempotency_key` to `information_request_transition`. Existing rows take the key of their linked
  command receipt, or `system|<mutation>|<request id>|<sequence>` when they have none (the
  append-only guard is disabled only around this backfill). The `command_receipt_id` foreign key is
  recreated `DEFERRABLE INITIALLY DEFERRED`, so a transition written inside a command can name the
  receipt the command stores when it completes. Clean-schema and populated V169 upgrade contracts
  cover this change.

## Removed requirement revisions

- `V172__information_request_removed_requirement_revision_close.sql` closes the open revision of
  every requirement that was removed before V168 began closing revisions: a requirement an
  amendment removed (and that the request's current Template Version no longer binds) closes at
  that amendment, and a requirement in a removed repeated entry closes when the entry was removed.
  Revisions that are still current stay open. A populated V170 upgrade contract covers this change.

## Refresh rule retry state

- `V173__information_request_refresh_rule_retry.sql` adds `attempt_count` (default 0, never
  negative) and `next_attempt_at` to `information_request_refresh_rule`; a rule holds a retry time
  exactly while it counts failed attempts. It replaces the rule's append-only trigger with
  `information_request_refresh_rule_guard`, which keeps every definition column unchanged, lets
  only the retry columns change, and still allows deletion only during record disposal. Existing
  rules start with no retry state. Clean-schema and populated V172 upgrade contracts cover this
  change.

## Disposal reference lifetime

- `V174__record_disposal_reference_lifetime.sql` gives every fact recertification foreign keys to
  the accepted fact, source request, package, submission item, Requirement, Field value revision,
  and review it copies, and each recertified evidence row a foreign key to its evidence version,
  with an index on each. A source request therefore cannot be finalized while another request's
  recertification names it. It restates `record_disposal_reference_guard`: a new or changed
  reference takes a `FOR KEY SHARE` lock on the referenced document, document version, or owning
  request before it checks for an open claim, and new recertifications and recertified evidence are
  guarded too. `record_disposal_lock_information_request_objects` locks a request and its stored
  documents and versions `FOR UPDATE` in identifier order; a claim calls it before it assesses
  references, so a reference being written is waited for and a later one sees the claim.
  Clean-schema and populated V173 upgrade contracts cover this change.

## Active party assignments

- `V175__information_request_party_active_assignment.sql` adds partial unique indexes so a request
  holds one active party per role and principal, and one active subject party per subject. It fails
  to apply if a request still holds such a repeated active party; none existed when it was written.
  Clean-schema and populated V174 upgrade contracts cover this change.

## Request records under disposal

- `V176__record_preservation_request_records.sql` adds `record_preservation_information_request_records`,
  which lists the records a request owns that a hold can name: its submission packages, evidence
  artifacts and versions, outbound notices, and record exports. It restates
  `audit_legal_hold_guard` so an active hold on such a record is refused while an open disposal claim
  names the record's request in its scope, including a record added after the claim, and restates
  `record_disposal_scope_guard` so a claim cannot name a request while any of its records is held.
  It changes no rows. Clean-schema and populated V175 upgrade contracts cover this change.

## Document version storage cleanup

- `V177__document_version_storage_cleanup.sql` adds `document_version_storage_cleanup`: one row per
  stored object whose recording transaction has not committed, named by its object key (unique,
  `OBJECT_KEY` only), with an attempt count and the time it is next due. A version write records
  the row in its own transaction before writing bytes and deletes it inside the recording
  transaction, so only a write that never committed leaves a row. The cleanup worker
  (`app.document-version.storage-cleanup.*`) removes such an object once due, unless a committed
  document version refers to it. It changes no existing rows. The clean-schema contract and
  `DocumentVersionStorageCleanupContractTest` cover it.

## Archived files of conditional answers

- `V178__information_request_response_archive_evidence.sql` adds `evidence_count` to
  `information_request_response_archive` (default 0, never negative) and lets an archived answer
  hold files alone, so a hidden Document answer that has only files is archived instead of left
  active. It adds the append-only `information_request_response_archive_evidence`: one row for each
  file the archive took out of the answer, naming the exact evidence version and version number.
  A trigger refuses a version that is not a version of a file of the archived Requirement, and
  foreign keys cascade on request disposal, so the disposal function is unchanged. It changes no
  existing rows. The clean-schema, archive, and disposal contracts cover this change.

## Imported value Requirement revisions

- `V179__information_request_imported_value_requirement_revision.sql` adds the required
  `requirement_revision_id` to `information_request_imported_value`: the Requirement revision current
  when the value was recorded. Existing values are given the revision that was in effect when they
  were recorded, or the Requirement's first revision for a value recorded before it. The scope
  trigger now also refuses a revision of another Requirement. It allows the `FIELD_CHANGED`
  reconciliation outcome for a value recorded while its Requirement collected another Field. The
  external-source contract and a populated V178 upgrade contract cover this change.

## Connector exchange history

- `V180__information_request_connector_exchange_history.sql` widens
  `ck_information_request_transition_mutation` with the five changes the connector worker records:
  `START_EXTERNAL_SOURCE_ATTEMPT`, `SCHEDULE_EXTERNAL_SOURCE_RETRY`, `RECORD_EXTERNAL_SOURCE_PENDING`,
  `COMPLETE_EXTERNAL_SOURCE` and `FAIL_EXTERNAL_SOURCE`. Transitions stay append-only and every
  earlier value is still accepted. It changes no existing rows; exchanges that were already pending or
  finished keep their earlier history and record the new changes from their next step. The connector
  exchange history contract covers a clean schema, every mutation the application records, and a
  populated V179 upgrade.

## Group notice recipients

- `V181__information_request_group_notice_recipients.sql` replaces the unique constraint
  `ux_information_request_outbound_notice_intent`, which allowed one outbound notice per notice
  intent, with the unique index `ux_information_request_outbound_notice_intent_recipient` on the
  intent and the recipient's principal kind and id (`NULLS NOT DISTINCT`). A notice owed to a party
  held by a group is recorded once for each member it went to; a notice owed to anyone else still has
  exactly one row. It changes no existing rows. The outbound notice contract covers one notice per
  recipient and a repeated recipient being refused. `NULLS NOT DISTINCT` needs PostgreSQL 15 or later:
  check the target database's version (`SELECT version()`) before upgrading, because
  `infra/cloudformation.yml` does not pin the RDS engine version.

## Notice render failures

- `V182__information_request_notice_render_failures.sql` adds `information_request_notice_render_failure`:
  one row for each failed attempt to render a notice intent, numbered per intent
  (`ux_information_request_notice_render_failure_attempt`), with a non-blank `failure_code` and the
  time it failed. Rows are never updated (`information_request_notice_render_failure_unchanged`), and
  they are removed with their intent or request (`ON DELETE CASCADE`), so record disposal needs no
  change. It changes no existing rows. The outbound notice contract covers numbering, the code and the
  update refusal.

## Superseding follow-ups under disposal

- `V183__record_disposal_superseding_successor.sql` recreates `information_request_superseded_by_fkey`
  with `ON DELETE SET NULL`. Disposing a request that superseded another clears the other request's
  `superseded_by_request_id`, so a superseding follow-up can be disposed of before the request it
  superseded, which its lineage keeps until then. It changes no existing rows.

## Account links of disposed requests

- `V184__participant_account_link_request_disposal.sql` lets
  `participant_account_link.linked_via_information_request_id` be null and recreates its foreign key
  with `ON DELETE SET NULL`. Disposing the request through which a participant linked an account keeps
  the link to the account and clears the request it names. It changes no existing rows.

## Deep analysis backlog without a migration

- The backlog fixes of 2026-10-22 add no migration; the head stays V179.
- `audit_outbox.organization_id`, and its copy in the ledger, stay part of the hash-chained event
  envelope as current behavior. Ledger streams are chosen by `owner_type` and `owner_id` alone;
  `ck_audit_outbox_owner` (V84) already refuses a platform row that names an organization, so no
  stored row changes.
- `information_request_evidence_version.document_version_id` has been required since V165. The
  application now maps it as required too, so every evidence version is evaluated as a stored file.

## After upgrading

- Grants issued before V150 keep their old recipient cap value as `acting_party_cap` and have no
  evidence allowances, so their uploads are bounded by the platform ceiling alone. Grants issued
  afterwards freeze the plan's allowances while enforcement refuses.
- Read `GET /platform/information-request-health`. `RESERVATIONS_ABOVE_CAP` and
  `REQUESTS_WITHOUT_EXECUTION_GRANT` must read zero after any backfill or restore.

## V185 record preservation boundaries

- Canonicalizes existing preservation resource type and UUID keys without changing hold history.
- Adds privacy-request restriction links and backfills identifiable completed restriction requests.
  Existing immutable request rows are changed only inside the migration's scoped trigger exception.
- Enforces the 365000-day upper bound on new retention versions. The new check is NOT VALID so
  immutable historical versions remain; the worker skips invalid historical schedules and continues
  other owners' work.
- Removes disposed request Shares, access links and sessions. Participant account identities remain,
  with deleted link provenance cleared. Deleted object locators are scrubbed while deletion evidence
  remains; the locator check and one-time object guard permit that transition.
- Keeps hold events, schedules and tombstones append-only during disposal. Export deletion requires
  frozen claim scope and owner matching; claim scope cannot grow once object deletion starts.
- Adds the failed-claim selection index. No new cloud service or resource type is introduced.

## V186 reconciliation and fact history

- Extends the request transition mutation check with RECONCILE_IMPORTED_VALUES.
- Backfills immutable predecessor/successor fact identity links. The successor UUID intentionally has
  no foreign key, preserving supersession when its fact is disposed of. The predecessor fact owns
  the link and cascades it during its own authorized disposal; ordinary update/delete is refused.
- New fact inserts capture supersession in the same transaction. No historical fact value is changed.
- The next unused migration version is V187. No new cloud service or resource type is introduced.

## V187 storage boundaries

Owned program timestamp columns now store instants as TIMESTAMPTZ. Conversion interprets legacy
naive values as UTC, matching the earlier Field instant convention. Verify that assumption against
any populated deployment before applying it; a database that stored local wall-clock values needs
an explicit conversion plan. New indexes cover owner, party, evidence, archive and disposal lookups.
A hash index supports exact storage-locator lookup without a B-tree entry-size limit. New execution
grant writes require revocation time and reason together. The NOT VALID check preserves existing
inconsistent revocation rows for explicit remediation and does not certify historical data.

## V188 archive ownership and disposal accounting

Archived responses must belong to their recorded request and Requirement. Disposal explicitly
counts corrections, condition effects, archived responses and archived file references before
removing their parents. Existing migrations remain unchanged.

## V189 history claim scope and mapped vocabularies

Append-only deletion exceptions apply only to records of the active request disposal claim,
including private Template records and fact children resolved through their owning records.
An active claim for one request does not authorize deleting another request's history.
New delegated-authority actors and access-session principal and verification values use their
mapped closed vocabularies. NOT VALID checks retain inconsistent legacy rows for remediation.

## V190 disposal claim row scope

V189 left three kinds of rows that a request's disposal deletes without a way to name their request: review
worksheet rows, notice sequence numbers and the source packages of a follow-up. Their append-only guards refused
the disposal, so the claim of any request with a review worksheet was retried without end. V190 replaces the
claim scope function so these rows resolve their request through their review, their outbound notice, or the
follow-up they belong to. It changes no rows; a claim that was retried completes on its next pass.

## V191 subject Share role

V191 drops `SUBJECT` from the request roles the Share role check accepts. A subject party names a Subject
Identity and never holds a Share: no party write creates one, and V169 converted the owner read Shares that
used the role into `RECORD_OWNER`. The application no longer grants `SUBJECT` any request capability either.
It changes no rows. A `SUBJECT` request Share written outside the application stops the migration, and the
database needs a reviewed remediation before continuing. The next unused migration version is V192.

## Historical upgrade and direct-writer boundary

Clean-schema migration tests do not certify every populated historical shape. A database halted
by an earlier policy-basis, duplicate-active-party, external-reference or bootstrap-MFA migration
requires an explicit reviewed remediation before continuing; do not invent historical policy
facts, silently discard evidence, or edit applied migration files. Supported writers use the
application services. Some publication, tenant, pointer, link-mode, grant-owner, revocation and
cross-reference invariants remain service-enforced; direct SQL maintenance must independently
validate them. The migration ledger is not a claim of exhaustive constraint refusal coverage.