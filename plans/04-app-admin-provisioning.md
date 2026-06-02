# Plan 04 — Multiple App Admins (RoleAssignment provisioning)

**Severity:** 🟠 (entity exists, no write path)
**Depends on:** Plan 03's actor-identity pattern.
**Goal:** allow multiple App Admins to be granted/revoked at runtime, who can *also*
act as normal users, with a "never drop below one admin" invariant.

---

## STATUS — BACKEND DONE (2026-06-01), boot-validated (via Plan 05 boot), UI PENDING

**Backend complete, compiles green** (`./mvnw -o -q compile` → EXIT 0). What landed:
- **`RoleAssignmentRepository`** (+finders): `findActiveAppAdmins()`; `findAppRoleForUser(appUserId, roleName)`
  for idempotent reactivation. `countActiveAppAdmins()` already existed (task #19).
- **`exception/Exceptions.kt`**: `LastAppAdminException` (→ 409).
- **`service/auth/RoleAssignmentService.kt`** (NEW, `@ApplicationScoped`):
  - `grantAppRole(targetAppUserId, roleName, actorId)` — asserts role ∈ {APP_ADMIN, APP_AUDITOR,
    APP_SUPPORT}; APP scope ⇒ `scopeId=null`; **idempotent** (reactivates a soft-deleted row,
    else inserts); sets `grantedByAppUserId=actorId`.
  - `revokeAppRole(assignmentId, actorId)` — soft-delete (`isActive=false`); **refuses** to revoke
    the last active APP_ADMIN (`countActiveAppAdmins() <= 1` ⇒ `LastAppAdminException`); idempotent
    on already-revoked.
  - `listAppAdmins()`.
  - `bootstrapFirstAppAdmin(@Observes StartupEvent)` — config-gated first-run bootstrap (see below).
  - Audit via `AuthAuditService.emit` (best-effort, swallowed on failure).
- **`resource/AppRoleResource.kt`** (NEW, `@Path("admin/roles")`): GET/POST/DELETE `/app-admins`.
  Guarded by `userRoleService.isAppAdmin(actor.id)` where actor = `authTokenContext.authToken.appUser`
  (**session, never body**). Maps `LastAppAdminException`→409, `AppUserNotFoundException`→404,
  `IllegalArgumentException`→400, no-auth→401, non-admin→403.
- **DTOs** in `resource/model/RequestsResponses.kt`: `GrantAppAdminRequest{appUserId}`,
  `AppAdminDto{assignmentId, appUserId, email, grantedByAppUserId, grantedAt}`.
- **Bootstrap mechanism (per user decision "DB cleaned as if migrations run for the first time"):**
  config-gated **startup** seed, NOT a `V2__seed.sql` row (so it survives a fresh from-empty boot
  without a hardcoded UUID). New config `app.security.app-admin.bootstrap-email`
  (`${APP_ADMIN_BOOTSTRAP_EMAIL:}`, blank = disabled) on `ConfigurationService`
  (`getBootstrapAppAdminEmail()`). On boot: if `countActiveAppAdmins()==0` **and** the email
  resolves to an existing `AppUser`, grant APP_ADMIN. Idempotent; unmatched email logs a warn and
  skips (user can sign up later and be promoted on the next boot).

**"Also a normal user":** no special handling — capabilities are a union across role-assignments +
memberships + shares (verified in `DefaultAuthorizationService.grantsOn`). An APP_ADMIN row only
adds APP-wide caps.

**Authz note:** used `userRoleService.isAppAdmin` (reads the same APP-scope `role_assignment` table
`authorize()` would) rather than `authorize(APP_ADMINISTRATE, ResourceRef)` — `APP_ADMINISTRATE`
has no natural resource and `ResourceRef` is non-null. This is the honest representation for an
app-scope action and matches the existing pattern in `OrganizationMemberCapacityResource`.

**Remaining / deferred:**
- ~~NOT boot/augmentation-validated~~ — **RESOLVED (2026-06-01).** Plan 05's from-empty boot
  exercised ArC augmentation and ran `bootstrapFirstAppAdmin(@Observes StartupEvent)`, which calls
  `configurationService.getBootstrapAppAdminEmail()` → `ConfigurationService` (with its new
  `@ConfigProperty`) was generated + instantiated at startup with no error. The documented ArC
  64KB-codegen risk did **not** materialize; the full app started clean with this code present.
- **UI** → Plan 06 (App Admins management screen; "App Admin" toggle on `EditUserDialog.tsx`).
- **Tests deferred** per standing "do not write tests" instruction.

---

## 1. What the user wanted

> Multiple App Admins, who can also act as normal users.

## 2. Current state (verified)

- `RoleAssignment` entity exists: `appUserId?` / `serviceAccountId?` (exactly one),
  `roleName: String`, `scopeType: RoleScopeType` (APP/ORG/PRINCIPAL_GROUP/RESOURCE),
  `scopeId?`, `grantedByAppUserId?`, `grantedAt`, `expiresAt?`, `isActive`. DDL rule:
  **APP scope ⇔ `scope_id` NULL**.
- `RoleName.APP_ADMIN` exists; `RoleCapabilities` maps it to APP-wide capabilities
  (`APP_ADMIN`, `APP_AUDIT_READ`, `APP_SUPPORT`).
- `DefaultAuthorizationService.grantsOn` already reads APP-scope `RoleAssignment`s for a
  user — so granting an APP_ADMIN assignment *immediately* takes effect.
- **Missing:** no service/endpoint to create/revoke a `RoleAssignment`, and no invariant
  preventing removal of the last App Admin. ("Admin invariants" task #19 was completed —
  **verify what it actually covers**; it may already hold the org-admin invariant. Check
  before duplicating.)

Files:
- `…/model/entity/RoleAssignment.kt`
- `…/repository/RoleAssignmentRepository.kt` (verify finders exist)
- `…/service/auth/authz/{RoleCapabilities,DefaultAuthorizationService,Action}.kt`
- Whatever service task #19 added for "admin invariants" (grep for it before writing).

## 3. Design

### 3.1 Service: `RoleAssignmentService`
New `…/service/auth/RoleAssignmentService.kt`:

```kotlin
fun grantAppRole(targetAppUserId: UUID, roleName: RoleName, actorId: UUID): RoleAssignment
fun revokeAppRole(assignmentId: UUID, actorId: UUID)
fun listAppAdmins(): List<RoleAssignment>      // active, APP scope, APP_ADMIN
```
- `grantAppRole`: assert `roleName` is an APP-scope role; enforce APP scope ⇒
  `scopeId = null`; idempotent (reactivate if a soft-deleted assignment exists);
  set `grantedByAppUserId = actorId`.
- `revokeAppRole`: soft-delete (`isActive = false`).
- **Invariant:** `revokeAppRole`/expiry must refuse if it would leave **zero** active
  APP_ADMIN assignments → throw a domain exception (`LastAppAdminException`). Centralize
  the count check (`countActiveAppAdmins() >= 1` after the change). If task #19 already
  has an org-level invariant helper, mirror its style.

### 3.2 Authorization
Only an existing App Admin may grant/revoke app roles:
- `Action.APP_ADMINISTRATE` (→ `Capability.APP_ADMIN`) on an APP-scoped `ResourceRef`
  (or a dedicated no-resource check — confirm how APP-scope actions are represented;
  `DefaultAuthorizationService` resolves APP-scope role assignments regardless of
  `ResourceRef`).
- Actor from `currentPrincipal()`; never from the body.

### 3.3 Endpoint
New `…/resource/AppRoleResource.kt`, `@Path("admin/roles")` (or extend an existing admin
resource):

| Method | Path | Authz |
|--------|------|-------|
| GET    | `/admin/roles/app-admins` | `APP_ADMINISTRATE` |
| POST   | `/admin/roles/app-admins` `{appUserId}` | `APP_ADMINISTRATE` |
| DELETE | `/admin/roles/app-admins/{assignmentId}` | `APP_ADMINISTRATE` |

Return `409`/`422` on `LastAppAdminException`, `403` on authz deny.

### 3.4 "Also a normal user"
No special handling needed — capabilities are a **union** across all of a user's role
assignments + memberships + shares. An APP_ADMIN row simply adds capabilities; the user
keeps their org/group/personal grants. Add a test asserting an App Admin still resolves
their normal END_USER/org grants.

### 3.5 Bootstrapping the first admin
From an empty DB there are zero admins, so the endpoint is unreachable (its own authz
denies). Provide a bootstrap path:
- Either a seed row in `V2__seed.sql` granting APP_ADMIN to a configured bootstrap user,
  **or** a one-shot `ConfigurationService`-gated bootstrap (env: first user / a
  configured email becomes APP_ADMIN on startup if no admin exists).
- **Decision for the user:** which bootstrap mechanism? Seed-by-config is cleanest for
  dev. Confirm before implementing.

## 4. UI (detail in Plan 06)

- An **App Admins** management screen (admin area): list current admins, add by user
  lookup, revoke (with a guard message when it's the last admin).
- `EditUserDialog.tsx` could surface an "App Admin" toggle for users with the capability.

## 5. Step-by-step

1. Grep for the task-#19 admin-invariant helper; reuse it. Confirm
   `RoleAssignmentRepository` finders (`findActiveAppAdmins`, `countActiveAppAdmins`,
   `findActive(appUserId, roleName, scopeType, scopeId)`); add if missing.
2. `RoleAssignmentService` (grant/revoke/list + last-admin invariant).
3. `AppRoleResource` with `APP_ADMINISTRATE` authz + actor from session.
4. Bootstrap mechanism (seed-by-config or startup) — per user decision.
5. Tests: grant→capability takes effect; revoke-last-admin rejected; admin still has
   normal grants.
6. Boot clean (only schema change is a possible seed row). Append DONE note to memory.

## 6. Acceptance

- An App Admin can promote another user to App Admin; the new admin immediately passes
  `APP_ADMINISTRATE` checks.
- Revoking the last App Admin is refused (`LastAppAdminException` → 409/422).
- An App Admin retains all normal-user grants.
- A non-admin gets 403 on every `/admin/roles` route.
- Boots clean.
