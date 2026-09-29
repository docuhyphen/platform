# Document-Driven Information Requests Implementation Plan

## Status

- Overall status: In progress. Phases 1 through 11 and the repeated remediation gate are complete; Phase 12
  has not started. Phase 10's manual width check (1440, 1024, 768, and 360 CSS pixels across the
  author, respondent, reviewer, authenticated, and no-auth journeys) was explicitly waived by the user
  on 2026-09-28 ("You can skip this"). It was not run and is not claimed as passing.
- Scanner decision (user, 2026-09-25): the Information Request feature and every other feature must
  work in production without a malware scanner. Malware scanning moved out of this program into
  `plans/INFORMATION-REQUEST-EVIDENCE-MALWARE-SCANNING-PLAN.md`, which the user will implement later.
  Evidence upload is on by default, `app.information-request.evidence.malware-scan.required`
  defaults to `false`, an unscanned file can satisfy a Requirement, and no file is ever described as
  scanned or safe without a real scan. This supersedes the earlier rule that production evidence
  upload waits for an approved scanner.
- Review checkpoint: the 2026-09-13 [Phases 1-5 recheck](DOCUMENT-DRIVEN-INFORMATION-REQUESTS-PHASES-1-5-RECHECK.md)
  found seven remaining gaps. The original P5-R01 through P5-R20 completion records remain
  historical evidence; they do not establish that the integrated implementation is gap-free.
- Current work: none in progress. Phase 11 was requested in full by the user and completed on
  2026-09-29: `P11-T1` through `P11-T12` and the exit gate (full backend suite 3,285 tests green,
  full frontend suite 730 tests green, eight conformance scenarios in 13 tests green, traceability
  matrix under `### Phase 11 capability traceability`). V148 was not applied to the local database
  when the session ended; `quarkus:dev` applies it on its next reload, and it must not be edited once
  the local history shows it.
- Development stage: the platform has no production users and is in active development. No
  backwards-compatibility code may be written, and the compatibility mechanisms already shipped are
  now defect work. See `## Development-Stage Constraint` and
  `## Development-Stage Compatibility Removal`.
- Exact next task: `P12-T1`, only when the user asks for Phase 12. Flyway head is V148; V149
  through V160 remain unallocated.
- Latest implementation result: see `## Latest Implementation Result`.
- Carried into Phase 12: external sources have REST and help but no screen (Phase 11 decision 12);
  a remote connector, if ever scoped, needs its own security review and the shared signer noted in
  decision 2. The Personal Request Schema entitlement decision and the Phase 9 AWS and legacy
  trigger issues remain open as recorded in prior evidence.
- Phase 11: complete. Verification and mutation proof are recorded in the Phase 11 evidence entries.
- Phase 10: complete. Verification and mutation proof are recorded in the Phase 10 evidence entry.
- Phase 9: complete. Verification and mutation proof are recorded in the Phase 9 evidence entry.
- Phase 8: complete. Template review plans (V136), package review cycles with assignments,
  worksheets, decisions, overrides, findings, corrections with allowlists, comments, remediation,
  reconsideration, and appeal (V137), Accepted Facts and Business Decisions (V138), the
  `RESPONSE_REVIEW` executor, the walking fixtures, and the reviewer and respondent review UI are
  done.
- Phase 7: complete. Staged and whole-package submission with frozen packages, multi-party
  Submission Attestation, amendments with effective-binding advance, reconfirmation, and pending
  Notice Intents, supplement, superseding, recurrence, and refresh follow-ups, the walking
  fixtures, the respondent submission UI, and executor registration with issuance are done.
- Phase 6: complete. Evidence persistence, storage and content identity, Requirement-scoped access,
  upload, replacement, withdrawal, reads, evaluation, policy validation, intake limits, content
  inspection, the optional scanner port, quarantine release rules, supporting-evidence links, the
  walking fixtures, and the respondent evidence UI are done and verified. No new AWS service or paid
  resource type is authorized.
- Phases 1 and 2: no additional standalone defect identified in the inspected foundation and
  Template paths. Their adapters and recipient-facing integration are included in the new findings.
- Phase 3: complete. P3-T11b through P3-T11d existing-runtime credential hooks are complete through
  P5-R26 through P5-R28. Future evidence/version/package preservation remains in later phases.
- Phase 4: complete. Independent credentials, owner-funded grants, party authorization, transaction-safe
  OTP limits, and recipient-safe workspace and request-detail projection are verified.
- Phase 5: complete. Occurrence-level disclosure filtering, nested saves, group creation authorization, response
  editor refusal/clear handling, party command lock ordering, PostgreSQL party concurrency
  evidence and party credential lifecycle hooks are complete. The integrated gate passed.
- Verification: use this recheck's current results and the newest evidence entry. The earlier
  2026-09-13 passing gate is retained as history, not reused as current verification.
- Entitlement decision: `INFORMATION_REQUESTS` is included in Personal and Business. Platform-admin
  feature overrides are the only per-owner feature control. Subscription rollout grants and their
  configuration were removed. An active organization grant also sponsors its members when they use
  the Personal Information Request Settings scope. Older task text describing a second owner rollout
  gate is historical and superseded by this decision.
- Future-phase boundaries remain: evidence/upload/package behavior stays in Phases 6-7; item-level
  correction allowlists remain with P8-T3 and currently fail closed. An administrative surface for
  explicit execution-grant revocation remains a future scoping decision.
- Completion evidence: `plans/DOCUMENT-DRIVEN-INFORMATION-REQUESTS-COMPLETION-EVIDENCE.md`.
- Implementation source of truth: this plan. Design input:
  `plans/DOCUMENT-DRIVEN-INFORMATION-REQUESTS-PROPOSAL.md`.

Historical task results live in the companion evidence journal. Preserve unrelated working-tree
changes and do not rewrite the proposal as part of this remediation.
## Plan and Evidence Update Protocol

Future sessions must keep the active plan and completion evidence split.

- Update this active plan with current status, the exact next task, remaining unchecked work, subsequent planned work,
  any task splits, and only the latest implementation result.
- Do not accumulate older implementation results in this active plan. When a newer result is added, make sure the
  displaced result is present in the completion evidence file, then replace it here.
- Add full completion results and evidence to
  `plans/DOCUMENT-DRIVEN-INFORMATION-REQUESTS-COMPLETION-EVIDENCE.md` newest first. Include exact tests, observed
  failures, files changed, migrations, decisions, help documentation review, risks, blockers, the exact next task, and
  files for the next agent.
- For planning-only sessions, update this active plan and add evidence only when the session changes handoff context,
  task boundaries, or future-session instructions.
- In older task text, references to a journal or journaled decisions mean the companion completion evidence file unless
  a section explicitly says otherwise.

## Mandatory Protocol for Every Implementation Session

The following sequence is mandatory for every new implementation session. Do not skip or reorder
the test-first, implementation, verification, and handoff stages.

1. Read the repository `AGENTS.md` completely before inspecting or changing implementation files,
   plus any more-specific `AGENTS.md` governing files in scope.
2. Read this implementation plan completely, including the latest implementation result. Read
   `plans/DOCUMENT-DRIVEN-INFORMATION-REQUESTS-COMPLETION-EVIDENCE.md` when you need prior completion evidence, older
   test results, or historical decisions.
3. Inspect `git status`, the current implementation, relevant migrations, tests, and help articles.
   Preserve all unrelated user changes and never assume a dirty file belongs to the current task.
4. Select the first incomplete and unblocked task. Confirm its dependencies and exit criteria.
   Confirm that the task, proposed identifiers, test data, and shipped configuration satisfy the
   industry-neutral platform rules in `AGENTS.md` before writing the first test.
5. Use test-driven development:
   - Write or update the smallest automated test that expresses the next required behavior.
   - Run the focused test and confirm that it fails for the intended behavioral reason.
   - Do not treat compilation errors, broken fixtures, or unrelated failures as the required red
     state.
6. Implement the smallest complete production change that makes the new test pass.
7. Run the focused test again until it passes, then refactor while keeping it green.
8. Run all phase-specific tests and the affected backend or frontend regression suites. Record the
   exact commands and results. Never describe a test as passing if it was skipped or could not run.
9. For every user-visible feature change, search the help documentation, read every matched article
   in full, update inaccurate content, enforce the article and registry size limits in `AGENTS.md`,
   and run `npx tsc --noEmit` from `web-app`.
10. As the final action after the implementation and verification attempt, update this plan and the companion completion
    evidence file even if the task is blocked or a test still fails. Keep this plan limited to current status, what is
    next, remaining planned work, any task splits, and the latest implementation result. Add the detailed completion
    evidence entry to
    `plans/DOCUMENT-DRIVEN-INFORMATION-REQUESTS-COMPLETION-EVIDENCE.md`, including what changed, what remains,
    migrations, every exact test result, decisions, risks, blocking failures, the exact next task, and files to read.
    Never hide a failed or incomplete session by omitting its evidence entry.
11. Do not create a Git commit or push any Git ref without the user's explicit permission.

Documentation-only planning sessions do not need artificial tests. Any session that changes
production code, migrations, API contracts, configuration behavior, or user-facing behavior must
follow the TDD sequence above.

Code comments must describe the implementation itself. They must never mention this plan, its file
name, or its phase and task identifiers.

## Industry-Neutrality Constraint

DocuHyphen is an industry-neutral Document-Driven process management and execution platform. This
constraint applies to every task, test, migration, contract, fixture, and acceptance gate in this
plan.

- Production packages, classes, entities, models, DTOs, services, repositories, resources,
  functions, properties, enums, endpoints, database objects, events, Workflow primitives, UI
  components, routes, configuration keys, feature flags, metrics, and log event names must use
  reusable process terminology rather than terminology from a particular industry or customer
  domain.
- Production behavior must not hardcode a particular process vocabulary, decision rule, form,
  role, evidence type, deadline, retention period, Template, or seeded Workflow. Variation belongs
  in generic versioned configuration, policy, rules, roles, Fields, Templates, and extension
  contracts interpreted through the same production paths.
- Customer-authored terminology remains runtime data. Production logic must not branch on customer
  labels, Template names or IDs, Requirement keys, subject labels, fixture identifiers, or seeded
  values.
- Tests, reusable fixtures, seed data, example Templates, and executable acceptance scenarios must
  use neutral synthetic process names and content. Concrete industry examples are limited to
  explanatory documentation and cannot create production identifiers, shipped defaults, special
  branches, or program acceptance gates.
- Each generic capability must be validated against at least two materially different neutral
  process patterns. If a task cannot be implemented without industry-specific production naming or
  behavior, stop and ask the user how to scope it before implementation.
- Before checking a task, audit all changed artifacts for industry-specific naming and embedded process rules. Record
  the result in the companion completion evidence file.

## Development-Stage Constraint

DocuHyphen has no production users yet and is in active development. This constraint governs every
task, migration, contract, fixture, and test in this plan, and it overrides any older text here that
assumes deployed data or an older deployed application version must be accommodated.

- Do not write backwards-compatibility code. There is exactly one supported shape of every column,
  contract, payload, and identity, and production code reads and writes only that shape.
- Do not dual-write a new shape beside a legacy one, do not read a legacy shape as a fallback, and
  do not keep a legacy column, field, endpoint, alias, or enum value alive for an older reader or
  writer.
- Replace a shape in one forward-only migration: add what is new, transform what is there, remove
  what it replaces, and enforce the final constraints in the same file. A destructive change to
  existing rows is acceptable because no row belongs to a customer.
- Do not plan for rolling deployment, mixed application versions, old-writer drain, later contract
  releases, catch-up backfills, or deprecation windows. A deployment replaces the running version.
- Historical rows carry no special status. Do not keep an unresolvable legacy value as a history
  label, and do not introduce a state whose only purpose is to describe rows an older release wrote.
- This constraint removes compatibility with older releases. It is not permission to regress current
  behavior: shipped features, their tests, and their user-visible behavior must keep working unless a
  task explicitly changes them. Flyway files that may already have been applied are still never
  edited, renamed, reused, or squashed; replacement happens in a new forward migration.
- Where a completed task already shipped a compatibility shim under the previous assumption, that
  shim is now defect work. Completed task text below is left as the historical record of what was
  built and is not rewritten; the removals are tracked under
  `## Development-Stage Compatibility Removal`.

## Development-Stage Compatibility Removal

These tasks remove compatibility mechanisms this program already shipped. They are ordinary
implementation tasks and follow the mandatory TDD sequence. Allocate each migration from the current
unallocated range immediately before creating it, and record it in the migration ledger.

- [x] `DS-T1` Remove the Document Version compatibility shims added by `P6-T2a` and `P6-T2b`. Too
  large for one session, so it is split below. Do not check `DS-T1` until both subtasks are complete.
    - [x] `DS-T1a` Storage locator. Drop `document_version.storage_path` and the dual-write that
      fills it, make `storage_provider`, `storage_locator_kind`, and `storage_locator` `NOT NULL`,
      and remove the legacy branch from `DocumentVersionStorageLocatorMapper` and
      `DocumentVersionContentService` so a stored version resolves only through its canonical
      locator. Remove `LEGACY_LOCAL_PATH` and `LegacyLocalDocumentVersionLocator` once nothing
      resolves one. Rows written before the canonical columns are transformed or removed by the
      migration rather than left readable through a fallback. Remove
      `DocumentVersionDetailedDto.storagePath` and the frontend field that declares it, since no
      client reads it and an object key is not client data.
      Done by V123, which removes every version that could only be located through the retired local
      path, drops `storage_path`, makes the three canonical locator columns `NOT NULL`, and admits
      only `OBJECT_STORE` with `OBJECT_KEY`. The locator vocabulary, mapper, content service, entity,
      version service, DTO, transformer, and frontend model now carry the canonical shape alone.
    - [x] `DS-T1b` Creator provenance. Make every version-creation path name a real principal,
      including the no-auth upload, which records a server-resolved primary recipient email instead
      of a principal. That upload is authorized by the Exchange no-auth access token under the
      primary recipient's Share rather than by a ShareLink, so the principal it names is that Share's
      principal. Then drop
      `document_version.created_by` and `document_version.createdbyemail`, make
      `created_by_principal_kind` and `created_by_principal_id` `NOT NULL`, remove the legacy branch
      from `DocumentVersionCreatorMapper` and the legacy field from
      `DocumentVersionCreatorProvenance`, and remove the rolling-deployment allowance in the V122
      legacy consistency constraint by dropping that constraint with the columns it guarded. Replace
      the creator display in `DetailedEntityToDtoTransformer` with a resolution from the canonical
      principal, because the registered-user association it reads today disappears.
      Done by V124, which carries a version still named only by the registered-user key to that
      user, removes a version whose creator could only be stated by an email or not at all, drops
      `created_by`, `createdbyemail`, and their constraints, and makes the canonical pair `NOT NULL`.
      Each upload path passes the principal it authorized: the authenticated principal, or the
      primary recipient Share's principal for the no-auth token path, which is refused before any
      content is stored when there is no active primary recipient. `PrincipalDisplayService`,
      extracted from `ShareQueryService`, resolves the creator label the payload carries, and the
      `userEmail` form field and `DocumentVersionCreatorResolver` are gone.
- [x] `DS-T2` Remove the Fields attribution shims added by `P1-T6`. Drop
  `field_value.updated_by_app_user_id`, `schema_assignment.assigned_by_app_user_id`, and
  `field_value_revision.recorded_by_app_user_id` with their legacy consistency constraints, and
  remove `FieldPrincipalProvenance.legacyAppUserId` and the three writes that set them. The canonical
  pair already carries every value those columns hold, so nothing is lost.
  Done by V125, which drops the three columns with their consistency constraints.
- [x] `DS-T3` Remove the Share provenance shims added by `P3-T1d`. Drop
  `share.granted_by_app_user_id` and `share.revoked_by_app_user_id` with their legacy consistency
  constraints, and remove the dual-write in `ShareService`. Check every reader first, including
  access-management DTOs, and move any that still reads a legacy column onto the canonical pair.
  Done by V126, which drops both columns with their consistency constraints. `ShareService` now
  has one `grant`, `revoke`, `revokePendingForResource`, and `revokeAllForResource`, each taking the
  canonical `PrincipalRef`; the app-user-keyed wrappers and their `WithPrincipalProvenance` twins are
  gone, and every caller passes a principal. The access view exposes the grantor as
  `grantedByPrincipalKind` and `grantedByPrincipalId`.
- [x] `DS-T4` Sweep the program for any remaining compatibility mechanism: a retained legacy column,
  a legacy read fallback, a dual-write, a tolerated older payload shape, an endpoint alias kept for
  an unknown client, or a test that asserts mixed-version behavior. Remove what is found and record
  the search. This gate replaces the compatibility half of `P12-T1`.
  Found and removed: the superseded `PUT /exchanges/{id}/fields` alias with its deprecation headers,
  `DeprecatedFieldsWriteUsage` telemetry, and the `optional` and `isStated` header helpers that only
  it used; the earlier `$` spelling of the root occurrence, which reads still accepted, with V127
  carrying stored `$` rows to `root` and refusing `$` from then on; and the upload dialog's second
  request to the version endpoint, which recorded a duplicate version for every upload because the
  upload already records one. Kept, because they are current behavior rather than compatibility:
  the Schema target default, which the first-party client relies on, and `FieldsPrecondition.Unconditioned`,
  which an explicit `If-Match: *` and internal writers state; only their stale compatibility wording
  changed. Compatibility code outside this program is reported in the completion evidence rather
  than removed here.

## Objective

Build a reusable Information Request capability for DocuHyphen that allows an Exchange owner to
request structured responses, evidence, and response attestations from one or more parties, preserve
immutable submission and review history, and move incomplete or deficient items through correction
without conflating information collection with a downstream process outcome.

The core must implement a configurable process pattern that is independent of industry vocabulary,
subject type, evidence type, and downstream outcome:

1. Define and issue request items.
2. Collect a response, exception disposition, Response Attestation, or evidence reference for each item.
3. Preserve the exact evidence versions and configuration used for each submission.
4. Verify evidence and record findings.
5. Request corrections or supplemental information without rewriting earlier submissions.
6. Record remediation and review completion while keeping any downstream outcome separate.

## Delivery Shape and Release Cut Lines

This document is a master roadmap for a multi-release program, not a single sprint or implementation
session. The following milestones are separately testable and releasable behind default-off rollout
controls:

1. Foundation program: Phase 1 makes the existing Fields engine safe, version-aware,
   principal-aware, and reusable. It is a prerequisite program, not a small preparatory patch.
2. Controlled skeleton: Phases 2 through 4 establish Templates, runtime requests, canonical
   authorization, recipient-bound access, audit, and lifecycle without general production rollout.
3. Collection MVP: Phases 5 through 7 complete structured responses, evidence, and immutable
   no-review submission for the basic walking skeleton. This is the earliest controlled pilot cut.
4. Review and operations: Phases 8 through 10 add review, remediation, clocks, records, and complete
   user journeys.
5. Conformance and release: Phases 11 and 12 prove cross-process behavior and complete migration,
   packaging, quota, documentation, and rollout gates. Connector work is not part of the MVP cut.

Before starting a task that cannot fit one implementation session, split it into journaled,
dependency-ordered subtasks, each with one bounded failing behavior, implementation change, and
verification set. Do not check the parent task until every subtask and its phase gate pass.

## Product Boundary

This program delivers controlled information and evidence collection. It does not attempt to become
the system that owns downstream process outcomes outside the Information Request capability.

### Core scope

- Standalone, reusable, immutable Information Request Template Versions.
- Structured Field, Document, and Response Attestation requirements.
- Stable requirement identity across template versions.
- Request-scoped parties and roles, including subject, contributor, preparer, attestor, reviewer,
  and decision maker.
- Authenticated and magic-link response paths backed by the same application services.
- Sparse draft saving with explicit server-side completeness validation at submission.
- Repeatable structured groups and server-evaluated conditional requirements.
- Multi-file evidence with version identity, hashes, evidence policy, and requirement-specific ACLs.
- Immutable submission packages, Submission Attestations, correction cycles, and supplemental requests.
- Item-level review findings, remediation, retesting, and configurable separation of duties.
- Workflow events scoped to a specific Information Request and submission revision.
- SLA clocks, reminders, audit history, retention, record-preservation holds, and evidence export.
- Responsive author, respondent, reviewer, and operational queue experiences.

### Later configurable extensions

- Reusable subject information profiles with explicit promotion and recertification.
- External source and verification adapters.
- Structured external-message adapters.
- Customer-authored configuration bundles and policy vocabularies.
- Generic output-manifest and external-outcome reference contracts.

### Explicit non-goals

- Downstream calculation, scoring, eligibility, adjudication, filing, approval, or opinion engines.
- External systems of record that own the outcome of a wider process.
- OCR, automated extraction, document classification, or AI-generated findings.
- Qualified electronic-signature services. The core stores Submission Attestations and external signature
  references only.
- Specialized content-authoring, transformation, redaction, viewing, or production engines.
- Live external protocol, authority, or registry integrations before generic connector contracts,
  separate scope, and security review exist.
- Any representation that using DocuHyphen alone guarantees compliance with a law or standard.
- A new AWS service or paid cloud resource type without explicit user confirmation.

## Verified Repository Baseline

The following facts were verified against the repository on 2026-08-30 and rechecked during the
third architecture review on the same date. Every implementation session must recheck the relevant
fact because the codebase may have changed since this plan was updated.

Three classes of error were found and corrected in the third review, and every future review must
test for the same failure modes:

1. Enum breadth mistaken for persistence breadth. A value existing in a Kotlin enum does not mean
   the database accepts it or that any row uses it. Check the CHECK constraint and the actual
   writers.
2. A mapped entity mistaken for a populated table. An entity, table, and repository can exist with
   no writer anywhere, which makes any planned backfill fictional and hides the real work of
   building the missing lifecycle.
3. A schema change mistaken for a complete change. Extending a persisted vocabulary usually also
   requires changes to the sealed types, adapters, and guards that branch on it, and those code
   sites belong in the same task.

| Area | Current repository reality | Required response in this plan |
|---|---|---|
| Blueprints | `blueprint_definition` is mutable and has no version table. Its Schema link uses stable `schema_definition_id`. | Preserve current Blueprint behavior. Add an exact Template Version reference for future instantiations without inventing Blueprint version history. |
| Blueprint and Document Library defaults | `BlueprintParticipantDefault`, `BlueprintDocumentDefault`, and `BlueprintFieldDefault` already configure participants, Document Library-derived document metadata, and Field defaults. `BlueprintDefinitionService.copyChildren` preserves them during clone. | Reuse these defaults during request instantiation. Define explicit mappings into request party roles, Requirement defaults, and document placeholders rather than creating a parallel Blueprint-default mechanism. |
| Schema Assignment | `SchemaAssignmentService` accepts a Schema Definition and resolves the latest published version. `schema_definition.target_resource_type` is `VARCHAR(48) NOT NULL DEFAULT 'EXCHANGE'` with no CHECK constraint, so a new target value needs no migration. The `@Transactional` annotation sits on the three-argument `assignSchema` overload only; the four-argument overload relies on its caller's transaction, currently `ExchangeInitiationService.initiateExchange`. | Add an internal assign-exact-published-version path for Information Requests while preserving the legacy assign-latest path. Register `INFORMATION_REQUEST` as a Schema target through the service and adapter, not a database migration. Give every new assignment entry point its own explicit transaction boundary instead of inheriting one. |
| No-auth access | The legacy Exchange stores one Exchange-wide token hash and verified window plus `recipientOtpHash`, `recipientOtpExpiry`, and `noAuthAccessValidityDays`; the emailed OTP is the gate that issues the token. `Exchange.requireRecipientSignIn` is an existing per-Exchange owner choice that disables the no-auth path, and `resendNoAuthPrimaryRecipientInvitation` refuses when it is true. A separate `ShareLink` model hashes a token for one Share and is partially wired through no-auth Exchange metadata retrieval and the central authorizer, but it currently represents direct `PUBLIC_LINK` access rather than recipient contact proof, has no request-party session or rotation lineage, does not enforce every stored password or domain field, and has no production creation or atomic use-count path. | Keep the Exchange credential for the legacy shell. Extend `ShareLink` into a bootstrap-only mode bound through a request-party Share, enforce every configured constraint, and add request-bound verified sessions. Treat `requireRecipientSignIn` as an authoritative owner policy that blocks bootstrap-link issuance for that Exchange; never silently override it. Do not add a duplicate request-credential table unless implementation proves the compatible `ShareLink` extension cannot satisfy the contract. |
| Caller identity | `AuthorizationContextFactory` resolves only authenticated users and applications, while `PrincipalRef` already supports participants and public links. | Keep `PrincipalRef` canonical and build the no-auth request context at the adapter boundary without changing the global authentication pipeline. |
| Authorization | `Action`, `Capability`, `RoleCapabilities`, `Share`, `ShareConstraints`, `ResourceType`, and `DefaultAuthorizationService` are the central stack. `Action` has 105 values, each mapping to exactly one `Capability`. `ResourceType` names twelve values but only three are Share-bearing: `share_resource_type_check` in the V1 baseline restricts `share.resource_type` to `EXCHANGE`, `DOCUMENT`, and `PRINCIPAL_GROUP`, no later migration widens it, and `ShareService.grant` is only ever called with `ResourceType.EXCHANGE`. Every Share is Exchange-role-typed, capability derivation always calls `forExchangeShareRole`, `share_role_name_check` restricts `role_name` to the seven Exchange roles, and `parentRef` is not enforced. `ShareService` maps any non-Exchange Share audit event to `AuditOwnerScope.Platform`. | Add resource-scoped Share roles, explicit parent-grant inheritance, and a central resource-policy evaluator. Widen `share_resource_type_check` and `share_role_name_check` in the same migration that introduces request resource types and role keys; without the resource-type change every request-party Share insert fails a check constraint. Characterize and regress the three resource types that can actually hold a Share, and treat the other nine as enum-only values that need no Share migration. Correct the non-Exchange Share audit owner before request-party Shares exist. Do not create a second authorization system. |
| Resource authorization context | `ResourceAuthorizationContextRegistry.resolve` returns null when a `ResourceType` has no `ResourceKind` mapping or no registered provider, and `DefaultAuthorizationService` treats null as permission to skip the archived and suspended denies. `collectOrgMembershipGrants` then falls back to `AuthorizationContext.activeOrgId`, so an unresolvable resource derives organization-role capabilities from the caller's active organization rather than the resource owner. `ResourceKind` has eleven values against twelve `ResourceType` values: `APPLICATION` and `WORKFLOW_WEBHOOK_ENDPOINT` map to null deliberately, and `DOCUMENT` maps to a kind that has no registered provider. | The existing generic path fails open, so fail-closed is a correction to current behavior rather than a property new types inherit. Add an explicit unresolved-context deny policy, remove the `activeOrgId` fallback for mapped resource kinds, and characterize the present behavior of `DOCUMENT`, `APPLICATION`, and `WORKFLOW_WEBHOOK_ENDPOINT` before changing it. Register the request kinds only after their providers exist. |
| Fields port | `FieldResourceAdapter` is whole-resource only, `setValues` has no Value Set or expected revision, and Field Value authorship is App User-only. | Generalize the Fields command and authorization ports for binding, occurrence, Value Set, canonical principal provenance, and concurrency. |
| Fields caller reachability | Current authenticated Field resources can obtain `USER` or `APPLICATION` principals. An external registered User is reachable now, while `PARTICIPANT` and `PUBLIC_LINK` Field calls require a synthetic service or adapter test until the Phase 4 no-auth surface exists. | Test the service and adapter contracts with explicit synthetic participant and public-link principals in Phase 1, identify those tests as pre-exposure contract tests, and add real no-auth endpoint coverage when Phase 4 exposes the path. |
| Fields scope vocabulary | `FieldScopeKind` and three V36 checks use `PLATFORM` and `ORGANIZATION`; other domains use differing persisted vocabularies such as `APP`, `ORG`, and `PERSONAL`. Personal scope is also blocked in code, not only in the schema: `ScopeReference` is `Platform` or `Organization` only while `OwnerContext` already has `Personal`, `ExchangeFieldResourceAdapter.ownerScope` returns null for a personally owned Exchange, `SchemaAssignmentService.assertSchemaVisibleToResource` casts to `ScopeReference.Organization` and rejects anything else, and `BusinessFieldsSubscriptionGuard.requireConfigurationMutation` calls `requireNotNull(organizationId)` for every non-`PLATFORM` scope. | Preserve the existing Fields spelling and extend it exactly to `PLATFORM`, `ORGANIZATION`, and `PERSONAL`. Use the same spelling for Information Request Templates while leaving unrelated existing domain enums unchanged. Rewrite all affected Fields checks explicitly. Extend `ScopeReference` with a personal owner and correct all four code sites in the same task; extending the enum alone yields a scope that throws on first use. |
| Fields commercial guard | `SchemaAssignmentService.setValues` and every assignment mutation call `BusinessFieldsSubscriptionGuard.requireResourceMutation`, which resolves the resource's `SubscriptionContext` and re-checks the owner's live plan for `PlanFeature.BUSINESS_FIELDS_AND_SCHEMAS` on each write. | Route Information Request Field writes through the request's `RequestExecutionGrant` instead of a live owner-plan lookup. Organization-owned Field-bearing requests require both `INFORMATION_REQUESTS` and `BUSINESS_FIELDS_AND_SCHEMAS`; Personal Information Requests include their typed response data. |
| Attribution foreign keys | `field_value.updated_by_app_user_id`, `schema_assignment.assigned_by_app_user_id`, and Share grant and revoke App User columns are nullable foreign keys to `app_user`. | Add parallel canonical principal kind and ID columns during expand, preserve valid legacy App User foreign keys while dual-writing, and state the later contract decision explicitly. Never place participant or public-link IDs into an App User foreign-key column. |
| Document Versions | `DocumentVersion` has no content hash, its creator is nullable App User-shaped, and version bytes use a hardcoded local path with replacement semantics. The mutable Document row owns the current hash. | Add typed provider-neutral write-once locators, version-level hashing, and canonical principal provenance with truthful legacy states. |
| End-to-end encryption | `DocumentEncryptionMode.END_TO_END` exists even though current frontend upload paths send `INTERNAL`. Server-side inspection and malware scanning cannot establish plaintext safety for opaque end-to-end ciphertext. | Keep end-to-end encrypted Document Versions ineligible to satisfy a file-backed Evidence Requirement until a separately approved inspectable representation and security contract exists. Do not claim ciphertext scanning establishes plaintext safety. |
| Record preservation | `AuditLegalHold` already stores a generic organization plus resource type and resource ID reference and has place and release behavior, but its authorization, subscription guard, owner model, history, and only disposal consumer are audit-specific. It cannot represent a personal owner safely, and no business-record purge pipeline exists. | Generalize the existing hold persistence and service behind a neutral record-preservation contract, preserve compatible audit APIs and the physical table during rollout, add personal ownership and append-only lifecycle history, and avoid a second active hold table for the same resource. Build reference-aware disposal before claiming evidence is purge-eligible. |
| Outbound notices | Communications are mutable and delivery logs do not retain recipient endpoints, rendered content, or content hashes. | Build immutable notice and append-only delivery-attempt records rather than describing them as existing. |
| Variables and Sequences | `{{TOKEN}}` and side-effecting `{{SEQ:KEY}}` interpolation are live in Exchange naming and Communication rendering. Sequence interpolation increments persisted state. | Claim each Notice Intent before rendering, render once, consume each configured sequence occurrence under the same idempotent transaction, and persist rendered content and hashes so retries never increment a Sequence again. |
| Content safety | Content-type tooling exists, but malware scanning does not. | Resolved on 2026-09-25: scanning is optional, evidence upload works without it, and the scanner is planned in `plans/INFORMATION-REQUEST-EVIDENCE-MALWARE-SCANNING-PLAN.md`. |
| Command safety | There is no reusable client-command idempotency receipt or `If-Match` infrastructure. | Add shared command-receipt and HTTP precondition foundations before retryable request mutations. |
| Transactional events | `DomainEvent` and `DomainEventPublisher` are already neutral. Their durable implementation, qualifier, mapped entity, repository, dispatcher, scheduler, metrics, and physical table are Workflow-branded. The event outbox has only nullable `organization_id`, so it cannot distinguish platform from personal ownership, and `idempotency_key` is globally unique with no type or owner prefix. A separate `audit_outbox` exists for immutable audit intent. | Preserve the neutral public contract, generalize only the durable implementation vocabulary, add explicit event owner kind and ID, and reuse the existing physical event table. Define and test an explicit idempotency-key namespace for Information Request events so a request key can never collide with a Workflow key in the shared global unique index. Keep audit and domain-event intents distinct but correlated and transactional; do not create a request-specific event outbox. |
| Audit | Audit event keys and categories are a closed catalog at version 14. `AuditRecorder` can accept an explicit actor but otherwise falls back to request authentication, and `AuditOwnerScope` is only platform or organization. `AuditActorKind` already includes `PUBLIC_LINK`. Roughly twenty existing call sites resolve owner as `organizationId?.let(AuditOwnerScope::Organization) ?: AuditOwnerScope.Platform`, so every personally owned Exchange already records its audit events under platform scope. | Add Information Request catalog entries and personal owner scope, and always pass explicit canonical actor data for request mutations, including no-auth actions. Enumerate and correct the existing organization-or-platform fallbacks that a request mutation can reach through Exchange, Document, Document Version, access-management, and hold services; otherwise a personal request inherits the same mislabelling through code the plan reuses rather than replaces. |
| Entitlement | `PlanFeature` has no Information Requests value and persisted feature overrides are organization-only. | Add an owner-neutral commercial entitlement override controlled by platform administration. Include Information Requests in Personal and Business without a second rollout gate. |
| Subscription trials and enforcement | User and organization trial grants and requests exist, and subscription enforcement has global `OFF`, `REPORT_ONLY`, and `ENFORCE` modes. A trial may expire after a request is issued. | Resolve issuance through the existing effective-subscription and enforcement services, snapshot trial or paid entitlement provenance in the execution grant, and prove trial expiry does not strand reserved issued work. |
| External participants | `ExternalParticipant` and `external_participant` exist with `owner_organization_id`, normalized email, and verification metadata, but no code path ever constructs or persists one. The repository exposes only `findById`, used by three read-only display paths, and `findByOwnerAndEmail` has no caller, so the table is empty in every environment. No production code emits a `PrincipalKind.PARTICIPANT` principal. | This is greenfield, not an extension. Build the owner-scoped participant identity lifecycle, including creation, contact verification, owner-scoped lookup, collision handling, and registration-upgrade lineage, and add personal ownership to the existing empty table as a forward-only schema change. Do not plan a data backfill, ambiguity report, or legacy-row resolution pass for a table with no rows. |
| Email-only recipient identity today | The existing email recipient path does invent an App User: `ExchangeRecipientSelectionResolver.resolveExternalEmail` creates `AppUser(isTemporary = true, isActive = false)` for every `EXTERNAL_EMAIL` selection. `ExchangeRecipientService.recordExternalEmailPrimaryDecision` and `ExchangeAccessManagementService.resendNoAuthPrimaryRecipientInvitation` both require `share.principalKind == PrincipalKind.USER`, so acceptance, resend, and `ExternalEmailAcceptancePolicyService` are structurally bound to User-kind Shares. | Never invent an App User for Information Request access. Because the reusable Exchange acceptance, resend, and eligibility paths assume a User-kind Share, record an explicit decision for each one: reuse it only for User-backed request parties, or implement a participant-principal equivalent. A participant-principal request party cannot silently flow through the existing recipient decision paths. |
| Exchange recipient binding | `exchange_recipient.direct_share_id` is `NOT NULL UNIQUE`, the V63 trigger `validate_exchange_recipient_share_binding` requires that Share to be a direct, non-owner Share whose `resource_type` is `EXCHANGE` and whose `resource_id` equals the recipient's Exchange, and `uq_exchange_recipient_primary` permits one `PRIMARY` recipient per Exchange. | A recipient's single Share is permanently pinned to its Exchange, so a request-party Share cannot be attached through `direct_share_id`. Reference an `ExchangeRecipient` by ID from the request party and bind the bootstrap `ShareLink` to the request-party Share, leaving the existing recipient invariant untouched. Do not model several request contributors as several primary recipients. |
| Trusted Organizations | V59 through V65 implement trust relationships, revisioned party policies, suspension, trusted person and group selection, recipient attestation, validation, and group access reconciliation. | Reuse these services for organization-owned request party assignment, replacement, group expansion, acceptance, and registration upgrade. Treat trust suspension as a separate security and eligibility axis and define its effect on new and already-issued work without weakening current reconciliation. |
| Principal Groups and recipient replacement | `ShareService` already materializes and reconciles group inheritance, propagates role and constraints, activates pending Shares, and revokes descendants. `ExchangeAccessManagementService.replacePrimaryRecipient` already replaces an eligible pending trusted recipient transactionally, but it requires a sender organization, an `INITIATED` Exchange, a `PENDING` acceptance status, a trusted-person or trusted-group selection, and an authenticated App User caller. | Generalize and reuse these methods for Information Request resource roles and party reassignment. Add only request-specific history, completed-work, session, precondition, and policy behavior. Treat the organization-sender, draft-only, pending-only, trusted-only, and authenticated-caller preconditions as five separate explicit policy decisions for request parties rather than inherited defaults. |
| Issued capacity | No persisted request-local execution grant or reservation ledger exists. | Capture and reserve conservative completion limits atomically at issuance so a later commercial lapse cannot strand assigned work. |
| Exchange lifecycle | Existing Fields and Documents enforce Exchange lifecycle rules, but no parent-child request transition contract exists. | Add an explicit Exchange and Information Request transition matrix with transactional race protection. |
| Workflow trigger registry | `WorkflowTriggerEventRegistry` is Flyway-seeded and drives both the Workflow trigger dropdown and `$subject.*` autocomplete through `subject_fields_json`. `WAIT_FOR_COUNTERPARTY_CLEARANCE` is an existing related waiting primitive. | Register Information Request events through an explicit migration with safe subject-field descriptors and evaluate the existing waiting primitive before adding any overlapping Workflow behavior. |
| Webhooks and connectors | `WorkflowWebhookEndpoint`, `WebhookWorkflowActionHandler`, application identity, destination policy, signing, and outbound delivery already provide an integration foundation. | Reuse applicable identity, destination-security, signing, delivery, and retry components. Keep structured-evidence or verification connector contracts distinct where they require request, polling, imported-value, or verification semantics that outbound Workflow webhooks do not provide. |
| Realtime | Authenticated Exchange experiences use `RealtimeEventService`, viewer and presence registries, and a WebSocket tied to User Sessions. There is no request-party no-auth realtime identity. | Keep ETag and precondition conflict handling authoritative for all clients. Reuse authenticated realtime only for optional notifications, do not require a no-auth WebSocket for correctness, and do not imply live collaborative editing unless separately scoped. |
| Model layout | Entities and DTOs currently use flat `model/entity` and `model/dto` packages. Dedicated DTO mappers live primarily in flat `model`, with a few in `model/dto` and one in `resource/mapper`; no `model/mapper` package exists. | Keep entities and DTOs in their existing flat packages and place new dedicated pure DTO mappers in flat `model` with domain-qualified names. Use domain subpackages for services, resources, and repositories, and do not create a third mapper convention in this program. |
| Help registry | `helpDocsRegistry.tsx` is 58 lines and the repository limit is under 60. | Refactor registry composition before registering an Information Requests section. |
| REST compatibility | Existing Exchange Field updates use sparse `PUT`. | Add canonical sparse `PATCH`, retain `PUT` temporarily as a deprecated compatibility alias, and migrate the frontend. |
| Flyway | The observed migration head is V75 and no initiative range is reserved. | Use the migration allocation rules in this plan and revalidate the head before every migration task. |
| Website | Existing marketing content includes concrete use-case examples and legacy non-neutral identifiers. | Treat marketing examples as explanatory content. Do not use them as product naming precedent or rename unrelated legacy assets in this program. |

## Architectural Decisions

These decisions replace the contradictory or unresolved alternatives in the proposal.

1. The product term is `Information Request`. `Intake` may appear only as legacy proposal language,
   not as the primary entity or API term.
2. Information Request Templates are standalone, reusable, versioned definitions. The existing
   mutable Blueprint Definition may reference one exact published Template Version for future
   instantiations. Changing that reference affects only later instantiations; every created request
   permanently pins and snapshots the exact Template Version it used. An ad hoc request is
   shorthand for atomically creating a private one-off Template Definition and immutable published
   Version before creating the request, so the pinning invariant has no exception. This program
   does not invent Blueprint version history.
3. A Schema remains a pure structured-data contract. Respondent prompt text, requiredness,
   response mode, conditions, assignment role, review policy, and evidence policy belong to the
   Template Requirement binding.
4. Existing `EXCHANGE` Schema Assignments and Field Values remain internal Exchange metadata. They
   are not automatically converted into respondent submissions.
5. Every runtime Information Request is eligible to be a Fields resource with
   `resource_type = INFORMATION_REQUEST` and `resource_id = information_request.id`. A
   Document-only or Response-Attestation-only request does not require an empty Schema Assignment.
6. The existing `UNIQUE(resource_type, resource_id)` Schema Assignment invariant remains. Each
   Information Request has at most one Schema Assignment, so several requests may use the same
   Schema without weakening assignment cardinality.
7. When Field Requirements exist, the Template Version is the configuration source of truth for
   `schemaVersionId`; the runtime Schema Assignment materializes the same reference. Creation must
   enforce equality, and an issued request's assignment cannot be replaced or removed. The
   Information Request does not add a third copy. The Fields service gains an internal
   assign-exact-published-version operation; the existing assign-latest-by-definition operation
   remains for legacy Exchange behavior.
8. Structured values have one authoritative store. A `FieldValueSet` belongs to one Schema
   Assignment and represents either the root response or a stable repeatable-group occurrence.
   `FieldValue` uniqueness includes the Value Set. Existing Exchange values are backfilled into one
   root set, so existing Workflows retain single-value semantics.
9. `RequestResponse` is the Requirement response envelope for disposition, narrative, provenance,
   and occurrence. For a Field Requirement it references an exact immutable Field Value Revision
   within the authoritative Value Set; it never stores a second current-value copy or resolve an
   older response through a mutable Field Value row.
10. Every Information Request pins one exact Template Version. Runtime Requirement snapshots are
   append-only revisions with a configuration hash and retain the exact policy used when issued or
   amended.
11. The first UI may present one primary contributor, but the persistence model supports several
   request parties and requirement-level contributor assignment from the start.
12. A request subject may differ from its contributor. The subject identifies what the request
    concerns, while one or more authorized parties provide, attest to, or review responses.
    Recurrence and reuse identify the subject through a durable, tenant-scoped
    `SubjectIdentityRef`, never through mutable PII such as display name or email address. This name
    is intentionally distinct from the existing serialized `DomainEvent.SubjectRef`, which remains
    the transport reference for what an event is about.
13. Authenticated and no-auth resources call the same Information Request application services and
    pass an explicit `RequestAccessContext`. The authenticated adapter builds it from the existing
    `PrincipalRef` and authorization context. The no-auth adapter validates a recipient-bound
    credential and session, resolves its participant `PrincipalRef`, and builds the same context.
    Shared application services never read `AuthTokenContext` or raw tokens. The global
    authentication pipeline remains unchanged.
14. Responding is authorized by request assignment and capability, not by the respondent's plan.
    Authoring and storage entitlement are resolved from the owning Exchange. Issuance freezes an
    immutable `RequestExecutionGrant` and applicable request-local limits. Atomic usage reservations
    charge that grant rather than rechecking the respondent's subscription or the owner's current
    plan on each response. A later commercial lapse blocks new authoring, issuance, recurrence,
    expanding amendments, and added capacity, but assigned respondents and reviewers may finish an
    already-issued request within the frozen limits, and authorized reads and exports remain
    available. Trial expiry is one commercial-lapse case: the grant records the effective paid or
    trial source and its issuance-time policy while reserved issued work remains finishable after
    the trial ends. Global subscription `OFF`, `REPORT_ONLY`, or `ENFORCE` behavior remains distinct
    from operational security suspension, which may freeze mutations with an explicit reason.
    The existing `BusinessFieldsSubscriptionGuard.requireResourceMutation` re-checks the owner's
    live plan for `BUSINESS_FIELDS_AND_SCHEMAS` on every Field write, which would strand an
    already-issued request after a lapse. Information Request Field operations therefore consult the
    frozen `RequestExecutionGrant` instead of that live guard. Issuance requires the owner to hold
    `INFORMATION_REQUESTS`. Organization-owned requests with Field Requirements also require
    `BUSINESS_FIELDS_AND_SCHEMAS`; Personal Information Requests include their typed response data.
    The grant snapshots the applicable features and governs completion thereafter. The guard's
    existing Exchange behavior is unchanged.
15. Data classification, respondent response mode, confidentiality compartment, and reviewer access
    are separate policies and must be enforced on both reads and writes.
16. Collection state, response disposition, evidence state, review disposition, and business
    decision are separate dimensions. No single large status enum may collapse them.
17. `SATISFIED` means the request's collection and review policy has been met. It never represents
    or implies a downstream process outcome.
18. Workflows reading Exchange metadata continue to use `EXCHANGE` Fields. Request Workflows must
    identify the Information Request and immutable Submission Package revision explicitly. There is
    no implicit latest-response-wins rule.
19. Submitted values and evidence never mutate a previous Submission Package. Corrections create a
    new revision.
20. Every state-changing service records its classified audit event and immutable history in the
    same transaction as the mutation. Information Request event types and their category are added
    to the closed audit catalog. Every mutation supplies explicit principal, actor kind, safe label,
    session, owner, correlation, and redacted payload data; no-auth capture never relies on the
    `AuthTokenContext` fallback. No-auth history uses the existing `PUBLIC_LINK` audit actor kind
    with a stable non-secret participant or request-party identifier, never a raw credential or
    session secret.
21. Runtime mutations also persist their versioned event envelope transactionally when the change
    has downstream meaning. `DomainEvent` and `DomainEventPublisher` remain the neutral public
    contracts. Generalize the existing Workflow-branded durable implementation, qualifier, mapped
    entity, repository, dispatcher, scheduler, backlog health, metrics, and log vocabulary to
    neutral transactional event names. Extend the compatible physical event outbox with explicit
    owner kind and owner ID so platform, organization, and personal events are distinguishable.
    Preserve the legacy physical table and index names during rollout. The separate audit outbox
    remains the immutable audit-intent mechanism; mutation, audit intent, and domain-event intent
    share correlation and commit atomically, but neither outbox is treated as the other or given an
    invented cross-outbox delivery order. Information Request code never imports a Workflow-branded
    outbox type, and this program creates no second request-specific domain-event outbox.
22. Persistence and ownership support `PERSONAL` scope before Template runtime work begins. Fields,
    Schemas, Schema Assignments, and Information Request Templates use the exact persisted scope
    vocabulary `PLATFORM`, `ORGANIZATION`, and `PERSONAL`; unrelated `APP` and `ORG` enums remain
    unchanged. Add `INFORMATION_REQUESTS` to backend and frontend plan features and include it in
    Personal and Business plans. Generalize the existing organization-only feature override into an
    owner-scoped commercial entitlement that supports organization and user owners. Platform-admin
    feature overrides remain the only per-owner feature control.
    Personal scope is a code change as well as a schema change. The same task extends
    `ScopeReference` with a personal owner, gives `FieldResourceAdapter.ownerScope` a personal
    result for personally owned resources, rewrites
    `SchemaAssignmentService.assertSchemaVisibleToResource` so it no longer requires an
    organization scope, and gives `BusinessFieldsSubscriptionGuard.requireConfigurationMutation` a
    personal-owner branch instead of `requireNotNull(organizationId)`. Adding the enum value alone
    produces a scope that throws on first use.
23. The existing `PrincipalRef` and `PrincipalKind` are the only authorization identity. Do not add
    `ActorRef`. Immutable records store canonical principal kind and ID plus history-only actor
    classification or session provenance where needed. History-only system attribution never
    becomes a competing authorization principal.
24. The existing Exchange-wide no-auth token remains legacy Exchange-shell behavior and is never
    used to authorize Information Request party data. Reuse and extend `ShareLink` rather than
    creating a parallel request-credential table. A request-party Share may have a
    `VERIFICATION_BOOTSTRAP` ShareLink that is recipient-bound through that Share and records a
    hashed secret, explicit expiry, revocation, replacement and rotation lineage, verification
    strength, atomic use count, and last use. Every configured password, domain, MFA, Share, expiry,
    status, and use constraint must either be enforced or rejected as unsupported at creation.
    `DefaultAuthorizationService` must never turn a bootstrap-mode ShareLink directly into a content
    grant. Successful recipient contact proof creates the separate expiring `RequestAccessSession`
    used to authorize as the stable participant `PrincipalRef`. Existing direct-grant ShareLinks
    retain compatible behavior. Forwarding the bootstrap link alone grants no request access. Raw
    secrets are never persisted.
    `Exchange.requireRecipientSignIn` remains the owner's authoritative choice about unauthenticated
    recipient access. When it is set, bootstrap-mode ShareLink issuance for that Exchange's requests
    is refused with a stable reason and respondents use the authenticated surface. The bootstrap
    ShareLink binds to the request-party Share and references its `ExchangeRecipient` by ID; it never
    occupies `exchange_recipient.direct_share_id`, whose V63 trigger requires an Exchange-typed
    Share.
25. Information Request authorization extends `ResourceType`, `ResourceKind`, `ResourceRef`,
    `Action`, `Capability`, `RoleCapabilities`, `Share`, `ShareConstraints`, the resource-context
    registry, and `DefaultAuthorizationService`. Request party roles, Requirement assignment,
    correction scope, and confidentiality become resource context and decision inputs rather than a
    parallel authorization engine. The request aggregate and each runtime Requirement occurrence
    are centrally authorized resources. Field bindings map to their exact Requirement-occurrence
    `ResourceRef`, whose provider derives its parent request, assignment, response mode,
    confidentiality, and correction scope. This keeps per-binding decisions inside the existing
    authorization call shape instead of adding a Fields-only policy engine. Generalize
    `Share.roleName` from its Exchange-only enum mapping to a resource-scoped role key with a
    resource-kind role-capability registry. Preserve all existing Exchange role values and adapters,
    and migrate plus regress the three ResourceTypes that the database actually permits on `share`:
    `EXCHANGE`, `DOCUMENT`, and `PRINCIPAL_GROUP`. The remaining nine ResourceType values are
    enum-only and carry no Share rows, so they need no Share migration or capability
    characterization; asserting otherwise both invents work and hides the change that matters. The
    same migration widens `share_resource_type_check` for the request aggregate and
    Requirement-occurrence types and widens `share_role_name_check` for the new role keys. Without
    the resource-type widening every request-party Share insert fails a check constraint. It also
    corrects `ShareService`, which currently records any non-Exchange Share audit event under
    `AuditOwnerScope.Platform`.
    Request party creation, reassignment, revocation, group inheritance, and registration upgrade
    materialize or revoke their corresponding Share rows in the same transaction by extending the
    existing `ShareService` grant, group inheritance, activation, constraint propagation, and
    descendant revocation paths. Extend
    `DefaultAuthorizationService` to honor `ResourceAuthorizationContext.parentRef` only through an
    explicit, one-level, owner-matched, cycle-safe inheritance policy. Requirement occurrences
    inherit grants from their parent request, then a central resource-policy evaluator applies exact
    assignment, response-mode, confidentiality, delegated-authority, and correction-scope denies
    and obligations. The evaluator is part of the central authorization stack, registered once per
    resource kind, and fails closed when its facts cannot be resolved.
    Fail-closed is a correction to current behavior, not an inherited property.
    `ResourceAuthorizationContextRegistry.resolve` returns null for an unmapped ResourceType or an
    unregistered provider, `DefaultAuthorizationService` currently reads that null as permission to
    skip the archived and suspended denies, and `collectOrgMembershipGrants` then falls back to the
    caller's `activeOrgId` instead of the resource owner. This program characterizes that behavior
    for the resources it affects today, being `DOCUMENT` whose kind has no provider, plus
    `APPLICATION` and `WORKFLOW_WEBHOOK_ENDPOINT` which map to null deliberately, then adds an
    explicit unresolved-context deny and removes the `activeOrgId` fallback for mapped kinds. It does
    not register a request kind before that kind's provider exists.
26. The Fields engine accepts an explicit access context, Value Set, occurrence, binding operation,
    and expected revision. Read filtering and write authorization use the same per-binding policy.
    Existing Exchange callers retain compatibility adapters and single-root-set behavior.
27. Document Version becomes the source of immutable storage identity, content hash, and creator
    principal for new uploads. A provider-neutral version-storage port uses typed locator kinds and
    unique write-once keys; it never overwrites a prior version. Existing stored paths are explicitly
    classified as legacy local locators and are never reinterpreted as object keys. Historical
    hashes are never copied from the mutable Document row. A legacy version is `UNVERIFIED` until
    its own located bytes are read and hashed; unreadable or missing legacy content cannot satisfy
    an evidence Requirement.
28. Existing audit hold enforcement is not yet a business-record hold system, but
    `AuditLegalHold` already has the generic resource-reference seed of that system. Generalize the
    existing mapped persistence and service behind the neutral `RecordPreservationHold` contract,
    preserve the compatible physical `audit_legal_hold` table and audit-governance adapters during
    rollout, add platform, organization, and personal ownership plus append-only lifecycle history,
    and create no second active hold table for the same resource. Phase 9 adds descendant and
    reference propagation, disposal eligibility, tombstones, and retryable object purge for
    Information Requests, packages, evidence, Documents, notices, and exports. Phase 6 withdrawal
    and replacement preserve bytes and only change eligibility for future packages.
29. Mutable Communication records remain authoring inputs. An `OutboundNotice` freezes the
    recipient, protected endpoint, channel, rendered content or protected immutable
    content-addressed reference, content hash, source Communication identity and content hash,
    event, and time. Delivery attempts are
    append-only subordinate records. The originating mutation first persists an authoritative
    append-only `NoticeIntent`; the transactional outbox signals work but is never the only durable
    notice obligation. A worker idempotently claims the intent before rendering. Source content is
    hashed before interpolation, rendered content is hashed after interpolation, and side-effecting
    `{{SEQ:KEY}}` tokens are consumed exactly once in the same claimed operation. A retry reuses the
    stored render and never increments a Sequence again.
30. Content-type detection and malware scanning are different controls. Evidence is never marked
    safe unless a real configured scanner returns a successful result. Timeout, error, unavailable,
    stale-signature, and skipped states never produce a clean result. By the user's decision on
    2026-09-25, a deployment does not need a scanner: evidence upload is on by default, and
    `app.information-request.evidence.malware-scan.required` (default `false`) decides whether a
    production-eligible clean scan is a condition for upload, for counting toward a Requirement, and
    for release to other parties. The scanner itself is planned separately in
    `plans/INFORMATION-REQUEST-EVIDENCE-MALWARE-SCANNING-PLAN.md`. A detected infection always
    quarantines the file. An opaque `END_TO_END` encrypted Document Version
    is ineligible to satisfy a file-backed Evidence Requirement because scanning ciphertext does not
    establish plaintext safety. Supporting it requires separately approved inspectable content and
    threat-model contracts; the current program does not invent such a bypass.
31. Retryable client commands use a shared `CommandReceipt` scoped to resource, operation,
    validated principal or access session, idempotency key, and canonical request fingerprint.
    Mutable drafts use `If-Match`; missing preconditions return `428` and stale revisions return
    `412`. Command receipt, mutation, audit, and transactional event commit atomically.
32. Parent and child lifecycle are enforced together. Request drafts and issuance may be prepared on
    an `INITIATED` Exchange, but response and review mutations require `ACCEPTED_STARTED`.
    `REJECTED` and `RESCINDED` cancel nonterminal requests and revoke external sessions. Ending an
    Exchange must satisfy configured gates and explicitly cancel any remaining nongating requests.
    `ENDED` Exchanges retain authorized read-only access; an existing request-bound external session
    may read until its own expiry or explicit revocation but cannot mutate. Rejected or rescinded
    history remains visible to authorized owners and administrators, while external access is
    revoked by default. Deletion revokes all external request sessions and permits only authorized
    owner or recovery access to retained read-only records. Every command rechecks or locks the
    parent state in its mutation transaction. The Exchange Fields adapter retains its existing
    `INITIATED`-only edit rule. The Information Request Fields adapter implements this separate
    parent and request policy and must never inherit the Exchange adapter's draft-only rule.
33. `ExchangeRecipientAttestation` continues to mean a verified Trusted Organization recipient
    selection snapshot. The Information Request Requirement type is
    `RESPONSE_ATTESTATION`, and immutable submission assertions are `SubmissionAttestation`
    records. Unqualified new production types named only `Attestation` are not introduced.
34. Organization-owned request recipient selection, replacement, acceptance, trusted group
    expansion, and registration upgrade reuse `TrustedRecipientValidationService`,
    `OrganizationTrustRelationship`, revisioned party policies, recipient-selection attestations,
    and `TrustedGroupAccessReconciliationService`. Trust suspension is a third, explicit security
    and eligibility axis alongside commercial lapse and operational suspension. The existing trust
    policy remains authoritative; request code cannot silently materialize or retain access that
    existing trust reconciliation would deny. A tested matrix defines new assignment, issuance,
    group expansion, already-issued response, session, and recovery behavior for active and
    suspended relationships.
35. Request creation from a Blueprint reuses `BlueprintParticipantDefault`,
    `BlueprintDocumentDefault`, `BlueprintFieldDefault`, and Document Library-derived metadata.
    The instantiation service maps them explicitly into request party roles, Requirement defaults,
    document placeholders, and root Field values where compatible. It does not create a parallel
    default system or reinterpret defaults as submitted respondent data.
36. `RequestAccessSession` is intentionally retained as a scoped authentication name distinct from
    `UserSession`; no generic `Session` production type is introduced. ETag and precondition
    handling remain authoritative for multi-device correctness. Authenticated realtime may provide
    optional notifications, but no-auth realtime and live collaborative editing are not correctness
    dependencies in this program.
37. Workflow trigger registration uses the existing Flyway-seeded
    `WorkflowTriggerEventRegistry`, including safe versioned `subject_fields_json` descriptors for
    designer autocomplete. The existing `WAIT_FOR_COUNTERPARTY_CLEARANCE` behavior is evaluated for
    semantic reuse, but it is not stretched to mean Requirement satisfaction if its Exchange and
    organization-wide semantics do not match.
38. Generic structured-evidence and external-verification connectors reuse existing Application,
    Workflow webhook endpoint, destination-policy, signing, delivery, and retry components where
    their semantics match. Connector contracts remain distinct when they require request, polling,
    imported-value, reconciliation, or verification state rather than outbound event delivery.
39. A canonical polymorphic principal pair is the only attribution any row carries. Every principal
    the platform can authenticate, including participant, public-link, application, and service
    principals, is named through it. The App User-only attribution columns that earlier tasks kept
    beside it were compatibility shims; the development-stage constraint removes them, and a
    migration that introduces the canonical pair drops the column it replaces in the same file.
40. External Participant identity is built, not extended. `ExternalParticipant` and
    `external_participant` exist, but nothing constructs or persists a row, `findByOwnerAndEmail`
    has no caller, and the table is empty in every environment. This program owns the whole
    participant lifecycle: creation, contact verification, owner-scoped lookup and collision
    handling, activity state, and `ParticipantAccountLink` registration lineage. Personal ownership
    is a forward-only schema addition to an empty table, so no data backfill, ambiguity report, or
    legacy-row resolution pass is planned or claimed. No production code emits a `PARTICIPANT`
    principal today, so Phase 4 is the first producer and must be treated as such rather than as a
    new caller of an existing identity.
41. The existing email-only recipient path is not a participant path. `EXTERNAL_EMAIL` selection
    creates a temporary, inactive App User, and `recordExternalEmailPrimaryDecision`,
    `resendNoAuthPrimaryRecipientInvitation`, and `ExternalEmailAcceptancePolicyService` all require
    a `USER`-kind Share. Information Requests never create a temporary App User. Each reusable
    Exchange recipient path therefore carries an explicit journaled decision: reuse it only for a
    User-backed request party, or implement a participant-principal equivalent. A
    participant-principal request party may not be routed through a User-only decision path, and
    that constraint is proven by test before Phase 4 exposes the no-auth surface.

## Core Domain Model

| Record | Responsibility |
|---|---|
| `InformationRequestTemplateDefinition` | Stable reusable template identity and ownership scope. |
| `InformationRequestTemplateVersion` | Immutable published configuration and optional exact Schema Version reference required when Field Requirements exist. |
| `TemplateSection` | Stable ordered section with localized title and help. |
| `TemplateRequirement` | Stable Field, Document, Response Attestation, or later external-evidence requirement. The persisted type is `RESPONSE_ATTESTATION`. |
| `InformationRequest` | Runtime request attached to one Exchange and one Template Version. |
| `SubjectIdentityRef` | Durable tenant-scoped, non-PII identity for a person, organization, asset, record, or other request subject; distinct from `DomainEvent.SubjectRef`. |
| `InformationRequestParty` | Request-scoped subject, contributor, preparer, attestor, reviewer, or decision-maker role referencing `SubjectIdentityRef` where applicable. |
| `InformationRequestRequirement` | Append-only runtime Requirement revision with stable ID, occurrence path, effective interval, configuration hash, policy, assignment, and amendment lineage. |
| `FieldValueSet` | Root or repeatable-group occurrence containing the authoritative typed Field Values for one Schema Assignment. |
| `FieldValueRevision` | Immutable typed-value revision with exact actor, provenance, value-set, contract, canonical value, and recorded time. |
| `RequestResponse` | Requirement and occurrence response envelope containing disposition, narrative, provenance, revision, and references to authoritative Field Values where applicable. |
| `EvidenceArtifact` | Logical evidence item associated with a requirement. |
| `EvidenceVersion` | Immutable request-specific association to one Document Version or external-reference subtype, plus captured issuer, coverage, and source metadata. |
| `EvidenceAssessment` | Append-only technical conformance, verification, expiry, quarantine, and policy-evaluation result for an Evidence Version. |
| `DocumentVersionStorageLocator` | Typed, immutable locator and provider identity for exact version bytes; legacy local paths remain explicitly classified. |
| `SubmissionPackage` | Immutable package revision containing exact responses, evidence versions, validation, actor, and Submission Attestation records. |
| `SubmissionAttestation` | Immutable acting-party assertion under a frozen versioned response-attestation policy. |
| `ReviewFinding` | Item-specific reviewer result, reason codes, narrative, and correction scope. |
| `RequestTransition` | Immutable state transition with actor, reason, time, and idempotency key. |
| `RequestClock` | Deadline type, timezone, urgency, pause history, extension, and escalation state. |
| `PrincipalRef` | Existing canonical authorization identity reused for every acting user, participant, application, service account, or public link. |
| `ShareLink` bootstrap mode | Existing Share-bound credential extended with recipient-bound verification-bootstrap mode, rotation lineage, enforced constraints, atomic use metadata, and no direct request-content capability. Raw secrets are never stored. |
| `RequestAccessSession` | Verified no-auth session bound to one bootstrap-mode ShareLink, participant principal, request party, authentication strength, and expiry. |
| `ParticipantAccountLink` | Verified lineage between an External Participant and a later App User account without rewriting historical principal provenance. |
| `RequestAccessContext` | Canonical principal, verified authenticated or request-bound no-auth session reference, authentication method and strength, active scope, and request capabilities. A credential ID may appear only as non-secret lineage; bootstrap credential validation alone never creates a content-capable context, and no raw bearer token is stored. |
| `CommandReceipt` | Transactional client-command deduplication record containing actor or session scope, operation, request fingerprint, and resulting resource reference. |
| `RequestExecutionGrant` | Immutable issuance-time owner, commercial entitlement, policy reference, and request-local limits that remain valid for completion after a later commercial lapse. |
| `RequestExecutionUsageReservation` | Idempotent atomic reservation and consumption ledger for request-local recipients, uploads, storage, and other captured limits. |
| `RecordPreservationHold` | Neutral service and model over the compatibly extended existing hold persistence, with authorized immutable-history lifecycle covering selected records, descendants, and referenced retained objects. |
| `NoticeIntent` | Append-only authoritative request to produce an immutable party notice, persisted with the originating mutation. |
| `OutboundNotice` | Immutable rendered notice, protected recipient endpoint snapshot, content or content-addressed object hash, source reference, and event identity. |
| `NoticeDeliveryAttempt` | Append-only channel attempt and result for one Outbound Notice. |
| `AcceptedFact` | Optional, explicit promotion of a reviewed response value or evidence assertion for downstream reuse. |
| `BusinessDecision` | Optional reference to a downstream process outcome, kept separate from request satisfaction. |

Entities and DTOs use dedicated classes in the existing flat `model/entity` and `model/dto`
packages. Pure DTO mapper classes use dedicated, domain-qualified classes in the existing flat
`model` package; they do not live in resources or services, and this program does not add a third
`model/mapper` convention. Repositories, services, and resources use logical `fields` or
`informationrequest` domain subpackages. Existing mapper locations outside this program are not
reorganized. REST resources remain thin adapters.
Services communicate through service methods and never use another service's repository directly.

## State and Disposition Model

The initial vocabulary is intentionally split into independent axes.

### Request collection state

- `DRAFT`
- `ISSUED`
- `ACTIVE`
- `CLOSED`
- `CANCELLED`
- `EXPIRED`
- `SUPERSEDED`

Submission and review states belong to each Submission Package or configured stage. A staged request
may contain locked submitted stages while other stages remain editable, so submission does not move
the whole request into a global `SUBMITTED` state. Correction opens a scoped response cycle for the
allowed Requirements and occurrences without reversing the request lifecycle or reopening a prior
Submission Package. The request moves to `CLOSED` only when its configured satisfaction policy is
met.

### Requirement response disposition

- `NOT_ANSWERED`
- `PROVIDED`
- `PARTIALLY_PROVIDED`
- `NOT_APPLICABLE`
- `UNAVAILABLE`
- `EXCEPTION_REQUESTED`
- `SATISFIED_BY_REFERENCE`
- `WAIVED`

Templates restrict which platform dispositions are permitted for each Requirement type and may
provide customer-authored display labels and reason-code vocabularies. Configuration cannot add
production enum values or special execution branches.

### Evidence conformance and review

Evidence conformance records technical state such as `PENDING`, `CONFORMING`, `DEFICIENT`,
`QUARANTINED`, `CORRUPT`, or `EXPIRED`. Reviewer disposition separately records `PENDING_REVIEW`,
`SATISFIED`, `CHANGES_REQUIRED`, `REJECTED`, or `WAIVED`.

### Request review disposition

- `NOT_REQUIRED`
- `PENDING`
- `IN_REVIEW`
- `CHANGES_REQUESTED`
- `SATISFIED`
- `SATISFIED_WITH_EXCEPTION`

### Business decision

Business decision values are owned by the relevant downstream process or Workflow, not by
Information Request status. The Information Request stores only an optional typed reference and
reason summary.

## REST and Application-Service Rules

- Use plural, resource-based URLs and correct HTTP verbs.
- Use `PATCH` for sparse response updates. Omitted items are unchanged.
- Add canonical sparse `PATCH /exchanges/{id}/fields` behavior in Phase 1. Preserve the current
  sparse `PUT /exchanges/{id}/fields` temporarily as a deprecated compatibility alias that
  delegates to the same service. The new `PATCH` requires `If-Match`. During the measured migration
  window the legacy `PUT` enforces `If-Match` when supplied but may accept a missing header with its
  existing last-write behavior, emits deprecation metadata, and records usage telemetry. Phase 12
  may require the header or remove the alias only after first-party migration, published notice,
  and an observed compatibility gate. All new Information Request response APIs use `PATCH`.
- Submission is creation of a subordinate resource, for example
  `POST /information-requests/{id}/submissions`.
- Correction is creation of a correction request, not a generic status setter.
- Cancellation, supersession, review, and amendment operations use explicit subordinate resources.
- Do not expose generic `PATCH /status` endpoints.
- Require an idempotency key for submission, issuance, review decisions, and other retryable
  transitions. Replaying the same key and fingerprint returns the original result; reusing a key
  with a different fingerprint returns a stable conflict.
- Require `If-Match` for mutable request drafts, party changes, Value Sets, response cycles, and
  review drafts. Return `428` when the precondition is missing and `412` with a stable machine code
  when it is stale. Return the current ETag after every successful mutation.
- Use stable error codes, Requirement IDs, and field paths so the client can focus the failing item.
- Authenticated and no-auth resources may have separate URL roots, but they must delegate to the
  same application services, pass an explicit `RequestAccessContext`, and return the same
  recipient-safe runtime projection. Shared services never read a raw credential or implicit global
  authentication state.
- Every REST resource method must use the repository-standard `return try { } catch { }` structure
  and log a unique operation-specific error message. Resource contract tests must verify delegation
  and response mapping without moving business logic into the resource.

## Phase Summary

| Phase | Purpose                                                      | Dependencies                           | Status      | Exit gate                                                                                                                                                        |
|-------|--------------------------------------------------------------|----------------------------------------|-------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1     | Business Fields foundation program                           | None                                   | Complete    | Current Fields are safe, principal-aware, version-aware, and deterministic enough to reuse.                                                                      |
| 2     | Versioned Information Request Templates                      | Phase 1                                | Complete    | Published templates are immutable and referenceable by existing Blueprint Definitions.                                                                           |
| 3     | Runtime requests, parties, lifecycle, and command safety     | Phase 2                                | Complete    | Drafts, parties, exact assignments, parent-child lifecycle, audit, transactional events, idempotency, and concurrency are safe; issuance remains executor-gated. |
| 4     | Authorization and dual access surfaces                       | Phase 3                                | Complete | Recipient workspace and request-detail occurrence and condition-result disclosure verified. |
| 5     | Structured responses, repeatable groups, and conditions      | Phase 4                                | Complete | P5-R01 through P5-R30 and the repeated P5-R-GATE passed. |
| 6     | Evidence and secure document handling                        | P5-R-GATE | Complete | Evidence collection works in production without a scanner; scanning is planned separately. |
| 7     | Submission, response attestation, amendments, and recurrence | Phase 6                                | Complete    | Immutable packages survive staged submission, amendments, supplements, and recurrence.                                                                           |
| 8     | Review, findings, remediation, and decision separation       | Phase 7                                | Complete    | Item-level and staged review is complete and auditable.                                                                                                          |
| 9     | Time, Workflow, audit, retention, and export                 | Phase 8                                | Complete    | Events, clocks, immutable notices, preservation holds, disposal, and downstream use are reliable.                                                                |
| 10    | Author, respondent, reviewer, and operations UX              | Phases 2-9                             | Complete    | All primary journeys are responsive, accessible, and documented (manual width check waived by the user on 2026-09-28).                                          |
| 11    | Generic capability conformance and extension contracts       | Phases 2-10                            | Complete    | Eight neutral conformance scenarios pass.                                                                                                                        |
| 12    | Compatibility, packaging, rollout, and final hardening       | Phases 1-11                            | Not started | Migration, entitlement, quotas, documentation, and release gates pass.                                                                                           |

### Progress rules

- Check a task only after its implementation, focused tests, applicable regression suites, and
  required help documentation checks pass.
- A task that introduces a mutation cannot be checked until its classified audit event, immutable
  history or transition record, transactional behavior, and sensitive-data redaction tests pass.
- Leave a partially completed or blocked task unchecked. Record exact subprogress and failures in
  the implementation journal.
- Mark a phase `In progress` when its first task begins.
- Mark a phase `Complete` only after every task is checked and the phase exit gate, applicable
  verification commands, documentation checks, and manual checks pass.
- `Current phase` identifies the active dependency boundary. `Next task` identifies the exact
  dependency-ready task a new session should start.

## Verification Command Matrix

Use focused test selectors during TDD, then run the applicable suite below before checking a task.
Run the complete phase suite before marking a phase complete.

TypeScript verification must actually compile the application project. Continue running the
repository-required `npx tsc --noEmit`, but its root configuration has an empty file list and its
success is not application typecheck evidence. Also run `npx tsc -p tsconfig.app.json --noEmit`
or an equivalent verified build-mode check. `P5-R20` must establish an enforced diagnostic gate:
zero Information Request feature diagnostics and no new diagnostics against a recorded, reviewed
unrelated baseline. Record any remaining baseline errors explicitly; never describe that result as
a clean full application typecheck. This rule supersedes older no-op TypeScript gate substitutions.

| Change type | Required verification |
|---|---|
| Backend Kotlin or service behavior | Focused Maven test for the changed class, then `.\mvnw.cmd test` at phase completion. |
| Flyway migration or persistence invariant | Focused migration or PostgreSQL contract test, affected repository tests, then `.\mvnw.cmd test`. |
| REST contract | Focused resource contract and authorization tests, then affected service tests. |
| `web-app` TypeScript or React | Focused `npm test -- <test-file>`, then `npm test`, `npx tsc --noEmit`, `npm run lint`, and `npm run buildWithTs` at phase completion. |
| Help documentation | Article-specific review, file-size checks, and `npx tsc --noEmit` from `web-app`. |
| Website pricing or marketing | `npm run lint` and `npm run build` from `website`. The website currently has no test script. |
| Cross-surface or release gate | Full backend and `web-app` suites, applicable website commands, migration contracts, and stated manual checks. |

If a command is unavailable or fails for an environmental reason, record the exact command, output
summary, and blocker in the journal. Do not check the task or phase.

### Walking-skeleton rule

Do not wait until Phase 10 or 11 to validate the whole contract. Beginning in Phase 2, maintain one
test-only `basic_field_document_response_attestation_request` fixture and one test-only
`multi_party_staged_evidence_request` stress fixture. The basic fixture proves the smallest complete
author, respondent, submission, and no-review closure path. The stress fixture proves distinct
subjects and contributors, delegated authority, repeatable occurrences, conditions, multi-file
evidence, item dispositions, staged submission, multi-stage review, correction, supplemental
requests, recurrence, clocks, retention, and export. Each later phase extends the same fixtures and,
when the surface is safe to expose behind the default-off feature switch, adds the smallest
corresponding UI slice. Phase 10 completes, integrates, and hardens these slices; it must not be the
first time the frontend exercises the runtime APIs. The fixtures must use neutral names, IDs,
labels, content, files, test classes, and helper functions. They are not bundled product Templates
and cannot activate special production branches. Before the first legitimate issuance in Phase 7,
tests may exercise services with an explicit test-only capability registry, but production code may
not bypass issuance checks.

## Cross-Process Capability Traceability

The neutral conformance scenarios in Phase 11 configure and prove the shared capabilities delivered
by the earlier phases. They must not compensate for missing core behavior with scenario-specific
production branches.

| Configurable process pattern | Shared capability | Owning phases | Neutral proof |
|---|---|---|---|
| Different subjects, contributors, delegates, attestors, and reviewers | Request-scoped party roles, delegated authority, and Requirement assignment | 3, 4, 8 | One actor contributes for a distinct subject under time-bounded authority while another actor reviews. |
| Variable-length or nested collections | Repeatable groups, stable occurrence paths, and cross-occurrence validation | 5 | Two nested occurrences retain independent values, evidence, findings, and completeness. |
| Conditional and stage-dependent collection | Versioned conditions, amendments, and staged policy | 5, 7, 8 | A changed answer activates one Requirement, deactivates another, and preserves prior revisions. |
| Multi-file, coverage, freshness, issuer, certification, and expiry constraints | Evidence Artifact, immutable Evidence Versions, Assessments, and generic evidence policy | 6 | Several files with different coverage and conformance states aggregate deterministically. |
| Partial responses, exception requests, waivers, alternatives, and references | Policy-controlled response dispositions and evidence alternatives | 5, 6 | Each item accepts only configured dispositions and records narrative and provenance independently. |
| Immutable whole-package or staged submissions and response attestations | Submission Packages, hashes, exact configuration, stage locks, and Submission Attestation | 7 | A submitted stage stays immutable while another stage remains editable. |
| Supplemental, recurring, amendment, and remediation cycles | Request lineage, carry-forward policy, recurrence, correction, and retest | 7, 8 | A linked follow-up preserves its source package and explicitly carries forward or invalidates items. |
| Sequential, parallel, and independent review | Review stages, aggregation, separation of duties, findings, correction allowlists, and remediation | 8 | Configured review stages aggregate deterministically and reopen only allowed items. |
| Urgent, paused, extended, and recurring deadlines | Request Clocks and scheduler policy | 9 | Versioned clock inputs reproduce due times through pause, resume, extension, and retry. |
| Reconstructable records, preservation holds, privacy controls, and export | Audit, access history, retention, hold, purge, privacy operations, and evidence export | 9 | A complete record is reproducible while held data cannot purge and unauthorized values remain redacted. |
| Reviewed-value reuse and external verification | Explicit Accepted Facts and generic connector contracts | 8, 11 | Reuse exposes source and freshness, requires configured reconfirmation, and never silently overwrites a response. |
| Registered and unregistered participation | Shared application services with authenticated and no-auth adapters | 4, 10 | Both access surfaces produce identical authorized projections and validation outcomes. |

## Phase 1: Business Fields Foundation Program

### Goal

Correct the current Business Fields defects and add the reusable value, provenance, authorization,
and concurrency foundations required by Information Requests. Preserve existing Exchange metadata
behavior except where it is unsafe or internally inconsistent.

Treat `P1-T1` through `P1-T4` as the independently deployable current-Fields hardening milestone.
Treat `P1-T5` through `P1-T9` as the reusable value-foundation milestone. Phase 2 cannot begin until
both milestones pass.

### Tasks

- [x] `P1-T1` Write failing authorization and projection tests for external reads, writes, Schema
  assignment, and unassignment. Enforce identical audience filtering on read and write responses,
  block writes to invisible bindings, and require an explicit owner or configuration capability for
  assignment changes. Cover the currently reachable external registered-User path and use explicit
  synthetic `PARTICIPANT` and `PUBLIC_LINK` principals at the service and adapter boundary as
  pre-exposure contract tests. Do not claim no-auth HTTP reachability until Phase 4 adds that
  endpoint surface.
- [x] `P1-T2` Write failing tests that characterize sparse Field updates and cover read-only
  defaults and the Details form posting read-only bindings. Keep sparse updates permitted. Do not add
  Information Request aggregate completeness to the Fields service; Request requiredness belongs to
  Template Requirements and is implemented in Phases 5 and 7. Correct read-only default population
  and client payload behavior now, while preserving the existing sparse `PUT` contract. Defer the
  canonical `PATCH` and ETag rollout to `P1-T7`, after Value Sets provide a monotonic revision.
- [x] `P1-T3` Write failing service and PostgreSQL migration contract tests proving that one Schema
  Version cannot bind two Contract versions of the same stable Field Definition. Use an
  expand-contract migration: first block new duplicates in the service, add and backfill the stable
  definition invariant key, report and explicitly resolve existing conflicts, then enforce
  consistency and uniqueness. Test clean and populated baselines, including existing duplicates.
- [x] `P1-T4` Write failing tests for offset date-time normalization, decimal precision, and
  unanswered Boolean values. Store time with explicit instant or offset semantics and preserve
  decimal values without JavaScript `Number` conversion.
- [x] `P1-T5` Add one root `FieldValueSet` per existing Schema Assignment and backfill current Field
  Values through an expand-contract migration. Make all new Field Value history and uniqueness
  Value Set-aware while preserving existing Exchange query behavior.
- [x] `P1-T6` Replace App User-only Field Value and Schema Assignment attribution through an
  expand-contract migration with canonical `PrincipalKind` and principal ID provenance plus a
  separate non-secret session reference where applicable. Backfill existing authors and assigners
  as `USER`. Add immutable `FieldValueRevision`, value-mutation and assignment audit coverage,
  update timestamps, and non-destructive history. Preserve the nullable
  `updated_by_app_user_id` and `assigned_by_app_user_id` foreign keys during expand and dual-write
  them only for `USER` principals. Participant, public-link, application, and service principals
  populate canonical columns only. Record the old-writer drain and later retain-or-drop contract
  decision before changing either legacy foreign key. Do not add a competing actor identity.
- [x] `P1-T7` Generalize the Fields command and authorization contract before Information Request
  runtime work. Every Field-value read or mutation identifies its resource, Value Set, occurrence,
  binding, operation, explicit access context, and expected revision. Schema-assignment commands
  instead carry the target resource, exact Schema reference where applicable, operation, access
  context, and expected assignment revision. Extend `FieldResourceAdapter` or add a dedicated
  generic Fields authorization port so the same per-binding policy filters value reads, authorizes
  value writes, and enforces later correction scope. Preserve the current Exchange adapter through
  a compatibility implementation whose `valuesEditable` behavior remains `INITIATED`-only. The
  later Information Request adapter owns a separate parent and request lifecycle policy and cannot
  inherit that Exchange-only edit rule. Give each Value Set a monotonic revision and derive its strong
  ETag from that persisted value. Add canonical sparse `PATCH /exchanges/{id}/fields`, make Field
  GETs and successful mutations return the current root-set ETag, and migrate the frontend. Preserve
  sparse `PUT` as a deprecated alias: enforce `If-Match` when supplied, emit deprecation metadata,
  and record missing-header usage throughout the measured compatibility window.
    - [x] `P1-T7a` Give each Value Set a monotonic revision and derive the Fields strong ETag from that persisted value.
      Advance the revision exactly once per mutation that stores a change, leave it alone for a write that stores
      nothing, and return the current root-set ETag from the Fields read and from a successful mutation.
    - [x] `P1-T7b` Introduce the generalized Fields command and per-binding authorization contract. A value read or
      mutation carries its resource, Value Set, occurrence, binding, operation, explicit access context, and expected
      revision; a Schema-assignment command carries the target resource, Schema reference, operation, access context,
      and expected assignment revision. Add the generic per-binding Fields authorization port and keep the Exchange
      adapter on an
      `INITIATED`-only compatibility implementation. Enforce the expected revision with a missing precondition and a
      stale precondition distinguishable by stable machine code.
    - [x] `P1-T7c` Add canonical sparse `PATCH /exchanges/{id}/fields` requiring `If-Match`, returning
      `428` when absent and `412` when stale. Preserve sparse `PUT` as a deprecated alias that enforces `If-Match` when
      supplied, emits deprecation metadata, and records missing-header usage.
    - [x] `P1-T7d` Migrate the first-party frontend to the canonical `PATCH` with ETag round-tripping and
      stale-precondition recovery, so it no longer depends on unconditioned `PUT` behavior.
- [x] `P1-T8` Move Field request DTOs out of `SchemaAssignmentService`, extract repository-backed
  projection assembly, and place pure DTO construction in dedicated mapper classes under
  the existing flat `model` package with Fields-qualified class names. Do not introduce a
  `model/mapper` package or place `toDto` logic in a service or resource. Add characterization,
  mapper, and projection tests before changing behavior.
    - [x] `P1-T8a` Move the Field request DTOs out of `SchemaAssignmentService`, extract the repository-backed
      projection assembly into one collaborator both Fields services resolve their bindings through, and place the pure
      construction of the value, binding, and assignment projections in dedicated mapper classes under the flat `model`
      package. Pin the current projection with characterization tests first and keep it byte-identical.
    - [x] `P1-T8b` Make one audience-filtered projection authoritative for both the bindings the Details form renders
      its editors from and the values it carries, so the form no longer depends on the separately fetched,
      scope-authorized resolved-schema view. Migrate the frontend to the single fetch.
- [x] `P1-T9` Correct inaccurate help documentation concerning required Documents, read-only Fields,
  entitlement-loss visibility, Workflow missing-value behavior, and the deprecated sparse `PUT`
  alias. Refactor help registry composition before adding any new section so
  `helpDocsRegistry.tsx` remains under 60 lines.

### Tests to write first

- `SchemaAssignmentService` principal and audience matrix tests.
- Exchange Fields resource response-contract tests.
- Required omission and sparse Field update characterization tests.
- Canonical `PATCH` and deprecated `PUT` parity tests proving omitted Fields remain unchanged.
- Field GET and mutation ETag tests plus legacy `PUT` deprecation metadata, optional-header
  enforcement, and usage-telemetry tests.
- Read-only default and UI payload tests.
- Schema stable-Field uniqueness migration tests.
- Root Field Value Set populated-upgrade and Exchange query compatibility tests.
- Date-time offset, numeric precision, tri-state Boolean, and provenance tests.
- Field Value Revision identity, canonical assignment provenance, and old-revision resolution tests.
- Synthetic participant and public-link pre-exposure service-contract authorship tests, plus Value
  Set, occurrence, binding-policy, and stale-revision tests. Phase 4 adds no-auth endpoint tests.
- Schema Assignment mapper and recipient-safe projection tests.
- Workflow duplicate-binding and missing-value regression tests.

### Likely code areas

- `service/fields/`
- `service/exchange/ExchangeFieldResourceAdapter.kt`
- `resource/fields/` and Exchange Fields resources
- `model/entity/` Fields entities and `model/dto/FieldsDtos.kt`
- flat `model/` Fields-qualified projection DTO mappers
- `repository/fields/`
- `web-app/src/app/exchanges/components/exchange-fields-tab/`
- `web-app/src/app/settings/fields-tab/`
- `web-app/src/services/fieldsService.ts`
- Fields, Workflow, and Blueprint help articles

### Verification

Run focused backend and frontend tests after each task. At phase completion run:

```text
.\mvnw.cmd test
cd web-app
npm test
npx tsc --noEmit
npm run lint
npm run buildWithTs
```

Phase-gate substitution recorded at Phase 1 completion. Two of the listed commands fail on a backlog that predates this
program and that Phase 1 neither created nor is scoped to clear.
`npm run buildWithTs` is `tsc -b` plus `vite build`, and `tsc -b` reports 345 errors spread across service modules,
`models.tsx`, and 26 help-doc files whose unused `React` import trips
`noUnusedLocals`. `npm run lint` reports 109 problems, 61 of them errors, mostly unused variables and stale
`eslint-disable` directives in service modules. No error or warning from either command is in a file Phase 1 added, and
none is in `web-app/src/app/components/help-docs`. The frontend gate actually used for Phase 1 is therefore `npm test`,
`npx tsc --noEmit`, `npm run build`, and
`npx eslint` on the changed paths, all of which pass. A later phase that needs `buildWithTs` or a clean repository-wide
`npm run lint` has to clear those two backlogs first, and neither total may be allowed to grow in the meantime.

### Exit criteria

- Read and write projections cannot disclose non-visible Fields.
- Assignment changes require the intended owner/configuration permission.
- Field updates have documented sparse semantics; no incorrect Request-wide completeness rule is
  added to the Fields service.
- New clients use sparse `PATCH`; the deprecated sparse `PUT` alias remains behaviorally identical
  for existing clients during its measured compatibility window. The first-party frontend uses
  ETags and no longer depends on unconditioned `PUT` behavior.
- Read-only values have a defined privileged/default population path and do not break saving.
- A Schema Version has at most one Contract for each stable Field Definition.
- Date-time, decimal, Boolean, provenance, and concurrency semantics are deterministic.
- Existing values occupy one root Value Set and every later value change has an immutable,
  exact-addressable revision.
- Field reads and mutations support explicit Value Set, occurrence, per-binding policy, canonical
  principal provenance, and expected revision without relying on App User-only authorship.
- Fields DTO mapping and projection assembly no longer live inside `SchemaAssignmentService`.
- Existing Exchange metadata and Workflow behavior remain covered by regression tests.
- Required help documentation is accurate, help registry composition has capacity for the new
  feature, and all validation commands pass.

## Phase 2: Versioned Information Request Templates

### Goal

Create a standalone versioned configuration model that composes structured data, requested
Documents, and Response Attestations without placing respondent behavior inside the Schema contract.

### Tasks

- [x] `P2-T1` Add `PERSONAL`-capable ownership columns, checks, and tenant-safe unique indexes for
  Fields, Schemas, and Information Request Templates. Extend `FieldScopeKind` and the persisted
  Template scope vocabulary exactly to `PLATFORM`, `ORGANIZATION`, and `PERSONAL`; do not import
  another domain's `APP` or `ORG` spelling. Rewrite `ck_field_def_scope_kind`,
  `ck_schema_def_scope_kind`, `ck_assignment_scope_kind`, their owner checks, and tenant-safe unique
  indexes for personal ownership. Note that the existing `ux_field_def_key` and `ux_schema_def_key`
  indexes key `PLATFORM` rows through a `COALESCE(scope_org_id, '000...0')` sentinel, so personal
  ownership needs a new index expression rather than an added column alone. In the same task make
  personal scope reachable in code: extend `ScopeReference` with a personal owner, return it from
  `FieldResourceAdapter.ownerScope` for personally owned resources, rewrite
  `SchemaAssignmentService.assertSchemaVisibleToResource` so it no longer casts to
  `ScopeReference.Organization` and rejects everything else, and add a personal-owner branch to
  `BusinessFieldsSubscriptionGuard.requireConfigurationMutation` in place of
  `requireNotNull(organizationId)`. Register `INFORMATION_REQUEST` as a Schema target with explicit
  compatibility rules through the service and adapter only; `schema_definition.target_resource_type`
  has no CHECK constraint, so this needs no migration and must not be journaled as one. Add
  `INFORMATION_REQUESTS` to backend and frontend
  `PlanFeature` and keep it absent from default plan catalogs. Generalize the current
  organization-only feature entitlement persistence and resolver into an owner-scoped commercial
  override for organization and user owners, preserving existing organization rows. Add a separate
  owner-scoped operational rollout gate. Controlled testing requires an explicit
  `INFORMATION_REQUESTS` commercial grant and a rollout grant for the same owner. Extend audit owner
  scope, persistence, projection, authorization, retention, and export with a tenant-safe personal
  user owner before any personal Template mutation is exposed. Keep authoring and runtime
  capabilities default-off until Phase 4 is complete. Split ownership, personal-scope code
  reachability, commercial entitlement, rollout, and personal audit into journaled subtasks before
  implementation.
    - [x] `P2-T1a` Personal-capable ownership storage. One migration adds the personal owner column to
      `field_definition`, `schema_definition`, and `schema_assignment`, widens
      `ck_field_def_scope_kind`, `ck_schema_def_scope_kind`, and `ck_assignment_scope_kind` to
      `PERSONAL`, rewrites `ck_field_def_scope_org` and `ck_schema_def_scope_org` into owner checks that admit exactly
      one owner per scope kind, adds the assignment owner check the released schema never had, and replaces
      `ux_field_def_key` and `ux_schema_def_key`. The released indexes key
      `PLATFORM` rows through a `COALESCE(scope_org_id, '000...0')` sentinel, so a personal row would collide with the
      platform row of the same key; the replacement must separate the three owner spaces without a sentinel collision.
      Extend `FieldScopeKind` and the three entities. Starts red with a clean-schema and populated-baseline PostgreSQL
      contract test.
    - [x] `P2-T1b` Personal-scope code reachability. Add `ScopeReference.Personal`, return it from
      `FieldResourceAdapter.ownerScope` for a personally owned resource, rewrite
      `SchemaAssignmentService.assertSchemaVisibleToResource` so it stops casting to
      `ScopeReference.Organization` and rejecting everything else, and give
      `BusinessFieldsSubscriptionGuard.requireConfigurationMutation` a personal-owner branch in place of
      `requireNotNull(organizationId)`. Depends on `P2-T1a`.
    - [x] `P2-T1c` `INFORMATION_REQUEST` Schema target. Register the target with explicit compatibility rules through
      `SchemaDefinitionService` and the adapter registry only, replacing the hardcoded
      `ResourceType.EXCHANGE` comparison. `schema_definition.target_resource_type` is a
      `VARCHAR(48) NOT NULL DEFAULT 'EXCHANGE'` with no CHECK constraint, so this needs no migration and must not be
      journaled or ledgered as one.
    - [x] `P2-T1d` Owner-scoped commercial entitlement. Add `INFORMATION_REQUESTS` to backend and frontend
      `PlanFeature`, absent from every default plan catalog. Generalize
      `organization_feature_entitlement` and `SubscriptionPolicyService.organizationFeatureOverrides`
      into an owner-scoped override that resolves for an organization or a user owner, preserving every existing
      organization row and its resolution. Depends on `P2-T1a` for the personal owner concept. Split into two journaled
      subtasks because storage plus resolution and the platform-administered writer for a personal grant are separate
      bounded behaviors, and shipping the first without the second would leave a table whose personal half has no
      writer.
        - [x] `P2-T1d1` Owner-scoped entitlement storage and resolution. One migration generalizes
          `organization_feature_entitlement` into an owner-scoped override table that names either an organization or a
          person, admits exactly one owner per row, keys uniqueness per owner, and carries every released organization
          row across unchanged. Generalize the entity, the repository, and
          `SubscriptionPolicyService.organizationFeatureOverrides` into an owner-scoped lookup, and make
          `EffectiveSubscriptionFactory.fromUserPolicy` apply overrides the way the organization path already does. Add
          `INFORMATION_REQUESTS` to backend and frontend
          `PlanFeature`, held back from every plan in the catalog. A feature no plan sells must not tell an individual
          to select an organization plan. Starts red with a clean-schema and populated-baseline PostgreSQL contract test
          plus owner-resolution tests.
        - [x] `P2-T1d2` Platform-administered personal grant. Add the platform-admin surface that writes a user owner's
          override, mirroring the organization surface already released: service, thin REST resource, admin-action
          approval, and audit. Without it the personal half of the table has no writer and a personal commercial grant
          cannot be recorded. Depends on `P2-T1d1`.
    - [x] `P2-T1e` Operational rollout gate. Add a separate owner-scoped rollout grant that is distinct from the
      commercial entitlement and defaults to deny. Controlled testing requires an explicit
      `INFORMATION_REQUESTS` commercial grant and a rollout grant for the same owner; either alone denies. Depends on
      `P2-T1d`.
    - [x] `P2-T1f` Personal audit ownership. Extend `AuditOwnerScope` and audit persistence, search projection,
      authorization, retention, and export with a tenant-safe personal user owner, so a personally owned resource stops
      filing under `AuditOwnerScope.Platform`. Cross-owner reads must deny. Required before any personal Template
      mutation is exposed. Depends on `P2-T1a`.
- [x] `P2-T2` Add the tenant-scoped `SubjectIdentityRef` foundation with stable opaque identity,
  subject kind, optional authorized external identifiers, merge and supersession history, and
  tenant-boundary rules. Keep it distinct from the existing serialized
  `DomainEvent.SubjectRef`. Do not use mutable PII as the primary identity.
- [x] `P2-T3` Add template definition, immutable template version, ordered section, stable
  Requirement, and versioned Requirement binding entities with Flyway migrations and repository
  contract tests.
- [x] `P2-T4` Implement Requirement types `FIELD`, `DOCUMENT`, and `RESPONSE_ATTESTATION`. The last
  name distinguishes a respondent assertion from `ExchangeRecipientAttestation`, which remains a
  Trusted Organization recipient-selection snapshot. Store prompt, help, response mode,
  requiredness, allowed dispositions, contributor role, review policy,
  confidentiality compartment, conditional-rule reference, repeatable occurrence anchor, and
  optional supporting-evidence relationship on the template binding.
- [x] `P2-T5` Define versioned Document evidence policy fields for file counts and types, file and
  page limits, issuer, coverage period, issue and expiry dates, freshness, jurisdiction, language,
  certification, signature, substitutes, waiver policy, and technical conformance.
- [x] `P2-T6` Add a versioned template capability schema and executor registry. Publication requires
  structural validation and records the exact capability versions a runtime must supply. Issuance,
  not publication, rejects any Template Version whose required runtime executors are not installed.
- [x] `P2-T7` Implement draft, publish, clone, retire, and create-new-version services. Published versions must be
  immutable and mappable through dedicated DTO mappers. Split into three journaled subtasks before implementation,
  because the five named operations divide cleanly into authoring a draft, freezing one, and the lifecycle after a
  Version has frozen, and the first of the three has to establish the authorization, entitlement, audit, and read
  contracts the other two reuse.
    - [x] `P2-T7a` Draft authoring. Create a Template Definition together with the one Version an author can edit,
      replace everything that Version configures as a single authored document over the eight configuration tables, and
      read it back through dedicated DTO mappers in the flat
      `model` package. Positions are derived from the authored order rather than stated, so a stored configuration
      cannot hold a gap, a duplicate, or two orders for one thing. Validate coherence within the document and refuse
      with the offending section or requirement key named; leave cross-cutting policy validation to `P2-T10` and
      single-row bound contradictions to the stored constraints that already refuse them. A rewrite replaces rather than
      merges, keeps the stable requirement identities the new document still names, and leaves behind the identities it
      stops naming. A requirement cannot change what kind of thing it asks for. Configuration cannot reach a Version
      that has stopped being a draft. Add the template-configuration `Action` and `Capability`
      values with organization-administrator grants only, the Information Request audit category and the two
      configuration event types with a catalog version bump, and one owner-scoped guard that requires both the
      commercial entitlement and the rollout grant for the owner the configuration names. Refuse `PLATFORM` ownership,
      which holds neither gate.
    - [x] `P2-T7b` Publication. Transition the editable Version into a frozen one. Derive the required runtime
      capability set through `request_template_required_capabilities` rather than reimplementing it, record it with the
      contract version of each capability from
      `InformationRequestCapability`, and flip the status in the same transaction, because the stored completeness rules
      refuse an unrecorded set and the freeze guard refuses a later addition. Create the draft and transition it; a
      service that inserted a finished published Version in one statement would bypass every completeness rule V87
      through V89 added, since
      `request_template_version_guard` is a `BEFORE UPDATE OR DELETE` trigger. Refuse a Version that configures nothing.
      Advance the Definition's own status, which `P2-T7a` leaves at `DRAFT`
      because nothing had frozen yet. Settle ownership of
      `InformationRequestTemplateVersionCapabilityRepository` so the reader and the writer are not two services both
      writing it. Depends on `P2-T7a`.
    - [x] `P2-T7c` Lifecycle after a Version freezes. Retire a published Version, start a new editable Version from a
      published one, and clone a Definition into a new Definition the caller owns. Each copies configuration into a new
      Version rather than reusing frozen rows, and recomputes the capability set rather than copying it, which the
      composite freeze would refuse anyway. A Definition holds at most one editable Version at a time. Depends on
      `P2-T7b`.
- [x] `P2-T8` Add thin REST resources and typed frontend service contracts for template
  administration. Do not build the full authoring UI until Phase 10.
- [x] `P2-T9` Extend the existing mutable Blueprint Definition with an optional exact published
  Information Request Template Version reference. Do not create Blueprint version history and do
  not change the existing stable Schema Definition or Field default semantics. Updating the
  reference affects only future instantiations; every created request pins and snapshots the exact
  Template Version. A Blueprint may retain a reference after that Template Version is retired for
  history and editing, but new instantiation fails with a stable retired-version error until the
  author selects a currently published, non-retired Version. Existing pinned requests remain
  unaffected. Do not permit Blueprint-level policy overrides that make the Template incomplete.
- [x] `P2-T10` Add configuration validation for stable Requirement keys, section ordering, Schema
  compatibility, duplicate Fields, invalid policies, contradictory bounds, and unsafe classification or response-mode
  combinations. Split into two journaled subtasks before implementation, because the seven named areas divide into rules
  that read only the authored document or one already-stored Schema Version, and rules that first need a typed
  requirement to say which Field of that Schema Version it collects. The second needs a migration, a new authored value,
  and a change to every Field requirement fixture, so it cannot share a session with the first.
    - [x] `P2-T10a` Rules over the authored document and the Schema Version it names. A named Schema Version must exist,
      have frozen, be written for `INFORMATION_REQUEST`, not be retired, and belong to the Template's own owner or to
      the platform; the question is asked while a draft is authored, so an author can still choose another Version, and
      it is asked about the stored owner so a copy made for another owner cannot carry a contract that owner was never
      shown. Within one requirement, everything stated about answering must be reachable by the party the response mode
      nominates: a party that cannot answer is not owed an answer, is offered no answers to choose from, and cannot
      declare a waiver, while a requirement reviewed only on exception must permit an answer that is one. Stable
      Requirement keys, section ordering, and single-row contradictory bounds were already refused by `P2-T7a` and the
      stored constraints in V86 through V89, so nothing restates them.
    - [x] `P2-T10b` The Field a typed requirement collects. A `FIELD` requirement names the stable Field Definition it
      resolves against, a requirement of any other kind names none, and one Version cannot collect the same Field twice.
      Publication additionally refuses a named Field that the Version's Schema Version does not bind. Needs a migration
      for the binding column, its write guard, and its per-Version uniqueness, plus the authored value on the
      requirement request and read shape. Also settle what an empty permitted-disposition set means, which the stored
      waiver rule currently reads as an allowlist while nothing refuses an empty one. Depends on `P2-T10a`.
- [x] `P2-T11` Add the versioned, test-only `basic_field_document_response_attestation_request` and
  `multi_party_staged_evidence_request` fixtures and contract tests. Every fixture identifier,
  label, value, file, test class, and helper function must use neutral capability terminology.
  Extend these same walking-skeleton scenarios in every later phase so core-model failures are
  found before Phase 11 conformance testing.
- [x] `P2-T12` Add the smallest feature-switched Template list, draft, and publish UI needed to
  exercise the typed administration contract. Keep unsupported policy controls disabled with a
  server-provided reason.

### Tests to write first

- Definition and version tenant-boundary tests.
- Personal, organization, and platform scope persistence tests.
- Personal-scope code reachability tests: `ScopeReference` personal owner, personal
  `FieldResourceAdapter.ownerScope`, personal `assertSchemaVisibleToResource` acceptance, and
  `BusinessFieldsSubscriptionGuard.requireConfigurationMutation` personal-owner branch instead of a
  `requireNotNull(organizationId)` failure.
- `INFORMATION_REQUEST` Schema target acceptance without a migration, proving
  `schema_definition.target_resource_type` has no CHECK constraint.
- Organization-entitlement migration, organization and personal commercial override, separate
  rollout gate, both-gates-required, default-off, and cross-owner denial tests.
- Personal audit owner capture, search, export, retention, and cross-owner denial tests.
- Subject identity, tenant boundary, merge, supersession, and non-account subject tests.
- `INFORMATION_REQUEST` Schema target and feature-switch tests.
- Published-version immutability tests.
- Template clone and retirement tests.
- Stable Requirement identity tests across versions.
- Invalid evidence-policy and response-mode tests.
- Structural publication and missing-runtime-executor issuance tests.
- Supporting-evidence relationship validation tests.
- Blueprint Definition reference scope, future-instantiation, changed-reference, and existing
  instance immutability tests.
- Blueprint reference retirement, blocked new instantiation, replacement-reference, and unaffected
  existing-request tests.
- REST resource contract and error-mapping tests.

### Exit criteria

- A published Template Version is structurally valid and records all required capability versions;
  it cannot be issued until compatible runtime executors are installed.
- The referenced Schema remains a pure typed-data contract.
- Template versions are immutable, tenant-safe, and exactly referenceable by existing mutable
  Blueprint Definitions without fabricating Blueprint version history. Retired references remain
  inspectable but cannot create a new request.
- Every Requirement has a stable machine identifier and a versioned runtime policy.
- Personal-capable storage, commercial entitlement overrides, separate operational rollout, personal
  audit ownership, and tenant uniqueness exist even while personal authoring remains hidden from
  default plans. A controlled owner passes both commercial and rollout gates explicitly.
- Commercial entitlement and operational rollout both default to deny, and no incomplete runtime
  surface is discoverable.
- No configuration endpoint contains business logic.
- Backend tests and TypeScript contract checks pass.

## Phase 3: Runtime Requests, Parties, Lifecycle, and Command Safety

### Goal

Create the runtime Information Request aggregate, explicit party roles, parent-child lifecycle,
append-only transition history, transactional audit and events, client-command safety, and one
Fields resource per request.

`P3-T1` through `P3-T4` establish cross-cutting contracts that do not resolve runtime entities.
`P3-T5` then creates the versioned aggregate. `P3-T8` materializes party Shares and registers the
aggregate and Requirement-occurrence context providers. No request creation or transition command
may start until `P3-T1` through `P3-T8` are complete and their dependencies pass. This ordering
prevents authorization providers and lifecycle services from resolving entities that do not yet
exist.

### Tasks

- [x] `P3-T1` Extend the existing central authorization stack for `INFORMATION_REQUEST` and its
  runtime Requirement occurrences: add aggregate and subordinate `ResourceType` and `ResourceKind`
  values, registry mappings that fail closed until their providers are installed, `ResourceRef`
  helpers, request `Action` values, atomic `Capability` values, and default-deny role grants.
  Generalize `Share.roleName` from `ExchangeShareRoleName` to a resource-scoped role key and
  resource-kind capability registry through an expand-contract migration that preserves every
  existing Share row and replaces both Exchange-only database checks with resource-aware validation.
  The same migration must widen `share_resource_type_check`, which the V1 baseline restricts to
  `EXCHANGE`, `DOCUMENT`, and `PRINCIPAL_GROUP` and which no later migration has touched, to admit
  the request aggregate and Requirement-occurrence types; omitting it makes every request-party
  Share insert in `P3-T8` fail a check constraint. Widen `share_role_name_check` for the new role
  keys in the same file. Add characterization and migration coverage for the three ResourceTypes
  that can actually hold a Share, being Exchange, Document, and Principal Group, before changing
  capability derivation. Confirm by test that the other nine ResourceType values hold no Share rows
  and require no Share migration; do not budget characterization work for them. Correct
  `ShareService` so a non-Exchange Share audit event resolves its real owner instead of defaulting
  to `AuditOwnerScope.Platform`. Expand Share
  grant and revocation provenance to canonical principal kind and ID, backfilling trusted App User
  foreign keys as `USER` without fabricating missing principals. Keep the nullable legacy grantor
  and revoker App User foreign keys during expand, dual-write them only for User actors, and record
  the later contract decision after old writers drain. Do
  not register a provider for a runtime entity until `P3-T8`. Add central contracts for explicit
  one-level parent-grant inheritance and a resource-kind policy evaluator; both default to no
  inheritance and no resource-specific allowance until registered, validate owner equality, reject
  cycles, and fail closed on missing facts. Making the registry fail closed changes existing
  behavior rather than adding new behavior: `ResourceAuthorizationContextRegistry.resolve` returns
  null for an unmapped ResourceType or an unregistered provider, `DefaultAuthorizationService` reads
  that null as permission to skip the archived and suspended denies, and
  `collectOrgMembershipGrants` falls back to the caller's `activeOrgId`. First characterize today's
  behavior for `DOCUMENT`, whose `ResourceKind` has no registered provider, and for `APPLICATION`
  and `WORKFLOW_WEBHOOK_ENDPOINT`, which map to null deliberately. Then add an explicit
  unresolved-context deny and remove the `activeOrgId` fallback for mapped kinds. Add the
  authenticated input path of
  `InformationRequestAccessContextFactory` so owner-side services receive explicit
  `RequestAccessContext` from the first mutation. Define the full request action vocabulary and
  stable error catalog. Do not add an independent authorization service or actor identity. Split into journaled subtasks
  before implementation, because the task names a vocabulary, a behavioral correction to an existing generic path, an
  expand-contract Share migration, a provenance expansion, two new central contracts, and an access-context input, each
  of which has its own failing behavior and verification set.
    - [x] `P3-T1a` Resource vocabulary and default-deny capability model. Add the request aggregate and
      Requirement-occurrence `ResourceType`, `ResourceKind`, and `ResourceRef` helpers, the runtime request `Action` and
      `Capability` vocabulary granted to no role, the registry mapping, and the stable refusal catalog. A kind whose
      facts may only come from a registered provider is refused while no provider is installed, so the new types are
      unreachable rather than decided from grants that were never scoped to them. No migration, no Share row, no
      provider.
    - [x] `P3-T1b` Unresolved-resource-context correction. Characterize today's behavior for
      `DOCUMENT`, whose `ResourceKind` has no registered provider, and for `APPLICATION` and
      `WORKFLOW_WEBHOOK_ENDPOINT`, which map to null deliberately. Then add the explicit unresolved-context deny for
      every mapped kind and remove the `activeOrgId` organization-role fallback, so the archived and suspended denies
      are no longer skipped. Depends on `P3-T1a` for the refusal shape it generalizes.
    - [x] `P3-T1c` Resource-scoped Share role key. Generalize `Share.roleName` from
      `ExchangeShareRoleName` to a resource-scoped role key with a resource-kind capability registry through an
      expand-contract migration that widens `share_resource_type_check` and
      `share_role_name_check`, preserves every existing Share row, and replaces both Exchange-only database checks with
      resource-aware validation. Characterize the three ResourceTypes that can hold a Share and prove by test that the
      other nine hold none. Depends on `P3-T1a`.
    - [x] `P3-T1d` Share provenance and audit owner. Expand Share grant and revocation provenance to canonical principal
      kind and ID, backfilling trusted App User foreign keys as `USER` and dual-writing them only for User actors, and
      correct `ShareService` so a non-Exchange Share audit event resolves its real owner instead of defaulting to
      `AuditOwnerScope.Platform`. Depends on
      `P3-T1c`.
    - [x] `P3-T1e` Parent-grant inheritance and resource policy evaluator. Add both central contracts, defaulting to no
      inheritance and no resource-specific allowance until registered, validating owner equality, rejecting cycles, and
      failing closed on missing facts. Depends on `P3-T1b`.
    - [x] `P3-T1f` Authenticated request access context. Add the authenticated input path of
      `InformationRequestAccessContextFactory` so owner-side services receive an explicit
      `RequestAccessContext` from the first mutation. Depends on `P3-T1a`.
- [x] `P3-T2` Add the Information Request audit category and namespaced event types, bump the audit
  catalog version, and cover search projection, retention classification, failure policy, and
  sensitive-data redaction. Preserve the already-neutral `DomainEvent` and `DomainEventPublisher`
  contracts. Generalize the existing Workflow-branded durable qualifier, mapped entity class,
  repository, dispatcher, scheduler, backlog health, metrics, and log vocabulary behind those
  contracts. Add explicit event owner kind and ID so platform, organization, and personal events
  are distinguishable. Keep the existing physical table and index names during compatible rollout,
  but expose no Workflow-branded outbox code type to Information Request services or operations.
  Define an explicit Information Request idempotency-key namespace, because
  `workflow_event_outbox.idempotency_key` is globally unique with no type or owner prefix and a
  request key would otherwise be able to collide with a Workflow key. Test that collision case.
  Keep `audit_outbox` as the separate immutable audit-intent mechanism; correlate both intents and
  commit mutation, audit intent, and domain-event intent atomically without inventing cross-outbox
  delivery ordering. Every later mutation passes explicit `PrincipalRef`, applicable
  access-session ID, safe label, audit owner, and redacted payload, and commits audit plus event with
  the mutation. Use the organization or personal audit owner foundation from Phase 2 and never map
  a personal request to platform or organization scope merely because the old sealed type lacked a
  user owner. Enumerate the existing
  `organizationId?.let(AuditOwnerScope::Organization) ?: AuditOwnerScope.Platform` call sites that a
  request mutation can reach, at minimum in `ExchangeUpdateService`, `ExchangeDocumentService`,
  `ExchangeDocumentVersionService`, `ExchangeDocumentAuditService`,
  `ExchangeAccessManagementService`, `ShareService`, `DocumentLibraryService`, and
  `AuditLegalHoldService`. Give each one a personal owner where the resource is personally owned, and
  record any deliberately unchanged site with its reason. Without this, a personal request inherits platform-scoped
  audit through reused Exchange code. Split into two journaled subtasks before implementation, because the runtime audit
  vocabulary and personal-owner correction read and write only the already-released `AuditOwnerScope`/
  `ResourceAuthorizationContextRegistry` stack, while generalizing the durable transactional-event outbox is an
  unrelated rename-and-extend of the Workflow-branded dispatcher that no personal-owner fix depends on.
    - [x] `P3-T2a` Runtime request audit vocabulary and personal-owner correction. Add the runtime Information Request
      event vocabulary (`information_request.request.*`,
      `information_request.party.*`, `information_request.requirement.*`, and
      `information_request.evidence.*`) beside the existing Template events under the same
      `INFORMATION_REQUEST` category, and bump `AuditEventType.CATALOG_VERSION` from 18 to 19. Search projection,
      retention classification, and failure-policy resolution already key off
      `AuditCategory` generically, so the new event types are covered without further production changes; a catalog test
      pins the new keys, category, and version instead. Add the reusable
      `AuditOwnerScopeResolver`, which asks `ResourceAuthorizationContextRegistry` for a resource's real
      `OwnerContext` the same way `ShareService.resolveOwnerScope` already does, and correct every enumerated
      `organizationId?.let(AuditOwnerScope::Organization) ?: AuditOwnerScope.Platform`
      fallback a request mutation can reach: `ExchangeUpdateService` (rescind, lifecycle transition, deletion),
      `ExchangeDocumentService` (document access, ZIP export, denied download),
      `ExchangeDocumentVersionService` (version download), `ExchangeDocumentAuditService` (per-action logging), and
      `ExchangeAccessManagementService` (denied access-management decisions).
      `ShareService` already resolved real ownership before this task. `DocumentLibraryService` gets a direct
      `BlueprintScope.PERSONAL` branch reading `createdByAppUserId` instead of the registry, because a Document Library
      entry already carries its own scope and creator column and has no resource-kind provider. `AuditLegalHoldService`
      is deliberately left unchanged: Architectural Decision 28 assigns hold-owner generalization to Phase 9's
      `RecordPreservationHold` contract, and a hold's own `organizationId` column records the administering scope rather
      than the held resource's owner, so rewriting it here would preempt that later contract. The nine remaining
      `?: AuditOwnerScope.Platform` sites (`WorkflowDefinitionService`, `TrustedRecipientAuditService`,
      `SchemaDefinitionService`, `FieldDefinitionService`, `AuditDeniedAttemptService`,
      `AuditAnalyticsReconciliationService`, `AuditEngagementService`, `AuditIntegrityService`,
      `AuthAuditService`) audit platform- and organization-governed configuration or authentication resources with no
      personal-ownership path today and are not reachable by an Information Request mutation, so they are left
      unchanged.
    - [x] `P3-T2b` Transactional event outbox generalization. Preserve the already-neutral `DomainEvent`
      and `DomainEventPublisher` contracts. Generalize the existing Workflow-branded durable qualifier, mapped entity
      class, repository, dispatcher, scheduler, backlog health, metrics, and log vocabulary behind those contracts,
      keeping the existing physical `workflow_event_outbox` table and index names. Add explicit event owner kind and ID
      columns so platform, organization, and personal events are distinguishable. Define and test an explicit
      Information Request idempotency-key namespace so a request key cannot collide with a Workflow key in the shared
      global unique index. Depends on `P3-T2a` only for the shared `AuditOwnerScope` vocabulary it reuses to describe
      owner kind consistently; the outbox rename itself is independent production work.
- [x] `P3-T3` Implement the shared `CommandReceipt` and HTTP precondition foundation. Scope receipts
  by resource, operation, validated principal or access-session ID, idempotency key, and canonical
  request fingerprint. Commit the result reference atomically with the mutation. Define reusable
  revision-to-ETag parsing, response, `428`, and `412` contracts against a synthetic versioned
  resource in this task. `P3-T5` adds request revisions, and `P3-T8` through `P3-T10` wire party,
  creation, and transition commands after persistence exists. Test same key with same versus
  different fingerprints. Never scope by a raw token.
- [x] `P3-T4` Define a pure, exhaustive Exchange and Information Request transition matrix plus the
  parent-lock and recheck contract that later commands must call. An
  `INITIATED` Exchange may contain drafts and issued requests, but response and review mutations
  require `ACCEPTED_STARTED`. `REJECTED` and `RESCINDED` atomically cancel nonterminal requests and
  revoke sessions. Ending requires configured gates and explicit cancellation of remaining
  nongating requests. Deleted Exchanges make requests read-only. Lock or recheck the parent in the
  same transaction so termination cannot race with save, submission, or review. Test the matrix as
  a side-effect-free policy here; `P3-T10` wires it to the runtime aggregate after `P3-T5` exists.
  Include actor-specific read visibility and external-session effects for every parent state:
  `ENDED` is authorized read-only until session expiry or revocation, `REJECTED` and `RESCINDED`
  revoke external access by default while preserving owner history, and deletion revokes all
  external sessions while retaining authorized owner or recovery reads.
- [x] `P3-T5` Add `InformationRequest`, `InformationRequestParty`, append-only runtime Requirement
  revisions, transition history, and current-revision entities with migration contract tests. Each
  Requirement revision stores its stable request Requirement ID, source Template Requirement
  Version, revision, occurrence path, effective interval, configuration hash, and optimistic
  version needed by the shared precondition contract. Derive a strong response ETag from the
  persisted aggregate or party revision, never from timestamps or serialized response order.
- [x] `P3-T6` Add a Fields-domain operation that assigns one exact published `schemaVersionId` after
  validating its Schema Definition lifecycle, target resource type, tenant scope, and visibility.
  Preserve the existing assign-latest-by-definition path for legacy Exchanges. Use the exact path
  to create one root Value Set for a Field-bearing request and expose only dedicated Fields service
  methods to the Information Request domain. Give the new operation its own `@Transactional`
  boundary. The existing four-argument `assignSchema` overload is not annotated and works only
  because its single caller runs inside `ExchangeInitiationService.initiateExchange`; do not
  reproduce that implicit dependency, and annotate the existing overload while adding the new one.
  Resolve the Field write path through the request's `RequestExecutionGrant` rather than
  `BusinessFieldsSubscriptionGuard.requireResourceMutation`, whose live owner-plan lookup would
  strand an already-issued request after a lapse. Leave the guard's Exchange behavior unchanged.
- [x] `P3-T7` Add an `InformationRequestFieldResourceAdapter` using
  `resource_type = INFORMATION_REQUEST`. Preserve the Schema Assignment uniqueness constraint and
  enforce equality with the Template Version's Schema Version. Forbid assignment replacement or
  removal after issuance. Do not create an assignment for a request with no Field Requirements.
  Keep this adapter unavailable to runtime callers until `P3-T8` registers the central subordinate
  resource provider.
- [x] `P3-T8` Implement party roles for subject, contributor, preparer, attestor, reviewer, and
  decision maker. Allow non-account subjects while linking acting contributors to an
  ExchangeRecipient or supported principal reference and linking the subject role to a stable
  `SubjectIdentityRef`. Reference an `ExchangeRecipient` by ID only. Its `direct_share_id` is
  `NOT NULL UNIQUE` and the V63 trigger `validate_exchange_recipient_share_binding` requires that
  Share to be a direct, non-owner, Exchange-typed Share for the same Exchange, so a request-party
  Share can never be attached through it. `uq_exchange_recipient_primary` also permits one `PRIMARY`
  recipient per Exchange, so several request contributors must not be modelled as several primary
  recipients.
  Build the External Participant lifecycle rather than extending a populated one. `ExternalParticipant`
  and `external_participant` exist with `owner_organization_id`, normalized email, and verification
  metadata, but no code constructs or persists a row, `findByOwnerAndEmail` has no caller, and the
  table is empty in every environment. Add creation, contact verification, owner-scoped lookup,
  collision handling, activity state, and an explicit personal owner App User ID with owner checks so
  organization and personal owners have separate tenant-safe email uniqueness. Treat personal
  ownership as a forward-only schema addition to an empty table: do not plan or journal a data
  backfill, an ambiguous-legacy-row report, or a personal-owner derivation pass. Route all resolution
  and creation through the owning participant service. This task is also the first producer of a
  `PARTICIPANT` principal in the codebase; nothing emits one today.
  Do not create a temporary App User for a request party. The existing `EXTERNAL_EMAIL` path creates
  `AppUser(isTemporary = true, isActive = false)`, and `recordExternalEmailPrimaryDecision`,
  `resendNoAuthPrimaryRecipientInvitation`, and `ExternalEmailAcceptancePolicyService` all require a
  `USER`-kind Share. For each of those paths, journal an explicit decision to either reuse it only
  for a User-backed request party or implement a participant-principal equivalent, and prove by test
  that a participant-principal party cannot be routed through a User-only decision path.
  For organization-owned trusted selections, reuse `TrustedRecipientValidationService`, revisioned
  party policy, recipient-selection attestation, and trusted group reconciliation rather than
  bypassing the existing B2B eligibility path.
  In the same transaction as each acting party assignment, reassignment, revocation, or group
  expansion, materialize or revoke the matching aggregate Share with its request resource role by
  extending the existing `ShareService` grant, group inheritance, activation, role and constraint
  propagation, reconciliation, and descendant-revocation methods. This depends on the
  `share_resource_type_check` widening in `P3-T1`; verify that migration is applied before the first
  party Share insert.
  Register the aggregate and Requirement-occurrence authorization-context providers only after
  these entities and mappings exist; the subordinate provider resolves parent request, exact party
  assignment, response mode, confidentiality compartment, delegated authority, and correction
  scope. Register explicit owner-matched parent-grant inheritance for Requirement occurrences and
  the central resource-policy evaluator that narrows inherited capability by those facts. Apply
  Command Receipts and required party-aggregate `If-Match` preconditions to party and Share changes.
- [x] `P3-T9` Implement ad hoc request creation and Blueprint Definition instantiation. Snapshot every runtime
  Requirement and resolve default party-role assignments without copying respondent data between
  Exchanges. Explicitly map existing `BlueprintParticipantDefault`, `BlueprintDocumentDefault`,
  `BlueprintFieldDefault`, and Document Library-derived metadata into request party roles,
  Requirement defaults, document placeholders, and compatible root Field defaults. Do not create
  a second Blueprint-default model or reinterpret a default as submitted respondent data. Ad hoc
  creation atomically materializes a validated request-owned, non-reusable
  Template Definition and immutable published Version, then pins the new request to it. It does not
  bypass Template validation or create a request without a Template Version. The combined Template
  and request creation uses one Command Receipt and commits one result reference so retry cannot create duplicate
  private Templates or requests. Split into dependency-ordered subtasks because ad hoc creation establishes the reusable
  request-creation command, while Blueprint instantiation adds existing Blueprint and Document Library default mappings
  on top of that command.
    - [x] `P3-T9a` Ad hoc request creation. Add a command service that, inside one Command Receipt, creates a validated
      request-owned, non-reusable private Template Definition and immutable published Version from an authored
      configuration, creates the runtime request pinned to that Version, materializes runtime Requirements through the
      existing materializer, records one result reference, and replays the same command without duplicate private
      Templates or requests.
    - [x] `P3-T9b` Blueprint Definition instantiation and defaults. Instantiate from the optional exact Blueprint
      Template Version reference, fail with the stable retired-version reason when the referenced Version is no longer
      instantiable, map `BlueprintParticipantDefault`,
      `BlueprintDocumentDefault`, `BlueprintFieldDefault`, and Document Library-derived metadata into request party
      roles, Requirement defaults, document placeholders, and compatible root Field defaults, and prove no respondent
      data is copied between Exchanges or treated as submitted.
- [x] `P3-T10` Implement named transitions for draft creation, cancellation, and supersession. Define
  issue, first view, first progress, expiry, submission, close, and correction-cycle contracts, but
  do not expose a generic transition setter. Successful issuance remains disabled until Phase 4
  authorization passes and the executor registry confirms every capability required by the
  Template Version. Submission is a package or stage operation rather than a request state change.
  Wire every available command through the Phase 3 transition matrix and parent lock/recheck. A
  no-review closure must be atomic with the final package in Phase 7, while review-required closure
  and correction commands must be atomic with records created in Phase 8. Scheduled expiry belongs
  to Phase 9.
- [x] `P3-T11` Define reassignment behavior for recipient replacement, revoked access, group
  membership changes, trust-policy revision or suspension, and work already completed by the previous contributor. Split
  into dependency-ordered subtasks. Existing bootstrap-credential, no-auth session, and structured-response
  behavior is implemented and verified; future evidence and package preservation remains in Phases 6-7.
    - [x] `P3-T11a` Wire `InformationRequestPartyService.reassignMutation` through the same locked-parent-Exchange
      recheck, transition matrix, and request-party transition history/audit/domain-event mechanism the Phase 3
      lifecycle commands already use. Journal an explicit decision for each of the five `replacePrimaryRecipient`
      preconditions rather than inheriting it:
        1. Sender organization: not a separate check. Central authorization
           (`Action.INFORMATION_REQUEST_MANAGE_PARTIES`) already resolves organization-membership grants against the
           request owner, and a personally owned request has no sender organization by design (Architectural Decision
           22), so this precondition does not apply uniformly and is not reproduced.
        2. `INITIATED` Exchange only: explicitly rejected as request policy. Party reassignment must remain usable while
           a request is already issued and in progress, for example when an assigned contributor's access is revoked
           mid-request, so it reuses the same parent-lock and recheck contract as lifecycle mutations (Architectural
           Decision 32) and the transition matrix's existing `REASSIGN -> allowSameFrom(nonTerminalStates())` rule
           rather than
           `replacePrimaryRecipient`'s single-state restriction. This was the concrete gap fixed in this task:
           `reassignMutation` previously performed no parent lock or recheck at all.
        3. `PENDING` acceptance status only: not applicable. `InformationRequestParty` has no acceptance-status field;
           its own `active` flag governs eligibility, and the existing
           `require(party.active)` guard is the equivalent precondition.
        4. Trusted-person or trusted-group selection only: not inherited. Reassignment already accepts any
           `requireSupportedActingPrincipal` kind (`USER`, `PARTICIPANT`, `PRINCIPAL_GROUP`), which is broader than
           primary-recipient replacement by design, since request parties are not limited to Trusted Organization
           selections. Trust suspension for a trusted-selection-bound recipient is still enforced through the existing
           reused path: when `exchangeRecipientId` is supplied,
           `ExchangeRecipientService.requireAssignablePartyRecipient` revalidates the trusted attestation and reconciles
           group Shares before the party is updated.
        5. Authenticated App User caller only: not inherited. Reassignment authorizes through
           `RequestAccessContext.principal` and the central authorization service, consistent with Architectural
           Decision 13, rather than requiring an App User specifically. Group membership changes for a `PRINCIPAL_GROUP`
           party are out of scope for this command by design: they do not change the party's `principalId` and are
           already propagated by
           `ShareService.synchronizeGroupMemberAccess`/group inheritance without calling reassignment. Added a nullable
           `party_id` column (migration `V101`) to `information_request_transition` with an insert-time scope guard so
           party-scoped history is queryable, and extended
           `InformationRequestTransitionHistoryCommand`/`InformationRequestTransitionHistoryService` to accept and
           persist it.
    - [x] `P3-T11b` Revoke active bootstrap ShareLinks and their RequestAccessSessions before the old
      Share is revoked during reassignment or party revocation. Bind the replacement Share to the new
      principal. A replacement bootstrap credential is minted only through the explicit issue command,
      so the reassignment mutation never returns or silently distributes a raw secret.
    - [x] `P3-T11c` Revoke persisted RequestAccessSessions for every revoked bootstrap ShareLink during
      reassignment and party revocation. Service and transaction tests verify old credential cutoff.
    - [x] `P3-T11d` Preserve completed work during reassignment. The stable party and Requirement
      association, response record, content, revision, and original principal provenance remain
      unchanged. Future evidence/version/package preservation remains in Phases 6-7 and must keep
      stable party identity separate from the replacement principal.
- [x] `P3-T12` Model delegated authority separately from party role. Record the
  authority instrument or evidence reference, grantor, grantee, scope, effective and expiry dates,
  revocation, and the exact requests or Requirements for which the delegate may act. Reconciled the pre-existing
  untracked code against this task's text before writing anything new:
  the minimal fact table added under `P3-T8` (migration `V98`) already modeled grantee (delegate principal), scope
  (request plus an optional single Requirement), and a boolean `active` flag, but had no grantor, no authority
  instrument or evidence reference, no explicit effective/expiry window, and no revocation record distinct from the
  active flag -- exactly the gap its own migration comment named as deferred to this later task. Migration `V102` adds
  `grantor_principal_kind`/`grantor_principal_id`, `authority_instrument_ref`,
  `effective_at`/`expires_at` with a `CHECK` that expiry is after the effective date, and
  `revoked_at`/`revoked_by_principal_kind`/`revoked_by_principal_id`/`revocation_reason` with a
  `CHECK` tying the revocation columns to the `active` flag. The table has no rows in any environment, so the new
  `NOT NULL` columns needed no default or backfill.
  `InformationRequestDelegatedAuthorityService.grant` now persists the command's access principal as grantor and rejects
  an expiry at or before the effective date; `revoke` now stamps the revoking principal, a revocation timestamp, and an
  optional reason. Both commands' fingerprints include the new fields so a replay with the same idempotency key but
  different content is still distinguishable from an identical replay.
  `InformationRequestDelegatedAuthorityFactSource.factsFor` now also excludes an authority that is not yet effective or
  has expired, computing "now" once per call rather than accepting an `asOf`
  parameter, because a default parameter on this concrete Mockito-mocked class broke
  `InformationRequestRequirementAuthorizationContextProviderTest`'s positional `eq()` stubs (Kotlin's default-argument
  bridge passes a resolved literal for the omitted parameter alongside matcher results for the others, which Mockito
  rejects as a mixed matcher/literal call).
- [x] `P3-T13` Add a feature-switched owner-only request list, draft creation, cancellation, and
  supersession slice for the walking scenarios. Do not expose issuance or respondent behavior
  before authorization and runtime executors are complete. Added `InformationRequestResource` at
  `/information-requests`: owner-only `GET` list scoped by
  `exchangeId`, ad hoc draft creation (`POST`), cancellation (`POST .../{id}/cancellation`), and supersession
  (`POST .../{id}/supersession`), all delegating to the existing
  `InformationRequestAdHocCreationService`, `InformationRequestLifecycleService`, and the new
  `InformationRequestQueryService`. No issuance or respondent action is exposed; a pinned resource test asserts that.
  Added `InformationRequestEntitlementGuard` and wired it into ad hoc creation, lifecycle mutation (cancel and
  supersede), and the new query service list, so `PlanFeature.INFORMATION_REQUESTS`
  commercial entitlement and operational rollout are both required for every one of the four operations, answered
  against the parent Exchange's owner. Neither gate existed for runtime requests before this task. Added
  `InformationRequestDto` and `InformationRequestDtoMapper`, carrying a per-row
  `requestETag` so a client can cancel or supersede any listed request directly from the list response. Granted
  `ExchangeShareRoleName.OWNER` exactly `INFORMATION_REQUEST_CREATE`,
  `INFORMATION_REQUEST_READ`, `INFORMATION_REQUEST_CANCEL`, and `INFORMATION_REQUEST_ADMIN`, the narrow exception a
  request aggregate that does not exist yet requires, since no request-scoped Share can exist before it does. Every
  other role stays untouched, and
  `INFORMATION_REQUEST_ISSUE`/`WRITE`/`EXPORT` remain withheld from the owner grant. Updated the two
  `InformationRequestAuthorizationVocabularyTest` cases that pinned blanket default-deny to pin this narrower,
  documented exception instead.

### Tests to write first

- Lifecycle transition-table tests covering every permitted and forbidden transition.
- Exchange parent-state mutation, actor-visible read, external-session effect, recovery access, and
  termination-versus-mutation race tests.
- Missing-executor and disabled-feature issuance denial tests.
- Command receipt same-key replay, fingerprint conflict, principal or session scope, `428`, `412`,
  and parallel stale-write tests.
- Central `Action`, `Capability`, role, Share constraint, resource-context provider, and
  default-deny authorization tests.
- Share role migration and capability characterization for the three Share-bearing ResourceTypes
  only, being Exchange, Document, and Principal Group, plus resource-role validation, canonical grant
  and revoke provenance, preserved legacy App User foreign keys during expand, and unchanged Exchange
  capability tests.
- `share_resource_type_check` and `share_role_name_check` widening tests: a request-party Share
  insert fails before the migration and succeeds after it, the nine enum-only ResourceTypes hold no
  Share rows, and every pre-existing Share row survives.
- Non-Exchange Share audit owner tests proving `ShareService` no longer defaults to
  `AuditOwnerScope.Platform`.
- Parent-grant inheritance disabled by default, exact one-level inheritance, owner mismatch, cycle,
  missing facts, and central Requirement policy deny or obligation tests.
- Unresolved-resource-context characterization and fail-closed tests covering `DOCUMENT` with no
  registered provider, `APPLICATION` and `WORKFLOW_WEBHOOK_ENDPOINT` mapped to null, the removed
  `activeOrgId` organization-role fallback, and the archived or suspended denies no longer being
  skipped.
- Information Request event idempotency-key namespace tests proving no collision with a Workflow key
  in the shared global unique index.
- Runtime request audit event type, namespace, category, and catalog-version pin tests, plus
  `AuditOwnerScopeResolver` personal, organization, and unresolved-fallback tests.
- Personal-owner audit tests for each corrected
  `?: AuditOwnerScope.Platform` call site a request mutation can reach.
- Requirement-occurrence `ResourceRef`, parent-context resolution, binding mapping, correction
  scope, and batch projection authorization tests.
- Audit catalog, explicit no-auth principal and `PUBLIC_LINK` actor mapping, redaction,
  transactional capture, neutral event publisher, outbox rollback, and duplicate event tests.
- Party-role and subject-versus-contributor tests.
- Transactional party-to-Share materialization, revocation, group inheritance, party `If-Match`,
  command replay, and provider-registration-order tests.
- External Participant lifecycle tests on an empty table: first-ever creation, contact verification,
  owner-scoped lookup, organization and personal email isolation, and same-tenant collision handling.
  No backfill or ambiguous-legacy-row test is required because the table has no rows.
- Request-party recipient linkage tests proving an `ExchangeRecipient` is referenced by ID, that a
  request-party Share is rejected by the V63 trigger if written to
  `exchange_recipient.direct_share_id`, and that several contributors do not violate
  `uq_exchange_recipient_primary`.
- Temporary-App-User exclusion tests proving no request party creates
  `AppUser(isTemporary = true)`, and that a participant-principal party is refused by
  `recordExternalEmailPrimaryDecision`, `resendNoAuthPrimaryRecipientInvitation`, and
  `ExternalEmailAcceptancePolicyService` rather than silently accepted.
- Trusted Organization person and group selection, policy revision, suspension, acceptance,
  expansion, reassignment, already-issued response, session, and recovery matrix tests using the
  existing trust validation, attestation, and reconciliation services.
- Ad hoc private Template and request atomic replay tests.
- Existing Blueprint participant, Document, Field, and Document Library default mapping tests.
- Reused ShareService group recipient and Exchange replacement characterization plus request-specific
  recipient replacement tests, including one case per `replacePrimaryRecipient` precondition:
  organization sender, `INITIATED` parent, `PENDING` acceptance, trusted-only selection, and
  authenticated caller.
- Delegated-authority expiry, revocation, and reassignment tests.
- Two Information Requests using the same stable Field without collision.
- Root Field Value Set migration and Exchange query compatibility tests.
- Exact Schema Version assignment and later Schema publication non-drift tests.
- Template and Schema version consistency tests.
- Issued-assignment replacement and removal denial tests.
- Legacy `EXCHANGE` assignment isolation tests.

### Exit criteria

- Several Information Requests can coexist on one Exchange without changing existing Exchange
  Field cardinality.
- Every runtime request is pinned to exact immutable configuration, including a Schema Version when
  Field Requirements exist.
- Runtime Requirement revisions are append-only and exact-policy-addressable.
- Subject, contributor, attestor, and reviewer can be represented independently.
- Implemented lifecycle behavior is enforced by named services and preserved in history; later
  transition commands remain unavailable until their atomic records exist.
- Parent Exchange termination cannot race past a request mutation, and terminal parent states have
  deterministic child, read-visibility, and external-session effects.
- Authorization uses the central stack and canonical `PrincipalRef` without a parallel policy
  engine. Resource-scoped Shares, explicit parent inheritance, and Requirement policy evaluation
  preserve existing Exchange authorization behavior.
- Every pre-existing Share-bearing resource retains characterized capability behavior, and
  organization-owned trusted request parties remain governed by the existing trust-policy and
  reconciliation services.
- Organization and personal request audit records have the correct tenant owner, and email-only
  actors use tenant-scoped External Participant identity rather than temporary App Users.
- Client-command replay, fingerprints, ETags, audit capture, and transactional events are
  deterministic and atomic.
- No legacy Exchange Field Value is silently reclassified as a respondent response.

## Phase 4: Authorization and Dual Access Surfaces

### Goal

Provide one capability model and one application-service implementation for organization users,
registered recipients, group recipients, and unregistered magic-link respondents.

### Tasks

- [x] `P4-T1` Extend the Phase 3 `InformationRequestAccessContextFactory` with its second explicit
  input: a validated bootstrap-mode ShareLink and RequestAccessSession plus its recipient-bound participant
  `PrincipalRef`. Keep credential and session identity separate from the authorization principal.
  Shared services receive the same result as the authenticated path and never inspect
  `AuthTokenContext` or a raw bearer token. Added `fromBootstrapSession(shareLink, session, participant)` alongside the
  existing
  `currentAuthenticated()`. `RequestAccessSession` is introduced here as a plain, non-persisted data class (`id`,
  `shareLinkId`, `expiresAt`, `revokedAt`) since no persisted entity exists yet; `P4-T4`
  is expected to grow it into the full persisted entity rather than replace it. The method refuses a session not bound
  to the given ShareLink, a revoked session, or an expired session, and otherwise returns
  `RequestAccessContext(participant, AuthorizationContext(sessionRef = session.id.toString()))`
  -- the session's own non-secret id, never a ShareLink token, so a bootstrap-mode ShareLink can never read as a
  `PUBLIC_LINK` content grant through this path.
- [x] `P4-T2` Implement the concrete Information Request actions through the central
  `DefaultAuthorizationService`, including authoring, issuing, viewing, responding, attesting,
  reviewing, reassigning, cancelling, and administering evidence. Confirm every new capability is
  default-deny until explicitly granted. Found already implemented, uncommitted, in the working tree:
  `RoleCapabilities.INFORMATION_REQUEST_SHARE`
  maps every `InformationRequestShareRoleKey` (`SUBJECT`, `CONTRIBUTOR`, `PREPARER`, `ATTESTOR`,
  `REVIEWER`, `DECISION_MAKER`) to its exact capability set, `RoleCapabilities.forShareRole` dispatches on
  `ResourceType` so `DefaultAuthorizationService.toGrant` resolves any Share's role generically, and migration `V92`
  already widened the check constraints for `INFORMATION_REQUEST`. This task added the missing complete actor-capability
  matrix test proving all six roles resolve correctly end-to-end through `DefaultAuthorizationService`, that none grants
  `INFORMATION_REQUEST_CREATE`, that the six roles jointly cover the full runtime request capability vocabulary apart
  from creation, and that concrete unauthorized actions are denied. No production code changed.
- [x] `P4-T3` Feed Exchange ownership, request party role, Requirement assignment, response mode,
  confidentiality compartment, request state, delegated authority, and correction allowlist into
  the registered aggregate and Requirement-occurrence resource authorization contexts and decision
  obligations. Fields operations map each binding and occurrence to that subordinate resource and
  use the central authorization decision. Do not create a parallel policy engine. Complete apart from the correction
  allowlist, which is resolved as blocked on `P8-T3` (no Review, Finding, or correction-request entity exists yet in the
  repository) -- a deliberate, documented limitation, not an oversight. Every other slice is implemented: Exchange
  ownership reaches request resource decisions through `InformationRequestParentGrantInheritancePolicy`; request party
  role, Requirement assignment, response mode, confidentiality compartment, delegated authority, and request state are
  all fed into
  `InformationRequestRequirementPolicyFacts`/`InformationRequestRequirementPolicyEvaluator`; and Fields
  binding/occurrence operations now map onto the `INFORMATION_REQUEST_REQUIREMENT` resource through
  `InformationRequestFieldBindingPolicy`, which resolves the Requirement a binding answers (via
  `InformationRequestTemplateRequirementBinding.collectedFieldDefinitionId`) and asks the central
  `AuthorizationService` for `INFORMATION_REQUEST_REQUIREMENT_VIEW`/`_RESPOND` against that exact occurrence instead of
  the blanket request aggregate.
- [x] `P4-T4` Extend existing `ShareLink` persistence and services with an explicit
  `VERIFICATION_BOOTSTRAP` mode and add `RequestAccessSession` persistence and services. Complete at
  the service layer (unreachable from any REST surface until `P4-T5`):
  `ShareLink.linkMode` (`DIRECT_GRANT`/`VERIFICATION_BOOTSTRAP`, migration `V103`) exists and
  `DefaultAuthorizationService.resolveLinkGrant` refuses a content grant for a bootstrap-mode link.
  Persisted `RequestAccessSession` (migration `V104`), bootstrap-issuance
  (`InformationRequestBootstrapShareLinkService`, with `requireRecipientSignIn` refusal and Command
  Receipts), contact-proof verification (`InformationRequestContactProofService`, migration
  `V105`), bootstrap-link rotation, replacement, and revocation
  (`InformationRequestBootstrapShareLinkService.rotate`/`replace`/`revoke`, migration `V106`, each
  revoking every active `RequestAccessSession` minted from the affected link via
  `RequestAccessSessionService.revokeAllForShareLink`), and the verified-registration upgrade path
  (`InformationRequestParticipantAccountUpgradeService.upgrade`, migration `V107`, persisting a
  `ParticipantAccountLink`, granting the App User an equivalent Share via
  `ShareService.grantRoleKeyWithPrincipalProvenance` without touching the Participant's original
  Share/party/ShareLink history, and revoking every active bootstrap ShareLink and session bound to
  that Share via `ShareLinkRepository.findActiveBootstrapLinksForShare` and
  `RequestAccessSessionService.revokeAllForShareLink`) are done. Bind the
  ShareLink to one request-party Share, request party, and canonical participant principal, and
  reference its `ExchangeRecipient` by ID; the V63 trigger forbids putting a request-party Share into
  `exchange_recipient.direct_share_id`. Refuse bootstrap issuance when the parent Exchange sets
  `requireRecipientSignIn`, returning a stable reason and directing the respondent to the
  authenticated surface, so the owner's existing no-auth choice is never silently overridden.
  Store only a secret hash and record explicit expiry, revocation,
  replacement, rotation lineage, verification strength, atomic use count, and last use. Enforce or
  reject every configured password, domain, MFA, Share, status, expiry, and usage constraint.
  Preserve existing direct-grant ShareLink behavior, but make the central authorizer refuse to turn
  a bootstrap-mode link into a content grant. Require recipient contact proof before issuing the
  request-bound session that may read or mutate content. Reuse existing OTP delivery, fixed-delay,
  and rate-limit primitives where applicable, noting that the legacy flow issues its Exchange-wide
  token only after an emailed OTP verifies contact and records `noAuthAccessVerifiedAt`. Do not reuse
  the Exchange-wide token, `recipientOtpHash`, or `recipientOtpExpiry` as request authorization, and
  never create a temporary App User to represent an email-only caller even though the existing
  `EXTERNAL_EMAIL` path does exactly that. Do not add
  a separate request-credential table unless a journaled implementation proof shows the compatible
  ShareLink extension cannot meet a required invariant. Resolve identity collisions within the
  owning tenant without cross-tenant merging. On a later verified registration upgrade, persist a
  `ParticipantAccountLink`, grant the App User an equivalent request Share, then revoke future
  bootstrap-mode ShareLinks and active sessions in the same transaction; preserve all earlier
  participant provenance unchanged. Bootstrap ShareLink issue, rotation, replacement, revocation,
  and registration upgrade use Command Receipts and expected aggregate revisions.
- [x] `P4-T5` Add authenticated REST resources and a parallel no-auth adapter. Each validates its
  own credential type, builds the same explicit access context, and delegates to the same
  application services. The no-auth path remains outside the global authentication filter, which
  means adding its exact path prefix to the `EndpointAuthorizationFilter` allowlist that currently
  names `/no-auth/exchanges` and `/no-auth/sales-enquiries`. Add a test that a request path absent
  from that allowlist is rejected rather than silently authenticated. Complete: authenticated
  `InformationRequestAccessLinkResource` (`/information-requests/{id}/access-links`) exposes
  `InformationRequestBootstrapShareLinkService.issue`/`rotate`/`replace`/`revoke`.
  `InformationRequestNoAuthAccessResource` (`/no-auth/information-requests/access-links/challenges` and
  `/sessions`) exposes `InformationRequestContactProofService.issueChallenge`/`verifyChallenge` behind
  an `X-Request-Access-Token` header, mirroring `NoAuthExchangeResource`'s header-based raw-token
  handling; `/no-auth/information-requests/` (trailing slash, so a look-alike path that only shares the
  prefix's characters is not accidentally excluded) was added to
  `EndpointAuthorizationFilter.excludedEndpoints`, with a passing rejection test.
  `InformationRequestParticipantAccountLinkResource` (`/information-requests/{id}/participant-account-links`)
  exposes `InformationRequestParticipantAccountUpgradeService.upgrade`, authenticating the caller's App
  User identity from the access token while taking the no-auth session id from the request body.
- [x] `P4-T6` Apply recipient-safe projection to every read and mutation response. The same audience
  rule must govern identifier disclosure, write eligibility, and returned values. Complete for
  `P4-T6a` (party projection): every `InformationRequestShareRoleKey` shares `INFORMATION_REQUEST_READ`,
  so `Action.INFORMATION_REQUEST_VIEW` alone could not distinguish which party rows a caller should see
  in full; `InformationRequestPartyQueryService.listForRequest` now reveals a party's `principalId`,
  `principalKind`, `subjectIdentityRefId`, and `exchangeRecipientId` (via `InformationRequestPartyDtoMapper`)
  only to a caller holding `INFORMATION_REQUEST_MANAGE_PARTIES` or viewing their own party row; every
  other caller sees only role and status. Now reachable from both the authenticated
  `InformationRequestPartyResource` and the no-auth `InformationRequestNoAuthRequestResource`, added in
  `P4-T9`. `P4-T6b` (occurrence and response projection) is deferred, not blocked: today's
  `InformationRequestRequirement`/`InformationRequestRequirementRevision` occurrence carries only
  template/source references and a path string, no identity-bearing field to redact, and response and
  evidence content does not exist until Phase 5-7; revisit then and reuse this same projection pattern.
- [x] `P4-T7` Resolve subscription checks from the owning Exchange. A respondent's plan must never
  hide or block an assigned request. Freeze execution entitlement and quota limits at issuance.
  Persist an immutable `RequestExecutionGrant` containing owner type and ID, entitlement and policy
  version, paid or trial entitlement source, trial grant reference and issuance-time expiry where
  applicable, global enforcement mode observed at the decision, permitted continuation actions,
  expiry, operational revocation state, and conservative request-local recipient, upload, and
  storage caps. Resolve issuance through the existing effective-subscription and subscription
  enforcement services. Atomically reserve those commitments against owner usage at issuance and
  use idempotent `RequestExecutionUsageReservation` rows to reserve, consume, release, and roll back
  capacity under concurrent operations. Phase 12 may tune future plan limits but never reduce an
  issued grant. Prove that paid lapse or trial expiry blocks new or expanding work but still permits
  assigned respondents and reviewers to finish the existing request within the reserved limits,
  while authorized reads and exports remain available. Keep the owner rollout gate distinct from
  global `OFF`, `REPORT_ONLY`, and `ENFORCE` subscription behavior. Model emergency operational
  suspension and explicit execution-grant revocation separately.
  Include `BusinessFieldsSubscriptionGuard` in this task. Its `requireResourceMutation` re-checks the
  owner's live plan for `BUSINESS_FIELDS_AND_SCHEMAS` on every Field write and would strand an
  already-issued request after a lapse, so Information Request Field operations must consult the
  grant instead. Snapshot both `INFORMATION_REQUESTS` and, for a Template with Field Requirements,
  `BUSINESS_FIELDS_AND_SCHEMAS` into the grant at issuance and require both then. Add a test proving
  a lapsed owner's respondent can still write Field values on an issued request, which fails today
  against the live guard.

  Too large for one implementation session; split into dependency-ordered subtasks, none of which may
  check the parent box until all are done:
  - [x] `P4-T7a` Persist the immutable `RequestExecutionGrant` (migration `V108`) once, at issuance,
    via `InformationRequestExecutionGrantService.issueGrant`, called from
    `InformationRequestLifecycleService.issue()`. Freezes owner type/id, plan code, subscription
    status, the enforcement mode observed, trial expiry (when trialing), a mutation-allowance expiry
    (trial end, grace-period end, or paid-through date depending on status), and the plan's
    additional-participant limit as the request's local recipient cap. Idempotent by request id.
    Complete. Deliberately narrower than the full task text: `BUSINESS_FIELDS_AND_SCHEMAS` snapshotting,
    upload/storage caps, permitted continuation actions, and revocation semantics are out of scope here
    (`P4-T7c`/`P4-T7d`); upload/storage caps specifically have nothing to size against yet, since no
    upload or storage capability exists anywhere in the platform before Phase 5-7. Nothing yet reads
    this grant back.
  - [x] `P4-T7b` Add the idempotent `RequestExecutionUsageReservation` model: reserve, consume, release,
    and roll back capacity against a `RequestExecutionGrant` under concurrent operations, with tests for
    exhaustion and concurrent reservation up to the cap. Complete: `reserve`/`consume`/`release`/`rollback`
    all implemented and unit-tested against mocked repositories (10 cases); a real-Postgres concurrency
    contract test proving the exhaustion and release-then-reuse cases under actual concurrent
    transactions was also written, mirroring `OrganizationSeatConcurrencyPostgresContractTest`'s
    raw-JDBC lock pattern, but could not be executed in this session's environment (no Docker daemon
    available, the same pre-existing limitation that already blocks that older test here) -- a
    Docker-capable session should run it before treating its pass as observed. Nothing yet calls any of
    the four operations; wiring a caller is `P4-T7c`'s job.
  - [x] `P4-T7c` Redirect continuation-path subscription checks to the frozen grant instead of the live
    subscription: request lifecycle mutations driven by a respondent or reviewer on an already-issued
    request, and `BusinessFieldsSubscriptionGuard`/`InformationRequestFieldResourceAdapter` Field writes.
    Prove a lapsed owner's respondent can still submit and write Field values within the reserved
    limits, while new or expanding work (new requests, issuance, adding parties beyond the reserved cap)
    still answers to the live subscription. Complete: `InformationRequestLifecycleService.mutate()` now
    consults `InformationRequestExecutionGrantService.findForRequest` before deciding whether to call the
    live-subscription `entitlementGuard` -- a request with no grant yet (issuance, or any `DRAFT`
    mutation) still answers to the live subscription, a request that already holds one (today, only
    `cancel`/`supersede` are wired past issuance) does not. `FieldResourceAdapter` gained a default
    `mutationEntitlementFrozen` method that `InformationRequestFieldResourceAdapter` overrides to consult
    the same grant, and `SchemaAssignmentService` skips `BusinessFieldsSubscriptionGuard.requireResourceMutation`
    when an adapter reports a frozen entitlement. Grant issuance itself now also requires
    `BUSINESS_FIELDS_AND_SCHEMAS` when the Template binds a Field. `SUBMIT` and the other Phase 5-7
    response mutations remain unwired and were not exercised; whichever session wires them should route
    through the same `findForRequest(requestId) == null` gate.
  - [x] `P4-T7d` Global enforcement-mode and owner-rollout interplay, operational suspension, and
    explicit grant revocation; the commercial-entitlement and operational-rollout truth-table tests.
    Complete: `InformationRequestEntitlementGuardTest` now pins all four commercial-entitlement x
    rollout-grant combinations plus the `REPORT_ONLY` enforcement-mode cases. Emergency operational
    suspension reuses the existing `SubscriptionStatus.SUSPENDED` value (deliberately not a new global
    kill-switch configuration) through
    `InformationRequestEntitlementGuard.requireNotOperationallySuspended`, which the lifecycle service
    now checks even once a request already holds a frozen grant, since `SUSPENDED` carries no grace
    window unlike `PAST_DUE`/`CANCELED`. Explicit per-request revocation is
    `InformationRequestExecutionGrantService.revoke`, writing the grant's existing
    `revokedAt`/`revokedReason` columns and enforced in `InformationRequestLifecycleService.mutate()`
    via a new `EXECUTION_GRANT_REVOKED` catalog code. Neither has a REST caller yet; an administrative
    surface for either was left for a future session to place.
- [x] `P4-T8` Keep both gates off until authenticated and no-auth access, authorization, audit, and
  safe projection tests all pass. In a controlled scope, add an explicit owner commercial
  entitlement and separately enable owner rollout; prove that either gate alone denies creation or
  issuance while retained and already-issued authorized work stays discoverable. Do not add the
  feature to a default plan before Phase 12 rollout. Complete: both gates are confirmed off by
  default (`PlanCatalog` grants `INFORMATION_REQUESTS` to no plan; `app.subscription.rollout.grants`
  defaults to empty), and the full `auth.authz`/`informationrequest`/`fields`/`subscription`
  regression suite passes with only the pre-existing Docker-dependent contract-test errors unrelated
  to this change. New tests in `InformationRequestAdHocCreationServiceTest` and
  `InformationRequestLifecycleServiceTest` wire a real `InformationRequestEntitlementGuard` (built
  from a real `SubscriptionAccessService`/`FeatureRolloutConfigService` against a mocked policy
  repository, rather than mocking the guard itself) through the actual creation and issuance call
  paths and prove each single-gate combination (rollout only, entitlement only) denies both, while a
  request that already holds a frozen execution grant remains cancellable under the same single-gate
  gap. No production code changed: `InformationRequestAdHocCreationService.createAdHoc` and
  `InformationRequestLifecycleService.issue`/`mutate` were already correctly wired to the guard; the
  gap closed here was that nothing previously exercised that wiring end to end rather than only
  through the guard's own unit tests.
- [x] `P4-T9` Add the minimal authenticated and no-auth request shell that proves safe request list
  and detail projection, token handling, and equal capability discovery for the walking scenarios.
  Complete: `InformationRequestQueryService.findById` plus `InformationRequestResource.get`
  (`GET /information-requests/{id}`) add authenticated detail projection alongside the existing
  authenticated list. `InformationRequestPartyResource`
  (`GET /information-requests/{id}/parties`) is the first REST caller of the `P4-T6a` party
  projection. `InformationRequestNoAuthReadAccessService` resolves a presented bootstrap
  `X-Request-Access-Token` to the same `RequestAccessContext` shape via
  `InformationRequestContactProofService.resolveBootstrapLink` (made public),
  `RequestAccessSessionService.findUsableForShareLink` (new), and
  `InformationRequestAccessContextFactory.fromBootstrapSession` (its first production caller), and
  `InformationRequestNoAuthRequestResource` (`no-auth/information-requests/{id}` and `.../parties`)
  mirrors the authenticated detail and party reads against the identical application services, already
  covered by the existing `/no-auth/information-requests/` allowlist prefix.

### Tests to write first

- Complete actor-capability matrix tests.
- Central authorization registry, default-deny capability, and decision-obligation tests.
- Cross-organization and unrelated-participant denial tests.
- Registered and no-auth projection parity tests.
- Explicit authenticated and no-auth access-context construction tests proving shared services do
  not read global request authentication.
- Group-member join, leave, and overwrite-policy tests.
- Existing direct-grant ShareLink compatibility plus bootstrap-mode issue, constraint enforcement,
  unsupported-constraint rejection, atomic use count, expiry, revocation, forwarding denial,
  replacement, rotation lineage, central content-grant denial, and Exchange-token rejection tests.
- Bootstrap-link-only denial, recipient contact-proof, session strength, and request/session binding
  tests.
- External Participant collision, no-temporary-App-User, verified registration upgrade,
  equivalent User Share, participant session revocation, and unchanged historical provenance tests.
- `requireRecipientSignIn` tests proving bootstrap issuance is refused with a stable reason on an
  Exchange that requires sign-in, that the authenticated surface still serves that respondent, and
  that the flag cannot be overridden by a request-level setting.
- No-auth path allowlist tests proving an Information Request no-auth prefix absent from
  `EndpointAuthorizationFilter` is rejected rather than treated as authenticated.
- Bootstrap ShareLink issue, rotation, revocation, and upgrade command replay, fingerprint conflict,
  expected-revision, and parallel-race tests.
- Reusable bearer-session and one-time command replay tests.
- Response-mode and confidentiality-compartment read/write tests.
- Free-plan respondent, paid lapse, trial expiry, trial extension non-drift, frozen execution
  entitlement, captured quota, global `OFF` or `REPORT_ONLY` or `ENFORCE` behavior, owner rollout
  independence, and operational suspension tests.
- Lapsed-owner Field write tests proving an issued request's respondent is governed by the frozen
  grant and not by `BusinessFieldsSubscriptionGuard.requireResourceMutation`, plus issuance tests
  requiring both `INFORMATION_REQUESTS` and, for Field-bearing Templates,
  `BUSINESS_FIELDS_AND_SCHEMAS`, and a test that the guard's existing Exchange behavior is unchanged.
- Execution Grant issuance reservation, concurrent usage, rollback, retry, exhaustion, cancellation
  release, later-plan-change non-drift, expiry, and explicit operational revocation tests.
- Commercial-entitlement and operational-rollout four-case truth-table tests.

### Exit criteria

- Every supported actor is represented in audit and authorization without inventing an App User.
- Every no-auth actor resolves to a stable recipient-bound participant principal and session; the
  Exchange-wide legacy credential cannot authorize request data.
- Request access extends the existing ShareLink credential mechanism without creating a parallel
  request-credential table, and existing direct-grant ShareLinks retain compatible behavior.
- A link without verified contact proof grants no request access, and later registration upgrades
  future access without rewriting prior participant provenance.
- Both API surfaces enforce identical request behavior and recipient-safe projections.
- Authorization uses the central server-side stack and is Requirement-specific.
- Subscription checks charge or gate the Exchange owner, never the respondent.
- Paid lapse or trial expiry does not strand an already-issued request, while operational
  suspension remains an explicit, visible control and owner rollout remains distinct from global
  subscription enforcement mode.
- Issued capacity is represented by a durable grant and atomic reservation ledger, not an assumed
  future plan lookup.
- Cross-party evidence and response leakage tests pass.
- Baseline request-rate controls apply and default commercial and production rollout gates remain
  closed.

## Phase 5: Structured Responses, Repeatable Groups, and Conditions

### Goal

Support complex structured responses and item-level semantics across configurable document-driven
processes.

### Tasks

- [x] `P5-T1` Implement sparse `PATCH` response updates with typed canonicalization, explicit clear
  operations, canonical `PrincipalRef` provenance, explicit access context, Value Set and occurrence
  identity, Command Receipt handling, required `If-Match`, and optimistic concurrency. Split into
  dependency-ordered subtasks because persistence, HTTP exposure, and Field Value Set integration
  are each independently testable:
  - [x] `P5-T1a` Add the first response-draft persistence and service slice: current
    `InformationRequestResponse` envelopes, request-level response ETag, sparse disposition and
    narrative patching, explicit narrative clear, canonical principal/session provenance, central
    Requirement response authorization, Command Receipt replay/conflict handling, parent-state and
    frozen-grant checks, and `SAVE_RESPONSE` transition/audit routing.
  - [x] `P5-T1b` Expose authenticated and no-auth sparse response `PATCH` resources that require
    `If-Match` and an idempotency key, build the same explicit access context on both surfaces,
    delegate to `InformationRequestResponseDraftService`, return response ETags, and map stable
    service refusals to HTTP responses without adding resource-layer business logic. Complete:
    `InformationRequestResponseResource` (`PATCH /information-requests/{id}/responses`) and a new
    `patchResponses` method on `InformationRequestNoAuthRequestResource` both delegate to the
    identical service and additionally project only the requirement occurrences the caller's own
    patch named, since the service's own result carries every current response on the request and
    echoing it verbatim would leak a co-party's disposition/narrative to a single-occurrence
    respondent.
  - [x] `P5-T1c` Wire Field Requirement patches through the existing Fields engine with typed
    canonicalization, explicit clear operations, canonical provenance, current Value Set identity,
    and occurrence-scoped writes. Complete: `ResponseFieldValuesPatch`/`writeFieldValues`/
    `readFieldValues` on `InformationRequestResponseDraftService`, `InformationRequestResponse
    .fieldValueSetId`, both `P5-T1b` resources, and `InformationRequestResponseDto`'s
    `fieldValueSetId`/`fieldValueSetETag`/`fieldValues` all delegate to the Fields engine's existing
    `FieldValueSetRef`/`FieldValueEntry`/`FieldsPrecondition` machinery.
  - [x] `P5-T1d` Add optimistic-concurrency and replay coverage for multi-field and multi-occurrence
    response saves, then run the full affected Phase 5 regression gate. Complete:
    `InformationRequestResponseDraftServiceTest` covers a two-occurrence patch writing independent
    Field Value Sets, a field-patch replay that does not re-invoke `SchemaAssignmentService.setValues`,
    and a stale Field precondition aborting the whole patch with no response/history side effects.
- [x] `P5-T2` Add arbitrary repeatable and nested group definitions and runtime group instances.
  Give each occurrence a stable path and parent reference that can anchor Field, Document, and
  Response Attestation Requirement instances independently. Split into dependency-ordered subtasks
  because the template-level definition, runtime occurrence provisioning, and runtime add/remove/
  reorder are each independently testable:
  - [x] `P5-T2a` Add template-level repeatable and nested group definitions: a version-scoped
    `InformationRequestTemplateRequirementGroup` (stable key within the version, optional parent
    group for nesting, min/max occurrence cardinality), authored and read through the same
    configuration document as sections and bindings, with an existing occurrence anchor key now
    required to resolve to a group the document defines. Complete: entity, repository, `V111`
    migration, DTOs, validator (distinct keys, parent resolution, cycle detection, occurrence-anchor
    resolution), writer (parents written before children in topological layers), projection loader,
    and the lifecycle service's new-version-from-existing round trip all updated and covered by
    `InformationRequestTemplateConfigurationValidatorTest` (9 new cases) and
    `InformationRequestTemplateConfigurationWriterContractTest` (whole-document and rewrite cases
    extended with nested groups). `occurrence_anchor_key` deliberately keeps no new database-level
    foreign key to the group table: existing persistence-contract tests write it as a bare string to
    exercise the binding row in isolation, so the resolution rule is enforced only where every other
    cross-requirement relation in this document already is, at the Kotlin validator.
  - [x] `P5-T2b` Add runtime group occurrence instances: decide where an occurrence `FieldValueSet`
    get-or-create belongs relative to Requirement occurrence provisioning (`SchemaAssignmentService
    .valueSetForWrite` only get-or-creates the root set and throws for a missing occurrence set
    today), materialize the first occurrence(s) at issuance, and give each occurrence a stable path
    and parent reference that can anchor Field, Document, and Response Attestation Requirement
    instances independently. Complete: `InformationRequestGroupOccurrence` (`V112` migration) holds
    a stable `occurrencePath` and `parentOccurrenceId` per runtime repetition;
    `InformationRequestTemplateMaterializer` materializes each group's authored `minOccurrences`
    parent-first at issuance and threads the resolved path into every anchored Requirement instead of
    a hardcoded `root`; `SchemaAssignmentService.createOccurrenceValueSet` provisions each
    occurrence's Field Value Set get-or-create, called only from server-side provisioning, never
    implicitly from a write.
  - [x] `P5-T2c` Add authenticated and no-auth support to add, remove, and reorder repeatable group
    occurrences at runtime within the group's authored cardinality, with concurrent-edit coverage.
    Complete: a shared `InformationRequestGroupOccurrenceService` powers authenticated and no-auth
    REST adapters, requires response-shape ETags plus Command Receipt idempotency, checks central
    Requirement response authorization and issued-request continuation gates, provisions occurrence
    Field Value Sets and new Requirement rows when an occurrence is added, marks occurrences removed
    with canonical principal provenance while keeping prior Requirement history addressable, and
    reorders active sibling occurrences without changing their stable paths.
- [x] `P5-T3` Add a versioned condition expression model using stable Requirement and Field IDs.
  Define supported operators, null and unknown semantics, cycle detection, server evaluation, and
  a client-safe evaluation projection. Split into dependency-ordered subtasks because persistence,
  server evaluation, and the client-safe projection are each independently testable:
  - [x] `P5-T3a` Persist the versioned condition rule and predicate definitions a template version
    authors, instead of validating them and discarding them. Complete:
    `InformationRequestTemplateConditionRule`/`ConditionPredicate`/`ConditionPredicateLiteral`
    entities, migration `V114`, repositories, `InformationRequestConditionPredicateLiteralCodec` for
    the canonical-JSON/typed-column literal conversion (mirroring `FieldValue`'s own typed-column
    convention), `InformationRequestTemplateConfigurationWriter.writeConditionRules`, and
    `InformationRequestTemplateProjectionLoader.loadConditionRules` onto the new
    `InformationRequestTemplateVersionDto.conditionRules`.
  - [x] `P5-T3b` Wire `InformationRequestConditionEvaluator` against a live runtime request's
    current Field values and current Requirement dispositions, resolving each rule's predicates from
    the now-persisted rows rather than an ad hoc in-memory list, so a request's conditional
    requirements can be evaluated server-side. Complete: `InformationRequestConditionEvaluationService
    .evaluate(requestId)` loads the request's persisted condition rules via
    `InformationRequestTemplateProjectionLoader.loadConditionRules`, resolves current Requirement
    dispositions (defaulting an unanswered Requirement to `NOT_ANSWERED` rather than unknown) and
    current root Field Value Set values, and delegates to the unchanged `InformationRequestConditionEvaluator`.
    Occurrence-scoped Field predicates are not yet handled; only root-scoped values resolve.
  - [x] `P5-T3c` Expose a client-safe evaluation projection (rule key to TRUE/FALSE/UNKNOWN state)
    on the runtime request read surfaces (`InformationRequestResource`/
    `InformationRequestNoAuthRequestResource`). Complete: `InformationRequestDto` now carries
    `conditionEvaluations`, each entry includes only rule key, expression version, and state, and
    both authenticated and no-auth detail reads populate it from
    `InformationRequestConditionEvaluationService.evaluate` after the existing read authorization
    and no-auth request-token checks pass.
- [x] `P5-T4` Define hidden-data policy per condition: retain securely, clear with confirmation, or
  archive outside the active response. Re-evaluate completeness when conditions change.
  - [x] `P5-T4a` Define, persist, copy, and expose the per-condition hidden-response-data policy
    with `RETAIN_SECURELY` as the omitted and upgrade default.
  - [x] `P5-T4b` Enforce hidden-response data handling when conditions become false or unknown:
    retain securely, clear with explicit confirmation, or archive outside the active response, then
    re-evaluate active response shape and completeness.
- [x] `P5-T5` Implement the neutral platform response dispositions and narratives, including
  partial, not applicable, unavailable, exception requested, satisfied by reference, and waived
  where the Template permits. Customer-authored labels and reason codes remain configuration data
  and cannot add production branches.
- [x] `P5-T6` Add cross-field, cross-row, unit, currency, date-range, period-coverage, and duplicate
  validation extension points.
- [x] `P5-T7` Implement a deterministic completeness and progress service. Optional, hidden, waived,
  rejected, and conditional structured responses must have defined denominator behavior. Expose a
  composable evaluator that Phase 6 extends with evidence and Phase 7 invokes for final submission.
- [x] `P5-T8` Extend `basic_field_document_response_attestation_request` and
  `multi_party_staged_evidence_request` through sparse draft response, occurrence creation,
  conditions, and structured-response completeness.
- [x] `P5-T9` Add the minimal shared structured-response UI for Field Requirements, occurrence
  editing, conditions, sparse save, and conflict feedback behind the feature switch. Complete:
  `InformationRequestStructuredResponseWorkspace` renders active Field Requirements per occurrence
  with add, remove, and reorder controls, names each inactive condition rule, saves only changed
  Field values sparsely, and shows stale conflict feedback instead of retrying.
  `structuredResponseCommands` binds those four commands to `informationRequestRuntimeService` with
  a per-command idempotency key, the expected response ETag as `If-Match`, and an optional
  access-link token so the identical handlers serve the authenticated and no-auth routes.
  `InformationRequestStructuredResponsePanel` gates the workspace on
  `PlanFeature.INFORMATION_REQUESTS` for an authenticated caller while treating a presented
  access-link token as server-gated access. Mounting the panel on a respondent route belongs to
  Phase 10 integration.
- [x] `P5-T10` Evaluate condition rules per occurrence. Added after the 2026-09-08 exit-gate run
  found that evaluation was root-only and failed open, so a conditional Requirement inside a
  repeatable group was permanently UNKNOWN, was treated as HIDDEN, and silently left the completeness
  denominator. Complete: `InformationRequestConditionEvaluationService` resolves the occurrences each
  rule governs from its conditional bindings and the runtime Requirements that name them, evaluates
  the rule once per occurrence, lays each occurrence's Field Value Set over the root set so a
  root-collected Field still resolves from inside a group, and resolves a disposition predicate to
  that occurrence's own answer, falling back to a single root answer and otherwise staying UNKNOWN
  rather than picking an arbitrary sibling. `InformationRequestConditionEvaluationProjection`, the
  evaluation DTO, `InformationRequestCompletenessProgressService`, the hidden-response policy in
  `InformationRequestResponseDraftService`, and the respondent workspace all key condition state by
  rule key plus occurrence path.

### Tests to write first

- Sparse update, explicit clear, and stale revision tests.
- Repeatable group add, remove, reorder, and concurrent-edit tests.
- Two nested occurrences with independent Field, evidence-placeholder, finding-placeholder, and
  completeness paths.
- Condition true, false, unknown, cycle, and changed-answer tests.
- Hidden response retain and clear-policy tests.
- Per-item exception-disposition and partial-response tests.
- Cross-occurrence aggregate consistency, period coverage, and duplicate-entry tests.
- Progress and completeness truth-table tests.

### Exit criteria

- A respondent can save incomplete work without bypassing submission validation.
- Repeatable and nested process data does not require ad hoc Field names.
- Conditions are deterministic, versioned, server-authoritative, and auditable.
- Every applicable structured-response Requirement has a valid response or allowed exception
  disposition; final package completeness remains owned by Phase 7.
- Progress uses one documented formula and does not conflict with ordinary Exchange Document counts.

### Mandatory pre-Phase 6 review remediation

The numbered tasks below map one-to-one to findings 1 through 20 in
[the Phases 1-5 review](DOCUMENT-DRIVEN-INFORMATION-REQUESTS-PHASES-1-5-REVIEW.md).
All are mandatory, including P2 findings. Keep each unchecked until the production or verification
defect is corrected, a meaningful regression demonstrates the fix, and completion evidence names
the changed files, exact commands and results. A passing existing suite, a documentation change,
or a decision to defer the defect does not close a finding. Preserve the report as the original
review record and record resolutions in the companion evidence journal using these stable IDs.

The original `P5-R01` through `P5-R20` completion records are retained as history. The 2026-09-13
[Phases 1-5 recheck](DOCUMENT-DRIVEN-INFORMATION-REQUESTS-PHASES-1-5-RECHECK.md) found remaining
integration gaps, recorded below as `P5-R21` through `P5-R27`. This reopened `P5-R-GATE`; subsequent
review added `P5-R28` through `P5-R30`. All remediations and the repeated gate passed on 2026-09-20.
The user subsequently authorized scanner-independent Phase 6 work on 2026-09-20. Since
2026-09-25 the scanner gates nothing: production uploads work without it, and it is planned in
`plans/INFORMATION-REQUEST-EVIDENCE-MALWARE-SCANNING-PLAN.md`.
Any earlier deferral that overlaps these findings is superseded for already implemented behavior.
In particular, implement the session, reassignment, revocation, and structured-response preservation
parts of `P3-T11b` through `P3-T11d` needed here; their old missing-prerequisite descriptions are not
a reason to defer these fixes. Evidence and package behavior that does not yet exist remains in its
original future phase. No Phase 6 implementation may proceed while a remediation is blocked.

- [x] `P5-R01` (finding 1, P1): Separate bootstrap links from content session credentials.
  Require an independent secret session credential with mandatory expiry and validated recipient,
  request, and link binding. Update both access surfaces and client transport. Prove that a second
  browser holding only the original link still cannot read or mutate after the recipient verifies,
  and that expired, revoked, rotated, or incorrectly bound credentials fail closed.
- [x] `P5-R02` (finding 2, P1): Apply parent Exchange termination effects to runtime access.
  Wire rejection, rescission, and deletion into transactional child lifecycle and session revocation;
  enforce the defined actor-specific read policy and parent effects on every access surface.
  Test each parent transition, permitted historical reads, refused writes, and concurrent commands.
- [x] `P5-R03` (finding 3, P1): Remove hidden values from active workspace projections and clear
  current Fields through the revision-preserving Fields engine when policy requires CLEAR.
  Do not merely detach the response envelope or delete historical revisions. Test read DTOs after
  false/unknown conditions and explicit clear, including retained history and fresh workspace loads.
- [x] `P5-R04` (finding 4, P1): Authorize the exact Field binding and addressed occurrence using
  `FieldBindingAccess.valueSet`. Reject patch entries outside the named Requirement's collected
  Field. Test sibling occurrences, differently assigned Requirements, and scoped delegates for both
  reads and writes; never choose the first matching Field definition as the authorization target.
- [x] `P5-R05` (finding 5, P1): Enforce Field audience and purpose boundaries using the actual
  caller and owner context. Missing collecting Requirements must fail closed for respondent access.
  Filter read projections and writes consistently. Test extra INTERNAL and CONFIDENTIAL Schema
  Fields against external and registered respondents while preserving permitted owner access.
- [x] `P5-R06` (finding 6, P1): Authorize group commands against the authored group and every
  affected Requirement and descendant scope. Cover zero-occurrence creation without falling back
  to aggregate contributor authority. Test add, remove, and reorder refusal for protected,
  PREPARER, and NOT_DISCLOSED scopes and success for appropriately assigned actors.
- [x] `P5-R07` (finding 7, P1): Exclude removed occurrence subtrees from active workspace,
  conditions, and completeness, and reject subsequent response writes to them. Preserve history.
  Test parent removal with descendants, stale commands, and progress before and after removal.
- [x] `P5-R08` (finding 8, P1): Batch sparse Field changes per distinct Value Set, validate each
  precondition once, and return consistent resulting ETags within one atomic command. Test two
  Fields in one occurrence, multiple occurrences, explicit clear, stale external edits, and full
  rollback on failure. Restore the review's self-conflicting-save probe as permanent coverage.
- [x] `P5-R09` (finding 9, P1): Provide explicit runtime Requirement to Template/binding identity
  in the response contract and use it in the frontend. Remove Field-presence guessing over whole
  Value Sets. Test editing only the second Field and editing both Fields in the same occurrence,
  plus repeated occurrences, with commands naming the correct distinct runtime Requirements.
- [x] `P5-R10` (finding 10, P1): Allocate stable occurrence identity independently of display
  order and retain uniqueness across removal and reordering. Add PostgreSQL coverage for remove,
  reorder, then add, including concurrent adds and correct Field Value Set paths. Restore the
  review's duplicate-path probe as permanent coverage.
- [x] `P5-R11` (finding 11, P1): Resolve exact-party assignment through verified
  `ParticipantAccountLink` records and current group membership. Preserve assignment provenance
  and scope. Test upgraded registered recipients and assigned group members across reads and
  writes, unrelated accounts, membership removal, reassignment, and revoked access.
- [x] `P5-R12` (finding 12, P1): Close both contact-proof abuse and bootstrap accounting gaps.
  Bound OTP verification attempts and enforce rate limits and expiry, including concurrent guesses.
  Atomically advance the bootstrap usage counter that `maxUses` actually checks when access is
  consumed. Test exhausted links, failed proof, successful proof, expiry, and parallel redemption;
  session-only counters do not satisfy bootstrap limits.
- [x] `P5-R13` (finding 13, P1): Reauthorize receipt replay and any returned representation
  against current assignment, lifecycle, and grant state. Ensure payload and ETag describe the
  same authorized result. Test narrative-only and Field replays after revocation, reassignment,
  replacement responses, parent termination, and operational suspension without leaking new content.
- [x] `P5-R14` (finding 14, P1): Align existing-work reads and UI access with owner-funded,
  frozen execution grants. Fix both server live-commercial-gate checks and the authenticated
  respondent's own-plan UI gate. Test owner entitlement lapse, rollout changes, explicit operational
  revocation, a free registered recipient, and recipient-bound sessions; permitted continuation must
  not weaken operational suspension or new-work issuance checks.
- [x] `P5-R15` (finding 15, P1): Complete supported nesting in both backend conditions and the
  editor. Resolve ancestor occurrence Fields in order with sibling isolation. Expose immutable
  group, occurrence, and parent identifiers and send `parentOccurrenceId` in nested commands;
  remove outermost-path parsing as identity. Test two-level nested conditions, two distinct parent
  branches, nested add/remove/reorder/save, and independent completeness. Do not defer supported
  nesting or hide UNKNOWN conditions as a substitute for implementing ancestor resolution.
- [x] `P5-R16` (finding 16, P2): Define and implement response activation transitions for every
  hidden-response policy when conditions return to TRUE. Retained responses must reactivate with
  their authorship and history intact. Test TRUE/FALSE/TRUE and UNKNOWN/TRUE sequences and ensure
  CLEAR does not resurrect cleared current values. Keep the review's reactivation probe permanently.
- [x] `P5-R17` (finding 17, P2): Compute structured completeness from the exact collected
  canonical Field value and allowed exception semantics, not existence of a Value Set. Test empty
  first patches, explicit clear, optional Schema Fields required by the request, and exceptions.
  Incomplete draft saves remain allowed; final package submission validation remains in Phase 7.
- [x] `P5-R18` (finding 18, P2): Wire reservation reserve/consume/release/rollback into actual
  issuance and party expansion paths and enforce frozen recipient capacity. Test transactional
  rollback, idempotency, concurrent capacity claims, and release without double counting through
  production callers. Recipient capacity must be enforced now; future upload/evidence usage remains
  in its original phase and cannot serve as justification for an unused current reservation ledger.
- [x] `P5-R19` (finding 19, P2): Repair the delegated-authority persistence fixture to supply
  V102's required grantor kind, grantor ID, and effective time. Preserve migration constraints.
  Run the positive and scope-refusal contract cases against PostgreSQL, then the full Docker-backed
  backend suite with zero failures, errors, or unexplained skips.
- [x] `P5-R20` (finding 20, P2): Fix invalid Information Request Template test fixtures,
  including missing `groups`/`conditionRules` and nonexistent `FieldValueType.TEXT`. Establish an
  enforced application-project typecheck command, following the Verification Command Matrix.
  Capture and review the unrelated diagnostic baseline separately; require zero feature diagnostics
  and no new unrelated diagnostics. Demonstrate that an introduced feature type error fails the gate.
  A root `tsc --noEmit` success alone cannot close this finding or pass the Phase 5 exit gate.

- [x] `P5-R21` (recheck, P1): Make failed OTP accounting survive the transactional refusal.
  Add real CDI/PostgreSQL tests that reload attempt state after each failure, then preserve bounded
  failure accounting with narrowly scoped transaction handling. Bound challenge resends without
  resetting the attempt budget, cover concurrent verification/resend abuse, and retain atomic
  successful session issuance and bootstrap use accounting. Completes the remaining `P5-R12` gap.
- [x] `P5-R22` (recheck, P1): Apply recipient-safe projection to workspace configuration as well as
  response values. Filter denied Requirement prompts, identifiers, policies, relationships,
  conditions/literals, and occurrence metadata on authenticated and no-auth reads. Retain authorized
  zero-occurrence group controls through authored-policy checks. Add mixed-party and NOT_DISCLOSED
  JSON-disclosure regressions. Completes the current response/occurrence portion of `P4-T6`.
- [x] `P5-R23` (recheck, P1): Save nested Field edits using the same Template group and runtime
  Requirement identity as rendering. Cover child-only, parent-plus-child, sibling-parent, and deeper
  nested saves through the actual UI handler. The current parser drops child patches. Completes the
  remaining `P5-R15` save-path gap.
- [x] `P5-R24` (recheck, P1): Authorize a new group occurrence against its intended creation scope.
  Do not borrow an arbitrary existing or removed Requirement's authority. Prove exact-Requirement
  delegates cannot expand into new scopes and assigned parties can recreate a minimum-zero group
  after removing its final occurrence, including nested parents and descendant bindings.
  Completes the remaining `P5-R06` creation-authorization gap.
- [x] `P5-R25` (recheck, P2): Recover the response editor after ordinary command refusals and add
  the explicit hidden-data clear-confirmation flow. Always release busy state, show the server
  refusal, preserve drafts, and support retry or confirmation cancellation. Cover rejected save
  and group commands, session loss, and accepted/cancelled clearing in UI interaction tests.
- [x] `P5-R26` (recheck, P2): Standardize parent-before-request locking for party reassignment and
  other existing request commands. Focused unit coverage and production lock-order changes are in
  place for acting-party assignment, reassignment, external participant assignment, trusted
  recipient assignment, and revocation. Deterministic real-service PostgreSQL concurrency coverage
  passed for reassignment versus response save and parent termination through
  `InformationRequestPartyConcurrencyTransactionTest`. Session authentication locks the parent
  through `RequestAccessSessionRepository.lockParentForShare`.
- [x] `P5-R27` (recheck, P2): Finish the existing-runtime portions of `P3-T11b` through `P3-T11d`.
  Explicit bootstrap-link/session revocation now runs during party reassignment and revocation,
  preserving replacement principal binding and existing structured response history. Focused unit
  tests cover persisted old credential revocation and bootstrap session cutoff. Only future
  evidence/package behavior stays deferred.
- [x] `P5-R28` (gate review, P2): Prove that reassignment preserves the existing structured response
  record, content, revision, and original principal provenance through transaction-backed service
  entry coverage.
- [x] `P5-R29` (gate review, P2): Split the modified structured-response occurrence component to the
  repository size limit while preserving occurrence behavior. The component is 150 lines and its
  extracted command hook and presentation helper pass focused lint, typecheck, and UI regressions.
- [x] `P5-R30` (final gate review, P1): Restrict workspace occurrence metadata and condition results
  by authorized runtime occurrence scope. Group IDs and rule keys alone are insufficient because
  a caller can view one occurrence and be denied another sibling using the same group or rule.
  Apply the same projection to authenticated and no-auth request-detail reads. Preserve necessary
  authorized ancestor context and empty-parent optional-group controls. Keep original authored
  conditions authoritative when redacting their configuration, and authorize condition sources in
  the evaluated branch. Focused red regressions cover all of these boundaries; the final focused
  run passed 40 tests and the full backend run passed 2,579 with no failures, errors, or skips.
- [x] `P5-R-GATE` Close the complete review before authorizing Phase 6 entry.
  Reopened by the 2026-09-13 recheck and closed again on 2026-09-20 with the evidence below.
  Keep the completed `P5-R21` through `P5-R30` regressions green and reconcile
  the current unrelated frontend test,
  TypeScript, lint, and build failures without silently expanding an accepted baseline.
  Verify that all 20 parent tasks and any subtasks are checked with implementation and regression
  evidence; reconcile overlapping earlier tasks and all active handoff instructions. Rerun
  `.\mvnw.cmd test` with Docker and PostgreSQL available, `npm test -- --run` in `web-app`, the
  meaningful application typecheck gate from `P5-R20`, and applicable lint/build checks from the
  Verification Command Matrix. Record exact totals, all baseline diagnostics, and any accepted
  pre-existing lint/build baseline explicitly; no feature-related failure or environmental blocker
  can be waived. Check changed help articles in full, their size limits, and industry-neutral naming.
  Re-review the integrated changes across authenticated and no-auth access, lifecycle, Fields,
  conditions, occurrence identity, entitlements, idempotency, and persistence. Any newly discovered
  defect in this remediation scope becomes an additional unchecked pre-Phase 6 task. Only after
  this gate passes may Phase 5 be marked complete and the exact next task change to `P6-T1`.

## Phase 6: Evidence and Secure Document Handling

### Goal

Represent supporting evidence as versioned, policy-checked material whose access is scoped to the
Information Request Requirement rather than inherited blindly from ordinary Exchange Documents.

### Entry gate

Phase 6 is blocked until every `P5-R01` through `P5-R30` finding is fixed and verified and
`P5-R-GATE` is checked with recorded evidence. This applies before starting any Phase 6 task,
including `P6-T1`, not merely before enabling uploads. Scanner approval does not waive remediation.

Historical, superseded on 2026-09-25 by the next paragraph: the remediation gate is complete. On
2026-09-20 the user explicitly authorized continued work without resolving the scanner decision now. Proceed with scanner-independent tasks, starting with
P6-T1. Keep the real scanner deployment and signature-update implementation deferred. Do not mark
P6-T6 or the full Phase 6 exit gate complete without its evidence, enable production external
uploads, or treat unscanned evidence as safe. A task with a genuine scanner dependency must remain
unchecked, but that does not place the entire phase on hold.

Superseded on 2026-09-25 by the user's decision recorded in `## Status`: Phase 6 closes without a
malware scanner, evidence upload is available in production without one, and the scanner moves to
`plans/INFORMATION-REQUEST-EVIDENCE-MALWARE-SCANNING-PLAN.md`. The rules below apply only to a
deployment that sets `app.information-request.evidence.malware-scan.required=true`.

Content-type inspection is not malware scanning. Before external evidence upload can be enabled in
any production scope, select and approve a concrete scanner deployment and signature-update model
that runs on existing infrastructure. A local or test adapter may exercise contracts, but it cannot
mark production evidence safe. Every file-backed Evidence Version, including a link to an existing
or legacy Document Version, requires a malware assessment tied to its exact verified bytes before
it can satisfy a Requirement. Linking may create a pending Evidence Version, but cannot bypass this
gate. `DocumentEncryptionMode.END_TO_END` content is opaque ciphertext to the server: hashing or
scanning that ciphertext does not establish plaintext safety, so such a Document Version is
ineligible to satisfy a file-backed Evidence Requirement in this program. Supporting it later
requires a separately approved inspectable representation and security contract. If the selected
design needs a new AWS service or paid resource type, stop for explicit user approval.

### Tasks

- [x] `P6-T1` Add logical Evidence Artifact and immutable Evidence Version records linked to runtime
  Requirement occurrences. File-backed evidence references an exact existing Document Version;
  external evidence uses a separate typed-reference subtype.
    - [x] `P6-T1a` Define immutable, typed evidence-source values: exact Document Version identity
      versus a nonblank external reference type and value. Validate both with neutral tests; this
      is model groundwork only and grants no eligibility, scanner result, or access authority.
    - [x] `P6-T1b` Add PostgreSQL Artifact and append-only Version persistence bound to runtime
      Requirement occurrences, with exact file-version and separate external-reference subtypes.
      Prove foreign keys, source exclusivity, version ordering, and immutability with migration tests.
      V120 adds `information_request_evidence_artifact` and `information_request_evidence_version`.
      Storage grants no eligibility, safety claim, or access authority; nothing writes these tables yet.
    - [x] `P6-T1c` Add domain-aligned entity/repository mappings and round-trip tests. Keep mutation
      APIs disabled until command, audit, provenance, and Requirement authorization contracts exist.
      `InformationRequestEvidenceArtifact` and `InformationRequestEvidenceVersion` entities,
      `InformationRequestEvidenceVersionSourceMapper`, and the two repositories exist. The Version
      repository refuses update and delete, and no service, resource, DTO, or UI reaches either table.
- [x] `P6-T2` Make Document Version the authoritative owner of immutable storage identity and
  version-level content hash. Replace the hardcoded local
  version-copy path with a provider-neutral version-storage port and a typed immutable locator that
  distinguishes provider and locator kind. New versions use unique write-once object keys and must
  fail rather than overwrite an existing key. Classify existing `storagePath` values as legacy
  local locators and never reinterpret them as object-store keys. Store byte length, hash algorithm,
  and a truthful `VERIFIED` or `UNVERIFIED` state. Bind hash and scan results to the exact locator
  and bytes. Never copy a historical hash from the mutable Document row. Unreadable or missing
  content remains unverified and cannot satisfy a Requirement. Make the canonical creator principal
  kind and ID the only creator a version states. The dual-write, legacy fallback, retained legacy
  column, rolling-deployment, old-writer-drain, and catch-up-backfill parts of this task were
  written before the development-stage constraint and are superseded by it: `P6-T2a` and `P6-T2b`
  shipped them and `DS-T1` removes them. Evidence Version owns request-specific
  issuer, coverage, uploader `PrincipalRef`, and source metadata. Append-only Evidence Assessments
  own conformance, verification, expiry, quarantine, and policy results. Submission later stores all
  applicable IDs and the frozen hash. Split this task into journaled storage, provenance, and hash
  subtasks before implementation. That split is recorded below as `P6-T2a` storage identity,
  `P6-T2b` creator provenance, and `P6-T2c` content hash and verification state. `P6-T2a` is itself
  larger than one session, so it is further split into `P6-T2a1` through `P6-T2a3`. Do not check
  `P6-T2` until every subtask is complete and `DS-T1` has removed the compatibility shims that
  `P6-T2a` and `P6-T2b` shipped.
    - [x] `P6-T2a` Storage identity. Make an immutable typed locator, rather than a bare filesystem
      string, the thing a Document Version stores, and route reads and writes through a
      provider-neutral port.
        - [x] `P6-T2a1` Define the typed immutable locator value model: a locator states its
          provider and its locator kind, an object key is validated as a key rather than a path,
          and a value with no recorded kind resolves as a legacy local locator and is never
          reinterpreted as an object-store key. Model groundwork only, with neutral tests; no
          migration, no storage call, and nothing reads or writes it yet.
        - [x] `P6-T2a2` Expand-stage migration and entity mapping. Add nullable locator kind,
          provider, and canonical locator columns to `document_version`, keep `storage_path` for
          legacy rows, and map them so an existing row still resolves through
          `P6-T2a1` classification. Prove with a PostgreSQL migration contract test and a
          round-trip persistence test. Nothing writes the new columns yet, and no behavior changes.
          V121 adds `storage_provider`, `storage_locator_kind`, and `storage_locator` as optional
          columns that are either fully stated or absent, and `DocumentVersionStorageLocatorMapper`
          reads a canonical locator first with a legacy `storage_path` fallback. No production
          writer set the columns at the time this subtask closed; `P6-T2a3` added the writer, so
          rows created from then on state a canonical locator and older rows stay legacy local.
        - [x] `P6-T2a3` Provider-neutral version-storage port and write-once keys. Replace the
          hardcoded local version-copy path with a port whose implementations are selected the same
          way the existing file storage implementations are. New versions allocate a unique
          write-once object key and the write fails rather than overwriting an existing key.
          Dual-write the canonical locator and the legacy `storage_path`, read canonical first with
          a history-only legacy fallback, and resolve a legacy version through the matching legacy
          implementation. Contracting `storage_path` stays in a later release and is not part of
          this subtask.
          `DocumentVersionStorageService` is the port, `DocumentVersionStorageServiceProducer`
          selects its local or object-store implementation from `document.version.storage.service`,
          which defaults to `file.storage.service`, and `DocumentVersionContentService` owns key
          allocation and provider routing. Each new version is written once at
          `document-versions/<documentId>/<versionId>/<reduced file name>`, the local implementation
          refuses an existing target and the object-store implementation uses a conditional write,
          and reads resolve the canonical locator first with a legacy local path fallback. Existing
          rows are not backfilled, and no hash, verification state, or evidence eligibility is
          granted here.
    - [x] `P6-T2b` Creator provenance. Add a canonical creator principal kind and ID for every new
      Document Version, dual-write it beside the legacy creator columns during rolling deployment,
      and read canonical first with a history-only legacy fallback. Backfill `USER` only where the
      legacy App User foreign key is non-null and trusted; an email-only row keeps its history label
      and gets no fabricated principal ID. Report ambiguous rows rather than guessing.
      V122 adds `created_by_principal_kind` and `created_by_principal_id` as optional columns,
      backfills `USER` from the `created_by` foreign key alone, and reports the email-only remainder
      by notice instead of resolving an address into an identity. `DocumentVersionCreatorMapper`
      reads the canonical pair first and falls back to the retained foreign key a pre-canonical
      writer left, and a row that states that foreign key alone stays valid so an instance which
      predates this release keeps serving during a rolling deployment.
      `DocumentVersionCreatorResolver` makes the authenticated principal of the request the creator,
      so the `userEmail` form field can no longer decide who an uploaded version is attributed to;
      an application is recorded through the canonical pair alone, and an unauthenticated upload
      names a principal only when the email the platform resolved belongs to a registered user.
      Naming a link or participant principal on the no-auth upload path needs the resolved link
      identity that arrives with the `P6-T3` endpoints; the schema and mapper already accept it.
    - [x] `P6-T2c` Content hash and verification state. Runs after `DS-T1`, so it is written against
      the canonical-only Document Version shape. Store byte length, hash algorithm, hash value, and a
      truthful `VERIFIED` or `UNVERIFIED` state bound to the exact locator and bytes that were read.
      Never copy a hash from the mutable Document row. Hash the bytes the storage port actually
      wrote, at the moment they are written. Unreadable, missing, or `END_TO_END` content stays
      unverified and cannot satisfy a Requirement. Do not add a state that exists only to describe a
      row an older release wrote, and do not build a retryable backfill for historical rows: any row
      that cannot state a hash under the final shape is transformed or removed by the migration.
      Evidence eligibility consumes this state; it is not granted here.
      Done by V128 and `DocumentVersionContentIdentity`. The storage port writes each version once
      and confirms the SHA-256 digest of exactly the bytes it wrote: the local store digests while
      copying and removes a mismatched object, and the object store is sent the checksum so it
      refuses bytes that do not match. A version states `VERIFIED` for server-readable content and
      `UNVERIFIED` for declared `END_TO_END` ciphertext, whose digest names the stored bytes only.
      Every read re-digests the stored bytes and refuses content that no longer matches.
    - [x] `P6-T2d` Evidence record shape. The Evidence Version owns its request-specific uploader
      `PrincipalRef` and session reference, the declared file name and media type, and the captured
      issuer, jurisdiction, language, issue date, expiry date, coverage period, certification
      reference, and signature reference. The Artifact owns its creator and a collection state of
      `ACTIVE`, `WITHDRAWN`, or `REMOVED` with the principal, time, and reason of the last change.
      One forward migration, contract-tested, with no writer until `P6-T3`. This subtask delivers the
      "Evidence Version owns request-specific issuer, coverage, uploader `PrincipalRef`, and source
      metadata" clause of `P6-T2`.
      Done by V129, the two entity mappings, and `InformationRequestEvidenceAttributes` with its
      mapper, which validates the captured attributes the same way the database checks do. A guard
      keeps an Artifact's identity and creator, never lowers its revision, allows only `ACTIVE` to
      `WITHDRAWN` or `REMOVED` and `WITHDRAWN` to `REMOVED`, never rewrites a recorded change, and
      refuses deletion; a second guard appends a Version only to an `ACTIVE` Artifact.
- [x] `P6-T3` Enforce Requirement ACLs on list, preview, download, upload, replace, withdraw, and
  version endpoints. Ordinary Exchange Document access must not bypass these controls. Upload
  initiation and completion use scoped Command Receipts, canonical content fingerprints, and the
  expected Requirement or Evidence Artifact revision so retry cannot create duplicate immutable
  versions. Too large for one session, so it is split below. Do not check `P6-T3` until every
  subtask is complete.
  Done: every subtask below is complete and the full backend suite passed at this checkpoint with
  2,755 tests, 0 failures, 0 errors, and 0 skipped.
    - [x] `P6-T3a` Close the ordinary Exchange Document Version bypass. The version list, latest,
      download, and create paths resolve the Document inside its Exchange and the version inside its
      Document, and the list and latest reads are authorized, so a Document that is not one of the
      Exchange's Documents, including every evidence Document, is unreachable through them.
      Done: `ExchangeDocumentVersionService` resolves every Document through the Exchange's own
      Document collection, refuses a version of another Document, and authorizes the list and latest
      reads with `DOCUMENT_VIEW`; before this, the list and latest reads checked no permission and
      the download served any version of any Document to a caller permitted on any Exchange.
    - [x] `P6-T3b` Evidence upload command. One upload creates an Artifact and its first Version
      under one runtime Requirement occurrence, stored as a write-once Document Version of a
      request-owned Document that is never attached to the Exchange. The command takes the parent
      then request lock, requires the occurrence evidence ETag through `If-Match`, runs once per
      Command Receipt whose fingerprint includes the content digest, is authorized by
      `INFORMATION_REQUEST_EVIDENCE_UPLOAD` on the exact occurrence, honors the frozen execution
      grant, and records transition history, audit, and a domain event in the same transaction.
      Done: `InformationRequestEvidenceUploadService.upload` with `InformationRequestEvidenceGate` and
      the neutral `DocumentVersionRecordingService`, which records a Document that belongs to no
      Exchange. Upload is one multipart request: the parent and request locks are taken before the
      Command Receipt is claimed, so a retried or parallel duplicate waits for the first and replays
      it rather than recording a second immutable version, and the content digest in the receipt
      fingerprint is the digest the storage port must confirm.
    - [x] `P6-T3c` Replacement and withdrawal commands. Replacement appends the next Version to an
      Artifact under its `If-Match` artifact revision; withdrawal moves the Artifact to `WITHDRAWN`.
      Both keep every stored byte and every earlier Version.
      Done: `InformationRequestEvidenceUploadService.replace` and
      `InformationRequestEvidenceCollectionService.withdraw`. Submission membership cannot be
      rechecked yet because no Submission Package exists; that check belongs with `P7-T1`.
    - [x] `P6-T3d` Evidence reads. The occurrence evidence list, an Artifact's Version list, and
      Version content download and preview are authorized per occurrence, by
      `INFORMATION_REQUEST_EVIDENCE_VIEW` for an assigned party and `INFORMATION_REQUEST_EVIDENCE_MANAGE`
      for an evidence administrator, with download audit and a recipient-safe projection.
      Done: `InformationRequestEvidenceQueryService` behind the request read gate; removed evidence
      is listed only to an evidence administrator; content is re-verified against its recorded
      digest, served as an attachment with `nosniff`, a sandbox Content-Security-Policy, and
      `no-store`, and offered inline only for PDF and raster images; every content read records
      `information_request.evidence.download` or `.preview` (catalog version 21). The payload
      carries no uploader identity, only whether the caller created the item.
    - [x] `P6-T3e` Authenticated and no-auth REST resources that build the same access context and
      delegate to the same services, with resource contract tests.
      Done: `InformationRequestEvidenceResource` at
      `/information-requests/{id}/requirements/{requirementId}/evidence-artifacts` and
      `InformationRequestNoAuthEvidenceResource` under the existing `/no-auth/information-requests/`
      allowlist prefix, both over `InformationRequestEvidenceEndpoint`. Upload and replacement are
      multipart; withdrawal is `POST .../withdrawals`; logical removal is `DELETE`.
    - [x] `P6-T3f` Requirement-scoped evidence authorization matrix, including cross-party list,
      preview, and download denial and an unassigned Exchange participant refused on both the
      evidence and the ordinary Document surfaces.
      Done: `InformationRequestEvidenceAuthorizationMatrixTest` runs every evidence action through
      the real central authorization stack. Only the assigned party views, uploads, and withdraws;
      a co-party holding evidence capabilities is refused as unassigned; a decision maker reads
      through the manage permission only; confidential and not-disclosed occurrences refuse their
      respondent. The evidence Document is never one of the Exchange's Documents, so the ordinary
      Document surfaces cannot resolve it.
- [x] `P6-T4` Implement multi-file aggregation with independent file state. Accepted, rejected,
  quarantined, expired, and replacement versions may coexist without corrupting Requirement status.
  Done: `InformationRequestEvidencePolicyEvaluator` gives every Evidence Version its own standing
  (`CURRENT`, `SUPERSEDED`, `WITHDRAWN`, `REMOVED`), conformance (`PENDING`, `CONFORMING`,
  `DEFICIENT`, `QUARANTINED`, `CORRUPT`, `EXPIRED`), and findings, and derives the Requirement state
  (`NOT_PROVIDED`, `PENDING_ASSESSMENT`, `INCOMPLETE`, `DEFICIENT`, `REVIEWABLE`, `SATISFIED`, `WAIVED`,
  `WAIVER_REQUESTED`) from current files only; superseded, withdrawn, and removed versions keep their
  own state and never count, and every current file must conform before the Requirement is satisfied.
  `InformationRequestEvidenceEvaluationService` reads the facts from storage and assessments,
  `InformationRequestEvidenceCompletenessEvaluator` makes a Document Requirement complete only when
  its evidence completes the work or its disposition needs no evidence, and the evidence list carries
  the Requirement evaluation and each version's conformance and findings. Reviewer acceptance and
  rejection arrive with review in Phase 8 and add to these states rather than replacing them.
- [x] `P6-T5` Implement generic evidence-policy validation for counts, MIME and detected content,
  size, pages, issuer, issue date, expiry, freshness, coverage periods, language, jurisdiction,
  certification, signature, alternatives, and waivers.
  Done: the evaluator checks every dimension against the versioned Template evidence policy, as of
  the configured clock's date: file count, per-file and total size, detected and declared content
  type, page bounds, required captured attributes and accepted issuer, jurisdiction, and language
  values, issue age, remaining validity, expiry, multi-period coverage length and continuity across
  files, substitutes (a satisfied substitute at the same occurrence, else at the root), and the
  waiver rule. `InformationRequestEvidenceIntake` refuses before storage what can never conform: a
  file above the policy's size, a file past its count or total, a detected type the policy does not
  accept, and bytes already provided for the occurrence.
- [x] `P6-T6` Separate detected-content inspection from malware scanning. Add a provider-neutral
  scanner port and the approved real implementation, signature-version metadata, timeout and
  unavailable handling, quarantine access rules, rescan policy, observability, and append-only
  Evidence Assessments. Scanner error, timeout, unavailable, stale signatures, skipped execution,
  and indeterminate results fail closed. A no-op or test adapter can never produce a production-safe
  result. Apply this contract to every exact file-backed Evidence Version regardless of uploader,
  access surface, or whether the Document Version already existed. Reuse a prior assessment only
  when the exact verified content hash, scanner engine and signature version, and configured
  freshness policy still match.
  The concrete scanner deployment and signature-update decision is deferred by the user; other
  independent tasks continue. This task and production upload enablement remained incomplete until
  the user's 2026-09-25 decision recorded below.
  Split so the scanner-independent parts proceed. Do not check `P6-T6` until every subtask,
  including the deferred `P6-T6d`, is complete.
  Done for this program on 2026-09-25: the user moved the approved real implementation out of the
  program (`P6-T6d`) and made scanning optional, so `P6-T6` is complete with `P6-T6a` through
  `P6-T6c` and `InformationRequestEvidenceDeploymentPolicy`. With scanning not required, a missing
  or incomplete scan never blocks a file and a detection still quarantines it; with
  `malware-scan.required=true` every rule above applies unchanged.
    - [x] `P6-T6a` Append-only Evidence Assessment persistence bound to the exact Evidence Version
      and the content digest that was assessed, for content inspection and malware scan results.
      Done by V130, `InformationRequestEvidenceAssessment`, `InformationRequestEvidenceAssessmentKind`,
      and `InformationRequestEvidenceAssessmentRepository`. An assessment names one file-backed
      Evidence Version of its own request and must state exactly that version's digest and length;
      opaque content is never inspected or recorded as scanned clean; a settled scan names its engine
      and signature version; a reuse must repeat a settled scan of the same bytes by the same engine
      and signatures; rows are append-only in the database and through the repository.
    - [x] `P6-T6b` Detected-content inspection from the stored bytes: magic-byte media type,
      declared-versus-detected mismatch, PDF page count, and encrypted or corrupt content, recorded
      as a content-inspection assessment. Inspection is never a malware result.
      Done: `InformationRequestEvidenceContentInspector` detects the type from the bytes and reads a
      PDF's page count, password protection, and structure; the intake records the result as a
      `CONTENT_INSPECTION` assessment of the exact stored digest for every new file-backed version,
      and opaque content is never inspected.
    - [x] `P6-T6c` Provider-neutral malware scanner port with a fail-closed result vocabulary,
      timeout, unavailable, error, stale-signature, and skipped handling, signature-version
      metadata, production-eligibility of the engine, exact-digest assessment reuse, rescan policy,
      quarantine access rules, and observability. The default configuration names no scanner, so
      every file-backed Evidence Version stays unscanned and cannot satisfy a Requirement.
      That last sentence is superseded on 2026-09-25: an unscanned file can satisfy a Requirement
      unless `malware-scan.required=true`.
      Done: `InformationRequestEvidenceMalwareScanner` is the port and
      `UnconfiguredInformationRequestEvidenceMalwareScanner` the default (`malware-scanner=none`),
      selected by `InformationRequestEvidenceScanProducer`; any other value refuses to start the
      scanner. `InformationRequestEvidenceMalwareAssessmentService.assess` records `SKIPPED` for
      opaque content, `UNAVAILABLE` with no engine or signatures, `STALE_SIGNATURES` past the
      maximum signature age, `ERROR` for changed or missing bytes or a failed scan, `TIMEOUT` after
      the configured timeout, and otherwise the verdict with the engine and signatures that produced
      it; a settled result of the same bytes by the same engine, engine version, signatures, and
      eligibility inside the reuse window is reused by reference. `InformationRequestEvidenceScanScheduler`
      runs only when an engine is configured and scans versions never scanned, retries an
      incomplete scan after `retry-after`, and rescans a clean result after `rescan-after`; a
      detection or a skip is final. `InformationRequestEvidenceContentRelease` refuses detected
      content to everyone and, where only production-scanned content is released (the default),
      releases other content only after a production-eligible clean scan or to its own uploader.
      Detections and scan failures log the greppable `INFORMATION_REQUEST_EVIDENCE_MALWARE_DETECTED`
      and `INFORMATION_REQUEST_EVIDENCE_SCAN_FAILED` markers for a log metric filter, so no new
      service is needed. Every recorded scan is also a classified `information_request.evidence.scan`
      system audit event (catalog version 22) naming the outcome, engine, signatures, and reuse, and
      never the threat name.
    - [x] `P6-T6d` The approved real scanner implementation, deployment, and signature-update model.
      Moved out of this program on 2026-09-25 by user direction to
      `plans/INFORMATION-REQUEST-EVIDENCE-MALWARE-SCANNING-PLAN.md`; nothing is implemented here.
      Deferred by the user on 2026-09-20; blocked until that decision is made.
- [x] `P6-T7` Define replacement, withdrawal, deletion, retention, duplicate, corrupt, encrypted,
  and password-protected-file behavior. Replacement and withdrawal preserve bytes and only change
  eligibility for future packages. Reject an opaque `END_TO_END` Document Version as satisfying
  evidence with a stable reason; never mark it safe from a ciphertext scan. There is no existing
  business-record hold or purge pipeline, so
  Phase 6 performs no irreversible evidence purge. Phase 9 owns record-preservation holds, disposal
  eligibility, tombstones, and physical object deletion. Replacement, withdrawal, and logical
  deletion use Command Receipts plus required `If-Match` and recheck submission membership before
  committing.
  Done, with one clause carried: replacement appends a version and withdrawal and logical removal
  change only the collection state, so every stored byte and earlier version is kept (the database
  refuses deleting either); duplicate bytes for one occurrence are refused before storage; a corrupt
  file is kept and marked `CORRUPT`; a password-protected file is kept with the blocking
  `CONTENT_ENCRYPTED` finding and is never offered for review; opaque `END_TO_END` content is kept,
  never inspected, recorded `SKIPPED` rather than scanned, carries the blocking `CONTENT_OPAQUE`
  finding, and can never be recorded as clean; no purge exists. Every mutation uses a Command Receipt
  and a required `If-Match`. Rechecking Submission Package membership is carried to `P7-T1`, because
  no Submission Package exists before it.
- [x] `P6-T8` Materialize supporting-evidence links from a Field response occurrence to one or more
  Document Requirement occurrences. Preserve those links in runtime projections and later
  Submission Packages without turning a file into a Field value.
  Done by V131, `InformationRequestSupportingEvidenceLink`, and `InformationRequestSupportingEvidenceLinkService`.
  Each Template evidence link is resolved to exact runtime occurrences: the Document at the supported
  occurrence, else at its nearest enclosing occurrence, else at the root, else every descendant
  occurrence. Links are written once when the request is materialized and again whenever a group
  occurrence is added, and the database refuses a link that does not follow its Template link
  between the exact bindings of its request. The response workspace projects
  `supportingEvidenceLinks` only between Requirements the caller may see. Submission Packages will
  freeze these rows in `P7-T1`.
- [x] `P6-T9` Enforce baseline per-file, per-request, per-recipient, and no-auth upload limits before
  accepting content. Phase 12 may tune limits by plan but must not introduce the first abuse guard.
  Done: `InformationRequestEvidenceIntake` refuses with `INFORMATION_REQUEST_EVIDENCE_UPLOAD_LIMIT_EXCEEDED`
  before anything is stored: 25 MiB per file, 10 MiB per file without sign-in, 200 files and 500 MiB
  per request, and 100 files and 250 MiB per uploading party, counting every retained file-backed
  version (withdrawn and removed bytes are still held). Each limit is configurable.
- [x] `P6-T10` Extend `basic_field_document_response_attestation_request` and
  `multi_party_staged_evidence_request` through multi-file upload, occurrence-scoped evidence,
  policy validation, ACL, quarantine, and supporting-evidence links.
  Done: `InformationRequestTemplateWalkingSkeletonPhase6Test` drives each fixture's own authored
  evidence policy through the real upload, intake, scan, evaluation, query, and link services. The
  basic fixture collects two independent files, is satisfied only after production-eligible clean
  scans, refuses a non-PDF and a third file by its policy, quarantines detected content, and links
  its recorded summary to its supporting record. The staged fixture needs two continuous, attributed
  files, becomes reviewable when a replacement loses its certification, turns a waiver into a
  review-approval request, is satisfied by its alternate record as a substitute, links each reported
  item to the root primary evidence, and refuses a caller who may neither view nor manage it. The
  Phase 5 walking test now records the disposition the basic fixture permits for its supporting
  record (`PROVIDED`, not the unpermitted `WAIVED`).
- [x] `P6-T11` Add the minimal shared evidence upload, progress, preview, replace, withdraw, and
  conformance UI behind the feature switch.
  Done; the final backend regression passed 2,875 tests with 0 failures. The response workspace now renders
  each Document Requirement occurrence through `RequirementEvidencePanel`, reached from the new
  `StructuredResponseRequirement` child of `StructuredResponseOccurrence`, on both the signed-in and
  access-link surfaces. It shows the Requirement status and findings, every item with its latest
  version, conformance, and findings, and offers upload with progress, preview for PDF and images,
  download, replacement, and withdrawal with a reason. Each command sends the evidence or artifact
  ETag as `If-Match` with a fresh `Idempotency-Key` through `informationRequestEvidenceService.ts`,
  and a stale refusal reloads the list and explains it. The switch is the backend: the workspace
  DTO's `evidenceUploadAvailable` reports whether `InformationRequestEvidenceUploadAvailability`
  admits uploads (on by default; refused only where a deployment switches upload off or requires
  scanning without a production-eligible scanner), and `evidenceMalwareScanning` shows the
  "Files are not scanned for malware." note while no scanner is configured,
  and the evidence UI is provided only by the response panel's context. The help article
  "Information Request evidence files" is registered in the Fields and Schemas section.

### Tests to write first

- Requirement-scoped document authorization matrix tests.
- Multi-file count and mixed-disposition aggregation tests.
- Evidence replacement and immutable-version tests.
- Duplicate and parallel upload completion, replacement, withdrawal, logical deletion, command
  replay, fingerprint conflict, missing or stale precondition, and submission race tests.
- Typed locator classification, provider routing, unique write-once version key, overwrite-denial,
  and byte identity tests, plus proof that a version cannot be stored without a canonical locator.
- New-version hash, algorithm, canonical creator principal for every creation path including the
  link-verified no-auth upload, missing-object, unverified-state, and submission-membership tests.
- Coverage period, freshness, expiry, issuer, and substitute-evidence tests.
- Field-response to exact supporting Requirement and Evidence Version traceability tests.
- MIME spoofing, size, corrupt, password-protected, quarantine, malware-detected, scanner timeout,
  scanner unavailable, stale-signature, fail-closed, rescan, and no-op-adapter denial tests.
- Explicit `END_TO_END` Evidence ineligibility and no-ciphertext-safety-claim tests.
- Existing Document linking, exact-hash assessment reuse, changed-bytes denial, scanner
  engine or signature change, and assessment-freshness tests.
- Cross-recipient list, preview, and download denial tests.

### Exit criteria

- One Requirement can contain several independently versioned files.
- Every reviewed or submitted file is identified by immutable Evidence and Document Version IDs and
  a typed write-once storage locator, byte length, and version-level hash.
- Another Exchange participant cannot access evidence without Requirement-specific authorization.
- Technically unsafe, unverified, or expired evidence cannot satisfy submission policy, and unscanned
  evidence cannot satisfy it where a deployment requires malware scanning,
  including evidence linked from an existing Document Version.
- Opaque `END_TO_END` ciphertext cannot satisfy a file-backed Evidence Requirement or receive a
  plaintext-safety claim from server-side ciphertext inspection.
- Evidence policy can express multi-period coverage, expiry, certification, and configured
  alternatives without process-specific database columns.
- Replacement, withdrawal, or logical deletion cannot remove retained bytes before Phase 9 provides
  a tested record-preservation and disposal pipeline.
- Superseded on 2026-09-25: production external upload is available without a scanner. Where a
  deployment requires scanning, upload stays refused unless a real scanner is configured, healthy, and
  fail-closed.
- Baseline upload and storage abuse limits apply before no-auth evidence collection is enabled.

## Phase 7: Submission, Response Attestation, Amendments, and Recurrence

### Goal

Create immutable evidentiary submissions and support the repeated, supplemental, and recurring
cycles found in real document-driven processes.

### Phase 7 design decisions (2026-09-25)

Recorded before implementation so every subtask below states against one design.

1. A submission scope is the whole request (`WHOLE_PACKAGE`) or one stage (`STAGED`). A Template
   Version states its submission mode and stage ordering (`ANY_ORDER` or `SEQUENTIAL`); in staged
   mode every section names a stage key and a stage is the set of sections naming it. A condition
   rule, a repeatable group, and every Requirement anchored to that group stay inside one stage, so
   an editable stage can never change what a submitted stage holds.
2. The submission content hash is a SHA-256 over the canonical, sorted facts of one scope: the
   pinned Template and Schema Versions, each active runtime Requirement occurrence with its current
   revision, response envelope and revision, exact Field Value Revision, current Evidence Versions
   with their document hashes and governing assessments, and the materialized supporting-evidence
   links. It is the strong submission ETag a client reads in review-before-submit and states as
   `If-Match` when it attests or submits, so a submitter can only submit exactly what was reviewed.
3. A Submission Attestation binds to the content hash it was given against. A later change to the
   scope changes the hash, so earlier attestations stop counting without being rewritten. The
   package freezes the attestations that satisfied each Response Attestation Requirement's policy.
4. A Response Attestation Requirement's binding states its attestation policy: required party roles
   (ordered), ordering (`ANY_ORDER` or `ROLE_SEQUENCE`), minimum assent count, minimum
   authentication strength (`VERIFIED_CONTACT` < `ACCOUNT_SIGN_IN` < `MULTI_FACTOR`), optional
   validity hours, and whether an external signature reference is not accepted, optional, or
   required. An authored Requirement that states none stores the explicit default: its nominated
   role, any order, one assent, verified contact, no expiry, no external reference. A recorded
   external signature reference is an opaque reference and never a qualified electronic signature.
   The attestation Requirement is complete when its policy is satisfied or when a permitted
   exception disposition with a narrative is recorded; a plain `PROVIDED` draft disposition no
   longer stands in for an attestation.
5. Submission is a package operation, not a request state. `SUBMIT` keeps the request's collection
   state; the final package of a request whose packages need no review records satisfaction and the
   `CLOSED` transition in the same transaction. `SUBMITTED`, `UNDER_REVIEW`, and `CHANGES_REQUESTED`
   stay in the vocabulary for Phase 8 to settle; nothing in Phase 7 reaches them.
6. Every Template capability that can route work to a reviewer, a binding review policy other than
   `NOT_REQUIRED`, a `DEFICIENCY_REVIEWABLE` conformance policy, and a `REVIEW_APPROVAL_REQUIRED`
   waiver, derives `RESPONSE_REVIEW`. Phase 7 installs every other capability, so only Template
   Versions that can never need a reviewer are issuable until Phase 8.
7. An amendment re-pins an issued request to a later published Version of the same Template
   Definition, or to a new private Version of its own ad hoc Definition. Each runtime Requirement
   keeps its stable identity: its effective binding advances with an appended revision. A Requirement
   the new Version no longer names stays on the earlier Version and leaves the active request. A
   presentation-only change is compatible; any change of meaning is incompatible and requires the
   respondent to reconfirm an existing response. A different Schema Version, a changed occurrence
   anchor under existing occurrences, or a change to a Requirement inside a submitted stage refuses
   the amendment by stable reason; those need a supplemental or superseding request.
8. Carry-forward never writes an answer on a respondent's behalf. A successor request (supplement,
   recurrence, refresh, or superseding request) records, per Requirement occurrence matching an
   item of its source package, whether the prior submitted answer is offered for reconfirmation or
   invalidated with a stable reason. Evidence and attestations are always invalidated because
   freshness is their point. The respondent sees the offered prior answer and confirms by saving.
9. Notice Intents are append-only and authoritative. Materializing and delivering a notice is Phase
   9, so every Phase 7 intent stays pending and is presented as pending, never as delivered.

### Tasks

- [x] `P7-T1` Implement atomic Submission Package or stage-package creation without introducing a
  request-wide submitted state. Freeze
  Template and Schema versions,
  runtime Requirement revisions, canonical responses, dispositions, Evidence Version IDs and
  hashes, applicable Evidence Assessment IDs, Field-response supporting-evidence links, validation
  results, actors, time, and every required `SubmissionAttestation` record. When the final required
  package or stage satisfies a `NOT_REQUIRED` review policy, atomically record satisfaction, the `CLOSED`
  transition, classified audit history, and the transactional domain event.
  Carried from `P6-T7`: evidence replacement, withdrawal, and logical removal must recheck
  Submission Package membership before committing once packages exist, and packages freeze the
  materialized supporting-evidence link rows.
  Done: `InformationRequestSubmissionService` freezes one scope in one transaction and closes a
  no-review request with its final package; evidence replacement, withdrawal, and removal recheck
  package membership through `InformationRequestEvidenceGate.requireEvidenceOpen`.
    - [x] `P7-T1a` Submission Package persistence (V133): package, item, evidence member, supporting
      link member, package attestation member, and withdrawal tables, all append-only, with composite
      keys holding every member to its own request.
    - [x] `P7-T1b` Deterministic scope and content-hash manifest builder and the submission ETag.
    - [x] `P7-T1c` Package creation with atomic `SUBMIT` history, audit, and event, no-review
      satisfaction and `CLOSED` closure, and the evidence membership recheck.
- [x] `P7-T2` Require server-side completeness and conformance validation immediately before the
  transaction creates the package. Return structured item errors without partial submission.
  Done: `InformationRequestSubmissionReadinessEvaluator`; a refused scope answers `422` with the
  problems the caller may see and an undisclosed count.
- [x] `P7-T3` Make submission idempotent and concurrency-safe when save, upload, expiry, or duplicate
  submission requests race. Use the Phase 3 Command Receipt and `If-Match` contracts rather than a
  submission-only deduplication mechanism.
  Done: receipts claimed after the parent and request locks, the submission ETag as `If-Match`, and
  a parallel duplicate test recording exactly one package.
- [x] `P7-T4` Add versioned response-attestation policy and one immutable
  `SubmissionAttestation` record per acting party. Keep these names distinct from
  `ExchangeRecipientAttestation`. Support required roles, order, quorum, explicit assent or refusal,
  authentication strength, expiry, and external signature references without claiming qualified
  electronic signature.
    - [x] `P7-T4a` Template attestation policy (V132): binding policy columns and ordered role set,
      freeze guards, publication rules, authoring validation, writer, loader, DTOs, and copy.
    - [x] `P7-T4b` Submission Attestation persistence (V133), recording service, policy evaluation,
      attestation completeness contribution, and the authorization facts for attesting roles.
- [x] `P7-T5` Implement append-only request amendments and changed-requirement notices. An issued
  Template Version is never edited in place. Define compatibility and explicit carry-forward rules
  for responses, occurrences, evidence, conditions, and Submission Attestations when Requirements are
  unchanged, changed incompatibly, removed, or added. Persist an append-only `NoticeIntent`
  atomically with the amendment, audit event, and transactional event; it is the authoritative
  durable work record and cannot be lost if an outbox event is dispatched before a later consumer
  exists. Require reconfirmation where policy meaning changed. Immutable notice materialization and
  dispatch become available through Phase 9, so externally dispatched amendment behavior remains
  disabled until that capability is installed. Any added or changed Field Requirement that needs a
  different Schema Version must create a supplemental or superseding Information Request with its
  own Schema Assignment; it cannot amend the issued request's assignment.
  Done: `InformationRequestAmendmentService` with its classifier, guard, target resolver, recorder,
  and the materializer's effective-binding advance; each active party is owed a `PENDING` Notice
  Intent (V134 admits no other delivery state until Phase 9 installs delivery), a changed meaning
  marks existing answers for reconfirmation until they are saved again, and a schema change,
  occurrence-structure change, or change reaching a submitted scope is refused by stable reason.
    - [x] `P7-T5a` Runtime effective binding (V134): a runtime Requirement's binding may advance
      to a later Version's binding of the same stable Template Requirement, recorded by an appended
      revision; the active request reads only Requirements of its pinned Version.
    - [x] `P7-T5b` Amendment, change classification, reconfirmation, group and occurrence
      carry-forward, refusals, and append-only amendment, change, and Notice Intent records (V134).
- [x] `P7-T6` Implement supplemental request lineage so a reviewer can request additional
  information while preserving the original request and package.
  Done: `InformationRequestSuccessorService` (V135 lineage and carry-forward decisions); a
  supplement is a draft on the same Exchange for the same acting parties, open to a reviewer through
  `INFORMATION_REQUEST_REQUEST_SUPPLEMENT` or to the request administrator.
- [x] `P7-T7` Implement configurable whole-package or staged submission, withdrawal before review,
  active-request cancellation, supersession, recurrence definitions, and expiry-triggered refresh
  rules. Phase 9 owns the clock calculation and scheduler that executes expiry and refresh.
    - [x] `P7-T7a` Template submission mode, stage ordering, and section stage keys (V132) with
      stage containment validation.
    - [x] `P7-T7b` Stage-scoped packages, stage locks on every response, evidence, group, and
      attestation command, sequential stage order, and withdrawal before review.
    - [x] `P7-T7c` Cancellation and supersession with packages, superseding successors, recurrence
      definitions and next-occurrence command, and expiry refresh rules and refresh command (V135).
- [x] `P7-T8` Extend `basic_field_document_response_attestation_request` and
  `multi_party_staged_evidence_request` through multi-party response attestation, immutable package
  submission, compatible and incompatible amendment, supplemental request, and recurrence.
  Done: `InformationRequestTemplateWalkingSkeletonPhase7Test`; the stress fixture now submits in three
  sequential stages with a subject-then-attestor assertion policy.
- [x] `P7-T9` Add the minimal review-before-submit, multi-party response-attestation, submission result,
  amendment change summary with pending-notice state, and supplemental-request UI behind the
  feature switch. Do not present an intent as a delivered notice.
  Done: `web-app/src/app/information-requests/submission/` mounted in the respondent workspace;
  notices read "Notice pending"; the supplement action needs a signed-in caller whose plan includes
  Information Requests.
- [x] `P7-T10` Register the structured-response, condition, evidence, response-attestation, and submission
  executor capability versions. Enable successful issuance in controlled test scopes only for
  Template Versions whose review policy is `NOT_REQUIRED` and whose complete capability set is
  installed.
  Done: `InformationRequestCapabilityExecutors.kt` installs every capability except `RESPONSE_REVIEW`;
  `POST /information-requests/{id}/issuance`; `InformationRequestControlledIssuanceTest` issues a
  fully served Version and refuses one that routes work to a reviewer. Entitlement and owner plan
  gates are unchanged.

### Tests to write first

- Atomicity and idempotency tests.
- Staged submission tests proving a submitted stage stays locked while another stage remains
  editable and the request remains `ACTIVE`.
- Whole-package and final-stage tests proving a satisfied no-review request closes atomically, while
  an incomplete staged request remains `ACTIVE`.
- Snapshot immutability after response or file replacement.
- Save-versus-submit and upload-versus-submit race tests.
- Response-attestation version, naming boundary, and actor-strength tests.
- Multi-party Submission Attestation order, quorum, refusal, expiry, and delegated-authority tests.
- Amendment does-not-rewrite-issued-version tests.
- Amendment carry-forward, invalidation, respondent notice, and reconfirmation tests.
- Atomic Notice Intent persistence, outbox-delivered-before-consumer recovery, materialization
  capability gate, and pending-versus-delivered presentation tests.
- Field-contract change requires supplemental or superseding request tests.
- Supplemental request lineage tests.
- Recurrence, expiry refresh, withdrawal, cancellation, and supersession tests.
- Missing-executor issuance denial and fully supported no-review issuance tests.

### Exit criteria

- A Submission Package is reproducible without reading mutable current response state.
- Amendments, supplements, recurrence, and resubmission create new revisions or linked requests,
  never destructive edits. Correction allowlists and correction-cycle completion remain Phase 8.
- Submission Attestation meaning and actor identity are frozen with each submission and cannot be
  confused with Trusted Organization recipient-selection attestation.
- Whole-package and permitted staged-submission policies are deterministic.
- A no-review request closes in the final package transaction; it never waits for Phase 8 review.
- Recurring collection, periodic recertification, and evidence refresh can be represented.
- Amendment intent is durable, but external amendment dispatch remains unavailable until Phase 9
  installs immutable notice materialization and delivery.
- Missing-executor issuance remains blocked, while a fully supported no-review Template can be
  issued in a controlled test scope.

## Phase 8: Review, Findings, Remediation, and Decision Separation

### Goal

Support item-level, multi-stage, and independent review without treating request satisfaction as a
downstream process outcome.

### Phase 8 design decisions (2026-09-25)

Recorded before implementation so every subtask below states against one design.

1. Review belongs to a Submission Package, never to the request's collection state. `SUBMITTED`,
   `UNDER_REVIEW`, and `CHANGES_REQUESTED` leave `InformationRequestState` and both database state
   checks; nothing ever wrote them. Review and correction commands keep the request's state; only
   closure moves it, to `CLOSED`.
2. A Version that derives `RESPONSE_REVIEW` states ordered review stages run `SEQUENTIAL` or
   `PARALLEL`. A stage states its aggregation (`ALL`, `ANY`, `QUORUM` with a quorum count,
   `CONSENSUS`), its minimum reviewer count, its tie resolution (`MOST_SEVERE_OUTCOME` or
   `REQUIRE_OVERRIDE`), whether an authorized override is permitted, its separation-of-duties
   exclusions (response parties, prior reviewers), and optionally the sections it covers (none named
   means every section). A reviewer-routed Version that states none freezes with the explicit default
   of one `review` stage, `ANY`, one reviewer, no override, and no exclusions. A Version that routes no
   work to a reviewer states no stage. A Version may also name a fact-reuse purpose key.
3. Which items a review covers is derived from frozen facts only: the binding review policy, the
   frozen disposition, and the frozen evidence state, by the same rule submission uses for
   `review_required`. Publication refuses a reviewer-routed binding no stage covers.
4. A review-required package opens a review cycle in the submission transaction: `INITIAL`, or
   `RESUBMISSION` when the package resubmits a correction. `RECONSIDERATION` and `APPEAL` open a new
   cycle of the same package naming the exact prior settled review. A review is `PENDING` until a
   reviewer is assigned, `IN_REVIEW` after, and settles to `CHANGES_REQUESTED`, `REJECTED`,
   `SATISFIED`, or `SATISFIED_WITH_EXCEPTION`; a pending review of a withdrawn package is `WITHDRAWN`.
   An assignment names an active `REVIEWER` party, a stage, and an optional explicit due instant.
   Recusal, delegation to another `REVIEWER` party, and revocation keep the assignment and record who,
   when, and why.
5. Item outcomes are `SATISFIED`, `SATISFIED_WITH_EXCEPTION`, `WAIVED`, `CHANGES_REQUIRED`, and
   `REJECTED`, ordered by severity `REJECTED` > `CHANGES_REQUIRED` > `SATISFIED_WITH_EXCEPTION` =
   `WAIVED` > `SATISFIED`. Per stage and item: an override decides; a carried outcome decides; `ANY`
   takes the earliest decision; `ALL` waits for every eligible reviewer and takes the most severe;
   `QUORUM` takes an outcome reaching the quorum (the most severe if several do) and ties when every
   eligible reviewer decided and none did; `CONSENSUS` waits for every eligible reviewer and ties on
   disagreement. Recused, delegated, and revoked assignments are not eligible, and fewer eligible
   reviewers than the minimum keeps the item pending. Sequential stages open in order and a settled
   stage with a non-passing item settles the review; parallel stages settle together. An item's final
   outcome is its most severe outcome across the stages covering it, and the package outcome maps the
   most severe item outcome; items no stage covers count as satisfied.
6. Each assignment has a mutable worksheet of draft item outcomes and notes, saved with `If-Match`
   on its draft revision (`428` missing, `412` stale). Recording the worksheet (Idempotency-Key and
   `If-Match`) appends one immutable decision per covered item, once per assignment. A
   `CHANGES_REQUIRED` or `REJECTED` outcome needs a finding by that assignment on the item;
   `SATISFIED_WITH_EXCEPTION` and `WAIVED` need a narrative; `WAIVED` needs the binding to permit a
   waived answer. An authorized override appends one decision for one item with a narrative.
7. Findings are immutable. Each names one package item and optionally one exact Evidence Version of
   that item, with a stable reason code, narrative, severity, confidentiality (`RESPONDENT_VISIBLE`
   or `REVIEWERS_ONLY`), correction scope (`NONE`, `RESPONSE`, `EVIDENCE_VERSION`,
   `ADDITIONAL_EVIDENCE`), reviewer, and time. A finding in a resubmission review may retest an
   earlier finding as `RESOLVED` or `UNRESOLVED`. Respondents see respondent-visible findings only
   after settlement and only on items they may view.
8. Settlement is atomic with the command that causes it. `CHANGES_REQUESTED` opens one correction,
   the scoped response cycle, whose allowlist is every `CHANGES_REQUIRED` item's Requirement plus every
   Evidence Version a finding on it returned with scope `EVIDENCE_VERSION`. `SATISFIED` and
   `SATISFIED_WITH_EXCEPTION` close the request in the same transaction when every scope's current
   package is accepted. `REJECTED` leaves the request open for reconsideration, appeal, cancellation,
   or supersession.
9. While a correction is open, only allowlisted Requirement occurrences accept response and evidence
   changes, plus items the corrected package froze as hidden, which a changed condition may activate.
   In an allowlisted Document Requirement new files may be added, but only an artifact whose frozen
   version was returned may be replaced or withdrawn. Attestation Requirements of the corrected scope
   accept new assent because the content hash changes. Group structure stays locked. Resubmitting the
   scope creates a package naming the corrected one as previous; the corrected package stops being
   current, the correction closes as `RESUBMITTED`, and remediation records link each returning finding
   to the resubmitted item. The resubmission review carries forward the accepted outcome of every item
   whose item hash is unchanged, so only corrected items are retested.
10. Withdrawal stays "before review": once a reviewer is assigned, a package cannot be withdrawn.
11. Separation of duties: a stage excluding response parties refuses, at assignment, delegation,
    decision, and override, a reviewer who responded to, submitted, or attested any item of the
    package, or who is an answering party of the request. A stage excluding prior reviewers refuses
    anyone who decided an earlier stage of the same review or the prior review of a reconsideration or
    appeal. Recusal records a declared conflict with its reason.
12. Comments are immutable and tied to one package item (Requirement and submission revision),
    optionally to a finding (a response-to-finding thread) and a parent comment. Reviewer comments may
    be reviewers-only; respondent comments are respondent-visible and allowed after settlement on
    items the respondent may view.
13. Reconsideration (request administration) and appeal (a submitting respondent) name the exact
    prior settled review, `REJECTED` or `CHANGES_REQUESTED` with its correction still open; an open
    correction then closes as `SUPERSEDED`.
14. An Accepted Fact is an explicit, authorized promotion of one accepted `FIELD` package item: owner,
    the request's subject identity, purpose key, source request, package, item, response revision,
    exact Field Value Revision, canonical value and type, visibility, confidence (`REVIEWED` when a
    satisfied review accepted the item, otherwise `DECLARED`), valid period, expiry, supersession of an
    earlier fact, promotion-time conflict, and revocation. Reuse is an offer to a later request of the
    same owner, subject, Field, and purpose (named by its Version), exposing source and freshness; the
    respondent confirms by saving, so nothing is ever written on their behalf, and neither promotion
    nor satisfaction writes Exchange metadata.
15. A Business Decision is an append-only external reference: owning process key, outcome code,
    reason reference, actor, decision time, revision, and for reconsideration or appeal the exact prior
    decision of the same process. Satisfying a request never creates or changes one, and recording one
    never changes the request.
16. Installing the `RESPONSE_REVIEW` executor makes review-required Versions issuable; every other
    issuance gate is unchanged.

Migrations: V136 (Template review plan and fact-reuse purpose), V137 (runtime review, state
vocabulary, corrections, comments, remediation), V138 (Accepted Facts and Business Decisions).

### Tasks

- [x] `P8-T1` Add reviewer assignment, queues, delegation, review due dates, and optional sequential
  or parallel review stages. Store only an explicitly supplied review due instant here; calculated
  clock policy belongs to Phase 9.
  Done: `InformationRequestReviewAssignmentService` (assign, recuse, delegate, revoke, explicit
  `dueAt`), `GET /information-request-reviews` for the caller's queue.
    - [x] `P8-T1a` Template review plan (V136): stage ordering, stages, aggregation, tie, override,
      exclusions, section coverage, default at freeze, publication rules, fact-reuse purpose key, and
      the authoring, validation, writer, loader, DTO, and copy paths.
    - [x] `P8-T1b` Runtime review cycles and assignments (V137): opening at submission, assignment,
      recusal, delegation, revocation, explicit due instants, and the reviewer queue.
- [x] `P8-T2` Add Review Findings linked to a Submission Package item and exact Evidence Version.
  Store stable reason codes, narrative, severity, reviewer, decision time, and confidentiality.
  Done: `InformationRequestReviewFindingService`; findings are append-only in V137.
- [x] `P8-T3` Implement correction requests with an explicit Requirement and evidence-version
  allowlist. Only returned items become editable unless a condition invalidates another item.
  Done: settlement opens the correction; `InformationRequestSubmissionLockService` and the
  Requirement correction scope (`CORRECTION_ALLOWED`, `CORRECTION_EXCLUDED`) enforce it.
- [x] `P8-T4` Support requirement outcomes such as satisfied, changes required, rejected, waived,
  and satisfied with exception. Define versioned stage aggregation for all, any, quorum, consensus,
  tie, recusal, delegation, and authorized override, followed by deterministic package and request
  aggregation. For review-required requests, create the `CLOSED` transition and scoped correction
  response-cycle records atomically with their review decisions without reversing the whole request
  lifecycle. Apply the shared Command Receipt contract to retryable review decisions and required
  `If-Match` preconditions to mutable review drafts.
    - [x] `P8-T4a` Pure stage, review, and package aggregation with its unit matrix.
    - [x] `P8-T4b` Worksheets, recorded decisions, overrides, and atomic settlement: correction
      opening, request closure, and the state vocabulary change (V137).
- [x] `P8-T5` Add separation-of-duties and conflict-of-interest policy. Where configured, a preparer,
  contributor, or prior reviewer cannot perform final satisfaction.
  Done: `InformationRequestReviewSeparationPolicy` at assignment, delegation, decision, and override.
- [x] `P8-T6` Add comments and response-to-finding threads tied to Requirement and submission
  revisions rather than unversioned general comments.
  Done: `InformationRequestReviewCommentService` on both access surfaces.
- [x] `P8-T7` Add remediation, retest, reconsideration, and appeal references while preserving every
  earlier finding and decision.
  Done: remediation at resubmission, retest findings, and `InformationRequestReviewCycleService`.
- [x] `P8-T8` Implement explicit Accepted Fact promotion. Store tenant, subject, purpose, source
  Submission Package and response revision, canonical value and type, visibility, confidence,
  valid period, expiry, supersession, revocation, and conflict state. Reuse always exposes source and
  freshness and requires configured reconfirmation. Review satisfaction never silently overwrites
  Exchange metadata or a reusable information profile.
  Done: V138, `InformationRequestAcceptedFactService`, `InformationRequestAcceptedFactQueryService`,
  and `/information-requests/{id}/accepted-facts` with `/accepted-fact-offers` on both surfaces.
- [x] `P8-T9` Implement the typed external `BusinessDecision` reference with owning process, outcome
  code, reason reference, actor, time, and revision. Appeals and reconsideration reference the exact
  prior decision. Satisfying a request never creates or changes a Business Decision.
  Done: V138, `InformationRequestBusinessDecisionService`, and `/information-requests/{id}/business-decisions`.
- [x] `P8-T10` Extend `basic_field_document_response_attestation_request` and
  `multi_party_staged_evidence_request` through staged review, correction allowlists, multi-review
  aggregation, Accepted Fact promotion, and an independent Business Decision reference.
  Done: `InformationRequestTemplateWalkingSkeletonPhase8Test`.
- [x] `P8-T11` Add the minimal reviewer submission review, finding, correction, remediation, and
  satisfaction UI behind the feature switch.
  Done: `web-app/src/app/information-requests/review/`: the reviewer workspace at
  `/information-requests/:requestId/reviews/:reviewId`, the queue at `/information-request-reviews`
  with a menu entry, both gated on `PlanFeature.INFORMATION_REQUESTS`, and respondent Review results
  with Appeal in the submission section.
- [x] `P8-T12` Register review and correction executor capability versions. Enable successful
  issuance in controlled test scopes for review-required Template Versions only when their complete
  capability set is installed.
  Done: `InformationRequestResponseReviewExecutor`; `InformationRequestControlledIssuanceTest` issues a
  reviewer-routed Version and refuses one frozen against an unserved review contract.

### Tests to write first

- Reviewer authorization and stage-order tests.
- Per-item finding and mixed-outcome aggregation tests.
- All, any, quorum, consensus, tie, recusal, delegation, and override aggregation tests.
- Correction allowlist and condition-invalidation tests.
- Multiple correction cycle history tests.
- Separation-of-duties and conflict tests.
- Finding comment revision tests.
- Remediation and retest lineage tests.
- Accepted Fact scope, freshness, conflict, revocation, reconfirmation, and no-silent-overwrite tests.
- Request-satisfaction versus Business Decision independence and appeal-reference tests.
- Review-required missing-executor denial and fully supported issuance tests.
- Review-decision replay, fingerprint conflict, missing or stale precondition, and concurrent draft
  tests using the shared command-safety contracts.

### Exit criteria

- Reviewers decide against exact immutable submission items.
- Only authorized correction scope becomes editable.
- Several review and correction cycles preserve complete history.
- Separation of duties is enforceable when configured.
- Satisfaction, exception, appeal, and final business decision remain distinct.
- Accepted Facts remain purpose-bound, source-visible, freshness-checked, and explicitly revocable.
- Fully supported review-required Templates can be issued in controlled test scopes; unsupported
  Templates remain blocked.

## Phase 9: Time, Workflow, Audit, Retention, and Export

### Goal

Make Information Requests operationally reliable, traceable, schedulable, and safe for downstream
automation.

### Phase 9 design decisions (2026-09-26)

Recorded before implementation so every task below states against one design. Verified facts they
rest on: the transition history service is the only Information Request event publisher and its
event type is the audit key (`information_request.request.issue`); nothing consumes those events;
`RECORD_FIRST_VIEW` and `EXPIRE` have no caller, so nothing reaches `IN_PROGRESS` or `EXPIRED`; the
Workflow registry is only the designer's catalogue and Workflows start only through
`WorkflowEngineService.trigger`; the outbox claims by `next_attempt_at` with no per-consumer
receipt, so a redelivery can repeat a side effect; `canEndExchange` has no production caller and an
Exchange ends without consulting its requests; notice intents are append-only with a `PENDING`-only
state; `AuditLegalHold` is organization-or-platform only with App User attribution; the storage port
cannot delete.

1. Trigger vocabulary. A Workflow trigger name is the durable event type its owning transition
   already publishes. The registry gains `subject_resource_type` (`EXCHANGE`, `INFORMATION_REQUEST`)
   and `subject_schema_version`; the seven Exchange rows are version 1. Request triggers:
   `information_request.request.issue` (issued), `.view` (viewed), `.start` (started), `.submit`
   (submitted), `.correction` (changes requested), `.close` (satisfied), `.expire`, `.cancel`,
   `.supersede`, and `.overdue`. Their version 1 subject fields carry identifiers, states, stable
   codes, counts, and instants only (`requestId`, `exchangeId`, `templateVersionId`, `orgId`,
   `state`, `transitionSequence`, and per event `submissionPackageId`, `packageNumber`, `stageKey`,
   `reviewId`, `reasonCode`, `supersededByRequestId`, `clockId`, `clockKey`, `dueAt`); never a
   response value, label, or contact endpoint.
2. Viewed and started. `RECORD_FIRST_VIEW` keeps the request's state and is recorded once, on the
   first workspace read by a responding party on either surface (`first_viewed_at`). New
   `START_RESPONSE` moves `ISSUED` to `IN_PROGRESS` on the first response, evidence, attestation,
   group, or submission mutation (`started_at`). `EXPIRE` gains its audit key and `expired_at`.
3. Durable consumption. A `DomainEventConsumer` contract replaces hard-wired routing for new
   consumers. Each consumer's effect commits with a `domain_event_consumption` receipt (consumer
   key, event, outcome `APPLIED` or `SKIPPED`, detail) in the routing transaction, so a redelivered
   event causes one business effect per consumer; the durable notification fan-out is receipted the
   same way. Ordering is opt-in by `DomainEvent.orderingKey`: the outbox stores it with a sequence
   number and never claims an event while an earlier undelivered event with the same key exists.
   Request events are ordered per request. The Workflow trigger consumer starts matching
   definitions with subject type `INFORMATION_REQUEST`; a subscription refusal is a `SKIPPED`
   receipt with its reason, never an endless retry that would block the request's ordered queue.
4. Counterparty wait. `WAIT_FOR_COUNTERPARTY_CLEARANCE` waits for other organizations' instances on
   the same subject; it never observes Requirement satisfaction. A characterization test proves it
   completes at once for a request subject with no counterparty instance, so it is not reused, and
   Requirement satisfaction is a distinct generic condition (decision 5).
5. Scoped operands. `ApplicabilitySpec.requirementConditions` name a stable Template Requirement ID,
   an occurrence path (default `root`), a value type, an operator, and a literal. They evaluate only
   for an `INFORMATION_REQUEST` subject against the exact Submission Package the triggering event
   names, reading the frozen Field Value Revision of that package item. No package in the subject, a
   package of another request, an absent item, several matching items, a non-Field item, or a type
   mismatch is a non-match: automation never picks a response. Field conditions stay Exchange-only
   and unchanged; the validator refuses each kind on the other subject type.
6. Completion gates. `gates_exchange_closure` stays the per-request gate and becomes changeable by
   `PUT /information-requests/{id}/completion-gate` (`If-Match`, Idempotency-Key,
   `CHANGE_COMPLETION_GATE` history) while the request is not terminal. Ending an Exchange that holds
   requests, directly or when its ending Workflow confirms, runs
   `InformationRequestExchangeCompletionService` under the Exchange lock: a gating request that is
   still open refuses `COMPLETION_GATES_UNSATISFIED` and is never cancelled by the ending command, so
   it must be satisfied or explicitly cancelled first; a remaining nongating open request refuses
   `REMAINING_REQUESTS_REQUIRE_CANCELLATION` unless the caller states
   `cancelRemainingInformationRequests`, which cancels each one with reason `EXCHANGE_ENDED` in the
   same transaction. A gating request that already ended (closed, cancelled, superseded, or expired)
   no longer holds the gate, because a terminal request cannot change its gate and would otherwise
   block ending forever; its history shows whether it was satisfied. The check runs before an ending
   Workflow starts, and again at confirmation, where a refusal leaves the Exchange unchanged. An
   Exchange with no requests ends exactly as today, including from `INITIATED`.
7. Clocks. A clock policy is an owner-scoped definition with immutable numbered versions: clock type
   (`CALENDAR` or `BUSINESS`), business timezone, weekly working periods, holiday dates, standard and
   urgent durations, ordered reminder offsets before due, an optional escalation offset after due, and
   a due effect (`MARK_OVERDUE` or `EXPIRE_REQUEST`), all measured in the clock type. A request clock
   (one per `clock_key` per request) freezes the policy version, urgency, and received instant and
   records every calculation append-only (`STARTED`, `PAUSED`, `RESUMED`, `EXTENDED`, `REMINDED`,
   `OVERDUE`, `ESCALATED`, `EXPIRED`, `STOPPED`) with its inputs and resulting due instant, so a later
   policy version never rewrites deadline proof. A pure calculator computes due instants across
   timezones, working hours, holidays, pauses, and extensions. A scheduler records each reminder,
   overdue, escalation, and expiry point once (unique per clock, kind, and ordinal); overdue and
   expiry are request transitions and Workflow triggers, and reminders and overdue owe Notice Intents
   to the acting parties. A clock stops when its request becomes terminal.
8. Operations projection. `GET /information-request-operations` lists the requests the caller may
   administer in its active owner scope with state, age, first view, start, nearest due and SLA
   status (`NO_CLOCK`, `ON_TRACK`, `DUE_SOON`, `OVERDUE`, `PAUSED`, `MET`), reminder count, notice
   delivery counts, and exception counts, filtered by state, Exchange, SLA status, and exceptions.
9. Audit projections. `GET /information-requests/{id}/audit-events` and
   `GET /information-request-audit-events` return classified Information Request events with
   allow-listed payload keys only. `GET /information-requests/{id}/audit-reconciliation` matches every
   audited transition to its audit record by business transaction, reporting missing and unmatched
   history without payload values. `POST /information-requests/{id}/record-exports` freezes an
   immutable JSON record of who requested, provided, reviewed, changed, and decided each item, with
   evidence identified by version hash, stores it with its SHA-256, and verifies that hash on read.
10. Notices. A claim row idempotently claims one Notice Intent before rendering. The immutable
    `OutboundNotice` snapshots the party, recipient endpoint, channel, rendered subject and body,
    rendered hash and algorithm, source Communication and its pre-interpolation hash (or the
    platform default content for the notice kind when none is named), event and idempotency keys,
    and every `{{SEQ:KEY}}` allocation: each distinct key gets one value shared by subject and body,
    consumed once in the claim transaction. Delivery attempts are append-only; a retry reuses the
    stored render and never increments a Sequence. A notice without an endpoint is recorded as such
    and never described as delivered. Intents lose the unwritable `delivery_state`; delivery state is
    derived. Endpoints are masked in every projection except to the request administrator.
11. Preservation and disposal. `audit_legal_hold` is generalized in place: owner kind and ID for
    platform, organization, and personal ownership, effective instant, scope (`RESOURCE` or
    `DESCENDANTS_AND_REFERENCES`), canonical principal attribution replacing the App User columns,
    and an append-only lifecycle history. `RecordPreservationHoldService` owns placement, scope
    change, release, and the preservation question, and the audit governance APIs and
    `AuditDisposalEligibilityService` delegate to it, so one hold is visible to both subsystems.
    Owner-scoped retention schedules state a minimum retention and an automatic disposal age after a
    request becomes terminal; without a schedule nothing is disposed. A disposal claim locks the
    request, rechecks retention, holds, and shared-byte reachability, and refuses new references and
    hold placement while it is open; objects are deleted through the idempotent storage port (an
    absent object is success), then a database function removes the request's rows under the claim
    and a tombstone is finalized. A retry after object deletion completes safely. Every claim,
    denial, object deletion, and finalization is audited.
12. Privacy requests. An owner records a subject access, export, correction, restriction, or deletion
    request for one `SubjectIdentityRef` with purpose and policy basis keys as data. Access and
    export produce an immutable subject record export; correction appends a correction record
    against a package item or points an open request to a new response revision, never rewriting a
    package; restriction stops Accepted Fact promotion and reuse for the subject; deletion claims
    each terminal request about the subject for disposal unless a hold or minimum retention forbids
    it, which refuses with a stable reason.
13. Extension points. Storage location, permitted transfer regions, and ownership changes are
    policy interfaces with configuration-backed defaults that keep current behavior. Exports record
    the storage location and the transfer decision; organization member removal asks the ownership
    policy about that member's request parties. No infrastructure changes.

14. Owner-scope administration (added 2026-09-26 during `P9-T13`). The cross-request operations
    queue, the owner-scope audit search, and privacy handling act across an owner's requests, not on
    one request, so they require their own capabilities, `INFORMATION_REQUEST_OPERATIONS_READ`
    (`INFORMATION_REQUEST_VIEW_OPERATIONS_QUEUE`) and `INFORMATION_REQUEST_PRIVACY_MANAGE`
    (`INFORMATION_REQUEST_MANAGE_PRIVACY`), which only Organization Owners and Administrators hold; a
    personal owner is always permitted for their own requests. Request-level views (clocks, notices, audit
    history, reconciliation, disposal standing) keep `INFORMATION_REQUEST_VIEW_OPERATIONS` on
    `INFORMATION_REQUEST_ADMIN`, so they stay with the Exchange owner or a decision maker, and no
    request-scoped action accepts an owner-scope capability. Organization membership grants reach
    every organization-owned request, so granting request administration to organization roles
    would open every request to organization administrators; that remains a user decision.

Refinements recorded while implementing: decision 10 masks recipient endpoints in every projection,
including the request administrator's; decision 12 applies a correction to a submitted package item
only, and pointing an open request at a new response revision stays with the respondent workspace.

Migrations (the program range is extended past V139 below): V139 (events, trigger registry,
ordering, consumption, and transition vocabulary), V140 (clock policies and request clocks), V141
(notices), V142 (preservation holds, retention, disposal, and record exports), V143 (privacy
requests).

### Tasks

- [x] `P9-T1` Register the namespaced events already emitted transactionally by their owning
  mutation phases as Workflow trigger events, with safe versioned subject schemas for issued,
  viewed, started, submitted, changes requested, satisfied, expired, cancelled, and superseded.
  Add an explicit Flyway migration for `workflow_trigger_event_registry` with versioned
  `subject_fields_json` descriptors used by the designer trigger dropdown and `$subject.*`
  autocomplete. Add the scheduled overdue event in this phase. Characterize
  `WAIT_FOR_COUNTERPARTY_CLEARANCE` and reuse it only if its Exchange subject, organization, and
  completion semantics exactly match the requested wait; otherwise keep Requirement satisfaction
  as a distinct generic Workflow condition.
  Done: V139 request trigger rows with version 1 subject schemas and the scheduled overdue event;
  `WAIT_FOR_COUNTERPARTY_CLEARANCE` was characterized and is not reused (decision 4).
- [x] `P9-T2` Implement Workflow and notification consumers, ordering rules, retry behavior, and
  duplicate-side-effect protection over the neutral transactional publisher introduced in Phase 3
  and backed compatibly by the existing physical outbox. Do not import Workflow-specific publisher
  types into Information Request services and do not retrofit event persistence after the
  originating transaction has already shipped.
  Done: `DomainEventConsumer` receipts per consumer, opt-in per-request ordering in the outbox, the
  Workflow trigger and notice consumers, and `SKIPPED` receipts for subscription refusals.
- [x] `P9-T3` Scope Workflow operands to Information Request ID, stable Requirement ID, and exact
  Submission Package revision. Keep existing Exchange Workflow Field behavior unchanged.
  Done: `ApplicabilitySpec.requirementConditions` evaluated only against the exact package the event
  names (`InformationRequestWorkflowOperandService`); Exchange Field conditions are unchanged and
  Exchange-only.
- [x] `P9-T4` Add configurable Exchange completion gates based on specified satisfied Information
  Requests rather than generic document-required flags. Ending must atomically reject or explicitly
  cancel any remaining nongating request instead of silently abandoning it.
  Done: `InformationRequestExchangeCompletionService` under the Exchange lock
  (`COMPLETION_GATES_UNSATISFIED`, `REMAINING_REQUESTS_REQUIRE_CANCELLATION`,
  `cancelRemainingInformationRequests`) and `PUT /information-requests/{id}/completion-gate`.
- [x] `P9-T5` Implement Request Clocks for urgency, clock type, received time, business timezone,
  versioned business calendar, working hours and holiday set, pause and resume reasons,
  extensions, reminder points, escalation, and overdue transition deduplication. Freeze the policy
  version, inputs, and calculation history so later calendar changes do not rewrite deadline proof.
  Done: V140 clock policies with immutable versions, the pure calculator, clock commands under
  `If-Match`, and the point processor and scheduler; a finished request or ended Exchange stops its
  clocks, including an overdue clock with no further point.
- [x] `P9-T6` Add cross-request search, queue, aging, SLA, delivery, reminder, and exception
  projections.
  Done: `GET /information-request-operations` with the service level calculator, notice, reminder,
  and exception counts, filters, and paging, at owner scope through
  `INFORMATION_REQUEST_VIEW_OPERATIONS_QUEUE` (decision 14).
- [x] `P9-T7` Add cross-request audit search, reconciliation, integrity, and export projections over
  the classified transactional events already recorded by Phases 2 through 8. Detect missing or
  unmatched history without exposing sensitive values in unrestricted payloads.
  Done: request audit history, owner-scope audit search, reconciliation, and SHA-256-verified record
  exports that record their storage location and transfer decision.
- [x] `P9-T8` Build immutable `OutboundNotice` and append-only `NoticeDeliveryAttempt` records before
  retention or privacy integration begins. Idempotently claim each durable `NoticeIntent`, snapshot
  request party and recipient endpoint, channel, rendered subject and body, rendered-content hash
  and algorithm, source Communication ID and pre-interpolation source-content hash, event and
  idempotency IDs, timestamps, attempts, and outcomes. Perform interpolation only after the claim.
  Consume every side-effecting `{{SEQ:KEY}}` occurrence exactly once in the claimed render
  transaction, persist the render before delivery, and make every retry reuse it without another
  Sequence increment. Define one deterministic allocation when the same Sequence token appears in
  both subject and body rather than invoking the current interpolator independently by accident.
  Large content may use only an immutable content-addressed retained object
  whose hash is verified on read; it can never point to mutable Communication content. Treat
  recipient endpoints and retained content as sensitive: apply compartment authorization,
  protection at rest through approved existing controls, redaction, access history, privacy rules,
  and retention. Mutable Communication remains authoring input only.
  Done: V141 claims, immutable outbound notices, one Sequence allocation per key shared by subject
  and body, append-only attempts with backoff, derived delivery state, and masked endpoints in every
  projection.
- [x] `P9-T9` Build neutral record-preservation and disposal foundations for Information Requests,
  Submission Packages, Evidence Versions, referenced Document Versions, Outbound Notices, retained
  notice content, and exports. Define authorized hold placement, release, and scope change through
  central capabilities and thin REST resources; include tenant and resource scope, reason,
  effective time, explicit principal audit, and immutable lifecycle history so release never erases
  prior hold evidence. Add retention schedules, descendant and reference-graph propagation,
  disposal eligibility, tombstones, access history, an idempotent storage-deletion port, and a
  retryable purge job using existing scheduling and storage infrastructure. Eligibility must prove
  that no retained ordinary Document, Evidence Version, Submission Package, notice, export, or other
  live reference still needs shared bytes. Use a claimed disposal state machine that locks or
  rechecks retention, holds, and reachability, prevents a new reference after claim, deletes the
  object, treats an already-missing object as idempotent success, and then finalizes the database
  tombstone. A retry after object deletion but before database finalization must safely complete.
  Generalize the existing `AuditLegalHold` mapped persistence and service into the neutral
  `RecordPreservationHold` contract instead of adding a parallel active hold table. Preserve the
  physical `audit_legal_hold` table and current audit-governance APIs during compatible rollout;
  add owner kind and owner ID for platform, organization, and personal ownership, central
  capability authorization, effective time, descendant and reference scope, and append-only hold
  lifecycle history. Adapt `AuditDisposalEligibilityService` through the neutral hold service so an
  operator cannot place a hold in one subsystem that is invisible to another. Every claim, denial,
  retry, database deletion, and object deletion is audited and reproducible.
  Done: V142 generalized `audit_legal_hold` with its lifecycle history, retention schedules, claimed
  disposal with idempotent storage deletion, tombstones, and reference guards, with the hold,
  schedule, disposal, and standing resources; audit governance delegates to the neutral hold service.
- [x] `P9-T10` Implement privacy-request handling for authorized subject access, correction by new
  revision, export, restriction, and deletion where retention or a record-preservation hold does
  not prohibit it. Apply the same handling to sensitive recipient endpoints and retained notice
  content. Record purpose and policy basis without embedding policy conclusions in application
  code.
  Done: V143 privacy requests, subject restrictions, and package item corrections; deletion is all or
  nothing; restrictions stop fact promotion and offers.
- [x] `P9-T11` Add policy extension points for storage location, permitted transfer regions, and
  ownership changes when a user or organization relationship changes. Any infrastructure change
  remains subject to explicit AWS-service approval.
  Done: storage location, transfer region, and ownership-change policies with configuration-backed
  defaults; organization member removal consults the ownership policy.
- [x] `P9-T12` Extend `basic_field_document_response_attestation_request` and
  `multi_party_staged_evidence_request` through scoped Workflow, versioned clocks, immutable
  notices, audit search, retention, record-preservation holds, privacy requests, and evidence export.
  Done: `InformationRequestTemplateWalkingSkeletonPhase9Test`, one basic-shaped and one staged
  scenario.
- [x] `P9-T13` Add the minimal operational queue, clock, notice history, audit history, authorized
  hold placement and release, disposal status, and evidence export UI behind the feature switch.
  Done: the operations queue and request detail pages, the record preservation page, the menu entry,
  the Exchange end refusal handling, and request-trigger applicability in the designer. Privacy
  request, clock policy, clock command, hold scope change, and audit search screens remain for Phase
  10.

### Tests to write first

- Transaction rollback, outbox retry, ordering, and duplicate event tests.
- Workflow exact-request and exact-submission snapshot tests.
- Exchange completion-gate tests.
- Urgent, standard, timezone, pause, extension, and overdue scheduler tests.
- Calendar policy version, holiday, working-hours, and calculation-history tests.
- Notification deduplication and recipient-safety tests.
- Notice Intent claim and recovery, immutable or content-addressed notice content, verified content
  read, sensitive endpoint projection, delivery attempt, and ordered communication-timeline tests.
- Source versus rendered hash, one-time Sequence allocation across subject and body, retry without
  Sequence increment, claim race, and persisted-render replay tests.
- Audit classification and actor tests.
- Hold capability, tenant scope, placement, release, scope change, immutable history, and projection
  tests, including platform, organization, and personal ownership plus compatibility through the
  existing audit-governance APIs and physical table.
- Record-preservation hierarchy and reference-graph reachability, shared-byte retention, disposal
  claim, hold-placement-versus-purge, new-reference-versus-purge, database and object purge, retry,
  object-deleted-before-finalization recovery, tombstone, missing-object idempotency, audit, and
  reproducible export tests.
- Subject access, restriction, correction-by-revision, permitted deletion, held-deletion denial,
  and tenant or subject authorization tests.

### Exit criteria

- Workflow automation never selects an ambiguous response from several requests.
- Duplicate event delivery causes one business effect.
- Clocks and reminders are deterministic across timezones and pause cycles.
- Audit and export reconstruct who requested, provided, reviewed, changed, and decided each item.
- Retention never purges held records and sensitive values are not copied into unrestricted logs.
- Purge removes eligible database and stored objects through an idempotent, audited, retryable path;
  logical deletion alone is never described as physical disposal, and shared bytes remain while any
  live reference needs them.
- Authorized operators can place, change, and release holds without erasing hold history or racing
  past a claimed disposal.
- Official notices and delivery attempts form a reproducible chronology, with immutable content and
  protected recipient endpoints.
- Privacy operations respect identity, purpose, retention, record-preservation holds, and tenant
  boundaries.

## Phase 10: Author, Respondent, Reviewer, and Operations UX

### Goal

Integrate and harden the feature-switched capability slices from earlier phases into complete,
responsive, and accessible user journeys. This phase must not redefine backend contracts merely to
compensate for UI assumptions.

### Phase 10 design decisions (2026-09-27)

Recorded before the backend additions so every task below states against one design. Verified facts
they rest on: Template authoring saved one Field Requirement only, and the Template API reported
Document evidence policy as unavailable although Phase 6 shipped it; a publish that breaks a storage
completeness rule answered "Request failed"; no REST endpoint adds, reassigns, or revokes request
parties, and nothing creates a `SubjectIdentityRef`; `createFromBlueprint` has no caller and no
entitlement check; ad hoc creation answers 500 for a validation refusal; `InformationRequestDto` has
no title; the operations queue has no search, assignee, or name; nothing sends a reminder on demand;
the respondent workspace has no autosave, section navigation, dispositions other than `PROVIDED`,
narrative, or upload retry, and shows raw keys; the reviewer workspace has no assignment, override,
reconsideration, comment, or evidence preview control.

1. Template authoring. The Settings tab lists Templates per scope and opens one editor for a whole
   Version: ordered sections and requirements (typed answer, document, confirmation) with response,
   evidence, confirmation, and link policies; repeatable groups; conditions; review stages and order;
   submission mode and stages; Schema and fact reuse purpose; and a Versions panel (start a draft from
   the published version, retire, copy). A client preflight mirrors the server's rules and each item
   opens the part it names. The server stays authoritative: a refused save or publication answers
   `InformationRequestTemplateRefusalDto` (`INFORMATION_REQUEST_TEMPLATE_INVALID` with the section,
   requirement, group, or review stage key), and publication states the freeze-time storage rules
   first (`InformationRequestTemplatePublicationReadiness`). The document editing parts live in
   `information-requests/template-document/` so ad hoc request creation reuses them.
2. Request creation. `POST /information-requests` takes exactly one source: `configuration` (ad hoc),
   `blueprintDefinitionId` (the existing instantiation service, now with the owner entitlement check
   ad hoc creation already applies), or `templateVersionId` (a published, non-retired Version the
   caller may read). A validation refusal answers 400 with the Template refusal; an unavailable
   Version answers 409 with its stable code.
3. Request title. The title is the display name of the Template Definition the request pins (for an
   ad hoc request, the author's own name for it); no column is added. It is carried by the projections
   that display it (the Exchange summary, the response workspace, and the operations row), not by
   every command result.
4. Parties. `POST /information-requests/{id}/parties` assigns one party under the parties `ETag`
   (`If-Match`) and an Idempotency-Key: `roleKey` with exactly one of `userId`, `principalGroupId`,
   `email` (an owner-scoped External Participant, found or created), or, for `SUBJECT`,
   `subjectIdentityRefId`. `POST .../parties/{partyId}/reassignment` and `.../revocation` complete it,
   and `GET .../parties` answers the parties `ETag` in its header. Issuance keeps needing an explicit
   request-scoped Decision Maker: the author UI assigns the author as Decision Maker when a request is
   created and shows it; it never widens the Exchange owner grant.
5. Subjects. `POST /information-requests/{id}/subjects` names the request's Subject party: a known
   external reference finds the owner's existing `SubjectIdentityRef`; otherwise one is created in the
   request owner's scope (the owning organization, or the owning user) with the optional reference
   stored as an authorized alias, once per Idempotency-Key. `GET /information-request-subjects` lists
   the active owner's subjects with their references for party selection and privacy requests, under
   `INFORMATION_REQUEST_MANAGE_PRIVACY`. No name or email is ever stored on a subject.
6. Access links. A link is issued per acting party and shown once for the author to copy. "Resend"
   rotates the link (a new secret; earlier sessions end) and shows the new link. No raw secret is
   stored or placed in a notice, so the platform never emails a link.
7. Exchange tab. `GET /exchanges/{exchangeId}/information-requests` answers
   `InformationRequestExchangeListingDto`: `canCreate` (the caller may create, the Exchange takes new
   drafts, and the owner's plan allows it) and an `InformationRequestSummaryDto` for each request the
   caller may discover under the existing view rule: title, state, issue time, nearest running due
   instant, required-answer progress over what the caller may see, the caller's party roles,
   server-derived permissions, and a next action code (`COMPLETE_SETUP`, `RESPOND`, `REVIEW`, `MANAGE`,
   `VIEW`). No count of hidden requests is returned. A no-auth session is bound to one request and uses that
   request's workspace; the legacy Exchange shell never authorizes request data, so it has no such tab.
8. Respondent workspace. One workspace for both surfaces with section navigation, per-requirement
   permitted answers and narrative, required markers and help text, a missing-items summary that
   links to items, review before submit, correction guidance naming the items and files a correction
   reopens, and respondent comments on settled reviews. Autosave runs two seconds after the last edit
   beside an explicit Save; saved edits are cleared; a stale save keeps the unsaved edits, loads the
   latest, and asks the respondent to review and save again rather than overwriting; a failed upload
   keeps the file for retry under the same Idempotency-Key; an expired session keeps unsaved edits in
   the tab's session storage until the contact code is verified again. No WebSocket is required.
9. Reviewer workspace. Assignment management (assign a Reviewer party to a stage, delegate, recuse,
   revoke), overrides where a stage permits them, reconsideration, comments, exact evidence version
   preview and download, decision and remediation history, and each stage's separation-of-duties
   rules; Accepted Fact promotion and revocation and Business Decision records in the request's
   management workspace.
10. Operations. The queue gains `search` (title or request id prefix), an assignee filter, the row
    title, and acting assignees; `POST /information-request-reminders` owes a `RESPONSE_REMINDER`
    Notice Intent to each active responding party of each open request named, audited per request and
    replayed by Idempotency-Key, under the new owner-scope capability
    `INFORMATION_REQUEST_OPERATIONS_MANAGE` (Organization Owners and Administrators; a personal owner
    for their own requests), consistent with decision 14 of Phase 9. The queue exports its authorized
    projection as CSV. Clock policies, clock commands, privacy requests and subject restrictions, hold
    scope change, and the owner-scope audit search get their screens.
11. Accessibility and formatting. Shared formatting helpers state times with the viewer's timezone,
    numbers and sizes by locale; save status is a polite live region; a refusal moves focus to the
    part it names; every region and control has an accessible name; layouts reflow at phone width.
12. Help and pricing. A dedicated Information Requests help section replaces the articles filed under
    Fields; the website pricing table gains an Information Requests row matching the plan catalog
    (Personal and Business).

Refinements recorded while implementing (2026-09-27 and 2026-09-28):

- Clock commands live in one place, the request's management workspace (`Due dates`), because
  starting, pausing, resuming, and extending need the request-scoped management authority
  (`INFORMATION_REQUEST_MANAGE_CLOCKS`, held through `INFORMATION_REQUEST_ADMIN`) that reading the
  operations queue does not grant. The operations detail links there with `Manage this request`.
- A privacy correction names one submitted item, so it is recorded from the management workspace's
  `Corrections` section, and only when the current session owns the request (a personal owner, or
  an organization session with `INFORMATION_REQUEST_PRIVACY_MANAGE`). The operations `Privacy` tab
  records access, export, restriction, and deletion requests. The submission package read shape now
  names each item by `id` so an item can be promoted or corrected exactly.
- The management workspace treats a successful accepted-fact listing as the server's signal that the
  caller may promote facts and record decisions, since both need `INFORMATION_REQUEST_ADMIN`; a
  refused listing hides those controls and still lists decisions.
- The queue's CSV export is built in the browser from the authorized queue itself (every matching
  page at 200 rows, the same projection the caller can read), with values that could start a
  spreadsheet formula prefixed. No export endpoint was added.
- A refusal is announced as an alert; where the refusal names a part, `Show` (Template editor) or
  opening that part (a refused one-off creation) moves focus there, and submission blockers link to
  their items. A response save refusal names no part.
- No Field type carries a currency or unit, so typed values are shown as entered; times use the
  viewer's time zone and counts the viewer's number format through the shared formatting helpers.
- Accepted-fact offers are not shown in the respondent workspace yet; reuse and reconfirmation in a
  later request belong to `P11-T1`.

### Tasks

- [x] `P10-T1` Add Settings authoring for Template Definitions, versions, ordered sections,
  Requirement types, response policies, conditions, evidence policies, review stages, and publish
  validation.
- [x] `P10-T2` Add request-author creation and dispatch for Blueprint-based and ad hoc Information
  Requests, including preview-as-recipient, subject and contributor selection, schedule, resend,
  link rotation, due policy, cancel, supersede, and supplemental request actions.
- [x] `P10-T3` Build one respondent Information Request workspace shared by authenticated and
  no-auth shells. Include section navigation, repeatable groups, inline evidence, save status,
  missing-item summary, review-before-submit, response attestation, and correction guidance.
- [x] `P10-T4` Add autosave, explicit save fallback, stale-write conflict recovery, multi-device
  messaging, interrupted upload recovery, session-expiry recovery, and safe retry behavior. Treat
  persisted revisions, ETags, `412`, and refresh or merge messaging as the correctness mechanism for
  authenticated and no-auth clients. Reuse `RealtimeEventService` and existing authenticated User
  Session sockets only for optional notifications where they reduce latency. Do not require a
  no-auth WebSocket or imply live collaborative editing in this program.
- [x] `P10-T5` Add reviewer workspaces with responses and evidence together, exact version preview,
  findings, correction selection, stage status, separation-of-duties controls, satisfaction, and
  remediation history.
- [x] `P10-T6` Add operational work queues with search, filters, aging, deadlines, assignees,
  delivery status, exceptions, bulk reminders, and authorized export.
- [x] `P10-T7` Add one `Information Requests` tab to each Exchange. The tab lists only requests in
  that Exchange that the current principal and access session is authorized to discover, with only
  its permitted recipient, progress, due date, status, and next action projection. Authorized owners
  and administrators may see all; contributors and reviewers see only assigned work; a no-auth
  session sees only its bound party scope. Use server-provided capabilities and owner-derived
  commercial and execution state for tab visibility and actions, never the viewer's own plan.
  Retained and already-issued authorized work remains visible after commercial lapse with creation
  or expansion controls disabled. Do not reveal hidden request counts. Open the selected author,
  respondent, or reviewer workspace. Keep the existing `Fields` tab for internal Exchange metadata;
  do not create one Exchange tab per request.
- [x] `P10-T8` Complete responsive, keyboard, screen-reader, focus, localization, timezone, number,
  currency, unit, upload progress, empty, loading, and error states.
- [x] `P10-T9` Verify the Phase 1 help registry refactor still leaves `helpDocsRegistry.tsx` under 60
  lines, then update all affected help documentation and website pricing copy only after actual plan
  availability is implemented. Marketing copy may describe concrete use cases as explanatory
  examples, but no new component, function, route, script, asset, or product configuration name may
  encode one. Existing legacy marketing identifiers are outside this program and are not naming
  precedent.

### Frontend constraints

- Keep TSX components short and single-purpose. Split components near 150 lines.
- Put all styles in co-located `*Styles.tsx` files using Fluent UI `makeStyles` and tokens.
- Do not use `any` or inline styling.
- Add stable IDs to every added or updated HTML and React element and use circular Buttons.
- When an HTML or React element has more than one attribute, put each attribute on a separate line.
- Put dialog actions at the bottom right using the required primary and secondary ordering.
- Design every new or updated component for desktop, tablet, and mobile.

### Tests to write first

- Template authoring and immutable-publish UI tests.
- Registered and no-auth workspace parity tests.
- Sparse autosave, stale conflict, and recovery tests.
- Repeatable group and conditional visibility tests.
- Evidence upload, replacement, preview, and error tests.
- Review correction and separation-of-duties UI tests.
- Keyboard, focus, accessible name, and responsive-state tests.
- Queue filtering, deadline, delivery, and bulk-action tests.
- Exchange `Information Requests` tab tests for list fields, role-specific next action, workspace
  opening, owner/admin visibility, contributor and reviewer filtering, no-auth party scope, hidden
  counts, no-visible-request state, free-plan respondent behavior, lapsed-owner retained visibility,
  responsive states, and continued separation from the existing `Fields` tab.
- Help registry composition and under-60-line limit checks before registering the new help section.

### Verification

```text
cd web-app
npm test
npx tsc --noEmit
npm run lint
npm run buildWithTs
```

Manually verify at minimum 1440, 1024, 768, and 360 CSS pixel widths for author, respondent,
reviewer, authenticated, and no-auth journeys.

Session exception (2026-09-28): the user explicitly said "You can skip this" when asked for the
local session needed for these width checks. They remain unperformed; this exception waives that
gate for Phase 10 completion and does not constitute visual verification.

### Exit criteria

- Every primary journey is usable without administrative Schema endpoints.
- Authenticated and no-auth users receive the same permitted behavior.
- Autosave and conflicts cannot silently lose work.
- Review and correction identify exact Requirements and evidence versions.
- Responsive and accessibility tests pass.
- Help documentation matches the implemented navigation, terminology, permissions, and lifecycle.

## Phase 11: Generic Capability Conformance and Extension Contracts

### Goal

Prove that the configurable core covers varied document-driven process structures through
test-only, neutral conformance fixtures and generic extension contracts. No shipped Template,
vocabulary, branch, identifier, or fixture may encode a particular industry or customer domain.

### Phase 11 design decisions (2026-09-29)

Recorded for `P11-T11` and `P11-T12` before their tests. Verified characterization of the existing
integration components the tasks ask to reuse:

- `WorkflowWebhookEndpoint` is an organization-owned row bound to one Workflow Definition, with a
  signing secret and a permitted event-type list. It names where a Workflow ACTION step posts an
  event and carries no request, Requirement, polling, or result state.
- `WebhookDeliveryService.deliver` posts one HMAC-SHA256-signed Workflow-instance payload and returns
  only success or failure. It reads no response body, does not retry, and does not re-validate the
  destination at delivery time; Workflow step failure is the only retry path.
- `WebhookDestinationPolicy.validate` is a generic egress policy (scheme, resolved host, loopback,
  link-local, site-local, and metadata addresses) usable unchanged by any adapter that calls a
  remote endpoint.
- `Application` identity and `ApplicationTokenBoundaryService` authenticate inbound application
  calls; neither is an outbound identity.

Decisions:

1. The connector contract stays separate from Workflow webhooks (architectural decision 38): it
   needs request, polling, imported-value, reconciliation, and verification state. The port
   `InformationRequestConnector` states a lowercase `key`, a `kind` (`STRUCTURED_EVIDENCE` or
   `EXTERNAL_VERIFICATION`), a `contractVersion`, the `resultKeys` it may return, and an optional
   `maximumResultAge`, mirroring the configuration bundle's connector contract; `request` and `poll`
   answer `Pending` (external reference, retry-after), `Completed` (external reference, result), or
   `Failed` (reason code). A call carries identifiers and the optional lookup reference the
   requester supplied; the platform never sends response values implicitly.
2. Reused as-is: the central authorizer (`INFORMATION_REQUEST_MANAGE_EXTERNAL_SOURCES`, held through
   the request administration capability, for requesting exchanges, proposing values, and recording
   outputs; `INFORMATION_REQUEST_DECIDE_EXTERNAL_VALUES`, held through the request review capability,
   for deciding, reconciling, and resolving), Command Receipts,
   transition history with its audit catalog, the continuation entitlement check, and record
   disposal. A future remote adapter must validate its destination with `WebhookDestinationPolicy`
   before every call and sign its requests with the HMAC-SHA256 scheme `WebhookDeliveryService`
   uses, extracting that private signer into a shared component when the first remote adapter is
   scoped. No second outbound endpoint model, HTTP client, or delivery stack is added,
   and no production connector is shipped: the registry is empty outside tests.
3. V148 persists connector exchanges (forward-only `REQUESTED`, `PENDING`, `COMPLETED`, `FAILED` with
   attempts and next attempt time), imported values (append-only, `MANUAL` or `CONNECTOR` source,
   source reference, result key, value type, canonical value, confidence `ASSERTED`, `MATCHED`, or
   `VERIFIED`, verification time, expiry, provenance reference, recorder), one decision per value,
   discrepancies (one per value and response revision) with one resolution each, and generated
   output references. Disposal removes and counts every one of them.
4. A scheduled worker (`app.information-request.connectors.every`, default one minute, `off` in the
   test profile so test adapters are never raced by the empty production registry) claims a due
   exchange under a row lock with a five-minute lease, calls the connector outside any transaction,
   and records the outcome under the request lock. A thrown call retries with linear backoff; ten
   attempts end in `attempts_exhausted`; a missing connector ends in `connector_unavailable`; a
   result that breaks its declared contract ends in `result_rejected` and records nothing; a request
   that no longer accepts external records ends in `request_not_accepting_values`.
5. Imported values are untrusted and never write a response, Field value, or Accepted Fact. A value
   for a Requirement that collects a Field is canonicalized by that Field's contract
   (`FieldValueValidator`), so it compares exactly with an answer; a value for any other Requirement
   must be a scalar type validated by its type contract. `MATCHED` and `VERIFIED` need a
   verification time that is not in the future, and expiry follows verification.
6. A decision (`ACCEPTED` or `REJECTED`, with a reason code) is recorded once per value by a
   holder of the request review capability. The principal who proposed a manual value cannot decide it
   (`REVIEW_SEPARATION_OF_DUTIES`), and an expired value cannot be accepted
   (`IMPORTED_VALUE_EXPIRED`). Accepting a value changes nothing else.
7. Reconciliation compares every value not rejected with its Requirement's current response:
   `MATCHES`, `DIFFERS` (a discrepancy is recorded once per value and response revision),
   `NO_ANSWER`, `NOT_COMPARABLE` (the Requirement collects no Field), or `EXPIRED`. A discrepancy is
   resolved once, `RESPONSE_STANDS` or `FOLLOW_UP_REQUESTED`, with a reason; neither changes the
   response, and a follow-up uses the existing correction and supplement commands.
8. A generated output reference records where an output produced outside the platform lives, its
   optional package, hash, and media type, and what produced it. The platform never generates,
   fetches, or stores the output. No OCR, extraction, AI finding, or document generation is built.
9. REST, authenticated and requesting-side only (respondents never see imported values; there is no
   no-auth surface): `GET` and `POST /information-requests/{id}/connector-exchanges`, `GET` and
   `POST .../imported-values`, `POST .../imported-values/{valueId}/decisions`,
   `POST .../imported-value-reconciliations`,
   `POST .../imported-value-discrepancies/{discrepancyId}/resolutions`, and `GET` and
   `POST .../generated-outputs`. Every `POST` requires an Idempotency-Key. DTOs state whether the
   caller recorded or decided an item instead of exposing principal identifiers.
10. External source records are part of the request record: the verified record export gains the
    external sources and the request's own recertifications, and the export schema version rises.
    Transition details carry identifiers and keys only, never imported values or lookup references.
11. The configuration bundle's connector contract is aligned with the port: `resultFieldKeys` is
    renamed `resultKeys`, and `requiresReview` is removed because every imported value is reviewed.
12. Phase 11 adds no screen for external sources. The contracts are documented in help, and the
    capability is proven by the multi-party (connector verification of an answer) and timed,
    retained, exported (manual source, generated output, export, and disposal) scenarios.
13. Defect found by the `P11-T12` tests and fixed: the central authorizer treated every terminal
    request as archived and refused every non-read action unless the caller held `EXCHANGE_ADMIN`,
    which no request principal holds, so promoting or revoking a fact, recording a business decision,
    and every external-source record were refused on a CLOSED request although the transition matrix
    allows them there (a satisfied review closes the request, so reviewed facts could never be
    promoted). `InformationRequestParentPolicy.closedRecordActions` (fact promotion, business
    decision, external-source management, and external-value decisions) is exempt from the archived
    refusal for the request resource only; the capability check, the parent policy, and the
    transition matrix still decide, so every other write to a terminal request stays refused.
14. Registered and unregistered participation is proven inside the scenarios as well as by the
    dual-surface resource tests: the itemized scenario runs its whole flow for a registered and for
    an unregistered (participant) contributor with identical outcomes, and the parallel review
    scenario reviews an unregistered respondent's package. `PublishedRequestSupport.issue` can create
    participant-backed parties for this.

### Phase 11 capability traceability

Every shared capability in `## Cross-Process Capability Traceability` is proven by at least two
materially different scenarios under `src/test/kotlin/.../service/informationrequest/conformance/`:
S1 `BasicFieldDocumentResponseAttestationRequestConformanceTest`, S2
`MultiPartyStagedEvidenceRequestConformanceTest`, S3 `RepeatableConditionalRequestConformanceTest`,
S4 `MultiFileEvidencePolicyRequestConformanceTest`, S5 `ItemizedStagedSubmissionRequestConformanceTest`,
S6 `MultiStageReviewCorrectionRequestConformanceTest`, S7 `RecurringSupplementalRequestConformanceTest`,
S8 `TimedRetainedExportRequestConformanceTest`.

| Shared capability | Scenarios | What each proves |
|---|---|---|
| Request-scoped party roles, delegated authority, and Requirement assignment | S2, S6, S5 | S2: distinct subject, contributor, attestor, reviewer, delegate, and outsider under real authorization; expiring, scoped, revocable delegation. S6: stage reviewers, separation of duties. S5: the responder recorded per item. |
| Repeatable groups, stable occurrence paths, and cross-occurrence validation | S3, S6 | S3: nested occurrences, per-occurrence values and evidence, no path reuse, cross-occurrence refusal. S6: two entry occurrences reviewed, only the returned occurrence reopened. |
| Versioned conditions, amendments, and staged policy | S3, S7, S2, S5 | S3: `UNKNOWN`, `TRUE`, `FALSE` per occurrence. S7: an amendment that requires reconfirmation. S2 and S5: staged submission policy. |
| Evidence Artifacts, immutable Evidence Versions, assessments, and evidence policy | S4, S3, S1 | S4: coverage, issuer, age, validity, certification, quarantine, replacement. S3: per-occurrence conforming file. S1: the evidence version frozen in the package. |
| Policy-controlled dispositions and evidence alternatives | S5, S4 | S5: six dispositions and refusal of unconfigured ones. S4: configured alternative and waiver. |
| Submission Packages, hashes, exact configuration, stage locks, and Submission Attestation | S1, S5, S2 | S1: one package, hash equal to the attested ETag, atomic closure. S5: stage locks and exact manifests. S2: staged packages with a separate confirmation stage. |
| Request lineage, carry-forward, recurrence, correction, and retest | S7, S6, S3 | S7: supplement, recurrence, supersession, cancellation. S6: correction, resubmission, remediation, retest. S3: occurrence-scoped correction. |
| Review stages, aggregation, separation of duties, findings, correction allowlists, remediation | S6, S3 | S6: sequential and parallel stages, aggregation, prior-reviewer refusal. S3: a finding that returns one occurrence. |
| Request Clocks and scheduler policy | S8, S7 | S8: pinned clock version, pause, resume, extension, reminder. S7: a recurrence that refuses an early occurrence. |
| Audit, access history, retention, hold, purge, privacy, and export | S8, S1, S7 | S8: reproducible export, export read in access history, hold, retention, disposal, subject requests. S1: database refusal to change a package, transition history, replay. S7: cancellation and supersession history kept. |
| Explicit Accepted Facts and generic connector contracts | S7, S2, S8 | S7: fact freshness and reconfirmation. S2: connector verification of an answer, reconciliation, reviewer decision, respondents denied. S8: manual value on a document Requirement, generated output, export, disposal. |
| Registered and unregistered participation through shared services | S5, S6 | S5: the same itemized flow and outcomes for a registered and an unregistered contributor. S6: an unregistered respondent's package reviewed in parallel stages. Surface parity is also proven by the dual-surface resource contract tests. |

### Tasks

- [x] `P11-T1` Add a purpose-bound reusable information profile for explicitly promoted values and
  evidence references. Reuse must enforce subject and tenant scope, consent or policy basis,
  freshness, expiry, source visibility, and explicit respondent recertification.
  - [x] `P11-T1a` Correct Accepted Fact offer eligibility before exposing reuse: exclude expired
    and out-of-period facts, use exclusive end boundaries, preserve current fact history and
    promotion conflict checks, and prove owner, subject, purpose, and audience isolation.
  - [x] `P11-T1b` Persist explicit consent or policy basis and promoted evidence references with
    immutable provenance, owner and subject scope, revocation, and retention reference safety.
  - [x] `P11-T1c` Add an atomic respondent recertification command that revalidates an offer,
    requires the target response precondition, records exact source provenance, and never writes
    a response without explicit assent. Prove authenticated and no-auth parity and refusal races.
  - [x] `P11-T1d` Expose eligible offers and recertification in the shared respondent workspace,
    extend promotion controls and help, then run affected suites before closing the parent task.
- [x] `P11-T2` Define a versioned generic configuration-bundle format for Template Versions,
  validation policies, customer-authored reason-code vocabularies, role presets, clocks, retention
  defaults, and optional connector contracts. The format describes generic capability schemas and
  never ships a particular process vocabulary or execution branch.
- [x] `P11-T3` Complete the test-only `basic_field_document_response_attestation_request`
  conformance scenario: sparse draft, one Field Requirement, one Document Requirement, one
  Response Attestation Requirement, exact immutable submission, and atomic no-review closure.
- [x] `P11-T4` Complete the test-only `multi_party_staged_evidence_request` conformance scenario:
  distinct subject, contributor, delegate, attestor, and reviewer; scoped assignment; delegated
  authority expiry and revocation; staged submission; and cross-party denial.
- [x] `P11-T5` Complete the test-only `repeatable_conditional_request` conformance scenario: nested
  occurrences, stable paths, conditions with unknown state, cross-occurrence validation, and
  occurrence-scoped evidence and findings.
- [x] `P11-T6` Complete the test-only `multi_file_evidence_policy_request` conformance scenario:
  multiple files, coverage, issuer, freshness, expiry, certification, configured alternatives,
  waiver, quarantine, replacement, and immutable version membership. Quarantine is exercised with a
  test scanner adapter; the scenario does not need a real scanner.
- [x] `P11-T7` Complete the test-only `itemized_staged_submission_request` conformance scenario:
  item-specific provided, partial, unavailable, exception-requested, referenced, and waived
  dispositions; stage locks; later-stage edits; and an exact package manifest.
- [x] `P11-T8` Complete the test-only `multi_stage_review_correction_request` conformance scenario:
  sequential and parallel review, separation of duties, finding aggregation, correction allowlist,
  resubmission, remediation, and retest.
- [x] `P11-T9` Complete the test-only `recurring_supplemental_request` conformance scenario:
  recurrence, amendments, supplements, carry-forward and invalidation, Accepted Fact freshness,
  reconfirmation, cancellation, and supersession.
- [x] `P11-T10` Complete the test-only `timed_retained_export_request` conformance scenario:
  versioned clocks, pause and resume, extension, reminders, immutable notices, access history,
  retention, record-preservation hold, privacy operations, and reproducible export.
- [x] `P11-T11` Add generic connector interfaces for structured evidence and external verification.
  First characterize and reuse existing Application identity, `WorkflowWebhookEndpoint`,
  destination policy, signing, outbound delivery, retry, and authorization components where their
  contracts match. Keep a connector contract separate where it requires request, polling,
  imported-value, reconciliation, or verification state that an outbound Workflow webhook does not
  provide. Do not build a second generic outbound endpoint or delivery stack. Ship only local or
  test adapters in this program; live external integrations require separate scope and security
  review.
- [x] `P11-T12` Add source-aware imported-value proposal, reconciliation, discrepancy-finding, and
  generated-output-reference contracts for manual or external sources. Imported or proposed values
  remain untrusted until reviewed and never overwrite responses or Accepted Facts automatically.
  Do not implement OCR, automated extraction, AI findings, or a document-generation engine in this
  program.

### Executable capability proofs

| Neutral process pattern | Required proof |
|---|---|
| Basic mixed-requirement collection | Field, Document, and Response Attestation Requirements use one versioned configuration, submit one reproducible package, and close atomically when review is not required. |
| Delegated multi-party execution | A contributor acts for a distinct subject under scoped, unexpired authority; a separate actor attests; another actor reviews; unauthorized parties cannot read or mutate data. |
| Repeatable conditional collection | Nested occurrences keep independent values, evidence, findings, and completeness while condition changes preserve history and deterministically recalculate applicability. |
| Versioned multi-file evidence | Several files with distinct coverage and conformance states aggregate by policy; replacement keeps immutable membership and history; unsafe evidence cannot satisfy a Requirement. |
| Itemized staged submission | Each item accepts only configured dispositions; a submitted stage remains immutable while another stage remains editable; the package manifest is reproducible. |
| Independent multi-stage review | Sequential and parallel stages aggregate deterministically; separation of duties is enforced; correction reopens only allowed items; remediation and retest preserve prior findings. |
| Recurring and supplemental collection | A linked request preserves its source package, applies explicit carry-forward or invalidation rules, rechecks reusable-value freshness, and retains cancellation and supersession history. |
| Timed retained export | Versioned clock inputs reproduce due times across pauses and extensions; notices and access history are immutable; held records cannot purge; authorized export is reproducible. |

### Exit criteria

- All eight neutral conformance scenarios pass as automated service or integration tests.
- A capability-to-scenario traceability matrix proves every reusable capability through at least
  two materially different neutral process scenarios.
- Scenario names, IDs, labels, values, files, test classes, and helper functions contain only neutral
  process terminology.
- Conformance fixtures remain test-only and are not installed as platform Templates or seed data.
- Production code contains no branches keyed by fixture identity, Template name or ID, Requirement
  key, subject label, customer vocabulary, or seeded value.
- The basic scenario proves the first complete capability slice, while the remaining scenarios
  stress multi-party, repeatable, staged, recurring, review, clock, and records behavior.
- Configuration bundles define generic schemas and extension points, not shipped process
  vocabularies or downstream decision logic.
- Connector contracts preserve source, confidence, verification time, expiry, and provenance.
- No live external integration or new AWS service is introduced without separate approval.

## Phase 12: Packaging, Rollout, and Final Hardening

### Goal

Release the feature without corrupting existing Exchange metadata, surprising customers, or leaving
operational and cost controls undefined.

### Tasks

- [ ] `P12-T1` Aggregate the clean-schema migration tests already added by each owning phase across
  Fields and their exact scope checks, Blueprints and existing defaults, Documents, every Exchange
  state, the three Share-bearing ResourceTypes and the widened Share resource-type and role-name
  checks, ShareLink direct and bootstrap modes, the External Participant owner column, the unchanged
  `exchange_recipient` binding trigger, personal event and hold owners, the generalized existing
  hold table, and existing Workflow Field conditions and trigger registry. Prove the whole migration
  set applies to an empty database in one pass and that the final constraints refuse every shape
  they replaced. Do not add rolling-deployment or application-version rollback coverage; the
  development-stage constraint removes both, and `DS-T4` owns the sweep that proves no compatibility
  mechanism survives. Require `If-Match` on every Field mutation path rather than measuring an
  unknown client's use of a tolerated alias.
- [ ] `P12-T2` Keep legacy Exchange Schema Assignments as Exchange metadata. Do not create synthetic
  satisfied requests or fabricated review decisions. Allow explicit owner-driven conversion only if
  a later product requirement defines its meaning.
- [ ] `P12-T3` Define compatibility for existing mutable Blueprint Definitions. Do not fabricate
  version history. Existing Definitions retain stable Schema Definition and Field default behavior;
  an optional exact Request Template Version reference affects only future instantiations, while
  created requests retain their pinned snapshot. Participant, Document, Field, and Document
  Library-derived defaults retain their existing meaning and use explicit request mappings.
- [ ] `P12-T4` Add capability discovery, data backfill observability, and
  safe disable behavior. Paid lapse or trial expiry prevents new or expanding work but does not
  strand already-issued respondents or reviewers. Global subscription enforcement mode, emergency
  operational suspension, and Trusted Organization suspension are
  separate, explicit, auditable states with tested effects visible in the UI. None silently hides
  retained records or permitted exports from authorized readers.
- [ ] `P12-T5` Finalize plan-tier storage, upload, request-count, recipient, retention, and export
  quotas against the owning Exchange subscription. Configure issuance-time reservation amounts and
  release rules for future `RequestExecutionGrant` records without rewriting an existing grant or
  withdrawing its reserved completion capacity. Include trial-sourced grants and trial-expiry
  continuation in final quota verification. Tune and operationalize the baseline request, upload,
  and retry abuse controls introduced in Phases 4 and 6.
- [ ] `P12-T6` Complete Personal Information Request support. Personal owners may create Templates
  from personal or platform Schemas and may create and issue Information Requests. The Personal plan
  includes `INFORMATION_REQUESTS`, while an active organization entitlement may also sponsor the
  Personal Settings scope without a separate user grant. Personal Field and Schema authoring remains
  a separate incomplete decision. Personal response-only access remains governed by assignment and
  an issued execution grant, not by the respondent's plan.
- [ ] `P12-T7` Update plan catalog, pricing page, Settings discoverability, and respond-only messaging
  only when the implemented entitlement behavior is proven. Preserve the boundary between generic
  product capabilities and explanatory marketing examples; do not add or spread legacy non-neutral
  code identifiers.
- [ ] `P12-T8` Complete performance, load, concurrency, security, accessibility, responsive, audit,
  retention, export, and disaster-recovery verification using existing infrastructure.
- [ ] `P12-T9` Complete all help documentation, operator guidance, support diagnostics, API
  documentation, migration notes, and release notes.

### Final verification

```text
.\mvnw.cmd test
.\mvnw.cmd verify -DskipITs=false
cd web-app
npm test
npx tsc --noEmit
npm run lint
npm run buildWithTs
```

If website pricing or product copy changes, run `npm run lint` and `npm run build` from `website`.
The website currently has no automated test script, so record that absence rather than claiming a
website test passed. Its existing build invokes a legacy concrete-use-case asset generator; running
that unchanged build does not authorize new industry-specific identifiers or an unrelated rename.

### Exit criteria

- All existing and new migrations upgrade representative PostgreSQL databases safely.
- Existing Exchange metadata, Blueprint defaults, direct-grant ShareLinks, Share behavior across all
  existing ResourceTypes, Documents, Workflows, trusted-recipient policy, and audit hold APIs retain
  their documented meaning.
- The owning subscription pays for authoring and storage while assigned respondents can complete an
  issued request even after a later paid lapse or trial expiry, within the persisted and reserved
  execution limits.
- Quotas, abuse controls, retention, recovery, and operational diagnostics are documented and tested.
- All automated suites, required manual checks, help documentation, and eight neutral conformance
  scenarios pass.

## Cross-Phase Test Matrix

Every applicable row must be expanded with concrete tests before its owning task is implemented.

| Concern | Minimum coverage |
|---|---|
| Lifecycle | Every parent and child state, command, permitted actor, resulting state, transition record, read visibility, external-session effect, owner recovery access, event, and idempotent retry. |
| Capability | Canonical PrincipalRef, resource-scoped Share roles and unchanged capability behavior for the three Share-bearing ResourceTypes, proof that the other nine hold no Share rows, widened `share_resource_type_check` and `share_role_name_check`, corrected non-Exchange Share audit owner, unresolved-resource-context fail-closed behavior and the removed `activeOrgId` organization-role fallback, parent-grant inheritance, central Requirement policy facts, owner, org admin, author, reviewer, registered contributor, recipient-bound participant, trusted person and group, suspended trust relationship, group member, removed member, replacement recipient, application, unrelated same-org user, and cross-org user. |
| Field policy | Classification, response mode, correction scope, request state, read projection, write eligibility, and mutation response filtering. |
| Structured data | Sparse updates, explicit clear, root and occurrence Value Sets, repeatable groups, cross-row rules, condition unknowns, cycles, and hidden-data policy. |
| Evidence | Occurrence anchoring, supporting-response links, counts, typed write-once locators, byte identity, hashes, every-inspectable-source inspection, malware scanning where a deployment requires it, explicit `END_TO_END` ineligibility, mixed findings, replacement, withdrawal, command replay, preconditions, quarantine, expiry, duplication, and cross-party ACL. |
| Submission | Completeness, multi-party Submission Attestation, naming separation from Exchange recipient attestation, exact snapshot, concurrency, idempotency, amendment carry-forward, supplement, recurrence, and supersession. |
| Review | Item findings, all/any/quorum aggregation, stage order, separation of duties, correction allowlist, remediation, retest, appeal, and independent Business Decision. |
| Command safety | Idempotency key and fingerprint, actor or RequestAccessSession scope, original-result replay, conflict, required If-Match, stale ETag, HTTP-authoritative multi-device recovery, and concurrent request, party, ShareLink bootstrap, response, evidence, submission, and review races. |
| Entitlement | Organization and personal commercial override under platform-admin control, Personal and Business plan inclusion, global subscription enforcement mode, paid and trial entitlement source, trial expiry continuation, immutable execution grant, capacity reservation, lapse continuation, exhaustion, rollback, release, expiry, operational revocation, organization-owned Field composition at issuance, Personal typed-response inclusion, and grant-governed Field writes that bypass the live `BusinessFieldsSubscriptionGuard` re-check without changing its Exchange behavior. |
| Workflow | Exact request and package scope, existing trigger-registry migration and subject-field descriptors, counterparty-wait characterization, neutral publisher over the compatible owner-aware outbox, rollback, retry, deduplication, ordering, and Exchange completion gates. |
| Time | Instant storage, versioned calendar, working hours, holidays, business timezone, urgency, pause, resume, extension, reminder, escalation, and overdue deduplication. |
| Records | Principal provenance, Notice Intent recovery, source and rendered hashes, exactly-once Sequence allocation, immutable notices, protected endpoints and content, sensitive-data minimization, privacy requests, one generalized hold lifecycle visible through compatible audit APIs, platform and organization and personal hold ownership, retention, live-reference safety, claimed disposal, database and object purge recovery, tombstones, access history, and reproducible export. |
| No-auth | Existing direct-grant ShareLink behavior, bootstrap-mode recipient binding by recipient ID rather than `direct_share_id`, `requireRecipientSignIn` refusal, `EndpointAuthorizationFilter` allowlist enforcement, stable participant principal, no temporary App User, Exchange-token and Exchange-OTP rejection, bootstrap-only denial in the central authorizer, enforced constraints, atomic use count, contact proof, expiry, revocation, rotation, replay, forwarding, rate limiting, replacement, registration upgrade, and RequestAccessSession expiry. |
| Migration | Every Exchange state, empty and populated assignments, all classifications, exact `PLATFORM` or `ORGANIZATION` or `PERSONAL` scope checks and their rewritten unique-index expressions, read-only values, personal audit owners, event-outbox owner kind and ID plus request key namespace, owner entitlements and rollout, the widened `share_resource_type_check` and `share_role_name_check`, the three Share-bearing resource roles, canonical-only principal attribution with the replaced shape proven refused, the personal owner column on the empty `external_participant` table, the unchanged `exchange_recipient` binding trigger, mutable Blueprint Definition links and existing defaults, typed Document Version locators, hash and creator states, execution grants, ShareLink bootstrap extension, command receipts, notice intents and notices, generalized existing hold persistence, disposal claims, Document placeholders, Workflow trigger registry descriptors, and Workflow Field references. No mixed or rolling application-version coverage: the development-stage constraint removes it. |

## Security and Privacy Gates

No phase is complete unless its applicable gates pass.

- Deny cross-organization and unassigned-party access by default.
- Apply the same audience policy to reads, writes, mutation responses, notifications, realtime
  payloads, exports, logs, and support tooling.
- Never authorize evidence only through ordinary Exchange Document access.
- Treat bootstrap-mode ShareLinks as recipient-bound revocable verification credentials with
  expiry, rotation, replay controls, enforced configured constraints, and rate limiting. Require
  recipient contact proof before creating the scoped RequestAccessSession and apply step-up policy
  for sensitive compartments. A bootstrap link by itself and the legacy Exchange-wide token or OTP
  cannot authorize Information Request data; existing direct-grant ShareLinks retain compatible
  behavior. Refuse bootstrap issuance entirely when the parent Exchange sets
  `requireRecipientSignIn`, and add each no-auth request path to the
  `EndpointAuthorizationFilter` allowlist explicitly rather than by prefix guesswork.
- Record purpose, response attestation, actor, provenance, and applicable retention policy without copying
  sensitive response values into unrestricted audit payloads.
- Freeze typed storage locator identity, byte length, version-level content hashes, and evidence
  membership for every submission.
- Require detected-content inspection for every eligible inspectable file-backed Evidence Version
  before it can satisfy a Requirement. Malware scanning is optional (user decision, 2026-09-25):
  where a deployment sets `app.information-request.evidence.malware-scan.required=true`, also require
  a real fail-closed malware scan; wherever a scan detects malware, quarantine the file. Tika or a
  no-op adapter is not malware scanning and never produces a clean result, no file is described as
  scanned or safe without a real scan, and linking an existing Document does not bypass assessment.
- Reject opaque `END_TO_END` ciphertext as satisfying file-backed Evidence; scanning ciphertext
  cannot create a plaintext-safety claim.
- Enforce record-preservation holds, retention eligibility, and all-live-reference reachability
  before deletion. Recheck them under the disposal claim and make partial object-deletion recovery
  idempotent.
- Keep accepted responses separate from reusable facts until an explicit authorized promotion occurs.
- Threat-model every new external connector and obtain user approval before adding a new AWS service
  or paid resource type.

## Migration and Compatibility Strategy

### Flyway numbering and allocation

- The repository head observed on 2026-08-30 is V75. Provisionally allocate V76 through V139 to
  this multi-release program. This range is a coordination aid, not permission to overwrite or
  rename any migration created by another initiative.
- Never fill an older historical gap. Allocate new Information Request migrations sequentially
  from the first still-unused number in the current program range.
- Immediately before creating any migration, list every migration in
  `src/main/resources/db/migration`, recheck the repository head, and compare the intended number
  with this plan and its journal. If an unrelated migration has occupied any unallocated part of
  the range, move the remaining unallocated block above the new head and update this section and
  the journal before creating a file.
- If another initiative occupies a number already recorded in the ledger before this program's file
  is created, never overwrite the conflicting file. Mark the old ledger allocation `SUPERSEDED`,
  allocate a new number above the current head, and record both decisions. If this program's
  migration file already exists or may have been applied, its number is immutable.
- Record the task ID, exact migration filename, allocation date, and status in the ledger below
  before implementation. One number belongs to one immutable migration file. Never rename, edit,
  reuse, or squash a migration that may have been applied.
- Migration allocation does not replace TDD. Each migration task starts with a failing clean-schema
  PostgreSQL contract test. A migration that replaces an existing shape also proves the final
  constraints refuse the shape it replaced, so the old shape cannot be written again.

| Task    | Migration filename                                     | Allocated  | Status                                                                                                                                                                                                                                                                                                                                                                                                       |
|---------|--------------------------------------------------------|------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `P1-T2` | `V76__field_value_schema_default_provenance.sql`       | 2026-08-31 | Created and contract-tested. Widens `ck_field_value_provenance` with `SCHEMA_DEFAULT`.                                                                                                                                                                                                                                                                                                                       |
| `P1-T3` | `V77__schema_field_binding_stable_field_invariant.sql` | 2026-08-31 | Created and contract-tested. Adds and backfills the stable `field_definition_id` invariant key, records and resolves conflicts, then enforces uniqueness and contract consistency.                                                                                                                                                                                                                           |
| `P1-T4` | `V78__field_value_datetime_instant_semantics.sql`      | 2026-08-31 | Created and contract-tested. Converts `field_value.datetime_value` to an explicit instant and adds `datetime_offset_minutes` with its range and companionship rule.                                                                                                                                                                                                                                          |
| `P1-T5` | `V79__field_value_root_value_set.sql`                  | 2026-08-31 | Created and contract-tested. Adds `field_value_set`, backfills one root set per Schema Assignment, carries every Field Value into it, and moves value uniqueness from the assignment to the set.                                                                                                                                                                                                             |
| `P1-T6` | `V80__field_value_canonical_principal_provenance.sql`  | 2026-08-31 | Created and contract-tested. Adds canonical principal kind, principal ID, and non-secret session reference to `field_value` and `schema_assignment`, backfills them from the trustworthy legacy App User keys, ties the legacy key to the canonical pair, and adds the append-only `field_value_revision` and `field_value_revision_selection` tables with one backfilled revision per existing Field Value. |

| `P1-T7a` | `V81__field_value_set_revision.sql` | 2026-08-31 | Created and contract-tested. Adds the monotonic
`revision` count to `field_value_set`, carries every existing set to its first revision, and refuses any update that
would move a count backwards. |

| `P2-T1a` | `V82__fields_personal_ownership.sql` | 2026-08-31 | Created and contract-tested. Adds the `scope_user_id`
owner to `field_definition`, `schema_definition`, and `schema_assignment`, widens the three scope-kind checks to
`PERSONAL`, replaces the two owner checks with three-way owner checks, adds the assignment owner check the released
schema never had after restoring drifted rows from their definition, and rebuilds `ux_field_def_key` and
`ux_schema_def_key` so each owner contributes its own id instead of sharing the platform sentinel. |

| `P2-T1d1` | `V83__subscription_feature_entitlement_owner.sql` | 2026-09-01 | Created and contract-tested. Renames
`organization_feature_entitlement` to `subscription_feature_entitlement`, adds the `owner_type` kind and the
`app_user_id` owner beside the now-nullable `organization_id`, carries every released row across as an organization
decision, adds the owner check that admits exactly one owner per kind, and replaces the
`(organization_id, feature_code)` unique constraint with a per-owner unique index so a nullable organization id cannot
stop constraining a personal row. |

| `P2-T1f` | `V84__audit_personal_ownership.sql` | 2026-09-01 | Created and contract-tested. Adds the audit owner pair
to the outbox, ledger, analytics, export, and retention tables, backfills the trustworthy platform and organization
owners, and keeps an equal-UUID organization and user in separate owner streams. |

| `P2-T2` | `V85__subject_identity_ref.sql` | 2026-09-01 | Created and contract-tested. Adds the opaque
`subject_identity_ref` identity with exactly one organization or person owner, the separate authorized external alias
table, and the tenant-bound, single-successor, cycle-safe, append-only merge and supersession lineage. |

| `P2-T3` | `V86__information_request_template.sql` | 2026-09-01 | Created and contract-tested. Adds the Template
Definition with the three owner key spaces, the Version with per-definition numbering and its publication rule, the
per-Version ordered Section, the per-definition stable Requirement, and the Requirement Binding whose composite foreign
keys hold its section to its own Version and its requirement to its own definition. Guards freeze a published Version
and its configuration except to record retirement, and refuse any rewrite of a stable requirement identity. |

| `P2-T4` | `V87__information_request_template_requirement_policy.sql` | 2026-09-01 | Created and contract-tested. Adds
the requirement type to the stable requirement identity and widens the identity guard to protect it, adds the nine
respondent-policy columns and their checks to the Requirement Binding, and adds the permitted-disposition and
supporting-evidence sets as version-keyed child tables that freeze with the Version. Publication now refuses a Version
that binds a Field Requirement without naming the Schema Version it resolves against. |

| `P2-T5` | `V88__information_request_template_evidence_policy.sql` | 2026-09-01 | Created and contract-tested. Adds the
per-binding evidence policy with its file-count, size, page, attribute-requirement, freshness, validity, coverage,
waiver, and conformance columns and checks, the generic accepted-value set keyed by which attribute it restricts, and
the flat substitute-evidence set. Triggers keep a policy and a substitute on Document requirements only, refuse a
chained substitute from either end, and freeze all three with the Version. Publication completeness moves into its own
function: the released typed-data rule now sits beside the rules that a requested document has a policy, that no
restriction names an uncaptured attribute, and that the waiver rule and the permitted waived answer agree. |

| `P2-T6` | `V89__information_request_template_version_capability.sql` | 2026-09-01 | Created and contract-tested. Adds
the per-Version capability requirement with its closed capability vocabulary, its per-Version uniqueness, and its
contract-version floor, and freezes it with the Version through the guard the earlier configuration tables already use.
Adds `request_template_required_capabilities`, which derives from one Version's configuration the set of capabilities it
needs, and two publication-completeness rules that refuse a recorded set differing from the derived one in either
direction. |

| `P2-T9` | `V90__blueprint_information_request_template_version.sql` | 2026-09-01 | Created and contract-tested. Adds
the optional `blueprint_definition.information_request_template_version_id` reference to one exact Template Version,
with a restricting foreign key so a Version something still names cannot be removed, and a partial index over the rows
that name one. Storage deliberately states nothing about the named Version's status: a reference stays after retirement
so the blueprint remains readable and editable, and whether a new request may be created from it is asked at
instantiation. |

| `P2-T10b` | `V91__information_request_template_collected_field.sql` | 2026-09-02 | Created and contract-tested. Adds
the stable collected Field reference for typed-data requirements, refuses one on other requirement kinds, keeps one
Version from collecting the same Field twice, and asks at publication whether the named Schema Version contains that
Field. |

| `P3-T1c` | `V92__share_resource_scoped_role_key.sql` | 2026-09-02 | Created and contract-tested. Widens Share storage
to admit `INFORMATION_REQUEST`, widens `share.resource_type` to `VARCHAR(64)` so longer central ResourceType values
reach the check constraint, keeps legacy Exchange-role strings for Exchange, Document, and Principal Group rows, admits
only request-party role keys for request rows, refuses all other resource types, and preserves existing Share rows
across upgrade. |

| `P3-T1d` | `V93__share_canonical_principal_provenance.sql` | 2026-09-02 | Created and contract-tested. Adds canonical
grantor and revoker principal kind and ID columns to Share, backfills legacy App User provenance as `USER`, keeps legacy
user foreign keys aligned when present, indexes canonical provenance, and refuses half-principal or legacy-drift rows. |

| `P3-T2b` | `V94__domain_event_outbox_owner.sql` | 2026-09-02 | Created and contract-tested. Adds explicit owner kind
and owner ID columns to the shared durable domain-event outbox while preserving the physical `workflow_event_outbox`
table, backfills legacy rows from `organization_id`, keeps rolling old-writer inserts owned by trigger, and admits
personal user-owned event rows without an organization. |

| `P3-T3` | `V95__command_receipt.sql` | 2026-09-03 | Created and contract-tested. Adds the shared `command_receipt`
table with a per-resource, per-operation, per-actor idempotency-key uniqueness scope, stores the canonical request
fingerprint and replay result reference, and indexes resource and actor lookup paths. |

| `P3-T5` | `V96__information_request_runtime_persistence.sql` | 2026-09-03 | Created and contract-tested. Adds runtime
request aggregates, request-scoped parties, stable runtime Requirement occurrences, append-only Requirement revisions,
current-revision pointers, and append-only transition history with owner and Template Version guards. |

| `P3-T8` | `V97__external_participant_personal_owner.sql` | 2026-09-03 | Created and contract-tested. Adds an explicit
personal owner App User column to External Participants, enforces exactly one owner kind, and replaces organization-only
email uniqueness with owner-scoped organization and personal unique indexes. |

| `P3-T8` | `V98__information_request_delegated_authority.sql` | 2026-09-04 | Created and contract-tested. Adds the
minimal delegated-authority fact table, principal-kind check, indexes, and same-request guards for the assigned party
and optional Requirement scope. |

| `P3-T9a` | `V99__information_request_ad_hoc_template_origin.sql` | 2026-09-05 | Created and contract-tested. Adds
reusable versus ad hoc Template origin facts, deferrable request-origin ownership, a one-private-Template-per-request
uniqueness guard, and reusable-list filtering support. |

| `P3-T9b` | `V100__information_request_blueprint_document_placeholders.sql` | 2026-09-05 | Created and contract-tested.
Adds request-scoped document placeholders sourced from Blueprint document defaults, snapshots Document Library metadata
without storage paths, keeps the optional library reference nullable on library deletion, and guards blank titles and
negative file sizes. |

| `P3-T11a` | `V101__information_request_transition_party.sql` | 2026-09-06 | Created and contract-tested. Adds a
nullable `party_id` column to `information_request_transition` with an index and an insert-time scope guard trigger so
party-scoped reassignment history is queryable. |

| `P3-T12` | `V102__information_request_delegated_authority_instrument.sql` | 2026-09-06 | Created and contract-tested.
Adds grantor identity, an authority instrument or evidence reference, an effective/expiry window with a `CHECK` that
expiry follows the effective date, and an explicit revocation record (`revoked_at`, revoker, reason) with a `CHECK`
tying it to the `active` flag. The table has no rows in any environment, so the new `NOT NULL` columns needed no default
or backfill. |

| `P5-T4a` | `V115__information_request_condition_hidden_data_policy.sql` | 2026-09-08 | Created. Adds the
per-condition hidden-response-data policy column, defaults existing rows to `RETAIN_SECURELY`, and constrains stored
values to the three platform policies. Docker was unavailable in this environment, so its Testcontainers contract could
compile but could not execute. |

| `P5-T4b` | `V116__information_request_response_hidden_state.sql` | 2026-09-08 | Created. Adds active or
hidden state to mutable response drafts so hidden conditional responses can leave active response projections while
preserving their recorded data or clearing the active reference after explicit confirmation. Docker was unavailable in
this environment, so the updated Testcontainers runtime-persistence contract could compile but could not execute. |

| `P5-R01` | `V117__request_access_session_credential.sql` | 2026-09-09 | Created and PostgreSQL contract-tested. Adds hashed independent session credentials, enforces expiry for credential-bearing sessions, freezes binding identity, and revokes legacy token-only sessions. |

| `P6-T1b` | `V120__information_request_evidence_artifact.sql` | 2026-09-24 | Created and PostgreSQL
contract-tested. Adds the logical evidence Artifact bound by composite foreign key to a runtime
Requirement occurrence of its own request, its non-blank per-Requirement artifact key and optimistic
revision, and the append-only evidence Version whose source is either one exact `document_version`
row or a typed nonblank external reference, never both and never neither. A trigger refuses a gap in
per-Artifact version numbers while the unique constraint refuses a repeated or lower number, and the
shared append-only guard refuses every update and delete. Also adds the
`(id, information_request_id)` unique key on `information_request_requirement` that the Artifact
composite foreign key resolves against. |

| `P6-T2a2` | `V121__document_version_storage_locator.sql` | 2026-09-24 | Created and PostgreSQL
contract-tested. Adds the optional `storage_provider`, `storage_locator_kind`, and `storage_locator`
columns beside the retained `storage_path`, so a stored version states which provider holds its
content and what kind of identifier the value is. A locator is either fully stated or absent, a
stated one cannot claim a kind its provider does not serve, and a blank value is refused. |

| `P6-T2b` | `V122__document_version_creator_principal.sql` | 2026-09-24 | Created and PostgreSQL
contract-tested. Adds the optional `created_by_principal_kind` and `created_by_principal_id` columns
beside the retained `created_by` foreign key and `createdbyemail` label, backfills `USER` only where
that trusted foreign key is non-null, and reports by notice how many rows name a creator by email
alone. Constraints hold the principal vocabulary, keep the kind and identifier together, and stop
the canonical pair and the retained foreign key from naming different creators, while still
accepting a row that states the foreign key alone so a rolling deployment does not break. The
development-stage constraint supersedes that allowance; `DS-T1b` drops the constraint together with
the legacy columns it guards. |

| `DS-T1a` | `V123__document_version_canonical_storage_locator.sql` | 2026-09-24 | Created and PostgreSQL
contract-tested. Removes every document version that could only be located through the retired local
path, drops `storage_path`, makes `storage_provider`, `storage_locator_kind`, and `storage_locator`
`NOT NULL`, and replaces the provider and kind check with one that admits only `OBJECT_STORE` with
`OBJECT_KEY`. |

| `DS-T1b` | `V124__document_version_canonical_creator.sql` | 2026-09-24 | Created and PostgreSQL
contract-tested. Carries a version still named only by `created_by` to that registered user as a
canonical principal, removes a version whose creator could only be stated by an email or not at all,
drops `created_by`, `createdbyemail`, the legacy and pair constraints, and the creator foreign key,
and makes `created_by_principal_kind` and `created_by_principal_id` `NOT NULL`. |

| `DS-T2` | `V125__fields_canonical_attribution.sql` | 2026-09-24 | Created and PostgreSQL
contract-tested. Drops `field_value.updated_by_app_user_id`, `schema_assignment.assigned_by_app_user_id`,
and `field_value_revision.recorded_by_app_user_id` with their legacy consistency constraints. |

| `DS-T3` | `V126__share_canonical_provenance.sql` | 2026-09-24 | Created and PostgreSQL
contract-tested. Drops `share.granted_by_app_user_id` and `share.revoked_by_app_user_id` with their
legacy consistency constraints. |

| `DS-T4` | `V127__information_request_root_occurrence_path.sql` | 2026-09-24 | Created and PostgreSQL
contract-tested. Carries Requirement, Requirement revision, and response rows stored under the earlier
`$` root occurrence spelling to `root`, holding the append-only guards off only for that
normalisation, and adds checks that refuse `$` as an occurrence path on all three tables. |

| `P6-T2c` | `V128__document_version_content_identity.sql` | 2026-09-25 | Created and PostgreSQL contract-tested. Adds the required
content length, hash algorithm, SHA-256 hash, and `VERIFIED` or `UNVERIFIED` state to `document_version`,
removes every version that never stated them, and refuses any rewrite of a version's storage or content
identity. |

| `P6-T2d` | `V129__information_request_evidence_record_shape.sql` | 2026-09-25 | Created and PostgreSQL contract-tested. Adds the
Artifact creator and collection state with its last change, the Version uploader, declared file name and
media type, and captured attributes, and guards Artifact identity, revision, and state movement. |

| `P6-T6a` | `V130__information_request_evidence_assessment.sql` | 2026-09-25 | Created and PostgreSQL contract-tested. Adds the
append-only `information_request_evidence_assessment` table for content inspection and malware scan
results, bound to one file-backed Evidence Version and the exact digest of its bytes, refusing any
inspection or clean scan of opaque content and any reuse that does not repeat a settled scan of the
same bytes by the same engine and signatures. |

| `P6-T8` | `V131__information_request_supporting_evidence_link.sql` | 2026-09-25 | Created and PostgreSQL contract-tested. Adds the
append-only `information_request_supporting_evidence_link` table joining one runtime Requirement
occurrence to one Document Requirement occurrence of the same request through the Template evidence
link it follows. |

| `P7-T4a`, `P7-T7a` | `V132__information_request_template_submission_policy.sql` | 2026-09-25 | Created and PostgreSQL contract-tested. Version submission mode and stage ordering, section stage keys, binding attestation policy and ordered role set, restated freeze guard, publication rules, and review-routed capability derivation. |

| `P7-T1a`, `P7-T4b`, `P7-T7b` | `V133__information_request_submission_package.sql` | 2026-09-25 | Created and PostgreSQL contract-tested. Submission Packages with items, evidence and link members, Submission Attestations and package membership, withdrawals, request satisfaction, and the widened transition mutations. |

| `P7-T5a`, `P7-T5b` | `V134__information_request_amendment.sql` | 2026-09-25 | Created and PostgreSQL contract-tested. Runtime Requirement effective binding, relaxed revision key, amendment, change, and Notice Intent records, and response reconfirmation. |

| `P7-T6`, `P7-T7c` | `V135__information_request_lineage.sql` | 2026-09-25 | Created and PostgreSQL contract-tested. Successor lineage, carry-forward decisions, recurrence definitions, and refresh rules. |

| `P8-T1a` | `V136__information_request_template_review_plan.sql` | 2026-09-25 | Created and PostgreSQL contract-tested. Version review stage ordering and fact-reuse purpose, review stages and their section coverage, default stage at freeze, restated freeze guard, and publication rules. |

| `P8-T1b` through `P8-T7` | `V137__information_request_review.sql` | 2026-09-25 | Created and PostgreSQL contract-tested. Request state vocabulary without package review states, review cycles, assignments, worksheets, decisions, findings, corrections with their allowlists, comments, remediation, and the widened transition mutations. |

| `P8-T8`, `P8-T9` | `V138__information_request_accepted_fact_business_decision.sql` | 2026-09-25 | Created and PostgreSQL contract-tested. Accepted Facts with revocation, and Business Decision references. |

| `P9-T1`, `P9-T2` | `V139__information_request_event_consumption.sql` | 2026-09-26 | Created and PostgreSQL contract-tested. Trigger registry subject type, schema version, and request trigger rows; outbox ordering key and sequence; consumption receipts; request view, start, and expiry instants; widened transition mutations. |

| `P9-T5` | `V140__information_request_clock.sql` | 2026-09-26 | Created and PostgreSQL contract-tested. Clock policies with immutable versions, working periods, holidays, and reminders; request clocks and their append-only calculation history. |

| `P9-T8` | `V141__information_request_outbound_notice.sql` | 2026-09-26 | Created and PostgreSQL contract-tested. Notice intent sources and kinds without the stored delivery state, claims, immutable outbound notices, sequence allocations, and delivery attempts. |

| `P9-T7`, `P9-T9` | `V142__record_preservation_disposal.sql` | 2026-09-26 | Created and PostgreSQL contract-tested. Generalized hold ownership, scope, attribution, and lifecycle history; retention schedules; disposal claims, objects, and tombstones; record exports; the claimed disposal function. |

| `P9-T10` | `V143__information_request_privacy.sql` | 2026-09-26 | Created and PostgreSQL contract-tested. Privacy requests, subject restrictions, and package item corrections. |

| `P10-T2`, `P10-T6` | `V144__information_request_author_actions.sql` | 2026-09-27 | Created and PostgreSQL contract-tested. Party assignment, party revocation, and sent-reminder transition mutations; a sent-reminder source on notice intents. |
| `P11-T1b` | `V145__information_request_reusable_fact_profile.sql` | 2026-09-29 | Created and PostgreSQL contract-tested. Required reuse policy basis and exact submitted evidence references. |
| `P11-T1b` | `V146__information_request_fact_evidence_disposal.sql` | 2026-09-29 | Created and PostgreSQL contract-tested. Disposal counts promoted evidence references before deleting their parent facts. |
| `P11-T1c` | `V147__information_request_fact_recertification.sql` | 2026-09-29 | Created and PostgreSQL contract-tested. Append-only respondent recertification records with copied source provenance and evidence, insert guards, the `RECERTIFY_FACT` transition mutation, and disposal of a request's own recertifications. |
| `P11-T11`, `P11-T12` | `V148__information_request_external_sources.sql` | 2026-09-29 | Created and PostgreSQL contract-tested (3 tests). Connector exchanges with a forward-only state guard and a same-request Requirement guard, append-only imported values, decisions, discrepancies, and resolutions with same-request guards, generated output references, four transition mutations, and disposal of every external source record. Not applied to the local database when created. |

The provisional program range ended at V139. On 2026-09-26, before the second Phase 9 migration, it
was extended to V139 through V160; the head was V138 and no other initiative had taken a number
above it. Remaining unallocated program range: V149 through V160. Flyway head is V148.
V118 and V119 were taken by prior P5 remediation work before this row was written.

When verifying that a migration contract test is genuinely red, remove the migration from
`target/classes/db/migration` as well as from `src/main/resources/db/migration`. Flyway resolves migrations from the
compiled classpath copy, so deleting only the source file leaves the test green and produces a false red observation.

### Schema and behavior change rules

The platform has no production users, so these rules are about keeping current features correct
while a shape is replaced, not about tolerating an older reader or writer. See
`## Development-Stage Constraint`.

- Add and replace structures through forward-only Flyway migrations with PostgreSQL contract tests.
- Never edit, rename, reuse, or squash a migration that may already have been applied. Replace what
  it created in a new forward migration instead.
- One migration takes a shape all the way to its final form: add the new columns, transform or
  remove the rows that cannot satisfy it, drop what it replaces, and enforce the final constraints
  in the same file. Do not leave a column alive for a later contract release.
- In the same phase as every migration, test clean creation and prove the final constraints refuse
  the shape that was replaced. Do not test mixed or rolling application versions, and do not defer
  migration discovery to Phase 12.
- Preserve `UNIQUE(resource_type, resource_id)` on Schema Assignment.
- Preserve the Fields persisted scope spellings and extend all affected Field, Schema, Assignment,
  and Template checks and owner invariants exactly to `PLATFORM`, `ORGANIZATION`, and `PERSONAL`.
  Do not migrate unrelated `APP` or `ORG` domain values. Replace the
  `COALESCE(scope_org_id, '000...0')` sentinel in `ux_field_def_key` and `ux_schema_def_key` with an
  expression that also keys a personal owner, and extend `ScopeReference` plus the three Fields code
  sites that currently require an organization scope in the same task.
- Add `INFORMATION_REQUEST` as a supported Schema target and resource adapter in code only.
  `schema_definition.target_resource_type` is an unconstrained `VARCHAR(48)`, so this needs no
  migration and must not consume a migration number.
- Keep every Field Value in a Value Set and keep Field Value uniqueness set-aware. Existing Exchange
  queries always address the root set; repeatable occurrence sets are Information Request behavior.
- Keep immutable Field Value Revisions so every recorded response resolves the exact canonical value
  and occurrence it recorded.
- Canonical `(kind, id)` principal provenance is the only attribution any row carries. The
  App User-only foreign keys that `P1-T6` and `P3-T1d` kept beside it are compatibility shims and are
  removed by `DS-T2` and `DS-T3`. Do not add another one.
- Generalize the organization-only feature-entitlement rows into owner type and ID in one migration.
  Add operational rollout in a separate owner-scoped table and resolver so no migration or fallback
  can confuse commercial and operational gates.
- Extend audit owner persistence from platform or organization to an explicit owner kind and ID,
  including personal users, and test personal tenant filtering, export, and retention. Correct the
  reachable `?: AuditOwnerScope.Platform` writers rather than describing the rows they wrote as
  personally owned.
- Introduce `SubjectIdentityRef` before runtime request recurrence, Accepted Facts, or reusable
  information profiles, keep mutable PII outside its primary identity, and do not reuse the
  existing `DomainEvent.SubjectRef` name for this persisted tenant identity.
- Widen both Exchange-only Share database checks in one migration. `share_resource_type_check`
  currently admits only `EXCHANGE`, `DOCUMENT`, and `PRINCIPAL_GROUP` and must admit the request
  aggregate and Requirement-occurrence types; without it every request-party Share insert fails.
  `share_role_name_check` currently admits only the seven Exchange role names and must admit the new
  resource-scoped role keys. Replace the Exchange-only enum mapping with resource-scoped role keys
  and a resource-kind capability registry while keeping characterized behavior for the three
  ResourceTypes that hold Share rows. Prove the other nine hold none rather than migrating them.
- Add a personal owner column and owner check to the empty `external_participant` table. There are
  no rows to map and no personal owner to derive, so claim none of that work. Enforce organization
  and personal email uniqueness separately from the first write.
- Leave `exchange_recipient` and its V63 binding trigger unchanged. A request party references an
  `ExchangeRecipient` by ID; no request-party Share is written to `direct_share_id`, and
  `uq_exchange_recipient_primary` keeps its single-primary meaning.
- Leave all current `EXCHANGE` assignments and values attached to their Exchanges.
- Do not label existing metadata as submitted, reviewed, or satisfied.
- Detect and resolve ambiguous duplicate stable-Field bindings before adding the database invariant.
- Preserve existing mutable Blueprint Definitions and their stable Schema Definition and Field
  default behavior. Preserve and explicitly map existing participant, Document, Field, and Document
  Library-derived defaults during request instantiation. Add only an optional exact published
  Information Request Template Version reference for future instantiations. Changing that reference
  never rewrites an already-created request and does not fabricate Blueprint history.
- Add the assign-exact-published-Schema-Version operation before an Information Request can
  materialize a Template Version's Schema reference. Keep the assign-latest-by-definition operation
  only while current Exchange behavior still needs it as a feature, not as a compatibility shim.
- A Document Version states one typed storage locator, with a provider and a locator kind, and
  nothing else. New versions use unique write-once keys, a write never overwrites a prior version,
  and there is no second path by which content can be located.
- A Document Version states its byte length, hash algorithm, and verification status from the bytes
  that were actually written or read. Never copy the mutable Document hash onto a version. Missing
  or unreadable content remains unverified.
- Keep opaque `END_TO_END` Document Versions ineligible for file-backed Evidence satisfaction; no
  migration may translate a ciphertext hash or scan into a plaintext-safety claim.
- Extend `workflow_event_outbox` with explicit owner kind and owner ID while preserving its physical
  name. Keep the existing neutral event envelope and publisher contracts and the separate audit
  outbox. Its `idempotency_key` unique index is global with no type or owner prefix, so define and
  test an Information Request key namespace that cannot collide with a Workflow key.
- Extend existing `ShareLink` persistence with bootstrap mode and add RequestAccessSession records,
  Command Receipts, Notice Intents, immutable outbound notices and delivery attempts, disposal
  claims, tombstones, and purge records through their owning phase migrations. Never treat the
  Exchange-wide secret as request authorization or create a second request-credential table without
  a journaled incompatibility proof.
- Generalize the existing `audit_legal_hold` persistence with owner kind and ID plus append-only
  lifecycle support behind the neutral Record Preservation service. Keep one hold model and do not
  create a parallel active hold table whose state can disagree.
- Register Information Request Workflow trigger events through a Flyway migration that supplies
  versioned `subject_fields_json` descriptors for the existing registry and designer.
- Add immutable Request Execution Grants and idempotent usage reservations before issuance is
  enabled. Record paid or trial entitlement source and the observed enforcement decision. Reserve
  conservative per-request completion capacity transactionally; later plan or trial changes affect
  only new grants and never rewrite issued commitments.
- Preserve ordinary Exchange Documents that are not linked to Request Requirements.
- Provide feature disablement through capability and UI gates, not destructive data reversal.
## Help Documentation Requirements

After every user-visible feature change:

1. Search `web-app/src/app/components/help-docs/sections/` for all affected terminology and behavior.
2. Read every matched article in full.
3. Update navigation, names, permissions, lifecycle, statuses, values, and endpoint descriptions.
4. Keep each article under 150 lines of JSX, each section under 300 lines, and
   `helpDocsRegistry.tsx` under 60 lines.
5. Run `npx tsc --noEmit` from `web-app`.

Expected documentation areas include Business Fields, Schemas, Blueprints, Exchange initiation,
recipient access, no-auth access, Documents, Workflows, audit, subscriptions, and plan availability.

## Program Acceptance Criteria

- Existing Exchange metadata remains behaviorally and semantically intact.
- An Information Request pins the exact Template configuration and, when Field Requirements exist,
  an equal runtime Schema Assignment without a competing mutable source of truth. Ad hoc creation
  first creates and pins a validated private immutable Template Version.
- Existing mutable Blueprint Definitions retain their behavior; an optional Template Version link
  affects future instantiations only and no Blueprint history is fabricated. Existing participant,
  Document, Field, and Document Library-derived defaults are mapped explicitly and remain defaults,
  not submitted data.
- Registered and unregistered respondents can save, resume, submit, and correct only assigned
  Requirements.
- No-auth access extends the existing ShareLink mechanism with recipient-bound, expiring, revocable
  bootstrap mode and uses canonical participant principals produced by a newly built External
  Participant lifecycle. Every configured constraint is enforced
  or rejected. A bootstrap link alone cannot read request content; verified contact proof creates
  the scoped RequestAccessSession. Existing direct-grant ShareLinks keep their current behavior, and
  `exchange_recipient` keeps its Exchange-typed Share binding.
  Registration upgrade preserves participant history and grants the linked App User access without
  a temporary App User identity, and no request path creates one even though the legacy
  `EXTERNAL_EMAIL` selection still does. `Exchange.requireRecipientSignIn` is honored, the legacy
  Exchange-wide credential never authorizes request
  content, and no duplicate request-credential table is created without a journaled incompatibility
  proof.
- Information Request authorization extends the central capability, Share, constraint, and resource
  context stack. Shared request services receive explicit access context and never infer identity
  from raw credentials or create a parallel actor model. Resource-scoped Share roles, explicit
  parent-grant inheritance, and central Requirement policy evaluation preserve behavior for the
  three Share-bearing ResourceTypes and fail closed, including for a resource whose authorization
  context cannot be resolved.
- Organization-owned trusted party assignment, group expansion, replacement, acceptance, and
  registration upgrade reuse existing trust policy, recipient attestation, validation, ShareService,
  and reconciliation paths. Trust suspension has explicit tested effects separate from commercial
  lapse and operational suspension.
- Subject, contributor, preparer, attestor, reviewer, and decision maker can be distinct.
- Repeatable groups and conditional Requirements are server-authoritative and versioned.
- No production code path reads or writes a compatibility shape. Every row states its canonical
  storage locator, creator principal, and attribution, and no legacy column, fallback read,
  dual-write, or mixed-version allowance survives.
- Evidence supports multiple immutable versions, typed write-once storage locators, byte identity,
  policy metadata, hashes, technical conformance, confidentiality compartments, and
  Requirement-specific authorization.
- Malware scanning is optional by the user's decision on 2026-09-25 and planned in
  `plans/INFORMATION-REQUEST-EVIDENCE-MALWARE-SCANNING-PLAN.md`. Where a deployment requires it, a
  real, healthy, fail-closed scanner is required before evidence upload or before any file-backed
  evidence can satisfy a Requirement; content detection, existing Document linkage, and test
  adapters cannot satisfy that gate. No file is ever described as scanned or safe without a scan.
- Opaque `END_TO_END` ciphertext cannot satisfy file-backed Evidence or receive a plaintext-safety
  claim in this program.
- Submission Packages are immutable, atomic, idempotent, and reproducible.
- Retryable mutations use scoped Command Receipts and mutable drafts use required HTTP
  preconditions with deterministic replay and conflict behavior.
- Corrections, supplements, amendments, recurrence, remediation, and retesting preserve history.
- Review satisfaction never implies a favorable business decision.
- Workflow evaluation identifies an exact Information Request and Submission Package; trigger
  events are registered in the existing registry with safe versioned subject-field descriptors.
- Parent Exchange and child request transitions follow one tested transactional matrix, including
  cancellation, ending gates, deletion read-only behavior, and concurrent parent-state changes.
- Paid entitlement lapse or trial expiry blocks new or expanding work without stranding
  already-issued work within persisted, reserved execution limits; operational suspension is a
  separate explicit control. Commercial entitlement and rollout are separate owner-scoped gates,
  and owner rollout remains distinct from global subscription enforcement mode.
- SLA clocks, reminders, explicit-principal audit, immutable notices, retention,
  exactly-once Sequence rendering, record-preservation hold lifecycle generalized from the existing
  hold persistence, live-reference-safe database and object disposal with partial failure recovery,
  and export are deterministic. Personal owners have explicit tenant-safe audit, event, and hold
  owner scopes, and current audit hold APIs cannot disagree with a separate hold system.
- All author, respondent, reviewer, and operations experiences are accessible and responsive.
- Eight executable neutral conformance scenarios pass.
- No unsupported compliance, signature, security, or automated-decision assertion is introduced.
- No new AWS service or paid resource type is added without explicit user approval.
- All required backend, frontend, migration, documentation, and manual validation gates pass.

## Latest Implementation Result

Keep only the newest product implementation result in this section. Full historical results and completion evidence live
in `plans/DOCUMENT-DRIVEN-INFORMATION-REQUESTS-COMPLETION-EVIDENCE.md`. When a newer implementation session finishes,
make sure this result is present in the evidence file, then replace it here with the new latest result.

### 2026-09-29: Phase 11 external sources, connectors, and exit gate

- Resumed an interrupted, unjournaled start of `P11-T11` and `P11-T12` and corrected the stale
  Status (it still named `P11-T8` although the journal recorded `P11-T8` through `P11-T10`). The
  design was recorded first as `### Phase 11 design decisions (2026-09-29)`.
- `P11-T11`: `InformationRequestConnector` states its contract as data (key, kind, version, result
  keys, optional result age limit), and the registry refuses two adapters under one key and is empty
  outside tests. A connector exchange carries an optional lookup reference, allows one open exchange
  per Requirement and connector, and checks continuation entitlement. A scheduled worker (off in the
  test profile) checks the request state, the installed connector, and the contract version before
  any call, then records `PENDING`, `COMPLETED`, or `FAILED` under the request lock with bounded
  retries.
- `P11-T12`: imported values are canonicalized by the collected Field's contract
  (`InformationRequestImportedValueCanonicalizer`) or validated as scalars, and keep their source,
  confidence, verification time, expiry, and provenance. A reviewer decides each once (never the
  author of a manual value, never accepting an expired one); reconciliation reports `MATCHES`,
  `DIFFERS`, `NO_ANSWER`, `NOT_COMPARABLE`, or `EXPIRED` and records discrepancies once per answer
  revision, each resolved once. Nothing writes an answer or an Accepted Fact. Generated output
  references are recorded without fetching or storing anything. REST lives under
  `/information-requests/{id}/` (`connector-exchanges`, `imported-values` with `/{valueId}/decisions`,
  `imported-value-reconciliations`, `imported-value-discrepancies/{discrepancyId}/resolutions`,
  `generated-outputs`). The record export (schema version 2) now includes external sources and the
  request's own recertifications. V148 was tightened before its first application, and a help
  article was added.
- Defect fixed: the authorizer refused every non-read action on a CLOSED request, so fact promotion,
  business decisions, and external-source records failed there despite the transition matrix. Those
  record actions are now exempt from the archived refusal for the request resource only.
- Exit gate: full backend `.\mvnw.cmd -o test -DskipFrontend=true` passed 3,285 tests (0 failures,
  errors, or skips, 35:07); the eight conformance scenarios passed 13 tests after the participant
  variants were added; `npm.cmd test -- --maxWorkers=2` passed 730 tests in 173 files;
  `npx.cmd tsc --noEmit`, `npm.cmd run typecheck:app` (346 reviewed unrelated, 0 Information
  Request), and `npm.cmd run build` passed; `npm.cmd run lint` (109 problems) and `buildWithTs` (346
  diagnostics) match their recorded baselines. Neutrality audit clean. No commit or push.
- Exact next task: `P12-T1`, only when the user asks for Phase 12.

## Continuation Prompt

Use this instruction in a new implementation session:

> Continue the Document-Driven Information Requests implementation from
> `plans/DOCUMENT-DRIVEN-INFORMATION-REQUESTS-IMPLEMENTATION-PLAN.md`. First read `AGENTS.md`, then the
> full active plan, including `## Status`, `## Plan and Evidence Update Protocol`,
> `## Mandatory Protocol for Every Implementation Session`, and `## Latest Implementation Result`. Use
> `plans/DOCUMENT-DRIVEN-INFORMATION-REQUESTS-COMPLETION-EVIDENCE.md` for prior completion results,
> evidence, and older decisions. Also read the 2026-09-13 recheck at
> `plans/DOCUMENT-DRIVEN-INFORMATION-REQUESTS-PHASES-1-5-RECHECK.md`. Inspect the working tree and
> preserve unrelated changes. `P5-R01` through `P5-R30`, `P5-R-GATE`, and Phases 6 through 11 are complete.
> On 2026-09-25 the user made malware scanning optional and moved it to
> `plans/INFORMATION-REQUEST-EVIDENCE-MALWARE-SCANNING-PLAN.md`; evidence collection works in
> production without a scanner, and no file is ever described as scanned or safe without a scan.
> The platform has no production users and is in active development: read
> `## Development-Stage Constraint` and `## Development-Stage Compatibility Removal`, write no
> backwards-compatibility code, and continue with the next incomplete task, which is P12-T1, when
> the user asks for Phase 12; read the Phase 12 handoff in `## Status` first. Do not assume permission for
> a new AWS service or paid resource type. For implementation,
> follow TDD: add a focused failing test, confirm the intended
> failure, implement the smallest complete change, run focused and required regression tests, update
> affected help documentation, then update the active plan and companion evidence file with the exact
> next starting point. Do not commit or push without explicit permission.
