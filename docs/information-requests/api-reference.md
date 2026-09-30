# Information Requests API reference

All paths are relative to the API base. Endpoints under `/no-auth/` authenticate with an access
link and a verified session, not an access token. Every other endpoint needs an access token and
answers to the central authorization service. The inventory below was generated from the resource
annotations on 2026-09-30 (154 endpoints).

## Conventions

| Header | Use |
|---|---|
| `If-Match` | Required on conditional commands, naming the ETag read (request, parties, responses, party, evidence, clock, submission, or Fields answers). A missing value answers `428` and a stale one `412`, each with a stable precondition code. `*` means whichever version is current, where a command allows it. |
| `Idempotency-Key` | Required on commands. A retry with the same key replays the original result, and a different body under the same key is refused. |
| `X-Request-Access-Token` | The access link token on `/no-auth/` calls. |
| `X-Request-Session-Token` | The verified session on `/no-auth/` calls. |
| `Retry-After` | Sent with every `429`. |

Refusals carry `{"errorMessage": ..., "reasonCode": ...}`. Request codes start with
`INFORMATION_REQUEST_` and are listed in `InformationRequestErrorCatalog`; they are never renamed or
reused. Plan refusals use the subscription denial body (`reasonCode`, `planCode`, `featureCode`,
`currentValue`, `limit`, `upgradePlanCode`), with reasons `FEATURE_NOT_INCLUDED`,
`PLAN_LIMIT_REACHED`, `SUBSCRIPTION_PAST_DUE`, `SUBSCRIPTION_SUSPENDED`, `SUBSCRIPTION_CANCELED`,
`TRIAL_ENDED`, `SEAT_LIMIT_REACHED`, and `ORGANIZATION_SUBSCRIPTION_REQUIRED`.

## Discovery and listing

- `GET /information-request-capabilities`: for the caller's active scope, the plan, status,
  enforcement mode, `featureIncluded`, `newWorkAvailable` and `newWorkUnavailableReason`,
  `operationallySuspended`, `typedAnswersAvailable`, `personalTemplatesAvailable`, `assignedWork`,
  and `holdsRequests`.
- `GET /exchanges/{exchangeId}/information-requests`: the requests on an Exchange the caller may
  see. Each has an `executionStanding`. The listing has `canCreate` and `creationUnavailableReason`.
- `GET /information-requests`, `GET /information-requests/{id}`.
- `GET /information-request-reviews`: the caller's review queue.

## Templates

- `GET|POST /information-request-templates`, `GET /information-request-templates/{id}`.
- `PUT /information-request-templates/{id}/draft/configuration`,
  `POST .../draft/publication`, `POST .../versions`, `POST .../versions/{versionNumber}/retirement`.
- `POST /information-request-templates/{id}/clones`: copies a Version into the caller's personal or
  organization scope. This is how a platform Template is used. The `PLATFORM` scope cannot be
  created through this API.
- `POST /information-request-configuration-bundles/validations`.

## Creating, issuing, and ending

- `POST /information-requests`: from a Template Version, a Blueprint, or a one-off configuration. A
  platform Version is refused with `INFORMATION_REQUEST_TEMPLATE_VERSION_PLATFORM_COPY_REQUIRED`,
  and the open-request allowance applies.
- `POST /information-requests/{id}/issuance`: freezes the execution grant, checks committed
  evidence, and reserves a place for each acting party.
- `POST /information-requests/{id}/cancellation`, `POST .../supersession`,
  `PUT .../completion-gate`.
- `GET|POST .../successors`, `POST .../recurrences`, `POST .../recurrences/{recurrenceId}/occurrences`,
  `POST .../refresh-rules`, `POST .../refresh-rules/{ruleId}/refreshes`: follow-ups, which are new
  requests and need the owner's live plan.

## Parties and access

- `GET|POST /information-requests/{id}/parties`, `POST .../parties/{partyId}/reassignment`,
  `POST .../parties/{partyId}/revocation`, `POST .../subjects`, `GET /information-request-subjects`.
  Parties report `trustSuspended`.
- `GET|POST /information-requests/{id}/access-links`, `POST .../access-links/{shareLinkId}/rotation`,
  `POST .../replacement`, `POST .../revocation`. A link issued or replaced without an expiry or use
  limit takes the configured defaults.
