# Information Request code audit

Date: 22 October 2026

## Assessment

The current working tree contains substantial implementations of the Information Request lifecycle, its HTTP interfaces,
persistence, authorization, and user workspaces. Three reproducible frontend defects remain: two command recovery
defects and an issuance control that ignores draft execution standing. The verification results below distinguish the
maintained test suites from the additional audit probes.

The source of truth for this audit is the current code. Planning documents were not used to establish implementation or
completion. The existing working tree changes were preserved, and production files were not edited for this audit.

The user confirmed during the audit that the production malware scanner is intentionally deferred. Its absence is
excluded from the completion findings. The existing scan assessment, quarantine, and release contracts were inspected
with that scope in mind.

## Findings

### 1. Changed supersession reasons reuse an earlier command's idempotency key

Priority: P2.

`web-app/src/app/information-requests/authoring/author-workspace/useAuthorWorkspace.ts:142` builds the supersession
command identity from the request ETag and replacement ID, omitting the reason code. `useAuthorCommandRunner.ts:64`
retains this identity's key after a transport failure.

Reproduction:

1. Supersede a request with a chosen replacement and reason.
2. Lose the response after the server records the command.
3. Open Supersede again and choose the same replacement with another reason.
4. The browser sends the changed payload with the earlier idempotency key.

The backend includes the reason in its fingerprint in `InformationRequestLifecycleService.supersede`.
`CommandReceiptService.runOnce` rejects the changed payload with `COMMAND_RECEIPT_FINGERPRINT_CONFLICT`. This leaves the
management screen showing its old request state instead of recovering through the normal stale-command reload.

The isolated hook probe fails when it expects different reasons to receive different keys. Its unchanged-payload control
passes. Include the reason in the command signature, preferably by serializing the full command body as other author
commands already do.

### 2. An unanswered link rotation keeps retrying a completed command

Priority: P2.

`web-app/src/app/information-requests/authoring/author-workspace/useAuthorCommandRunner.ts:56` reloads only
precondition-stale refusals. It handles `INFORMATION_REQUEST_ACCESS_LINK_ALREADY_ROTATED` as an ordinary error,
retaining the key and the old link state. `useAuthorWorkspace.ts:170` derives the next rotation identity from that
unchanged rotation count.

Reproduction:

1. Select Resend link and lose the response after the rotation commits.
2. Select Resend link again.
3. The backend correctly reports that the rotation already happened and its secret cannot be recovered.
4. Further attempts retain the completed command's key and old rotation count until the user manually refreshes the
   parties or reloads the page.

The isolated hook probe confirms that the already-rotated refusal does not reload the links. Handle this refusal by
clearing the completed command key and refreshing link and party state, while retaining the server's explanation that a
new link is needed. The same handling deserves coverage for an unanswered initial link issuance.

### 3. Read-only and suspended drafts still offer an enabled Issue action

Priority: P2.

`web-app/src/app/information-requests/authoring/request-lifecycle-actions/RequestLifecycleActions.tsx` decides whether
to show Issue from the request state alone, and disables it only while a command is busy. `AuthorWorkspaceContent.tsx`
computes whether changes are allowed from execution standing, but does not pass that restriction to the lifecycle
actions.

A draft with `NEW_WORK_UNAVAILABLE` or `OPERATIONALLY_SUSPENDED` standing consequently retains an enabled Issue button
alongside the notice explaining that it cannot be issued. The backend entitlement and continuation guards refuse the
operation correctly. The missing restriction is in the user interface.

The component probes fail for both unavailable standings and pass for an active draft. Gate issuance using the current
standing while preserving the distinct owner recovery actions that remain permitted.

## Implementation traced

