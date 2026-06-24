# Fields / Metadata Layer — Implementation Guide (v3)

## BEFORE YOU START

**Read `AGENTS.md` before writing a single line of code.**
It contains project conventions, commit rules, and coding standards that all implementations must follow.

**Do NOT create any pull requests at any point during this implementation.**
Implement all phases directly on the working branch. No PR, no draft PR, no branch push.

**Migration versions:** This plan uses V19, V20, V21. A JSON-to-table normalization pass
already consumed V16 (drop `communication.channel_overrides_json`), V17 (workflow
`workflow_step_assignee` + `workflow_step_decision`), and V18 (blueprint
`blueprint_document_default` + `blueprint_participant_default`). Confirm the latest applied
migration before creating new files and bump if needed.

---

## Context

Exchanges currently have only fixed metadata (name, description, status, dates, documents, permissions).
There is no configurable metadata system and no way to filter which workflows apply to which exchanges —
all active workflows for an org fire on every matching trigger event.

This feature introduces **Fields** as a first-class domain capability:
1. **Classification** — structured metadata on exchanges (Category, Highly Sensitive, etc.)
2. **Workflow gating** — pre-execution filter so a workflow only fires when its field conditions match
3. **Blueprint defaults** — blueprints pre-populate field values at exchange creation time

This is the **v3 rearchitected** plan. Major structural decisions are taken now because the platform is
pre-production — proper normalized tables over JSON blobs, typed value storage, and future-proofed
workflow condition grouping.

---

## Architectural Decisions (Final)

| Decision | Approach | Why |
|---|---|---|
| `FieldDefinition.key` | Immutable slug, `UNIQUE(scope_type, scope_org_id, key)` | Stable token for snapshot injection (`fields.<key>`); matches `VariableDefinition`/`SequenceDefinition` pattern |
| `ExchangeFieldValue` storage | Multi-column typed: `text_value`, `bool_value`, `json_value` | Direct SQL search, no JSON parsing on read, type safety |
| CHECKLIST persistence | Separate `exchange_checklist_item_value` table with `checked_by`, `checked_at` | Checklist implies per-item auditability — fundamentally different from MULTISELECT |
| Blueprint defaults | Separate `blueprint_field_default` table — NOT in `configJson` | FK integrity, queryable, cascade delete, no JSON parsing |
| Workflow conditions | `workflow_condition_group` (OR-ed) + `workflow_field_condition` (AND-ed within group) | Future-proofs `(A AND B) OR (C AND D)`; v1 creates one AND group, no UI change needed |
| Workflow filter timing | Pre-execution — filter BEFORE instance creation | Avoids orphan `WorkflowInstance` rows for non-matching workflows |
| Snapshot injection | Flat `"fields.<key>" → value` in existing `subjectData Map<String, String>` | Compatible with existing predicate evaluator — nested JSON would break it |
| Guest visibility | Filter `INTERNAL` fields in `toNoAuthDto()` transformer | Server-enforced; follows existing endpoint-separation pattern |
| Settings tab gating | Tab always visible; backend enforces 403 for non-admin mutations | Consistent with all other Settings tabs — no frontend tab hiding |
| `required_from` timestamp | On `FieldDefinition` | Validation only applies to exchanges created after this timestamp; existing exchanges stay valid |

**Codebase patterns this reuses (verified):**
- Sub-entity validation before main entity save → `ExchangeInitiationService.initiateExchange()`
- `@OneToMany(cascade = [CascadeType.ALL])` sub-entity save → `Exchange.documents` in `Exchange.kt`
- Discrete junction entity (not `@ManyToMany`) → `PrincipalGroupMember.kt`
- Domain event publishing → `@Inject DomainEventPublisher` in `ExchangeAutoAcceptActionHandler.kt`
- Immutable `key` slug → `VariableDefinition.kt`, `SequenceDefinition.kt`
- Standalone child entity persisted via repository (Phase 3 blueprint defaults) → `BlueprintDocumentDefault.kt` + `persistDocuments`/`loadDocuments` in `BlueprintDefinitionService.kt`

