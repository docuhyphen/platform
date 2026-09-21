# Information Requests: Phases 1-5 recheck

Review date: 2026-09-13. Decision: Phase 5 remediation is not complete; keep Phase 6 blocked.

The original review and its remediation journal remain historical evidence. This review traced the
current implementation through Fields, Template configuration/publication, runtime materialization,
parties, parent lifecycle, both access surfaces, response writes/projection, conditions, nested
occurrences, execution grants, and frontend commands. It found seven actionable gaps. Existing green
unit tests do not establish these missing integration invariants. This is an analysis and planning
change; no production fix, commit, push, or infrastructure change was made.

## Findings

### P5-R21: P1 - OTP failure accounting rolls back on refusal

Location: `src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestContactProofService.kt`,
`verifyChallenge`, `registerOtpFailure`, and `issueChallenge`.

`verifyChallenge` is `@Transactional`. An incorrect code updates the ShareLink attempt counter and
then throws `InformationRequestLifecycleException`, which extends `RuntimeException`. The repository
update joins that transaction. There is no rollback exemption or separately committed failure
transaction, so the service interceptor rolls back the counter before the REST resource catches the
exception. Successive HTTP failures therefore do not accumulate the five attempts needed for lockout.
The existing test directly constructs the service with Mockito repositories and observes mutated
objects; it does not cross a transactional CDI boundary. The V118 tests validate storage constraints,
not persistence of attempts following an HTTP refusal.

There is a second escape from the intended bound: challenge reissuance unconditionally resets the
failure count, with no persisted resend budget. Even after correcting rollback, a caller can request
a new challenge before reaching the attempt threshold. The one-second response floor does not bound
parallel issuance or total email sends. The existing sign-in and step-up services already illustrate
the need to preserve OTP failure state across specific refusal exceptions.

Required closure: exercise the real CDI transaction with PostgreSQL, verify counters in a fresh
transaction after each refused attempt, enforce lockout across concurrent attempts and resends, and
retain atomic successful session/use accounting. Use narrowly scoped failure handling so unrelated
session-issuance failures still roll back. This is an incomplete P5-R12 security invariant.

Evidence level: production transaction-path analysis, verified against the installed Quarkus 3.17.5
`TransactionalInterceptorBase.handleExceptionNoThrow` source, which marks unexempted runtime
exceptions rollback-only. This review does not claim a live HTTP brute-force test.

### P5-R22: P1 - Denied Requirement configuration is returned in the workspace

Location: `src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestResponseWorkspaceService.kt`,
`load`, and `InformationRequestTemplateProjectionLoader.loadVersion`.

The workspace filters runtime responses through `canViewRequirement`, but independently loads the
entire author-facing Template Version and returns it unchanged. A respondent allowed to view the
request can therefore receive prompts, help, stable Requirement/binding IDs, confidentiality keys,
evidence policy, and condition predicates/literals for Requirements denied to that respondent. All
active occurrence metadata and condition evaluations are also returned without a corresponding
Requirement disclosure filter. The shared service exposes this through authenticated and no-auth
workspace routes. Hiding the response values does not enforce NOT_DISCLOSED on the configuration.

Required closure: introduce a recipient-safe workspace configuration projection and authorize every
disclosed Requirement, relationship, group/occurrence, and rule detail. Preserve safe controls for
authorized zero-occurrence groups using authored-policy authorization. Test one visible Requirement
beside an unassigned, NOT_DISCLOSED, or protected Requirement on both routes, including returned JSON
identifiers and literals. This closes the still-applicable P4-T6 projection requirement.

Evidence: the temporary backend projection probe denied Requirement access, verified that responses
were empty, and then failed because the Template still contained Requirement configuration. The four
existing workspace tests in that probe passed.

### P5-R23: P1 - Nested Field edits are silently omitted from saves

