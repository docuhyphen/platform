# Blueprints Feature — Implementation Plan & Session Context

## What This Is
Implement a "Blueprints" feature for the doc-hyphen platform. Blueprints are saved exchange configurations (documents, permissions, recipient settings, participants) that let users start exchanges quickly without filling out everything from scratch. Entry point: "Start Exchange" → "From Blueprint".

---

## Codebase Location
`C:\Users\Black\IdeaProjects\doc-hyphen`

- Backend: Quarkus/Kotlin JAX-RS, JPA/Hibernate, Flyway migrations
- Frontend: React + TypeScript + Fluent UI v9
- DB migrations: `src/main/resources/db/migration/V<n>__<description>.sql` — **current latest is V5**
- Package root: `com.docuhyphen.app.api`

---

## Pre-Wired Scaffold (Already In Codebase)

These already exist and just need to be connected:

1. **`choosingBlueprint` / `setChoosingBlueprint`** state — in `web-app/src/app/exchange-initiation/hooks/useExchangeInitiatingState.ts` line 14; flows through `ExchangeInitiation.tsx`, `ExchangeInitiationDialogTitleSection.tsx`, `ExchangeInitiationDialogActions.tsx`

2. **Placeholder at `ExchangeInitiation.tsx` line 723**:
   ```tsx
   {choosingBlueprint ? (
       <div>Choosing Blueprint</div>   // ← REPLACE with <BlueprintPicker />
   ) : renderTabs()}
   ```

3. **Placeholder at `ExchangeInitiationDialogTitleSection.tsx` line 83**:
   ```tsx
   {choosingBlueprint && <div>Choosing Blueprint</div>}  // ← REPLACE with heading text
   ```

4. **"From Blueprint" menu item** — already in `ExchangeInitiationDialogTrigger.tsx` (line 58–64) but calls `onRequestingDocumentsChange(false)` instead of `setChoosingBlueprint(true)` — needs a new `onChooseBlueprint` prop.

5. **`TemplatesTab.tsx`** — exists at `web-app/src/app/settings/templates-tab/TemplatesTab.tsx` as a "feature disabled" placeholder. Already registered in `Settings.tsx` as `tabIds.templates` → "Blueprints" tab (visible to all users).

6. **Settings.tsx** already imports `BlueprintsTab` from `TemplatesTab.tsx` and renders it. The tab id is `tabIds.templates` → needs renaming to `tabIds.blueprints`.

---

## Key Existing Patterns To Follow

### WorkflowDefinition → Blueprint (direct analog)
The WorkflowDefinition feature is the exact pattern to mirror. Key files:
- Entity: `src/main/kotlin/com/docuhyphen/app/api/model/entity/WorkflowDefinition.kt`
- Repository: `src/main/kotlin/com/docuhyphen/app/api/repository/WorkflowDefinitionRepository.kt`
- Service: `src/main/kotlin/com/docuhyphen/app/api/service/workflow/WorkflowDefinitionService.kt`
- Resource: `src/main/kotlin/com/docuhyphen/app/api/resource/WorkflowDefinitionResource.kt`
- Frontend service: `web-app/src/services/workflowService.ts`
- Frontend list: `web-app/src/app/settings/workflows-tab/WorkflowsListView.tsx`

WorkflowDefinition has: `scope (APP/ORG)`, `isPublished`, `isActive`, `isDeleted`, `isTemplate`, `generalTags`, `sourceTemplateId`, `organizationId`, `createdByAppUserId`.

**Blueprint adds**: `PERSONAL` scope (for per-user blueprints), `updatedAt`, and `configJson` instead of `triggerEvent`+`stepsJson`+`version`.

### WorkflowDefinitionService pattern
Uses `@ApplicationScoped`, injected `WorkflowDefinitionRepository`, `UserRoleService`, `OrganizationMembershipService`. Methods receive `callerOrgId`, `isAppAdmin`, `isOrgAdmin` resolved at the resource layer.

