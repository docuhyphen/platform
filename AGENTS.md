# DocuHyphen - Agent Context

## Coding Rules

- Never use —
- never use →
- never use emojis
- All backend endpoints must follow REST conventions (resource-based URLs, correct HTTP verbs,
  plural nouns, no verbs in paths except sub-resource actions). Examples:
  - `GET /workflows/definitions` not `GET /getWorkflowDefinitions`
  - `POST /workflows/definitions/{id}/clone` is acceptable as a sub-resource action
  - `PATCH /workflows/definitions/{id}/status` for partial updates
- Frontend components must be kept short and focused (single responsibility). If a component
  grows beyond ~150 lines of TSX, split it into smaller child components.
- Styles must always be in a separate file from the component. Use Fluent UI's
  `makeStyles` / `useStyles` pattern in a co-located `*Styles.tsx` file (e.g.
  `WorkflowDesigner.tsx` + `WorkflowDesignerStyles.tsx`). Never use inline `style={{}}` props
  except for purely dynamic values that cannot be expressed in `makeStyles`.
- Frontend styling must use Fluent UI `tokens` for design-system values. Use spacing tokens
  for gaps, padding, margins, inset spacing, and related offsets (for example
  `tokens.spacingHorizontalM` or `tokens.spacingVerticalS`) instead of hardcoded px/rem
  values. Use color and stroke tokens instead of hardcoded colors whenever a Fluent token
  exists. Literal `0`, percentages, viewport units, content dimensions, and
  component-specific measurements are acceptable when they are layout mechanics rather than
  design-system spacing or color.
- REST resource classes (`resource/`) must contain no business logic. They are thin HTTP
  adapters only: validate the request, delegate to an `@ApplicationScoped` service, and map
  the result to a `Response`. No repository calls, no entity manipulation, no domain
  decisions inside a resource class. Business logic belongs exclusively in the service layer
  (`service/`).
- When implementing work from a planning/implementation document (e.g. a phased implementation
  plan file), code comments must never reference that document, its phase/task numbers, or its
  file name (no "Phase 6 task 3", no "see AUDIT-ARCHITECTURE-IMPLEMENTATION.md", etc). Comments
  must instead describe what that section of the code actually does, on its own terms, so the
  code remains self-explanatory to a reader with no access to the planning document.

---

## Project Overview

**DocuHyphen** is a document-exchange platform. Users (individuals or organization members)
create **Exchanges** - named collections of documents sent from an initiator to one or more
recipients. The platform handles the full lifecycle: drafting, recipient acceptance, active
collaboration on documents, and ending/completion.

Organizations can configure workflows that gate each lifecycle stage - approvals, notifications,
conditions, and actions. A visual workflow designer in Settings lets org admins build and manage
these workflows. Platform-bundled workflow templates (categorized by tags) can be cloned
into an org. 

An "Exchange" is a first class word and should be used as a noun. For example, in a sentence like
"Open an exchange and select the Fields tab", "exchange" should be "Exchange".

---
## BACKEND RULES
- A service should not user another service's repository directly, they should communicate via methods
- toDto methods must be in their own dedicated class an not in the service or resource class
- Entity/model (data classes) must be in dedicated entity classes or a single one
- Use SOLID software design principles so that classes are not too big
- Each resource must follow the same structure of "return try {} catch {}", so each resource must log their own 
  unique error messages
- Services, resources, and repositories must be organized into logical domain subpackages (for
  example `exchange/`, `workflow/`, or `organization/`). Keep related layers aligned under the
  same domain when that ownership is clear, and leave only genuinely shared, cross-cutting base
  abstractions at a layer's root. Do not add new domain classes to flat root directories.
---

---
## INFRASTRUCTURE AND COST RULES
- No agent may add a new AWS service (or any new paid cloud resource type) without explicit
  confirmation from the user first. Keep infrastructure cost at a minimum.
- Reuse the services already declared in `infra/cloudformation.yml` (e.g. VPC/EC2, RDS Postgres,
  ECR, ECS/Fargate, CloudWatch Logs, IAM, ELB, S3, CloudFront, Secrets Manager). Adding a new
  bucket, table, role, or log group within an existing service is allowed; introducing a new
  service type (e.g. KMS, CloudTrail data events, DynamoDB, SQS, Kinesis, ElastiCache,
  OpenSearch, cross-Region/account replication) is not, unless the user explicitly approves it.
- If a task appears to require a new AWS service, stop and ask the user before making the change.
---

---
## FRONT-END RULES
- Treat ts as if it was strongly typed language, so no use of any.
- All HTML tags, react tags, etc attributes must be on a new line when they have more than oone attribute like example:
<Button appearance="primary"
        shape={"circular"}
