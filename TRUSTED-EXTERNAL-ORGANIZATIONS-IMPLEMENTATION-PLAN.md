# Trusted External Organizations Implementation Plan

## Purpose

This document replaces the earlier pairing plan with an implementation plan based on the current
DocuHyphen codebase.

Before implementing any part of this plan, read `AGENTS.md` and this document in full. `AGENTS.md`
is the controlling instruction set.

## Session Start and Phase Handoff Protocol

Every implementation session must begin by reading `AGENTS.md` and this implementation plan in
full before changing code. The session must treat `AGENTS.md` as the controlling instruction set
when this plan is incomplete, ambiguous, or inconsistent with repository rules.

After implementing each phase, and before beginning the next phase, the implementing session must:

1. Re-read `AGENTS.md` and validate every change from the completed phase against its backend,
   frontend, REST, documentation, infrastructure, testing, and coding requirements.
2. Correct every identified violation before marking the phase complete. A phase is not complete
   while a known `AGENTS.md` violation remains in its implementation.
3. Run the verification required by `AGENTS.md` and by the phase exit criteria. Record the commands,
   results, failures, and any confirmed unrelated failures in this plan.
4. Update the phase section in this plan with its current status, completed work, important design
   decisions, files or areas changed, verification results, and remaining work.
5. Add enough concrete handoff context for a new session to continue without relying on chat history.
   The handoff must identify the next task, relevant code locations, unresolved risks, and any
   assumptions that still need validation.
6. Reconcile later phases with discoveries made during implementation so that obsolete or incorrect
   instructions are removed instead of being carried forward.

The plan is the durable implementation handoff between sessions. Chat history is supplementary and
must not be the only source of implementation state or decisions.

The feature is named **Trusted Organizations** in the product and code written for the new feature.
Obsolete `pair`, `pairing`, `link`, and `linked organization` names are removed as part of the
implementation.

## Development-State Assumption

DocuHyphen is still under development. This feature is implemented as a clean replacement, not as
a backwards-compatible production migration.

- Do not add dual-write, fallback reads, compatibility endpoints, compatibility DTOs, transitional
  feature flags, or one-time production remediation scripts.
- Do not backfill obsolete organization links or infer recipient purpose from existing Shares.
- Delete obsolete relationship code, tables, settings, APIs, UI, tests, and terminology in the same
  implementation that introduces their replacements.
- Existing development data may be reset. Correctness of the new model takes priority over
  preserving obsolete development records.
- Continue to use a new forward-only Flyway migration. Do not edit an applied migration or
  `V1__baseline.sql`.

## Executive Decision

Replace the current organization link feature with a jointly controlled trust relationship that:

- Lets two verified, active organizations agree to collaborate.
- Gives each organization independent send, receive, member-resolution, and group-discovery policy.
- Supports exact-email resolution of a known member without exposing an external directory.
- Supports explicitly published external Principal Groups.
- Uses the caller's validated active organization for every organization-scoped decision.
- Uses Shares for access while recording recipient purpose and acceptance separately.
- Preserves historical verification evidence without presenting it as current truth.

This is not a new authorization model. The feature must use the existing `AuthorizationService`,
`Action`, `Capability`, `ResourceRef`, active organization context, Share model, audit ledger,
security incident service, and Redis-backed rate limiting.

No new AWS service or paid cloud resource type is required.

## Product Contract

### Recipient paths

Exchange initiation supports four distinct paths:

1. **People**: an existing contact or an exact email invitation. This is not organization-verified.
2. **My Groups**: a personal Principal Group.
3. **My Organization**: a person or Principal Group in the active organization.
4. **Trusted Organization**: a verified member resolved by exact email or a published Principal
   Group belonging to an active trusted organization.

Selecting a trusted organization is an explicit assurance choice. DocuHyphen never infers trusted
membership from an email domain, a previous Exchange, a contact record, or an organization name.

### Assurance language

The first release may attest only facts represented by the current model:

- The target organization is active and has `verificationComplete = true`.
- The trust relationship is active for the caller and target organizations.
- The account is active and not deprovisioned.
- The account has an active membership in the selected target organization.
- The target organization allows the requested operation.
- The display name and exact normalized email were resolved from the account at verification time.

The UI label is `Membership verified by <organization>`. Do not use `Identity verified`, because the
current model does not prove a person's real-world identity. Do not display title, department, or
similar fields in the first release. Those fields do not currently exist as organization-attested
profile data.

### Trusted person flow

1. The sender has an active organization selected.
2. The sender chooses `Trusted Organization`.
3. The sender selects an active trusted organization.
4. The sender enters a complete work email address.
5. DocuHyphen performs an exact normalized-email resolution inside the selected organization.
6. On success, the UI displays the resolved display name, normalized email, organization, and
   verification expiry.
7. The sender confirms the result and submits the Exchange with the resolution ID.
8. The backend consumes the resolution in the Exchange transaction and revalidates all policy and
   membership facts.
9. The recipient must sign in. Acceptance is allowed only to the resolved account while its target
   organization membership remains active.

Trusted external selections always require recipient acceptance, even when the sender organization
normally auto-starts Exchanges. This is part of the assurance contract.

### Trusted group flow

1. The sender selects an active trusted organization.
2. DocuHyphen lists only active ORG-scoped Principal Groups that the target organization published
   and its policy permits the partner to discover.
3. The sender selects a group.
4. Exchange initiation revalidates the group, relationship, both organizations, and both policies.
5. A group OWNER or MANAGER may accept or reject when recipient acceptance is required.
6. The group Share becomes active only after approval and acceptance gates permit activation.

### General email invitations

The `People` email path remains separate from trusted resolution.

- It displays `External recipient`, never a trusted badge.
- It does not receive organization-attested profile data.
- An email domain is not treated as membership evidence.
- Existing contact status is not treated as current membership evidence.
- Organization B2B policy is still enforced by the backend when the email resolves to an account.
- Policy must be rechecked when an invited person authenticates or accepts.

### Relationship changes

- Pending, rejected, ended, or effectively suspended trust blocks new trusted resolutions, group
  discovery, trusted selections, and trusted Exchange initiation.
- Pending Exchange invitations are revalidated at acceptance and fail closed when trust, policy,
  account, membership, or group eligibility is no longer valid.
- An accepted Exchange retains existing explicit and already-materialized Shares when trust is
  suspended or ended.
- A trusted person's membership becoming inactive after acceptance updates the attestation state but
  does not automatically revoke that person's explicit Share. The Exchange owner can revoke it.
- Removing a person from a Principal Group still removes inherited access.
- Adding a new person to a trusted external group materializes new inherited access only while the
  relationship is active and both directional Exchange policies still permit it.
- Resuming trust reconciles eligible group membership. Ending trust is terminal and never resumes
  group expansion for existing Exchanges.
- Historical UI shows `Verified at send` separately from current relationship and membership state.

## Current Codebase Findings

The implementation must begin with these facts, not with assumptions from the previous plan.

### Foundations that already exist

- `AuthTokenContext.activeOrganizationId` is populated from a validated
  `X-Active-Organization-Id` header.
- `SessionService` exposes active organization roles and effective capabilities.
- `AuthorizationService`, `Action`, `Capability`, and resource authorization-context providers are
  the centralized authorization foundation.
- Exchange ownership is represented by `ownerOrganizationId` or `ownerUserId`.
- Exchange access is represented by `Share`; group inheritance is materialized.
- Organization membership is multi-organization and has explicit status and roles.
- ORG-scoped Principal Groups have `ownerOrganizationId`, `externallyPublished`, and `isActive`.
- `AuditRecorder` writes durable audit intents to the existing audit outbox and ledger.
- `DirectoryLookupGuardService`, `SecurityIncidentService`, and the Redis-backed rate limiter exist.
- The frontend API client sends active organization context.

### Baseline unsafe findings and current disposition

- The linked member-enumeration endpoint and its frontend caller were removed in Phase 0.
- The member-bearing linked-group endpoint and caller were removed in Phase 4. The replacement uses
  a dedicated member-free published-group projection.
- Trust administration, discovery, policy, suspension, and notification paths now use the new trust
  aggregate and resource-aware authorization. Obsolete pairing administration is deleted.
- `OrganizationExchangeLink` persistence, the `allow_share_without_pairing` setting, and the minimal
  policy lookup were removed in the Phase 7 clean cutover (V64). Cross-organization sharing now
  evaluates current relationship, organization, and directional policy through
  `OrganizationTrustExchangePolicyService`.
- Organization discovery is an explicit opt-in and no longer depends on the sharing setting.
- Exchange initiation uses validated active organization context for ownership, policy, settings,
  variables, workflows, and audit ownership.
- Primary and participant recipient selections are discriminated and server-validated. Recipient
  purpose and acceptance authority are persisted in `exchange_recipient`.
- Recipient decisions use `EXCHANGE_ACCEPT`; generic Exchange updates cannot accept or reject.
- Exact-email identity resolution persistence, guards, cleanup, REST API, and confirmation UI exist
  (Phase 5, certified 2026-07-17). Phase 6 wired `TRUSTED_PERSON` end to end: a resolution is now a
  real discriminated recipient selection with transactional row-locked consumption, person
  attestation, forced sign-in and acceptance, and acceptance-time revalidation.
- The trusted recipient UI is decomposed into focused components. Phase 8 split the recipient tab,
  cleared touched dead code, and added full-stack owner recovery for pending trusted-person
  invitations.
- A 2026-07-18 post-implementation audit found unresolved policy, acceptance, owner-recovery,
  frontend-compliance, and certification gaps. The feature is not complete until the correction
  section below is implemented and verified.

## Post-implementation Audit Correction - 2026-07-18

The 2026-07-18 audit compared the current working tree with the Product Contract, Required Test
Matrix, Definition of Done, and `AGENTS.md`. The main persistence, relationship lifecycle, exact
identity resolution, published-group, trusted-primary-recipient, and migration work is present.
The audit found four product-contract defects and several certification and handoff defects.
All seven original findings were corrected and verified on 2026-07-18. Manual browser
certification later that day found runtime response-serialization defects and a pre-authentication
organization-discovery privacy defect. Under the current handoff, Phases 1, 4, 7, and 8 remain
reopened until the open browser findings below are corrected and the remaining manual matrix
passes. The historical audit baseline remains below only to explain why the phases were originally
reopened.

### Trusted additional participant Product Contract decision - 2026-07-18

Trusted people and trusted groups added as participants require an independent recipient-specific
acceptance decision before their direct Share can become active. Initiation persists each trusted
participant Share as `PENDING_APPROVAL` and each participant recipient binding as `PENDING`.
The invited trusted person, or a current eligible OWNER or MANAGER of the invited trusted group,
may accept or reject only that participant invitation. A participant decision does not accept or
reject the Exchange and does not change the primary recipient's decision.

Acceptance revalidates the attested account or group, membership, both organizations, the current
relationship generation, suspensions, relationship review validity, and both directional policies.
Rejection remains available when eligibility has lapsed because it activates no access. Acceptance
failure, stale state, client tampering, or transaction rollback leaves the Share inactive. This
decision preserves the Product Contract statement that every trusted external selection requires
recipient acceptance and supersedes older Phase 1 and Phase 4 notes that participants use
`NOT_REQUIRED` or activate immediately.

### Resolved post-implementation findings

1. **Ordinary B2B directional trust policy - resolved and verified 2026-07-18**
   - Added `OrganizationTrustExchangePolicyService`, which evaluates the current relationship
     generation, active status, suspension state, relationship review deadline, both policy
     directions, policy expiry and review deadlines, and both organizations' active and verified
     state through owning service boundaries.
   - `OrganizationExchangePolicyService.assertCanShareWithUser` now accepts a B2B recipient only
     when at least one of the recipient's current active organization memberships passes that
     evaluation. `assertCanShareWithGroup` applies the same evaluation to an external
     organization-owned group. The unsafe public `hasActiveTrust` boolean was removed.
   - The separate external-customer policy remains unchanged. Disabling
     `requireTrustedOrganizationForB2b` continues to allow ordinary B2B user and published-group
     sharing without a trust-policy evaluation.
   - Focused tests cover all four outbound/inbound combinations, missing and expired policies,
     overdue policy and relationship reviews, suspension, ending, missing organizations, inactive
     or unverified parties, multi-organization recipients, external-group validation, the B2B
     opt-out, and denial before manage-access writes.
   - The Trusted Organizations help article now explains that ordinary B2B sharing requires
     current organizations, relationship review, and directional policies while the setting is on.

2. **General email acceptance revalidation - resolved and verified 2026-07-18**
   - Added `ExternalEmailAcceptancePolicyService`, which resolves the invitation email against the
     current non-temporary account immediately before acceptance, rejects inactive or deprovisioned
     accounts, and reuses `OrganizationExchangePolicyService` with the Exchange's stored sender
     organization and initiator.
   - Authenticated and no-auth `EXTERNAL_EMAIL` acceptance now call this boundary before recording
     an accepted recipient decision. An unresolved no-auth recipient is evaluated as an external
     customer; a current account is evaluated with its current active organization memberships.
     Rejection remains available because it activates no access.
   - Account substitution, missing sender evidence, and stale B2C or B2B policy fail closed with the
     same generic `409 CONFLICT` response. The People email path gains no trusted badge,
     organization-attested profile data, or membership assurance.
   - Focused tests cover unresolved B2C, newly resolved no-auth accounts, authenticated accounts,
     account substitution, inactive and deprovisioned accounts, stale policy, rejection, no partial
     decision update, generic resource responses, and the complete directional trust-policy matrix.

3. **Primary-recipient replacement activation gate - resolved and verified 2026-07-18**
   - The owner-recovery contract is restricted to `TRUSTED_PERSON` and `TRUSTED_GROUP`, matching the
     recovery UI and its purpose. Registered users, external email invitations, internal groups, and
     personal groups are rejected before recipient lookup, Share resolution, or mutation.
   - The resolver result must match the requested trusted selection type and contain the required
     person-resolution or group-validation evidence. A substituted type or missing evidence fails
     before the old primary binding or Share is changed.
   - Every replacement Share is now unconditionally `PENDING_APPROVAL`, the new primary binding is
     `PENDING`, and recipient sign-in is required. There is no non-trusted branch that can activate
     replacement access before acceptance.
   - Service and resource tests cover both allowed types, all four denied selection types,
     authorization denial, stale Exchange and recipient state, resolver substitution, pending Share
     and recipient state, and propagated mid-transaction failure for rollback.
   - The Manage access help article explicitly documents the trusted-only recovery contract and
     inactive-until-acceptance behavior.

4. **Independent trusted-participant acceptance - resolved and verified 2026-07-18**
   - The Product Contract decision above was recorded before participant activation changed.
   - Trusted person and trusted group participant Shares now start as `PENDING_APPROVAL`, their
     participant recipient bindings start as `PENDING`, and unresolved trusted participant Shares
     are excluded from primary-recipient and workflow bulk activation.
   - `GET /exchange-recipient-invitations` lists only invitations the signed-in user can decide.
     `POST /exchange-recipient-invitations/{recipientId}/decisions` accepts or rejects only that
     recipient resource. A person decision requires the exact invited user. A group decision
     requires a current eligible OWNER or MANAGER.
   - Acceptance locks and revalidates attestation, relationship, organizations, membership, and
     directional policy before activating only the participant Share. Rejection remains available
     when eligibility has lapsed and revokes only that Share.
   - V65 permits `PENDING`, `ACCEPTED`, or `REJECTED` participant bindings only for trusted person
     and trusted group selection types. Ordinary participant bindings remain `NOT_REQUIRED`.
   - Service, resource, migration, and frontend tests cover both trusted types, decision authority,
     tampering, stale state, trust invalidation, rejection, isolation from the primary decision,
     and propagated activation failure for transaction rollback.

5. **Frontend test certification - resolved and verified 2026-07-18**
   - `CopyableFieldLabel` now renders its existing actor and target copy controls with stable IDs,
     circular Fluent UI buttons, clipboard support, and a DOM fallback.
   - The focused audit detail suite passes all 10 tests. The full frontend suite passes all 204
     tests in 39 files with zero failures.

6. **Frontend lint and component-size certification - resolved and verified 2026-07-18**
   - Removed the unused access-panel import and corrected recipient initialization and effect
     dependencies without retaining stale recipient data.
   - Extracted the new-recipient state hook and trusted-organization dialog state hook.
   - Split Manage access into a state hook and focused people, access-permission, and Exchange
     settings components with co-located Fluent UI style files.
   - Final component counts are 130 lines for `ExchangeInitiationRecipientsTab.tsx`, 118 for
     `TrustedOrganizationsTab.tsx`, and 131 for `ExchangeAccessManagementDialog.tsx`. Every new
     child component is 144 lines or fewer.
   - ESLint over all 46 modified or untracked frontend TypeScript files passes with zero errors and
     zero warnings. Type checking and the full frontend suite also pass.

7. **Local configuration hygiene - resolved and verified 2026-07-18**
   - Added `/.claude/settings.local.json` to the repository root `.gitignore`.
   - `git check-ignore -v .claude/settings.local.json` resolves to that exact rule. The local file
     remains untouched and does not appear in `git status --short`.

### Mandatory audit-correction order completed

1. Read `AGENTS.md` and this plan in full.
2. Recorded and implemented independent trusted-participant acceptance.
3. Corrected frontend tests, lint, and component sizes.
4. Protected local configuration without staging or deleting it.
5. Ran the complete verification set and updated the reopened phases and handoff.

### Audit verification baseline

- Backend:
  `.\mvnw.cmd test -DskipFrontend=true "-Dkotlin.compiler.execution.strategy=in-process"` passed
  with 914 tests, 0 failures, 0 errors, and 166 skipped. Docker-backed PostgreSQL clean migration
  through V64 and concurrency contracts ran successfully.
- Frontend type checking: `npx.cmd tsc --noEmit` passed.
- Frontend tests: `npm.cmd test -- --run` completed with 197 passed and 3 failed, all in
  `AuditEventDetail.test.tsx`.
- Targeted ESLint completed with 1 error and 4 warnings as described in finding 6.
- Help-document size limits currently pass: Trusted Organizations article 149 lines, manage-access
  article 141 lines, `adminOperationsSection.tsx` 99 lines, `exchangesSection.tsx` 66 lines, and
  `helpDocsRegistry.tsx` 58 lines.
- The repository still contains `.claude/settings.local.json` as an untracked, non-ignored file.

### Audit correction verification update - 2026-07-18

Work completed:

- Recorded the independent acceptance Product Contract for trusted additional participants before
  changing any participant activation behavior.
- Resolved finding 1 through a dedicated current B2B trust-policy evaluation used by ordinary
  registered-user, general-email account resolution, organization-group, and manage-access paths.
- Removed the obsolete boolean-only trust gate and updated the affected help statement.
- Resolved finding 2 through current-account and current-policy revalidation immediately before
  authenticated and no-auth general-email acceptance.
- Resolved finding 3 by restricting primary-recipient recovery to trusted selections and making
  `PENDING_APPROVAL` the only replacement Share status.
- Resolved finding 4 with independent trusted-participant invitations, recipient-specific decision
  authority, acceptance-time trust revalidation, and isolation from primary and workflow bulk
  activation.
- Added the trusted-participant Requests UI and aligned the Trusted Organizations and workflow help
  articles with the independent-decision contract.
- Resolved finding 5 by restoring the audit detail copy controls and passing the focused and full
  frontend suites.
- Resolved finding 6 by removing all reported lint defects and splitting the audited oversized
  components into focused components and hooks.
- Resolved finding 7 by ignoring the exact local settings path without changing the local file.

Design decisions:

- `requireTrustedOrganizationForB2b = false` remains an explicit ordinary B2B opt-out. It bypasses
  trust-policy evaluation but does not change external-customer policy.
- A recipient with several active organization memberships is eligible when at least one target
  organization currently permits the sender-to-receiver direction. Internal membership in the
  sender organization remains an internal share.
- Missing relationship, organization, or directional policy state fails closed. Optional policy
  expiry and review dates are valid when absent; when present, their deadline must be in the future.
  An active relationship must have a future relationship review deadline.
