# Document-Driven Information Requests Proposal

## Status

Conceptual product and architecture proposal for discussion.

This document is not an implementation plan. It does not define phases, task ordering, delivery
estimates, or implementation commitments.

Every recommendation below has been checked against the current Fields, Schema, Share, recipient,
authorization, and subscription code. Passages headed `Finding` record what the code actually does
today, including places where an earlier draft of this proposal assumed a capability that does not
exist or understated the work required. `Pre-existing Defects Found` lists faults in the shipped
Fields feature that are independent of this proposal and worth fixing either way.

## Purpose

DocuHyphen is a document-driven business process management and execution platform. Many professional services processes
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

DocuHyphen currently has useful foundations for this scenario. The following are implemented and
working:

- Reusable typed Field Definitions with immutable Field Contracts
- Versioned Schemas containing Field bindings, with published versions frozen on publish
- Required and read-only Field bindings
- Exchange document placeholders
- Required-document and upload-type metadata on those placeholders
- Blueprints that combine Exchange configuration, documents, and a business Schema
- Share-based Exchange authorization
- Typed Field Value validation and canonicalization
- Workflow applicability that reads Field Values by stable Field Definition identifier

The following exist in the persistence model and the DTOs but are not functional. They must be
treated as new work rather than as capabilities to carry forward:

- Binding sections. `schema_field_binding.section` is nullable free text with no section entity,
  no section ordering, no description, and no authoring UI. The only binding controls exposed in
  Settings are required, read only, and display order.
- Binding visibility. `schema_field_binding.visibility` decides whether an external caller sees a
  Field at all, but has no authoring UI and defaults to the Field Contract's data classification,
  which itself defaults to `INTERNAL`. In practice no Field is recipient-visible today unless
  someone classified the Field Definition as `PUBLIC`.
- Binding default values. `schema_field_binding.default_value_json` is stored, cloned into each new
  draft version, and returned in DTOs, but is never applied to a Field Value and never used as a
  form prefill.

The current design does not yet provide a complete client-facing information request. Business
Fields are primarily treated as Exchange metadata, editing is coupled to the Exchange lifecycle,
external recipients cannot reliably load the editable Schema configuration, required documents are
not enforced as a complete submission, and there is no draft, submission, review, or correction
lifecycle for client-provided information.

## Verified Current Behaviour

The constraints this proposal sets out to remove are real and are enforced in code, not merely
implied by the UI:

- One Schema per Exchange. `schema_assignment` carries
  `UNIQUE (resource_type, resource_id)` (`V36__fields_engine.sql`), and `SchemaAssignmentService`
  additionally refuses a second assignment with "A schema is already assigned".
- Field Value editing ends when the draft ends. `ExchangeFieldResourceAdapter.valuesEditable`
  returns true only while the Exchange status is `INITIATED`. Every write path in
  `SchemaAssignmentService` consults it, so once an Exchange is Active no principal of any role can
  change a Field Value.
- Recipients cannot load the Schema configuration. The resolved-schema endpoint requires
  `FIELD_CONFIG_VIEW`, which maps to the `FIELD_SCHEMA_READ` capability. That capability is granted
  only by organization roles, and the check also demands that the caller's active organization is
  the Schema's owning organization. A recipient therefore receives 403. The Exchange Fields tab
  swallows that failure and falls back to rendering values with no labels, help text, ordering,
  constraints, or options.
- Required documents are decoration. `document.required` is written at Exchange initiation and
  echoed back in DTOs. No service reads it to make a decision anywhere in the codebase.
- There is no partial save. `SchemaAssignmentService.setValues` rejects the whole request if any
  supplied binding is required and canonicalizes to empty, and the Fields form posts every binding
  on every save. A single blank required Field therefore blocks saving anything at all.

These findings support the proposal's diagnosis. The sections below record where the proposal's
remedies need to change to fit the code as it stands.

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
- `assignedExchangeRecipientId` (see `Recipient Assignment` for why this is not a Share id)
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

### Finding: multiple Intakes break the contract Workflows depend on

`ExchangeFieldQueryService.getCanonicalValues(exchangeId)` finds the single Schema Assignment for an
Exchange and returns one map keyed by stable Field Definition id.
`WorkflowApplicabilityEvaluator` consumes exactly that shape, so today "the value of
`claim-travel-expenses` on this Exchange" is unambiguous by construction.

With several Intakes on one Exchange, possibly pinned to different Schema Versions, two Intakes can
bind the same Field Definition and hold different answers. The statement elsewhere in this proposal
that Workflow applicability "should continue to reference typed Field Values through stable Field
Definition identifiers" is then no longer sufficient on its own, because it does not say which
Intake supplies the value.

A resolution rule must be chosen before this is implementable. Candidates:

