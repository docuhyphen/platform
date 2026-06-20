# DocuHyphen vs. Make.com: Feature Gap Analysis

**Document Purpose**: Comprehensive gap analysis for an LM/agent to understand what DocuHyphen is missing to compete with Make.com for general workflow/integration automation.

**Last Updated**: June 20, 2026  
**Version**: 1.0  
**Audience**: Development team, product strategy, engineering leads

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Current DocuHyphen Capabilities](#current-docuhyphen-capabilities)
3. [Make.com Feature Categories](#makecom-feature-categories)
4. [Gap Analysis by Category](#gap-analysis-by-category)
5. [Critical Gaps](#critical-gaps)
6. [Medium-Priority Gaps](#medium-priority-gaps)
7. [Nice-to-Have Gaps](#nice-to-have-gaps)
8. [Architecture Notes for Implementation](#architecture-notes-for-implementation)
9. [Risk Assessment](#risk-assessment)

---

## Executive Summary

**What DocuHyphen Does Well**:
- Document-exchange lifecycle management (INITIATED → ACCEPTED_STARTED → ENDED)
- Multi-step workflow orchestration with approval quorum logic
- SLA tracking, escalation, and conditional branching
- Event-driven architecture with `DomainEvent` → `EventRouter` → handlers
- Extensible action handler framework (`WorkflowActionHandler` interface)
- Industry-specific workflow templates with cloning/portability

**What Make.com Does That DocuHyphen Doesn't**:
- 1000+ pre-built app connectors (Salesforce, Slack, Gmail, Stripe, etc.)
- Visual drag-and-drop canvas for cross-app workflows
- Bidirectional data sync (push *and* pull from external systems)
- Native Slack/Teams/email integration with rich formatting
- Advanced scheduling, delays, and conditional routing
- Data transformation and mapping UI
- Webhook management (inbound *and* outbound)
- Multi-tenant app marketplace
- Error handling and retry policies (per connection)

**Gap Severity Scale**:
- 🔴 **Critical** - blocks Make.com parity for general users
- 🟠 **High** - limits appeal to Make.com migrants
- 🟡 **Medium** - useful but not deal-breakers
- 🟢 **Low** - nice-to-have polish

---

## Current DocuHyphen Capabilities

### Backend (Fully Implemented)

| Capability | Status | Notes |
|---|---|---|
| Multi-step workflows | ✅ Complete | APPROVAL, NOTIFICATION, CONDITION, ACTION step types |
| Quorum-based approvals | ✅ Complete | ANY, ALL, N_OF_M support |
| SLA & escalation | ✅ Complete | Escalate, auto-reject, auto-approve |
| Event publishing | ✅ Complete | `DomainEvent` → `EventRouter` |
| Event routing to rules engine | ✅ Complete | `NotificationRuleEngine` + `DeliveryDispatcher` |
| In-app notifications | ✅ Complete | WebSocket + polling |
| Email notifications | ✅ Complete | AWS SES integration |
| Conditional logic (CONDITION steps) | ✅ Complete | `==`, `!=`, `contains`, `startsWith` predicates |
| Action handler registry | ✅ Complete | Pluggable `WorkflowActionHandler` SPI |
| Built-in actions | ✅ Partial | `exchange.auto-accept`, `exchange.send-reminder`, `exchange.revoke-access` |
| Scheduled escalation | ✅ Complete | `WorkflowEscalationScheduler` (60s tick) |
| Addon reminders | ✅ Complete | `ReminderBeforeDue`, `ReminderIfNoDecision` |
| Workflow templates | ✅ Complete | `isTemplate=true`, cloneable with UUID scrubbing |
| Trigger event registry | ✅ Complete | 5 canonical events seeded |

### Frontend (Phases 1-5 Complete)

| Component | Status | Notes |
|---|---|---|
| Visual workflow designer | ✅ Complete | Linear step list + settings header |
| Step card editor | ✅ Complete | Type, assignees, quorum, SLA, escalation, outcomes |
| Assignee builder | ✅ Complete | ROLE, PRINCIPAL, GROUP_ROLE variants |
| Workflow list view | ✅ Complete | My Workflows + Platform Templates |
| Workflow instance dashboard | ✅ Complete | List + filter by status |
| Workflow instance detail drawer | ✅ Complete | Step timeline + decision audit trail |

### Notification Channels (Stubbed/Partial)

| Channel | Implementation | Notes |
|---|---|---|
| Email | ✅ Complete | AWS SES + template rendering |
| In-App | ✅ Complete | Database + WebSocket push |
| SMS | 🟡 Stubbed | Logs only, no real integration |
| Slack | 🟡 Stubbed | Logs only, no real integration |
| Teams | 🟡 Stubbed | Logs only, no real integration |
| WhatsApp | 🟡 Stubbed | Logs only, no real integration |

---

## Make.com Feature Categories

### 1. App Connectors & Integrations

**What Make.com offers**:
- 1000+ pre-built connectors (Salesforce, Hubspot, Slack, Gmail, Stripe, Twilio, etc.)
- Each connector has multiple modules (search, create, update, delete, trigger on, etc.)
- OAuth/token management at platform level
- Built-in request/response mapping per connector

### 2. Automation Building

**What Make.com offers**:
- Visual drag-and-drop canvas (nodes + edges)
- Scenario templates (quick-start automations)
- Data mapping/transformation UI (GUI, not code)
- Conditional routing (IF/THEN/ELSE with formula support)
- Loop/repeat constructs
- Error handling routing per module

### 3. Data & Triggering

**What Make.com offers**:
- Inbound webhooks (listen for external events)
- Outbound webhooks (push data to external systems)
- Scheduled triggers (cron)
- Polling-based triggers (check external API every N minutes)
- Instant triggers (real-time from app events)

### 4. Scheduling & Delays

**What Make.com offers**:
- Time delays between steps
- Scheduled execution (one-time or recurring)
- Deferred/throttle execution
- Queue management

### 5. Error Handling & Retry

**What Make.com offers**:
- Per-module retry policies (max retries, backoff strategy)
- Error routing branches
- Webhook delivery logs + manual retry
- Dead-letter handling

### 6. Monitoring & Analytics

**What Make.com offers**:
- Execution logs with full payloads
- Performance metrics
- Error alerts
- Usage-based billing visibility

---

## Gap Analysis by Category

### 🔴 CRITICAL GAPS

#### **1. Outbound Webhooks**
**What's Missing**: DocuHyphen fires internal events but **does not expose them as outbound webhooks**.

**Make.com Equivalent**: "Send to Webhook" module (push any data to external URLs).

**Impact**:
- Make.com users use webhooks to trigger downstream systems (Salesforce CRM, Zapier, IFTTT)
- DocuHyphen users cannot push events to external systems without code
- **Workaround**: Polling external systems or building custom middleware

**What Exists in DocuHyphen**:
- `DomainEvent` → `EventRouter` infrastructure (internal only)
- `DeliveryTask` model for multi-channel dispatch
- `NotificationChannelType` enum (extensible)

**What Needs to Be Built**:
- [ ] `WebhookChannel` implementation (currently stubbed as `NotificationChannel`)
- [ ] `OrganizationWebhookConfig` entity (URL, auth headers, event filtering)
- [ ] `WebhookDeliveryLog` table (track delivery success/failure/retry)
- [ ] Webhook retry logic with exponential backoff
- [ ] Org admin UI to configure webhook endpoints
- [ ] Webhook signature verification (HMAC-SHA256)

**Priority**: 🔴 **CRITICAL** — blocks ~60% of Make.com automation use cases

---

#### **2. Inbound Webhooks**
**What's Missing**: No way to *trigger* DocuHyphen workflows from external systems.

**Make.com Equivalent**: "Webhook" trigger module (listen for incoming POST from external app).

**Impact**:
- Make.com users listen for Salesforce Deal.Created → trigger Make scenario
- DocuHyphen workflows are event-triggered internally only (exchange.created, etc.)
- Cannot trigger workflows from external CRM, form submission, etc.
- **Workaround**: Call REST API directly (but no API endpoint exists for "invoke workflow")

**What Exists in DocuHyphen**:
- REST API at `/workflows/definitions` (CRUD only)
- No trigger invocation endpoint

**What Needs to Be Built**:
- [ ] `POST /workflows/triggers/invoke` endpoint (invoke a workflow by definition + subject data)
- [ ] Webhook registration per org (unique URL endpoint to POST to)
- [ ] Request payload validation + transformation
- [ ] Org-level API key + webhook signature verification
- [ ] Audit trail (who invoked, when, with what payload)

**Priority**: 🔴 **CRITICAL** — required for "external event → DocuHyphen workflow" bridging

---

#### **3. Bidirectional Data Sync**
**What's Missing**: DocuHyphen pushes notifications but **does not pull/sync data from external systems**.

**Make.com Equivalent**: Search/Lookup modules (query Salesforce, HubSpot, etc. inside a scenario).

**Impact**:
- Make.com can "search Salesforce for Contact by email, then update if found, else create"
- DocuHyphen has no way to query external systems during workflow execution
- Cannot enrich exchange data with external context (CRM account type, JIRA ticket status, etc.)
- **Workaround**: Hardcode lookups in custom action handlers

**What Exists in DocuHyphen**:
- `WorkflowActionHandler` SPI (extensible)
- `$subject.*` placeholder substitution in assignees/routes

**What Needs to Be Built**:
- [ ] **Generic HTTP Action Handler** - allows workflows to call external APIs mid-execution
  - `GET/POST/PUT/DELETE` support
  - Request body templating with `$subject.*` placeholders
  - Response body parsing → store in exchange context (optional)
- [ ] **Connector-specific integrations** (lower priority, can start with Salesforce + HubSpot)
  - Salesforce connector (OAuth, search contacts, create/update records)
  - HubSpot connector (list deals, companies, etc.)
  - Stripe connector (lookup customers, invoices)

**Priority**: 🔴 **CRITICAL** — enables advanced use cases like "auto-enrich from CRM"

---

#### **4. Inbound Notification Channels (Slack, Teams)**
**What's Missing**: Receiving *interactive* messages from Slack/Teams (approvals via button click).

**Make.com Equivalent**: Slack "Send Message" (with interactive elements) → user clicks button → triggers next step.

**Impact**:
- Make.com scenarios post Slack messages with Action Buttons
- User clicks "Approve" button → workflow advances
- DocuHyphen sends email/in-app notifications only; no way to approve via Slack
- **Workaround**: Users must open app/email to approve

**What Exists in DocuHyphen**:
- Stubbed Slack/Teams channels (log-only)
- `WorkflowDecisionResource` (`POST /workflows/steps/{id}/decision`) – already records approvals

**What Needs to Be Built**:
- [ ] Slack integration (OAuth, post interactive messages to channel)
- [ ] Interactive Slack buttons (Approve, Reject) → webhook callback → `recordDecision()`
- [ ] Teams integration (similar pattern)
- [ ] User channel preferences (Slack workspace link, Teams tenant, etc.)

**Priority**: 🔴 **CRITICAL** — required for non-email workflow communication

---

### 🟠 HIGH-PRIORITY GAPS

#### **5. Visual Canvas Designer**
**What's Missing**: Workflows are visualized as linear step list, not a graph/canvas.

**Make.com Equivalent**: Drag-and-drop visual builder with connectors showing flow.

**Impact**:
- Complex workflows with many branches are hard to visualize in a list
- CONDITION branching (onTrue/onFalse) is not visually obvious
- **Workaround**: Users must read JSON or step cards in sequence

**What Exists in DocuHyphen**:
- Linear `StepCard` list in `WorkflowDesigner.tsx`
- All frontend infrastructure (TypeScript, React, Fluent UI)

**What Needs to Be Built**:
- [ ] Integrate `@xyflow/react` (React Flow) canvas library
- [ ] Render workflow steps as nodes, outcomes as edges
- [ ] Drag-to-connect, add-step UI
- [ ] Conditional branching visualization (diamond nodes for CONDITION type)
- [ ] Auto-layout + manual positioning

**Priority**: 🟠 **HIGH** — improves UX but workflows function fine in list form

---

#### **6. Scheduled/Recurring Triggers**
**What's Missing**: Workflows are event-triggered; no way to run on a schedule.

**Make.com Equivalent**: "Schedule" trigger (run scenario at fixed times or intervals).

**Impact**:
- Make.com scenarios can run "every Monday 9am"
- DocuHyphen workflows require an exchange to exist and fire an event
- Cannot do "send weekly digest of pending approvals" without external scheduler
- **Workaround**: Custom cron job calling REST API

**What Exists in DocuHyphen**:
- Quarkus `@Scheduled` framework (used for SLA escalation)
- Event publishing infrastructure

**What Needs to Be Built**:
- [ ] New trigger type: `SCHEDULED` (complements existing `EVENT`-based triggers)
- [ ] Org admin UI to define cron expression + parameters
- [ ] Scheduler job that instantiates workflows on schedule
- [ ] Subject data template (how to populate `subjectDataJson` for scheduled runs, e.g., `{ "action": "send_digest" }`)

**Priority**: 🟠 **HIGH** — enables digest/reminder use cases

---

#### **7. Data Transformation/Mapping UI**
**What's Missing**: No way to transform/enrich event data before routing.

**Make.com Equivalent**: "Set Multiple Variables" / "Router" (transform data, map fields, filter).

**Impact**:
- Make.com users can "extract email domain from sender → use in next step"
- DocuHyphen passes `$subject.*` placeholders directly; no transformation layer
- Cannot enrich, filter, or reshape data mid-workflow
- **Workaround**: Custom action handlers with hardcoded logic

**What Exists in DocuHyphen**:
- `CONDITION` step with basic predicate logic (==, !=, contains, startsWith)
- `$subject.*` placeholder substitution

**What Needs to Be Built**:
- [ ] **Data Transformer step type** (new `WorkflowStepType.TRANSFORM`)
  - Input: `$subject.*` variables
  - Operation: extract, rename, compute (e.g., `{ recipientDomain: "$subject.recipientEmail.split('@')[1]" }`)
  - Output: enriched context passed to downstream steps
- [ ] UI builder (field mapper / expression editor)
- [ ] Support common transformations (split, substring, lowercase, format_date, etc.)

**Priority**: 🟠 **HIGH** — enables data-driven branching logic

---

#### **8. Error Handling & Retry Per Action**
**What's Missing**: Workflows have no per-action error handling or retry policies.

**Make.com Equivalent**: "Retry" policy, "Set Error Handler" routing.

**Impact**:
- Make.com actions can auto-retry on failure (exponential backoff)
- DocuHyphen ACTION steps either succeed or fail the whole workflow
- Transient errors (network timeout) reject the workflow instead of retrying
- **Workaround**: Manual re-trigger, or custom handler logic

**What Exists in DocuHyphen**:
- `WorkflowActionHandler.execute()` returns `ActionResult(success, reason)`
- Webhook retry logic (not yet built, see gap #1)

**What Needs to Be Built**:
- [ ] Per-step retry policy (max_attempts, backoff_strategy, retry_on_errors)
- [ ] Webhook/HTTP action handler with builtin retry (exponential backoff)
- [ ] Error handler routing (on ACTION failure → route to alternate step or notify)
- [ ] Dead-letter handling (steps that fail repeatedly → quarantine and alert admin)

**Priority**: 🟠 **HIGH** — improves reliability for integrations

---

#### **9. Execution Logs & Observability**
**What's Missing**: Limited visibility into workflow execution history.

**Make.com Equivalent**: Execution inspector (view each step's input/output, timing, errors).

**Impact**:
- Make.com shows every step's input, output, and errors with full payloads
- DocuHyphen has basic instance dashboard; no detailed step-level execution logs
- Debugging failed workflows is difficult
- **Workaround**: Database queries, logs

**What Exists in DocuHyphen**:
- `WorkflowInstanceDetail` drawer (step timeline + decisions)
- `NotificationDeliveryLog` table (tracked emails, etc.)
- Structured logging (SLF4J)

**What Needs to Be Built**:
- [ ] **WorkflowStepExecutionLog** table - record input/output for every step
  - Input: decoded spec + subject data snapshot
  - Output: step result, any side-effects (notifications sent, etc.)
  - Timing: start_at, end_at, duration_ms
- [ ] UI to inspect step execution (show input JSON, output JSON, errors)
- [ ] Performance metrics (average step duration, retry counts)

**Priority**: 🟠 **HIGH** — critical for debugging + support

---

### 🟡 MEDIUM-PRIORITY GAPS

#### **10. Multi-Org Webhook/Connector Marketplace**
**What's Missing**: No way for users to publish/share connectors or workflows across orgs.

**Make.com Equivalent**: Marketplace (browse/install community apps and templates).

**Impact**:
- Make.com marketplace has thousands of templates
- DocuHyphen has platform templates but no community contribution mechanism
- Cannot monetize or distribute org-specific connectors

**What Exists in DocuHyphen**:
- Workflow templates with `isTemplate=true` + cloning
- Industry tags for discovery

**What Needs to Be Built**:
- [ ] Marketplace table: `WorkflowMarketplaceListing` (publisher org, install count, rating, category)
- [ ] Org webhook/connector definitions shareable (with scrubbing of org-specific IDs)
- [ ] Rating/review system
- [ ] Install counter + analytics

**Priority**: 🟡 **MEDIUM** — nice-to-have for ecosystem growth, not critical for MVP

---

#### **11. Advanced Scheduling Features**
**What's Missing**: Cannot delay, throttle, or defer steps.

**Make.com Equivalent**: "Wait" module (delay N seconds/minutes/hours), "Iterator" (loop over array).

**Impact**:
- Make.com can "wait 24 hours then send reminder"
- DocuHyphen reminders are addon-based only (time-relative to SLA, not absolute delays)
- Cannot build "escalate after 2 days, then after 7 days" sequences without multiple definitions
- **Workaround**: Custom scheduled tasks

**What Needs to Be Built**:
- [ ] Step delay/wait mode (absolute delay or time-relative)
- [ ] Iterator step type (loop over array in `$subject.*`)
- [ ] Throttle/deduplication per subject

**Priority**: 🟡 **MEDIUM** — useful but not deal-breaker

---

#### **12. Formula/Expression Language**
**What's Missing**: Limited expression support (only basic predicates in CONDITION steps).

**Make.com Equivalent**: Formula builder (JavaScript-like syntax for data transformations).

**Impact**:
- Make.com users can write `{{companyName}}.com` or `Math.floor(revenue / 12)`
- DocuHyphen predicates are hardcoded operators (==, !=, contains, startsWith)
- Cannot compute derived fields or format strings
- **Workaround**: Custom action handlers

**What Needs to Be Built**:
- [ ] Expression parser (e.g., SpEL - Spring Expression Language, or Kotlin eval)
- [ ] Functions: string manipulation, math, date formatting
- [ ] Variable scope: `$subject.*`, `$step.*` (output from previous steps)
- [ ] UI formula editor with autocomplete

**Priority**: 🟡 **MEDIUM** — powerful but can defer; basic workflows work without it

---

#### **13. Bulk/Batch Workflow Triggering**
**What's Missing**: Cannot trigger multiple instances in bulk.

**Make.com Equivalent**: "Iterator" + batch processing (apply scenario to each item in list).

**Impact**:
- Make.com can iterate over 1000 records and create 1000 records in target system
- DocuHyphen workflows are 1:1 (one workflow per exchange)
- Cannot do bulk operations like "send batch of exchanges for approval"

**What Needs to Be Built**:
- [ ] Batch trigger endpoint (POST `/workflows/definitions/{id}/trigger-batch` with list of subject data)
- [ ] Async job queue (do not block on response)
- [ ] Progress tracking + completion callback

**Priority**: 🟡 **MEDIUM** — useful for enterprise bulk operations

---

### 🟢 LOW-PRIORITY GAPS (Nice-to-Have)

#### **14. Approval Shortcut Links (Email/SMS)**
**What's Missing**: Users must click through to app to approve; no direct action links.

**Make.com Equivalent**: "Send Email" with action buttons or SMS with callbacks.

**Impact**: Users expect "click Approve in email" without opening app.

**Build**:
- [ ] Email template with "Approve" / "Reject" links (containing signed decision tokens)
- [ ] SMS template with callback URLs or short codes

**Priority**: 🟢 **LOW** — email can link to app UI, acceptable workaround

---

#### **15. Notification Digest/Batching**
**What's Missing**: Notifications sent individually; no option to batch hourly/daily digests.

**What Needs**: Digest scheduler to batch notifications + configurable user preferences.

**Priority**: 🟢 **LOW** — nice-to-have but not essential

---

#### **16. Multi-Language Support**
**What's Missing**: UI and workflows are English-only.

**Priority**: 🟢 **LOW** — defer until user demand

---

#### **17. White-Label / Branding**
**What's Missing**: No org-level branding (logo, colors, email footers).

**Priority**: 🟢 **LOW** — enterprise feature

---

---

## Critical Gaps

| Gap # | Title | Impact | Effort | Blocker? |
|---|---|---|---|---|
| 1 | Outbound Webhooks | Blocks external system integration | 3-4w | 🟠 Yes |
| 2 | Inbound Webhooks | Blocks external trigger → DocuHyphen | 2-3w | 🟠 Yes |
| 3 | Bidirectional Sync | Blocks data enrichment from CRM | 4-6w | 🟠 Yes |
| 4 | Interactive Slack/Teams | Blocks communication UX | 3-4w | 🟠 Yes |

These four gaps represent **~80% of Make.com usage patterns**. Closing them enables:
- "CRM event → DocuHyphen workflow → update CRM"
- "DocuHyphen workflow → Slack approval button → workflow continues"
- "External form submission → trigger DocuHyphen workflow"

---

## Medium-Priority Gaps

| Gap # | Title | Impact | Effort | Notes |
|---|---|---|---|---|
| 5 | Visual Canvas | UX improvement | 4-5w | Linear list works, not critical |
| 6 | Scheduled Triggers | Enables digest/reminders | 2-3w | Can use cron workaround |
| 7 | Data Transformation | Enables branching logic | 3-4w | CONDITION steps cover basics |
| 8 | Error Handling & Retry | Improves reliability | 3-4w | Impacts integrations most |
| 9 | Execution Logs | Observability | 2-3w | Necessary for debugging |

---

## Nice-to-Have Gaps

| Gap # | Title | Effort | Notes |
|---|---|---|---|
| 10 | Marketplace | 4-6w | Future revenue stream |
| 11 | Advanced Scheduling | 2-3w | Cron jobs work as workaround |
| 12 | Formula Language | 3-4w | Custom handlers work for now |
| 13 | Bulk Operations | 2-3w | Single workflow mode is fine for MVP |
| 14-17 | Polish (email shortcuts, digests, i18n, white-label) | 2-8w | Nice to have |

---

## Architecture Notes for Implementation

### Key Existing Infrastructure (Don't Rebuild)

1. **Event System**
   - `DomainEvent` (envelope)
   - `DomainEventPublisher` (interface)
   - `EventRouter` (router)
   - Future: Kafka-backed async (comments in code indicate this is planned)

2. **Notification Channels**
   - `NotificationChannel` interface (pluggable)
   - `NotificationChannelType` enum (extensible)
   - `DeliveryTask` model
   - `DeliveryDispatcher` coordination
   - Existing: EmailChannel, InAppChannel, StubChannels (SMS, Slack, Teams, WhatsApp)

3. **Action Handlers**
   - `WorkflowActionHandler` SPI (registry at startup)
   - `DefaultWorkflowEngineService` dispatches by key
   - Existing: ExchangeAutoAcceptActionHandler, ExchangeSendReminderActionHandler, ExchangeRevokeAccessActionHandler

4. **REST API Layer**
   - JAX-RS resource pattern (thin adapter)
   - JWT auth + AuthTokenContext
   - Org-level scoping

### Recommended Sequencing

**Phase 1 (Weeks 1-4)**: Close critical gaps 1-2 (webhooks)
- Outbound webhooks (push events to external URLs)
- Inbound webhooks (accept external POST to trigger workflows)
- **Enables**: "CRM → DocuHyphen → external system" chains

**Phase 2 (Weeks 5-8)**: Close critical gap 3 (bidirectional sync)
- Generic HTTP action handler (call external APIs mid-workflow)
- Optional: Salesforce/HubSpot connectors
- **Enables**: Workflow enrichment from CRM/third-party systems

**Phase 3 (Weeks 9-12)**: Close critical gap 4 (interactive Slack/Teams)
- Implement Slack channel properly (OAuth, interactive buttons)
- Implement Teams channel similarly
- **Enables**: "Approve via Slack button" UX

**Phase 4 (Weeks 13-16)**: Close medium gaps 5-9 (canvas, scheduling, data transform, error handling, logs)
- Visual canvas (React Flow integration)
- Scheduled triggers
- Data transformer step
- Error handling + retry logic
- Execution logs

**Phase 5 (Beyond)**: Medium/low gaps 10-17 (marketplace, advanced features, polish)

---

## Risk Assessment

### High-Risk Items

| Item | Risk | Mitigation |
|---|---|---|
| Bidirectional sync (gap 3) | Data consistency, security (API keys) | Start with non-destructive reads; HTTPS + signed requests |
| Webhook signature verification | Security (replay attacks) | Use HMAC-SHA256 standard; document in security guide |
| Kafka future compatibility | Refactor event system | Keep EventRouter abstraction; plan Kafka consumer in parallel |

### Medium-Risk Items

| Item | Risk | Mitigation |
|---|---|---|
| Slack/Teams OAuth setup | Org config complexity | Provide setup wizard + documentation |
| Canvas UX (React Flow) | Scope creep on step editor | Start MVP (no drag), upgrade later |
| Formula language | Security (code injection) | Use restricted parser (SpEL, not arbitrary JS); sandbox evaluation |

### Dependencies

1. **Gaps 1 & 2 can be done in parallel** (webhooks, separate concerns)
2. **Gap 3 (sync) depends on API structure** (RESTful design)
3. **Gap 4 (Slack/Teams) can be done anytime** (independent channel implementation)
4. **Gap 5 (canvas) is independent** (UI-only, doesn't affect engine)
5. **Gaps 6-9 have some overlap** (scheduling + data transform + error handling share state management)

---

## Implementation Readiness Checklist

- [ ] **Gap 1 (Outbound Webhooks)** - Create WebhookChannel, org config, retry logic
- [ ] **Gap 2 (Inbound Webhooks)** - Create `/workflows/actions/invoke` endpoint
- [ ] **Gap 3 (Bidirectional Sync)** - Create HTTP action handler, Salesforce connector starter
- [ ] **Gap 4 (Slack/Teams)** - Implement real SlackChannel + TeamsChannel
- [ ] **Gap 5 (Canvas)** - Prototype React Flow integration
- [ ] **Gap 6 (Scheduling)** - Add SCHEDULED trigger type + UI
- [ ] **Gap 7 (Data Transform)** - Add TRANSFORM step type + expression evaluator
- [ ] **Gap 8 (Error Handling)** - Add per-step retry policy + error routing
- [ ] **Gap 9 (Logs)** - Add WorkflowStepExecutionLog + UI inspector

---

## References

**Existing DocuHyphen Docs**:
- `AGENTS.md` - Architecture overview, coding rules
- `WORKFLOW_REDESIGN_PLAN.md` - Phases 1-5 (complete), future considerations

**Related Code**:
- `service/notification/EventRouter.kt` - event routing hub
- `service/notification/NotificationRuleEngine.kt` - delivery rules
- `service/workflow/DefaultWorkflowEngineService.kt` - action dispatcher
- `service/workflow/WorkflowActionHandler.kt` - action SPI
- `service/notification/channels/*.kt` - channel implementations

---

**Document End**

