# Sharing / Collaboration Redesign — Follow-up Plans (Index)

> **Read this first.** It explains what was originally asked, what is already built,
> and how the remaining work is split into the six self-contained plans in this folder.
> Each plan is a vertical slice (backend + UI) you can hand to a fresh Claude Code
> session on its own.

---

## 1. What the user originally wanted

A complete, enterprise-grade redesign of sharing/collaboration and the permission
model. The product must support, with one coherent model:

- **All sharing topologies:**
  - user → user (small, personal)
  - user → org
  - org → user (B2C customer, the recipient may have *no* org)
  - org → org (internal divisions **and** external partners)
  - org → group (internal **and** external)
- **Participants with limited access** — recipients who are not full collaborators
  (e.g. view-only, no download, watermarked, capped views, time-boxed).
- **Clear principal definitions:** user, org-user, org-group.
- **Granular org-group permissions** — incl. managing sessions and adding/removing
  users, with permissions *separable* (not all-or-nothing).
- **Teams/Slack/M365/Google-Docs-grade flexibility**, including end users creating
  and managing **their own** groups (self-service, personal scope).
- **Multiple App Admins**, who can also act as normal users.
- **A flexible workflow system**, starting with *approve/decline a sharing session
  within a group*.
- **Updated notification rules** driven by the workflow/event system.
- **Updated access management** both at session creation **and** while a session is
  active.
- **Possibly updated app settings** to back the above.
- Must scale across small user→user, small/large B2C, and small/large B2B; support
  **onboarding teams**.
- Still **development phase, not production** — no backwards-compat owed, DB is
  disposable.

## 2. What is already implemented (SOLID)

The A–E cutover to the unified model is complete, compiles, and boots clean against
an empty DB (Hibernate `validate` passes; Flyway `V1__baseline.sql` + `V2__seed.sql`).

- **Unified authorization core.** `AuthorizationService.authorize(principal, action,
  resource, context): Decision` with `DefaultAuthorizationService`. `Action` →
  `Capability`; `RoleCapabilities` maps `RoleName` → `Set<Capability>` (most-permissive
  union). `PrincipalRef` / `ResourceRef` (`.session(id)`, `.group(id)`),
  `AuthorizationContextFactory.currentPrincipal()/currentContext()`.
  Path: `src/main/kotlin/com/docuhyphen/app/api/service/auth/authz/`.
- **Unified data model.** `Share`, `PrincipalGroup` + `PrincipalGroupMember`,
  `OrganizationMembership`, `RoleAssignment`, workflow + notification tables.
- **All 7 sharing topologies are *structurally* representable** via `Share` +
  recipient/participants + `PrincipalGroup`.
- **Workflow engine.** `WorkflowEngineService` (trigger/recordDecision/escalateOverdue/
  cancel); seeded `session-approval-in-group` definition (`V2__seed.sql`, fixed UUID
  `…0001`). Decision identity comes from the authenticated session, never the body.
- **Notification pipeline.** DomainEventPublisher → EventRouter → NotificationRuleEngine
  → DeliveryDispatcher; seeded rules for `workflow.step_assigned`, `workflow.escalated`,
  `session.activated`, `session.rejected` (`V2__seed.sql`, UUIDs …0101–0104).
- **Session access management (backend).** `SessionAccessManagementService`
  (`grantAccess` / `changeRole` / `revokeAccess`), endpoints on
  `SharingSessionResource` (`GET/POST /{sessionId}/access`,
  `PATCH/DELETE /{sessionId}/access/{shareId}`), DTOs `SessionAccessEntryDto`,
  `GrantSessionShareRequest`, `UpdateSessionShareRoleRequest`.
- **Org sharing policy.** `OrganizationSharingPolicyService.assertCanShareWithUser`
  gates org→unpaired/no-org recipients on `OrganizationSettings.allowShareWithoutPairing`.
- **Migration squash.** V1–V14 collapsed into `V1__baseline.sql` (schema dump) +
  `V2__seed.sql` (5 deterministic seeds). Validated end-to-end.

## 3. What is NOT solid (the scorecard gaps → the six plans)

