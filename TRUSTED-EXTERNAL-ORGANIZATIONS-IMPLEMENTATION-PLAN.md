# Trusted External Organizations Implementation Plan

## Mandatory Starting Instruction

Before implementing any phase in this plan, read `AGENTS.md`,
`PRE-FIELDS-AUTHORIZATION-HARDENING-PLAN.md`, and this plan in full. Treat `AGENTS.md` as the
controlling project instruction set.

## Executive Decision

Keep the organization pairing concept, but reposition it as an optional B2B trust and identity
assurance layer called **Trusted Organizations** or **External Collaboration**.

Do not implement the complete feature before the relevant work in
`PRE-FIELDS-AUTHORIZATION-HARDENING-PLAN.md`. The feature crosses organization boundaries, exposes
identity data, creates Exchange Shares, and depends on permissions that the hardening plan is
explicitly replacing. Building it first would either preserve authorization paths that must later be
deleted or create a second authorization model.

The permitted work before the hardening gates is limited to:

- Product and architecture planning.
- Tests that demonstrate current security gaps.
- Immediate removal or fail-closed disabling of unsafe external-user enumeration.
- Removal of unused frontend calls that enumerate external users.
- A narrowly scoped backend fix that prevents unpaired or unpublished groups from being used as
  Exchange recipients.

Those security fixes should be recorded in the hardening plan's Phase 0 finding and deletion
registers. They must not introduce compatibility layers or the final feature schema.

The full feature may begin only after the Trusted Organizations Readiness Gate in this plan passes.
That gate requires the relevant outcomes of hardening Phases 1, 2, 3, 4, 5, 7, 8, and 9. It does not
require unrelated broader hardening work to be complete.

## Product Position

Pairing is not another kind of recipient. It is an administrative relationship that adds assurance
and policy to an external person or group.

DocuHyphen should support three recipient paths:

1. **Internal person or group**: search the active organization's directory normally.
2. **External individual**: enter an exact email address. The recipient remains an unverified
   external address unless DocuHyphen can attest more.
3. **Trusted external person or published group**: select a trusted organization, then resolve an
   exact email address or select a group that the partner explicitly published.

The end-user experience should stay simple while administrators retain meaningful B2B controls.
Trusted Organizations must provide more than recipient discovery. It should attest that:

- The matched account belongs to the selected organization.
- The account and membership are active at resolution time.
- The organization permits that account or published group to receive Exchanges.
- The displayed name and optional business profile fields were supplied by that organization.
- The recipient authenticates as the resolved identity before accepting the Exchange.

An exact email address by itself proves only where an invitation was sent. It does not prove the
person's organizational membership, status, role, or authorization to receive business documents.

## Recommended User Experience

Keep `External organization` as a recipient selection mode because it has a distinct assurance
contract from a general `People` email invitation.

### Trusted person flow

1. The sender selects `External organization`.
2. The sender selects an active trusted organization.
3. The sender enters the person's exact work email address.
4. DocuHyphen performs a blind exact-match resolution within that organization.
5. If matched and permitted, DocuHyphen displays an organization-attested contact card.
6. The sender confirms the card and continues.
7. At acceptance, DocuHyphen verifies that the authenticated recipient is the resolved account and
   still satisfies the trusted relationship policy.

Example contact card:

```text
Thandi Mokoena
Legal Operations, Acme Ltd
thandi@acme.example
External | Verified by Acme Ltd
```

The UI must clearly distinguish these states:

| State | Display meaning |
|---|---|
| External email | The address has not been resolved to a trusted organization member |
| Existing contact | The sender has interacted with the account before, but current organization membership is not implied |
| Verified external member | The selected organization attested the active account and profile |
| Trusted external group | The selected organization explicitly published the group as an Exchange destination |
| Verification expired | The earlier resolution is too old or current membership can no longer be confirmed |

Do not display a trusted badge based only on the email domain. Domain ownership and active user
membership are different facts.

### Privacy behavior

