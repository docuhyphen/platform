# Information Requests API reference

All paths are relative to the API base. Endpoints under `/no-auth/` authenticate with an access
link and a verified session, not an access token. Every other endpoint needs an access token and
answers to the central authorization service. The inventory below was generated from the resource
annotations on 2026-09-30 (154 endpoints).

## Conventions

| Header                    | Use                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
|---------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `If-Match`                | Required on conditional commands, naming the ETag read (request, parties, responses, party, evidence, clock, submission, Fields answers, preservation hold, or retention schedule). A missing value answers `428` and a stale one `412`, each with a stable precondition code. No Information Request command reads `*` as whichever version is current: `*` is refused with `428` and the same precondition code as a missing value; the Exchange Fields answer endpoints are not Information Request commands and keep their own rule. A caller without access to the target is refused with `403` before its precondition, the request's state, its execution grant, or a submission lock is checked. A signed-in caller who may not view a request gets that same `403`, with the same body, for a request id that does not exist, before anything about the request or the items it names is looked up. |
| `Idempotency-Key`         | Required on commands. A retry with the same key does not run the command again: it answers with what the command changed as that stands now, and every ETag in the answer, header and body alike, describes that same current state, so a later change made by another command shows up in the answer and its ETags. A retried link issue, rotation, or replacement is refused instead, because a raw link token is never shown twice. A different body under the same key is refused. Bodies are compared field by field, so a value that only moves a separator character from one field to another is a different body. For submitting, recording a confirmation, recording review decisions, review overrides, assignment changes, reopening a review, and Template publication, the `If-Match` value is part of the compared body.                                                                      |
| `X-Request-Access-Token`  | The access link token on `/no-auth/` calls.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| `X-Request-Session-Token` | The verified session on `/no-auth/` calls.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| `Retry-After`             | Sent with every `429`, as the seconds before a retry may succeed; on `/no-auth/` routes it is the time left on the address's current pause.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |

Refusals carry `{"errorMessage": ..., "reasonCode": ...}`. Request codes start with
`INFORMATION_REQUEST_` and are listed in `InformationRequestErrorCatalog`; they are never renamed or
reused. Plan refusals use the subscription denial body (`reasonCode`, `planCode`, `featureCode`,
`currentValue`, `limit`, `upgradePlanCode`), with reasons `FEATURE_NOT_INCLUDED`,
`PLAN_LIMIT_REACHED`, `SUBSCRIPTION_PAST_DUE`, `SUBSCRIPTION_SUSPENDED`, `SUBSCRIPTION_CANCELED`,
`TRIAL_ENDED`, `SEAT_LIMIT_REACHED`, and `ORGANIZATION_SUBSCRIPTION_REQUIRED`. A request body that
is not valid JSON, or that leaves out a required field, answers `400` with the reason
`REQUEST_BODY_INVALID`. A change refused by a stored rule that the services do not check first answers
`409 INFORMATION_REQUEST_CONSTRAINT_CONFLICT` rather than `500`; any other storage failure still answers `500`.
A Field value the Fields engine refuses answers `400 INFORMATION_REQUEST_FIELD_VALUE_INVALID` with its
message, a missing or stale Field precondition answers `428` or `412` with the current `ETag`, and an
item refusal answers `409` with its `problems`, on every request command route and on the request and response
workspace reads, signed in or through an access link. Times in command bodies, such as a reviewer assignment's `dueAt`,
an
Accepted Fact's `validFrom`, `validTo` and `expiresAt`, a business decision's `decidedAt`, and a
clock's `receivedAt`, are JSON numbers of milliseconds since the Unix epoch; a time sent as a
string is `400 REQUEST_BODY_INVALID`. A recurrence's `firstDueAt` is the exception and takes an
ISO-8601 string. Every time in a response body is also a JSON number of milliseconds since the Unix
epoch, including a recurrence's `firstDueAt`; a reminder result's `cooldownUntil` is the only
ISO-8601 string. An optional time that is not set is sent as an explicit `null`, not left out: for
example an Accepted Fact offer's `validTo` and `expiresAt` without an end, or an operations row's
`nearestDueAt` when no clock is running. `0` is a time, the Unix epoch, and never means "not set".

Every command `Idempotency-Key`, including those on the response, group occurrence, access link, participant
account link, delegated authority, evidence and record preservation routes, contains at most 128 characters; a
longer key answers `400 COMMAND_IDEMPOTENCY_KEY_INVALID` before the command runs. Stored command receipts retain a
256-character boundary for internal operation prefixes. Every Information Request authorization
refusal uses `INFORMATION_REQUEST_FORBIDDEN`. Decisions about a request's own records (parties, links, evidence,
packages, reviews, facts, exports, holds and the like) are made through the request or the service that owns the
record; a decision named directly on such a record is refused as unresolved. A `429` response includes
`retryAfterSeconds` in
its JSON body as well as `Retry-After`, so browser clients can read the delay without relying on
cross-origin header exposure. Fields and Blueprint timestamps use epoch-millisecond numbers,
including nullable Schema publication times.

Delegated authority instrument references and revocation reasons contain at most 512 characters;
over-length inputs answer `400`. Both authenticated and access-link party listings return the
same identity-filtered labels, trust state and collection `ETag`. A permitted request header
includes its Template Version identifier, as its response workspace does, while owner identifiers
remain manager-only. Removing an organization member is refused before membership deletion when
account links or retained personally owned records prevent deleting the underlying account.
Malware assessment reuse is confined to the same request, so a new scan cannot create another
request's retention dependency.

Template `contentHash` uses a dedicated V1 canonical configuration format independent of API DTO
serialization. It is derived from immutable configuration rather than a separately stored cache. Command result bodies
acknowledge the mutation; refresh the
permitted workspace/detail read for computed evidence, conformance and answer projections.
Historical DTO state and hash-algorithm tokens retain their documented string vocabulary.
Condition response archives and effects are forensic-only stored history. They are absent from
active response reads and from request/subject exports; those exports do not reconstruct archived
answer content.

Client retry signatures include the complete revocation body and a correction's subject, purpose
and policy basis as well as its item/value/reason. A lost response retries the same inputs under
the same key. `COMMAND_RECEIPT_FINGERPRINT_CONFLICT` ends that client attempt and rereads the
current record before issuing a new key. Client file-size displays use binary KiB/MiB/GiB units;
wire lengths remain byte counts.

## Discovery and listing

- `GET /information-request-capabilities`: for the caller's active scope, the plan, status,
  enforcement mode, `featureIncluded`, `newWorkAvailable` and `newWorkUnavailableReason`,
  `operationallySuspended`, `typedAnswersAvailable`, `personalTemplatesAvailable`, `assignedWork`,
  `holdsRequests`, and `quotas` (`maxOpenRequests`, `openRequests`, `maxActingPartiesPerRequest`,
  `maxEvidenceFilesPerRequest`, `maxEvidenceBytesPerRequest`, `maxCommittedEvidenceBytes`,
  `committedEvidenceBytes`; a missing maximum means no cap). While the plan is enforced and the
  owner holds `maxOpenRequests` open requests, `newWorkUnavailableReason` and the Exchange listing's
  `creationUnavailableReason` are `OPEN_REQUEST_LIMIT_REACHED`; Templates stay available.
- `GET /exchanges/{exchangeId}/information-requests`: the requests on an Exchange the caller may
  see. Each has an `executionStanding`. The listing has `canCreate` and `creationUnavailableReason`,
  and, only for a caller who may create requests on the Exchange, `creationOwner` (`ownerType` with
  `ownerOrganizationId` or `ownerUserId`): the owner every request created there takes. The web client
  lists Templates, Blueprints, and Schemas to start from only while the active workspace is that owner.
  A caller who may see neither the Exchange nor any request on it is refused with `403`, the same
  answer, with the same body, that an Exchange id that does not exist gets.
- `GET /information-requests/{id}`: the request header, with its `executionStanding` (`kind`; the
  `reason` only for people who manage the request). A superseded request's `supersededByRequestId`
  names its replacement only to a caller who may view the replacement; anyone else gets `null`, in
  this and every other answer that carries the header.
- `GET /information-request-reviews`: the caller's review queue. Each entry has an
  `executionStanding` without a reason. An assignment is listed to whoever acts for its reviewer
  party now (its holder, a member of a group party, or the account a participant linked), so a
  reassigned party's assignments follow the new holder.
- `GET /information-request-assignments`: every request on which the caller holds an active party role (directly,
  through a group, or through a linked participant) and may view, as an array of
  `{request, exchangeName}`, where `request` is the same summary the Exchange listing returns, with the
  caller's roles and next action. It lists requests on Exchanges the caller cannot open. Nearest due
  first, then by title. An empty array means nothing is assigned. `assignedWork` in the capabilities is
  `true` while this list would have an entry. A request leaves the list, and stops counting for
  `assignedWork`, once it is closed, cancelled, superseded or expired.

## Templates

- `GET|POST /information-request-templates`, `GET /information-request-templates/{id}`.
- `GET /information-request-templates/{id}/versions/{versionNumber}`: reads one published or retired
  Version, so an earlier or retired Version stays inspectable. A draft is read through the Template.
- `PUT /information-request-templates/{id}/draft/configuration`,
  `POST .../draft/publication`, `POST .../versions`, `POST .../versions/{versionNumber}/retirement`.
- `POST /information-request-templates/{id}/clones`: copies a Version into the caller's personal or
  organization scope. This is how a platform Template is used. The `PLATFORM` scope cannot be
  created through this API.
- `POST /information-request-configuration-bundles/validations`.

