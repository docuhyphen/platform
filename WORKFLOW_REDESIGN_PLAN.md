# Workflow-Driven Exchange Lifecycle Redesign Plan

> **Coding rules**: Never use em dashes.
>
> This document captures the full context, current-state analysis, design decisions, and phased
> implementation plan for the workflow redesign. It is intended to bootstrap a new session with
> enough context to begin implementing without re-reading the entire codebase.

---

## Implementation Status

> Last updated: 2026-06-13 (Phase 5)

| Phase | Title | Status |
|---|---|---|
| 1 | Data Model and Engine Foundations | **COMPLETE** |
| 2 | Implement Stubbed Step Type Handlers | **COMPLETE** |
| 3 | Exchange Lifecycle Rewiring | **COMPLETE** |
| 4 | Workflow CRUD API | **COMPLETE** |
| 5 | Visual Designer UI | **COMPLETE** |

### Phase 1 - Completed work (2026-06-13)

Files modified:

| File | Change |
|---|---|
| `model/entity/WorkflowDefinition.kt` | Added `industryTags`, `summary`, `isTemplate`, `sourceTemplateId` |
| `model/entity/OrganizationSettings.kt` | Added `requireRecipientAcceptance: Boolean = true` |
| `service/workflow/WorkflowSpec.kt` | Added `messageTemplateKey`, `predicateExpression`, `onTrue`, `onFalse`, `addons` to `WorkflowStepSpec`; added `StepAddonSpec` sealed class (`ReminderBeforeDue`, `ReminderIfNoDecision`) |
| `web-app/src/app/models/models.tsx` | Added `requireRecipientAcceptance` to `OrganizationSettingsDto`; added `WorkflowDefinitionSummaryDto`, `WorkflowTriggerEventDto`, `WorkflowSubjectFieldDto`, `WorkflowInstanceSummaryDto`, `WorkflowStepInstanceDto`, `WorkflowPrincipalRefDto`, `WorkflowDecisionEntryDto` |

Files created:

| File | Purpose |
|---|---|
| `src/main/resources/db/migration/V5__workflow_lifecycle_redesign.sql` | Adds `industry_tags`, `summary`, `is_template`, `source_template_id` to `workflow_definition`; adds `require_recipient_acceptance` to `organization_settings`; adds `addons_state_json` to `workflow_step_instance`; creates and seeds `workflow_trigger_event_registry` with five canonical trigger events |

### Phase 2 - Completed work (2026-06-13)

Files created:

| File | Purpose |
|---|---|
| `service/workflow/WorkflowActionHandler.kt` | Interface (`key()`, `execute()`) + `ActionResult` data class |
| `service/workflow/actions/ExchangeAutoAcceptActionHandler.kt` | key `exchange.auto-accept` - sets exchange to `ACCEPTED_STARTED`, fires `exchange.activated` |
| `service/workflow/actions/ExchangeSendReminderActionHandler.kt` | key `exchange.send-reminder` - sends in-app + email reminder to `recipientId` from subject data |
| `service/workflow/actions/ExchangeRevokeAccessActionHandler.kt` | key `exchange.revoke-access` - calls `shareService.revokeAllForResource()` |

Files modified:

| File | Change |
|---|---|
| `model/entity/WorkflowStepInstance.kt` | Added `addonsStateJson: String = "{}"` (column already exists from V5) |
| `service/workflow/DefaultWorkflowEngineService.kt` | Full Phase 2 implementation: injected `Instance<WorkflowActionHandler>`, `AppNotificationService`, `EmailService`; `@PostConstruct` builds handler key map; `activateStep()` dispatcher; `executeNotificationStep()`, `executeConditionStep()`, `executeActionStep()` handlers; `processAddons()` + `dispatchAddonReminder()` for `ReminderBeforeDue` / `ReminderIfNoDecision` addons; `advanceOrComplete()` + updated `advanceToStep()` to handle auto-advance step chains; `evaluatePredicate()` for `==`, `!=`, `contains`, `startsWith`; `decodeAddonsState()` / `encodeAddonsState()` JSON helpers; addon processing integrated into `escalateOverdue()` |

### Phase 3 - Completed work (2026-06-13)

Files created:

| File | Purpose |
|---|---|
| `exception/WorkflowConflictException.kt` | HTTP 409 signal when a running workflow gates a direct status write. `@Transactional(dontRollbackOn)` ensures the workflow instance row persists. |

Files modified:

| File | Change |
|---|---|
| `repository/WorkflowInstanceRepository.kt` | Added `findRunningForSubjectAndTrigger(subjectResourceId, triggerEvent)` - JPQL Cartesian-join with `WorkflowDefinition` to find running instances by trigger event name |
| `service/exchange/ExchangeApprovalEventHandler.kt` | Full rewrite: fixed known gap (`session.activated` now also sets `exchange.status = ACCEPTED_STARTED`); added handling for `exchange.activated` (activate shares + set ACCEPTED_STARTED), `exchange.draft_approved` (fire `exchange.acceptance_pending` or auto-advance based on org setting), `exchange.ending` (set ENDED), `exchange.ended_confirmed` (set ENDED); injected `WorkflowEngineService`, `OrganizationMembershipService`, `OrganizationRepository` |
| `service/exchange/ExchangeInitiationService.kt` | Replaced `maybeTriggerGroupApproval()` with unified lifecycle trigger chain: fires `exchange.draft_submitted` (pre-send gate), then fires `exchange.acceptance_pending` (if `requireRecipientAcceptance = true`) or `exchange.activated` + sets `ACCEPTED_STARTED` (if false); `recipientNeedsApproval` now covers all recipient types, not just GROUP; injected `OrganizationMembershipService` and `OrganizationRepository` |
| `service/exchange/ExchangeUpdateService.kt` | Added workflow routing in `updateExchange()`: ACCEPTED_STARTED/REJECTED status writes check for a running `exchange.acceptance_pending` instance and route through `workflowEngineService.recordDecision()` instead of direct DB write; ENDED write fires `exchange.ending` trigger and throws `WorkflowConflictException` (409) if a workflow starts, holding the write until `exchange.ended_confirmed`; `@Transactional(dontRollbackOn)` preserves the workflow instance row on conflict; added no-auth OTP bridge in `updateNoAuthExchange()` routing through the engine when an acceptance workflow is running; injected `WorkflowInstanceRepository`, `WorkflowStepInstanceRepository`, `WorkflowEngineService`, `AuthTokenContext`, `OrganizationMembershipService` |
| `resource/ExchangeResource.kt` | Added `WorkflowConflictException` import and 409 Conflict case in `updateExchange()` catch block |

### Phase 5 - Completed work (2026-06-13)

Files created:

| File | Purpose |
|---|---|
| `web-app/src/app/settings/workflows-tab/WorkflowsTab.tsx` | Orchestrator: sub-tabs (Workflows / Activity) + designer routing state. Shows designer overlay when editing or creating. |
| `web-app/src/app/settings/workflows-tab/WorkflowsTabStyles.tsx` | Styles for WorkflowsTab container, sticky sub-tab bar, and content area. |
| `web-app/src/app/settings/workflows-tab/WorkflowsListView.tsx` | "My Workflows" row list + "Platform Templates" card list. Row actions: Edit, Activate/Deactivate, Duplicate (clone), Delete. Template rows have "Add to my workflows" clone button. |
| `web-app/src/app/settings/workflows-tab/WorkflowsListViewStyles.tsx` | Styles for the list view sections, rows, tag rows, and template cards. |
| `web-app/src/app/settings/workflows-tab/WorkflowDesigner.tsx` | Full workflow creation/edit form. Header fields: name, trigger dropdown (from `listWorkflowTriggers`), summary, industry tags multi-input, isActive toggle. Step list with Add Step. Save bar with portability warning for hardcoded Principal UUIDs. |
| `web-app/src/app/settings/workflows-tab/WorkflowDesignerStyles.tsx` | Styles for the designer form grid, step list, save bar, and tag input area. |
| `web-app/src/app/settings/workflows-tab/StepCard.tsx` | Collapsible step editor card. Renders type selector, quorum picker (APPROVAL), SLA field, escalation action, AssigneeBuilder, OutcomeConnector fields (onApprove/onReject for APPROVAL/NOTIFICATION/ACTION, onTrue/onFalse for CONDITION), action handler key dropdown, message template key input, and predicate expression input. |
| `web-app/src/app/settings/workflows-tab/StepCardStyles.tsx` | Styles for step card shell, header, body grid, field groups. |
| `web-app/src/app/settings/workflows-tab/AssigneeBuilder.tsx` | Add/remove AssigneeSpec entries. Supports ROLE (roleName + scopeType + scopeIdRef), PRINCIPAL (principalKind + principalId), and GROUP_ROLE (groupIdRef + groupRole) variants with inline field sets per kind. |
| `web-app/src/app/settings/workflows-tab/AssigneeBuilderStyles.tsx` | Styles for the assignee builder rows and add button. |
| `web-app/src/app/settings/workflows-tab/WorkflowInstanceDashboard.tsx` | Paginated list of `WorkflowInstance` rows for the org. Status filter dropdown. Click row opens `WorkflowInstanceDetail` drawer. |
| `web-app/src/app/settings/workflows-tab/WorkflowInstanceDashboardStyles.tsx` | Styles for the dashboard filter bar, rows, pagination. |
| `web-app/src/app/settings/workflows-tab/WorkflowInstanceDetail.tsx` | Overlay drawer showing full step timeline from `getWorkflowInstanceDetail`. Each step shows type, status badge, dueAt, completedAt, assignees, and decision entries (who, what decision, when, reason). Current active step is highlighted. |
| `web-app/src/app/settings/workflows-tab/WorkflowInstanceDetailStyles.tsx` | Styles for the detail drawer step timeline cards and decision entries. |

Files modified:

| File | Change |
|---|---|
| `web-app/src/app/models/models.tsx` | Added workflow DSL draft types: `WorkflowStepType`, `AssigneeKind`, `QuorumKind`, `EscalationAction`, `AddonKind`, `WorkflowScopeType`, `AssigneeSpecDraft`, `QuorumSpecDraft`, `StepOutcomeSpecDraft`, `EscalationSpecDraft`, `AddonSpecDraft`, `WorkflowStepSpecDraft`, `WorkflowDesignerState`. |
| `web-app/src/app/components/IconBundles.tsx` | Added `SettingsWorkflowsTabIcon` (TaskListSquareLtr), `AddIcon`, `BackIcon`, `CheckmarkIcon`, `DismissIcon`. |
| `web-app/src/app/settings/Settings.tsx` | Added `workflows` tab entry gated by `canManageOrganization`; imports and renders `WorkflowsTab`. |

### Key File Map updates (Phase 5)

| File | Phase | Status |
|---|---|---|
| `web-app/src/app/settings/Settings.tsx` | 5 | **Done** |
| `web-app/src/app/settings/workflows-tab/WorkflowsTab.tsx` | 5 | **Done** |
| `web-app/src/app/settings/workflows-tab/WorkflowsListView.tsx` | 5 | **Done** |
| `web-app/src/app/settings/workflows-tab/WorkflowDesigner.tsx` | 5 | **Done** |
| `web-app/src/app/settings/workflows-tab/StepCard.tsx` | 5 | **Done** |
| `web-app/src/app/settings/workflows-tab/AssigneeBuilder.tsx` | 5 | **Done** |
| `web-app/src/app/settings/workflows-tab/WorkflowInstanceDashboard.tsx` | 5 | **Done** |
| `web-app/src/app/settings/workflows-tab/WorkflowInstanceDetail.tsx` | 5 | **Done** |

### What is next

All five phases of the Workflow Redesign Plan are now complete. Further considerations from the plan that could be addressed in follow-up work:

1. **Org settings toggle UI** - Add a `requireRecipientAcceptance` switch to `OrganizationTab` (small addition to the existing settings form).
2. **`WorkflowDecisionResource` inbox enrichment** - Extend `GET /workflows/steps/pending` to return exchange name, trigger event, and step type for richer inbox rendering.
3. **Platform template seeding** - Add `V6__workflow_templates.sql` with `isTemplate=true` entries using only portable `GROUP_ROLE`/`ROLE` assignees.
4. **Addon editor in StepCard** - The `addons` field is serialized but not yet exposed as a UI section in `StepCard`. Add `AddonList` sub-component to let designers configure `ReminderBeforeDue` and `ReminderIfNoDecision` addons.
5. **React Flow canvas upgrade** - Replace the linear step list in `WorkflowDesigner` with a `@xyflow/react` canvas once CONDITION branching is production-ready.

---

### Phase 4 - Completed work (2026-06-13)

Files created:

| File | Purpose |
|---|---|
| `model/entity/WorkflowTriggerEventRegistry.kt` | JPA entity mapping `workflow_trigger_event_registry` table (String PK = event_name). |
| `repository/WorkflowTriggerEventRepository.kt` | Standalone repository (no UUID PK so cannot extend BaseRepository); `findAll()` and `findByEventName()`. |
| `model/dto/WorkflowDtos.kt` | Response DTOs: `WorkflowDefinitionDto`, `WorkflowDefinitionListItemDto`, `WorkflowTriggerEventResponseDto`, `WorkflowSubjectFieldResponseDto`, `WorkflowInstanceListItemDto`, `WorkflowInstanceDetailResponseDto`, `WorkflowStepInstanceResponseDto`, `WorkflowPrincipalRefResponseDto`, `WorkflowDecisionResponseDto`. |
| `service/workflow/WorkflowDefinitionService.kt` | `@ApplicationScoped` service for definition CRUD + clone (with UUID scrubbing) + trigger listing + instance queries. Request DTOs (`CreateWorkflowDefinitionRequest`, `UpdateWorkflowDefinitionRequest`, `PatchWorkflowStatusRequest`, `CloneWorkflowRequest`) defined in the same file. |
| `resource/WorkflowDefinitionResource.kt` | Thin JAX-RS adapter at `/workflows` with 10 endpoints (definitions CRUD+clone, triggers, instances). Delegates all logic to `WorkflowDefinitionService`. |
| `web-app/src/services/workflowService.ts` | Typed Axios wrappers for all `/workflows/*` endpoints: `listWorkflowDefinitions`, `getWorkflowDefinition`, `createWorkflowDefinition`, `updateWorkflowDefinition`, `patchWorkflowDefinitionStatus`, `deleteWorkflowDefinition`, `cloneWorkflowDefinition`, `listWorkflowTriggers`, `listWorkflowInstances`, `getWorkflowInstanceDetail`. |

Files modified:

| File | Change |
|---|---|
| `repository/WorkflowDefinitionRepository.kt` | Added `findAllAccessibleForOrg(organizationId)` - returns platform templates + org-scoped definitions for the caller's org. |
| `repository/WorkflowInstanceRepository.kt` | Added `findRunningForDefinition(definitionId)` to block edits/deletes on live definitions; added `findForOrg(orgId, status?, subjectResourceType?, page, pageSize)` for paginated instance listing. |
| `web-app/src/app/models/models.tsx` | Added `WorkflowDefinitionDto` (extends `WorkflowDefinitionSummaryDto` with stepsJson); added `WorkflowInstanceDetailDto` (extends `WorkflowInstanceSummaryDto` with steps array); added `organizationId` field to `WorkflowDefinitionSummaryDto`; added `isActive` field to `WorkflowTriggerEventDto`. |

