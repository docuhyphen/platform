# DocuHyphen Subscription Tier Enforcement Implementation Plan

Start by reading AGENTS.md

## Plan Control

| Field | Value |
|---|---|
| Overall status | In progress |
| Current phase | Phase 8: Subscription lifecycle, administration, and rollout (in progress) |
| Next action | Deploy a non-production environment in REPORT_ONLY, exercise the rollout matrix, and review structured subscription decision logs before proposing ENFORCE |
| Last updated | 2026-08-10 |
| Product tiers | Free, Personal, Business |
| Billing model | Business priced per purchased seat; each active provisioned membership consumes one seat |
| Rollout mode | Begin with report-only enforcement, then enable enforcement after migration verification |

## Settled Product Decisions

These are closed. Do not reopen, re-scope, or implement against them without explicit user
approval, whichever phase you are working on.

### Personal reminders are not being built

Settled 2026-08-10 during Phase 4. **Do not implement Personal reminders.**

A reminder has no independent existence in this product. It exists only as an addon on a step of
a workflow definition (`StepAddonSpec.ReminderBeforeDue`, `StepAddonSpec.ReminderIfNoDecision`)
and as the `exchange.send-reminder` workflow action handler. Selling a reminder to an individual
would therefore mean selling workflow authoring, which can run arbitrary actions, call external
systems, and evaluate organization-scoped conditions. That is Business power and must not leak
into an individual plan.

The shipped position:

- `PERSONAL_REMINDERS` is in the Business feature set only. It is deliberately absent from Free
  and Personal, and `PlanCatalogTest` asserts exactly that.
- The name `PERSONAL_REMINDERS` is historical. It does not mean the feature belongs to the
  Personal plan. Do not "fix" the catalogue to match the name.
- The pricing page shows "Coming soon" for Personal workflow automation, rendered quietly so it
  cannot be read as an entitlement, with a note stating reminders run inside a workflow and are
  a Business capability today.
- Any future work here is a separate product decision to build a standalone reminder that cannot
  execute workflow actions. It is out of scope for every phase of this plan.

Anything in this document that describes deciding a "simple reminders" boundary is a record of
how that decision was reached, not an outstanding instruction.

## Completed-Phase Audit (2026-08-10)

Phases 1, 2, and 3 were independently re-verified against the source tree before Phase 4 began.
Every artefact named in the three completion records exists, is wired into a live call path, and
behaves as recorded. Findings:

- All migrations apply cleanly. Flyway reaches head version 72 during the test run.
- Exchange ownership is safe to count against. `owner_user_id` and `owner_organization_id` are
  backfilled by V30, which also constrains an Exchange to exactly one of the two, so no Exchange
  escapes the allowance counts through a null owner.
- The counting queries exclude deleted Exchanges and exclude organization-owned Exchanges from a
  user's personal count, so an Exchange is only ever charged to whoever pays for it.
- The refusal path is intact end to end: guard raises, service propagates, resource logs its own
  message and rethrows, and the dedicated mapper produces the structured 403 in one place.
- Provisioning is hooked into every registration and activation path claimed: `AppUserService`,
  `SignUpService`, `PlatformOrganizationStatusService`, and `OrganizationVerificationMessaging`.
- Backend suite: `Tests run: 1398, Failures: 0, Errors: 0, Skipped: 0` - BUILD SUCCESS.
- Web-app type check: zero errors.

Corrections made during the audit:

- The Standard Plan Error Contract above documented a `message` field. The shipped DTO uses
  `errorMessage` to match the existing `ResponseError` model. The plan has been corrected so
  Phase 7 builds its error parser against the real contract.

Open observations carried into later phases:

- `SubscriptionPolicyService` injects `OrganizationRepository` directly to resolve an
  organization by ID. This brushes against the rule that a service should not reach into another
  service's repository. Phase 5 already consolidates organization subscription access and should
  replace this with an `OrganizationService` call.
- `app.subscription.enforcement.mode` is still undeclared, so the `REPORT_ONLY` code default
  applies everywhere including tests. This is intended until Phase 8 sets it per environment.
- Blueprint reads are gated but Blueprint mutations are not. That is Phase 4 work, not a defect.

## Purpose

Implement subscription enforcement across the Kotlin API and React web app. The API is the authority. The web app presents plan availability, usage, and upgrade guidance, but it must never be the only enforcement layer.

The authorization decision for a protected action must be:

```text
role or Share capability is allowed
and
subscription feature is included
and
subscription status permits mutations
and
the applicable usage or seat limit has not been reached
```

## Non-Negotiable Product Rules

1. No-account access is not a subscription tier.
2. No-account recipients never consume paid seats.
3. No-account recipients authenticate with the existing Exchange access mechanism and receive only Exchange-scoped access.
4. Free and Personal subscriptions belong to individual registered users operating in personal mode.
5. Business subscriptions belong to registered organizations.
6. Selecting an active organization changes the subscription context from the user subscription to that organization subscription.
7. External recipients remain available on every tier.
8. Free users must always be able to perform the basic document-sharing flow with one primary recipient.
9. The "Require recipient sign in" toggle remains available on every tier. Turning it off must not create or imply a recipient subscription.
10. Plan downgrades and payment problems must not delete Exchanges, documents, audit data, or recipient access already granted.
11. Existing recipient access must not depend on the recipient purchasing a plan.
12. "Unlimited under reasonable use" means no commercial quota, but security rate limits, file-size limits, and abuse controls still apply.

## Confirmed Pricing Matrix

This plan implements the product matrix currently shown on the marketing pricing page.

| Feature or limit | Free | Personal | Business |
|---|---|---|---|
| Subscription owner | Individual | Individual | Registered organization |
| Paid seats | One included user | One user | Each active organization member |
| External recipients | Unlimited | Unlimited | Unlimited |
| No-account client access | Included | Included | Included with organization controls |
| New Exchanges | 5 per calendar month | Unlimited under reasonable use | Unlimited under reasonable use |
| Open Exchanges | Up to 3 | Unlimited under reasonable use | Unlimited under reasonable use |
| Additional participants | Not included | Included | Included |
| Blueprints | Not included | Personal and platform | Personal, organization, and platform |
| Document Library | Not included | Personal library | Personal and organization libraries |
| Comments and versions | Not included | Included | Included |
| Advanced access controls | Basic permissions only | Included | Included |
| Variables and sequences | Not included | Personal | Personal and organization |
| Business Fields and schemas | Not included | Not included | Included |
| Workflow automation | Not included | Coming soon, see Settled Product Decisions | Full workflows |
| Organization administration | Not included | Not included | Included |
| Audit and governance | Basic Exchange activity | Extended Exchange activity | Full audit workspace and governance |
| Identity and integrations | Not included | Not included | Included |

For quota enforcement, an open Exchange is an owned Exchange with status `INITIATED` or `ACCEPTED_STARTED`. Counting drafts prevents a Free user from bypassing the limit by keeping Exchanges in draft status.

## Existing Foundation

The following functionality already exists and should be extended rather than duplicated:

- `OrganizationSubscriptionPolicy` stores an organization tier and `maxUsers`.
- `PlatformOrganizationSubscriptionPolicyService` allows platform administrators to manage organization subscription policy.
- `OrganizationFeatureEntitlement` stores per-organization feature overrides.
- `PlatformOrganizationService` manages those overrides.
- `OrganizationIdentityPolicyService` contains partial organization user-cap enforcement.
- `OrganizationMemberCapacityService` exposes active-user capacity.
- `CurrentSessionDto` exposes the active organization and effective role capabilities.
- `Exchange` already identifies its subscription owner using `ownerUserId` or `ownerOrganizationId`.
- `ExchangeInitiationService` already distinguishes personal mode from active-organization mode.
- Share and no-auth token services already provide resource-scoped recipient authorization.

## Existing Mismatch to Correct

The current organization subscription implementation treats a missing organization policy as `FREE` with three users. That does not match the selected product model because Free is an individual plan and Business is the only organization plan.

During migration:

- Existing organizations should be assigned `BUSINESS` with `ACTIVE` status and their current `maxUsers` retained.
- An existing organization with no `maxUsers` remains uncapped until a platform administrator assigns purchased seat capacity.
- New approved organizations should receive a Business subscription policy as part of organization activation.
- Existing individual accounts should initially be placed in a non-breaking Personal trial or grandfathered Personal state while enforcement runs in report-only mode.
- Newly registered individual accounts should receive an explicit Free subscription row.

Do not silently downgrade all existing users to Free when enforcement is introduced.

## Architecture Decisions

### Authorization and subscription checks remain separate

Role capabilities answer whether the caller is authorized. Plan features answer whether the paying subject owns the feature. Do not add Free, Personal, or Business roles and do not place plan checks inside `RoleCapabilities`.

### Effective subscription subject

| Operation context | Paying subject |
|---|---|
| Creating a personal resource | Authenticated app user |
| Creating an organization resource | Active organization |
| Mutating an existing Exchange | `ownerUserId` or `ownerOrganizationId` stored on the Exchange |
| Mutating an existing scoped definition | The persisted owner or scope of that definition |
| No-account recipient action | No recipient plan; use persisted Exchange and Share constraints |

The active-organization request header is sufficient for creation context only after membership validation. It must not be trusted as the owner of an existing resource.

### Plan catalogue

Fixed tier defaults belong in a typed code catalogue. Subscription status, purchased seats, billing periods, and platform overrides belong in the database.

Create these domain types in dedicated files under a focused `service/subscription/` package:

```text
PlanCode: FREE, PERSONAL, BUSINESS
SubscriptionOwnerType: USER, ORGANIZATION
SubscriptionStatus: TRIALING, ACTIVE, PAST_DUE, SUSPENDED, CANCELED
BillingFrequency: MONTHLY, ANNUAL
PlanFeature
PlanLimits
EffectiveSubscription
SubscriptionContext
```

