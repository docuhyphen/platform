# Notification Communications — Implementation Plan

**Status:** PLANNING COMPLETE — Ready for Phase 1  
**Last updated:** 2026-06-23  
**Progress tracker:** Update the status line above and the phase checklist below after every session.

> **2026-06-23 — Communication rename complete.** All "Message Template" identifiers renamed to
> "Communication" across Kotlin (entity, DTOs, repository, service, resolver, resource), SQL
> (V11__communications.sql), FreeMarker (communication-wrapper.ftl), and TypeScript/React
> (communicationService.ts, CommunicationsTab, CommunicationEditorDialog, CommunicationPickerDialog,
> models, Settings, IconBundles, StepCard). `messageTemplateKey` → `communicationId` in WorkflowSpec
> and all referencing code. Zero tsc errors, zero mvn compile errors.

---

## Phase Checklist

- [x] **Phase 1** — DB schema + backend domain model (entity, repo, service, API)
- [x] **Phase 2** — Workflow engine integration (runtime template resolution + rendering pipeline)
- [x] **Phase 3** — Frontend — Template Manager UI (CRUD, scope tabs, preview)
- [x] **Phase 4** — Frontend — Workflow builder integration (template picker in NOTIFICATION step)
- [ ] **Phase 5** — Channel renderers (email HTML, in-app, Teams/Slack stubs)
- [ ] **Phase 6** — Migration + fallback wiring (backward-compat, existing hardcoded paths untouched)

---

## 1. Architecture Assessment

### What already exists and MUST be reused

| Component | Location | Relevance |
|-----------|----------|-----------|
| `WorkflowScope` enum (APP/ORG/PERSONAL) | `model/entity/WorkflowDefinition.kt` | Exact same enum for template scoping |
| `BlueprintDefinition` entity | `model/entity/BlueprintDefinition.kt` | **Structural template** — copy field layout verbatim |
| `WorkflowDefinitionRepository.findAllAccessibleForCaller()` | `repository/WorkflowDefinitionRepository.kt` | Scope-filter pattern to replicate for templates |
| `TemplateVariableInterpolator` | `service/variable/TemplateVariableInterpolator.kt` | Re-use directly; do NOT rewrite |
| `VariableResolutionContext` | `service/variable/TemplateVariableInterpolator.kt` | Re-use directly |
| `NotificationChannel` interface | `service/notification/channels/NotificationChannel.kt` | Extend, do not replace |
| `DeliveryTask` | `service/notification/DeliveryTask.kt` | Extend payload to carry `RenderedMessage?` |
| `WorkflowStepSpec.messageTemplateKey` | `service/workflow/WorkflowSpec.kt` | **Already present** — wire it up |
| `EmailTemplateRenderer` | `service/communication/templates/EmailTemplateRenderer.kt` | Use for email channel rendering |
| Flyway migrations | `resources/db/migration/` | Next version is **V11** |
| `BlueprintDefinitionResource` | `resource/BlueprintDefinitionResource.kt` | API endpoint pattern to replicate |

### Key architectural insight: WorkflowStepSpec already has the hook

```kotlin
// WorkflowSpec.kt (already exists)
data class WorkflowStepSpec(
    ...
    val messageTemplateKey: String? = null,   // ← THIS IS ALREADY THERE
    ...
)
```

This field is currently unused. The entire feature is wiring it up end-to-end.

### Scope/visibility pattern (from WorkflowDefinition + Blueprint — follow exactly)

```
PLATFORM (isTemplate=true, scope=APP)  → read-only, visible to all orgs
ORG      (scope=ORG, organizationId)   → visible to org members, editable by org-admin
PERSONAL (scope=PERSONAL, createdByAppUserId) → private to creator
```

---

## 2. Proposed Solution Design

### 2.1 Domain Model

#### New entity: `MessageTemplate`

```kotlin
// Mirrors BlueprintDefinition field-for-field where applicable
@Entity @Table(name = "message_template")
class MessageTemplate {
    var id: UUID
    var name: String                    // Display name
    var summary: String?                // Short description
    var description: String?            // Long description
    var scope: MessageTemplateScope     // PLATFORM | ORG | PERSONAL
    var organizationId: UUID?           // Non-null when scope=ORG
    var createdByAppUserId: UUID?       // Creator

    var subject: String                 // Interpolatable subject line e.g. "Invoice {{invoiceNumber}} Approved"
    var body: String                    // Interpolatable body in Markdown
    var generalTags: String             // JSON array

    var isActive: Boolean
    var isPublished: Boolean            // ORG-scoped: non-admins only see published
    var isDeleted: Boolean              // Soft-delete
    var isTemplate: Boolean             // Platform-bundled flag (PLATFORM scope)
    var sourceTemplateId: UUID?         // Cloned-from reference

    var createdAt: Timestamp
    var updatedAt: Timestamp
}
```

