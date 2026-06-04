# Plan 07 — Frontend integration gaps (the punch list)

**STATUS:** ✅ **COMPLETE (closed 2026-06-03 evening).** All nine gaps landed.
Re-audit log + per-gap notes below preserved for history.
**Depends on:** Plan 06 spec (don't re-read the whole thing, only §5 acceptance criteria).
**Goal:** close the concrete gaps a 2026-06-02 audit found between the Plan 06 spec and
what's actually on disk.

> **Result.** Plans 01–07 are now functionally complete end-to-end. The unified
> sharing/collaboration model is fully wired from `AuthorizationService` through the
> React UI: viewer obligations applied (watermark + download gate), recipient
> constraints settable at initiation, personal groups acceptable as recipients,
> external recipients soft-handled with a badge, app-admin role manageable through a
> global user search, capability gating on group edit, friendly last-admin guard,
> pending-decision inbox survives page refresh, and the popover is visible to every
> signed-in user. `npx tsc --noEmit` is green in `web-app/`.

## Final close-out (2026-06-03 evening)

- **G1 — DONE.** Backend: `SharingSessionInitiationDto` now carries
  `recipientRoleName` + `recipientConstraintsJson`; `SharingSessionInitiationService`
  honours both with explicit-wins merge over the legacy `allowDocument*` derivation.
  Frontend: `useSharingSessionInitiatingState` now holds `recipientRole` +
  `recipientConstraints`; the recipients tab renders a role Dropdown plus
  `<ShareConstraintToggles>` when the role is in `CONSTRAINED_ROLES`; the request
  serializes `recipientRoleName` + `recipientConstraintsJson`.
- **G3b — DONE.** Backend: `GET /admin/roles/app-admin-candidates`
  (`AppRoleResource.searchAppAdminCandidates`) + `AppUserSearchResultDto` +
  `AppUserRepository.searchActiveUsers`. Frontend: `AppAdminsTab` rewired to call
  `searchAppAdminCandidates` with a 250ms debounce; orphaned `orgUsers` /
  `fetchMyOrganizationUsers` removed.
- **G5 — DONE.** Recipients tab now shows an **External recipient** `Badge` +
  explanatory copy when the user enters a fresh email in PEOPLE/EMAIL mode without
  picking a known app user. Hard validation untouched for org/group modes.
- **G6 — DONE.** Backend: `SharingSessionDetailedDto` exposes `watermark`,
  `maxViews`, `requireMfa`; `SharingSessionResource.enrichSessionWithPermissions`
  parses the constraint keys. Frontend: `models.tsx` mirrors those fields;
  `SessionDocumentPreviewer` renders a translucent rotated watermark overlay when
  `session.watermark`, and hides the "Download original" button (showing
  "Download is disabled for this session." instead) when
  `session.allowDocumentDownload === false`.
- **G7 — DONE (backend + frontend).** `WorkflowEngineService.listPendingForUser` +
  `GET /workflows/steps/pending` + `PendingWorkflowStepDto` on the backend;
  `getMyPendingDecisions()` called on `PendingApprovals` mount.
- **G8 — DONE.** `MainMenu.tsx` no longer gates `<PendingApprovals/>` on
  `verificationComplete && isActive`. Backend returns `[]` when there's nothing to
  show, so the popover stays empty for users without assignments.
- **G9 — DONE.** Removed the unused `OrganizationSettingsV2Dto` interface and the
  dead `WORKFLOW_STEP_ASSIGNED` / `WORKFLOW_ESCALATED` / `SESSION_ACTIVATED` /
  `SESSION_REJECTED` members of `RealtimeMessageType` (those events only ever
  arrive wrapped in a `NOTIFICATION` envelope; consumers key off
  `msg.notification.type`).

### Deferred (not blocking)

- **G4 follow-up** — `EditGroupDialog` still discovers "no permission" on Save
  (catches 403). Cheap upgrade: add `GET /organizations/groups/{id}/capabilities`
  and probe on open. Not blocking.
- **`max_views` enforcement** — backend exposes it via obligations but real per-
  view counting needs a `share_view` table (already deferred in Plan 01).

## Re-audit log (2026-06-03)

- **G2 — DONE.** `MyGroupsRecipients.tsx` (new today) wired into a new `MY_GROUPS`
  recipient mode in `SessionInitiationRecipientsTab.tsx`. Validation branch added in
  `SharingSessionInitiation.tsx:253-260`. Personal-group recipients now functional.
- **G3a — DONE (2026-06-03).** `Settings.tsx` no longer gates the App Admins tab on
  `AppUserRole.ORG_ADMIN`. The tab is rendered for any signed-in user and
  `AppAdminsTab` renders a clean "No access" `MessageBar` when `fetchAppAdmins()`
  returns 403 (new `noAccess` state).
- **G3c — DONE (2026-06-03).** `AppAdminsTab.handleRevoke` now detects a 409 (or a
  message containing "last admin") and surfaces:
  *"You can't revoke the last app administrator. Grant the role to another user first."*
  instead of bubbling the raw backend message.
- **G4 — DONE (2026-06-03).** `EditGroupDialog.handleSave` catches 403 → sets a
  `permissionDenied` flag → renders an info `MessageBar` ("You don't have permission to
  manage this group's members. Ask a group OWNER or MANAGER to make changes.") and
  disables the Save button, the per-row member checkbox, the GroupRole dropdown, and
  the Permissions button. Discovery is still "on-save" since there's no capabilities
  probe endpoint — flagged below as a follow-up.
- **G9 — DONE (2026-06-03, partial).** `PendingApprovals.handleDecision` no longer
  uses `alert()`; errors are kept in a `decisionError` state and rendered inline as a
  `MessageBar` inside the popover. The `OrganizationSettingsV2Dto` and dead
  `RealtimeMessageType` union members still need a sweep.
- **G1 — reclassified 🛑.** Plan 07 originally said "Backend already parses it
  (`SessionAccessManagementService.grantAccess` normalizes write-time)". True at
  grant-time on `/sharing-sessions/{id}/access`, **not true at initiation** —
  `SharingSessionInitiationService.grantRecipientShare` calls `sessionConstraintsJson(dto)`
  which only emits the legacy `can_download` + `allow_document_*` keys, and
  `recipientRoleFor(dto)` only ever returns EDITOR or VIEWER (never PARTICIPANT). To
  expose watermark/MFA/max_views/reshare at initiation, either:
    (a) **Backend:** add `recipientConstraintsJson: String?` (and optionally
        `recipientRoleName: String?`) to `SharingSessionInitiationDto`, thread through
        `grantRecipientShare`. Cleanest, but needs sign-off.
    (b) **Frontend workaround:** after `initiateSharingSession` returns, look up the
        recipient share via `listSessionAccess(sessionId)` and immediately
        `changeSessionAccessRole(sessionId, shareId, { roleName, constraintsJson })`
        with the full constraints. Two round-trips, race-y if a recipient acts before
        the PATCH lands.
  **Stop and ask** which approach to take before writing G1.
- **G8 — regressed.** `MainMenu.tsx:94` is now *more* restrictive than the original
  audit: `appUserPersonOrganization?.verificationComplete && appUserPersonOrganization?.isActive`.
  Plan 07 originally wanted this gate dropped entirely. The tightening may be deliberate
  (B2C users without orgs probably shouldn't see workflow approvals today since the
  only seeded workflow is `session-approval-in-group`), so leaving as-is until the user
  weighs in.

### Still open after 2026-06-03 pass

- **G1** 🛑 — see reclassification above. Awaiting sign-off on (a) vs (b).
- **G3b** 🛑 — `AppAdminsTab` picker still uses `fetchMyOrganizationUsers`. Needs a
  global user search endpoint (e.g. `GET /admin/users/search?q=…`). Awaiting sign-off.
- **G4 follow-up** — discovery of the "no permission" state happens only after the
  user clicks Save. Cheap improvement: add a `GET /organizations/groups/{id}/capabilities`
  endpoint and probe on dialog open. Backend addition; awaiting sign-off.
- **G5** — `SharingSessionInitiation.tsx:217-280` still hard-fails on
  external-individual recipients with no paired org. Backend permits this when
  `allowExternalCustomerSharing = true` (default). UX change is meaningful — awaiting
  sign-off on whether to soften now or after G1.
- **G6** 🛑 — viewer obligations. Awaiting backend confirmation that the
  document-read response carries `Decision.obligations`.
- **G7** 🛑 — `GET /me/workflow-steps?status=PENDING` endpoint missing. Awaiting
  sign-off.
- **G8** — see "regressed" note above. Awaiting decision.
- **G9 follow-up** — sweep the unused `OrganizationSettingsV2Dto` and the dead
  `WORKFLOW_STEP_ASSIGNED` / `SESSION_ACTIVATED` members of the `RealtimeMessageType`
  union in `services/NotificationService.tsx:21-43`. Trivial, can land any time.

---

## 0. Repo state snapshot (so you don't have to re-derive it)

Latest mtime on `web-app/src` before this plan was written: 2026-06-02 23:06 (today).

**Files that exist and work (don't touch unless gap below says so):**
- `web-app/src/services/workflowApi.ts` — `recordWorkflowDecision`
- `web-app/src/services/meGroupsApi.ts` — personal-group CRUD
- `web-app/src/services/appRoleApi.ts` — `/admin/roles/app-admins`
- `web-app/src/services/sharingSessionApi.ts` — access methods (list/grant/changeRole/revoke)
- `web-app/src/services/types/{dtos,roles}.ts` — shared TS types + role enums
- `web-app/src/app/components/main-menu/pending-approvals/PendingApprovals.tsx` — mounted in `MainMenu.tsx:94`
- `web-app/src/app/sharing-sessions/components/session-access-management-dialog/SessionAccessPanel.tsx` — launched from `SessionDetailsHeader.tsx:196,234`
- `web-app/src/app/settings/my-groups-tab/MyGroupsTab.tsx`
- `web-app/src/app/settings/app-admins-tab/AppAdminsTab.tsx`
- `web-app/src/app/settings/organization-tab/OrganizationTab.tsx` — two new switches at lines 188-199
- `web-app/src/app/components/main-menu/notification/NotificationList{,Item}.tsx` — 4 new event types handled
- `web-app/src/app/settings/organization-groups-tab/edit-group-dialog/EditGroupDialog.tsx` — GroupRole dropdown wired

**Plan-wording divergence to be aware of (functional, not bugs):**
- Plan 06 §B says "add an **Access** tab to `SessionEditDialog.tsx`". Actual implementation
  is a *separate* `SessionAccessManagementDialog` reached from `SessionDetailsHeader`.
  `SessionEditDialog.tsx` is still just name+description. **Don't rewrite this** — it
  meets the functional acceptance ("works on active sessions"). Plan 07 leaves it alone.

---

## 1. The gaps (ordered by ratio of impact ÷ cost)

### 🔴 G1. `ShareConstraintToggles` is orphaned — wire it into session initiation
**Acceptance violated:** Plan 06 §C "*In the grant/edit access UI (B) **and the initiation
recipients tab***: when role is PARTICIPANT/VIEWER, expose toggles".
- `app/components/share-constraints/ShareConstraintToggles.tsx` exists and is correct,
  but `grep ShareConstraintToggles` matches only its own file.
- Today constraints can only be set *after* session creation via the access panel.
- **Action:** in `app/sharing-session-initiation/components/session-initiation-recipients-tab/`,
  when the selected participant role is `PARTICIPANT` or `VIEWER`
  (use `CONSTRAINED_ROLES` from `services/types/roles.ts`), render
  `<ShareConstraintToggles>` and serialize the result into the initiation request
  payload as `constraintsJson` on the participant entry. Backend already parses it
  (`SessionAccessManagementService.grantAccess` normalizes write-time).
- **Cost:** ~50 LOC, no backend change.

### 🔴 G2. Recipient picker doesn't accept personal groups
**Acceptance violated:** Plan 06 §D "such a group can be a recipient".
- `grep -E 'personalGroup|PERSONAL|meGroups|fetchPersonalGroups'` in
  `app/sharing-session-initiation/**` → 0 hits. Same in `SessionEditDialog.tsx`.
- `MyGroupsTab` CRUD works; the gap is purely in the picker.
- **Action:**
  1. In the recipients tab's group lookup, also call `fetchPersonalGroups()` from
     `meGroupsApi.ts` and merge results into the group source (tag each with
     `scope: 'PERSONAL'` for the badge).
  2. Submit the chosen group's id as the recipient regardless of scope —
     `SharingSessionInitiationService` group resolution is scope-agnostic.
  3. Mirror the change in `SessionAccessPanel.tsx`'s add-principal lookup.
- **Cost:** ~80 LOC across two files, no backend change.

### 🔴 G3. App-admin tab is gated and scoped wrong
**Acceptance violated:** Plan 06 §F "App-admin add/revoke works with the last-admin guard
surfaced".
- **G3a — visibility axis is wrong.** `Settings.tsx:117` gates `AppAdminsTab` on
  `appUser?.role == ORG_ADMIN`. App-admin and org-admin are independent. An org-admin
  who isn't an app-admin currently sees the tab and 403s on every call; an app-admin
  who isn't an org-admin doesn't see the tab at all.
  **Action:** probe `fetchAppAdmins()` on Settings mount (or expose an "amIAppAdmin"
  flag); show the tab only if the call succeeds. Cheapest: render the tab always for
  signed-in users and let `AppAdminsTab` itself render "you don't have access" on 403
  instead of the error MessageBar.
- **G3b — picker is org-scoped.** `AppAdminsTab.tsx:65,113-121` uses
  `fetchMyOrganizationUsers`. App-admin is a global role; the picker should be able to
  find any active app-user. **Action:** introduce a global user search endpoint *or*
  reuse `personalContactsApi.searchContacts` (broader scope). 🛑 If neither exists at
  global scope, **stop and ask** — backend may need a `GET /admin/users/search?q=…`.
- **G3c — 409 isn't surfaced friendly.** `AppAdminsTab.tsx:101-107` bubbles raw
  `errorMessage`. **Action:** check `err.status === 409` (or
  `err.errorMessage?.includes('last app admin')`) and render
  "You can't remove the last app administrator." instead.
- **Cost:** ~40 LOC for G3a + G3c; G3b depends on whether you can ship without a
  backend endpoint.

### 🟡 G4. No capability gating in EditGroupDialog
**Acceptance violated:** Plan 06 §E "Group member controls are disabled without permission".
- `EditGroupDialog.tsx` shows the role dropdown + member buttons unconditionally; today
  unauthorized actions surface as a raw alert.
- **Action:** wrap the save/remove handlers to catch 403 → set a `permissionDenied`
  state → disable the controls + render a `MessageBar` ("You don't have permission to
  manage this group's members."). Optionally probe once on dialog open with a no-op
  PATCH or a future `GET /organizations/groups/{id}/capabilities` endpoint.
- **Cost:** ~30 LOC.

### 🟡 G5. Soften initiation hard-error into "external recipient" badge
**Acceptance violated:** Plan 06 §G "Session initiation: external individual recipients
show an 'external recipient' badge + the participant-constraints panel, not a hard error."
- `SharingSessionInitiation.tsx:198-260` still hard-fails recipient validation when the
  recipient has no paired org. `grep external` in initiation = 0 hits.
- Backend already permits this case when `OrganizationSettings.allowExternalCustomerSharing
  = true` (default). The UI is just refusing before it ever asks.
- **Action:** remove the "recipient must be paired"-style validation when the recipient
  is an individual (no org). Add a small `<Badge>` next to the recipient input reading
  "External recipient" with a tooltip, and surface the constraints panel from G1 by
  default for this case.
- **Cost:** ~40 LOC, may require an inverse check on the org-org path.

### 🔴 G6. Viewer obligations not wired (watermark + download gate)
**Acceptance violated:** Plan 06 §C "visibly enforced in the viewer (watermark shown,
download hidden when denied)".
- `grep obligations` across `web-app/src` → 0 hits. `SessionDocumentPreviewer.tsx` has
  no watermark overlay and no obligation-driven download hide.
- **Action:**
  1. Confirm the document-read/render API returns `Decision.obligations` in its
     response. Per Plan 01 §3.3 the backend computes it; check
     `SharingSessionDocumentService` or the resource it sits behind. 🛑 If the
     response doesn't include obligations today, **stop and ask** — that's a backend
     DTO change.
  2. Plumb `obligations` through the existing fetch hook into
     `SessionDocumentPreviewer.tsx`.
  3. When `obligations.watermark`, overlay a translucent diagonal text watermark
     (current user email + "CONFIDENTIAL") on top of the react-pdf `<Page>` (CSS
     `position: absolute; inset: 0; pointer-events: none; opacity: 0.15;
     transform: rotate(-30deg);`).
  4. When `obligations.can_download === false` (or however the field is named), hide
     the download button in `SessionDocumentActionsMenu.tsx`.
- **Cost:** ~120 LOC if the backend already returns obligations; backend touch otherwise.

### 🟢 G7. No initial fetch of pending decisions (refresh = empty inbox)
**Acceptance violated:** Plan 06 §A — fresh-page-load shows no items because the only
source is realtime push (which doesn't replay missed events).
- 🛑 **Backend gap.** `WorkflowDecisionResource.kt` only exposes the POST. There is no
  `GET /workflows/steps/pending` (or equivalent on `/me/workflows/...`).
- **Action:** *Don't write the endpoint without confirming.* Ask the user whether to
  add it; the natural shape is `GET /me/workflow-steps?status=PENDING` returning
  `List<PendingWorkflowStep>`. Once added, call it on `PendingApprovals` mount and
  prepend realtime pushes to the result.
- **Cost:** small backend addition + ~15 LOC frontend.

### 🟢 G8. PendingApprovals popover hidden for no-org users
- `MainMenu.tsx:94` — `{appUserPersonOrganization && <PendingApprovals/>}`.
- Architecturally personal groups should support workflow assignments; today the only
  seeded workflow (`session-approval-in-group`) is org-bound, so this is latent.
- **Action:** drop the `appUserPersonOrganization` gate. Cost: 1 LOC.

### 🟢 G9. Cosmetic / hygiene
- `services/types/dtos.ts:144` defines `OrganizationSettingsV2Dto` which is unused;
  `OrganizationTab.tsx` consumes the legacy `OrganizationSettingsDto` from
  `models.tsx`. Either delete V2 or migrate `OrganizationTab` to it.
- `services/NotificationService.tsx:21-43` lists `WORKFLOW_STEP_ASSIGNED`,
  `SESSION_ACTIVATED`, etc. as standalone `RealtimeMessageType` union members; the
  backend only ever wraps these inside `NOTIFICATION`. Either delete the dead members
  or have the backend emit them at the envelope level (frontend currently keys off
  `msg.notification.type`, which is correct).
- `PendingApprovals.tsx:97` uses `alert(...)` for errors — swap for `MessageBar`.

---

## 2. Conventions reminder (same as Plans 00–06)

- **Authz identity rule:** UI never sends "actor" in a body — backend pulls it from
  `AuthorizationContextFactory.currentPrincipal()`. Don't add picker UI that asks
  *whose* decision is being recorded.
- **Stack:** React + Fluent UI v9 + Vite + react-pdf. Match existing folder layout
  (`web-app/src/<feature>/components/...`). Reuse existing hooks
  (`useRealtime.tsx`, `NotificationContext.tsx`).
- **DTO shapes are final on the backend** — consume what's there, don't reshape.
- **No tests required** (project has no test harness).
- **Never run destructive migrations** — UI-only plan. If you think you need a
  migration, **stop and ask** (applies to G6 and G7).
- **Append a DONE note** to `~/.claude/projects/C--Users-Black-IdeaProjects-doc-hyphen/memory/project_sharing_collab_redesign.md`
  as each gap lands.

---

## 3. Suggested sequencing

Independent enough to parallelize, but the cheap UI-only ones should land first to
demonstrate progress and avoid waiting on backend confirms:

1. **G1** (wire `ShareConstraintToggles` into initiation) — 50 LOC, zero risk.
2. **G3a + G3c** (app-admin tab gating + 409 copy) — 40 LOC, fixes a visible bug.
3. **G2** (personal groups in recipient picker) — 80 LOC, unlocks D acceptance.
4. **G4** (capability gating in EditGroupDialog) — 30 LOC.
5. **G5** (soften initiation hard-error) — 40 LOC, depends on G1 for the constraints panel.
6. **G8 + G9** (cosmetic) — trivial.
7. **G6** (viewer obligations) — first verify backend response shape; ask if absent.
8. **G7** (pending-decisions GET endpoint) — ask before touching backend.
9. **G3b** (global user search for app-admin picker) — ask before touching backend.

## 4. Acceptance (delta over Plan 06 §5)

- All toggles in `ShareConstraintToggles` are reachable at session initiation, not
  just from the access panel (G1).
- A personal group can be selected as a session recipient from both the initiation
  flow and an active session's access panel (G2).
- App-admin tab is visible only to current app-admins; revoking the last admin shows
  a tailored copy, not a raw 409 message (G3).
- A non-permitted user opening an org group sees disabled member controls and a
  `MessageBar`, not an alert (G4).
- Initiating a session to an external individual no longer hard-errors; an
  "External recipient" badge appears and constraint toggles are offered (G5).
- A session document viewed with `obligations.watermark` shows the overlay; with
  download denied the download button is gone (G6).
- After a page refresh, an assignee still sees their pending approvals (G7, contingent
  on backend endpoint).
- No dead/orphaned components remain in `web-app/src/app/components/share-constraints`
  or `services/types/dtos.ts` (G9).