**Integration points confirmed against current code (re-verify before implementing):**
- `ExchangeInitiationService.initiateExchange(dto: ExchangeInitiationDto)` exists and calls
  `workflowEngineService.trigger(TriggerRequest(... subjectData = buildMap { ... }))` at **three**
  sites (draft / acceptance / ending). Merge `getValuesAsSubjectDataMap(...)` into the `subjectData`
  map at **each** of those call sites.
- Guest visibility: `BasicEntityToDtoTransformer.toNoAuthDto(exchange)` returns the separate
  `NoAuthExchangeBasicDto`. "Strip INTERNAL fields" means: only populate SHARED field values when
  building the no-auth DTO. The full `exchangeFields` block goes on `ExchangeDetailedDto`
  (populated in `ExchangeRetrievalService`) for authenticated views.
- `Settings.tsx` registers a tab in four places: the `tabIds` object, the label map, the `<Tab>`
  list, and the render switch. Add the Fields tab to all four, after Variables.
- UUID DDL convention: existing migrations (V14, V17, V18) supply the `id` from the entity
  (`UUID.randomUUID()`) and omit a column default. The `DEFAULT gen_random_uuid()` in this plan's
  DDL is harmless on PostgreSQL 15 but inconsistent with that convention — entities must still
  assign their own `id` regardless. Drop the default for consistency if preferred.

---

## Delivery: 3 Phases

---

## Phase 1 — Core Fields: Schema, Backend, Exchange UI, Settings Admin

### V19 Migration: `V19__exchange_fields.sql`

**`exchange_field_definition`**
```sql
CREATE TABLE exchange_field_definition (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key                     VARCHAR(64)  NOT NULL,
    name                    VARCHAR(255) NOT NULL,
    description             TEXT,
    field_type              VARCHAR(20)  NOT NULL,
      -- TEXT | TEXTAREA | CHECKBOX | SELECT | MULTISELECT | CHECKLIST
    scope_type              VARCHAR(10)  NOT NULL DEFAULT 'ORG',
      -- PLATFORM | ORG
    scope_org_id            UUID REFERENCES organization(id),
    visibility              VARCHAR(20)  NOT NULL DEFAULT 'INTERNAL',
      -- INTERNAL | SHARED
    required                BOOLEAN      NOT NULL DEFAULT false,
    required_from           TIMESTAMP,
    special_behavior        VARCHAR(30)  NOT NULL DEFAULT 'NONE',
      -- NONE | SENSITIVE_FLAG
    display_order           INTEGER      NOT NULL DEFAULT 0,
    active                  BOOLEAN      NOT NULL DEFAULT true,
    is_default              BOOLEAN      NOT NULL DEFAULT false,
    created_by_app_user_id  UUID REFERENCES app_user(id),
    created_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_field_key UNIQUE (scope_type, scope_org_id, key)
);
```

**`exchange_field_option`** (SELECT, MULTISELECT, CHECKLIST)
```sql
CREATE TABLE exchange_field_option (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    field_id      UUID         NOT NULL REFERENCES exchange_field_definition(id),
    value         VARCHAR(100) NOT NULL,
    display_name  VARCHAR(255) NOT NULL,
    display_order INTEGER      NOT NULL DEFAULT 0,
    active        BOOLEAN      NOT NULL DEFAULT true
);
```

**`exchange_field_value`** — TEXT, TEXTAREA, SELECT, MULTISELECT, CHECKBOX (CHECKLIST uses its own table)
```sql
CREATE TABLE exchange_field_value (
    id                      UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    exchange_id             UUID      NOT NULL REFERENCES exchange(id),
    field_definition_id     UUID      NOT NULL REFERENCES exchange_field_definition(id),
    text_value              VARCHAR(5000),   -- TEXT, TEXTAREA, SELECT
    bool_value              BOOLEAN,         -- CHECKBOX
    json_value              TEXT,            -- MULTISELECT: '["legal","hr"]'
    updated_by_app_user_id  UUID REFERENCES app_user(id),
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_exchange_field UNIQUE (exchange_id, field_definition_id)
);
```

Storage mapping per type:
- TEXT / TEXTAREA / SELECT → `text_value`
- CHECKBOX → `bool_value`
- MULTISELECT → `json_value` (JSON array of option values)
- CHECKLIST → **does not use this table** (see below)

