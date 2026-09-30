# Information Requests operator guide

This guide is for the people who run DocuHyphen: platform administrators, support, and whoever
deploys it. It covers configuration, commercial states, quotas, abuse controls, workers, and support
diagnostics. For failure recovery, see `recovery-runbook.md`. For endpoints, see `api-reference.md`.

## Commercial and operational states

The owner of a request's Exchange (an organization or a person) provides the plan. Parties who
respond or review never need a plan of their own.

| State | Effect | How it is set |
|---|---|---|
| Enforcement mode `ENFORCE` | Plan refusals are raised. | `app.subscription.enforcement.mode` (default `ENFORCE`) |
| Enforcement mode `REPORT_ONLY` | Refusals are logged (`event=subscription_decision outcome=WOULD_DENY`) and allowed. Grants issued in this mode carry no allowances. | same key |
| Enforcement mode `OFF` | Plan checks are skipped. Grants carry no allowances. | same key |
| Lapse: trial ended, past due after grace, canceled | New requests, drafts, follow-ups, and Template changes are refused. Issued requests continue under their execution grant. | subscription status |
| Operational suspension | Nothing on the owner's requests can change, whatever the enforcement mode. Everything stays readable and exportable. | subscription status `SUSPENDED` |
| Execution grant revocation | One request stops for good. It stays readable and exportable. The database writes a revocation once and refuses to rewrite it. | platform action on the grant |
| Trusted relationship suspended | New trusted assignments are refused (`INFORMATION_REQUEST_TRUST_SUSPENDED`). Parties already assigned keep working. | Trusted Organizations |

Reads are never hidden by any of these. Each request projection states an execution standing:
`ACTIVE`, `NEW_WORK_UNAVAILABLE`, `CONTINUING_AFTER_LAPSE`, `OPERATIONALLY_SUSPENDED`, or
`EXECUTION_GRANT_REVOKED`. Only callers who manage the request see the owner's reason.

## Quotas

Plan quotas live in code, in `PlanCatalog`, and are the documented starting point.

| Quota | Free | Personal | Business | Checked |
|---|---|---|---|---|
| Open requests (draft, issued, in progress) | 0 | 25 | not capped | at creation, against the live plan, under the owner's subscription row lock |
| Acting parties per request | 0 | 10 | 100 | frozen into the grant at issuance; each assignment reserves a place |
| Evidence files per request | 0 | 100 | 200 | frozen into the grant; checked at upload, beneath the platform ceiling |
| Evidence bytes per request | 0 | 250 MiB | 500 MiB | as above |
| Committed evidence per owner | 0 | 5 GiB | 100 GiB | at issuance. Open requests count their allowance, finished requests what they store, disposed requests nothing |

An issued request keeps its allowances whatever later happens to the plan. Retention is set by the
owner's retention schedule and no plan shortens it. Record exports are limited by a daily ceiling
that does not depend on the plan (see below), so permitted exports stay available after a lapse.

## Configuration

Every key can be overridden by the environment variable shown in `application.properties`.

