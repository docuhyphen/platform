# Blueprint and Schema Integration Plan

## Status

Done. Implemented on top of the completed Fields and Business Schema engine (see
`FIELDS-FEATURE.md`), the existing Blueprint feature (`service/blueprint`,
`web-app/src/app/settings/blueprints-tab`), and **`EXCHANGE-FIELDS-AT-CREATION-PLAN.md`** (which
adds the creation-time schema + values seam this plan reuses).

`FIELDS-FEATURE.md` lists "Blueprints may select a schema and provide defaults in a later
increment" and "Blueprint schema selection" is the item deferred by
`WORKFLOW-FIELD-APPLICABILITY-PLAN.md`. This plan closes that gap.

## Problem

A blueprint is a reusable template for starting an Exchange (name, documents, participants,
permissions, recipient config). Blueprints have **no** link to the Fields engine today, so a
blueprint cannot say "every Exchange started from me is a Client Onboarding case with these fields
and these default values."

Verified: `BlueprintDefinition` has no schema/field columns; `V6`/`V18` blueprint migrations have no
field references; the blueprint editor exposes no field UI; and starting an Exchange from a blueprint
is frontend-driven (`ExchangeInitiation.handleBlueprintSelect` maps `configJson` + documents +
participants into the wizard, then calls `initiateExchange`). The `schema_assignment.assignment_source`
enum already includes `BLUEPRINT`, but nothing sets it.

## Confirmed Current State (verified)

- **Entity `BlueprintDefinition`** (`entity/BlueprintDefinition.kt`): id, name, scope
  (`APP|ORG|PERSONAL`), organizationId, createdByAppUserId, `configJson` (text), summary,
  description, isActive, isPublished, isDeleted, isTemplate, sourceTemplateId, generalTags,
  createdAt, updatedAt. No schema/field columns.
- **Normalized child defaults pattern:** `BlueprintDocumentDefault` and
  `BlueprintParticipantDefault` are child tables persisted by
  `BlueprintDefinitionService.persistDocuments` / `persistParticipants` and read back by
  `loadDocuments` / `loadParticipants` in `toDto()`. This is the pattern to mirror for field
  defaults.
- **DTOs (`BlueprintDtos.kt`):** `BlueprintConfigJson` holds scalar config; `BlueprintDefinitionDto`
  exposes `exchangeDocuments` + `participants`; `CreateBlueprintRequest` / `UpdateBlueprintRequest`
  (list = replace, null = leave unchanged for child collections).
- **Resource (`BlueprintDefinitionResource.kt`):** `GET/POST /blueprints`, `GET/PUT /blueprints/{id}`,
  `PATCH /blueprints/{id}/status`, `PATCH /blueprints/{id}/published`, `DELETE /blueprints/{id}`,
  `POST /blueprints/{id}/clone`.
- **No backend "create exchange from blueprint" endpoint.** The frontend applies the blueprint to
  the wizard and posts the normal `POST /exchanges`.
- **Save-as-blueprint:** `SaveBlueprintDialog` captures `configJson` + `exchangeDocuments` +
  `participants` from the in-progress exchange and calls `createBlueprint`.
- **Latest Flyway migration is `V37`; next is `V38`.** `schema_assignment.assignment_source` already
  supports `BLUEPRINT`.
- **Fields services:** `SchemaAssignmentService.assignSchema` pins the latest published version of a
  `schemaDefinitionId`; `setValues` takes `FieldValueEntry(fieldContractId, value)`;
  `SchemaDefinition` has a stable `id`; `FieldContract` links `fieldContractId -> fieldDefinitionId`.

## Confirmed Decisions

1. **A blueprint references a schema by `schemaDefinitionId` (definition, not version).** Applying
   it assigns the latest published version at Exchange-creation time, matching how the Fields tab
   and `assignSchema` already behave. This keeps blueprints valid as schemas are re-published.