Location: `web-app/src/app/information-requests/structured-response-workspace/structuredResponseWorkspaceState.ts`,
`buildResponsePatches`, and its caller in `InformationRequestStructuredResponseWorkspace.tsx`.

Rendering now resolves the group through `sourceTemplateGroupId`, but patch construction still calls
`occurrenceGroupKey`, whose regular expression returns the outermost group. For
`items[0]/entries[0]`, the result is `items`, so a Requirement anchored to `entries` is filtered out.
Editing only that child generates no patches and Save silently returns; editing parent and child
together saves only the parent. The P5-R15 regression covered nested add/reorder, not nested saves.

Required closure: use the same immutable group and runtime Requirement identity for rendering and
saving. Cover child-only saves, parent-plus-child saves, two sibling parent branches, and deeper
nesting through the actual Save handler on both access modes.

Evidence: a temporary Vitest probe reused the existing typed fixtures with a child path and anchor.
It expected the child runtime Requirement ID in the payload and received `[]`; the two existing
correlation tests in that probe passed. P5-R15 is not fully satisfied.

### P5-R24: P1 - New group authorization borrows an arbitrary existing occurrence

Location: `src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestGroupAuthorizationService.kt`,
`authorizeMaterializedBindings`, called by `InformationRequestGroupOccurrenceService.addOccurrence`.

The service builds `existingByBindingId` from every runtime Requirement and authorizes the selected
existing occurrence instead of the scope being created. A delegate authorized only for one existing
Requirement can pass that check and create a new sibling to which the delegation does not apply.
Conversely, remove the only occurrence of a group whose minimum is zero and then add it again: the
preserved Requirement is selected, its `occurrenceRemoved` policy denies mutation, and the assigned
party cannot recreate the group. Multiple occurrences make the decision depend on which row wins
`associateBy`, rather than the requested parent and new scope.

Required closure: evaluate new occurrences through an explicit creation/authoring scope, allowing
only authority that covers the new scope. Do not use historical or unrelated runtime Requirements
as authorization surrogates. Cover exact-Requirement delegation, removed-last-occurrence recreation,
different nested parents, and descendant bindings. This completes the P5-R06 authorization boundary.

Evidence level: traced add-command and authorization-policy paths; no new database exploit test is claimed.

### P5-R25: P2 - Ordinary save refusals strand the editor; confirmed clearing has no UI path

Location: `web-app/src/app/information-requests/structured-response-workspace/InformationRequestStructuredResponseWorkspace.tsx`,
`save`, and `structuredResponseCommands.ts` / `informationRequestRuntimeService.ts`.

Save sets `busy=true` and awaits the command without catch/finally. The service converts stale
preconditions into an outcome but throws other refusals. A validation error, revoked session,
authorization denial, or network failure therefore leaves Save disabled and displays no handled
error. The payload always contains `confirmedHiddenResponseClearRequirementIds: []`, so a valid
controlling-answer change requiring confirmed clearing can never be completed in this editor.
The help article explicitly instructs respondents to confirm affected Requirements before saving,
but no such control or retry flow exists. Group command promise chains need equivalent recovery.

Required closure: implement explicit affected-Requirement confirmation, preserve unsaved edits on
refusal, show the server error, and always release busy state. Add interaction tests for rejection,
retry, confirmation acceptance/cancellation, and group command failure. This is already-implemented
P5-T4/P5-T9 behavior, not a reason to defer the defect to Phase 10.

Evidence: a temporary UI interaction probe made the real Save handler receive a rejected command.
Save remained disabled, the assertion expecting recovery failed, and Vitest recorded an unhandled
rejection; the six existing workspace tests in that probe passed. The clear-confirmation gap was
verified through the payload/control paths and the full Information Request help article.

### P5-R26: P2 - Party reassignment retains inverted parent/request lock ordering

Location: `src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestPartyService.kt`,
`reassignMutation`.

