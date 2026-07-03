# Exchange Field Selection at Creation Plan

## Status

Done. Depends on the completed Fields and Business Schema engine (see `FIELDS-FEATURE.md`) and
the existing Exchange initiation flow (`service/exchange/ExchangeInitiationService.kt`,
`web-app/src/app/exchange-initiation`).

This is the foundational plan of the three related Fields plans. See "Recommended Implementation
Order" at the end.

## Problem

The Fields engine lets an organization assign a business schema to an Exchange and record typed
field values. Today this only happens **after** the Exchange already exists, via the Exchange
detail "Fields" tab (`PUT /exchanges/{id}/schema`, then `PUT /exchanges/{id}/fields`).

When a user starts a new Exchange (without a blueprint), there is no opportunity to classify it with
a schema and enter field values as part of the creation wizard. This is confusing (the feature looks
unused) and it breaks the intended lifecycle ordering: workflows fire during creation, so any
workflow that wants to gate on a field value (see `WORKFLOW-FIELD-APPLICABILITY-PLAN.md`) sees no
values because they have not been entered yet.

`FIELDS-FEATURE.md` ("Keep the First User Experience Small") explicitly calls for: "Exchange
creators select an applicable schema and enter values." This plan delivers that at creation time.

## Confirmed Current State (verified)

Backend:
- `POST /exchanges` -> `ExchangeResource.initiateExchange(ExchangeInitiationDto)` ->
  `ExchangeInitiationService.initiateExchange(...)` (`@Transactional`). The Exchange is created with
  `status = ExchangeStatus.INITIATED`, documents are attached, the exchange is saved, and workflows
  are fired near the end of the method.
- `ExchangeInitiationDto` (`resource/model/RequestsResponses.kt`) has **no** schema/field fields.
- Schema assignment and value setting live in `SchemaAssignmentService`:
  - `assignSchema(resourceType, resourceId, schemaDefinitionId)` pins the latest published
    `schemaVersionId`.
  - `setValues(resourceType, resourceId, values)` where each value is
    `FieldValueEntry(fieldContractId: UUID, value: JsonElement)`.
  - Exposed via `ExchangeFieldsResource`: `PUT /exchanges/{id}/schema` (body
    `AssignSchemaRequest { schemaDefinitionId }`) and `PUT /exchanges/{id}/fields` (body
    `SetFieldValuesRequest { values: List<FieldValueEntry> }`).
  - The Exchange `FieldResourceAdapter` allows value edits only while the Exchange is `INITIATED`.

Frontend:
- `web-app/src/app/exchange-initiation/ExchangeInitiation.tsx` is a multi-step wizard:
  optional blueprint picker -> Recipients -> Details -> Documents -> Options, then
  `initiateExchange(exchange)` (from `services/exchangeApi.ts`) with an `ExchangeInitiationRequest`.
- `ExchangeInitiationRequest` (`models.tsx`) has **no** schema/field fields.
- The existing Fields tab already has the reusable pieces: `fieldsService.ts`
  (`listSchemas`, `getResolvedSchema`, `getExchangeSchema`, `assignExchangeSchema`,
  `setExchangeFieldValues`), plus `SchemaAssignPanel`, `FieldValuesForm`, and `FieldValueEditor`.

## Confirmed Decisions

1. **Scope: creation-time selection of one schema plus its values, without a blueprint.** The
   post-creation Fields tab remains for later edits while `INITIATED`.
2. **Backend applies fields inside the same transaction, before workflows fire.** This guarantees
   that workflow applicability (and any workflow assignee logic) can observe the values.
3. **Fully optional and backward compatible.** If no schema is chosen, behavior is unchanged.
4. **Fail the whole creation on invalid schema/values.** `initiateExchange` is already
   `@Transactional`; validation errors roll back the Exchange too (no half-created state).
5. **Only published, non-retired schemas whose `targetResourceType == "EXCHANGE"` are selectable.**

## Approach