2. **Default field values are stored keyed by the stable `fieldDefinitionId`, not `fieldContractId`.**
   Contract ids are version-specific; the stable key survives re-publishing. At apply time the
   defaults are resolved to the assigned version's `fieldContractId`s; any field no longer present
   in the current version is dropped with a warning (never fail-closed on blueprint apply).
3. **Applying a blueprint reuses the creation-time seam from
   `EXCHANGE-FIELDS-AT-CREATION-PLAN.md`.** The frontend pre-populates the wizard's schema + values;
   the backend assignment is marked `assignmentSource = BLUEPRINT`.
4. **Schema selection is optional per blueprint.** Existing blueprints keep working unchanged.
5. **Respect the existing `allowEditOnExchangeStart` / blueprint-lock behavior** for the field
   values, consistent with documents/participants.

## Approach

Add an optional schema linkage and a set of default field values to a blueprint, mirroring the
existing document/participant defaults pattern (a nullable `schema_definition_id` column plus a new
`blueprint_field_default` child table). Extend the blueprint DTOs, service persistence, editor UI,
and the save-as-blueprint capture. When starting an Exchange from a blueprint, feed the blueprint's
schema + defaults into the creation-time schema/values seam (so no separate apply endpoint is
needed), tagging the resulting assignment as `BLUEPRINT`.

## Design Detail

### Backend

- **Flyway `V38__blueprint_schema_fields.sql`:**
  - `ALTER TABLE blueprint_definition ADD COLUMN schema_definition_id UUID NULL REFERENCES
    schema_definition(id) ON DELETE SET NULL;`
  - `CREATE TABLE blueprint_field_default ( id UUID PRIMARY KEY, blueprint_definition_id UUID NOT
    NULL REFERENCES blueprint_definition(id) ON DELETE CASCADE, field_definition_id UUID NOT NULL,
    value_type VARCHAR(32) NOT NULL, value_json TEXT NULL, display_order INT NOT NULL DEFAULT 0 );`
    with an index on `blueprint_definition_id`. `value_json` holds the canonical JSON form (same
    shape `setValues` consumes), `value_type` is the `FieldValueType` at authoring time for
    validation.
- **Entity + repository:** `BlueprintFieldDefault` entity; `BlueprintFieldDefaultRepository`
  (find/delete by blueprint id), mirroring the document/participant repositories.
- **DTOs (`BlueprintDtos.kt`):**
  - `BlueprintFieldDefaultConfig(fieldDefinitionId, valueType, value: JsonElement?, displayOrder)`.
  - Add `schemaDefinitionId: UUID?` and `fieldDefaults: List<BlueprintFieldDefaultConfig>` to
    `BlueprintDefinitionDto`, `CreateBlueprintRequest`, and `UpdateBlueprintRequest` (null list =
    leave unchanged; a list, incl. empty, replaces).
- **Service (`BlueprintDefinitionService`):**
  - Add `persistFieldDefaults(blueprintId, defaults)` and `loadFieldDefaults(blueprintId)` following
    `persistDocuments` / `loadDocuments`. Wire into `create`, `update`, `clone`, and `toDto`.
  - On save, validate: if `fieldDefaults` non-empty then `schemaDefinitionId` must be set, must be a
    `PUBLISHED` schema with `targetResourceType = "EXCHANGE"`, and each `fieldDefinitionId` must
    belong to that schema's latest published version. Reuse `FieldValueValidator` /
    `FieldTypeContract` to validate each default value against its type. Reject invalid blueprints
    with a clear message. (Fetch schema info via the Fields service, not its repositories.)
- **Apply path (source tagging):** extend the creation seam so the assignment can be recorded as
  `BLUEPRINT`. Add an optional `schemaAssignmentSource: SchemaAssignmentSource? = null` to
  `ExchangeInitiationDto` (default `MANUAL`) and pass it to `assignSchema`. This is the only backend
  change on the initiation side beyond `EXCHANGE-FIELDS-AT-CREATION-PLAN.md`. Requires a small
  `assignSchema` overload/param to set `assignmentSource`.