**Why Markdown for body?**
- Portable across all channels
- Email: Markdown → HTML via CommonMark
- In-app: Markdown → plain text (strip formatting) or rendered
- Teams: Markdown is natively supported in Adaptive Cards
- Slack: subset of Markdown supported via `mrkdwn`
- Future channels: trivial to add renderers

**Why no separate `MessageTemplateVersion` table?**
The workflow engine already handles this via `spec_snapshot_json` on `workflow_step_instance`. At the moment a workflow step executes, its entire spec (including `messageTemplateKey`) is frozen into `specSnapshotJson`. The runtime resolves the template at that moment. If the template is later edited, in-flight instances already have the snapshot — they are unaffected.

This means: templates are **mutable** records (edit in place), but execution is **safe** because the step snapshot is the authoritative source. This is simpler than immutable versioning and consistent with how the rest of the engine works.

**Future extension point for channel overrides:**
Add `channelOverridesJson: String?` (nullable, defaults null). Structure:
```json
{
  "EMAIL": {"subject": "...", "body": "..."},
  "TEAMS": {"subject": "...", "body": "..."}
}
```
Not implemented now. Column present in schema for future use.

### 2.2 Runtime Rendering Model

Introduce `RenderedMessage` as the intermediate output of template resolution:

```kotlin
data class RenderedMessage(
    val subject: String,
    val body: String,         // Resolved Markdown
    val metadata: Map<String, String> = emptyMap(),
)
```

### 2.3 Rendering Pipeline

```
NOTIFICATION WorkflowStep executes
    ↓
DefaultWorkflowEngineService.activateNotificationStep()
    ↓
MessageTemplateResolver.resolve(messageTemplateKey, subjectData, context)
    ├── Find MessageTemplate by key/id
    ├── TemplateVariableInterpolator.interpolate(subject, context)
    ├── TemplateVariableInterpolator.interpolate(body, context)
    └── Return RenderedMessage

    ↓
DomainEvent published with RenderedMessage embedded in payload
(serialized as JSON string in payload["renderedMessage"])

    ↓
DeliveryTask carries payload including RenderedMessage

    ↓
Per-channel rendering:
    EmailChannel       → MarkdownToHtml(body) → wrap in email-header/footer FTL → sendEmail()
    InAppChannel       → subject as title, body as detail → persist InAppNotification
    TeamsChannel       → Adaptive Card with title=subject, text=body (Markdown native)
    SlackChannel       → mrkdwn block with *subject* + body
```

### 2.4 New Service: `MessageTemplateService`

```kotlin
@ApplicationScoped
class MessageTemplateService {
    fun create(dto: CreateMessageTemplateDto, actor: AppUser, orgId: UUID?): MessageTemplate
    fun update(id: UUID, dto: UpdateMessageTemplateDto, actor: AppUser): MessageTemplate
    fun delete(id: UUID, actor: AppUser)
    fun setPublished(id: UUID, published: Boolean, actor: AppUser)
    fun setActive(id: UUID, active: Boolean, actor: AppUser)
    fun clone(id: UUID, actor: AppUser, orgId: UUID?): MessageTemplate
    fun findById(id: UUID): MessageTemplate?
    fun findAllAccessibleForCaller(callerUserId: UUID?, orgId: UUID?): List<MessageTemplate>
    fun preview(id: UUID, sampleVariables: Map<String, String>, context: VariableResolutionContext): RenderedMessage
}
```

### 2.5 New Service: `MessageTemplateResolver`

Called at workflow execution time:

```kotlin
@ApplicationScoped
class MessageTemplateResolver {
    fun resolve(
        templateId: String?,            // The messageTemplateKey from step spec
        subjectData: Map<String, String>,
        context: VariableResolutionContext,
    ): RenderedMessage?                 // null if templateId is null (fallback to existing behaviour)
}
```

#### Variable resolution — three-tier merge (IMPORTANT)

When `resolve()` builds the `VariableResolutionContext` to pass to `TemplateVariableInterpolator`, the variable tiers resolve in this order (each tier overrides the one above):

1. **System tokens** — resolved automatically by `TemplateVariableInterpolator`:
   `{{USER_FIRST_NAME}}`, `{{USER_LAST_NAME}}`, `{{USER_EMAIL}}`, `{{ORG_NAME}}`,
   `{{CURRENT_DATE}}`, `{{CURRENT_YEAR}}`, `{{CURRENT_MONTH}}`, etc.