Initial `PlanFeature` values:

```text
EXCHANGE_CREATE
MULTIPLE_PARTICIPANTS
BLUEPRINT_USE
BLUEPRINT_MANAGE
DOCUMENT_LIBRARY_USE
DOCUMENT_LIBRARY_MANAGE
DOCUMENT_COMMENTS
DOCUMENT_VERSION_HISTORY
ADVANCED_ACCESS_CONTROLS
VARIABLES_AND_SEQUENCES
PERSONAL_REMINDERS
BUSINESS_FIELDS_AND_SCHEMAS
WORKFLOW_AUTOMATION
ORGANIZATION_ADMINISTRATION
AUDIT_GOVERNANCE
IDENTITY_AND_INTEGRATIONS
```

Use feature codes for commercial product features and capabilities for authorization actions. Similar names are acceptable because the two concepts answer different questions.

### Organization feature overrides

Existing `OrganizationFeatureEntitlement` rows act as platform-admin overrides on top of the Business catalogue:

1. Resolve the Business plan defaults.
2. Apply explicit organization overrides by feature code.
3. Clamp mutation access based on subscription status.

Only platform administrators may manage overrides. User-level overrides are not needed initially.

### Subscription status behavior

| Status | Read existing data | Create or mutate | Billing and recovery access |
|---|---|---|---|
| TRIALING | Yes | Yes, using trial plan | Yes |
| ACTIVE | Yes | Yes | Yes |
| PAST_DUE | Yes | Yes during configured grace period | Yes |
| SUSPENDED | Yes | No, except safe recovery operations | Yes |
| CANCELED | Yes | No after paid period ends | Yes |

Existing no-account recipient access remains valid according to the Exchange token, Share, access window, and Exchange state. Subscription suspension must not replace those checks.

### Enforcement rollout

Add an application setting with these modes:

```text
OFF
REPORT_ONLY
ENFORCE
```

`REPORT_ONLY` calculates every decision and records what would have been denied, but does not reject the request. Start production rollout in this mode. Change to `ENFORCE` only after existing-account migration and denial logs have been reviewed.

No new AWS service is required. Use existing application configuration and logging.

## Standard Plan Error Contract

Plan denials must return a structured response. Extend the existing response-error model or add a dedicated DTO without removing existing error-message compatibility.

The shipped contract uses `errorMessage` rather than `message`, because that is the field name the
existing `ResponseError` model already uses and every frontend error handler already reads:

```json
{
  "errorMessage": "Your Free plan includes five new Exchanges per calendar month.",
  "reasonCode": "PLAN_LIMIT_REACHED",
  "featureCode": "EXCHANGE_CREATE",
  "planCode": "FREE",
  "currentValue": 5,
  "limit": 5,
  "upgradePlanCode": "PERSONAL"
}
```

Initial reason codes:

```text
FEATURE_NOT_INCLUDED
PLAN_LIMIT_REACHED
SUBSCRIPTION_PAST_DUE
SUBSCRIPTION_SUSPENDED
SUBSCRIPTION_CANCELED
SEAT_LIMIT_REACHED
ORGANIZATION_SUBSCRIPTION_REQUIRED
```

Use HTTP `403 Forbidden` for commercial feature and mutation denials. Preserve `401 Unauthorized` for missing authentication and `429 Too Many Requests` for security rate limiting. Each resource must retain the project-standard `return try { } catch { }` structure and its own unique error log message.

## Phase Completion and Handoff Procedure

Every implementation session must update this file before ending a completed phase.

For the completed phase:

1. Change its status from `Not started` or `In progress` to `Completed`.
2. Add the completion date.
3. List the exact migrations, entities, services, resources, frontend files, and tests changed.
4. Record implementation decisions or deviations from the plan.
5. Record exact verification commands and their results.
6. Record any known follow-up issue that is safe to defer.

For the next phase:

1. Change its status to `In progress` only when work has actually begun. Otherwise leave it `Not started`.
2. Update the `Plan Control` table with the next phase and first concrete action.
3. Replace the `Next Session Handoff` section at the end of this document.
4. Give the next session enough context to continue without rediscovering completed architecture.
5. Include the first files to read and the first tests to run.

Code comments created during implementation must describe the code itself. They must not mention this plan, a phase number, or a task number.

---

## Phase 1: Subscription Domain and Persistence

**Status:** Completed (2026-08-10)

### Objective

Create typed subscription concepts and persist individual subscriptions while making organization policies compatible with Business subscriptions.

### Deliverables

- Add dedicated enums for plan, status, billing frequency, owner type, and plan features.
- Add `UserSubscriptionPolicy` as a dedicated entity.
- Add a dedicated repository for user subscription policies.
- Extend `organization_subscription_policy` with:
  - subscription status
  - billing frequency
  - current period start and end
  - grace period end
  - optional external billing customer and subscription references
- Keep `max_users` as purchased Business seat capacity for now.
- Add database constraints restricting tier and status values.
- Add a unique constraint for one subscription policy per user and one per organization.
- Seed explicit user subscriptions safely:
  - existing users receive a grandfathered or trial Personal state
  - future users receive Free through registration service logic
- Migrate existing organization policies to Business without reducing current access.
- Add a `PlanCatalog` with immutable Free, Personal, and Business defaults.
- Add unit tests for every plan definition and migration-sensitive default.

### Data and migration safety

- Use the next available Flyway migration number after checking the repository at implementation time.
- Do not edit an already-applied migration.
- Add columns as nullable when needed for backfill, populate them, then add constraints.
- Do not infer paid status from organization membership roles.
- Do not store payment card or bank details in DocuHyphen.
- External billing identifiers must be nullable and provider-neutral.

### Acceptance criteria

- Every registered user has one user subscription policy.
- Every active organization has one Business subscription policy.
- Free and Personal cannot be assigned to an organization.
- Business cannot be assigned to an individual user.
- Existing organizations and users retain access during report-only rollout.
- Backend tests pass.

### Required verification

```powershell
.\mvnw.cmd test
```

### Completion record

Completed 2026-08-10.

#### Migrations

- `src/main/resources/db/migration/V71__subscription_policies.sql` (new; previous head was V70)
  - Creates `user_subscription_policy` with one row per registered account, a unique
    `app_user_id`, plan/status/billing-frequency check constraints, period ordering check,
    nullable provider-neutral external billing references, and indexes on plan and status.
  - Backfills every existing non-temporary, non-machine account as `PERSONAL` / `ACTIVE` with a
    grandfathering reason so no account loses features when enforcement arrives.
  - Adds `subscription_status`, `billing_frequency`, `current_period_start`,
    `current_period_end`, `grace_period_end`, `external_billing_customer_ref`, and
    `external_billing_subscription_ref` to `organization_subscription_policy` as nullable, then
    populates and constrains them.
  - Rewrites every organization policy to `BUSINESS`, retaining existing `max_users` exactly,
    collapses historical duplicates, and inserts an explicit `BUSINESS` row for organizations
    that previously relied on the implicit default.
  - Adds `uq_organization_subscription_policy_organization`, `tier_code = 'BUSINESS'`, status,
    billing-frequency, `max_users > 0`, and period-ordering constraints.

#### New domain files (`service/subscription/`)

`PlanCode`, `SubscriptionOwnerType`, `SubscriptionStatus`, `BillingFrequency`, `PlanFeature`,
`PlanLimits`, `PlanDefinition`, `PlanCatalog`, `SubscriptionContext`, `EffectiveSubscription`,
`SubscriptionPolicyService`.

#### New entity and repository

- `model/entity/UserSubscriptionPolicy.kt`
- `repository/UserSubscriptionPolicyRepository.kt` (includes `findByAppUserIdForUpdate` for the
  concurrency-safe quota work that follows)

#### Modified files

- `model/entity/OrganizationSubscriptionPolicy.kt` - billing lifecycle fields, unique
  organization constraint, default tier now `BUSINESS`.
- `service/auth/PlatformOrganizationSubscriptionPolicyService.kt` - `FREE_TIER_CODE` /
  `FREE_TIER_MAX_USERS` replaced by `ORGANIZATION_TIER_CODE` and `UNASSIGNED_SEAT_CAPACITY`;
  upsert now rejects any plan that is not assignable to an organization.
- `service/auth/OrganizationIdentityPolicyService.kt` - `enforceUserCapForEmail` delegates to
  `enforceUserCapForOrganization`; implicit free cap removed.
- `service/organization/OrganizationMemberCapacityService.kt`,
  `service/organization/OrganizationAppUserService.kt`, `model/PlatformOrganizationDtoMapper.kt` -
  seat capacity now comes only from purchased seats.
- `service/AppUserService.kt` - `create` provisions an individual subscription for registered
  accounts through `provisionSubscriptionIfRegistered`, skipping temporary recipients and
  machine accounts.
- `service/auth/SignUpService.kt` - sign-up completion provisions the default individual plan.
- `service/platform/PlatformOrganizationStatusService.kt` and
  `messaging/OrganizationVerificationMessaging.kt` - activation provisions the Business record.
- `web-app/src/app/components/help-docs/sections/articles/platformAdministrationOverviewArticle.tsx` -
  documents that organizations hold Business and that capacity means purchased seats.

#### Tests

- `service/subscription/PlanCatalogTest.kt` (10 tests) - every plan definition, feature set,
  superset relationship, owner-type assignability, and provisioning default.
- `service/subscription/EffectiveSubscriptionTest.kt` (6 tests) - status clamping, grace period
  boundary, seat capacity resolution, feature lookup.
- `migration/SubscriptionMigrationContractTest.kt` (2 tests, Testcontainers Postgres 17) -
  migrates to V70, seeds representative rows, migrates to V71, and asserts grandfathering,
  temporary-user exclusion, seat retention, and constraint enforcement.
