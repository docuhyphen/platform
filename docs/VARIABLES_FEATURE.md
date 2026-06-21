# Feature: Template Variables (Variables & Sequences)

## Status Overview

| Section | Status | Notes |
|---|---|---|
| 1. DB Migration V9 | ✅ Done | `src/main/resources/db/migration/V9__variables_sequences.sql` |
| 2. Backend Entities & Repos | ✅ Done | `SequenceDefinition.kt`, `VariableDefinition.kt`, repos |
| 3. Template Interpolator | ✅ Done | `TemplateVariableInterpolator.kt` — system/SEQ/org/personal |
| 4. Service Layer | ✅ Done | `SequenceDefinitionService`, `VariableDefinitionService`, `AvailableVariablesService` |
| 5. REST Resources | ✅ Done | `SequenceDefinitionResource`, `VariableDefinitionResource` (includes `/available`) |
| 6. Wire into Exchange Initiation | ✅ Done | `variableOverrides` in DTO + interpolation before entity creation |
| 7. Frontend: VariableTokenInput | ✅ Done | `web-app/src/components/variable-token-input/VariableTokenInput.tsx` |
| 8. Frontend: Blueprint + Exchange forms | ✅ Done | BlueprintEditorDialog + ExchangeInitiation + override panel |
| 9. Frontend: Sequences settings tab | ✅ Done | `OrganizationSequencesTab.tsx` + registered in Settings.tsx |
| 10. Frontend: Variables settings tabs | ✅ Done | `OrganizationVariablesTab.tsx` + `PersonalVariablesTab.tsx` + registered |

**Legend:** ⬜ Not started · 🔄 In progress · ✅ Done

---

## Context

### Why this feature exists
All string fields in Blueprints and Exchange creation (name, description, initialShareMessage, document titles) currently store **literal strings** only — no runtime substitution. Users who create Blueprints want to define reusable templates like `{{ORG_NAME}} - {{CLIENT_NAME}} - {{CURRENT_DATE}} - {{SEQ:INV}}` that resolve to real values when an exchange is created from the blueprint.

### Terminology decision
The platform calls these **Variables** (not "custom fields"). Comparable platforms:
- Jira Automation → *Smart Values*; PandaDoc/DocuSign → *Variables*; Salesforce → *Merge Fields*; Notion/Confluence → *Variables*

Three categories:
1. **System Variables** — built-in, auto-resolved from user/org/date context
2. **Sequences** — org-defined named counters (`{{SEQ:KEY}}`), auto-increment on each use
3. **Org/Personal Variables** — admin-defined key-value pairs, static default + overridable at creation time

### How this fits the existing codebase
- The workflow engine **already has** a `$subject.fieldName` placeholder pattern (`WorkflowAssigneeResolver.kt`, `DefaultWorkflowEngineService.kt`). Variables extends this concept to the exchange/blueprint naming layer using `{{TOKEN}}` syntax.
- Blueprint `configJson` stores raw `{{TOKEN}}` strings. Interpolation happens at **application time** (frontend when blueprint is picked, backend for safety net).
- The exchange DTO will gain a `variableOverrides: Map<String, String>?` field for user-supplied overrides at creation time.

---

## Section 1: DB Migration V9

**File to create:** `src/main/resources/db/migration/V9__variables_sequences.sql`

```sql
-- Sequence definitions per org (auto-incrementing named counters)
CREATE TABLE sequence_definition (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id         UUID        NOT NULL REFERENCES organization(id),
    name                    VARCHAR(255) NOT NULL,
    key                     VARCHAR(64)  NOT NULL,   -- token: {{SEQ:key}}
    current_value           BIGINT      NOT NULL DEFAULT 0,
    pad_width               INT         NOT NULL DEFAULT 0,   -- 3 → "007"
    prefix                  VARCHAR(64),
    suffix                  VARCHAR(64),
    reset_period            VARCHAR(16) NOT NULL DEFAULT 'NEVER',  -- NEVER | YEARLY | MONTHLY
    last_reset_at           TIMESTAMPTZ,
    is_active               BOOLEAN     NOT NULL DEFAULT TRUE,
    is_deleted              BOOLEAN     NOT NULL DEFAULT FALSE,
    created_by_app_user_id  UUID        REFERENCES app_user(id),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_sequence_key UNIQUE (organization_id, key)
);

-- User-defined variable definitions (ORG or PERSONAL scope)
CREATE TABLE variable_definition (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    key                     VARCHAR(64)  NOT NULL,
    default_value           TEXT,
    scope                   VARCHAR(16)  NOT NULL,   -- ORG | PERSONAL
    organization_id         UUID        REFERENCES organization(id),
    created_by_app_user_id  UUID        NOT NULL REFERENCES app_user(id),
    is_active               BOOLEAN     NOT NULL DEFAULT TRUE,
    is_deleted              BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_variable_org   ON variable_definition (organization_id, key) WHERE scope = 'ORG';
CREATE UNIQUE INDEX uq_variable_user  ON variable_definition (created_by_app_user_id, key) WHERE scope = 'PERSONAL';
CREATE INDEX ix_sequence_org          ON sequence_definition (organization_id);
CREATE INDEX ix_variable_scope        ON variable_definition (scope, organization_id);
```