Extend the existing single create call rather than making the client issue three sequential calls.
Add an optional schema + values block to `ExchangeInitiationDto`. In
`ExchangeInitiationService.initiateExchange`, after the Exchange is persisted and before workflows
are fired, if a schema was supplied, delegate to `SchemaAssignmentService.assignSchema` and
`setValues` (services communicate service-to-service, never cross-repository, per backend rules).

On the frontend, add one wizard step ("Business Fields") that appears only when the org has at least
one eligible published Exchange schema. It reuses the existing schema list + resolved-schema +
field-editor components to collect a `schemaDefinitionId` and a list of values, which are included
in the create request.

## Design Detail

### Backend

- **DTO (`ExchangeInitiationDto`):** add two optional fields:
  - `var schemaDefinitionId: String? = null`
  - `var fieldValues: List<FieldValueEntry>? = null` (reuse the existing `FieldValueEntry`
    `{ fieldContractId, value }`; keep the value canonical/JSON as the Fields tab already sends).
  - Do **not** add a schema-assignment-source field yet; creation-time assignment is `MANUAL`.
    `BLUEPRINT` source is handled in `BLUEPRINT-SCHEMA-INTEGRATION-PLAN.md`.
- **Service (`ExchangeInitiationService.initiateExchange`):** after `exchangeRepository` save and
  **before** the workflow-firing block:
  - If `schemaDefinitionId != null`: call
    `schemaAssignmentService.assignSchema("EXCHANGE", exchange.id, UUID.fromString(schemaDefinitionId))`.
  - Then, if `fieldValues` is non-empty: call
    `schemaAssignmentService.setValues("EXCHANGE", exchange.id, fieldValues)`.
  - Let `FieldValidationException` / `IllegalArgumentException` propagate so the transaction rolls
    back. Add a focused error message identifying the schema/field problem.
  - Guard: if `fieldValues` is provided without `schemaDefinitionId`, reject with a clear message.
- **Authorization:** `assignSchema` / `setValues` already run the Exchange
  `FieldResourceAdapter.authorizeManageFields` and `valuesEditable` checks. The initiating principal
  is the Exchange owner and the Exchange is `INITIATED`, so both pass. No new authz code, but add a
  test asserting a non-owner path still fails.
- **No Flyway migration** is required. No schema changes.

### Frontend

- **`models.tsx`:** add optional `schemaDefinitionId?: string` and
  `fieldValues?: { fieldContractId: string; value: unknown }[]` to `ExchangeInitiationRequest`.
- **New wizard step component** (co-located, follows all frontend rules: circular buttons, ids,
  one-attribute-per-line, `makeStyles` in a `*Styles.tsx`, no inline styles, responsive, under
  ~150 lines):
  `exchange-initiation/components/exchange-initiation-fields-tab/ExchangeInitiationFieldsTab.tsx`
  (+ styles). It:
  - Calls `listSchemas()` filtered to `latestPublishedVersion` set, `status != RETIRED`, and
    `targetResourceType === 'EXCHANGE'`.
  - On schema pick, calls `getResolvedSchema(id)` and renders a values form reusing the
    `FieldValueEditor` pattern from the Fields tab (extract a shared presentational form if needed
    so both the tab and the wizard use the same editor without duplicating logic).
  - Emits `{ schemaDefinitionId, fieldValues }` into the initiation state hook.
- **`ExchangeInitiation.tsx`:** insert the step after "Details" (before "Documents"). Only show the
  tab when the eligible-schema list is non-empty (fetch once on wizard open). Include
  `schemaDefinitionId` and `fieldValues` in the object passed to `initiateExchange(...)`.
- **State:** extend `useExchangeInitiatingState()` with `schemaDefinitionId` and a values map.
- **Blueprint interaction:** when a blueprint is selected, this step is pre-populated by
  `BLUEPRINT-SCHEMA-INTEGRATION-PLAN.md`. This plan only wires the manual (no-blueprint) path; the
  request fields it adds are the exact seam that plan reuses.

### Docs