**`exchange_checklist_item_value`** — CHECKLIST fields only
```sql
CREATE TABLE exchange_checklist_item_value (
    exchange_id             UUID      NOT NULL REFERENCES exchange(id),
    field_definition_id     UUID      NOT NULL REFERENCES exchange_field_definition(id),
    option_id               UUID      NOT NULL REFERENCES exchange_field_option(id),
    checked                 BOOLEAN   NOT NULL DEFAULT false,
    checked_by_app_user_id  UUID REFERENCES app_user(id),
    checked_at              TIMESTAMP,
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (exchange_id, field_definition_id, option_id)
);
```

**Seed data — platform default fields**
```sql
INSERT INTO exchange_field_definition
    (id, key, name, description, field_type, scope_type, visibility,
     required, special_behavior, display_order, active, is_default)
VALUES (
    '00000000-0000-0000-0000-000000000010',
    'highly_sensitive', 'Highly Sensitive',
    'Marks this exchange as containing highly sensitive information',
    'CHECKBOX', 'PLATFORM', 'SHARED', false, 'SENSITIVE_FLAG', 0, true, true
), (
    '00000000-0000-0000-0000-000000000011',
    'category', 'Category',
    'The category or nature of this exchange',
    'SELECT', 'PLATFORM', 'SHARED', false, 'NONE', 1, true, true
);

INSERT INTO exchange_field_option (field_id, value, display_name, display_order) VALUES
    ('00000000-0000-0000-0000-000000000011', 'legal',      'Legal',      0),
    ('00000000-0000-0000-0000-000000000011', 'finance',    'Finance',    1),
    ('00000000-0000-0000-0000-000000000011', 'hr',         'HR',         2),
    ('00000000-0000-0000-0000-000000000011', 'compliance', 'Compliance', 3),
    ('00000000-0000-0000-0000-000000000011', 'operations', 'Operations', 4),
    ('00000000-0000-0000-0000-000000000011', 'other',      'Other',      5);
```

---

### Backend — Phase 1

**New entities** (`src/main/kotlin/.../model/entity/`):
- `ExchangeFieldDefinition.kt` — Kotlin enums `FieldType`, `ScopeType`, `FieldVisibility`, `SpecialBehavior`; `@OneToMany(cascade = [CascadeType.ALL])` to `ExchangeFieldOption`
- `ExchangeFieldOption.kt` — `@ManyToOne` to `ExchangeFieldDefinition`
- `ExchangeFieldValue.kt` — typed columns `textValue`, `boolValue`, `jsonValue`; `@ManyToOne` to `ExchangeFieldDefinition`
- `ExchangeChecklistItemValue.kt` — composite PK `(exchangeId, fieldDefinitionId, optionId)`; `checkedByAppUserId`, `checkedAt`

**New repositories**:
- `ExchangeFieldDefinitionRepository` — `findApplicableForOrg(orgId)`, `findAdminListForOrg(orgId)`, `findByKey(scopeType, orgId, key)`
- `ExchangeFieldOptionRepository` — `findAllActiveByFieldId(fieldId)`
- `ExchangeFieldValueRepository` — `findAllByExchangeId(exchangeId)`, `findByExchangeIdAndFieldDefinitionId(...)`
- `ExchangeChecklistItemValueRepository` — `findAllByExchangeId(exchangeId)`, `findAllByExchangeIdAndFieldDefinitionId(...)`

**New service `ExchangeFieldDefinitionService`**:
- `listApplicable(orgId)` → applicable definitions ordered by `display_order`
- `listForAdmin(orgId)` → includes inactive; PLATFORM rows flagged read-only
- `create(orgId, adminUserId, request)` — org admin only; key set once, never updated; PLATFORM-scope → 403
- `update(orgId, id, request)` — org admin only; key excluded from update path; PLATFORM-scope → 403
- `toggleActive(orgId, id)` — org admin only; PLATFORM-scope → 403
- `reorder(orgId, orderedIds)` — ORG-scope only

