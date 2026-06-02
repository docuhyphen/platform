# Sharing & Collaboration Redesign — v1

**Status:** Draft for discussion (pre-implementation). Pre-production project, so a "big-bang" refactor is acceptable.
**Stack (grounded from `pom.xml` and `src/main/kotlin`):** Quarkus 3.17.5 · Kotlin 2.0.21 · JDK 21 · Hibernate ORM + PostgreSQL · Flyway (current head `V7`) · SmallRye Kafka (`quarkus-messaging-kafka`) · Redis (`quarkus-redis-client`) · WebSockets · Mailer + AWS SES · Twilio · S3 · JJWT · WebAuthn · CDI (`quarkus-arc`).
**Convention:** services are `@ApplicationScoped` Kotlin classes under `com.docuhyphen.app.api.service.*`; entities under `...api.model.entity`; JAX-RS resources under `...api.resource`; events via Kafka channels.

---

## 0. Note on framework

The previous chat exchange contained no reference to Spring Boot. This project is **Quarkus + Kotlin**, and the plan below is written against that stack (CDI beans, Hibernate entities, Flyway migrations, SmallRye Kafka, Redis, Quarkus WebSockets). Wherever I say "service" / "event bus" / "router", that maps to:

- service → `@ApplicationScoped` class
- event bus → SmallRye Reactive Messaging Kafka channel
- realtime push → Quarkus `@ServerEndpoint` WebSocket + Redis pub/sub for fan-out across pods
- schema change → Flyway `V<n>__*.sql`

---

## 1. What the current model gets wrong (grounded gap analysis)

Findings from reading the existing code:

### 1.1 Sharing is hard-coded into 3 recipient shapes
`SharingSession` has three mutually-exclusive recipient fields (`recipient: AppUser?`, `recipientGroup: OrganizationGroup?`, plus `recipient_type: EMAIL`). This forces every new sharing combination to add columns and branch logic. It cannot represent:
- Multiple recipients per session
- Mixed recipients (some users + a group + an external email)
- Recipient-specific permissions / expiries / constraints
- Re-share controls per recipient
- Tokenised / link-based shares (anyone-with-link, password link, domain-restricted link)
- Request-access flow

### 1.2 Permission booleans live in two unrelated places
- `SharingSession.allow_document_*` flags describe **session defaults** (apply to the recipient).
- `OrganizationGroupMemberPermission.allow_session_*` / `allow_document_*` describe **a group member's capabilities** inside the group.

There is no unified `authorize(principal, action, resource)` contract — authorisation is scattered as boolean checks at call-sites. This is the root cause of the "primitive" feel.

### 1.3 `AppUserRole` is a single enum on the user row
`AppUserRole { PLATFORM_ADMIN, APPLICATION, APP_USER, ORG_ADMIN, ORG_GROUP_ADMIN, ORG_MEMBER }`