- All Buttons must be circular shape
- Do not use inline styling
- Add html/react ids to all components, for example <Button id={"my-component-id"}...
- Do not add any emojis or characters such as ⚠
- When creating a component that has styling, create a folder to include the styling and the component itself
- Every new component added or updated must be responsive following best Web responsiveness designs
- Dialog actions must sit at the bottom right of the dialog by default. Dialogs should have one or
  two primary footer actions on the right. When two actions are present, the primary action must be
  on the left and the action to its right must use `appearance="secondary"`. A dialog may also have
  one left-aligned third action using `appearance="subtle"` when it is a low-emphasis dismissive or
  defer action. Do not add a footer Back action when the dialog already has a header Back button.
---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend language | Kotlin (Quarkus, Jakarta EE, JPA / Hibernate) |
| Database | PostgreSQL, schema managed by Flyway migrations |
| Frontend language | TypeScript |
| Frontend framework | React 18, Vite |
| UI component library | Fluent UI v9 (`@fluentui/react-components`) |
| Serialization (backend) | kotlinx.serialization |
| Auth | JWT tokens, custom `AuthTokenContext` interceptor |
| Realtime | Custom `RealtimeEventService` (WebSocket / SSE) |
| Scheduling | Quarkus `@Scheduled` |
| HTTP client (frontend) | Axios |
| Testing (frontend) | Vitest |

---

## Key Architectural Concepts

- **Exchange** - the primary domain entity. Statuses: `INITIATED` (Draft), `ACCEPTED_STARTED`
  (Active), `ENDED` (Completed), `REJECTED`.
- **Share** - unified authorization model. Every principal's access to an Exchange is a `Share`
  row (role, constraints, status). Roles: `OWNER`, `EDITOR`, `VIEWER`, `PARTICIPANT`,
  `REVIEWER`, `COMMENTER`, `SIGNER`.
- **WorkflowDefinition** - a reusable workflow template stored as a JSON DSL (`stepsJson`).
  Scoped to `APP` (platform-wide) or `ORG` (organization-specific).
- **WorkflowInstance** - one execution of a definition against a subject (e.g. an Exchange).
- **WorkflowStepInstance** - one step within an instance. Step types: `APPROVAL`,
  `NOTIFICATION`, `CONDITION`, `ACTION`.
- **DomainEvent** - published by services after state changes, routed by `EventRouter` to
  `NotificationRuleEngine` and business handlers (e.g. `ExchangeApprovalEventHandler`).
- **PrincipalGroup** - an org-owned group of users. Used in Share grants and workflow assignee
  resolution.

---

## Source Layout (abbreviated)

```
src/main/kotlin/com/docuhyphen/app/api/
  model/entity/          JPA entities
  model/dto/             Response DTOs
  resource/              JAX-RS REST resources (endpoints)
  service/
    exchange/            Exchange lifecycle services
    workflow/            Workflow engine (DefaultWorkflowEngineService, WorkflowSpec DSL)
    notification/        DomainEvent routing, rule engine, delivery dispatcher
    auth/                Authentication, authorization, authz helpers
    organization/        Org policy services
    communication/       Email, in-app notifications
  repository/<domain>/   JPA repositories grouped by owning domain
  interceptor/           Auth token context

web-app/src/
  app/
    models/models.tsx    All TypeScript DTO interfaces and enums
    settings/            Settings drawer (tabs: Profile, Org, Workflows, Templates, etc.)
    exchanges/           Exchange list and detail views
    components/          Shared UI components
  services/              Axios API service modules
  context/               React context providers (AuthContext, etc.)
```

---

The help docs live in:
`web-app/src/app/components/help-docs/sections/` (section files and per-article files)

### Required steps after any feature change

1. Search for existing coverage of the changed feature area:
   `grep -r "your-feature-keyword" web-app/src/app/components/help-docs/sections/`
2. Read every matched article in full. Verify that every statement is still accurate
   for the new behaviour. Check: UI navigation paths, field names, permission rules,
   step counts, listed values (token lists, status names, endpoint paths).
3. Update any article that is inaccurate or out of date.
4. Size limits (enforce strictly):
   - Article files: under 150 lines of JSX.
   - Section files: under 300 lines.
   - `helpDocsRegistry.tsx`: under 60 lines.
5. Run `npx tsc --noEmit` inside `web-app/` after any edits to confirm zero type errors.

### What counts as a feature change requiring a docs update

- New entity, endpoint, or UI tab added.
- Existing feature renamed (field names, UI labels, menu paths).
- Permission model changed (who can do what).
- Workflow or lifecycle behaviour changed.
- A settings tab added, removed, or relabelled.

### What does NOT require a docs update

- Pure refactoring with no user-visible change.
- Bug fix that restores the documented behaviour (no behaviour change).
- Infrastructure changes (CI, Flyway migrations, CloudFormation) with no UI impact.

---