- Only the Intake assigned to the `PRIMARY` recipient feeds Workflow conditions.
- The most recently accepted Intake wins per Field Definition.
- Workflow conditions become Intake-scoped, and the evaluator is given an Intake context.

The first is the smallest change and keeps the existing single-snapshot shape. The third is the most
correct and the most invasive. This is a decision, not a detail, and it also determines whether
`ExchangeFieldQueryService` keeps its current signature.

Variable interpolation is unaffected. The `{{TOKEN}}` and `{{SEQ:KEY}}` interpolator does not read
Field Values, so this ambiguity is confined to Workflow applicability.

## Recipient Assignment

An Intake should be assigned through the existing recipient and Share model rather than directly to
an email address. This preserves authorization when a recipient registers, accepts an invitation,
changes identity type, or accesses the Exchange through a supported participant flow.

The first version can assign the entire Intake to one recipient. The model should allow later
support for section-level or requirement-level assignment when a process needs different parties to
provide different information.

### Finding: a raw `assignedShareId` is the wrong anchor

An earlier draft of this proposal assigned the Intake through an `assignedShareId`. That predates the
current recipient model and does not survive contact with it, which is why the Exchange Intake Model
above now names `assignedExchangeRecipientId` instead.

- The canonical recipient identity is now `exchange_recipient`, not `share`. It binds a recipient to
  exactly one Share, with a database trigger enforcing that the Share belongs to the same Exchange,
  that its source is `DIRECT` with a null `source_share_id`, and that its role is not `OWNER`. A
  partial unique index enforces one `PRIMARY` recipient per Exchange.
- Group recipients fan out. A group recipient's direct Share has principal kind `PRINCIPAL_GROUP`,
  and each member receives a derived Share with source `INHERITED_FROM_GROUP` and
  `source_share_id` pointing back at it. An Intake assigned to the group Share lets any member
  answer; an Intake assigned to a derived Share pins one person and breaks when group membership
  changes. `assignedShareId` alone does not say which is meant.
- Shares are revoked and re-granted, never mutated. `ExchangeAccessManagementService`
  `replacePrimaryRecipient` revokes the previous Share and grants a brand new one while preserving
  only the role and constraints, and `exchange_recipient.direct_share_id` cascades on delete. Any
  Intake holding the old Share id would be orphaned by an ordinary owner-recovery action, and the
  same applies to any access-removal path.

The Intake should therefore reference `exchangeRecipientId`, or a principal pair resolved through
the Share at request time, rather than a Share row id. Whichever is chosen, the model needs an
explicit reassignment rule for recipient replacement and access revocation, and that reassignment
must be audited alongside submission and review decisions.

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

### Finding: there is no participant principal, and no-auth recipients use a separate API surface

This is the largest hidden cost in the proposal. The statement that "runtime Intake endpoints must
not require an `appUser`" reads like the removal of a guard clause. It is not.

- `AuthorizationContextFactory.currentPrincipal()` only ever constructs a `USER` or an
  `APPLICATION` principal. `PrincipalRef.participant()` and `PrincipalRef.publicLink()` are declared
  but are never called anywhere in the request path.
- As a consequence the `PARTICIPANT, PUBLIC_LINK` branch of
  `ExchangeFieldResourceAdapter.isExternalCaller` is unreachable today. The external-caller
  behaviour that does fire is the `USER` branch, for a registered user who is not a member of the
  owning organization.
- Recipients without an account do not use the authenticated Exchange API at all. They use a
  parallel surface, `no-auth/exchanges`, whose access is validated by
  `NoAuthExchangeAccessTokenService` and `ShareLinkValidationService` rather than by
  `AuthorizationService`. Its response DTO carries no Schema or Field data of any kind.

So a single `exchanges/{exchangeId}/intakes` resource family cannot serve magic-link clients as
written. There are two viable routes, and the choice materially changes scope:

1. Mint participant and public-link principals in the authentication filter so
   `AuthorizationService` can adjudicate every caller uniformly, then expose one Intake resource
   family. This is the correct end state and unblocks the dead `isExternalCaller` branch, but it
   touches the authentication pipeline that every other endpoint depends on.
2. Expose a parallel `no-auth/exchanges/{exchangeId}/intakes` family that reuses the Intake
   application services but keeps the existing token-based access validation. Cheaper and lower
   risk, at the cost of a second surface to keep in step.

Whichever route is chosen, the runtime projection and the Intake services must be written so that
both surfaces call the same application service methods, with only the principal resolution
differing. The open question "whether clients without registered accounts may submit through magic
links" is therefore not only a product question; it is the single biggest fork in engineering scope
in this proposal.

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

Whatever carries the response mode must be enforced on the write path as well as the read path. The
current value-write path applies no classification or audience check at all, which is recorded under
`Pre-existing Defects Found`.

