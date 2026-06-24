# Document Library — Feature Plan

## Session Start Instructions

**Before writing any code, read `AGENTS.md`** in the project root. It contains conventions, code style, and project-specific rules that must be followed throughout implementation.

After completing each phase below, update this plan file:
- Mark the phase as `[DONE]`
- Note any deviations from the original plan
- Write a short "Next" line describing exactly what to do in the next session

---

## Status

- [DONE] Phase 1 — Backend (DB migration + entity + repo + service + resource)
- [DONE] Phase 2 — Frontend Settings tab (models + service + documents-tab components + Settings.tsx)
- [DONE] Phase 3 — Integration (DocumentLibraryPicker + BlueprintEditor update + ExchangeInitiation update + backend file-copy hook)
- [DONE] Phase 4 — Polish and deficiency fixes (6 items, see Phase 4 section below)

**Phases 1-4 all complete 2026-06-24.**

### Deviations from plan
- `DocumentLibraryService.cloneEntry` sets `storagePath = null` and does NOT copy the file (per plan spec "user re-uploads").
- `ExchangeInitiationService` handles library file-copy inline (not via a separate `ExchangeDocumentService` method) to keep the transaction boundary clean.
- The `BlueprintEditorDialog.tsx` "Link Document" picker is shown inline by toggling `linkingDocIndex` state rather than opening a nested Dialog — keeps the existing Dialog open and avoids z-index stacking issues.
- Help docs added as a new `documentLibrarySection` (separate from blueprints section).

### Next
Feature complete. All phases done.

---

## Context

The platform's Exchange model is a bounded document transaction, and Blueprints are reusable templates for those exchanges. Currently, Blueprint document slots are metadata-only (title, type restriction, required flag) — there's no way to pre-attach a physical file. Users also have no place to store reusable standard documents outside of individual exchanges. This feature adds a **Document Library**: a scope-aware repository of physical documents that integrates with Blueprints and Exchange creation.

### What competitors get wrong (and how we fix it)

| Platform | Mistake | Our fix |
|---|---|---|
| **SharePoint** | Folder hierarchy creates "where does this belong?" confusion | Flat library + multi-value tag search, no folders |
| **DocuSign/PandaDoc** | Template library is decoupled from the transaction workflow | Library documents pre-populate Exchange documents at creation time via Blueprints |
| **Box/Dropbox** | Metadata templates exist per folder, not per document type | Tags are per-document, composable with Blueprint tags for cross-discovery |
| **Google Drive** | No publish gate — sharing is binary | ORG-scoped documents require org-admin publish approval before they circulate |

**The core differentiator:** When an Exchange is created from a Blueprint that references a library document, the physical file is copied into the Exchange automatically. Recipients get completed documents, not empty slots. No competitor does this because none has the Blueprint/Exchange bounded-transaction concept.

---

## Scope Pattern

Follow the existing `BlueprintScope` enum exactly: `PERSONAL | ORG | APP`

- **PERSONAL** — private to the creating user
- **ORG** — org-wide; any member can read, ORG_ADMIN manages and publishes
- **APP** — platform-curated templates; readable by all; APP_ADMIN manages; users can clone but not edit in-place

Industry tags on APP-scoped entries (`general_tags` JSONB) enable discovery: LEGAL, HEALTHCARE, FINANCE, REAL_ESTATE, INSURANCE, HR, PROCUREMENT, COMPLIANCE, CONTRACTS, ONBOARDING.

---

## Database — `V14__document_library.sql`