Reassignment first locks the Information Request and then its parent Exchange. Response saves,
request lifecycle mutations, and parent termination now lock Exchange first and request second.
Concurrent reassignment and response save/termination can each hold the row the other needs,
producing a PostgreSQL deadlock and rolling back one legitimate operation. The parent-lock helper
documents the shared ordering, but reassignment does not use it.

Required closure: apply one parent-before-request ordering across existing request/party commands
and review session/link lock interactions. Add a deterministic concurrent reassignment-versus-save
and reassignment-versus-termination contract using real service entry points. Preserve the lifecycle
recheck after acquiring locks. This is a remaining P3-T11/P5-R02 integration issue.

Evidence level: concrete opposing lock acquisition paths; no new concurrent deadlock probe was run.

### P5-R27: P2 - Reassignment still does not revoke its bootstrap sessions

Location: `src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestPartyService.kt`,
`reassignMutation` and `revokeMutation`.

The original P3-T11b/P3-T11c tasks remain unchecked with obsolete statements that bootstrap links
and sessions do not exist. They now exist, but these party mutations still only revoke the Share
and update the party. Neither calls the bootstrap/session revocation services, and ShareService
does not perform that work either. Reassignment leaves old link/session rows unrevoked and does not
create a replacement recipient-bound bootstrap handoff. These already-existing mechanisms were
explicitly brought into the mandatory pre-Phase 6 scope.

The old link currently fails content access because its Share no longer resolves to an active party;
this finding does not claim that revoked respondents retain content access. It is the missing
explicit credential lifecycle and reassignment handoff required by the plan. Complete those hooks
transactionally, verify old sessions/links are revoked and any replacement is bound to the new
principal, and prove structured response authorship/history survives reassignment. Update the old
P3-T11 prerequisite text to distinguish completed runtime behavior from future evidence/package work.

Evidence level: production call-site search and party/Share mutation inspection.

## Phase and original remediation assessment

| Scope | Assessment |
|---|---|
| Phase 1 | Value Sets, per-binding audience projection, sparse updates, ETags, exact Schema assignment, and provenance paths inspected. No additional standalone Phase 1 defect identified; later request adapters are covered above. |
| Phase 2 | Ownership, immutable publication, capability recording, validation, exact references, and materialization paths inspected. No additional standalone Phase 2 defect identified. Author configuration must not be reused as a recipient projection. |
| Phase 3 | Runtime and receipt foundations exist; reassignment still conflicts with the parent lock contract. Historical P3-T11 prerequisite descriptions must not be used to defer already-existing session/response behavior. |
| Phase 4 | Independent credentials, account/group identity, grants, and party projections exist. OTP transaction semantics and workspace configuration disclosure prevent closure. |
| Phase 5 | Batched Fields saves, occurrence identity, removed-state filtering, ancestor condition lookup, hidden-value handling, reactivation, and exact Field completeness are implemented. Nested saves, group creation authorization, and UI refusal/clear handling remain incomplete. |
| P5-R01 | Independent hashed session secret, expiry, and link/principal validation are implemented. |
| P5-R02 | Parent effects and shared read policy are wired; remaining lock-order integration is P5-R26. |
| P5-R03 | Active Field projection filtering and revision-preserving clear are present; recipient configuration and clear UI gaps are P5-R22/P5-R25. |
| P5-R04 / R05 | Exact Field occurrence matching, entry-to-Requirement checks, and owner-aware audience enforcement are present. |
| P5-R06 | Removal scope checks exist; creation authorization remains incomplete under P5-R24. |
| P5-R07 / R08 / R09 / R10 | Removed occurrence filtering, Value Set batching, explicit runtime correlation, and stable occurrence path allocation are present. |
| P5-R11 | Verified participant-account links and current group membership feed exact-party facts. |
| P5-R12 | Successful bootstrap use accounting exists; failure lockout is not transactionally effective, P5-R21. |
| P5-R13 / R14 | Response replay reauthorizes current access with revision-bounded envelopes; issued-work reads and UI use owner-funded grants. |
| P5-R15 | Ancestor conditions and nested controls exist; nested saving is still broken, P5-R23. |
| P5-R16 / R17 / R18 | Reactivation, exact collected-value completeness, and production recipient reservation callers exist. |
| P5-R19 | Updated grantor/effective-time fixture is present; current regression results are recorded below. |
| P5-R20 | The meaningful gate exists and correctly detects a new unrelated diagnostic; a historical passing run is not a current green gate. |
| P5-R-GATE | Reopened. Resolve P5-R21 through P5-R27, reconcile current verification failures, then rerun integrated verification. |

