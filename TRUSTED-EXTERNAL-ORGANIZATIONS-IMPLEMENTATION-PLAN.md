# Trusted External Organizations Implementation Plan

## Purpose

This document replaces the earlier pairing plan with an implementation plan based on the current
DocuHyphen codebase.

Before implementing any part of this plan, read `AGENTS.md` and this document in full. `AGENTS.md`
is the controlling instruction set.

The feature is named **Trusted Organizations** in the product and code written for the new feature.
Legacy `pair`, `pairing`, `link`, and `linked organization` names are removed during cutover.

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

### Unsafe or obsolete paths that still exist

- `GET /organizations/linked/{organizationId}/app-users` still enumerates another organization's
  members.
- The current linked-group endpoint returns `PrincipalGroupDto`, whose `members` field exposes group
  membership even though the service comment says members are not enumerated.
- `ExternalOrganizationRecipients.tsx` still imports and calls the linked-organization user API.
- `OrganizationExchangeLink`, its repository, service, DTOs, endpoints, frontend services, and UI
  remain the live relationship model.
- Organization discovery currently loads all organizations and is tied to
  `allowShareWithoutPairing`.
- `OrganizationExchangePolicyService` reads `primaryOrganizationId` instead of the validated active
  organization.
- Exchange initiation validates direct users but does not apply the group policy to primary group
  recipients or group participants.
- Group-recipient initiation derives Exchange ownership, settings, variables, and workflow context
  from the recipient group's organization instead of the sender's active organization.
- Exchange acceptance and rejection begin with `EXCHANGE_VIEW`; there is no recipient-specific
  acceptance action.
- Recipient purpose is inferred from Share role, source, and ordering. Shares do not reliably
  distinguish the primary recipient from additional participants.
- The initiation request uses several nullable recipient fields instead of a discriminated
  recipient selection.
- Frontend and backend participant request contracts do not match cleanly.
- `ExchangeInitiationService`, `ExternalOrganizationRecipients.tsx`, `OrganizationTab.tsx`, and
  `OrganizationPairingTab.tsx` require decomposition before adding more behavior.
- Trust administration still uses legacy `@EnforceAdminAction` names and legacy audit APIs rather
  than the current resource-aware authorization and audit ledger consistently.

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

## Database Migration and Cutover

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

### Legacy-link migration

Before writing SQL, inspect production-like data for duplicate unordered organization pairs and
invalid self-links.

- Canonicalize each organization pair.
- Choose at most one current relationship generation per pair.
- Map `PENDING` to `PENDING`, `ACCEPTED` to `ACTIVE`, and `REJECTED` to `REJECTED`.
- Preserve request, linked, rejection, and message data where available.
- Create one party policy per organization.
- For migrated active links, preserve existing published-group behavior but keep member resolution
  disabled by default.
- Derive the positive B2B setting from the old setting without changing effective behavior.
- Record or report duplicates and invalid rows rather than choosing silently.

### Recipient binding migration

Existing Share data may not distinguish a primary recipient from a participant reliably. Before
backfill, produce a data report that classifies rows as deterministic or ambiguous.

- Backfill deterministic primary recipients and participants.
- Do not guess ambiguous records.
- If deployed data contains ambiguous active Exchanges, provide a one-time operational resolution
  script or keep the legacy acceptance path only for those identified Exchange IDs until they end.
- All newly created Exchanges must use `ExchangeRecipient` immediately after cutover.

### Application cutover

- New application code reads and writes only the new trust model.
- Do not dual-write old and new relationship tables.
- Retain the old table read-only for one release only if production rollback policy requires it.
- Remove old endpoints and frontend calls in the same release that switches reads.
- Drop the legacy table and obsolete setting in a later forward migration after verification.

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
- legacy link DTOs and enums
- stale organization-user state from trusted-recipient components

Replace query-string mutation APIs with typed JSON request bodies.

## Implementation Phases

### Phase 0: Containment and characterization

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
9. Inventory legacy relationship rows and ambiguous recipient Shares.

Exit criteria:

- No external organization member-list endpoint exists.
- Published group responses contain no member identities.
- Guessed, foreign, unpublished, inactive, or unpaired group IDs cannot create Shares.
- Recipient organization data cannot determine Exchange ownership.
- A general viewer or participant cannot accept or reject an Exchange.
- Migration data reports are available.

### Phase 1: Authorization and recipient foundations

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

### Phase 2: Trust persistence and migration

Work:

1. Add relationship, suspension, and party-policy entities and migrations.
2. Add constraints, indexes, policy revisioning, and optimistic locking.
3. Backfill valid legacy links and policies.
4. Add repositories used only by their owning services.
5. Implement relationship state transitions, request expiry scheduling, and per-party suspension.
6. Implement correlated audit events for both organizations.

Exit criteria:

- Invalid pairs, policy ownership, or suspension ownership cannot be persisted.
- Concurrent decisions and policy edits fail predictably.
- Migrated active links preserve group behavior without enabling member lookup.
- A clean database and an upgraded database both initialize successfully.

### Phase 3: Trust administration API and UI

Work:

1. Implement organization discovery with verified, active, opt-in filtering.
2. Implement relationship, decision, suspension, termination, and policy resources.
3. Keep all resources thin.
4. Build capability-driven Trusted Organizations administration UI.
5. Add email, in-app, realtime, and audit notifications using new terminology.
6. Remove legacy link administration endpoints and UI.

Exit criteria:

- Only the correct party can request, decide, suspend, resume, end, or change policy.
- Cross-organization guessed IDs fail closed.
- The UI exposes no operation the backend would authorize differently.
- Legacy pairing administration is absent.

### Phase 4: Published trusted groups

Work:

1. Implement the published-group query service and endpoint.
2. Enforce active relationship and compatible directional policy.
3. Return no group membership data.
4. Implement trusted group selection with the new initiation contract.
5. Persist `ExchangeRecipient` and group attestation.
6. Implement group manager acceptance.
7. Gate new inherited Share materialization on current trust policy.

Exit criteria:

- Only eligible published groups are visible and selectable.
- Client tampering cannot substitute another organization or group.
- Group OWNER and MANAGER acceptance works; other group roles fail.
- Relationship changes follow the documented group access rules.

### Phase 5: Blind exact-email member resolution

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

Work:

1. Add `TRUSTED_PERSON` to the discriminated initiation contract.
2. Consume resolutions under row lock in the initiation transaction.
3. Revalidate both policies, account, membership, and organizations.
4. Create the USER Share, recipient binding, and attestation atomically.
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

Work:

1. Replace `allowShareWithoutPairing` with the positive B2B policy setting.
2. Separate organization discoverability, B2B trust requirements, and external-customer sharing.
3. Converge initiation and manage-access sharing policy on explicit active organization input.
4. Remove primary-organization inference from Exchange and trust paths.
5. Complete responsive, keyboard, screen-reader, loading, empty, error, and narrow-screen states.
6. Remove stale nullable recipient fields after all callers use discriminated selections.

Exit criteria:

- Every sharing path evaluates the same policy service.
- Organization discovery is not controlled by a sharing permission.
- Active organization switching cannot retain stale trusted state.
- Frontend recipient state contains no `any`.

### Phase 8: Documentation, deletion, and certification

Work:

1. Delete old link entities, repositories, services, resources, DTOs, transformers, settings,
   frontend services, components, audit names, tests, and notifications.
2. Search help docs for `pair`, `linked organization`, `external organization`, `recipient`,
   `participant`, `group`, `sharing`, and `acceptance`.
3. Read every matched help article in full and update inaccurate statements.
4. Add a Trusted Organizations article covering setup, policies, exact-email privacy, published
   groups, assurance labels, suspension, ending, and existing Exchange effects.
5. Keep help files within the limits in `AGENTS.md`.
6. Run backend tests, frontend tests, `npx tsc --noEmit`, clean database startup, and upgrade
   migration tests.
7. Perform adversarial review of direct IDs, enumeration, timing, logs, replay, stale policy,
   cross-organization context, Share inheritance, acceptance, audit, notifications, exports,
   workflows, realtime, and application principals.

Exit criteria:

- No legacy pairing behavior remains in active code.
- Help content matches the released UI and policy.
- No critical or high-severity authorization or privacy issue remains.
- Backend tests, frontend tests, type checking, clean initialization, and upgrade migration pass.

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
- Different account, participant, viewer, owner, group member, and unauthenticated caller fail.
- Group OWNER and MANAGER accept; MEMBER and OBSERVER fail.
- Relationship or policy changes before acceptance.
- Account, membership, group publication, group status, and organization status changes.
- Workflow present and absent, repeated decision, concurrent decision, reject, rescind, and end.
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

Also run clean database initialization and an upgrade migration from a database containing legacy
organization links and active Exchanges.

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
- Legacy pairing code and terminology are removed.
- Help documentation, audit, notifications, exports, workflows, and realtime output are consistent.
- Tests, type checking, clean database initialization, upgrade migration, and adversarial review pass.