### WorkflowDefinitionResource pattern
`@Path("/workflows")`, constructor-injects `AuthTokenContext`, `WorkflowDefinitionService`, `UserRoleService`, `OrganizationMembershipService`. Each method: extract actor → check UNAUTHORIZED → resolve org/role context → delegate to service → map exceptions to HTTP codes.

### ExchangeInitiationDialogTrigger wiring
The component uses `React.forwardRef` and accepts an `onRequestingDocumentsChange` prop. Need to add `onChooseBlueprint: () => void` prop and wire the "From Blueprint" MenuItem to call it.

---

## Three Blueprint Categories

| Category | `scope` | Created by | Visible to |
|----------|---------|------------|-----------|
| My Blueprints | `PERSONAL` | Any user | Owner only (NOT org admins) |
| Organization | `ORG` | Org admins | All org members (published+active); org admins see all |
| Platform | `APP` | App admins (`isTemplate=true`) | Everyone |

**Critical rule:** Org admins managing ORG blueprints have ZERO visibility into users' PERSONAL blueprints.

---

## Database — V6__blueprints.sql

```sql
CREATE TABLE blueprint_definition (
    id                     uuid         NOT NULL,
    name                   VARCHAR(255) NOT NULL,
    summary                VARCHAR(512),
    description            VARCHAR(1024),
    scope                  VARCHAR(32)  NOT NULL
        CONSTRAINT ck_blueprint_scope CHECK (scope IN ('APP', 'ORG', 'PERSONAL')),
    organization_id        uuid         REFERENCES organization(id),
    created_by_app_user_id uuid         REFERENCES app_user(id),
    config_json            text         NOT NULL,
    is_active              boolean      NOT NULL DEFAULT true,
    is_published           boolean      NOT NULL DEFAULT false,
    is_deleted             boolean      NOT NULL DEFAULT false,
    is_template            boolean      NOT NULL DEFAULT false,
    source_template_id     uuid         REFERENCES blueprint_definition(id),
    general_tags           text         NOT NULL DEFAULT '[]',
    created_at             TIMESTAMP(6) NOT NULL,
    updated_at             TIMESTAMP(6) NOT NULL,
    CONSTRAINT blueprint_definition_pkey PRIMARY KEY (id)
);
CREATE INDEX ix_blueprint_def_org     ON blueprint_definition (organization_id)        WHERE is_deleted = false;
CREATE INDEX ix_blueprint_def_creator ON blueprint_definition (created_by_app_user_id) WHERE is_deleted = false;
CREATE INDEX ix_blueprint_def_scope   ON blueprint_definition (scope, is_active)       WHERE is_deleted = false;
```

### config_json shape
Everything from `ExchangeInitiationDto` EXCEPT specific recipient identity (`recipientEmail`, `recipientFirstName`, `recipientLastName`, `recipientAppUserId` — chosen at initiation time). Includes recipient *role/constraints/default group* and participants:

```json
{
  "name": "Standard NDA Request",
  "description": "...",
  "initialShareMessage": "Please upload the signed NDA.",
  "requestRecipientSignIn": true,
  "allowDocumentAddition": false,
  "allowDocumentDeletion": false,
  "allowDocumentDownload": false,
  "allowDocumentUpdate": false,
  "allowDocumentUpload": true,
  "allowedDownloadFormats": null,
  "exchangeDocuments": [
    { "title": "Signed NDA", "restrictedType": "PDF", "restrictType": true }
  ],
  "recipientConfiguration": {
    "recipientRoleName": "VIEWER",
    "recipientConstraintsJson": "{\"can_download\":false,\"watermark\":true}",
    "defaultRecipientOrgGroupId": null
  },
  "participants": [
    { "principalId": "<uuid>", "principalKind": "APP_USER", "roleName": "REVIEWER" },
    { "principalId": "<uuid>", "principalKind": "PRINCIPAL_GROUP", "roleName": "VIEWER" }
  ]
}
```