```sql
CREATE TABLE document_library (
    id                      uuid            NOT NULL,
    title                   VARCHAR(255)    NOT NULL,
    description             VARCHAR(1024),
    scope                   VARCHAR(16)     NOT NULL
        CONSTRAINT ck_doc_lib_scope CHECK (scope IN ('APP', 'ORG', 'PERSONAL')),
    organization_id         uuid            REFERENCES organization(id),
    created_by_app_user_id  uuid            REFERENCES app_user(id),
    -- File metadata (null until a file is uploaded)
    document_type           VARCHAR(16)
        CONSTRAINT ck_doc_lib_type CHECK (document_type IN
            ('PDF','DOCX','DOC','XLSX','XLS','PPTX','PPT','PNG','JPG')),
    file_name               VARCHAR(512),
    file_size_bytes         BIGINT,
    storage_path            VARCHAR(1024),  -- key: lib/<id>.<ext>
    content_hash            VARCHAR(128),
    is_published            boolean         NOT NULL DEFAULT false,
    is_active               boolean         NOT NULL DEFAULT true,
    is_deleted              boolean         NOT NULL DEFAULT false,
    source_document_id      uuid            REFERENCES document_library(id),
    general_tags            text            NOT NULL DEFAULT '[]',
    created_at              TIMESTAMP(6)    NOT NULL,
    updated_at              TIMESTAMP(6)    NOT NULL,
    CONSTRAINT document_library_pkey PRIMARY KEY (id)
);

CREATE INDEX ix_doc_lib_org     ON document_library (organization_id)        WHERE is_deleted = false;
CREATE INDEX ix_doc_lib_creator ON document_library (created_by_app_user_id) WHERE is_deleted = false;
CREATE INDEX ix_doc_lib_scope   ON document_library (scope, is_active)       WHERE is_deleted = false;
CREATE INDEX ix_doc_lib_title   ON document_library USING gin (to_tsvector('english', title));
```

**Notes:**
- `storage_path` is nullable — a record can exist as a metadata stub before a file is uploaded (same lifecycle as exchange `Document`)
- Storage key prefix `lib/` avoids collision with exchange document keys (`${document.id}${extension}`)
- `source_document_id` tracks clone genealogy (mirrors `blueprint_definition.source_template_id`)

---

## Backend — Kotlin

### Entity
**`src/main/kotlin/com/docuhyphen/app/api/model/entity/DocumentLibraryEntry.kt`**

Mirrors `BlueprintDefinition.kt`. Reuses `BlueprintScope` enum (identical scopes — no new enum needed). Fields: all DB columns plus `@ManyToOne` to `AppUser` (createdByAppUserId) and `Organization`.

### DTOs
**`src/main/kotlin/com/docuhyphen/app/api/model/dto/DocumentLibraryDtos.kt`**

```
DocumentLibraryEntryDto              // full response (has hasFile: Boolean = storagePath != null)
DocumentLibraryEntrySummaryDto       // list response, omits storagePath/contentHash
CreateDocumentLibraryEntryRequest    // title, description, generalTags, scope
UpdateDocumentLibraryEntryRequest    // title, description, generalTags
CloneDocumentLibraryEntryRequest     // newName: String? = null
PatchDocumentLibraryStatusRequest    // isActive: Boolean
PatchDocumentLibraryPublishedRequest // isPublished: Boolean
```

**Also extend `BlueprintDtos.kt`** — add one nullable field to `BlueprintDocumentConfig` (additive, backward-compatible because `ignoreUnknownKeys = true` is already set in `BlueprintDefinitionService`):
```kotlin
val libraryDocumentId: UUID? = null
```

### Repository
**`src/main/kotlin/com/docuhyphen/app/api/repository/DocumentLibraryRepository.kt`**

Extends `BaseRepository<DocumentLibraryEntry>`. One custom query `findAllAccessibleForCaller` — structural copy of `BlueprintDefinitionRepository.findAllAccessibleForCaller`:
- PERSONAL → owned by `callerUserId`
- ORG → same `callerOrgId` (published-only unless org/app admin)
- APP → `isPublished = true` always visible

### Service
**`src/main/kotlin/com/docuhyphen/app/api/service/documentlibrary/DocumentLibraryService.kt`**

`@ApplicationScoped`. Injected: `DocumentLibraryRepository`, `FileStorageService`.

