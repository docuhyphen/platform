# External Application Integration Business Specification

## 1. Document Purpose

This document defines the business requirements for enabling DocuHyphen workflows to interact with external applications and services.

DocuHyphen is centered on document Exchanges. Organizations must be able to include external business capabilities in an Exchange workflow without requiring organization-supplied code to be installed or executed inside DocuHyphen.

This specification describes the required business capability and expected outcomes. Detailed system architecture, technology selection, data models, endpoint design, and delivery sequencing will be addressed in a later architecture and implementation plan.

## 2. Business Context

Organizations use external applications to perform activities that affect the creation, collection, review, validation, authorization, signing, release, or governance of documents.

Examples include:

- Sending documents to DocuSign or another electronic signature provider.
- Requesting facial or identity verification before documents are released.
- Confirming an external banking event before an Exchange can proceed.
- Validating professional credentials through an industry system.
- Sending documents to an organization's records, case management, or compliance platform.
- Receiving externally generated documents or verification evidence into an Exchange.

These capabilities differ by industry and organization. Adding every external activity as a built-in workflow step type would create a large, inflexible catalogue and would couple the workflow engine to individual providers.

DocuHyphen therefore requires an extensible integration capability that allows an organization to define meaningful custom workflow steps while keeping external execution outside the DocuHyphen application.

## 3. Business Objective

The objective is to allow authorized organizations to create and use custom workflow step definitions that interact with external applications through approved integration methods.

The capability must:

- Preserve the document-centered purpose of DocuHyphen.
- Present business-friendly step names in the workflow designer.
- Support different external systems without changing the core workflow engine for each provider.
- Avoid organization-uploaded executable code.
- Protect Exchange documents, personal information, credentials, and organization boundaries.
- Provide traceable, reliable, and recoverable external processing.
- Allow an external result to control the continuation of a workflow.

## 4. Guiding Principles

### 4.1 Document-centered integration

A custom step must have a defined relationship to an Exchange or its documents. It should create, collect, inspect, transform, sign, verify, distribute, release, or govern documents, or confirm an external fact required for the document lifecycle.

### 4.2 Business intent separated from transport

A workflow should express an activity such as "Verify Identity" or "Request Electronic Signature." It should not expose technical transport instructions as the step's business meaning.

### 4.3 No organization-supplied code execution

Organizations must not upload Kotlin, JavaScript, scripts, libraries, or other executable code to DocuHyphen. External processing must occur through an approved connector, protocol, or organization-managed application.

### 4.4 Least-privilege data sharing

Each custom step must explicitly declare the Exchange information and documents that may be shared. External applications must receive only the information needed to perform the activity.

### 4.5 Stable and auditable workflows

Published workflows must retain the behavior and configuration version against which they were designed. Changes to a custom step definition must not silently alter active or historical workflow executions.

## 5. Scope

### 5.1 In scope

- Creating and managing organization-specific custom step definitions.
- Making approved custom steps available in the workflow designer.
- Mapping Exchange data and selected documents to step inputs.
- Receiving typed results, status updates, and documents from external applications.
- Supporting immediate, delayed, user-interactive, and externally initiated completion.
- Configuring security credentials through protected connection records.
- Monitoring, retrying, cancelling, timing out, and manually recovering external operations.
- Maintaining an audit history of external interactions.
- Supporting three integration options:
  - DocuHyphen-managed connectors.
  - Generic protocol connectors.
  - Organization middleware.

### 5.2 Out of scope

- Executing organization-uploaded code within DocuHyphen.
- Building a general-purpose application marketplace in the initial capability.
- Replacing external systems as the system of record for their specialist processes.
- Embedding provider-specific behavior directly into the core workflow engine.
- Allowing workflow designers to enter or view raw secrets.
- Defining the detailed technical architecture or implementation sequence.

## 6. Integration Options

### 6.1 DocuHyphen-managed connectors

DocuHyphen may provide and maintain connectors for selected external applications with broad relevance to document Exchanges.

Examples include electronic signature, document processing, identity verification, or records management providers.

A managed connector should provide business-friendly operations and shield organizations from unnecessary provider-specific technical details. The organization remains responsible for its provider account, authorization, lawful use, and any provider charges unless a separate commercial arrangement states otherwise.

Managed connectors are appropriate when:

- A provider is commonly used across multiple organizations.
- The integration requires specialist handling that cannot be safely expressed through generic configuration.
- A consistent DocuHyphen user experience provides clear business value.
- DocuHyphen can support the provider's security, lifecycle, and maintenance requirements.

### 6.2 Generic protocol connectors