- Update `web-app/src/app/components/help-docs/sections/articles/usingExchangeFieldsArticle.tsx`
  (and `fieldsOverviewArticle.tsx` if it describes navigation) to state that a schema and values can
  be chosen while starting an Exchange, in addition to the post-creation Fields tab.
- Run the `AGENTS.md` post-change docs checklist: grep existing coverage, verify accuracy, honor
  size limits (articles < 150 lines, sections < 300, registry < 60), and run `tsc --noEmit`.

### Tests and Verification

- Backend unit/integration tests for `initiateExchange`:
  - schema + valid values -> assignment created, values stored, source `MANUAL`.
  - values present but schema absent -> rejected.
  - invalid value (fails `FieldValueValidator`) -> whole creation rolled back (no Exchange row).
  - values applied before workflow firing (assert ordering, e.g. a workflow that reads a value).
  - no schema supplied -> unchanged behavior.
- Backend: `mvnw -o test` green.
- Frontend: `node ./node_modules/typescript/bin/tsc --noEmit` clean; add a Vitest test for the new
  step's schema filtering and payload assembly.

## Work Breakdown

1. Add `schemaDefinitionId` / `fieldValues` to `ExchangeInitiationDto`.
2. Apply schema + values in `initiateExchange` before workflows fire; validate and roll back on
   error.
3. Backend tests for ordering, validation, rollback, and authz.
4. Add the two optional fields to `ExchangeInitiationRequest` (frontend models).
5. Extract/share the field-values editor so the wizard and Fields tab reuse one component.
6. Build `ExchangeInitiationFieldsTab` and wire it into the wizard (conditional on eligible
   schemas), threading values into `initiateExchange`.
7. Update Fields help docs; run the docs checklist.
8. Final verification: backend suite green and `tsc` clean.

## Notes and Considerations

- **No migration:** purely additive DTO + service wiring.
- **Backward compatible:** absent block leaves current behavior unchanged.
- **Enables `WORKFLOW-FIELD-APPLICABILITY-PLAN.md`:** values now exist at trigger time.
- **Reused by `BLUEPRINT-SCHEMA-INTEGRATION-PLAN.md`:** the new request fields are the injection
  point for blueprint-seeded schema + defaults.
- **Explicitly deferred:** multiple schemas per Exchange, editing values after `INITIATED`, and
  cross-resource (non-Exchange) creation flows.

## Recommended Implementation Order (all three plans)

1. **This plan (Exchange fields at creation)** - foundational; surfaces the feature and adds the
   creation-time seam the other plans rely on.
2. **`WORKFLOW-FIELD-APPLICABILITY-PLAN.md`** - delivers the headline "gate a workflow on a field
   value" capability; only meaningful once values exist at trigger time (this plan).
3. **`BLUEPRINT-SCHEMA-INTEGRATION-PLAN.md`** - convenience layer that auto-seeds schema + default
   values from a blueprint, reusing this plan's creation-time seam.

Plans 2 and 3 are independent of each other and can proceed in parallel after this plan lands.

## Implementation Progress Log

> Update this section at the end of every work session and after each completed Work Breakdown item.
> Keep it truthful and specific so a future session can resume without re-discovering context. For
> each entry record: date, which items are done, the exact files touched, decisions/deviations from
> this plan, test status, and the single most useful "next step". Mark the Status field at the top
> of this document (Planned -> In Progress -> Done) as it changes.

**Current status:** Done. All 8 Work Breakdown items complete. Backend suite green (244 tests);
web-app `tsc --noEmit` clean; new Vitest helpers green.

**Next step:** Proceed to `WORKFLOW-FIELD-APPLICABILITY-PLAN.md` and
`BLUEPRINT-SCHEMA-INTEGRATION-PLAN.md` (both may run in parallel now).

**Work Breakdown status:**