| Method | Notes |
|---|---|
| `listEntries(callerUserId, callerOrgId, isOrgAdmin, isAppAdmin, scope?, tag?)` | In-memory tag filter (same pattern as `BlueprintDefinitionService`) |
| `createEntry(request, ...)` | Creates metadata stub; no file yet |
| `uploadFile(id, file, extension, ...)` | Stores at `lib/${id}${extension}`; updates `storagePath`, `documentType`, `fileName`, `fileSizeBytes`, `contentHash`, `updatedAt` |
| `downloadFile(id, ...)` | Checks read access; streams via `fileStorageService.downloadDocument` |
| `updateEntry`, `patchStatus`, `patchPublished`, `deleteEntry` | Mirrors blueprint equivalents |
| `cloneEntry(id, ...)` | Copies metadata to new PERSONAL entry; `storagePath = null` (user re-uploads); `sourceDocumentId` set. Does NOT copy the physical file. |
| `resolveLibraryFileForBlueprintDocument(libraryDocumentId, ...)` | Called during Exchange creation; returns `File?` for pre-population |

Access control mirrors blueprint logic exactly: PERSONAL = owner only; ORG = same org (admin for write); APP = read-only for non-admins.

**Exchange creation integration:** Inject `DocumentLibraryService` into `ExchangeDocumentService`. When `ExchangeInitiationService` creates documents from a Blueprint config that has `libraryDocumentId != null`, call `resolveLibraryFileForBlueprintDocument` and upload the returned file against the newly created exchange document.

### REST Resource
**`src/main/kotlin/com/docuhyphen/app/api/resource/DocumentLibraryResource.kt`**

`@Path("/document-library")`. Mirrors `BlueprintDefinitionResource.kt`.

```
GET    /document-library                  listEntries      (?scope, ?tag)
POST   /document-library                  createEntry
GET    /document-library/{id}             getEntry
PUT    /document-library/{id}             updateEntry
PATCH  /document-library/{id}/status      patchStatus
PATCH  /document-library/{id}/published   patchPublished
DELETE /document-library/{id}             deleteEntry
POST   /document-library/{id}/file        uploadFile       (multipart/form-data: file, extension)
GET    /document-library/{id}/file        downloadFile     (application/octet-stream)
POST   /document-library/{id}/clone       cloneEntry
```

`uploadFile` and `downloadFile` follow the identical pattern as `ExchangeDocumentsResource.uploadSessionDocument` / `downloadDocument`.

---

## Frontend — React/TypeScript

### Models
**`web-app/src/app/models/models.tsx`** — add `DocumentLibraryEntryDto`, `CreateDocumentLibraryEntryRequest`, `UpdateDocumentLibraryEntryRequest`. Extend `BlueprintDocumentConfig` with `libraryDocumentId?: string`.

### API Service
**`web-app/src/services/documentLibraryService.ts`** — mirrors `blueprintService.ts`. Exports: `listDocumentLibraryEntries`, `getDocumentLibraryEntry`, `createDocumentLibraryEntry`, `updateDocumentLibraryEntry`, `patchDocumentLibraryEntryStatus`, `patchDocumentLibraryEntryPublished`, `deleteDocumentLibraryEntry`, `uploadDocumentLibraryFile`, `downloadDocumentLibraryFile`, `cloneDocumentLibraryEntry`.

### Settings Tab
**`web-app/src/app/settings/documents-tab/DocumentsTab.tsx`**

Three sub-tabs: My Documents (PERSONAL) / Organization (ORG) / Platform (APP). Same `canCreate`/`canManageItem` gating as blueprints tab. Each entry shows a `documentType` badge (PDF, DOCX, etc.) and an upload or download button depending on `hasFile`.

Sub-components:
- **`DocumentLibraryEntryCard.tsx`** — single entry card with upload/download affordance
- **`DocumentLibraryEditorDialog.tsx`** — edit dialog: title, description, generalTags only
- **`DocumentLibraryUploadDialog.tsx`** — file picker + progress spinner; calls `uploadDocumentLibraryFile`

**`web-app/src/app/settings/Settings.tsx`** changes:
1. Add `documents: "DocumentsTab"` to `tabIds`
2. Add `[tabIds.documents]: "Documents"` to `tabLabels`
3. Add `SettingsDocumentsTabIcon` to `IconBundles.tsx` — use `DocumentBulletListMultipleFilled / DocumentBulletListMultipleRegular` (already in Fluent UI)
4. Add `<Tab id="DocumentsTab" ...>Documents</Tab>` after the Blueprints tab
5. Add `{selectedValue === tabIds.documents && <DocumentsTab/>}` to render block