- `migration/AuditMigrationUpgradeContractTest.kt` - head-version assertion updated to 71.
- `service/exchange/permutation/SignUpPermutationSupport.kt` and
  `service/platform/PlatformOrganizationStatusServiceTest.kt` - updated constructor wiring.

#### Decisions and deviations

- `SubscriptionPolicyService` was created in this phase rather than Phase 2 because registration
  and activation need a persistence-facing owner immediately. Phase 2 extends it rather than
  introducing a competing service.
- `PERSONAL_REMINDERS` is included in the Business feature set so Business remains a strict
  superset of Personal. The narrow reminder boundary is still a Phase 4 decision. (Settled in
  Phase 4: no individual plan sells a reminder. See "Settled Product Decisions".)
- Provisioning is hooked into `AppUserService.create` because every non-sign-up registration path
  (OAuth/SSO JIT, organization member add, SCIM) funnels through it. `SignUpService` gets its own
  call because it persists through the repository directly.
- Provisioning calls are wrapped in `runCatching` so a subscription write can never break
  registration or activation while enforcement is not yet active.
- Removing the implicit three-user organization cap increases access for organizations without an
  assigned `max_users`. This is deliberate and matches the product model; no organization loses
  capacity.

#### Verification

```powershell
.\mvnw.cmd -o test
```

Result: `Tests run: 1342, Failures: 0, Errors: 0, Skipped: 0` - BUILD SUCCESS.

```powershell
Set-Location web-app
node .\node_modules\typescript\bin\tsc --noEmit
```

Result: zero type errors. Note that `npx` is broken in this environment; invoke the compiler from
`node_modules` directly.

#### Known follow-ups (safe to defer)

- `OrganizationIdentityPolicyService`, `OrganizationMemberCapacityService`, and
  `OrganizationAppUserService` still read `OrganizationSubscriptionPolicyRepository` directly.
  Phase 5 consolidates them behind a single organization subscription service method.
- Seat checks are not yet locked or transactionally serialized. Phase 5 covers that.
- `max_users` is not yet renamed to `seatQuantity`. Phase 5 decides whether to rename.
- No enforcement mode setting exists yet; nothing is denied on plan grounds. Phase 2 adds it.

---

## Phase 2: Effective Subscription Resolution and API Contract

**Status:** Completed (2026-08-10)

### Objective

Create one authoritative service that resolves the paying subject, effective plan, feature set, limits, overrides, status, and usage context.

### Deliverables

- Create `SubscriptionPolicyService` for persistence-facing subscription operations.
- Create `SubscriptionAccessService` for access decisions.
- Services communicate through methods and do not use one another's repositories.
- Implement resolution for:
  - personal session context
  - active-organization session context
  - existing Exchange ownership
  - other owned resource scopes as they are added
- Apply organization feature overrides after Business plan defaults.
- Implement `OFF`, `REPORT_ONLY`, and `ENFORCE` modes.
- Add structured subscription denial exceptions and response DTOs.
- Add an effective-subscription DTO to `CurrentSessionDto` containing:
  - plan code
  - owner type and owner ID
  - subscription status
  - effective feature codes
  - applicable limits
  - current usage summary fields
- Recalculate the effective subscription whenever active organization selection changes.
- Preserve the existing role-capability calculation.
- Add tests proving that role permission alone cannot grant a plan feature and plan ownership alone cannot grant role permission.

### Suggested service interface

```kotlin
interface SubscriptionAccessService
{
    fun resolve(context: SubscriptionContext): EffectiveSubscription
    fun requireFeature(context: SubscriptionContext, feature: PlanFeature)
    fun requireMutationAllowed(context: SubscriptionContext)
    fun requireExchangeCapacity(context: SubscriptionContext)
    fun requireParticipantCapacity(context: SubscriptionContext, additionalParticipants: Int)
    fun requireOrganizationSeat(organizationId: UUID)
}
```

### API design

- Extend `GET /app-user/session` with effective subscription data.
- If a separate endpoint is needed, use `GET /subscriptions/current`.
- Keep existing platform organization subscription endpoints compatible while moving shared logic into the new service layer.
- Do not expose external billing provider secrets or internal audit snapshots.

### Acceptance criteria

- Personal mode resolves Free or Personal from the authenticated user.
- Organization mode resolves Business from the active organization.
- Existing Exchange resolution uses persisted owner fields, not only request headers.
- Organization overrides are applied predictably.
- Subscription status correctly clamps mutation access.
- The web-app TypeScript model matches the API DTO.

### Required verification

```powershell
.\mvnw.cmd test
Set-Location web-app
npx tsc --noEmit
```

### Completion record

Completed 2026-08-10.

#### New domain files (`service/subscription/`)

- `SubscriptionAccessService.kt` - the single authority for plan decisions. Exposes
  `resolve`, `resolveForSession`, `measureUsage`, `enforcementMode`, `requireFeature`,
  `requireMutationAllowed`, `requireExchangeCapacity`, `requireParticipantCapacity`, and
  `requireOrganizationSeat`. Talks only to `SubscriptionPolicyService` and
  `SubscriptionUsageService`, never to a repository.
- `SubscriptionEnforcementMode.kt` - `OFF`, `REPORT_ONLY`, `ENFORCE`, with
  `evaluatesDecisions` and `refusesDeniedRequests` so call sites never test the mode by name.
- `SubscriptionEnforcementConfigService.kt` - reads `app.subscription.enforcement.mode`,
  default `REPORT_ONLY`. An unrecognised value logs a warning and falls back to the default
  rather than failing startup or blocking customers.
- `SubscriptionDenial.kt`, `SubscriptionDenialReason.kt`, `SubscriptionDenialFactory.kt` - the
  refusal value, its reason codes, and the wording that explains the allowance and the plan
  that lifts it.
- `EffectiveSubscriptionFactory.kt` - maps a persisted policy onto the `PlanCatalog`
  definition, applies organization overrides, and clamps mutations by status and grace period.
- `SubscriptionUsage.kt` and `SubscriptionUsageService.kt` - measures capped allowances only.
  Uncapped subjects are never counted.
- `ExchangeUsageCounter.kt` and `OrganizationSeatCounter.kt` - ports implemented outside the
  subscription package so it owns no foreign repository.
- `SessionSubscriptionService.kt` - builds the session view. A resolution failure returns null
  rather than failing the session.

#### New files elsewhere

- `exception/SubscriptionDenialException.kt` and `exception/SubscriptionDenialExceptionMapper.kt`
  (403 with the structured body).
- `model/dto/SubscriptionDtos.kt` - `EffectiveSubscriptionDto`, `SubscriptionLimitsDto`,
  `SubscriptionUsageDto`, `SubscriptionDenialDto`.
- `model/SubscriptionDtoMapper.kt` - the dedicated mapper, kept out of the services and
  resources.
- `service/exchange/ExchangeUsageCounterService.kt` and
  `service/organization/OrganizationSeatCounterService.kt` - the counter implementations.
- `repository/ExchangeRepository.kt` gained `countCreatedByOwnerUserBetween`,
  `countCreatedByOwnerOrganizationBetween`, `countByOwnerUserAndStatuses`, and
  `countByOwnerOrganizationAndStatuses`.

#### Modified files

- `model/dto/AccessDtos.kt` - `CurrentSessionDto.subscription`.
- `service/auth/SessionService.kt` - populates it on every session fetch, so switching
  organization changes the plan without reissuing a token.
- `web-app/src/app/models/models.tsx` - mirrors the API contract: `PlanCode`,
  `SubscriptionOwnerType`, `SubscriptionStatus`, `BillingFrequency`, `PlanFeature`,
  `SubscriptionEnforcementMode`, `SubscriptionDenialReason`, `SubscriptionLimitsDto`,
  `SubscriptionUsageDto`, `EffectiveSubscriptionDto`, `SubscriptionDenialDto`, and the new
  `subscription` field on `CurrentSessionDto`.

#### Tests

- `service/subscription/SubscriptionAccessServiceTest.kt` - resolution per context, override
  precedence, status clamping, allowance refusals, and the three enforcement modes.
- `service/subscription/SubscriptionAuthorizationSeparationTest.kt` - role capability alone
  grants no plan feature, and plan ownership alone grants no role permission.
- `service/subscription/SubscriptionUsageServiceTest.kt` - calendar month boundaries and lazy
  counting.
- `service/subscription/EffectiveSubscriptionFactoryTest.kt` and
  `model/SubscriptionDtoMapperTest.kt` - mapping and DTO contract.
- `service/auth/ActiveOrgContextTest.kt` - updated for the session wiring.

#### Decisions and deviations

- Fields with a Kotlin default serialize as absent rather than null, so the mirrored
  TypeScript marks exactly those fields optional. Required fields stay required.
- A session that cannot resolve a subscription returns `subscription` absent instead of
  failing. The session is an identity and capability contract first, and a missing commercial
  summary must never widen access or lock a customer out.
- `resolve` provisions a missing policy row through `SubscriptionPolicyService` rather than
  inventing an in-memory default, so a subject can never drift between requests.
- A user-owned subject whose plan lacks a feature that Personal also lacks is refused with
  `ORGANIZATION_SUBSCRIPTION_REQUIRED` instead of `FEATURE_NOT_INCLUDED`, because no
  individual plan can ever unlock it.
- No separate `GET /subscriptions/current` endpoint was added. The session already carries the
  data and is refetched on organization switch, so a second endpoint would only add a way for
  the two to disagree.

#### Verification

```powershell
.\mvnw.cmd -o test
```

Result: `Tests run: 1380, Failures: 0, Errors: 0, Skipped: 0` - BUILD SUCCESS.

```powershell
Set-Location web-app
node .\node_modules\typescript\bin\tsc --noEmit
```

