# Schema-Aware Workflow Applicability Plan

## Status

Done. Delivered on the completed Fields/Business Schema engine and the existing workflow engine
(`service/workflow`). Applicability gating is live and validated on save.

## Problem

The Fields engine lets organizations define fields, compose them into versioned schemas, assign
a schema to an Exchange, and record typed values. A primary motivation for Fields, letting a
workflow apply only when an Exchange's typed field values match (for example, a workflow that
runs only when `province == 'Ontario'`), is not yet implemented.

Today, when a trigger event fires, `DefaultWorkflowEngineService.trigger()` starts every active
definition registered for that `triggerEvent` + organization. There is no per-definition
applicability filter based on typed field values. The only conditional logic is a string
`predicateExpression` on `CONDITION` steps, evaluated against a `Map<String, String>` subject
snapshot, which the architecture explicitly warns against as a durable field reference.

## Confirmed Decisions

1. **Scope of this increment: applicability gate only.** A workflow starts only if the
   Exchange's typed field values match the definition's conditions. Mid-flow typed
   `CONDITION`-step branching is out of scope for this increment.
2. **Missing data behavior: non-match (skip).** If the Exchange has no assigned schema, the
   referenced field has no value, a retired option, or an unevaluatable value, the workflow does
   not start (treated as a non-match). Never fail-open.
3. **Combination logic: AND only.** All conditions must match for the workflow to apply.

## Approach

Add an optional `applicability` block to the workflow DSL (`WorkflowSpec`), stored inside the
existing `steps_json` text column. This needs no database migration and is version-frozen with
the definition, consistent with the "published configuration is immutable" principle.

At trigger time, before creating an instance, the engine evaluates the block against the subject
Exchange's typed field values via a Fields service method (never the Fields repositories
directly, per backend rules). If conditions are present and not all satisfied, that definition
is skipped. Conditions reference the immutable `fieldDefinitionId` (stable across schema
versions) plus a `FieldOperator` and a canonical literal value; they never reference display
labels or option labels.

The frontend workflow designer gains an "Applicability" section to author these conditions using
the organization's published schema fields. Behavior is fully backward compatible: definitions
with no `applicability` block behave exactly as today.

## Design Detail

### Backend

- **DSL (`WorkflowSpec.kt`):**
  - `val applicability: ApplicabilitySpec? = null` on `WorkflowSpec`.
  - `data class ApplicabilitySpec(val fieldConditions: List<FieldConditionSpec> = emptyList())`
    with AND semantics; null or empty means always applicable.
  - `data class FieldConditionSpec(val fieldDefinitionId: String, val fieldKey: String? = null,
    val valueType: FieldValueType, val operator: FieldOperator, val value: JsonElement? = null)`.
    `value` is a canonical literal, nullable for `IS_EMPTY` / `IS_NOT_EMPTY`. Serialized with the
    existing `WorkflowSpecJson` (`ignoreUnknownKeys` keeps it forward-compatible).
- **Fields query method (no cross-repo access):** add a public Fields service method, for example
  `ExchangeFieldQueryService.getCanonicalFieldValues(exchangeId)` returning canonical values
  keyed by `fieldDefinitionId` (or a small typed record including `valueType` and option codes),
  plus a `hasAssignedSchema` flag. The workflow engine calls this method only.
  - **Verified gap:** stored `FieldValue` rows are keyed by `fieldContractId` (see
    `FieldValue.fieldContractId`), not `fieldDefinitionId`. The new method must resolve each value's
    `fieldContractId` to its stable `fieldDefinitionId` via `FieldContract`, and must include the
    selection option codes from `FieldValueSelection` (`optionCode`) for `SINGLE_SELECT` /
    `MULTI_SELECT`. Reuse `CanonicalValueCodec` to produce the comparable canonical form.
- **Evaluator:** new `WorkflowApplicabilityEvaluator` (`service/workflow`), `@ApplicationScoped`.
  - `isApplicable(subjectResourceType, subjectResourceId, organizationId, applicability): Boolean`.
  - Only `EXCHANGE` subjects support field conditions. Null or empty block means true. A
    non-Exchange subject with conditions means false. No assigned schema or an absent referenced
    value means false.
  - Typed comparison per `FieldValueType` using `FieldOperator`, reusing the canonical codec and
    `FieldTypeContract` supported-operator sets. `SINGLE_SELECT` / `MULTI_SELECT` compare option
    codes, never labels. An unsupported operator for a type is a non-match plus a warning log.