| Key | Default | Meaning |
|---|---|---|
| `app.information-request.evidence.upload.enabled` | `true` | Evidence uploads are available. |
| `app.information-request.evidence.upload.maximum-file-bytes` | 26214400 | Largest evidence file (25 MiB). |
| `app.information-request.evidence.upload.maximum-no-auth-file-bytes` | 10485760 | Largest file uploaded without sign-in (10 MiB). |
| `app.information-request.evidence.upload.maximum-request-files` | 200 | Platform ceiling of files per request, beneath any grant allowance. |
| `app.information-request.evidence.upload.maximum-request-bytes` | 524288000 | Platform ceiling of bytes per request (500 MiB). |
| `app.information-request.evidence.upload.maximum-party-files` | 100 | Files one respondent may store on a request. |
| `app.information-request.evidence.upload.maximum-party-bytes` | 262144000 | Bytes one respondent may store on a request (250 MiB). |
| `app.information-request.evidence.malware-scan.required` | `false` | Whether content must pass a scanner before release. Leave `false` unless a scanner is deployed. |
| `app.information-request.evidence.malware-scanner` | `none` | The scanner integration. |
| `app.information-request.evidence.scan.*` | see file | Scan scheduling and reuse windows. |
| `app.information-request.clock.every` | `1m` | Due date clock processing. |
| `app.information-request.notice.dispatch-every` | `1m` | Notice delivery. |
| `app.information-request.connectors.every` | `1m` | External connector exchanges. |
| `app.record-disposal.every` | `1h` | Record disposal worker. |
| `app.information-request.health.every` | `15m` | Health breach check. |
| `app.information-request.health.notice-intent-minutes` | 60 | A notice intent older than this with no notice is a breach. |
| `app.information-request.health.disposal-claim-hours` | 24 | A disposal claim unfinished after this is a breach. |
| `app.information-request.health.event-backlog-minutes` | 15 | A request event pending longer than this is a breach. |
| `app.information-request.health.connector-failure-hours` | 24 | Window for counting failed connector exchanges. |
| `app.information-request.no-auth.challenges-per-minute` | 10 | Contact-code challenges per client address per minute. |
| `app.information-request.no-auth.sessions-per-minute` | 20 | Contact-code verifications per client address per minute. |
| `app.information-request.access-link.default-lifetime` | `P30D` | Expiry given to an access link created without one. |
| `app.information-request.access-link.default-uses` | 25 | Verifications allowed on an access link created without a use limit. |
| `app.information-request.reminder.cooldown` | `PT24H` | A request reminded within this is skipped. |
| `app.information-request.export.daily-ceiling` | 100 | Request record exports per owner in a rolling day. |
| `app.auth.rate-limit.enabled` | `true` | Switches every Redis rate limit, including the no-auth request limits. |

## Abuse controls

Every refusal logs `INFORMATION_REQUEST_ABUSE_REFUSED control=<control>`:

| Control | Answer |
|---|---|
| `NO_AUTH_CHALLENGE_RATE`, `NO_AUTH_SESSION_RATE` | `429` with `Retry-After: 60` and `INFORMATION_REQUEST_RATE_LIMITED`, before any code is sent or checked. The limiter applies progressive backoff to repeat offenders. |
| `REMINDER_COOLDOWN` | The request is skipped in the reminder result with `cooldownUntil`, and nothing is sent. |
| `EXPORT_DAILY_CEILING` | `429` with `Retry-After` and `INFORMATION_REQUEST_EXPORT_LIMIT_REACHED`. |
| `EVIDENCE_UPLOAD_LIMIT` | `409 INFORMATION_REQUEST_EVIDENCE_UPLOAD_LIMIT_EXCEEDED`. |

Contact-code attempts are also limited per link: repeated invalid codes lock verification, and a
link sends at most three codes.

## Workers

The clock, notice, connector, evidence scan, disposal, and health workers are Quarkus scheduled
jobs in the application service. Each resumes from the database after a restart. The health check
logs `INFORMATION_REQUEST_HEALTH_BREACH` markers, which drive `InformationRequestHealthBreachAlarm`.

## Support diagnostics

| Question | Where to look |
|---|---|
| Why can this person or organization not create requests? | `GET /information-request-capabilities` as that caller: `newWorkAvailable`, `newWorkUnavailableReason`, `featureIncluded`, `operationallySuspended`. The Exchange tab states the same reason. |
| Why can a party not answer? | The response workspace's `executionStanding`. `OPERATIONALLY_SUSPENDED` or `EXECUTION_GRANT_REVOKED` stops changes. A lapse does not. |
| Why was a party assignment refused? | `INFORMATION_REQUEST_CAPACITY_EXHAUSTED` means the acting-party allowance is used up. `INFORMATION_REQUEST_TRUST_SUSPENDED` means the trusted relationship is suspended. |
| Why was issuing refused? | A `PLAN_LIMIT_REACHED` refusal naming committed evidence: the owner's open requests reserve their evidence allowance until they finish. |
| Why was a reminder not sent? | The result's `cooldownUntil`: the request was reminded within the cooldown. |
| Why did a request not start from a platform Template? | `INFORMATION_REQUEST_TEMPLATE_VERSION_PLATFORM_COPY_REQUIRED`: copy the platform Template into the owner's Templates first. |
| Is anything stuck? | `GET /platform/information-request-health` (platform administrators; audited). |
| Why does the Exchange tab load slowly? | Listing cost grows with the number of requests on one Exchange (about 60 ms per request in the volume test), because each request is authorized separately. |