Result: zero type errors. `npx` is still broken in this environment; invoke the compiler from
`node_modules` directly.

#### Known follow-ups (safe to defer)

- Nothing calls the `require*` methods yet other than the Exchange and Blueprint paths added in
  Phase 3. Phases 4 to 6 add the remaining call sites.
- Denials are recorded as structured logs. Whether they should also become audit rows is a
  Phase 8 decision.
- `app.subscription.enforcement.mode` is not yet declared in `application.properties`; the code
  default applies. Phase 8 sets it explicitly per environment.

---

## Phase 3: Free Exchange Enforcement

**Status:** Completed (2026-08-10)

### Objective

Enforce the complete Free plan at Exchange creation without weakening basic document sharing or no-account recipient access.

### Deliverables

- Add repository count methods for:
  - user-owned Exchanges created in the current UTC calendar month
  - user-owned open Exchanges in `INITIATED` or `ACCEPTED_STARTED`
- Add appropriate indexes for owner, creation date, and status if query plans require them.
- Lock the explicit user subscription row during Free quota check and Exchange creation.
- In `ExchangeInitiationService`, enforce before entity creation:
  - five new Exchanges per calendar month
  - three open Exchanges
  - no additional participants
  - primary recipient remains required
- Keep external email primary recipients available.
- Keep the "Require recipient sign in" toggle available.
- Keep both send-document and request-document flows available.
- Keep document upload and download permissions required for the core sharing flow.
- Reject blueprint-based initiation for Free users.
- Return structured `FEATURE_NOT_INCLUDED` or `PLAN_LIMIT_REACHED` responses.
- Emit report-only and enforced denial audit events without logging document content or access tokens.

### Concurrency requirement

The quota check and Exchange insert must be in the same transaction while holding a lock on the user subscription policy. Two simultaneous requests must not both consume the final available quota slot.

### API test cases

- Free user creates Exchanges one through five successfully within the period.
- Sixth creation is denied.
- Three open Exchanges prevent another creation even when monthly quota remains.
- Completing or rejecting an open Exchange releases open capacity.
- A primary external email recipient is allowed.
- No-account access can be enabled.
- One additional participant is denied.
- Personal and Business contexts are not subject to Free quotas.
- Report-only mode logs the denial but permits the operation.

### Acceptance criteria

- Free users can complete the absolute minimum sharing workflow.
- Limits cannot be bypassed with concurrent requests or draft Exchanges.
- Direct API calls receive the same restrictions as web-app calls.
- No-account recipients remain outside plan and seat calculations.

### Required verification

```powershell
.\mvnw.cmd test
```

### Completion record

Completed 2026-08-10.

#### Migration

- `src/main/resources/db/migration/V72__exchange_owner_usage_indexes.sql` (new; previous head
  was V71). Four partial indexes on `exchange` covering owner plus creation date and owner plus
  status, for both the personal and organization owner columns. They are partial because a
  deleted Exchange never counts towards an allowance, which keeps them small and skips rows the
  counts always exclude.

#### New files

- `service/exchange/ExchangeInitiationSubscriptionGuard.kt` - the Exchange creation allowance.
  Resolves the paying subject from the selected organization or the authenticated user, takes a
  row-level write lock on a capped subject before counting, then applies the mutation, Exchange
  capacity, and participant checks. Kept separate from `ExchangeInitiationService` so the
  initiation flow keeps one responsibility.
- `service/blueprint/BlueprintSubscriptionGuard.kt` - the Blueprint allowance.

#### Modified files

- `service/exchange/ExchangeInitiationService.kt` - injects the guard and calls it inside the
  existing `@Transactional initiateExchange`, after authorization and before any entity is
  created.
- `resource/exchange/ExchangeResource.kt` - logs its own message for a plan refusal and
  rethrows so the dedicated mapper returns the structured 403 rather than flattening it into a
  generic 500.
- `service/blueprint/BlueprintDefinitionService.kt` - `listBlueprints` and `getBlueprint` apply
  the Blueprint allowance.
- `resource/blueprint/BlueprintDefinitionResource.kt` - same log-and-rethrow treatment on both
  read endpoints.
- `test/.../migration/AuditMigrationUpgradeContractTest.kt` - head version assertion is now 72.
- `test/.../migration/SubscriptionMigrationContractTest.kt` - now migrates with an explicit
  target of the subscription migration, so the contract stays about that migration instead of
  breaking every time a later migration is added.
- `test/.../service/exchange/ExchangeInitiationFieldsTest.kt`,
  `test/.../service/exchange/permutation/ExchangeInitiationAutoAcceptPermutationSupport.kt`,
  `test/.../service/blueprint/BlueprintTenantBoundaryTest.kt`, and
  `test/.../service/content/ResourceAuthorizationTest.kt` - constructor wiring.

#### Tests

- `service/exchange/ExchangeInitiationSubscriptionGuardTest.kt` (12 tests) - creation inside the
  allowance, the sixth creation of the month refused, open Exchanges blocking creation while
  monthly allowance remains, freeing an open Exchange restoring capacity, an additional
  participant refused on Free, a suspended subscription refused, Personal being uncapped and
  uncounted, an organization context never charged against the member's personal plan, a capped
  subject being locked before counting, an uncapped subject not being locked, report-only
  permitting the operation, and disabled enforcement doing no work at all.
- `service/blueprint/BlueprintSubscriptionGuardTest.kt` (6 tests) - Free refused, Personal
  allowed, organization context using the organization plan, platform catalogue curation not
  charged against a personal plan, report-only, and disabled enforcement.

#### Decisions and deviations

- The initiation request carries no Blueprint reference, so "reject blueprint-based initiation"
  had to be applied where a Blueprint is actually consumed: reading it. Gating the read also
  closes the direct API route that would otherwise let a caller fetch the Blueprint and
  assemble the same Exchange by hand.
- Curating the platform-supplied Blueprint catalogue is exempt from the Blueprint allowance. It
  is a platform administration duty rather than something a personal plan buys, and leaving it
  in would have locked a newly registered platform administrator out of the platform templates
  once enforcement is switched on.
- The lock is taken only for a subject whose Exchange allowances are actually capped. An
  uncapped subject has nothing to over-consume, so serializing its creations would buy nothing.
- The guard returns immediately when enforcement is `OFF`, so a disabled environment pays no
  query or locking cost.
- Refusals are recorded through the structured logging already built into
  `SubscriptionAccessService`. It records the reason, plan, owner type, feature, current value,
  and limit, and never touches document content or access tokens. Whether these should also
  become audit rows is left to Phase 8.
- Resource classes log their own unique message and rethrow rather than mapping the refusal
  themselves, so the structured body stays defined in exactly one place.
- No help documentation changed. No article states a plan allowance, and enforcement defaults
  to report-only so no documented behaviour changed. Plan-aware documentation belongs with the
  app experience in Phase 7.

#### Verification

```powershell
.\mvnw.cmd -o test
```

Result: `Tests run: 1398, Failures: 0, Errors: 0, Skipped: 0` - BUILD SUCCESS.

#### Known follow-ups (safe to defer)

- Personal-plan gating for the Document Library, comments, version history, advanced access
  controls, and variables and sequences is Phase 4.
- Blueprint management, as opposed to use, is not gated yet. Phase 4 covers `BLUEPRINT_MANAGE`.
- Seat enforcement on membership activation is still unlocked and unconsolidated. Phase 5.

---

## Phase 4: Personal Plan Enforcement

**Status:** Completed (2026-08-10)

### Objective

Enable Personal plan features for individual users without requiring or silently creating an organization.

### Deliverables

- Gate personal blueprint use and management.
- Gate the personal Document Library.
- Gate document comments and version history.
- Gate advanced access controls such as restrictions, watermarking, and MFA requirements where currently supported.
- Gate personal variables and sequences.
- Define and enforce the exact boundary of "simple reminders" separately from full workflow
  automation. Settled: no such boundary can be expressed, so Personal reminders are not built.
  See "Settled Product Decisions" near the top of this document.
- Ensure Business Fields and organization schema configuration remain unavailable in personal context.
- Ensure organization administration, SSO, registered applications, and webhooks remain unavailable in personal context.
- Resolve every personal resource from its persisted user owner.
- Add tests for ownership changes, active-organization switching, and direct API calls.

### Reminder boundary decision (settled, no action outstanding)

This section recorded the question that had to be answered before Phase 4 could proceed. It has
been answered and closed. Personal reminders are not being built. The original wording is kept
below only so the reasoning is traceable; it is not an instruction to a future session.

> Before implementation, identify the current reminder and workflow capabilities. Prefer a narrow Personal reminder definition that cannot execute arbitrary actions, webhooks, organization approvals, or organization-scoped conditions.
>
> If the existing workflow engine cannot safely express this boundary, defer Personal reminders and mark them "coming soon" in both pricing and UI rather than exposing full Business workflow power.

The engine cannot express that boundary, so the deferral branch applies. See "Settled Product
Decisions" near the top of this document.

### Acceptance criteria

- Personal users can use every implemented Personal feature in personal mode.
- Personal users do not need an organization.
- Free users cannot call the same APIs directly.
- Personal users cannot access Business-only features.
- Switching into an organization uses the organization subscription instead of the Personal subscription.

### Required verification

```powershell
.\mvnw.cmd test
Set-Location web-app
npx tsc --noEmit
```

### Completion record

Completed 2026-08-10.

#### Reminder boundary decision: deferred

A reminder has no independent existence in this product. It exists only as a step addon on a
workflow definition (`StepAddonSpec.ReminderBeforeDue`, `StepAddonSpec.ReminderIfNoDecision`) and
as a workflow action handler (`exchange.send-reminder`). Authoring one therefore requires
authoring a full workflow definition, which can also carry arbitrary actions, external calls,
organization approvals, and organization-scoped conditions.