### Document Library Picker (Reusable)
**`web-app/src/app/exchange-initiation/components/document-library-picker/DocumentLibraryPicker.tsx`**

Mirrors `BlueprintPicker.tsx`. Three-tab layout (My Documents / Organization / Platform). Only shows entries where `hasFile = true && isActive && (scope === 'PERSONAL' || isPublished)`. Props: `onSelect(entry)`, `onCancel()`.

### Blueprint Editor Update
**`web-app/src/app/settings/templates-tab/BlueprintEditorDialog.tsx`**

Each document slot row gets a "Link Document" button. Opens `DocumentLibraryPicker` in a nested Dialog. On select: `updateDoc(index, { libraryDocumentId: entry.id, title: entry.title })`. Shows linked doc name as a read-only badge with an "Unlink" button.

### Exchange Initiation Update
**`web-app/src/app/exchange-initiation/ExchangeInitiation.tsx`** (Documents tab)

Add a "Pick from Library" button alongside "Add Document". Opens `DocumentLibraryPicker`. On select, appends a document slot pre-filled with title and `libraryDocumentId` set.

---

## Delivery Sequencing

> Do NOT create PRs. When each phase is complete, update this plan: mark it `[DONE]`, note any deviations, and write what to do next.

**Phase 1 — Backend:** `[ ]`
1. `V14__document_library.sql`
2. `DocumentLibraryEntry.kt` entity
3. `DocumentLibraryDtos.kt` + extend `BlueprintDtos.kt`
4. `DocumentLibraryRepository.kt`
5. `DocumentLibraryService.kt`
6. `DocumentLibraryResource.kt`

> Phase 1 complete: update Status checklist above, note any deviations, write what Phase 2 should start with.

**Phase 2 — Frontend Settings tab:** `[ ]`
7. Models + `documentLibraryService.ts`
8. `documents-tab/` components
9. `Settings.tsx` tab registration + icon

> Phase 2 complete: update Status checklist above, note any deviations, write what Phase 3 should start with.

**Phase 3 — Integration:** `[ ]`
10. `DocumentLibraryPicker.tsx`
11. `BlueprintEditorDialog.tsx` update (Link Document button)
12. `ExchangeInitiation.tsx` update (Pick from Library)
13. Backend: `ExchangeDocumentService` calls `resolveLibraryFileForBlueprintDocument`

> Phase 3 complete: update Status checklist above, run full verification checklist below, update memory file `project_document_library.md` to reflect feature complete.

---

## What NOT to Build in v1

- **Library document versioning** — defer; adds version-picker UI and "which version does the Blueprint point to?" complexity
- **Full-text search endpoint** — GIN index created now at zero future migration cost; tag + scope filters sufficient for v1
- **Pre-signed S3 URLs** — streaming download through API (same as exchange documents) is sufficient; pre-signing requires IAM scope changes
- **Document approval workflow before publication** — ORG admin publish toggle is sufficient; multi-step review belongs in a future Workflow step type
- **Cross-org library sharing** — out of scope; requires extending the B2B pairing model
- **Live reference (no file copy)** — the v1 model copies the file into the Exchange at creation time; a live link breaks the bounded-transaction semantics of an Exchange

---

## Verification

1. **Migration:** Run `./mvnw quarkus:dev`; confirm `document_library` table exists with correct constraints.
2. **Upload + Download:** POST `/document-library` (create stub) → POST `/document-library/{id}/file` (upload) → GET `/document-library/{id}/file` (download; verify content matches).
3. **Scope access:** As a regular member, confirm ORG entries with `is_published = false` are not returned. As ORG_ADMIN, confirm full list visible.
4. **Blueprint integration:** Create a Blueprint with a document slot linked to a library entry. Initiate an Exchange from that Blueprint. Verify the exchange document has `uploadDate` not null (file was pre-populated).
5. **Settings UI:** Navigate to Settings > Documents. Verify three sub-tabs render. Upload a personal document. Confirm it appears in `DocumentLibraryPicker` during exchange creation.
6. **Clone:** Clone an APP-scoped platform template; confirm `scope = PERSONAL`, `source_document_id` set, `storage_path = null`.