**New service `ExchangeFieldValueService`**:
- `saveValues(exchangeId, orgId, callerUserId, request: ExchangeFieldValueRequest)`:
  - TEXT/TEXTAREA/SELECT/MULTISELECT/CHECKBOX → upsert `exchange_field_value` typed column
  - CHECKLIST → upsert rows in `exchange_checklist_item_value`; set `checked_by_app_user_id` + `checked_at` on each item transitioning to checked
  - Validation: SELECT value is active option; MULTISELECT all values are active options; CHECKLIST all option_ids are active options; CHECKBOX bool; TEXT ≤500 chars; TEXTAREA ≤5000 chars; deactivated option values rejected on new saves
- `getValues(exchangeId)` → `ExchangeFieldValuesDto` containing:
  - `fieldValues: List<ExchangeFieldValueDto>` — typed values per non-CHECKLIST field
  - `checklistValues: List<ExchangeChecklistFieldDto>` — per-item state with `checkedBy`, `checkedAt`
- `getValuesAsSubjectDataMap(exchangeId)` → `Map<String, String>` for workflow snapshot:
  - TEXT/TEXTAREA/SELECT: `"fields.<key>"` → `text_value`
  - CHECKBOX: `"fields.<key>"` → `"true"`/`"false"`
  - MULTISELECT: `"fields.<key>"` → raw `json_value`
  - CHECKLIST: `"fields.<key>"` → JSON array of checked option values only
- `validateRequired(orgId, submittedFieldIds, now)` — throws `IllegalArgumentException` naming any required field (where `required_from IS NULL OR required_from ≤ now`) missing from submitted data

**Changes to `ExchangeInitiationDto`** — add `fieldValues: ExchangeFieldValueRequest? = null` (typed maps per field type, not raw JSON strings)

**Changes to `ExchangeInitiationService.initiateExchange()`** — follow existing validate-before-save pattern:
1. Before exchange save: `exchangeFieldValueService.validateRequired(orgId, request.fieldValues, Instant.now())`
2. After `savedExchange` persisted: `exchangeFieldValueService.saveValues(savedExchange.id, orgId, callerId, request.fieldValues)`
3. Before each `workflowEngineService.trigger(...)`: merge `getValuesAsSubjectDataMap(savedExchange.id)` into `subjectData`

**Changes to `ExchangeDetailedDto`** — add `exchangeFields: ExchangeFieldValuesDto`, populated in `ExchangeRetrievalService`

**Changes to `BasicEntityToDtoTransformer.toNoAuthDto()`** — strip any field where `visibility == INTERNAL` (guest/magic-link safety, server-enforced)

**New REST resource `ExchangeFieldDefinitionResource`** at `/field-definitions`:
```
GET    /field-definitions              → list applicable for caller's org
GET    /field-definitions/admin        → admin list incl. inactive [org admin]
POST   /field-definitions              → create [org admin]
PUT    /field-definitions/{id}         → update [org admin; PLATFORM → 403]
PATCH  /field-definitions/{id}/active  → toggle active [org admin; PLATFORM → 403]
PUT    /field-definitions/reorder      → reorder [org admin]
```

**Audit events** — emit via `DomainEventPublisher` (pattern: `ExchangeAutoAcceptActionHandler`):
- `field_definition.created` / `field_definition.updated` / `field_definition.deactivated`
- `exchange.field_value_changed` — payload `fieldKey`, `beforeValue`, `afterValue`
- CHECKLIST: `exchange.checklist_item_checked` / `exchange.checklist_item_unchecked` — payload `fieldKey`, `optionValue`, `checkedBy`

---

### Frontend — Phase 1

**New file `web-app/src/app/exchange-initiation/fieldConstants.ts`**:
```ts
export const HIGHLY_SENSITIVE_FIELD_ID = '00000000-0000-0000-0000-000000000010';
export const CATEGORY_FIELD_ID         = '00000000-0000-0000-0000-000000000011';
```

**State additions in `useExchangeInitiatingState.ts`**:
```ts
fieldValues: ExchangeFieldValueDraft       // typed, not raw JSON strings
setFieldValues: (v: ExchangeFieldValueDraft) => void
```
Reset to `{}` in `resetInitiationForm()`.

**New shared component `web-app/src/app/fields/FieldValueInput.tsx`** — reused across exchange creation, exchange details (edit), and workflow condition builder:
- TEXT → `<Input>`, TEXTAREA → `<Textarea>`, CHECKBOX → `<Checkbox>`
- SELECT → `<Dropdown>` from options
- MULTISELECT → grouped `<Checkbox>` list or `<TagPicker>`
- CHECKLIST → list of `<Checkbox>` items; compact mode hides audit info, full mode shows `checkedBy`/`checkedAt`