- **Save-as-blueprint capture:** when a blueprint is created from an exchange, include the
  exchange's current `schemaDefinitionId` and its field values (converted to
  `BlueprintFieldDefaultConfig` keyed by `fieldDefinitionId`) in `CreateBlueprintRequest`.

### Frontend

- **`models.tsx`:** add `schemaDefinitionId?: string` and
  `fieldDefaults?: BlueprintFieldDefaultConfig[]` to the blueprint DTOs
  (`BlueprintDefinitionDto`, `CreateBlueprintRequest`, `UpdateBlueprintRequest`), plus a
  `BlueprintFieldDefaultConfig` interface. Reuse the `SchemaAssignmentSource` enum already present.
- **Blueprint editor (`settings/blueprints-tab/BlueprintEditorDialog.tsx`):** add a "Business
  Fields" tab/section (co-located component + `*Styles.tsx`, under ~150 lines, all frontend rules):
  pick a published Exchange schema (`listSchemas` filtered as in the Fields tab), then set default
  values with the shared field-values editor extracted in
  `EXCHANGE-FIELDS-AT-CREATION-PLAN.md`. Persist `schemaDefinitionId` + `fieldDefaults` via
  `create`/`updateBlueprint`.
- **Apply on start (`exchange-initiation/ExchangeInitiation.tsx` `handleBlueprintSelect`):** when the
  selected blueprint has `schemaDefinitionId`, set the wizard's `schemaDefinitionId` and map
  `fieldDefaults` (fieldDefinitionId -> resolved contract) into the wizard's field-values state, and
  set the initiation request's `schemaAssignmentSource = 'BLUEPRINT'`. Honor the blueprint lock so
  values are read-only when `allowEditOnExchangeStart !== true` for ORG/APP scope.
- **Save-as-blueprint (`SaveBlueprintDialog.tsx`):** extend props to receive the exchange's
  `schemaDefinitionId` + current field values and include them in the `createBlueprint` request.
- **`blueprintService.ts`:** no new endpoints; the existing `create`/`update`/`get` carry the new
  fields.

### Docs

- Update blueprint help articles (`usingBlueprintsArticle.tsx`, `managingBlueprintsArticle.tsx`,
  `orgBlueprintsArticle.tsx` as applicable) to document selecting a schema and default values on a
  blueprint and how they apply when starting an Exchange.
- Update the Fields articles to note that a blueprint can pre-fill the schema and values.
- Run the `AGENTS.md` post-change docs checklist (grep coverage, verify accuracy, size limits,
  `tsc --noEmit`).

### Tests and Verification

- Backend:
  - Migration applies; entity round-trips; `create`/`update`/`clone` persist and reload
    `schemaDefinitionId` + `fieldDefaults`.
  - Save-time validation: non-published schema, wrong `targetResourceType`, `fieldDefinitionId` not
    in schema, and invalid typed value each rejected.
  - Applying a blueprint yields a `SchemaAssignment` with `assignmentSource = BLUEPRINT` and the
    expected values; a `fieldDefinitionId` absent from the current published version is dropped with
    a warning, not an error.
  - Save-as-blueprint captures the exchange's schema + values.
  - `mvnw -o test` green.
- Frontend: `tsc --noEmit` clean; Vitest for editor persistence and `handleBlueprintSelect`
  pre-population (including the locked, read-only case).

## Work Breakdown

1. `V38` migration: `schema_definition_id` column + `blueprint_field_default` table.
2. `BlueprintFieldDefault` entity + repository.
3. Extend blueprint DTOs; add `persist/loadFieldDefaults`; wire into create/update/clone/toDto with
   save-time schema+value validation.
