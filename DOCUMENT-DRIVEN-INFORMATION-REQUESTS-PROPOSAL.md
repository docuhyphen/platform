# Document-Driven Information Requests Proposal

## Status

Conceptual product and architecture proposal for discussion.

This document is not an implementation plan. It does not define phases, task ordering, delivery
estimates, or implementation commitments.

## Purpose

DocuHyphen is a document-driven business process platform. Many professional services processes
require a client to provide both structured information and supporting documents. Examples include
tax preparation, lending, insurance claims, legal matters, property transactions, onboarding, and
compliance reviews.

A South African tax practitioner may need a taxpayer to provide:

- Electricity and water amounts
- Electricity and water bills
- IRP5 documents
- Medical aid certificates
- Travel or vehicle information
- Bank statements
- Additional supporting information

DocuHyphen currently has useful foundations for this scenario:

- Reusable typed Field Definitions
- Versioned Schemas containing Field bindings
- Required and read-only Field bindings
- Exchange document placeholders
- Required-document metadata and upload-type restrictions
- Blueprints that combine Exchange configuration, documents, and a business Schema
- Share-based Exchange authorization
- Typed Field Value validation
- Workflow applicability based on Field Values

The current design does not yet provide a complete client-facing information request. Business
Fields are primarily treated as Exchange metadata, editing is coupled to the Exchange lifecycle,
external recipients cannot reliably load the editable Schema configuration, required documents are
not enforced as a complete submission, and there is no draft, submission, review, or correction
lifecycle for client-provided information.

## Central Design Recommendation

Add an Information Request or Intake layer above Fields, Schemas, Documents, and Blueprints.

- Fields remain reusable typed questions or business attributes.
- Schemas remain reusable structured-data definitions.
- Documents remain first-class document records.
- Blueprints remain reusable Exchange configuration templates.
- An Intake combines structured Fields and requested Documents into a runtime request assigned to a
  recipient, completed over time, submitted, reviewed, and enforced.

Documents should not be embedded directly into the Field type system. Structured responses and
files have different storage, validation, authorization, rendering, versioning, and review needs.
They should be composed at the Intake level while remaining separate underlying resources.

## Conceptual Relationships

```text
Field Definitions
    |
    v
Data Schema ------------------+
                              |
Document Requirements --------+--> Information Request Template
                                      |
                                      v
                                  Blueprint
                                      |
                                      v
                                   Exchange
                                      |
                                      v
                                Client Intake
                                  /       \
                                 v         v
                         Field Responses  Requested Documents
                                  \       /
                                   v     v
                              Submission and Review
```

## Terminology

### Field Definition

A reusable typed question or business attribute, such as `Electricity amount`, `Tax year`, or
`Employment status`.

### Data Schema

A versioned set of Field bindings defining the structured information used by a business process.
A Schema can define sections, ordering, required status, defaults, help text, respondent behavior,
and validation constraints.

The existing `SchemaDefinition` and `SchemaVersion` concepts can continue to provide this role.
The UI label `Business schema` may be reconsidered in favor of `Data schema` when that distinction
makes the relationship with Information Requests clearer.

### Document Requirement

A reusable or template-owned definition of a document that must or may be supplied, including its
name, description, accepted upload types, count rules, and review behavior.

### Information Request Template

A reusable composition of a Data Schema and Document Requirements. It defines what information is
requested, how it is organized, who should provide it, and how submission and review behave.

This may be represented within a Blueprint when it is specific to that Blueprint, or as a separate
reusable definition referenced by several Blueprints.

### Exchange Intake

A runtime instance of an Information Request assigned to a recipient or recipient group within an
Exchange. It owns progress, responses, requested document fulfillment, submission state, and review
state.

## Exchange Intake Model

Introduce an `ExchangeIntake` entity with properties such as:

- `id`
- `exchangeId`
- `schemaVersionId`
- `assignedShareId`
- `status`
- `dueAt`
- `submittedAt`
- `reviewedAt`
- `reviewedByAppUserId`
- `returnReason`
- `createdAt`
- `updatedAt`

Suggested statuses are:

- `DRAFT`
- `IN_PROGRESS`
- `SUBMITTED`
- `RETURNED`
- `ACCEPTED`
- `CANCELLED`

The Intake lifecycle must be independent of the Exchange lifecycle. An Active Exchange may contain
an editable Intake, and a client may save progress over several sessions before submission.

An Exchange should support multiple Intakes. This is needed when different information must be
collected from a taxpayer, spouse, employer, bookkeeper, company director, insurer, or other party.