---

## Section 2: Backend Entities & Repositories

### New Entities

**`SequenceDefinition.kt`** — `src/main/kotlin/com/docuhyphen/app/api/model/entity/SequenceDefinition.kt`
```kotlin
@Entity @Table(name = "sequence_definition")
class SequenceDefinition(
    @Id var id: UUID = UUID.randomUUID(),
    var organizationId: UUID,
    var name: String,
    var key: String,               // used in {{SEQ:key}}
    var currentValue: Long = 0L,
    var padWidth: Int = 0,
    var prefix: String? = null,
    var suffix: String? = null,
    @Enumerated(EnumType.STRING)
    var resetPeriod: SequenceResetPeriod = SequenceResetPeriod.NEVER,
    var lastResetAt: Instant? = null,
    var isActive: Boolean = true,
    var isDeleted: Boolean = false,
    var createdByAppUserId: UUID? = null,
    var createdAt: Instant = Instant.now()
)

enum class SequenceResetPeriod { NEVER, YEARLY, MONTHLY }
```

**`VariableDefinition.kt`** — `src/main/kotlin/com/docuhyphen/app/api/model/entity/VariableDefinition.kt`
```kotlin
@Entity @Table(name = "variable_definition")
class VariableDefinition(
    @Id var id: UUID = UUID.randomUUID(),
    var key: String,
    var defaultValue: String? = null,
    @Enumerated(EnumType.STRING)
    var scope: VariableScope,
    var organizationId: UUID? = null,
    var createdByAppUserId: UUID,
    var isActive: Boolean = true,
    var isDeleted: Boolean = false,
    var createdAt: Instant = Instant.now()
)

enum class VariableScope { ORG, PERSONAL }
```

### Repositories
- `SequenceDefinitionRepository` — extends `JpaRepository<SequenceDefinition, UUID>`
  - `findByOrganizationIdAndKeyAndIsDeletedFalse(orgId, key)`
  - `findAllByOrganizationIdAndIsDeletedFalse(orgId)`
- `VariableDefinitionRepository`
  - `findByScopeAndOrganizationIdAndIsDeletedFalse(scope, orgId)`
  - `findByScopeAndCreatedByAppUserIdAndIsDeletedFalse(scope, userId)`

---

## Section 3: Template Interpolator

**`TemplateVariableInterpolator.kt`** — `src/main/kotlin/com/docuhyphen/app/api/service/variable/TemplateVariableInterpolator.kt`

Key concepts:
- **`VariableResolutionContext`** — carries `AppUser`, `Organization?`, `Instant` (creation timestamp), `Map<String, String>` overrides from caller
- **`InterpolationResult`** — `resolvedString: String`, `unresolvedTokens: List<String>`
- Resolution order: System → `SEQ:key` (atomic DB increment) → Org Variables (check overrides first, then default) → Personal Variables → leave unresolved

```kotlin
data class VariableResolutionContext(
    val user: AppUser,
    val organization: Organization?,
    val timestamp: Instant,
    val overrides: Map<String, String> = emptyMap()
)

data class InterpolationResult(
    val resolved: String,
    val unresolvedTokens: List<String> = emptyList()
)
```

**System variables** resolved inline (no DB call):
```
USER_FIRST_NAME, USER_LAST_NAME, USER_FULL_NAME, USER_EMAIL,
ORG_NAME, ORG_REG_NUMBER,
CURRENT_DATE (yyyy-MM-dd), CURRENT_YEAR, CURRENT_MONTH, CURRENT_MONTH_NUMBER
```

**SEQ: resolution** — calls `SequenceDefinitionService.nextValue(orgId, key)` which does:
```sql
UPDATE sequence_definition
SET current_value = current_value + 1
WHERE organization_id = ? AND key = ? AND is_active = TRUE AND is_deleted = FALSE
RETURNING current_value, pad_width, prefix, suffix
```
Then formats: `prefix + paddedValue + suffix`.

---

## Section 4: Service Layer

### `SequenceDefinitionService.kt`
`src/main/kotlin/com/docuhyphen/app/api/service/variable/SequenceDefinitionService.kt`