Magic-link and participant principals should be supported by the Intake resource where permitted.
Runtime Intake endpoints must not require an `appUser` when the authenticated principal is an
authorized participant or public-link principal. See the finding under `Configuration Access and
Runtime Access` for why this requires new principal plumbing or a second API surface rather than a
relaxed guard clause.

### Finding: response mode on a Schema binding is frozen for the life of an Intake

`schema_field_binding` rows belong to a `schema_version`, and publishing freezes that version.
Changing any binding attribute requires opening a new draft version and republishing. Combined with
the rule that each Intake stays pinned to the exact Schema Version it was created from, putting
`responseMode` on the binding means that who may answer a question can never change for an Intake
already in flight.

The practical failure looks like this. A practitioner marks `Medical expenses` as `OWNER_ONLY`,
sends the Exchange, and the client then says they hold the figure. Correcting that requires
publishing a new Schema Version and creating a replacement Intake, discarding whatever the client
had already entered against the old one.

Two ways out, and this should be settled with the question of whether Information Request Templates
are standalone or Blueprint-owned:

- Put response mode on the Information Request Template rather than on the Schema binding, so the
  Schema stays a pure data contract and audience is a request-level concern.
- Keep it on the binding but allow a per-Intake override row, accepting that the Intake is then no
  longer fully described by its pinned Schema Version.

The first is cleaner and reinforces the separation this proposal already argues for between what the
data is and who supplies it.

### Finding: data classification and response mode are currently conflated

The proposal correctly argues that classification and respondent-edit permission are separate
concepts. They are not separate today. When a binding is created, its `visibility` defaults to the
Field Contract's `data_classification`, and there is no authoring UI for either the binding
visibility or the section. Every binding on every already-published Schema Version therefore carries
a visibility that was inherited from a classification rather than chosen.

Because published versions are immutable, introducing a response mode needs an explicit backfill
decision for existing versions rather than a column default. The safe reading is that every existing
binding is `OWNER_ONLY`, since no recipient can edit a Field Value today under any circumstances,
and that anything client-facing is authored fresh.

## Schema Capabilities

Schemas should define reusable structured data and should support the list below. The word
"continue" does not apply uniformly, so each entry is marked with whether it works today.

| Capability | State today |
|---|---|
| Ordered Field bindings | Works |
| Required Field bindings | Works |
| Read-only Fields | Works |
| Help text and descriptions | Works, on the Field Contract |
| Validation constraints | Works, on the Field Contract |
| Selection options | Works, on the Field Contract |
| Stable Field identity across Schema Versions | Works, via `field_definition_id` |
| Compatibility information between versions | Works, recorded at publish |
| Data classifications | Works on the Field Contract; the binding-level override has no UI |
| Ordered sections | Column only. No section entity, no ordering, no UI |
| Default values | Column only. Stored and cloned, never applied or prefilled |
| Respondent response modes | Does not exist |

The three entries at the bottom are new work. Sections in particular are load-bearing for the
client experience proposed here, since section navigation and a missing-requirement summary both
assume sections are a first-class ordered concept rather than a free-text label on each binding.

Conditional visibility and conditional requirement rules may be added without changing the core
Field Definition. For example, `Travel log` may become required only when `Claim travel expenses`
is `Yes`.

Conditions should use stable Field Definition identifiers rather than labels or display order.

## Scope and Plan Availability

### Finding: Fields and Schemas have no personal scope

`FieldScopeKind` is `PLATFORM` and `ORGANIZATION` only, and that restriction is enforced by CHECK
constraints on `field_definition`, `schema_definition`, and `schema_assignment`. Every other
reusable configuration concept on the platform has since gained a personal scope: Blueprints,
Workflows, the Document Library, and Variables and Sequences all support personal ownership, and all
of them surface it in Settings, whether as a My / Organization / Platform tab split or as separate
personal and organization Settings tabs. Fields and Schemas were left behind.

The consequences are concrete and they undercut the driving example in this proposal:

- An individual with no organization cannot author a Field Definition or a Schema at all. Schema
  creation resolves to `PLATFORM` scope when the caller has no organization edit capability, and
  platform authoring is refused to anyone who is not an App Administrator.
- `SchemaAssignmentService` will only attach an organization-scoped Schema to an Exchange whose
  owning organization matches, so a personally owned Exchange can only ever carry a platform
  Schema, of which an individual can author none.
- `BusinessFieldsSubscriptionGuard.requireConfigurationMutation` asserts that a non-platform scope
  has an owning organization, so it would fail outright on a personal scope.

A South African tax practitioner working alone is precisely the persona this proposal opens with,
and today that practitioner cannot request structured information at all.

### Decision: add a personal scope

