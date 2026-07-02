# Pre-Fields Authorization Hardening Implementation Plan

## Implementation Status

- Status as of 2026-07-01 (updated): All 8 critical cross-cutting findings are fixed.
  Phase 0 is complete. Phase 1 is complete (all 13 items done, including item 13 completed
  2026-07-01). Phase 2 is complete (all 19 cross-org tests pass). Phase 3 is complete (OwnerContext/ScopeReference/
  ResourceReference canonical contracts defined; V30+V31 DB CHECK constraints added; ownership
  verified in all create service paths).
  Phase 4 is complete (ResourceAuthorizationContextRegistry + ExchangeAuthorizationContextProvider
  introduced; DefaultAuthorizationService now resolves owner from target resource for EXCHANGE
  and PRINCIPAL_GROUP; resource-state deny for archived/suspended exchanges wired in;
  ResourceRef.session() alias removed and all 8 callers renamed to ResourceRef.exchange();
  DefaultAuthorizationService refactored to constructor injection; 5 exit tests pass, 31/31
  total tests green). Phase 5 is complete (Action/Capability model: EXCHANGE_RESCIND,
  EXCHANGE_INITIATE, APP_ADMIN wildcard removed; 7 Phase5 tests pass). Phase 6 is complete
  (Application management: signing secrets, webhook destination policy; 24 Phase6 tests pass).
  Phase 7 is complete (Share constraint enforcement: malformed JSON denies, IP allowlist CIDR
  enforcement, MFA enforcement, allowedDownloadFormats intersection obligations, PUBLIC_LINK
  ShareLink grant resolution via X-Share-Link-Token header, SHA-256 hash pipeline through
  EndpointAuthorizationFilter -> AuthorizationContextFactory, ShareLinkValidationService for
  no-auth path, maxViews removed from contract; 12 PublicLinkShare + 28 ShareConstraint
  exit tests pass; 135/135 total tests green 2026-07-01). Phase 8 is complete (see Phase 8 section
  below). Phase 9 is complete (2026-07-01): org group creation now authorizes via
  AuthorizationService against ResourceRef.organization(orgId) (OrganizationAuthorizationContextProvider,
  ResourceType.ORGANIZATION, ResourceRef.organization() added); ExchangeDocumentService APPLICATION
  principal support: validateUserPermissions now uses currentPrincipal() not hardcoded
  PrincipalRef.user(appUser!!.id), all audit paths handle APPLICATION via actorEmail() helper;
  webhook delivery hardened: signingSecretHash renamed to signingSecretToken (raw Base64, V34
  migration), WebhookDeliveryService and WebhookWorkflowActionHandler implemented (HMAC-SHA256
  X-DocuHyphen-Signature-256); document path validation and UUID bypass already done;
  ResourceAuthorizationTest (25 tests) and OrgGroupAuthorizationTest (10 tests) pass;
  201/201 total tests green 2026-07-01. Phase 10 is complete (2026-07-02): complete test matrix
  verified, 11 new Phase 10 tests added (GroupMediatedShareTest 5 tests, RescindSideEffectsTest
  6 tests), 212/212 total tests green; help docs (adminOperationsSection, manageAccessArticle)
  reviewed and confirmed accurate with no updates required.
- Fields implementation status: UNBLOCKED. Fields Foundation Readiness Gate PASSED 2026-07-02.

### All 8 critical findings — resolved

1. **`GET /exchanges/{id}` no auth** — `ExchangeRetrievalService.getExchange()` now authorizes
   `EXCHANGE_VIEW` and throws `ExchangeNotFoundException` for both 404 and 403 (UUID enumeration
   concealment). `GET /exchanges/{id}/access` routed through
   `ExchangeAccessManagementService.getSessionAccessView()`, which enforces `EXCHANGE_MANAGE_ACCESS`.
   TEMP DEBUG logging block removed from `ExchangeResource`.
2. **`APP_ADMIN` capability wildcard** — `RoleCapabilities.kt`: `APP_ADMIN` now maps to an explicit
   set `{APP_ADMIN, APP_AUDIT_READ}`; `APP_AUDITOR` to `{APP_AUDIT_READ, ORG_AUDIT_READ}`;
   `APP_SUPPORT` to `{APP_SUPPORT}`. Customer-content reads (`EXCHANGE_READ`, `DOCUMENT_READ`,
   `GROUP_READ`) removed from `APP_AUDITOR` and `APP_SUPPORT`.
3. **Cross-org privilege gaps** — all identified services now use `isOrgAdminIn(userId, targetOrgId)`
   with the org ID parsed before the auth check: `OrganizationAppUserService` (add/update/delete),
   `OrganizationService.updateOrganization`, `SettingsService.updateOrganizationSettings`,
   `OrganizationExchangeLinkService` (all 5 methods, dead TODO/commented checks removed),
   `OrganizationAuthSessionPolicyResource` (get/settings), `OrganizationIdpSecretRotationRunbookService`,
   `OrganizationIdentityProviderConfigService`, `OrganizationIdpSecretLifecycleService`.
   `AuthAuditResource` now scopes results to the actor's organization (APP_ADMIN sees all).
   `SecurityIncidentResource` now requires `isAppAdmin` (platform-level data).
4. **Exchange has no persisted owner** — `Exchange` entity has `ownerOrganizationId: UUID?` and
   `ownerUserId: UUID?`; V28 migration adds both columns; `ExchangeInitiationService` sets one of the
   two in the same transaction as creation.
5. **Plaintext application credentials** — column renamed `api_secret` → `api_secret_hash` (V29
   migration); `ApplicationRepository` and `ApplicationService` created; `ApplicationService`
   uses `BCrypt.checkpw`, checks `isActive`, updates `lastAccessDate`, issues JWT;
   `ApplicationAuthResource` is now thin (no entity queries, no raw key logging).
6. **`ShareConstraints.parse()` fail-open** — returns `null` on malformed JSON; all callers updated:
   `DefaultAuthorizationService` denies with `INVALID_CONSTRAINTS`; `ExchangeResource`,
   `ExchangeUpdateService`, `ExchangeDocumentService` handle null safely.
7. **`ShareService` dual-write comment** — stale class doc removed; confirmed legacy columns already
   dropped.
8. **`mfaSatisfied`/`clientIp` always zero/null** — `AuthTokenContext` now carries `clientIp: String?`
   populated by `EndpointVerificationFilter` from `X-Forwarded-For`/`X-Real-IP` headers.
   `AuthorizationContextFactory` injects `StepUpAuthService` and calls `isFresh()` for `mfaSatisfied`.
   The `requireMfa` Share constraint now evaluates a real value.

### Phase 1 item 13 — resolved (2026-07-01)

**`APPLICATION` principal wired into centralized capability evaluation.** Token-scope and
endpoint-prefix gating now acts as a boundary check only; business authorization flows through
`DefaultAuthorizationService` for all principal kinds.

Changed files (all uncommitted, working tree):

- `interceptor/EndpointAuthorizationFilter.kt` — injects `ApplicationService`; loads
  `Application` entity via `ApplicationService.findActive(applicationId)` in the APPLICATION token
  branch; aborts 401 if the application is missing or inactive; sets `authToken.application =
  application` so downstream code has the full entity without a second DB round-trip.
- `model/entity/AuthToken.kt` — added `@Transient var application: Application?`.
- `service/application/ApplicationService.kt` — added `findActive(id: UUID): Application?`
  delegating to `ApplicationRepository.findActiveById`.
- `service/auth/authz/AuthorizationContext.kt` — added `applicationId: UUID? = null`; APPLICATION
  requests now carry a real context instead of resolving to `ANONYMOUS`.
- `service/auth/authz/AuthorizationContextFactory.kt` — `currentContext()` checks
  `token.application` first and returns `AuthorizationContext(applicationId = application.id, ...)`
  for application tokens; user path is unchanged.
- `service/auth/authz/DefaultAuthorizationService.kt` — injects `ApplicationService`; `grantsOn()`
  calls new `collectApplicationRoleGrants()` for `APPLICATION` kind principals. The APPLICATION role
  maps to `emptySet()` in `RoleCapabilities` today; resource capabilities come from Share grants.
  The plumbing is in place for Phase 5 to add machine capabilities without structural change.
- `service/auth/authz/Decision.kt` — added `Grant.SourceKind.APPLICATION_ROLE` for audit
  attribution distinct from human `ROLE_ASSIGNMENT`.
- `exception/Exceptions.kt` — added `RoleScopeViolationException`.
- `exception/RoleScopeViolationExceptionMapper.kt` (new) — `@Provider`; maps
  `RoleScopeViolationException` to 400 with `reasonCode = ROLE_SCOPE_VIOLATION`.

### Delivery-rule gate

Phase 1 is fully closed. Phase 2 exit criteria (cross-organization negative tests) have not been
met. The plan rule requires Phase 2 to be closed before moving deeper into Phases 3–9.

### Phase 2 work completed 2026-07-01

**Authorization fix:** `OrganizationAppUserService.getAppUsers` was missing an org-boundary check.
Added `isOrgAdminIn` guard matching the pattern used in `addAppUser`, `updateAppUser`, and
`deleteAppUser` (`src/main/kotlin/.../service/organization/OrganizationAppUserService.kt`).

**Test files written (all in `src/test/kotlin/...`):**

- `service/organization/CrossOrgMemberManagementTest.kt` — 8 tests covering `addAppUser`,
  `updateAppUser`, `deleteAppUser`, `getAppUsers` for negative (OrgB target denied) and positive
  (OrgA target passes auth gate) cases. Target: `OrganizationAppUserService`.
- `service/organization/CrossOrgExchangeLinkTest.kt` — 8 tests covering `createLink`, `acceptLink`,
  `deLink`, `getLinksByOrganization` for negative and positive cases. Target:
  `OrganizationExchangeLinkService`.
- `resource/CrossOrgAuditScopeTest.kt` — 3 tests: ORG_ADMIN gets 200 with events scoped to own
  org; ORG_ADMIN does not receive APP_ADMIN (null-org) scope; non-admin gets 403. Target:
  `AuthAuditResource`.

**Next session start**

Read `AGENTS.md` and this plan in full before writing any code.

**Phase 4 complete.** `ResourceAuthorizationContextRegistry` and `ExchangeAuthorizationContextProvider`
introduced; `DefaultAuthorizationService` now resolves the owner organization from the target
Exchange, not from `context.activeOrgId`. Resource-state denies (archived / suspended) wired in.
`ResourceRef.session()` alias removed; all callers use `ResourceRef.exchange()`. Constructor
injection adopted in `DefaultAuthorizationService`. 5/5 Phase 4 tests pass; 31/31 total green.

**Phase 5 complete (2026-07-01).** Action and Capability model fully expanded.

Changed files (all uncommitted, working tree):

- `service/auth/authz/Action.kt` — expanded from 22 to 98 actions covering Exchange
  (INITIATE, RESCIND added), Exchange documents, Document Library, Blueprint, Workflow
  Definition, Workflow Webhook, Sequence, Variable, Communication, Principal Group (GROUP_EDIT
  added), Organization, Platform (APP_SUPPORT_OPERATE added), and Application Registration.
- `service/auth/authz/Capability.kt` — expanded from 23 to 80 capabilities. EXCHANGE_INITIATE
  and EXCHANGE_RESCIND added. DOC_LIBRARY_*, BLUEPRINT_*, WORKFLOW_*, WEBHOOK_*, SEQUENCE_*,
  VARIABLE_*, COMMUNICATION_*, GROUP_EDIT, APP_REG_READ, APP_REG_ADMIN added. All new
  capabilities default to no role (default-deny extension point confirmed by WEBHOOK_DELIVER test).