---

## Phase 4 — Polish and Deficiency Fixes

Six deficiencies identified after Phase 3 was completed. Implement in order; each item is self-contained.

> When done, mark `[ ]` items in the Status section as `[DONE]`, add any deviations, and update the memory file `project_document_library.md`.

---

### Item 1 — Delete confirmation dialog (step-up flow)

**File:** `web-app/src/app/settings/documents-tab/DocumentsTab.tsx`

**Problem:** The "Delete" menu item currently calls `deleteDocumentLibraryEntry(entry.id)` directly with no confirmation. This is destructive and inconsistent with other tabs.

**Pattern to follow:** `web-app/src/app/settings/my-groups-tab/MyGroupsTab.tsx`
- State: `const [confirmDeleteGroupId, setConfirmDeleteGroupId] = useState<string | null>(null)`
- On menu item click: `setConfirmDeleteGroupId(entry.id)` (do NOT call delete yet)
- After the card list, render:
  ```tsx
  <Dialog open={confirmDeleteId !== null} modalType="alert">
      <DialogSurface>
          <DialogBody>
              <DialogTitle>Delete document?</DialogTitle>
              <DialogContent>
                  This document will be permanently removed from the library. Any blueprints that
                  reference it will lose the file association but will not be deleted.
              </DialogContent>
              <DialogActions>
                  <DialogTrigger disableButtonEnhancement>
                      <Button
                          id="doc-library-delete-cancel-btn"
                          shape="circular"
                          appearance="secondary"
                          onClick={() => setConfirmDeleteId(null)}
                      >
                          Cancel
                      </Button>
                  </DialogTrigger>
                  <Button
                      id="doc-library-delete-confirm-btn"
                      shape="circular"
                      appearance="primary"
                      onClick={() => { handleDelete(confirmDeleteId!); setConfirmDeleteId(null); }}
                  >
                      Delete
                  </Button>
              </DialogActions>
          </DialogBody>
      </DialogSurface>
  </Dialog>
  ```
- State variable: `const [confirmDeleteId, setConfirmDeleteId] = useState<string | null>(null)` (one for the whole tab, not per card)
- The `handleDelete` function that already exists calls `deleteDocumentLibraryEntry` and refreshes the list

**Imports already in file:** `Dialog`, `DialogSurface`, `DialogBody`, `DialogTitle`, `DialogContent`, `DialogActions`, `DialogTrigger` — verify these are imported; add any missing ones from `@fluentui/react-components`.

---

### Item 2 — Tags as chips (TagGroup pattern)

**File:** `web-app/src/app/settings/documents-tab/DocumentLibraryEditorDialog.tsx`

**Problem:** Tags are currently a plain comma-separated `<Input>` field that produces a raw string. Every other tagging surface in the app uses chips (TagGroup + dismissible Tag).

**Pattern to follow exactly:** `web-app/src/app/settings/communications-tab/CommunicationEditorDialog.tsx` — look for the `tagInput`/`tags`/`addTag`/`TagGroup` pattern. It looks like this:

```tsx
const [tagInput, setTagInput] = useState('');
const [tags, setTags] = useState<string[]>([]);   // initialized from entry.generalTags

const addTag = () => {
    const trimmed = tagInput.trim();
    if (trimmed && !tags.includes(trimmed)) {
        setTags([...tags, trimmed]);
    }
    setTagInput('');
};

// In the form:
<Label htmlFor="doc-lib-tag-input">Tags</Label>
<div style={{ display: 'flex', gap: '4px' }}>
    <Input
        id="doc-lib-tag-input"
        value={tagInput}
        onChange={(_, d) => setTagInput(d.value)}
        onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); addTag(); } }}
        placeholder="Add tag..."
    />
    <Button id="doc-lib-add-tag-btn" shape="circular" onClick={addTag}>Add</Button>
</div>
{tags.length > 0 && (
    <TagGroup
        onDismiss={(_, { value }) => setTags(tags.filter(t => t !== value))}
    >
        {tags.map(tag => (
            <Tag key={tag} value={tag} dismissible shape="circular">{tag}</Tag>
        ))}
    </TagGroup>
)}
```