Add `PERSONAL` to `FieldScopeKind` and carry it through the Fields engine. This is a prerequisite
for Information Requests, not an optional extension, because an Intake with no authorable Schema is
not a product.

The work this implies:

- A migration adding `PERSONAL` to the `scope_kind` CHECK constraints on `field_definition`,
  `schema_definition`, and `schema_assignment`, plus a `scope_owner_user_id` column with a check
  pairing it to the personal scope in the same way `scope_org_id` is paired to the organization
  scope today.
- Scope resolution in `FieldDefinitionService` and `SchemaDefinitionService`, which currently fall
  back to `PLATFORM` when there is no organization edit capability and must instead fall back to
  `PERSONAL`.
- A personal branch in `requireScopeAccess`, keyed on the caller owning the definition rather than
  on an organization capability, since the capability model has no personal-scope capability.
- A personal branch in `BusinessFieldsSubscriptionGuard`, resolving the subscription from the owning
  user rather than requiring an organization.
- Visibility rules in `SchemaAssignmentService.assertSchemaVisibleToResource` and in
  `replaceBindings`, so a personal Schema may bind platform Fields and the caller's own personal
  Fields, and a personal Schema may only be assigned to an Exchange that same user owns.
- The Settings Fields area gaining an explicit scope split, in place of today's behaviour where
  scope is inferred from whether the caller can manage an organization. The Workflows and Document
  Library tabs are the closest precedent, both using a My / Organization / Platform inner tab set.
- The visibility rule for that tab, which currently requires an organization before the tab appears
  at all and would silently defeat the personal scope. See the finding on client gates below.

### Decision: authoring is a paid capability, responding is not

Two capabilities are conflated today under a single plan feature, and they must be separated before
Intakes ship:

- **Authoring.** Creating, publishing, and retiring Field Definitions and Schemas in Settings, and
  attaching a Schema or Information Request Template to an Exchange you own. This remains a paid
  capability.
- **Responding.** Seeing the Fields requested of you, entering values, uploading requested Documents,
  and submitting or correcting an Intake assigned to you. This is never gated on the respondent's
  plan.

`BUSINESS_FIELDS_AND_SCHEMAS` is added to the `PERSONAL` plan in `PlanCatalog` and is redefined to
mean the authoring capability only. Because `BUSINESS` is defined as `PERSONAL.features` plus its own
additions, `BUSINESS` inherits it automatically and the explicit entry in the `BUSINESS` set becomes
redundant and should be removed to keep one source of truth.

The `FREE` plan does not receive the feature. A Free account cannot manage its own Fields and Schemas
in Settings. A Free account can still be sent an Information Request and complete it in full,
including entering values and uploading requested Documents.

No second `PlanFeature` is introduced for responding. A feature granted to every tier is not a
feature, and unregistered magic-link respondents hold no subscription at all, so respondent
capability cannot be expressed as a plan entitlement even in principle. Respondent access is
governed by Intake assignment, Intake status, and the Field binding response mode, which is where it
belongs.

One adjacent case should be confirmed rather than assumed. Attaching a Schema to an Exchange you own
is treated above as authoring, so a Free account cannot start an Information Request even from a
platform-scoped template. That follows from "cannot manage their own Fields", but it is a slightly
wider reading than Settings alone, and it is the line the guard in `SchemaAssignmentService` already
draws.

### Finding: the backend already splits the two correctly

This split needs no new backend subscription logic, because
`BusinessFieldsSubscriptionGuard.requireResourceMutation` resolves the subscription from
`FieldResourceAdapter.subscriptionContext`, and the Exchange implementation of that returns
`SubscriptionContext.forOwner(exchange.ownerUserId, exchange.ownerOrganizationId)`. The respondent's
own plan is never consulted on a value write. A Free respondent answering on an Exchange owned by a
paying practitioner already passes the subscription check today.

So the required changes are:

- Keep `requireResourceMutation` on `assignSchema` and `unassignSchema`. These are owner actions and
  stay paid.
- Keep `requireConfigurationMutation` on every Field Definition and Schema mutation. This is the
  authoring gate.
- Leave the value-write subscription check as it is. It is owner-scoped and therefore already
  correct for a free respondent.
- Fix the client-side gates, which are the only place the respondent's own plan is consulted.

### Decision: pricing page changes

In `website/src/pages/PricingPageData.ts` the `Business Fields and schemas` row currently reads
`Not included`, `Not included`, `Included` across Free, Personal, and Business. It must become:

```text
["Business Fields and schemas", "Respond to requests only", "Personal", "Personal, organization and platform"]
```

The Personal and Business cells mirror how the neighbouring `Blueprints`, `Document Library`, and
`Variables and sequences` rows already express scope per tier, so no new vocabulary is introduced
there. The Free cell is the one genuinely new idea on the page: a capability that is present but
one-directional.