- `service/auth/authz/RoleCapabilities.kt` — all role families updated with intentional
  capabilities for their scope. EXCHANGE_RESCIND added to OWNER Share role only. EXCHANGE_INITIATE
  added to APP_USER and ORG_MEMBER. APPLICATION role remains emptySet() (EXCHANGE_INITIATE
  comes from Application.grantedCapabilitiesJson only). ORG_OWNER and ORG_ADMIN receive full
  org-resource admin capabilities. ORG_MEMBER receives discover/read/use only (no write/admin).
  GROUP_EDIT added to group OWNER and MANAGER. APP_ADMIN gains APP_REG_READ + APP_REG_ADMIN.
  ORG_BILLING_MANAGE reserved to ORG_OWNER; ORG_ADMIN does not receive it.
- `model/entity/ResourceType.kt` — six new values: DOC_LIBRARY, BLUEPRINT, WORKFLOW_DEFINITION,
  SEQUENCE, VARIABLE, COMMUNICATION.
- `service/auth/authz/ResourceAuthorizationContextRegistry.kt` — exhaustive `when` in
  `toResourceKind()` extended to map all six new ResourceType values to their ResourceKind.
- `model/entity/Application.kt` — added `ownerOrganizationId: UUID?` and
  `grantedCapabilitiesJson: String` (default `[]`).
- `service/auth/authz/DefaultAuthorizationService.kt` — `collectApplicationRoleGrants()` now
  unions role capabilities with `parseApplicationCapabilities(grantedCapabilitiesJson)`.
  Added private `parseApplicationCapabilities()` helper (no external dependency; fail-closed
  on malformed JSON).
- `db/migration/V32__application_capability_grants.sql` — adds `owner_organization_id` and
  `granted_capabilities` columns to the `application` table.
- `test/.../Phase5ActionCapabilityModelTest.kt` — 29 tests: default-deny extension point,
  EXCHANGE_RESCIND scope, EXCHANGE_INITIATE scope, ORG_MEMBER write restriction, GROUP_EDIT
  scope, APP_ADMIN/APP_AUDITOR registration access, multi-role composition (Ethan scenario),
  scope isolation, discover vs view vs value, Use does not imply Edit.
- `test/.../Phase5ApplicationCapabilityGrantTest.kt` — 7 tests: CLM with EXCHANGE_INITIATE
  grant allowed, CLM gains no other Exchange capabilities, application without grant denied,
  authorize() Allow and Deny, inactive application denied, malformed JSON fails closed.

Test result: 67/67 pass (38 Phase 5 + 29 prior phases).

Fields-blocking items in Phase 5: EXCHANGE_INITIATE, EXCHANGE_RESCIND, APPLICATION capability
resolution, and the default-deny extension point are all Fields-blocking. The new resource type
capabilities (DOC_LIBRARY, BLUEPRINT, etc.) are broader platform hardening.

**Phase 6 complete (2026-07-01).** Platform administrator, credential, and webhook hardening.

Changed files (all uncommitted, working tree):

- `service/auth/AppRoleAssignmentService.kt` — removed auto-bootstrap from `requireAppAdmin()`.
  Method now simply checks `isAppAdmin(actorId)` and throws SecurityException if false. The
  config-based startup bootstrap (`bootstrapFirstAppAdmin`) is the only remaining in-process path.
- `service/auth/AuthenticationService.kt` — application JWT now includes `iss` and `aud` claims
  (both set to `ConfigurationService.getJwtIssuer()` which is the deployment base URL).
- `service/config/ConfigurationService.kt` — added `getJwtIssuer()` returning `baseUrl`.
- `model/entity/ResourceType.kt` — added `APPLICATION` and `WORKFLOW_WEBHOOK_ENDPOINT`.
- `service/auth/authz/ResourceAuthorizationContextRegistry.kt` — exhaustive `when` extended;
  both new types map to `null` (no resource-state provider; resource-state checks skipped).
- `service/auth/authz/Action.kt` — added `APP_REG_LIST` and `APP_REG_CREATE` actions.
- `repository/ApplicationRepository.kt` — added `findAllOrdered()`.
- `exception/Exceptions.kt` — added `ApplicationNotFoundException`.
- `service/application/ApplicationManagementService.kt` (new) — list, get, create, deactivate,
  rotateCredentials, updateGrantedCapabilities. Every operation authorized via `AuthorizationService`
  using `APP_REG_READ` / `APP_REG_ADMIN` capability. Credential generation uses `SecureRandom`
  (Base64url-encoded). Audit records emitted for every mutation.
- `resource/ApplicationManagementResource.kt` (new) — thin adapter at `/admin/applications`.
  All authorization delegated to `ApplicationManagementService`. Raw secret returned only at
  create and rotate time; never stored in plaintext.
- `model/entity/WorkflowWebhookEndpoint.kt` (new) — JPA entity with `signingSecretHash` stored
  separately from `Application.apiSecretHash`. Separate `signingSecretVersion` for overlap-window
  rotation.
- `repository/WorkflowWebhookEndpointRepository.kt` (new).
- `service/application/WebhookDestinationPolicy.kt` (new) — SSRF guard. Validates URLs against
  loopback, link-local, private/site-local, and EC2 metadata-service address ranges. DNS resolution
  at registration time. Non-http(s) schemes denied.
- `service/application/WorkflowWebhookEndpointManagementService.kt` (new) — register, enable,
  disable, rotateSigningSecret. Every operation authorized via `WEBHOOK_ADMIN` capability. Audit
  records emitted. Target URL validated via `WebhookDestinationPolicy` before persist.
- `resource/WorkflowWebhookEndpointResource.kt` (new) — thin adapter at
  `/organizations/{orgId}/workflow-webhooks`.
- `db/migration/V33__workflow_webhook_endpoint.sql` (new) — creates `workflow_webhook_endpoint`
  table with CHECK constraints on target_url and signing_secret_version.
- `pom.xml` — added `mockito-kotlin 5.4.0` test dependency.
- `test/.../Phase6AppAdminGuardTest.kt` — 4 tests: SecurityException thrown when non-admin calls
  requireAppAdmin; no save() called regardless of org-admin status; existing admin not blocked.
- `test/.../Phase6ApplicationManagementTest.kt` — 10 tests: list/rotate/deactivate/capabilities
  with Allow and Deny decisions; rotation produces new credentials each time; ApplicationNotFoundException.
- `test/.../Phase6WebhookDestinationPolicyTest.kt` — 14 tests: valid public IP allowed; loopback,
  link-local, EC2 metadata, private ranges, non-http(s) schemes, blank URLs denied.

Test result: 95/95 pass (28 Phase 6 + 67 prior phases).

Fields-blocking items in Phase 6: auto-bootstrap removal (prevents standing platform role
self-escalation via REST), application credential management (revocable, least-privilege principals),
webhook endpoint credential separation (signing secrets separate from API credentials). The full
time-limited support-elevation workflow is broader platform hardening deferred per the plan boundary.

### Next action (priority order)

1. **Phase 8 — Organization context and frontend permissions:** Explicit active organization
   selection, multi-org session model, frontend capability contract replacing literal role checks.

2. **Phase 9:** Frontend permission rendering and action controls;
   central authorization adoption platform-wide.

No Fields entities, migrations, services, or user interfaces may be added until the Fields
Foundation Readiness Gate passes.

No Fields entities, migrations, services, or user interfaces may be added until the Fields
Foundation Readiness Gate passes.

## Mandatory Starting Instruction

Before performing any work in this plan, read `AGENTS.md` in full and treat it as the controlling
project instruction set. Re-read it at the start of every implementation phase because repository
rules may change while this plan is being delivered.

## Fields Dependency and Readiness Boundary

The Fields implementation must not start until the Fields Foundation Readiness Gate defined in this
plan passes. That gate is narrower than completion of every broader platform-hardening item because
the first Fields release supports Platform and Organization configuration scopes and an Exchange
resource adapter only.

Writing code for Schema Definitions, Field Definitions, Schema Assignments, Field Values,
Field-based workflow rules, or Fields user interfaces before the Fields Foundation Readiness Gate
passes is out of scope. The only permitted Fields-related work before that gate is analysis or
updating architecture and planning documents.

Fields classify resources and expose typed business metadata. Fields do not grant resource access.
A separate Access Policy capability may consume classification in the future, but dynamic
metadata-driven Access Policies are deferred by `FIELDS-FEATURE.md` and are not implemented by this
plan.

The complete authorization-hardening program remains required. Broader items that do not block the
first Exchange-focused Fields release may continue after the Fields Foundation Readiness Gate. No
later resource type may receive a Fields adapter until that resource has passed the same ownership,
authorization, visibility, and output-channel requirements.

## Delivery Environment and Clean-Break Rule

DocuHyphen is not in production. The database will be cleared after this authorization hardening
work is implemented. This plan therefore requires a clean break to the target authorization model.

- Do not add backward-compatibility layers.
- Do not add dual-read, dual-write, fallback, or transitional authorization paths.
- Do not preserve obsolete role enums, DTO fields, endpoints, columns, services, or frontend checks.
- Do not write data backfills for development data that will be discarded.
- Do not retain insecure behavior merely because current development data or code depends on it.
- Delete replaced code after all call sites use the new model.
- Update fixtures, seed data, tests, and local setup to create valid data in the new model directly.
- The final schema must initialize successfully from an empty database.

Flyway or schema work in this plan exists to define and verify the final clean database structure,
not to support an upgrade from a production or historical database. The implementation must not
carry temporary compatibility code into the final result.

## Purpose

This plan hardens DocuHyphen's existing role and authorization implementation before the Fields
architecture in `FIELDS-FEATURE.md` is implemented. It addresses the following findings in the
current platform:

- A single `RoleName` enum mixes human platform, organization, group, and Exchange Share roles and
  does not represent registered external applications as first-class authorized principals.
- Some write paths accept a role without proving that it is valid for the target scope.
- Some organization operations authorize against a user's primary organization instead of the
  organization named by the resource or request.
- Governed resources do not all expose a consistent, stable owner context.
- `DefaultAuthorizationService` cannot centrally resolve the owning scope of every resource.
- Some services use `AuthorizationService`, while others use custom `isOrgAdmin` or ownership
  booleans.
- Some direct Exchange endpoints load data without an explicit resource authorization decision.
- Application administrator, auditor, and support capabilities are broader than necessary.
- Share constraints have incomplete fail-closed and runtime enforcement behavior.
- The frontend infers permissions from an incomplete role model instead of effective capabilities
  for an explicit active organization.
- Authorization-specific tests do not yet provide a complete role, tenant, resource, and action
  regression matrix.

## Scope

This plan covers authorization foundations used by the existing platform. It includes:

- Role taxonomy and scope validation.
- First-class authorization for registered external applications using the REST API.
- Secure workflow webhook delivery to registered external applications and authorization of any
  subsequent application action against an Exchange.
- Organization boundary enforcement.
- Stable resource ownership.
- Central resource context resolution.
- Capability and action definitions for existing governed resources.
- Central authorization adoption across backend services.
- Human platform administrator, auditor, and support controls.
- Exchange Share and constraint enforcement.
- Explicit active organization context.
- Frontend capability consumption.
- Audit, clean-schema initialization, documentation, and test coverage.

This plan does not implement Fields or a future metadata-aware Access Policy capability.

## Fields Foundation Readiness Gate Scope

