# Plan 02 — Personal / self-service groups

**STATUS:** ✅ **BACKEND DONE** (2026-06-01). Compiles green (`./mvnw -o -q compile`).
No schema migration needed (all columns pre-existed in V1 baseline).

**Severity:** 🔴 → ✅bk (no path at all → backend done)
**Depends on:** Plan 03 (reuse its actor-identity + `authorize()` pattern). Independent
otherwise.
**Goal:** let an end user create and manage their **own** groups (PERSONAL scope) —
Teams/Slack/Google-Contacts style — and share to them, without an org admin.

---

## 1. What the user wanted

> Teams/Slack/M365/Google-Docs-grade flexibility, **including users creating and
> managing their own groups.** A user→user world and small B2C must work without an org.

## 2. Current state (verified)

- `PrincipalGroupScope` enum has **ORG / PERSONAL / SHARED_PROJECT** — the model already
  anticipates this.
- `PrincipalGroup` carries `ownerOrganizationId?`, `ownerAppUserId?`, `parentGroupId?`,
  `scope` (default ORG).
- `PrincipalGroupService.upsertOrgGroup` **hardcodes** `scope = ORG` and
  `ownerAppUserId = null`. `syncMembers` exists and is scope-agnostic.
- The **only** group API is `OrganizationGroupResource` (`@Path("organizations")`,
  org-scoped, admin-flavored). There is **no** user-facing group endpoint and **no**
  PERSONAL creation method.
- `GroupRole`: OWNER/MANAGER/MEMBER/OBSERVER. `PrincipalGroupMember` supports USER and
  PARTICIPANT kinds.

Files:
- `…/service/organization/PrincipalGroupService.kt`
- `…/model/entity/PrincipalGroup.kt`, `…/PrincipalGroupMember.kt`
- `…/repository/PrincipalGroupRepository.kt`, `…/PrincipalGroupMemberRepository.kt`
- `…/resource/OrganizationGroupResource.kt` (the org-only analog to mirror)

## 3. Design

### 3.1 Service: PERSONAL group creation + ownership
Add to `PrincipalGroupService` (or a new `PersonalGroupService` that delegates to it —
prefer extending the existing one to keep `syncMembers` shared):

```kotlin
fun createPersonalGroup(
    ownerAppUserId: UUID,
    name: String,
    description: String? = null,
): PrincipalGroup   // scope = PERSONAL, ownerOrganizationId = null, ownerAppUserId = owner

fun renamePersonalGroup(groupId: UUID, name: String, description: String?)
fun addPersonalMembers(groupId: UUID, members: List<GroupMemberSpec>, actorId: UUID)
fun removePersonalMember(groupId: UUID, principalKind: PrincipalKind, principalId: UUID)
fun deletePersonalGroup(groupId: UUID)
```
On create, also insert the owner as a `PrincipalGroupMember` with `groupRole = OWNER`.

**Reciprocity / directory rule:** personal-group membership must respect the existing
personal-contacts model (per-user, reciprocity-gated — see `project_personal_contacts_model`
in memory). A user may only add another user to a personal group if they are already a
mutual contact (or the addee is an EMAIL/PARTICIPANT principal). Reuse whatever guard the
contacts feature exposes; do **not** open a global directory.

### 3.2 Authorization
Personal groups are authorized by **ownership/role on the group**, not org role:
- Use `ResourceRef.group(groupId)` + `Action.GROUP_MANAGE_MEMBERS` / `GROUP_DELETE`.
- `DefaultAuthorizationService.grantsOn` already resolves group-role-based grants — but
  confirm it grants `GROUP_ADMIN`/`GROUP_DELETE` to the **OWNER** `GroupRole` for a
  PERSONAL group. If `RoleCapabilities` maps `OWNER`→those caps, it works for free.
  Verify and, if missing, add OWNER caps. (This overlaps Plan 03; coordinate.)

### 3.3 New user-facing resource
Create `…/resource/PersonalGroupResource.kt`, `@Path("me/groups")` (or `users/{userId}/
groups` — prefer `me/groups` so the owner is always the authenticated principal, removing
an IDOR surface):

| Method | Path | Action authorized |
|--------|------|-------------------|
| POST   | `/me/groups` | none (any authenticated user may create their own) |
| GET    | `/me/groups` | list groups owned by / containing the caller |
| GET    | `/me/groups/{groupId}` | `GROUP_READ` on group |
| PUT    | `/me/groups/{groupId}` | `GROUP_MANAGE_MEMBERS` on group |
| POST   | `/me/groups/{groupId}/members` | `GROUP_MANAGE_MEMBERS` |
| DELETE | `/me/groups/{groupId}/members/{principalId}` | `GROUP_MANAGE_MEMBERS` |
| DELETE | `/me/groups/{groupId}` | `GROUP_DELETE` |

Actor = `AuthorizationContextFactory.currentPrincipal()`. Never trust an owner id from
the body.

### 3.4 Sharing to a personal group
`SharingSessionInitiationService` should already accept a `PRINCIPAL_GROUP` recipient.
Confirm it resolves PERSONAL-scope groups the same as ORG groups (membership expansion in
`DefaultAuthorizationService.grantsOn` is scope-agnostic). Add a test for share→personal
group.

### 3.5 DTOs / views
Reuse / add `PrincipalGroupDto` with `scope`, `ownerAppUserId`, members. Add a repository
finder `findByOwnerAppUserId(userId)` and `findGroupsContaining(principalKind, principalId)`.

## 4. UI (detail in Plan 06)

- New **"My groups"** area (likely under the contacts/people area, not org settings):
  list/create/rename/delete personal groups, add/remove members from personal contacts.
- Recipient picker in session initiation: allow selecting a personal group as recipient.

## 5. Step-by-step

1. Add PERSONAL methods to `PrincipalGroupService` (+ owner-as-OWNER insert,
   reciprocity guard).
2. Verify/extend OWNER `GroupRole` capability mapping (coordinate with Plan 03).
3. Repository finders: `findByOwnerAppUserId`, `findGroupsContaining`.
4. New `PersonalGroupResource` (`/me/groups`) with `authorize()` on every mutation.
5. Confirm `SharingSessionInitiationService` resolves PERSONAL recipients; add test.
6. DTOs + transformer.
7. UI "My groups" + recipient picker (or hand to Plan 06).
8. No schema change expected (columns already exist) — but if a finder needs an index,
   add `V4__…` (V3 is taken by Plan 05) and validate on throwaway DB. Append DONE note to memory.

## 6. Acceptance

- An authenticated user with no org can `POST /me/groups`, add a mutual contact, and
  share a session to that group; members get access.
- A user cannot manage a personal group they do not own (403 via `authorize`).
- Adding a non-contact user is rejected by the reciprocity guard.
- Boots clean; org-group flows unaffected.