2. **Org/Personal variables** — resolved automatically by `TemplateVariableInterpolator`
   from the `variable_definition` table (the existing Variables feature).
   Users create these under Settings → Variables.
   Example: `{{COMPANY_TAGLINE}}`, `{{SUPPORT_EMAIL}}`, `{{SLA_HOURS}}`

3. **Workflow subject data** — fields from `WorkflowInstance.subjectDataJson` passed as
   `context.overrides`. This is the critical bridge:
   ```kotlin
   // Inside MessageTemplateResolver.resolve():
   val enrichedContext = context.copy(
       overrides = context.overrides + subjectData
       // subjectData contains: exchangeName, initiatorName, recipientType, etc.
       // contributed by ExchangeInitiationService when it fires the TriggerRequest
   )
   interpolator.interpolate(template.subject, enrichedContext)
   interpolator.interpolate(template.body, enrichedContext)
   ```
   Example tokens available via subjectData: `{{exchangeName}}`, `{{initiatorName}}`,
   `{{recipientType}}`, `{{orgId}}`.

4. **SEQ tokens** — `{{SEQ:invoiceNumber}}` — transactional increment from
   `sequence_definition` table. Resolver detects presence of `{{SEQ:` and calls
   `interpolator.interpolateWithSequences()` instead of `interpolate()`.

This means a template body can freely mix all four tiers:
```
Dear {{USER_FIRST_NAME}},

Exchange **{{exchangeName}}** submitted by {{initiatorName}} requires your approval.

Reference: {{SEQ:approvalRef}} | SLA: {{SLA_HOURS}} hours | Team: {{ORG_NAME}}
```

#### Preview endpoint variable resolution

For `POST /message-templates/{id}/preview`, the caller supplies `sampleVariables` in the
request body. These are merged as `context.overrides`, overriding system tokens if the
caller provides them, so the preview accurately reflects what a live execution would render:

```json
{
  "sampleVariables": {
    "exchangeName": "Contract Review Q2",
    "initiatorName": "Jane Smith",
    "SLA_HOURS": "24"
  }
}
```

The editor UI should populate the sample variable form from two sources:
- Subject fields from the trigger event (from `GET /workflows/triggers` → `subjectFields`)
- Org/Personal variables (from `GET /variables`)

---

## 3. Database Changes

### New table: `message_template` (V11 migration)

```sql
-- V11__message_templates.sql
SET search_path TO public;

CREATE TABLE message_template (
    id                      UUID        NOT NULL,
    name                    VARCHAR(255) NOT NULL,
    summary                 VARCHAR(512),
    description             VARCHAR(1024),
    scope                   VARCHAR(32)  NOT NULL,
    organization_id         UUID         REFERENCES organization(id),
    created_by_app_user_id  UUID         REFERENCES app_user(id),
    subject                 TEXT         NOT NULL,
    body                    TEXT         NOT NULL,
    channel_overrides_json  TEXT,                  -- reserved for future per-channel overrides, nullable
    general_tags            TEXT         NOT NULL DEFAULT '[]',
    is_active               BOOLEAN      NOT NULL DEFAULT true,
    is_published            BOOLEAN      NOT NULL DEFAULT false,
    is_deleted              BOOLEAN      NOT NULL DEFAULT false,
    is_template             BOOLEAN      NOT NULL DEFAULT false,
    source_template_id      UUID         REFERENCES message_template(id),
    created_at              TIMESTAMP(6) NOT NULL,
    updated_at              TIMESTAMP(6) NOT NULL,

    CONSTRAINT message_template_pkey PRIMARY KEY (id),
    CONSTRAINT ck_message_template_scope CHECK (scope IN ('PLATFORM', 'ORG', 'PERSONAL')),
    CONSTRAINT ck_message_template_org_scope CHECK (
        (scope = 'PLATFORM' AND organization_id IS NULL)
        OR (scope = 'ORG'      AND organization_id IS NOT NULL)
        OR (scope = 'PERSONAL' AND organization_id IS NULL)
    )
);

CREATE INDEX ix_msg_tmpl_org     ON message_template (organization_id)        WHERE is_deleted = false;
CREATE INDEX ix_msg_tmpl_creator ON message_template (created_by_app_user_id) WHERE is_deleted = false;
CREATE INDEX ix_msg_tmpl_scope   ON message_template (scope, is_active)       WHERE is_deleted = false;
```

**Note:** `scope` uses `PLATFORM` instead of `APP` to distinguish message template scope from workflow trigger scope semantics. Alternatively use `APP` for consistency with WorkflowScope — decide before migration.

**No other tables modified.** The `workflow_step_spec` already has `messageTemplateKey: String?` in its Kotlin DSL and is stored in `steps_json` (text column). No DB schema change needed for workflows. The `messageTemplateKey` value will be the UUID of the `MessageTemplate` record.

---

## 4. Backend Changes