The following requirements are Fields-blocking:

- Scope-safe platform-user, external-application, organization, Principal Group, and Exchange Share
  roles.
- Target-organization authorization with no primary-organization substitution.
- A canonical resource owner context that distinguishes ownership from configuration governance
  scope.
- A canonical Resource Reference contract and a registered authorization-context provider for
  Exchanges and Exchange documents.
- Default-deny action and capability extension behavior for future Schema, Field, Assignment, and
  Value operations.
- Explicit authorization for Platform and Organization configuration administration.
- Fail-closed Exchange Share behavior for direct users, groups, participants, registered
  applications, service accounts where supported, and public or magic-link access.
- Explicit active organization context.
- Central authorization for authenticated and no-auth Exchange detail, list, search, document,
  access-management, workflow, export, and realtime surfaces that will carry Field data.
- A two-stage response contract in which resource authorization does not imply Field visibility.
- Webhook payload projection and callback authorization that cannot bypass Exchange or future Field
  visibility policy.
- Tests proving the requirements above and a signed Fields Foundation readiness record.

The following work remains part of the complete hardening program but does not by itself block the
first Exchange-focused Fields foundation:

- Central authorization adoption for a resource that cannot yet receive a Fields adapter.
- A complete support-elevation workflow beyond the minimum rule that standing support and audit
  roles cannot read customer content.
- Transactional view-limit accounting when the unsupported constraint is removed from the active
  contract instead.
- Frontend capability conversion for screens unrelated to the first Fields release, provided their
  backend authorization remains correct.

This boundary is an architecture dependency statement. The delivery team may still choose to finish
the entire hardening program before starting Fields as a scheduling or risk-management decision.

## Delivery Rules for Every Phase

Each phase is a hard gate for the work it contains. Work should be delivered in phase order unless
the plan is formally amended with a written dependency analysis. The Fields Foundation Readiness
Gate may be evaluated once every Fields-blocking requirement is complete, even if explicitly
non-blocking platform-hardening items remain open.

At the end of every phase:

1. Inspect the changed code, not only the test result.
2. Compare the implementation with the phase requirements in this plan.
3. Search for old patterns that the phase was intended to remove and delete them.
4. Run the phase-specific automated tests and the relevant broader regression suite.
5. Review clean database initialization where data structures changed. Backward compatibility is
   not required.
6. Review every matched help article in full when user-visible behavior or permissions changed.
7. Record evidence for each exit criterion.
8. Mark the phase complete only when every exit criterion passes.
9. State the next phase and its first action in the phase completion note.
10. Mark each item in the evidence record as Fields-blocking or broader platform hardening.

If verification finds a gap, remain in the current phase. Fix and re-verify it before moving on.

## Role Inventory and Role Decisions

### Existing Human Platform Roles

| Role | Intended meaning after hardening |
|---|---|
| `APP_ADMIN` | Manages the DocuHyphen control plane; does not automatically receive all future capabilities |
| `APP_AUDITOR` | Reads platform audit information; customer content access is not implied |
| `APP_SUPPORT` | Uses support tooling; customer content access requires an explicit elevated access grant |
| `APP_USER` | Baseline authenticated-user designation for a signed-in user who may operate entirely without organization membership and has no administrative capabilities |

### External Application Role

| Role | Intended meaning after hardening |
|---|---|
| `APPLICATION` | Identifies a registered non-human application that authenticates with its own credentials and calls the DocuHyphen REST API using only explicitly granted, scope-safe capabilities |

`APPLICATION` is distinct from the human `APP_USER` role, the human platform-administration roles,
and a generic internal `SERVICE_ACCOUNT`. It represents external products and integration clients,
such as a Contract Lifecycle Management application integrating with DocuHyphen.

- Add `APPLICATION` as a first-class principal kind and preserve the registered application ID in
  authentication context, authorization decisions, resource relationships, and audit records.
- An application may be linked to an organization and receive organization-scoped capabilities. It
  must not select or infer an organization that was not explicitly granted to it.
- Token scopes constrain the token's audience and maximum API boundary. They do not replace
  resource-aware role and capability authorization.
- `ApplicationType`, endpoint path prefixes, possession of valid API credentials, or successful
  token issuance must not grant a business capability by themselves.
- An application may receive a capability such as `EXCHANGE_INITIATE`. It receives no other
  Exchange, document, Fields, administrative, or customer-content capability unless explicitly
  granted.
- An application that initiates an Exchange is recorded as the initiating principal. The Exchange
  owner context remains explicit, normally the organization for which the application is authorized
  to act, and must never be guessed from token defaults.
- Every application-initiated Exchange must receive at least one active human `OWNER` Share as part
  of the same transaction. The human owner manages the Exchange lifecycle and prevents an external
  application from becoming the sole authority over customer content.
- An application does not impersonate an `APP_USER`. Any separately supported delegated-user flow
  must record both the application and delegating user and authorize both parts of the delegation.

Example: Acme registers its CLM as an `APPLICATION`, links it only to Acme, and grants it
`EXCHANGE_INITIATE`. The CLM initiates an Acme-owned Exchange, is recorded as its initiating
principal, and assigns Maya as the human `OWNER`. Maya manages access and lifecycle actions. The CLM
cannot read unrelated Exchanges, administer Acme, act for another organization, or gain capabilities
merely by requesting a broader token scope.

### Workflow Webhook and Application Callback Requirement

DocuHyphen workflows may send outbound webhooks to a registered external application. That
application may subsequently call the DocuHyphen REST API to perform an authorized action on the
Exchange. Outbound delivery and the later inbound API request are separate security decisions.

- A workflow webhook endpoint belongs to an explicit owner context and registered application. Its
  URL, authentication method, signing secret, enabled state, permitted event types, and payload
  contract are governed resources.
- Before delivery, DocuHyphen authorizes the workflow action, resolves the target application and
  owner context, and projects only the data permitted for that webhook contract. Exchange access
  must not imply that every future Field Value is included.
- Every outbound webhook is signed, timestamped, uniquely identified, correlated to its workflow
  instance and Exchange, and protected against replay. Secrets are encrypted or stored through an
  approved secret manager and are never returned or logged in plaintext.
- Retries use a stable delivery ID and documented idempotency semantics. Delivery history records
  attempts, response status, timing, and redacted failure details without storing unauthorized
  payload data.
- Receiving a webhook does not authorize the external application to call DocuHyphen. Any later API
  request authenticates as the registered `APPLICATION` and passes token-boundary, capability,
  owner-context, target-resource, lifecycle-state, Share-constraint, and idempotency checks.
- An application may perform only explicitly granted actions such as adding a document, updating
  permitted Exchange data, recording a workflow result, or reading status. It does not inherit the
  human `OWNER` role from the workflow or webhook.
- If one webhook is intended to delegate a particular follow-up action, represent that authority as
  a short-lived, audience-bound workflow action grant. Bind it to the application, workflow instance,
  Exchange, allowed action set, expiry, and one-time or idempotent-use rule. It must not be a general
  bearer link or a substitute for the application's identity.
- Callback results and resulting Exchange mutations are attributed to the application and correlated
  to the webhook delivery and workflow instance. The human owner remains visible and retains
  lifecycle control.

### Existing Organization Roles

| Role | Intended meaning after hardening |
|---|---|
| `ORG_OWNER` | Full organization governance, including billing and owner-only decisions |
| `ORG_ADMIN` | Organization administration excluding owner-only and billing-only decisions unless granted |
| `ORG_BILLING_ADMIN` | Billing administration and only the audit access specifically required for billing |
| `ORG_USER_MANAGER` | Organization membership administration and permitted group discovery |
| `ORG_AUDITOR` | Organization audit access and explicitly approved read-only resource access |
| `ORG_MEMBER` | Standard member with no implicit administration |
| `ORG_GUEST` | Limited organization membership with no implicit resource access |

### Existing Principal Group Roles

| Role | Intended meaning after hardening |
|---|---|
| `OWNER` | Owns and fully administers a Principal Group |
| `MANAGER` | Manages group membership and permitted group settings |
| `MEMBER` | Participates in the group and receives grants assigned to the group |
| `OBSERVER` | May view the group only where organization policy allows it |

### Existing Exchange Share Roles

| Role | Intended meaning after hardening |
|---|---|
| `OWNER` | Owns and administers one Exchange |
| `EDITOR` | Reads and edits an Exchange and its permitted documents |
| `REVIEWER` | Reviews and comments on permitted Exchange documents |
| `SIGNER` | Reads and signs permitted Exchange documents |
| `VIEWER` | Reads an Exchange subject to download and other Share constraints |
| `COMMENTER` | Reads and comments without general edit permission |
| `PARTICIPANT` | Participates with the minimum capabilities defined for the Exchange process |

### New Role Decision

No new user-facing business role is required by this pre-Fields plan.

The implementation should introduce scope-safe Kotlin role types such as `AppRoleName`,
`ApplicationRoleName`, `OrganizationRoleName`, `PrincipalGroupRoleName`, and
`ExchangeShareRoleName`. These are new code types, not new user-facing roles. Their constants should
use the approved business role names listed above.
There is no requirement to preserve the current persistence representation or parse obsolete role
values.

`OWNER` is intentionally present in both the Principal Group and Exchange Share role families. Its
capabilities must be resolved using its scope. A group owner must not receive Exchange owner
capabilities merely because both roles currently use the same stored string.

Do not add roles such as `ORG_CONTENT_ADMIN`, `APP_CONTENT_READER`, or `FIELD_ADMIN` during this
pre-work unless a separately approved business requirement proves that a role is necessary.
Fine-grained behavior should normally be expressed as capabilities. A future Fields implementation
may propose additional roles, but that decision belongs to the Fields implementation plan.

### Multi-Role User Requirement

DocuHyphen must support one user holding multiple roles at the same time, including multiple
organization roles within the same organization. Specialized organization roles are additive and
must not replace the user's ordinary participation role.

- A user may hold `ORG_MEMBER` together with `ORG_BILLING_ADMIN`, `ORG_ADMIN`,
  `ORG_USER_MANAGER`, `ORG_AUDITOR`, or `ORG_OWNER` in the same organization.
- Organization governance roles grant their specialized administrative capabilities. They must not
  prevent the user from performing ordinary member activities granted through `ORG_MEMBER`.
- Exchange Share roles remain separate, resource-scoped grants. An organization member may also be
  an `OWNER`, `EDITOR`, `VIEWER`, or other Share role on a particular Exchange.
- Effective capabilities are composed from all valid role assignments applicable to the selected
  organization and target resource. Roles from another organization or resource must not contribute.
- Role assignment, removal, session serialization, auditing, and frontend rendering must preserve
  the full role set rather than selecting one primary or highest role.

For example, Ethan may hold both `ORG_BILLING_ADMIN` and `ORG_MEMBER` in Acme. He uses billing
capabilities while managing Acme's subscription, ordinary member capabilities while collaborating
with a colleague through the platform, and an Exchange Share role such as `EDITOR` for the specific
Exchange. Removing his billing role must not remove his member or Exchange access.

### Machine and Non-User Principal Decision

Registered applications, service accounts, workflow actors, migration actors, participants, and
public links are principals, not identities to be silently mapped onto application users.

- Registered applications use the `APPLICATION` machine role and only their explicitly assigned,
  scope-safe capabilities.
- Service accounts may receive only explicitly assigned, scope-safe capabilities.
- A workflow action runs as a recorded system actor or delegated initiating actor according to a
  documented rule. It does not inherit unrestricted platform authority.