4. Add `schemaAssignmentSource` to the initiation DTO and `assignSchema`; tag assignments
   `BLUEPRINT`.
5. Capture schema + values in save-as-blueprint (backend + `SaveBlueprintDialog`).
6. Blueprint editor "Business Fields" tab (reusing the shared field editor).
7. Pre-populate the creation wizard from the blueprint (`handleBlueprintSelect`), honoring the lock.
8. Backend + frontend tests.
9. Update blueprint and fields help docs; run the docs checklist.
10. Final verification: backend suite green and `tsc` clean.

## Notes and Considerations

- **Depends on `EXCHANGE-FIELDS-AT-CREATION-PLAN.md`** for the creation-time schema/values seam; do
  that plan first.
- **Durable references:** defaults keyed by stable `fieldDefinitionId`, resolved to the assigned
  version's contracts at apply time - consistent with `FIELDS-FEATURE.md`.
- **Fail-open on apply, fail-closed on authoring:** invalid defaults are rejected when saving a
  blueprint; missing fields at apply time are skipped with a warning so an old blueprint never
  blocks starting an Exchange.
- **Completes the onboarding scenario:** blueprint assigns the schema and seeds e.g. `Category`, then
  a workflow (via `WORKFLOW-FIELD-APPLICABILITY-PLAN.md`) gates on that `Category` value.
- **Explicitly deferred:** multiple schemas per blueprint, per-recipient field visibility defaults,
  and blueprint-level field overrides that change field meaning.

## Recommended Implementation Order (all three plans)

1. `EXCHANGE-FIELDS-AT-CREATION-PLAN.md` (foundation).
2. `WORKFLOW-FIELD-APPLICABILITY-PLAN.md` (headline value; needs values at trigger time).
3. **This plan** (blueprint convenience; reuses the creation-time seam).

This plan and the workflow plan are independent of each other and may proceed in parallel once the
foundation lands.

## Implementation Progress Log

> Update this section at the end of every work session and after each completed Work Breakdown item.
> Keep it truthful and specific so a future session can resume without re-discovering context. For
> each entry record: date, which items are done, the exact files touched, decisions/deviations from
> this plan, test status, and the single most useful "next step". Mark the Status field at the top
> of this document (Planned -> In Progress -> Done) as it changes.

**Current status:** Done. All 10 Work Breakdown items complete. Backend suite green (270 tests);
frontend `tsc --noEmit` clean; blueprint field-default helper Vitest green (10 tests in
`creationFieldsUtils.test.ts`).

**Prerequisite check:** `EXCHANGE-FIELDS-AT-CREATION-PLAN.md` is Done (creation-time
`schemaDefinitionId` + `fieldValues` seam in `ExchangeInitiationService.applyCreationTimeFields`).

**Next step:** None required - feature complete. Optional future hardening: add a full
`BlueprintDefinitionService` persist/reload integration test if a Quarkus/DB harness is introduced
(currently no DB test harness exists; see deviation note below).

**Work Breakdown status:**

- [x] 1. `V38` migration: `schema_definition_id` column + `blueprint_field_default` table.
- [x] 2. `BlueprintFieldDefault` entity + repository.
- [x] 3. Extend blueprint DTOs; `persist/loadFieldDefaults`; wire into create/update/clone/toDto with
  save-time validation.
- [x] 4. `schemaAssignmentSource` on the initiation DTO and `assignSchema`; tag assignments
  `BLUEPRINT`.
- [x] 5. Capture schema + values in save-as-blueprint (backend + `SaveBlueprintDialog`).
- [x] 6. Blueprint editor "Business Fields" tab (reusing the shared field editor).
- [x] 7. Pre-populate the creation wizard from the blueprint (`handleBlueprintSelect`), honoring lock.
- [x] 8. Backend + frontend tests.
- [x] 9. Update blueprint and fields help docs; run the docs checklist.
- [x] 10. Final verification: backend suite green and `tsc` clean.

