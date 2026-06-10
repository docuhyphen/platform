# Sharing / Collaboration Redesign, Memory Log

## Consolidated Status (2026-06-01)

**All backend plans (01–05) are DONE.** Build compiles green (`./mvnw -o -q compile`).
Boot-validated from empty DB (Flyway V1→V3, Hibernate validate). Next migration is **V4**.

| Plan | Status | Key deliverables |
|------|--------|-----------------|
| 01 – Participant limited access | ✅ backend | `ShareConstraints` parser + `adjustCapabilities`; obligations on `Decision`; download gate |
| 02 – Personal groups | ✅ backend | `PrincipalGroupService` PERSONAL CRUD; `PersonalGroupResource` `/me/groups`; reciprocity guard |
| 03 – Group permission enforcement | ✅ backend | ORG_MEMBERSHIP + GROUP_MEMBERSHIP grant bridges in `DefaultAuthorizationService`; `OrganizationGroupService` authz |
| 04 – App admin provisioning | ✅ backend | `RoleAssignmentService`; `AppRoleResource` `/admin/roles/app-admins`; startup bootstrap |
| 05 – B2C external-customer sharing | ✅ backend+boot | `OrganizationSharingPolicyService` recipient-nature branching; `V3__org_settings_external_customer.sql` |
| 06 – Frontend integration | 🔴 NOT STARTED | All 9 workstreams (A–I) pending |

**Only Plan 06 (frontend) remains.**

---

## DONE, Plan 02: Personal / self-service groups (2026-06-01)

**What shipped:**
- Extended `PrincipalGroupService` with PERSONAL-scope group CRUD:
  `createPersonalGroup`, `renamePersonalGroup`, `addPersonalMembers`,
  `removePersonalMember`, `deletePersonalGroup`.
- Owner inserted as `PrincipalGroupMember` with `groupRole=OWNER` on create.
  `scope=PERSONAL`, `ownerOrganizationId=null`, `ownerAppUserId=owner`.
- **Reciprocity guard:** Added `UserContactRepository.isMutualContact(a, b)`;
  `addPersonalMembers` requires mutual-contact status for USER principals.
  PARTICIPANT principals bypass the gate. OWNER role cannot be assigned or removed.
- `GroupMemberSpec` generalized: `(principalId, principalKind, groupRole)` with
  backwards-compat `appUserId` getter. OrganizationGroupService call sites updated.
- `PrincipalGroupDto` extended with `description` and `ownerAppUserId` (both optional,
  backwards-compat with ORG-group usage).
- **New resource:** `PersonalGroupResource` at `@Path("me/groups")`:
  POST create, GET list, GET /{id}, PUT /{id}, POST /{id}/members,
  DELETE /{id}/members/{pid}, DELETE /{id}. Actor always from
  `AuthorizationContextFactory.currentPrincipal()`. `authorize()` on every mutation
  via `ResourceRef.group(id)` + `Action.GROUP_MANAGE_MEMBERS` / `GROUP_DELETE`.
- **Authorization verified:** `RoleCapabilities` maps `OWNER` → `GROUP_READ` +
  `GROUP_ADMIN` + `GROUP_DELETE` (and session/document caps). The
  `DefaultAuthorizationService.collectGroupMembershipGrants` bridge resolves
  `GROUP_MEMBERSHIP` grants for `PRINCIPAL_GROUP` resources, so an OWNER passes
  `GROUP_MANAGE_MEMBERS` and `GROUP_DELETE` checks for free.
- **Sharing confirmed scope-agnostic:** `SharingSessionInitiationService` resolves
  GROUP recipients via `principalGroupRepository.findById()` and membership expansion
  in `DefaultAuthorizationService.grantsOn` walks members regardless of scope.
- **No schema migration:** All columns (`scope`, `owner_app_user_id`, etc.) already
  existed in `V1__baseline.sql`. Next migration remains V4.
- **Compiles green:** `./mvnw -o -q compile` → BUILD OK.

**Files changed:**
- `repository/UserContactRepository.kt` — added `isMutualContact`
- `service/organization/PrincipalGroupService.kt` — personal group CRUD + generalized `GroupMemberSpec`
- `service/organization/OrganizationGroupService.kt` — updated `GroupMemberSpec` call sites
- `model/dto/PrincipalGroupDtos.kt` — added `description`, `ownerAppUserId` to `PrincipalGroupDto`
- `resource/PersonalGroupResource.kt` — **new** `/me/groups` resource

**Deferred:**
- UI "My groups" + recipient picker for personal groups → Plan 06.