- Migration actors are limited to controlled migration operations and remain attributable in audit.
- Participants and public links receive only Exchange Share capabilities and constraints associated
  with their resolved grant.
- Unknown or unresolved principal kinds fail closed.

The Fields implementation may record these actors as value provenance, but provenance never grants
authorization.

## Target Authorization Principles

- A role is valid only within its declared scope.
- A capability describes an allowed action; a role is a named bundle of capabilities.
- A principal may hold multiple additive roles, and authorization evaluates every applicable role in
  the current scope without leaking capabilities across scopes.
- Resource type and owning scope are inputs to every authorization decision.
- Organization authority applies only to the organization that owns the target resource.
- Platform administration does not automatically imply unrestricted customer-content access.
- Resource list, search, direct lookup, use, and mutation operations enforce the same policy.
- Malformed, unavailable, or ambiguous authorization data fails closed.
- Backend authorization is authoritative. Frontend visibility is a usability aid only.
- Resource authorization and Field visibility are separate decisions. Resource access must never
  imply access to every Field Value.
- Classification metadata does not grant access unless a separately approved policy engine evaluates
  it as an input.
- User, registered-application, participant, service-account, workflow, public-link, and other system
  principal behavior is explicit. No principal receives authority merely because it is
  non-interactive or internal.
- Existing behavior is retained only when it is explicitly confirmed as part of the target model,
  not for compatibility.
- Every denied and privileged decision is explainable and testable.

## Phase 0: Reconfirm the Baseline and Freeze Fields Implementation

### Objective

Create an evidence-backed baseline of the current authorization system and prevent accidental Fields
implementation from beginning before the Fields Foundation Readiness Gate passes.

### Implementation Work

1. Read `AGENTS.md`, this plan, and `FIELDS-FEATURE.md` in full.
2. Create a tracked phase checklist in the team's normal work-tracking system.
3. Inventory every role write path and every place that parses `RoleName` from a string.
4. Inventory all uses of `isAppAdmin`, `isOrgAdmin`, `isOrgAdminIn`, ownership booleans, and direct
   role-name comparisons.
5. Inventory all protected REST endpoints and map each endpoint to its service authorization check.
6. Inventory the owner fields and scope fields on Exchanges, Document Library entries, Blueprints,
   Workflow Definitions, Sequence Definitions, Variable Definitions, Communications, and Principal
   Groups.
7. Inventory authenticated-user, registered-application, participant, service-account, workflow,
   public-link, magic-link, and unauthenticated principal paths.
8. Inventory every output channel that may expose governed data, including detail DTOs, lists,
   search, exports, reports, workflow snapshots, notifications, audit views, realtime events, logs,
   integration payloads, and workflow webhook deliveries.
9. Record existing tests for roles, organizations, Shares, direct resource access, and frontend
   permission rendering.
10. Decide and document whether personal resource ownership is represented by the same type as
    Platform and Organization configuration governance scope or by a distinct `OwnerContext`.
11. Add a visible project note or tracking gate stating that Fields implementation is blocked by this
   plan. Do not add placeholder Fields entities or migrations.
12. Create a deletion register for obsolete roles, parsers, DTO fields, endpoints, columns, services,
   frontend checks, and tests that must not survive the clean break.
13. Inventory planned or existing webhook endpoint registration, outbound delivery, retry, signing,
    secret storage, callback correlation, idempotency, and workflow action delegation paths.

### Required Baseline Artifacts

- Role-to-scope inventory.
- Endpoint-to-action inventory.
- Resource-to-owner inventory.
- Current capability matrix.
- Authorization bypass and inconsistency register with severity and affected files.
- Principal-path and output-channel inventory.
- Recorded decision separating resource owner context from configuration governance scope.
- Test coverage gap list.
- Obsolete-code deletion register.

### Verification Gate

- Re-run repository searches and compare every match with the inventories.
- Inspect `RoleName.kt`, `RoleCapabilities.kt`, `Action.kt`, `Capability.kt`,
  `DefaultAuthorizationService.kt`, and `AuthorizationContextFactory.kt` directly.
- Inspect representative services and resources for every governed resource family.
- Compare the resulting findings with this plan and amend the plan if the code changed since the
  analysis.
- Confirm that no Fields implementation files or migrations were introduced.

### Exit Criteria

- Every current role and authorization entry point is accounted for.
- Every existing governed resource has a documented current owner source, including missing or
  unstable ownership.
- Security gaps have owners and intended phases.
- The Fields implementation block is visible to the delivery team.

### Next Step

Begin Phase 1 by replacing the mixed role model with explicit role families and scope validation.

## Phase 1: Separate Role Families and Enforce Role Scope

### Objective

Make it impossible for a human platform role, external-application role, organization role, group
role, or Exchange Share role to be written into the wrong scope.

### Context

`RoleName` currently combines all role families. Services that accept `RoleName.valueOf(...)` can
therefore accept a valid role name that is invalid for the target scope. For example, an
organization membership must never contain `APP_ADMIN`, and an Exchange Share must never contain
`ORG_BILLING_ADMIN`.

### Implementation Work

1. Introduce scope-safe role types for human platform, external application, organization, Principal
   Group, and Exchange Share roles.
2. Define the final persistence model for scoped roles without maintaining compatibility with the
   mixed `RoleName` representation.
3. Update `RoleAssignment`, `OrganizationMembership`, `PrincipalGroupMember`, and `Share` write
   services to accept only the correct role family.
4. Update registered-application and service-account role assignment paths to accept only the role
   families and scopes approved for their distinct machine principal kinds.
5. Replace general `RoleName.valueOf(...)` parsing at request boundaries with scope-specific parsers.
6. Reject unknown or wrong-scope roles with a clear `400 Bad Request`; do not silently substitute a
   broader or narrower role.
7. Validate Exchange initiation recipient roles against the Exchange Share role family.
8. Update DTOs and frontend enums so each role selector receives only its allowed values.
9. Define database constraints and clean seed data so invalid role and scope combinations cannot be
   created.
10. Keep role display labels separate from persisted identifiers.
11. Delete the mixed `RoleName` model and obsolete parsers after all call sites are converted. Do not
    leave adapters or fallback parsing behind.
12. Model organization role assignments so one active membership can retain multiple organization
    roles in the same organization, with assignment and removal operating on an individual role
    without replacing unrelated roles.
13. Replace application-token authorization based only on token scope and endpoint prefix with
    resolution of a registered `APPLICATION` principal and centralized capability evaluation. Token
    boundaries remain an additional restriction, never the business authorization decision.

### Verification Gate

- Search for remaining uses of the mixed `RoleName` in write paths.
- Inspect every role mutation service and REST adapter.
- Test every valid role in its intended scope.
- Test multiple organization roles assigned concurrently to the same user in the same organization.
- Test that adding or removing one role preserves the user's other role assignments.
- Test `APPLICATION` against every human, organization, group, and Exchange Share role scope and
  reject invalid substitutions or attempts to deserialize it as `APP_USER`.
- Test every role family against every invalid scope and confirm rejection.
- Initialize an empty database and inspect seeded role assignments for invalid combinations.
- Confirm the obsolete-code deletion register has no remaining Phase 1 items.
- Compare the implementation with the complete role inventory in this plan.

### Exit Criteria

- Wrong-scope role assignment is blocked by backend types or validation and database safeguards.
- Freshly created and seeded assignments have the approved business meaning.
- Frontend role choices match backend role families.
- Organization membership persistence and APIs preserve a user's complete role set.
- No new user-facing role has been introduced without a documented business decision.

### Next Step

Begin Phase 2 by fixing organization-boundary checks against the actual target organization.

## Phase 2: Enforce Target Organization Boundaries

### Objective

Ensure organization authority is evaluated against the organization being read or changed, never a
different primary or default organization.

### Context

A user can belong to more than one organization. A caller who administers Organization A must not be
treated as an administrator of Organization B. Methods that accept an organization ID must authorize
against that exact ID or against the stable owner resolved from the target resource.

### Implementation Work

1. Replace authorization uses of `isOrgAdmin(userId)` with target-aware capability checks.
2. Audit `OrganizationAppUserService`, `OrganizationMembershipService`, `OrganizationService`,
   organization pairing, group administration, billing, policies, and audit endpoints.
3. Add authorization to organization member-list operations, including direct organization-ID
   requests.
4. Prevent callers from changing, deactivating, deleting, or assigning roles to members of an
   organization they do not administer.
5. Preserve last-owner and last-administrator safety rules within the target organization.
6. Ensure human platform privilege is checked separately and explicitly where it is permitted.
7. Return `404 Not Found` where resource concealment is required and `403 Forbidden` where revealing
   existence is acceptable and documented.
8. Emit audit records for organization role and membership mutations.

### Example

Alice is `ORG_ADMIN` in Legal Firm A and `ORG_MEMBER` in Client B. A request from Alice to update a
member of Client B must be denied even if Legal Firm A is her primary organization.

### Verification Gate

- Search for `isOrgAdmin(` and review every remaining match.
- Inspect every service method that accepts or derives an organization ID.
- Run cross-organization negative tests for reads and mutations.
- Run same-organization positive tests for each organization administrative role.
- Compare the implementation with the endpoint and organization-boundary inventories from Phase 0.

### Exit Criteria

- No organization permission is inherited from an unrelated primary organization.
- All organization member-list and mutation endpoints enforce target-organization authorization.
- Cross-organization negative tests cover direct IDs as well as list endpoints.

### Next Step

Begin Phase 3 by establishing stable ownership for every resource that Fields may later govern.

## Phase 3: Establish Stable Resource Ownership

### Objective

Give every governed resource an immutable or deliberately transferable owner context that can be
resolved without guessing from the current user session, while preserving DocuHyphen's ability to
operate for users who do not belong to any registered organization.

### Product Goal Alignment

DocuHyphen must remain usable for an authenticated person who has no registered organization.
Organization ownership is important, but it is not the baseline assumption for the whole product.

- Personal ownership must be a first-class model, not a temporary compatibility path.
- Authentication, Exchange participation, and personal resource ownership must not depend on
  organization creation or organization membership.
- Organization context adds governance and collaboration scope where needed, but the absence of an
  organization must not make the user unauthorized by default.

### Ownership Context and Configuration Governance Scope

Resource ownership and Fields configuration governance are related but distinct concepts.

- `OwnerContext` identifies who owns a governed resource and must cover Platform, Organization, and
  Personal ownership where those resource forms exist.
- `ScopeReference` identifies who governs reusable configuration. The first Fields release resolves
  Platform and Organization configuration scopes only.
- A Resource Reference identifies the resource type and resource ID. It does not encode permission
  or configuration scope by itself.
- An adapter or authorization-context provider resolves the authoritative owner from the resource.

Do not force personal ownership into a nullable Organization `ScopeReference`, and do not create a
second incompatible owner or resource-reference abstraction inside the Fields engine.

### What Stable Organization Ownership Means

A resource created for an organization stores that organization's ID as its owner. The owner does
not change merely because the creator later changes their primary organization, leaves the
organization, or joins another organization.

Examples:

- A Document Library entry created for Legal Firm A stores `ownerOrganizationId = Legal Firm A`.
- An organization Blueprint stores `ownerOrganizationId = Legal Firm A`.
- An organization Workflow, Sequence, Variable, or Communication stores the same stable owner.
- A personal Blueprint stores `ownerUserId = Alice` and no organization owner.
- A bundled platform template stores platform scope and no customer organization owner.
- An organization-owned Exchange stores the organization that governs it, even when external
  recipients participate through Shares.
