# Analytics and Operational Intelligence Architecture

## Document Purpose

This document defines the architectural direction for analytics in DocuHyphen. It describes how the
platform can turn trusted operational facts into useful organizational insight without making
analytics an afterthought or coupling dashboards directly to mutable transactional models.

This is an architecture document, not an implementation plan. It deliberately avoids database
schemas, REST endpoint definitions, package layouts, framework choices, and delivery sequencing.
Those details belong in a separate implementation plan after the prerequisite features have been
implemented and their final contracts have been verified.

## Dependency Order

This architecture is the fourth design in the following dependency chain:

1. `PRE-FIELDS-AUTHORIZATION-HARDENING-PLAN.md`
2. `WEBHOOK-INTEGRATIONS-FEATURE.md`
3. `FIELDS-FEATURE.md`
4. This Analytics and Operational Intelligence architecture

The order is important.

- Authorization hardening establishes trusted principals, owner context, Scope References, Resource
  References, centralized authorization, and restricted-data projection.
- Webhook integrations establish durable external-delivery facts, application principals,
  correlation, retries, callback outcomes, and operational delivery history.
- Fields establish stable business semantics through versioned schemas, typed Field Contracts,
  Schema Assignments, canonical values, provenance, and visibility metadata.
- Analytics consumes these foundations. It must not invent competing identity, tenancy,
  authorization, event, or field models.

Analytics design may continue while these dependencies are implemented. Analytics implementation
must use the final verified contracts rather than assumptions copied from planning documents.

## Intended Outcome

DocuHyphen should provide actionable operational intelligence to authorized organization owners,
administrators, managers, auditors, compliance teams, and operational users.

The system should help them answer questions such as:

- Which workflows are delayed, and at which steps?
- Which document requests are outstanding or overdue?
- Which documents are actively used, stale, or consuming disproportionate storage?
- Are external participants engaging with shared material?
- Are communications and webhook integrations reaching their intended destinations?
- Which security and access events require attention?
- How are business cases distributed by configured schema and Field values?
- What changed during a reporting period, and what evidence supports the result?

The platform is not a public marketing website. Page views, bounce rates, advertising attribution,
and similar website metrics are outside this architecture unless a future product requirement
explicitly introduces them.

## Architectural Position

Analytics is a governed read model over domain facts. It is not the system of record for Exchanges,
documents, workflows, permissions, Fields, integrations, or security decisions.

The architecture has four logical layers:

1. Domain systems record authoritative current state and immutable operational facts.
2. A canonical analytical fact boundary captures facts that cannot be safely reconstructed later.
3. Governed analytical projections organize facts into stable measures and dimensions.
4. Reports and dashboards query those projections through authorization and visibility policy.

Consumers should not recreate business definitions independently. A workflow dashboard, exported
report, scheduled summary, and future warehouse feed must use the same metric definitions and
security rules.

## Design Principles

### Business Facts Over Interface Activity

Measure meaningful domain activity such as document access, workflow decisions, request completion,
permission changes, delivery outcomes, and storage growth. Do not treat navigation, component
rendering, or session heartbeats as evidence of productive activity.

### Reuse Authoritative Records

Existing workflow runtime records, audit records, delivery attempts, security incidents, document
versions, Shares, Schema Assignments, and Field Values remain authoritative where they contain the
required fact. Analytics must not duplicate them merely to make reporting convenient.

Add a durable analytical fact only when the occurrence cannot be reconstructed reliably, when its
historical context would otherwise be lost, or when retaining it is required for a stable metric.

### Stable Identity Over Labels

Metrics and dimensions must reference stable IDs, namespaced keys, type codes, schema versions,
Field Contract versions, option IDs, event types, and Resource References. Mutable names and labels
are presentation metadata and must not define historical meaning.

### Scope Is Not Authorization

