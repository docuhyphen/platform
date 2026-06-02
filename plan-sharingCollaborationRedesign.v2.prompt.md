# Sharing & Collaboration Redesign — v2 (decisions locked)

**Supersedes:** `plan-sharingCollaborationRedesign.v1.prompt.md`
**Locked answers from §9 of v1:**
- **Q1 — Multi-org membership: YES.** One `AppUser` can hold N active `OrganizationMembership` rows. `app_user.organization_id` and `app_user.role` columns will be retired in V9 after services migrate.
- **Q2 — External participants: PERSISTENT.** New `external_participant` table. `Share` rows reference participants by id (`principal_kind=PARTICIPANT`, `principal_id=external_participant.id`).
- **Q3 — Cross-org pairing**: keep `organization_sharing_session_link` as the trust prerequisite for `SHARED_PROJECT` groups and cross-org `Share(USER)`. Per-org policy may relax inside the pairing.
- **Q4 — Workflows**: per-org customisable from day one, but with app-level templates. `WorkflowDefinition.scope ∈ {APP, ORG}`.
- **Q5 — Billing/plan gating**: out of scope for this redesign. Existing `OrganizationSubscriptionPolicy` may later add limits; the redesign doesn't depend on it.
- **Q6 — Email/identity scope for participants**: scoped per-org (composite uniqueness `(owner_organization_id, email_lower)`). Avoids implicit cross-tenant linkage; merging is an explicit admin action later.

**Migration strategy (revised):** v1 said "big-bang single release". v2 splits this into two Flyway versions to keep Hibernate `validate` mode happy between iterations:
- **V8** (this iteration): additive only — new tables, indices, backfills. Legacy columns stay (nullable where needed). Build never breaks.
- **V9** (later iteration): cutover — drop `app_user.organization_id`, `app_user.role`, `sharing_session.recipient_id/recipient_type/group_id/allow_document_*`, and legacy `organization_group*` tables, after all services are routed through the new model.

A feature flag `app.sharing.v2.enabled` gates user-visible behaviour while we straddle V8 and V9.

---

## 1. Concrete DDL (V8)

PostgreSQL reserves `group`, so the new groups table is `principal_group`. `principal` itself is **not a table** — it's a polymorphic `(principal_kind, principal_id)` pair embedded everywhere it's referenced. This is simpler than a single super-table and matches the rest of the codebase's style.

### 1.1 Enums (declared as string-checked columns, like existing tables)

| Column                  | Allowed values                                                                                   |
|-------------------------|---------------------------------------------------------------------------------------------------|
| `principal_kind`        | `USER`, `PARTICIPANT`, `PRINCIPAL_GROUP`, `ORGANIZATION`, `SERVICE_ACCOUNT`, `PUBLIC_LINK`        |
| `membership_status`     | `INVITED`, `ACTIVE`, `SUSPENDED`, `LEFT`                                                          |
| `group_scope`           | `ORG`, `PERSONAL`, `SHARED_PROJECT`                                                               |
| `group_role`            | `OWNER`, `MANAGER`, `MEMBER`, `OBSERVER`                                                          |
| `role_scope_type`       | `APP`, `ORG`, `PRINCIPAL_GROUP`, `RESOURCE`                                                       |
| `role_name`             | `APP_ADMIN`, `APP_AUDITOR`, `APP_SUPPORT`, `END_USER`, `ORG_OWNER`, `ORG_ADMIN`, `ORG_BILLING_ADMIN`, `ORG_USER_MANAGER`, `ORG_AUDITOR`, `ORG_MEMBER`, `ORG_GUEST`, `OWNER`, `EDITOR`, `REVIEWER`, `SIGNER`, `VIEWER`, `COMMENTER`, `PARTICIPANT` |
| `share_source`          | `DIRECT`, `INVITE`, `LINK`, `INHERITED_FROM_GROUP`, `INHERITED_FROM_ORG`                          |
| `share_status`          | `PENDING_APPROVAL`, `ACTIVE`, `REVOKED`, `EXPIRED`                                                |
| `resource_type`         | `SHARING_SESSION`, `DOCUMENT`, `PRINCIPAL_GROUP`                                                  |
| `share_link_status`     | `ACTIVE`, `REVOKED`, `EXPIRED`                                                                    |