- General email acceptance resolves the invited email against the current non-temporary account.
  A current account must be active and not deprovisioned. Current active memberships determine
  whether the existing policy boundary treats it as internal, B2C, or B2B. An unresolved no-auth
  placeholder remains an external-customer decision. Rejection does not run activation eligibility.
- Eligibility failures use a dedicated exception and generic conflict response for both resources;
  internal policy reasons are retained only as a server-side cause.
- Primary-recipient replacement is a trusted-recipient recovery operation, not a general recipient
  editing API. Registered people, external email invitations, internal groups, and personal groups
  retain their existing initiation and Add access contracts.
- The trusted selection request and resolver result must agree. Person recovery requires prepared
  resolution evidence and group recovery requires current group-validation evidence before any old
  recipient state is removed.
- Trusted participants make an independent decision on their own recipient binding. Their decision
  never accepts or rejects the Exchange or changes the primary recipient binding.
- Primary-recipient and workflow bulk Share activation exclude unresolved trusted participant
  Share IDs. Only the trusted-participant decision path can activate such a Share.
- Rejection does not require current trust eligibility because it cannot activate access. Acceptance
  repeats current trust and membership validation while the recipient row is locked.
- Participant invitation REST paths are recipient resources. The service derives and authorizes the
  owning Exchange from the locked recipient rather than accepting a client-supplied Exchange ID.
- Frontend state and API effects live in hooks; visual responsibilities live in focused components
  with separate Fluent UI style files.

Files changed for finding 1:

- Backend production:
  `OrganizationTrustExchangePolicyService.kt`, `OrganizationExchangePolicyService.kt`,
  `OrganizationTrustRelationshipService.kt`, and `OrganizationSettings.kt`.
- Backend tests:
  `OrganizationTrustExchangePolicyServiceTest.kt`, `OrganizationExchangePolicyUserTest.kt`,
  `OrganizationExchangePolicyGroupTest.kt`, and `ExchangeAccessManagementServiceTest.kt`.
- Help:
  `trustedOrganizationsAdministrationArticle.tsx`.
- Handoff:
  this implementation plan.

Files changed for finding 2:

- Backend production:
  `ExchangeRecipientEligibilityException.kt`, `ExternalEmailAcceptancePolicyService.kt`,
  `AppUserService.kt`, `ExchangeRecipientService.kt`, `ExchangeUpdateService.kt`,
  `ExchangeAcceptanceResource.kt`, and `NoAuthExchangeResource.kt`.
- Backend tests:
  `ExternalEmailAcceptancePolicyServiceTest.kt`, `ExchangeRecipientServiceTest.kt`,
  `ExchangeRecipientEligibilityResourceTest.kt`, and `ExchangeAuthorizationTest.kt`.
- Help:
  `trustedOrganizationsAdministrationArticle.tsx` and `workflowOrgSettingsArticle.tsx`.
- Handoff:
  this implementation plan.

Files changed for finding 3:

- Backend production:
  `ExchangeAccessManagementService.kt`.
- Backend tests:
  `ExchangeAccessManagementServiceTest.kt` and
  `ExchangePrimaryRecipientReplacementResourceTest.kt`.
- Help:
  `manageAccessArticle.tsx`.
- Handoff:
  this implementation plan.

Files changed for finding 4:

- Persistence and backend production:
  `V65__trusted_participant_acceptance.sql`, `ExchangeRecipientRepository.kt`,
  `ExchangeRecipientService.kt`, `ExchangeInitiationService.kt`, `ExchangeUpdateService.kt`,
  `ExchangeApprovalEventHandler.kt`, `ShareService.kt`, `ExchangeRecipientInvitationService.kt`,
  `ExchangeRecipientInvitationResource.kt`, `ExchangeRecipientInvitationDtoTransformer.kt`,
  `ExchangeRecipientDtos.kt`, and `RequestsResponses.kt`.
- Backend tests:
  `ExchangeRecipientServiceTest.kt`, `ExchangeRecipientInvitationResourceTest.kt`, and
  `AuditMigrationUpgradeContractTest.kt`.
- Frontend:
  `models.tsx`, `exchangeApi.ts`, `ExchangeListTabs.tsx`, and
  `trusted-participant-invitations/TrustedParticipantInvitations.tsx`, its style file, and its test.
- Help:
  `trustedOrganizationsAdministrationArticle.tsx` and `workflowOrgSettingsArticle.tsx`.
- Handoff:
  this implementation plan.

Files changed for finding 5:

- Frontend production:
  `CopyableFieldLabel.tsx` and `CopyableFieldLabelStyles.tsx`.
- Existing focused verification:
  `AuditEventDetail.test.tsx`.
- Handoff:
  this implementation plan.

Files changed for finding 6:

- Recipient surfaces:
  `ExchangeInitiation.tsx`, `ExchangeInitiationRecipientsTab.tsx`,
  `ExchangeInitiationRecipientsTab.types.ts`, `NewRecipient.tsx`, `useNewRecipient.ts`,
  `MyOrganizationRecipients.tsx`, `TrustedOrganizationsTab.tsx`, and
  `useTrustedOrganizationDialogs.ts`.
- Manage access:
  `ExchangeAccessPanel.tsx`, `ExchangeAccessManagementDialog.tsx`,
  `ExchangeAccessManagementContent.tsx`, `useExchangeAccessManagementDialog.ts`,
  `exchangeAccessManagementTypes.ts`, and the focused people-summary, access-permissions, and
  exchange-settings component and style files.
- Handoff:
  this implementation plan.

Files changed for finding 7:

- Local hygiene:
  `.gitignore`.
- The ignored `.claude/settings.local.json` was not changed or staged.
- Handoff:
  this implementation plan.

Commands and exact results:

- Focused backend command:
  `.\mvnw.cmd test -DskipFrontend=true "-Dkotlin.compiler.execution.strategy=in-process"
  "-Dtest=ExchangeAccessManagementServiceTest,OrganizationTrustExchangePolicyServiceTest,OrganizationExchangePolicyGroupTest,OrganizationExchangePolicyUserTest"`.
  Result: BUILD SUCCESS, 27 tests, 0 failures, 0 errors, 0 skipped.
- Full backend command:
  `.\mvnw.cmd test -DskipFrontend=true "-Dkotlin.compiler.execution.strategy=in-process"`.
  Result: BUILD SUCCESS, 932 tests, 0 failures, 0 errors, 166 skipped. Docker-backed PostgreSQL 17
  contracts ran and Flyway initialized an empty schema through V64 with 61 validated migrations.
- Frontend type checking: `npx.cmd tsc --noEmit` from `web-app`. Result: PASS, exit 0.
- Full frontend tests: `npm.cmd test -- --run` from `web-app`. Result: 197 passed and 3 failed,
  all in `AuditEventDetail.test.tsx`; finding 5 was still open at this checkpoint and is resolved
  by the final results below.
- Targeted ESLint:
  `npx.cmd eslint src/app/components/help-docs/sections/articles/trustedOrganizationsAdministrationArticle.tsx`.
  Result: PASS, 0 errors and 0 warnings.
- Help search matched 12 files for trusted-organization, B2B, sharing, sending, and receiving terms.
  Every matched article and section was read in full. The updated article is 149 lines,
  `adminOperationsSection.tsx` is 99 lines, and `helpDocsRegistry.tsx` is 58 lines.
- `git diff --check`: PASS after the final handoff update, with only line-ending warnings.
- Finding 2 focused backend command:
  `.\mvnw.cmd test -DskipFrontend=true "-Dkotlin.compiler.execution.strategy=in-process"
  "-Dtest=ExternalEmailAcceptancePolicyServiceTest,ExchangeRecipientServiceTest,ExchangeRecipientEligibilityResourceTest,ExchangeAuthorizationTest,OrganizationExchangePolicyUserTest,OrganizationTrustExchangePolicyServiceTest"`.
  Result: BUILD SUCCESS, 63 tests, 0 failures, 0 errors, 0 skipped.
- Full backend after finding 2:
  `.\mvnw.cmd test -DskipFrontend=true "-Dkotlin.compiler.execution.strategy=in-process"`.
  Result: BUILD SUCCESS, 945 tests, 0 failures, 0 errors, 166 skipped. Docker-backed PostgreSQL 17
  contracts ran and Flyway initialized an empty schema through V64 with 61 validated migrations.
- Frontend type checking after finding 2: `npx.cmd tsc --noEmit` from `web-app`.
  Result: PASS, exit 0.
- Full frontend tests after finding 2: `npm.cmd test -- --run` from `web-app`.
  Result: 197 passed and 3 failed, all in `AuditEventDetail.test.tsx`; finding 5 was still open at
  this checkpoint and is resolved by the final results below.
- Targeted ESLint after finding 2:
  `npx.cmd eslint src/app/components/help-docs/sections/articles/trustedOrganizationsAdministrationArticle.tsx src/app/components/help-docs/sections/articles/workflowOrgSettingsArticle.tsx`.
  Result: PASS, 0 errors and 0 warnings.
- Finding 2 help search matched 8 files for external-recipient, email-invitation, recipient
  acceptance, and B2B terms. Every matched article and section was read in full. The updated Trusted
  Organizations article is 149 lines, the workflow organization settings article is 114 lines,
  `adminOperationsSection.tsx` is 99 lines, `exchangesSection.tsx` is 66 lines, and
  `helpDocsRegistry.tsx` is 58 lines.
- `git diff --check` after finding 2: PASS with only line-ending warnings. The prohibited-character
  scan over every finding 2 authored or updated file found no em dash, arrow, or warning symbol.
- Finding 3 focused backend command:
  `.\mvnw.cmd test -DskipFrontend=true "-Dkotlin.compiler.execution.strategy=in-process"
  "-Dtest=ExchangeAccessManagementServiceTest,ExchangePrimaryRecipientReplacementResourceTest"`.
  Result: BUILD SUCCESS, 11 tests, 0 failures, 0 errors, 0 skipped.
- Full backend after finding 3:
  `.\mvnw.cmd test -DskipFrontend=true "-Dkotlin.compiler.execution.strategy=in-process"`.
  Result: BUILD SUCCESS, 951 tests, 0 failures, 0 errors, 166 skipped. Docker-backed PostgreSQL 17
  contracts ran, validated 61 migrations, and initialized an empty schema through V64.
- Frontend type checking after finding 3: `npx.cmd tsc --noEmit` from `web-app`.
  Result: PASS, exit 0. The sandboxed attempt failed with Node `EPERM` while resolving
  `C:\Users\Black`; the required rerun outside that filesystem sandbox passed.
- Targeted ESLint after finding 3:
  `npx.cmd eslint src/app/components/help-docs/sections/articles/manageAccessArticle.tsx`.
  Result: PASS, 0 errors and 0 warnings.
- Finding 3 help search matched seven files for primary-recipient, replacement, and Trusted
  Organization terms. Every matched article and section was read in full. The updated Manage access
  article is 143 lines, `adminOperationsSection.tsx` is 99 lines, and `helpDocsRegistry.tsx` is
  58 lines.
- Focused `git diff --check` for the four finding 3 implementation and documentation files:
  PASS with only line-ending warnings.
- Finding 4 focused backend command:
  `.\mvnw.cmd test -DskipFrontend=true "-Dkotlin.compiler.execution.strategy=in-process"
  "-Dtest=ExchangeRecipientServiceTest,ExchangeRecipientInvitationResourceTest,AuditMigrationUpgradeContractTest"`.
  Result: BUILD SUCCESS, 32 tests, 0 failures, 0 errors, 0 skipped.
- Full backend after finding 4:
  `.\mvnw.cmd test -DskipFrontend=true "-Dkotlin.compiler.execution.strategy=in-process"`.
  Result: BUILD SUCCESS, 961 tests, 0 failures, 0 errors, 166 skipped. Testcontainers PostgreSQL 17
  validated 62 migrations and initialized an empty schema through V65.
- Finding 4 focused frontend command:
  `npm.cmd test -- --run src/app/exchanges/components/exchange-list/trusted-participant-invitations/TrustedParticipantInvitations.test.tsx`.
  Result: 1 file and 2 tests passed.
- Finding 5 focused frontend command:
  `npm.cmd test -- --run src/app/audit/__tests__/AuditEventDetail.test.tsx`.
  Result: 1 file and 10 tests passed.
- Finding 6 stale-state command:
  `npm.cmd test -- --run src/app/settings/trusted-organizations-tab/useTrustedOrganizationDialogs.test.ts`.
  Result: 1 file and 2 tests passed. The tests prove exact-version confirmation and stale
  relationship rejection without redirecting the action.
- Final frontend type checking:
  `npx.cmd tsc --noEmit` from `web-app`. Result: PASS, exit 0.
- Final full frontend tests:
  `npm.cmd test -- --run` from `web-app`. Result: 39 files and 204 tests passed, zero failures.
- Final targeted ESLint built its target list from every existing modified or untracked `.ts` and
  `.tsx` file and invoked `web-app/node_modules/.bin/eslint.cmd` over all 46 files. Result: PASS,
  zero errors and zero warnings. The sandboxed Node attempt failed with `EPERM` while resolving
  `C:\Users\Black`; the required outside-sandbox rerun passed.
- Final component counts:
  `ExchangeInitiationRecipientsTab.tsx` 130,
  `TrustedOrganizationsTab.tsx` 118,
  `ExchangeAccessManagementDialog.tsx` 131,
  `ExchangeAccessManagementContent.tsx` 101,
  `PeopleSummaryPanel.tsx` 144,
  `AccessPermissionsPanel.tsx` 109,
  `ExchangeSettingsPanel.tsx` 95, and `NewRecipient.tsx` 94.
- Finding 4 help search matched seven files for trusted-participant, recipient-invitation,
  acceptance, and workflow terms. Every match was read in full. Final counts are
  `trustedOrganizationsAdministrationArticle.tsx` 149,
  `workflowOrgSettingsArticle.tsx` 115,
  `manageAccessArticle.tsx` 143,
  `adminOperationsSection.tsx` 99,
  `exchangesSection.tsx` 66, and `helpDocsRegistry.tsx` 58.
- `git check-ignore -v .claude/settings.local.json`:
  PASS and resolves to `.gitignore:53:/.claude/settings.local.json`. The file is absent from
  `git status --short`.

Remaining risks:

- No post-implementation audit finding remains open.
- Concurrent primary-recipient replacement by two owners can still make one transaction fail on
  the single-primary database invariant. No partial update survives transaction rollback.

Next-session starting point:

- Continue from the manual browser certification update below. Do not use the older statement that
  no correction remains.

### Manual browser certification update - 2026-07-18

Work completed:

- Created two isolated local test accounts and organizations through the browser UI, then activated
  only those two organization rows as local fixtures because no platform-admin test credentials
  were available.
- Enabled organization discovery, created and accepted a trust request, configured all five
  directional policy controls independently for both parties, and confirmed the relationship
  reached `ACTIVE`.
- Verified exact-email trusted-member resolution from the receiver organization to the sender
  organization. The UI showed the attested member, organization, expiry, and membership-verification
  label only after the explicit verification action.
- Created `Trusted B2B Browser Certification` as an ordinary trusted B2B Exchange. The primary
  recipient remained behind required sign-in acceptance.
- Verified Manage access renders the trusted primary recipient and the additional-participant
  entry point. A distinct trusted partner member is still required to certify participant
  invitation acceptance and isolation.
- Corrected three live HTTP 500 failures caused by erased collection element types:
  `GET /organization-trust-relationships`, `POST /organization-directory-searches`, and
  `GET /exchange-recipient-invitations` now wrap their DTO lists in typed `GenericEntity`
  responses. Browser retries proved the trust list, discovery results, and empty invitation list
  all load without the serialization failure.
- Added focused resource tests that assert each list response preserves its serializable DTO
  element type.
- Corrected two broken frontend relative imports found by the Vite browser runtime in
  `ExchangeSettingsPanel.tsx` and `AccessPermissionsPanel.tsx`.
- Removed the duplicate `boxSizing` property reported by the production Vite build in
  `WorkflowsTabStyles.tsx`.
- Searched help documentation for trusted-organization, trusted-participant, and organization-trust
  terms. Seven files matched; every matched section and article was read in full. The runtime fixes
  restore the documented behavior, so no help text changed.

Design decisions:

- The typed-response correction stays in the thin resource layer because it is HTTP serialization
  metadata only. Business queries and decisions remain in the existing application-scoped
  services, and no resource accesses a repository.
- Local organization activation was test-fixture setup only. It changed only the two newly created
  local rows and is not a compatibility path, migration, backfill, fallback read, or product code.
- The browser certification cannot treat Gmail plus aliases as evidence that organization lookup is
  isolated. PostgreSQL proved the two accounts have separate user IDs and exactly one membership
  each, while the pre-authentication picker still returned FNB and both new organizations for
  either email. This is an open privacy and authorization finding.
- No AWS service was added. The user explicitly accepted the temporary root AWS session for local
  SES verification emails only.

Files changed in this browser-certification session:

- Backend production:
  `OrganizationTrustRelationshipResource.kt`, `OrganizationDirectorySearchResource.kt`, and
  `ExchangeRecipientInvitationResource.kt`.
- Backend tests:
  `OrganizationTrustResourceContractTest.kt` and
  `ExchangeRecipientInvitationResourceTest.kt`.
- Frontend:
  `ExchangeSettingsPanel.tsx`, `AccessPermissionsPanel.tsx`, and `WorkflowsTabStyles.tsx`.
- Handoff:
  this implementation plan.

Commands and exact results:

- Focused backend:
  `.\mvnw.cmd test -DskipFrontend=true "-Dtest=OrganizationTrustResourceContractTest,ExchangeRecipientInvitationResourceTest" "-Dkotlin.compiler.execution.strategy=in-process"`.
  Result: BUILD SUCCESS, 9 tests, 0 failures, 0 errors, 0 skipped.
- Full backend:
  `.\mvnw.cmd test -DskipFrontend=true "-Dkotlin.compiler.execution.strategy=in-process"`.
  Result: BUILD SUCCESS, 964 tests, 0 failures, 0 errors, 166 skipped.
  Testcontainers PostgreSQL 17 validated all 62 migrations and initialized an empty schema through
  V65.
- Frontend type check: `npx.cmd tsc --noEmit` from `web-app`.
  Result: PASS, exit 0.
- Full frontend tests: `npm.cmd test -- --run` from `web-app`.
  The sandboxed attempt failed with Node `EPERM` while resolving `C:\Users\Black`; the required
  outside-sandbox rerun passed all 39 files and 204 tests.
- Targeted ESLint:
  `npx.cmd eslint src/app/exchanges/components/exchange-access-management-dialog/exchange-settings-panel/ExchangeSettingsPanel.tsx src/app/exchanges/components/exchange-access-management-dialog/access-permissions-panel/AccessPermissionsPanel.tsx src/app/settings/workflows-tab/WorkflowsTabStyles.tsx`.
  Result: PASS, zero errors and zero warnings.
- Production frontend build: `npm.cmd run build` from `web-app`.
  The first successful build found the duplicate `boxSizing` property. After correction, the rerun
  transformed 3455 modules and completed successfully in 12.66 seconds. Only the existing large
  chunk advisory remains.
- Help search:
  `rg -il "trusted organization|trusted participant|organization trust" web-app/src/app/components/help-docs/sections`.
  Result: seven files matched and every match was read in full; no inaccurate statement was found.
- Component and help sizes:
  `ExchangeSettingsPanel.tsx` 95 lines, `AccessPermissionsPanel.tsx` 109,
  `trustedOrganizationsAdministrationArticle.tsx` 149, `manageAccessArticle.tsx` 143,
  `workflowOrgSettingsArticle.tsx` 115, and `helpDocsRegistry.tsx` 58. All applicable limits pass.
- `git diff --check`: PASS with only expected LF-to-CRLF warnings.
- Prohibited-character scan over every file authored or updated in this browser session found no
  em dash, arrow, or emoji.
- `git check-ignore -v .claude/settings.local.json`: PASS and resolves to
  `.gitignore:53:/.claude/settings.local.json`.

