# Feature: Recipient-Side Internal Workflows

## Context

Recipients viewing an exchange can currently see all workflow instances for that exchange, including the initiator org's internal approval steps, assignees, and decisions. This violates org sovereignty — internal workflows should never be visible across organizational boundaries.

The fix is to scope workflow instance visibility to the viewer's organization. The feature extension is to let recipient orgs trigger their own internal workflows when exchange lifecycle events fire, and optionally add an opt-in "Wait for Counterparty Clearance" step type that lets one party's workflow pause until the other's completes.

---

## What We're Building

1. **Bug Fix** — filter the workflow instances returned by `GET /exchanges/{exchangeId}/workflow-instances` to only include instances owned by the calling user's organization.
2. **Recipient-side trigger events** — when exchange lifecycle events fire, also fire parallel events in each recipient org's context so their workflow definitions can respond.
3. **Internal Clearance Status API** — a lightweight endpoint that exposes each party's aggregate workflow status (NONE / RUNNING / CLEARED) without leaking internal step details.
4. **Opt-in Cross-boundary Clearance Gate** — a new step type `WAIT_FOR_COUNTERPARTY_CLEARANCE` that pauses a workflow until the other party's workflows on the same exchange have all reached a terminal state.

---

## Phase 1 — Bug Fix: Org-Scoped Instance Visibility

**The problem:** `WorkflowInstanceRepository.findForSubject()` queries all instances for a given `(resourceType, resourceId)` pair with no org filter. `ExchangeResource.getExchangeWorkflowInstances()` returns all of them to any authenticated exchange member.

**The fix (3-layer change, backend only):**

1. **Repository** — Add an overload of `findForSubject` that accepts an optional `organizationId` and adds a `WHERE i.organizationId = :orgId` clause to the existing JPQL query.

2. **Service** — `WorkflowDefinitionService.listInstancesForSubject()` receives the caller's org ID and passes it to the new repository method.

3. **Resource/Controller** — `ExchangeResource.getExchangeWorkflowInstances()` resolves the calling user's org ID (already available from the security context / `AppUserContext`) and threads it down to the service call.

**Frontend:** No change needed — the endpoint now returns only the caller's org's instances, which is the correct filtered set.

---

## Phase 2 — Recipient-Side Trigger Events

**New trigger events (to be seeded in a new DB migration V13):**

| Event Name | Recipient Perspective Of |
|---|---|
| `exchange.received` | `exchange.acceptance_pending` |
| `exchange.received_activated` | `exchange.activated` |
| `exchange.received_ending` | `exchange.ending` |

**Dual-trigger dispatch (in `ExchangeApprovalEventHandler`):**

After each existing initiator-side `workflowEngineService.trigger(...)` call, add a second loop that:
1. Calls a new helper `ExchangeParticipantOrgService.findRecipientOrgIds(exchangeId): List<UUID>`.
2. For each recipient org, fires `workflowEngineService.trigger(TriggerRequest(triggerEvent = "exchange.received", organizationId = recipientOrgId, subjectData = ...))`.

The subject data for recipient-side triggers should include the same fields as the initiator-side plus `recipientOrgId` so recipient workflow definitions can reference it.

**New helper: `ExchangeParticipantOrgService`**

Encapsulates the two-step lookup that currently has no single method:
1. `shareRepository.findAllByResource(ResourceType.EXCHANGE, exchangeId)` → filter for `principalKind == USER` shares with status `ACTIVE`.
2. For each user principal, call `organizationMembershipRepository.findActiveByUser(principalId)` to get their org memberships.
3. Return distinct org IDs, excluding the initiator's org (to avoid double-triggering).

---

## Phase 3 — Internal Clearance Status API

**New endpoint:** `GET /exchanges/{exchangeId}/workflow-clearance-status`

**Response shape:**
```
{
  myOrg: { status: "NONE" | "RUNNING" | "CLEARED" | "BLOCKED" },
  counterparties: [
    { status: "NONE" | "RUNNING" | "CLEARED" | "BLOCKED" }
    // one entry per counterparty org — no org name or details exposed
  ]
}
```

**Backend logic:**
- "My org" = workflow instances for this exchange where `organizationId == callerOrgId`. Aggregate to: NONE (no instances), RUNNING (any instance RUNNING), CLEARED (all COMPLETED), BLOCKED (any REJECTED/CANCELLED).
- "Counterparties" = workflow instances where `organizationId != callerOrgId`, grouped by org. Same aggregation logic. **No step details, no assignee info crosses the boundary.**