The narrow boundary the plan asks for cannot be expressed without building a separate reminder
concept, which is out of scope here. Personal reminders are therefore deferred:

- `PERSONAL_REMINDERS` was removed from the Personal feature set and added explicitly to the
  Business set, so Business remains a strict superset and reminders arrive with the workflow
  authoring that actually contains them.
- The marketing pricing page now reads "Coming soon" for Personal workflow automation.
- The confirmed pricing matrix in this document was corrected to match.
- No help article promises Personal reminders. Every reminder reference in the help
  documentation describes workflow step addons and remains accurate.

#### New files

- `service/documentlibrary/DocumentLibrarySubscriptionGuard.kt` - the Document Library
  allowance. Resolves the subject from the persisted scope of the entry, exempts the platform
  catalogue and platform administrators, and separates reading from authoring.
- `service/exchange/ExchangeFeatureSubscriptionGuard.kt` - the allowances for features consumed
  inside an existing Exchange: document comments, document version history, and advanced access
  controls. Always resolves the subject through `SubscriptionContext.forOwner` from the owner
  columns persisted on the Exchange.
- `service/variable/VariableSubscriptionGuard.kt` - the variables and sequences allowance,
  applied to authoring only.

#### Modified backend files

- `service/subscription/PlanCatalog.kt` - Personal no longer includes `PERSONAL_REMINDERS`;
  Business now lists it explicitly alongside `WORKFLOW_AUTOMATION`.
- `service/blueprint/BlueprintSubscriptionGuard.kt` - gained `requireBlueprintManagement`,
  which resolves the subject from the Blueprint scope and exempts the platform catalogue.
- `service/blueprint/BlueprintDefinitionService.kt` - `createBlueprint`, `updateBlueprint`,
  `patchPublished`, `patchStatus`, `deleteBlueprint`, and `cloneBlueprint` apply
  `BLUEPRINT_MANAGE`.
- `service/documentlibrary/DocumentLibraryService.kt` - `listEntries`, `getEntry`,
  `downloadFile`, and `resolveLibraryFileForBlueprintDocument` apply `DOCUMENT_LIBRARY_USE`;
  `createEntry`, `updateEntry`, `uploadFile`, `patchStatus`, `patchPublished`, `deleteEntry`,
  and `cloneEntry` apply `DOCUMENT_LIBRARY_MANAGE`.
- `service/exchange/ExchangeDocumentCommentsService.kt` - `addDocumentComment` applies
  `DOCUMENT_COMMENTS`.
- `service/exchange/ExchangeDocumentVersionService.kt` - `createVersion` applies
  `DOCUMENT_VERSION_HISTORY`.
- `service/exchange/ExchangeAccessManagementService.kt` - `grantAccess`,
  `inviteTrustedParticipant`, and `changeRole` apply `ADVANCED_ACCESS_CONTROLS`, but only when
  the request carries share constraints or an access window.
- `service/variable/VariableDefinitionService.kt` and
  `service/variable/SequenceDefinitionService.kt` - authoring applies
  `VARIABLES_AND_SEQUENCES`.
- `resource/blueprint/BlueprintDefinitionResource.kt`,
  `resource/documentlibrary/DocumentLibraryResource.kt`,
  `resource/exchange/ExchangeDocumentCommentResource.kt`,
  `resource/exchange/ExchangeDocumentVersionResource.kt`,
  `resource/variable/VariableDefinitionResource.kt`,
  `resource/variable/SequenceDefinitionResource.kt`, and
  `resource/exchange/ExchangeResource.kt` (through `mapAccessMutationError`) - each endpoint
  that can now surface a plan refusal logs its own message and rethrows.

#### Modified frontend files

- `website/src/pages/PricingPageData.ts` - Personal workflow automation now reads
  "Coming soon".
- `website/src/pages/PricingFeatureValue.tsx` (new) - renders one comparison cell. A definite
  answer stays emphasised; a planned capability is rendered quietly so it cannot be mistaken for
  an entitlement. Keeps `PricingPage.tsx` short.
- `website/src/pages/PricingPage.tsx` - uses the new cell and gained a note stating that
  reminders run inside a workflow and are a Business capability today.
- `website/src/pages/PricingPageStyles.tsx` - added the muted `pendingValue` style and made the
  notes grid auto-fit so a fourth note lays out cleanly.
- `web-app/src/app/models/models.tsx` - `PlanFeature.PERSONAL_REMINDERS` documented as an
  organization-only feature whose name is historical, so the Phase 7 UI never renders it as an
  individual capability.
- `service/subscription/PlanFeature.kt` - the same warning at the source of truth.

#### Tests

- `service/documentlibrary/DocumentLibrarySubscriptionGuardTest.kt` (11 tests) - Free refused
  for both reading and authoring, Personal allowed, an organization entry charged to the
  organization, an entry owned by somebody else still charged to the caller reading it, the
  platform catalogue exempt, a platform administrator exempt, a suspended subject able to read
  but not author, report-only, and disabled enforcement.
- `service/exchange/ExchangeFeatureSubscriptionGuardTest.kt` (12 tests) - comments and versions
  refused on a Free owner's Exchange and allowed on a Personal owner's, an organization-owned
  Exchange using the organization plan, a plain role grant staying available on every plan, a
  constrained grant and an access window both refused on Free and allowed on Personal, a
  suspended owner refused, an Exchange with no recorded owner left alone, report-only, and
  disabled enforcement.
- `service/variable/VariableSubscriptionGuardTest.kt` (6 tests) - Free refused, Personal
  allowed, an organization value charged to the organization, suspended refused, report-only,
  and disabled enforcement.
- `service/blueprint/BlueprintSubscriptionGuardTest.kt` - extended by 6 tests covering
  authoring: Free refused, Personal allowed, an organization Blueprint charged to its
  organization, the platform catalogue exempt, suspended refused, and report-only.
- `service/subscription/PersonalContextBusinessFeatureTest.kt` - every organization-only feature
  refused for both individual plans with `ORGANIZATION_SUBSCRIPTION_REQUIRED` and Business named
  as the plan that lifts it.
- `service/subscription/PlanCatalogTest.kt` - Personal feature set updated and a new assertion
  that no individual plan sells a reminder while Business does.
- Constructor wiring updated in `service/content/ResourceAuthorizationTest.kt`,
  `service/documentlibrary/DocumentLibraryAuditOwnershipTest.kt`,
  `service/documentlibrary/DocumentLibraryTenantBoundaryTest.kt`,
  `service/variable/SequenceTenantBoundaryTest.kt`,
  `service/variable/VariableTenantBoundaryTest.kt`,
  `service/exchange/ExchangeAccessManagementMutationTest.kt`,
  `service/exchange/ExchangeAccessManagementRecipientBindingTest.kt`, and
  `service/exchange/ExchangeAccessManagementServiceTest.kt`.

#### Decisions and deviations

- Advanced access controls are charged only when a grant carries more than a role. Assigning
  somebody a role is basic sharing and stays on every plan; narrowing that grant with download
  rules, watermarking, an MFA requirement, an address allowlist, or an expiry is the control
  being sold. Watermarking and MFA do exist, contrary to an early reading of the code: they live
  in `ShareConstraints` as `watermark` and `require_mfa`.
- Only the operations that consume a feature are gated. Reading comments or versions that
  already exist is untouched, so a plan change never hides content a participant already has and
  a recipient is never charged for a feature the sender bought.
- `recordUploadedFileAsVersion` is deliberately not gated. That snapshot belongs to an upload the
  caller has already been allowed to make, and refusing it would discard the only stored copy of
  the file.
- Listing and resolving variables and sequences is not gated either, so a Blueprint, document, or
  Exchange that already refers to a value keeps working after a downgrade.
- Document Library reads are gated because reading a stored document is how the library is
  consumed, mirroring the reasoning already applied to Blueprint reads.
- The Document Library and Blueprint guards resolve the subject from the persisted scope of the
  entry, not from the active-organization header, so an organization asset is always charged to
  that organization whichever organization is selected.
- Every guard returns immediately when enforcement is `OFF`, and the tests assert that no
  repository or role lookup happens in that mode.
- Existing behaviour tests were wired with mocked guards rather than real ones, so an
  authorization test stays about authorization.

#### Verification

```powershell
.\mvnw.cmd -o test
```

Result: `Tests run: 1435, Failures: 0, Errors: 0, Skipped: 0` - BUILD SUCCESS.

```powershell
Set-Location web-app
node .\node_modules\typescript\bin\tsc --noEmit
Set-Location ..\website
node .\node_modules\typescript\bin\tsc --noEmit
```

Result: zero type errors in both projects. `npx` is still broken in this environment; invoke the
compiler from `node_modules` directly.

#### Help documentation

Searched `web-app/src/app/components/help-docs/sections` for reminder, subscription, tier, and
plan-allowance wording. Every match describes workflow step addons or the platform
administration view of organization tier and capacity, all of which remain accurate. Enforcement
is still report-only, so no documented behaviour changed. No article edits were required.

#### Known follow-ups (safe to defer)

- Seat enforcement on membership activation is still unlocked and unconsolidated. Phase 5.
- Organization-only mutations beyond the ones touched here (Business Fields, schemas, workflow
  authoring, audit, identity, registered applications, webhooks) are gated by organization role
  and scope but do not yet call a plan check. Phase 6 adds those call sites.
- Personal reminders remain unsold. Building a standalone reminder that cannot execute arbitrary
  workflow power is a separate product decision.

---

## Phase 5: Business Seats and Organization Enforcement

**Status:** Completed (2026-08-10)

### Objective

Make Business the organization plan and enforce purchased seats consistently across every membership activation path.

### Deliverables