Each Intake should remain pinned to the exact Schema Version and Information Request Template
version from which it was created.

## Recipient Assignment

An Intake should be assigned through the existing Share model rather than directly to an email
address. This preserves authorization when a recipient registers, accepts an invitation, changes
identity type, or accesses the Exchange through a supported participant flow.

The first version can assign the entire Intake to one Share. The model should allow later support
for section-level or requirement-level assignment when a process needs different parties to
provide different information.

An assigned respondent should be able to:

- View requirements assigned to them
- Save draft responses
- Upload requested documents
- See missing required items
- Submit the complete Intake
- Correct and resubmit an Intake returned for changes

## Configuration Access and Runtime Access

Schema configuration endpoints are for organization administrators and must remain separate from
recipient runtime access.

The Intake API should return a recipient-safe runtime projection containing:

- Intake identity and status
- Sections and ordering
- Field labels and help text
- Field types, constraints, and selection options
- Required and read-only state
- Current responses
- Requested documents and their current fulfillment state
- Upload restrictions
- Completion progress
- Validation messages
- Server-calculated capabilities such as `canEdit`, `canUpload`, `canSubmit`, and `canResubmit`

Recipients should never need `FIELD_CONFIG_VIEW` or access to Settings Schema endpoints to complete
an Intake.

## Field Edit Authorization

Field editing should no longer depend exclusively on the Exchange being in the `INITIATED` state.
Runtime authorization should consider:

- Whether the caller may access the parent Exchange
- Whether the Intake is assigned to the caller
- Whether the Intake is in an editable status
- Whether the Field binding permits respondent editing
- Whether the Share constraints permit the action
- Whether the owning subscription permits Intake mutation

Data classification and respondent edit permissions are separate concepts. A confidential value
may still need to be entered by the assigned client. Classification determines where the resulting
value may be disclosed, while respondent behavior determines who may enter or change it.

Add an explicit Field binding response mode, such as:

- `OWNER_ONLY`
- `ASSIGNEE`
- `OWNER_AND_ASSIGNEE`
- `READ_ONLY`

Magic-link and participant principals should be supported by the Intake resource where permitted.
Runtime Intake endpoints must not require an `appUser` when the authenticated principal is an
authorized participant or public-link principal.

## Schema Capabilities

Schemas should continue to define reusable structured data and should support:

- Ordered sections
- Ordered Field bindings
- Required Field bindings
- Help text and descriptions
- Validation constraints
- Selection options
- Default values
- Read-only Fields
- Respondent response modes
- Data classifications
- Stable Field identity across Schema Versions
- Compatibility information between versions

Conditional visibility and conditional requirement rules may be added without changing the core
Field Definition. For example, `Travel log` may become required only when `Claim travel expenses`
is `Yes`.

Conditions should use stable Field Definition identifiers rather than labels or display order.

## Blueprint Changes

Blueprints already combine Exchange settings, document placeholders, and an optional business
Schema. Extend that capability with Information Request configuration:

- Data Schema
- Required and optional Document Requirements
- Sections and ordering
- Assigned recipient role
- Due-date rule
- Draft-saving behavior
- Practitioner review requirement
- Owner-managed Fields
- Client-editable Fields
- Default and prefilled values
- Workflow events associated with submission and review

When an Exchange is created from a Blueprint, DocuHyphen should instantiate an `ExchangeIntake` for
the selected or resolved recipient.

The Exchange creator should enter only owner-managed values and defaults. Client-managed Fields
should remain available for the assigned recipient to complete after receiving the Exchange.

## Document Requirements

Requested Documents should become enforceable Intake requirements rather than only document
placeholders with display metadata.

A runtime requested Document should capture or derive:

- `intakeId`
- `requirementKey`
- `assignedShareId`
- `required`
- `description`
- `allowedTypes`
- `minimumCount`
- `maximumCount`
- `status`

Suggested fulfillment statuses are:

- `MISSING`
- `UPLOADED`
- `REJECTED`
- `ACCEPTED`

A requirement should support one or several uploaded files. For example, a requirement for twelve
months of bank statements cannot always be represented by one document record.

The review model should allow a practitioner to return a specific requested Document for correction
without reopening unrelated accepted requirements.

## Supporting Evidence Relationships

Allow an optional relationship between a structured Field and one or more supporting Document
Requirements. This should be composition metadata rather than a new Field Value type.

Examples include:

- Electricity amount supported by an electricity bill
- Travel expenses supported by a travel log
- Medical expenses supported by a medical aid certificate
- Rental income supported by a lease agreement