### 4.1 New files to create

```
src/main/kotlin/com/docuhyphen/app/api/
├── model/entity/
│   └── MessageTemplate.kt                     ← Entity
├── model/entity/
│   └── MessageTemplateScope.kt                ← Enum: PLATFORM, ORG, PERSONAL
├── repository/
│   └── MessageTemplateRepository.kt           ← CRUD + findAllAccessibleForCaller
├── service/notification/
│   ├── RenderedMessage.kt                     ← Data class
│   └── MessageTemplateResolver.kt             ← Resolution + interpolation
├── service/template/
│   └── MessageTemplateService.kt              ← Business logic
└── resource/
    └── MessageTemplateResource.kt             ← REST API at /message-templates
```

### 4.2 Files to modify

| File | Change |
|------|--------|
| `service/workflow/DefaultWorkflowEngineService.kt` | In `activateStep()` for NOTIFICATION type: call `MessageTemplateResolver.resolve()`, embed result in DomainEvent payload |
| `service/notification/channels/EmailChannel.kt` | Check `payload["renderedMessage"]`; if present, use it instead of hardcoded template |
| `service/notification/channels/InAppChannel.kt` | Same — use `renderedMessage.subject` as title if present |
| `service/notification/DeliveryTask.kt` | Add optional `renderedMessage: RenderedMessage?` field OR keep in payload as JSON string |

### 4.3 API endpoints (MessageTemplateResource)

```
GET    /message-templates                       → list accessible (scope-filtered)
POST   /message-templates                       → create
GET    /message-templates/{id}                  → get by id
PUT    /message-templates/{id}                  → update
DELETE /message-templates/{id}                  → soft-delete
PATCH  /message-templates/{id}/status           → activate/deactivate
PATCH  /message-templates/{id}/published        → publish/unpublish
POST   /message-templates/{id}/clone            → clone to caller scope
POST   /message-templates/{id}/preview          → render with sample variables → RenderedMessage
```

### 4.4 Notification step execution (DefaultWorkflowEngineService change)

Currently `NOTIFICATION` steps auto-complete without doing anything meaningful. The change:

```kotlin
// In activateStep(), NOTIFICATION branch:
WorkflowStepType.NOTIFICATION -> {
    val spec = parseStepSpec(stepInstance)
    val renderedMessage: RenderedMessage? = spec.messageTemplateKey?.let { key ->
        runCatching {
            messageTemplateResolver.resolve(key, subjectData, resolutionContext)
        }.getOrNull()
    }

    // Publish domain event; embed renderedMessage in payload if present
    val payload = buildMap<String, String> {
        put("instanceId", instance.id.toString())
        put("stepInstanceId", stepInstance.id.toString())
        renderedMessage?.let {
            put("renderedSubject", it.subject)
            put("renderedBody", it.body)
        }
    }

    eventPublisher.publish(
        DomainEvent(
            type = "workflow.notification",
            subject = ...,
            payload = payload,
        )
    )
    // Auto-complete the step
    completeStep(stepInstance, WorkflowStepStatus.COMPLETED)
}
```

### 4.5 EmailChannel change (use RenderedMessage when present)

```kotlin
override fun send(task: DeliveryTask): ChannelSendResult {
    ...
    val renderedSubject = task.event.payload["renderedSubject"]
    val renderedBody = task.event.payload["renderedBody"]

    val (subject, htmlBody) = if (renderedSubject != null && renderedBody != null) {
        // User-defined template path
        val html = markdownToHtml(renderedBody)
        val wrappedHtml = wrapInEmailLayout(html, appName)
        renderedSubject to wrappedHtml
    } else {
        // Existing hardcoded fallback (no change to existing behaviour)
        buildLegacyEmail(task)
    }
    ...
}
```

### 4.6 Markdown → HTML

Add CommonMark dependency for the email channel renderer:

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.commonmark</groupId>
    <artifactId>commonmark</artifactId>
    <version>0.21.0</version>