- Require a complete normalized email address.
- Do not support empty, prefix, name, or partial-email searches in the first release.
- Do not return suggestions or neighboring directory entries.
- Return only the exact matched profile fields that the partner permits for this relationship.
- Use a generic not-found response that does not distinguish nonexistent, inactive, hidden, or
  policy-blocked accounts.
- Rate limit and audit lookups by actor, caller organization, target organization, and relationship.
- Never place the searched email in a URL, routine access log, analytics event, or security-event
  detail.

### Published group flow

After selecting a trusted organization, the sender may select one of that organization's explicitly
published Exchange destinations, such as `Legal Intake` or `Accounts Payable`. The backend must prove
that the group belongs to the selected organization, is active, is externally published, and is
permitted by the active trusted relationship.

Individual exact-match resolution and published group discovery are separate permissions. An
organization may enable one without enabling the other.

## Administrative Experience

Rename user-facing `Org Pairing` terminology to `Trusted Organizations` or `External Collaboration`.
Use one term consistently in navigation, dialogs, notifications, audit labels, and help content.

An organization administrator should be able to:

- Request a trusted relationship with a verified organization.
- Accept, reject, suspend, resume, or end a relationship.
- Configure inbound and outbound Exchange permissions independently.
- Permit or deny exact-email member resolution.
- Permit or deny published-group discovery.
- Choose which profile fields may be returned after an exact match.
- Restrict eligible published groups or people where a future requirement justifies it.
- Set an optional expiry or review date.
- Review relationship activity and audit history.

The first release should default to least privilege:

- Relationship activation requires acceptance by both organizations.
- Exact-email resolution is disabled until the target organization enables it.
- Partial directory search is unsupported.
- Published-group visibility is disabled until groups are individually published.
- Inbound and outbound Exchange permissions are explicit.
- Trust does not grant access to existing Exchanges.

## Trust and Sharing Model

### Relationship model

Replace the current loosely defined symmetric link during the hardening clean break. Use an explicit
relationship with lifecycle status and directional policy.

Suggested concepts:

- `OrganizationTrustRelationship`
  - Stable ID.
  - Requesting organization ID.
  - Requested organization ID.
  - Status: `PENDING`, `ACTIVE`, `SUSPENDED`, `REJECTED`, or `ENDED`.
  - Request, activation, suspension, review, and end timestamps.
  - Actor and reason references suitable for audit.
- `OrganizationTrustPolicy`
  - Relationship ID.
  - Policy-owning organization ID.
  - Partner organization ID.
  - Allow outbound Exchanges.
  - Allow inbound Exchanges.
  - Allow exact-email member resolution.
  - Allow published-group discovery.
  - Allowed returned profile fields.
  - Optional expiry or next-review timestamp.

The exact persistence shape should be finalized after hardening Phases 1 through 5 establish the
final role, owner, resource-reference, action, and capability contracts. Do not store security
policy as unvalidated arbitrary JSON when database columns or typed child records can enforce the
contract.

### Separate unrelated settings

Do not use `allowShareWithoutPairing` to decide whether an organization is publicly discoverable.
Separate these concerns:

- Whether the caller organization permits B2B sharing without trust.
- Whether a target organization can be found for a trust request.
- Whether exact-email identity resolution is allowed.
- Whether published groups are visible.
- Whether individual external customer sharing is allowed.

Prefer positive names such as `requireTrustedOrganizationForB2B` over double-negative settings after
the clean break, subject to the final settings model established by hardening.

### Effect of relationship changes

Use these default rules unless product requirements approve stricter behavior:

- Suspending or ending trust blocks new resolutions, new trusted-group selections, and new Exchanges
  that depend on the relationship.
- Pending Exchange invitations that have not been accepted must be revalidated. If policy or
  membership no longer permits acceptance, fail closed and notify the Exchange owner.
- Existing accepted Exchanges retain access through their explicit Shares unless an administrator
  separately suspends or rescinds them. Ending trust must not silently destroy an active business
  transaction.