A Scope Reference identifies governance or ownership context. It does not grant permission to view
analytics. Every analytical query must separately evaluate the caller, requested scope, resource
authorization, metric sensitivity, and data visibility.

### Resource Access Does Not Reveal Every Dimension

Permission to read an Exchange does not imply permission to see every Field Value, participant
identity, security event, individual performance measure, or integration payload. Aggregates can
leak restricted facts and require the same projection discipline as detail responses.

### Metric Definitions Are Versioned Contracts

A metric must have one approved meaning. Changes to inclusion rules, time boundaries, status
classification, or calculation semantics create a new metric definition version. Historical reports
must remain explainable under the definition used when they were produced.

### Event Time and Processing Time Are Distinct

The architecture must distinguish when a business occurrence happened from when analytics observed
or processed it. Reporting uses occurrence time by default and exposes freshness when late or
replayed facts may affect a result.

### Rebuildability and Idempotency

Analytical projections must be reproducible from their authoritative sources and durable facts.
Reprocessing the same fact must not increase a measure twice. Corrections and late arrivals must
produce deterministic results.

### Privacy and Minimization by Default

Analytics retains only the identities, dimensions, and detail needed for approved operational or
compliance purposes. Secrets, credentials, document contents, unrestricted payloads, and sensitive
Field Values must not enter general analytical facts.

### Operational Analytics Before Enterprise Intelligence

The initial value is timely insight within DocuHyphen. Cross-organization benchmarking, predictive
models, customer-authored formulas, and a general-purpose warehouse are future capabilities. The
foundations must permit them without requiring them now.

## Canonical Analytical Context

Every analytical fact should carry enough context to remain meaningful without depending on mutable
display data.

### Fact Identity

Each fact has a stable identity and a fact type from a controlled catalog. Retried delivery,
replayed processing, or projection rebuilding must preserve the identity needed for deduplication.

### Occurrence Context

A fact distinguishes:

- When the business occurrence happened.
- When it was recorded by the source domain.
- When it became available to analytics.

These times may differ for callbacks, retries, imported activity, scheduled detection, and recovery
after an outage.

### Principal Context

The actor is represented through the hardened principal model. Supported actors may include users,
registered applications, service accounts, external participants, workflow actors, and system
actors. Non-user principals must never be silently mapped to an application user.

Where delegated activity is supported, both the acting application and delegating user remain
attributable.

### Owner and Scope Context

Facts carry the canonical owner context and relevant Scope Reference available at occurrence time.
Platform, organization, and personal activity remain distinguishable. Active organization is caller
context and must not replace resource ownership.

### Resource Context

Subjects and related objects use canonical Resource References. Relationships such as an Exchange,
document, workflow instance, workflow step, application, webhook endpoint, or communication remain
explicit rather than embedded only in descriptive text.

### Correlation and Causation

Related activity should preserve correlation and causation where available. This supports questions
such as whether a reminder preceded completion, which webhook delivery led to a callback, or which
workflow action changed an Exchange.

Correlation is evidence of a relationship, not proof that one event caused another. Effectiveness
metrics must state the attribution rule they use.

### Business Schema Context

When a resource has a Schema Assignment, analytical context may include:

- Stable Schema Definition identity.
- Exact Schema Version.
- Stable Field or binding identity.
- Field Contract and type-contract version.
- Canonical option identity or approved canonical value.
- Value provenance where relevant.

Only Fields declared reportable under their Field Contract may become analytical dimensions. Field
visibility and resource authorization still apply.

## Metric Catalog

DocuHyphen should maintain a governed metric catalog. Each metric definition describes:

- Stable metric key and version.
- Business name and purpose.
- Owning product or domain area.
- Authoritative fact sources.
- Population and exclusion rules.
- Calculation and status semantics.
- Supported dimensions and time grain.
- Required freshness.
- Sensitivity classification.
- Authorized audiences.
- Retention and historical correction behavior.
- Known limitations.