</dependency>
```

Create `MarkdownRenderer.kt`:
```kotlin
@ApplicationScoped
class MarkdownRenderer {
    private val parser = Parser.builder().build()
    private val renderer = HtmlRenderer.builder().build()
    fun toHtml(markdown: String): String = renderer.render(parser.parse(markdown))
}
```

---

## 5. Frontend Changes

### 5.1 New: Message Templates management UI

**Location:** `web-app/src/app/settings/message-templates-tab/`

**Structure:**
```
message-templates-tab/
├── MessageTemplatesTab.tsx           ← Main tab (My / Organization / Platform)
├── MessageTemplateEditor.tsx         ← Create/edit form
├── MessageTemplateListView.tsx       ← List with scope tabs
├── MessageTemplatePreview.tsx        ← Live preview panel (sample variable inputs)
└── TemplatePickerDialog.tsx          ← Reusable picker dialog (used by workflow builder)
```

**Tab placement:** Add "Message Templates" tab to the settings sidebar alongside Workflows, Blueprints, Variables.

### 5.2 MessageTemplateEditor fields

- Name (text)
- Summary (text)
- Scope selector (Personal / Organization / Platform — gated on role)
- Subject (text input with `{{variable}}` syntax highlighting or hints)
- Body (Markdown textarea with variable hints panel on the side)
- Tags (multi-select)
- Available variables panel (shows system tokens + org variables from existing `/variables` API)
- Preview button → calls `POST /message-templates/{id}/preview`

### 5.3 Workflow builder change (StepCard.tsx for NOTIFICATION type)

Currently NOTIFICATION steps have minimal configuration. Add:

```tsx
// In StepCard.tsx, when step.type === 'NOTIFICATION':
<TemplatePickerField
    value={step.messageTemplateId}
    onChange={(id) => updateStep({ ...step, messageTemplateId: id })}
    label="Message Template"
    description="Optional. Leave blank to use the default system notification."
/>
```

`TemplatePickerField` renders:
- Selected template name + scope badge if one is chosen
- "Select template" button → opens `TemplatePickerDialog`
- "Clear" button to remove assignment

### 5.4 New API service functions (workflowApi.ts / messageTemplateApi.ts)

```typescript
// messageTemplateApi.ts
export function listMessageTemplates(): Promise<MessageTemplateListItem[]>
export function getMessageTemplate(id: string): Promise<MessageTemplate>
export function createMessageTemplate(dto: CreateMessageTemplateDto): Promise<MessageTemplate>
export function updateMessageTemplate(id: string, dto: UpdateMessageTemplateDto): Promise<MessageTemplate>
export function deleteMessageTemplate(id: string): Promise<void>
export function publishMessageTemplate(id: string, published: boolean): Promise<void>
export function previewMessageTemplate(id: string, sampleVars: Record<string, string>): Promise<RenderedMessage>
export function cloneMessageTemplate(id: string): Promise<MessageTemplate>
```

### 5.5 DTO types (dtos.ts)

```typescript
export interface MessageTemplate {
    id: string;
    name: string;
    summary?: string;
    description?: string;
    scope: 'PLATFORM' | 'ORG' | 'PERSONAL';
    organizationId?: string;
    subject: string;
    body: string;
    isActive: boolean;
    isPublished: boolean;
    isTemplate: boolean;
    generalTags: string[];
    createdAt: string;
    updatedAt: string;
}

export interface RenderedMessage {
    subject: string;
    body: string;
}
```

---

## 6. Versioning Strategy

### Decision: mutable templates + snapshot-safe execution

**Do NOT introduce a separate `MessageTemplateVersion` table.**

**Rationale:**
- `WorkflowStepInstance.specSnapshotJson` already freezes the entire step spec at execution time
- The `messageTemplateKey` (template UUID) is part of that frozen spec
- But what's frozen is the *reference*, not the content — so we need to also freeze the rendered output

**Solution:** At the moment the NOTIFICATION step activates, the engine resolves + interpolates the template and stores the result (`renderedSubject`, `renderedBody`) in the `specSnapshotJson` or as part of the DomainEvent payload. Once rendered, the content is frozen in the delivery pipeline.

This gives:
- ✅ Simple data model (no version table)
- ✅ In-flight instances use the template content as it was at activation time
- ✅ Future workflow runs use the latest template content
- ✅ Consistent with how everything else in the engine works

**Answering the versioning questions:**

1. **Store `templateId` not `templateVersionId`** — the snapshot happens at execution time, not definition time
2. **Publishing** — ORG-scoped templates require `isPublished=true` for non-admins to see them (same as workflows)
3. **Drafts** — unpublished templates = drafts; visible only to creator and org-admins
4. **Editing** — allowed; future workflow runs get the new content; existing `specSnapshotJson` records are unaffected because the rendered output was already frozen
5. **Already-published workflows** — unaffected; if the workflow hasn't fired yet, it will use the new template content when it does fire; this is acceptable (template is a content resource, not a contract)
6. **In-flight instances** — unaffected; the rendering happened when the step activated

---

## 7. Runtime Sequence Diagrams

### Template Creation

```
User → POST /message-templates
    → MessageTemplateResource
    → MessageTemplateService.create()
    → Validate (name, subject, body not blank; scope constraints)
    → Save MessageTemplate (isPublished=false)
    ← Return MessageTemplateDto (201 Created)
