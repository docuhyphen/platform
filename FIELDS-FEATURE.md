# Configurable Fields and Business Schema Engine Architecture

## Document Purpose

This document defines the architectural direction for configurable Fields in DocuHyphen. The goal is
not to build an insurance, lending, vehicle-finance, legal, or any other industry-specific domain
model into the platform. The goal is to give each organization a safe way to describe its own
business concepts and use them consistently in Exchanges, Blueprints, Workflows, search, reporting,
and governance.

This is an architecture document, not an implementation plan. It deliberately distinguishes:

- Foundations that should be designed correctly in the first release.
- Capabilities that may be implemented incrementally.
- Enterprise capabilities that are deferred but must remain possible without replacing the engine.

## Intended Outcome

An Exchange can act as a generic business work item. A configured schema gives that work item its
business meaning.

Examples include:

- An insurer configures a Claim Case schema with Policy Number, Claim Type, Loss Date, Broker,
  Estimated Loss, and Fraud Indicator.
- A lender configures a Loan Application schema with Application Number, Product, Requested Amount,
  Affordability Outcome, and Credit Decision.
- A vehicle-finance business configures a Vehicle Finance Case schema with Dealer, Vehicle VIN,
  Settlement Amount, Contract Number, and Case Type.
- A legal team configures a Matter schema with Matter Number, Practice Area, Client, Jurisdiction,
  and Confidentiality Level.

These are configurations, not Kotlin entities. DocuHyphen continues to own the document exchange,
workflow, sharing, audit, and collaboration lifecycle while customers define the business language
that surrounds that lifecycle.

## Architectural Correction

The engine should not be designed as a collection of independent custom fields attached directly to
resources. That model works for labels and filters, but becomes fragile when Fields are used as a
configurable business schema.

The durable abstraction is:

1. A governance Scope owns configuration.
2. A Schema describes a business concept for a resource type.
3. A versioned Schema composes reusable Field Definitions.
4. A Schema Assignment records which schema version governs a resource.
5. Field Values store typed data against that assignment.
6. Workflows and Blueprints reference stable schema and field identities.

This separation is the main requirement for future enterprise support.

## Design Principles

### Configuration Over Industry Code

Industry concepts must be created through configuration. The core platform must not contain
industry-specific branches or fixed Claim, Policy, Loan, and Vehicle Finance entities.

### Stable Identity Over Display Names

Names and labels may change. References used by workflows, integrations, reports, and stored values
must use immutable identifiers and stable namespaced keys.

### Published Configuration Is Immutable

Changing a schema that is already used by live resources must create a new version. Historical
resources remain understandable and workflows remain reproducible.

### Scope Is Not Authorization

Scope identifies who governs configuration. Authorization decides who may discover, view, use, or
change a resource. A future Business Unit scope must not be treated as a substitute for business-unit
data isolation.

### Generic Does Not Mean Untyped

Values must have explicit types, validation rules, canonical representations, and supported
operators. Storing arbitrary JSON without a type contract would move complexity into every consumer.

### Incremental Delivery Without Architectural Dead Ends

The first release may support only Platform and Organization scopes, Exchanges, and a small set of
field types. Its identities and contracts must already allow more scopes, resources, types, and
consumers to be registered later.

## Conceptual Model

### Scope Reference

A Scope Reference identifies the governance owner of configuration using:

- scopeKind, such as PLATFORM or ORGANIZATION.
- scopeId, which identifies the owner when the kind requires one.

The contract must allow future kinds such as BUSINESS_UNIT without changing every schema and field
table. The initial release does not need a Business Unit entity, scope hierarchy, inherited
configuration, delegated administration, or isolation rules.

Scope References should be represented consistently across Fields, Schemas, Blueprints, Workflows,
Variables, Sequences, and Communications over time. New field-engine records should not encode
organization ownership solely through a nullable organizationId plus an unrelated enum.

Future scope resolution may support:

- Platform configuration available to all organizations.
- Group-level configuration shared by multiple organizations.
- Organization configuration.
- Business-unit configuration below an organization.
- Team configuration below a business unit.

Only Platform and Organization need to resolve initially.

### Resource Reference

A Resource Reference identifies the subject receiving a schema or values using:

- A stable resourceType code, such as EXCHANGE, BLUEPRINT, or DOCUMENT_LIBRARY_ENTRY.
- A resourceId.

The field engine must not depend directly on every resource repository. Each supported resource type
registers an adapter responsible for:

- Verifying that the resource exists.
- Resolving its owner Scope Reference.
- Confirming whether a schema can be assigned.
- Checking the caller's permission through the owning resource service.
- Supplying lifecycle context needed for validation.

The first adapter may support only Exchanges. Adding another resource should require an adapter and
consumer UI, not a new field-definition model.

### Schema Definition

A Schema Definition is the stable identity of a configurable business concept. It contains:

- An immutable ID.
- A stable namespaced key.
- A mutable display name and description.
- Its owning Scope Reference.
- Its target resource type.
- Lifecycle status.

Example keys:

- first-rand:customer-case
- directaxis:loan-application
- wesbank:vehicle-finance-case
- old-mutual-iwyze:claim-case

The namespace is part of the identity. A plain key such as status or type is too likely to collide
across platform, organization, and future business-unit configuration.

A Schema Definition must not contain live mutable field rules. Those belong to a Schema Version.

### Schema Version

A Schema Version is an immutable published contract containing:

- A monotonically increasing version number.
- The set and order of field bindings.
- The exact Field Contracts used.
- Schema-level validation rules.
- Publication metadata and audit information.
- A compatibility classification for the change.

Lifecycle should support:

- DRAFT: editable and not valid for new production assignments.
- PUBLISHED: immutable and available for assignments.
- RETIRED: unavailable for new assignments but retained for history.

Publishing a changed draft creates a new immutable version. It does not rewrite an existing published
version.

Moving an existing resource to a newer version must be an explicit migration operation with
validation and audit. Automatic adoption of the latest version is not permitted.

The initial UI may expose only one draft and the latest published version. The persistence and
service contracts should still preserve version identity from the start.

### Field Definition

A Field Definition is the stable identity of a reusable business attribute. It contains:

- An immutable ID.
- A stable namespaced key.
- Its owning Scope Reference.
- A lifecycle status.

Examples include:

- common:customer-reference
- common:data-classification
- directaxis:affordability-outcome
- wesbank:vehicle-vin
- iwyze:loss-date

A field's display label, help text, type configuration, constraints, and option set belong to a
versioned Field Contract rather than the stable identity.

### Field Contract

A Field Contract is the immutable version of a Field Definition. It specifies:

- Value type and type-contract version.
- Display label, description, and help text.
- Validation constraints.
- Selection options or an Option Set reference.
- Data classification and visibility metadata.
- Searchable, filterable, sortable, and reportable capabilities.
- External integration aliases where required.

Once a Field Contract is published and used by a Schema Version, its value type and semantic meaning
must not change. A new contract version is required.

### Schema Field Binding

A Schema Field Binding places a Field Contract into a Schema Version. This is where contextual
behavior belongs:

- Display order and section.
- Required or optional behavior.
- Read-only or editable behavior.
- Default value expression.
- Conditional visibility.
- Conditional requiredness.
- Lifecycle stages in which editing is allowed.
- Whether the value is included in workflow context.

Requiredness must not live only on the global Field Definition. Customer Reference may be required
in a Loan Application schema and optional in a Complaint schema.

The initial release may support only order, requiredness, visibility, and static defaults. The model
should leave the remaining binding rules as versioned extensions.

### Resolved Schema View

Consumers should request a Resolved Schema View from the engine instead of loading definitions,
bindings, and scope precedence themselves. The view provides:

- The selected Schema Definition and exact version.
- An ordered, flattened list of effective bindings.
- The Field Contract and allowed operators for each binding.
- The source Scope Reference for inherited or composed configuration.
- Effective defaults, visibility, and validation constraints.
- Resolution diagnostics when configuration conflicts or dependencies are unavailable.

Initially, resolution is simple because a schema is owned by either the Platform or one
Organization and has no inheritance. Keeping this service boundary from the start allows future
business-unit overlays and schema composition to be added in one place.

### Option Set and Option

Selection options need their own stable identities. Display labels are mutable presentation, not
stored business values.

Each option should have:

- An immutable ID.
- A stable code.
- A display label.
- Active-from and retired-at lifecycle metadata.
- Display order.
- Optional external mappings.

Deactivating an option prevents new selection but does not invalidate or erase historical values.
An option code must not be reused later with a different meaning.

Shared Option Sets may be added when several fields genuinely use the same controlled vocabulary.
The initial implementation may keep options within a Field Contract if the identity rules are the
same and extraction into a shared set remains lossless.

### Schema Assignment

A Schema Assignment connects one Resource Reference to one published Schema Version. It records:

- The resource.
- The exact schema version.
- The Scope Reference under which the assignment was resolved.
- Assignment source, such as manual selection, Blueprint, API, or migration.
- Who or what assigned it and when.