**New component `.../exchange-initiation-fields-tab/ExchangeInitiationFieldsTab.tsx`**:
- PLATFORM-scope definitions first, then ORG-scope
- Required fields: red asterisk; INTERNAL fields: lock icon + "(Only visible to your organization)"
- Uses `FieldValueInput` for each definition

**`ExchangeInitiationDialogTitleSection.tsx`** — add `fields-tab` as 5th tab (`TagMultipleRegular` icon)

**`ExchangeInitiation.tsx`**:
- Load `GET /field-definitions` on dialog open
- Add `fields-tab` render branch
- `onInitiateExchange()`: validate required → `setSelectedTab('fields-tab')` on error
- Blueprint selection: populate field defaults from `blueprint.fieldDefaults` (Phase 3 wires this; in Phase 1 leave a hook)

**`ExchangeDetailsTab.tsx`** — add "Fields" section:
- Non-CHECKLIST: badge pills (SELECT/MULTISELECT), checkmark (CHECKBOX), plain text (TEXT/TEXTAREA)
- CHECKLIST: compact checklist; click reveals `checkedBy`/`checkedAt`
- INTERNAL fields: `(Internal)` label; absent from guest view (server-filtered)

**`ExchangeDetailsHeader.tsx`** — Highly Sensitive treatment:
- `exchangeFields.fieldValues.find(fv => fv.fieldDefinitionId === HIGHLY_SENSITIVE_FIELD_ID && fv.boolValue === true)`
- If true: `<MessageBar intent="warning">` + `<ShieldRegular />` badge beside exchange name

**New settings directory `web-app/src/app/settings/fields-tab/`**:
- `FieldsTab.tsx`, `FieldsTabStyles.tsx` — PLATFORM section read-only; ORG section editable (Edit / Deactivate / drag-reorder)
- `FieldDefinitionDialog.tsx` — Key required on create, read-only on edit; option list editor for SELECT/MULTISELECT/CHECKLIST; Required + optional `required_from`; Visibility toggle
- Register "Fields" tab in `Settings.tsx` after Variables — always visible; mutations 403 for non-admins

---

### After completing Phase 1 — UPDATE THIS FILE
- Mark Phase 1 complete with date
- Note any deviations from the plan
- Note any files created not listed here
- Confirm 4 tables + seed data created

---

## Phase 2 — Workflow Field Conditions + Engine Refactor

### V20 Migration: `V20__workflow_field_conditions.sql`
```sql
CREATE TABLE workflow_condition_group (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_definition_id  UUID NOT NULL REFERENCES workflow_definition(id),
    group_order             INTEGER NOT NULL DEFAULT 0,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE workflow_field_condition (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    condition_group_id  UUID NOT NULL REFERENCES workflow_condition_group(id),
    field_definition_id UUID NOT NULL REFERENCES exchange_field_definition(id),
    operator            VARCHAR(20) NOT NULL,
      -- EQUALS | NOT_EQUALS | IS_TRUE | IS_FALSE
      -- CONTAINS | NOT_CONTAINS | ANY_SELECTED | ALL_SELECTED
    expected_value      TEXT,   -- null for IS_TRUE / IS_FALSE
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);
```

**Evaluation semantics:**
- Groups are **OR-ed**; conditions within a group are **AND-ed**
- V1 UI creates exactly one group per workflow (AND-only, same behavior as today)
- Future UI can add multiple groups for `(A AND B) OR (C AND D)`

---

### Backend — Phase 2

**New entities**: `WorkflowConditionGroup` (`@ManyToOne` to `WorkflowDefinition`), `WorkflowFieldCondition` (`@ManyToOne` to group + `ExchangeFieldDefinition`)

**New repositories**:
- `WorkflowConditionGroupRepository` — `findAllByWorkflowDefinitionId(...)`, `deleteAllByWorkflowDefinitionId(...)`
- `WorkflowFieldConditionRepository` — `findAllByConditionGroupId(groupId)`

**`WorkflowDefinitionService`** — create/update: delete + re-insert all groups and conditions (simple replace)