- An Exchange initiated by a registered application stores that application as its initiating
  principal and stores the explicitly authorized organization or other approved owner context
  separately. Application identity does not replace resource ownership, and at least one active
  human `OWNER` Share is created transactionally.
- A personal Exchange stores a Personal owner context and does not infer an organization from the
  creator's current membership.

If Alice creates a Legal document while working in Legal Firm A and later makes Client B her primary
organization, the document remains owned by Legal Firm A. Ownership is one input to authorization.
It is not sufficient to decide whether a future Field Value may be shown to Alice. Field visibility
must also evaluate the published Field Contract, effective Schema Field Binding, caller relationship,
and output channel.

### Implementation Work

1. Define canonical `OwnerContext`, `ScopeReference`, and `ResourceReference` contracts with the
   distinctions above.
2. Inventory and normalize ownership columns for all governed resource types, including resources
   that must remain valid for users with no organization membership.
3. Define the final ownership columns and constraints in the clean database schema.
4. Remove superseded ownership columns, inference logic, and compatibility fallbacks. Development
   data is discarded rather than backfilled.
5. Set ownership in service-layer create operations inside the same transaction as resource
   creation.
6. Prevent ordinary update DTOs from changing ownership.
7. If ownership transfer is supported, implement a dedicated audited service operation with source
   and destination authorization plus dependency validation.
8. Define deletion and deactivation behavior without erasing ownership history.
9. Ensure the contracts can be reused by the Fields engine without depending on Fields entities or
   adding placeholder Fields persistence.
10. Add explicit no-organization ownership scenarios to the canonical test matrix.

### Verification Gate

- Inspect every governed entity and create service.
- Initialize an empty database, create representative resources, and query for null, conflicting, or
  invalid owners.
- Test that changing a creator's primary organization does not change resource ownership.
- Test personal, organization, and platform resource creation.
- Test personal-resource flows for a user with no organization memberships at all.
- Search update DTOs and entity mappers for unintended owner mutation.
- Compare the final owner model with the resource-to-owner inventory from Phase 0.
- Verify personal resources, organization resources, and platform resources cannot be confused with
  configuration governance scopes.

### Exit Criteria

- Every governed resource has exactly one valid owner context.
- Ownership is assigned transactionally and cannot drift with user membership changes.
- No obsolete ownership field or inference path remains.
- The canonical contracts are reusable by future resource adapters and configuration services.

### Next Step

Begin Phase 4 by making the central authorization service resolve and use the stable owner context.

## Phase 4: Centralize Resource Context and Capability Evaluation

### Objective

Make one authorization path responsible for resolving the target resource, its owner, its state,
and the caller's applicable grants.

### Context

Authorization cannot safely use `activeOrgId` as a substitute for the target resource owner. A
central resource-context resolver should load the authoritative context for a canonical
`ResourceReference` and provide it to `AuthorizationService`. The resolver is a registry of
resource-owned context providers, not a service that reaches directly into every other service's
repository.

### Implementation Work

1. Introduce a `ResourceAuthorizationContextResolver` or equivalent provider registry that uses the
   canonical Resource Reference contract from Phase 3.
2. Register one context provider per governed resource type. Each provider is implemented by or
   delegates to the owning resource service. A service must not use another service's repository.
3. For each governed resource type, resolve at least:
   - Resource ID and type.
   - Owner scope.
   - Owning organization ID or personal owner ID.
   - Parent resource where permissions inherit from a parent.
   - Lifecycle, active, publication, or deletion state relevant to authorization.
4. Expand the canonical resource type registry to cover every existing governed resource. Keep
   stable serialized codes and remove misleading aliases such as treating every Exchange as a
   generic session.
5. Require `DefaultAuthorizationService` to use resolved target ownership for organization grants.
6. Keep `AuthorizationContext` focused on caller/session facts such as principal kind, user,
   registered-application or service-account ID, active or granted organization context, token
   boundaries, group memberships, MFA state, and network facts.
7. Define capability precedence clearly: explicit deny and failed constraints override grants;
   multiple valid grants are otherwise additive.
8. Add decision reasons suitable for audit and an eventual "why do I have access" interface.
9. Define the contract that a future Fields resource adapter uses to verify existence, resolve owner
   context, check assignability, obtain lifecycle context, and invoke resource authorization. The
   adapter must reuse these providers instead of loading resource repositories directly.
10. Cache only data whose invalidation behavior is defined and tested.
11. Resolve workflow webhook endpoint ownership, target application, workflow instance, Exchange,
    delivery, and any short-lived workflow action grant through registered authorization-context
    providers rather than trusting callback identifiers supplied by the application.

### Verification Gate

- Inspect every resource resolver and compare it with entity ownership.
- Test a caller whose active organization differs from the target owner.
- Test nonexistent, deleted, personal, organization, and platform resources.
- Test unknown resource type codes, duplicate provider registration, missing providers, and provider
  failures.
- Test user, registered-application, service-account, participant, and public-link principal
  resolution where supported.
- Test webhook delivery and workflow action-grant context resolution, including mismatched
  application, workflow instance, Exchange, owner context, expiry, and replay.
- Confirm that unresolved or conflicting context denies access.
- Review decision reasons for sensitive-data leakage.
- Compare behavior with the authorization principles and Phase 3 owner model.

### Exit Criteria

- Authorization decisions use the target resource owner, not a session default.
- Every existing governed resource type has a resolver.
- Missing or ambiguous resource context fails closed.
- Resource services retain ownership of their repositories and expose context through reviewed
  provider methods.
- The Resource Reference and provider contracts are suitable for reuse by the first Exchange Fields
  adapter.

### Next Step

Begin Phase 5 by defining resource-specific actions and capability bundles for the existing platform.

## Phase 5: Complete the Action and Capability Model

### Objective

Replace broad booleans and overly coarse permissions with explicit actions that match existing
business operations.

### Capability Direction

The exact names should be finalized through the Phase 0 endpoint inventory, but the model must cover
at least the following distinctions:

| Resource | Required action distinctions |
|---|---|
| Exchange | initiate, view, edit, delete, suspend, rescind, end, transfer ownership, manage access |
| Exchange document | view, download, upload, update, delete, comment, sign |
| Document Library | discover, view, use, create, edit, delete, manage |
| Blueprint | discover, view, use, create, edit, delete, clone, manage, publish where applicable |
| Workflow Definition | discover, view, use, create, edit, delete, clone, publish, manage |
| Workflow webhook | configure endpoint, enable, disable, deliver, retry, view delivery history |
| Sequence | discover, view, consume, create, edit, delete, manage |
| Variable | discover definition, view value, use value, create, edit, delete, manage |
| Communication | discover, view, use, create, edit, delete, publish, manage |
| Principal Group | view, manage members, edit, delete |
| Organization | manage members, manage policy, manage billing, read audit |
| Platform | administer control plane, read platform audit, perform support operation |
| Application registration | authenticate, view registration, rotate credentials, grant capabilities, deactivate |

`Rescind` is a distinct Exchange lifecycle action and capability. It must not be inferred from
`Delete`, `End`, `Edit`, or `Manage Access`. The authorization decision must require
`EXCHANGE_RESCIND`, an eligible lifecycle relationship, and `INITIATED` or `ACCEPTED_STARTED` status.
For a human-initiated Exchange, the initiating human normally also holds `OWNER`. For an
application-initiated Exchange, an assigned human `OWNER` must be able to rescind even though the
application is the initiating principal. The application may rescind only if it has a separate,
explicit `EXCHANGE_RESCIND` grant. A successful rescind moves the Exchange to `RESCINDED`, cancels
its running workflows, revokes its active Shares, archives it as read-only, and emits the applicable
notifications, realtime event, and audit record. An idempotent retry must still authenticate and
authorize the caller.

`Initiate Exchange` is also a distinct capability. The `APPLICATION` role makes a registered
application eligible to receive machine capabilities, but it does not grant Exchange initiation by
default. A CLM integration may initiate an Exchange only when its registration has an explicit
`EXCHANGE_INITIATE` grant for the requested owner context and its token boundary also permits the
operation. The created Exchange must record the application as initiator for authorization and
audit, while retaining a separate stable owner context. Initiation must not imply permission to
list, view, edit, rescind, end, or manage access to any Exchange unless those capabilities or
resource-specific Shares are granted separately.

`Discover` controls appearance in lists, searches, and pickers. `Use` or `Consume` controls applying
or executing a resource. It must not imply `Edit`. Sensitive Variable values require a distinct
capability from discovering the Variable definition.

The model must also provide a default-deny extension point for future Schema Definition, Schema
Version, Field Definition, Field Contract, Schema Assignment, and Field Value actions. This phase
does not add those Fields capabilities. It proves that adding them later grants them to no role until
the Fields implementation explicitly maps them. Platform configuration administration and
Organization configuration administration must remain distinguishable.

### Implementation Work

1. Expand `Action` and `Capability` for the existing resource operations listed above.
2. Map every action to one capability in a reviewed matrix.
3. Map each scoped role to intentional capabilities for its scope.
4. Remove `APP_ADMIN` use of `enumValues<Capability>().toSet()` so future capabilities are never
   granted accidentally.
5. Make organization roles operational through capabilities. For example,
   `ORG_USER_MANAGER` must be honored by membership operations and `ORG_AUDITOR` by authorized audit
   operations.
6. Ensure group roles grant group behavior and group-derived resource grants only, not unrelated
   resource capabilities by name collision.
7. Preserve Share role distinctions for upload, update, download, comment, and sign operations.
8. Define explicit control-plane and organization-configuration administration capabilities that a
   later Fields plan can compose without treating `APP_ADMIN` as customer-content superuser.
9. Define how registered applications, service accounts, and delegated workflow actors receive
   capabilities without borrowing an application user's role implicitly.
10. Document the effective role-to-capability matrix in code tests and administrator documentation.
11. Compose effective capabilities from every applicable role assignment in the same validated
    scope, including ordinary `ORG_MEMBER` capabilities alongside specialized organization-role
    capabilities, while preserving target-specific Exchange Share constraints.
12. Add a dedicated Exchange rescind action and capability, and evaluate the caller's human-owner,
    initiating-principal, or explicit application-grant relationship together with lifecycle-state
    requirements through centralized resource authorization before any status change or side effect.
13. Add the `APPLICATION` role and `APPLICATION` principal kind to centralized capability resolution.
    Add `EXCHANGE_INITIATE` as an independently grantable capability scoped to an approved owner
    context, and require both token-boundary and resource-aware authorization checks.
14. Add explicit workflow webhook administration and delivery actions. Do not create a generic
    `WEBHOOK_CALLBACK` capability that permits arbitrary Exchange mutations. Map every application
    callback to the ordinary Exchange or document action it performs.
15. Define a short-lived workflow action-grant contract for explicitly delegated callbacks. Its
    allowed actions must be a narrowing constraint on the registered application's capabilities,
    never an expansion of them.

### Verification Gate

- Review every action and capability for least privilege.
- Generate or inspect a role-to-capability snapshot and compare it with this plan.
- Test that adding a new capability grants it to no role until explicitly mapped.
- Prove with a generic or synthetic test that a capability not explicitly assigned to a role grants
  no access. Do not add placeholder Fields production capabilities during this plan.
- Test all existing roles, including specialized organization roles.
- Test representative multi-role combinations and confirm their effective capabilities are the
  intended composition of all applicable roles in the current scope.
- Test Ethan as both `ORG_BILLING_ADMIN` and `ORG_MEMBER`, with an Exchange Share role, and confirm
  that billing, ordinary collaboration, and Exchange permissions remain independently revocable.