A Template lists every published and retired Version in `versions` (`versionNumber`, `status`,
`publishedAt`, `retiredAt`, oldest first; the draft is not listed), and a list summary names its latest
published Version in `latestPublishedVersionId` and `latestPublishedVersionNumber`. A reader who may not
author the Template's scope sees only Templates with a published Version, in the list and on the detail
route alike. An unknown Template id, another owner's Template, a never-published Template a reader may
not see and a one-off request's private Definition all answer the same `403` (`Access denied to
information request template configuration`), so a refusal never confirms that a Template exists.

Lists in a Version read back in a canonical order: groups, condition rules, and the substitute and
supporting keys of a Requirement by key, so equal content always gets the same `contentHashSha256`.
Requirement keys in `conditionalRuleKey` and `occurrenceAnchorKey` are trimmed and lowercased like the
keys they name. Saving a draft locks its Template, so a Version retired or published at the same time
keeps the status it sets.

Saving refuses, with `INFORMATION_REQUEST_TEMPLATE_INVALID` naming the group, rule or Requirement:
more than 100 repeatable groups, a group nested more than five deep or whose occurrence path could
exceed 512 characters, a group requiring more than 100 occurrences or allowing more than 1000, and a
Version whose required occurrences would add more than 500 when a request is created; a condition
whose source Requirement is anchored in a group that is neither the governed Requirement's own group
nor one that encloses it; a substitute anchored anywhere but the top level or the document's own
group; and a collected Field that does not exist, also before a Schema Version is named. One-off
creation refuses a display name over 255 characters or a description over 1024 in the same way.

Saving a Template refuses a condition rule on a Requirement that is not Conditional, and a
repeatable group with no Requirement anywhere beneath it. Text has stated limits, refused with `400`
naming the field: keys 128 characters, compartment keys 64, titles and display names 255,
descriptions and prompts 1024, help text 2048, accepted values 255, and condition values 256.
An accepted `CONTENT_TYPE` value must be a media type such as `application/pdf`, with no wildcard or
parameters; it is stored in lower case, and anything else is refused with
`INFORMATION_REQUEST_TEMPLATE_INVALID` naming the section and Requirement, on saving and on publication (publication
also refuses a value stored before this rule applied).
Retiring a Version sets the Template's status from the Versions that remain, so retiring the only
published Version while a draft exists leaves the Template a draft.
Saving and publication refuse, with `INFORMATION_REQUEST_TEMPLATE_INVALID` and the affected section and
Requirement keys, a collected Field that the named Schema Version does not bind, and evidence policy bounds
that cannot hold: a minimum file count below one or a maximum below the minimum, a file or total size limit
that is not positive or a total size limit below the file size limit, page bounds below one or crossing, a
maximum issue age below one day or without a required issue date, a minimum remaining validity below zero
or without a required expiry date, and a minimum coverage below one day or coverage rules without a required
coverage period. A condition rule whose hidden-data policy is `CLEAR_WITH_CONFIRMATION` or
`ARCHIVE_OUTSIDE_ACTIVE_RESPONSE` is refused when a Requirement it governs has a different `contributorRole`
from a Requirement it reads, and in a `STAGED` Template a condition that reads an answer from another stage
is refused; publication checks both again. A condition rule that reads a `RESPONSE_ATTESTATION` Requirement is
refused on saving and on publication: a condition reads an answer or a document, never a confirmation. A
disposition predicate on a `DOCUMENT` Requirement whose response states no disposition (or `NOT_ANSWERED`) reads
`PROVIDED` once the Requirement's own current files complete its evidence policy; a stated disposition always wins.
A refusal about a condition rule names it in `conditionRuleKey`;
`groupKey` names only repeatable groups.
`GET /schemas/versions/{versionId}` reads one Schema Version with its Field bindings for a caller who may
view its Schema; the Template editor uses it for a draft that names an earlier Version than the latest.
Any signed-in caller may read a platform Schema and its published or retired versions (`GET /schemas/definitions/{id}`,
`GET /schemas/versions/{versionId}`); a platform draft version stays limited to
platform administrators and organization members with Field configuration view. `scopeKind` on
`GET /fields/definitions` and `GET /schemas/definitions` is `PLATFORM`, `ORGANIZATION` or `PERSONAL`; another value
answers `400` naming the accepted values.
A Template's Request Schema must be a Schema created with `targetResourceType` `INFORMATION_REQUEST` on
`POST /schemas/definitions`; a Schema created without it targets `EXCHANGE`. The web client states the target
through the schema editor's **Used for** choice.
Saving configuration and publishing a draft also refuse `SUBJECT` in a Requirement's
`contributorRole` or attestation policy `requiredRoles`, with `INFORMATION_REQUEST_TEMPLATE_INVALID`
and the affected section and Requirement keys. Use `CONTRIBUTOR`, `PREPARER`, or `ATTESTOR` for
these duties, except that a `FIELD` or `DOCUMENT` Requirement is answered by `CONTRIBUTOR` or
`PREPARER`: `ATTESTOR` can confirm but never provide data or files, so it is refused there in the same
way. `SUBJECT` remains the request's subject identity. The same validation applies to
one-off request creation and private amendments.

A condition predicate that compares a Field states one value for a single-value test, a non-empty
list of option codes for `IN` and `NOT_IN`, and no value for `IS_EMPTY` and `IS_NOT_EMPTY`. The
value must read as the predicate's `valueType` and is stored in that type's canonical form; the
`valueType` must be the Field's type in the named Schema Version, and option codes must be options
that Field offers. Saving, one-off creation, and private amendments refuse any other condition with
`INFORMATION_REQUEST_TEMPLATE_INVALID` naming the rule, and publication checks the stored
conditions again. A Schema Version another owner holds is refused as another owner's before its
lifecycle, target, or key is described. At runtime a Field that an active Requirement of the request
collects but that has no stored value is empty (`IS_EMPTY` is true, `IS_NOT_EMPTY` and every comparison
false), as is a Field collected only by a Requirement its condition hides; a Field no Requirement of
the request collects is unknown.

In a `STAGED` Template, a Requirement and every Requirement named in its `substituteRequirementKeys`
belong to sections of the same `submissionStageKey`, so each stage's reviewed-content hash and
frozen evidence membership identify every file that can complete it. Saving configuration, one-off
creation, and private amendments refuse a substitute from another stage with
`INFORMATION_REQUEST_TEMPLATE_INVALID` naming the relying Requirement, and publication checks the
stored relationships again. `supportingEvidenceRequirementKeys` may still name a document from
another stage; a supporting link never completes the Requirement it supports. At runtime a substitute completes a
Requirement
only while the substitute is applicable: a Conditional substitute whose condition is false or
unknown at its occurrence (or at the root) does not satisfy the primary, even while its files are
retained under `RETAIN_SECURELY`, and its files stay out of the package.

`POST /information-request-configuration-bundles/validations` checks each `clockPolicies` entry
with the same rules as clock policy authoring: a known time zone (surrounding spaces ignored), a
positive standard duration, an urgent duration from one minute up to the standard one, an
`escalationAfterMinutes` that is absent (no escalation) or zero or more (0 escalates at the due
time), business periods only on a `BUSINESS` clock, each period within one day, no two periods of
one day overlapping (`period.overlap` at the later-starting period), and positive, distinct reminder
offsets (`collection.duplicate` at each repeat). Holiday dates are also checked.

Template audit events that change a Version (`information_request.template.configure`, `.publish`,
`.new_version`, and `.retire`) name it with `templateVersionId`, `versionNumber`, and
`versionRevision`; a new Version, and the configuration event of a copy made with `.../clones`, name their
source with `sourceTemplateDefinitionId`, `sourceTemplateVersionId`, and `sourceVersionNumber`.
`information_request.template.create` names only the Definition. A Template
change whose required audit capture fails, or whose audit draft is invalid, is refused and rolled
back; a capture the audit failure policy lets degrade does not stop the change.

These routes manage reusable Templates. A one-off request's private Definition answers `403`, like an
unknown id, on Template reads, configuration changes, publication, new Versions, retirement, and
cloning. Private configuration changes use the originating request's amendment operation.

## Creating, issuing, and ending

- `POST /information-requests`: from a Template Version, a Blueprint, or a one-off configuration. A
  platform Version is refused with `INFORMATION_REQUEST_TEMPLATE_VERSION_PLATFORM_COPY_REQUIRED`,
  and the open-request allowance applies.
  A private one-off Version is refused with `INFORMATION_REQUEST_TEMPLATE_VERSION_NOT_FOUND`.
  The same refusal applies when selecting it for a Blueprint or instantiating a Blueprint that
  already names it, and to any Version the request's owner does not hold, which is answered with the
  same message as an unknown id, before that owner's plan or the Version's lifecycle is examined. Each kind of creation
  authorizes the caller first, so a caller who may not
  create a request on the Exchange is refused `403` before the Exchange is locked or its state, the
  Template Version, or the owner's entitlement is examined.
- `POST /exchanges` with `blueprintDefinitionId`: when that Blueprint names a Template Version, the
  Exchange start also creates one draft request from it in the same transaction, with the initiator
  as Decision Maker. Such an Exchange may have no documents; any other Exchange needs at least one.
  The Blueprint's participants become request parties only when the Exchange has them as its primary
  recipient or a participant, and never the initiator. A Blueprint that was deleted or deactivated is
  refused with `409 INFORMATION_REQUEST_BLUEPRINT_UNAVAILABLE` here and when a request is created from
  it directly, and an unknown Blueprint id is refused with the same `403` as another owner's.
    - If the request cannot be created, nothing is created. The refusal is `409` with
      `INFORMATION_REQUEST_TEMPLATE_VERSION_RETIRED`, `..._NOT_PUBLISHED`, `..._NOT_FOUND`,
      `..._OWNER_MISMATCH` (the Template belongs to a different workspace than the Exchange),
      `..._SCHEMA_UNAVAILABLE` (the Version's Schema can no longer be assigned, for example because it
      was retired after publication; creating a request from such a Version is refused the same way),
      `INFORMATION_REQUEST_CAPABILITY_NOT_INSTALLED`, or a lifecycle reason.
    - A plan refusal stays `403`.
    - The created request is read from `GET /exchanges/{exchangeId}/information-requests`.
    - Document Library files that the Exchange's documents name are copied into storage only after the
      request is created, so a refused start leaves no stored file or thumbnail job.
- `GET /information-requests/{id}/document-placeholders`: the documents the request's Blueprint
  listed, frozen when the request was created, in `displayOrder`. Each has `title`, `required`,
  `restrictType`, `restrictedType`, and the Document Library snapshot (`libraryDocumentId`,
  `libraryTitle`, `libraryDescription`, `libraryDocumentType`, `libraryFileName`,
  `libraryFileSizeBytes`). Only callers who manage the request may read it.
- `POST /information-requests/{id}/issuance`: freezes the execution grant, checks committed
  evidence, and reserves a place for each acting party. More acting parties than the plan allows (every Decision Maker,
  Contributor, Preparer, Reviewer and Attestor counts) is
  `409 INFORMATION_REQUEST_CAPACITY_EXHAUSTED`, whose message states the plan's limit. It is refused with
  `409 INFORMATION_REQUEST_FIELD_OUTSIDE_AUDIENCE` while a party outside the owner holds a role that
  answers a Field Requirement whose binding is not `PUBLIC`. The grant lists the continuation actions it
  allows (responding, submitting, confirming, reviewing, assigning parties within the cap,
  uploading within the allowance, non-expanding amendments, reminders, cancelling, and superseding)
  and the plan features it was issued with. A change whose action the grant does not list is `409`
  `INFORMATION_REQUEST_CONTINUATION_NOT_GRANTED`. Typed Field answers follow the grant when it
  includes typed Fields; otherwise they follow the owner's live plan.
- `POST /information-requests/{id}/cancellation`, `POST .../supersession`,
  `PUT .../completion-gate`. A cancellation or supersession reason, like the reason of every successor,
  including a supplement, is a machine code matching `^[a-z0-9][a-z0-9._-]{0,127}$` after trimming; a blank
  reason means none, and anything else is `400` `INFORMATION_REQUEST_REASON_CODE_INVALID`. After the
  execution grant is revoked, the owner can still cancel or supersede the request when the grant
  lists that action; every other change, including follow-ups, is refused with
  `INFORMATION_REQUEST_EXECUTION_GRANT_REVOKED`.
  Superseding needs the supersede authority on the replacement as well as on the request it
  replaces. The replacement is authorized before anything about it is read, so an unknown id and a
  request the caller cannot supersede both answer `403`; a replacement the caller may supersede that
  is on another Exchange, or finished, is `409 INFORMATION_REQUEST_STATE_INVALID`. A `SUPERSEDING`
  successor is authorized the same way through the parties it copies. A draft can be cancelled or
  superseded like an issued request, and its replacement can be another draft; as with every change to
  a draft, this needs the owner's live plan, except that a draft can always be cancelled, and
  `PUT .../completion-gate` can always set `gatesExchangeClosure` to `false` on it, unless its owner is
  operationally suspended. Setting it to `true` still needs the live plan.
  Finishing a request stops its running clocks at the finishing instant. Cancelling or superseding a
  request revokes its access links, recorded as `REVOKE_ACCESS_LINK` with the reason `REQUEST_CANCELLED`
  or `REQUEST_SUPERSEDED`; access sessions already issued keep reading it until they expire. Issuing,
  cancelling, and superseding, assigning, reassigning, and revoking a party, and the cancellations made as
  an Exchange ends or is rejected or rescinded fail with `500` and are rolled back when their audit record
  cannot be captured. Deleting an Exchange cancels each of its unfinished requests with the reason
  `PARENT_DELETED`, as rejection and rescission do with theirs, so they stop counting toward the owner's
  open requests and committed evidence and can be disposed of. A Workflow instance running on a request
  is cancelled when the request is cancelled, superseded or expires, and when its Exchange is rejected,
  rescinded or deleted, whether or not the request had finished.
- `PUT /exchanges/{id}` does not take `rejectionReason`: a body that names one is `400`, also on a finished
  Exchange, and the reason is given with the recipient's decision (`POST /exchanges/{id}/acceptance-decisions`).
  Changing the Exchange's settings or ending it needs Exchange edit access, and a caller who may only view it
  is `403` (previously `500`). Accepting, rejecting, ending and rescinding an Exchange fail with `500` and are
  rolled back when their `exchange.lifecycle.*` audit event cannot be captured.
- Ending an Exchange (`PUT /exchanges/{id}` with status `ENDED`) while a request on it is open is `409`:
  `INFORMATION_REQUEST_COMPLETION_GATES_UNSATISFIED` while a request that must finish first is open, and
  `INFORMATION_REQUEST_REMAINING_REQUESTS_REQUIRE_CANCELLATION` while other requests are open, unless
  `cancelRemainingInformationRequests` is `true`. The refusal's `informationRequestIds` names only the
  requests the caller may view. Cancelling the remaining requests by ending needs the cancel authority
  on every one of them; otherwise ending is `409 INFORMATION_REQUEST_REMAINING_REQUESTS_NOT_CANCELLABLE`
  and nothing is cancelled. When an ending Workflow must approve the ending, the answer is `409` with
  `reasonCode` `EXCHANGE_ENDING_HELD_BY_WORKFLOW`, whether the Workflow has just started or was already
  running; the stated cancellation choice is kept until the Workflow confirms the ending. When requests are
  still open as the Workflow approves the ending, the Exchange stays active, `exchange.lifecycle.ended` is
  recorded with outcome `FAILURE` (`reasonCode`, `refusedRequestCount`), and the in-app notice
  `exchange.ending_refused` goes to the person who asked to end it and to the approvers; ending it again needs
  a new approval. The confirmation cancels only the requests whose cancel authority was checked when the
  ending was asked for: a request created after that refuses the ending in the same way, with
  `INFORMATION_REQUEST_REMAINING_REQUESTS_NOT_CANCELLABLE`.
- Exchange routes the requests rely on refuse in the same order: `POST /exchanges/{id}/rescind` answers `404` to
  a caller who cannot see the Exchange, as for an unknown id, and `403` to one who can see it but may not
  rescind; the `/exchanges/{id}/documents/{documentId}/versions` routes answer `404` for a hidden Exchange and
  `403` (previously `400`) when the caller can see it but lacks the document permission. A draft approval that
  starts the Exchange without recipient acceptance moves its requests on from the draft as acceptance does, so
  the clocks of requests issued while it was a draft start from the acceptance.
- `GET|POST .../successors`, `POST .../recurrences`, `POST .../recurrences/{recurrenceId}/occurrences`,
  `POST .../refresh-rules`, `POST .../refresh-rules/{ruleId}/refreshes`: follow-ups, which are new
  requests and need the owner's live plan. They remain available after the source request closes:
  a closed request still takes follow-ups, fact promotion, business decisions, and external-source
  records, while every other change to it is refused. A draft already takes its recurrence and refresh
  rules, but its successors, occurrences, and refreshes start only once it is issued; before that they
  are `409 INFORMATION_REQUEST_STATE_INVALID`.
    - A refresh rule names a requested document whose evidence policy captures the expiry date;
      otherwise it is refused with `INFORMATION_REQUEST_REFRESH_EXPIRY_NOT_CAPTURED`.
    - A refresh is due once the earliest expiry date the document states in the latest submission,
      less the rule's lead days, has arrived. Before then a refresh is refused with
      `INFORMATION_REQUEST_REFRESH_NOT_DUE`, and each submission is refreshed at most once per rule
      (`INFORMATION_REQUEST_REFRESH_ALREADY_CREATED`).
    - A scheduled pass (`app.information-request.refresh.every`) creates each due refresh as a system
      change, for a request in any state a follow-up can come from (an expired request too) whose
      Exchange is `INITIATED` or `ACCEPTED_STARTED` and not deleted; rules of any other Exchange are no
      longer tried. Each pass takes up to 50 due rules, first those never tried or whose retry time has
      come. A refresh that cannot be created yet (for example, the owner's plan no longer allows new
      requests) is retried 1 hour later, then after 2, 4, 8, and 16 hours, then every 24 hours, without
      holding up other rules. A refresh created by the pass or with `.../refreshes` clears the rule's
      retry state. A recurrence and a refresh rule cannot be changed or removed once defined.
    - `POST .../recurrences` and `POST .../refresh-rules` take `If-Match` with the request ETag. Each
      definition advances the request ETag and the `201` returns the new value in its `ETag` header.
      Retrying with the same Idempotency-Key returns the original definition with the request's
      current ETag, and needs view access to the request at the time of the retry (`403` otherwise).
    - Retrying `.../successors`, `.../occurrences`, or `.../refreshes` with the same Idempotency-Key
      returns the follow-up the command created, with the follow-up's current ETag, only while the caller
      can view the request the command was sent to, the request the follow-up recorded as its source,
      and the follow-up itself; otherwise it is `403`. Its `sourceRequestId` is that recorded source,
      never the series' current latest occurrence. A retry never creates another follow-up. Creating
      the next occurrence needs the supersede authority on both the recurrence's first request and
      its latest occurrence, and the next occurrence follows the latest one. `nextOccurrenceDueAt`
      counts every occurrence the series has created.
    - `GET .../successors` names a related request only to a caller who may view it: the request
      this one follows, and each follow-up, is left out when the caller may not view it, with its
      package, recurrence, refresh rule, reason and time, exactly as if it did not exist. The recurrence
      schedule belongs to the request itself and is always listed. Refresh rules are listed in full to
      a caller who manages follow-ups; anyone else sees only the rules of Requirements they may view. Because the next
      due time counts every occurrence, `nextOccurrenceDueAt` and `everyOccurrenceCreated` (true once
      a limited series has created all its requests) are given only to a caller who may view every
      occurrence the series has created; anyone else gets `null` and `false`, and creating the next
      occurrence then answers whether it is due, refusing with `INFORMATION_REQUEST_RECURRENCE_NOT_DUE`
      until it is. A limited series that has created all its requests refuses another with
      `INFORMATION_REQUEST_RECURRENCE_COMPLETE`.
    - An occurrence of a series also carries `series`, the recurrence it belongs to, when the caller may
      view the series' first request (`recurrence` stays the request's own schedule), and its due-time
      fields describe that series. `nextOccurrenceCreatable` is `true` when the next occurrence could be
      created now apart from its due time: the series is not complete, the caller holds the supersede
      authority on its first request and on the latest occurrence that can still be followed, and that
      occurrence's state and its Exchange permit a follow-up. The series continues from its latest open
      occurrence after its first request is cancelled or replaced; the next occurrence is still created
      through the first request's `.../recurrences/{recurrenceId}/occurrences`.
    - Each listed refresh rule carries `refreshDueOn`, the date its refresh falls due for the latest
      submission (that submission's earliest stated expiry less the lead days, or `null` while no
      submitted file of the document states an expiry date), `refreshDue` (due now and not yet
      refreshed) and `refreshed` (the latest submission was already refreshed under the rule).
    - A follow-up's `targetTemplateVersionId` must be the source request's Version or a later
      published Version of the same Template; any other Version is
      `409 INFORMATION_REQUEST_SUCCESSOR_TARGET_INVALID`. A follow-up copies the source's acting
      parties only where the sharing policy and trusted recipient rules still permit them, checked as
      the person who added each party (or, failing that, who created the source request or owns it);
      a party left out is listed by id in the successor audit event as `partiesNotCopied`.
    - A recurrence repeats every 1 to 1000 (`intervalCount`) days, weeks, months, or years, is first
      due from `1970-01-01T00:00:00Z` up to but not including `10000-01-01T00:00:00Z`, and, when
      `maximumOccurrences` is stated, stops after at least one request. A refresh rule's `leadDays` is
      zero or more. Anything else is `400 INFORMATION_REQUEST_FOLLOW_UP_SCHEDULE_INVALID` before
      anything is written. A request recurs once, and a requested document takes one refresh rule; a
      second is `409 INFORMATION_REQUEST_SUCCESSOR_SOURCE_INVALID`.

## Parties and access

- `GET|POST /information-requests/{id}/parties`, `POST .../parties/{partyId}/reassignment`,
  `POST .../parties/{partyId}/revocation`, `POST .../subjects`, `GET /information-request-subjects`.
  A refused party list answers like every party command, for example
  `403 INFORMATION_REQUEST_FORBIDDEN`. `GET /information-request-subjects` lists the owner's newest
  500 subjects; an older subject is named through its `reference`, which resolves to the existing
  subject. Parties report `trustSuspended`, and a subject party reports `processingRestricted` when the
  owner restricted processing for that subject, so a client offers no fact promotion for it. A group party, and a
  group's Share on the request or its
  Exchange, admits the members its Share materialized; a member who joined while the group's trusted
  relationship was suspended or no longer eligible is admitted only once that trust is eligible
  again, and is otherwise refused as any other caller without a grant. Assigning or reassigning a person, an email
  participant, or a
  group applies the owning organization's sharing policy, as Exchange sharing does; a refusal is
  `409 INFORMATION_REQUEST_PARTY_SHARING_NOT_PERMITTED` with the policy's reason, and a person or
  group that does not exist is `404`. A personally owned request checks only the group rules. A
  participant is checked by its address, as an email assignment is: as the account the address
  belongs to, or otherwise as an outside individual.
  The same rules apply to a Blueprint's default participants when a request is created from it,
  which is refused whole when any of them is not permitted. On an Exchange that requires recipient
  sign-in, an email assignment names the account of someone who already has access to the
  Exchange, its owner or a current recipient; any other address, whether or not it has an account,
  is `409 INFORMATION_REQUEST_PARTY_EMAIL_NOT_RECIPIENT` with one message, so the answer never tells
  whether an address has an account. A participant must exist and belong to
  the request's owner (`404` otherwise). A principal holds at most one active party in each role:
  assigning, assigning by email, or reassigning onto a principal that already holds the role is
  `409 INFORMATION_REQUEST_PARTY_ALREADY_ASSIGNED` before any capacity is reserved, while another
  role, or the same role after that party is revoked, is allowed. Blueprint default parties and
  follow-up copies keep one party per role and principal. Retrying an assignment, reassignment,
  revocation, or delegated authority grant or revocation with the same Idempotency-Key needs the
  caller to still manage parties (`403` otherwise). A role that answers a Field Requirement whose
  binding is not `PUBLIC` cannot be given to a principal outside the request's owner: a participant,
  a user who is not a member of the owning organization (or, on a personal request, anyone but its
  owner), or a group with any such active member. Assigning, assigning by email and reassigning are
  refused with `409 INFORMATION_REQUEST_FIELD_OUTSIDE_AUDIENCE` naming the Requirement; issuing, and
  amending to a Version whose Fields such a party could not answer, are refused the same way.
  Publication refuses a Requirement that provides a Field its Schema Version marks read-only, with
  `INFORMATION_REQUEST_TEMPLATE_INVALID` and the Requirement's key.
- A subject party names a Subject Identity and never holds a Share; `SUBJECT` is not a request Share role.
  A subject party that names an acting principal or no Subject Identity, or an acting party that names
  a Subject Identity or no principal, is `400 INFORMATION_REQUEST_PARTY_ASSIGNMENT_INVALID`, as are an
  email address named as the subject and a delegated authority for a principal that is not a user,
  participant or group. A trusted recipient whose Exchange access was withdrawn, or that is otherwise no
  longer assignable, is `409 INFORMATION_REQUEST_TRUSTED_RECIPIENT_UNAVAILABLE` at assignment,
  reassignment and issuance. A reassignment's history, audit event and domain event name the previous
  holder (`previousPrincipalKind`, `previousPrincipalId`) and the new one (`principalKind`,
  `principalId`). A subject named by its kind is created only once the request is locked, so a retry
  under the same `Idempotency-Key` waits for the first attempt and replays it.
  Reassigning a subject party is `409 INFORMATION_REQUEST_PARTY_NOT_REASSIGNABLE`, and reassigning or
  revoking a party that is already revoked is `409 INFORMATION_REQUEST_PARTY_ALREADY_REVOKED`.
  Parties created with a request (Blueprint defaults, follow-up copies, and successor subjects)
  record `ASSIGN_PARTY` and `information_request.party.assign` under the creating caller. Deleting or
  deactivating a group revokes each active party it holds on any request with reason `GROUP_REMOVED`
  under the system service account, ending the party's access links, sessions, and Shares; a group
  that still holds an active request Share is deactivated rather than deleted. Removing an
  organization member or a group locks the parent Exchanges of the affected requests in identifier
  order before any party is revoked.
- `GET .../parties/{partyId}/recipient-preview` returns, for a caller who manages parties (`403`
  otherwise), the questions one active answering party (Contributor, Preparer, or Attestor) is
  asked: `partyId`, `roleKey`, and a `templateVersion` holding only the Requirements that party may
  view, with only the condition rules that refer to them and no review stages. It uses the same
  party rule as response reads, so a Requirement that is not disclosed never appears, and a
  protected Requirement appears with its `confidentialityCompartmentKey`. A party of another
  request, or one no longer active, is `404 INFORMATION_REQUEST_NOT_FOUND`; any other role is `400`.
  It never changes what the caller's own `response-workspace` returns.
- `GET /information-requests/{id}/trusted-recipient-candidates` lists, for a caller who manages
  parties, the parent Exchange's accepted trusted recipients (`exchangeRecipientId`,
  `selectionType`, principal, label, target organization, `trustSuspended`); a personally owned
  request lists none. Assigning or reassigning a person or group that holds a trusted recipient
  binding on the parent Exchange always binds it and runs the trust checks, whether or not
  `exchangeRecipientId` is sent. Adding by email the address of a trusted person recipient accepted
  on the parent Exchange is `409 INFORMATION_REQUEST_TRUSTED_RECIPIENT_SELECTION_REQUIRED`; any other
  address is treated as an email participant. An address that belongs to an account is first checked
  against the owner's sharing policy as that account, as the Exchange email paths do: a member of
  another organization needs a trusted organization where one is required, and a suspended trust
  refuses it (`409 INFORMATION_REQUEST_PARTY_SHARING_NOT_PERMITTED`).
- `POST .../subjects` with a `reference`, and `POST .../parties` naming a `subjectIdentityRefId`,
  link the request to the owner's existing subject records, so they need
  `INFORMATION_REQUEST_MANAGE_PRIVACY` for the request's owner (a personal owner always has it) as
  well as party management; without it they are `403` before any reference or subject is looked up.
  A subject named by its kind alone needs party management only.
- `GET|POST /information-requests/{id}/access-links`,
  `POST .../access-links/{shareLinkId}/rotation`, `POST .../replacement`, `POST .../revocation`. A
  link issued or replaced without an expiry or use limit takes the configured defaults; an expiry
  that is not in the future or a use limit below one is
  `400 INFORMATION_REQUEST_ACCESS_LINK_LIMITS_INVALID`. Links are only for parties held by an email
  participant; any other party, including the subject party and a party whose request Share does not
  exist yet, is `409 INFORMATION_REQUEST_ACCESS_LINK_PARTY_NOT_ELIGIBLE`, and a removed party is
  `409 INFORMATION_REQUEST_PARTY_ALREADY_REVOKED` (revocation stays available). A link of another
  request answers `404` like an unknown link; a link of this request that is not an access link is
  `409 INFORMATION_REQUEST_ACCESS_LINK_INVALID`. Replacing a link and setting its expiry or use limit
  are available only through this API. A participant who linked an account (`accountLinked` on the party)
  gets no new, rotated, or replaced link (`409 INFORMATION_REQUEST_ACCESS_LINK_PARTY_ACCOUNT_LINKED`), and contact proof
  refuses any link
  of such a party with the same code. Issuing, rotating, and replacing a link give access, so they
  need the request's execution grant to be in force: after the grant is revoked they are
  `409 INFORMATION_REQUEST_EXECUTION_GRANT_REVOKED`, and while the owner is operationally suspended
  they are `403` with the plan refusal reason `SUBSCRIPTION_SUSPENDED`. Revoking a link only reduces
  access and stays available in both. Each command takes `If-Match` with the party ETag, advances
  the party revision, and returns the new party ETag in the `ETag` header and as `partyETag`; a
  replayed revocation returns the party's current ETag. Commands on a finished request or a terminated
  Exchange are `409`. Links record their verification strength, last use, and revocation time; a
  replaced link records its revocation time too. A
  response that carries the raw token (issue, rotation, replacement) is sent with
  `Cache-Control: no-store`.
- `POST /no-auth/information-request-access-links/challenges` and `.../sessions`: contact proof.
  Rate limited per client address (`429 INFORMATION_REQUEST_RATE_LIMITED`, with `Retry-After` stating the
  seconds left on the address's current pause). A code is emailed only after its challenge is committed,
  so no lock is held while it is sent; a delivery failure after commit is logged, and the participant
  asks for a new code. Contact proof and step-up are
  refused once the request is finished (`409 INFORMATION_REQUEST_STATE_INVALID`) or its Exchange no longer
  accepts changes (`409 INFORMATION_REQUEST_PARENT_STATE_INVALID`). Rejecting, rescinding, or deleting the
  Exchange revokes the request's access links together with its sessions, each recorded with the reason
  `PARENT_REJECTED`, `PARENT_RESCINDED`, or `PARENT_DELETED`.
- `POST /no-auth/information-request-access-links/sessions/current/step-up-challenges` sends a new
  code to the party's contact address (`204`), and `POST .../sessions/current/step-ups` with
  `{otp}` verifies it (`201`, `{sessionId, verifiedUntil}`). Both take the access link token and the
  session credential headers and share the contact proof address budgets. Refusals reuse the
  contact proof codes: `..._CONTACT_PROOF_REQUIRED`, `_EXPIRED`, `_INVALID`, `_LOCKED` (five invalid
  codes lock the session's step-up for five minutes), and `_CHALLENGE_LIMIT` (five unused codes per
  session). A session that does not belong to the link is `403`. Step-up never spends the link's
  uses.
- A Requirement with a non-null `confidentialityCompartmentKey` is protected: viewing or answering
  it needs recent verification. An access session counts as verified until
  `app.information-request.access-session.step-up-window` (default 15 minutes) after it was issued
  or last stepped up, and never past its own expiry. A signed-in caller needs a recent sign-in or
  step-up (5 minutes). The key is a label only; there is no per-party clearance. The same applies to
  a protected Requirement's imported values and discrepancies, accepted facts and fact offers, and
  review decisions, findings, and comments: lists leave them out, and a command on one answers
  `401 STEP_UP_REQUIRED` for a signed-in caller (`403` for an access session) only when verifying again
  would let the caller act on it; a caller who could not, even after verifying, is refused `403` like
  for an unknown item. An access-session
  command authorizes with the session's verification as it stands inside its own transaction, after
  locking the request, so a protected change sent after the window closed is refused even when the
  session was verified as the request arrived; other changes still succeed for the session's lifetime.
  A response save (`PATCH .../responses`, signed in or through an access link) naming a protected
  Requirement the caller could answer once verified again answers `403
  INFORMATION_REQUEST_CONFIDENTIALITY_DENIED` and saves nothing, so the client reloads and the workspace
  counts the item as awaiting verification; any other Requirement the caller may not answer still
  answers the plain `403`.
  A read whose window closes while it is built is built again without the protected items. A file
  download or preview authorizes again with the session's current verification once the stored file
  has been read, so a protected file whose window closes while it is read answers `403` and is
  neither sent nor recorded as opened, while an unprotected file is still released.
- While the parent Exchange requires recipient sign-in, issuing, rotating, or replacing an access
  link, contact proof, and every access session are refused with `409`
  `INFORMATION_REQUEST_RECIPIENT_SIGN_IN_REQUIRED`. Turning the setting on, including by replacing
  the primary recipient with a trusted selection, revokes each access link and its sessions on the
  Exchange's requests and records `information_request.access_link.revoke` with reason
  `RECIPIENT_SIGN_IN_REQUIRED` under the person who changed it.
- Every `/no-auth/` command checks its access session again inside its own transaction, after it
  locks the parent Exchange and the request and before it looks up its `Idempotency-Key`. A session
  revoked or expired, a link revoked or expired, a party no longer bound to the link, or recipient
  sign-in required since the session was first checked refuses the command with the same `409`
  reason as an ended session (`..._ACCESS_SESSION_REVOKED`, `_ACCESS_SESSION_EXPIRED`,
  `_ACCESS_LINK_REVOKED`, `_ACCESS_LINK_EXPIRED`, or `_RECIPIENT_SIGN_IN_REQUIRED`), and nothing is
  recorded. A `/no-auth/` read checks the session again before it answers and is refused the same
  way, and a first view is not recorded for a session that has ended. A file download or preview
  checks the session again after the stored file has been read and before the opening is recorded or
  any of the file is sent; a refusal closes the file and records nothing. A command recorded before
  its session ended replays under its key through a newly verified session of the same participant.
- `POST /information-requests/{id}/participant-account-links` (signed-in account holders only,
  `403` otherwise) takes `{sessionId}` with the `X-Request-Session-Token` of that session, which
  must match it (`403`); a token whose secret does not match its session is
  `409 INFORMATION_REQUEST_ACCESS_SESSION_REQUIRED`. The token's secret is checked before anything is
  locked, and the parent Exchange and session are then locked inside the command's own transaction. A
  session of another request answers `404 INFORMATION_REQUEST_NOT_FOUND` like an unknown request, before
  that request is locked or its state is examined. A repeated link answers only while the party is active
  and its account Share is in force; otherwise it is `409 INFORMATION_REQUEST_PARTY_ALREADY_REVOKED`. Linking ends the
  session, yet repeating
  a recorded link with the same `Idempotency-Key`, session token, and signed-in account returns the
  recorded link with the party's current ETag. The ended session cannot start anything else: another key is
  `409 INFORMATION_REQUEST_ACCESS_SESSION_REVOKED`, and another account under the same key is
  `409 COMMAND_RECEIPT_FINGERPRINT_CONFLICT`. It returns the new party ETag in the `ETag` header.
  It is refused with `409` on a finished request or a terminated Exchange,
  `INFORMATION_REQUEST_CONTACT_PROOF_REQUIRED` while the participant's contact address is
  unverified, and `INFORMATION_REQUEST_TRUST_SUSPENDED` while one of the account's organizations
  has a suspended trust relationship with the owner. Linking is per participant: every active party the
  participant holds on the request gets an account Share and is linked, the access links and sessions of
  each end, and the participant itself no longer acts for them. Linking is also refused with
  `409 INFORMATION_REQUEST_PARTICIPANT_ACCOUNT_ACCESS_CONFLICT` while the account holds any other live
  direct Share on the request, with `409 INFORMATION_REQUEST_PARTY_SHARING_NOT_PERMITTED` when the
  owner's sharing policy does not admit the account or its organization, with
  `409 INFORMATION_REQUEST_RECIPIENT_SIGN_IN_REQUIRED` once the Exchange requires recipient sign-in (a
  manager then reassigns the participant's parties to an Exchange recipient), and with `403` and the
  plan refusal reason `SUBSCRIPTION_SUSPENDED` while the owner is operationally suspended. The answer's `respondentKey`
  names the linked
  account as a respondent, the same key its signed-in `response-workspace` returns; a client moves
  the participant's unsaved answers to it.
- `GET|POST /information-requests/{id}/delegated-authorities`,
  `POST .../delegated-authorities/{authorityId}/revocations`. Listing needs the request's
  administration (the Exchange owner or a Decision Maker), answers with the parties ETag, and stays
  available after the request finishes or its Exchange ends. Grants and revocations need party
  management, lock the parent Exchange, and are refused with `403` once the request has finished or
  its Exchange has ended, because party management ends with them; a grant also needs the request's
  execution grant to be in force. Reassigning, removing, or member-removal revocation of a party
  revokes every delegated authority it held, recorded with the party change's reason.
  The delegate may be a user, a participant, or a group, and must exist and pass the same owner and
  sharing checks as a party: a delegate that does not exist is `404`, and a sharing refusal is
  `409 INFORMATION_REQUEST_PARTY_SHARING_NOT_PERMITTED`. The delegate itself, the account linked to
  a delegated participant, and each active member of a delegated group (with a member
  participant's linked account) act for the assigned party within the authority's Requirement
  scope and period, for answers, files, and confirmations. An active authority is itself the
  delegate's access: through the central authorization service it lets the delegate read the request
  and act on the named Requirement, or on every Requirement the party answers, with the party's role,
  and nothing more (it cannot submit). A member removed from the group loses the authority at once.
  A grant is refused with `409 INFORMATION_REQUEST_DELEGATED_AUTHORITY_PARTY_INELIGIBLE` for a party
  whose role answers nothing (Subject, Reviewer, Decision Maker) and with
  `409 INFORMATION_REQUEST_DELEGATED_AUTHORITY_REQUIREMENT_INELIGIBLE` for a Requirement the party
  neither answers nor confirms. Delegated authority has no screen; these routes manage it.

Every request audit record names its actor explicitly: the canonical principal, an audit actor
kind, a label, and the access session. Signed-in callers are labelled `Full Name <email>` (or their
email) with their user session; access-link respondents are recorded with actor kind `PUBLIC_LINK`,
the label `participant:<participant id>`, and their request access session; system work (clocks,
notice delivery, file scans, scheduled refreshes, connector exchanges) names the platform service
account with the label `service-account:<id>` and no session. Changes an Exchange owner causes on the Exchange
(rejecting,
rescinding, deleting, ending, requiring recipient sign-in) carry the owner's session. Request transitions caused by an
access-link recipient's decision name that recipient, an approved ending names the person who asked to end the
Exchange (or the approver when no ending request was stored), and approval Workflow outcomes name the deciding
approver. Workflow step, outcome, escalation and failure events name their owner: the organization, the owner of a
personal Workflow (owner kind `USER`), or the platform. Each request
transition records the Idempotency-Key and command receipt of the command that made it; a
transition made outside a command (schedulers and cascades) records the key
`system|<mutation>|<request id>|<sequence>` and no receipt. A transition made inside a command is
audited under that command's receipt and the transition, so different callers, operations, and
requests that share an Idempotency-Key each keep their own audit record; a retry replays the
command and adds none.

Delegated authority grants and revocations record `information_request.delegated_authority.grant`
and `information_request.delegated_authority.revoke`, respectively. The authority change, party
revision, immutable request history, audit intent, event intent, and command receipt commit
together. Audit payloads identify the authority, party, and optional Requirement and exclude
instrument references and free-text revocation reasons. A failure to capture the required audit
or event refuses and rolls back the command. Identical retries add no duplicate records.

Explicit access-link commands record `information_request.access_link.issue`, `.rotate`,
`.replace`, and `.revoke`. Each credential change, session revocation, immutable request history,
audit intent, event intent, and receipt commits in one transaction. The correlated records carry
the canonical actor, access session, party, link, and replacement link reference, with expiry,
use limit, and rotation count; tokens, token hashes, contact codes, and contact addresses are
excluded. Audit or event capture failure rolls back the change. Retries recheck current management
authority and add no duplicate records. An issuance, rotation, or replacement retry cannot return
the secret again and reports `ACCESS_LINK_ALREADY_ISSUED`, `ACCESS_LINK_ALREADY_ROTATED`, or
`ACCESS_LINK_ALREADY_REPLACED`. Repeated revocation returns the current scoped link without
another mutation record.

Automatic credential cutoffs are recorded in the same transaction as the change that causes them.
Revoking or reassigning a party, and revoking it when an organization member is removed under a
revoking ownership policy, records `information_request.access_link.revoke` for each access link
the party still held, with reason `PARTY_REVOKED`, `PARTY_REASSIGNED`, or
`ORGANIZATION_MEMBER_REMOVED`. The member-removal path also records
`information_request.party.revoke` with `ORGANIZATION_MEMBER_REMOVED`. Rejecting, rescinding, or
deleting the parent Exchange records `information_request.access_session.revoke` for each live
access session it ends, with reason `PARENT_REJECTED`, `PARENT_RESCINDED`, or `PARENT_DELETED`,
before any cancellation it applies. A request cancelled because its Exchange ended has its access
links revoked in the same transaction, recorded with reason `REQUEST_CANCELLED`; a session already open
keeps reading until it expires. Ending, rejecting, rescinding, or deleting the parent Exchange
also gives each Exchange owner without a Share on the request a read-only `RECORD_OWNER` request
Share, whatever kind of owner the request has, so the owner keeps reading the request, its
Requirements, and its evidence while every change stays refused. `RECORD_OWNER` is not a party role;
assigning it to a party is `400`. The records name the canonical caller and the caller's access
session when the change came from a command, and the system service account when it did not. They
carry the affected party and the link or session identifier; tokens and credential hashes are
excluded. A failure
to capture the required audit or event rolls back the causing change. Repeating the change adds no
record, because nothing is left to cut off.

Contact proof records `information_request.contact_challenge.issue` for every code sent, also after an earlier
verification,
`information_request.contact_challenge.refuse` (reason `CONTACT_PROOF_INVALID`, with the attempt
number and, on the attempt that locks verification, `lockedUntil`) for each refused code, and
`information_request.access_session.issue` for the session a verified code opens. These records
name the party's canonical principal with audit actor kind `PUBLIC_LINK`; the session issuance also
carries the new session as its access session. A refused code's record commits with the attempt
count even though the call is refused. Contact proof locks the parent Exchange and request before
the access link, in the same order as the access-link commands. A participant account upgrade
records `information_request.participant_account.link` and an
`information_request.access_link.revoke` with reason `PARTICIPANT_ACCOUNT_LINKED` for each link it
ends, under the registering account and the request access session that proved contact. Contact
codes, their hashes, tokens, session secrets, and contact addresses are never recorded. A failure
to capture any of these records refuses the call and rolls back its changes.

Step-up records `information_request.contact_challenge.issue` with reason
`ACCESS_SESSION_STEP_UP` when a code is sent, `information_request.contact_challenge.refuse` for each
refused code, and `information_request.access_session.step_up` (with `verifiedUntil`) when a code is
verified. Each names the party, carries the session as its access session, and carries
`accessSessionId`; a refused code's record commits with the attempt count. A failure to capture a
step-up record leaves the session unverified.

Each acting party holds its own Share, even when one principal holds several parties on a request;
revoking or reassigning a party ends only that party's Share. A participant account upgrade stores
the account Share it grants on the party. Revoking or
reassigning the party, including revocation on member removal, revokes that Share with the party's
own Share, and reassignment clears it. A Share another active party on the request still holds is
kept, together with its access links. An upgrade is refused with `409`
`INFORMATION_REQUEST_PARTICIPANT_ACCOUNT_ACCESS_CONFLICT` while the account already has active or
pending access to the request, so an upgrade never changes the role of a Share someone else uses.

## Responding

The same operations exist under `/information-requests/{id}` for signed-in parties and under
`/no-auth/information-requests/{id}` for access-link sessions. An access link and its session open
only the request the link was issued for: a route naming another request answers `404`
`INFORMATION_REQUEST_NOT_FOUND`, and the refused call does not count as a use of the session. A
session presented with a link it does not belong to stays `403`. A signed-in caller who may not
view a request is refused with `403` whether or not it exists, so an unknown request id answers the
same as a request hidden from the caller; on the `/no-auth/` surface any request other than the
link's own answers `404 INFORMATION_REQUEST_NOT_FOUND`. Other lifecycle refusals answer `409` with
their reason code.

Inside a request the caller may open, a target it names is checked the same way: a Requirement, a file
of a Requirement, a repeatable group, a group occurrence, a file a submission holds, or a reviewed
item that the caller may not see answers exactly as one that does not exist, with `403` and the same
body, before its membership, removal, state, lock, or count is checked. A response patch checks every
Requirement it names, including those in `confirmedHiddenResponseClearRequirementIds`, so an id the
caller may not see saves nothing whether or not it exists. A caller who may see a target still gets
the specific refusal for it, such as `GROUP_OCCURRENCE_REMOVED`, `EVIDENCE_NOT_COLLECTED`, or
`SUBMISSION_LOCKED`.

- `GET .../response-workspace` (includes `executionStanding` and `requirementsAwaitingStepUp`, the
  number of active protected Requirements the caller could see after verifying again),
  `PATCH .../responses`.
    - `?purpose=MANAGEMENT` reads the same workspace for a management page: it is `403` for a caller
      who cannot view the request's operations (`INFORMATION_REQUEST_VIEW_OPERATIONS`) and never records
      the respondent's first view, even when the caller also holds a responding role. Without a purpose,
      or with `RESPONSE`, the read records a responding party's first view as before; any other purpose
      is `400`. The `/no-auth/` surface takes no purpose.
    - The workspace carries `authorActions`, the actions on the request a manager's page may offer:
      `issue`, `cancel`, `supersede`, `changeCompletionGate`, `manageParties`, `previewRecipients`,
      `manageClocks`, `scheduleFollowUps`, `createFollowUps`, `promoteFacts`, `revokeFacts`, and
      `recordDecisions`. Each is `true` only when the caller's authorization and the current state of
      the parent Exchange and of the request permit that action, so after the Exchange ends only
      `revokeFacts` stays `true`, and on a deleted Exchange none does. A draft's state permits
      `supersede` and `scheduleFollowUps` as well as `issue` and `cancel`; it permits `createFollowUps`,
      `promoteFacts`, `revokeFacts`, and `recordDecisions` only once the request is issued. The owner's
      execution standing is not folded in; it stays in `executionStanding`, which a client applies as
      well. Every value is `false` for a caller who does not manage the request. `issueAccessLinks` is
      `manageParties` while the parent Exchange does not require recipients to sign in, so a client offers
      Create link for a participant party only then.
    - The workspace carries `creatableGroupKeys`, the keys of the repeating groups the caller may add
      an entry to, judged by the same authorization the add command applies. A client offers Add only
      for these keys; the key list is empty, or left out, when the caller may add to none. Removing
      or moving an entry is still judged when it is asked for, so a client offers those for every
      entry it shows and reports the refusal.
    - The workspace carries `exchangeAccepted`, `false` while the parent Exchange is still a draft (`INITIATED`). Every
      response, evidence, and submission command is then refused with
      `409 INFORMATION_REQUEST_PARENT_STATE_INVALID`, so a client shows the request read-only and says that
      answers wait for the Exchange to be accepted.
    - Each response carries `editLock` when a patch to it would be refused: `SUBMITTED` (its
      submission must be withdrawn first), `NOT_RETURNED_FOR_CORRECTION` (an open correction did not
      return it), or `GIVEN_ONCE` (an answer given once was submitted). A Document response carries
      `evidenceFileCount`, the number of active files attached to it, when the caller may view or
      manage its evidence. A response that needs reconfirming after an amendment is confirmed by a
      patch that names its current disposition and nothing else, under the current response ETag.
    - The workspace's `respondentKey` is an opaque key for the respondent it was read for: the same
      through a renewed session or a newer link for the same recipient, and different for the
      recipient a party is reassigned to and for every other account. A client keeps unsaved answers
      under the request and this key, never under the request alone.
    - Carry-forwards, accepted fact offers, amendments, review results, and the submission preview
      with its `canSubmit` are read for the caller too and can differ between respondents while the
      response ETag stays the same. When a reread returns another `respondentKey`, a client discards
      everything it read or kept for the earlier key, including a file kept to send again and any
      answer still arriving for it, and reads these again. Evidence content still being read for the
      earlier key is neither previewed nor saved.
    - A patch may name the `respondentKey` whose unsaved answers it carries. When that is not the
      caller, for example after the party was reassigned or another account signed in, nothing is
      saved and the refusal is `409 INFORMATION_REQUEST_RESPONDENT_CHANGED`, before any recorded
      command is replayed. A patch that names no key is not checked.
    - A client keeps a command's `Idempotency-Key` until the command is answered, including through
      a newly verified session of the same respondent, so a retry after a lost answer replays the
      command, answering with the current state and its ETag, instead of meeting `412` with the
      earlier ETag. This applies equally to saves,
      adding, removing, and reordering entries, submissions, confirmations, and withdrawing a
      submission or a file. A changed command, or the same one after an answer, gets a new key.
    - When the Template requires confirmed clearing and a patch would hide answers that hold data,
      nothing is saved. The refusal is `409` with
      `INFORMATION_REQUEST_HIDDEN_RESPONSE_CLEAR_CONFIRMATION_REQUIRED` and `hiddenResponseClears`,
      one `{requirementId, sourceTemplateBindingId, occurrencePath}` for each answer the patch would
      clear. Send the patch again with those ids in `confirmedHiddenResponseClearRequirementIds`. If
      clearing them hides further answers, that attempt is refused the same way for those answers.
    - A condition never clears or archives an answer the caller cannot respond to, or one a submission
      still holds: that answer is kept as under `RETAIN_SECURELY` and is not listed for confirmation.
      Confirming the clearing of such an answer is refused: with `403` and the body an unknown Requirement
      gets when the caller may not see it, with `409 INFORMATION_REQUEST_HIDDEN_RESPONSE_CLEAR_NOT_PERMITTED`
      and without naming the answer when the caller may see it but another party gives it, and with
      `SUBMISSION_LOCKED` or `CORRECTION_SCOPE_DENIED` when a submission holds it.
    - A patch or an upload that addresses a Requirement its condition hides at that occurrence is refused
      with `409 INFORMATION_REQUEST_REQUIREMENT_HIDDEN_BY_CONDITION`, and the evidence list of such a
      Requirement shows no files. A file a condition clears or archives is withdrawn through the evidence
      collection and recorded as an evidence transition with the reason `hidden-by-condition` or
      `archived-by-condition`.
    - A Template can instead archive hidden answers: the answer is copied to an append-only archive and
      cleared from the live response without confirmation, and it is not restored when the condition
      holds again. Its active files are withdrawn from the collection with the reason
      `archived-by-condition`, and the archive names the exact version of each; an answer that has
      only files is archived the same way. Withdrawn files keep their versions, and earlier
      submissions keep the files they froze. A retained answer is hidden and restored intact, with
      its files. Every hide, clear, archive, and
      reactivation is recorded in the `information_request.requirement.respond` audit payload (`conditionEffectCount`,
      `conditionEffects`).
    - A draft may clear a Field its Schema marks required; requiredness applies at submission.
    - A save names at most 200 answers, each note is at most 4000 characters, and a save carries at most
      500 Field values; anything more is `400` before anything runs. An answer saved as one that carries no
      value (`NOT_APPLICABLE`, `UNAVAILABLE`, `EXCEPTION_REQUESTED`, `SATISFIED_BY_REFERENCE` or `WAIVED`)
      without Field values clears the Field value given earlier, keeping its revision history. The Field
      projections a save returns reflect any value its conditions cleared in the same Field Value Set, so
      their ETags are current.
    - Within one save, answers a condition brings back are restored before any hidden answer is cleared or
      archived, so removals follow the settled conditions; an answer cleared earlier in the save is
      restored too when its condition then holds. An amendment re-evaluates every hidden answer, also under
      an unchanged rule key, and restores one whose condition now holds. Hiding and restoring a retained
      answer leaves its `responseRevision` as it was, so an answer returned for correction stays returned
      until it is changed. A save's audit payload names the answered Requirements in
      `answeredRequirementIds`, and adding, removing and reordering occurrences record `occurrenceChange`
      (`ADD`, `REMOVE`, `REORDER`), the `groupKey`, and the occurrence's id and path (or the new order).
    - A workspace read by a caller who does not manage the request leaves out the Template Version's
      `schemaVersionId`, `contentHashSha256` and `factReusePurposeKey`; the recipient preview does the same.
    - An answer to a Requirement whose response mode is `PROVIDE_ONCE` can change until a submission
      first holds it while it is shown. Afterwards a save or file change is
      `409 INFORMATION_REQUEST_SUBMISSION_LOCKED`, even once that submission is withdrawn, unless an
      open correction explicitly returns it. A correction that returns other answers does not reopen
      it, even when the latest submission held it as `HIDDEN`, and its Requirement's correction scope
      is then excluded. A condition never clears or archives such an answer: the save goes through and
      keeps it hidden as under `RETAIN_SECURELY`. Confirming its clearing is refused the same way,
      naming the answer, or with `INFORMATION_REQUEST_CORRECTION_SCOPE_DENIED` under a correction that
      returned only the controlling answer. Removing a group occurrence that
      holds such an answer is refused the same way; adding and reordering occurrences are not
      affected. A confirmation with that mode can be given again after withdrawal, or under a
      correction that does not return it, only with the decision its role gave in a submission
      (`409 INFORMATION_REQUEST_SUBMISSION_LOCKED` otherwise). An item that no submission held while
      it was shown was never answered, so it stays open.
    - Files of a Document Requirement its condition hides are not frozen into a submission (the item
      is `HIDDEN`). Under confirmed clearing they are listed in `hiddenResponseClears`, and confirming
      withdraws them with the state reason `hidden-by-condition`.
- `POST .../group-occurrences`, `PATCH .../group-occurrences/order`,
  `DELETE .../group-occurrences/{occurrenceId}`. Each answers with the affected group's siblings
  that the caller may see. Removing or reordering occurrences needs respond access to every Requirement
  the affected occurrences and their nested occurrences hold, and to the group's own Requirements (with
  those of nested groups that must have an occurrence); a nested optional group answered by another
  role does not block it while none of its occurrences hold that role's Requirements. A new occurrence
  never reuses the path of an earlier one, removed or not, also after an amendment. The response
  workspace also lists the enclosing occurrences of a group the caller may add to, without their
  Requirements, so a nested group can be added under them. A group key or occurrence the caller may not change answers
  `403` with the
  same body as one that does not exist, before the group's lock, parent, order, or occurrence count is
  checked; an occurrence the caller may change that was already removed stays `404`, and a parent that is
  not an active occurrence of the group's parent group is `409 INFORMATION_REQUEST_GROUP_OCCURRENCE_PARENT_INVALID`
  with one message whether or not the id exists. A retry under the same `Idempotency-Key` rechecks the parent and
  request
  state, the owner's continuation, and the caller's respond access, and answers with the current
  visible siblings and the current response ETag.
- A refused patch or occurrence change about specific items is `409` with an item refusal body:
  `errorMessage`, `reasonCode`, `problems` (each with `code`, `message`, and where known
  `requirementId`, `occurrencePath`, `fieldContractId`, `validationKind`), and
  `undisclosedProblemCount` for items the caller cannot see. It is a superset of the plain refusal
  body. A Field change that conflicts with the stored Field values is
  `INFORMATION_REQUEST_FIELD_PRECONDITION_CONFLICT`. The submission preview reports
  `STRUCTURED_VALIDATION_FAILED` for answers that fail the Template's structured checks, and a
  returned Field not revised since its submission blocks submission again until it changes.
- A participant's workspace omits review stages (`reviewStages` is empty); people who manage the
  request see them.
- `GET|POST .../requirements/{requirementId}/evidence-artifacts`,
  `GET .../evidence-artifacts/{artifactId}`, `POST .../evidence-artifacts/{artifactId}/removals`, `POST .../versions`,
  `GET .../versions/{versionId}/content`, `GET .../versions/{versionId}/preview`,
  `POST .../withdrawals`. Removal and withdrawal accept a JSON body with an optional nonblank `reason` of at most
  512 characters; invalid reasons answer `400 INFORMATION_REQUEST_EVIDENCE_REQUEST_INVALID`. Reasons are never taken
  from the URL.
  Uploads draw on the grant's evidence allowance beneath the platform ceiling.
  An upload whose declared size is over the file limit plus 64 KiB answers `413` with
  `INFORMATION_REQUEST_EVIDENCE_UPLOAD_LIMIT_EXCEEDED` before the body is read; the refusal names the file
  limit in KiB, MiB, or GiB, as the record export size refusal does. An upload that declares no
  `Content-Length` answers `411 INFORMATION_REQUEST_EVIDENCE_UPLOAD_LENGTH_REQUIRED` before its body is accepted. A
  received file over
  the file limit, or one that would exceed a request or respondent allowance, answers `409` with the
  same reason before it is hashed or stored. A respondent allowance counts every stored version
  uploaded by the caller, by the account a participant caller linked, or by any participant linked to
  the calling account, so linking an account never starts a new allowance; each version keeps the
  identity that uploaded it. Every evidence version is an uploaded file (`sourceKind`
  `DOCUMENT_VERSION`), so a malware scan, inspection, and the total size limit apply to every version.
  An upload declaring `encryptionMode` `END_TO_END` is refused with `409`
  `INFORMATION_REQUEST_EVIDENCE_OPAQUE_CONTENT_REFUSED` before anything is stored, because its type
  could not be checked and it could not be scanned.
  An evidence route answers a signed-in caller who may not view its request with `403`, whether or
  not the request exists, and a caller who may neither view nor manage the files of the Requirement it
  names with `403` and the same body, whether or not that Requirement exists, before the Requirement,
  the file, or its lock is looked up; the item and file routes of a Requirement hidden by its condition answer `404`. A
  file still being scanned counts toward a minimum file count
  while it awaits its checks. Upload refusals keep their codes and state the limit that applied. A retried upload or
  state change never shows a file a manager has since removed to a
  caller who may not manage evidence; such a retry answers `404 INFORMATION_REQUEST_NOT_FOUND`.
  A file belongs to the party that provided it: replacing, withdrawing, or removing it as a
  respondent needs the caller to act for that party (as its holder, a member of its group, its
  delegate, or the account its participant linked), otherwise
  `409 INFORMATION_REQUEST_EVIDENCE_PROVIDED_BY_ANOTHER_PARTY`; once the person who provided it no
  longer acts for any party that answers the Requirement, any party that may answer it can change
  it, and a caller who manages evidence can always remove it. Each artifact reports
  `changeableByCaller`. These routes serve the Requirement's current collection only: an occurrence
  that was removed answers `409 INFORMATION_REQUEST_GROUP_OCCURRENCE_REMOVED`, and a Requirement the
  request's current Version no longer has answers `404`. A file a submission froze is read through
  that submission instead (see below). A reviewer's client opens content it read only while the
  review still shows that request, package, item, and exact evidence version; content arriving after
  that is discarded. A
  respondent's client opens content only while its evidence panel still lists that artifact with
  that version as its latest; content arriving after a refused reread, or after the file was
  replaced, is discarded. A refused `content` or `preview` read answers the usual JSON refusal
  body, labelled with the route's `*/*` type rather than `application/json`; the web client reads
  the body of any refused file request as JSON, so an ended access session returns the respondent
  to verification.
- `POST .../requirements/{requirementId}/attestations` needs the exact submission ETag.
  A confirmation covers the answers, files, notes, and the inspection and malware assessment of
  each file, so a change to any of them moves the submission ETag.
- `GET .../submission-preview` reports `correctionOpen` for the previewed scope and for each stage.
  A scope returned for correction can be confirmed and submitted again, and an unnamed preview
  opens on it first. Each confirmation reports `callerCanAttest` and `waitingForRoles`: under role
  order, the earlier roles that must assent before the caller can (refusing is not ordered, and an
  early assent is `409` `INFORMATION_REQUEST_ATTESTATION_ORDER_UNMET`). `attestingParties` lists the
  parties the caller may attest as (`partyId`, `role`, `throughGroup`, `delegated`); when it holds
  more than one, the attestation names one in `partyId`, or it is refused with `409`
  `INFORMATION_REQUEST_ATTESTATION_PARTY_AMBIGUOUS`.
  `groupProblems` lists each repeatable group in the previewed scope whose active occurrences under
  one parent fall outside its bounds (`groupKey`, `parentOccurrencePath`, `minOccurrences`,
  `maxOccurrences`, `occurrenceCount`), checked from the group configuration rather than from the
  Requirements that happen to exist. A group problem is named only to a caller who could add an
  occurrence to that group; others are counted in `undisclosedProblemCount`. Submitting with one is
  refused with `422 INFORMATION_REQUEST_SUBMISSION_INCOMPLETE`, whose body carries the same list.
- `GET|POST .../submissions`, `GET .../submissions/{packageId}`,
  `POST .../submissions/{packageId}/withdrawal`. Submitting needs the exact submission ETag. Under
  sequential stage ordering, a stage is withdrawn only after every later submitted stage, otherwise
  `409` `INFORMATION_REQUEST_SUBMISSION_NOT_WITHDRAWABLE`. A submission that answers a correction
  cannot be withdrawn and is refused the same way. Each package reports `current` (false once a later
  submission replaced it) and `withdrawable`; withdrawing a replaced package is refused the same way
  with a message that a later submission replaced it.
- `GET .../submissions/{packageId}/evidence/{versionId}/content` and `.../preview` read a file that
  submission froze, by its exact evidence version. They keep working after the submission is
  withdrawn, its occurrence is removed, or an amendment removes its Requirement. A version the
  submission does not hold, or a submission of another request, is
  `404 INFORMATION_REQUEST_NOT_FOUND` for a caller who may open every file of that submission; any
  other caller gets the `403` a file it may not open gets, with the same body. The caller needs the request's read
  access and either the
  permission to view or manage the evidence of the submitted item's Requirement, or the right to read
  that item in a review of this submission: review reading on the request and on the item's
  Requirement, the same check that shows the item's content in the review, while a review of the
  submission exists. Both carry the Requirement's confidentiality and recent-verification rules (`403` otherwise). A
  file a manager removed stays hidden from a caller who may not manage evidence (`404`). The Requirement's own evidence
  routes keep evidence view to the assigned party. The stored content
  must still match the identity and digest the submission froze, and its own digest, otherwise
  `409 INFORMATION_REQUEST_EVIDENCE_CONTENT_UNAVAILABLE`; quarantine and malware-scan release apply
  as for any file. Headers, refusal bodies, the preview rule, and the access session recheck are those
  of the current collection's content routes. Each read is recorded as an evidence download or
  preview whose payload also names `submissionPackageId` and `submissionItemId`. The review
  workspace opens submitted files this way.
- Preview `canSubmit` and `callerCanAttest` reflect current continuation rights, including grant
  revocation and operational suspension; SUBMIT and ATTEST are checked independently. Hidden
  assertions are omitted, cannot be attested, and freeze no attestations into a submission.
  Repeating a still-counted assent for the same party, content and signature reference returns
  the existing assent without changing the role sequence. Partial provision never completes an
  assertion; an allowed exception disposition needs a nonblank narrative.
- Withdrawal is an explicit recovery right of any caller authorized to submit or manage parties;
  the caller need not be the original submitter. Stage ordering, review and parent-state limits
  still apply. Amendment and withdrawal `reasonCode` values are limited to 128 characters;
  malformed codes answer `400 INFORMATION_REQUEST_REASON_CODE_INVALID` before persistence.
- Meaning changes require reconfirmation of existing Field values and active files as well as
  dispositions and notes, including file-only answers without a response row. Newly added
  Requirements do not reconfirm earlier answers. Amendment notices use current party equivalence,
  so eligible group members and linked accounts see their party's notices and delivery states.
- Privacy corrections use the same NOT_FOUND refusal and message for unknown submitted items
  and items outside the subject or owner scope.
- An amendment of a draft owes no notices, since its parties have not been sent the request; the
  amendment's `notices` is empty. An amendment of an issued request owes each active party one.
- `GET .../amendments` (plus `POST` for managers), `GET .../carry-forwards`,
  `GET .../accepted-fact-offers`, `POST .../accepted-fact-offers/{factId}/recertifications`,
  `GET .../review-results`, `POST .../reviews/{reviewId}/appeals`, `POST .../reviews/{reviewId}/comments`.
  Each review result lists `items`, the submitted items whose Requirement the caller may view (each
  `submissionItemId` and `requirementId`), so a respondent can comment on any of them once the review
  is decided; respondent-visible comments that belong to no finding are listed in `comments` with
  their item. Assigning a reviewer settles the review when its stages already hold enough decisions,
  as recording decisions and changing an assignment do.
  A respondent's comment on an item it may not see, or on an item the review does not hold, answers
  `403` with the same body, before whether the review is decided is checked; a respondent is offered
  recent verification only for a protected item it may see once verified. A finding or comment a
  respondent may not see, or one that does not exist, is refused with the same
  `409 INFORMATION_REQUEST_REVIEW_COMMENT_NOT_PERMITTED` and message.
  A carry-forward entry is listed only while the caller may view both the follow-up's Requirement
  and the earlier Requirement it came from, under their current Shares, assignment, and the recent
  verification a protected Requirement needs. Other entries are left out, and the follow-up's
  Requirement can still be answered. An accepted fact is offered only for a Requirement whose Template
  binding permits a provided answer, and only while its value is still valid under the Field contract
  the request asks for it in. A fact kept to the requesting side (`REQUESTING_SIDE`) is offered to nobody,
  since a reused answer becomes part of a response the responding parties see; only `RESPONDING_PARTIES`
  facts are offered.
- An amendment that raises a group's minimum adds the missing occurrences, with the minimums of the
  groups nested in them, under each active parent occurrence. Existing occurrences, their answers,
  and removed occurrences keep their identities; a new occurrence never reuses a removed
  occurrence's path. A changed group bound changes the meaning of every Requirement anchored in
  that group or in a group nested beneath it, so it asks those answers to be confirmed again and is
  refused with `409 INFORMATION_REQUEST_AMENDMENT_SUBMITTED_SCOPE_CHANGED` when any of those
  Requirements belongs to a submitted stage. An amendment that changes what a Requirement asks, or
  stops asking it, is refused the same way when its answer is given once and was submitted, also in a
  submission since withdrawn, unless the answer was returned for correction; rewording it is accepted.
- A request's Schema can be assigned only while it is a Draft; creation assigns it when the
  Version has a Field Requirement. An amendment whose target Version has a Field Requirement, on a request that has no
  Schema assignment, is refused with `409 INFORMATION_REQUEST_AMENDMENT_SCHEMA_CHANGED`; use a
  supplemental or superseding request instead. On a request that has a Schema assignment, every
  amendment gives each active occurrence its Field value set, including occurrences added while the
  Version asked for no Field answers, so their Field answers can be saved once Field Requirements
  return.

## Reviewing and outcomes

- `GET .../reviews`, `GET .../reviews/{reviewId}`, `POST .../assignments`,
  `POST .../assignments/{assignmentId}/decisions`, `.../delegation`, `.../recusal`, `.../revocation`,
  `PATCH .../worksheet`, `POST .../findings`, `.../overrides`, `.../reconsiderations`.
  The two reads answer the request's reviewers, with item content as described below, and the
  request's administration (the Exchange owner or a Decision Maker), who see stage standing,
  outcomes, and assignments without item content. Reading continues after the request finishes and
  after its Exchange ends; a reviewer loses it with their Share or once the Exchange no longer lets
  its parties read (rejected, rescinded, or deleted), and the administration keeps it for as long
  as its own access lasts. Retrying an
  assignment command, recorded decisions, an override, or a finding with the same Idempotency-Key
  needs the same reading rights, so a retry of the decisions that finished the request returns their
  recorded result; recording anything new on a finished request stays refused.
  Delegation, recusal, and revocation each need a non-blank reason (`400` without one). Each
  assignment the caller may still delegate (their own active, undecided assignment in an unsettled
  stage when they hold the review action, or any such assignment for the request's administration)
  lists `delegationCandidates`: the `partyId` and display `label` of every other active Reviewer
  party that is not already on the stage and that the stage's separation rules admit. The list is
  empty for every other assignment and on a finished request, and it names no other party. A finding
  may name its `assignmentId`, which must be the caller's own active, undecided assignment in an open
  stage that covers the item; without it the caller's single eligible assignment is used, and more
  than one is `409 INFORMATION_REQUEST_REVIEW_ASSIGNMENT_AMBIGUOUS`. A finding reports its `reviewId`
  and `assignmentId`. The reviewer view adds `earlierFindings` and `earlierRemediations` from the
  prior reviews of the same submission, limited to items the caller may review, and `canReconsider`.
  `canRequestSupplement` says whether the caller may create a supplement of the request now (the supersede or
  request-supplement authority, and a state and standing that permit a successor), and `requestETag` is the
  request's current ETag for that command's `If-Match`.
  A correction lists its returned files as `evidence` entries (`artifactId`, `evidenceVersionId`,
  `versionNumber`, `declaredFileName`), and submitted evidence reports `declaredFileName`.
  Item content governs everything written about the item: for an item the caller may not review (including a protected
  item without recent verification), the list and detail views omit its
  findings, their remediations, comments, and worksheet entries, return its decisions without
  `narrative`, and count its returned files in the correction's `undisclosedItemCount` instead of
  listing them. Stage standing, item outcomes, and assignments stay visible. A worksheet save
  answers only the entries for items the caller may still review. Each worksheet, in the reviewer
  view and in a save's answer, carries `draftETag` for `If-Match` and `draftRevision`, which grows
  with every save of that worksheet, so a client that receives answers out of order keeps the
  higher revision. A caller with several active assignments receives one worksheet for each,
  including one whose decisions are already recorded (its assignment states `decidedAt`), so a
  client works in an undecided assignment whose stage is open. One person decides a stage once,
  whichever party they act through (`409 INFORMATION_REQUEST_REVIEW_ALREADY_DECIDED`), and is not
  assigned to a stage twice (`409 INFORMATION_REQUEST_REVIEWER_ALREADY_ASSIGNED`). A reviewer reviews while
  signed in: adding a Reviewer by email that would create a participant, naming a participant as Reviewer, and
  assigning or delegating to a Reviewer party held by a participant are refused with
  `409 INFORMATION_REQUEST_REVIEWER_SIGN_IN_REQUIRED`, and so is creating a request from a Blueprint whose
  default Reviewer is a participant; a follow-up does not copy a Reviewer party held by a participant and
  lists it in `partiesNotCopied`. Assigning or delegating to a
  stage that has settled is `409 INFORMATION_REQUEST_REVIEW_STAGE_NOT_OPEN`, and a retest naming a
  prior finding about another Requirement is `409 INFORMATION_REQUEST_REVIEW_RETEST_INVALID`. A
  retried assignment change counts its narrative as part of the command.
  A stage that excludes response parties refuses (`409 INFORMATION_REQUEST_REVIEW_SEPARATION_OF_DUTIES`)
  an assignment, decision, or override by the recorded submitter, responders, attestors, and evidence
  suppliers, by the parties currently responding, and by the account a recorded participant linked,
  even after that participant's party was reassigned. Recorded attribution is never rewritten.
  The reviewer view reports `itemsAwaitingStepUp`, the routed items whose content is hidden only
  because the caller has not verified recently; saving a worksheet entry for, or recording a
  decision about, such an item answers `401 STEP_UP_REQUIRED` for a signed-in caller. A review's
  `openingReason` (the reason a reconsideration or appeal gave) is returned to the person who gave
  it and in the reviewer view to a caller who may read every item the review covers; in review
  results an appeal's reason is also returned to a caller who may view every item it covers, and a
  reconsideration's reason never is. Each review item carries its Requirement's `prompt`. A withdrawn
  review decides nothing: review results count only decided reviews, and a respondent sees no finding
  of a withdrawn review and cannot reply to one.
- `GET|POST .../accepted-facts`, `POST .../accepted-facts/{factId}/revocation`,
  `GET|POST .../business-decisions`. Listing business decisions, and retrying a recorded one with
  the same Idempotency-Key, need the request's administration (the Exchange owner or a Decision
  Maker), otherwise `403`. A waived item is promoted with `DECLARED` confidence. A fact's
  `conflictState` and `conflictingFactId` are derived when it is read: a fact is `CONFLICTING` while
  another current fact (neither revoked nor superseded) of the same owner, subject, Field, and purpose
  holds a different value, naming the newest such fact, so both differing facts are marked and the mark
  clears once either is revoked or superseded. Promotion
  needs the request's current subject to be the subject its package answered for
  (`409 INFORMATION_REQUEST_ACCEPTED_FACT_SUBJECT_REQUIRED` otherwise), and superseding a fact from
  another request needs the promote authority on that request (`403`). Revocation, whose narrative
  is part of the command, is allowed for any issued request, including a finished one and one whose
  Exchange ended, and stays allowed after the execution grant is revoked; promotion, business
  decisions, imported values, reconciliation, generated outputs, and starting or resuming a clock
  need a grant that is not revoked and lists the matching continuation action (`RESPOND`, `REVIEW`,
  or `SEND_REMINDER`), and changing the completion gate needs a grant that lists `CANCEL`.
- External sources: `GET|POST .../connector-exchanges`, `GET|POST .../imported-values`,
  `POST .../imported-values/{valueId}/decisions`, `POST .../imported-value-reconciliations`,
  `POST .../imported-value-discrepancies/{discrepancyId}/resolutions`,
  `GET|POST .../generated-outputs`. A reconciliation is stored with its ordered outcomes; a retry
  under the same `Idempotency-Key` returns the stored outcomes after checking access again, and the
  key reused for another request is `409`. Each imported value records the Requirement revision
  current when it was recorded. A value recorded while its Requirement collected another Field (or
  none) reconciles as `FIELD_CHANGED`: it keeps its type, value, provenance, and decision, is never
  compared with an answer to the current Field, and records no discrepancy. A value that no longer
  fits the collected Field reconciles as `NOT_COMPARABLE`, as does a value for a Requirement that
  collects no Field. A value is compared with the answer only when the caller may view, review, or
  export its Requirement; for any other Requirement the reconciliation reports only
  `NOT_COMPARABLE`, `FIELD_CHANGED`, and `EXPIRED`, which do not depend on the answer, and records no
  discrepancy. A retry applies the same rule to the stored outcomes. The connector worker applies the
  `RESPOND` continuation rule under the request lock before it calls a connector and again before it
  records the result; a revoked grant, a grant without `RESPOND`, or an operationally suspended owner
  ends the exchange `FAILED` with `request_not_accepting_values` and records no value.
- Every change the connector worker commits is a request transition with its own audit event,
  written in the same transaction as the exchange change and attributed to the platform service
  account: `information_request.external.attempt` when an attempt starts and leases the exchange
  until `nextAttemptAt`, `.retry` (outcome `FAILURE`) when a call fails and is tried again at
  `nextAttemptAt`, `.pending` when the connector asks to be polled at `nextAttemptAt`, `.complete`
  when a result is accepted (each value then records `information_request.external.record`), and
  `.fail` (outcome `FAILURE`) with the exchange's `failureCode` when it ends failed, including an
  exchange ended before any call. Payloads carry `connectorExchangeId`, `connectorKey`,
  `requirementId`, `state`, `attemptCount`, and `nextAttemptAt` or `failureCode`, never the lookup
  or external reference or an imported value. Each event is keyed by its exchange, attempt, and
  step, so the reconciliation matches every step and no step is recorded twice. A step whose audit
  capture fails is rolled back: an unrecorded attempt does not call the connector, and an
  unrecorded outcome or imported value leaves the exchange to its next attempt once the lease ends.

### External record and fact boundary rules

- Imported-value, connector-exchange and generated-output lists use read permissions. They remain
  available while the caller retains record access after an Exchange ends, is rejected, rescinded
  or deleted; writes still follow the parent lifecycle.
- A removed group occurrence or a closed current Requirement revision cannot receive a proposal or
  connector lookup. Queued lookups fail before calling the connector, and reconciliation and fact
  offers skip those Requirements. Unknown child targets return authorization refusals.
- Each new reconciliation run records `RECONCILE_IMPORTED_VALUES` and
  `information_request.external_value.reconciled`, with `reconciliationId`, `comparisonCount` and
  the count of newly recorded discrepancies. Replaying its key adds no run or audit event.
- A connector whose claimed attempt count reaches ten ends with `attempts_exhausted` before another
  call, including when earlier result-recording transactions rolled back. External verification
  timestamps may be at most five minutes ahead of the server to allow clock skew.
- A fact from a deleted source Exchange or a corrected source item is unavailable for reuse. A
  corrected submitted item cannot be promoted again; the frozen source and fact history remain.
  A waived review item yields `DECLARED` with no `sourceReviewId`. Durable supersession remains
  until the predecessor is disposed of, independently of the successor's retention lifetime.
- Recertification accepts optional `confirmedHiddenResponseClearRequirementIds` (UUID array,
  default empty). A required-clear refusal is `409` with `hiddenResponseClears` targets, as for a
  response patch. Retry with the current response ETag, a new command key and explicit confirmations.
  Both signed-in and access-link routes use the same contract. Replays include confirmations in
  their fingerprint. The UI asks for each clearing confirmation before retrying reuse.
- Revocation `reasonCode` allows 128 characters; business-decision `reasonReference` and
  `externalReference` allow 512 each. Oversized input is `400` before persistence.
- Record exports apply the Requirement disclosure decision to `acceptedFacts` and
  `recertifications`. Withheld entries contain their record ID, `requirementId`, and `redacted: true`.

## Operations, clocks, and notices

- `GET /information-request-operations`: the owner's queue. Each row has its `executionStanding` with
  its `reason`. The state, Exchange, search and assignee filters, the order (nearest running due
  instant first, then creation) and `limit` and `offset` are applied in the database; a service level
  or exception filter is applied while reading the matching requests in batches of 500, and `total`
  counts the rows that match every filter.
- `POST /information-request-reminders` answers `201` with a JSON array, one result for each request;
  each result has `noticeCount`, and `cooldownUntil` when the request was skipped inside the cooldown.
  `GET /information-requests/{id}/delegated-authorities` likewise answers a JSON array.
- `GET|POST /information-request-clock-policies`, `GET .../{policyId}`, `POST .../{policyId}/versions`.
  Defining a policy and publishing a version need an `Idempotency-Key` (missing is `400`; the key
  reused with another body is `409`). A policy key already taken is `409`
  `INFORMATION_REQUEST_CLOCK_POLICY_KEY_TAKEN`. A `CALENDAR` policy states no `workingPeriods`
  (`400`); a `BUSINESS` policy states at least one.
- Clock standing: a stopped clock is `OVERDUE` when an overdue point was recorded or it stopped after
  its due instant, and `MET` otherwise, including a clock stopped while paused. A finished request
  has no `nextDueAt`.
- `GET|POST /information-requests/{id}/clocks`, `POST .../clocks/{clockId}/pauses`,
  `.../resumptions`, `.../extensions`. Each clock command is authorized first, so a caller without
  `MANAGE_CLOCKS` gets `403` whatever the request's state or the owner's standing. While the owner is
  operationally suspended, starting, pausing, resuming, and extending a clock are refused with `403`
  and the plan refusal reason `SUBSCRIPTION_SUSPENDED`; a retry of a change recorded before the
  suspension returns that change. After an execution grant is revoked, pausing and extending stay possible and starting
  or
  resuming is `INFORMATION_REQUEST_EXECUTION_GRANT_REVOKED`.
- A clock started on a draft without a received time counts its budget from the moment the request is
  issued: issuing moves its due time and records a `RESUMED` event with reason `REQUEST_ISSUED`, and its
  received time stays as recorded. When several reminder points are due in one pass, only the latest
  is sent; the earlier ones are recorded with reason `REMINDER_SUPERSEDED` and owe no notices.
- While the parent Exchange is `INITIATED`, a running clock records no reminder, overdue mark,
  escalation, or expiry and is checked again an hour later. Issuing a request on such an Exchange
  does not move its clocks. When the Exchange is accepted, each running clock of an issued request whose
  received time was not stated (it defaulted to the issue or the start instant) counts its whole budget
  from the acceptance: its due time moves and a `RESUMED` event with reason `EXCHANGE_ACCEPTED` is
  recorded. A clock with a stated received time keeps its due time.
- A submission that completes the request (every stage submitted and no correction open) pauses each
  running clock: a `PAUSED` event and a `PAUSE_CLOCK` transition with reason `SUBMISSION_COMPLETE`,
  recorded by the platform service account. When work returns to the respondents (a correction is
  opened, or the completing submission is withdrawn), each clock paused that way resumes with the time
  it had left, recorded with reason `RESPONSE_REOPENED`. A clock paused by hand is never resumed
  automatically. Reminder and overdue notices, scheduled or sent with `POST /information-request-reminders`,
  are owed only to responding parties whose role answers a Requirement that is not yet submitted, or was
  returned, and is not hidden by a condition, so `noticeCount` can be `0`.
- `GET /information-requests/{id}/notices`. A notice owed to a party held by a group is rendered once and
  recorded as one notice for each active member of the group, with that member as its recipient and its
  own delivery attempts. A member without an email address is recorded undeliverable (`NO_ENDPOINT`),
  and a member who left the group, or was held back from the group's Share, before delivery is skipped
  (`RECIPIENT_REASSIGNED`). A group member's notice has `recipientWithheld: true` and no masked
  endpoint, so the requesting side sees how many notices were sent and their states but never a
  member's address. The operations queue counts each member's notice; an amendment's notice state is
  in progress while any member's notice is, and otherwise delivered when any member's notice was.
  A notice whose rendering fails is recorded as a render failure with code `RENDER_FAILED` and tried
  again on later passes, after every notice that has not failed; after five failures it is given up
  and its state is `FAILED`. A Communication's sender email token renders empty when no account
  created or owns the request.
  A queued notice whose party was revoked, whose request
  finished, or whose Exchange was rejected, rescinded, or deleted is recorded with a `SKIPPED`
  attempt (`PARTY_INACTIVE`, `REQUEST_FINISHED`, or `PARENT_CLOSED`) and is not sent. The one
  exception to `REQUEST_FINISHED` is a `RESPONSE_OVERDUE` notice owed by the clock due time that
  expired the request (the clock and due cycle of its `EXPIRED` event): it is delivered, and retried,
  under every other rule here. A rendered
  notice whose party now names another holder is recorded with a `SKIPPED` attempt (`RECIPIENT_REASSIGNED`), including
  on a retry after a failed attempt; it keeps its frozen recipient
  and is never sent to the new holder. A queued notice or retry is also recorded as `SKIPPED` while the
  owner is operationally suspended (`OPERATIONALLY_SUSPENDED`), once the request's execution grant is
  revoked (`EXECUTION_GRANT_REVOKED`), for a reminder or overdue notice the grant does not allow
  (`CONTINUATION_NOT_GRANTED`), and for a request without a grant whose owner's plan refuses changes
  (`OWNER_CHANGES_UNAVAILABLE`). In those states a due clock point records no reminder, overdue
  mark, escalation, or expiry and is checked again an hour later; a due time that passed during an
  operational suspension takes effect at the first check after it ends. Delivery holds share locks on the parent
  Exchange, the request,
  and then the party row while it checks and sends, so a cancellation, expiry, submission,
  supersession, Exchange status change, or reassignment either commits first and the notice is
  skipped, or waits for the delivery. Rendering a notice holds the same Exchange and request share
  locks, and disposal locks the Exchange and request before it removes a request's notices.

## Records, audit, and privacy

- `GET /information-requests/{id}/audit-events`, `GET .../audit-reconciliation`,
  `GET /information-request-audit-events`.
- `GET|POST /information-requests/{id}/record-exports`, `GET .../record-exports/{exportId}`. A new
  export beyond the daily ceiling answers `429 INFORMATION_REQUEST_EXPORT_LIMIT_REACHED`. The ceiling
  counts the owner's request and subject record exports of the last day across all of its Exchanges, a
  subject export beyond it is refused the same way, and
  creations for one owner take their turn, so exports created at the same moment cannot pass it
  together; a refused or rolled back creation takes no place, and a repeated `Idempotency-Key`
  returns its recorded export without taking one. A client saves a record it read only while the
  panel that asked for it still shows the same request. Exports report `containsConfidentialContent`;
  reading one that does without recent verification answers `401 STEP_UP_REQUIRED`. Content (schema version 4) adds
  `itemCorrections`, and entries for a
  Requirement the caller may not export are stubs with `redacted: true`. Audit events report
  `targetType` and `targetId`; owner search without a request covers Templates, due date policies,
  and privacy requests, and `requestId` is null for those targets.
- `POST .../record-exports` takes an optional `transferRegion`. The export then records
  `transferDecision` `PERMITTED` with that region; a region the owner may not transfer to is
  `409 INFORMATION_REQUEST_TRANSFER_NOT_PERMITTED` and nothing is stored. The Records tab sends it when
  a region is entered, shows each export's transfer decision and region, and says that the SHA-256
  covers the stored record, so a saved file's own checksum differs.
- A request's `.../audit-events` shows a `successorRequestId` or `supersededByRequestId` payload value
  only to a caller who may view the request it names; for anyone else the key is left out and counted
  in `withheldKeyCount`, like a key that is not allow-listed, and the event itself stays listed. The
  owner audit search keeps both, because its readers may list every request the owner holds and a
  follow-up or replacement shares its source's Exchange. A record export names the replacement
  (`request.supersededByRequestId`) and the request behind each recertified accepted fact
  (`recertifications[].sourceInformationRequestId`, with that request's package, item, Requirement,
  Field Value Revision, review and evidence version ids) only when its creator may view that request;
  otherwise they are `null`, marked `supersededByRequestRedacted: true` or `sourceRedacted: true`, and
  the stored hash covers that content. Reading an export through `.../record-exports/{exportId}`
  leaves out the same way any such request the reader may no longer view, without changing the stored
  export or its hash, and `withheldRelatedRequestCount` says how many it left out (`0` when the content
  is the stored export exactly, and in lists and creation answers, which carry no content). A subject
  export read through privacy keeps them for the owner's privacy managers.
- `GET /information-requests/{id}/disposal-standing`, `GET /record-disposals`,
  `GET|POST /record-preservation-holds`, `GET /record-preservation-holds/{holdId}` (the web client reads it for a
  hold's History: placement, each scope change and the release, with who, when and why),
  `POST .../release`, `PATCH .../scope`, `GET|PUT /record-retention-schedules/{resourceType}`.
  Placing, rescoping, and releasing a hold and publishing a schedule require `Idempotency-Key`
  (`400` without one); a reused key with a different body is `409 COMMAND_RECEIPT_FINGERPRINT_CONFLICT`.
  Hold responses carry the hold's `ETag` header and `holdETag`, and schedule reads and publications
  the schedule's `ETag` header and `scheduleETag` (a schedule that was never published has one too).
  Rescoping or releasing a hold and publishing a schedule require `If-Match` with that ETag: missing
  is `428 COMMAND_PRECONDITION_REQUIRED`, stale is `412 COMMAND_PRECONDITION_STALE` with the current
  `ETag`. The hold, or the owner's schedule for the record type, is locked before the retry is looked
  up, so a retry racing its original replays it, and a schedule's next version number is allocated
  under that lock. Placing a hold locks the owner's placements first, so a placement retried while
  its original is still running replays it too.
  Scheduled disposal skips the retention schedule of an operationally suspended owner, so none of that
  owner's requests is claimed until the suspension is lifted; a claim made before it finishes.
  A hold on `INFORMATION_REQUEST_SUBMISSION_PACKAGE`, `INFORMATION_REQUEST_EVIDENCE_ARTIFACT`,
  `INFORMATION_REQUEST_EVIDENCE_VERSION`, `INFORMATION_REQUEST_OUTBOUND_NOTICE`, or
  `INFORMATION_REQUEST_RECORD_EXPORT`, naming one of a request's own records, refuses that
  request's disposal with `INFORMATION_REQUEST_RECORD_HELD`, as a hold on the request does.
  `POST /record-preservation-holds` accepts only those record types and `INFORMATION_REQUEST`,
  `EXCHANGE`, `ORGANIZATION`, `APP_USER`, `SUBJECT_IDENTITY`, `DOCUMENT`, and `DOCUMENT_VERSION`
  (`400` otherwise), naming a record of the caller's current owner (`404` otherwise). Disposal
  standing lists full `holds` only to a caller who may read the owner's holds; other callers get
  `withheldHolds` with each hold's `resourceType`, `scope`, `status`, and `effectiveFrom`. A record
  export withholds review decisions, findings, and attestations about a Requirement the caller may
  not export, as stubs with `redacted: true`.
  Disposal is refused with `INFORMATION_REQUEST_RECORD_REFERENCED` while another request follows it,
  carries one of its answers forward, supersedes or conflicts with one of its facts, reuses one of its
  assessments, uses a Template it created, or holds an answer confirmed from one of its facts (`FACT_RECERTIFICATION`);
  a reference from a request claimed in the same deletion does not refuse
  it, and the deletion's claims are finished referring request first. Disposing a request that
  superseded another clears the other request's `supersededByRequestId`, and disposing the request
  through which a participant linked an account keeps the link and clears the request it names. It is
  also refused while a subject
  record export the request shares also covers a request that is open, inside its minimum retention,
  or owned by another owner (`SHARED_RECORD_EXPORT`); a hold on such a request, on one of its own
  records or stored Documents and Document Versions, or on its Exchange or subject refuses it with
  `INFORMATION_REQUEST_RECORD_HELD`. A shared export is disposed of with the first of its requests.
  Until that claim finishes, `409 RECORD_PRESERVATION_DISPOSAL_IN_PROGRESS` refuses a hold on the
  claimed request or the others, on any of their records, including one added after the claim, and
  on the stored Documents and Document Versions their own disposal would delete. A reference to a
  claimed record is refused; one being written while a claim is assessed is waited for and keeps
  the record. A fact whose request is under disposal is not offered for reuse.
- `GET|POST /information-request-privacy-requests`, `GET .../{privacyRequestId}`,
  `GET /information-request-subject-restrictions`, `POST .../{restrictionId}/lift`. Recording a
  privacy request and lifting a restriction require `Idempotency-Key`. A privacy request is
  recorded, processed, and completed or refused in one step: a domain refusal returns `201` with
  state `REFUSED` and its reason, and a deletion reports open requests as `SKIPPED_OPEN`. An access
  or export request locks the parent Exchanges and then every request about the subject, each in a
  fixed order, until its subject record is saved or it is refused, so the record shows each request
  at one moment and a command on one of them waits for it. A restriction takes a transaction lock on
  the owner and subject before it looks for the active restriction, so concurrent restrictions of one
  subject under different keys each complete and share it. A lift `reasonCode` is a lowercase code
  matching `^[a-z0-9][a-z0-9._-]{0,127}$`. An access, export, or item correction about a subject with
  a request still being disposed is recorded as `REFUSED` with
  `INFORMATION_REQUEST_DISPOSAL_IN_PROGRESS`. A refused deletion records `record.disposal.denied`
  (basis `PRIVACY_DELETION`) against each request it could not dispose. A privacy request reports
  `recordExportAvailable`, false once its subject record was disposed with one of its requests.
- `GET /information-request-privacy-requests/{privacyRequestId}/record-export` returns the verified
  subject record an access or export request produced, for a caller who manages privacy for the
  owner. Under a request's record exports, a subject record is listed and read only by a caller who
  may export every request it covers. A client saves a subject record it read only while the privacy
  records that asked for it are still shown.

## Platform

- `GET /platform/information-request-health`: platform administrators only; every read and denial
  is audited as `platform.information_request_health.view`. Each indicator has a key, a count, and
  whether it is breached. `NOTICE_INTENTS_OVERDUE` counts intents older than the configured window that
  have no notice and whose rendering has not been given up after five failed attempts; a given-up intent
  shows as a failed notice in the operations queue instead.

### Review and preview boundary refinements (2026-10-27)

`GET /information-requests/{id}/submission-preview` includes `scopeItems` from the content snapshot
covered by `submissionETag`. Each visible non-confirmation item states `requirementId`,
`templateBindingId`, key, type, prompt, occurrence, disposition, narrative, immutable Field revision
value/type/contract, cleared state and evidence-file count. Authorization is checked before reading
values. Condition-hidden items are omitted; `undisclosedScopeItemCount` counts other scoped items
withheld by authorization. Confirmation standing stays in `attestations`.

Review queue entries include `requestTitle`, `exchangeName` and `reviewStageTitle`; only open stages
are offered, ordered by due time, assignment time and id. Exchange summaries request only their own
request subset. The queue remains an unpaged list and still calculates a snapshot for coverage.
Review reads include `canComment`; each item includes `waiverPermitted`. Assignment readback includes
`changeNarrative` alongside `changeReasonCode` and `changedAt`; the browser shows this recorded context. Terminal
requests expose
no actionable assignments or management controls. An administrator's own finding/comment remains
readable after the independent operations permission and protected-item verification checks, without
making the underlying submitted content visible.

Duplicate worksheet item ids, review narrative/body over 4,000 characters and reason codes over
128 characters return HTTP 400 `INFORMATION_REQUEST_REVIEW_INPUT_INVALID`. Item outcome refusals
return their Requirement id and occurrence path. Edited corrections must be resubmitted before an
appeal or reconsideration can supersede them. Removing an undecided reviewer party revokes its active
assignments and reevaluates settlement; a declared recusal excludes its holder from the same stage.
Delegation audit details include the resulting assignment and party ids; reopening and corrected
submission details identify their correction and prior-review links.

`withdrawable` reflects the actual caller's submit-or-party-management permission plus lifecycle,
stage-order and review restrictions. It can be true when `submittedByCaller` is false. Withdrawal
accepts an optional `reasonCode`, at most 128 characters under the shared lowercase machine-code rule.
Detailed remediation navigation remains API-only, while a confirmation's permitted exceptions are recorded from the
respond page through the response patch route under the response ETag; carry-forward
answers remain reference-only and require a deliberate saved answer.

### Clock and notice refinements (2026-10-27)

An omitted clock `receivedAt` now means the start command's instant, including on an issued request.
Draft and pre-acceptance clocks reset their full budget at issue/acceptance; a manually paused clock
stays paused. The historical `RESUMED` event kind remains for these resets with `REQUEST_ISSUED` or
`EXCHANGE_ACCEPTED` reason, displayed as Counting reset. Reminder eligibility uses that effective start.
Explicit API received instants continue to permit historical or future process dates; the browser
starts from now. Overdue standing records the passed due instant and zero-budget resume preserves it.
Scheduling deferral updates its next pass without changing the user-facing clock revision.

Clock keys have at most 64 characters, policy keys 128, policy display names 255. Overlong values
return HTTP 400 `INFORMATION_REQUEST_CLOCK_INPUT_INVALID`. Changes use the shared lowercase reason-code
contract of at most 128 characters. Policies allow at most 168 working periods, 128 reminder offsets,
and 3,660 holidays. Calendar budgets, reminder offsets, escalation delays and extensions have at most
5,256,000 minutes; a business budget also fits within 500 of its stated working weeks. The calendar
calculator's ten-year search horizon still applies, so a calendar with no eligible future working time
can refuse a start. Calendar-time policies retain but ignore holiday dates; business-time policies use them.
Unknown and foreign clock-policy reads/publications both return the same forbidden response.

Manual reminder transitions include `noticeIntentCount`, so event dispatch can deliver without waiting
for the scheduled pass. Parties holding the same principal in multiple responding roles owe one intent;
separate group principals keep their per-party member delivery records. Pending delivery selection
excludes retry backoff before applying the batch limit. Clock notices are skipped as `CLOCK_CHANGED`
when their frozen due cycle/time no longer holds, or the clock pauses/stops, except the final overdue
notice owed by an expiring deadline. Higher-priority request/parent refusals keep their existing reasons.
The transmitted email subject and the stored rendered-content hash include the frozen deployment prefix;
retries send the stored subject verbatim.

`reminderCount` remains the number of recorded, non-superseded clock reminder points, rather than a
count of emails or manual reminder commands. Manual reminders remain in the notice history and delivery
states. The browser labels this clock reminder points explicitly.

### Record preservation boundaries (2026-10-28)

- Request and subject exports are limited to 26214400 UTF-8 bytes by default, configured with
  `app.information-request.export.max-bytes`. Oversized content is refused with
  `INFORMATION_REQUEST_RECORD_EXPORT_TOO_LARGE` before an export is persisted. The limit applies
  after assembly; it is not a streaming memory limit.
- Reading a subject export through a request records `information_request.request.export_read`
  against every source request. All source requests must remain exportable. The visible audit
  payload includes the `verified` boolean. The stored hash continues to cover the stored content.
- Preservation reasons allow 2048 characters, case references 256, resource types 64 and resource
  identifiers 128. Oversized input is a malformed request. Resource type and UUID spelling are
  canonicalized for placement and coverage lookup; V185 normalizes existing hold keys too.
- Retention and disposal ages are whole days from 0 to 365000, with disposal no earlier than the
  minimum retention. Invalid historical schedules are isolated from other owners' disposal work.
- A privacy deletion reuses existing claims without changing their original basis or provenance.
  Repeated restrictions report the existing active restriction and affected request targets.
  Promotion shares the subject restriction transaction lock before it checks restriction standing.
- Disposal removes request Shares, access links and access sessions. Participant account links are
  retained while their deleted request/link provenance becomes null. Deleted object locators are
  cleared, while deletion outcome and document/version identifiers remain in the disposal record.
  Failed claims are selected by attempt count before claim age. Preservation history, schedules
  and tombstones stay append-only even inside a disposal; export deletion requires the export in
  the current claim's frozen scope and the same owner.
- Platform audit export routes require a PLATFORM-owned export, including metadata, approvals
  and download routes. Personal exports do not become platform exports when organizationId is null.
- Archive verification checks owner metadata against the owner encoded in each protected stream.
  Existing canonical envelope hashes are retained. Idle streams do not consume a ready archive batch.- Legacy
  platform/organization audit hold responses retain `organizationId`, `placedByUserId` and
  `releasedByUserId` alongside the owner/principal fields. User aliases are null for non-user
  principals. Legacy hold disposal conflicts, including database races, return HTTP 409 with
  `RECORD_PRESERVATION_DISPOSAL_IN_PROGRESS` rather than HTTP 500.
- Hold audit drafts retain the actual placement, scope-change or release reason and effective time.
  Privacy disposal claims/denials retain the caller session; object deletion and finalization use
  an explicit system identity and the claim as business correlation, including inline processing.
  Repeated restriction records retain their applying restriction after it is lifted.
- Personal security-search reads record a personal audit owner outside an organization. Unknown
  governed-resource owners are refused instead of being filed as platform activity. A Document
  without a parent Exchange resolves its own owner. Stream integrity checks also validate the
  database ledger owner columns against their protected stream identity.
- Privacy/restriction/export and preservation/disposal lists retain their current unpaged response
  contracts. Large histories still carry a scale cost. General personal audit search, signed audit
  export, retention and integrity endpoints are not exposed; Information Request owner audit search
  and record exports remain the available personal Information Request surfaces.