**`DefaultWorkflowEngineService.trigger()` — refactored flow**:
```
findAllActiveForTrigger(event, orgId)
  → filterByFieldConditions(definitions, request.subjectData)   ← NEW
    → for each passing definition → createInstance → createStep → activateStep
```
```kotlin
private fun filterByFieldConditions(
    definitions: List<WorkflowDefinition>,
    subjectData: Map<String, String>
): List<WorkflowDefinition> = definitions.filter { definition ->
    val groups = conditionGroupRepo.findAllByWorkflowDefinitionId(definition.id)
    if (groups.isEmpty()) return@filter true  // no conditions = triggers all
    groups.sortedBy { it.groupOrder }.any { group ->          // OR across groups
        conditionRepo.findAllByConditionGroupId(group.id).all { condition ->  // AND within group
            val actual = subjectData["fields.${condition.fieldDefinition.key}"]
            evaluateFieldCondition(condition.operator, actual, condition.expectedValue)
        }
    }
}

private fun evaluateFieldCondition(operator: String, actual: String?, expected: String?): Boolean =
    when (operator) {
        "EQUALS"       -> actual == expected
        "NOT_EQUALS"   -> actual != expected
        "IS_TRUE"      -> actual == "true"
        "IS_FALSE"     -> actual == "false" || actual == null
        "CONTAINS"     -> actual != null && expected != null && actual.contains(expected)
        "NOT_CONTAINS" -> actual == null || expected == null || !actual.contains(expected)
        "ANY_SELECTED" -> actual != null && expected != null && parseJsonArray(actual).contains(expected)
        "ALL_SELECTED" -> expected != null && parseJsonArray(expected).all { e ->
                              parseJsonArray(actual ?: "[]").contains(e)
                          }
        else           -> false
    }
```
The filter reads only from `request.subjectData` (already enriched with `"fields.<key>"` by `ExchangeInitiationService` in Phase 1). The engine stays decoupled from `ExchangeFieldValueService`.

> Integration note: `trigger()` was refactored during the JSON-normalization pass into
> `trigger()` (loads definitions, loops) + `triggerOne(definition, request)`. Insert
> `filterByFieldConditions(...)` in `trigger()` right after `findAllActiveForTrigger(...)` and
> loop `triggerOne` over the filtered list. Inject `conditionGroupRepo` / `conditionRepo`
> alongside the existing `assigneeRepository` / `decisionRepository` injections.

**`WorkflowDefinitionDto`** — add `conditionGroups: List<WorkflowConditionGroupDto>`:
```kotlin
data class WorkflowConditionGroupDto(
    val id: UUID, val groupOrder: Int,
    val conditions: List<WorkflowFieldConditionDto>
)
data class WorkflowFieldConditionDto(
    val id: UUID, val fieldDefinitionId: UUID,
    val fieldKey: String, val fieldName: String,   // denormalized for display
    val operator: String, val expectedValue: String?
)
```

**`WorkflowDefinitionResource`** — include `conditionGroups` in GET; accept in POST/PUT

---

### Frontend — Phase 2

**`WorkflowDesigner.tsx`** — "Field Conditions" section between Trigger Event and Steps:
- Label: **"Apply only when conditions match"**; V1 renders a single AND group as a flat list with `+` Add Condition
- Operators filtered by field type (CHECKBOX → IS_TRUE/IS_FALSE; SELECT → EQUALS/NOT_EQUALS; MULTISELECT/CHECKLIST → ANY_SELECTED/ALL_SELECTED; TEXT/TEXTAREA → EQUALS/NOT_EQUALS/CONTAINS/NOT_CONTAINS)
- Reuses `FieldValueInput` (compact; hidden for IS_TRUE/IS_FALSE); "AND" visual separator between rows
- State: `conditionGroups: WorkflowConditionGroupDraft[]` in `WorkflowDesignerState`

**`WorkflowsListView.tsx`** — badge "N field conditions" when conditions exist

---

### After completing Phase 2 — UPDATE THIS FILE
- Mark Phase 2 complete with date
- Note deviations
- Confirm regression: no-condition workflows still trigger on all exchanges

---

## Phase 3 — Blueprint Field Defaults (Separate Table)