DocuHyphen may provide configurable connectors for standard integration protocols and interaction patterns.

These may include:

- REST API requests.
- Outbound webhooks and inbound callbacks.
- Status polling.
- Message publication and correlated responses.
- Secure file transfer.
- Inbound business events.

Generic connectors are appropriate when an external system supports a standard protocol and the required behavior can be safely configured without custom code in DocuHyphen.

Configuration must be constrained, validated, and governed. A generic connector must not become an unrestricted mechanism for accessing arbitrary internal or internet resources.

### 6.3 Organization middleware

An organization may connect DocuHyphen to its own integration service or integration platform. Examples include an API gateway, enterprise service bus, integration platform, or organization-managed adapter.

DocuHyphen communicates through a supported, stable contract. The organization's middleware is responsible for translating that contract into the protocols and data formats required by banking systems, legacy applications, proprietary industry platforms, or other internal services.

Organization middleware is appropriate when:

- The external application is private, proprietary, or accessible only within the organization's environment.
- The organization already uses an integration platform.
- Provider-specific transformation or orchestration is required.
- Regulatory or operational policy requires the organization to control the integration boundary.

## 7. Custom Step Definition

An authorized organization administrator must be able to define a custom workflow step that represents a business activity performed with an external application.

A custom step definition must include:

- A unique key within the organization.
- A business name and description.
- A version.
- The selected connection or connector.
- The requested external operation.
- Its completion pattern.
- Typed input definitions.
- Typed output definitions.
- Permitted document inputs and document outputs.
- Expected result outcomes.
- Timeout and failure behavior.
- Retry and recovery rules where applicable.
- Permissions required to configure and use the step.

The workflow designer must display the custom step as a business capability. Technical connection and credential details must remain managed separately.

## 8. Supported Completion Patterns

The capability must support the following business interaction patterns regardless of the underlying integration option.

### 8.1 Immediate completion

DocuHyphen sends a request and receives the final result during the same interaction.

Example: classify a document and immediately return its classification.

### 8.2 Delayed completion by callback

DocuHyphen starts an external operation and waits for an authenticated response from the external application.

Example: send an envelope for signature and wait for completion or rejection.

### 8.3 Delayed completion by polling

DocuHyphen starts an external operation and periodically requests its current status until it completes, fails, or times out.

Example: request document analysis from a service that does not support callbacks.

### 8.4 Message-based completion

DocuHyphen publishes a request and waits for a correlated response through an approved messaging service.

Example: request confirmation from an enterprise banking platform.

### 8.5 User-interactive completion

DocuHyphen creates an external session and directs an authorized participant to complete an activity through a provider-controlled experience.

Example: complete facial verification or an embedded electronic signing session.

### 8.6 File-based completion

DocuHyphen exchanges an approved document package and manifest with an external system and later receives a correlated response package.

Example: exchange documents with a legacy government or banking system through secure file transfer.

### 8.7 Inbound event completion

DocuHyphen does not initiate the external activity. The workflow waits for an authenticated external event that satisfies the step's completion criteria.

Example: wait for an externally managed payment confirmation before releasing documents.

## 9. Actors and Responsibilities

### 9.1 Platform administrator

- Controls which connector types and managed connectors are available.
- Establishes platform-wide security and governance restrictions.
- Can disable an unsafe or unavailable connector.
- Monitors platform-level integration health without gaining unauthorized access to organization data.

### 9.2 Organization administrator

- Creates and manages organization connections.
- Creates, tests, versions, publishes, and retires custom step definitions.
- Selects which Exchange data and document categories may be shared.
- Assigns permissions for using custom steps.

### 9.3 Workflow designer

- Adds approved custom steps to organization workflows.
- Maps workflow variables and documents to defined inputs.
- Maps results to typed workflow outputs.
- Configures permitted step-level outcomes and failure behavior.

### 9.4 Exchange participant

- Completes any external user interaction for which they are authorized.
- Can see an understandable status when an Exchange is waiting for external processing.
- Must not be exposed to credentials or unnecessary technical integration details.

### 9.5 External application or organization middleware

- Authenticates its interaction with DocuHyphen.
- Uses the supplied correlation reference.
- Processes only the data authorized for the operation.
- Returns results that conform to the agreed contract.
- Handles retries without causing duplicate business actions.

## 10. Functional Requirements

### 10.1 Connection management

- Authorized administrators must be able to create, test, disable, and retire connections.
- Connections must be scoped to the owning organization unless explicitly provided as a platform-managed capability.
- Credentials must be stored and managed separately from workflow definitions.
- Workflow designers must reference a connection without being able to retrieve its secrets.
- Disabling a connection must prevent new executions and provide a controlled response for executions already in progress.