- Existing Exchanges continue to display the recipient's verified-at-send snapshot and current
  verification state separately.
- Group membership changes continue to affect dynamic group-derived access according to the final
  Share model from hardening Phase 7.

## Identity Resolution Contract

### REST shape

Use a resource-based endpoint whose request body contains the sensitive email. A suitable shape is:

```text
POST /organizations/{organizationId}/external-identity-resolutions
```

Request:

```json
{
  "email": "thandi@acme.example"
}
```

Successful response:

```json
{
  "id": "short-lived-resolution-id",
  "organizationId": "target-organization-id",
  "displayName": "Thandi Mokoena",
  "email": "thandi@acme.example",
  "title": "Legal Operations",
  "verifiedAt": "timestamp",
  "expiresAt": "timestamp"
}
```

The resource adapter must remain thin. An application service must perform authorization, trust
policy evaluation, normalization, lookup, projection, rate limiting, audit, and response decisions.

### Resolution security

- Authorize against the caller's explicit active organization and the target organization.
- Require an active relationship and the target organization's exact-resolution permission.
- Normalize email consistently and compare exact normalized values.
- Resolve membership using the organization membership service, not another service's repository.
- Return only policy-approved profile fields.
- Make the resolution ID short-lived, caller-bound, caller-organization-bound, target-bound, and
  single-purpose.
- Store or sign enough server-side context to prevent the client from changing the resolved user,
  organization, or email.
- Revalidate the resolution during Exchange initiation.
- Revalidate identity and membership during recipient acceptance.
- Prevent replay of a resolution for another Exchange or by another sender unless the contract
  explicitly permits bounded reuse.

### Exchange persistence

Store durable evidence needed to explain the decision without treating a stale snapshot as current
truth:

- Resolved recipient principal ID.
- Target organization ID at send time.
- Normalized invited email.
- Attested display-name snapshot.
- Optional attested title or department snapshot.
- Resolution and verification timestamps.
- Relationship ID and policy version used for the decision.
- Current acceptance identity and membership verification result.

Do not trust organization IDs, user IDs, group IDs, profile details, or verification flags supplied
directly by the frontend.

## Authorization-Hardening Dependency Analysis

| Hardening phase | Dependency for Trusted Organizations | Required before full implementation |
|---|---|---|
| Phase 0 | Records current enumeration, primary-organization inference, group-recipient bypasses, endpoints, and deletion targets | Yes |
| Phase 1 | Provides scope-safe organization and Exchange Share roles and validates initiation roles | Yes |
| Phase 2 | Ensures pairing administration and identity resolution authorize the exact caller and target organizations | Yes |
| Phase 3 | Gives organization relationships, policies, and organization-owned Exchanges stable owner context | Yes |
| Phase 4 | Supplies canonical resource references and target-aware authorization context | Yes |
| Phase 5 | Defines explicit relationship management, policy management, directory resolution, group discovery, and Exchange initiation capabilities | Yes |
| Phase 6 | Prevents platform and application principals from acquiring external directory or customer-content access implicitly | Required where those principals can call these endpoints |
| Phase 7 | Makes direct-user and group recipient Shares fail closed and handles membership changes correctly | Yes |
| Phase 8 | Supplies explicit active organization and frontend capabilities for settings and recipient modes | Yes |
| Phase 9 | Enforces consistent authorization and projection for lists, pickers, direct IDs, initiation, acceptance, audit, realtime, and notifications | Yes |
| Phase 10 | Certifies the final behavior and evidence | Run after this feature if the feature lands before certification |

### Scheduling decision

Do not build the final schema, APIs, or UI before the prerequisite hardening outcomes above.

Use this sequence:

1. Complete Phase 0 inventories and record the current pairing findings.
2. Apply the immediate fail-closed security fixes described in Phase A below.
3. Complete the relevant hardening work through Phase 9.
4. Pass the Trusted Organizations Readiness Gate.
5. Implement Phases B through H of this plan.
6. Include the new feature in hardening Phase 10 certification if Phase 10 has not already run.
7. If hardening Phase 10 already passed, run an equivalent focused authorization certification and
   update the signed readiness evidence before release.

## Trusted Organizations Readiness Gate

Implementation beyond Phase A is blocked until all of the following are true:

- Organization roles and Exchange Share roles are scope-safe.
- Organization authorization evaluates the exact target organization.
- Active organization context is explicit and validated.
- Organization relationships and policies can use canonical owner and resource references.
- Explicit capabilities exist for trust requests, trust decisions, trust policy management,
  external identity resolution, published-group discovery, and Exchange initiation.
- External identity resolution defaults to deny for roles without an explicit capability.
- Exchange initiation and acceptance use centralized authorization.
- Direct-user and Principal Group Shares fail closed.
- List, picker, lookup, and direct-ID operations enforce equivalent authorization and projection.
- The frontend consumes effective capabilities instead of literal role names.
- The clean-break schema strategy is finalized so obsolete organization link behavior can be
  deleted, not adapted.

Produce an evidence record listing the code, tests, and hardening exit criteria that satisfy each
item before starting Phase B.

## Phase A: Immediate Containment and Baseline

### Objective

Remove current exposure and authorization bypasses without prematurely implementing the replacement
feature.

### Implementation work

1. Add tests proving that an arbitrary organization ID cannot enumerate member IDs, names, or email
   addresses.
2. Remove or fail-closed disable the existing linked-organization app-user listing endpoint.
3. Remove frontend calls and state that fetch external organization users when the UI supports only
   published groups.
4. Require an active accepted pairing for external group retrieval.
5. During Exchange initiation, prove that an external group belongs to the selected paired
   organization and is active and externally published.
6. Apply the same validation to participant groups, not only the primary recipient.
7. Add direct-ID, guessed-ID, unpaired, inactive-group, unpublished-group, and ended-pairing negative
   tests.
8. Add every replaced endpoint, DTO, service method, frontend state field, and old setting to the
   hardening deletion register.

### Exit criteria

- External organization members cannot be enumerated.
- A guessed group ID cannot bypass pairing or publication requirements.
- No final feature schema or compatibility adapter has been introduced.

## Phase B: Finalize Domain and Policy Contracts

### Objective

Approve the clean-break relationship, directional policy, identity assurance, and lifecycle model.

### Implementation work

1. Confirm the final user-facing term.
2. Define relationship states and valid transitions.
3. Define independent inbound, outbound, exact-resolution, and published-group controls.
4. Define who may request, accept, configure, suspend, resume, review, and end trust.
5. Define behavior for pending invitations and accepted Exchanges when trust or membership changes.
6. Define returned profile fields and partner-controlled projection.
7. Define audit retention and sensitive-value redaction.
8. Approve abuse controls and generic failure behavior.
9. Add the new resources and actions to the hardening resource and capability matrices.

### Exit criteria

- Product, privacy, security, and authorization contracts have no unresolved high-risk ambiguity.
- Every operation has an owner context, action, capability, actor, target, and audit outcome.

## Phase C: Persistence and Service Foundations

### Objective

Create the final clean-break relationship and policy model behind service-owned boundaries.

### Implementation work

1. Replace the obsolete organization exchange link schema with the approved trust relationship and
   directional policy schema.
2. Add database constraints for distinct organizations, valid states, unique active relationships,
   policy ownership, and timestamps.
3. Create repositories used only by their owning services.
4. Implement relationship and policy application services with centralized authorization.
5. Register authorization-context providers for the new governed resources.
6. Add audit records and domain events without leaking searched email addresses or excessive profile
   data.
7. Update development fixtures and seed data for the final model. Do not backfill discarded
   development data.

### Exit criteria

- An empty database initializes with the final schema.
- Invalid cross-organization relationships and policy ownership cannot be persisted.
- Services do not access another service's repository.