### What is next - Phase 5

Phase 5 adds the Visual Workflow Designer UI inside the Settings drawer. The backend API and frontend service layer from Phase 4 are the foundation for all Phase 5 components.

Key tasks in order:

1. **`Settings.tsx`** - Add a `workflows` tab entry gated by `canManageOrganization` (org admin role). Lazy-import `WorkflowsTab`.

2. **`WorkflowsTab.tsx`** + **`WorkflowsTabStyles.tsx`** (new)
   - Two sections: "My Workflows" (`DataGrid` listing org definitions) and "Platform Templates" (filtered `isTemplate=true` list).
   - Row actions: Edit (opens designer), Duplicate (clone), Activate/Deactivate, Delete.
   - "Add to my workflows" button on template rows calls `cloneWorkflowDefinition`.

3. **`WorkflowDesigner.tsx`** + **`WorkflowDesignerStyles.tsx`** (new)
   - Header fields: name, summary, triggerEvent dropdown (from `listWorkflowTriggers`), industryTags multi-select, isActive toggle.
   - MVP: linear step list using collapsible Fluent UI `Card` components.
   - Save bar: serializes state to `WorkflowSpec` JSON, calls POST (new) or PUT (edit).

4. **`StepCard.tsx`** + **`StepCardStyles.tsx`** (new) - individual step editor card.

5. **`AssigneeBuilder.tsx`** + **`AssigneeBuilderStyles.tsx`** (new) - add/remove AssigneeSpec entries with $subject.* auto-complete.

6. **`WorkflowInstanceDashboard.tsx`** + **`WorkflowInstanceDashboardStyles.tsx`** (new) - org workflow activity panel using `listWorkflowInstances`.

7. **`WorkflowInstanceDetail.tsx`** + **`WorkflowInstanceDetailStyles.tsx`** (new) - drawer showing step timeline from `getWorkflowInstanceDetail`.

Phase 4 adds the full Workflow CRUD REST API. The engine, lifecycle wiring, and all step types are
now complete. This phase is purely HTTP surface area.

Key tasks in order:

1. **`WorkflowTriggerEventRepository.kt`** (new)
   - `findAll(): List<WorkflowTriggerEventRegistry>` - reads from `workflow_trigger_event_registry`.
   - `findByEventName(name): WorkflowTriggerEventRegistry?`

2. **`WorkflowTriggerEventRegistry.kt`** entity (new)
   - JPA entity mapping `workflow_trigger_event_registry` table created in V5 migration.
   - Fields: `eventName`, `description`, `subjectFieldsJson`, `isActive`, `createdAt`.

3. **DTO models** (in `model/dto/` or inline in the resource)
   - `WorkflowDefinitionDto` (full: id, name, summary, triggerEvent, version, scope, industryTags, isTemplate, sourceTemplateId, isActive, stepsJson as raw string)
   - `WorkflowInstanceDetailDto` (instance + decoded step timeline)
   - `WorkflowTriggerEventDto` (eventName, description, subjectFields)
   - `CreateWorkflowDefinitionRequest` / `UpdateWorkflowDefinitionRequest`
   - `CloneWorkflowRequest` (optional newName)

4. **`WorkflowDefinitionResource.kt`** (new) - endpoints:
   - `GET  /workflows/definitions` - list org's own + platform templates; query: `?tag=&triggerEvent=&isTemplate=`
   - `POST /workflows/definitions` - create; scope forced to ORG for non-admins
   - `GET  /workflows/definitions/{id}` - full definition
   - `PUT  /workflows/definitions/{id}` - update (blocked while RUNNING instance exists)
   - `PATCH /workflows/definitions/{id}/status` - `{ isActive: true/false }`
   - `DELETE /workflows/definitions/{id}` - soft-delete (sets isActive=false; blocked if running)
   - `POST /workflows/definitions/{id}/clone` - scrubs `AssigneeSpec.Principal` UUIDs, copies to caller's org
   - `GET  /workflows/triggers` - list `workflow_trigger_event_registry` rows
   - `GET  /workflows/instances` - paginated instances for org; `?status=&subjectResourceType=`
   - `GET  /workflows/instances/{id}` - instance detail with step timeline

5. **`WorkflowDefinitionService.kt`** (new `@ApplicationScoped`)
   - Business logic for create/update/clone/delete.
   - Clone scrubs `AssigneeSpec.Principal` entries: replaces hardcoded UUIDs with `{ kind: "ROLE", roleName: "REVIEWER", scopeType: "ORG", scopeIdRef: "$subject.orgId" }`.
   - Blocks PUT/DELETE when `WorkflowInstanceRepository.findRunningForOrg()` returns instances linked to the definition.

6. **Frontend `workflowService.ts`** (new)
   - Typed Axios wrappers for all `/workflows/*` endpoints.
   - Functions: `listDefinitions(params)`, `getDefinition(id)`, `createDefinition(req)`, `updateDefinition(id, req)`, `patchDefinitionStatus(id, isActive)`, `deleteDefinition(id)`, `cloneDefinition(id, req)`, `listTriggers()`, `listInstances(params)`, `getInstance(id)`.

---

## Table of Contents