Methods:
- `listSequences(orgId, isActive?)`
- `getSequence(id, callerContext)`
- `createSequence(orgId, request)` — validates key uniqueness
- `updateSequence(id, request, callerContext)` — name/prefix/suffix/padWidth/resetPeriod only (not currentValue)
- `deleteSequence(id, callerContext)` — soft delete
- `resetCounter(id, callerContext)` — org admin only, sets `currentValue = 0`, `lastResetAt = now`
- `nextValue(orgId, key)` — atomic increment (used internally by Interpolator)

### `VariableDefinitionService.kt`
`src/main/kotlin/com/docuhyphen/app/api/service/variable/VariableDefinitionService.kt`

Methods:
- `listVariables(scope, orgId?, userId?)` 
- `createVariable(scope, request, callerContext)`
- `updateVariable(id, request, callerContext)`
- `deleteVariable(id, callerContext)` — soft delete

Access control mirrors `BlueprintDefinitionService.kt`:
- ORG scope: readable by org members, writable by org admin/owner
- PERSONAL scope: only creator can read/write

### `AvailableVariablesService.kt`
Aggregates all variable categories for the frontend picker:
```kotlin
data class AvailableVariablesDto(
    val system: List<SystemVariableDto>,   // read-only catalogue
    val sequences: List<SequenceDefinitionDto>,
    val org: List<VariableDefinitionDto>,
    val personal: List<VariableDefinitionDto>
)
```

---

## Section 5: REST Resources

### `SequenceDefinitionResource.kt`
`src/main/kotlin/com/docuhyphen/app/api/resource/SequenceDefinitionResource.kt`

```
GET    /sequences              → listSequences
POST   /sequences              → createSequence
GET    /sequences/{id}         → getSequence
PUT    /sequences/{id}         → updateSequence
DELETE /sequences/{id}         → deleteSequence
PATCH  /sequences/{id}/reset   → resetCounter (org admin only)
```

### `VariableDefinitionResource.kt`
`src/main/kotlin/com/docuhyphen/app/api/resource/VariableDefinitionResource.kt`

```
GET    /variables              → listVariables(scope?)
POST   /variables              → createVariable
PUT    /variables/{id}         → updateVariable
DELETE /variables/{id}         → deleteVariable
```

### `AvailableVariablesResource.kt`
```
GET    /variables/available    → AvailableVariablesDto
                                  (system list + org sequences + org vars + personal vars for current user)
```
Used by the `VariableTokenInput` frontend picker to know which tokens are valid.

---

## Section 6: Wire into Exchange Initiation

**Modified files:**
- `src/main/kotlin/com/docuhyphen/app/api/resource/model/RequestsResponses.kt` — add `variableOverrides: Map<String, String>? = null` to `ExchangeInitiationDto`
- `src/main/kotlin/com/docuhyphen/app/api/service/exchange/ExchangeInitiationService.kt` — inject `TemplateVariableInterpolator`, call before entity creation:

```kotlin
val context = VariableResolutionContext(
    user = initiator,
    organization = org,
    timestamp = Instant.now(),
    overrides = dto.variableOverrides ?: emptyMap()
)
val resolvedName        = interpolator.interpolate(dto.name, context).resolved
val resolvedDescription = dto.description?.let { interpolator.interpolate(it, context).resolved }
val resolvedMessage     = dto.initialShareMessage?.let { interpolator.interpolate(it, context).resolved }
val resolvedDocuments   = dto.exchangeDocuments.map { doc ->
    doc.copy(title = interpolator.interpolate(doc.title, context).resolved)
}
```

> **Important:** Sequence counters are incremented during this call. If exchange creation later fails (validation error, DB rollback), the counter is already consumed. This is acceptable behaviour (gap in sequence) — consistent with how invoice numbers work in financial platforms.

---

## Section 7: Frontend — VariableTokenInput Component

**New directory:** `web-app/src/components/variable-token-input/`

Files:
- `VariableTokenInput.tsx` — main component
- `VariableTokenInputStyles.tsx` (or `.css.ts`) — styled components
- `VariablePickerPopover.tsx` — popover shown when user types `{{`
- `VariablePreviewChip.tsx` — inline chip rendering `{{TOKEN_KEY}}`

### Behaviour
1. User types `{{` → popover opens showing available variables grouped:
   - **System** (date, user, org) — auto badge
   - **Sequences** — counter badge with current value preview
   - **Org Variables** — org badge with default value shown
   - **Personal Variables** — personal badge
2. Selecting a variable inserts `{{TOKEN_KEY}}` at cursor.
3. Rendered tokens appear as coloured chips. Hovering shows: key, description, resolved preview value.
4. Chips are deletable (click ×).

### Props interface
```typescript
interface VariableTokenInputProps {
  value: string
  onChange: (value: string) => void
  availableVariables: AvailableVariablesDto  // from GET /variables/available
  resolvedPreview?: Record<string, string>   // for live preview in exchange initiation
  multiline?: boolean
  placeholder?: string
  label?: string
  disabled?: boolean
}
```