**On save:** pass `generalTags: tags` (the string array) to `CreateDocumentLibraryEntryRequest` / `UpdateDocumentLibraryEntryRequest`. The backend already stores it as a JSON array in the `general_tags text` column.

**On dialog open (edit mode):** populate `setTags(entry.generalTags ?? [])` in the `useEffect` that runs when the dialog opens.

**Imports to add:** `Tag`, `TagGroup` from `@fluentui/react-components`.

**Remove:** the old comma-split `Input` and any string-join logic for `generalTags`.

---

### Item 3 — Restriction properties (restrictType / restrictedType / required)

**Problem:** Library entries have no restriction metadata. When a library document is linked to a blueprint slot or picked during exchange initiation, the slot cannot auto-fill `restrictType`/`restrictedType`/`required`. These three fields need to exist on the library entry so they can be propagated.

**Pattern to follow:** `web-app/src/app/exchange-initiation/components/exchange-initiation-documents-tab/ExchangeInitiationDocumentsCard.tsx` — the existing `Switch` + `Dropdown` + `Checkbox` UI for exactly these three fields.

#### Step A — Database migration

Check whether `V14__document_library.sql` has been applied to your local database (i.e., whether `./mvnw quarkus:dev` was run after this session). If the table does NOT yet exist: add these three columns directly into `V14__document_library.sql` before the `CONSTRAINT document_library_pkey` line:
```sql
restrict_type      boolean         NOT NULL DEFAULT false,
restricted_type    VARCHAR(16)
    CONSTRAINT ck_doc_lib_restricted_type CHECK (restricted_type IN
        ('PDF','DOCX','DOC','XLSX','XLS','PPTX','PPT','PNG','JPG')),
required           boolean         NOT NULL DEFAULT false,
```

If V14 WAS already applied (table exists): create `src/main/resources/db/migration/V15__document_library_restrictions.sql`:
```sql
ALTER TABLE document_library
    ADD COLUMN restrict_type    boolean  NOT NULL DEFAULT false,
    ADD COLUMN restricted_type  VARCHAR(16)
        CONSTRAINT ck_doc_lib_restricted_type CHECK (restricted_type IN
            ('PDF','DOCX','DOC','XLSX','XLS','PPTX','PPT','PNG','JPG')),
    ADD COLUMN required         boolean  NOT NULL DEFAULT false;
```

#### Step B — Backend

**`src/main/kotlin/com/docuhyphen/app/api/model/entity/DocumentLibraryEntry.kt`**

Add three fields:
```kotlin
@Column(name = "restrict_type", nullable = false)
var restrictType: Boolean = false

@Column(name = "restricted_type", nullable = true)
var restrictedType: String? = null

@Column(name = "required", nullable = false)
var required: Boolean = false
```

**`src/main/kotlin/com/docuhyphen/app/api/model/dto/DocumentLibraryDtos.kt`**

Add to `DocumentLibraryEntryDto`, `DocumentLibraryEntrySummaryDto`, `CreateDocumentLibraryEntryRequest`, `UpdateDocumentLibraryEntryRequest`:
```kotlin
val restrictType: Boolean = false
val restrictedType: String? = null
val required: Boolean = false
```

Update `DocumentLibraryService.createEntry` and `updateEntry` to map these three fields from request to entity.

#### Step C — Frontend models

**`web-app/src/app/models/models.tsx`** — add to `DocumentLibraryEntrySummaryDto` and `DocumentLibraryEntryDto` and both request types:
```ts
restrictType?: boolean;
restrictedType?: string;
required?: boolean;
```

#### Step D — Editor dialog UI

**`web-app/src/app/settings/documents-tab/DocumentLibraryEditorDialog.tsx`**

Add three state variables:
```tsx
const [restrictType, setRestrictType] = useState(false);
const [restrictedType, setRestrictedType] = useState<string | undefined>(undefined);
const [required, setRequired] = useState(false);
```

