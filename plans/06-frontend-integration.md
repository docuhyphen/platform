# Plan 06 — Frontend integration (the UI front door)

**STATUS:**  COMPLETE (2026-06-01). All 9 workstreams implemented.
This is the **only remaining plan** — now done.

**Severity:**  (zero UI changes shipped; backends are ahead of the UI)
**Depends on:** consumes Plans 01–05 — **all backends now complete.**
**Goal:** give every redesigned capability a real UI in `web-app/` (React + Fluent UI +
react-pdf + Vite).

> **Important context:** the previous work was **backend-only**. No `web-app/` files were
> touched. Several backends have **no front door at all** (workflow approve/decline,
> live access management). This plan closes that gap and adds UI for Plans 01–05.

---

## 1. Frontend layout (verified file landmarks)

- `web-app/src/sharing-session-initiation/components/session-initiation-recipients-tab/`
  — recipient selection during session creation.
- `web-app/src/sharing-sessions/components/session-edit-dialog/SessionEditDialog.tsx`
  — edit an existing (incl. active) session.
- `web-app/src/settings/organization-groups-tab/OrganizationGroupsTab.tsx` +
  `edit-group-dialog/EditGroupDialog.tsx` — org group management.
- `web-app/src/settings/organization-people-tab/app-user-edit-dialog/EditUserDialog.tsx`
  — user management.
- `web-app/src/components/main-menu/notification/NotificationList.tsx` /
  `NotificationListItem.tsx` + `web-app/src/context/NotificationContext.tsx` — notifications.
- `web-app/src/services/organizationSharingSession.ts`, `organizationApi.ts`,
  `appUserApi.ts`, `useRealtime.tsx` — API + realtime wiring.

## 2. Workstreams (ordered by independence)

### A. Workflow approve/decline UI  *(backend already solid — do first)*
The workflow engine + `WorkflowDecisionResource` exist with no UI.
- **API service:** add `workflowApi.ts` — `getMyPendingDecisions()`,
  `recordDecision(workflowInstanceId, stepId, decision: APPROVE|REJECT, comment?)`.
  **Decision identity is server-side** — the body must **not** carry the actor.
- **Inbox/tasks surface:** a "Pending approvals" list (likely in the notification/main-
  menu area). Each item shows subject (session, requesting user, group), Approve / Reject
  buttons, optional comment.
- **Realtime:** subscribe via `useRealtime.tsx` so `workflow.step_assigned` /
  `workflow.escalated` push new items; `session.activated`/`session.rejected` update the
  initiator's view.

### B. Live session access-management panel  *(backend already solid)*
Endpoints exist: `GET/POST /sharing-sessions/{id}/access`,
`PATCH/DELETE /sharing-sessions/{id}/access/{shareId}`; DTO `SessionAccessEntryDto`,
requests `GrantSessionShareRequest` / `UpdateSessionShareRoleRequest`.
- **API service:** extend `organizationSharingSession.ts` — `listAccess(sessionId)`,
  `grantAccess(sessionId, req)`, `changeRole(sessionId, shareId, roleName)`,
  `revokeAccess(sessionId, shareId)`.
- **UI:** in `SessionEditDialog.tsx`, add an **Access** tab/panel listing entries
  (principal, role, source, status, expiry, constraints badges) with: add principal
  (user / email / group / personal group), change role, revoke. Must work on **active**
  sessions, not just at creation.

### C. Participant limited-access controls  *(consumes Plan 01)*
- In the grant/edit access UI (B) and the initiation recipients tab: when the role is
  PARTICIPANT/VIEWER, expose toggles — *Allow download*, *Allow reshare*, *Watermark*,
  *Max views*, *Expires* — serialized to `constraints_json`.
- Render constraint **badges** on access rows.
- **Viewer obligations:** in the react-pdf viewer, overlay a watermark when
  `Decision.obligations.watermark` is set and hide/disable the download button when
  download is denied. (Requires the read/render API to surface obligations — coordinate
  with Plan 01 §3.3.)