### 1.2 Tables added in V8 (summary; see migration file for full DDL)

- `organization_membership` — `(app_user_id, organization_id, role_name, status, joined_at, invited_by, is_primary, expires_at)`, unique on `(app_user_id, organization_id)`.
- `external_participant` — `(id, owner_organization_id?, email, email_verified_at, display_name, ...)`. `owner_organization_id` nullable for personal-scope participants. Composite unique `(coalesce(owner_organization_id, 'PERSONAL'), lower(email))`.
- `service_account` — `(id, organization_id?, name, scopes, created_by, is_active)`. Authn deferred to existing application-token machinery.
- `principal_group` — `(id, name, description, scope, owner_organization_id?, owner_app_user_id?, parent_group_id?, externally_published, is_active, created_date)`.
- `principal_group_member` — `(id, principal_group_id, principal_kind, principal_id, group_role, added_by_app_user_id, added_at, is_active)`. `principal_kind ∈ {USER, PARTICIPANT}` in V8 (nested groups deferred).
- `principal_group_co_owner_org` — `(principal_group_id, organization_id)`, PK both; used only for `scope=SHARED_PROJECT`.
- `role_assignment` — `(id, app_user_id?, service_account_id?, role_name, scope_type, scope_id?, granted_by_app_user_id?, granted_at, expires_at?, is_active)`. `scope_id` is the org id / group id / resource id depending on `scope_type` (null for `APP`).
- `share` — `(id, resource_type, resource_id, principal_kind, principal_id, role_name, granted_by_app_user_id, granted_at, expires_at?, status, source, source_share_id?, constraints_json)`. Index on `(resource_type, resource_id)`, `(principal_kind, principal_id)`.
- `share_link` — `(id, token_hash, share_id, password_hash?, max_uses?, used_count, domain_allowlist?, expires_at?, status, created_by_app_user_id, created_at)`.
- `access_audit_log` — append-only, hash-chained (mirrors `auth_audit_event` pattern) — `(id, created_date, action, actor_kind, actor_id, target_resource_type, target_resource_id, target_principal_kind, target_principal_id, before_snapshot, after_snapshot, reason_code, event_hash, prev_event_hash)`.

### 1.3 Backfill rules in V8

Idempotent inline `INSERT … SELECT` statements:

- For every active `app_user` with non-null `organization_id` → insert `organization_membership(app_user_id=..., organization_id=..., role_name=<mapped from app_user.role>, status='ACTIVE', is_primary=true)`.
- For every existing `organization_group` row → insert one `principal_group` with `scope='ORG'`, `owner_organization_id=organization_group.organization_id`, same `id`. (We **reuse the id** so existing FKs from `sharing_session.group_id` and `sharing_session_participant.organization_group_id` continue to resolve to the right new row by id-equality during the dual-write window.)
- For every existing `organization_group_member` → insert `principal_group_member(principal_group_id=organization_group_id, principal_kind='USER', principal_id=app_user_id, group_role=<MANAGER if perms.allow_session_* else MEMBER>)`.
- For every `app_user.role ∈ {PLATFORM_ADMIN, APPLICATION}` → insert `role_assignment(role_name='APP_ADMIN', scope_type='APP')`.
- For every existing `sharing_session` → insert exactly one `share` row representing the legacy recipient:
  - `recipient_type='APP_USER'` → `principal_kind='USER'`, `principal_id=recipient_id`.
  - `recipient_type='GROUP'` → `principal_kind='PRINCIPAL_GROUP'`, `principal_id=group_id`.
  - `recipient_type='EMAIL'` → no `external_participant` row yet (we don't know the email — it's only on the session as part of the no-auth flow); a one-off backfill in V9 will lift it once the no-auth recipient email is migrated to its own column.