### V21 Migration: `V21__blueprint_field_defaults.sql`
```sql
CREATE TABLE blueprint_field_default (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    blueprint_definition_id UUID NOT NULL REFERENCES blueprint_definition(id),
    field_definition_id     UUID NOT NULL REFERENCES exchange_field_definition(id),
    text_value              VARCHAR(5000),   -- TEXT, TEXTAREA, SELECT
    bool_value              BOOLEAN,         -- CHECKBOX
    json_value              TEXT,            -- MULTISELECT: '["legal","hr"]'
                                             -- CHECKLIST: '["opt1","opt3"]' (pre-checked option values)
    display_order           INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_blueprint_field UNIQUE (blueprint_definition_id, field_definition_id)
);
```
CHECKLIST defaults store pre-checked option `value` strings in `json_value`. No `checked_by`/`checked_at` — those only apply to live exchanges.

---

### Backend — Phase 3

**Follow the exact pattern already established in V18 for `blueprint_document_default` /
`blueprint_participant_default`** (see `BlueprintDefinitionService.kt`). Blueprint child
collections are NOT JPA `@OneToMany` cascades — they are standalone entities persisted
explicitly through their repository. Mirror that, do not invent a new approach.

- **New entity `BlueprintFieldDefault.kt`** — standalone `@Entity` with `blueprintDefinitionId: UUID`, `fieldDefinitionId: UUID`, typed `textValue`/`boolValue`/`jsonValue`, `displayOrder`. (Mirror `BlueprintDocumentDefault.kt`.)
- **New repository `BlueprintFieldDefaultRepository`** — `findAllByBlueprintDefinitionId(...)` (ordered by `display_order`) + `deleteAllByBlueprintDefinitionId(...)`. (Mirror `BlueprintDocumentDefaultRepository`.)
- **`BlueprintDefinitionService`** — inject the new repo; add private helpers mirroring the existing ones:
  - `persistFieldDefaults(blueprintId, defaults)` — delete-then-insert (like `persistDocuments`)
  - `loadFieldDefaults(blueprintId)` — map rows to `BlueprintFieldDefaultDto` (like `loadDocuments`)
  - `createBlueprint` → call `persistFieldDefaults(bp.id, request.fieldDefaults)`
  - `updateBlueprint` → `request.fieldDefaults?.let { persistFieldDefaults(bp.id, it) }`
  - `cloneBlueprint` → extend `copyChildren(sourceId, targetId)` to also copy field-default rows
  - `toDto` → add `fieldDefaults = loadFieldDefaults(id)`
- **`BlueprintDtos.kt`** — add `fieldDefaults: List<BlueprintFieldDefaultDto>` to `BlueprintDefinitionDto`; add `fieldDefaults` to `CreateBlueprintRequest` (default `emptyList()`) and `UpdateBlueprintRequest` (nullable; null = leave unchanged). `BlueprintConfigJson` already holds scalars only — do NOT add field data to it.

---

### Frontend — Phase 3

Mirror how `exchangeDocuments` / `participants` are already wired as typed arrays (the V18
refactor), not the old configJson route.

- **`models.tsx`** — add `fieldDefaults: BlueprintFieldDefaultDto[]` to `BlueprintDefinitionSummaryDto`; add `fieldDefaults?: BlueprintFieldDefaultDto[]` to `CreateBlueprintRequest` and `UpdateBlueprintRequest`. (Sits beside the existing `exchangeDocuments` / `participants` arrays.)
- **`ExchangeInitiation.tsx` — `handleBlueprintSelect`** — read `blueprint.fieldDefaults` from the DTO (like `blueprint.exchangeDocuments` today); map into typed `fieldValues` state. `buildBlueprintConfigJson` stays scalars-only (field defaults are passed separately, like `buildBlueprintDocuments()`).
- **`SaveBlueprintDialog.tsx`** — add a `fieldDefaults` prop and include it in the create request (mirror the `exchangeDocuments` / `participants` props added in V18).
- **`BlueprintPicker.tsx`** — render badge pills from `blueprint.fieldDefaults`:
  - CHECKBOX `SENSITIVE_FLAG` + `boolValue === true` → `<ShieldRegular />` "Highly Sensitive"
  - SELECT → `textValue` resolved to option `display_name` via embedded definition
  - MULTISELECT/CHECKLIST → count badge e.g. "2 items"