| # | Gap | Severity | Plan |
|---|-----|----------|------|
| 1 | **Participants with limited access** — ✅ **BACKEND DONE.** `ShareConstraints` typed parser + `adjustCapabilities`; `DefaultAuthorizationService` parses `constraints_json` (can_download/can_reshare/watermark/max_views/require_mfa), VIEWER download gate applied, obligations computed; write-time normalization in `SessionAccessManagementService.grantAccess` + download gate in `SharingSessionDocumentService`. **Deferred:** real `max_views` counting (needs a `share_view` table); UI (Plan 06). | 🟡→✅bk | [01](01-participant-limited-access.md) |
| 2 | **Personal / self-service groups** — ✅ **BACKEND DONE.** `PrincipalGroupService` extended with `createPersonalGroup` / `renamePersonalGroup` / `addPersonalMembers` / `removePersonalMember` / `deletePersonalGroup` (scope=PERSONAL, ownerOrganizationId=null, ownerAppUserId=owner, owner inserted as OWNER member). Reciprocity-gated via `UserContactRepository.isMutualContact`. New `PersonalGroupResource` (`/me/groups`) with `authorize()` on every mutation via `ResourceRef.group(id)`. `RoleCapabilities` OWNER→GROUP_ADMIN+GROUP_DELETE confirmed. `SharingSessionInitiationService` GROUP recipient resolution is scope-agnostic (works for PERSONAL). No schema migration needed. **Deferred:** UI (Plan 06). | 🔴→✅bk | [02](02-personal-self-service-groups.md) |
| 3 | **Group permission enforcement** — ✅ **BACKEND DONE.** Added the membership→grant bridge (`ORG_MEMBERSHIP` + `GROUP_MEMBERSHIP` SourceKinds in `DefaultAuthorizationService`) so `authorize()` resolves org-role and group-role grants; `OrganizationGroupService` create→`requireOrgAdminIn(orgId)`, update→`GROUP_MANAGE_MEMBERS`, delete→`GROUP_DELETE`. **Deferred:** UI (Plan 06); session-vs-member separability needs new GroupRole (user sign-off). | 🟠→✅bk | [03](03-group-permission-enforcement.md) |
| 4 | **Multiple App Admins** — ✅ **BACKEND DONE.** `RoleAssignmentService` (grant/revoke/list, idempotent, last-admin invariant via `LastAppAdminException`) + `AppRoleResource` (`/admin/roles/app-admins`, guarded by `isAppAdmin` from the session) + config-gated `@Observes StartupEvent` bootstrap (`app.security.app-admin.bootstrap-email`). App roles are additive. **Boot-validated** as of Plan 05's from-empty boot (the StartupEvent bootstrap observer forced `ConfigurationService` instantiation — ArC 64KB risk cleared). **Deferred:** UI (Plan 06). | 🟠→✅bk | [04](04-app-admin-provisioning.md) |
| 5 | **B2C external-customer default** — ✅ **BACKEND DONE + boot-validated.** `OrganizationSharingPolicyService.assertCanShareWithUser` now branches by recipient nature: internal=allowed, **B2C (no-org recipient)=allowed by default** (new `OrganizationSettings.allowExternalCustomerSharing`, default `true`, audit-logged), B2B-unpaired=existing pairing gate unchanged. Migration `V3__org_settings_external_customer.sql` (Flyway V1→V3 + Hibernate `validate` confirmed on a throwaway DB). **Deferred:** UI (Plan 06, two-toggle settings + recipient badge). | 🟠→✅bk | [05](05-b2c-external-customer-sharing.md) |
| 6 | **UI — Plan 06 first pass landed scaffolds for all 9 workstreams (workflow inbox, access panel, my-groups tab, app-admin tab, constraints component, two-toggle org settings, notification copy, role enums).** Follow-up gaps tracked in Plan 07; all closed 2026-06-03. | ✅ | [06](06-frontend-integration.md) |
| 7 | **UI gaps from Plan 06 (Plan 07)** — ✅ **COMPLETE (closed 2026-06-03 evening).** All nine gaps landed. G1 (initiation constraints, backend DTO + UI), G2 (personal groups in picker), G3 (App Admins tab gate + global candidate search + 409 copy), G4 (capability gating in EditGroupDialog), G5 (external-recipient badge), G6 (viewer obligations — backend DTO fields + watermark overlay + download gate), G7 (`GET /workflows/steps/pending` + initial fetch), G8 (popover gate removed), G9 (dead DTO + dead realtime union members swept). `npx tsc --noEmit` green in web-app. | ✅ | [07](07-frontend-integration-gaps.md) |