Problems:
- One user, one role — cannot be `ORG_MEMBER` in one org and `ORG_ADMIN` in another (multi-org membership impossible).
- `ORG_GROUP_ADMIN` is global on the user, not scoped per group.
- "Application Admin" is implicit (there's no clear app-level admin role separate from `PLATFORM_ADMIN`/`APPLICATION`) and there's no constraint preventing a single point of failure.
- No `OrgAuditor`, `OrgBillingAdmin`, `OrgGuest`, no `ServiceAccount`, no `Participant-as-principal`.

### 1.4 Participants are weak
`SharingSessionParticipant` only references `appUser` or `organizationGroup`. There is no representation of an **external participant** (email-only / magic-link identity) as a first-class principal that can be granted persistent or session-scoped rights. Today `recipient_type=EMAIL` lives on the session and uses an OTP hash on the session itself — that doesn't scale to multiple email recipients.

### 1.5 Groups are org-only and flat
- `OrganizationGroup` is owned by an org. There is no notion of:
  - **Personal groups** (a free `AppUser` curating contacts)
  - **Shared / cross-org project groups** (for B2B collaboration)
  - **Nested groups** (Engineering → Backend → Platform)
  - **Dynamic / rule-based membership**
  - **Group roles** (Owner / Manager / Member / Observer) — currently only one boolean permission-bag per member.

### 1.6 Org-to-org sharing assumes pairing only
`OrganizationSharingSessionLink` models a pending/accepted/rejected pairing between two orgs (and `OrganizationGroup.externallyPublished` exposes a group to paired orgs). There is no policy layer (e.g. "external sharing requires admin approval", "block public links", "allowlisted domains", "external recipients require MFA").

### 1.7 Approval is bespoke, not a workflow
`AdminApprovalRequest` is a single-table, single-step approval primitive (`action`, `requester`, `approver`, `status`). It is not generic — every new approvable action will add ad-hoc code. No SLA, no escalation, no quorum, no conditional steps.

### 1.8 Session lifecycle is binary-ish
`SharingSessionStatus` (existing) plus `is_deleted` / `endDate` / `expireDate`. No formal state machine like `Draft → PendingApproval → Active → Suspended → Completed → Archived`, and no co-ownership / hand-off / observer concept.

### 1.9 Notifications are direct sends
Mailer + SES + Twilio are wired, but there is no:
- Event taxonomy (`session.*`, `document.*`, `group.*`, `workflow.*`)
- Per-user preferences (channel routing, digest, quiet hours)
- Org-level integration configuration (Slack / Teams / WhatsApp)
- Rules engine (e.g. "notify group managers when session pending > 24h")
- Channel fallback (Slack → in-app → email)

### 1.10 No first-class audit log
`DocumentAuditLog` exists for documents, and `AuthAuditEvent` for auth. There is no general **access-change audit log** (who shared what with whom, when, with what constraints, who revoked), which is required for enterprise.

---

## 2. Target model

### 2.1 Unified `Principal`

Everything that can hold a permission is a Principal:

| Principal kind   | Backing entity                                           |
|------------------|----------------------------------------------------------|
| `USER`           | `AppUser`                                                |
| `PARTICIPANT`    | new `ExternalParticipant` (email-verified, no login)     |
| `GROUP`          | redesigned `Group` (org / personal / shared)             |
| `ORGANIZATION`   | `Organization`                                           |
| `SERVICE_ACCOUNT`| new `ServiceAccount`                                     |
| `PUBLIC_LINK`    | new `ShareLink` (tokenised anonymous principal)          |

A `principal_ref` is `(kind, id)`. Every permission grant references one.

### 2.2 Multi-org membership

Introduce `OrganizationMembership`:
```
OrganizationMembership {
  id, app_user_id, organization_id,
  status: ACTIVE | INVITED | SUSPENDED | LEFT,
  joined_at, invited_by, deprovisioned_at,
  is_primary: bool   // UI default; identity is still global
}
```
A user can have many memberships. `AppUser.role` (the column) is retired — role becomes per-membership and per-scope (see §2.4).

### 2.3 Group model

```
Group {
  id, name, description,
  scope: ORG | PERSONAL | SHARED_PROJECT,
  owner_org_id?, owner_user_id?,
  parent_group_id?,              // nesting
  membership_mode: STATIC | DYNAMIC,
  dynamic_rule_json?,            // phase 2
  externally_published: bool,    // ORG groups, kept from current model
  is_active, created_date
}

GroupMember {
  id, group_id, principal_ref,   // USER or PARTICIPANT (or nested GROUP later)
  group_role: OWNER | MANAGER | MEMBER | OBSERVER,
  added_by, added_at, is_active
}

GroupCoOwnerOrg (for SHARED_PROJECT)  // jointly owned by 2+ orgs
```

`OrganizationGroup` / `OrganizationGroupMember` / `OrganizationGroupMemberPermission` are migrated into this and deleted.

### 2.4 Permissions — three layers + capability checks

| Layer        | Roles (initial set)                                                                                          | Scope       |
|--------------|---------------------------------------------------------------------------------------------------------------|-------------|
| **System**   | `APP_ADMIN`, `APP_AUDITOR`, `APP_SUPPORT`, `END_USER`                                                         | Application |
| **Org**      | `ORG_OWNER`, `ORG_ADMIN`, `ORG_BILLING_ADMIN`, `ORG_USER_MANAGER`, `ORG_AUDITOR`, `ORG_MEMBER`, `ORG_GUEST`  | Organization|
| **Resource** | `OWNER`, `EDITOR`, `REVIEWER`, `SIGNER`, `VIEWER`, `COMMENTER`, `PARTICIPANT`                                 | Document / Session / Group |

Capabilities are derived (table) not free-form booleans, e.g. `EDITOR` ⇒ `{document.view, document.download, document.update, document.comment}`.

`AppUserRole` (current enum) is replaced. To preserve existing semantics during migration:
- `PLATFORM_ADMIN`, `APPLICATION` → `APP_ADMIN` role assignment (system layer)
- `ORG_ADMIN` → `ORG_ADMIN` role on their membership
- `ORG_GROUP_ADMIN` → `MANAGER` group role on the relevant group(s)
- `ORG_MEMBER` / `APP_USER` → just `END_USER` system role + (optional) `ORG_MEMBER` org role

**Multiple App Admins.** `APP_ADMIN` is a role assignment, not an enum value on the user row. Constraints:
- Minimum **2 active** `APP_ADMIN`s at all times (last-admin removal blocked).
- A dedicated **break-glass** account separate from staff identities (Secrets Manager-stored credentials, alarmed on use).
- Sensitive ops (remove admin, change policy, delete org) go through the **workflow engine** (§4) requiring a second-admin approval — the modern replacement for today's `AdminApprovalRequest`.
- App admins keep their normal user privileges — they operate as users by default and "elevate" per-action.

### 2.5 Unified `Share`

Single sharing table replacing the recipient columns on `SharingSession`:

```
Share {
  id,
  resource_ref: (resource_type, resource_id)         // SESSION | DOCUMENT | GROUP | FOLDER(later)
  principal_ref: (principal_kind, principal_id)
  role,                                              // resource-layer role
  granted_by_principal_ref,
  granted_at,
  expires_at?,
  status: PENDING_APPROVAL | ACTIVE | REVOKED | EXPIRED,
  source: DIRECT | INVITE | LINK | INHERITED_FROM_GROUP | INHERITED_FROM_ORG,
  source_share_id?,                                  // for inheritance traceability
  constraints_json: {
    can_reshare, can_download, can_print, watermark,
    ip_allowlist, require_mfa, max_views, domain_allowlist
  }
}
```

Sharing modes are just `source` values plus the principal kind:
- **Direct** → existing `USER` / `PARTICIPANT` / `GROUP` principal
- **Invite (email → participant)** → creates `ExternalParticipant` on accept, share rebound to it
- **Link** → `PUBLIC_LINK` principal backed by `ShareLink` (token, optional password hash, optional domain allowlist, expiry, max uses)
- **Request-access** → starts as `PENDING_APPROVAL`

`SharingSession.recipient` / `recipientGroup` / `recipientType` are dropped; the session always resolves recipients through `Share` rows (`resource_type=SESSION`).

### 2.6 Authorisation contract

One CDI bean replaces ad-hoc checks across services:

```kotlin
@ApplicationScoped
class AuthorizationService {
  fun authorize(principal: PrincipalRef, action: Action, resource: ResourceRef): Decision
  fun capabilities(principal: PrincipalRef, resource: ResourceRef): Set<Capability>
}
```

Resolution algorithm:
1. Collect all grants applicable to the principal on the resource (direct + via group memberships + via org role + via system role + via share inheritance).
2. Union their capabilities.
3. Apply explicit `Deny` rules (org policies, link constraints).
4. Apply contextual constraints (MFA satisfied? IP in allowlist? share expired?).
5. Return `ALLOW` / `DENY(reason)`.

All resources / services delegate to this — no more boolean fields scattered around.

### 2.7 Session lifecycle (formal state machine)

```
DRAFT
 → PENDING_APPROVAL (if recipient group has approval workflow)
 → ACTIVE
 → SUSPENDED  (admin / owner can pause)
 → COMPLETED  (terminal, normal end)
 → ARCHIVED   (read-only, retention window)
 → REJECTED   (terminal, from PENDING_APPROVAL)
 → EXPIRED    (terminal, auto)
```

Transitions are guarded by `AuthorizationService` + workflow engine. Every transition emits a Kafka event.

---

## 3. Notifications

### 3.1 Event taxonomy (Kafka topics → in-process router)

Topic `docuhyphen.events.v1`, key = `aggregate_id`. Event types (initial set):
- `session.created`, `session.approval_requested`, `session.approved`, `session.rejected`, `session.activated`, `session.suspended`, `session.completed`, `session.expired`
- `share.created`, `share.revoked`, `share.expired`, `share.access_requested`
- `document.viewed`, `document.downloaded`, `document.commented`, `document.updated`
- `group.member_added`, `group.member_removed`, `group.role_changed`
- `workflow.step_assigned`, `workflow.step_completed`, `workflow.escalated`

### 3.2 Per-user preferences

```
NotificationPreference {
  id, app_user_id,
  event_pattern,                  // "session.*", "document.viewed", etc.
  channels: [EMAIL, IN_APP, SLACK, TEAMS, WHATSAPP, SMS]
  delivery: INSTANT | DIGEST_HOURLY | DIGEST_DAILY
  quiet_hours_start, quiet_hours_end, timezone
}
```

### 3.3 Org integrations

```
OrganizationNotificationChannel {
  id, organization_id,
  channel: SLACK | TEAMS | WHATSAPP,
  oauth_tokens (encrypted via existing Secrets Manager pattern),
  enforced: bool,                 // if true, suppress per-user email opt-out
  fallback_chain: [SLACK, IN_APP, EMAIL]
}
```

Per-user OAuth links (`UserChannelLink`) bind an `AppUser` to their Slack/Teams identity inside an org-configured channel.

### 3.4 Rules engine

`NotificationRule` = `(scope, event_pattern, predicate_json, action)` — runs in a CDI bean fed by the Kafka consumer. Examples:
- "Notify group managers when a session is pending > 24h" (scheduled re-eval via Quarkus scheduler).
- "Suppress notifications to the actor of the event".
- "Only notify owners on `document.downloaded` for external recipients".

### 3.5 Delivery pipeline

```
Kafka event
  → EventRouter (CDI)
  → NotificationRuleEngine
  → DeliveryDispatcher
       ├── EmailChannel  (quarkus-mailer / SES)
       ├── SmsChannel    (Twilio)
       ├── InAppChannel  (persisted + WebSocket push via Redis pub/sub)
       ├── SlackChannel  (new)
       ├── TeamsChannel  (new)
       └── WhatsAppChannel (Twilio / Meta)
  → DeliveryLog (retry / fallback chain)
```

Existing `quarkus-mailer` / `SES` / `Twilio` clients are reused as channel implementations.

---

## 4. Workflow engine (generic, ships with one workflow)

### 4.1 Schema

```
WorkflowDefinition {
  id, name, version,
  scope: APP | ORG,
  org_id?,                          // null for app-wide templates
  trigger_event,                    // matches event taxonomy
  steps_json: [WorkflowStepSpec],
  is_active
}

WorkflowStepSpec {
  type: APPROVAL | NOTIFICATION | CONDITION | ACTION
  assignees: [principal_ref | role_ref | group_role_ref]
  quorum: ANY | ALL | N_OF_M(n)
  sla_minutes?, escalate_to?
  on_approve_next_step?, on_reject_next_step?
  action_handler_key?               // for ACTION steps (registered CDI bean)
}

WorkflowInstance {
  id, definition_id, version,
  subject_ref,                      // e.g. SharingSession id
  status: RUNNING | COMPLETED | REJECTED | CANCELLED | ESCALATED
  current_step_index,
  created_at, completed_at
}

WorkflowStepInstance {
  id, instance_id, step_index, spec_snapshot_json,
  status: PENDING | APPROVED | REJECTED | ESCALATED | SKIPPED,
  assignee_refs, decisions: [{ principal_ref, decision, reason, at }],
  due_at, escalated_at
}
```

### 4.2 First workflow registered: Session Approval

```
Trigger:  session.approval_requested  (emitted when initiator creates a session whose recipient group has an approval policy)
Step 1:   APPROVAL
            assignees = group_role_ref(group_id=<recipient_group>, role=MANAGER)
            quorum    = ANY
            sla       = 24h
            escalate  = ORG_ADMIN role of group's owning org
            on_approve → session.activated  (state machine moves DRAFT→PENDING_APPROVAL→ACTIVE)
            on_reject  → session.rejected
```

`AdminApprovalRequest` and its `AdminApprovalResource` are replaced by workflow instances against the generic engine; backward-compatible read endpoints can be retained as a thin adapter during migration if needed.

### 4.3 Future workflows (no code changes, just new definitions)
- Document review chain (Reviewer → Approver → Signer)
- External access policy (any share to non-paired org requires `ORG_ADMIN` approval)
- Onboarding checklist (a sequence of `ACTION` and `NOTIFICATION` steps)

---

## 5. App- and Org-level settings changes

### 5.1 App settings (system layer)
- **Administrators** panel — list of `APP_ADMIN`s, add/remove (two-admin approval via workflow), break-glass account status, last-used.
- **Workflow templates** — catalogue of app-defined workflows that orgs can adopt.
- **Notification defaults** — system-wide rate limits, default channels.
- **Audit log** — searchable; export for compliance.
- **Feature flags** — gate redesign rollouts; useful even pre-production.
- **Sharing policy defaults** — defaults orgs inherit (link expiry max, MFA-for-external, etc.).

### 5.2 Org settings
- **Members & Roles** (per-membership roles)
- **Groups** (org / shared-project)
- **Integrations** (Slack, Teams, WhatsApp; enforcement toggle; fallback chain)
- **Workflows** (adopt template / customise)
- **Sharing Policies**: external sharing requires approval, allowed link types, allowed recipient domains, max link expiry, watermark default, re-share default.
- **Branding**
- **Audit log** (org-scoped slice)

---

## 6. Access management UX (server contract)

### 6.1 At session creation (`POST /api/sharing-sessions`)
Request body shape (illustrative):
```json
{
  "sessionName": "...",
  "shares": [
    {
      "principal": { "kind": "USER", "id": "..." },
      "role": "EDITOR",
      "constraints": { "expiresAt": "...", "canReshare": false }
    },
    {
      "principal": { "kind": "GROUP", "id": "..." },
      "role": "REVIEWER"
    },
    {
      "principal": { "kind": "PARTICIPANT", "email": "ext@acme.com" },
      "role": "VIEWER",
      "constraints": { "requireMfa": true, "watermark": true }
    },
    {
      "principal": { "kind": "PUBLIC_LINK" },
      "role": "VIEWER",
      "constraints": { "passwordRequired": true, "expiresAt": "...", "maxUses": 10 }
    }
  ],
  "documents": [...],
  "defaults": { "allowDocumentDownload": true, "allowDocumentUpload": false }
}
```

Server flow:
1. `AuthorizationService.authorize(initiator, SESSION_CREATE, org_or_personal_scope)`.
2. Materialise `SharingSession` + a `Share` row per recipient.
3. If any recipient triggers an approval policy → session enters `PENDING_APPROVAL`, emit `session.approval_requested`, workflow engine takes over. Otherwise activate immediately.
4. Emit `session.created` + per-share `share.created`.

### 6.2 While active (`GET/PATCH /api/sharing-sessions/{id}/access`)
Returns the unified access view:
```
[
  { share_id, principal, role, source: DIRECT|INHERITED_FROM_GROUP|LINK,
    granted_by, granted_at, expires_at, last_activity, constraints, status }
]
```
Operations: add share, change role, revoke, change constraints, transfer ownership, suspend session — all guarded by `AuthorizationService` and audit-logged.

### 6.3 Real-time revocation
- WebSocket sessions subscribe to `share.revoked` / `session.suspended` (via Redis pub/sub fanned out from Kafka consumer).
- Quarkus WebSocket endpoint closes affected client sockets and JWT short-TTL + Redis denylist invalidates outstanding tokens against the revoked share.

### 6.4 Inheritance rules
- Share to a `GROUP` grants `INHERITED_FROM_GROUP` shares to current members (and future members, unless `snapshotMode=true` is chosen at creation).
- Removing a member auto-revokes their inherited shares.
- An explicit `DIRECT` share to a user always wins over the inherited one (different role merges via "most permissive" within the resource layer; explicit `Deny` constraints win).

---

## 7. Coverage check against your use-cases

| Scenario                                       | Mechanism                                                                                          |
|------------------------------------------------|----------------------------------------------------------------------------------------------------|
| user → user                                    | Direct `Share(USER)` on a personal-scope session                                                   |
| user → org user                                | Direct `Share(USER)` — recipient's org policy may add MFA constraint                                |
| org user → user (customer)                     | Direct `Share(USER)` or `Share(PARTICIPANT)` (invite by email)                                      |
| org user → org user (internal)                 | Direct `Share(USER)` within the same org scope                                                      |
| org user → org user (external)                 | Direct `Share(USER)` between paired orgs; org sharing policy may require approval workflow          |
| org user → org group/dept (internal)           | `Share(GROUP)` where group scope=ORG, owner_org=current org                                         |
| org user → org group/dept (external)           | `Share(GROUP)` where target group is `externally_published` and orgs are paired (or policy allows)  |
| Limited participants                           | `ExternalParticipant` principal with role `PARTICIPANT` / `VIEWER` + constraints                    |
| Onboarding team (large org)                    | `Group(scope=ORG)`, optionally `DYNAMIC` (rule: `title=Onboarding`), with `MANAGER` group role      |
| Cross-org B2B project                          | `Group(scope=SHARED_PROJECT)` with two orgs as co-owners + workflow for membership changes          |
| Bulk customer document delivery                | `Group(scope=PERSONAL)` of `ExternalParticipant`s, or `PUBLIC_LINK` share with constraints          |
| Auditor / read-only across org                 | Org role `ORG_AUDITOR`                                                                              |
| External legal counsel across multiple clients | Multiple `OrganizationMembership` rows with role `ORG_GUEST`                                        |

---

## 8. Migration plan (big-bang, pre-production)

Single coordinated release. Numbered for delivery order; **bold** items unblock the next.

1. **Flyway `V8__principals_and_authz.sql`**
   - New tables: `principal`, `organization_membership`, `group` (rename of `organization_group` then re-pointed), `group_member`, `role_definition`, `role_assignment`, `capability`, `share`, `share_link`, `external_participant`, `service_account`, `audit_log`.
   - Drop columns from `sharing_session`: `recipient_id`, `recipient_type`, `group_id`; data backfilled into `share` rows first.
   - Drop enum value `ORG_GROUP_ADMIN` from `app_user.role` (replaced by group-role assignment); retire `app_user.role` column in a later step once code paths are migrated (keep it as nullable for the release that does the cutover).

2. **Backfill SQL inside `V8`** (single transaction)
   - For every `sharing_session` row, insert a `share` row keyed by current recipient.
   - For every `organization_group_member`, insert a `group_member` row + a `role_assignment` (GROUP scope, role=`MANAGER` if `OrganizationGroupMemberPermission.allow_session_*` is mostly true, else `MEMBER`).
   - For every `app_user.role`, create the equivalent `role_assignment` (system or org scope).
   - Existing single Application Admin → seed a `APP_ADMIN` role assignment + create a break-glass admin account row (credentials provisioned out-of-band via Secrets Manager).

3. **`AuthorizationService` bean** in `service/auth/`
   - All new code routes through it.
   - Provide `@AuthorizationRequired(action = ..., resource = ...)` JAX-RS interceptor (use existing `api/interceptor` package) so resources don't repeat checks.

4. **Refactor existing services** to call `AuthorizationService`:
   - `SharingSessionInitiationService`, `SharingSessionUpdateService`, `SharingSessionParticipantService`, `SharingSessionDocumentService`, `OrganizationGroupService`, `OrganizationAppUserService`, `OrganizationSharingSessionLinkService`.
   - Delete the boolean check helpers they currently use.

5. **Replace `AdminApprovalRequest` / `AdminApprovalResource`** with the workflow engine + a "Session Approval" workflow definition seeded by Flyway. Keep `AdminApprovalResource` URL as a deprecated read-only adapter for one release if any UI still calls it.

6. **Event pipeline**
   - Introduce Kafka topic `docuhyphen.events.v1`.
   - New CDI beans: `DomainEventPublisher` (called from services), `EventRouter` (Kafka consumer), `NotificationRuleEngine`, `DeliveryDispatcher`, channel beans (`EmailChannel` wrapping existing mailer/SES, `SmsChannel` wrapping Twilio, `InAppChannel`, stubs for `SlackChannel` / `TeamsChannel` / `WhatsAppChannel`).
   - In-app channel persists to a new `in_app_notification` table and pushes via a new WebSocket endpoint; Redis pub/sub used for cross-pod fanout.

7. **Rebuild "Manage Access" endpoints** under `SharingSessionResource` / new `ShareResource` around the unified `Share` model. Remove the legacy `allow_document_*` flags from `SharingSession` and migrate their semantics into default constraints on the auto-generated initial share (so existing sessions keep behaving).

8. **Org settings additions**
   - `OrganizationSharingPolicyResource` (external sharing, link types, domain allowlist, MFA-for-external).
   - `OrganizationNotificationChannelResource` (Slack/Teams/WhatsApp config + OAuth callback).
   - `OrganizationWorkflowResource` (adopt / customise templates).

9. **App settings additions**
   - `AppAdminResource` (list/add/remove admins; min-2 invariant enforced server-side; sensitive removals create a workflow instance).
   - `WorkflowTemplateResource`, `NotificationDefaultsResource`, `AuditLogResource`.

10. **Delete legacy code paths** in the same release: old recipient columns, `OrganizationGroupMemberPermission` table, `AdminApprovalRequest` table (after migrating in-flight requests into workflow instances), `AppUserRole` enum (replaced by role-assignment types).

11. **Feature-flag the cutover** (`docuhyphen.sharing.v2.enabled`) so the redesign can be toggled per environment during integration testing, even though the goal is a single big-bang release to production.

---

## 9. Open questions (need answers before locking v2 schema)

1. **Multi-org membership**: confirm a single `AppUser` can be a member of N organisations (recommended). If yes, all current code assuming a single `organization_id` on `AppUser` (via `OneToMany` on `Organization.appUsers` with `@JoinColumn(name = "organization_id")`) must move to `OrganizationMembership`.
2. **Participants** persistent (`ExternalParticipant` row reused across sessions for the same verified email) or strictly ephemeral per session? Recommend persistent — enables re-share, audit continuity, and digest notifications.
3. **Cross-org B2B sharing**: keep the existing explicit pairing (`OrganizationSharingSessionLink`) as a hard prerequisite for `SHARED_PROJECT` groups and `Share(USER)` across orgs? Or allow implicit cross-org sharing gated only by per-org policy?
4. **Workflow customisation**: per-org customisable from day one, or only system-defined templates initially with per-org enable/disable?
5. **Billing/plan gating** of new features (group count limits, workflow step limits, integrations) — does a billing/plan model exist or is it deferred?
6. **Email vs identity**: should two `ExternalParticipant`s with the same email auto-merge into one principal across orgs, or be scoped per org for privacy?

---

## 10. Deliverables after sign-off on this plan

When you green-light v2 of this plan, the implementation will produce:

- `V8__principals_and_authz.sql` (+ data backfill) and any follow-up `V9__retire_legacy_columns.sql`.
- New entities in `model/entity/`: `Principal`, `OrganizationMembership`, `Group`, `GroupMember`, `RoleDefinition`, `RoleAssignment`, `Share`, `ShareLink`, `ExternalParticipant`, `ServiceAccount`, `WorkflowDefinition`, `WorkflowInstance`, `WorkflowStepInstance`, `NotificationPreference`, `NotificationRule`, `OrganizationNotificationChannel`, `InAppNotification`, `AuditLogEntry`.
- New services in `service/`: `AuthorizationService`, `ShareService`, `GroupService` (replaces `OrganizationGroupService`), `WorkflowEngineService`, `DomainEventPublisher`, `EventRouter`, `NotificationRuleEngine`, `DeliveryDispatcher` + channel beans.
- New JAX-RS resources: `ShareResource`, `WorkflowResource`, `AppAdminResource`, `OrganizationSharingPolicyResource`, `OrganizationNotificationChannelResource`, `OrganizationWorkflowResource`, `AuditLogResource`, `InAppNotificationResource` (+ WebSocket endpoint).
- Refactor of: every existing `service/sharingsession/*` and `service/organization/*` service to delegate to `AuthorizationService`.
- New `application.properties` keys (channel toggles, Kafka topic, feature flag).
- Tests under `src/test/kotlin/` covering the authorisation matrix, share inheritance, workflow happy/escalation paths, and notification routing.

---

*End of v1. Reply with answers to §9 (or "go with recommendations") and I'll produce v2 with concrete DDL, the `AuthorizationService` contract in Kotlin, the workflow DSL JSON schema, and the notification routing spec.*