These are review conclusions for the inspected paths, not a guarantee that no other defect exists.
Future evidence/upload/package/review work remains in its original phases. Phase 6 also retains its
separate scanner decision prerequisite after this remediation gate is satisfied.

## Verification

- `.\mvnw.cmd test`: **passed**, 2,554 tests, 0 failures, 0 errors, 0 skipped, with Docker and
  PostgreSQL available. Completed in 16:59. The original delegated-authority fixture regression is
  green. Temporary new probes were not part of this existing-suite run.
- `npm test -- --run` in `web-app`: **failed**, 118 files, 480 tests passed and 1 failed.
  The failure is `OrganizationsTable > renders only restricted platform account summary fields`,
  outside Information Requests. Rerunning that file alone reproduced it: 1 passed, 1 failed.
- `npm run typecheck:app`: **failed as designed**, 347 total diagnostics, 0 Information Request
  diagnostics. There is one new unrelated diagnostic beyond the 346-entry reviewed baseline:
  `OrganizationsTable.tsx`, TS6133, unused `entitlementSummary`. Do not silently accept the new baseline.
- `npx tsc --noEmit`: passed. As before, the root configuration does not compile the referenced
  app project, so this is not evidence of a clean application typecheck.
- `npm run lint`: **failed**, 110 problems, 62 errors and 48 warnings. Filtered output contained no
  Information Request diagnostics. The earlier gate recorded 109 problems, not the current 110.
- `npm run buildWithTs`: **failed** on the app-project TypeScript diagnostics; filtered output
  contained no Information Request diagnostics. Neither lint nor build is a clean gate.
- `npx vitest run src/app/information-requests/structured-response-workspace/ReviewNestedSaveProbe.test.ts`:
  2 existing tests passed, 1 new assertion failed because the nested payload was empty.
- `npx vitest run src/app/information-requests/structured-response-workspace/ReviewSaveRefusalProbe.test.tsx`:
  6 existing tests passed, 1 new assertion failed because Save stayed disabled, plus 1 unhandled rejection.
- `.\mvnw.cmd "-Dtest=ReviewWorkspaceProjectionProbeTest" test -DskipFrontend=true`: 5 tests,
  4 passed, 1 new assertion failed, 0 errors, 0 skipped. The denied response list was empty but the
  denied Template Requirement configuration was still returned.
- Help review: read the complete Information Request article. It documents lockout, nested responses,
  and confirmed clearing that the findings above show are not fully delivered. Article size is 141
  lines and registry size is 24, within their limits. No product behavior or help content was changed;
  the remediations should restore the documented behavior.
- Initial sandboxed test launches failed before test execution due to Maven connection and Node
  filesystem restrictions. Approved retries ran successfully; these initial errors were not counted
  as product test failures.
- Temporary probe sources and command logs are retained locally under
  `target/information-request-recheck/`; probes were removed from the normal test source directories
  after execution. Only the three review/plan/evidence Markdown files remain as working-tree changes.

## Handoff

Start with P5-R21 using a transaction-backed failing test. Keep the original P5-R01 through P5-R20
history, add the new unchecked tasks, and keep P5-R-GATE unchecked. Do not interpret the earlier
2026-09-13 gate result as permission to start Phase 6. No new industry-specific identifiers, fixtures,
production defaults, or paid infrastructure were introduced by this review.