**Frontend:** `ExchangeWorkflowTab.tsx` calls this new endpoint and renders an "Internal Clearance Status" summary card at the top of the tab, above the instance list. The card shows:
- Caller's own clearance status (if they have workflows running)
- Counterparty clearance status (aggregate badge only: Running / Cleared / None required)

---

## Phase 4 — Opt-in Cross-boundary Clearance Gate

**New step type:** `WAIT_FOR_COUNTERPARTY_CLEARANCE`

This step type can be added anywhere in a workflow definition's steps array. When the workflow engine advances to a step of this type:

1. It queries all workflow instances for the same exchange where `organizationId != this instance's organizationId`.
2. If **any** of those instances have status `RUNNING`, this step enters a new status `AWAITING_COUNTERPARTY` — the instance remains RUNNING but does not advance.
3. When **any** workflow instance reaches a terminal state (COMPLETED / REJECTED / CANCELLED), the engine re-evaluates all `AWAITING_COUNTERPARTY` steps across all instances for the same exchange. If the counterparty instances are all now terminal, the waiting step is unblocked and the workflow advances.

**Schema changes (DB migration V13):**
- Add `AWAITING_COUNTERPARTY` as a valid value to the step instance status check constraint (or enum).
- Add a new index on `workflow_instance(subject_resource_type, subject_resource_id, status)` to make the re-evaluation query fast.

**Engine change — re-evaluation trigger:**
In `DefaultWorkflowEngineService`, at the end of any step completion that advances an instance to a terminal state, add a call to `unblockWaitingCounterpartySteps(subjectResourceType, subjectResourceId)`. This method runs the re-evaluation query above and resumes any unblocked steps.

**Workflow definition builder (frontend):**
Add `WAIT_FOR_COUNTERPARTY_CLEARANCE` as a selectable step type in the step editor with a description: "Pauses this workflow until all workflows on the other party's side of this exchange have completed."

---

## DB Migration V13

New file: `V13__recipient_workflows.sql`

Contents:
1. Insert new rows into `workflow_trigger_event_registry` for `exchange.received`, `exchange.received_activated`, `exchange.received_ending`.
2. Add `AWAITING_COUNTERPARTY` to the step instance status constraint/enum.
3. Add the compound index `ix_wf_inst_subject_status` on `(subject_resource_type, subject_resource_id, status)`.

---

## Critical Files to Modify

| Layer | File | Change |
|---|---|---|
| DB | `V13__recipient_workflows.sql` (new) | Seed events, new status, new index |
| Repository | `WorkflowInstanceRepository.kt` | Org-filtered `findForSubject` overload |
| Service | `WorkflowDefinitionService.kt` | Pass org ID to repo; add clearance aggregation |
| Service (new) | `ExchangeParticipantOrgService.kt` | `findRecipientOrgIds(exchangeId)` |
| Event Handler | `ExchangeApprovalEventHandler.kt` | Dual-trigger loop; new recipient event constants |
| Engine | `DefaultWorkflowEngineService.kt` | `WAIT_FOR_COUNTERPARTY_CLEARANCE` step logic + `unblockWaitingCounterpartySteps()` |
| Resource | `ExchangeResource.kt` | Org-filtered instance query; new clearance-status endpoint |
| Frontend Tab | `ExchangeWorkflowTab.tsx` | Add clearance status card; call new endpoint |
| Frontend Builder | Workflow step editor component | Add new step type option |

---

## Implementation Order

1. **Phase 1 (Bug fix)** — standalone, shippable independently. No new schema.
2. **Phase 3 (Clearance Status API)** — can be built on top of Phase 1; no engine changes.
3. **Phase 2 (Recipient triggers + V13 migration)** — requires the new DB migration. After this, recipient orgs can configure workflow definitions that respond to `exchange.received`.
4. **Phase 4 (Cross-boundary gate)** — builds on Phases 2 & 3. Schema and engine changes. Implement last.

---

## Verification

- **Phase 1**: Log in as a recipient user; open an exchange; the Workflow tab should show zero instances (or only ones their own org triggered). Log in as the initiator; all instances appear as before.
- **Phase 2**: Configure an ORG-scoped workflow on a recipient org with trigger event `exchange.received`. Send an exchange to that org. A new workflow instance appears on the recipient's Workflow tab; nothing new appears on the initiator's tab.
- **Phase 3**: Both parties see the clearance status card. Initiator sees "Recipient: Running" while recipient's workflow is active; updates to "Cleared" when it completes.
- **Phase 4**: Add a `WAIT_FOR_COUNTERPARTY_CLEARANCE` step to an initiator-side workflow. Trigger the exchange. The initiator's workflow pauses at that step with status `AWAITING_COUNTERPARTY`. Complete the recipient's workflow. The initiator's workflow automatically resumes.