Two follow-ups on the page copy:

- The Free wording must track whatever user-facing term is settled for this feature. `Respond to
  requests only` assumes `Information Request`. If the term becomes `Intake` or something else, this
  cell changes with it.
- Once the term is settled, splitting this into two rows is clearer than one row with three
  different kinds of answer: an authoring row scoped per tier, and a `Complete requested
  information` row reading `Included` across all three. That is a copy decision for whoever owns the
  pricing page, not a modelling one.

Any pricing or marketing copy that positions Business Fields as an organization-only capability
needs the same review.

### Finding: two client gates block this, and they must move in opposite directions

The respond-freely / author-if-paid split is enforced correctly on the server and incorrectly on the
client, in two separate places that need changes pulling in opposite directions.

**The runtime gate is too strict and must stop consulting the viewer's plan.** The Exchange Fields
tab is gated on `usePlanFeature(PlanFeature.BUSINESS_FIELDS_AND_SCHEMAS).isDiscoverable`, which
resolves the viewer's own subscription. With enforcement active, a taxpayer on the Free plan would
never see an Information Requested area on an Exchange sent by a paying practitioner, no matter which
scope the Schema was authored in. This is precisely the case that must work. Granting the feature to
the Personal plan narrows it but does not close it, because Free remains a legitimate recipient tier
and unregistered magic-link recipients have no plan at all.

That gate must be replaced by a server-calculated capability on the Intake runtime projection,
alongside `canEdit`, `canUpload`, `canSubmit`, and `canResubmit`. Being asked to supply information
is not a feature the recipient purchases.

**The Settings gate is too strict in a different way and would defeat the personal scope.** The
Settings Fields tab is gated on `canManageOrganization && fields.isDiscoverable`, where
`canManageOrganization` requires both an active organization and the `ORG_POLICY_MANAGE` capability.
So even after `PERSONAL` is added to `FieldScopeKind` and the plan feature is granted to the Personal
plan, an individual with no organization still would not see the tab. The organization requirement
has to become conditional on which scope is being authored, exactly as the Blueprints and Document
Library tabs already do, rather than a precondition for seeing the tab at all.

The plan feature check on that tab stays. That is the gate that keeps authoring paid and keeps Free
out of it.

Both gates only bite when the subscription enforcement mode is `ENFORCE`. In the permissive modes
`isDiscoverable` is true for every plan, so neither problem is visible in an environment where
enforcement is off. That is worth knowing before anyone tries to reproduce either one.

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

Two of the entries above are not extensions of working behaviour. `Default and prefilled values` is
partly new: Blueprint field defaults do work and are applied at Exchange creation, keyed by stable
Field Definition id, but Schema binding defaults do not (see the table under `Schema Capabilities`).
`Sections and ordering` is entirely new for the same reason.

The current creation wizard also shows the creator every binding in the Schema and requires values
for the required ones, because `setValues` runs during creation and rejects empty required Fields.
Splitting owner-managed from client-managed Fields is therefore a change to Exchange creation, not
only to the Blueprint editor: creation must stop demanding values for Fields the client is meant to
supply.

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

### Finding: the Document model shape constrains how a requirement can be linked

`document` is a titled placeholder with no Exchange foreign key of its own. It is attached to an
Exchange through the `exchange_document` join table, which carries only `exchange_id` and
`documents_id`. `document_version` rows are successive revisions of that one placeholder, not
sibling files, so uploading a second bank statement against a placeholder replaces the first in the
version history rather than adding to a set.

This confirms that a multi-file requirement cannot be expressed with the existing model, and it also
constrains the fix. A requirement link cannot simply be a column on `document` pointing at a
requirement without deciding whether the requirement or the join table owns the Exchange
relationship. The cleanest shape is a requirement table owned by the Intake, with uploaded
`document` rows referencing the requirement, leaving `exchange_document` to continue expressing plain
Exchange membership so ordinary documents are unaffected.

Minimum and maximum counts then become properties of the requirement rather than of any document,
and `document.required` can be left alone as the legacy placeholder flag rather than being
overloaded with new meaning.

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

If the `no-auth` route is chosen for unregistered clients, the same paths must be mirrored under
`no-auth/exchanges/{exchangeId}/intakes`, restricted to the subset a client actually needs: read the
Intake, save values, upload against a requirement, and submit. Review and status transitions stay on
the authenticated surface only. The existing `no-auth` document upload endpoint is the precedent for
this shape.

REST resources must remain thin HTTP adapters. Assignment, authorization, validation, submission,
review, and lifecycle behavior belong in dedicated application-scoped services. Services must use
other services' public methods rather than accessing repositories owned by another domain. That rule
matters more than usual here, because two HTTP surfaces sharing one service layer is the only way the
authenticated and `no-auth` routes stay consistent.