Open findings and remaining risks:

1. **Trusted additional-participant browser certification - open.**
   Create a distinct active member in the trusted partner organization, add that member through
   Manage access, prove the Share remains inactive before invitation acceptance, then accept and
   reject separate invitations while proving neither decision changes the primary recipient or
   Exchange decision.
2. **Trusted-primary replacement browser certification - open.**
   Exercise replacement with a distinct verified trusted person or group, prove the old invitation
   is withdrawn, and prove the replacement Share remains inactive until its primary-recipient
   acceptance gate. Include stale trust, policy denial, tampering, and rollback checks.
3. The local audit archiver still reports a Windows-only invalid-path error because audit stream IDs
   containing `:` are used in local archive paths. This is unrelated to Trusted Organizations but
   remains a local runtime defect.

Next-session starting point:

- Begin with open finding 1, trusted additional-participant browser certification. Use a distinct
  active member in the trusted partner organization and prove pending, accepted, and rejected
  invitation isolation without changing the primary recipient or Exchange decision.
- Preserve the local browser fixtures and `Trusted B2B Browser Certification` Exchange. Do not
  commit `.claude/settings.local.json`.

### Sign-in lookup privacy correction update - 2026-07-18

Work completed:

- Resolved the pre-authentication organization-picker privacy defect. `SignInResource` now extracts
  HTTP inputs, delegates to the application-scoped `SignInLookupService`, and maps typed service
  outcomes. It no longer performs account, membership, organization, provider, rate-limit, incident,
  or lookup-policy decisions.
- Exact active registered accounts receive organization choices only from their current active
  organization memberships. Inactive organizations, inactive memberships, inactive accounts, and
  deprovisioned accounts cannot project organization choices.
- A client-supplied organization identifier must match one of those current active memberships.
  Malformed, guessed, foreign, and stale identifiers fail with the same generic response before
  provider lookup or business-state mutation.
- A consumer email domain cannot enumerate organizations whose contact email shares that domain.
  When no eligible account membership exists, domain-based IdP routing is allowed only for one
  unambiguous active configured organization and returns no organization name or organization ID.
- Added focused service tests for positive exact-membership selection, configured IdP routing,
  consumer-domain non-enumeration, valid-ID tampering, invalid input, rate limiting, inactive
  organization state, deprovisioned account state, and no business-state mutation.
- Searched help documentation for sign-in, organization-picker, identity-provider, SSO, and email
  domain terms. Twelve files matched and every matched section and article was read in full.
  `identitySection.tsx` now documents the non-enumerating contract and current-membership validation.

Design decisions:

- Pre-authentication organization options are account-membership projections, not email-domain
  projections. Exact account lookup is required before any organization name or organization ID can
  appear.
- Domain matching remains available only to route one unambiguous configured organization to its
  external IdP. That response uses `NO_ORG` and contains no organization option, so consumer and
  shared domains cannot become an organization directory.
- Selected organization validation uses the active membership service boundary and organization
  lookups use the identity-policy service boundary. The new service accesses no repository and the
  resource remains an HTTP adapter.
- Lookup denial records generic audit and security evidence without including the submitted email
  or selected organization ID in incident details.
- The lookup is read-only. Denial, tampering, stale state, and rate limiting perform no user,
  membership, organization, or provider-configuration mutation.
- No compatibility path, fallback read, backfill, dual write, migration, AWS service, or paid cloud
  resource was added.

Files changed:

- Backend production:
  `SignInLookupService.kt` and `SignInResource.kt`.
- Backend tests:
  `SignInLookupServiceTest.kt`.
- Help:
  `identitySection.tsx`.
- Handoff:
  this implementation plan.

Commands and exact results:

- Focused backend:
  `.\mvnw.cmd test -DskipFrontend=true "-Dkotlin.compiler.execution.strategy=in-process"
  "-Dtest=SignInLookupServiceTest,OrganizationOAuthPolicyTest"`.
  Result: BUILD SUCCESS, 11 tests, 0 failures, 0 errors, 0 skipped.
- Full backend:
  `.\mvnw.cmd test -DskipFrontend=true "-Dkotlin.compiler.execution.strategy=in-process"`.
  Result: BUILD SUCCESS, 973 tests, 0 failures, 0 errors, 166 skipped.
  Docker-backed PostgreSQL 17 validated all 62 migrations and initialized an empty schema through
  V65.
- Frontend type check: `npx.cmd tsc --noEmit` from `web-app`.
  Result: PASS, exit 0.
- Full frontend tests: `npm.cmd test -- --run` from `web-app`.
  The sandboxed attempt failed with Node `EPERM` while resolving `C:\Users\Black`; the required
  outside-sandbox rerun passed all 39 files and 204 tests.
- Targeted ESLint:
  `npx.cmd eslint src/app/components/help-docs/sections/identitySection.tsx`.
  The sandboxed attempt failed with the same Node `EPERM`; the required outside-sandbox rerun passed
  with zero errors and zero warnings.
- Help search matched 12 files and every match was read in full. `identitySection.tsx` is 126 lines,
  below the 300-line section limit, and `helpDocsRegistry.tsx` remains 58 lines.
- Prohibited-character scan over every file authored or updated for this correction: PASS.
- `git check-ignore -v .claude/settings.local.json`: PASS and resolves to
  `.gitignore:53:/.claude/settings.local.json`.
- `git diff --check`: PASS with only expected LF-to-CRLF warnings.

Remaining risks:

- The privacy defect is resolved and removed from the open list after focused, full-suite, and clean
  PostgreSQL verification.
- The two Trusted Organizations browser-certification findings listed above remain open.
- The unrelated Windows audit archive-path defect remains open.

Next-session starting point:

- Perform the trusted additional-participant browser certification described in open finding 1.
  Preserve the existing browser fixtures and prove that accepting or rejecting one participant
  invitation changes only that participant Share and recipient binding.

### Exit criteria for closing this correction

- Ordinary B2B People, registered-user, group, and manage-access paths enforce current compatible
  sender and receiver policy through service boundaries.
- General email acceptance rechecks current account, membership, organization, trust, and policy
  facts without exposing trusted assurance or directory information.
- Replacing a primary recipient never activates access before the chosen acceptance gate.
- Trusted participant behavior is unambiguous and matches the Product Contract, backend state,
  frontend language, help documentation, and tests.
- Backend tests, frontend tests, type checking, targeted ESLint, and clean database initialization
  all pass with no unrecorded exception.
- Updated frontend components comply with the component-size, style-file, responsive, ID, circular
  button, and strong-typing rules in `AGENTS.md`.
- Local machine configuration cannot be accidentally staged.

## Pre-commit Review Correction - 2026-07-17

The 2026-07-17 pre-commit review compared the full working tree with this plan and `AGENTS.md`.
It found that Phase 5 is substantially implemented even though this plan still described it as not
started. It also found open correctness and quality defects in work previously recorded as complete.
Under the phase handoff protocol, Phases 1 through 4 are therefore reopened until their assigned
findings are corrected and verified.

The working tree contains 161 modified, deleted, or untracked entries spanning Phases 0 through 5.
The implementation remains one non-deployable clean-cutover unit. Do not present or deploy the
current tree as a completed Trusted Organizations feature, and do not commit
`.claude/settings.local.json`, which is local machine configuration unrelated to the feature.

### Open pre-commit findings

1. **Destructive action routing**
   - `web-app/src/app/settings/trusted-organizations-tab/useTrustedOrganizations.ts` routes
     `RESUME` to `terminateOrganizationTrust` when `currentOrganizationSuspensionId` is absent.
   - Replace the fallback with an exhaustive action switch. `END` must be the only action that can
     call termination, and an invalid `RESUME` state must fail without mutation.
2. **Action dialogs are not bound to the relationship that opened them**
   - `TrustedOrganizationsTab.tsx` confirms an action against the current `trust.selected` value.
     A notification refresh or selection change can therefore redirect an open dialog to a
     different relationship.
   - Store the action together with the relationship ID and expected version captured when the
     dialog opens. Confirmation must use that immutable target and close or fail if it is stale.
3. **Stale organization-scoped frontend responses**
   - Relationship and policy requests in `useTrustedOrganizations.ts` have no request sequence,
     cancellation, or active-organization snapshot.
   - A response started under the previous active organization can repopulate relationship or
     policy state after an organization switch. Add stale-response protection to relationship and
     policy requests and add hook tests for organization switches and rapid relationship changes.
4. **Trusted-person UI is visible but cannot submit**
   - Exact-email resolution, confirmation, and expiry UI exist, but the initiation contract and
     request builder still support only `TRUSTED_GROUP`.
   - `ExchangeInitiation.tsx` requires a published group for every Trusted Organization selection.
     A successfully resolved person therefore reaches a validation dead end.
   - Phase 6 owns the complete fix. Until the `TRUSTED_PERSON` selection is wired end to end, the UI
     must not imply that a verified person is a selectable Exchange recipient.
5. **Concurrent suspension clearing can miss group reconciliation**
   - `OrganizationTrustRelationshipService.resume` clears a suspension and then queries for other
     active suspensions without serializing resume operations on the relationship.
   - When both parties clear their suspensions concurrently, each transaction can observe the
     other suspension and skip reconciliation. After both commits, no active suspension remains,
     but neither transaction reconciles eligible group access.
   - Serialize resume operations on the relationship row or implement an equivalent transactionally
     safe last-suspension mechanism. Add a PostgreSQL concurrency contract test.
6. **Trusted-group acceptance maps expected invalidation to HTTP 500**
   - `ExchangeRecipientService.recordPrimaryDecision` revalidates current trust and can throw an
     organization-trust exception.
   - `ExchangeAcceptanceResource` catches it through the generic branch and returns an internal
     server error. Map current-trust invalidation to the intended fail-closed client response and
     add a resource or service contract test.
7. **The database does not enforce the ExchangeRecipient Share binding invariant**
   - V58 created independent foreign keys for `exchange_id` and `direct_share_id`, but the database
     does not prove that the Share is direct, non-owner, and belongs to the same Exchange.
   - Service validation is present but is not sufficient for the target database invariant. Add the
     constraint in the next forward migration after V62. Do not edit V58.
8. **Frontend lint and handoff hygiene**
   - Targeted ESLint reports an unused `shorthands` import in
     `TrustedOrganizationRequestDialogStyles.tsx`.
   - It also reports hook dependency warnings for the notification and selected-policy effects in
     `useTrustedOrganizations.ts`. Correct the dependencies as part of the stale-response fix.
   - Remove the stray standalone `+` previously present in this plan before committing.

### Pre-commit findings resolution - 2026-07-17

All eight pre-commit findings are now fixed and verified. Docker was available in this session, so
the previously outstanding Docker-backed migration and concurrency verification ran successfully.

1. **Resolved.** `useTrustedOrganizations.act` now uses an exhaustive action switch. `END` is the
   only action that calls `terminateOrganizationTrust`. `RESUME` without
   `currentOrganizationSuspensionId` rejects without any mutation and surfaces an error.
2. **Resolved.** `TrustedOrganizationsTab` stores the action together with the exact relationship
   snapshot (including its `version`) captured when the dialog opens. Confirmation re-checks that
   the captured relationship still exists with the same version and fails closed otherwise, so a
   background refresh or selection change cannot redirect the action.
3. **Resolved.** Relationship and policy fetches in `useTrustedOrganizations` now carry a request
   sequence and an active-organization snapshot (`activeOrganizationRef`); responses are applied
   only when they are the newest request for the still-current active organization. Notification
   and policy effects use correct dependencies. New hook tests cover organization switches, stale
   policy responses, notification refreshes, and the missing-suspension resume guard.
4. **Resolved by Phase 6.** `TRUSTED_PERSON` is now a first-class discriminated selection end to
   end; a verified member is a submittable recipient (see Phase 6).
5. **Resolved.** `OrganizationTrustRelationshipService.resume` takes the per-relationship advisory
   lock (`lockOrganizations`) before clearing the suspension and counting remaining active
   suspensions, serializing concurrent resumes. `OrganizationTrustResumeConcurrencyContractTest`
   proves against real PostgreSQL that exactly one of two concurrent resumes observes zero
   remaining suspensions.
6. **Resolved.** Trusted-recipient trust revalidation now runs only on acceptance, so rejection
   stays permitted when activation eligibility has lapsed (documented decision). Trust
   invalidation during acceptance is mapped to `409 CONFLICT` with a generic message in
   `ExchangeAcceptanceResource` instead of the generic 500. Service contract tests cover accept
   fail-closed and reject-permitted for both trusted group and trusted person.
7. **Resolved.** Forward migration `V63__exchange_recipient_share_binding_invariant.sql` adds a
   trigger proving the bound Share is direct, non-owner, and on the same Exchange. V58 was not
   edited. `AuditMigrationUpgradeContractTest` now asserts current version 63 and that foreign,
   inherited (non-direct), and owner Share bindings are rejected with SQLSTATE 23514.
8. **Resolved.** Removed the unused `shorthands` import; hook dependency warnings are corrected as
   part of finding 3. `.claude/settings.local.json` was left untracked. The later
   post-implementation audit found that it is not actually ignored and reopened this handoff item.
   No stray `+` line remains in this plan.

### Verification recorded by the pre-commit review

- Full backend command:
  `.\mvnw.cmd test -DskipFrontend=true "-Dkotlin.compiler.execution.strategy=in-process"`.
  Result: 735 tests ran, 733 passed, and 2 PostgreSQL Testcontainers contract tests errored because
  no Docker environment was available. There were no assertion failures.
- The two PostgreSQL contract tests were retried outside the filesystem sandbox and failed for the
  same unavailable Docker environment. V62 clean initialization and upgrade certification remain
  outstanding until Docker is available.
- Focused Phase 5 backend tests:
  `ExternalIdentityResolutionServiceTest`, `ExternalIdentityLookupGuardServiceTest`, and
  `TrustedRecipientValidationServiceTest`. Result: 15 passed, 0 failures, 0 errors.
- `npx.cmd tsc --noEmit` in `web-app`: PASS.
- Full frontend command `npm.cmd test -- --run`: 191 passed and 3 failed. The three failures remain
  the pre-existing `AuditEventDetail.test.tsx` copy-button failures recorded in earlier phases.
- Focused trust frontend tests: 3 files and 7 tests passed.
- Targeted ESLint for the trust administration, trusted recipient, trust client, and selection
  builder: FAIL, with 1 unused-import error and 2 React hook dependency warnings described above.
- `git diff --check`: PASS.

### Mandatory next-session order

This order was completed on 2026-07-17. All eight pre-commit findings are fixed and verified, Phase
5 is certified, and Phase 6 is complete. See the "Pre-commit findings resolution - 2026-07-17"
subsection above, the per-phase status lines, and the "Consolidated Verification and Handoff -
2026-07-17" section for details. The next work is Phase 7; do not begin the Phase 7 obsolete-model
deletion until all ordinary sharing callers are ready for the clean cutover.

Historical order (all items done):

1. Re-read `AGENTS.md` and this plan in full.
2. Fix findings 1 through 3 before adding more administration UI behavior.
3. Fix the concurrent resume and acceptance error-mapping findings and add their regression tests.
4. Add the ExchangeRecipient database invariant in the next forward migration after V62 and extend
   the PostgreSQL migration contract.
5. Fix targeted ESLint and ensure `.claude/settings.local.json` is excluded from the commit.
6. Complete and certify Phase 5, including full output-channel redaction review and Docker-backed
   migration verification.
7. Continue with Phase 6 so the visible trusted-person resolver becomes a real discriminated
   recipient selection rather than a dead end.
8. Do not begin Phase 7 deletion until Phase 6 is complete and all ordinary sharing callers are
   ready for the clean cutover.

## Target Domain Model

### OrganizationTrustRelationship

One row represents one relationship generation between an unordered organization pair. A rejected
or ended relationship remains historical. A later request creates a new relationship ID so old
Exchange attestations can never become current again merely because the organizations reconnect.

Required fields:

- `id`
- `organizationAId`
- `organizationBId`
- `requestedByOrganizationId`
- `status`: `PENDING`, `ACTIVE`, `REJECTED`, `WITHDRAWN`, `EXPIRED`, or `ENDED`
- `requestMessage`
- `requestedAt`
- `requestExpiresAt`
- `activatedAt`
- `rejectedAt`
- `withdrawnAt`
- `expiredAt`
- `endedAt`
- `reviewDueAt`
- `version` for optimistic locking
- actor IDs and reason fields for the latest lifecycle transition

Constraints:

- Organizations are distinct.
- Organization IDs are stored in canonical order.
- At most one `PENDING` or `ACTIVE` relationship exists per unordered organization pair.
- The requesting organization is one of the two parties.
- Status and timestamp combinations are valid.
- Rejected, withdrawn, expired, and ended relationships are immutable except for retention
  operations.

Valid transitions:

| Current state | Actor | Operation | Result |
|---|---|---|---|
| No current relationship | Requesting organization | Request | `PENDING` |
| `PENDING` | Requested organization | Accept | `ACTIVE` |
| `PENDING` | Requested organization | Reject | `REJECTED` |
| `PENDING` | Requesting organization | Withdraw | `WITHDRAWN` |
| `PENDING` | System scheduler | Expire | `EXPIRED` |
| `ACTIVE` | Either organization | End | `ENDED` |

Suspension does not change relationship status. Re-request after any terminal state creates a new
relationship generation.

`SUSPENDED` is not a relationship status. Suspension is per organization, because both parties may
suspend independently and each party may clear only its own suspension.

### OrganizationTrustSuspension

Required fields:

- `id`
- `relationshipId`
- `suspendingOrganizationId`
- `reason`
- `suspendedByAppUserId`
- `suspendedAt`
- `clearedByAppUserId`
- `clearedAt`

At most one active suspension exists per relationship and suspending organization. A relationship
is effectively suspended while either party has an active suspension.

### OrganizationTrustPartyPolicy

Each relationship has one policy row owned by each organization.

Required fields:

- `id`
- `relationshipId`
- `policyOwnerOrganizationId`
- `allowExchangesToPartner`
- `allowExchangesFromPartner`
- `allowPartnerMemberResolution`
- `allowPartnerGroupDiscovery`
- `shareMemberDisplayName`
- `expiresAt`
- `reviewDueAt`
- `revision`
- `updatedByAppUserId`
- `updatedAt`

Policy is typed. Do not store policy as arbitrary JSON.

An Exchange from organization A to organization B requires:

- A policy permits Exchanges to B.
- B policy permits Exchanges from A.
- The relationship is active and not effectively suspended or expired.
- Both organizations are active and verified.
- The target operation is enabled by B policy.

Only the policy-owning organization can modify its row. Every update increments `revision`.

### ExchangeRecipient

Shares remain the only source of access. `ExchangeRecipient` records why a direct Share exists and
who may make the primary recipient decision.

Required fields:

- `id`
- `exchangeId`
- `directShareId`
- `purpose`: `PRIMARY` or `PARTICIPANT`
- `selectionType`: `REGISTERED_USER`, `EXTERNAL_EMAIL`, `INTERNAL_GROUP`, `PERSONAL_GROUP`,
  `TRUSTED_PERSON`, or `TRUSTED_GROUP`
- `targetOrganizationId`
- `acceptanceStatus`: `NOT_REQUIRED`, `PENDING`, `ACCEPTED`, or `REJECTED`
- `acceptedOrRejectedByAppUserId`
- `acceptedOrRejectedAt`
- `createdAt`

Constraints:

- The Share is a direct Share on the same Exchange.
- The initiator OWNER Share cannot be an ExchangeRecipient.
- Exactly one primary recipient exists per Exchange in the first release.
- Participants cannot decide Exchange acceptance.
- A trusted person uses a direct USER Share.
- A trusted group uses a direct PRINCIPAL_GROUP Share.

### ExternalIdentityResolution

A successful exact-email resolution is a short-lived, single-purpose server-side record.

Required fields:

- `id`
- `actorAppUserId`
- `callerOrganizationId`
- `targetOrganizationId`
- `relationshipId`
- `senderPolicyRevision`
- `targetPolicyRevision`
- `resolvedAppUserId`
- `resolvedMembershipId`
- `normalizedEmail`
- `displayNameSnapshot`
- `createdAt`
- `expiresAt`
- `consumedAt`
- `consumedByExchangeId`