The catalog prevents two screens from displaying differently calculated values under the same
label. It also provides the contract for exports and future external reporting.

## Analytical Model

### Facts

Facts represent immutable occurrences or stable observations, including:

- A document was uploaded, viewed, downloaded, shared, archived, or deleted.
- An Exchange changed lifecycle state.
- A workflow or step entered or left a state.
- A workflow decision was recorded.
- A request was created, fulfilled, waived, cancelled, or became overdue.
- A communication or webhook delivery attempt reached an outcome.
- An application callback completed or expired.
- A permission, Share, role, or capability changed.
- An authentication or security event occurred.
- A document version changed stored-byte consumption.
- A Schema Assignment or reportable Field Value changed.

Current-state snapshots may support inventory measures, but they must not replace transition facts
when duration, historical state, or period movement matters.

### Dimensions

Dimensions provide governed ways to group and filter facts. Examples include:

- Owner Scope Reference.
- Resource type and Resource Reference.
- Workflow Definition and exact version.
- Workflow step type and stable step identity.
- Document type and lifecycle state.
- Principal kind and approved organizational grouping.
- Communication template, channel, and event purpose.
- Registered application, endpoint, and webhook event type.
- Schema Definition and Schema Version.
- Reportable Field and canonical option identity.
- Security incident type and severity.
- Time period using the organization's reporting time zone.

Dimensions that contain sensitive or high-cardinality values require explicit approval. Free-text
Field values, email addresses, document titles, and error messages should not become general-purpose
dimensions by default.

### Measures

Measures include counts, distinct counts, durations, rates, sizes, and point-in-time balances.
Every measure must define its unit, denominator, null behavior, terminal-state rules, and treatment of
late or corrected facts.

Percentiles and medians are preferable to averages for skewed durations such as workflow completion
and request response time. Averages may still be shown when their limitations are clear.

### Projections

Analytical projections provide read-optimized, tenant-aware views of approved facts. They may serve
different needs:

- Near-current operational status.
- Period trends.
- Bottleneck and duration analysis.
- Ranked resource or integration activity.
- Compliance evidence and security review.
- Executive summaries.

Projection design is replaceable. Dashboards depend on metric contracts, not on a particular
storage or aggregation mechanism.

## Analytics Categories

### Document Analytics

Document analytics should cover volume, access, engagement, lifecycle, stale content, and trends.

Important distinctions include:

- A view is not a download.
- A delivery attempt is not necessarily a successful content transfer.
- A new version is not necessarily a new logical document.
- Soft deletion, archival, and permanent removal are separate states.
- Repeated automated access by an application must remain distinguishable from human engagement.

Stale-content metrics use the most recent qualifying access fact and must define whether owner,
system, workflow, preview, and external-participant activity qualify.

### External Participant and Client Analytics

External participant analytics may report active and inactive participants, last meaningful
activity, shared documents, successful access, fulfilled requests, and outstanding work.

An external participant is not automatically a business client. A future client or account concept
must have its own stable identity and relationship model before client-level reporting is claimed.
Email address alone is not a durable client identity.

### Workflow Analytics

Workflow analytics should cover starts, terminal outcomes, completion duration, pending work,
overdue work, escalation, step wait time, decision time, integration waiting time, and callback time.

Workflow Definition versions remain distinct so changes to a workflow do not make historical
comparisons misleading. Step bottleneck analysis requires an explicit definition of when a step
became actionable, when work first occurred, and when the step became terminal.

Failure, rejection, cancellation, escalation, and administrative pause must not be collapsed into a
single unsuccessful outcome. Each has different operational meaning.

### User Activity and Work Analytics

Authenticated activity and meaningful work are separate measures.

- Authenticated activity includes successful session establishment and approved security events.
- Meaningful work includes domain actions such as uploads, decisions, comments, Shares, request
  fulfillment, workflow initiation, and approved changes.