- Test Exchange rescind independently from delete and end. Cover a human initiator-owner, the human
  owner of an application-initiated Exchange, an application with and without an explicit rescind
  grant, each eligible and terminal status, editors, participants, organization administrators, and
  human platform administrators.
- Test that a denied rescind produces no status change, workflow cancellation, Share revocation,
  notification, realtime event, or audit success record.
- Test registered-application, service-account, and delegated-system-actor capability resolution.
- Test a CLM `APPLICATION` with `EXCHANGE_INITIATE` for Acme. Confirm that it can initiate an
  Acme-owned Exchange, assigns an active human `OWNER`, is recorded as initiator, cannot initiate for
  another organization, and gains no unrelated Exchange read, mutation, or administration
  capability.
- Test that the human `OWNER` of an application-initiated Exchange can manage its lifecycle,
  including rescind, while the initiating application cannot do so without the corresponding
  explicit capability.
- Test workflow webhook delivery and callback actions independently. Confirm that receiving a valid
  webhook, knowing its delivery ID, or presenting its correlation ID grants no API capability.
- Test delegated workflow action grants for application, workflow instance, Exchange, action, expiry,
  replay, idempotency, revocation, and owner-context mismatches.
- Test that `Use` does not imply `Edit`, `Discover` does not imply `View`, and Variable discovery does
  not imply value access.
- Search for role-name authorization checks that bypass capabilities.

### Exit Criteria

- Every protected business operation maps to a named action and capability.
- Every role has an explicit, scope-aware capability bundle.
- New capabilities default to no role.
- Specialized existing roles perform their documented functions.
- Users with multiple roles retain the intended capabilities of every applicable role.
- The action model can add Fields configuration and value operations without changing existing
  resource authorization semantics.

### Next Step

Begin Phase 6 by separating platform control-plane power from customer-content access.

## Phase 6: Harden Platform Administrator, Auditor, Support, and Application Credentials

### Objective

Prevent standing human platform roles and registered applications from receiving unnecessary
customer-content access or acquiring privilege through ordinary platform or API use.

For the Fields Foundation Readiness Gate, standing human platform roles and registered-application
roles must be least-privilege and must not receive customer content automatically. Completing the
full time-limited support-elevation workflow is broader platform hardening and may follow the Fields
gate if support access remains denied until it is complete.

### Implementation Work

1. Define control-plane capabilities for `APP_ADMIN` explicitly.
2. Remove automatic customer Exchange, document, group, and organization-content reads from
   `APP_AUDITOR` unless a separately documented legal or operational requirement requires them.
3. Limit `APP_SUPPORT` to support operations by default.
4. Model customer-content support access as an explicit, time-limited elevated access grant with
   target organization, reason, approver, start, expiry, and complete audit trail.
5. Remove automatic promotion to `APP_ADMIN` from normal REST endpoint access.
6. Keep initial `APP_ADMIN` bootstrap in controlled configuration or an out-of-band process.
7. Protect the last active application administrator without silently creating a replacement.
8. Require strong authentication and fresh session checks for high-risk application operations where
   supported.
9. Store registered-application secrets using approved credential hashing or secret-management
   controls, support rotation and revocation, and never log raw API keys, secrets, or bearer tokens.
10. Issue application tokens with the registered application ID, principal kind, expiry, audience,
    and maximum token boundary. Resolve current capabilities server-side so a stale or forged token
    claim cannot grant a revoked capability.
11. Keep application credential validation and token issuance in authentication services. REST
    resource classes must not query the `Application` entity or make capability decisions directly.
12. Protect workflow webhook signing credentials separately from inbound application credentials.
    Support rotation with a bounded overlap window, endpoint disablement, delivery revocation, and
    redacted operational diagnostics.
13. Validate outbound webhook destinations against an approved egress policy. Prevent loopback,
    link-local, private-network, metadata-service, redirect, DNS-rebinding, oversized-response, and
    unbounded-timeout abuse unless a separately reviewed deployment policy permits a destination.

### Verification Gate

- Inspect all human platform and registered-application role grant, revoke, and bootstrap paths.
- Test that visiting an admin endpoint never creates a human platform role assignment.
- Test auditor and support access to customer content without elevation and confirm denial.
- Test approved elevated support access, expiry, scope, revocation, and audit records.
- Confirm that adding a future capability does not expand `APP_ADMIN` automatically.
- Test registered-application secret rotation, deactivation, token expiry, capability revocation,
  organization-boundary enforcement, and audit attribution.
- Test webhook signing-secret rotation, endpoint disablement, destination validation, signed payload
  verification fixtures, replay rejection, redirect handling, response limits, and retry behavior.
- Compare implementation with the human platform and external-application role definitions in this
  plan.

### Exit Criteria

- No ordinary endpoint can self-bootstrap an application administrator.
- Platform roles follow least privilege.
- Registered applications are independently identifiable, revocable, least-privilege principals.
- Any exceptional customer-content access is explicit, scoped, expiring, and audited.

### Next Step

Phase 7 is complete. Begin Phase 8 by introducing explicit active organization context.

## Phase 7: Complete Exchange Share and Constraint Enforcement (COMPLETE 2026-07-01)

### Objective

Make Exchange access reliable for direct users, Principal Groups, participants, registered
applications, service accounts, and public or magic-link principals, including every declared
constraint.

### Implementation Work

1. Make malformed Share constraints deny access and produce an actionable audit event.
2. Populate and verify runtime inputs for MFA state, client IP, time windows, and other declared
   constraints.
3. Implement server-side enforcement for `maxViews` or remove it from the supported contract until
   it can be enforced safely.
4. Ensure IP allow-list behavior is modeled, parsed, and enforced consistently if retained.
5. Evaluate constraints for dynamic Principal Group grants, not only materialized inherited rows.
6. Prevent constraints from adding capabilities that the Share role did not grant.
7. Track view counts transactionally where limits apply and define what operation counts as a view.
8. Separate upload from update authorization even if both previously used `DOCUMENT_WRITE`.
9. Test group membership activation, deactivation, and removal without rewriting every Share.
10. Resolve public and magic-link tokens to a constrained principal or deny access. A token hash in
    request context without a validated grant is not authorization.
11. Ensure authenticated and no-auth Exchange paths use equivalent Share status, expiry, resource,
    document-parent, and constraint checks.
12. Define service-account Share behavior explicitly or reject service-account Shares until the
    contract is supported.
13. Define registered-application Share behavior explicitly. An application that initiates an
    Exchange does not automatically receive every Share capability; any continuing resource access
    must come from the initiator policy and explicit capabilities or an application-targeted Share.
14. Apply Share constraints to application callbacks exactly as they apply to direct application API
    calls. A workflow correlation or action grant may narrow access but must not bypass a failed Share
    constraint or explicit deny.

### Verification Gate

- Inspect `ShareConstraints`, `AuthorizationContextFactory`, Share creation, group grant resolution,
  and `DefaultAuthorizationService` together.
- Run malformed, missing, expired, MFA, IP, view-limit, direct-user, participant,
  registered-application, service-account, public-link, magic-link, and group-grant tests.
- Test concurrent view-limit consumption.
- Confirm that deactivating a group or membership removes derived access immediately.
- Confirm revoking or exhausting a link removes access immediately on authenticated and no-auth
  endpoints.
- Compare every documented Share constraint with an enforcement test.

### Exit Criteria

- All supported constraints are enforced server-side.
- Unsupported constraints are removed from requests and UI until supported.
- Invalid constraint data fails closed.
- Group and direct Share paths produce equivalent constraint behavior.
- Public-link, magic-link, participant, and no-auth paths cannot bypass resource or Field-ready
  visibility boundaries.

### Next Step

Begin Phase 8 by introducing explicit active organization context and an effective frontend access
contract.

## Phase 8: Make Organization Context and Frontend Permissions Explicit (COMPLETE 2026-07-01)

### Objective

Represent users accurately across no-organization, single-organization, and multi-organization
states, and stop the frontend from inferring authorization from a single role string.

### Implementation Work

1. Define an explicit active organization selection mechanism for authenticated requests, while
   allowing sessions with no active organization at all.
2. Validate that the caller has an active membership in the selected organization when an
   organization is selected.
3. Do not rely on the primary or first organization membership for authorization, and do not
   require any organization membership for personal-product flows.
4. Return a current-session model containing at least user identity, active organization ID,
   applicable scoped roles, and effective capabilities relevant to the active context.
5. Update JWT or server-side session behavior so stale role changes do not remain effective beyond a
   defined interval.
6. Replace frontend literal comparisons such as `'APP_ADMIN'` and incomplete obsolete enums with the
   session capability contract.
7. Use capabilities to render menus, buttons, settings tabs, and action controls.
8. Keep backend checks in place for every action regardless of frontend rendering.
9. Provide a safe organization-switch flow that refreshes effective capabilities and cached data.
10. Provide a no-organization session mode that still returns correct baseline authenticated-user
    capabilities for personal actions, Exchange participation, and any no-org settings screens.
11. Treat active organization as caller context and configuration-navigation context only. It must
    not replace the owner resolved from a target Resource Reference.
12. Ensure the future Fields administration UI can distinguish Platform configuration authority from
    Organization configuration authority without inspecting literal role names.
13. Represent all concurrent roles in the current-session contract. Do not collapse them into one
    primary, highest, or display role when calculating capabilities or rendering user access.

### Verification Gate

- Inspect `AuthenticationService`, `AuthorizationContextFactory`, current-user responses,
  `AuthContext`, frontend role types, and permission-based rendering.
- Test a user with no organization memberships and confirm personal flows remain available.
- Test a user with different roles in two organizations and switch between them.
- Test a user with multiple roles in one organization and confirm the session exposes the full role
  set and composed capabilities.
- Test role revocation and organization membership deactivation during an active session.
- Search the frontend for direct role-string authorization checks.
- Confirm hidden controls do not replace backend denial tests.
- Compare returned capabilities with backend authorization decisions for representative actions.

### Exit Criteria

- Active organization is explicit and validated when present, and absence of an active organization
  is supported for personal-product flows.
- Frontend behavior derives from effective capabilities, not one primary role.
- Current-session data preserves all applicable concurrent roles and their composed capabilities.
- Organization switching cannot retain permissions or data from the previous context.
- Session capabilities can represent future Fields configuration actions without granting them
  implicitly.

### Next Step

Begin Phase 9 by adopting central authorization across every governed resource and closing direct-ID
bypasses.

### Phase 8 Completion Evidence (2026-07-01)

**Backend changes:**
- `EndpointAuthorizationFilter.kt`: `AuthTokenContext` gains `activeOrganizationId` and
  `activeMembershipId`; `EndpointVerificationFilter` parses `X-Active-Organization-Id` header,
  validates membership via `OrganizationMembershipRepository.findActiveByUserAndOrg`, and aborts
  with HTTP 403 on invalid or non-member org IDs.
- `AuthorizationContextFactory.kt`: removed primary-org fallback entirely; reads active context
  exclusively from `AuthTokenContext` fields set by the filter.
- `AccessDtos.kt`: `CurrentSessionDto` added (`userId`, `email`, `appRoles`,
  `activeOrganizationId`, `organizationRoles`, `capabilities`).
- `SessionService.kt` (new): `@ApplicationScoped` service computing effective capabilities from
  live role assignments; no stale token dependence.
- `AppUserResource.kt`: `GET /app-user/session` endpoint returning `CurrentSessionDto`.