The resolution is consumed with row locking in the Exchange initiation transaction. Consumption
rolls back when Exchange creation rolls back.

Unsuccessful lookups do not create resolution rows. Audit and incident records never contain the
raw email. Abuse-correlation keys use a keyed hash, not the email itself.

### ExchangeRecipientAttestation

Attestation is attached to `ExchangeRecipient`, not directly to Exchange and not to inherited group
Shares.

Required fields:

- `id`
- `exchangeRecipientId`
- `relationshipId`
- `callerOrganizationId`
- `targetOrganizationId`
- `senderPolicyRevision`
- `targetPolicyRevision`
- `subjectType`: `PERSON` or `GROUP`
- `subjectAppUserId`
- `subjectMembershipId`
- `subjectGroupId`
- `invitedEmailSnapshot`
- `displayNameSnapshot`
- `organizationNameSnapshot`
- `verifiedAt`
- `verificationExpiresAt`
- `acceptanceVerifiedAt`

Historical snapshots explain what was verified. Authorization always uses current entities,
current Shares, and current policy. Current verification state is computed from live data when the
recipient is projected or revalidated. It may be cached for display, but a cached value is never an
authorization input.

## Authorization Model

### Joint relationship authorization

A trust relationship has two controlling organizations, while the current resource provider model
has one owner. Do not assign the relationship to one artificial owner and do not expand
`OwnerContext` solely for this feature.

For every trust operation:

1. Require a validated active organization.
2. Authorize the requested action against `ResourceRef.organization(activeOrganizationId)`.
3. Load the relationship through `OrganizationTrustRelationshipService`.
4. Prove that the active organization is a relationship party.
5. Apply side-specific transition or policy rules.

Policy rows are owned by one organization and are authorized against that organization.

### New capabilities and actions

Add capabilities:

- `ORG_TRUST_READ`
- `ORG_TRUST_REQUEST`
- `ORG_TRUST_DECIDE`
- `ORG_TRUST_POLICY_MANAGE`
- `ORG_TRUST_SUSPEND`
- `EXTERNAL_IDENTITY_RESOLVE`
- `EXTERNAL_GROUP_DISCOVER`
- `EXCHANGE_ACCEPT`

Add matching actions. No role receives a new capability implicitly.

Recommended grants:

- ORG_OWNER and ORG_ADMIN receive all organization trust administration capabilities.
- Organization roles that can initiate Exchanges receive identity-resolution and external-group
  discovery capabilities.
- Assignable recipient Share roles receive `EXCHANGE_ACCEPT`, but the acceptance service also
  requires an `ExchangeRecipient` with `purpose = PRIMARY`.
- APPLICATION and SERVICE_ACCOUNT principals receive none by default.
- APP_ADMIN does not receive organization trust capabilities without membership in the active
  organization.

Frontend visibility uses effective capabilities. Backend authorization remains mandatory.

## REST API

All resources are thin HTTP adapters. Services perform authorization, validation, state
transitions, persistence coordination, audit, rate limiting, and projection.

### Organization discovery

```text
POST /organization-directory-searches
```

- Requires `ORG_TRUST_REQUEST` in the active organization.
- Uses a JSON body containing `query`; search input is not placed in the URL.
- Returns active, verified, discoverable organizations only.
- Excludes the active organization and pairs with a current pending or active relationship.
- Requires a minimum query length and returns a capped result set.
- `discoverableForTrustRequests` is separate from Exchange sharing settings.
- Exact registration-number matches may be supported, but registration numbers must not be
  returned unless product policy classifies them as public.

### Relationships

```text
POST /organization-trust-relationships
GET /organization-trust-relationships
GET /organization-trust-relationships/{relationshipId}
POST /organization-trust-relationships/{relationshipId}/decisions
POST /organization-trust-relationships/{relationshipId}/withdrawals
POST /organization-trust-relationships/{relationshipId}/suspensions
DELETE /organization-trust-relationships/{relationshipId}/suspensions/{suspensionId}
POST /organization-trust-relationships/{relationshipId}/terminations
```

- Creation derives the requesting organization from active organization context.
- Decision bodies contain `ACCEPT` or `REJECT`; only the requested organization may decide.
- Only the requesting organization may withdraw a pending request.
- Suspension creation and deletion apply only to the active organization's suspension.
- Either organization may terminate an active relationship.
- Re-requesting after rejection or ending uses `POST /organization-trust-relationships` and creates
  a new relationship generation after cooldown and policy checks.

### Policies

```text
GET /organization-trust-relationships/{relationshipId}/policies
PATCH /organization-trust-relationships/{relationshipId}/policies/{policyId}
```

The response can show both parties' effective Exchange directions, but only fields intentionally
shared with the partner. The caller may update only the policy owned by its active organization.

### Exact-email resolution

```text
POST /organizations/{targetOrganizationId}/external-identity-resolutions
```

Request:

```json
{
  "email": "thandi@acme.example"
}
```

Success:

```json
{
  "id": "resolution-id",
  "organizationId": "target-organization-id",
  "organizationName": "Acme Ltd",
  "displayName": "Thandi Mokoena",
  "email": "thandi@acme.example",
  "verifiedAt": "timestamp",
  "expiresAt": "timestamp"
}
```

Requirements:

- Request body contains the email. It never appears in the URL.
- Only complete normalized addresses are accepted.
- Empty, name, prefix, domain-only, and partial searches are unsupported.
- Lookup uses an organization-membership service method, not another service's repository.
- Account, membership, relationship, organization, and policy must all be active.
- The caller must be able to send and the target must be able to receive.
- The target must allow partner member resolution.
- Only allowed fields are projected.
- Failure is generic across missing account, inactive account, missing or inactive membership,
  policy denial, and hidden profile.
- The service equalizes response shape and avoids obvious timing distinctions where practical.
- Rate-limit keys include actor, caller organization, target organization, relationship, and a
  keyed email hash.
- Logs, traces, metrics, analytics, audit payloads, and incidents contain no raw searched email.

### Published groups

```text
GET /organizations/{targetOrganizationId}/published-exchange-groups
```

- Requires active trust, compatible send and receive policy, and target group-discovery permission.
- Returns only active, externally published, ORG-scoped Principal Groups owned by the target.
- Returns group destination metadata only, never group members.
- Uses a dedicated `PublishedExchangeGroupDto`; it must not reuse `PrincipalGroupDto`.

### Exchange acceptance

```text
POST /exchanges/{exchangeId}/acceptance-decisions
```

Request:

```json
{
  "decision": "ACCEPT",
  "reason": null
}
```

This endpoint replaces recipient acceptance and rejection through the generic Exchange update
payload. The service requires `EXCHANGE_ACCEPT`, an active primary-recipient binding, and all
selection-specific validation.

## Exchange Initiation Contract

Replace the nullable recipient fields with a discriminated selection contract shared by TypeScript
and Kotlin.

Example trusted person selection:

```json
{
  "type": "TRUSTED_PERSON",
  "resolutionId": "resolution-id"
}
```

Example trusted group selection:

```json
{
  "type": "TRUSTED_GROUP",
  "organizationId": "target-organization-id",
  "groupId": "group-id"
}
```

The full request has:

- One `primaryRecipient` selection.
- Zero or more `participants`, each with a selection and Share role.
- No client-supplied display name, organization verification flag, membership ID, relationship
  state, policy revision, or attestation state.

The backend performs these steps in one transaction:

1. Require and capture the caller's validated active organization for organization mode.
2. Authorize `EXCHANGE_INITIATE` against the active organization.
3. Validate all Exchange fields and recipient selection shape.
4. Resolve the selection through a recipient-resolution coordinator that delegates to service
   methods for users, membership, trust, and groups.
5. Revalidate each trusted relationship and both party policies.
6. Lock and consume any identity resolution.
7. Set Exchange owner from active organization, never from a recipient.
8. Use the owner organization for settings, variables, workflows, audit, and notifications.
9. Create the Exchange and initiator OWNER Share.
10. Create each direct recipient Share and its `ExchangeRecipient` binding.
11. Create each trusted recipient attestation.
12. Apply workflow gates and acceptance status.
13. Commit audit outbox intents atomically with the state change.
14. Dispatch notifications after commit or through existing durable event infrastructure.

Trusted person selection always requires recipient sign-in. The backend rejects attempts to disable
it.
Trusted person and trusted group selections always require recipient acceptance. The backend does
not apply the owner's normal auto-start setting to these selection types.

## Acceptance Rules

### Trusted person

Acceptance requires:

- The authenticated user is the attested `subjectAppUserId`.
- The direct primary Share belongs to that user and is eligible for acceptance.
- The account is active and not deprovisioned.
- The exact target membership is active and unexpired.
- The target organization is active and verified.
- The relationship is active and not effectively suspended.
- Current sender and target policies still permit the Exchange.
- The acceptance workflow, when present, permits the decision.

### Trusted group

Acceptance requires:

- The authenticated user is an active USER member of the attested group.
- The group role is OWNER or MANAGER.
- The group remains active, ORG-scoped, target-owned, and externally published.
- Relationship, organization, and policy checks pass.
- The acceptance workflow, when present, permits the decision.

### Other recipients

The same acceptance endpoint uses `ExchangeRecipient` to identify the primary recipient. It must not
infer acceptance authority from `EXCHANGE_VIEW` alone.

### Failure and recovery

- Failed revalidation does not activate pending Shares.
- The owner receives an actionable, non-sensitive reason category.
- The recipient receives a generic message when detailed policy information would leak data.
- The owner may replace or resend the primary recipient through the existing manage-access domain
  only after that domain supports updating `ExchangeRecipient` atomically with Shares.

## Service Boundaries

Create focused application services:

- `OrganizationTrustRelationshipService`
- `OrganizationTrustPolicyService`
- `OrganizationTrustQueryService`
- `ExternalIdentityResolutionService`
- `TrustedExternalGroupQueryService`
- `TrustedRecipientValidationService`
- `ExchangeRecipientService`
- `ExchangeAcceptanceService`

Repository ownership:

- Trust relationship service owns relationship and suspension repositories.
- Trust policy service owns the policy repository.
- Identity resolution service owns the resolution repository.
- Exchange recipient service owns recipient and attestation repositories.
- Services obtain organization, membership, group, user, and Exchange facts through service methods.
- No service uses another service's repository.
- DTO transformation lives in dedicated transformer classes.

`OrganizationExchangePolicyService` is replaced with a service that accepts explicit caller
organization context. No sharing decision may call `primaryOrganizationId`.

## Audit, Events, and Notifications

Add audit event types for:

- Relationship requested, accepted, rejected, withdrawn, expired, re-requested, suspended, resumed,
  and ended.
- Party policy updated.
- Identity resolution allowed, denied, expired, consumed, and replay denied.
- Published group listed or denied.
- Trusted recipient validation allowed or denied.
- Trusted acceptance allowed or denied.

Cross-organization mutations write correlated audit events to both organization streams. Each event
uses the appropriate organization as `AuditOwnerScope.Organization`, shares a business transaction
ID, and exposes only data permitted to that organization.

Never put raw searched emails, full profiles, secrets, or resolution tokens in audit payloads.

Relationship and policy changes publish domain events for email, in-app, and realtime notification.
Notification delivery failure must not roll back a valid relationship mutation. Do not send email
inside the persistence transaction.

## Database Schema and Clean Replacement

Use forward-only Flyway migrations with the next available version. Never edit `V1__baseline.sql`
or another applied migration.

### Additive migration

1. Create trust relationship, suspension, party policy, identity resolution, Exchange recipient,
   and recipient attestation tables.
2. Add foreign keys, lifecycle checks, uniqueness constraints, indexes, and optimistic-lock version.
3. Add `discoverable_for_trust_requests` to organization settings with a least-privilege default.
4. Replace `allow_share_without_pairing` with a positive B2B policy name such as
   `require_trusted_organization_for_b2b`.
5. Add the new capabilities and any persisted resource or event identifiers.

Implementation sequencing note: `V58__exchange_recipient.sql` created `exchange_recipient` during
Phase 1, `V59__organization_trust_persistence.sql` created the trust aggregate during Phase 2, and
`V60__organization_trust_discovery.sql` added the separate discovery opt-in during Phase 3. Later
migrations must not recreate those tables or that setting. Identity resolution and recipient
attestation tables are added only with the phases that introduce their owning services. Phases 2
through 7 are one non-deployable clean-replacement cutover unit: new trust persistence,
administration, recipient paths, and policy cleanup must be completed before the obsolete
relationship table, setting, and last callers are removed. Do not add compatibility reads, dual
writes, or fallback paths between the models while that cutover unit is in progress.

### Clean replacement

- New application code reads and writes only the new trust model.
- Create no data backfill from obsolete organization links or existing recipient Shares.
- Drop the obsolete relationship table and obsolete B2B setting in the same forward migration that
  establishes the replacement schema.
- Remove obsolete endpoints, DTOs, services, repositories, frontend calls, tests, notifications,
  and terminology in the same implementation.
- Reset development databases when existing local data cannot satisfy the new constraints.

## Frontend Plan

### Administration

Rename the Organization subtab from `Org Pairing` to `Trusted Organizations`.

Build focused responsive components for:

- Relationship list and status filters.
- Incoming requests.
- Outgoing requests.
- New request dialog and organization search.
- Relationship detail and partner policy summary.
- Current organization's policy editor.
- Suspension, resume, rejection, and ending dialogs.
- Activity and audit summary.

Use effective capabilities for visibility and enabled state. Follow all component, styling, ID,
button-shape, dialog-action, and responsiveness rules in `AGENTS.md`.

### Exchange initiation

Replace `ExternalOrganizationRecipients.tsx` with a folder of focused components:

- `TrustedOrganizationSelector`
- `TrustedRecipientMethodSelector`
- `TrustedPersonEmailResolver`
- `TrustedPersonConfirmationCard`
- `TrustedGroupSelector`
- `TrustedVerificationState`

Create co-located `*Styles.tsx` files. Keep components under the repository size guideline.

State requirements:

- Use strongly typed discriminated unions. Remove `any` from initiation state.
- Clear a resolution when target organization, active organization, recipient mode, or email changes.
- Prevent stale async responses from replacing results for a newer query.
- Never store raw lookup emails in analytics.
- Disable submit while resolution or group selection is stale.
- Require a fresh resolution after expiry.
- Display unverified, contact, verified membership, published group, expired, and policy-blocked
  states distinctly.

### API cleanup

Remove:

- `fetchPairedOrganizationUsers`
- `fetchPairedOrganizations`
- obsolete linked-group APIs
- `organizationExchange.ts`
- obsolete link DTOs and enums
- stale organization-user state from trusted-recipient components

Replace query-string mutation APIs with typed JSON request bodies.

## Implementation Phases

### Phase 0: Containment and characterization

**Status: COMPLETE - verified 2026-07-16**

Completed work:

- Removed the linked-organization member enumeration endpoint and its frontend caller.
- Replaced external group projection with `PublishedExchangeGroupDto`, which has no member field.
- Required validated active organization context and an accepted relationship for current linked
  organization and published-group queries.
- Added primary and participant group validation before Share creation. Inactive, unpublished,
  foreign personal, shared-project, and unpaired organization groups fail closed.
- Changed Exchange ownership, settings, interpolation, workflow, and audit context to the sender's
  active organization instead of the recipient group's organization.
- Added recipient-specific acceptance protection and containment tests.

Important decisions:

- The containment UI supports published external groups only. External member enumeration was
  deleted rather than hidden.
- Current obsolete relationship terminology remains only where the old model is still the live
  source pending the clean cutover unit in Phases 2 through 7.

Changed code areas:

- Organization linked-group resource, services, policy checks, and dedicated DTO transformer.
- Exchange initiation group validation and sender organization context.
- External organization recipient component, hook, and organization API client.
- Containment and group-policy tests.

Verification:

- `TrustedOrganizationContainmentTest`: 2 passed.
- `OrganizationExchangePolicyGroupTest`: 5 passed.
- Full backend suite and clean PostgreSQL initialization are recorded under Phase 1 verification.

Remaining work and risks:

- The obsolete relationship model remains live until its clean replacement is ready. It must not
  gain new behavior or compatibility paths.
- Directional trusted-group policy and current-trust revalidation are Phase 4 work.

Work:

1. Add tests proving linked member enumeration is forbidden.
2. Remove `GET /organizations/linked/{organizationId}/app-users`.
3. Remove the frontend call and all unreachable individual-directory code.
4. Replace the external group response with a dedicated summary DTO that contains no members.
5. Require active organization context in current link and published-group paths.
6. Validate primary and participant groups before any Share is created.
7. Fix group-recipient Exchange ownership, settings, variable, workflow, and audit context to use
   the sender's active organization.
8. Add recipient-specific guards to current acceptance while the final acceptance resource is built.

Exit criteria:

- No external organization member-list endpoint exists.
- Published group responses contain no member identities.
- Guessed, foreign, unpublished, inactive, or unpaired group IDs cannot create Shares.
- Recipient organization data cannot determine Exchange ownership.
- A general viewer or participant cannot accept or reject an Exchange.

### Phase 1: Authorization and recipient foundations

**Status: REOPENED - general-email acceptance correction is verified; current handoff keeps this
phase open pending final browser certification.**

Post-implementation correction:

- Authenticated and no-auth `EXTERNAL_EMAIL` acceptance now resolves the invitation email against
  the current account and reuses `OrganizationExchangePolicyService` with the Exchange's stored
  sender organization and initiator immediately before recording acceptance.
- Unresolved no-auth recipients use current external-customer policy. Current accounts use current
  active memberships and therefore current internal or B2B policy, including both directional trust
  decisions and organization eligibility. Inactive or deprovisioned accounts fail closed.
- Rejection remains permitted because it activates no access. Eligibility denial returns the same
  generic conflict for both acceptance resources and leaves the recipient decision pending.
- Focused and full verification is recorded in the post-implementation audit correction. No
  persistence change or migration was required.

Reopened finding (RESOLVED 2026-07-17):

- Added forward migration `V63__exchange_recipient_share_binding_invariant.sql` with a trigger that
  rejects an `exchange_recipient` binding to a Share from another Exchange or to a non-direct or
  OWNER Share (SQLSTATE 23514). V58 was not edited. The PostgreSQL migration contract
  (`AuditMigrationUpgradeContractTest`) now seeds an exchange, foreign/inherited/owner Shares, and
  asserts each invalid binding is rejected while the valid binding succeeds. `ExchangeRecipientServiceTest`
  continues to cover the service-level binding rules.

Completed work:

- Added the trust, external-resolution, external-group, and Exchange acceptance capabilities and
  actions with explicit organization and Share-role mappings. Platform application principals and
  service accounts receive no trust capability by default.
- Added `ExchangeRecipient`, its repository, service, DTO, dedicated transformer, and forward-only
  `V58__exchange_recipient.sql` migration with lifecycle checks, foreign keys, indexes, and one
  primary recipient per Exchange.
- Added recipient bindings for primary recipients, initiation participants, and manage-access
  grants. Idempotent direct Share grants reuse their existing binding.
- Added pessimistic locking for primary recipient decisions and validation that the bound direct
  Share is current, eligible, non-owner, on the same Exchange, and matches its selection type.
- Added `POST /exchanges/{exchangeId}/acceptance-decisions` and prevented authenticated acceptance
  or rejection through generic Exchange updates.
- Routed OTP acceptance through the primary external-email recipient binding after access-token and
  OTP verification.
- Authorized organization-mode initiation against the validated active organization.
- Replaced remaining Exchange lifecycle inference from the initiator's primary organization with
  `Exchange.ownerOrganizationId`.
- Routed organization and membership facts through service methods in touched service code.

Important decisions:

- Shares remain the only access grant. `ExchangeRecipient` records business purpose and decision
  authority without becoming an authorization source.
- Ordinary participants use `NOT_REQUIRED` acceptance status. The later V65 correction supersedes
  the original rule for trusted person and trusted group participants: each uses `PENDING` and can
  decide only its own participant invitation. Only a primary binding can decide the Exchange.