- Replace implicit Free organization defaults with Business subscription resolution.
- Interpret `maxUsers` as purchased seat capacity or rename it through a safe migration to `seatQuantity`.
- Consolidate seat checks behind one organization subscription service method.
- Update `OrganizationIdentityPolicyService` to delegate instead of implementing its own policy repository logic.
- Enforce seats when:
  - an invitation is accepted
  - a member is added directly
  - a deactivated member is reactivated
  - OAuth or SSO provisions a membership
  - SCIM or another registered application provisions a membership
  - a pending membership becomes active
- Count unique active, provisioned organization memberships.
- Include the organization owner in seat count.
- Exclude external recipients, no-account recipients, deactivated members, revoked members, and pending invitations.
- Lock the organization subscription policy during count and activation.
- Keep the existing member-capacity endpoint compatible or replace it with a documented organization subscription resource.
- Update platform administration to show purchased seats, active seats, and remaining seats.

### Seat billing rule

Business pricing is per purchased seat. The organization may invite beyond capacity only if invitations do not become active. Acceptance or activation must fail cleanly when no seat is available.

Do not automatically increase paid seat quantity without an explicit billing workflow or platform-admin action.

### Acceptance criteria

- All membership activation paths enforce the same count.
- Concurrent activations cannot exceed purchased capacity.
- Removing or deactivating a member releases a seat.
- No-account recipients never appear in seat usage.
- Business features still require the caller's organization role capabilities.

### Required verification

```powershell
.\mvnw.cmd test
Set-Location web-app
npx tsc --noEmit
```

### Completion record

Completed 2026-08-10.

#### Transactional seat enforcement

- `OrganizationSubscriptionPolicyRepository.findByOrganizationIdForUpdate` now takes a
  pessimistic write lock on the organization subscription row.
- `SubscriptionPolicyService.findOrganizationPolicyForUpdate` exposes that lock through the
  subscription persistence boundary. The service now resolves organizations through
  `OrganizationService` instead of reading `OrganizationRepository` directly.
- `OrganizationSeatGuard` is the single activation guard. It returns immediately in `OFF`,
  checks subscription mutation status in evaluated modes, locks only capped organizations, and
  calls `SubscriptionAccessService.requireOrganizationSeat` while the activation transaction is
  still open.
- `OrganizationMembershipService.assignOrgRole` now resolves memberships in every status, so an
  invited, suspended, or left membership is reactivated rather than duplicated. It reserves a
  seat only when the transition creates a new active, provisioned membership. Adding another
  role to an existing active member does not consume another seat.
- Account reactivation reserves seats for every active organization membership in stable
  organization-ID order. This covers organization-administrator reactivation, SCIM replacement
  and patch activation, and temporary-recipient signup completion without creating deadlock-prone
  lock ordering.
- OAuth and SSO just-in-time provisioning, direct organization member creation, organization
  registration, SCIM creation, and future registered-application paths all reach the same guard
  through `OrganizationMembershipService.assignOrgRole`.
- Membership activation is transactional. Concurrent requests hold the subscription lock across
  the seat count and membership write, so only one request can consume the final seat.

#### Seat counting and policy consolidation

- `OrganizationMembershipRepository.countActiveProvisionedMembers` is the seat-count query. It
  counts active memberships whose user is active, non-temporary, and not deprovisioned.
  Organization owners count because ownership is represented by the same active membership.
  External recipients and no-account recipients have no qualifying membership and do not count.
- `OrganizationSeatCounterService` now reads through the membership repository without depending
  on the membership write service, avoiding a dependency cycle with the activation guard.
- `OrganizationIdentityPolicyService` delegates its legacy cap methods to
  `OrganizationSeatGuard` and no longer reads the subscription policy repository.
- `OrganizationMemberCapacityService` resolves plan and seat usage through
  `SubscriptionAccessService`; it no longer reads the policy repository or duplicates counting.
- `OrganizationAppUserService` no longer contains a private cap implementation. Direct creation
  reaches the membership guard, and account reactivation uses the shared multi-organization
  activation check.
- `max_users` remains the persisted purchased-seat quantity for compatibility. It was not renamed,
  so no V73 migration was needed. A null value continues to mean unassigned and uncapped.

#### Refusal paths and administration

- `OrganizationAppUserResource`, `SignUpResource`, and `OAuthResource` log and rethrow
  `SubscriptionDenialException` so the shared mapper returns the structured 403 instead of a
  generic 500 or callback failure.
- `ScimUserResource` runs create, replace, patch, and delete mutations transactionally; create
  uses the shared membership path and reactivation reserves all applicable organization seats.
- Platform organization administration now labels capacity as purchased seats and shows
  purchased, active, and remaining seat values responsively in the account editor.
- `platformAdministrationOverviewArticle.tsx` was read in full and updated to describe purchased,
  active, and remaining seats. It remains below the article size limit.

#### Tests

- `OrganizationSeatGuardTest` covers disabled enforcement, uncapped organizations, and locking
  before counting for capped organizations.
- `OrganizationMembershipSeatEnforcementTest` covers invited membership activation, role changes
  without double charging, temporary and inactive accounts, and stable multi-organization lock
  order.
- `SubscriptionAccessServiceTest` now covers report-only seat exhaustion in addition to enforced
  refusal and uncapped behavior.
- `OrganizationSeatConcurrencyPostgresContractTest` applies all migrations through V72 and proves
  that two simultaneous activations cannot consume the same final seat. It also proves that
  deactivation releases a seat, the owner consumes a seat, and a temporary no-account recipient
  does not consume one.
- Existing capacity, OAuth, organization policy, cross-organization member management, and signup
  permutation tests were updated for the consolidated dependencies.

#### Verification

```powershell
.\mvnw.cmd -o "-Dtest=SubscriptionAccessServiceTest,OrganizationSeatGuardTest,OrganizationMembershipSeatEnforcementTest,OrganizationMemberCapacityServiceTest,OAuthUserLinkingServiceTest,OrganizationOAuthPolicyTest,CrossOrgMemberManagementTest" surefire:test
```

Result: `Tests run: 45, Failures: 0, Errors: 0, Skipped: 0` - BUILD SUCCESS.

```powershell
.\mvnw.cmd -o -DskipFrontend=true "-Dtest=OrganizationSeatConcurrencyPostgresContractTest" test
```

Result: `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0` - BUILD SUCCESS. Flyway
reached V72.

```powershell
.\mvnw.cmd -o test -DskipFrontend=true
```

Result: `Tests run: 1444, Failures: 0, Errors: 0, Skipped: 0` - BUILD SUCCESS.

```powershell
Set-Location web-app
node .\node_modules\typescript\bin\tsc --noEmit
node .\node_modules\vitest\vitest.mjs run src\app\platform-administration\organizations\organization-editor\useOrganizationEditor.test.tsx
```

Result: TypeScript reported zero errors; the focused organization-editor test passed
(`1 test`).

#### Known follow-ups (safe to defer)

- The storage and public API field remains named `maxUsers` / `max_users` for compatibility even
  though the UI and product language now call it purchased seats. A future provider integration
  may rename it through a separate compatibility migration if the benefit justifies the churn.
- The SCIM resource contains older provisioning logic in the HTTP adapter. Moving the complete
  SCIM mutation flow into a focused application service is an architectural cleanup separate from
  the seat decision itself.

---

## Phase 6: Business Feature Gates Across the API

**Status:** Completed (2026-08-10)

### Objective

Apply Business feature checks at every relevant mutation boundary while preserving existing reads and safe recovery actions.

### Gate inventory

Add explicit enforcement to service methods for:

- Organization blueprint creation, publishing, cloning, and use
- Shared organization Document Library management and use
- Business Fields and schema configuration
- Full workflow definitions, publishing, execution, approvals, conditions, actions, and escalations
- Organization communications used by workflows
- Organization variables and sequences
- Audit workspaces, exports, retention, integrity verification, and legal holds
- SSO and organization identity-provider configuration
- Registered applications and service accounts
- Webhook configuration and delivery administration
- Organization trust and shared organization groups where commercially gated

### Enforcement placement

- Put checks in application-scoped or request-scoped services, not repositories or resource classes.
- Resource classes validate input, call services, and map structured errors.
- Check persisted resource ownership before feature evaluation.
- For actions involving two organizations, authorize the caller and apply the subscription policy of the organization performing the paid action.
- Do not plan-gate recipient reads, downloads, signatures, or comments that were already validly granted on an existing Exchange unless the feature itself was never configured on that Exchange.

### Downgrade behavior

- Existing advanced resources remain readable.
- New creation, publishing, configuration changes, and new executions are blocked when no longer entitled.
- Existing running workflows must follow an explicitly tested policy. Recommended initial behavior is to let already-started workflow instances finish while blocking new instances.
- Never delete or rewrite existing definitions during downgrade.

### Acceptance criteria

- Every Business-only mutation has an API plan check.
- Existing authorization tests still pass.
- Direct endpoint calls cannot bypass UI gates.
- Existing resources remain recoverable after downgrade.
- No resource or service class violates the project structure rules.

### Required verification

```powershell
.\mvnw.cmd test
Set-Location web-app
npx tsc --noEmit
```

### Completion record

Completed 2026-08-10.

#### Shared Business enforcement

- `OrganizationFeatureSubscriptionGuard` is the shared organization-owned feature boundary. It
  exits without persistence work in `OFF`, checks mutation status before the requested feature,
  and treats a null organization owner as a platform-owned exemption.
- Existing resources resolve their paying subject from persisted ownership. Creation paths use a
  validated target scope. Active-organization context is not trusted as the owner of an existing
  resource.
- Every affected HTTP adapter logs its own subscription refusal and rethrows it so
  `SubscriptionDenialExceptionMapper` remains the single structured 403 mapper.

#### Business Fields and workflow automation

- `BusinessFieldsSubscriptionGuard` protects field definitions, schema definitions, schema
  bindings, schema assignments, and Exchange field-value mutations with
  `BUSINESS_FIELDS_AND_SCHEMAS`. Platform field and schema configuration remains exempt.