### 1.4 Constraints & invariants enforced by DDL

- `organization_membership` unique `(app_user_id, organization_id)`; partial index on `(app_user_id) where is_primary=true` (only one primary at a time).
- `principal_group` check: `(scope='ORG' AND owner_organization_id IS NOT NULL AND owner_app_user_id IS NULL) OR (scope='PERSONAL' AND owner_app_user_id IS NOT NULL AND owner_organization_id IS NULL) OR (scope='SHARED_PROJECT')`.
- `role_assignment` check: `(scope_type='APP' AND scope_id IS NULL) OR (scope_type<>'APP' AND scope_id IS NOT NULL)`.
- `share` check: `expires_at IS NULL OR expires_at > granted_at`.
- `access_audit_log` is append-only — no UPDATE/DELETE grants required by app role (enforce out of band when production runs as restricted user).

---

## 2. `AuthorizationService` Kotlin contract

```kotlin
package com.docuhyphen.app.api.service.auth.authz

import com.docuhyphen.app.api.model.entity.AppUser
import java.util.UUID

/** Canonical principal reference used by Share, RoleAssignment, AccessAudit. */
data class PrincipalRef(val kind: PrincipalKind, val id: UUID)

/** Canonical resource reference. */
data class ResourceRef(val type: ResourceType, val id: UUID)

/** Caller's contextual envelope (org under which they are acting, MFA status, IP). */
data class AuthorizationContext(
    val actingUser: AppUser?,
    val activeMembershipId: UUID?,   // which OrganizationMembership context
    val activeOrgId: UUID?,
    val mfaSatisfied: Boolean,
    val clientIp: String?,
)

sealed class Decision {
    object Allow : Decision()
    data class Deny(val reasonCode: String, val message: String) : Decision()
}

interface AuthorizationService {
    fun authorize(
        principal: PrincipalRef,
        action: Action,
        resource: ResourceRef,
        context: AuthorizationContext,
    ): Decision

    fun capabilities(
        principal: PrincipalRef,
        resource: ResourceRef,
        context: AuthorizationContext,
    ): Set<Capability>

    /** Resolve every active grant (role + share) the principal has on the resource. */
    fun grantsOn(
        principal: PrincipalRef,
        resource: ResourceRef,
        context: AuthorizationContext,
    ): List<Grant>
}
```

### 2.1 Resolution algorithm (deterministic)

