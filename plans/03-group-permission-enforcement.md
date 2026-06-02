# Plan 03 — Group permission enforcement (separable, actually checked)

**Severity:** 🟠 (matrix exists, never invoked)
**Depends on:** nothing. **Establishes the actor-identity + `authorize()` pattern the
other plans reuse — do this first.**
**Goal:** make org-group management permissions real and *separable* — managing members
vs. deleting a group vs. managing sessions are distinct capabilities, enforced by
`AuthorizationService`, keyed off the caller's `GroupRole`.

---

## STATUS — BACKEND DONE (2026-06-01), UI PENDING

**Backend complete and compiling green** (`./mvnw -o -q compile` → BUILD OK).

What landed:
- **The membership→grant bridge** (the missing piece that made `authorize()` a no-op for
  group/org resources). `DefaultAuthorizationService.grantsOn` now collects two new grant
  kinds in addition to role-assignments + shares:
  - `Grant.SourceKind.ORG_MEMBERSHIP` — reads the caller's `organization_membership.role_name`
    (org roles live there, **not** in `role_assignment`) and maps it via `RoleCapabilities`.
    The org chosen is the **resource's owning org** when the resource is a group
    (`PrincipalGroup.ownerOrganizationId`), else `context.activeOrgId`. Org roles only yield
    `ORG_*`/`GROUP_*` capabilities, so this never widens SESSION/DOCUMENT authz.
  - `Grant.SourceKind.GROUP_MEMBERSHIP` — reads the caller's `PrincipalGroupMember.groupRole`
    for the target group and maps `OWNER/MANAGER/MEMBER/OBSERVER` via `RoleCapabilities`.
    Bounded to `ResourceType.PRINCIPAL_GROUP` resources only (near-zero blast radius).
  - New helpers `collectOrgMembershipGrants` / `collectGroupMembershipGrants`; both injected
    repos `OrganizationMembershipRepository` + `PrincipalGroupRepository`.
