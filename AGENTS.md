# DocuHyphen - Agent Context

## Coding Rules

- Never use em dashes
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
- REST resource classes (`resource/`) must contain no business logic. They are thin HTTP
  adapters only: validate the request, delegate to an `@ApplicationScoped` service, and map
  the result to a `Response`. No repository calls, no entity manipulation, no domain
  decisions inside a resource class. Business logic belongs exclusively in the service layer
  (`service/`).

---

## Project Overview

**DocuHyphen** is a document-exchange platform. Users (individuals or organization members)
create **Exchanges** - named collections of documents sent from an initiator to one or more
recipients. The platform handles the full lifecycle: drafting, recipient acceptance, active
collaboration on documents, and ending/completion.

Organizations can configure workflows that gate each lifecycle stage - approvals, notifications,
conditions, and actions. A visual workflow designer in Settings lets org admins build and manage
these workflows. Platform-bundled workflow templates (categorized by industry tags) can be cloned
into an org.

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
  repository/            JPA repositories
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

## Active Feature Plan

See `WORKFLOW_REDESIGN_PLAN.md` in the project root for the full phased plan covering:
- Workflow-driven Exchange lifecycle (Phase 1-3)
- Workflow CRUD API (Phase 4)
- Visual Workflow Designer UI in Settings (Phase 5)