- Direct registered-user selections do not infer a target organization in the current nullable
  contract. The later discriminated selection contract must supply or resolve explicit target
  context where trusted assurance requires it.
- Group acceptance currently requires an active group and an active OWNER or MANAGER membership.
  Trusted relationship and directional policy revalidation remain Phase 4 work.
- No recipient backfill or compatibility acceptance path was added.

Changed code areas:

- Authorization action, capability, role mapping, pending-Share evaluation, and matrix tests.
- Exchange recipient entity, DTO, transformer, repository, service, migration, and tests.
- Exchange initiation, access management, acceptance, OTP decision, lifecycle workflow context,
  and frontend acceptance API routing.
- Recipient acceptance and workflow organization help content.

Verification commands and results:

- `.\mvnw.cmd test -DskipFrontend=true`: PASS, 697 tests, 0 failures, 0 errors.
- The full backend run used Testcontainers PostgreSQL 17, migrated an empty schema through V58, and
  verified the `exchange_recipient` table and participant acceptance constraint: PASS.
- Focused containment, authorization, recipient, access-management, and group-policy run: PASS,
  62 tests.
- `npx.cmd tsc --noEmit` in `web-app`: PASS, zero type errors. Direct `npx` is blocked by the host
  PowerShell execution policy, so the Windows command shim is required.
- `npm.cmd test` in `web-app`: 184 passed, 3 failed in one unrelated audit detail test file.
- Help-doc search covered paired organizations, published groups, recipient acceptance, primary
  recipient, and accept/reject wording. All seven matched files were read in full. Article files
  remain below 150 lines, the matched section is below 300 lines, and `helpDocsRegistry.tsx` is 58
  lines.
- `git diff --check`: PASS.

Known unrelated failures:

- `web-app/src/app/audit/__tests__/AuditEventDetail.test.tsx` has three failures because
  `document.getElementById` cannot find the actor and target copy buttons expected by the tests.
  No audit UI or audit test file changed in this phase. The remaining 184 frontend tests pass.

Remaining work and risks:

- Existing Exchanges are intentionally not backfilled with recipient bindings. Development data
  may be reset as stated by this plan.
- The current nullable initiation request remains until the discriminated recipient contract is
  introduced in later phases.
- Relationship and policy revalidation for trusted group and person acceptance is not present until
  their respective phases.

Historical next-session context from 2026-07-16, superseded by the 2026-07-17 pre-commit review:

- The next incomplete work is the Phase 2 through Phase 7 clean-replacement cutover unit, beginning
  with relationship, suspension, and party-policy persistence and owning services.
- Re-read `AGENTS.md` and this plan before work. Inspect `V58__exchange_recipient.sql` and use the
  next available Flyway version without editing V58.
- Treat Phases 2 through 7 as one cutover: do not deploy or mark the unit complete while old
  relationship persistence, settings, or callers remain.
- Do not add compatibility DTOs, dual writes, fallback reads, backfills, or a new AWS service.

Work:

1. Add trust and acceptance capabilities and actions.
2. Map roles explicitly and update action-capability matrix tests.
3. Introduce `ExchangeRecipient` and its service, repository, DTO transformer, and constraints.
4. Write recipient bindings in initiation and manage-access flows.
5. Add `ExchangeAcceptanceService` and the acceptance-decision resource.
6. Remove acceptance and rejection from generic Exchange update requests.
7. Replace remaining Exchange lifecycle uses of initiator primary organization with stored Exchange
   ownership.

Exit criteria:

- Active organization is explicit for every organization-mode initiation.
- Every new direct recipient Share has a recipient binding.
- Acceptance authority is primary-recipient-specific.
- Applications and service accounts have no trust capability by default.

### Phase 2: Trust persistence and schema replacement

**Status: COMPLETE - reopened finding resolved and verified 2026-07-17**

Reopened finding (RESOLVED 2026-07-17):

- `OrganizationTrustRelationshipService.resume` now takes the per-relationship transaction advisory
  lock (`relationshipRepository.lockOrganizations`) before clearing the suspension and querying the
  remaining active suspensions, so two parties resuming concurrently serialize and the
  last-suspension group reconciliation runs exactly once. `OrganizationTrustResumeConcurrencyContractTest`
  drives two concurrent resume transactions against real PostgreSQL and asserts exactly one observes
  zero remaining active suspensions (the other still sees the partner suspension).

Completed work:

- Added `OrganizationTrustRelationship`, `OrganizationTrustSuspension`, and
  `OrganizationTrustPartyPolicy` entities with explicit lifecycle fields, actor evidence,
  optimistic versions, and typed directional policy fields.
- Added forward-only `V59__organization_trust_persistence.sql`. It creates the three trust tables,
  foreign keys, lifecycle checks, canonical organization ordering, partial uniqueness for a current
  relationship generation, suspension ownership and clearing checks, policy ownership checks,
  indexes, and database triggers that reject unrelated policy or suspension owners.
- Added repositories owned only by the relationship or policy service. Relationship creation takes
  a transaction-scoped PostgreSQL advisory lock for the canonical organization IDs so reversed or
  concurrent requests serialize before the partial unique constraint is reached.
- Implemented request, accept, reject, withdraw, expire, suspend, resume, and end transitions with
  validated active organization authorization and side-specific actor rules.
- Added configurable request expiry, re-request cooldown, and relationship review periods with
  defaults of 14 days, 7 days, and 365 days respectively.
- Added an expiry scheduler with skipped concurrent execution and request-context activation.
- Added least-privilege policy initialization and owner-only typed policy updates. Every user policy
  update validates an expected revision and relationship generation loaded through the owning
  relationship service.
- Added correlated durable audit intents for both organization streams for every lifecycle,
  suspension, resume, and policy mutation. Added the new trust event types and advanced the audit
  catalog version to 8.
- Added lifecycle, authorization, cooldown, optimistic-version, suspension, policy, scheduler, and
  PostgreSQL migration constraint tests.

Important decisions:

- Organization UUIDs are stored in lowercase UUID string order, which matches PostgreSQL UUID order
  and gives every relationship generation one canonical representation.
- Default policy rows are created atomically with the relationship and deny every directional
  operation. They are system defaults, so `updatedByAppUserId` remains null until the owning
  organization explicitly edits the policy.
- `@Version` fields remain the persistence concurrency authority. Mutation methods also require the
  caller's expected relationship version or policy revision so stale requests fail before write.
- Rejected, withdrawn, expired, and ended generations remain immutable. Re-request creates a new ID
  only after the configured cooldown.
- Ending a relationship does not rewrite either party's suspension history. Effective suspension is
  computed only for an active relationship.
- No compatibility read, dual write, backfill, obsolete-table drop, endpoint, frontend caller, or
  new AWS service was added. The obsolete relationship model remains live until the clean Phase 7
  cutover because Phases 2 through 7 are one non-deployable implementation unit.

Changed code areas:

- Trust relationship, suspension, and party-policy entities and domain exceptions.
- Relationship, suspension, and policy repositories.
- Relationship lifecycle, policy, configuration, and expiry scheduler services.
- Audit event catalog and correlated trust audit capture.
- Flyway V59 and PostgreSQL migration upgrade contract.
- Focused relationship, policy, and scheduler unit tests.

Verification commands and results:

- `.\mvnw.cmd test -DskipFrontend=true`: PASS, 711 tests, 0 failures, 0 errors.
- The full backend run used Testcontainers PostgreSQL 17 and migrated clean databases through V59:
  PASS. The migration contract directly verified rejection of noncanonical organization ordering,
  duplicate current generations, unrelated policy owners, and unrelated suspension owners.
- Focused relationship, policy, and scheduler run: PASS, 14 tests.
- `npx.cmd tsc --noEmit` in `web-app`: PASS, zero type errors.
- `npm.cmd test` in `web-app`: 184 passed, 3 failed in the same unrelated audit detail test file
  recorded under Phase 1.
- Help-doc search covered trusted and paired organization wording, organization relationships,
  external organizations, sharing policy, and recipients. All 16 matched files were read in full.
  No article describes the backend-only persistence introduced in this phase, so no content change
  was required. All matched articles remain under 150 lines, both matched sections remain under 300
  lines, and `helpDocsRegistry.tsx` is 58 lines.
- `git diff --check`: PASS, with only existing line-ending conversion warnings.

Known unrelated failures:

- `web-app/src/app/audit/__tests__/AuditEventDetail.test.tsx` still has the same three failures
  recorded under Phase 1. The tests cannot find the actor and target copy-button elements. Phase 2
  changed no frontend or audit-detail component.

Remaining work and risks:

- The new persistence and services intentionally have no HTTP or UI caller until Phase 3. The
  obsolete relationship model is still the live administration path and the combined Phase 2
  through Phase 7 unit must not be deployed in this intermediate state.
- Email, in-app, and realtime trust notifications and user-facing audit labels are Phase 3 work.
- Directional policy evaluation for trusted group and person operations is not connected until
  Phases 4 through 6.
- Obsolete persistence, settings, callers, and terminology are removed only after their new callers
  exist, in the Phase 7 cutover migration. V59 contains no compatibility data.

Historical next-session context from 2026-07-16, superseded by the 2026-07-17 pre-commit review:

- The next incomplete work is Phase 3: organization discovery plus the trust administration API and
  responsive capability-driven UI.
- Build thin resources over `OrganizationTrustRelationshipService` and
  `OrganizationTrustPolicyService`. Mutation request DTOs must carry expected `version` or
  `revision`; map the dedicated trust exceptions consistently without exposing guessed IDs.
- Add a dedicated query/projection service and DTO transformers. Do not serialize trust entities
  directly and do not let resources coordinate repositories or domain transitions.
- Organization discovery must use a new explicit opt-in setting and the new relationship service to
  exclude current generations. It must not use `allowShareWithoutPairing`.
- The lifecycle audit intents already exist. Phase 3 should add email, in-app, and realtime
  notifications outside persistence transactions, add frontend audit labels for the new event keys,
  and remove the obsolete administration endpoints and UI only when the replacement is complete.
- Continue the same clean cutover unit without compatibility DTOs, fallback reads, dual writes,
  backfills, or a new AWS service.

Work:

1. Add relationship, suspension, and party-policy entities and migrations.
2. Add constraints, indexes, policy revisioning, and optimistic locking.
3. Prepare the obsolete link persistence deletion as part of the Phase 2 through Phase 7 cutover;
   execute the drop only when all new trust, recipient, and policy callers are ready in the
   same non-deployable implementation unit. Create no compatibility backfill.
4. Add repositories used only by their owning services.
5. Implement relationship state transitions, request expiry scheduling, and per-party suspension.
6. Implement correlated audit events for both organizations.

Exit criteria:

- Invalid organization combinations, policy ownership, or suspension ownership cannot be persisted.
- Concurrent decisions and policy edits fail predictably.
- A clean development database initializes successfully.

### Phase 3: Trust administration API and UI

**Status: COMPLETE - reopened findings resolved and verified 2026-07-17**

Reopened findings (RESOLVED 2026-07-17):

- Relationship action routing in `useTrustedOrganizations` is now an exhaustive `switch`; `RESUME`
  can never fall through to termination and fails without mutation when no suspension id is present.
- `TrustedOrganizationsTab` binds each open action dialog to the relationship snapshot and its
  `version` captured at open, and fails closed if that target became stale before confirmation.
- Relationship and policy fetches carry a request sequence and active-organization snapshot; stale
  responses are discarded.
- `useTrustedOrganizations.test.ts` covers stale relationship responses (organization switch), stale
  policy responses, notification refreshes, and the missing-suspension resume guard.
- Targeted ESLint is clean (removed unused `shorthands`; corrected hook dependencies).

Completed work:

- Added a least-privilege `discoverableForTrustRequests` organization setting and forward-only
  `V60__organization_trust_discovery.sql` migration. Discovery uses a JSON request body, the
  existing directory lookup guard, a configured result cap, and active, verified, opt-in filters.
  It excludes the caller and every current pending or active relationship and never consults
  `allowShareWithoutPairing`.
- Added relationship and policy projection DTOs plus a dedicated transformer. The query service
  validates active USER organization context, requires `ORG_TRUST_READ`, exposes only the current
  party view, and returns not found for a relationship ID guessed by an unrelated organization.
- Added thin REST resources for relationship creation and listing, direct relationship reads,
  decisions, withdrawals, suspensions, suspension removal, termination, policy reads, and policy
  updates. Mutation bodies carry the required relationship version or policy revision.
- Added a command facade that invokes the transactional owning services and publishes notifications
  only after a successful transaction returns. Initial requests and re-requests preserve their
  distinct lifecycle event types.
- Added email and in-app administrative notifications for both organizations. In-app persistence
  broadcasts through the existing realtime service. Individual delivery failures are logged and
  contained so they cannot undo a valid trust mutation. Scheduled expirations publish the same
  administrative notification after each committed expiry batch.
- Added a responsive, capability-driven Trusted Organizations subtab with status filtering,
  relationship detail, current-party policy editing, request search, decision, withdrawal,
  suspension, resume, and termination dialogs. Active organization changes clear relationship,
  policy, dialog, and pending search state; stale search responses cannot replace newer results.
- Added the discovery opt-in to Organization Details. Added frontend service types and JSON-body API
  calls without `any`, inline styles, or query-string mutation data.
- Removed the obsolete organization link administration resource, frontend service, pairing tab,
  dialogs, DTOs, enums, icons, and settings wiring. The minimal old accepted-link lookup remains only
  for Exchange and published-group paths scheduled for replacement in Phases 4 through 7.
- Added user-facing labels for every trust audit event, including re-request, and added focused
  backend and frontend coverage for discovery, projections, guessed IDs, REST contracts,
  notifications, lifecycle authorization, policy ownership, and audit labels.
- Added Trusted Organizations help content and updated notification help content for administrative
  email and in-app delivery.

Important decisions:

- Discovery is a separate explicit opt-in with a false default. Sharing permissions do not make an
  organization discoverable.
- Resources parse identifiers and delegate only. Relationship transitions and policy ownership stay
  in their owning services, and entity-to-DTO conversion stays in the dedicated transformer.
- Guessed relationship, policy, or suspension ownership mismatches fail closed. A caller can clear
  only its own suspension and edit only its own policy.
- Notification work is deliberately outside relationship and policy transactions. The notifier
  isolates organization, in-app, and email delivery failures and uses the exact lifecycle event key.
- Phase 3 removes administration over the obsolete link model but does not add a compatibility read,
  dual write, backfill, or early persistence drop. Existing Exchange and group policy callers are
  replaced in later phases before the Phase 7 drop.
- No new AWS service or paid infrastructure resource was introduced.

Changed code areas:

- Organization settings entity, DTO mapping, settings service, organization discovery repository
  query, organization service, and Flyway V60.
- Trust exception mapping, request and response DTOs, DTO transformer, query service, command service,
  directory search service, thin resources, notification service, and expiry scheduler integration.
- In-app administrative notification publishing and trust audit labels.
- Organization settings and Trusted Organizations React components, Fluent styles, hooks, API client,
  capability model, settings navigation, and removal of obsolete pairing administration code.
- Trust administration and notification help articles, resource and service tests, migration
  contract, frontend API tests, and audit-label tests.

Verification commands and results:

- `.\mvnw.cmd clean test -DskipFrontend=true "-Dkotlin.compiler.execution.strategy=in-process"`:
  PASS, 709 tests, 0 failures, 0 errors before the final discovery contract test was added.
- `.\mvnw.cmd test -DskipFrontend=true "-Dkotlin.compiler.execution.strategy=in-process"` after all
  production and test changes: PASS, 710 tests, 0 failures, 0 errors.
- The full backend runs used Testcontainers PostgreSQL 17, validated 57 migrations, initialized empty
  schemas through V60, and verified the discovery column and current Flyway version: PASS.
- Focused Phase 3 backend run covering discovery, query projection, resource contracts,
  notifications, relationship lifecycle, policy ownership, and expiry: PASS, 21 tests.
- `npx.cmd tsc --noEmit` in `web-app`: PASS, zero type errors.
- `npm.cmd test -- --run` in `web-app`: 186 passed, 3 failed in one unrelated audit detail test file.
  The new organization trust API tests and trust audit-label tests pass.
- Focused frontend organization trust API and audit-label run: PASS, 2 files and 4 tests.
- Help search covered trusted organization, pair, linked organization, external organization,
  sharing, and recipient terms. All 20 matched files were read in full. No matched article is 150
  lines or longer, `adminOperationsSection.tsx` is 99 lines, the new article is 78 lines, and
  `helpDocsRegistry.tsx` is 58 lines.
- The new Trusted Organizations components are 148 lines or fewer. Compliance scans found no new
  `any`, inline style, non-circular button, planning-document code comment, or resource-layer
  repository usage.
- `git diff --check`: PASS after the final plan update.

Known unrelated failures:

- `web-app/src/app/audit/__tests__/AuditEventDetail.test.tsx` has the same three pre-existing failures
  recorded in Phases 1 and 2. The tests cannot find the actor and target copy-button elements. All
  other 186 frontend tests pass.

Remaining work and risks:

- Phases 2 through 7 remain one non-deployable clean cutover. The obsolete link persistence and the
  minimal accepted-link lookup still support current Exchange and published-group policy paths and
  must not survive the Phase 7 cutover.
- The administration policy editor persists all directional flags, but published group and exact
  member behavior is intentionally connected only in Phases 4 through 6. This intermediate unit
  must not be deployed as a complete feature.
- Adversarial output review remains part of Phase 8 after the recipient paths are complete.

Historical next-session context from 2026-07-16, superseded by the 2026-07-17 pre-commit review:

- The next incomplete work is Phase 4, Published trusted groups. Re-read `AGENTS.md` and this plan,
  then inspect the completed Phase 3 trust query, policy, and DTO boundaries before changing code.
- Build `TrustedExternalGroupQueryService` on active relationship and both directional policy facts.
  Return the existing member-free published group summary shape and fail closed for inactive,
  unpublished, foreign, personal, shared-project, guessed, suspended, or policy-blocked groups.
- Replace the obsolete linked-group endpoint and frontend caller only when the new trusted-group path
  is complete. Do not restore the deleted pairing administration resource or UI.
- Add group recipient attestation in the next available Flyway migration after V60. Do not recreate
  `exchange_recipient`, the trust aggregate, or the discovery setting.
- Continue the non-deployable cutover without compatibility DTOs, fallback reads, dual writes,
  backfills, primary-organization inference, or a new AWS service.

Work:

1. Implement organization discovery with verified, active, opt-in filtering.
2. Implement relationship, decision, suspension, termination, and policy resources.
3. Keep all resources thin.
4. Build capability-driven Trusted Organizations administration UI.
5. Add email, in-app, and realtime notifications using new terminology, and add user-facing labels
   for the trust audit events already captured by Phase 2.
6. Remove obsolete link administration endpoints and UI.

Exit criteria:

- Only the correct party can request, decide, suspend, resume, end, or change policy.
- Cross-organization guessed IDs fail closed.
- The UI exposes no operation the backend would authorize differently.
- Obsolete pairing administration is absent.

### Phase 4: Published trusted groups

**Status: REOPENED - independent trusted-participant acceptance is verified; participant browser
certification remains open.**

Reopened finding (RESOLVED 2026-07-17):

- Trusted-group (and trusted-person) trust revalidation in `ExchangeRecipientService.recordPrimaryDecision`
  now runs only when the decision is an acceptance. Rejection remains permitted even when activation
  eligibility has lapsed, because rejection never activates a Share and lets the owner reach an
  actionable rejected state (documented decision). Trust invalidation during acceptance now maps to
  `409 CONFLICT` with a generic, non-leaking message in `ExchangeAcceptanceResource` instead of the
  generic 500 branch. `ExchangeRecipientServiceTest` covers accept-fails-closed and reject-permitted
  for trusted groups (and, for Phase 6, trusted persons).

Completed work:

- Added `TrustedExternalGroupQueryService` and
  `GET /organizations/{targetOrganizationId}/published-exchange-groups`. The path requires a USER
  principal, validated active organization context, `EXTERNAL_GROUP_DISCOVER`, an active and
  unsuspended current relationship, eligible organizations, sender outbound permission, receiver
  inbound permission, receiver group-discovery permission, and unexpired policy and review facts.
- Reused the dedicated `PublishedExchangeGroupDto` transformer. Responses contain group summary
  fields only and never expose membership.
- Added a strongly typed initiation selection contract for registered users, external emails,
  internal groups, personal groups, and trusted groups. Removed the old nullable initiation
  recipient request fields and aligned participant selections with the same contract plus an
  explicit non-owner Share role.
- Added server-side selection resolution that verifies the declared selection kind, scope, owner,
  active organization, target organization, group publication, and group status. Duplicate
  participants and primary-recipient substitution through the participant list fail before Share
  creation.
- Added Flyway V61 and the `exchange_recipient_attestation` entity, repository, and service. Trusted
  group selections persist relationship, organization, policy-revision, group, display snapshot,
  verification time, expiry, and acceptance-verification evidence with database constraints and
  relationship and recipient consistency checks.
- Made trusted group primaries require sign-in and recipient acceptance even when the initiating
  organization normally auto-starts Exchanges. Their direct group Share remains pending until
  acceptance and workflow gates permit activation.
- Authorized pending group acceptance only for current active group OWNERs and MANAGERs, with a
  second domain check in `ExchangeRecipientService`. MEMBERs and OBSERVERs fail. No permanent
  individual reviewer Shares are created for group managers.
- Revalidated the current relationship generation, organizations, policies, publication, and group
  status during trusted group acceptance and before pending group Share activation. Recipient
  decisions cannot run while sender draft approval is still pending.
- Gated new trusted-group inherited Share materialization on current eligibility. Existing
  materialized access remains, removal still revokes inherited access, suspended or ended trust
  blocks new expansion, and clearing the last suspension reconciles current eligible members.
- Added trusted groups and trusted people as additional participants with a pending direct Share,
  pending participant binding, and attestation. An independent recipient-specific decision
  revalidates current eligibility before activating only that participant Share and inheritance.
- Replaced the external-organization recipient component with focused responsive Trusted
  Organization and published-group selectors. Active organization changes clear state, capability
  checks control visibility, and request identifiers prevent stale group responses from replacing
  newer selections.
- Removed the obsolete linked-group endpoint, service and repository callers, frontend paired-group
  calls, old external recipient component and hook, unused participant enum, and stale initiation
  comments.
- Added success and denied audit events and frontend labels for published-group listing attempts.
- Expanded help content for published-group selection, member privacy, forced acceptance, manager
  authority, policy revalidation, suspension, ending, and inherited access behavior.

Important decisions:

- Trusted group evidence is historical evidence only. Current relationship, policy, organization,
  and group facts remain the authorization source at acceptance and Share expansion time.
- Attestation expiry is the earliest current relationship review date or party-policy expiry. A
  relationship with an overdue review or an expired policy fails closed.
- Trusted group primary recipients always require a direct group decision. The organization-level
  acceptance bypass does not weaken the trusted assurance path.
- Pending group Shares provide acceptance capability only through current OWNER or MANAGER
  membership. The recipient service repeats the role check before recording the decision.
- Suspension and ending do not revoke already materialized access. Only new inheritance is gated;
  group member removal remains effective, and resumption reconciles after all suspensions clear.
- Trusted group and trusted person participants use the same binding and attestation model. They do
  not use the primary recipient decision, but each requires its own participant invitation decision.
- No compatibility request fields, fallback link reads, dual writes, backfill, or new AWS service
  were added.

Changed code areas:

- Published group REST resource, trust validation and query services, relationship query and resume
  reconciliation, organization group summary methods, trust audit catalog, and application config.
- Exchange recipient selection resolver, initiation contract and service, recipient attestation,
  recipient decision service, Share materialization, authorization, approval activation, and direct
  acceptance workflow guard.
- Flyway V61, migration contract, containment, trust validation, acceptance, Share inheritance,
  relationship resume, authorization, and initiation-selection tests.
- Frontend models, initiation request builder and state, Trusted Organization recipient components,
  trust API client, audit labels, and removal of obsolete paired-group callers.
- Trusted Organizations, workflow settings, trigger events, and blueprint help articles.

Verification commands and results:

- `.\mvnw.cmd test -DskipFrontend=true "-Dkotlin.compiler.execution.strategy=in-process"`: PASS,
  723 tests, 0 failures, 0 errors.
- Full backend verification used Testcontainers PostgreSQL 17, validated 58 migrations, initialized
  empty schemas through V61, and verified the upgrade from V51 through V61: PASS.
- Focused trust, containment, acceptance, workflow guard, relationship resume, migration, and Share
  authorization runs: PASS, including a final 78-test run with 0 failures and 0 errors.
- `npx.cmd tsc --noEmit` in `web-app`: PASS, zero type errors.
- Targeted ESLint for the new trusted recipient components, selection builder, and trust client:
  PASS.
- Focused frontend trust API, initiation-selection, and audit-label tests: PASS, 3 files and 7 tests.
- `npm.cmd test -- --run` in `web-app`: 189 passed, 3 failed in the unrelated audit detail test
  file recorded below. All Phase 4 frontend tests pass.
- Help search matched 22 section and article files; every match was read in full. Updated articles
  are under 150 lines, all section files are under 300 lines, and `helpDocsRegistry.tsx` is 58
  lines.
- New Trusted Organization TSX components are 55 lines or fewer. Compliance scans found no new
  `any`, inline style, hardcoded design-system color or spacing, non-circular button, prohibited
  character, planning-document code comment, or resource-layer repository usage.
- `git diff --check`: PASS after the Phase 4 plan update.

Known unrelated failures:

- `web-app/src/app/audit/__tests__/AuditEventDetail.test.tsx` retains the same three pre-existing
  failures recorded in Phases 1 through 3. The tests cannot find the actor and target copy-button
  elements. The other 189 frontend tests pass.

Remaining work and risks:

- Phases 2 through 7 remain one non-deployable clean cutover. Obsolete `OrganizationExchangeLink`
  persistence, the old B2B setting, and the minimal ordinary sharing-policy lookup remain until the
  Phase 7 replacement and drop. Trusted group discovery and initiation do not use them.
- Exact-email trusted person resolution is now substantially implemented in Phase 5. Trusted-person
  initiation and acceptance remain absent until Phase 6. The V61 attestation schema includes the
  person evidence columns, but no compatibility or placeholder initiation behavior is active.
- The Phase 4 selectors use Fluent controls, responsive width, stable IDs, explicit loading and
  error states, and capability-driven visibility.

Post-audit verification update - 2026-07-18:

- V65 enforces the trusted participant acceptance-state contract at the database boundary.
- Focused recipient, invitation-resource, and migration tests pass: 32 tests, zero failures.
- The full backend suite passes: 961 tests, zero failures, zero errors, 166 skipped, with clean
  PostgreSQL initialization through V65.
- The Requests tab lists eligible trusted participant invitations and lets the authorized invited
  person or eligible group manager accept or reject only that invitation.
- Trusted Organizations and workflow help articles document the independent decision and remain
  within their size limits.
- Phase 4 has no remaining automated audit-correction defect. Trusted participant browser
  certification remains open under the current handoff.

Historical next-session context from 2026-07-16, superseded by the 2026-07-18 audit correction:

- Phase 5 resolution persistence and UI were subsequently implemented in V62 and the current
  working tree. Use the Phase 5 current status and the mandatory next-session order above instead
  of repeating the former Phase 4 handoff.
- Continue the cutover without compatibility fields, fallback reads, dual writes, backfills,
  primary-organization inference, or a new AWS service.

Work:

1. Implement the published-group query service and endpoint over the Phase 3 trust query and policy
   boundaries; do not reuse the obsolete link administration model.
2. Enforce active relationship and compatible directional policy.
3. Return no group membership data.
4. Implement trusted group selection with the new initiation contract.
5. Persist `ExchangeRecipient` and group attestation.
6. Implement group manager acceptance.
7. Gate new inherited Share materialization on current trust policy.
8. Remove the obsolete linked-group endpoint and caller after the trusted group path replaces it.

Exit criteria:

- Only eligible published groups are visible and selectable.
- Client tampering cannot substitute another organization or group.
- Group OWNER and MANAGER acceptance works; other group roles fail.
- Relationship changes follow the documented group access rules.

### Phase 5: Blind exact-email member resolution

**Status: COMPLETE - certified 2026-07-17 (Docker-backed verification ran successfully this session)**

Completed implementation present in the working tree:

- Added forward-only V62 and the `external_identity_resolution` entity and repository. Resolution
  records bind the actor, caller organization, target organization, relationship generation,
  sender and target policy revisions, resolved account, exact membership, normalized email,
  display-name snapshot, creation and expiry times, and single-use consumption evidence.
- Added database checks and a trigger proving that the caller and target are relationship parties
  and that the resolved membership belongs to the resolved account and target organization.
- Added `ExternalIdentityResolutionService` with exact trim-and-lowercase normalization, complete
  email validation, active exact-membership lookup through `OrganizationMembershipService`,
  active and non-deprovisioned account checks through `AppUserService`, target-controlled display
  name projection, short-lived expiry, pessimistic row locking for consumption, replay rejection,
  and retention cleanup.
- Added `POST /organizations/{targetOrganizationId}/external-identity-resolutions` with a typed JSON
  body. The email is not placed in the URL.
- Added `ExternalIdentityLookupGuardService` with rate-limit dimensions for actor, caller
  organization, target organization, relationship, and a keyed HMAC email hash. Rate-limit
  incidents contain the hash and organization or relationship identifiers, not the raw email.
- Added the cleanup scheduler and configuration for resolution expiry, retention, and cleanup
  frequency.
- Added allowed, denied, expired, consumed, and replay-denied audit event types and frontend labels.
  Resolution audit payloads contain target organization and relationship identifiers but not the
  raw email.
- Added the Trusted Organization recipient method selector, exact-email input, confirmation card,
  membership-verification label, expiry state, active-organization resets, and stale resolution
  response protection.
- Added help content covering exact lookup privacy, target-controlled display names, assurance
  language, expiry, request binding, JSON-body transport, and audit redaction.
- Added focused service, guard, validation, REST path, frontend API, stale response, and selection
  separation tests.

Important decisions and current boundary:

- A successful resolution is evidence only. It does not grant access and is not yet a valid
  initiation selection.
- `consumeForExchange` exists for Phase 6 but has no Exchange initiation caller yet.
- The UI displays `Membership verified by <organization>` and does not claim real-world identity
  verification.
- General People email invitations remain separate and do not receive trusted assurance.
- The trusted-person UI currently exposes a verification method that cannot be submitted as an
  Exchange recipient. Phase 6 must close this gap before the recipient flow is deployable.
- No compatibility request fields, fallback reads, dual writes, backfill, or new AWS service were
  added.

Changed code areas:

- `ExternalIdentityResolution`, its DTO and transformer, repository, service, cleanup scheduler,
  lookup guard, request model, resource, exception mapping, security incident type, audit event
  catalog, and V62.
- Exact-email organization membership query methods and service boundaries.
- `organizationTrust.ts`, trusted recipient React components and hook, audit labels, help content,
  and focused backend and frontend tests.

Verification recorded on 2026-07-17:

- Focused backend resolution, lookup-guard, and trusted-recipient validation run: PASS, 15 tests,
  0 failures, 0 errors.
- `npx.cmd tsc --noEmit`: PASS.
- Focused trust frontend run: PASS, 3 files and 7 tests.
- Full backend run: 733 tests passed. Two Docker-dependent PostgreSQL contract tests could not start
  because Docker was unavailable; no test assertion failed.
- Full frontend run: 191 passed and the same 3 unrelated audit-detail tests failed.
- Targeted ESLint is not clean because of one unused import and two hook dependency warnings
  recorded in the pre-commit review correction.

Certification (2026-07-17):

1. Administration stale-response and lint findings are fixed (findings 1, 2, 3, 8).
2. The exact-email path continues to keep the raw searched email out of URLs, audit payloads, and
   security-incident records; unsuccessful lookups create no resolution row. The consuming
   `prepareForInitiation`/`consumeForExchange` path added in Phase 6 carries only identifiers and
   the already-stored normalized email snapshot, and adds no new raw-email output channel.
3. Focused resolution, lookup-guard, and validation service tests pass; the Docker-backed migration
   contract exercises the V62 trigger and now the V63 binding trigger.
4. The Docker-backed migration contract ran successfully this session and asserts a clean upgrade
   through V63.
5. Full backend, full frontend, type checking, targeted ESLint, help-doc size checks, and
   `git diff --check` all pass (see the consolidated verification block at the end of this plan).

Work:

1. Add identity resolution persistence and cleanup scheduling.
2. Implement exact normalization and target membership lookup through services.
3. Add target-controlled profile projection.
4. Add keyed-hash rate-limit dimensions and security incident detection.
5. Add generic failure behavior and output-channel redaction.
6. Build the trusted person resolver and confirmation UI.
7. Test expiry, replay, wrong actor, wrong organization, and stale result handling.

Exit criteria:

- No partial directory search is possible.
- A success returns only the permitted account and membership facts.
- All denied outcomes avoid disclosing why the match failed.
- A resolution cannot be reused by another actor, organization, target, or Exchange.

### Phase 6: Trusted person initiation and acceptance

**Status: COMPLETE - implemented and verified 2026-07-17**

Completed work:

- Added `TrustedPersonRecipientSelectionRequest(resolutionId)` to the discriminated initiation
  contract (Kotlin `@SerialName("TRUSTED_PERSON")`) and the matching `TRUSTED_PERSON` arm of the
  TypeScript `ExchangeRecipientSelection` union. No nullable recipient fields were reintroduced and
  no client-supplied identity, organization, membership, or attestation field is trusted.
- Added `ExternalIdentityResolutionService.prepareForInitiation`, which revalidates a stored
  resolution without consuming it: it confirms the resolution belongs to the initiating actor and
  caller organization, is unconsumed and unexpired, and that the current relationship, both party
  policies, the resolved account, and the exact target membership are still eligible. The single-use
  consumption remains `consumeForExchange`, which runs under a row lock inside the Exchange
  transaction so it rolls back with the Exchange.
- Added the `TRUSTED_PERSON` branch to `ExchangeRecipientSelectionResolver`, which returns the
  resolved account, the target organization, and a `PreparedPersonResolution` carrier.
- `ExchangeInitiationService` now, for a trusted-person primary or participant: forces recipient
  sign-in and recipient acceptance (never auto-start), consumes the resolution under lock bound to
  the new Exchange id, creates the USER direct Share, the `ExchangeRecipient` binding, and a PERSON
  `ExchangeRecipientAttestation` via `ExchangeRecipientAttestationService.createPersonAttestation`.
- Person acceptance revalidation: `TrustedRecipientValidationService.validatePersonAttestation`
  re-checks the current relationship, both Exchange-direction policies, the target organization, the
  attested account, and the exact target membership; `recordPrimaryDecision` invokes it only on
  acceptance and marks the attestation acceptance-verified. Because the direct primary Share is the
  resolved USER, `canDecide` already requires the acceptor to be the attested account.
- Frontend: `buildRecipientSelection` emits `TRUSTED_PERSON` when a fresh resolution is present
  (preferring it over a group), the initiation state threads `recipientResolution`, the trusted
  recipients hook publishes/clears the resolution (on resolve success, email change, method or
  relationship change, and expiry), and `ExchangeInitiation` validation now accepts a verified
  member or a published group and blocks an expired verification. This closes pre-commit finding 4:
  a verified member is now a real, submittable recipient rather than a dead end.
- Added backend tests for trusted-person acceptance (revalidates and marks verified; fails closed
  when the attested membership is no longer eligible; rejection permitted without revalidation) and
  `validatePersonAttestation` (happy path and account/membership mismatch), plus frontend selection
  builder tests for `TRUSTED_PERSON` and person-over-group preference.
- Extended the Trusted Organizations help article with a "Sending an Exchange to a verified member"
  section (article remains 144 lines, under the 150-line limit).

Important decisions:

- Trusted-person and trusted-group primaries are treated uniformly for sign-in and acceptance
  (`trustedRecipient` in initiation). The organization auto-start setting never applies to them.
- Owner recovery for expired or invalid pending trusted-person invitations is deferred to the
  manage-access domain (Phase 7), consistent with the plan's requirement that recovery wait until
  that domain updates `ExchangeRecipient` atomically with Shares. No parallel evidence model was
  added; the V61 attestation table is reused for PERSON subjects.

Changed code areas:

- `RequestsResponses.kt`, `ExchangeRecipientSelectionResolver.kt`, `ExternalIdentityResolutionService.kt`,
  `TrustedRecipientValidationService.kt`, `ExchangeRecipientAttestationService.kt`,
  `ExchangeRecipientService.kt`, `ExchangeInitiationService.kt`.
- `models.tsx`, `exchangeInitiationRecipientSelection.ts`, `useExchangeInitiatingState.ts`,
  `ExchangeInitiation.tsx`, `ExchangeInitiationRecipientsTab.tsx`, `TrustedOrganizationRecipients.tsx`,
  `useTrustedOrganizationRecipients.ts`.
- Backend and frontend tests listed above; Trusted Organizations help article.

Original work checklist. Item 7 is complete after the verified finding 3 correction restricted
recovery to trusted selections and made every replacement Share pending until acceptance:

1. Extend the existing discriminated initiation contract with `TRUSTED_PERSON`; do not reintroduce
   the removed nullable recipient request fields.
2. Consume resolutions under row lock in the initiation transaction.
3. Revalidate both policies, account, membership, and organizations.
4. Create the USER Share, recipient binding, and person attestation atomically using the V61
   attestation table; do not create a parallel evidence model.
5. Require sign-in.
6. Revalidate the attested account and exact membership during acceptance.
7. Add owner recovery for expired or invalid pending invitations.
8. Apply the same trusted selection rules to additional participants.

Exit criteria:

- Client-supplied identity or organization fields cannot change the resolved recipient.
- The accepting account must be the attested account.
- Membership or policy changes before acceptance fail closed.
- Accepted Exchanges preserve historical evidence without using it for authorization.

### Phase 7: Policy cleanup and recipient UX consolidation

**Status: REOPENED - ordinary B2B directional-policy correction is verified; current handoff keeps
this phase open pending final browser certification. The obsolete relationship model remains fully
removed from code and schema.**

Post-implementation correction:

- Ordinary B2B policy decisions now use `OrganizationTrustExchangePolicyService` rather than the
  removed `hasActiveTrust` boolean. It combines both policy directions with relationship,
  suspension, review, policy-expiry, and organization eligibility.
- Initial registered-user and general-email account resolution and manage-access user/group grants
  all continue through `OrganizationExchangePolicyService` with explicit sender organization
  context. The user path evaluates every current active recipient organization and the group path
  evaluates the declared owning organization.
- The external-customer policy and B2B opt-out retain their separate semantics.
- Positive, denial, tampering, stale-state, and no-partial-write coverage is recorded in the audit
  correction verification update above. Full backend and clean PostgreSQL migration verification
  pass. No persistence change or new migration was required.
- Phase 1 acceptance-time general-email revalidation now reuses this corrected policy boundary
  rather than duplicating trust repositories or policy rules.

Completed work:

- Added forward-only `V64__trusted_organization_b2b_policy.sql`. It drops the obsolete
  `organization_exchange_link` table (with its foreign keys) via `DROP TABLE ... CASCADE`, drops the
  permissive `allow_share_without_pairing` column, and adds the positive
  `require_trusted_organization_for_b2b` column (`NOT NULL DEFAULT TRUE`). The default preserves the
  prior restrictive posture in which a cross-organization share was blocked unless a relationship
  existed. V1 and every applied migration were left unedited.