- `POST /no-auth/information-request-access-links/challenges` and `.../sessions`: contact proof.
  Rate limited per client address (`429 INFORMATION_REQUEST_RATE_LIMITED`).
- `POST /information-requests/{id}/participant-account-links`,
  `POST .../delegated-authorities`, `POST .../delegated-authorities/{authorityId}/revocations`.

## Responding

The same operations exist under `/information-requests/{id}` for signed-in parties and under
`/no-auth/information-requests/{id}` for access-link sessions:

- `GET .../response-workspace` (includes `executionStanding`), `PATCH .../responses`.
- `POST .../group-occurrences`, `PATCH .../group-occurrences/order`,
  `DELETE .../group-occurrences/{occurrenceId}`.
- `GET|POST .../requirements/{requirementId}/evidence-artifacts`,
  `GET|DELETE .../evidence-artifacts/{artifactId}`, `POST .../versions`,
  `GET .../versions/{versionId}/content`, `GET .../versions/{versionId}/preview`,
  `POST .../withdrawals`. Uploads draw on the grant's evidence allowance beneath the platform ceiling.
- `POST .../requirements/{requirementId}/attestations`.
- `GET .../submission-preview`, `GET|POST .../submissions`, `GET .../submissions/{packageId}`,
  `POST .../submissions/{packageId}/withdrawal`.
- `GET .../amendments` (plus `POST` for managers), `GET .../carry-forwards`,
  `GET .../accepted-fact-offers`, `POST .../accepted-fact-offers/{factId}/recertifications`,
  `GET .../review-results`, `POST .../reviews/{reviewId}/appeals`, `POST .../reviews/{reviewId}/comments`.

## Reviewing and outcomes

- `GET .../reviews`, `GET .../reviews/{reviewId}`, `POST .../assignments`,
  `POST .../assignments/{assignmentId}/decisions`, `.../delegation`, `.../recusal`, `.../revocation`,
  `PATCH .../worksheet`, `POST .../findings`, `.../overrides`, `.../reconsiderations`.
- `GET|POST .../accepted-facts`, `POST .../accepted-facts/{factId}/revocation`,
  `GET|POST .../business-decisions`.
- External sources: `GET|POST .../connector-exchanges`, `GET|POST .../imported-values`,
  `POST .../imported-values/{valueId}/decisions`, `POST .../imported-value-reconciliations`,
  `POST .../imported-value-discrepancies/{discrepancyId}/resolutions`,
  `GET|POST .../generated-outputs`.

## Operations, clocks, and notices

- `GET /information-request-operations`: the owner's queue.
- `POST /information-request-reminders`: each result has `noticeCount`, and `cooldownUntil` when the
  request was skipped inside the cooldown.
- `GET|POST /information-request-clock-policies`, `GET .../{policyId}`, `POST .../{policyId}/versions`.
- `GET|POST /information-requests/{id}/clocks`, `POST .../clocks/{clockId}/pauses`,
  `.../resumptions`, `.../extensions`.
- `GET /information-requests/{id}/notices`.

## Records, audit, and privacy

- `GET /information-requests/{id}/audit-events`, `GET .../audit-reconciliation`,
  `GET /information-request-audit-events`.
- `GET|POST /information-requests/{id}/record-exports`, `GET .../record-exports/{exportId}`. A new
  export beyond the daily ceiling answers `429 INFORMATION_REQUEST_EXPORT_LIMIT_REACHED`.
- `GET /information-requests/{id}/disposal-standing`, `GET /record-disposals`,
  `GET|POST /record-preservation-holds`, `GET /record-preservation-holds/{holdId}`,
  `POST .../release`, `PATCH .../scope`, `GET|PUT /record-retention-schedules/{resourceType}`.
- `GET|POST /information-request-privacy-requests`, `GET .../{privacyRequestId}`,
  `GET /information-request-subject-restrictions`, `POST .../{restrictionId}/lift`.

## Platform

- `GET /platform/information-request-health`: platform administrators only; every read and denial
  is audited as `platform.information_request_health.view`. Each indicator has a key, a count, and
  whether it is breached.