- **Wire-in (`DefaultWorkflowEngineService.triggerOne` / `trigger`):** decode the spec, call the
  evaluator, and if not applicable log at debug and skip (return null) without creating an
  instance. Preserve the existing multi-definition loop behavior.
- **Validation (`WorkflowDefinitionService.validateStepsJson`):** when an `applicability` block is
  present, validate that the operator is allowed for the declared `valueType` and that a required
  `value` is present (except for `IS_EMPTY` / `IS_NOT_EMPTY`). Reject invalid definitions on save
  with a clear message.

### Frontend

- **`models.tsx`:** add `WorkflowFieldConditionSpec` and `WorkflowApplicabilitySpec`, and extend
  the designer's parsed spec typing to carry optional `applicability`. **Verified gap:** the
  `FieldValueType` enum already exists in `models.tsx`, but there is no `FieldOperator` TS enum;
  add one mirroring the backend `FieldOperator` (12 values) so the operator dropdown is typed.
- **`fieldsService.ts`:** reuse existing `listSchemas` / `getResolvedSchema` to populate a field
  picker (field label, `fieldDefinitionId`, `valueType`, options) for the org's published schemas.
- **Workflow designer:** new co-located component
  `workflows-tab/workflow-designer/applicability-editor/ApplicabilityEditor.tsx`
  (plus `ApplicabilityEditorStyles.tsx`): pick a schema, add and remove conditions (field
  dropdown, operator dropdown filtered by `valueType`, typed value editor reusing the Fields
  `FieldValueEditor` pattern). `WorkflowDesigner.tsx` `load()` reads `parsed.applicability`;
  `performSave()` serializes `{steps, applicability}`. Components stay under about 150 lines and
  follow all frontend rules (circular buttons, ids, one attribute per line, `makeStyles`, no
  inline styles, no forbidden characters, responsive).

### Docs

- Update the Workflows help-docs article(s) to document Applicability and typed field conditions:
  navigation path, AND semantics, non-match / skip behavior, and that conditions use stable field
  identities rather than labels.
- Update the Fields help-docs overview / "using exchange fields" article to note that field
  values can gate workflow applicability.
- Run the `AGENTS.md` post-change docs checklist: grep existing coverage, verify accuracy, honor
  size limits, and run `tsc --noEmit`.

### Tests and Verification

- Backend unit tests for `WorkflowApplicabilityEvaluator`: match, non-match, missing schema,
  missing value, retired option, each operator and type, non-Exchange subject, and empty block
  means applicable.
- Backend: `mvnw -o test` green (currently 234 tests).
- Frontend: `node ./node_modules/typescript/bin/tsc --noEmit` clean.

## Verification Findings (Gap Analysis)

The plan was re-verified against the current code. It is broadly accurate. The following facts and
gaps were confirmed and are now reflected above:

- **Confirmed present and reusable:**
  - `WorkflowSpec` (`service/workflow/WorkflowSpec.kt`) is `{ steps, onComplete, onReject }` with
    no `applicability` block today. `WorkflowSpecJson` uses `ignoreUnknownKeys = true`, so adding an
    optional block is forward/backward compatible with existing stored definitions.
  - `FieldOperator` enum exists (`service/fields/FieldOperator.kt`, 12 values) and
    `FieldValueType` exists (`entity/FieldsEnums.kt`, 9 values). The DSL can reference them directly.
  - `FieldTypeContract.supportedOperators` already defines the allowed operator set per type;
    reuse it for both evaluation and save-time validation (do not hardcode a second table).
  - `CanonicalValueCodec` + `CanonicalFieldValue` exist and give a typed comparable form; the
    evaluator reuses them instead of parsing display strings.
  - `DefaultWorkflowEngineService.trigger()` loops `findAllActiveForTrigger(triggerEvent, orgId)`
    and calls `triggerOne(definition, request)`. Wire the applicability check inside `triggerOne`
    immediately after `WorkflowSpecJson.decode(definition.stepsJson)` and before the instance is
    created; skip (return null) on non-match.

- **Gaps corrected in this plan:**
  1. **`fieldContractId` -> `fieldDefinitionId` resolution.** No existing service returns values
     keyed by the stable `fieldDefinitionId`. The new Fields query method must do this mapping and
     surface selection `optionCode`s. (Reflected in the Fields query-method bullet.)
  2. **No `FieldOperator` on the frontend.** Only `FieldValueType` exists in `models.tsx`; add a
     matching TS `FieldOperator` enum. (Reflected in the frontend bullet.)
  3. **`validateStepsJson` currently only decodes** (`WorkflowDefinitionService`); it performs no
     semantic checks today. Adding applicability validation is net-new. Ensure it runs on every
     write path that persists `stepsJson` (create, update, and clone), not just create.