- Deleted the obsolete `OrganizationExchangeLink` entity and its `LinkStatus` enum,
  `OrganizationExchangeLinkRepository`, `OrganizationExchangeLinkService`,
  `OrganizationExchangeLinkBasicDto`, its `BasicEntityToDtoTransformer.toDto` method, the dead
  `LinkedOrgAppUserDto`/`LinkedOrgAppUserPersonDto` projections and their transformer methods (residue
  of the Phase 0 member-enumeration removal), and the `OrganizationLinkNotFoundException` with its
  dangling import in `OrganizationGroupResource`.
- Refactored `OrganizationExchangePolicyService` to be a sharing decision that accepts the caller's
  validated active organization as an explicit parameter instead of reading `AuthTokenContext`. It no
  longer calls `primaryOrganizationId`; it derives the recipient's organizations from
  `activeOrganizationIds` (correct for multi-organization members). The B2B gate now requires an
  active, non-effectively-suspended trust relationship via the then-new
  `OrganizationTrustRelationshipService.hasActiveTrust`, and reads
  `requireTrustedOrganizationForB2b` instead of `allowShareWithoutPairing`. User-facing messages use
  trusted-organization wording. The distinct `allowExternalCustomerSharing` (B2C) path is retained.
- Updated the two policy callers to pass explicit active organization context:
  `ExchangeRecipientSelectionResolver` (registered user, external email, internal group, personal
  group) and `ExchangeAccessManagementService.enforceSharingPolicy` (reads
  `authTokenContext.activeOrganizationId` and passes it in). The trusted group and trusted person
  selection paths already validate through `TrustedRecipientValidationService`.
- Renamed the setting across `OrganizationSettings`, `OrganizationSettingsDto`,
  `DetailedEntityToDtoTransformer`, `SettingsService` (update guard and default now
  `requireTrustedOrganizationForB2b = true`), and the authorization method
  `validateUpdateTrustedOrganizationB2bSetting`.
- Frontend: renamed the `OrganizationSettingsDto.requireTrustedOrganizationForB2b` field; relabelled
  the Organization Preferences toggle to "Require a trusted organization for sharing with other
  organizations" (id `switch-require-trusted-organization-for-b2b`) and its read-only summary; added
  the missing `id` attributes on the recipient-mode `Field`/`Radio`s, the External-recipient `Badge`,
  the recipient-role `Field`, and the role `Option`s in `ExchangeInitiationRecipientsTab`; deleted the
  dead `organizationApiTesting.ts` mock module (unused, carried obsolete "paired" terminology).
- Purged remaining pairing terminology: the org-group visibility audit text now reads "Made visible
  to trusted organizations", the `PrincipalGroup.externallyPublished` doc comment no longer says
  "paired", and the disabled `ExchangePrimaryRecipientIdentityPermutationTest` case names use
  trusted-organization wording.
- Extended the Trusted Organizations help article with the positive B2B requirement setting (article
  is 149 lines, under the 150-line limit).
- Updated tests: originally rewrote `OrganizationExchangePolicyGroupTest` to mock the Phase 7
  active-trust gate and the new method signatures; extended
  `AuditMigrationUpgradeContractTest` to assert current version 64, the dropped table and column, and
  the new column. The `OrganizationExchangeLinkResource` non-existence guard and the discovery test's
  negative `allowShareWithoutPairing` guard remain valid.

Important decisions:

- The setting semantics were inverted from permissive to positive. Old default
  `allow_share_without_pairing = false` (relationship required) maps to new default
  `require_trusted_organization_for_b2b = true` (trust required), preserving behaviour.
- The recipient organization is resolved with `activeOrganizationIds`, not a single primary
  organization, so a recipient who belongs to several organizations is treated as internal when any
  of their organizations is the initiator's, and B2B trust is satisfied if any of their organizations
  has active trust with the initiator.
- The one surviving `primaryOrganizationId` caller is `AppUserResource.getSignInAppUser`, a sign-in
  profile projection that is not a sharing, Exchange, or trust decision, so it was intentionally
  left. No sharing decision calls `primaryOrganizationId`.
- No DI cycle is introduced: `OrganizationExchangePolicyService` -> `OrganizationTrustRelationshipService`
  and the relationship service's only edge back toward sharing code is the lazy
  `Provider<TrustedGroupAccessReconciliationService>`.
- No compatibility read, dual write, backfill, fallback path, or new AWS service was added.

Changed code areas:

- `V64__trusted_organization_b2b_policy.sql`; `OrganizationSettings`, `OrganizationSettingsDto`,
  `DetailedEntityToDtoTransformer`, `SettingsService`, `ServiceActionAuthorizationService`.
- `OrganizationExchangePolicyService`, `OrganizationTrustRelationshipService` (the original
  Phase 7 active-trust gate, removed by the post-implementation correction),
  `ExchangeRecipientSelectionResolver`, `ExchangeAccessManagementService`.
- Deleted `OrganizationExchangeLink`, its repository and service, `OrganizationExchangeLinkBasicDto`,
  `LinkedOrgAppUserDto`/`LinkedOrgAppUserPersonDto` and their transformer methods,
  `OrganizationLinkNotFoundException`, `web-app/src/services/organizationApiTesting.ts`.
- `OrganizationGroupService` audit text, `PrincipalGroup` doc comment.
- Frontend `models.tsx`, `OrganizationDetailsTab.tsx`, `ExchangeInitiationRecipientsTab.tsx`,
  `trustedOrganizationsAdministrationArticle.tsx`.
- Tests `OrganizationExchangePolicyGroupTest`, `AuditMigrationUpgradeContractTest`,
  `ExchangePrimaryRecipientIdentityPermutationTest`.

Verification commands and results:

- `.\mvnw.cmd test -DskipFrontend=true "-Dkotlin.compiler.execution.strategy=in-process"`: BUILD
  SUCCESS, 909 tests run, 0 failures, 0 errors, 166 skipped. Docker was available, so the
  Testcontainers PostgreSQL 17 `AuditMigrationUpgradeContractTest` ran for real and certified a clean
  upgrade through V64 (dropped `organization_exchange_link`, dropped `allow_share_without_pairing`,
  added `require_trusted_organization_for_b2b`, current version 64).
- `npx.cmd tsc --noEmit` in `web-app`: PASS (exit 0). This whole-project type-check confirms the
  deleted mock module and the renamed setting field have no dangling references.
- `npm.cmd test -- --run` in `web-app`: 197 passed, 3 failed. The 3 failures are the pre-existing
  `AuditEventDetail.test.tsx` copy-button failures carried from Phases 1-6 (audit UI, untouched here).
- Targeted ESLint over the touched frontend files: the trusted-recipient and models changes are
  clean. `OrganizationDetailsTab.tsx` reports 9 pre-existing errors (unused `Tab`/`TabList`/
  `SelectTab*`/`OrganizationOnboardingDialog` imports, unused onboarding-dialog state, one empty
  block, and a `getOrganization` hook-dep warning) and `ExchangeInitiationRecipientsTab.tsx` reports 2
  pre-existing warnings (`react-refresh/only-export-components`, a `saveCurrentModeSnapshot` hook
  dep). All are pre-existing dead code unrelated to the cutover; the Phase 7 diff to both files is
  limited to the setting toggle/summary and required `id` attributes.
- Help-doc size checks: Trusted Organizations article 149 lines (< 150), `adminOperationsSection.tsx`
  99 lines (< 300), `helpDocsRegistry.tsx` 58 lines (< 60).
- `git diff --check`: PASS (only expected LF/CRLF warnings).
- AGENTS.md character scan over changed code files: no em dash, arrow, or emoji. One pre-existing em
  dash in a `SettingsService` comment was corrected while editing that file.

Known unrelated failures:

- `web-app/src/app/audit/__tests__/AuditEventDetail.test.tsx` retains 3 failures (actor/target
  copy-button elements not found). Carried from Phases 1-6; no audit UI changed here.

Remaining work and risks:

- Pre-existing lint debt in `OrganizationDetailsTab.tsx` (dead onboarding-dialog imports/state and an
  empty block) and the large `ExchangeInitiationRecipientsTab.tsx` (~300 lines, above the ~150-line
  component guideline) were left untouched beyond the required Phase 7 edits. Splitting that shell and
  clearing the dead imports are candidates for Phase 8 certification.
- Owner recovery for expired or invalid pending trusted-person invitations (manage-access
  `ExchangeRecipient` convergence) remains open and is now a Phase 8 item; the plan previously parked
  it here.

Pre-commit findings owned partly by this phase (all resolved):

- Stale relationship/policy state on active-organization switch: the immediate correctness fix landed
  with the Phase 3 findings resolution; no stale trusted state is retained.
- Action dialogs bound to an immutable relationship ID and version: resolved in the Phase 3 findings
  resolution.
- The recipient UX no longer exposes a verification method that cannot produce a valid discriminated
  selection (closed by Phase 6).

Original work checklist, previously recorded as satisfied. The 2026-07-18 audit reopens policy
behavior covered by items 1 and 3:

1. Replace `allowShareWithoutPairing` with the positive B2B policy setting.
2. Keep the discovery opt-in introduced by V60 separate while introducing the positive B2B trust
   requirement and retaining the distinct external-customer sharing policy.
3. Converge initiation and manage-access sharing policy on explicit active organization input.
4. Remove primary-organization inference from Exchange and trust paths.
5. Complete responsive, loading, empty, and error states.
6. Certify that initiation callers remain on discriminated selections and remove only residual
   obsolete response or UI state after its callers have migrated. The removed nullable initiation
   request fields were not recreated.
7. Complete the clean cutover by dropping obsolete relationship persistence and settings and
   deleting their remaining backend, frontend, test, notification, and audit callers.

Clean-cutover exit criteria (met). The ordinary B2B policy correctness criteria are reopened by the
2026-07-18 post-implementation audit:

- Every sharing path evaluates the same policy service (`OrganizationExchangePolicyService` with
  explicit caller-organization input; trusted paths validate through `TrustedRecipientValidationService`).
- Organization discovery is not controlled by a sharing permission (`discoverableForTrustRequests`).
- Active organization switching cannot retain stale trusted state.
- Frontend recipient state contains no `any`.

Historical next-session context, superseded by the 2026-07-18 post-implementation audit:

- At the time of this historical handoff, Phases 0 through 7 were recorded as complete. The
  2026-07-18 audit later reopened Phases 1, 4, 7, and 8; the acceptance correction subsequently
  closed Phase 1. The obsolete
  `OrganizationExchangeLink` model, the `allow_share_without_pairing` setting, and all their callers
  no longer exist. Latest Flyway version is V64.
- Continue with Phase 8 (documentation, deletion sweep, and certification). Key relevant code:
  `OrganizationExchangePolicyService.assertCanShareWithUser/Group`,
  `OrganizationTrustExchangePolicyService`, `V64__trusted_organization_b2b_policy.sql`.
- Phase 8 should also address the pre-existing `OrganizationDetailsTab.tsx` lint debt, evaluate
  splitting `ExchangeInitiationRecipientsTab.tsx`, and implement owner recovery for expired/invalid
  pending trusted-person invitations.

### Phase 8: Documentation, deletion, and certification

**Status: REOPENED - the privacy defect is corrected and verified; participant and replacement
browser certification remains open.**

Completed work:

- Deletion sweep. Confirmed no obsolete pairing/link model remains in production code: no
  `OrganizationExchangeLink*`, `LinkStatus`, `LinkedOrgAppUser*`, `OrganizationLinkNotFound`,
  `fetchPairedOrganization*`, `allowShareWithoutPairing`, or `organizationExchange(.ts)`/
  `organizationApiTesting.ts`. The only remaining references are legitimate: `V64` drops the
  obsolete table/column, `AuditMigrationUpgradeContractTest` asserts the drop, the
  `OrganizationDirectorySearchServiceTest` asserts the discovery JPQL does not use
  `allowShareWithoutPairing`, and `OrganizationTrustResourceContractTest` asserts the old resource
  file no longer exists. Updated `docs/EXCHANGE-MANUAL-ACCESS-TEST-MATRIX.md`, which still used
  `pairing`/`paired`/`allowShareWithoutPairing` terminology and a now-false claim that a trusted
  person is a submit dead end, to trusted-organization terminology and the current Phase 6 behavior.
- Help-doc sweep. Searched the help docs for pairing/linked/external-organization/recipient/
  participant/group/sharing/acceptance terminology. No obsolete pairing wording remains (only
  "key/value pairs" in the variables article). Read the Trusted Organizations administration,
  manage-access, and exchanges articles in full; all statements are accurate for the current model.
  Added a "Replacing a pending primary recipient" section to `manageAccessArticle.tsx` (132 lines).
  Trusted Organizations article is 140 lines, `adminOperationsSection.tsx` 92 lines,
  `exchangesSection.tsx` 61 lines, `helpDocsRegistry.tsx` 58 lines - all within the AGENTS.md limits.
- Dead-code cleanup in `OrganizationDetailsTab.tsx`: removed the unused `Tab`/`TabList`/`TabValue`/
  `SelectTabData`/`SelectTabEvent` and `OrganizationOnboardingDialog` imports and the unused
  onboarding-dialog state, replaced the empty `AxiosError` catch block with a narrowed non-Axios
  branch, and wrapped `getOrganization` in `useCallback` with correct effect dependencies.
- Split the oversized `ExchangeInitiationRecipientsTab.tsx` (was ~300 lines, now 143). Extracted
  `recipient-role-selector/RecipientRoleSelector.tsx` (+ styles), `external-recipient-badge/
  ExternalRecipientBadge.tsx` (+ styles), and a `useRecipientModeSnapshots.ts` hook, and moved the
  `ExchangeInitiationRecipientMode` enum into its own `exchangeInitiationRecipientMode.ts` module so
  the tab file no longer mixes a component export with a constant export (clears the
  `react-refresh/only-export-components` warning). Cleaned an "exch-" copy-paste artifact from the
  role-description help text while extracting it.
- Owner recovery (full stack). Added `POST /exchanges/{exchangeId}/access/primary-recipient` and
  `ExchangeAccessManagementService.replacePrimaryRecipient`, which lets an Exchange owner replace a
  pending primary recipient on a draft (`INITIATED`) Exchange whose trusted verification, membership,
  relationship, or policy is no longer valid. It reuses the discriminated selection resolver (so
  trust/policy are revalidated), deletes the old `ExchangeRecipient` and its attestation, revokes the
  old primary Share (cascading to inherited group-member Shares), then grants a fresh Share, binds a
  new PENDING primary recipient, and re-attests; trusted selections consume the resolution under a
  row lock, force sign-in, and hold the Share PENDING_APPROVAL until acceptance. `EXTERNAL_EMAIL`
  replacement is rejected (that path is served by Add access, which owns temporary-user creation).
  The post-implementation finding 3 correction restricted the endpoint further to only
  `TRUSTED_PERSON` and `TRUSTED_GROUP`; registered users, internal groups, and personal groups are
  also rejected before recipient resolution or mutation.
  Added `ExchangeRecipientService.deleteBinding` and
  `ExchangeRecipientAttestationService.deleteForRecipient`. Frontend: added
  `ReplacePrimaryRecipientPanel` (reusing `TrustedOrganizationRecipients`), a "Replace recipient"
  action on the Manage access Summary primary card (shown only for `INITIATED` Exchanges), a
  `replaceExchangePrimaryRecipient` API call, and a help section. `OrganizationTrustException` maps
  to a generic `409 CONFLICT` in the resource error mapper.

Important decisions:

- Owner recovery reuses the existing V58 `exchange_recipient` and V61
  `exchange_recipient_attestation` tables. Owner recovery required no migration. The later
  trusted-participant correction added V65, which is the current Flyway version.
- Replacement is restricted to a `PENDING` primary on an `INITIATED` Exchange, so an already
  accepted or rejected recipient cannot be silently displaced, and terminal or active Exchanges are
  never mutated.
- The new Share preserves the previous primary's role and constraints; the replacement is always a
  fresh PENDING invitation. Trusted selections continue to force sign-in and acceptance.
- The replacement audit trail relies on the existing `SHARE_REVOKE`/`SHARE_GRANT` events emitted by
  `ShareService`, matching how `grantAccess`/`revokeAccess` audit today. No new audit event type was
  added, so the audit catalog version is unchanged.
- Owner recovery does not re-run draft-approval or acceptance workflows; the recipient's acceptance
  decision is the activation gate (see remaining risks).

Changed code areas:

- Backend: `ExchangeAccessManagementService` (new `replacePrimaryRecipient` + injections),
  `ExchangeRecipientService.deleteBinding`, `ExchangeRecipientAttestationService.deleteForRecipient`,
  `ExchangeResource` (new endpoint + `OrganizationTrustException` mapping),
  `RequestsResponses.ReplacePrimaryRecipientRequest`.
- Backend tests: new `ExchangeAccessManagementServiceTest` (4 cases), a `deleteBinding` case in
  `ExchangeRecipientServiceTest`, and updated `ExchangeAccessManagementRecipientBindingTest`
  constructor.
- Frontend: `ReplacePrimaryRecipientPanel(.tsx/Styles)`, `ExchangeAccessManagementDialog(.tsx)` +
  `ExchangeAccessManagementDialogStyles`, `exchangeApi.replaceExchangePrimaryRecipient`,
  `OrganizationDetailsTab.tsx`, `ExchangeInitiationRecipientsTab.tsx` (+ extracted components/hook/
  enum module and their import sites in `ExchangeInitiation.tsx`, `useExchangeInitiatingState.ts`,
  `exchangeInitiationRecipientSelection(.test).ts`).
- Docs: `manageAccessArticle.tsx`, `docs/EXCHANGE-MANUAL-ACCESS-TEST-MATRIX.md`.

Verification commands and results:

- `.\mvnw.cmd test -DskipFrontend=true "-Dkotlin.compiler.execution.strategy=in-process"`: BUILD
  SUCCESS, 914 tests, 0 failures, 0 errors, 166 skipped. Docker was available, so the Testcontainers
  PostgreSQL 17 `AuditMigrationUpgradeContractTest` (clean init + upgrade through V64) and
  `OrganizationTrustResumeConcurrencyContractTest` ran for real and passed. Test count rose from 909
  to 914 (the 5 new owner-recovery cases).
- Focused owner-recovery run (`ExchangeAccessManagementServiceTest`): 4 tests, 0 failures.
- `npx.cmd tsc --noEmit` in `web-app`: PASS.
- `npm.cmd test -- --run` in `web-app`: 197 passed, 3 failed. The 3 failures are the pre-existing
  `AuditEventDetail.test.tsx` copy-button failures carried from Phases 1-7 (audit UI, untouched).
- Consolidated ESLint over every authored/changed file: clean, except pre-existing debt in
  `ExchangeInitiation.tsx` (see known failures). The split components, hook, enum module, replace
  panel, dialog, API, `OrganizationDetailsTab.tsx`, and manage-access article all report 0 problems.
- Help-doc size checks: all within AGENTS.md limits (see completed work).
- `git diff --check`: PASS (only expected LF/CRLF warnings).
- AGENTS.md character scan over changed files: no em dash, arrow, or emoji. (A pre-existing en dash
  `1-30 days` in the untouched `ManageAccessHelpGuide.tsx` is an en dash, not the forbidden em dash.)

Adversarial review (owner recovery and Phase 8 surface):

- Authorization: `replacePrimaryRecipient` is owner-gated through `requireSessionOwnerAndReturn`
  (`EXCHANGE_MANAGE_ACCESS`); a non-owner gets `ForbiddenException` with an `AUTHORIZATION_DENIED`
  audit row, and a guessed Exchange id returns 404 without leaking existence.
- Direct IDs / tampering: only a `PENDING` primary on an `INITIATED` Exchange is replaceable;
  accepted, rejected, not-required, terminal, and active states fail closed with a client error.
- Trust/policy revalidation: trusted selections re-run `TrustedRecipientValidationService` /
  `prepareForInitiation`, which fail closed on inactive trust, ineligible membership, unverified or
  inactive organization, or disallowed direction; failures surface as a generic `409` with no leak.
- Replay / single use: the resolution is consumed under a row lock bound to the Exchange id and
  rolls back with the transaction; wrong actor, caller organization, or target organization is
  rejected by `consumeForExchange`.