- [x] 1. `schemaDefinitionId` / `fieldValues` on `ExchangeInitiationDto`.
- [x] 2. Apply schema + values in `initiateExchange` before workflows fire; validate + roll back.
- [x] 3. Backend tests: ordering, validation, rollback, authz.
- [x] 4. Add the two optional fields to `ExchangeInitiationRequest` (frontend models).
- [x] 5. Extract/share the field-values editor (used by wizard and Fields tab).
- [x] 6. Build `ExchangeInitiationFieldsTab`; wire into the wizard (conditional on eligible schemas).
- [x] 7. Update Fields help docs; run the docs checklist.
- [x] 8. Final verification: backend suite green and `tsc` clean.

**Handoff to next plan:** the creation-time seam is live.
- Request fields (backend `ExchangeInitiationDto`, frontend `ExchangeInitiationRequest`):
  `schemaDefinitionId: String?` and `fieldValues: List<FieldValueEntry>?`
  (`FieldValueEntry { fieldContractId: UUID, value: JsonElement }`).
- Application happens in `ExchangeInitiationService.applyCreationTimeFields(exchangeId, dto)`, called
  from `initiateExchange` immediately after `exchangeRepository.save(exchange)` /library copy and
  BEFORE the first `workflowEngineService.trigger(...)`. It calls
  `schemaAssignmentService.assignSchema("EXCHANGE", exchangeId, schemaDefinitionId)` then
  `setValues("EXCHANGE", exchangeId, fieldValues)`. Assignment source is `MANUAL` (set inside
  `assignSchema`).
- For BLUEPRINT (plan 3): add `schemaAssignmentSource` to `ExchangeInitiationDto` and thread it into
  an `assignSchema` overload; frontend maps blueprint defaults into `fieldValues` and sets the source.

**Session entries (newest first):**

### 2026-07-03 - Plan 1 implemented end-to-end
- Completed items: 1-8.
- Files touched:
  - `src/main/kotlin/com/docuhyphen/app/api/resource/model/RequestsResponses.kt` (DTO fields + import).
  - `src/main/kotlin/com/docuhyphen/app/api/service/exchange/ExchangeInitiationService.kt`
    (injected `SchemaAssignmentService`, added `applyCreationTimeFields`, called before workflows).
  - `src/test/kotlin/com/docuhyphen/app/api/service/exchange/ExchangeInitiationFieldsTest.kt` (new, 6 tests).
  - `web-app/src/app/models/models.tsx` (`ExchangeInitiationRequest` + fields).
  - `web-app/src/app/exchange-initiation/hooks/useExchangeInitiatingState.ts` (schema/value/bindings state).
  - `web-app/src/app/exchange-initiation/components/exchange-initiation-fields-tab/`
    (`ExchangeInitiationFieldsTab.tsx`, `ExchangeInitiationFieldsTabStyles.tsx`,
    `creationFieldsUtils.ts`, `creationFieldsUtils.test.ts`).
  - `web-app/src/app/exchange-initiation/ExchangeInitiation.tsx` (fetch eligible schemas on open,
    Business Fields step, payload assembly, resets).
  - `web-app/src/app/exchange-initiation/components/exchange-initiation-dialog-title-section/ExchangeInitiationDialogTitleSection.tsx`
    (conditional "Business Fields" tab).
  - `web-app/src/app/components/help-docs/sections/articles/usingExchangeFieldsArticle.tsx` (creation-time note).
- Decisions / deviations: no Quarkus/DB integration harness exists (pure Mockito), and driving the
  full `initiateExchange` would require mocking a large, brittle surface. Made the seam helper
  `applyCreationTimeFields` `internal` and unit-tested it directly (validation, rollback-propagation,
  authz-propagation, no-schema, MANUAL). The "values before workflows" ordering is guaranteed
  structurally by the single call site (helper runs before the trigger block), documented in the test.
  Reused the existing presentational `FieldValueEditor` + `toCanonicalValue` rather than extracting a
  new shared form (no duplication needed). Extracted pure `creationFieldsUtils` for testable schema
  filtering + payload assembly (no jsdom/testing-library available for component-render tests).
- Tests: backend `mvnw -o test` = 244 passed / 0 failed. web-app `tsc --noEmit` clean;
  `vitest run` new helper suite = 7 passed.
- Next step: start plans 2 and 3.

