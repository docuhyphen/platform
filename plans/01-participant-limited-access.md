# Plan 01 — Participants with limited access

**Severity:** 🟡 (modeled, barely enforced)
**Depends on:** nothing (pure backend authz). UI part feeds Plan 06.
**Goal:** make a "participant" share actually *limited* — view-only, no download,
watermark, capped views, time-boxed, no reshare — by parsing and enforcing
`constraints_json` in the authorization layer, and surface the limits in the UI.

---

## STATUS — BACKEND DONE (2026-06-01), UI + max_views PENDING

**Backend complete and compiling green.** What landed:
- **`service/auth/authz/ShareConstraints.kt`** (new): typed `{can_download, can_reshare,
  watermark, max_views, require_mfa}` with snake_case `@SerialName`. `parse(json?)` is lenient
  (→ permissive on blank/malformed); `parseStrict` throws; `normalizeForStorage` validates +
  canonicalises (null when blank/permissive); `adjustCapabilities(base)` adds/removes
  `DOCUMENT_DOWNLOAD` from `can_download` and strips `SESSION_SHARE` when `can_reshare=false`;
  `validate()` rejects `max_views <= 0`.
- **`Decision.kt`**: added `abstract val obligations: ShareObligations`; `Allow`/`Deny` carry
  it; new `ShareObligations(watermark, maxViews)`.
- **`DefaultAuthorizationService.kt`**: `Share.toGrant` shapes caps via
  `ShareConstraints.parse(...).adjustCapabilities(...)`; `evaluateShareConstraints` uses typed
  `requireMfa`; `computeObligations` (any watermark wins, smallest maxViews wins) attached to
  `Decision.Allow`.
- **`SessionAccessManagementService.grantAccess`**: `ShareConstraints.normalizeForStorage`
  before persisting.
- **`SharingSessionDocumentService`**: `validateDownloadPermission` (authorize
  `DOCUMENT_DOWNLOAD`) gates `downloadDocument` + `downloadDocumentsAsZip`.

**Deferred:**
- **Real `max_views` enforcement** — needs a `share_view` count table + migration (kept this
  slice migration-free). `max_views` is parsed and surfaced as a non-blocking obligation only.
- **UI** → Plan 06 (constraint editor on the access panel, view-only/watermark badges).
- **Obligations from group-mediated shares** — currently computed from the principal's direct
  active shares only.
- Tests deferred per standing "do not write tests" instruction (`ShareConstraintsTest.kt` was
  created then removed at user request).

---

## 1. What the user wanted

> "Participants with limited access" — recipients added to a session who are **not**
> full collaborators. They should be able to view but be constrained: e.g. no
> download, watermarked, a maximum number of views, an expiry, no resharing.

## 2. Current state (verified)

- `PrincipalKind.PARTICIPANT` exists; `RoleName.PARTICIPANT` →
  `{SESSION_READ, DOCUMENT_READ}` in `RoleCapabilities`.
- `Share` carries `constraintsJson: String?` and `expiresAt`.
- `DefaultAuthorizationService.evaluateShareConstraints(...)` **only** enforces:
  - share status (active),
  - expiry (`expiresAt`),
  - `require_mfa` — via a crude `constraints_json.contains("\"require_mfa\":true")`
    substring check.
- It **explicitly does not** parse `constraints_json` for `watermark`, `max_views`,
  `can_download`, `can_reshare` (a `// deferred to iteration 4` comment). `Share.toGrant`
  passes base capabilities "as-is".
- `RoleCapabilities` VIEWER entry has a comment: *"DOCUMENT_DOWNLOAD is conditional on
  share constraints.can_download — applied dynamically by DefaultAuthorizationService"*
  — that dynamic logic is **not** implemented.

Files:
- `src/main/kotlin/com/docuhyphen/app/api/service/auth/authz/DefaultAuthorizationService.kt`
- `…/authz/Capability.kt`, `…/authz/RoleCapabilities.kt`, `…/authz/Decision.kt`
- `…/model/entity/Share.kt`
- `…/service/sharingsession/SessionAccessManagementService.kt`
- `…/model/dto/AccessDtos.kt` (`SessionAccessEntryDto.constraintsJson`)

## 3. Design

### 3.1 Define a typed constraints model
Create `…/service/auth/authz/ShareConstraints.kt`:

```kotlin
data class ShareConstraints(
    val canDownload: Boolean = true,
    val canReshare: Boolean = false,
    val watermark: Boolean = false,
    val maxViews: Int? = null,           // null = unlimited
    val requireMfa: Boolean = false,
) {
    companion object {
        /** Lenient parse: missing/blank/malformed json -> permissive defaults,
         *  EXCEPT a participant baseline (see applyRoleFloor). */
        fun parse(json: String?): ShareConstraints { /* Jackson readValue, catch -> default */ }
    }
}
```
Use the existing Jackson `ObjectMapper` (inject it; do not hand-roll). Unknown fields
ignored (`@JsonIgnoreProperties(ignoreUnknown = true)`).