```

### Workflow Configuration (assigning a template to a step)

```
Builder UI → PUT /workflows/definitions/{id}
    → Payload includes steps[N].messageTemplateKey = "{templateUUID}"
    → WorkflowDefinitionService.update()
    → Validate templateUUID exists and is accessible to caller
    → Save updated stepsJson
    ← Return updated WorkflowDefinitionDto
```

### Workflow Execution (NOTIFICATION step)

```
TriggerRequest fires
    → WorkflowInstance created
    → Each step spec frozen into specSnapshotJson
    → NOTIFICATION step activates:
        → MessageTemplateResolver.resolve(messageTemplateKey, subjectData, context)
            → MessageTemplateRepository.findById(key)
            → TemplateVariableInterpolator.interpolate(subject, context)
            → TemplateVariableInterpolator.interpolate(body, context)
            → Return RenderedMessage{subject, body}
        → Embed renderedSubject + renderedBody into DomainEvent payload
        → eventPublisher.publish(DomainEvent{type="workflow.notification", payload})
        → Mark step COMPLETED

DomainEvent → NotificationRuleEngine → DeliveryTasks (one per recipient per channel)
    → DeliveryDispatcher dispatches each task to channel bean:

        EmailChannel.send(task):
            renderedBody = payload["renderedBody"]
            html = MarkdownRenderer.toHtml(renderedBody)
            wrapped = wrapInEmailLayout(html)
            emailService.sendEmail(to, payload["renderedSubject"], wrapped, useHtml=true)

        InAppChannel.send(task):
            title = payload["renderedSubject"]
            body = stripMarkdown(payload["renderedBody"])
            persist InAppNotification{title, body}

        TeamsChannel.send(task) [future]:
            card = AdaptiveCard{title=subject, body=body (Markdown native)}
            teamsClient.postCard(webhookUrl, card)
```

### Preview

```
User → POST /message-templates/{id}/preview
    Body: { sampleVariables: {"invoiceNumber": "INV-1001", "approvedBy": "Jane"} }
    → MessageTemplateService.preview()
    → Build VariableResolutionContext from authenticated user + org
    → Merge sampleVariables into context.overrides
    → TemplateVariableInterpolator.interpolate(subject, context)
    → TemplateVariableInterpolator.interpolate(body, context)
    ← Return RenderedMessage{subject, body}