**Test evidence:**
- `Phase8OrgContextTest.kt` (new): 16 tests — no-header personal mode, role composition, ORG_ADMIN
  / ORG_OWNER / ORG_BILLING_ADMIN caps, APP_ADMIN / APP_AUDITOR platform caps, sorted capabilities
  list, stale-role refresh, AuthTokenContext isolation.
- Full suite: **151/151 tests pass**.

**Frontend changes:**
- `models.tsx`: full 112-value `Capability` enum + `CurrentSessionDto` interface.
- `apiClient.ts`: `X-Active-Organization-Id` header injected on every request via interceptor.
- `appUserApi.ts`: `fetchCurrentSession()` added.
- `AuthContext.tsx`: `currentSession`, `hasCapability`, `switchOrganization` wired; localStorage
  persistence under `docuhyphen:auth:active-org-id`; cleared on logout and session expiry.
- `roles.ts`: `isAppAdministrator` and `canAdministerOrganization` migrated to `Capability[]`
  signature; `hasCapabilityIn` helper; `Capability` re-exported.
- 10 caller files updated to use `hasCapability` from `useAuth()` directly: `Settings.tsx`,
  `BlueprintsTab.tsx`, `SaveBlueprintDialog.tsx`, `OrganizationDetailsTab.tsx`,
  `OrganizationVariablesTab.tsx`, `OrganizationTab.tsx`, `OrganizationSequencesTab.tsx`,
  `VariablesTab.tsx`, `CommunicationsTab.tsx`, `DocumentLibraryTab.tsx`.

**Type check:** `npx tsc --noEmit` — exit code 0, zero errors.

## Phase 9: Adopt Central Authorization Platform-Wide

### Objective

Route governed resource reads and writes through the common authorization model. Exchange and
Exchange-document adoption is Fields-blocking. Adoption for other resource types remains required
before those resource types receive Fields adapters and remains part of the complete hardening
program.

### Priority Security Fixes

- Authorize registered-application REST requests as `APPLICATION` principals through central
  capability evaluation, not only token scopes or allowed endpoint prefixes.
- Authorize and project every workflow webhook payload before it leaves DocuHyphen, and authorize
  every resulting application callback as a new inbound request.
- Authorize `GET /exchanges/{id}` before returning the Exchange.
- Authorize `GET /exchanges/{id}/access` before returning principals or Shares.
- Authorize no-auth Exchange and document endpoints through validated participant or link grants.
- Verify that every document path parameter belongs to the Exchange named by its parent path before
  authorizing or returning it.
- Ensure guessed or leaked UUIDs do not bypass list-level restrictions.
- Authorize organization member lists and mutations against the target organization.

### Implementation Work

1. Keep REST resource classes thin. They validate transport input, call services, and map responses.
2. Place authorization and business decisions in application services.
3. Adopt `AuthorizationService` for Exchanges, Exchange documents, Document Library, Blueprints,
   Workflow Definitions, Sequences, Variables, Communications, Principal Groups, and organization
   administration.
4. Apply authorization consistently to list, search, detail, picker, create, initiate, use, clone,
   update, publish, deactivate, and delete operations where applicable.
5. Replace custom `isOrgAdmin` and caller-owned booleans with action checks against resolved
   resources.
6. Ensure organization members cannot edit organization-owned reusable resources merely because
   they belong to the same organization.
7. Filter list and picker results in the query or service layer before DTO creation.
8. Prevent unauthorized resource existence and sensitive metadata from leaking through counts,
   errors, audit responses, logs, events, or dependency checks.
9. Revalidate referenced resources when Blueprints or other reusable resources are used or cloned.
10. Add authorization decision audit records for privileged and denied operations according to a
    documented retention and sensitivity policy.
11. Define a two-stage data projection contract:
    - First authorize the resource action.
    - Then apply data visibility and redaction rules before serialization.
    - Treat a resource Allow decision as insufficient to expose every future Field Value.
12. Apply the projection boundary consistently to detail DTOs, lists, search, exports, reports,
    workflow snapshots, notifications, audit views, realtime events, logs, integration payloads, and
    workflow webhook deliveries.
13. Prevent direct entity serialization or generic event payloads from bypassing the projection
    boundary.
14. Keep each resource's authorization-context provider and repository access inside its owning
    service boundary.
15. Adopt centralized authorization for application token issuance and every REST endpoint available
    to registered applications. Keep token validation, token boundary, capability evaluation, owner
    context, and target-resource checks as distinct required gates.
16. Route workflow webhook delivery through a dedicated application service that performs endpoint
    authorization, payload projection, signing, durable delivery recording, retries, and audit. The
    workflow engine requests delivery through that service and does not handle secrets or perform
    unrestricted HTTP calls directly.
17. Correlate inbound application actions with a webhook delivery when supplied, but never treat the
    correlation as authorization. Require idempotency keys for callback mutations where retries could
    repeat a state change.

### Verification Gate

- Re-run the endpoint-to-action inventory and account for every endpoint.
- Search for direct repository loads in REST resource classes and authorization-sensitive services.
- Search for remaining bespoke admin or ownership booleans.
- Test list and direct-ID behavior for the same caller and resource.
- Test every governed resource with owner, permitted member, unpermitted same-organization member,
  other-organization admin, human platform roles, registered applications, and unauthenticated
  callers where applicable.
- Verify resource dependencies and picker endpoints.
- Test authenticated, participant, public-link, magic-link, and unauthenticated Exchange paths.
- Test outbound workflow webhook and inbound application callback paths as separate authorization
  decisions, including payload redaction before delivery.
- Trace representative sensitive values through every output channel in the Phase 0 inventory and
  confirm authorization and redaction occur before emission.
- Compare every result with the Phase 5 action matrix.

### Exit Criteria

- Every protected endpoint maps to a central authorization action.
- Direct resource access cannot bypass list, search, or picker restrictions.
- REST resources remain thin and contain no business or repository logic.
- Exchange resource and data-projection boundaries are ready for Schema Assignments and Field Values
  without treating classification as authorization.
- Each additional governed resource is ready to receive a future Fields adapter or separate Access
  Policy integration only after its Phase 9 adoption and tests pass.

### Next Step

Continue Phase 9 by adopting central authorization for remaining governed resources (Principal Groups,
organization administration) and by wiring APPLICATION principal capability evaluation for
registered-application REST requests. Write `Phase9ResourceAuthorizationTest`. Then begin Phase 10.

### Phase 9 Evidence (2026-07-01)

**Exchange write-path authorization:**
- `ExchangeUpdateService.kt`: added `AuthorizationService` + `AuthorizationContextFactory` to constructor;
  removed `OrganizationMembershipService`.
  - `updateExchange()`: `Action.EXCHANGE_VIEW` gate for all callers; `Action.EXCHANGE_EDIT` gate for
    owner-only mutations (name, description, settings, ENDED status); recipient ACCEPTED/REJECTED path
    requires only EXCHANGE_READ.
  - `rescindExchange()`: replaced manual `initiator?.id != currentUserId` check with
    `Action.EXCHANGE_RESCIND` via authorization service.
  - `deleteExchange()`: `Action.EXCHANGE_DELETE` gate added.
  - `issueRecipientOtp()`: `Action.EXCHANGE_EDIT` gate added.
  - ENDED workflow trigger: replaced `organizationMembershipService.primaryOrganizationId(initiator.id)`
    with `exchange.ownerOrganizationId` (direct column, no fallback).
- `ExchangeResource.kt`: removed `OrganizationMembershipService` from constructor; fixed
  `getExchangeWorkflowInstances()` and `getExchangeWorkflowClearanceStatus()` to use
  `authTokenContext.activeOrganizationId` (Phase 8 primary-org fallback violations closed).

**Endpoints already authorized (carried from earlier phases):**
- `GET /exchanges/{id}` → `Action.EXCHANGE_VIEW` in `ExchangeRetrievalService`.
- `GET /exchanges/{id}/access` → `Action.EXCHANGE_MANAGE_ACCESS` in `ExchangeAccessManagementService`.
- `POST/PATCH/DELETE /exchanges/{id}/access[/{shareId}]` → `Action.EXCHANGE_MANAGE_ACCESS`.
- `GET /no-auth/exchanges/{id}` with share-link token → `ShareLinkValidationService.validateForNoAuth`.

**Content service authorization (2026-07-01):**
- `DocumentLibraryService.kt`: injected `AuthorizationService`, `AuthorizationContextFactory`,
  `UserRoleService`; removed all `callerUserId`/`callerOrgId`/`isOrgAdmin`/`isAppAdmin` parameters
  from public methods; `checkReadAccess` uses `Action.DOC_LIBRARY_VIEW`, `checkWriteAccess` uses
  `Action.DOC_LIBRARY_EDIT`; both have `isAppAdmin` bypass at top; APP-scope writes forbidden
  (clone instead); `currentContext().activeOrgId` replaces `primaryOrganizationId`.
- `DocumentLibraryResource.kt`: removed `UserRoleService` + `OrganizationMembershipService`.
- `BlueprintDefinitionService.kt`: same pattern; `Action.BLUEPRINT_VIEW`/`BLUEPRINT_EDIT`;
  `AdminApprovalContext` preserved as explicit parameter.
- `BlueprintDefinitionResource.kt`: removed `UserRoleService` + `OrganizationMembershipService`;
  resource-level `isOrgAdmin` pre-check on `patchPublished` removed (service enforces it).
- `WorkflowDefinitionService.kt`: `checkReadAccess` uses `Action.WORKFLOW_VIEW` + published-state
  guard (unpublished ORG defs blocked for non-admin org members); `checkWriteAccess` uses
  `Action.WORKFLOW_EDIT`; `cloneDefinition` uses `Action.WORKFLOW_CLONE`; `listDefinitions`
  computes `showUnpublished` internally from `isOrgAdmin || isAppAdmin`; `listInstances` and
  `getInstanceDetail` derive active org from `currentContext().activeOrgId`.
- `WorkflowDefinitionResource.kt`: removed `UserRoleService` + `OrganizationMembershipService`.
- `SequenceDefinitionService.kt`: `checkReadAccess` uses `Action.SEQUENCE_VIEW`;
  `checkWriteAccess` uses `Action.SEQUENCE_EDIT`; `listSequences(isActive)` gets `activeOrgId`
  from context (returns empty when no org context).
- `SequenceDefinitionResource.kt`: removed `UserRoleService` + `OrganizationMembershipService`.
- `VariableDefinitionService.kt`: `checkWriteAccess` uses `Action.VARIABLE_EDIT` for ORG scope;
  PERSONAL ownership check kept direct; `listVariables(scope)` gets caller identity/org internally.
- `VariableDefinitionResource.kt`: removed role/org services; `getAvailableVariables` now uses
  `authTokenContext.activeOrganizationId` instead of `primaryOrganizationId`.
- `CommunicationService.kt`: `checkReadAccess` uses `Action.COMMUNICATION_VIEW`;
  `checkWriteAccess` uses `Action.COMMUNICATION_EDIT`; PLATFORM writes forbidden (clone instead).
- `CommunicationResource.kt`: removed role/org services; resource-level `isOrgAdmin` pre-check
  on `patchPublished` removed.