1. Gather **direct** `role_assignment` rows for the principal across all applicable scopes (`APP`, `ORG` matching the resource's owning org, `PRINCIPAL_GROUP` matching any group the principal belongs to, `RESOURCE` matching this resource id).
2. Gather **inherited** roles via `principal_group_member` (transitive group membership; depth-limited to 5 in V8).
3. Gather **share** rows where `(principal_kind, principal_id)` matches the principal OR any of their groups OR `PUBLIC_LINK` (when a valid link token has been validated upstream).
4. Translate every grant → set of `Capability` via the static `RoleCapabilities` table (see §2.3).
5. **Union** all capabilities. (Conflict resolution: most-permissive wins.)
6. Apply explicit denies: link constraints (`require_mfa`, `ip_allowlist`, `expires_at`), org sharing policy (e.g. external-block), session state machine (`SUSPENDED` denies writes for all but `OWNER`).
7. Return `Allow` if requested `Action`'s required `Capability` ∈ union; else `Deny(reasonCode)`.

### 2.2 `Action` ↔ required `Capability`

```kotlin
enum class Action(val required: Capability) {
    SESSION_VIEW(Capability.SESSION_READ),
    SESSION_EDIT(Capability.SESSION_WRITE),
    SESSION_DELETE(Capability.SESSION_DELETE),
    SESSION_SUSPEND(Capability.SESSION_ADMIN),
    SESSION_END(Capability.SESSION_ADMIN),
    SESSION_TRANSFER_OWNERSHIP(Capability.SESSION_OWNER),
    SESSION_MANAGE_ACCESS(Capability.SESSION_SHARE),
    DOCUMENT_VIEW(Capability.DOCUMENT_READ),
    DOCUMENT_DOWNLOAD(Capability.DOCUMENT_DOWNLOAD),
    DOCUMENT_UPLOAD(Capability.DOCUMENT_WRITE),
    DOCUMENT_UPDATE(Capability.DOCUMENT_WRITE),
    DOCUMENT_DELETE(Capability.DOCUMENT_DELETE),
    DOCUMENT_COMMENT(Capability.DOCUMENT_COMMENT),
    GROUP_VIEW(Capability.GROUP_READ),
    GROUP_MANAGE_MEMBERS(Capability.GROUP_ADMIN),
    GROUP_DELETE(Capability.GROUP_DELETE),
}
```

### 2.3 `RoleCapabilities` (initial mapping)

| Role               | Capabilities                                                                                       |
|--------------------|----------------------------------------------------------------------------------------------------|
| `APP_ADMIN`        | All app-scoped capabilities + ability to elevate to any resource (logged)                          |
| `APP_AUDITOR`      | `*_READ` across the app                                                                            |
| `APP_SUPPORT`      | Read + limited write (no destructive)                                                              |
| `END_USER`         | (no implicit capabilities; everything via Share / org membership)                                  |
| `ORG_OWNER`        | All ORG-scoped capabilities including delete-org                                                   |
| `ORG_ADMIN`        | All ORG-scoped except billing-management and delete-org                                            |
| `ORG_BILLING_ADMIN`| Billing capabilities                                                                                |
| `ORG_USER_MANAGER` | Member/role management within the org                                                              |
| `ORG_AUDITOR`      | `*_READ` within the org + export audit log                                                         |
| `ORG_MEMBER`       | (no implicit document/session caps; relies on shares)                                              |
| `ORG_GUEST`        | Read-only on directly-shared resources, no directory access                                        |
| `OWNER`            | `SESSION_OWNER, SESSION_ADMIN, SESSION_WRITE, SESSION_READ, SESSION_SHARE, DOCUMENT_*`             |
| `EDITOR`           | `SESSION_WRITE, SESSION_READ, DOCUMENT_READ, DOCUMENT_WRITE, DOCUMENT_DOWNLOAD, DOCUMENT_COMMENT`   |
| `REVIEWER`         | `SESSION_READ, DOCUMENT_READ, DOCUMENT_DOWNLOAD, DOCUMENT_COMMENT`                                  |
| `SIGNER`           | `SESSION_READ, DOCUMENT_READ, DOCUMENT_DOWNLOAD, DOCUMENT_SIGN`                                     |
| `VIEWER`           | `SESSION_READ, DOCUMENT_READ` (+ `DOCUMENT_DOWNLOAD` only if share constraint allows)              |
| `COMMENTER`        | `SESSION_READ, DOCUMENT_READ, DOCUMENT_COMMENT`                                                    |
| `PARTICIPANT`      | Whatever the share grants — defaults to `VIEWER`                                                   |

`Capability` is a flat enum used both as a unit of permission and as the value compared in `Action.required`.

### 2.4 Constraint enforcement (denies)

Applied after capability union:

- `share.expires_at < now` → `EXPIRED`
- `share.status != ACTIVE` → `NOT_ACTIVE`
- `share.constraints.require_mfa && !context.mfaSatisfied` → `MFA_REQUIRED`
- `share.constraints.ip_allowlist && client_ip not in list` → `IP_DENIED`
- For `SHARING_SESSION`: status `SUSPENDED` denies non-`SESSION_ADMIN` writes; status `ARCHIVED` denies all writes.
- Org policy: `block_public_links=true` denies creation/use of `PUBLIC_LINK` shares for that org's resources.

---

## 3. Workflow engine (DSL + lifecycle)

### 3.1 JSON schema (stored in `workflow_definition.steps_json`)

```json
{
  "steps": [
    {
      "type": "APPROVAL",
      "assignees": [
        { "kind": "GROUP_ROLE", "groupId": "<uuid>", "role": "MANAGER" },
        { "kind": "ROLE", "roleName": "ORG_ADMIN", "scopeType": "ORG", "scopeId": "<orgId>" },
        { "kind": "PRINCIPAL", "principalKind": "USER", "principalId": "<uuid>" }
      ],
      "quorum": { "kind": "ANY" },                          // or { "kind": "ALL" } / { "kind": "N_OF_M", "n": 2 }
      "slaMinutes": 1440,
      "escalation": {
        "afterSlaBreach": "ESCALATE",
        "escalateTo": [{ "kind": "ROLE", "roleName": "ORG_ADMIN", "scopeType": "ORG", "scopeId": "<orgId>" }]
      },
      "onApprove": { "nextStep": "END", "emit": "session.activated" },
      "onReject":  { "nextStep": "END", "emit": "session.rejected" }
    }
  ]
}
```

### 3.2 First registered workflow (seeded by Flyway in iteration 2)

```json
{
  "name": "session-approval-in-group",
  "scope": "APP",
  "version": 1,
  "trigger": { "event": "session.approval_requested" },
  "steps": [
    {
      "type": "APPROVAL",
      "assignees": [{ "kind": "GROUP_ROLE", "groupIdFrom": "subject.recipientGroupId", "role": "MANAGER" }],
      "quorum": { "kind": "ANY" },
      "slaMinutes": 1440,
      "escalation": { "afterSlaBreach": "ESCALATE", "escalateTo": [{ "kind": "ROLE", "roleName": "ORG_ADMIN", "scopeType": "ORG", "scopeIdFrom": "subject.orgId" }] },
      "onApprove": { "nextStep": "END", "emit": "session.activated" },
      "onReject":  { "nextStep": "END", "emit": "session.rejected" }
    }
  ]
}
```

`subject.*` is a small expression resolved against the workflow subject (the `SharingSession`) when the instance is created.

### 3.3 Tables (added in iteration 2's V10)

- `workflow_definition (id, name, version, scope, organization_id?, trigger_event, steps_json, is_active, created_at)`
- `workflow_instance (id, definition_id, definition_version, subject_resource_type, subject_resource_id, status, current_step_index, created_at, completed_at)`
- `workflow_step_instance (id, instance_id, step_index, type, status, spec_snapshot_json, decisions_json, due_at, escalated_at)`

(Skeleton entities will be added in iteration 2; not in V8 to keep this iteration tight.)

---

## 4. Notifications (event taxonomy + delivery)

### 4.1 Event envelope (Kafka topic `docuhyphen.events.v1`)

```json
{
  "eventId": "uuid",
  "eventType": "session.created",
  "occurredAt": "iso-8601",
  "actor": { "kind": "USER", "id": "uuid" },
  "subject": { "kind": "SHARING_SESSION", "id": "uuid", "orgId": "uuid?" },
  "data": { ... }
}
```

### 4.2 Channels

- `EMAIL` (existing `quarkus-mailer` + SES)
- `SMS` (existing Twilio)
- `IN_APP` (new `in_app_notification` table + WebSocket endpoint, Redis pub/sub fan-out across pods)
- `SLACK`, `TEAMS`, `WHATSAPP` (stubs in V8/iteration-2; real implementations later)

### 4.3 Routing rule order

1. Look up `notification_rule` rows matching `(event_type, scope=APP)` and `(event_type, scope=ORG=event.subject.orgId)`.
2. Resolve recipient set per rule (`principals` resolver supports the same DSL as workflow assignees).
3. For each recipient, look up `notification_preference` matching `event_pattern` (glob over event type).
4. Filter channels via `quiet_hours` (in user's `timezone`).
5. Dispatch through the channel; if delivery fails, fall back via `OrganizationNotificationChannel.fallback_chain` (org-level integrations) or user preference fallback.
6. Persist `delivery_log` row with outcome.

(Tables and beans added in iteration 2; V8 just makes room.)

---

## 5. Iteration plan (turn-by-turn)

| Iteration | Deliverable                                                                                                                                              |
|-----------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| **1 (now)** | v2 plan · `V8` migration · new entities (membership, principal_group, principal_group_member, role_assignment, share, share_link, external_participant, service_account, access_audit_log) · repositories · `AuthorizationService` + capability matrix + types |
| 2         | Workflow engine entities + V10 migration · session-approval workflow definition seed · `WorkflowEngineService` skeleton                                  |
| 3         | Notification entities + V11 · `DomainEventPublisher`, `EventRouter`, `NotificationRuleEngine`, `DeliveryDispatcher`, channel beans (email/in-app first)  |
| 4         | Service-layer refactor: `SharingSessionInitiationService` writes to `share` instead of legacy recipient columns (dual-write while flag off); `OrganizationGroupService` becomes a thin adapter over `PrincipalGroupService` |
| 5         | `ShareResource` + new `SharingSessionResource` "manage access" endpoints over unified `Share`                                                             |
| 6         | App-admin management (`AppAdminResource` with min-2 invariant) · `OrganizationSharingPolicyResource` · `OrganizationNotificationChannelResource`         |
| 7         | Multi-org context in JWT (`active_membership_id` claim) · `AuthorizationContext` propagation through `AuthTokenContext`                                  |
| 8         | **V9 cutover**: drop legacy columns and tables once feature flag flips                                                                                   |
| 9         | Slack/Teams/WhatsApp channel implementations · audit-log export · admin UI for workflow templates                                                        |

---

## 6. What ships in this iteration (concrete files)

```
src/main/resources/db/migration/V8__sharing_collaboration_redesign.sql

src/main/kotlin/com/docuhyphen/app/api/model/entity/
    OrganizationMembership.kt
    OrganizationMembershipStatus.kt
    PrincipalKind.kt
    PrincipalGroup.kt
    PrincipalGroupScope.kt
    PrincipalGroupMember.kt
    GroupRole.kt
    RoleAssignment.kt
    RoleName.kt
    RoleScopeType.kt
    ResourceType.kt
    Share.kt
    ShareSource.kt
    ShareStatus.kt
    ShareLink.kt
    ShareLinkStatus.kt
    ExternalParticipant.kt
    ServiceAccount.kt
    AccessAuditLog.kt

src/main/kotlin/com/docuhyphen/app/api/repository/
    OrganizationMembershipRepository.kt
    PrincipalGroupRepository.kt
    PrincipalGroupMemberRepository.kt
    RoleAssignmentRepository.kt
    ShareRepository.kt
    ShareLinkRepository.kt
    ExternalParticipantRepository.kt
    ServiceAccountRepository.kt
    AccessAuditLogRepository.kt

src/main/kotlin/com/docuhyphen/app/api/service/auth/authz/
    PrincipalRef.kt
    ResourceRef.kt
    AuthorizationContext.kt
    Action.kt
    Capability.kt
    RoleCapabilities.kt
    Decision.kt
    Grant.kt
    AuthorizationService.kt          (interface)
    DefaultAuthorizationService.kt   (implementation skeleton)
```

Nothing existing is edited in this iteration — pure additive — so the existing build, services, and resources all keep working while we layer the foundation in.

---

## 7. Open follow-ups for iteration 2 onward

- Decide JWT claim names for active-org context (`active_membership_id` vs `current_org`).
- Confirm whether `OrganizationMembershipValidationService` (already in `EndpointVerificationFilter`) should be folded into `AuthorizationService` or remain a thin pre-auth check.
- Decide retention policy on `access_audit_log` (mirror `auth_audit_event` WORM behaviour?).
- Confirm Kafka topic naming convention (`docuhyphen.events.v1` proposed).