## Phase D: Relationship Administration API and UI

### Objective

Deliver capability-driven administration of trusted relationships.

### Implementation work

1. Replace obsolete link endpoints with REST resources for relationships and policies.
2. Keep resource classes thin and move all decisions into application services.
3. Add settings navigation based on effective capabilities.
4. Build short, responsive components for relationship lists, requests, policy configuration,
   suspension, and ending.
5. Place every styled component and its `*Styles.tsx` file in a dedicated component folder.
6. Use circular Fluent UI buttons, component IDs, and responsive layouts throughout.
7. Add confirmation and impact messaging for suspension and ending.
8. Update email, in-app, realtime, and audit notifications with the approved terminology.

### Exit criteria

- Cross-organization and wrong-role administration attempts fail on the backend.
- The UI reflects capabilities but does not replace backend authorization.
- Relationship status and directional policy are understandable without exposing implementation
  terminology.

## Phase E: Blind Exact-Email Identity Resolution

### Objective

Resolve a known email to an organization-attested identity without exposing a browsable external
directory.

### Implementation work

1. Implement the external identity resolution resource and application service.
2. Enforce active relationship, directional policy, exact-match normalization, result projection,
   expiry, rate limits, audit, and generic failures.
3. Bind successful resolutions to the actor, active caller organization, target organization, and
   intended operation.
4. Do not expose search suggestions or partial matches.
5. Add incident detection for repeated misses, organization sweeps, and rate-limit evasion.
6. Ensure logs, traces, metrics, and analytics do not contain raw searched emails.
7. Add tests for case normalization, aliases according to the approved policy, inactive membership,
   hidden profiles, changed policy, expiry, replay, and cross-organization misuse.

### Exit criteria

- A permitted exact match returns only approved attested fields.
- Every other outcome is generic and reveals no directory detail.
- A resolution cannot be reused outside its authorized context.

## Phase F: Exchange Initiation and Acceptance

### Objective

Bind trusted identity assurance to the Exchange lifecycle and explicit Shares.

### Implementation work

1. Add a trusted external person selection to `External organization` initiation using a valid
   resolution ID, not client-asserted identity fields.
2. Retain published external groups as a separate selection within the same mode.
3. Revalidate relationship, policy, identity, organization membership, and group publication inside
   the initiation transaction.
4. Persist the verification evidence and profile snapshot defined earlier.
5. Create only the approved Exchange Share role and constraints.
6. At acceptance, require the authenticated account to match the resolved principal and revalidate
   current membership and policy.
7. Fail closed with actionable owner-facing recovery when verification expires or membership changes.
8. Apply relationship policy to additional participants as well as the primary recipient.
9. Define and test interaction with approval workflows, cancellation, rescind, end, and rejected
   invitations.

### Exit criteria

- Frontend request tampering cannot substitute a different user, organization, group, or trust state.
- The invited identity and accepting identity must match.
- Ending trust does not silently grant or revoke unrelated existing Exchange access.

## Phase G: Recipient UX and Assurance Presentation

### Objective

Help senders confirm the intended external recipient without overstating certainty.

### Implementation work

1. Refactor the current oversized external-recipient component into focused responsive child
   components before adding behavior.
2. Add organization selection, exact-email entry, resolution state, contact confirmation, published
   group selection, and verification-expired recovery as separate focused components.
3. Display `External` for all outside identities.
4. Display `Verified by <organization>` only for a current valid attestation.
5. Show permitted disambiguating fields such as title or department.
6. Show unverified email invitations distinctly and never imply organization trust from a domain.
7. Provide keyboard, screen-reader, loading, empty, error, narrow-screen, and touch behavior.
8. Prevent stale results when switching organizations, active organization context, or recipient
   mode.
9. Add component IDs, circular buttons, separate styles, and responsive tests as required by
   `AGENTS.md`.

### Exit criteria

- A sender can distinguish unverified email, existing contact, verified external member, and trusted
  published group.