Initialize in `useEffect` on open (edit mode):
```tsx
setRestrictType(entry?.restrictType ?? false);
setRestrictedType(entry?.restrictedType);
setRequired(entry?.required ?? false);
```

UI block to add after the description field — copy exactly from `ExchangeInitiationDocumentsCard.tsx`:
```tsx
<Switch
    id="doc-lib-restrict-type-switch"
    checked={restrictType}
    onChange={(_, d) => { setRestrictType(d.checked); if (!d.checked) setRestrictedType(undefined); }}
    label="Restrict upload type"
/>
<Dropdown
    id="doc-lib-restricted-type-dropdown"
    disabled={!restrictType}
    selectedOptions={restrictedType ? [restrictedType] : []}
    onOptionSelect={(_, d) => setRestrictedType(d.optionValue as string)}
    placeholder="Select allowed type..."
>
    <OptionGroup label="Documents">
        <Option value="PDF">PDF</Option>
        <Option value="DOCX">DOCX</Option>
        <Option value="DOC">DOC</Option>
        <Option value="XLSX">XLSX</Option>
        <Option value="XLS">XLS</Option>
        <Option value="PPTX">PPTX</Option>
        <Option value="PPT">PPT</Option>
    </OptionGroup>
    <OptionGroup label="Images">
        <Option value="PNG">PNG</Option>
        <Option value="JPG">JPG</Option>
    </OptionGroup>
</Dropdown>
<Checkbox
    id="doc-lib-required-checkbox"
    checked={required}
    onChange={(_, d) => setRequired(!!d.checked)}
    label="Required"
/>
```

Include `restrictType`, `restrictedType`, `required` in the save payload.

#### Step E — Propagate on link/pick

**`web-app/src/app/settings/templates-tab/BlueprintEditorDialog.tsx`** — in the `onSelect` callback for `DocumentLibraryPicker`:
```tsx
updateDoc(linkingDocIndex!, {
    libraryDocumentId: entry.id,
    title: entry.title,
    restrictType: entry.restrictType ?? false,
    restrictedType: entry.restrictedType,
    required: entry.required ?? false,
});
```

**`web-app/src/app/exchange-initiation/ExchangeInitiation.tsx`** — in `addLibraryDocument`:
```tsx
const newDoc: ExchangeRequestDocumentRequest = {
    title: entry.title,
    libraryDocumentId: entry.id,
    restrictType: entry.restrictType ?? false,
    restrictedType: entry.restrictedType,
    required: entry.required ?? false,
};
```

---

### Item 4 — Variable token support for title field

**File:** `web-app/src/app/settings/documents-tab/DocumentLibraryEditorDialog.tsx`

**Problem:** The title `<Input>` is a plain text field. When a library document is used as a blueprint slot, the title should support variable tokens (`{{TOKEN}}` / `{{SEQ:KEY}}`) just like blueprint document slot titles and communication subject lines.

**Pattern to follow:** `web-app/src/app/exchange-initiation/components/exchange-initiation-documents-tab/ExchangeInitiationDocumentsCard.tsx` — it renders `<VariableTokenInput>` for the title field when `availableVariables` is non-null.

**Import paths** (relative to `documents-tab/`):
```tsx
import VariableTokenInput from '../../../components/variable-token-input/VariableTokenInput.tsx';
import {getAvailableVariables} from '../../../services/variableService.ts';
```

**State:**
```tsx
const [availableVariables, setAvailableVariables] = useState<VariableDto[] | null>(null);
```

**Fetch on dialog open** (add to the existing `useEffect` that runs when `open` becomes `true`):
```tsx
getAvailableVariables().then(setAvailableVariables).catch(() => setAvailableVariables(null));
```

**Replace the title `<Input>` with:**
```tsx
{availableVariables !== null ? (
    <VariableTokenInput
        id="doc-lib-title-input"
        value={title}
        onChange={setTitle}
        availableVariables={availableVariables}
        placeholder="Document title..."
    />
) : (
    <Input
        id="doc-lib-title-input"
        value={title}
        onChange={(_, d) => setTitle(d.value)}
        placeholder="Document title..."
    />
)}
```

