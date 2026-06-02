# Prompt — Plan 06: Frontend Integration

Paste this into a new session to continue with Plan 06.

---

## Context

I'm working on a **sharing/collaboration redesign** for a Quarkus + Kotlin + React (Fluent UI v9 + Vite) app called **doc-hyphen**. The project is in **development phase** (no prod, DB is disposable).

### What's already done (backend-only, all compiling green + boot-validated)

All 5 backend plans are **COMPLETE**. No `web-app/` files were touched. Here's what exists with no UI:

1. **Plan 01 — Participant limited access (backend done).**
   `ShareConstraints` typed parser (`can_download`, `can_reshare`, `watermark`, `max_views`, `require_mfa`) with `adjustCapabilities` in `DefaultAuthorizationService`. `Decision` carries `ShareObligations(watermark, maxViews)`. Download gate in `SharingSessionDocumentService`. Write-time normalization in `SessionAccessManagementService.grantAccess`. Deferred: real `max_views` counting (needs `share_view` table); UI.

2. **Plan 02 — Personal / self-service groups (backend done).**
   `PrincipalGroupService` extended: `createPersonalGroup`, `renamePersonalGroup`, `addPersonalMembers`, `removePersonalMember`, `deletePersonalGroup` (scope=PERSONAL, ownerAppUserId=owner). Reciprocity-gated via `UserContactRepository.isMutualContact`. New `PersonalGroupResource` at `/me/groups` with full CRUD. Authorization via `ResourceRef.group(id)`. Sharing is scope-agnostic (PERSONAL groups work as recipients). No schema migration needed.

3. **Plan 03 — Group permission enforcement (backend done).**
   Added `ORG_MEMBERSHIP` + `GROUP_MEMBERSHIP` grant bridges in `DefaultAuthorizationService` so `authorize()` resolves org-role and group-role grants. `OrganizationGroupService` create→`requireOrgAdminIn(orgId)`, update→`GROUP_MANAGE_MEMBERS`, delete→`GROUP_DELETE`. Replaces the old org-unscoped, group-role-blind checks.

4. **Plan 04 — Multiple App Admins (backend done, boot-validated).**
   `RoleAssignmentService` (grant/revoke/list, idempotent, last-admin invariant via `LastAppAdminException`). `AppRoleResource` at `/admin/roles/app-admins` (GET/POST/DELETE). Config-gated startup bootstrap (`app.security.app-admin.bootstrap-email`). Capabilities are additive (admin is also a normal user).

5. **Plan 05 — B2C external-customer sharing (backend done, boot-validated).**
   `OrganizationSharingPolicyService.assertCanShareWithUser` now branches by recipient nature: internal=always allowed, B2C (no-org recipient)=allowed by default (`allowExternalCustomerSharing` default `true`, audit-logged), B2B-unpaired=existing pairing gate. Migration `V3__org_settings_external_customer.sql`. Next migration is **V4**.

### What needs to be done now — Plan 06 (Frontend)

**Read `plans/06-frontend-integration.md` for the full plan.** It has 9 workstreams (A–I):

- **A. Workflow approve/decline UI** — `WorkflowDecisionResource` exists, needs inbox/tasks UI + realtime
- **B. Live session access-management panel** — endpoints exist (`GET/POST /sharing-sessions/{id}/access`, `PATCH/DELETE .../access/{shareId}`), needs UI in `SessionEditDialog.tsx`
- **C. Participant limited-access controls** — constraint toggles + badges + viewer watermark/download gate
- **D. Personal / self-service groups** — "My groups" area + recipient picker integration
- **E. Org-group permission gating** — role dropdown + capability-driven control disabling
- **F. App-admin management** — admin screen for `/admin/roles/app-admins`
- **G. Org sharing settings split** — two toggles replacing the single opaque one
- **H. Role-dropdown enum alignment** — centralize TS enums matching backend `GroupRole` + session `RoleName`
- **I. Notification rendering** — render `workflow.step_assigned`, `workflow.escalated`, `session.activated`, `session.rejected`

### Key files to read first

**Plans:**
- `plans/06-frontend-integration.md` — the full plan with all workstreams
- `plans/00-INDEX.md` — overall index + conventions

**Frontend landmarks:**
- `web-app/src/sharing-session-initiation/components/session-initiation-recipients-tab/` — recipient selection
- `web-app/src/sharing-sessions/components/session-edit-dialog/SessionEditDialog.tsx` — edit active sessions
- `web-app/src/settings/organization-groups-tab/OrganizationGroupsTab.tsx` + `edit-group-dialog/EditGroupDialog.tsx` — org groups
- `web-app/src/settings/organization-people-tab/app-user-edit-dialog/EditUserDialog.tsx` — user management
- `web-app/src/components/main-menu/notification/NotificationList.tsx` / `NotificationListItem.tsx` — notifications
- `web-app/src/services/organizationSharingSession.ts`, `organizationApi.ts`, `appUserApi.ts` — API services
- `web-app/src/context/NotificationContext.tsx` — notification context
- `web-app/src/services/useRealtime.tsx` — realtime/SSE wiring

**Backend endpoints to wire (already implemented):**
- `resource/WorkflowDecisionResource.kt` — workflow decisions
- `resource/SharingSessionResource.kt` — session access management (`/sharing-sessions/{id}/access`)
- `resource/PersonalGroupResource.kt` — `/me/groups` CRUD
- `resource/AppRoleResource.kt` — `/admin/roles/app-admins`
- `resource/OrganizationGroupResource.kt` — org groups (existing, now authz-gated)

### Conventions

- **Package root:** `com.docuhyphen.app.api`
- **Stack:** Quarkus 3.17.5 / Kotlin / Hibernate ORM `validate` / Flyway / PostgreSQL 15
- **Frontend:** React + Fluent UI v9 + react-pdf + Vite
- **Authz identity rule:** the acting principal always comes from the authenticated session — never from the request body
- **Do not write tests** (standing instruction)
- **New migrations** start at **V4** (V1/V2 = baseline + seed, V3 = Plan 05)

### Instructions

Please start by reading `plans/06-frontend-integration.md` and the key frontend files listed above to understand the current UI structure. Then implement the workstreams in order: A and B first (most impactful, backends fully ready), then C–G, then H+I cross-cutting. For each workstream, create the API service module, TS types, and UI components needed. Reuse existing patterns from the codebase (axios/fetch wrappers, Fluent UI component patterns, etc.).