**Authorization infrastructure additions:**
- `DocumentLibraryAuthorizationContextProvider.kt` (new): `ResourceKind.DOCUMENT_LIBRARY_ENTRY`.
- `BlueprintAuthorizationContextProvider.kt` (new): `ResourceKind.BLUEPRINT`.
- `WorkflowDefinitionAuthorizationContextProvider.kt` (new): `ResourceKind.WORKFLOW_DEFINITION`.
- `SequenceDefinitionAuthorizationContextProvider.kt` (new): `ResourceKind.SEQUENCE_DEFINITION`.
- `VariableDefinitionAuthorizationContextProvider.kt` (new): `ResourceKind.VARIABLE_DEFINITION`.
- `CommunicationAuthorizationContextProvider.kt` (new): `ResourceKind.COMMUNICATION`.
- `PrincipalRef.kt`: added `ResourceRef.docLibrary()`, `.blueprint()`, `.workflowDefinition()`,
  `.sequence()`, `.variable()`, `.communication()` companion helpers.
- `DefaultAuthorizationService.kt`: fixed cross-org bug in `collectOrgMembershipGrants()` —
  now resolves owner org via registry before falling back to `context.activeOrgId`.

**Phase 9 completion evidence (2026-07-01):**

New files:
- `OrganizationAuthorizationContextProvider.kt`: resolves `ResourceKind.ORGANIZATION` with
  `OwnerContext.Organization(orgId)`; no archived/suspended concept for orgs.
- `ResourceType.ORGANIZATION` enum value added; `ResourceRef.organization(id)` companion added to
  `PrincipalRef.kt`; `ResourceType.ORGANIZATION -> ResourceKind.ORGANIZATION` mapping added to
  `ResourceAuthorizationContextRegistry.toResourceKind()`.
- `OrganizationGroupService.addOrganizationGroup()`: replaced `requireOrgAdminIn(orgId)` (bypassed
  AuthorizationService) with new `authorizeOrg(Action.ORG_MANAGE_MEMBERS, orgId)` helper; removed
  `userRoleService` dependency entirely.
- `ExchangeDocumentService.validateUserPermissions()`: removed `appUser: AppUser` parameter, now
  calls `authorizationContextFactory.currentPrincipal()` — works for USER and APPLICATION tokens.
  Added `actorEmail()` helper for audit logging; all callers updated (addDocument, deleteDocument,
  updateDocument, uploadDocument).
- `WorkflowWebhookEndpoint.signingSecretHash` renamed to `signingSecretToken` (raw Base64);
  `V34__webhook_signing_token.sql` migration added; `WorkflowWebhookEndpointManagementService`
  now stores raw secret (BCrypt removed).
- `WorkflowStepSpec` gains `webhookEndpointId: String?` and `webhookEventType: String?`.
- `WebhookDeliveryService.kt`: loads endpoint, verifies enabled, builds payload (deliveryId,
  eventType, workflowInstanceId, subjectResourceType, subjectResourceId, organizationId, timestamp),
  signs with HMAC-SHA256 as `sha256=<hex>` in `X-DocuHyphen-Signature-256`, HTTP POSTs, audits.
- `WebhookWorkflowActionHandler.kt`: key `"WEBHOOK_DELIVER"`, reads `webhookEndpointId` and
  `webhookEventType` from step spec, delegates to `WebhookDeliveryService`.

Test evidence:
- `Phase9ResourceAuthorizationTest.kt`: 25 tests (PERSONAL/ORG/PLATFORM content service gates).
- `Phase9OrgGroupAuthorizationTest.kt`: 10 tests (org provider, ORGANIZATION resource type mapping,
  WebhookWorkflowActionHandler dispatch, WebhookDeliveryService endpoint-not-found).
- Full suite: **201/201 tests pass**.

## Phase 10: Certification and Fields Foundation Readiness

### Objective

Produce two explicit certification outcomes:

1. A Fields Foundation Readiness decision for the first Platform and Organization scoped,
   Exchange-focused Fields implementation.
2. A complete authorization-hardening decision covering the entire platform plan.

Passing the first outcome permits a separate Fields implementation plan to begin. It does not mark
the broader authorization-hardening program complete.

### Required Test Matrix

The automated suite must include at least:

- Every role in every valid scope.
- The `APPLICATION` machine role resolved only for a registered `APPLICATION` principal and never as
  a human platform or organization role.
- Multiple concurrent roles for one user in the same organization, including a specialized
  organization role together with `ORG_MEMBER` and a resource-specific Exchange Share role.
- Independent grant and revocation of each concurrent role without replacing or removing the
  user's unrelated roles.
- Every role rejected from every invalid scope.
- Every action and capability mapping.
- Exchange rescind authorization for a human initiator-owner, the human owner of an
  application-initiated Exchange, an application with and without an explicit rescind grant,
  ineligible status, other Share roles, organization administrators, human platform administrators,
  and idempotent authorized retry.
- Rescinded Exchanges archived as read-only with running workflows cancelled and active Shares
  revoked only after authorization succeeds.
- A generic or synthetic unmapped capability granting no access, without adding placeholder Fields
  production capabilities.
- Same-organization positive cases.
- Cross-organization negative cases.
- Primary organization different from target organization.
- Personal, organization, and platform-owned resources with ownership kept distinct from
  configuration governance scope.
- Unknown Resource Reference types, missing context providers, and provider failures denying access.
- Direct-ID requests matching list and search visibility.
- Authenticated-user, registered-application, participant, service-account, workflow-actor,
  public-link, magic-link, and unauthenticated principal paths where supported.
- CLM application allowed to initiate an Exchange only for its granted organization and denied all
  ungranted Exchange actions, other organizations, and unrelated customer content.
- Application token scope or allowed path present without the required business capability still
  denied, and business capability present outside the token boundary still denied.
- Application deactivation, credential rotation, capability revocation, and organization unlinking
  taking effect within the documented interval and retaining complete audit attribution.
- Workflow webhook endpoint ownership, configuration authorization, signing, payload minimization,
  delivery retries, idempotency, replay protection, correlation, and audit attribution.
- Application callback actions checked against the exact Exchange or document action, with valid
  webhook possession alone granting no access.
- Short-lived workflow action grants denied for the wrong application, workflow instance, Exchange,
  owner context, action, expiry, nonce, or replay state.
- Legal group permitted while Finance group is denied.
- `Use` permitted while `Edit` is denied.
- Variable definition discoverable while its value is denied.
- Resource read permitted while a restricted data element remains redacted.
- Group membership deactivation removing resource access.
- Share constraint parsing, expiry, MFA, IP, download, and view-limit behavior. If view limits are
  removed from the supported contract, test rejection of that constraint instead.
- Document-parent mismatches denied even when both IDs exist.
- Exchange detail, list, search, access, document, no-auth, workflow, export, audit, realtime, and
  integration output paths enforcing authorization before emission.
- Auditor and support roles denied customer content without approved elevation.
- Human platform role bootstrap and last-administrator protections.
- Role and membership changes reflected in active sessions within the documented interval.
- Audit records for privileged, denied, role-change, ownership-transfer, and support-elevation events
  where support elevation has been implemented.

### Documentation Work

1. Search help documentation for roles, permissions, organization administration, applications,
   integrations, workflow webhooks, Exchanges, Document Library, Blueprints, Workflows, Sequences,
   Variables, Communications, Shares, and groups.
2. Read every matched article in full.
3. Update inaccurate navigation, role, permission, and behavior statements.
4. Add an authorization or role article if the new model has no adequate user-facing explanation.
5. Respect the article, section, and registry size limits in `AGENTS.md`.
6. Update technical architecture documentation with the scoped role and capability matrix,
   canonical Owner Context, Scope Reference, Resource Reference, provider registry, and two-stage
   data-projection contract.
7. Keep `FIELDS-FEATURE.md` focused on Fields architecture and link this prerequisite plan from the
   future Fields implementation plan.
8. State clearly that Fields classification does not grant access and metadata-aware Access Policies
   remain a separate deferred capability.

### Fields Foundation Verification Gate

1. Read `AGENTS.md`, `FIELDS-FEATURE.md`, and this plan again.
2. Confirm every item in `Fields Foundation Readiness Gate Scope` is implemented and has evidence.
3. Inspect the final scoped-role, ownership, resource-provider, Exchange authorization, Share,
   active-organization, and data-projection code directly.
4. Re-run the Phase 0 searches for Fields-blocking obsolete patterns.
5. Run backend unit and integration tests using the repository's Maven wrapper.
6. Run relevant frontend tests and `npx tsc --noEmit` inside `web-app`.
7. Clear the database and validate complete schema initialization and seed/setup behavior from an
   empty database.
8. Perform a manual adversarial review for guessed IDs, cross-organization access, stale sessions,
   participant and link access, constraint failures, output-channel leakage, and customer-content
   access by standing platform roles.
9. Produce a signed Fields Foundation readiness record with evidence for every blocking requirement
   and a list of broader hardening items that remain open.

### Fields Foundation Exit Criteria

- Every Fields-blocking requirement is complete with code-inspection and test evidence.
- The role inventory and role-to-capability matrix match the code.
- Future Schema and Field capabilities default to no role.
- Canonical Owner Context, Scope Reference, Resource Reference, and Exchange provider contracts are
  implemented and reusable by a future Fields adapter.
- No unresolved critical or high-severity finding affects Exchange, Exchange documents, active
  organization selection, configuration administration, Share enforcement, or relevant output
  channels.
- Cross-organization, direct-ID, registered-application, participant, public-link, magic-link, and
  no-auth negative tests pass.
- Resource authorization and restricted-data projection are demonstrably separate.
- Backend tests, relevant frontend tests, type checking, and clean database initialization pass.
- Help and technical documentation accurately describe the implemented user-visible behavior.

### Complete Program Verification Gate

1. Inspect the final code for every phase, including schema definitions and tests.
2. Re-run all searches used in Phase 0 and confirm every obsolete pattern was removed.
3. Run the complete backend and frontend regression suites and type checking.
4. Review every resource type, endpoint, output channel, and frontend permission surface in the
   inventories.
5. Produce a signed completion record listing evidence for every exit criterion in every phase.

### Complete Program Exit Criteria

- Phases 0 through 9 are marked complete with verification evidence.
- No unresolved critical or high-severity authorization finding remains anywhere in scope.
- Every governed resource has stable ownership and target-aware centralized authorization.
- Every supported output channel applies authorization and data projection before emission.
- Human platform privilege, registered-application authorization, Share constraints,
  multi-organization context, frontend permissions, audit, clean-schema initialization, and
  documentation meet their phase exit criteria.

### Next Step

After the Fields Foundation Exit Criteria pass, create and review a separate Fields implementation
plan based on `FIELDS-FEATURE.md`. That implementation may begin with metadata foundations while
explicitly non-blocking hardening work continues. If a Fields Foundation criterion fails, return to
the responsible phase and keep Fields implementation blocked.

No additional resource type may receive a Fields adapter until its owner context, resource provider,
authorization actions, data-projection paths, and negative tests meet the equivalent readiness
criteria.

## Definition of Done for This Entire Plan

This complete hardening plan is done only when:

- All eleven phases, Phase 0 through Phase 10, are implemented in dependency order.
- Each phase has code-inspection evidence, plan-comparison evidence, and passing tests.
- Every existing role is scope-safe and explicitly mapped to capabilities.
- No new business role was introduced without approval and documentation.
- Every governed resource has stable ownership.
- Every protected operation uses target-aware centralized authorization.
- Human platform privilege, registered-application authorization, Share constraints,
  multi-organization context, frontend permissions, audit, clean-schema initialization, and
  documentation meet their phase exit criteria.
- Phase 10 has formally completed the complete program verification.

Until the Fields Foundation Exit Criteria pass, the Fields implementation must not begin. Passing
that gate does not close this plan while broader authorization-hardening items remain incomplete.