- Share inheritance: revoking the old primary cascades to inherited group-member Shares; a new
  trusted group Share stays PENDING_APPROVAL so members are not materialized until acceptance.
- No orphan/duplicate: the old binding and attestation are deleted before the new PRIMARY binding is
  created, honoring the single-primary and unique `direct_share_id` constraints.
- Logs/audit: no raw searched email is written; the replacement relies on identifier-only
  `SHARE_REVOKE`/`SHARE_GRANT` events. No new raw-email output channel was added.
- This historical adversarial review found no critical or high-severity issue in the owner-recovery
  surface it examined. The later post-implementation audit found broader policy and activation
  defects that supersede this conclusion.

Post-audit frontend corrections:

- Restored the actor and target copy controls in `CopyableFieldLabel`; the focused audit detail
  suite now passes all 10 tests.
- Removed the `ExchangeInitiation.tsx` unused import and callback parameters and completed the
  subscription effect dependency list.
- Corrected all trust, recipient, and Manage access lint findings. ESLint over all 46 changed
  frontend TypeScript files passes with zero errors and zero warnings.
- Split the audited oversized recipient, trust, and Manage access components. The named parent
  components and every new TSX child are below 150 lines.
- Final frontend verification passes: 39 test files, 204 tests, zero failures, and zero type errors.
- The exact local file `/.claude/settings.local.json` is ignored and absent from Git status.

Remaining work and risks:

- Owner recovery intentionally does not re-run draft-approval or acceptance workflows on
  replacement; the new recipient's acceptance is the activation gate. If a future requirement needs
  a fresh workflow evaluation when the primary changes, that is a follow-up.
- Concurrent replacement by two co-owners on the same Exchange is not serialized beyond the
  transaction and DB single-primary constraint; one transaction will fail. This is a low-risk edge
  case for a single-owner workflow.

Exit criteria status: REOPENED under the current handoff until the remaining browser certification
matrix passes.

- No obsolete pairing behavior remains in active code.
- Help content matches the released UI and policy and stays within size limits.
- The pre-authentication high-severity privacy finding is corrected and verified. The remaining
  participant and replacement browser checks are still open.
- Backend tests, frontend tests, type checking, targeted ESLint, and Docker-backed clean
  initialization all pass with the exact results recorded in the audit correction.

## Historical Consolidated Verification and Handoff - 2026-07-17

This section records the earlier verification history. It is superseded by the 2026-07-18
post-implementation audit correction and must not be used to claim current completion.

### Verification commands and results

- Backend `.\mvnw.cmd test -DskipFrontend=true "-Dkotlin.compiler.execution.strategy=in-process"`:
  BUILD SUCCESS, 909 tests run, 0 failures, 0 errors, 166 skipped. Docker was available, so the
  Testcontainers PostgreSQL 17 migration contract and the new resume concurrency contract ran for
  real (no environment skips or errors).
- Docker-backed contract tests specifically:
  `AuditMigrationUpgradeContractTest` (asserts current version 63 and rejects foreign, inherited
  non-direct, and owner Share bindings) and `OrganizationTrustResumeConcurrencyContractTest` (proves
  concurrent-resume serialization) both PASS.
- Focused backend run (`ExchangeRecipientServiceTest`, `TrustedRecipientValidationServiceTest`,
  `OrganizationTrustRelationshipServiceTest`, `ExchangeInitiationFieldsTest`): 37 tests, 0 failures.
- `npx.cmd tsc --noEmit` in `web-app`: PASS.
- `npm.cmd test -- --run` in `web-app`: 197 passed, 3 failed. The 3 failures are the pre-existing
  `AuditEventDetail.test.tsx` copy-button failures recorded in Phases 1-5 (audit UI, untouched by
  this work).
- Targeted ESLint over the trust administration, trusted-recipient, selection-builder, initiation
  state, and trust client files: PASS (0 errors; the 2 warnings on
  `ExchangeInitiationRecipientsTab.tsx` are pre-existing and not introduced here).
- Help-doc size checks: Trusted Organizations article 144 lines (< 150), `adminOperationsSection.tsx`
  99 lines (< 300), `helpDocsRegistry.tsx` 58 lines (< 60).
- `git diff --check`: PASS (only expected LF/CRLF line-ending warnings).
- AGENTS.md character scan over changed files: no em dash, arrow, or emoji.

### Known unrelated failures

- `web-app/src/app/audit/__tests__/AuditEventDetail.test.tsx` retains 3 failures: the tests cannot
  find the actor and target copy-button elements. No audit UI or audit test file changed in this
  work. These are the same failures carried from Phases 1-5.

### Migration state

- At the time of this historical handoff, the latest Flyway version was V64. V58-V63 were not
  edited. V64
  drops the obsolete `organization_exchange_link` table and the `allow_share_without_pairing` column
  and adds `require_trusted_organization_for_b2b` (`NOT NULL DEFAULT TRUE`). The current version is
  V65, which adds the trusted-participant acceptance invariant. The Docker-backed
  `AuditMigrationUpgradeContractTest` certifies a clean upgrade through V65.

### Remaining work and risks

- The obsolete `OrganizationExchangeLink` persistence, service, DTOs, exception,
  `allowShareWithoutPairing` setting, and accepted-link lookup are deleted.
- Phase 1 is closed by the verified general-email acceptance correction. Findings 3 through 7 are
  closed by the current post-implementation audit correction. Phases 4 and 8 are complete.
- Owner recovery exists full stack and now permits only trusted person or trusted group
  replacements, both held pending until recipient acceptance.
- The audit copy-control regression, targeted ESLint findings, and oversized updated components are
  corrected and fully verified.
- `.claude/settings.local.json` is ignored by an exact root rule and remains outside Git status.

### Next-session context

- The audit correction has no remaining implementation item. The next task is manual browser
  certification of trusted participant invitations and trusted-primary replacement.
- Current verification (2026-07-18): backend 961 tests green with Docker-backed migration through
  V65, `web-app` `tsc --noEmit` passes, all 204 frontend tests pass, and ESLint over all 46 changed
  frontend TypeScript files is clean.
- Relevant new Phase 8 code: `ExchangeAccessManagementService.replacePrimaryRecipient`,
  `ExchangeRecipientService.deleteBinding`, `ExchangeRecipientAttestationService.deleteForRecipient`,
  `ExchangeResource` `POST /exchanges/{exchangeId}/access/primary-recipient`,
  `RequestsResponses.ReplacePrimaryRecipientRequest`, and frontend `ReplacePrimaryRecipientPanel` +
  `exchangeApi.replaceExchangePrimaryRecipient`.
- Relevant Phase 7 code: `OrganizationExchangePolicyService.assertCanShareWithUser/Group`,
  `OrganizationTrustExchangePolicyService`, `V64__trusted_organization_b2b_policy.sql`.
- Relevant Phase 6 code: `TrustedPersonRecipientSelectionRequest`,
  `ExchangeRecipientSelectionResolver.resolveTrustedPerson`,
  `ExternalIdentityResolutionService.prepareForInitiation`,
  `TrustedRecipientValidationService.validatePersonAttestation`,
  `ExchangeRecipientAttestationService.createPersonAttestation`.

## Required Test Matrix

### Relationship and policy

- Requester, requested organization, unrelated organization, no active organization, and personal
  mode.
- Active and inactive organizations; verified and unverified organizations.
- Pending, active, rejected, withdrawn, request-expired, ended, one-side suspended, both-side
  suspended, resumed, and policy-expired.
- Duplicate pair, reversed pair, self-pair, concurrent request, concurrent decision, and stale
  optimistic-lock version.
- Every combination of sender outbound and receiver inbound policy.
- Only policy owner can edit; partner receives only allowed projection.
- New relationship generation after rejection or ending and configured cooldown.

### Organization discovery

- Minimum query length, result cap, exact registration match, name match, inactive, unverified,
  hidden, self, already-related, and unrelated role.
- No use of `allowShareWithoutPairing` for discoverability.

### Identity resolution

- Exact normalized match and case normalization.
- Missing account, inactive account, deprovisioned account, no membership, suspended membership,
  expired membership, target mismatch, and multiple memberships.
- Policy disabled, relationship inactive, sender denied, receiver denied, wrong active organization,
  wrong actor, expired resolution, replay, and concurrent consumption.
- Display-name projection enabled and disabled.
- Raw email absent from URL, logs, audit, incidents, metrics, traces, and analytics.
- Rate limiting by actor, caller organization, target organization, relationship, and keyed email.

### Published groups

- Active published target-owned ORG group.
- Unpublished, inactive, deleted, personal, shared-project, foreign, and guessed group.
- Group discovery disabled and directional Exchange policy denied.
- No group members in response.

### Exchange initiation

- Trusted person and trusted group as primary recipient.
- Trusted person and trusted group as participant.
- Wrong resolution actor, caller organization, target organization, relationship, policy revision,
  and consumed resolution.
- Group substitution, user substitution, target organization substitution, and client-supplied
  attestation fields.
- Exchange owner, settings, variables, workflows, audit owner, and notifications always use sender
  active organization.
- Transaction rollback leaves resolution unconsumed and no partial Shares or attestations.

### Acceptance

- Attested person accepts with matching account and membership.
- Different account, ordinary participant, viewer, owner, group member, and unauthenticated caller
  fail to decide primary Exchange acceptance.
- Group OWNER and MANAGER accept; MEMBER and OBSERVER fail.
- Invited trusted person accepts or rejects only its own participant invitation.
- Current trusted group OWNER and MANAGER accept or reject only that group participant invitation;
  unrelated users, MEMBERs, OBSERVERs, and client-substituted recipient IDs fail.
- Trusted participant acceptance activates only its direct Share and does not decide the Exchange,
  primary recipient, or another participant. Trusted participant rejection revokes only its Share.
- Primary-recipient and workflow activation exclude unresolved trusted participant Shares.
- Relationship or policy changes before acceptance.
- Account, membership, group publication, group status, and organization status changes.
- Workflow present and absent, repeated decision, concurrent decision, stale participant decision,
  reject, rescind, rollback, and end.
- No generic Exchange update can perform recipient acceptance.

### Relationship changes after initiation

- Suspension or ending before initiation, after initiation, before acceptance, and after acceptance.
- Existing accepted direct person access remains.
- Existing materialized group access remains.
- Removed group member loses access.
- New group member is blocked while suspended or ended.
- New group member is reconciled after suspension is cleared.

### Authorization and outputs

- Human organization roles, APP_ADMIN, APP_AUDITOR, APP_SUPPORT, APPLICATION, SERVICE_ACCOUNT, and
  PUBLIC_LINK where those principals can reach the API.
- List, direct ID, picker, audit, export, notification, email, realtime, workflow, and integration
  output consistency.
- Active organization switch and stale frontend cache isolation.

### Frontend quality

- Responsive desktop, tablet, and narrow mobile layouts.
- Keyboard and screen-reader operation.
- Loading, empty, generic error, expiry, retry, stale request, and rate-limit states.
- Dialog footer action order, circular buttons, component IDs, and separate Fluent styles.

## Verification Commands

After implementation changes, run at minimum:

```text
.\mvnw.cmd test -DskipFrontend=true
cd web-app
npx tsc --noEmit
npm test
```

Also run clean database initialization from an empty development database.

## Definition of Done

The feature is complete only when:

- The containment phase is complete.
- Every organization-scoped operation uses validated active organization context.
- Relationship lifecycle and per-party suspension are correctly enforced.
- Directional policies combine sender and receiver decisions explicitly.
- Trusted member lookup is exact-email-only, short-lived, single-use, rate-limited, and redacted.
- Published groups expose no membership directory.
- Shares grant access and ExchangeRecipient records business purpose and acceptance authority.
- Trusted evidence is attached per recipient and never substitutes for current authorization.
- Trusted person acceptance matches the attested account and membership.
- Trusted group acceptance is limited to active group OWNERs and MANAGERs.
- Relationship changes follow the documented pending and accepted Exchange rules.
- Obsolete pairing code and terminology are removed.
- Help documentation, audit, notifications, exports, workflows, and realtime output are consistent.
- Tests, type checking, clean database initialization, and adversarial review pass.

## Continuation handoff - 2026-07-19

This is the current handoff. The two browser findings remain open and the feature is not complete.

### Work completed

- Confirmed the Product Contract decision already recorded above: a trusted person or published
  trusted group added as an additional participant requires independent acceptance. Its direct
  Share starts as `PENDING_APPROVAL`, its recipient binding starts as `PENDING`, and accepting or
  rejecting it must not decide the Exchange or primary recipient.
- Added the thin REST adapter `POST /exchanges/{exchangeId}/recipient-invitations` and delegated
  the mutation to `ExchangeAccessManagementService.inviteTrustedParticipant`.
- Implemented the trusted participant service path through the existing recipient resolver,
  Share service, recipient service, and attestation service. It validates explicit trusted
  selection evidence, creates an inactive Share and pending participant binding, consumes person
  evidence transactionally, and returns the access view.
- Added focused backend coverage for success, authorization denial, selection-type tampering,
  stale resolver state, and transactional rollback.
- Split the oversized Add person frontend into focused components. Manage access now has separate
  `Person` and `Trusted Organization` tabs. The trusted tab requires a verified member or published
  group and states that access remains inactive until acceptance.
- Fixed an unstable callback in both trusted-participant addition and trusted-primary replacement.
  The previous inline callback changed on every render and caused the shared trusted-recipient hook
  to clear a newly selected organization immediately.
- Updated the Manage access and Trusted Organizations help articles with the exact UI path and
  inactive-until-accepted behavior. Both remain below the article size limit.
- Browser verification created a trusted-person participant invitation. PostgreSQL proved recipient
  `72b3cdbd-8c8a-470b-8524-6876e05ddcf2` is `PENDING`, Share
  `f0f3b277-58de-49d8-bc8f-1201c23cf584` is `PENDING_APPROVAL`, the primary recipient remains
  `PENDING`, and Exchange `3e0c87e3-7db2-4b50-b0f8-14fc17bf27e7` remains `INITIATED`.
- Browser certification exposed a separate account-boundary defect in organization member
  addition: adding an already registered email created another active `app_user`. The new
  membership and invitation targeted the duplicate, while sign-in resolved the older account.
  `OrganizationAppUserService.addAppUser` now reuses an existing active registered account,
  preserves its credentials and person record, and denies inactive or deprovisioned accounts.
  Focused positive and stale-account denial tests were added.

### Design decisions

- Ordinary email access retains its existing generic Manage access path. Trusted participant
  invitations use a dedicated explicit-selection resource so trusted evidence is never inferred
  from a free-form email.
- Registered-account reuse is a service-layer decision. No resource accesses a repository.
- The existing duplicate local fixture rows were not merged, deleted, or backfilled. No
  compatibility path, fallback read, dual write, or backfill was added.
- No AWS service or paid resource was added. The previously approved temporary root session was
  used only for local SES verification email support.

### Files changed in this continuation

- Backend production:
  `RequestsResponses.kt`, `ExchangeResource.kt`, `ExchangeAccessManagementService.kt`, and
  `OrganizationAppUserService.kt`.
- Backend tests:
  `ExchangeAccessManagementServiceTest.kt`,
  `ExchangePrimaryRecipientReplacementResourceTest.kt`, and
  `CrossOrgMemberManagementTest.kt`.
- Frontend API and types:
  `exchangeApi.ts` and `services/types/dtos.ts`.
- Frontend components:
  `AddPersonPanel.tsx`, `AddPersonPanelStyles.tsx`,
  `ReplacePrimaryRecipientPanel.tsx`, and the new
  `registered-person-access-panel` and `trusted-participant-panel` folders.
- Frontend tests:
  `TrustedParticipantPanel.test.tsx`.
- Help:
  `manageAccessArticle.tsx` and `trustedOrganizationsAdministrationArticle.tsx`.
- Handoff:
  this implementation plan.

### Commands and exact results

- `.\mvnw.cmd -DskipFrontend=true "-Dkotlin.compiler.execution.strategy=in-process" -DskipTests test-compile`
  completed with `BUILD SUCCESS`.
- `.\mvnw.cmd test -DskipFrontend=true "-Dkotlin.compiler.execution.strategy=in-process" "-Dtest=ExchangeAccessManagementServiceTest,ExchangePrimaryRecipientReplacementResourceTest"`
  completed with `BUILD SUCCESS`: 17 tests, 0 failures, 0 errors, 0 skipped.
- `.\mvnw.cmd test -DskipFrontend=true "-Dkotlin.compiler.execution.strategy=in-process" "-Dtest=CrossOrgMemberManagementTest,ExchangeAccessManagementServiceTest,ExchangePrimaryRecipientReplacementResourceTest"`
  completed with `BUILD SUCCESS`: 27 tests, 0 failures, 0 errors, 0 skipped.
- `npm.cmd test -- --run src/app/exchanges/components/exchange-access-management-dialog/trusted-participant-panel/TrustedParticipantPanel.test.tsx`
  completed with 1 file and 2 tests passed.
- `npx.cmd tsc --noEmit` completed with exit 0.
- Help search for `manage access`, `trusted participant`, `trusted organization`,
  `additional participant`, and `recipient invitation` matched seven files. Every match was read
  in full. `manageAccessArticle.tsx` is 147 lines,
  `trustedOrganizationsAdministrationArticle.tsx` is 149 lines, the matched section is below
  300 lines, and `helpDocsRegistry.tsx` remains 58 lines.
- Browser and read-only PostgreSQL verification proved the new participant Share is inactive and
  the Exchange remains a draft, as recorded above.
- Added `V66__registered_email_uniqueness.sql`. It fails with a generic precondition error when
  duplicate registered accounts already exist, then creates the case-insensitive partial unique
  index `uq_app_user_registered_email_ci` on `LOWER(app_user.email)` for non-temporary accounts.
  No cleanup, merge, or backfill is performed by the migration.
- `.\mvnw.cmd test -DskipFrontend=true "-Dkotlin.compiler.execution.strategy=in-process" "-Dtest=AuditMigrationUpgradeContractTest"`
  completed with `BUILD SUCCESS`: 3 tests, 0 failures, 0 errors, 0 skipped. Testcontainers
  PostgreSQL 17 validated 63 migrations and upgraded a clean schema from V51 through V66.

### Remaining risks and open findings

1. **Trusted additional-participant browser certification remains open.**
   The inactive pre-acceptance proof passed. Acceptance and rejection isolation still require a
   clean distinct partner member. The old duplicate local fixture cannot provide that proof because
   sign-in resolves a different account. Do not remove this finding until separate accept and reject
   decisions prove that only the selected participant Share and binding change.
2. **Trusted-primary replacement browser certification remains open.**
   The callback reset defect is fixed, but full browser proof still must withdraw the old invitation,
   keep the replacement Share inactive until primary acceptance, and cover stale trust, policy
   denial, tampering, and rollback.
3. Full backend, full frontend, targeted ESLint over every changed frontend file,
   `git diff --check`, prohibited-character checks, and final clean PostgreSQL migration verification
   have not been rerun after this continuation. Do not mark either finding resolved before they pass.
4. The local duplicate account rows remain test-fixture contamination. Product code reuses an
   existing registered account, and V66 prevents another non-temporary account with the same
   case-insensitive email. V66 cannot apply to this contaminated local database until the duplicate
   is explicitly reviewed and corrected. The migration deliberately performs no cleanup or backfill.
5. The unrelated Windows audit archive-path defect remains open.

### Exact next-session starting point

1. Sign in as `mchrizy+codex-trust-sender@gmail.com`.
2. Through Settings, Administration, People, add a new member with an email not already present in
   `app_user`. Confirm the corrected service creates exactly one account and one active sender
   organization membership.
3. Sign back in as the receiver owner, revoke the unusable duplicate-target invitation through
   Manage access, and invite the clean member through Add person, Trusted Organization.
4. Prove pending state in PostgreSQL, then sign in as the clean member and accept. Create a separate
   invitation and reject it. Prove both decisions leave the primary binding and Exchange status
   unchanged.
5. Only after finding 1 is fully verified, perform trusted-primary replacement certification.
6. Run the complete required verification matrix and update this handoff with exact results.