- `WorkflowSubscriptionGuard` protects workflow definition authoring, publishing, lifecycle
  changes, cloning, and new instance startup with `WORKFLOW_AUTOMATION`. Platform definitions are
  charged to the validated organization starting the work when one exists.
- Running workflow instances are not rechecked during approvals, conditions, actions, reminders,
  or escalations. Instances already started can finish after a downgrade, while new instances are
  blocked.
- Organization communication creation, editing, publishing, status changes, and deletion use the
  same workflow automation entitlement. Reads, previews, personal copies, and platform
  communications retain their prior behavior.
- Blueprint, Document Library, variable, and sequence authoring and use were already protected by
  their catalogue features from Phase 4. Their persisted ownership and downgrade-read behavior
  were rechecked during the Phase 6 inventory.

#### Audit, identity, and organization administration

- `AUDIT_GOVERNANCE` now protects organization audit engagement creation and approval, export
  creation and approval, retention policy changes, integrity checks, and legal holds. Existing
  records, completed export downloads, revocation, hold release, and automatic expiry remain
  available as reads or safe recovery actions.
- `IDENTITY_AND_INTEGRATIONS` protects organization identity-provider configuration and secret
  lifecycle changes, registered application configuration and credential rotation, workflow
  webhook administration, organization trust lifecycle changes, and trust policy updates.
- `ORGANIZATION_ADMINISTRATION` protects membership activation and role changes, removals,
  organization group management, organization profile changes, and organization settings.
- Trust is classified under identity and integrations because it enables cross-organization
  identity, group, and membership resolution. Organization-owned groups remain organization
  administration.
- The `ServiceAccount` persistence model currently has no management service or public mutation
  API. There is therefore no callable service-account boundary to gate. Future service-account
  management must use `IDENTITY_AND_INTEGRATIONS` when that API is introduced.

#### Documentation and verification

- Help articles now explain Business eligibility and downgrade behavior for fields, workflows,
  organization communications, audit governance, identity providers, trusted organizations, and
  administration. Every changed article and section remains within its configured size limit.
- Focused Phase 6 verification passed with 68 tests and no failures.
- Full backend verification passed with 1,457 tests, no failures, and no errors. All 69 Flyway
  migrations reached version 72 in the integration runs.
- The `web-app` and `website` TypeScript checks both completed with zero errors.

---

## Phase 7: Web-App Plan Experience

**Status:** Completed (2026-08-10)

### Objective

Make plan availability understandable and prevent users from entering flows that the API will reject, while treating API enforcement as authoritative.

### Deliverables

- Extend frontend current-session models with effective subscription data.
- Add focused hooks such as:
  - `useCurrentSubscription`
  - `usePlanFeature`
  - `usePlanUsage`
- Add a reusable API error parser for structured plan errors using existing UI patterns.
- Do not introduce a new shared visual component unless an existing Fluent UI component and existing project component cannot satisfy the requirement.
- Hide navigation that is meaningless for the active plan.
- Disable discoverable upgrade features with concise explanatory text where discovery is useful.
- Show Free usage:
  - Exchanges created this month
  - open Exchanges
  - reset date or period boundary
- Show Business seat usage to authorized organization administrators and billing administrators.
- Remove or disable "From Blueprint" for Free users.
- Never present reminders or workflow automation as an individual-plan capability. If a plan
  comparison is shown in the app, Personal workflow automation reads "Coming soon" and is styled
  so it cannot be mistaken for an entitlement, matching the marketing pricing page. Do not read
  `PlanFeature.PERSONAL_REMINDERS` as a Personal feature; its name is historical and only the
  organization plan carries it.
- Prevent additional participant entry on Free.
- Keep external primary recipient and no-account access controls available on Free.
- Refresh effective subscription state after active-organization switching and after any subscription update.
- Handle stale UI state by displaying API plan denials cleanly.
- Ensure every updated component remains responsive and follows Fluent UI styling rules.

### UX principles

- Do not display "not authorized" for a plan limitation.
- Explain the current allowance and the plan that unlocks the feature.
- Do not repeatedly interrupt recipients with upgrade prompts.
- Upgrade prompts belong to the subscription owner, organization owner, or billing administrator.
- A regular Business member should be told to contact their organization administrator where appropriate.

### Acceptance criteria

- Free, Personal, and Business users see the correct controls in personal and organization contexts.
- Mobile layouts remain usable.
- A session refresh reflects plan changes without issuing a new identity token.
- API denials display meaningful plan messages.
- UI restrictions match API restrictions.

### Required help-document updates

Search and read every matching article before editing:

```powershell
rg -n -i "subscription|tier|billing|capacity|Exchange|blueprint|Document Library|workflow|audit|identity" web-app/src/app/components/help-docs/sections
```

At minimum, verify the platform administration overview and any articles for Exchange creation, organization members, blueprints, Document Library, workflows, audit, and identity configuration.

### Required verification

```powershell
Set-Location web-app
npx tsc --noEmit
npm test -- --run
```

Run focused tests first if the full frontend suite is expensive, then run the project-required TypeScript check.

### Completion record

- The authenticated session now returns a DTO-only effective subscription snapshot for the
  signed-in user's active personal or organization context. It includes the resolved subject,
  plan, lifecycle status, enforcement mode, effective features, relevant usage limits, usage
  counters, billing period, and Business seat capacity without exposing persistence entities.
- `AuthContext` exposes a reusable session refresh operation. Active-organization changes,
  successful Exchange creation, and platform subscription, status, entitlement, and capacity
  updates refresh the effective subscription without replacing the identity token.
- Focused `useCurrentSubscription`, `usePlanFeature`, and `usePlanUsage` hooks centralize web-app
  plan state. They fail closed when session data is unavailable, preserve discoverability for
  included features during suspended or otherwise non-mutating lifecycle states, and leave the
  API as the authoritative enforcement boundary.
- A reusable subscription-denial parser accepts both direct response bodies and Axios response
  envelopes, validates the standard denial contract, and presents stale-state API refusals as
  plan messages rather than generic authorization errors.
- Settings navigation now hides irrelevant plan areas, keeps Personal workflow automation visible
  as disabled `Coming soon`, and shows Free Exchange usage or authorized Business seat usage.
  The Settings menu was split into focused components, with styling retained in co-located Fluent
  UI style files.
- Exchange initiation now reflects Blueprint, Document Library, Business Fields, additional
  participant, and advanced access-control availability. Free users retain an external primary
  recipient and no-account access. Hidden additional-participant and constraint state is cleared
  when the active subscription context changes.
- The initiation service now also gates initial `recipientConstraintsJson` submissions with
  `ADVANCED_ACCESS_CONTROLS`, closing a direct-API path that could otherwise bypass the web UI.
- Platform organization subscription editing refreshes current-session state after successful
  mutations. The pricing page continues to describe Personal workflow automation as `Coming soon`.
- All matched help articles were read and the affected Blueprint, Document Library, variables,
  workflow, Exchange access, platform template, and Exchange creation documentation was updated.
  Article, section, and registry files remain within their configured size limits.
- Focused frontend verification passed with 9 tests covering plan hooks, structured denial
  parsing, and subscription refresh after administration updates. The complete frontend suite
  passed with 302 tests across 79 files.
- Focused backend verification passed with 38 tests, including direct-API initiation enforcement,
  subscription DTO mapping, and active-organization context. Full backend verification passed
  with 1,458 tests, no failures, and no errors.
- The `web-app` and `website` TypeScript checks completed with zero errors. Repository diff checks
  found no whitespace errors after line-ending warnings were excluded.

---

## Phase 8: Subscription Lifecycle, Administration, and Rollout

**Status:** In progress (implementation complete; deployment observation pending)

### Objective

Complete operational lifecycle handling and safely enable enforcement in production without requiring an immediate payment-provider integration.

### Deliverables

- Add platform-admin controls for user subscription policies as well as organization policies.
- Restrict tier values to valid subject types.
- Add subscription status and billing-period controls.
- Record audited reasons for plan, status, period, and seat changes.
- Add organization-facing subscription and seat visibility for authorized roles.
- Define trial expiration behavior.
- Define past-due grace-period behavior.
- Define cancellation at period end.
- Ensure suspended accounts retain read and export paths needed to recover their data.
- Add metrics or structured logs for:
  - report-only would-deny decisions
  - enforced feature denials
  - limit denials
  - seat denials
  - subscription resolution failures
- Review report-only production logs and correct unexpected denials.
- Enable `ENFORCE` only after acceptance review.
- Keep payment-provider integration behind the provider-neutral subscription service contract.

### Payment-provider boundary

The initial implementation may use audited platform-admin changes. A future payment provider should update DocuHyphen through a dedicated billing adapter and idempotent webhook processing. Business services must depend on DocuHyphen subscription state, not call the provider during normal requests.

Selecting or adding a payment provider, queue, or new AWS service is outside this plan and requires a separate decision. No new AWS service may be added without explicit user approval.

### Rollout checklist

1. Apply migrations in a non-production environment.
2. Verify every user and active organization has the correct subscription owner record.
3. Run in `REPORT_ONLY` mode.
4. Exercise Free, Personal, Business, active-organization switching, and no-account recipient scenarios.
5. Review all would-deny logs.
6. Correct migration or ownership issues.
7. Enable `ENFORCE` in a non-production environment.
8. Complete regression and mobile testing.
9. Deploy production in `REPORT_ONLY` mode.
10. Review production behavior for an agreed observation period.
11. Enable production enforcement.

### Acceptance criteria

- Platform administrators can safely correct subscription state.
- Every subscription change is auditable.
- Trial, past-due, suspended, and canceled behavior is deterministic.
- Existing data and recipient access remain safe during lifecycle transitions.
- Production enforcement is enabled only after report-only validation.
- Help documentation accurately describes the released behavior.