| Area                         | Code inspected and behavior checked                                                                                                                                                                                                   |
|------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Templates and capabilities   | Configuration normalization, publication readiness, schema compatibility, immutable published versions, capability recording and issuance gates.                                                                                      |
| Creation and issuance        | Ad hoc, Template and Blueprint creation, parent Exchange locks, owner entitlement, trusted recipient eligibility, Field audience checks, execution grants and capacity reservation.                                                   |
| Authorization and sessions   | Request and Requirement policies, exact party assignment, delegation, confidential compartments, OTP contact proof, secret session credentials, expiry, revocation, current-session revalidation and account linking coverage.        |
| Responses and conditions     | Sparse patches, bound Field writes, ETags, command receipts, occurrence identities, condition evaluation, hidden response policies, submission locks and correction scope.                                                            |
| Evidence                     | Upload and replacement, retained versions, content inspection, file policy evaluation, collection state, historical submission evidence, release authorization and download headers. Production scanner integration remains deferred. |
| Attestation and submission   | Authentication strength, role ordering, content-bound attestations, readiness, stage ordering, frozen package membership, withdrawal and replay behavior.                                                                             |
| Reviews and closure          | Assignments, worksheets, findings, decisions, overrides, review reopening, correction creation, accepted package satisfaction and Exchange completion gates.                                                                          |
| Follow-up and deadlines      | Successor creation, retained source packages, carry-forward contracts, recurrence, automatic expiry refresh, clock point processing, retry behavior and notices.                                                                      |
| Outcomes and external values | Accepted fact promotion and freshness, source evidence, imported value reconciliation, connector claiming and result recording.                                                                                                       |
| Operations and records       | Owner scope, queue projections, transition history, audit and outbox integration, frozen exports, subject privacy processing and disposal workers.                                                                                    |
| Frontend integration         | Routes, management and respondent workspaces, structured editing, submission, reviews, operations, Templates, Blueprint integration and service wire contracts.                                                                       |

Request amendment creation currently has an API path (`POST /information-requests/{id}/amendments`) and a backend
service. The frontend reads amendment history but offers no amendment authoring screen. The help article explicitly
describes this API-only boundary. This is recorded as a product scope boundary, not an additional defect without a
requirement for a UI.

## Verification

| Check                                           | Result                                                                                                                                 |
|-------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------|
| Maintained frontend source suite                | 287 files, 1,665 tests passed.                                                                                                         |
| Frontend production build                       | Passed. Vite reports a large bundle warning.                                                                                           |
| Information Request ESLint scope                | Passed with no diagnostics.                                                                                                            |
| Required `npx tsc --noEmit`                     | Passed. The root tsconfig is a references-only configuration.                                                                          |
| App project TypeScript check                    | 334 diagnostics, all unrelated to Information Requests and matching the existing baseline; zero Information Request diagnostics.       |
| Feature null-safety check                       | Zero Information Request diagnostics; 410 total diagnostics across the wider app.                                                      |
| Backend named feature suite                     | 383 classes, 3,121 tests passed; zero failures, errors or skips.                                                                       |
| Additional shared and conformance backend suite | 62 classes, 335 tests passed; zero failures, errors or skips. Includes 14 complete-process tests across eight conformance classes.     |
| Combined backend verification                   | 445 classes, 3,456 tests passed; zero failures, errors or skips.                                                                       |
| Focused audit probes                            | Two controls passed; four defect assertions failed across the three findings above. Both probe files have zero TypeScript diagnostics. |

The primary backend run uses the repository's Maven 3.9.9 wrapper and rebuilds Kotlin production and test sources. The
supplementary run reuses those freshly compiled classes. These checks boot Quarkus and real PostgreSQL containers.
Database checks include a clean schema and upgrades of populated older schemas, rather than relying only on mocked
repositories. The complete-process checks cover typed answers, documents and attestations, staged collection, multiple
parties, review corrections, repeatable conditions, recurring supplements, and retained exports.

The system Maven installation is 3.6.2, below the compiler plugin's minimum of 3.6.3. Its initial attempt failed before
compilation; the wrapper run supersedes that attempt. Sandbox restrictions initially prevented Node path resolution and
Maven cache access; the effective checks ran with the required local access.

A plain `npm test` also discovers pre-existing scratch audit tests outside `src`, including failing probes with missing
browser test setup. That attempt was stopped. The completed maintained suite was explicitly scoped to `src`.