The fallback to plain `<Input>` ensures the dialog still works if `getAvailableVariables` fails. Check the exact prop signature of `VariableTokenInput` against its definition — the `onChange` prop may be `(value: string) => void` or `(e, d) => void`; match it exactly.

---

### Item 5 — Blueprint editor "Link Document" button verification and bug fix

**File:** `web-app/src/app/settings/templates-tab/BlueprintEditorDialog.tsx`

**Problem:** After Phase 3, `linkingDocIndex` state was added but may have a stale-state bug on re-open, and there is uncertainty about whether the "Link Document" button is visible during document slot creation.

**Bug to fix — stale `linkingDocIndex` on dialog re-open:**

In the `useEffect` that fires when `open` changes (the one that resets `activeTab` and `error`), add:
```tsx
setLinkingDocIndex(null);
```

Without this, if the user opened the picker, dismissed without selecting, then closed the dialog and reopened it, `linkingDocIndex` could still be non-null from the previous session, causing the picker to show immediately instead of the document list.

**Verify the "Link Document" button is visible:**

Search `BlueprintEditorDialog.tsx` for the documents tab render block. The button should appear per slot in the slot list when `linkingDocIndex === null`. If it is inside a condition that only renders for edit mode (i.e., gated on `mode === 'edit'`), remove that gate — "Link Document" should work in both create and edit mode.

**If the picker never shows:**

Check the import path resolves. From `web-app/src/app/settings/templates-tab/`, the import should be:
```tsx
import DocumentLibraryPicker from '../../exchange-initiation/components/document-library-picker/DocumentLibraryPicker.tsx';
```
(Two levels up to `settings/`, then into `exchange-initiation/`... actually from `settings/templates-tab/` going `../../` lands at `src/app/` — verify the exact relative path by counting directory levels from the file.)

Correct path: `../../../app/exchange-initiation/...` would be wrong since we're already inside `app/`. From `app/settings/templates-tab/`, the correct path to `app/exchange-initiation/...` is `../../exchange-initiation/...`.

Verify this in the file and fix if wrong.

---

### Item 6 — "Pick from Library" icon replacement

**Files:**
- `web-app/src/app/components/IconBundles.tsx`
- `web-app/src/app/exchange-initiation/components/exchange-initiation-documents-tab/ExchangeInitiationDocumentsTab.tsx`

**Problem:** The "Pick from Library" button uses `ReceiveDocumentsIcon` (`DocumentArrowLeft`) which means "receive/download a document" — the wrong semantic for "browse and pick from a document library."

**Fix:** Add a new named export to `IconBundles.tsx`. `DocumentFolderFilled` and `DocumentFolderRegular` are already imported (they back `DocumentVersionsIcon`). Add a second export that reuses the same icons under a semantically correct name:

```tsx
export const PickFromLibraryIcon = bundleIcon(DocumentFolderFilled, DocumentFolderRegular);
```

This does NOT require a new import — the icons are already in the import block.

In `ExchangeInitiationDocumentsTab.tsx`:
1. Replace the `ReceiveDocumentsIcon` import with `PickFromLibraryIcon`
2. Replace `<ReceiveDocumentsIcon/>` on the "Pick from Library" button with `<PickFromLibraryIcon/>`

**Why `DocumentFolder`:** It visually represents a collection/archive of documents (a library), not a directional transfer. `DocumentArrowLeft` implies receiving something from a counterparty, which is the wrong affordance for a local library browse action.

---

### Phase 4 Delivery Order

1. Item 3 first (restriction properties) — needs a migration decision (V14 vs V15)
2. Item 6 (icon fix) — trivial, do it alongside item 3
3. Item 1 (delete confirmation) — isolated to `DocumentsTab.tsx`
4. Item 2 (tag chips) — isolated to `DocumentLibraryEditorDialog.tsx`
5. Item 4 (variable token title) — also in `DocumentLibraryEditorDialog.tsx`, do after item 2
6. Item 5 (blueprint picker bug) — verify and fix last; may just be a one-line useEffect addition

After all 6 items: run `npx tsc --noEmit` from `web-app/`, verify zero errors, then mark Phase 4 `[DONE]`.