- **APP-scoped definitions:** an `APP`-scoped definition can trigger for any organization, but
  ORG-specific `fieldDefinitionId`s are meaningless outside their owning org and will simply
  non-match (skip). For the first increment, either restrict the Applicability editor to
  `ORG`-scoped definitions or document that APP-scoped field conditions are effectively never
  applicable. Prefer restricting the editor.

## Preconditions and Sequencing (Important)

This applicability gate only produces useful behavior if the subject Exchange **already has an
assigned schema and field values at the moment the workflow trigger fires**.

Today, workflows are fired inside `ExchangeInitiationService.initiateExchange()` while the Exchange
is being created (`status = INITIATED`), but field values are only entered **after** creation via
the Exchange Fields tab (`PUT /exchanges/{id}/schema`, `PUT /exchanges/{id}/fields`). Consequently,
for any trigger that fires at creation time, this evaluator would see "no assigned schema / no
value" and correctly, but unhelpfully, skip every field-conditioned definition.

Therefore this plan is only end-to-end useful once field values can exist at trigger time. That is
delivered by **EXCHANGE-FIELDS-AT-CREATION-PLAN.md** (schema + values captured during creation,
applied before workflows fire) and/or **BLUEPRINT-SCHEMA-INTEGRATION-PLAN.md** (blueprint seeds the
values). Recommended order: implement exchange-fields-at-creation first, then this plan. The code
here does not depend on those plans, but the observable value does. The "non-match / skip" fail-safe
means shipping this plan first is harmless, just inert, until values are present.

## Work Breakdown

1. Add `ApplicabilitySpec` / `FieldConditionSpec` to the `WorkflowSpec` DSL.
2. Add a Fields service method returning canonical exchange field values.
3. Implement `WorkflowApplicabilityEvaluator` with typed operator logic.
4. Gate `trigger()` on applicability; skip non-matching definitions.
5. Validate the applicability block on workflow save.
6. Unit-test the evaluator; keep the full backend suite green.
7. Add TS types and field-picker data wiring on the frontend.
8. Build `ApplicabilityEditor` and wire it into `WorkflowDesigner`.
9. Update workflow and fields help docs; run the docs checklist.
10. Final verification: `tsc` clean and backend suite green.

## Notes and Considerations

- **No Flyway migration:** applicability lives in `steps_json` (text), frozen per definition
  version.
- **Durable references:** conditions reference `fieldDefinitionId` (immutable, stable across
  schema versions), never labels or option labels, satisfying the Workflow Integration
  durability rule in `FIELDS-FEATURE.md`.
- **Backward compatible:** an absent `applicability` block leaves current behavior unchanged.
- **Explicitly deferred:** typed `CONDITION`-step branching, schema version-range matching UI, OR
  groups, non-Exchange subjects, and Blueprint schema selection (covered separately by
  `BLUEPRINT-SCHEMA-INTEGRATION-PLAN.md`).

## Implementation Progress Log

> Update this section at the end of every work session and after each completed Work Breakdown item.
> Keep it truthful and specific so a future session can resume without re-discovering context. For
> each entry record: date, which items are done, the exact files touched, decisions/deviations from
> this plan, test status, and the single most useful "next step". Mark the Status field at the top
> of this document (Planned -> In Progress -> Done) as it changes.

**Current status:** Done. All 10 Work Breakdown items complete and verified.

**Next step:** None for this plan. Proceed to `BLUEPRINT-SCHEMA-INTEGRATION-PLAN.md` (Plan 3).

**Work Breakdown status:**

- [x] 1. `ApplicabilitySpec` / `FieldConditionSpec` in the `WorkflowSpec` DSL.
- [x] 2. Fields service method returning canonical exchange field values (keyed by
  `fieldDefinitionId`, incl. selection codes).
- [x] 3. `WorkflowApplicabilityEvaluator` with typed operator logic.
- [x] 4. Gate `trigger()` / `triggerOne()` on applicability; skip non-matching definitions.
- [x] 5. Validate the applicability block on workflow save (create + update + clone).
- [x] 6. Unit-test the evaluator; keep the full backend suite green.
- [x] 7. TS types (incl. new `FieldOperator` enum) and field-picker data wiring on the frontend.
- [x] 8. Build `ApplicabilityEditor`; wire into `WorkflowDesigner`.
- [x] 9. Update workflow and fields help docs; run the docs checklist.
- [x] 10. Final verification: `tsc` clean and backend suite green.