---

## Backend Implementation

### New files to create:

**1. `src/main/kotlin/com/docuhyphen/app/api/model/entity/BlueprintDefinition.kt`**
- `enum class BlueprintScope { APP, ORG, PERSONAL }` — do NOT reuse `WorkflowScope`
- `@Entity @Serializable @Table(name = "blueprint_definition") class BlueprintDefinition`
- Fields: `id`, `name`, `summary`, `description`, `scope: BlueprintScope`, `organizationId`, `createdByAppUserId`, `configJson`, `isActive=true`, `isPublished=false`, `isDeleted=false`, `isTemplate=false`, `sourceTemplateId`, `generalTags="[]"`, `createdAt`, `updatedAt`

**2. `src/main/kotlin/com/docuhyphen/app/api/repository/BlueprintDefinitionRepository.kt`**
- Extends `BaseRepository<BlueprintDefinition>(BlueprintDefinition::class.java)`
- `findAllAccessibleForCaller(callerUserId, callerOrgId, isOrgAdmin, isAppAdmin)`: returns PERSONAL blueprints for `callerUserId` + ORG blueprints for `callerOrgId` (published unless admin) + all APP isTemplate=true blueprints
- `findOrgBlueprints(organizationId)`: all ORG-scoped for that org regardless of isPublished (admin management view)

**3. `src/main/kotlin/com/docuhyphen/app/api/model/dto/BlueprintDtos.kt`**
```kotlin
@Serializable data class BlueprintDocumentConfig(val title: String, val restrictedType: String? = null, val restrictType: Boolean = false)
@Serializable data class BlueprintRecipientConfiguration(val recipientRoleName: String? = null, val recipientConstraintsJson: String? = null, val defaultRecipientOrgGroupId: String? = null)
@Serializable data class BlueprintParticipantConfig(val principalId: String, val principalKind: String, val roleName: String)
@Serializable data class BlueprintConfigJson(val name: String? = null, val description: String? = null, val initialShareMessage: String? = null, val requestRecipientSignIn: Boolean = false, val allowDocumentAddition: Boolean = false, val allowDocumentDeletion: Boolean = false, val allowDocumentDownload: Boolean = false, val allowDocumentUpdate: Boolean = false, val allowDocumentUpload: Boolean = false, val allowedDownloadFormats: List<String>? = null, val exchangeDocuments: List<BlueprintDocumentConfig> = emptyList(), val recipientConfiguration: BlueprintRecipientConfiguration? = null, val participants: List<BlueprintParticipantConfig> = emptyList())
// Response DTOs:
@Serializable data class BlueprintDefinitionSummaryDto(val id: UUID, val name: String, val summary: String?, val scope: String, val organizationId: UUID?, val createdByAppUserId: UUID?, val isActive: Boolean, val isPublished: Boolean, val isTemplate: Boolean, val generalTags: List<String>, val sourceTemplateId: UUID?, val configJson: String, val createdAt: Timestamp, val updatedAt: Timestamp)
// BlueprintDefinitionDto = same (configJson included in summary for picker efficiency)
// Request types:
data class CreateBlueprintRequest(val name: String, val summary: String? = null, val configJson: String, val generalTags: List<String> = emptyList(), val isActive: Boolean = true, val scope: String? = null, val isTemplate: Boolean = false)
data class UpdateBlueprintRequest(val name: String? = null, val summary: String? = null, val configJson: String? = null, val generalTags: List<String>? = null)
data class PatchBlueprintPublishedRequest(val isPublished: Boolean)
data class PatchBlueprintStatusRequest(val isActive: Boolean)
data class CloneBlueprintRequest(val newName: String? = null)
```

**4. `src/main/kotlin/com/docuhyphen/app/api/service/blueprint/BlueprintDefinitionService.kt`**