Some backend test fixtures attempt Exchange notification delivery through the configured sender and log unavailable AWS
credentials. Actual external email delivery is not established by these tests. This does not change the individual
assertion results.

## Reproduction and evidence

From the repository root:

```powershell
.\mvnw.cmd -o test '-Dtest=*InformationRequest*,*RequestTemplate*' '-DfailIfNoTests=false'
$auditTests = (Get-Content '.codex-temp/information-request-code-audit-2026-10-22/additional-backend-tests.txt' -Raw).Trim()
.\mvnw.cmd -o surefire:test "-Dtest=$auditTests" '-DfailIfNoTests=false'
```

From `web-app`:

```powershell
npm.cmd test -- src --maxWorkers=2
npm.cmd run build
npm.cmd run typecheck:app
npm.cmd run typecheck:null-safety
npx.cmd eslint src/app/information-requests src/app/settings/information-request-templates-tab 'src/services/informationRequest*.ts'
npm.cmd test -- --config audit-probes/information-request-code-audit-2026-10-22/vitest.config.mjs --reporter=verbose
```

The last command intentionally fails against the current implementation because it asserts the expected behavior for the
three findings. The probes use a `.probe.tsx` suffix and a dedicated configuration, so normal source test discovery does
not include them. To check the probe types, run
`node audit-probes/information-request-code-audit-2026-10-22/check-probe-types.mjs` from `web-app`.

Logs, the initial working tree status, and a source hash snapshot are stored in
`.codex-temp/information-request-code-audit-2026-10-22/`. The snapshot inventories relevant files; it is not a claim
that every inventoried line was manually inspected. The maintained test suites and traced code paths support the
assessment, but do not establish that every possible input or deployment behavior is defect-free.

Effective result files in that directory:

- `backend-wrapper-tests.log`, `backend-additional-tests.log`, and `backend-results.json` contain both completed backend
  runs and per-class counts.
- `frontend-src-progress-tests.log` contains the completed maintained frontend suite.
- `focused-probes.log` and `probe-typecheck.log` contain the reproduced defects and probe type check.
- `frontend-build.log`, `typecheck-baseline.log`, `typecheck-null.log`, and `final-static-check-exits.json` contain the
  build and static check results.

The 1,439-file relevant source snapshot remained unchanged through verification. A final working tree comparison showed
only this report and the new isolated audit probe directory added by this audit; the original changes were preserved.

The audit additions use neutral synthetic request, record, party and replacement terminology. No production identifiers,
business rules, migrations, infrastructure, or shipped configuration were changed. No commit or push was performed.

## Remediation result (2026-10-22)

All three findings are fixed in the web client. The remediation changed no backend code, migration, API contract,
infrastructure, or shipped configuration. Nothing was committed or pushed.

### 1. Supersession retry key

`useAuthorWorkspace.supersede` builds its command signature from the whole command body,
`supersede:{"supersededByRequestId":...,"reasonCode":...}:<request ETag>`, as the party commands already do. An
unchanged retry keeps its `Idempotency-Key`; a changed reason or replacement gets a new key.

### 2. Already-completed access link commands

`useAuthorCommandRunner` recognizes `INFORMATION_REQUEST_ACCESS_LINK_ALREADY_ISSUED` and
`INFORMATION_REQUEST_ACCESS_LINK_ALREADY_ROTATED` through `isRepeatedInformationRequestAccessLinkCommand` in
`shared/informationRequestRefusal.ts`. As with a stale refusal, it drops the spent key and reloads the workspace,
parties and links, but it keeps the server's explanation as the error instead of the stale notice. The next Resend link
uses a new key against the reread rotation count and shows the new link. Both Resend link and the initial Create link
are covered.

### 3. Lifecycle actions and execution standing

`RequestLifecycleActions` reads `request.executionStanding`. Issue is offered only under `ACTIVE`
(`mayIssueUnderStanding`). Cancel request and Supersede are hidden only under `NEW_WORK_UNAVAILABLE` and
`OPERATIONALLY_SUSPENDED` (`mayRecoverAsOwnerUnderStanding`).