Daily, weekly, and monthly active-user metrics must identify the qualifying activity set. Named-user
productivity rankings are sensitive and should not be a default organizational feature. Team-level
capacity, workload, completion, and aging measures are generally safer and more actionable.

### Security and Access Analytics

Security analytics should cover authentication outcomes, MFA and recovery events, permission
changes, external sharing, authorization denials, security incidents, sensitive-resource access,
application credential activity, and privileged administrative actions.

Audit evidence remains distinct from operational analytics. Analytics may summarize audit records,
but it must not weaken append-only, hash-chain, retention, or evidence requirements.

### Communication Analytics

Communication analytics should cover sends, terminal delivery outcomes, suppression, failures,
retries, channel performance, template use, and latency.

Effectiveness may be measured only through a documented attribution window and correlation rule.
For example, an approval completed after a reminder may be associated with that reminder, but the
metric must not state that the reminder caused the approval unless stronger evidence exists.

### Integration Analytics

The webhook integration architecture provides a rich operational source for:

- Queue and backlog age.
- Time to first delivery attempt.
- Delivery success and permanent failure.
- Retry and dead-letter rates.
- Endpoint throttling and egress-policy rejection.
- Callback completion and expiry.
- End-to-end workflow delay caused by delivery or callback waiting.

CloudEvent ID, delivery ID, workflow step, application, endpoint configuration version, attempt
history, and correlation identity must retain their distinct meanings.

### Storage Analytics

Storage analytics should cover current logical and physical usage, growth, version history, largest
resources, retention state, and ownership context.

Logical document size, stored encrypted size, replicated physical size, and billable size are
different measures. The platform must name which one a dashboard displays. Storage by user should
mean an explicitly defined attribution such as uploader, owner, or creator, not an inferred personal
ownership claim.

### Request Analytics

Request analytics requires a first-class request lifecycle. Required documents or workflow tasks
must not be presented as document requests unless the domain contract explicitly defines them that
way.

A future request model should make creation, responsibility, due date, fulfillment, waiver,
cancellation, reminders, and terminal status historically observable. Request duration begins and
ends according to the approved lifecycle, not according to whichever timestamp is easiest to query.

### Field-Based Business Analytics

Fields make business reporting possible without introducing industry-specific backend entities.
Organizations may group or filter operational metrics using approved reportable Fields such as case
type, jurisdiction, product, loss category, or decision outcome.

Field-based analytics must preserve:

- Schema and contract version identity.
- Canonical typed values and option IDs.
- Field reportability and classification metadata.
- Visibility rules for the caller and output channel.
- Historical meaning after labels, options, or schemas are retired.
- Provenance when it affects interpretation.

Free-form aggregation over every Field is not supported by default. Each Field Contract declares
whether it is searchable, filterable, sortable, reportable, or exportable.

## Dashboard Architecture

Dashboards are role-sensitive views over the same metric catalog rather than independent reporting
systems.

### Operational Overview

The overview should surface a small number of trusted indicators such as active Exchanges, pending
workflow work, overdue requests, integration failures, recent security incidents, and storage
movement. It should prioritize conditions requiring attention over decorative totals.

### Domain Dashboards

Focused dashboards may cover documents, workflows, requests, communications, integrations,
security, storage, external participants, and Field-based business activity.

Each dashboard should support:

- A clear reporting period and organization time zone.
- Approved filters using stable dimensions.
- Comparison with a previous equivalent period where meaningful.
- Data freshness and metric-definition information.
- Drill-down from aggregate to authorized supporting records.
- Empty, partial, delayed, and insufficient-permission states.

### Drill-Down

Drill-down is a new authorization and visibility decision. An aggregate that a caller may view does
not automatically permit access to every contributing record. Supporting records are filtered and
projected through their owning services.

### Exports and Scheduled Reports

Exports and scheduled reports are output channels subject to the same authorization, Field
visibility, minimization, retention, and audit rules as interactive dashboards. A downloadable file
must not become a way to bypass on-screen redaction.