Access control:
```
checkReadAccess:
  PERSONAL → must be owner or APP_ADMIN (org admins: NO access to other users' personal blueprints)
  ORG      → must be in org; unpublished requires ORG_ADMIN or APP_ADMIN
  APP      → anyone

checkWriteAccess:
  PERSONAL → must be owner
  ORG      → must be ORG_ADMIN in that org, or APP_ADMIN
  APP      → must be APP_ADMIN
```

Scope assignment on create: regular users always get `PERSONAL`; org admins default to `ORG`; app admins can set `APP + isTemplate=true`.

`cloneBlueprint`: new blueprint gets scope `PERSONAL`, `isActive=false`, `sourceTemplateId` set.

Validate `configJson` parses to `BlueprintConfigJson` (throw `IllegalArgumentException` if invalid).

**5. `src/main/kotlin/com/docuhyphen/app/api/resource/BlueprintDefinitionResource.kt`**

`@Path("/blueprints")`. Endpoints:
- `GET /blueprints?scope=&tag=&isTemplate=` — list (any authed user)
- `POST /blueprints` — create (any authed user; scope enforced in service)
- `GET /blueprints/{id}` — full blueprint
- `PUT /blueprints/{id}` — update
- `PATCH /blueprints/{id}/status` — activate/deactivate
- `PATCH /blueprints/{id}/published` — publish/unpublish (ORG_ADMIN or APP_ADMIN)
- `DELETE /blueprints/{id}` — soft delete
- `POST /blueprints/{id}/clone` — clone to PERSONAL scope

---

## Frontend Implementation

### New/modified files:

**6. `web-app/src/app/models/models.tsx`** — append Blueprint types:
```typescript
export type BlueprintScope = 'APP' | 'ORG' | 'PERSONAL';
export interface BlueprintDocumentConfig { title: string; restrictedType?: string; restrictType?: boolean; }
export interface BlueprintRecipientConfiguration { recipientRoleName?: string; recipientConstraintsJson?: string; defaultRecipientOrgGroupId?: string; }
export interface BlueprintParticipantConfig { principalId: string; principalKind: 'APP_USER' | 'PRINCIPAL_GROUP'; roleName: string; }
export interface BlueprintConfig { name?: string; description?: string; initialShareMessage?: string; requestRecipientSignIn?: boolean; allowDocumentAddition?: boolean; allowDocumentDeletion?: boolean; allowDocumentDownload?: boolean; allowDocumentUpdate?: boolean; allowDocumentUpload?: boolean; allowedDownloadFormats?: string[]; exchangeDocuments?: BlueprintDocumentConfig[]; recipientConfiguration?: BlueprintRecipientConfiguration; participants?: BlueprintParticipantConfig[]; }
export interface BlueprintDefinitionSummaryDto { id: string; name: string; summary?: string; scope: BlueprintScope; organizationId?: string; createdByAppUserId?: string; isActive: boolean; isPublished: boolean; isTemplate: boolean; generalTags: string[]; sourceTemplateId?: string; configJson: string; createdAt: string; updatedAt: string; }
export interface BlueprintDefinitionDto extends BlueprintDefinitionSummaryDto { description?: string; }
export interface CreateBlueprintRequest { name: string; summary?: string; configJson: string; generalTags?: string[]; isActive?: boolean; scope?: BlueprintScope; isTemplate?: boolean; }
export interface UpdateBlueprintRequest { name?: string; summary?: string; configJson?: string; generalTags?: string[]; }
```

**7. `web-app/src/services/blueprintService.ts`** — new file mirroring `workflowService.ts`. All calls to `/blueprints/*`.

**8. `ExchangeInitiationDialogTrigger.tsx`** — add `onChooseBlueprint: () => void` prop. Change "From Blueprint" MenuItem onClick from `onRequestingDocumentsChange(false)` to `onChooseBlueprint()`. Wire in `ExchangeInitiation.tsx` with `() => setChoosingBlueprint(true)`.