1. [Project Overview](#project-overview)
2. [Tech Stack](#tech-stack)
3. [Current Exchange Lifecycle - How It Works Today](#current-exchange-lifecycle---how-it-works-today)
4. [Current Workflow Engine - What Already Exists](#current-workflow-engine---what-already-exists)
5. [What Needs to Change](#what-needs-to-change)
6. [Design Decisions and Constraints](#design-decisions-and-constraints)
7. [Phased Implementation Plan](#phased-implementation-plan)
   - [Phase 1 - Data Model and Engine Foundations](#phase-1---data-model-and-engine-foundations)
   - [Phase 2 - Implement Stubbed Step Type Handlers](#phase-2---implement-stubbed-step-type-handlers)
   - [Phase 3 - Exchange Lifecycle Rewiring](#phase-3---exchange-lifecycle-rewiring)
   - [Phase 4 - Workflow CRUD API](#phase-4---workflow-crud-api)
   - [Phase 5 - Visual Designer UI](#phase-5---visual-designer-ui)
8. [Key File Map](#key-file-map)
9. [Further Considerations and Open Decisions](#further-considerations-and-open-decisions)

---

## Project Overview

**DocuHyphen** is a document-exchange platform. Users (individuals or org members) create
**Exchanges** - collections of documents sent from an initiator to one or more recipients. The
current lifecycle is hardcoded:

```
INITIATED (Draft) -> ACCEPTED_STARTED (Active) -> ENDED / REJECTED
```

The goal is to replace this hardcoded flow with a **configurable, workflow-driven lifecycle**
where each lifecycle stage can have workflow steps attached to it - approvals, notifications,
conditions, actions, and addons such as reminders. Organizations configure their own workflows.
The system ships with platform-bundled workflow templates categorized by industry tags (not
hard-coded categories) so orgs can clone and customize them.

A **Visual Workflow Designer** will be added to the Settings area so org admins can build and
manage their workflows through a UI. A future Marketplace is out of scope for now, but the
entire design must make workflows portable and cloneable across organizations so that a template
collection can be added later with minimal rework.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend language | Kotlin (Quarkus, Jakarta EE, JPA/Hibernate) |
| Database | PostgreSQL (Flyway migrations) |
| Frontend | React 18 + TypeScript, Vite, Fluent UI v9 (`@fluentui/react-components`) |
| Serialization | kotlinx.serialization |
| Auth | JWT tokens, custom `AuthTokenContext` interceptor |
| Realtime | Custom `RealtimeEventService` (WebSocket/SSE) |
| Scheduling | Quarkus `@Scheduled` |
| HTTP client | Axios |
| Testing | Vitest (frontend), standard Quarkus test (backend) |

---

## Current Exchange Lifecycle - How It Works Today

### Status Enum (`ExchangeStatus.kt`)

```kotlin
enum class ExchangeStatus {
    INITIATED,         // Draft - exchange created, waiting for recipient action
    ACCEPTED_STARTED,  // Active - at least one recipient accepted
    ENDED,             // Completed
    REJECTED,          // Declined by recipient (or by approval workflow for group exchanges)
}
```

### Flow

1. **Initiator** calls `POST /exchanges` -> `ExchangeInitiationService.initiateExchange()`.
   - Creates `Exchange` at `INITIATED` status.
   - Grants the initiator an `OWNER` Share and the recipient a role Share (VIEWER/EDITOR/etc.).
   - For **GROUP recipients only**: may fire the existing `session.approval_requested` workflow
     (see below). All other recipient types bypass the engine entirely.
   - Sends email notifications directly (hardcoded).

2. **Recipient** calls `PUT /exchanges/{id}` -> `ExchangeUpdateService.updateExchange()`.
   - Directly writes `exchange.status = ACCEPTED_STARTED` or `REJECTED` via
     `exchangeRepository.updateStatus()`. No workflow gate.
   - For **no-auth recipients** (no account, OTP-based): `updateNoAuthExchange()` does the same
     with an OTP verification step.

3. **Initiator** calls `PUT /exchanges/{id}` with `status = ENDED` to close the exchange.

### What Is Hardcoded

- Status transitions are direct DB writes - any caller with the right Share role can push
  `ACCEPTED_STARTED` or `ENDED` without going through the engine.
- Notifications (emails) are fired inline inside `ExchangeInitiationService.sendNotifications()`
  and `ExchangeUpdateService.sendStatusChangeEmails()`.
- There is no org-level setting to require recipient acceptance before an exchange goes active.

---

## Current Workflow Engine - What Already Exists

The workflow engine is **already built** and used for one narrow case today. Understanding it
is critical before making changes.

### Entities

| Entity | File | Purpose |
|---|---|---|
| `WorkflowDefinition` | `model/entity/WorkflowDefinition.kt` | Reusable template. Has `name`, `version`, `scope` (APP/ORG), `organizationId`, `triggerEvent` (string), `stepsJson` (JSON blob), `isActive`. |
| `WorkflowInstance` | `model/entity/WorkflowInstance.kt` | One execution of a definition against a subject (e.g. an Exchange UUID). Captures `subjectDataJson` snapshot at start. |
| `WorkflowStepInstance` | `model/entity/WorkflowStepInstance.kt` | One step within an instance. Has `specSnapshotJson` (replay-safe), `assigneesSnapshotJson`, `decisionsJson`, `dueAt`, `status`. |

### Step Types (enum `WorkflowStepType`)

| Type | Current Status |
|---|---|
| `APPROVAL` | **Fully implemented** - quorum, decisions, SLA, escalation all work. |
| `NOTIFICATION` | **Stubbed** - advances unconditionally, no actual dispatch. |
| `CONDITION` | **Stubbed** - advances unconditionally, no branching logic. |
| `ACTION` | **Stubbed** - `actionHandlerKey` field exists but nothing is called. |

### WorkflowSpec DSL (`WorkflowSpec.kt`)

The `stepsJson` column stores a `WorkflowSpec` JSON blob:

```kotlin
data class WorkflowSpec(val steps: List<WorkflowStepSpec>)

data class WorkflowStepSpec(
    val type: WorkflowStepType,
    val assignees: List<AssigneeSpec>,      // who to assign to
    val quorum: QuorumSpec,                 // Any / All / N_OF_M
    val slaMinutes: Int?,                   // SLA deadline
    val escalation: EscalationSpec?,        // what to do on SLA breach
    val onApprove: StepOutcomeSpec?,        // next step + event to emit on approve
    val onReject: StepOutcomeSpec?,         // next step + event to emit on reject
    val actionHandlerKey: String?,          // for ACTION steps
)
```

**AssigneeSpec variants** (sealed class, JSON discriminator `kind`):
- `PRINCIPAL` - exact user/group UUID
- `GROUP_ROLE` - members of a group holding a given role (e.g. MANAGER). Supports
  `$subject.<field>` placeholder to avoid hardcoding the group UUID.
- `ROLE` - anyone holding a named role in a scope (APP or ORG).

**QuorumSpec variants**: `ANY` (at least 1), `ALL` (everyone), `N_OF_M` (exactly N).

**EscalationAction**: `ESCALATE` (reassign), `AUTO_REJECT`, `AUTO_APPROVE`.

### Engine Services

| Service | File | Purpose |
|---|---|---|
| `WorkflowEngineService` | `service/workflow/WorkflowEngineService.kt` | Interface: `trigger()`, `recordDecision()`, `escalateOverdue()`, `cancel()`, `listPendingForUser()`. |
| `DefaultWorkflowEngineService` | `service/workflow/DefaultWorkflowEngineService.kt` | Implementation. Synchronous, transactional. Publishes `DomainEvent`s on step assigned/completed. |
| `WorkflowAssigneeResolver` | `service/workflow/WorkflowAssigneeResolver.kt` | Resolves `AssigneeSpec` + `$subject.*` placeholders to concrete `PrincipalRef` lists. |
| `WorkflowEscalationScheduler` | `service/workflow/WorkflowEscalationScheduler.kt` | Quarkus `@Scheduled` job, runs every 60s, calls `escalateOverdue()`. |

### Current Trigger Usage

Only one trigger is fired today: `session.approval_requested` in
`ExchangeInitiationService.maybeTriggerGroupApproval()`. It only fires for **GROUP recipient**
exchanges where a matching active `WorkflowDefinition` exists. Non-group exchanges never touch
the engine.

### Event Routing

`DomainEventPublisher` -> `EventRouter` -> `NotificationRuleEngine` + `ExchangeApprovalEventHandler`.

`ExchangeApprovalEventHandler` handles `session.activated` and `session.rejected`:
- `session.activated` -> calls `shareService.activatePendingForResource()` (activates
  PENDING_APPROVAL shares). Does **not** update `exchange.status` - this is a known gap.
- `session.rejected` -> revokes shares and sets `exchange.status = REJECTED`.

### Existing API Surface for Workflows

`WorkflowDecisionResource` (`/workflows/steps`):
- `POST /workflows/steps/{stepInstanceId}/decision` - record approve/reject
- `GET /workflows/steps/pending` - list pending steps for the current user (inbox)

---

## What Needs to Change

### Summary of Gaps

| Requirement | Gap |
|---|---|
| Workflows at every lifecycle stage | Only `session.approval_requested` is wired; need `exchange.draft_submitted`, `exchange.acceptance_pending`, `exchange.activated`, `exchange.ending`. |
| Org-level acceptance toggle | `OrganizationSettings` has no `requireRecipientAcceptance` field. |
| At least 1 recipient must accept before Active | Not enforced for non-group exchanges. Recipient calls `updateExchange()` directly. |
| NOTIFICATION, CONDITION, ACTION steps | All three are no-ops. |
| Step addons (reminders, conditional reminders) | `WorkflowStepSpec` has no addon concept. |
| Workflow CRUD API | No endpoints exist to create/edit/list definitions. |
| Visual designer | Does not exist. `TemplatesTab` in Settings is a disabled placeholder. |
| Workflow portability | `WorkflowDefinition` has no `industryTags`, `summary`, `isTemplate`, `sourceTemplateId` fields. |
| Trigger event registry | No registry table - trigger events are magic strings with no schema description. |
| Bypass gap | `ExchangeUpdateService` writes status directly, bypassing the engine. Any caller with Share access can skip workflows. |
| `session.activated` does not set `exchange.status` | `ExchangeApprovalEventHandler` activates shares but does not set `exchange.status = ACCEPTED_STARTED`. |

---

## Design Decisions and Constraints

1. **Keep INITIATED / ACCEPTED_STARTED / ENDED / REJECTED as the Exchange statuses.** The
   workflow engine operates *within* these stages, not instead of them. Workflows are the
   mechanism that gates transitions between them.

2. **"Require acceptance" is on by default, org-level toggle to disable.** New
   `requireRecipientAcceptance: Boolean = true` on `OrganizationSettings`. When disabled, an
   exchange auto-advances to `ACCEPTED_STARTED` immediately on creation.

3. **At least one recipient must accept before Active.** Enforced via `QuorumSpec.Any` on the
   `exchange.acceptance_pending` APPROVAL step assigned to recipient principals.

4. **Industry tags are free-form strings, never an enum.** Stored as a JSON array on
   `WorkflowDefinition`. The UI presents a curated tag list (Legal, Finance, Healthcare, HR,
   Logistics, etc.) as a multi-select, but any string is valid so new industries never require
   a code deploy.

5. **Workflows must be portable across orgs.** The clone endpoint strips hardcoded principal
   UUIDs from `AssigneeSpec.Principal` entries and replaces them with `ROLE` placeholders. The
   designer warns when any step contains a hardcoded UUID (not portable).

6. **Platform-bundled templates are seeded via Flyway.** `isTemplate = true` definitions seeded
   in a migration script so they are versioned, auditable, and updatable without code changes.

7. **No Marketplace in this phase.** The portability foundation (tags, clone, scrubbed export)
   is built, but no public browse/publish surface is built yet.

8. **The no-auth OTP path is a special case.** A decision must be made before Phase 3:
   - **Option A (recommended)**: Route no-auth acceptance through `workflowEngineService.
     recordDecision()` after OTP verification succeeds. The step assignees for no-auth
     exchanges are resolved to a synthetic `EXTERNAL_RECIPIENT` principal kind, or the
     exchange itself holds the recipient email as a `$subject.recipientEmail` field and the
     acceptance step is assigned to that email-user.
   - **Option B**: Flag no-auth exchanges as ineligible for the acceptance workflow and
     auto-advance them (simpler, but creates a second-class citizen path).

9. **Direct status writes must be guarded.** Once Phase 3 is live, `ExchangeUpdateService`
   must check whether a running `WorkflowInstance` exists for the exchange before allowing a
   direct `status` write. If one does, return `409 Conflict` and require the caller to go
   through `workflowEngineService.recordDecision()`.

10. **Synchronous engine execution is acceptable for now.** The code already has comments
    noting a future Kafka-backed async path. For this phase everything stays in-process and
    transactional. Complex multi-step workflows will block the HTTP thread briefly - acceptable
    until async is wired in a later phase.

---

## Phased Implementation Plan

### Phase 1 - Data Model and Engine Foundations `[COMPLETE]`

#### 1.1 Extend `WorkflowDefinition` for portability `[DONE]`

File: `src/main/kotlin/com/docuhyphen/app/api/model/entity/WorkflowDefinition.kt`

Add:
```kotlin
@Column(name = "industry_tags", nullable = false, columnDefinition = "text")
var industryTags: String = "[]"         // JSON array of strings e.g. ["legal","hr"]

@Column(name = "summary", nullable = true, length = 512)
var summary: String? = null

@Column(name = "is_template", nullable = false)
var isTemplate: Boolean = false         // true = platform-bundled, false = org-created

@Column(name = "source_template_id", nullable = true)
@Serializable(with = UUIDSerializer::class)
var sourceTemplateId: UUID? = null      // set when cloned from a template
```

#### 1.2 Add `StepAddonSpec` to the WorkflowSpec DSL `[DONE]`

File: `src/main/kotlin/com/docuhyphen/app/api/service/workflow/WorkflowSpec.kt`

Add `addons: List<StepAddonSpec> = emptyList()` to `WorkflowStepSpec`.

New sealed class:
```kotlin
@Serializable
@JsonClassDiscriminator("kind")
sealed class StepAddonSpec {
    /** Send a reminder to recipientRef N minutes before the step's dueAt. */
    @Serializable
    @SerialName("REMINDER_BEFORE_DUE")
    data class ReminderBeforeDue(
        val minutesBeforeDue: Int,
        val recipientRef: AssigneeSpec,
        val messageTemplateKey: String? = null,
    ) : StepAddonSpec()

    /** Send a reminder if no decision has been recorded after N minutes. */
    @Serializable
    @SerialName("REMINDER_IF_NO_DECISION")
    data class ReminderIfNoDecision(
        val afterMinutes: Int,
        val recipientRef: AssigneeSpec,
        val messageTemplateKey: String? = null,
        val repeatEveryMinutes: Int? = null,   // null = send once
    ) : StepAddonSpec()
}
```

The `WorkflowEscalationScheduler` tick evaluates addons alongside SLA checks. Add
`processAddons(step, spec, now)` to `DefaultWorkflowEngineService`.

#### 1.3 Add `requireRecipientAcceptance` to `OrganizationSettings` `[DONE]`

File: `src/main/kotlin/com/docuhyphen/app/api/model/entity/OrganizationSettings.kt`

Add:
```kotlin
@Column(name = "require_recipient_acceptance", nullable = false)
var requireRecipientAcceptance: Boolean = true
```

Update `OrganizationSettingsDto` in `web-app/src/app/models/models.tsx`:
```typescript
requireRecipientAcceptance: boolean;
```

#### 1.4 Add `messageTemplateKey` and `predicateExpression` to `WorkflowStepSpec` `[DONE]`

File: `src/main/kotlin/com/docuhyphen/app/api/service/workflow/WorkflowSpec.kt`

Add to `WorkflowStepSpec`:
```kotlin
/** For NOTIFICATION steps: key into the email/in-app template registry. */
val messageTemplateKey: String? = null,

/** For CONDITION steps: expression evaluated against subjectDataJson fields.
 *  Syntax: "$subject.<key> <op> '<value>'"  e.g. "$subject.recipientType == 'GROUP'"
 *  Supported ops: ==, !=, contains, startsWith
 */
val predicateExpression: String? = null,

/** For CONDITION steps: branch taken when predicate is true. */
val onTrue: StepOutcomeSpec? = null,

/** For CONDITION steps: branch taken when predicate is false. */
val onFalse: StepOutcomeSpec? = null,
```

#### 1.5 Create Flyway migration `[DONE]`

File: `src/main/resources/db/migration/V5__workflow_lifecycle_redesign.sql`

```sql
-- WorkflowDefinition additions
ALTER TABLE workflow_definition
    ADD COLUMN IF NOT EXISTS industry_tags TEXT NOT NULL DEFAULT '[]',
    ADD COLUMN IF NOT EXISTS summary VARCHAR(512),
    ADD COLUMN IF NOT EXISTS is_template BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS source_template_id UUID;

-- OrganizationSettings addition
ALTER TABLE organization_settings
    ADD COLUMN IF NOT EXISTS require_recipient_acceptance BOOLEAN NOT NULL DEFAULT TRUE;

-- Trigger event registry
CREATE TABLE IF NOT EXISTS workflow_trigger_event_registry (
    event_name          VARCHAR(128) NOT NULL PRIMARY KEY,
    description         VARCHAR(512),
    subject_fields_json TEXT NOT NULL DEFAULT '[]',
    -- JSON array of {name, type, description} objects describing available $subject.* fields
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Seed canonical trigger events
INSERT INTO workflow_trigger_event_registry (event_name, description, subject_fields_json) VALUES
('exchange.draft_submitted',
 'Fired when an Exchange is first created. Use to gate internal pre-send approvals.',
 '[{"name":"initiatorId","type":"UUID","description":"App user who created the exchange"},
   {"name":"orgId","type":"UUID","description":"Initiator org id"}]'),

('exchange.acceptance_pending',
 'Fired after draft submission when recipient acceptance is required. Blocks transition to Active.',
 '[{"name":"recipientId","type":"UUID","description":"Primary recipient user id"},
   {"name":"recipientGroupId","type":"UUID","description":"Recipient group id (GROUP type only)"},
   {"name":"initiatorId","type":"UUID","description":"Initiator user id"},
   {"name":"orgId","type":"UUID","description":"Initiator org id"},
   {"name":"recipientType","type":"STRING","description":"EMAIL | APP_USER | GROUP"}]'),

('exchange.activated',
 'Fired when an Exchange transitions to ACCEPTED_STARTED.',
 '[{"name":"initiatorId","type":"UUID"},{"name":"orgId","type":"UUID"}]'),

('exchange.ending',
 'Fired when an Exchange is about to transition to ENDED. Use for completion checklists.',
 '[{"name":"initiatorId","type":"UUID"},{"name":"orgId","type":"UUID"}]'),

-- Legacy event name kept for backward compat with existing seeded workflow
('session.approval_requested',
 'Legacy: group-session approval gate (superseded by exchange.acceptance_pending).',
 '[{"name":"recipientGroupId","type":"UUID"},{"name":"initiatorId","type":"UUID"},
   {"name":"orgId","type":"UUID"}]')
ON CONFLICT (event_name) DO NOTHING;
```

---

### Phase 2 - Implement Stubbed Step Type Handlers `[COMPLETE]`

File: `src/main/kotlin/com/docuhyphen/app/api/service/workflow/DefaultWorkflowEngineService.kt`

#### 2.1 NOTIFICATION step handler

When the engine processes a step with `stepType == NOTIFICATION`:
1. Resolve assignees (already done for APPROVAL steps - reuse same path).
2. Look up the `messageTemplateKey` from `specSnapshotJson`.
3. For each resolved assignee, dispatch via `AppNotificationService` (in-app) and optionally
   `EmailService` (when the assignee has an email).
4. Mark the step `COMPLETED` immediately (auto-advance, no human decision needed).
5. Emit `onApprove?.emit` and advance to `onApprove?.nextStep`.

#### 2.2 ACTION step handler + `WorkflowActionHandler` registry

New interface:
```kotlin
// src/main/kotlin/com/docuhyphen/app/api/service/workflow/WorkflowActionHandler.kt
interface WorkflowActionHandler {
    fun key(): String
    fun execute(instance: WorkflowInstance, step: WorkflowStepInstance): ActionResult
}

data class ActionResult(val success: Boolean, val reason: String? = null)
```

Built-in implementations (each `@ApplicationScoped`):
- `ExchangeAutoAcceptActionHandler` - key `exchange.auto-accept`, sets `exchange.status =
  ACCEPTED_STARTED` and fires `exchange.activated`.
- `ExchangeSendReminderActionHandler` - key `exchange.send-reminder`, sends reminder email to
  recipient via `EmailTemplateService`.
- `ExchangeRevokeAccessActionHandler` - key `exchange.revoke-access`, calls
  `shareService.revokeAllForResource()`.

In `DefaultWorkflowEngineService`, inject `Instance<WorkflowActionHandler>` (CDI), build a
`Map<String, WorkflowActionHandler>` on startup, dispatch ACTION steps by `actionHandlerKey`.

#### 2.3 CONDITION step handler

Simple key-op-value expression evaluator. Parse `predicateExpression` from `specSnapshotJson`,
evaluate against `decodeSubjectData(instance.subjectDataJson)`. Supported operators: `==`,
`!=`, `contains`, `startsWith`. On true: follow `onTrue` outcome. On false: follow `onFalse`
outcome. No human decision - auto-advance.

#### 2.4 Reminder addon processing in the scheduler tick

Add `processAddons(step: WorkflowStepInstance, spec: WorkflowStepSpec, now: Timestamp)` called
from `escalateOverdue()` for each PENDING step. For each addon:
- `ReminderBeforeDue`: if `dueAt != null && dueAt - now <= minutesBeforeDue * 60s` and no
  reminder has been sent, send and mark sent (store sent flag in a new `addons_state_json`
  column on `WorkflowStepInstance`).
- `ReminderIfNoDecision`: if step has been PENDING for `afterMinutes` and `decisionsJson` is
  empty, send reminder. If `repeatEveryMinutes` set, re-send on that cadence.

Add `addons_state_json TEXT` column to `workflow_step_instance` (track which addons have fired
to avoid duplicate sends).

---

### Phase 3 - Exchange Lifecycle Rewiring `[COMPLETE]`

#### 3.1 Fire lifecycle events from `ExchangeInitiationService` `[DONE]`

File: `src/main/kotlin/com/docuhyphen/app/api/service/exchange/ExchangeInitiationService.kt`

After `exchangeRepository.save(exchange)`:

1. Fire `exchange.draft_submitted`. If a matching definition exists and runs, await its
   completion event before advancing (or use `onApprove.emit = "exchange.draft_approved"` and
   handle in `ExchangeApprovalEventHandler`).

2. If org has `requireRecipientAcceptance = true`:
   - Fire `exchange.acceptance_pending` with `subjectData` containing `recipientId` /
     `recipientGroupId` / `initiatorId` / `orgId` / `recipientType`.
   - Hold the exchange at `INITIATED`. Do not auto-advance.
   - If no matching definition exists (engine returns null), still require at least one manual
     recipient acceptance (a new lightweight guard in `updateExchange()`, not a full workflow).

3. If org has `requireRecipientAcceptance = false`: fire `exchange.activated` event immediately
   and set `exchange.status = ACCEPTED_STARTED`.

#### 3.2 Route recipient accept/reject through the engine `[DONE]`

File: `src/main/kotlin/com/docuhyphen/app/api/service/exchange/ExchangeUpdateService.kt`

When `request.status == ACCEPTED_STARTED || request.status == REJECTED`:
1. Check if a running `WorkflowInstance` exists for this exchange with trigger
   `exchange.acceptance_pending`.
2. If yes: call `workflowEngineService.recordDecision()` on the active pending step. Do not
   write `exchange.status` directly. Return the current exchange state.
3. If no: allow the direct write (backward compat for orgs with `requireRecipientAcceptance =
   false` or no matching workflow definition).

Add guard helper:
```kotlin
private fun hasRunningAcceptanceWorkflow(exchangeId: UUID): Boolean
```

#### 3.3 Fix `ExchangeApprovalEventHandler` to update `exchange.status` `[DONE]`

File: `src/main/kotlin/com/docuhyphen/app/api/service/exchange/ExchangeApprovalEventHandler.kt`

In the `EVENT_ACTIVATED` branch, after activating pending shares, also:
```kotlin
exchangeRepository.findById(exchangeId)?.let { session ->
    session.status = ExchangeStatus.ACCEPTED_STARTED
    exchangeRepository.update(session)
}
```

Extend `handles()` and `handle()` to also process:
- `exchange.draft_approved` (remove draft hold, fire `exchange.acceptance_pending` next)
- `exchange.activated` (set `ACCEPTED_STARTED`, fire further post-activation workflows)
- `exchange.ending` (fire completion workflow before setting `ENDED`)

#### 3.4 Fire `exchange.ending` before ENDED transition `[DONE]`

In `ExchangeUpdateService.updateExchange()`, when `request.status == ENDED`:
1. Fire `exchange.ending`.
2. If a workflow starts, hold the `ENDED` write and let the engine complete first (completion
   step emits `exchange.ended_confirmed`, handled in the event handler to do the final write).
3. If no workflow, write `ENDED` immediately (existing behavior).

#### 3.5 No-auth OTP bridge (recommended Option A) `[DONE]`

In `ExchangeUpdateService.updateNoAuthExchange()`, after OTP verification succeeds:
- If a running `exchange.acceptance_pending` instance exists, call
  `workflowEngineService.recordDecision()` using a synthetic principal for the no-auth
  recipient (resolved from the exchange's recipient Share). This keeps the engine as the
  single source of truth for acceptance state.
- If no running workflow, fall through to the existing direct `updateStatus()` call.

---

### Phase 4 - Workflow CRUD API

New resource file: `src/main/kotlin/com/docuhyphen/app/api/resource/WorkflowDefinitionResource.kt`

#### Endpoints

```
GET    /workflows/definitions
       - Lists org's own definitions (scope=ORG, organizationId=callerOrg)
       - Plus platform templates (isTemplate=true)
       - Query params: ?tag=legal&triggerEvent=exchange.acceptance_pending&isTemplate=true

POST   /workflows/definitions
       - Create new definition. scope forced to ORG for non-APP_ADMIN callers.
       - Validates stepsJson against WorkflowSpec schema.

GET    /workflows/definitions/{id}
       - Returns full definition including stepsJson.
       - Caller must be in the definition's org, or definition must be a platform template.

PUT    /workflows/definitions/{id}
       - Update name, summary, tags, stepsJson, isActive.
       - Cannot edit while a RUNNING instance references this definition.

PATCH  /workflows/definitions/{id}/status
       - { "isActive": true/false }

DELETE /workflows/definitions/{id}
       - Soft delete (set isActive=false). Block if running instances exist.

POST   /workflows/definitions/{id}/clone
       - Copies the definition into the calling org.
       - Scrubs all AssigneeSpec.Principal entries (hardcoded UUIDs) - replaces with
         ROLE placeholder { kind: "ROLE", roleName: "REVIEWER", scopeType: "ORG",
         scopeIdRef: "$subject.orgId" }.
       - Sets sourceTemplateId = original.id, isTemplate = false.
       - Returns the new definition.

GET    /workflows/triggers
       - Lists workflow_trigger_event_registry rows (event_name, description,
         subject_fields_json). Powers the designer's trigger dropdown.

GET    /workflows/instances
       - Lists WorkflowInstances for the org's exchanges. Paginated.
       - Query: ?status=RUNNING&subjectResourceType=EXCHANGE

GET    /workflows/instances/{id}
       - Instance detail + all WorkflowStepInstances + decisionsJson decoded.
```

New DTO models needed (create in `model/dto` or inline in the resource):
- `WorkflowDefinitionDto` (full definition, stepsJson as raw string)
- `WorkflowDefinitionSummaryDto` (list view - no stepsJson, includes tags, installCount)
- `WorkflowInstanceDetailDto` (instance + steps + decoded decisions timeline)
- `WorkflowTriggerEventDto` (event_name, description, subjectFields)
- `CloneWorkflowRequest` (optional `newName`, `targetOrgId` for future admin use)

---

### Phase 5 - Visual Designer UI

#### 5.1 Add `Workflows` tab to Settings

File: `web-app/src/app/settings/Settings.tsx`

Add `workflows: "WorkflowsTab"` to `tabIds`. Tab visible only when `canManageOrganization`.
Lazy-import `WorkflowsTab` alongside existing tabs.

#### 5.2 `WorkflowsTab` - Library view

File: `web-app/src/app/settings/workflows-tab/WorkflowsTab.tsx` (new)

Two sections:
1. **My Workflows** - Fluent UI `DataGrid` listing org's `WorkflowDefinition` rows.
   Columns: Name, Trigger Event, Version, Status (`Badge`), Industry Tags (`TagGroup`),
   Source Template link. Row actions: Edit, Duplicate (clone to self), Activate/Deactivate,
   Delete.
2. **Platform Templates** - Filtered list where `isTemplate=true`. Each row has an "Add to my
   workflows" button that calls `POST /workflows/definitions/{id}/clone`.

#### 5.3 `WorkflowDesigner` - Step editor

File: `web-app/src/app/settings/workflows-tab/WorkflowDesigner.tsx` (new)

**MVP: Linear step list** (no canvas dependency). Each step is a collapsible Fluent UI `Card`.

**Designer header fields:**
- `TriggerSelector` - Dropdown populated from `GET /workflows/triggers`. Shows trigger name +
  description. On change, updates available `$subject.*` auto-complete options.
- `name` field, `summary` field, `industryTags` multi-select (free-text entry + curated list).
- `isActive` toggle.

**Step card sections (`StepCard.tsx`):**

```
StepTypeSelector        Dropdown: APPROVAL | NOTIFICATION | CONDITION | ACTION
AssigneeBuilder         Add/remove AssigneeSpec entries:
                          - PRINCIPAL: UUID picker (warn if hardcoded = not portable)
                          - GROUP_ROLE: group picker + role dropdown (OWNER/MANAGER/MEMBER/OBSERVER)
                          - ROLE: role name input + scope type (APP/ORG) + scopeIdRef
                        $subject.* auto-complete from trigger's subjectFields
QuorumPicker            Any / All / N of M (number input when N_OF_M selected)
SlaPicker               Number input for slaMinutes (optional)
EscalationBuilder       afterSlaBreach: ESCALATE | AUTO_REJECT | AUTO_APPROVE
                        escalateTo: AssigneeBuilder (same as above, visible when ESCALATE)
OutcomeConnector        onApprove: { nextStep: "END" | step index, emit: event name }
                        onReject:  { nextStep: "END" | step index, emit: event name }
AddonList               Add ReminderBeforeDue / ReminderIfNoDecision entries
                        Each addon: minutesBeforeDue/afterMinutes, recipientRef (AssigneeBuilder)
MessageTemplateKey      (NOTIFICATION + ACTION steps) - text input
PredicateExpression     (CONDITION steps) - text input with $subject.* auto-complete
OnTrue / OnFalse        (CONDITION steps) - OutcomeConnector x2
ActionHandlerKey        (ACTION steps) - Dropdown populated from registered action handlers
```

**Save bar:**
- Validates no `AssigneeSpec.Principal` with hardcoded UUIDs (portability warning).
- Serializes the state to `WorkflowSpec` JSON.
- Calls `POST` (new) or `PUT` (edit) on `/workflows/definitions`.

**State shape** (TypeScript):
```typescript
interface WorkflowDesignerState {
  id?: string;
  name: string;
  summary: string;
  industryTags: string[];
  triggerEvent: string;
  isActive: boolean;
  steps: WorkflowStepSpecDraft[];  // mirrors WorkflowStepSpec JSON shape
}
```

#### 5.4 `WorkflowInstanceDashboard` - Org workflow activity view

File: `web-app/src/app/settings/workflows-tab/WorkflowInstanceDashboard.tsx` (new)

A secondary panel (tab or drawer) within `WorkflowsTab`. Shows:
- Paginated list of recent `WorkflowInstance` rows for the org's exchanges.
- Status filter: RUNNING | COMPLETED | REJECTED | CANCELLED | ESCALATED.
- Click row -> `WorkflowInstanceDetail.tsx` drawer: step timeline with assignees, decisions
  (who voted what and when), current step highlight, SLA countdown if PENDING.

#### 5.5 New frontend models

Add to `web-app/src/app/models/models.tsx`:

```typescript
export interface WorkflowDefinitionSummaryDto {
  id: string;
  name: string;
  summary?: string;
  triggerEvent: string;
  version: number;
  isActive: boolean;
  isTemplate: boolean;
  scope: 'APP' | 'ORG';
  industryTags: string[];
  sourceTemplateId?: string;
  createdAt: string;
}

export interface WorkflowTriggerEventDto {
  eventName: string;
  description?: string;
  subjectFields: WorkflowSubjectFieldDto[];
}

export interface WorkflowSubjectFieldDto {
  name: string;
  type: string;
  description?: string;
}

export interface WorkflowInstanceSummaryDto {
  id: string;
  definitionId: string;
  definitionName?: string;
  subjectResourceType?: string;
  subjectResourceId?: string;
  exchangeName?: string;
  status: 'RUNNING' | 'COMPLETED' | 'REJECTED' | 'CANCELLED' | 'ESCALATED';
  currentStepIndex: number;
  createdAt: string;
  completedAt?: string;
}

export interface WorkflowStepInstanceDto {
  id: string;
  stepIndex: number;
  stepType: string;
  status: string;
  assignees: WorkflowPrincipalRefDto[];
  decisions: WorkflowDecisionEntryDto[];
  dueAt?: string;
  escalatedAt?: string;
  completedAt?: string;
  createdAt: string;
}

export interface WorkflowPrincipalRefDto {
  kind: string;
  id: string;
}

export interface WorkflowDecisionEntryDto {
  principalKind: string;
  principalId: string;
  decision: 'APPROVE' | 'REJECT';
  reason?: string;
  atEpochMillis: number;
}
```

#### 5.6 New API service module

File: `web-app/src/services/workflowService.ts` (new)

Wraps all `/workflows/*` Axios calls with typed request/response shapes.

---

## Key File Map

### Backend files to modify

| File | Phase | Change | Status |
|---|---|---|---|
| `model/entity/WorkflowDefinition.kt` | 1 | Add `industryTags`, `summary`, `isTemplate`, `sourceTemplateId` | **Done** |
| `model/entity/OrganizationSettings.kt` | 1 | Add `requireRecipientAcceptance` | **Done** |
| `model/entity/WorkflowStepInstance.kt` | 2 | Add `addonsStateJson` field (column already in DB via V5) | **Done** |
| `service/workflow/WorkflowSpec.kt` | 1, 2 | Add `StepAddonSpec`, `messageTemplateKey`, `predicateExpression`, `onTrue`, `onFalse`, `addons` to `WorkflowStepSpec` | **Done** |
| `service/workflow/DefaultWorkflowEngineService.kt` | 2 | Implement NOTIFICATION, ACTION, CONDITION handlers; addon processing | **Done** |
| `service/workflow/WorkflowEscalationScheduler.kt` | 2 | Call `processAddons()` in tick | **Done** (integrated into `escalateOverdue()`) |
| `service/exchange/ExchangeInitiationService.kt` | 3 | Fire `exchange.draft_submitted` + `exchange.acceptance_pending`; check org setting | **Done** |
| `service/exchange/ExchangeUpdateService.kt` | 3 | Route accept/reject through engine; guard direct status writes; fire `exchange.ending` | **Done** |
| `service/exchange/ExchangeApprovalEventHandler.kt` | 3 | Handle new lifecycle events; set `exchange.status = ACCEPTED_STARTED` on `session.activated` | **Done** |
| `db/migration/V5__workflow_lifecycle_redesign.sql` | 1 | Schema additions + trigger event registry seed | **Done** |

### Backend files to create

| File | Phase | Purpose | Status |
|---|---|---|---|
| `resource/WorkflowDefinitionResource.kt` | 4 | CRUD + clone + triggers + instances endpoints | Pending |
| `service/workflow/WorkflowDefinitionService.kt` | 4 | Business logic: create/update/clone/delete with portability rules | Pending |
| `service/workflow/WorkflowActionHandler.kt` | 2 | Interface for ACTION step handlers | **Done** |
| `service/workflow/actions/ExchangeAutoAcceptActionHandler.kt` | 2 | Built-in action | **Done** |
| `service/workflow/actions/ExchangeSendReminderActionHandler.kt` | 2 | Built-in action | **Done** |
| `service/workflow/actions/ExchangeRevokeAccessActionHandler.kt` | 2 | Built-in action | **Done** |
| `repository/WorkflowTriggerEventRepository.kt` | 4 | Query `workflow_trigger_event_registry` | Pending |
| `model/entity/WorkflowTriggerEventRegistry.kt` | 4 | JPA entity for trigger event registry table | Pending |
| `exception/WorkflowConflictException.kt` | 3 | 409 signal for in-flight workflow gate | **Done** |

### Frontend files to modify

| File | Phase | Change | Status |
|---|---|---|---|
| `web-app/src/app/models/models.tsx` | 1, 5 | Add new DTO interfaces; add `requireRecipientAcceptance` to `OrganizationSettingsDto` | **Done** |
| `web-app/src/app/settings/Settings.tsx` | 5 | Add `WorkflowsTab` entry gated by `canManageOrganization` | Pending |

### Frontend files to create

| File | Phase | Purpose | Status |
|---|---|---|---|
| `web-app/src/app/settings/workflows-tab/WorkflowsTab.tsx` | 5 | Library view (list + templates) | Pending |
| `web-app/src/app/settings/workflows-tab/WorkflowDesigner.tsx` | 5 | Step editor (create/edit) | Pending |
| `web-app/src/app/settings/workflows-tab/StepCard.tsx` | 5 | Individual step editor card | Pending |
| `web-app/src/app/settings/workflows-tab/AssigneeBuilder.tsx` | 5 | Assignee spec builder | Pending |
| `web-app/src/app/settings/workflows-tab/WorkflowInstanceDashboard.tsx` | 5 | Instance activity view | Pending |
| `web-app/src/app/settings/workflows-tab/WorkflowInstanceDetail.tsx` | 5 | Instance detail drawer | Pending |
| `web-app/src/services/workflowService.ts` | 4 | API calls for all /workflows/* endpoints | Pending |

---

## Further Considerations and Open Decisions

1. **Template seeding via Flyway**: Platform-bundled workflow templates (`isTemplate=true`)
   should be in `V6__workflow_templates.sql` (or later). Each template must use only
   `GROUP_ROLE` or `ROLE` assignees (no hardcoded UUIDs) so they are portable on install.
   Example templates to ship: "Standard Recipient Acceptance" (ANY quorum APPROVAL),
   "Two-Manager Pre-Send Approval" (ANY quorum APPROVAL assigned to `$subject.orgId` ORG_ADMIN
   role), "Acceptance Reminder" (APPROVAL + `ReminderIfNoDecision` addon), etc.

2. **React Flow for canvas designer (future)**: The MVP uses a linear step list (Fluent UI
   Cards). When CONDITION steps with branching are production-ready, the designer should be
   upgraded to a canvas using `@xyflow/react`. The `WorkflowDesigner` component should be
   designed with a clear `StepList -> StepsJson` serialization interface so the rendering
   layer can be swapped independently.

3. **`WorkflowDecisionResource` extension**: The existing `GET /workflows/steps/pending`
   endpoint already works. It should be extended to also return the exchange name, trigger
   event, and current step type for richer inbox rendering in the UI.

4. **Org settings UI**: The `OrganizationTab` in Settings needs a new toggle for
   `requireRecipientAcceptance`. This is a small addition to the existing org settings form.

5. **Audit trail**: `WorkflowStepInstance.decisionsJson` already records all decisions. The
   `WorkflowInstanceDetail` drawer in the UI should render this as a readable timeline
   (who, what decision, when, reason).

6. **Future Marketplace readiness**: The portability foundation built in this phase (industry
   tags, `isTemplate`, `sourceTemplateId`, clone with UUID scrubbing) is sufficient to bolt
   on a public browse/publish surface later. When that time comes, a
   `WorkflowMarketplaceListing` entity wraps a `WorkflowDefinition` with public metadata
   (publisher org, publish status, install count, scrubbed `previewStepsJson`). No engine
   changes are needed at that point.