```

---

## 8. Migration Strategy

### Backward compatibility guarantee

- All existing hardcoded notification paths (`workflow.step_assigned`, `exchange.created`, etc.) remain **100% unchanged**
- `EmailChannel` only uses `RenderedMessage` when `payload["renderedSubject"]` is present; otherwise falls back to existing logic
- `InAppChannel` same pattern
- The `messageTemplateKey` field on `WorkflowStepSpec` is already nullable and defaults to null — existing workflows without it continue to work as before
- No existing workflow definitions need to be migrated

### Gradual adoption path

1. Deploy Phase 1–2 (schema + backend) → existing behavior unchanged
2. Org admins can create templates at their own pace
3. When they reassign a NOTIFICATION step to reference a template, future runs of that workflow use the template
4. Platform can ship seed PLATFORM-scoped templates (via V12 migration) as defaults

---

## 9. Risks and Edge Cases

| Risk | Mitigation |
|------|-----------|
| Template deleted while referenced by a workflow | `MessageTemplateResolver.resolve()` returns null on missing template → falls back to default notification behavior; log a warning |
| Template not accessible to the executing org | Repository scope-filter; resolver returns null → fallback |
| Interpolation produces unresolved tokens (`{{unknown}}`) | `InterpolationResult.unresolvedTokens` already tracks this; log but proceed with literal `{{unknown}}` visible in output |
| SEQ tokens in template body | Call `interpolateWithSequences()` (transactional) instead of `interpolate()` — resolver must detect `{{SEQ:` presence |
| Very long body crashes channel payload | Truncate at channel level (e.g., 4000 chars for Teams); log truncation |
| Markdown rendering produces unsafe HTML (XSS) | CommonMark's `HtmlRenderer` is safe by default; configure `sanitizeUrls = true` |
| Circular `sourceTemplateId` reference | Only set on clone; no runtime dereference — no risk |
| Org admin edits template that ORG workflows are using | Acceptable; next execution gets new content; document this behavior |
| Channel is disabled for recipient | Existing fallback chain in `DeliveryDispatcher` handles this already |

---

## 10. Package / Module Structure

```
src/main/kotlin/com/docuhyphen/app/api/
├── model/entity/
│   ├── MessageTemplate.kt             ← NEW
│   └── MessageTemplateScope.kt        ← NEW (enum)
├── repository/
│   └── MessageTemplateRepository.kt   ← NEW
├── service/
│   ├── notification/
│   │   ├── RenderedMessage.kt         ← NEW (data class)
│   │   ├── MessageTemplateResolver.kt ← NEW
│   │   └── channels/
│   │       ├── EmailChannel.kt        ← MODIFY
│   │       └── InAppChannel.kt        ← MODIFY
│   ├── template/
│   │   └── MessageTemplateService.kt  ← NEW
│   └── workflow/
│       └── DefaultWorkflowEngineService.kt  ← MODIFY (NOTIFICATION step branch)
├── resource/
│   └── MessageTemplateResource.kt     ← NEW
└── api/model/dto/
    └── MessageTemplateDtos.kt         ← NEW

src/main/resources/
├── db/migration/
│   └── V11__message_templates.sql     ← NEW
└── email-templates/
    └── message-template-wrapper.ftl   ← NEW (wraps rendered Markdown body in email layout)

web-app/src/app/
├── settings/
│   └── message-templates-tab/
│       ├── MessageTemplatesTab.tsx         ← NEW
│       ├── MessageTemplateEditor.tsx        ← NEW
│       ├── MessageTemplateListView.tsx      ← NEW
│       └── MessageTemplatePreview.tsx       ← NEW
├── components/
│   └── template-picker/
│       └── TemplatePickerDialog.tsx         ← NEW (reused by workflow builder)
└── services/
    ├── messageTemplateApi.ts                ← NEW
    └── types/dtos.ts                        ← MODIFY (add MessageTemplate, RenderedMessage)
```

---

## 11. Phased Implementation Plan

### Phase 1 — DB + Backend Domain Model
**Goal:** Persist and serve MessageTemplate records. No workflow wiring yet.

**Tasks:**
1. Write `V11__message_templates.sql`
2. Create `MessageTemplateScope.kt` enum (`PLATFORM`, `ORG`, `PERSONAL`)
3. Create `MessageTemplate.kt` entity (mirror `BlueprintDefinition` field layout)
4. Create `MessageTemplateRepository.kt` (mirror `WorkflowDefinitionRepository.findAllAccessibleForCaller`)
5. Create `MessageTemplateDtos.kt` (CreateDto, UpdateDto, ListItemDto, FullDto)
6. Create `MessageTemplateService.kt` (CRUD, publish, clone, findAllAccessibleForCaller)
7. Create `MessageTemplateResource.kt` (all 8 endpoints; mirror `BlueprintDefinitionResource`)
8. Add `RenderedMessage.kt` data class (subject, body, metadata)
9. Register `MessageTemplateResource` in Quarkus (check if auto-discovery or explicit registration needed)

**Dependencies:** None  
**Risk:** Low — purely additive

---

### Phase 2 — Workflow Engine Integration + Rendering Pipeline
**Goal:** NOTIFICATION steps resolve templates at runtime; channels use RenderedMessage.

**Tasks:**
1. Create `MessageTemplateResolver.kt`
   - Inject `MessageTemplateRepository` + `TemplateVariableInterpolator`
   - `resolve(templateId, subjectData, context): RenderedMessage?`
   - Handle SEQ token detection → call `interpolateWithSequences()`
2. Add CommonMark dependency to `pom.xml`
3. Create `MarkdownRenderer.kt` application-scoped bean
4. Modify `DefaultWorkflowEngineService.activateStep()` — NOTIFICATION branch:
   - Call resolver, embed `renderedSubject`/`renderedBody` in DomainEvent payload
5. Modify `EmailChannel.send()`:
   - If `payload["renderedSubject"]` present → use RenderedMessage path (Markdown→HTML→email layout)
   - Else → existing legacy path (no change)
6. Modify `InAppChannel.send()`:
   - If `payload["renderedSubject"]` present → use as title; strip markdown from body
   - Else → existing hardcoded strings
7. Create `message-template-wrapper.ftl` (email header/footer around rendered HTML body)
8. Add `POST /message-templates/{id}/preview` endpoint (calls `MessageTemplateService.preview()`)

**Dependencies:** Phase 1 complete  
**Risk:** Medium — touches DefaultWorkflowEngineService and channel implementations; regression-test existing notification flows

---

### Phase 3 — Frontend: Template Manager UI
**Goal:** Users can create, edit, preview, publish, and delete message templates.

**Tasks:**
1. Create `messageTemplateApi.ts` service
2. Add types to `dtos.ts`
3. Create `MessageTemplatesTab.tsx` (scope tabs: My / Organization / Platform)
4. Create `MessageTemplateListView.tsx` (list items with scope badge, publish status, actions)
5. Create `MessageTemplateEditor.tsx` (form with subject + body + variable hints + scope)
6. Create `MessageTemplatePreview.tsx` (sample variable inputs + live rendered output)
7. Add "Message Templates" entry to settings sidebar nav
8. Wire publish/unpublish/delete/clone actions

**Dependencies:** Phase 1 API endpoints must exist  
**Risk:** Low — new screens, no modification of existing UI

---

### Phase 4 — Frontend: Workflow Builder Integration
**Goal:** NOTIFICATION steps in the builder show a template picker.

**Tasks:**
1. Create `TemplatePickerDialog.tsx` (searchable list of accessible templates, scope badges)
2. Modify `StepCard.tsx` — for `type === 'NOTIFICATION'`: show template picker field
3. Modify `WorkflowDesigner.tsx` — update `WorkflowStepSpecDraft` type to include `messageTemplateId?: string`
4. Modify `WorkflowDtos.kt` on backend — ensure `messageTemplateKey` round-trips correctly in the DTO (it already exists in `WorkflowStepSpec`)
5. Show template name (not just ID) in builder by fetching template details on load
6. Optional: show inline preview of selected template with workflow subject variables

**Dependencies:** Phase 2 (backend pipeline) + Phase 3 (TemplatePickerDialog component)  
**Risk:** Low — isolated to StepCard + WorkflowDesigner; no backend changes

---

### Phase 5 — Channel Renderers (Teams, Slack)
**Goal:** Stub channels upgraded to real renderers using RenderedMessage.

**Tasks:**
1. `TeamsChannel.kt` — build Adaptive Card JSON from `renderedSubject` + `renderedBody`
2. `SlackChannel.kt` — build Block Kit JSON from `renderedSubject` + `renderedBody` (mrkdwn)
3. Add org-level channel configuration (webhook URLs, enable/disable per channel)

**Dependencies:** Phase 2  
**Risk:** Medium — external channel integrations; require test webhooks

---

### Phase 6 — Platform Seed Templates + Migration Polish
**Goal:** Ship platform-provided PLATFORM-scoped templates as a starting point.

**Tasks:**
1. Write `V12__seed_message_templates.sql` — insert 2–3 PLATFORM-scoped templates
   - "Exchange Approval Required" — for `workflow.step_assigned`-type notifications
   - "Exchange Approved" — outcome notification
   - "Exchange Rejected" — outcome notification
2. Add "Use this template" button on PLATFORM templates → clones to caller's ORG scope
3. Document the `{{variable}}` syntax in the editor UI (tooltip or sidebar help)

**Dependencies:** Phase 3 UI complete  
**Risk:** Low

---

## Progress Notes

*(Append a dated note here after each session)*

**2026-06-23 (session 4)** — Phases 3 + 4 complete. New frontend files: messageTemplateService.ts, MessageTemplatesTab.tsx, MessageTemplateEditorDialog.tsx (with variable token hints, preview tab), TemplatePickerDialog.tsx (searchable, scope-tabbed). Modified: Settings.tsx (added Message Templates nav entry + tab), StepCard.tsx (NOTIFICATION step now uses TemplatePickerDialog instead of static KNOWN_TEMPLATE_KEYS combobox; resolves template name from API on load), IconBundles.tsx (SettingsMessageTemplatesTabIcon), models.tsx (MessageTemplateSummaryDto, MessageTemplateDto, MessageTemplateScope, RenderedMessage, Create/UpdateMessageTemplateRequest). TypeScript check passes cleanly. Phase 5 (Teams/Slack channel renderers) is next.

**2026-06-23 (session 3)** — Phase 2 complete. New: MessageTemplateResolver.kt, MarkdownRenderer.kt, message-template-wrapper.ftl, CommonMark in pom.xml. Modified: DefaultWorkflowEngineService (executeNotificationStep resolves templates, renders Markdown HTML email), EmailChannel + InAppChannel (workflow.notification event handling), MessageTemplateService (preview method), MessageTemplateResource (POST /{id}/preview endpoint), MessageTemplateDtos (PreviewMessageTemplateRequest). Phase 3 next.

**2026-06-23 (session 2)** — Phase 1 complete. All 7 files created: V11__message_templates.sql, MessageTemplate.kt, MessageTemplateScope (in entity), MessageTemplateRepository.kt, MessageTemplateDtos.kt, RenderedMessage.kt, MessageTemplateService.kt, MessageTemplateResource.kt. 8 endpoints live. Phase 2 next.

**2026-06-23** — Investigation complete. Plan written. No code yet. Ready to begin Phase 1.  
All investigation findings confirmed:
- V11 is the correct next migration version
- `WorkflowStepSpec.messageTemplateKey` already exists (just needs wiring)
- `BlueprintDefinition` is the exact structural model to follow for the entity
- `TemplateVariableInterpolator` is fully reusable
- `EmailChannel` already has the pattern for template-based rendering (added in this session)
- The only DB change needed is one new table (`message_template`)

