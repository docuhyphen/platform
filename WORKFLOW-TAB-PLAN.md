# Plan: Workflow Tab on Exchange Details

## Context

Users need visibility into where an Exchange sits within its workflow — who has approved, who is pending, and why an Exchange may be blocked. The Exchange details panel currently has Documents, Details, and Audit tabs. A new **Workflow tab** provides a process-monitoring experience: real-time status, the active step, pending actors, SLA/escalation warnings, and a full decision history — without leaving the Exchange.

This is distinct from:
- The **Audit tab** — raw chronological event log
- The **Workflow Settings area** — definition management / designer

Architecture decision: **No BPMN diagrams, no flowchart editors.** This is a workflow status and monitoring UI. Fluent UI v9 / Fluent 2 components only.

---

## Backend Changes

### 1. Repository — `WorkflowInstanceRepository.kt`

Add `findForSubject` to return **all** instances (any status) for an exchange, newest first:

```kotlin
fun findForSubject(resourceType: String, resourceId: UUID): List<WorkflowInstance>
```
JPQL: filter on `subjectResourceType = :rt AND subjectResourceId = :rid`, `ORDER BY createdAt DESC`.

**File:** `src/main/kotlin/com/docuhyphen/app/api/repository/WorkflowInstanceRepository.kt`

### 2. Service — `WorkflowDefinitionService.kt`

Add delegation to keep `ExchangeResource` thin:

```kotlin
fun listInstancesForSubject(resourceType: String, resourceId: UUID): List<WorkflowInstanceListItemDto>
```
Calls `instanceRepository.findForSubject(...)`, maps with the existing `toListItemDto` mapper (resolving `definitionName` from `definitionRepository`).

**File:** `src/main/kotlin/com/docuhyphen/app/api/service/workflow/WorkflowDefinitionService.kt`

### 3. Endpoint — `ExchangeResource.kt`

```
GET /exchanges/{exchangeId}/workflow-instances
```
Returns `WorkflowInstanceListItemDto[]`. Auth gate: `exchangeRetrievalService.getExchange(exchangeId)` (throws `ExchangeNotFoundException` for non-members). Delegates to service with `resourceType = "EXCHANGE"`.

Full step detail is served by the **existing** `GET /workflows/instances/{id}` endpoint — loaded per-instance in the frontend.

**File:** `src/main/kotlin/com/docuhyphen/app/api/resource/ExchangeResource.kt`

> ⚠️ Verify the exact `subjectResourceType` string stored at trigger time in `ExchangeInitiationService.kt`. The `TriggerRequest` data class (in `WorkflowEngineService.kt`) has `subjectResourceType: String?` — check the call site to confirm the value passed (expected `"EXCHANGE"`).

---

## Frontend: Component Hierarchy

```
ExchangeWorkflowTab                          ← root (receives ExchangeDetailedDto)
├── LoadingState                             ← Spinner during fetch
├── ErrorState                               ← error card if fetch fails
├── NoWorkflowState                          ← empty state: no instances
└── WorkflowInstanceSection[]                ← one section per instance
    ├── WorkflowSummaryCard                  ← top-level status card
    ├── RejectionBanner (conditional)        ← prominent rejection callout
    ├── ActionRequiredCard (conditional)     ← approve/reject if user has pending step
    └── WorkflowTimeline                     ← vertical step list
        └── WorkflowTimelineItem[]           ← Accordion, one per step
```

---

## Frontend: Data Loading

**State shape:**
```typescript
loading: boolean
error: string | null
instances: WorkflowInstanceDetailDto[]      // full detail, loaded eagerly
pendingSteps: PendingWorkflowStep[]         // from GET /workflows/steps/pending
```

**On `exchange.id` change, run in parallel:**
1. `fetchExchangeWorkflowInstances(exchange.id)` → list of summary items (new function in `exchangeApi.ts`)
2. For each summary item: `getWorkflowInstanceDetail(item.id)` → full `WorkflowInstanceDetailDto` with steps (existing in `workflowService.ts`)
3. `getMyPendingDecisions()` → current user's pending steps (existing in `workflowApi.ts`)