### 10.2 Definition management

- Authorized administrators must be able to create, validate, test, publish, version, and retire custom step definitions.
- A published version must be immutable.
- A new version must not automatically replace the version used by a published workflow.
- A retired definition must remain available for historical interpretation and existing execution handling.
- Definitions must be isolated to their owning organization unless explicitly published as a platform-managed definition.

### 10.3 Workflow design

- The workflow designer must list only custom steps available to the current organization and user.
- Each step must display its business purpose, required inputs, possible outcomes, and expected external interaction.
- Input and output mappings must be type-checked before a workflow can be published.
- A designer must explicitly select any documents or document categories shared externally.
- A workflow must not publish if required mappings, connection references, or completion rules are invalid.

### 10.4 Step execution

- Each external execution must receive a unique correlation reference.
- The workflow step must expose a clear state such as pending, awaiting user action, waiting externally, completed, failed, timed out, or cancelled.
- The workflow must continue only after an accepted terminal outcome is recorded.
- Duplicate responses must not complete or alter a step more than once.
- Late responses must be recorded but must not silently change an already completed, cancelled, or timed-out workflow.
- External errors must not expose credentials or sensitive provider details to unauthorized users.

### 10.5 Document handling

- Only explicitly authorized documents may be sent externally.
- Temporary document access must be time-limited and scoped to the intended operation.
- Documents returned by an external application must be validated before becoming part of an Exchange.
- Returned documents must retain provenance linking them to the external execution.
- The organization must be able to define whether returned documents create a new document, a new version, evidence, or metadata.
- Document access, transfer, replacement, and creation must be recorded in the audit history.

### 10.6 Results and outcomes

- External results must be validated against the custom step definition.
- Outputs must be typed and available for later workflow conditions or actions where permitted.
- A definition may expose multiple business outcomes, such as verified, not verified, completed, rejected, or additional information required.
- Technical failure must remain distinguishable from a valid negative business outcome.
- External evidence references and provider transaction references must be retained where required for audit purposes.

### 10.7 Recovery and administration

- Authorized users must be able to inspect the status of an external execution.
- The system must support controlled retries where retrying cannot create an unsafe duplicate business action.
- Authorized users must be able to cancel or manually resolve an execution where organizational policy permits it.
- Manual resolution must require a reason and create an audit record.
- Timeouts must follow configured workflow behavior, such as fail, escalate, request intervention, or follow an alternative workflow outcome.

## 11. Security, Privacy, and Governance Requirements

- Every external request and response must be authenticated through an approved method.
- Data must be protected during transmission and while retained.
- Credentials and signing secrets must never be stored in workflow definition content or audit payloads.
- Callback credentials or tokens must be scoped as narrowly as practicable.
- The system must prevent one organization from using or viewing another organization's connections, definitions, executions, documents, or results.
- External endpoints and destinations must be subject to platform security controls.
- Sensitive payload fields must be redacted from operational logs.
- Organizations must be able to understand what data a custom step shares before publishing a workflow.
- Retention of request payloads, responses, evidence, and logs must follow applicable policy.
- User-interactive providers must receive only the participant and Exchange information required for the session.
- The system must support the revocation or rotation of connection credentials without editing every workflow.

## 12. Audit and Traceability Requirements

The audit history must provide sufficient evidence to reconstruct an external workflow interaction. It must include, where applicable:

- Organization, workflow, Exchange, step definition, and definition version.
- Connector and connection reference without exposed secrets.
- Initiation date, completion date, status transitions, and initiating principal.
- Correlation and external transaction references.
- The categories of data and identities of documents shared.
- Requests, acknowledgements, callbacks, polling attempts, retries, timeouts, cancellations, and manual interventions.
- Result outcome and returned document provenance.
- Validation or authentication failures.

Audit access must follow organization permissions and privacy rules.

## 13. Reliability and Operational Requirements

- External unavailability must not corrupt the workflow or lose its execution state.
- Transient failures must support controlled retry policies.
- Requests and responses must support idempotent processing.
- Each execution must have defined timeout behavior.
- Long-running external operations must survive application restarts and routine deployments.
- Administrators must be able to identify degraded connectors and affected workflow executions.
- The platform must apply limits to payload size, document size, execution duration, polling frequency, retry count, and concurrent operations.
- Connector maintenance or version changes must not silently change published workflow behavior.

## 14. Example Business Scenarios

### 14.1 Electronic signature through a managed connector

An organization adds "Request Electronic Signature" to a workflow. DocuHyphen sends selected Exchange documents and signer details through a managed connector. The participant completes signing through the provider. DocuHyphen records the outcome and adds the signed documents and signature evidence to the Exchange.