- **`settings/templates-tab/BlueprintEditorDialog.tsx`** — add a "Field Defaults" section in a new editor tab. Hold defaults in dedicated `fieldDefaults` state (mirror the `documents` state pattern), load from `blueprint.fieldDefaults` on open, send as `req.fieldDefaults` on save. Use `FieldValueInput` (compact) per picked field definition.

---

### After completing Phase 3 — UPDATE THIS FILE
- Mark Phase 3 complete with date
- Note deviations
- Confirm field defaults survive create → save → select cycle

---

## Cross-Cutting Concerns

### Search & Reporting (forward compatibility)
Typed columns enable direct SQL without JSON parsing:
```sql
SELECT e.* FROM exchange e
JOIN exchange_field_value v ON v.exchange_id = e.id
JOIN exchange_field_definition d ON d.id = v.field_definition_id
WHERE d.key = 'category' AND v.text_value = 'legal';
```
MULTISELECT containment: `v.json_value::jsonb @> '["hr"]'` (add GIN index when reporting scales). No schema change needed.

### Migration Strategy
Platform is pre-production — no data migration, only forward additive schema.
Per phase: apply Flyway migration → deploy backend (new endpoints, existing unchanged) → deploy frontend (new tab only).
**Backward compatibility:** workflows with no condition groups continue triggering on all exchanges — zero behavior change.

---

## FINAL VALIDATION — After All 3 Phases Are Complete

**1. Read `AGENTS.md` again.** Confirm the implementation follows every convention it defines.

**2. Read this file (`FIELDS-FEATURE.md`) again top to bottom.** Walk through every bullet in every phase and confirm it was implemented.

**3. Run the verification checklist below.**

---

### Verification Checklist

**Phase 1 — Core Fields:**
- [ ] 4 tables created + seed rows for `highly_sensitive` and `category` present
- [ ] `GET /field-definitions` returns both platform defaults for any org member
- [ ] Org admin creates a required TEXT field; non-admin `POST /field-definitions` → 403
- [ ] PLATFORM-scope field `PUT`/`PATCH` → 403
- [ ] Exchange creation: missing required field → 400 naming the field; UI navigates to Fields tab
- [ ] Category = Legal + Highly Sensitive = true saved and returned in `exchangeFields`
- [ ] CHECKLIST: checking an item writes `checked_by` + `checked_at` in `exchange_checklist_item_value`
- [ ] Highly Sensitive = true → warning banner and shield badge in exchange header
- [ ] Guest/magic-link access → response contains NO `INTERNAL` field values
- [ ] Settings → Fields tab visible to all; PLATFORM rows read-only, ORG rows editable

**Phase 2 — Workflow Conditions:**
- [ ] 2 tables created: `workflow_condition_group`, `workflow_field_condition`
- [ ] Workflow with no groups → triggers on all exchanges (regression)
- [ ] Condition Category EQUALS `"legal"` → no instance for Finance exchange
- [ ] Condition Category EQUALS `"legal"` → instance created for Legal exchange
- [ ] Two conditions in one group (AND) → both must match to trigger
- [ ] Designer saves, reloads, deletes conditions correctly; list card shows condition-count badge

**Phase 3 — Blueprint Defaults:**
- [ ] `blueprint_field_default` table created
- [ ] Blueprint with Category = Legal + Highly Sensitive saved with field defaults
- [ ] Blueprint picker card shows "Legal" badge and Highly Sensitive shield
- [ ] Selecting blueprint pre-populates Fields tab (typed values, not configJson)
- [ ] User can override field defaults before submitting exchange

**Final integrity:**
- [ ] `AGENTS.md` re-read; all conventions followed
- [ ] All new entities follow existing naming/package conventions
- [ ] All migrations are additive only (no column drops, no data loss)
- [ ] `npx tsc --noEmit` passes (vite build uses esbuild and does NOT type-check) AND `vite build` succeeds
- [ ] Backend compiles (`./mvnw -o compile`) and Flyway migrations apply against PostgreSQL 15 (Hibernate runs in `validate` mode — entity/column mismatches fail at boot)
- [ ] Existing workflow behavior unchanged (no-condition workflows still fire)