**9. `web-app/src/app/exchange-initiation/components/blueprint-picker/BlueprintPicker.tsx`** — new component. Props: `onSelect(blueprint: BlueprintDefinitionSummaryDto): void`, `onCancel(): void`. Three-tab layout: My / Organization / Platform, each fetching `GET /blueprints?scope=...`. Cards show name, summary, tags, "Use Blueprint" button.

In `ExchangeInitiation.tsx`:
- Replace `<div>Choosing Blueprint</div>` (line 723) with `<BlueprintPicker onCancel={() => setChoosingBlueprint(false)} onSelect={handleBlueprintSelect} />`
- Add `handleBlueprintSelect` that parses `blueprint.configJson`, applies all fields to form state setters, then calls `setChoosingBlueprint(false)` and `setSelectedTab('recipients-tab')`
- Apply `recipientConfiguration.defaultRecipientOrgGroupId` to pre-select recipient group if present

Replace placeholder at `ExchangeInitiationDialogTitleSection.tsx` line 83 with a proper heading.

**10. `web-app/src/app/exchange-initiation/components/save-blueprint-dialog/SaveBlueprintDialog.tsx`** — new dialog. Fields: name (pre-filled), summary, tags. On confirm: builds `configJson` from current form state, calls `createBlueprint({ scope: 'PERSONAL', ... })`.

In `ExchangeInitiationDialogActions.tsx`: add "Save as Blueprint" button in the `!choosingBlueprint && !exchangeInitiatedSuccessfully` block. Add `onSaveAsBlueprint?: () => void` prop alongside the existing `onInitiateExchange`.

**11. Settings.tsx renames** — in `web-app/src/app/settings/Settings.tsx`:
- `tabIds.templates` → `tabIds.blueprints`
- Tab id string `"TemplatesTab"` → `"BlueprintsTab"`
- Update import path after file rename

Also rename files:
- `TemplatesTab.tsx` → `BlueprintsTab.tsx`
- `TemplatesTabStyles.tsx` → `BlueprintsTabStyles.tsx`

**12. `web-app/src/app/settings/templates-tab/BlueprintsTab.tsx`** — replace placeholder with functional list view (mirror `WorkflowsListView.tsx`):
- Loads `GET /blueprints?scope=PERSONAL`
- Cards: name, summary, tags, Active/Inactive badge
- Three-dot menu: Edit, Activate/Deactivate, Duplicate (clone), Delete
- "Create Blueprint" → opens `BlueprintEditorDialog`
- NO Publish/Unpublish on personal scope

**New: `web-app/src/app/settings/templates-tab/BlueprintEditorDialog.tsx`** — create/edit dialog with sections:
1. Details (name, summary, description, tags)
2. Documents (same as `ExchangeInitiationDocumentsTab`)
3. Recipient & Permissions (recipient role, permission toggles, constraints, optional default recipient group)
4. Participants (add APP_USER or PRINCIPAL_GROUP participants with roles)

**13. `web-app/src/app/settings/organization-blueprints-tab/OrganizationBlueprintsTab.tsx`** — new file. Loads `GET /blueprints?scope=ORG`. Shows all (including unpublished drafts) for org admins. Badges: Draft/Published + Active/Inactive. Three-dot menu: Edit, Publish/Unpublish, Activate/Deactivate, Duplicate, Delete. "New Org Blueprint" → `BlueprintEditorDialog` with `scope='ORG'`. Does NOT surface PERSONAL blueprints.

In `Settings.tsx`: add `tabIds.orgBlueprints`, new Tab + panel inside `canManageOrganization` guard.

---

## Implementation Order