## Authorization and Tenant Boundaries

Analytics uses the hardened authorization architecture.

- Human callers use effective capabilities for an explicit active organization where applicable.
- The target owner context is resolved from the analytical subject and is not inferred from the
  caller's primary organization.
- Registered applications remain `APPLICATION` principals and receive no analytical access through
  token scope alone.
- Platform administrator, auditor, and support roles do not receive unrestricted customer-content
  analytics by standing privilege.
- Personal, organization, and platform analytics remain separate contexts.
- Cross-organization aggregation is a platform-governance capability and is not available to an
  organization merely because it shares configuration or integrations with another organization.

The initial capability model should distinguish at least ordinary analytical viewing, sensitive
security or audit insight, and analytics administration. The exact capability inventory belongs in
the implementation plan and must fit the final hardened authorization model.

## Restricted Data and Aggregate Leakage

Analytics applies a two-stage decision:

1. Authorize access to the analytical resource and requested metric.
2. Project only dimensions and supporting data visible to the caller and output channel.

Restricted information can leak through totals, small groups, filter combinations, ranking, and
changes over time even when individual rows are hidden. The architecture should allow controls such
as:

- Suppressing groups below an approved threshold.
- Removing restricted dimensions.
- Coarsening time or category detail.
- Limiting named-principal reporting.
- Returning unavailable rather than zero when disclosure would be unsafe.

These controls must be deterministic and explainable. They must not silently change the business
definition of a metric.

## Time, History, and Comparability

Organizations need a configured reporting time zone. Stored occurrences remain based on an
unambiguous universal time, while daily and period boundaries use the selected reporting zone.

Historical comparisons must account for:

- Workflow Definition and Schema Version changes.
- Renamed or retired options.
- Metric-definition version changes.
- Late-arriving callbacks and delivery outcomes.
- Corrected or invalidated facts.
- Changes to organization membership and visibility.

Analytics should prefer preserving and annotating history over rewriting it to resemble current
configuration.

## Data Quality and Trust

Every metric should expose or internally track relevant quality signals:

- Last successful refresh.
- Oldest unprocessed fact.
- Source completeness.
- Duplicate or rejected fact count.
- Late-arriving fact count.
- Definition version.
- Known period gaps.

Dashboards must not display stale or partial results as if they were current and complete.

Analytical reconciliation should be possible against authoritative domain totals for selected
periods. Differences require an explainable cause such as status rules, privacy suppression, late
arrival, or a changed definition.

## Retention, Privacy, and Governance

Retention is defined by data class and purpose rather than one global analytics duration.

The governance model should address:

- Operational trend retention.
- Security and compliance evidence retention.
- Delivery and callback troubleshooting retention.
- Personal-data minimization and anonymization.
- Organization deletion and contractual retention.
- Legal hold where applicable.
- Field classification and reportability.
- Export lifetime and secure disposal.
- Use of historical identities after membership or participant deletion.

Deletion from a transactional screen does not automatically mean every compliance fact is removed.
The legal and product rule must be explicit, with anonymization used where identity is no longer
required but aggregate history remains legitimate.

## Extensibility

The architecture should allow future support for:

- Additional resource adapters and analytics categories.
- Business Unit and team scopes after their wider isolation model exists.
- Cross-domain funnel and outcome analysis.
- Customer-approved calculated metrics.
- External business-intelligence tools.
- Warehouse or lakehouse projections.
- Near-real-time streaming consumers.
- Forecasting and anomaly detection.
- Privacy-preserving cross-organization benchmarks.

These capabilities must extend the metric catalog, fact contracts, Scope References, Resource
References, and projection boundary. They must not bypass them.

## Deferred Capabilities

The following are intentionally deferred:

- A general-purpose customer query language.
- Customer-authored SQL or executable analytical code.
- Unrestricted reporting over every Field Value.
- Predictive scoring and automated decisions.
- Cross-organization customer benchmarking.
- A mandatory external warehouse.
- Exactly-once distributed processing claims.
- Named-user productivity league tables.
- Marketing website analytics.

Deferral is safe only if stable identities, occurrence context, versioned metrics, durable facts,
tenant attribution, and visibility metadata are retained from the beginning.

## Analytics Readiness Contract for Future Features

Every new or materially changed feature should answer these questions during design review:

- What meaningful business facts does the feature produce?
- Which facts are already reconstructable from authoritative records?
- Which occurrences would be lost without durable capture?
- What principal caused the occurrence?
- What Resource Reference and owner context apply?
- What correlation and causation references are available?
- What are the occurrence, completion, and terminal-state semantics?
- Which Fields or dimensions may be reported, and under which contract versions?
- What information is sensitive, restricted, or prohibited from analytics?
- Which metrics could the feature support, and how would they be defined?
- Can projections be rebuilt idempotently?
- What retention and deletion rules apply?
- How will authorization and restricted-data projection apply to aggregates and drill-down?

This review does not require every feature to ship a dashboard. It ensures that valuable facts are
not discarded and ambiguous metrics are not created later.

## Anti-Patterns to Avoid

- Building dashboards directly over mutable entities without a metric contract.
- Creating a second event model for facts already durably captured.
- Treating synchronous in-process events as durable analytical history.
- Counting interface activity as business productivity.
- Using display labels as historical dimension keys.
- Inferring organization ownership from the caller or current relationships.
- Treating active organization as the analytical tenant of every resource.
- Assuming resource access reveals every Field or aggregate.
- Copying unrestricted Field Values, payloads, or audit snapshots into generic facts.
- Mixing workflow rejection, cancellation, failure, escalation, and pause into one outcome.
- Calling an external participant a client without a client domain identity.
- Calculating storage repeatedly from physical files instead of authoritative size facts.
- Claiming request analytics before a request lifecycle exists.
- Reporting communication effectiveness without a documented attribution rule.
- Letting each dashboard define its own version of the same metric.
- Publishing named-user comparisons without an approved governance purpose.
- Making a warehouse a prerequisite for useful operational analytics.

## Decisions to Confirm Before Implementation Planning

- The initial analytical audiences and the sensitive views each may access.
- The first approved metric catalog and exact metric semantics.
- Whether analytics initially supports organization and personal contexts or organization only.
- The organization reporting time-zone policy.
- Which document operations qualify as views, downloads, and meaningful access.
- The first-class request lifecycle that will support request analytics.
- Which workflow timestamps define activation, first action, waiting, and completion.
- Which communication attribution rules are acceptable.
- Which storage measure is operational and which is billable.
- Which initial Fields are eligible for grouping, filtering, export, and aggregation.
- Retention and anonymization rules by analytical category.
- Small-group suppression and named-principal reporting policy.
- Required freshness for operational, security, and executive views.
- Whether external applications may read any analytics in the first release.
- The boundary between Analytics, Audit, and the existing Integration Activity experience.

## Success Criteria

The architecture is successful when:

- New features retain meaningful facts without adding duplicate tracking systems.
- Every metric has one stable, versioned, explainable definition.
- Organization boundaries and resource ownership remain correct in every aggregate.
- Resource authorization and analytical data visibility remain separate decisions.
- Workflow, document, communication, integration, storage, security, request, and Field-based insight
  can share one analytical vocabulary.
- Historical reports remain understandable after workflows, schemas, Fields, labels, and options
  change.
- Dashboards, drill-downs, exports, and future external reporting apply consistent policy.
- Analytical projections can be rebuilt without double counting.
- Data freshness and quality are visible rather than assumed.
- A future warehouse, Business Unit model, or additional analytics category extends the architecture
  instead of replacing its fact, identity, metric, and governance contracts.