## UI Placement

The `Client Experience` and `Practitioner Experience` sections below describe what belongs in the
Intake area. They do not say where it lives, and that is not obvious, because there is no single
place in the current navigation that all three audiences reach.

### Three surfaces, two shells

| Audience | Route | Shell | Where the Intake area goes |
|---|---|---|---|
| Practitioner and org members | `/exchanges` | Tabbed detail pane | The existing `Details` tab |
| Registered recipients | `/exchanges` | The same tabbed detail pane | The same `Details` tab |
| Magic-link recipients | `/nas` | Flat single-column workspace, no tabs | A new sibling block |

The authenticated Exchange detail pane declares four tabs in `ExchangeTabsHeader`: Documents,
Details, Workflow, and Audit. The `details` tab renders `ExchangeFieldsTab` today.

There is no separate recipient view for signed-in users. A registered recipient opens the same page,
with the same tab set and the same component tree, as the practitioner who sent the Exchange. So the
`Details` tab has to serve both the practitioner review view and the client response view,
distinguished only by server-supplied capabilities on the runtime projection.

Magic-link recipients land somewhere structurally different. `NoAuthExchangeWorkspace` has no tabs at
all. It is a flat column: an overview block, then one block headed `Exchange documents` holding the
document list. An Information Requested area is a new sibling block in that column, not a tab.

### The area is built once and hosted twice

Because the two shells differ structurally, the Intake UI should be a self-contained component driven
entirely by the runtime projection, with each host supplying only layout and heading chrome. This is
the component-level counterpart of the rule in `REST Resource Shape` that both HTTP surfaces call the
same application services. Building the client area twice, once per shell, is how the two surfaces
drift apart.

### Finding: the same concept has two different labels today

The tab a user actually sees in the Exchange detail pane is labelled `Details`, and
`canViewBusinessFields` is an internal variable name that never reaches the screen. The Blueprint
editor dialog, meanwhile, labels the equivalent tab `Business Fields`.

So the same concept is already presented under two different names depending on where the user is
standing, and `Details` in particular reads as generic Exchange metadata rather than as a business
data set. This is worth settling alongside the open question of whether that tab represents internal
metadata, Intake responses, or two distinct views, because the answer determines whether the label
should change, split, or stay.

### The Details tab needs a shape change, not only new content

The tab is currently one Schema, one form. Under this proposal an Exchange may carry several Intakes
for different parties, so the practitioner view becomes list-then-detail: Intake status by recipient,
then a drill-down into one Intake for review. That is a different structure from what is there now,
and it should be recognised as a rewrite of that tab rather than an addition to it.

The client view of the same tab stays single-form, because a respondent sees only the Intakes
assigned to them, and in the first version that is one.

### An existing progress indicator competes for the same job

`ExchangeTabsHeader` already renders a progress bar and an `X of Y documents uploaded` count in the
tab strip, derived from how many Exchange Documents have an upload date. The `Client Experience`
section asks for an Intake completion percentage.

Two progress indicators on one screen measuring different denominators will be read as contradictory,
particularly once some documents belong to an Intake requirement and some do not. Either the header
count becomes Intake-aware, or the Intake percentage stays inside the Intake area and the header
count is scoped explicitly to documents. This should be decided rather than allowed to happen.

### Template authoring has an unresolved home

Where an Information Request Template is authored follows from whether templates are standalone or
Blueprint-owned, which is still open:

- Blueprint-owned templates go in the Blueprint editor dialog, which already has a `Business Fields`
  tab next to `Details`, `Documents`, and `Permissions`.
- Standalone templates need a new home, most naturally alongside Settings `Fields`, which currently
  carries two inner tabs, `Fields` and `Schemas`, and would gain a third.

This is not an independent decision. It is the user interface consequence of the template-ownership
question, and it should be answered at the same time.

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