1. `V6__blueprints.sql`
2. `BlueprintDefinition.kt` + `BlueprintScope` enum
3. `BlueprintDefinitionRepository.kt`
4. `BlueprintDtos.kt`
5. `BlueprintDefinitionService.kt`
6. `BlueprintDefinitionResource.kt`
7. `models.tsx` additions
8. `blueprintService.ts`
9. Fix `ExchangeInitiationDialogTrigger.tsx` (`onChooseBlueprint` prop)
10. `BlueprintPicker.tsx` + wire into `ExchangeInitiation.tsx` (replace placeholder + `handleBlueprintSelect`)
11. Fix `ExchangeInitiationDialogTitleSection.tsx` placeholder (line 83)
12. `SaveBlueprintDialog.tsx` + wire into `ExchangeInitiationDialogActions.tsx`
13. Rename `TemplatesTab.tsx` → `BlueprintsTab.tsx`, `TemplatesTabStyles.tsx` → `BlueprintsTabStyles.tsx`
14. Update `Settings.tsx` (tabIds rename, import path)
15. Implement `BlueprintsTab.tsx` (personal list) + `BlueprintEditorDialog.tsx`
16. `OrganizationBlueprintsTab.tsx` + add org tab to `Settings.tsx`

## Verification
- Start exchange → From Blueprint → picker opens with My / Organization / Platform tabs
- Selecting a blueprint populates all form fields, focuses Recipients tab
- "Save as Blueprint" from in-progress form → appears in My Blueprints settings tab
- Org admin creates ORG blueprint with participants and recipient role → visible in Org picker tab after publishing
- Org admin CANNOT see personal blueprints
- Non-admin cannot see unpublished ORG blueprints
- Clone a platform template → appears in My Blueprints, starts inactive

---

## Implementation Status (as of June 2026)

All 18 items from the original plan above are **complete**. The following is the current state of key files as a starting point for new work:

### Key files created / modified
| File | Status |
|------|--------|
| `src/main/resources/db/migration/V6__blueprints.sql` | ✅ Created |
| `src/main/kotlin/.../model/entity/BlueprintDefinition.kt` | ✅ Created — `BlueprintScope { APP, ORG, PERSONAL }` |
| `src/main/kotlin/.../repository/BlueprintDefinitionRepository.kt` | ✅ Created |
| `src/main/kotlin/.../model/dto/BlueprintDtos.kt` | ✅ Created — all request/response DTOs, `@Serializable` on request types added by linter |
| `src/main/kotlin/.../service/blueprint/BlueprintDefinitionService.kt` | ✅ Created |
| `src/main/kotlin/.../resource/BlueprintDefinitionResource.kt` | ✅ Created — `@Path("/blueprints")` |
| `web-app/src/app/models/models.tsx` | ✅ Blueprint types appended at end of file |
| `web-app/src/services/blueprintService.ts` | ✅ Created |
| `web-app/src/app/exchange-initiation/components/exchange-initiation-dialog-trigger/ExchangeInitiationDialogTrigger.tsx` | ✅ `onChooseBlueprint: () => void` prop added; "From Blueprint" calls it |
| `web-app/src/app/exchange-initiation/components/exchange-initiation-dialog-title-section/ExchangeInitiationDialogTitleSection.tsx` | ✅ `onSaveAsBlueprint?` prop added; button rendered top-right (appearance `"outline"`) |
| `web-app/src/app/exchange-initiation/components/exchange-initiation-dialog-actions/ExchangeInitiationDialogActions.tsx` | ✅ "Save as Blueprint" removed from actions (moved to title section) |
| `web-app/src/app/exchange-initiation/components/blueprint-picker/BlueprintPicker.tsx` | ✅ Created — 3 tabs, client-side filter: `isActive && (scope==='PERSONAL' \|\| isPublished)` |
| `web-app/src/app/exchange-initiation/components/save-blueprint-dialog/SaveBlueprintDialog.tsx` | ✅ Created — radio group for admins (My Blueprints vs Organization), publishes immediately on ORG save |
| `web-app/src/app/exchange-initiation/ExchangeInitiation.tsx` | ✅ Wired all above; `handleBlueprintSelect`, `buildBlueprintConfigJson` added |
| `web-app/src/app/settings/templates-tab/TemplatesTab.tsx` | ✅ Functional personal-blueprints list with icons matching workflows menu |
| `web-app/src/app/settings/templates-tab/BlueprintEditorDialog.tsx` | ✅ Created — 3 tabs: Details, Documents (title only), Permissions |
| `web-app/src/app/settings/organization-blueprints-tab/OrganizationBlueprintsTab.tsx` | ✅ Created — org-admin list with Publish/Unpublish, same icon set as workflows |
| `web-app/src/app/settings/Settings.tsx` | ✅ `tabIds.templates` → `tabIds.blueprints`; `orgBlueprints` tab added (org-admin gated) |