A live resource must be pinned to an exact version. It must not silently begin using the newest
schema when an administrator publishes changes.

Most resources should have one primary business schema initially. Supporting multiple composable
schemas can be added later, provided assignments are already first-class records rather than a
single schema key copied onto the resource.

### Field Value

A Field Value belongs to:

- A Schema Assignment.
- A Schema Field Binding or Field Contract.
- The Resource Reference represented by the assignment.

It also records:

- A canonical typed value.
- Value provenance.
- Created and updated timestamps.
- The actor or system that last changed it.
- Optional effective time for future temporal use cases.

Values remain readable after a schema, field, or option is retired.

### Value Provenance

Provenance should distinguish values supplied by:

- A user.
- A Blueprint default.
- An API or external system.
- A workflow action.
- A calculated rule.
- A migration.

This is inexpensive to model early and difficult to reconstruct later. It supports audit,
troubleshooting, source-system reconciliation, and future rules governing whether an imported value
may be overwritten.

## Field Type System

### Type Contract

Field types should be registered through a controlled type registry. Each type contract defines:

- A stable type code and contract version.
- Accepted input shape.
- Validation and normalization.
- Canonical persistence representation.
- API serialization.
- Supported search and workflow operators.
- Redaction and display behavior.
- Frontend editor and read-only renderer.

Unknown type codes must fail closed. Customer-provided executable code must not run inside the field
engine.

### Initial Types

The first release can remain focused:

- Short text.
- Long text.
- Yes or No.
- Integer.
- Decimal.
- Date.
- Date and time.
- Single selection.
- Multiple selection.

Integer, decimal, and date types are worth reserving in the first contract even if some arrive after
the first UI release. Treating money, dates, and counts as text would weaken validation, sorting,
conditions, and reporting.

Currency may initially be a Decimal field with a fixed currency semantic annotation. A richer Money
type can be added later if multi-currency values are required.

### Deferred Types

The registry should allow future types without pretending they are simple scalar fields:

- Principal reference.
- Resource reference.
- External-system reference.
- Address.
- Money.
- Repeating group or collection.
- Structured object.
- Calculated field.
- Checklist with per-item workflow and audit state.

A checklist is collaborative state, not merely a multiselect. Repeating objects introduce child
identity, ordering, validation, and query concerns. Both should remain separate capabilities.

## Typed Value Storage Contract

The architecture should use generic Field Values from the beginning rather than an
exchange-specific value model that later needs to be duplicated for every resource.

The storage strategy must satisfy:

- Type-safe reads and writes.
- Equality and range queries without parsing display strings.
- Stable references to field, contract, schema, and assignment versions.
- Efficient organization and future scope filtering.
- Preservation of retired definitions and options.
- Indexing of frequently queried scalar and selection values.

The exact table design is an implementation decision, but one unrestricted JSON value column should
not be the only query model. A practical design may use typed scalar columns plus a child relation
for multiple selections, with a canonical JSON representation only at API boundaries.

Large-scale reporting may later use an event-fed projection or warehouse. The transactional model
must publish enough stable identity, scope, schema version, type, and provenance data to build that
projection without interpreting labels.

## Scope Resolution and Future Business Units

Business-unit isolation is intentionally not part of the first Fields release. The following
contracts are required now so it can be added cleanly:

- Every Schema, Field, Option Set, Assignment, and Value has an explicit owner or effective Scope
  Reference where appropriate.
- Scope kind is extensible and not hard-coded to an organization column in service interfaces.
- Configuration keys are unique within a namespace and owner scope, not globally by display name.
- Scope resolution is handled by one service rather than repeated in repositories.
- Consumers receive an effective configuration result and do not implement inheritance themselves.
- Audit events include Scope References.

Future resolution may combine configuration in a deterministic order:

1. Platform baseline.
2. Group or parent-organization baseline.
3. Organization configuration.
4. Business-unit configuration.
5. Team configuration.

Later scopes should extend or bind parent configuration, not mutate the parent's published versions.
Conflict rules must be explicit. A lower scope must not silently redefine a stable field key with a
different type.

The field engine must not claim that a future BUSINESS_UNIT Scope Reference provides isolation by
itself. Isolation also requires membership, authorization, query filtering, administration,
auditing, and possibly encryption or tenancy boundaries across the wider platform.

## Schema Composition and Overrides

The preferred future model is composition with explicit precedence, not unrestricted inheritance.

For example:

- A group publishes a Customer Case base schema.
- DirectAxis composes it into a Loan Application schema.
- WesBank composes it into a Vehicle Finance Case schema.
- Each business adds local fields without redefining shared Customer Reference semantics.

The initial release does not need schema composition. It should still avoid assumptions that make
composition impossible:

- Bindings have their own identity.
- Fields are reusable across schemas.
- Schema versions pin exact Field Contracts.
- Keys are namespaced.
- Defaults and requiredness belong to bindings.
- Consumers use a resolved schema view supplied by the engine.

## Validation Model

Validation occurs in layers:

1. The Type Contract validates shape and canonical form.
2. The Field Contract validates constraints such as length, range, precision, or allowed options.
3. The Schema Field Binding validates contextual rules such as requiredness.
4. The resource adapter validates lifecycle rules such as which fields may change after acceptance.
5. Authorization validates whether the caller may perform the operation.

Validation rules must be deterministic, versioned, and executable on the backend. Frontend
validation improves usability but is not authoritative.

Conditional rules should use a controlled expression language over stable field references. The
language must define null behavior, type coercion, supported operators, and evaluation errors.
Arbitrary JavaScript, Kotlin, SQL, or template expressions must not be accepted as validation rules.

## Workflow Integration

Workflows should consume a typed schema context rather than depend on ad hoc string maps.

The workflow contract should eventually expose:

- Schema ID and version.
- Stable field ID and namespaced key.
- Type code.
- Canonical value.
- Value provenance where relevant.

The current workflow subject snapshot may serialize values for compatibility, but field conditions
must reference immutable field or binding IDs. A display label or mutable option label must never be
the durable workflow reference.

Workflow instances must retain the field values or subject snapshot used when decisions were made.
Later edits to a resource must not rewrite the evidence behind an approval or routing decision.

Schema-aware workflow applicability may support:

- Target resource type.
- Target schema identity.
- Compatible schema version range.
- Typed field conditions.

Unknown fields, incompatible types, and retired options must produce an explicit non-match or
configuration error according to a documented rule. They must not be silently treated as strings.

## Blueprint Integration

A Blueprint may define:

- The Schema Definition to assign.
- A pinned or controlled compatible Schema Version.
- Static field defaults.
- Future expressions that derive defaults from trusted context.

Selecting a Blueprint creates a new Schema Assignment and copies defaults as values with Blueprint
provenance. It does not create an invisible dependency on mutable Blueprint configuration.

When a Blueprint is cloned across scopes, DocuHyphen must validate that its schema, field contracts,
option identities, documents, workflows, and access policies are available or explicitly remapped.

## Classification, Visibility, and Authorization

Fields may classify a resource, but classification must not directly grant access.

Examples:

- Department = Legal describes the resource.
- An Access Policy grants the Legal group permission to discover and use it.
- Data Classification = Restricted may trigger a policy recommendation or workflow.
- It does not become an authorization rule unless a separate policy engine explicitly uses it.

Field visibility is also distinct from resource access. A caller may be allowed to participate in an
Exchange without seeing internal underwriting, fraud, affordability, or complaint-classification
fields.

Visibility rules must apply consistently to:

- Detail responses.
- Lists and search.
- Workflow snapshots and notifications.
- Exports and reports.
- Audit views.
- Realtime events.
- Integration payloads.

Sensitive values must not be written into logs or general-purpose events. Audit events should
prefer stable field identity and change metadata, with protected before and after values only where
there is an explicit requirement.

## Search, Reporting, and Integration Readiness

Each Field Contract should declare supported capabilities rather than assuming every field can be
searched, sorted, grouped, or exported.

The engine should provide a common query model using:

- Resource type and owner scope.
- Schema identity and version.
- Field identity.
- Type-aware operators.
- Canonical option codes or IDs.

Search and reporting must apply resource authorization and field visibility before returning data.
Counts and aggregations can also leak restricted information and require the same treatment.

External mappings should be aliases attached to a versioned Field Contract or Option, for example:

- Source system name.
- External field path.
- External option code.
- Mapping version.

The internal stable key must not be changed merely to match a vendor or legacy system.

## Audit and Change Impact

The platform should audit:

- Schema and Field creation.
- Draft changes and publication.
- Retirement and reactivation where permitted.
- Schema assignment.
- Field value changes and provenance.
- Option lifecycle changes.
- Configuration cloning or remapping across scopes.

Before publishing a new version, future impact analysis should identify:

- Active Blueprints using the prior version.
- Workflows referencing changed fields or options.
- Resources pinned to affected versions.
- Reports and integrations using external aliases.
- Whether the change is additive, compatible, or breaking.