- **`OrganizationGroupService` now authorizes via the unified service:**
  - `addOrganizationGroup` (create): no group exists yet → `requireOrgAdminIn(orgId)`
    (org-scoped admin check; replaces the old `requireOrgAdmin()` which only checked the
    caller's **primary** org — that was the real bug, not "no identity").
  - `updateOrganizationGroup`: `authorizeGroup(Action.GROUP_MANAGE_MEMBERS, gid)`.
  - `deleteOrganizationGroup`: `authorizeGroup(Action.GROUP_DELETE, gid)`.
  - `requireOrgAdmin()` replaced by `requireOrgAdminIn(orgId)` + `authorizeGroup(action, gid)`
    helpers. `adminActionGuardService.enforce(...)` step-up MFA retained; `requestId` tracing
    retained.

**Premise correction (the original §2 below was inaccurate):** the service did *not* lack
actor identity — it used `requireOrgAdmin()` with the authenticated token. The real defects
were (a) the check was **org-unscoped** (primary-org only), (b) it was **group-role-blind**
(a group OWNER/MANAGER who wasn't an org admin couldn't manage their own group), and (c) it
bypassed the unified `AuthorizationService`. All three are now fixed.

**Remaining / deferred:**
- **UI capability-gating** → Plan 06 (role dropdowns, disable controls on missing capability,
  surface 403-as-permission-state).
- **Separability of "manage sessions" vs "manage members"** (§3.2): the four `GroupRole`s
  cannot express "manages sessions but not membership" — MANAGER holds both `GROUP_ADMIN`
  and `SESSION_SHARE`. Documented gap; **not** changed without user sign-off. Minimum-viable
  enforcement (the existing matrix) is in place.
- **Tests deferred** per standing "do not write tests" instruction.
- Minor: the group/org membership grants ignore expiry on org-membership (memberships have no
  expiry today) — fine for now.

---

## 1. What the user wanted

> Org-groups need granular permissions, incl. managing sessions and adding/removing
> users — and these must be **separable**, not all-or-nothing. Teams/Slack-grade.

## 2. Current state (verified)

- Capability matrix exists and is *correct in principle*:
  - `Action.GROUP_MANAGE_MEMBERS` → `Capability.GROUP_ADMIN`
  - `Action.GROUP_DELETE` → `Capability.GROUP_DELETE`
  - `RoleCapabilities`: `MANAGER` holds `GROUP_ADMIN` (+ `SESSION_SHARE`); confirm
    `OWNER` holds `GROUP_ADMIN` **and** `GROUP_DELETE`; `MEMBER`/`OBSERVER` hold
    `GROUP_READ` only.
- **But** `OrganizationGroupResource` (POST/PUT/GET/DELETE `/organizations/{orgId}/
  groups[/{groupId}]`) and `OrganizationGroupService` **never call
  `AuthorizationService`** and pass **no actor identity** — only an
  `AdminApprovalContext(requestId)`. So any caller who reaches the endpoint can mutate
  any group. Permissions are not enforced and not separable in practice.
- `DefaultAuthorizationService.grantsOn` already resolves group-mediated grants from
  `PrincipalGroupMember.groupRole`, so once we *call* `authorize` with
  `ResourceRef.group(id)` the matrix takes effect.

Files:
- `…/resource/OrganizationGroupResource.kt`
- `…/service/organization/OrganizationGroupService.kt`
- `…/service/auth/authz/{Action,Capability,RoleCapabilities,DefaultAuthorizationService}.kt`
- `…/service/auth/authz/AuthorizationContextFactory.kt`

## 3. Design

### 3.1 Thread actor identity into the group service
`OrganizationGroupService` mutating methods (`addOrganizationGroup`,
`updateOrganizationGroup`, `deleteOrganizationGroup`) must authorize **before** mutating:

```kotlin
val principal = authorizationContextFactory.currentPrincipal()
val ctx = authorizationContextFactory.currentContext()
val decision = authorizationService.authorize(
    principal, Action.GROUP_MANAGE_MEMBERS, ResourceRef.group(groupId), ctx)
if (!decision.allowed) throw ForbiddenException(decision.reason)
```
- **Create** is special: there is no group id yet. Authorize on the **org** instead —
  add/confirm `Action.ORG_*` for "create group in org" (e.g. an org-scoped
  `GROUP_MANAGE_MEMBERS` check via `ResourceRef` for the org, or reuse
  `ORG_MEMBER_MANAGE`). Decide and document which capability gates creation.
- **Delete** → `Action.GROUP_DELETE` on `ResourceRef.group(groupId)`.
- **Update / add-remove members** → `Action.GROUP_MANAGE_MEMBERS`.
- Inject `AuthorizationService` + `AuthorizationContextFactory` into the service (or do
  the check in the resource — prefer the **service** so both org and future personal
  paths share enforcement).

### 3.2 Make "manage sessions" separable from "manage members"
The brief calls out *managing sessions* as a distinct group power. Today
`SESSION_SHARE` rides along with `MANAGER`. To make it separable:
- Confirm the `Capability` set distinguishes member-management (`GROUP_ADMIN`) from
  session-management (`SESSION_SHARE`/`SESSION_ADMIN`). It does.
- Ensure the `GroupRole`→capability mapping is the single source of truth and that a
  group can have a member who manages **sessions** but not **membership** (e.g. a role
  that holds `SESSION_SHARE` but not `GROUP_ADMIN`). If the four `GroupRole`s can't
  express that, document the gap; do **not** invent new roles without user sign-off
  (ask via the UI question pattern). Minimum viable: enforce the existing matrix.

### 3.3 Replace `AdminApprovalContext(requestId)`-only flow
The resource currently builds `AdminApprovalContext(requestId = requestId)` and passes
no identity. Keep `requestId` for tracing, but the **authz decision** must come from the
authenticated principal, not the header. Do not remove `AdminApprovalContext` if other
code depends on it — just stop relying on it for authorization.

## 4. UI (detail in Plan 06)

- Group editor (`OrganizationGroupsTab.tsx` / `EditGroupDialog.tsx`): show per-member
  **role** dropdown (OWNER/MANAGER/MEMBER/OBSERVER) and disable member/delete controls
  when the current user lacks the capability (drive off a `403`/capabilities response).
- Surface a clear "you don't have permission" state instead of a generic error.

## 5. Step-by-step

1. Inject `AuthorizationService` + `AuthorizationContextFactory` into
   `OrganizationGroupService`.
2. Add `authorize(...)` guards at the top of create/update/delete (create authorizes on
   the org; update/delete on the group). Throw `ForbiddenException` on deny.
3. Verify/extend `RoleCapabilities` for OWNER (GROUP_ADMIN + GROUP_DELETE) and confirm
   MEMBER/OBSERVER are read-only.
4. Confirm `DefaultAuthorizationService.grantsOn` resolves group-role grants for the
   acting user on the target group (write a unit test: MANAGER can manage members,
   MEMBER cannot, OBSERVER cannot delete).
5. Keep `requestId` tracing; drop authz reliance on `AdminApprovalContext`.
6. UI capability-gating (or hand to Plan 06).
7. Boot clean (no schema change expected). Append DONE note to memory.

## 6. Acceptance

- A group MANAGER can add/remove members; a MEMBER gets 403; only OWNER can delete.
- Authorization comes from the authenticated principal, never a header/body.
- Existing org-admin flows still pass (org admins should hold the org-level capability
  that satisfies these checks — verify ORG_ADMIN path).
- Boots clean; unit tests green.