**Reuse these existing functions — do not rewrite them:**
- `getWorkflowInstanceDetail(id)` — `web-app/src/services/workflowService.ts`
- `getMyPendingDecisions()` — `web-app/src/services/workflowApi.ts`
- `recordWorkflowDecision(stepInstanceId, request)` — `web-app/src/services/workflowApi.ts`
- `INSTANCE_STATUS_LABELS`, `STEP_TYPE_LABELS`, `STEP_STATUS_LABELS`, `DECISION_LABELS`, `PRINCIPAL_KIND_LABELS` — `web-app/src/app/settings/workflows-tab/workflowUtils.ts`
- `SettingsWorkflowsTabIcon` — `web-app/src/app/components/IconBundles.tsx`
- `formatDate` / `formatEpoch` — `web-app/src/app/helpers.ts`

**Key DTO types (already defined in `web-app/src/app/models/models.tsx`):**
- `WorkflowInstanceSummaryDto` — id, definitionId, definitionName, status, currentStepIndex, createdAt, completedAt
- `WorkflowInstanceDetailDto extends WorkflowInstanceSummaryDto` — adds `steps: WorkflowStepInstanceDto[]`
- `WorkflowStepInstanceDto` — id, stepIndex, stepType, status, assignees, decisions, dueAt, escalatedAt, completedAt, createdAt
- `WorkflowDecisionEntryDto` — principalKind, principalId, decision ('APPROVE'|'REJECT'), reason, atEpochMillis

**Pending step type (in `web-app/src/services/types/dtos.ts`):**
- `PendingWorkflowStep` — stepInstanceId, workflowInstanceId, stepType, exchangeId, name, requestedByEmail, requestedByName, groupName, createdAtEpochMillis

---

## Frontend: Component Specs

### `WorkflowSummaryCard`

Fluent components: `Card`, `CardHeader`, `Badge`, `Text`, `ProgressBar`

| Field | Source |
|---|---|
| Workflow name | `instance.definitionName` |
| Status badge | `instance.status` → `INSTANCE_STATUS_LABELS` + badge colour |
| Current step | Step at `instance.currentStepIndex` → `STEP_TYPE_LABELS[stepType]` |
| Waiting on | Assignees of the active PENDING step (USER kind → name; PRINCIPAL_GROUP kind → group name) |
| Progress | `stepsCompleted / totalSteps` as `ProgressBar` + "3 of 6 Steps Complete" |

- `stepsCompleted` = count of steps with status `APPROVED`, `COMPLETED`, or `SKIPPED`
- `totalSteps` = `instance.steps.length`

**SLA/Escalation row** (only if active step has `dueAt` or `escalatedAt`):
- Overdue: `dueAt < now && status === 'PENDING'` → amber Badge "⚠ Approval overdue by X days"
- Escalated: `escalatedAt` set → "⚠ Escalated" + date

---

### `RejectionBanner`

Render only when `instance.status === 'REJECTED'`.

Find first step with `status === 'REJECTED'`, then the REJECT decision within it.

Display using Fluent `MessageBar` (intent="error") or a `Card` with red accent border:
- "Workflow Rejected"
- Rejected by: `decision.principalId` / principal kind label
- Date: `formatEpoch(decision.atEpochMillis)`
- Reason: `decision.reason` (if present)

**Placed above the timeline** so it is immediately visible without scrolling.

---

### `ActionRequiredCard`

**Condition:** `pendingSteps.some(p => p.workflowInstanceId === instance.id)`

Find the matching `PendingWorkflowStep` and cross-reference `stepInstanceId` with the active step's `id`.

Renders a Card with accent border:
```
Action Required
───────────────────
{step type label}

[Approve]  [Reject]
```

- Buttons call `recordWorkflowDecision(stepInstanceId, { decision: 'APPROVE'|'REJECT', reason? })`
- On success: re-fetch instance detail + pending steps
- Show loading state on buttons while submitting
- If user cannot act: **do not render this card**

---

### `WorkflowTimeline`

Vertically stacked `WorkflowTimelineItem` components. **No DataGrid, no Table.**

Each item uses Fluent `AccordionItem`.

**Step icon mapping:**
| Status | Icon |
|---|---|
| APPROVED / COMPLETED | `CheckmarkCircleFilled` (green) |
| PENDING — active step | `RecordCircleFilled` (blue) |
| PENDING — future step | `CircleRegular` (neutral) |
| REJECTED | `DismissCircleFilled` (red) |
| ESCALATED | `WarningFilled` (amber) |
| SKIPPED | `ArrowForwardFilled` (neutral muted) |

**Collapsed view:** icon + step type label + status + brief summary ("Approved by John Smith · 15 Jan 2026" or "Waiting for action")