A supporting-evidence relationship may define:

- Source Field requirement
- Supporting Document Requirement
- Whether evidence becomes mandatory when a value is supplied
- An optional condition expression

Not every Field needs supporting evidence, and not every Document Requirement needs a related
Field.

## Draft Saving and Submission

Saving a draft and submitting an Intake must be distinct operations.

Saving a draft should:

- Validate the type and constraints of supplied values
- Allow required Fields and Documents to remain missing
- Store progress and provenance
- Avoid final submission workflow events

Submitting should:

- Validate every required Field across the complete Intake
- Verify every required Document Requirement
- Validate document counts and upload restrictions
- Return structured validation errors associated with requirements
- Change the Intake status atomically
- Record a submission snapshot
- Publish an Intake submission domain event

The server must validate the complete Intake during submission. It must not rely on the client to
send every required Field in one value-update request.

After submission, the Intake should be read-only unless it is returned for correction or explicitly
reopened by an authorized reviewer.

## Review and Correction

The practitioner or another authorized reviewer should be able to:

- Review structured responses and supporting Documents together
- Accept the Intake
- Return the Intake for correction
- Identify specific Fields or Documents requiring correction
- Provide a return reason or requirement-level comment
- Reopen an accepted Intake when policy permits
- View previous submission snapshots

Returning an Intake should expose only the appropriate requirements for editing while preserving
the history of the prior submission.

## Exchange Lifecycle Enforcement

Exchange lifecycle actions should be able to require accepted Intakes.

Organization policy, Blueprint configuration, or Workflow rules may determine whether an Exchange
can be ended or completed while it has:

- An unsubmitted required Intake
- A returned Intake awaiting correction
- A submitted Intake awaiting review
- Missing required Documents
- Rejected supporting Documents

This rule should be enforced in the service layer and not only by disabling frontend buttons.

## REST Resource Shape

The REST API should use Exchange-owned Intake resources:

```text
GET    /exchanges/{exchangeId}/intakes
GET    /exchanges/{exchangeId}/intakes/{intakeId}
POST   /exchanges/{exchangeId}/intakes
PATCH  /exchanges/{exchangeId}/intakes/{intakeId}

PUT    /exchanges/{exchangeId}/intakes/{intakeId}/values
GET    /exchanges/{exchangeId}/intakes/{intakeId}/validation
POST   /exchanges/{exchangeId}/intakes/{intakeId}/submissions
PATCH  /exchanges/{exchangeId}/intakes/{intakeId}/status

POST   /exchanges/{exchangeId}/intakes/{intakeId}/reviews
PATCH  /exchanges/{exchangeId}/intakes/{intakeId}/reviews/{reviewId}/status
```

Document uploads may continue through Exchange Document resources, with each uploaded document
linked to an Intake Document Requirement.

REST resources must remain thin HTTP adapters. Assignment, authorization, validation, submission,
review, and lifecycle behavior belong in dedicated application-scoped services. Services must use
other services' public methods rather than accessing repositories owned by another domain.

## Client Experience

The client-facing Exchange experience should include an `Information requested` area with:

- A clear Intake title and explanation
- Section navigation
- Field help text
- Required indicators
- Inline requested-document upload controls
- Upload type guidance
- Completion percentage
- Missing requirement summary
- Save draft action
- Submit action
- Due date where applicable
- Returned-for-correction guidance
- Responsive desktop, tablet, and mobile layouts

Fields and related supporting Documents should be displayed close together where that relationship
exists.

## Practitioner Experience

The organization-facing Exchange experience should include:

- Intake status by recipient
- Missing Field and Document summary
- Submitted values and files in one review view
- Requirement-level acceptance or correction feedback
- Accept Intake action
- Return for correction action
- Reopen action where permitted
- Response, upload, submission, and review history

The existing Business Fields area should be given a clear responsibility. It may remain the home of
owner-managed Exchange metadata or become the practitioner view of an Intake, but internal metadata
and client questionnaire behavior should not remain implicitly combined.

## Workflow Integration

Add domain events such as:

- `INTAKE_CREATED`
- `INTAKE_STARTED`
- `INTAKE_SUBMITTED`
- `INTAKE_RETURNED`
- `INTAKE_ACCEPTED`
- `INTAKE_OVERDUE`
- `INTAKE_REQUIREMENT_COMPLETED`

Workflow steps and notification rules can use these events to:

- Notify a client that information was requested
- Send reminders before or after a due date
- Notify a practitioner when an Intake is submitted
- Start an approval or review Workflow
- Return an Intake when a review is rejected
- Prevent process completion until the required Intake is accepted

Workflow applicability should continue to reference typed Field Values through stable Field
Definition identifiers.

## Audit and Provenance

Record at least:

- Who entered or changed each Field Value
- Previous and new values where policy permits their storage
- Who uploaded, replaced, accepted, or rejected each Document
- Intake assignment changes
- Submission snapshots
- Submission and resubmission times
- Return reasons
- Review decisions
- Schema Version and template version
- Blueprint version or source

Submitted Intakes must retain their original Schema Version even after a new version is published.
Audit payloads must respect data classification and must not expose unrestricted values in logs.

## Persistence Considerations

Potential persistence changes include:

- An `exchange_intake` table
- An Intake assignment reference to the existing Share model
- Intake status and lifecycle timestamps
- An Intake reference on runtime Field Values or Schema Assignments
- Intake Document Requirement tables or references
- Requirement-level status and review metadata
- Submission snapshot entities
- Optional Field-to-Document supporting-evidence relationships

The current one-Schema-assignment-per-Exchange restriction should be reconsidered. Multiple Intakes
may use the same or different Schema Versions within one Exchange.

Existing Schema Assignments and Field Values should remain migratable. A compatibility migration
could create a legacy Intake for each existing Exchange Schema Assignment, link its Field Values,
and mark it as accepted or legacy according to the existing Exchange state.

Previously requested Documents may be linked to migrated Intakes when their source Blueprint or
Schema relationship is unambiguous. Ambiguous documents should remain ordinary Exchange Documents.

## Security Boundaries

The Intake design must preserve the existing Share-based authorization model and add explicit
requirement-level controls where necessary.

Important boundaries include:

- A respondent sees only Intakes assigned to a Share through which they have access.
- A respondent edits only requirements that permit respondent input.
- Data classification controls downstream visibility, not whether the assigned respondent can
  answer a question.
- Reviewers require explicit review capability.
- Participant and public-link access must be checked through the central authorization service.
- Hiding a control in the frontend never replaces backend authorization.
- Subscription ownership is resolved from the owning Exchange.
- Cross-organization access is denied by default.
- Submission and review mutations are audited.

## Validation Rules

Validation should cover:

- Field type contracts
- Field constraints
- Required Fields
- Read-only and response-mode restrictions
- Required Documents
- Allowed upload types
- Minimum and maximum document counts
- Conditional requirements
- Intake assignment
- Intake status transitions
- Review permissions
- Exchange completion requirements
- Schema and template version consistency

Validation errors should use stable requirement identifiers and structured error codes so the
frontend can navigate directly to the affected Field or Document Requirement.

## Infrastructure Impact

The proposal can use the infrastructure already present in DocuHyphen:

- PostgreSQL for Intake, requirement, response, status, and review data
- Existing S3-backed document storage
- Existing Share and authorization services
- Existing realtime event delivery
- Existing Workflow engine and Domain Events
- Existing Quarkus scheduling for reminders where appropriate

No new AWS service or paid cloud resource type is required by this proposal.

## Compatibility Considerations

Existing use cases for internal Exchange metadata should continue to work. Compatibility decisions
should account for:

- Existing organization and platform Field Definitions
- Existing published Schema Versions
- Existing Exchange Schema Assignments
- Existing Field Values
- Existing Blueprint Field defaults
- Existing document placeholders
- Workflow conditions that reference Fields
- External visibility classifications

The Intake runtime projection should become the client-facing contract. Administrative Schema DTOs
should remain configuration contracts and should not be expanded into recipient APIs.

## Product Decisions Requiring Discussion

The following decisions remain open and are intentionally not resolved as implementation tasks:

- Whether the user-facing term should be `Information Request`, `Intake`, or another term
- Whether Information Request Templates are standalone reusable definitions or always owned by a
  Blueprint
- Whether the current Business Fields tab represents internal metadata, Intake responses, or two
  distinct views
- Whether one Intake may have several assignees or requires one Intake per assignee
- Whether review is always required or configurable
- Whether requirement-level acceptance is needed in the initial product behavior
- Whether conditional Fields and Documents are part of the first complete model
- Whether clients without registered accounts may submit through magic links
- How due dates and reminders are configured
- Which Exchange lifecycle transitions require accepted Intakes
- How migrated Schema Assignments should be represented to users

These decisions should be settled before this proposal is converted into an implementation plan.