The first release may provide basic usage counts. Stable references and immutable versions are what
make richer impact analysis possible later.

## Resource-Specific Adoption

### Exchanges

Exchanges are the first business-data subject. A Schema Assignment gives an Exchange its configured
case type and its Field Values provide workflow, search, and reporting context.

Exchange Shares continue to govern participation. Field visibility is evaluated separately,
especially for external or magic-link recipients.

### Blueprints

Blueprints can select a schema and provide defaults for a future Exchange. Blueprints may also have
their own classification schema later. These are distinct uses and must not share the same value
records.

### Workflows

Workflows may use Fields in two ways:

- Fields classify and govern the Workflow Definition itself.
- Typed conditions inspect the subject resource's assigned schema and values.

These contexts must remain distinct.

### Document Library, Communications, Variables, and Sequences

These resources may adopt schemas for classification and governance later. Their services register
resource adapters and retain authority over resource permissions and lifecycle behavior.

## Lean First Release

The first implementation should focus on proving the engine without implementing the full enterprise
operating model.

### Build in the Foundation

- Generic Scope Reference with PLATFORM and ORGANIZATION resolvers.
- Generic Resource Reference and an Exchange resource adapter.
- Stable namespaced Schema and Field identities.
- Draft, Published, and Retired lifecycle.
- Immutable published Schema Versions and Field Contracts.
- Schema Field Bindings with order, requiredness, visibility, and static defaults.
- Schema Assignment pinned to an exact version.
- Generic typed Field Values with provenance.
- Stable option identities and lifecycle.
- A controlled type registry and typed validation.
- Audit events containing stable IDs, versions, resource references, and scopes.
- Service boundaries for schema resolution, validation, assignment, and value access.

### Keep the First User Experience Small

- Organization administrators create Fields and one or more Exchange schemas.
- Administrators compose fields into a schema and publish a version.
- Exchange creators select an applicable schema and enter values.
- The backend validates and stores values.
- Exchange details display permitted values.
- Blueprints may select a schema and provide defaults in a later increment.
- Workflow applicability and typed field conditions may follow after the value foundation is stable.

### Explicitly Defer

- A Business Unit entity and business-unit data isolation.
- Scope hierarchy and inherited configuration.
- Delegated schema administration.
- Cross-business-unit discovery and reporting.
- Dynamic metadata-driven Access Policies.
- Multiple schemas assigned to one live resource.
- Schema migration of existing resources.
- Repeating groups, structured objects, calculated fields, and collaborative checklists.
- Customer-authored type plug-ins or executable rules.
- Enterprise analytics projections and data warehouse delivery.

Deferring these capabilities is safe only if the foundation rules above are retained.

## Anti-Patterns to Avoid

- Creating an exchange-specific definition as the permanent Field Definition model.
- Putting requiredness, display order, and defaults only on the global Field Definition.
- Updating a published field type or option meaning in place.
- Storing workflow references by field display name.
- Storing selected option labels as business values.
- Treating a plain organization ID as the permanent scope abstraction.
- Letting every consumer resolve scope inheritance independently.
- Storing all values as untyped text or opaque JSON.
- Making Field Values grant permissions directly.
- Automatically upgrading live resources to the latest schema.
- Adding industry-specific columns or code paths to the core engine.
- Calling a group or field named Business Unit an isolation boundary.

## Decisions to Confirm Before Implementation Planning

- Whether an Exchange may initially have exactly one primary Schema Assignment.
- Whether a published schema can be selected directly or only through a Blueprint.
- Whether organization administrators may create schemas from scratch or must clone a platform
  template.
- Which initial value types will be exposed in the first UI.
- Whether values can be edited after an Exchange is accepted and which service owns that decision.
- Which values, if any, may be shared with external Exchange participants.
- Whether schema publication requires approval or only an organization administrator.
- Whether platform-provided Field Definitions may be reused inside organization schemas.
- Whether external aliases are needed in the first release or only reserved in the contract.

## Success Criteria

The architecture is successful when:

- A new industry or business unit can define its business language without backend domain classes.
- Two business units can use different schemas without field-key or option collisions.
- Shared fields can be reused without allowing local configuration to change their meaning.
- A live Exchange remains tied to the schema version under which it was created.
- Workflow, Blueprint, search, audit, and integration consumers use stable typed references.
- Retired fields and options remain historically understandable.
- Adding future scope hierarchy and business-unit isolation extends the engine rather than replacing
  its core identities, assignments, values, and versioning model.