### D. Personal / self-service groups  *(consumes Plan 02)*
- New **"My groups"** area (under contacts/people, **not** org settings): list / create /
  rename / delete personal groups; add/remove members from personal contacts only
  (respect reciprocity).
- `meGroupsApi.ts` → `/me/groups` CRUD.
- Recipient picker in `session-initiation-recipients-tab` and `SessionEditDialog`: allow
  selecting a personal group as a recipient.

### E. Org-group permission gating  *(consumes Plan 03)*
- `OrganizationGroupsTab.tsx` / `EditGroupDialog.tsx`: per-member **GroupRole** dropdown
  (OWNER/MANAGER/MEMBER/OBSERVER); disable member-edit/delete controls when the current
  user lacks the capability; show a clear "no permission" state instead of a generic
  error (drive off 403s or a capabilities endpoint).

### F. App-admin management  *(consumes Plan 04)*
- New **App Admins** screen in the admin area: list admins, add by user lookup, revoke
  (with a "can't remove the last admin" guard message on 409/422).
- `appRoleApi.ts` → `/admin/roles/app-admins`.
- Optionally surface an "App Admin" toggle in `EditUserDialog.tsx` for privileged users.

### G. Org sharing settings split  *(consumes Plan 05)*
- Org settings → Sharing: replace the single opaque toggle with two —
  *"Allow sharing with external customers (individuals)"* (default ON) and
  *"Allow sharing with unpaired organizations"* (default OFF).
- Session initiation: external individual recipients show an "external recipient" badge +
  the participant-constraints panel, not a hard error.

### H. Role-dropdown enum alignment  *(cross-cutting)*
Audit every place the UI hardcodes roles. The backend enums are now:
- `GroupRole`: OWNER / MANAGER / MEMBER / OBSERVER
- session share `RoleName`s: EDITOR / REVIEWER / SIGNER / VIEWER / COMMENTER / PARTICIPANT
Centralize these in a single TS enum/constants module and replace any stale strings.

### I. Notification rendering  *(cross-cutting)*
`NotificationList.tsx` / `NotificationListItem.tsx` / `NotificationContext.tsx` must
render the new event types: `workflow.step_assigned`, `workflow.escalated`,
`session.activated`, `session.rejected` — with sensible titles, icons, and click-through
(e.g. step_assigned → the approval inbox item from workstream A).

## 3. Cross-cutting setup

- **Verify the dev API base URL / proxy** in the Vite config and that auth tokens flow on
  the new calls (reuse the existing axios/fetch wrapper used by `organizationApi.ts`).
- **Types:** generate or hand-write TS interfaces mirroring `SessionAccessEntryDto`,
  workflow decision payloads, `ShareConstraints`, personal-group DTOs. Keep them in
  `web-app/src/services/types/` (match existing convention — check first).
- **Realtime:** confirm `useRealtime.tsx`'s channel/topic names match what the backend
  `DeliveryDispatcher` emits.

## 4. Step-by-step

1. Add the new API service modules (`workflowApi`, access methods on
   `organizationSharingSession`, `meGroupsApi`, `appRoleApi`) + shared types + role enum
   module.
2. Workstream A (approval inbox) and B (access panel) first — most impactful, backends ready.
3. Then C–G — all backend plans are landed, so these can proceed in any order.
4. H + I cross-cutting cleanup throughout.
5. Manual smoke test each flow against a locally running backend (empty DB + seeds).
   Use the Vite dev server; verify realtime pushes.

## 5. Acceptance

- A group manager can approve/decline a session-approval request from the UI; the
  initiator sees the result live.
- A session owner can view/add/change-role/revoke access on an **active** session.
- Participant constraints are set in the UI and visibly enforced in the viewer
  (watermark shown, download hidden when denied).
- Personal "My groups" CRUD works for a user with no org; such a group can be a
  recipient.
- Group member controls are disabled without permission.
- App-admin add/revoke works with the last-admin guard surfaced.
- The two org-sharing toggles behave per Plan 05.
- All four new notification types render with click-through.
- No stale role strings remain in the UI.