### 14.2 Facial verification through a generic connector

An organization defines "Verify Participant Identity" using an approved REST-based connection. DocuHyphen creates an external verification session and directs the participant to the provider's experience. The provider returns a correlated result. The workflow records the verification outcome and continues according to organization policy.

### 14.3 Banking confirmation through organization middleware

An organization defines "Confirm Settlement" using its middleware connection. DocuHyphen sends the relevant Exchange reference and permitted transaction information to the middleware. The middleware communicates with the banking system and later returns a correlated confirmation. DocuHyphen does not process the payment. It records the external fact required before the controlled documents may be released.

### 14.4 External records filing through secure file transfer

An organization defines "File Approved Documents" using a generic secure file transfer connector. DocuHyphen sends an approved document package and manifest. The external records system returns a receipt package. The workflow stores the filing reference and receipt as Exchange evidence.

## 15. Business Rules

- A custom step must be associated with an organization unless it is platform-managed.
- Only authorized administrators may create or change connections and custom step definitions.
- Only published custom step versions may be used in published workflows.
- Published workflows must reference a specific custom step version.
- Custom steps must not access Exchange data or documents that were not explicitly mapped and authorized.
- A technical integration failure must not be treated as a successful or negative business decision.
- An external application must not directly determine access beyond the outcomes allowed by the workflow definition.
- Every externally returned document must have a recorded source and associated execution.
- Completion responses must be correlated to a single active execution.
- Manual overrides must be permission-controlled, justified, and audited.

## 16. Acceptance Criteria

The capability will satisfy this business specification when:

1. An authorized organization administrator can configure a connection without placing credentials in a workflow.
2. An authorized administrator can define and publish a versioned custom step with typed inputs, outputs, documents, outcomes, and completion behavior.
3. A workflow designer can use the custom step without needing to understand or modify executable code.
4. The same workflow execution model can use a managed connector, generic connector, or organization middleware.
5. A workflow can wait reliably for an immediate response, callback, poll result, message, user interaction, file response, or inbound event as configured.
6. Only explicitly selected Exchange data and documents are made available externally.
7. External results and returned documents are validated, correlated, and recorded before the workflow continues.
8. Duplicate, late, unauthenticated, or invalid responses cannot incorrectly alter the workflow.
9. Administrators can identify failed, delayed, timed-out, and manually resolved executions.
10. The audit history identifies what occurred without exposing credentials or unnecessary sensitive payload content.
11. Organization data, definitions, connections, and executions remain isolated.
12. A new custom step version does not silently change an already published workflow.

## 17. Measures of Success

Success should be evaluated using measures such as:

- Number of organizations using external custom steps.
- Number and variety of external document processes supported without workflow engine changes.
- Percentage of external executions completed successfully.
- Average time to configure and publish a new integration-backed step.
- Reduction in manual document transfer and status reconciliation.
- Number of failures recovered without data loss or workflow corruption.
- Absence of cross-organization data exposure and unauthorized document sharing.
- Traceability of external processing during audits and support investigations.

## 18. Assumptions and Dependencies

- External applications provide at least one supported integration mechanism.
- Organizations hold the necessary external accounts, agreements, and lawful authority to share data.
- DocuHyphen has an established organization permission model and workflow versioning capability that can be extended.
- External applications may have different availability, performance, payload, authentication, and retention constraints.
- Some industries will require organization middleware because direct connectivity is prohibited or impractical.
- Detailed commercial packaging for managed connectors will be decided separately.

## 19. Risks Requiring Later Architecture Decisions

- Arbitrary network access through generic connectors could create security exposure.
- Large or sensitive document transfers may require specialized handling.
- Provider callbacks may be delayed, duplicated, reordered, or never delivered.
- Polling may create provider cost, throttling, and scalability concerns.
- Embedded user experiences may have browser, accessibility, privacy, and session continuity constraints.
- Provider contract changes may affect active integrations.
- Manual recovery could weaken process controls if permissions and audit requirements are insufficient.
- Storing detailed external payloads may conflict with privacy and retention obligations.

These risks must be resolved through the later architecture and implementation plan without weakening the business requirements in this specification.

## 20. Future Considerations

The initial design should not prevent later support for:

- A governed catalogue of reusable step definitions.
- Platform-certified connector packages.
- Connection health dashboards and service-level reporting.
- Test environments and provider sandbox connections.
- Regional routing and data residency controls.
- Organization approval workflows for publishing or changing integrations.
- Import and export of custom step definitions between organization environments.
- Standard industry integration profiles.