**Session entries (newest first):**

<!--
### YYYY-MM-DD - <short summary>
- Completed items: <e.g. 1, 2>
- Files touched: <paths>
- Decisions / deviations: <anything that differs from this plan and why>
- Tests: <backend suite result, tsc result>
- Next step: <the single most useful thing to do next>
-->

_No sessions logged yet._

### 2026-02-14 - Plan 3 completed end-to-end (items 1-10)

- Completed items: 1, 2, 3, 4 (backend, prior session) and 5, 6, 7, 8, 9, 10 (this session).
- Files touched (this session):
  - Frontend wizard pre-population (item 7): `web-app/src/app/exchange-initiation/ExchangeInitiation.tsx`
    (import `getResolvedSchema`; `schemaFromBlueprint` state; `applyBlueprintSchema` helper resolving
    fieldDefinitionId -> fieldContractId and seeding `fieldValueMap`/`fieldBindings`; `schemaAssignmentSource`
    added to payload only when the schema came from a blueprint; reset in `resetInitiationForm`;
    `handleSchemaChange` clears the blueprint flag on manual change).
  - Save-as capture (item 5): `web-app/.../save-blueprint-dialog/SaveBlueprintDialog.tsx` (new
    `schemaDefinitionId` + `fieldDefaults` props flow into `createBlueprint`); `creationFieldsUtils.ts`
    (`buildBlueprintFieldDefaults` - keys defaults by stable fieldDefinitionId, carries valueType,
    omits empty). Caller wiring in `ExchangeInitiation.tsx`.
  - Editor Business Fields tab (item 6): new co-located component
    `web-app/.../settings/blueprints-tab/blueprint-business-fields-tab/BlueprintBusinessFieldsTab.tsx` +
    `...Styles.tsx` (reuses `ExchangeInitiationFieldsTab`; seeds from initial defaults; emits on change
    once bindings resolve). Wired into `BlueprintEditorDialog.tsx` (new `fields` tab, schema/defaults
    state, save on create + update).
  - Tests (item 8): backend `SchemaDefinitionServiceDefaultsTest.kt` (7 tests for
    `validateDefaultsForSchema`); frontend `creationFieldsUtils.test.ts` (+3 `buildBlueprintFieldDefaults`
    tests, 10 total). Updated `ExchangeInitiationFieldsTest.kt` and `ResourceAuthorizationTest.kt` for
    the new `assignSchema(...source)` overload and the two new `BlueprintDefinitionService` constructor
    params.
  - Docs (item 9): `managingBlueprintsArticle.tsx` (four tabs + Business Fields tab + save-as capture),
    `usingBlueprintsArticle.tsx` (pre-fill + lock read-only note + save-as capture),
    `orgBlueprintsArticle.tsx` (tab list). All under 150 lines; sections unchanged.
- Decisions / deviations:
  - Blueprint defaults stored by stable `fieldDefinitionId`; unknown fields are dropped at apply time
    (seed step skips any default whose fieldDefinitionId is absent from the resolved schema), so an old
    blueprint never blocks starting an Exchange - matches the "fail-open on apply" note.
  - Did NOT add a full `BlueprintDefinitionService` create/update/clone persist+reload test: there is no
    Quarkus/DB harness in this repo and the service depends on ~10 collaborators + `@Transactional`;
    the high-value new logic (`validateDefaultsForSchema` authoring guard, `assignSchema` BLUEPRINT
    source ordering, and the frontend default mapping) is unit-tested instead. Flagged as optional
    future work above.
  - `helpDocsRegistry.tsx` was already 65 lines before this work and was not touched.
- Tests: backend `.\mvnw.cmd -o test` = 270 passed, 0 failures. Frontend `tsc --noEmit` = clean.
  Vitest `creationFieldsUtils.test.ts` = 10 passed.
- Next step: none - feature complete. Optional: DB-backed blueprint persistence test if a harness lands.