**Expanded view (inside Accordion panel):**
- Assignees list (each with `PRINCIPAL_KIND_LABELS[a.kind]`)
- Decisions: decision badge (APPROVE green / REJECT red), principal label, `formatEpoch(d.atEpochMillis)`, reason text
- Due date / SLA: `dueAt` formatted, overdue flag
- Escalation: `escalatedAt` if set

**Active step** (index === `currentStepIndex` and status === `'RUNNING'`) starts expanded by default. All others start collapsed.

---

### Empty & Null States

| Scenario | Display |
|---|---|
| No instances returned | Card: "This exchange does not require workflow approval." |
| Instances exist but none RUNNING | Show completed instance(s) in timeline view |
| Fetch error | Error card with message text |

Never render a blank panel.

---

### Multiple Workflow Instances

Render one `WorkflowInstanceSection` per instance, stacked vertically, each headed by `definitionName` (e.g., "Creation Workflow", "Activation Workflow"). No nested TabList or dropdown for the initial implementation. If 3+ instances becomes crowded, a selector dropdown can be added as a follow-on.

---

## Frontend: New Files

All under `web-app/src/app/exchanges/components/exchange-workflow-tab/`:

| File | Purpose |
|---|---|
| `ExchangeWorkflowTab.tsx` | Root component — data loading, section layout |
| `WorkflowSummaryCard.tsx` | Summary card with status, progress, waiting-on |
| `RejectionBanner.tsx` | Prominent rejection callout |
| `ActionRequiredCard.tsx` | Conditional approve/reject action card |
| `WorkflowTimeline.tsx` | Accordion-based vertical step list |
| `WorkflowTimelineItem.tsx` | Single step row (collapsed + expanded) |
| `ExchangeWorkflowTabStyles.tsx` | `makeStyles` definitions |

---

## Frontend: Modified Files

| File | Change |
|---|---|
| `web-app/src/services/exchangeApi.ts` | Add `fetchExchangeWorkflowInstances(exchangeId): Promise<WorkflowInstanceSummaryDto[]>` |
| `web-app/src/app/exchanges/Exchanges.tsx` | Add import, `<Tab value="workflow">` after Audit tab, content panel block |

**Wire-up in `Exchanges.tsx`:**
```tsx
// Import
import ExchangeWorkflowTab from "./components/exchange-workflow-tab/ExchangeWorkflowTab";
import { SettingsWorkflowsTabIcon } from "../components/IconBundles";

// Tab (after Audit <Tab>)
<Tab value="workflow" icon={<SettingsWorkflowsTabIcon />}>Workflow</Tab>

// Content panel (after detailsActiveTab === 'audit' block)
{detailsActiveTab === 'workflow' && (
    <div className={styles.documentsSectionContainer}>
        <div className={styles.documentsSection}>
            <ExchangeWorkflowTab exchange={exchangeDetails} />
        </div>
    </div>
)}
```

---

## Permission-Aware Rendering

The backend already enforces access:
- `GET /exchanges/{id}/workflow-instances` gates on exchange membership
- `GET /workflows/steps/pending` returns only the current user's actionable steps

Frontend rules:
- **Assignee display:** render what the backend returns. `principalKind === 'USER'` → show id/name. `principalKind === 'PRINCIPAL_GROUP'` → show group name. Backend visibility filtering controls what's in the DTO.
- **Action Required card:** gate purely on presence in `pendingSteps`. No additional role checks needed.

A more granular visibility model (hiding assignee names from external participants) would require a backend `visibilityLevel` flag on the DTO — out of scope for this implementation.

---

## Verification Steps

1. Backend: `GET /exchanges/{id}/workflow-instances` returns instances for an exchange with a running workflow; `[]` for exchanges without one.
2. Workflow tab appears after "Audit" in the Exchange details panel.
3. Summary card shows correct workflow name, status badge, current step, and progress bar.
4. Rejection banner is visible and prominent for a rejected workflow instance.
5. Action Required card appears only for a user who has a pending step on that exchange; not for other users.
6. Approve/Reject buttons submit and the tab refreshes with updated status.
7. Timeline renders all steps; active step is expanded by default; others collapsed; each expands on click.
8. Empty state renders cleanly for exchanges with no workflow.
9. Multiple instances (e.g., creation + activation) render as separate stacked sections with clear headers.
10. SLA overdue warning appears on summary card and step detail for a step past its `dueAt`.