> **Progress (2026-06-03 evening):** Plans 01–07 functionally **complete**. The
> sharing/collaboration redesign is fully wired end-to-end: backend authorization
> with obligations + workflow engine + multi-org + personal groups + B2C default,
> plus the React UI consuming all of it (initiation constraints, viewer watermark,
> download gate, pending-decisions inbox surviving refresh, app-admin global
> search, external-recipient badge, group-edit permission gating). Backend boots
> green; `npx tsc --noEmit` green in web-app. **No migrations consumed by Plan 07
> — next migration remains V4.** Remaining deferred items: `share_view` table for
> real `max_views` counting (Plan 01 carry-over) and a `GET /groups/{id}/capabilities`
> probe so EditGroupDialog discovers "no permission" before the user clicks Save
> (Plan 07 G4 follow-up).

## 4. Suggested sequencing

Plans are independent enough to parallelize, but the natural order is:

1. ~~**Plan 03** (group permission enforcement)~~ — ✅ **DONE (backend).** Established the
   `authorize()` + membership→grant pattern the others reuse.
2. ~~**Plan 01** (participant limited access)~~ — ✅ **DONE (backend).** Authz depth for
   share constraints; UI still in Plan 06.
3. ~~**Plan 04** (app-admin provisioning)~~ — ✅ **DONE (backend).** RoleAssignmentService +
   AppRoleResource + startup bootstrap.
4. ~~**Plan 05** (B2C external-customer default)~~ — ✅ **DONE (backend) + boot-validated.**
   Per-recipient branch + `V3__…` migration. **Plan 02** (personal groups) ← **next**.
5. ~~**Plan 02** (personal/self-service groups)~~ — ✅ **DONE (backend).** PERSONAL-scope
   group CRUD + PersonalGroupResource `/me/groups` + reciprocity guard; no schema migration.
6. ~~**Plan 06** (frontend, first pass)~~ — ✅ **scaffolds landed** for all nine workstreams
   (workflow inbox, access panel, my-groups, app-admins, constraints component, two-toggle
   org settings, notification copy, role enums). Functional gaps captured in Plan 07.
7. **Plan 07** (frontend gaps) — ✅ **DONE (2026-06-03 evening).** All nine gaps closed.

## 5. Conventions every plan assumes

- **Package root:** `com.docuhyphen.app.api` (note: `docuhyphen`, not `dochyphen`).
- **Stack:** Quarkus 3.17.5 / Kotlin / Hibernate ORM `validate` mode / Flyway
  `migrate-at-start=true` / PostgreSQL 15.
- **Never run destructive migrations against the dev DB.** Validate schema changes on
  a throwaway DB: `CREATE DATABASE dh_validate_tmp`, override
  `QUARKUS_DATASOURCE_JDBC_URL` + `QUARKUS_HTTP_PORT=8099`, boot once ("Listening on"
  proves Hibernate `validate` matched), then `pg_terminate_backend` lingering
  connections and `DROP DATABASE`. Deleted source migrations linger in
  `target/classes/db/migration` — purge manually.
- **New migrations** start at `V4__…` (V1/V2 are the squashed baseline + seed; V3 is
  Plan 05's `allow_external_customer_sharing` column).
- **Authz identity rule:** the acting principal always comes from
  `AuthorizationContextFactory.currentPrincipal()` / the authenticated session — never
  from the request body.
- **Project memory:** `…/.claude/projects/C--Users-Black-IdeaProjects-doc-hyphen/memory/`
  — `MEMORY.md` (index) + `project_sharing_collab_redesign.md` (long-form log). Append
  a DONE note there when a plan lands.