- Organization switching cannot retain a prior organization's resolved identity.
- Components remain focused and within the repository's size expectations.

## Phase H: Documentation, Migration Cleanup, and Certification

### Objective

Delete obsolete pairing behavior and certify privacy, authorization, lifecycle, and UX outcomes.

### Implementation work

1. Delete obsolete link entities, repositories, services, endpoints, DTOs, frontend APIs, role
   checks, settings, and tests from the hardening deletion register.
2. Search help docs for pairing, linked organizations, external organizations, recipients,
   participants, groups, sharing policies, and Exchange acceptance.
3. Read every matched article in full and update inaccurate statements.
4. Add a Trusted Organizations article covering setup, exact-email resolution, badges, privacy,
   published groups, suspension, ending, and effects on existing Exchanges.
5. Register the article in the relevant section and add a prominent quick link if warranted.
6. Keep article, section, and registry files within the limits in `AGENTS.md`.
7. Run backend unit and integration tests, relevant frontend tests, and `npx tsc --noEmit` inside
   `web-app`.
8. Initialize a clean database and exercise request, acceptance, resolution, initiation, suspension,
   ending, and membership-change scenarios.
9. Perform an adversarial review for enumeration, guessed IDs, response differences, email leakage,
   stale resolutions, replay, cross-organization context, frontend tampering, group bypasses, and
   output-channel leakage.
10. Record evidence for every exit criterion in this plan and the affected hardening criteria.

### Exit criteria

- No obsolete pairing path survives the clean break.
- No unresolved critical or high-severity issue affects identity resolution or cross-organization
  Exchange access.
- Help and technical documentation match implemented behavior.
- Backend tests, frontend tests, type checking, and clean database initialization pass.

## Required Test Matrix

At minimum, cover:

- Relationship requester, recipient, unrelated organization, and no-organization callers.
- Every relationship state and invalid state transition.
- Directional inbound and outbound policy combinations.
- Exact match, miss, inactive user, inactive membership, hidden user, changed email, and changed
  organization membership.
- Resolution disabled, expired, replayed, wrong actor, wrong active organization, wrong target
  organization, and wrong Exchange.
- Profile projection with every allowed-field combination.
- Rate limiting, repeated misses, generic response behavior, and audit redaction.
- Published, unpublished, inactive, deleted, foreign, and guessed group IDs.
- Primary recipient and additional participant enforcement.
- Initiation request tampering with user, group, organization, relationship, policy, and resolution
  identifiers.
- Recipient acceptance by the intended identity, a different account, a changed member, and an
  unauthenticated caller.
- Relationship suspension or ending before initiation, before acceptance, and after acceptance.
- Existing accepted Exchange behavior after relationship changes.
- Group membership activation, removal, and deactivation after an Exchange starts.
- Human users, registered applications, service accounts, support, audit, and platform roles where
  those principals can reach the APIs.
- List, picker, direct-ID, notification, audit, realtime, export, workflow, and integration output
  consistency.
- Active organization switching and stale frontend cache isolation.
- Responsive, keyboard, screen-reader, loading, empty, and error states.

## Final Definition of Done

The feature is complete only when:

- The Trusted Organizations Readiness Gate passed with evidence.
- Pairing has been replaced by an explicit relationship and directional policy model.
- External people are resolved only by exact email and only under partner-approved policy.
- Returned identity details are organization-attested, projected, rate-limited, and audited.
- Exchange initiation and acceptance revalidate identity, membership, relationship, and policy.
- Published external groups cannot be selected or submitted outside their approved relationship.
- Trusted status is never inferred solely from an email domain or prior contact.
- Existing Exchanges have documented behavior when trust or membership changes.
- The frontend uses effective capabilities and clearly communicates assurance state.
- Obsolete endpoints, DTOs, services, settings, and compatibility behavior are deleted.
- Help docs, automated tests, adversarial review, type checking, and clean database initialization all
  pass.