---

## Section 8: Frontend — Blueprint Editor + Exchange Initiation

### BlueprintEditorDialog.tsx
`web-app/src/app/settings/templates-tab/BlueprintEditorDialog.tsx`

Replace these fields with `VariableTokenInput`:
- Details tab: `name`, `description`, `initialShareMessage`
- Documents tab: each `exchangeDocuments[i].title`

Add call to `GET /variables/available` on dialog open to populate the picker.

### ExchangeInitiation.tsx
`web-app/src/app/exchange-initiation/ExchangeInitiation.tsx`

1. Replace name / description / initialShareMessage / document title fields with `VariableTokenInput`.
2. Add `GET /variables/available` query (can reuse same data as blueprint editor).
3. Update `handleBlueprintSelect()`: after applying blueprint config, scan applied strings for Org/Personal variable tokens (`{{KEY}}` that are not System tokens). If any found, show a **"Fill in variables" panel** — a small inline section showing pre-filled defaults that the user can edit before proceeding.
4. Build `variableOverrides` map from the override panel values and include in `ExchangeInitiationRequest`.
5. Provide `resolvedPreview` to `VariableTokenInput` for live preview (system vars can be resolved client-side; sequences show current counter as hint only).

---

## Section 9: Frontend — Sequences Settings Tab

**New file:** `web-app/src/app/settings/organization-sequences-tab/OrganizationSequencesTab.tsx`

UI structure (mirrors `OrganizationBlueprintsTab.tsx`):
- List view: name, key (shown as `{{SEQ:KEY}}`), current counter value, reset period badge, active/inactive badge
- Action menu: Edit, Reset Counter (admin only), Activate/Deactivate, Delete
- Create/Edit drawer with fields:
  - Name (required)
  - Key (required, uppercase alphanumeric slug, e.g. `INV`)
  - Pad Width (number, 0 = no padding)
  - Prefix (optional text)
  - Suffix (optional text)
  - Reset Period: Never / Yearly / Monthly
- "Preview" shows sample output: e.g. `INV-007`

Add tab to Settings page in `Settings.tsx` (org admin only).

---

## Section 10: Frontend — Variables Settings Tabs

### Org Variables Tab
**New file:** `web-app/src/app/settings/organization-variables-tab/OrganizationVariablesTab.tsx`

- Section at top: **System Variables reference** — read-only table showing all built-in tokens, their description, and an example resolved value. Helps users understand what's available without creating their own.
- Section below: **Org Variables** — list of key/default-value pairs, org admin can create/edit/delete.
- Inline create row or small drawer with: Key, Default Value, (optionally: description/label for display in picker)

### Personal Variables Tab
**New file:** `web-app/src/app/settings/personal-variables-tab/PersonalVariablesTab.tsx`

- Same structure as Org Variables but scoped to the current user.
- Accessible from the personal settings section.

Add both tabs to `Settings.tsx`.

---

## Reuse Patterns (existing code to reference)

| What | Where | Pattern to copy |
|---|---|---|
| Scope access control | `BlueprintDefinitionService.kt` | ORG = org-members-read/admin-write; PERSONAL = creator-only |
| Soft delete | `BlueprintDefinition.kt`, `WorkflowDefinition.kt` | `isDeleted` flag, never hard-delete |
| Settings tab structure | `TemplatesTab.tsx` → `OrganizationBlueprintsTab.tsx` | Tab registration, list + drawer pattern |
| Entity pattern | `BlueprintDefinition.kt` | `@Entity`, `@Table`, UUID PK, `Instant` timestamps |
| Workflow `$subject.` resolution | `WorkflowAssigneeResolver.kt` | Regex parse + map lookup — extend for new syntax |

---

## Verification Checklist

- [ ] Create org sequence `INV`, pad 3, prefix `INV-`
- [ ] Create org variable `CLIENT_NAME`, default `"Acme Corp"`
- [ ] Blueprint name: `{{ORG_NAME}} - {{CLIENT_NAME}} - {{CURRENT_DATE}} - {{SEQ:INV}}`
- [ ] Initiate exchange from blueprint → override panel shows `CLIENT_NAME = Acme Corp`
- [ ] Change `CLIENT_NAME` to `Beta Inc` → exchange name resolves to e.g. `Legal Corp - Beta Inc - 2026-06-21 - INV-001`
- [ ] Second exchange from same blueprint → counter shows `INV-002`
- [ ] Manual exchange creation (no blueprint) → system vars resolve live in `VariableTokenInput`
- [ ] Concurrent exchange creation × 2 → no duplicate sequence values
- [ ] `./gradlew test` passes
- [ ] `vite build` passes in `web-app/`