---

## Next Feature: Document Properties in Blueprints

### Context & Gap

`ExchangeInitiationDocumentsCard.tsx` renders two document-level properties beyond the title:

1. **Restrict upload type** — a `Switch` (`restrictType: boolean`) + `Dropdown` (`restrictedType: DocumentType | ImageType`). Both are already wired in the exchange initiation form and stored on `ExchangeRequestDocumentRequest`.
2. **Required** — a `Checkbox` rendered at line 83 of `ExchangeInitiationDocumentsCard.tsx`. **It is completely unwired** — no `checked`, no `onChange`, no field in the model.

Currently `BlueprintDocumentConfig` (both backend and frontend) only captures `title`, `restrictType`, and `restrictedType`. Neither the restriction type nor the `required` flag round-trips through blueprints correctly:
- `restrictType`/`restrictedType` are in the config shape but the `BlueprintEditorDialog` document tab has no UI for them (only a plain title input).
- `required` does not exist anywhere in the model yet.

### What needs to be done

#### 1 — Add `required` to `ExchangeRequestDocumentRequest` (frontend model)

**File:** `web-app/src/app/models/models.tsx`

```typescript
export interface ExchangeRequestDocumentRequest
{
    title: string;
    restrictedType?: DocumentType | ImageType;
    type?: DocumentType;
    restrictType?: boolean;
    required?: boolean;   // ← ADD
}
```

#### 2 — Wire the "Required" checkbox in `ExchangeInitiationDocumentsCard.tsx`

**File:** `web-app/src/app/exchange-initiation/components/exchange-initiation-documents-card/ExchangeInitiationDocumentsCard.tsx`

Add `onRequiredChange: (index: number, required: boolean) => void` to `DocumentCardProps`. Wire the existing `<Checkbox label={"Required"}/>`:

```tsx
<Checkbox
    label="Required"
    checked={document.required ?? false}
    onChange={(_, d) => onRequiredChange(index, !!d.checked)}
/>
```

#### 3 — Propagate `onRequiredChange` up the chain

**File:** `web-app/src/app/exchange-initiation/components/exchange-initiation-documents-tab/ExchangeInitiationDocumentsTab.tsx`

Add `onRequiredChange: (index: number, required: boolean) => void` to `ExchangeDocumentsTabProps` and pass it down to each `ExchangeInitiationDocumentsCard`.

**File:** `web-app/src/app/exchange-initiation/ExchangeInitiation.tsx`

In the `<SharingDocumentsTab .../>` render, add:
```tsx
onRequiredChange={(index, value) => {
    setMessageGroupMessages([]);
    handleDocumentChange(documents, setDocuments)(index, 'required', value);
}}
```

#### 4 — Add `required` to `BlueprintDocumentConfig` (frontend)

**File:** `web-app/src/app/models/models.tsx`

```typescript
export interface BlueprintDocumentConfig
{
    title: string;
    restrictedType?: string;
    restrictType?: boolean;
    required?: boolean;   // ← ADD
}
```

#### 5 — Add `required` to backend `BlueprintDocumentConfig`

**File:** `src/main/kotlin/com/docuhyphen/app/api/model/dto/BlueprintDtos.kt`