Whether this area appears at all must be decided by a server-calculated capability on the Intake
runtime projection, not by the recipient's own subscription. The finding under `Scope and Plan
Availability` explains why the current plan-feature gate would hide it from exactly the clients it
is built for.

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

That area is the `Details` tab of the Exchange detail pane. Adopting the list-then-detail structure
described under `UI Placement` is what gives it a clear responsibility in practice, and the label
should be revisited at the same time, since `Details` and `Business Fields` are currently two names
for the same concept in two different places.

## Workflow Integration

Add events such as:

- `intake.created`
- `intake.started`
- `intake.submitted`
- `intake.returned`
- `intake.accepted`
- `intake.overdue`
- `intake.requirement_completed`

### Finding: event naming and mechanism need to be explicit

An earlier draft listed these as `INTAKE_SUBMITTED` and similar. That form matches neither existing
convention. Workflow trigger events are dotted lowercase and registry-backed, for example
`exchange.activated`, `exchange.received`, and `exchange.acceptance_pending`. `DomainEvent.type`
follows the same namespaced taxonomy, documented on the envelope as `session.*`, `share.*`, and
`workflow.*`. The names above have been corrected to that form.

Naming aside, "add domain events" understates the work, because three different mechanisms are in
play and each event has to be assigned to one:

- A Workflow trigger event needs a row in `workflow_trigger_event_registry` created by a migration,
  including its `subject_fields_json` describing the subject data a condition may reference. This is
  what makes an event selectable in the Workflow designer.
- A `DomainEvent` needs publishing through `DomainEventPublisher` and routing in `EventRouter`, which
  is what reaches the notification rule engine and any business handler.
- `intake.overdue` is neither. Nothing fires it on a state change, so it needs a Quarkus scheduled
  job comparing `dueAt` against now and publishing on transition, with the same
  concurrent-execution guard the audit schedulers use.

Submission and review events will generally need both of the first two, since they should be able to
start an approval Workflow and notify a practitioner.

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

### Finding: no Field Value audit exists to extend

The phrase "record at least" implies extending an existing trail. There is no value-level trail to
extend. The audit event catalog contains `field_schema.field_definition.*` and
`field_schema.schema_definition.*` only, which cover configuration changes. Nothing is recorded when
a Field Value is created or changed.

What exists on `field_value` is `updated_by_app_user_id`, `updated_at`, and a `provenance` enum, all
overwritten in place by the upsert. There is no history table and no ledger event, so the previous
value is gone the moment a new one is saved.

The first two bullets above are therefore net-new work: a new audit category or event set for value
mutation, plus either a value history table or a submission snapshot that is genuinely diffable
rather than a display copy. The classification requirement in the closing paragraph makes this
harder, not easier, since a `RESTRICTED` value cannot be written into an audit payload in the clear
and the existing audit identity treatment machinery would have to be applied.

This is worth deciding early. Retrofitting value history after Intakes are live means the first
cohort of submissions has no before-and-after record, which is exactly the record a practitioner
would want in a dispute.

## Persistence Considerations

Potential persistence changes include:

- An `exchange_intake` table
- An Intake assignment reference to `exchange_recipient`, not to `share`
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

### Finding: the specific constraints that block multiple Intakes

Two constraints have to be relaxed, and they behave differently:

- `schema_assignment` carries `UNIQUE (resource_type, resource_id)`. Dropping it is the change that
  allows several Schema Versions per Exchange. Note that `SchemaAssignmentService` also enforces the
  same rule in application code with its "A schema is already assigned" guard, so the migration
  alone does not lift the restriction.
- `field_value` carries `UNIQUE (schema_assignment_id, field_contract_id)`. This one should stay. It
  is scoped to the assignment rather than to the Exchange, so it already tolerates the same Field
  appearing in two Intakes, provided each Intake gets its own assignment.

That second point argues for one Schema Assignment per Intake rather than an Intake reference added
onto a shared assignment. Keeping the assignment as the per-Intake anchor means `field_value` needs
no new uniqueness rule, existing rows keep their meaning, and the compatibility migration reduces to
creating one Intake row per existing assignment and pointing it at the assignment that is already
there.

The migration should also record how a legacy Intake is presented. An Exchange that is already Ended
with a filled-in Schema is not an accepted Intake in any meaningful sense, and labelling it
`ACCEPTED` would put a review decision into the record that nobody made. A distinct `LEGACY` status,
or an accepted status with a `MIGRATION` provenance marker mirroring the existing
`FieldValueProvenance.MIGRATION` convention, keeps the history honest.

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
- Subscription ownership is resolved from the owning Exchange, on both the server and the client.
- Cross-organization access is denied by default.
- Submission and review mutations are audited.
- The audience rule that hides a Field on read must also block writing it, and must govern the
  response body of a write.

The fifth boundary needs qualifying. There is no participant or public-link principal today, so
"checked through the central authorization service" describes the target state, not a check that can
simply be reused. If the `no-auth` route is chosen, those callers keep their token-based validation
and the central authorization service never sees them, which makes that boundary a statement about
the authenticated surface only. This should be resolved rather than left ambiguous, because it is the
difference between one authorization model and two.

The last boundary is new, and it exists because both halves of it are currently violated. See
`Pre-existing Defects Found`.

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

## Pre-existing Defects Found

These were found in the shipped Fields feature while verifying this proposal. None of them are
caused by anything proposed here, and all of them are worth fixing whether or not Intakes are built.
The first two are the reason this proposal insists that the write path carry the same audience rules
as the read path.

### Non-public Field Values are returned to external callers on write

`SchemaAssignmentService.resolveValues` filters bindings to `PUBLIC` visibility when the caller is
external, and the read endpoint passes the external-caller flag correctly. The write path does not.
`setValues` finishes with `return assignment.toDto()`, and `toDto` declares
`externalCaller: Boolean = false`, so the response to a value write contains every binding and every
value in the Schema Version regardless of classification, bypassing the filter applied on the
equivalent read.

The path is reachable. A registered user at another organization who is granted `EDITOR` sees
`isExternalCaller` return true, and shares are activated while the Exchange status is still
`INITIATED` so the recipient can load the Exchange for the acceptance dialog. That window is exactly
when `valuesEditable` is true.

### The value write path applies no classification or audience check

`setValues` checks only that the caller has `EXCHANGE_EDIT`, that the Exchange is editable, and that
the target binding is not read-only. It never consults binding visibility. `recipientRoleFor`
returns `EDITOR` whenever any document write permission is requested at initiation, which is the
common case, and `EDITOR` carries `EXCHANGE_WRITE`. An external recipient in that position can write
`CONFIDENTIAL` and `RESTRICTED` bindings given a Field Contract id.

Today those ids are not surfaced to such a caller through the read path, so this is obscurity rather
than a control, and the previous defect hands the ids over anyway. Both should be closed together by
applying one audience rule on both paths.

### A read-only binding makes the Exchange Fields tab unsavable

`FieldValuesForm` builds its request from `bindings.map(...)` with no filter, so it posts read-only
bindings along with the rest. The backend rejects any read-only binding outright with "Field ... is
read-only". Any Schema containing a single read-only binding therefore makes the whole Fields tab
impossible to save, with an error naming an internal identifier.

The Exchange creation path already gets this right. `buildCreationFieldValues` filters on
`!binding.isReadOnly` and carries a comment explaining why, which shows the intended shape. The fix
is to apply the same filter in the Fields tab form.

### Section grouping collides on slugified titles

`groupBindingsBySection` keys each group with `toFieldElementId(title)`, which lowercases the title
and replaces every run of non-alphanumeric characters with a hyphen. Two distinct section names that
reduce to the same slug are silently merged into one group, so `Income & Tax` and `Income Tax` render
as a single section under whichever title appeared first.

This is low severity while sections have no authoring UI and are therefore unreachable in the
product. It stops being low severity under this proposal, where sections become navigation targets
and anchors for a missing-requirement summary. The group key should be the raw title, or an index,
with the slug used only for the element id.

## Product Decisions Requiring Discussion

### Settled

- Fields and Schemas gain a `PERSONAL` scope. See `Scope and Plan Availability`.
- Authoring Fields and Schemas is a paid capability. `BUSINESS_FIELDS_AND_SCHEMAS` is added to the
  `PERSONAL` plan and redefined to mean authoring only. `FREE` does not receive it and cannot manage
  its own Fields and Schemas in Settings.
- Responding to an Information Request is not a paid capability and is not plan-gated on the
  respondent. Every tier, including `FREE`, and unregistered magic-link recipients who hold no
  subscription at all, can see the Fields requested of them, enter values, upload requested
  Documents, and submit.
- The website pricing page changes accordingly, including new copy for the Free tier expressing a
  respond-only capability.

### Open

The following decisions remain open and are intentionally not resolved as implementation tasks:

- Whether the user-facing term should be `Information Request`, `Intake`, or another term
- Whether Information Request Templates are standalone reusable definitions or always owned by a
  Blueprint
- Whether the current Business Fields tab, which the Exchange detail pane labels `Details`,
  represents internal metadata, Intake responses, or two distinct views, and what it should be
  called once that is decided
- Whether one Intake may have several assignees or requires one Intake per assignee
- Whether review is always required or configurable
- Whether requirement-level acceptance is needed in the initial product behavior
- Whether conditional Fields and Documents are part of the first complete model
- Whether clients without registered accounts may submit through magic links
- How due dates and reminders are configured
- Which Exchange lifecycle transitions require accepted Intakes
- How migrated Schema Assignments should be represented to users

Two further questions were surfaced by checking this proposal against the code. Both sit upstream of
most of the rest of the model, so they should be answered first:

- Which identity owns an Intake assignment: `exchange_recipient`, a resolved principal pair, or
  something new. This determines the Intake table shape, the reassignment rules on recipient
  replacement, and how group recipients are handled. See `Recipient Assignment`.
- Whether the client-facing Intake is served on the authenticated surface, the `no-auth` surface, or
  both. This decides whether participant and public-link principals get built at all, and it is the
  largest single fork in engineering scope in this proposal. See `Configuration Access and Runtime
  Access`.

A third question follows from allowing several Intakes per Exchange: which Intake supplies a Field
Value to Workflow applicability. See the finding under `Exchange Intake Model`.

These decisions should be settled before this proposal is converted into an implementation plan.