### 3.2 Enforce in `DefaultAuthorizationService`
Replace the substring `require_mfa` hack and the "as-is" cap pass-through:

1. In `evaluateShareConstraints` (or a new `applyConstraints(grant, constraints, action,
   context)`), after status/expiry checks:
   - Parse once via `ShareConstraints.parse(share.constraintsJson)`.
   - **Download gate:** if `action.required == DOCUMENT_DOWNLOAD` and `!canDownload`,
     return `Decision.deny("download disabled by share constraints")`.
   - **Reshare gate:** if `action.required == SESSION_SHARE` and `!canReshare`, deny.
   - **MFA gate:** keep, but read from the typed `requireMfa` (drop the substring match).
   - **Watermark / maxViews** do not block authorization; they are *obligations* —
     return them on the `Decision` (see 3.3).
2. **Role floor for PARTICIPANT:** when the grant's role is `PARTICIPANT` (or VIEWER),
   force `canDownload`/`canReshare` defaults to `false` unless the constraints explicitly
   enable them — i.e. participants are deny-by-default for download/reshare. Implement as
   `constraints.applyRoleFloor(roleName)`.

### 3.3 Carry obligations on the Decision
Extend `Decision` (`…/authz/Decision.kt`) with an `obligations` field so callers (PDF
render, download endpoint) can honor watermark / view-count:

```kotlin
data class Decision(
    val allowed: Boolean,
    val reason: String? = null,
    val obligations: Obligations = Obligations(),
)
data class Obligations(
    val watermark: Boolean = false,
    val maxViews: Int? = null,
)
```
Keep existing `allow()`/`deny()` factories; add overloads that attach obligations.
**Audit every current construction site** of `Decision` so the new field has a default
and nothing breaks compilation.

### 3.4 View-count enforcement (max_views)
`maxViews` needs persisted state. Add a lightweight counter:
- New table `share_view` (migration `V3__share_view.sql`): `(id uuid pk, share_id uuid
  fk, viewed_by_app_user_id uuid null, viewed_at timestamptz)`, index on `share_id`.
- New entity `ShareView` + `ShareViewRepository.countForShare(shareId)`.
- On a *document-read/render* authorize for a constrained share: if `maxViews != null`
  and `count >= maxViews`, deny. Otherwise record a `ShareView` row when the view is
  actually served (do this in the render/download path, not inside `authorize`, to keep
  `authorize` side-effect-free).
- **Decision:** confirm with the user whether `max_views` should ship in v1 or be
  deferred — it is the only part needing a new table. If deferred, parse+expose it but
  treat as unlimited.

### 3.5 Validation of constraints at write time
In `SessionAccessManagementService.grantAccess` / `changeRole`, validate the incoming
`constraintsJson` parses to `ShareConstraints` and reject contradictions (e.g.
`role=EDITOR` + `canDownload=false` is allowed; `maxViews <= 0` is rejected). Store the
**normalized** JSON (re-serialize the parsed object) so storage is canonical.

## 4. UI (also in Plan 06, summarized here)

- **Grant/edit access dialog** (`SessionEditDialog.tsx`, recipients tab): when adding a
  participant, expose toggles — *Allow download*, *Allow reshare*, *Watermark*,
  *Max views* (number), *Expires* (date). Serialize to `constraints_json`.
- **Access list rows:** render a compact badge summary of the active constraints from
  `SessionAccessEntryDto.constraintsJson`.
- **Viewer:** when `obligations.watermark` is set, overlay the watermark in the
  react-pdf viewer; hide the download button when download is denied.

## 5. Step-by-step

1. Add `ShareConstraints.kt` (+ `applyRoleFloor`). Unit-test parse of empty/blank/
   malformed/partial JSON.
2. Extend `Decision` with `Obligations`; fix all construction sites; keep defaults.
3. Rewrite the constraints branch of `DefaultAuthorizationService`: download/reshare/mfa
   gates + obligations. Remove the substring `require_mfa` hack.
4. (If in-scope) `V3__share_view.sql` + `ShareView` entity/repo + view-count gate +
   recording hook in the render/download path.
5. Validate constraints at write time in `SessionAccessManagementService`; store
   normalized JSON.
6. UI toggles + badges + viewer obligations (or hand to Plan 06).
7. Validate boot on throwaway DB (only if a migration was added). Append DONE note to
   `project_sharing_collab_redesign.md`.

## 6. Acceptance

- A PARTICIPANT/VIEWER share with `{"canDownload":false}` → `DOCUMENT_DOWNLOAD` is
  denied; `DOCUMENT_READ` allowed.
- `{"canReshare":false}` (default for participants) → `SESSION_SHARE` denied.
- `{"watermark":true}` → `Decision.obligations.watermark == true` on read; viewer
  overlays it.
- `{"maxViews":3}` → 4th view denied (if shipped).
- `require_mfa` still enforced via typed field; no substring matching remains.
- Boots clean; existing authz tests green.