**Session entries (newest first):**

### 2026-07-03 - Full plan implemented end-to-end
- Completed items: 1-10.
- Files touched (backend):
  - `service/workflow/WorkflowSpec.kt` - added `applicability: ApplicabilitySpec?` plus
    `ApplicabilitySpec` and `FieldConditionSpec` (references immutable `fieldDefinitionId`,
    `valueType`, `FieldOperator`, canonical `JsonElement?` value). `WorkflowSpecJson` uses
    `ignoreUnknownKeys`, so existing stored definitions are unaffected.
  - `service/fields/ExchangeFieldQueryService.kt` (new) - `getCanonicalValues(exchangeId)` returns
    `ExchangeFieldSnapshot(hasAssignedSchema, Map<fieldDefinitionId, CanonicalFieldValue>)`,
    resolving each stored value's `fieldContractId` -> `FieldContract.fieldDefinitionId` and
    including selection option codes. Read-only; only touches Fields repos (same domain).
  - `service/workflow/WorkflowApplicabilityEvaluator.kt` (new) - `isApplicable(...)` (null/empty =
    applicable; non-Exchange / no schema / missing value / retired option / unparseable literal /
    unsupported operator = non-match, never fail-open; AND semantics) and `validate(applicability)`
    (operator must be in the type's `supportedOperators`; value required unless IS_EMPTY/IS_NOT_EMPTY;
    fieldDefinitionId must be a UUID). Typed per-type comparisons reuse `FieldTypeRegistry`.
  - `service/workflow/DefaultWorkflowEngineService.kt` - injected the evaluator; gate added in
    `triggerOne` after the empty-steps check and before instance creation (returns null + debug log
    on non-match).
  - `service/workflow/WorkflowDefinitionService.kt` - injected the evaluator; `validateStepsJson`
    now calls `validate(spec.applicability)`; clone also validates the scrubbed stepsJson (runs on
    create + update + clone).
- Files touched (backend tests):
  - `service/workflow/WorkflowApplicabilityEvaluatorTest.kt` (new) - 19 tests (empty block, non-Exchange,
    missing schema, missing value, text/number/boolean/select operators, IN, multi-select CONTAINS,
    retired option, IS_EMPTY/IS_NOT_EMPTY, AND semantics, validate rejections + accept).
- Files touched (frontend):
  - `models/models.tsx` - added `FieldOperator` enum, `WorkflowFieldConditionDraft`,
    `WorkflowApplicabilityDraft`, and `applicability?` on `WorkflowDesignerState`.
  - `settings/workflows-tab/workflow-designer/applicability-editor/` (new folder) -
    `applicabilityOperators.ts` (operator sets per type + `defaultConditionFor`),
    `ApplicabilityEditorStyles.tsx`, `ApplicabilityConditionRow.tsx`, `ApplicabilityEditor.tsx`,
    and `applicabilityOperators.test.ts` (7 Vitest tests).
  - `settings/workflows-tab/workflow-designer/WorkflowDesigner.tsx` - loads `parsed.applicability`,
    tracks `defScope`, renders `ApplicabilityEditor` only for ORG-scoped definitions, and persists
    `applicability` (omitted when empty) into `stepsJson`.
- Files touched (docs):
  - `help-docs/sections/articles/workflowApplicabilityArticle.tsx` (new, 53 lines),
    registered in `workflowsSection.tsx` as `workflow-applicability`; a short Step 5 pointer added
    to `buildingAWorkflowArticle.tsx` (kept at 147 lines).
- Decisions / deviations: none beyond the plan. Applicability restricted to ORG-scoped workflows in
  the editor (APP-scoped conditions can never match). Clone validation added per plan item 5. No DB
  integration harness exists, so tests are pure Mockito against the evaluator seam.
- Tests: backend `.\mvnw.cmd -o test` = 263 passed (was 244; +19). Frontend `tsc --noEmit` clean;
  Vitest applicabilityOperators = 7 passed. Docs sizes within limits (article 53/147, section 30,
  registry unchanged at 65 pre-existing).
- Next step: start Plan 3 (`BLUEPRINT-SCHEMA-INTEGRATION-PLAN.md`), item 1 (V38 migration).