This follows the backend guards. A draft has no execution grant, so `InformationRequestContinuationGuard` checks the
live subscription for issue, cancel and supersede alike, and refuses all three in both standings. An issued request
answers to its grant, which lists every continuation action, so cancel and supersede stay permitted after a lapse and
after revocation, and are refused only during an operational suspension. The page's `editable` flag was not reused: it
is false under `EXECUTION_GRANT_REVOKED` and would have hidden these owner recovery actions on a Stopped request.

Hiding Cancel request on a Read only draft, and both actions while changes are paused, goes beyond the finding, which
named only Issue. The backend refuses those actions in the same standings.

The managing help article now states that Cancel request and Supersede stay available on a Stopped request but not on
one marked Changes paused or Read only. The release notes have a matching entry.

### Follow-up outside the findings: completion gate on a Stopped request

`InformationRequestCompletionGateService` guards a completion gate change with the same owner recovery rule as
cancellation (`requireOwnerRecovery` with `CANCEL`), but the page offered the Must finish before the Exchange can end
switch only under the page's `editable` flag, so it was hidden on a Stopped request. `AuthorWorkspaceContent` now offers
the switch on an unfinished request whenever `mayRecoverAsOwnerUnderStanding` holds, so it is shown on a Stopped request
and stays hidden under `NEW_WORK_UNAVAILABLE` and `OPERATIONALLY_SUSPENDED`. The help article's Ending the Exchange
paragraph and the release notes say so.

### Tests added

- `useAuthorWorkspace.test.ts`: supersession key reuse and separation; a lost rotation and a lost initial issuance, each
  rereading parties and links, keeping the server's explanation, and sending a fresh key afterwards.
- `RequestLifecycleActions.test.tsx` (new): Issue and Cancel request on an active draft; no action on Read only and
  Changes paused drafts; Supersede and Cancel request kept under `ACTIVE`, `CONTINUING_AFTER_LAPSE` and
  `EXECUTION_GRANT_REVOKED`; neither while paused.
- `InformationRequestAuthorWorkspace.test.tsx`: a paused request offers neither Supersede, Cancel request, nor the
  completion gate switch; a Read only draft offers neither Issue, Cancel request, nor the switch; a Stopped request
  still offers Supersede and Cancel request, and changing its switch sends the command under the request ETag.
- `executionStandingText.test.ts`: the two standing rules.
- `helpDocs.test.tsx`: the managing article's sentences on which ending controls a Stopped, paused, or Read only request
  still offers.

Each new defect assertion was first run against the original behavior and failed: six in the hook and component tests,
two in the page tests, and the Stopped request's switch once the page used its `editable` flag again. The
permitted-action controls passed throughout.

### Verification

| Check                                                                                              | Result                                                                                          |
|----------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------|
| Focused audit probes                                                                               | 6 of 6 pass; previously 2 controls passed and 4 defect assertions failed.                       |
| Probe type check                                                                                   | Zero diagnostics.                                                                               |
| Completeness audit supersede probe (`.audit-completeness-20261022/author-retry-boundary.test.tsx`) | 2 of 2 pass.                                                                                    |
| Maintained frontend source suite                                                                   | 288 files, 1,679 tests passed.                                                                  |
| Frontend production build                                                                          | Passed, with the existing large bundle warning.                                                 |
| Information Request ESLint scope                                                                   | Passed with no diagnostics.                                                                     |
| Required `npx tsc --noEmit`                                                                        | Passed.                                                                                         |
| App project TypeScript check                                                                       | 334 diagnostics, all unrelated and matching the baseline; zero Information Request diagnostics. |
| Feature null-safety check                                                                          | 410 total diagnostics; zero Information Request diagnostics.                                    |

The backend suites were not rerun because no backend file changed. The changed code, tests and documentation use neutral
synthetic terminology and introduce no industry-specific identifiers or rules.