```kotlin
@Serializable
data class BlueprintDocumentConfig(
    val title: String,
    val restrictedType: String? = null,
    val restrictType: Boolean = false,
    val required: Boolean = false,   // ← ADD
)
```

#### 6 — Update `buildBlueprintConfigJson` in `ExchangeInitiation.tsx`

The existing mapping at the `exchangeDocuments` line already uses the `documents` state array. Once `required` is on `ExchangeRequestDocumentRequest` (step 1) the map just needs to pass it through:

```typescript
exchangeDocuments: documents.map(d => ({
    title: d.title,
    restrictedType: d.restrictedType as string | undefined,
    restrictType: d.restrictType,
    required: d.required,          // ← ADD
})),
```

#### 7 — Update `handleBlueprintSelect` in `ExchangeInitiation.tsx`

The existing document-mapping block needs to restore `required`:

```typescript
const docs: ExchangeRequestDocumentRequest[] = config.exchangeDocuments.map(d => ({
    title: d.title,
    restrictedType: d.restrictedType as any,
    restrictType: d.restrictType ?? false,
    required: d.required ?? false,   // ← ADD
}));
```

#### 8 — Update `BlueprintEditorDialog.tsx` documents tab

**File:** `web-app/src/app/settings/templates-tab/BlueprintEditorDialog.tsx`

The current documents tab in `BlueprintEditorDialog` only captures the title. Extend it to also capture restriction and required.

The `BlueprintDocumentConfig` type has `title`, `restrictType`, `restrictedType`, and (after step 4) `required`. The document list item should replace the plain `<span>{doc.title}</span>` row with a small inline form per document showing:

- Title input (existing)
- "Restrict upload type" Switch + type Dropdown (same pattern as `ExchangeInitiationDocumentsCard`)
- "Required" Checkbox

Import `DocumentType` and `ImageType` from models. Use the same `Dropdown` / `Switch` / `Checkbox` components as the card.

State per document lives in `config.exchangeDocuments` (a `BlueprintDocumentConfig[]`). Use a helper to update a single document field:

```typescript
const updateDoc = (index: number, patch: Partial<BlueprintDocumentConfig>) =>
    setConfig(prev => ({
        ...prev,
        exchangeDocuments: (prev.exchangeDocuments ?? []).map((d, i) =>
            i === index ? {...d, ...patch} : d
        ),
    }));
```

#### 9 — `SaveBlueprintDialog` — no changes needed

`SaveBlueprintDialog` receives a pre-built `configJson` string from `buildBlueprintConfigJson()` (in `ExchangeInitiation.tsx`). Once step 6 is done, the restriction and required fields will be included automatically.

### Implementation order (✅ all complete)

1. ✅ `models.tsx` — add `required` to `ExchangeRequestDocumentRequest` and `BlueprintDocumentConfig`
2. ✅ `BlueprintDtos.kt` — add `required` to `BlueprintDocumentConfig`
3. ✅ `ExchangeInitiationDocumentsCard.tsx` — add `onRequiredChange` prop, wire checkbox
4. ✅ `ExchangeInitiationDocumentsTab.tsx` — propagate `onRequiredChange`
5. ✅ `ExchangeInitiation.tsx` — pass `onRequiredChange`, update `buildBlueprintConfigJson` and `handleBlueprintSelect`
6. ✅ `BlueprintEditorDialog.tsx` — expanded documents tab: editable title, Switch+Dropdown for restriction, Required Checkbox

### Verification
- Add a document in exchange initiation → toggle "Required" → save as blueprint → open blueprint in editor → Required checkbox is checked
- Add a document with "Restrict upload type" enabled and PDF selected → save as blueprint → open blueprint → restriction is preserved
- Use blueprint in picker → exchange initiation documents tab shows correct `required` and `restrictType`/`restrictedType` values
- Blueprint editor: add a document, set restriction and required → save → re-open editor → values persisted correctly