### Required verification

```powershell
.\mvnw.cmd test
Set-Location web-app
npx tsc --noEmit
npm test -- --run
```

### Completion record

Implementation completed and locally verified on 2026-08-10. The phase remains in progress until
the non-production and production report-only observation steps are completed.

#### Lifecycle and administration

- Added `SubscriptionLifecycleUpdate` and `SubscriptionLifecycleValidator` as the shared
  lifecycle mutation contract. User plans remain limited to Free and Personal, organizations
  remain limited to Business, every mutation requires an audit reason, period boundaries must be
  ordered, trials and cancellations require a period end, and past-due subscriptions require a
  grace-period end.
- `EffectiveSubscription` now expires trial mutation access at the trial period end, permits
  past-due mutations only through the grace period, blocks suspended mutations immediately, and
  permits canceled mutations only until the paid-through period ends. Reads remain available in
  every state.
- Added platform-admin user subscription list, view, and update resources under
  `/platform/users/subscription-policies` and
  `/platform/users/{appUserId}/subscription-policy`, backed by a dedicated service and DTO mapper.
  Temporary recipients and registered applications are excluded.
- Extended organization subscription administration with status, billing frequency, current
  period, grace period, and mandatory audited change reasons. Subscription persistence and
  organization lookup now go through their owning services rather than another service's
  repositories.
- Added immutable Platform Audit event types for user subscription list, view, and update. User
  and organization changes record before and after lifecycle state through required audit capture.

#### Web app and operational rollout

- Added a responsive Platform Administration `User Subscriptions` section with searchable,
  paginated registered-user policies and an audited lifecycle editor.
- Extended the organization account editor with Business lifecycle status, billing frequency,
  period, grace-period, and purchased-seat controls. The current-session subscription refreshes
  after a successful update.
- Organization owners and authorized administrators continue to receive Business seat visibility
  through the existing Settings plan summary; regular members do not see the seat summary.
- Subscription decision logs now use stable `event=subscription_decision` fields and distinguish
  `WOULD_DENY` from `ENFORCED` outcomes plus feature, usage-limit, seat-limit, and lifecycle denial
  types. Resolution failures use `event=subscription_resolution_failure`.
- Declared `app.subscription.enforcement.mode` with the environment override
  `APP_SUBSCRIPTION_ENFORCEMENT_MODE`. Non-production profiles default to `REPORT_ONLY`, while
  `application-prod.properties` defaults to `ENFORCE`; production can still be placed explicitly
  into `REPORT_ONLY` for an observation deployment.
- Updated the Platform Administration help article with the released controls and lifecycle
  behavior. Every matched subscription, tier, billing, capacity, Platform Administration, and
  organization-member help article was read in full; affected article and registry sizes remain
  within their limits.

#### Verification

- Focused backend suite: 12 tests, no failures or errors.
- Full backend suite: `Tests run: 1462, Failures: 0, Errors: 0, Skipped: 0`; BUILD SUCCESS.
- Flyway integration runs validated 69 migrations and reached version 72.
- Focused web-app suite: 9 tests across 5 files, all passed.
- Full web-app suite: 303 tests across 80 files, all passed.
- `node .\node_modules\typescript\bin\tsc --noEmit` in `web-app`: zero errors.

#### Remaining rollout work

- Deploy to a non-production environment with `REPORT_ONLY` and exercise Free, Personal,
  Business, active-organization switching, lifecycle states, seat limits, and no-account
  recipients.
- Review structured would-deny and resolution-failure logs, correct any unexpected decisions,
  then complete non-production `ENFORCE` regression and mobile testing.
- Deploy production in `REPORT_ONLY`, observe it for the agreed period, and enable production
  enforcement only after acceptance review.

---

## Cross-Phase Test Matrix

Every affected phase must preserve or add coverage for these identities and contexts:

| Scenario | Expected subscription context |
|---|---|
| Free user in personal mode | User Free |
| Personal user in personal mode | User Personal |
| Personal user with active organization selected | Organization Business |
| Business organization owner | Organization Business plus owner capabilities |
| Business organization member | Organization Business plus member capabilities |
| Registered external recipient | Resource Share; recipient plan does not grant access |
| No-account recipient | No subscription context; token and Share constraints only |
| Platform administrator managing subscriptions | Platform capability plus audited admin action |
| Suspended subscription owner | Read-only owner access with billing recovery paths |

Every gated feature requires tests for:

1. Included plan with sufficient authorization.
2. Included plan without sufficient authorization.
3. Excluded plan with otherwise sufficient authorization.
4. Direct API request that bypasses the UI.
5. Active-organization switch.
6. Existing resource owned by a different subscription subject.
7. Report-only mode.
8. Enforced mode.

## Definition of Done for the Entire Plan

- Free, Personal, and Business are enforced by the API.
- The web app accurately reflects effective plan features and limits.
- Free Exchange and Business seat limits are concurrency-safe.
- No-account recipients remain outside the tier and seat model.
- Role capabilities and plan features remain separate.
- Existing data survives downgrade, suspension, and cancellation.
- Existing users and organizations are migrated without accidental lockout.
- Platform administrators can inspect and correct subscription state with audit evidence.
- Relevant help documentation is current and within file-size limits.
- Backend tests, frontend tests, and TypeScript checks pass.
- Production rollout completes report-only observation before enforcement.

## Next Session Handoff

### Start here

Phases 1 to 7 are complete and verified. Phase 8 code implementation is complete and locally
verified, but deployment observation is still pending. Continue Phase 8 rollout only.

Deploy and exercise the implementation in report-only mode. Preserve the provider-neutral
subscription boundary, keep reads and recovery exports available during non-active lifecycle
states, and do not enable enforcement until migration and would-deny evidence has been reviewed.

### Read first

1. `AGENTS.md`
2. The Phase 8 implementation record, remaining rollout work, and rollout checklist in this
   document
3. `SubscriptionAccessService`, `SubscriptionLifecycleValidator`, and
   `SubscriptionEnforcementConfigService`
4. The user and organization platform subscription administration services and resources
5. `application.properties` plus the target environment's
   `APP_SUBSCRIPTION_ENFORCEMENT_MODE` setting
6. Structured logs for `event=subscription_decision` and
   `event=subscription_resolution_failure`

### First concrete actions

1. Deploy a non-production environment with `APP_SUBSCRIPTION_ENFORCEMENT_MODE=REPORT_ONLY`.
2. Verify every registered user and active organization has the intended explicit policy row.
3. Exercise Free, Personal, Business, active-organization switching, lifecycle status, seat-limit,
   and no-account recipient scenarios while collecting structured subscription decision logs.
4. Correct any unexpected would-deny or resolution-failure outcome and rerun the full regression
   and mobile checks.
5. Enable `ENFORCE` in non-production only after report-only acceptance review.
6. Deploy production in `REPORT_ONLY`, observe for the agreed period, and obtain acceptance before
   changing production to `ENFORCE`.
7. Once production enforcement is accepted and enabled, mark Phase 8 and the overall plan
   completed and record the deployment evidence.

### Context that must not be lost

- Free and Personal belong to users, Business belongs to organizations. Database check
  constraints enforce this, so an invalid assignment fails at the persistence layer.
- Every registered account and every organization owns an explicit policy row. There is no
  implicit Free organization tier anywhere in the code.
- Organization seat capacity is `max_users` and a null value means uncapped, not a low default.
  Do not reintroduce a default cap; an organization with no assigned capacity stays uncapped
  until a platform administrator records purchased seats.
- Existing accounts were grandfathered onto Personal. Do not downgrade them.
- All membership activation routes now pass through `OrganizationSeatGuard`, which locks capped
  organization subscription rows. Do not add a second seat counter or cap implementation while
  adding organization feature gates.
- No-account recipients are not subscribers, own no policy row, and never consume seats. Neither
  do deactivated members, revoked members, or pending invitations.
- `PlanCatalog` is the only source of fixed plan defaults. Status, seats, periods, and overrides
  live in the database.
- Reminders are deliberately unsold on individual plans. `PERSONAL_REMINDERS` sits in the
  Business set only, and the pricing page says "Coming soon" for Personal. Do not quietly add it
  back to Personal.
- Enforcement defaults to `REPORT_ONLY` through `app.subscription.enforcement.mode`. Every check
  is evaluated and recorded, but nothing is refused until the mode is `ENFORCE`. Never write a
  test that assumes the default mode; construct the mode the test needs.
- A guard must return early when the mode is `OFF` so a disabled environment pays no cost, and
  its test should assert that no repository is touched in that mode.
- Resolve the subject of an existing resource from its persisted owner or scope columns, never
  from the active-organization header. `SubscriptionContext.forOwner` exists for exactly this.
- Only the operations that consume a feature are gated. Reads of content that already exists are
  left alone so a plan change never hides what a participant already has.
- A resource class must log its own unique message for a plan refusal and rethrow. The
  structured 403 body is produced in one place by `SubscriptionDenialExceptionMapper`.
  `SubscriptionDenialException` is a plain `RuntimeException`, so a `catch (e: Exception)` block
  that only rethrows `WebApplicationException` will swallow it into a 500. Add an explicit catch.
- Downgrade must never delete or rewrite anything. Block new creation and configuration, keep
  existing data readable and exportable.
- Existing behaviour tests should be wired with mocked guards so an authorization test stays
  about authorization.
- Code comments must not mention this plan or its phase numbers.
- Do not add a payment provider or any new AWS service.
- `npx` is broken in this environment. Run the type checks with
  `node .\node_modules\typescript\bin\tsc --noEmit` from `web-app` and from `website`.
- The Phase 7 full backend suite passed with 1458 tests. It takes several minutes and starts
  PostgreSQL and Kafka test containers.
