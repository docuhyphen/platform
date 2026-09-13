# Information Requests: Phases 1-5 review

Review date: 2026-09-09. Recommendation: do not advance to Phase 6 yet.

This review compares the current working tree with the completed Phase 1-5 scope, including the latest P5-T10 condition changes and the completion evidence. It is a code review, not a feature implementation. Existing user changes are preserved, and no production fixes, commits, or pushes were made.

The findings below are concrete integration and policy gaps. Passing isolated unit tests does not establish the end-to-end invariants they miss. P1 means fix before progression; P2 means a correctness or completeness gap that should also be resolved or explicitly scoped before closing these phases.

## 1. P1: Bootstrap tokens become content credentials after someone else verifies them

[InformationRequestNoAuthReadAccessService.kt:38](C:/Users/Black/IdeaProjects/doc-hyphen/src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestNoAuthReadAccessService.kt:38)

Give the same bootstrap link to two browsers. After the intended recipient verifies the OTP in one, the other can call the no-auth read and mutation endpoints using only that original token. The resolver looks up the latest usable session by ShareLink ID and adopts it; the caller supplies no independently issued session credential. This defeats forwarding denial and attributes the second browser's changes to the verified recipient. Issue a separate secret session credential after contact proof and require it for content access. Existing tests explicitly expect token-only resolution, so their passing does not establish this security invariant.

## 2. P1: Parent termination effects are defined but never applied to runtime access

[InformationRequestQueryService.kt:66](C:/Users/Black/IdeaProjects/doc-hyphen/src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestQueryService.kt:66)

Reject, rescind, or delete an Exchange after issuing a request and verifying a respondent. The production read path never calls InformationRequestTransitionMatrix.canRead, and parentEffects has no production consumer. Exchange termination code does not cancel child requests or revoke RequestAccessSessions. The request context only marks the request's own terminal states as archived, and parent inheritance validates ownership rather than parent lifecycle. A direct request-party Share can therefore retain reads the matrix says must be revoked. Wire parent effects transactionally into Exchange transitions and enforce the actor-specific parent read policy on every read surface.

## 3. P1: Hidden and explicitly cleared Field values still reach workspace responses

[InformationRequestResponseWorkspaceService.kt:56](C:/Users/Black/IdeaProjects/doc-hyphen/src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestResponseWorkspaceService.kt:56)

Save a conditional Field, make its rule false, and load the response workspace. Hidden envelopes are excluded from findCurrentForRequest, but the workspace independently loads the occurrence's full Fields projection for every authorized Requirement and creates a replacement NOT_ANSWERED DTO containing those values. CLEAR_WITH_CONFIRMATION only nulls the envelope's fieldValueSetId; it never clears the current Field value, and reads rediscover the Value Set by occurrence. Consequently hidden or cleared data reappears in response JSON and can reappear when the condition becomes true. Filter active projections and clear current values through the revision-preserving Fields command, while retaining historical revisions.

## 4. P1: Fields authorization selects an arbitrary occurrence instead of the addressed one

[InformationRequestFieldBindingPolicy.kt:66](C:/Users/Black/IdeaProjects/doc-hyphen/src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestFieldBindingPolicy.kt:66)

For a repeated Field, requirementIdAnswering selects the first Requirement with the matching Field Definition and ignores FieldBindingAccess.valueSet. If a delegate is permitted to act for only items[0], a Fields write targeting items[1] can be authorized against items[0]; the inverse can incorrectly deny legitimate work. The outer response patch does not bind each supplied Field entry to the patch's Requirement, so the broader occurrence mistake remains reachable. Resolve the exact binding and occurrence together, and reject entries that do not belong to the named Requirement.

## 5. P1: Uncollected internal Schema Fields bypass recipient audience restrictions

[InformationRequestFieldBindingPolicy.kt:38](C:/Users/Black/IdeaProjects/doc-hyphen/src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestFieldBindingPolicy.kt:38)

A request Schema may contain Fields not collected by a Template Requirement. isExternalCaller always returns false, and the no-matching-Requirement branch returns the shared allow decision. An external respondent who can access one normal Field therefore receives uncollected INTERNAL/CONFIDENTIAL bindings and current values in the full Value Set projection, and can write non-read-only ones. Restore owner-aware audience handling and fail closed for bindings without an explicitly authorized request purpose. This is a regression in the Phase 4 adaptation of the Phase 1 Fields policy.

## 6. P1: Group commands bypass the Requirement-specific party policy

[InformationRequestGroupOccurrenceService.kt:366](C:/Users/Black/IdeaProjects/doc-hyphen/src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestGroupOccurrenceService.kt:366)

A CONTRIBUTOR with aggregate response capability can remove or reorder a group whose Requirements are assigned to a PREPARER, or are NOT_DISCLOSED/protected. authorize asks REQUIREMENT_RESPOND against ResourceRef.informationRequest(requestId), so the Requirement policy evaluator never runs. The command subsequently mutates the chosen group and its descendants without authorizing their Requirements. Validate the affected group scope, including descendant Requirements, before mutation; adding a zero-occurrence group also needs an authored-policy authorization path.

## 7. P1: Removed occurrences remain active in response and completeness calculations

[InformationRequestCompletenessProgressService.kt:44](C:/Users/Black/IdeaProjects/doc-hyphen/src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestCompletenessProgressService.kt:44)

Remove an optional repetition that contains an unanswered required Field. Removal only stamps information_request_group_occurrence.removed_at and preserves its Requirement rows. Completeness loads all Requirement rows, so the removed row still increases the denominator. Response saves, condition evaluation, and the workspace also load these unfiltered Requirements, and the Requirement context does not deny removed occurrences. Keep the rows addressable for history, but exclude removed occurrence subtrees from active calculations and refuse new writes to them.

## 8. P1: A multi-Field save in one occurrence conflicts with its own precondition

[InformationRequestResponseDraftService.kt:191](C:/Users/Black/IdeaProjects/doc-hyphen/src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestResponseDraftService.kt:191)

Patch two different Field Requirements belonging to the same root or repeated Value Set, both using the ETag served by the workspace. The loop calls SchemaAssignmentService.setValues separately for each Requirement. The first write advances the shared Value Set revision; the second checks the original ETag and throws a stale-precondition refusal, rolling back the command. Existing coverage uses different occurrences with different sets. Validate preconditions once per distinct Value Set and submit its entries as one Fields mutation. A focused review probe models the real Fields revision/precondition contract.

## 9. P1: The frontend maps multiple Fields onto the first runtime Requirement

[structuredResponseWorkspaceState.ts:71](C:/Users/Black/IdeaProjects/doc-hyphen/web-app/src/app/information-requests/structured-response-workspace/structuredResponseWorkspaceState.ts:71)

Workspace response IDs are runtime Requirement IDs, while the supplied Template Requirement IDs are binding/template IDs. The direct comparisons therefore miss. Every Field response currently contains the same occurrence-wide fields projection, so the fallback that finds a response containing a Field contract selects the first response for every Field in that occurrence. Editing a second Field can update the first Requirement's envelope; editing both produces duplicate Requirement patches that the backend rejects. Return an explicit runtime-to-template/binding correlation and use it. A focused frontend probe reproduces runtime-a being selected when runtime-b is required.

## 10. P1: Reordering makes later occurrence creation reuse an existing stable path

[InformationRequestGroupOccurrenceService.kt:173](C:/Users/Black/IdeaProjects/doc-hyphen/src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestGroupOccurrenceService.kt:173)

Create items[0] and items[1], remove items[0], then reorder the remaining item so its display index is 0. The removed row also retains index 0. Add now computes max(occurrenceIndex)+1 as 1 and tries to create items[1] again. V112's unique request/path constraint rejects it, and the existing Value Set path would also collide. Separate the immutable identity counter from mutable display order, or derive the next identity from immutable occurrence identity. The review probe exercises this exact post-removal/reorder state.

## 11. P1: Registered upgrades and group members cannot pass exact-party authorization

[InformationRequestRequirementPolicyEvaluator.kt:92](C:/Users/Black/IdeaProjects/doc-hyphen/src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestRequirementPolicyEvaluator.kt:92)

Registration upgrade grants an App User a request Share and revokes the Participant's bootstrap access, but leaves the party principal as PARTICIPANT. Requirement authorization accepts only exact principal equality or an explicit delegated-authority record; no authorization reader consumes ParticipantAccountLink. The upgraded User consequently loses Field read/respond access. The same equality rejects ordinary User members of a PRINCIPAL_GROUP party despite their inherited aggregate Shares. Resolve verified account links and actual group membership into canonical assignment facts, retaining historical provenance and rechecking revocation.

## 12. P1: OTP verification has no attempt limit, and bootstrap use limits never advance

[InformationRequestContactProofService.kt:72](C:/Users/Black/IdeaProjects/doc-hyphen/src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestContactProofService.kt:72)

The public verification method accepts guesses against a six-digit OTP for its ten-minute validity window without invoking the existing auth rate limiter, persisting failed-attempt limits, or applying a verification delay. The one-second floor is only on challenge issuance and does not constrain parallel calls. Separately, resolveBootstrapLink checks shareLink.usedCount against maxUses, but successful verification/read paths only increment RequestAccessSession.useCount, so a positive bootstrap maxUses limit is never exhausted. Apply bounded verification and atomic use accounting at the appropriate credential operation; add concurrency and exhaustion tests.

## 13. P1: Response receipt replay skips current authorization

[InformationRequestResponseDraftService.kt:491](C:/Users/Black/IdeaProjects/doc-hyphen/src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestResponseDraftService.kt:491)

Save a disposition/narrative, revoke or reassign that caller's party Share, then replay the original authenticated PATCH with the same key. The receipt branch bypasses mutate and its authorization, parent-state, and grant checks, and loads current response envelopes. For a narrative-only patch it performs no authorization call at all. The resource filters by the caller-supplied Requirement IDs, which are not a current permission check, so the old caller can read a replacement respondent's later narrative. Reauthorize result disclosure on replay and keep the returned representation consistent with its recorded ETag.

## 14. P1: Existing-work access still depends on live commercial gates and the respondent's plan

[InformationRequestQueryService.kt:48](C:/Users/Black/IdeaProjects/doc-hyphen/src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestQueryService.kt:48)

The detail/workspace read always calls requireRequestAccess, which checks the owner's current feature and rollout gates without consulting the frozen execution grant. Withdrawing a live grant can therefore make an issued request unreadable while its response mutation path still permits continuation. Independently, InformationRequestStructuredResponsePanel uses usePlanFeature for the signed-in respondent and disables the workspace unless that respondent's own plan includes INFORMATION_REQUESTS; the token route bypasses this check. Use server-projected, owner-funded access for issued work on both surfaces. A registered free-plan respondent must not need their own commercial grant.

## 15. P1: Nested conditions and the nested editor remain incomplete

[InformationRequestConditionEvaluationService.kt:111](C:/Users/Black/IdeaProjects/doc-hyphen/src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestConditionEvaluationService.kt:111)

A condition on items[0]/entries[0] cannot read a Field collected on items[0]: evaluation overlays only the root and exact child sets. The result becomes UNKNOWN and the conditional child silently leaves completeness. The plan's latest result acknowledges this, but arbitrary nesting is marked complete and authoring does not reject the unsupported configuration. The frontend separately parses items[0]/entries[1] as group items, groups unrelated descendants as siblings, and omits parentOccurrenceId from add/reorder calls. Implement ancestor-aware evaluation and identity-based nested controls, or explicitly reject unsupported authoring. The nested group-key frontend defect is reproduced by a focused probe.

## 16. P2: Retained responses are not reactivated when a condition becomes true

[InformationRequestResponseDraftService.kt:420](C:/Users/Black/IdeaProjects/doc-hyphen/src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestResponseDraftService.kt:420)

After a RETAIN_SECURELY response is hidden, change its controlling answer so the rule is true again. enforceHiddenResponsePolicies filters to non-TRUE evaluations and never restores the retained envelope's activeInResponse state. The answer disappears from active response/completeness calculations until the user explicitly patches that Requirement again, even though the retained answer should remain reusable. Define the true-state transition for each policy and restore retained data without altering its authorship. The focused backend review probe checks this false-to-true transition.

## 17. P2: An empty Value Set is counted as a complete structured answer

[InformationRequestCompletenessProgressService.kt:137](C:/Users/Black/IdeaProjects/doc-hyphen/src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestCompletenessProgressService.kt:137)

hasStructuredResponse treats any non-null fieldValueSetId as complete without inspecting the collected Field. A first patch with fieldValues.entries empty, or an explicit clear of a non-required Schema Field used by a required request Requirement, still links the Value Set and contributes to the numerator. It can report 100 percent while the requested Field is unanswered. Inspect the exact collected Field's current canonical value and the applicable allowed exception disposition. Preserve incomplete draft saves; this finding concerns truthful progress, not introducing Phase 7 submission enforcement early.

## 18. P2: The execution reservation ledger has no production caller

[InformationRequestExecutionGrantService.kt:51](C:/Users/Black/IdeaProjects/doc-hyphen/src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestExecutionGrantService.kt:51)

Phase 4 marks issuance reservations and reserved-cap continuation complete, but issueGrant only persists a grant, and no production caller invokes InformationRequestExecutionUsageReservationService.reserve/consume/release/rollback. Party assignment paths do not consult the frozen recipient cap or reserve capacity. The concurrency tests prove the isolated ledger, not quota enforcement by actual request commands. Wire issuance and participant expansion/release through the ledger before treating the Phase 4 capacity exit criterion as met. Evidence/upload reservations can remain with their later phases; already-implemented recipient capacity cannot.

## 19. P2: The existing runtime persistence fixture is incompatible with V102

[InformationRequestRuntimePersistenceContractTest.kt:993](C:/Users/Black/IdeaProjects/doc-hyphen/src/test/kotlin/com/docuhyphen/app/api/migration/InformationRequestRuntimePersistenceContractTest.kt:993)

The Docker-backed run reaches `delegated authority rows are scoped to their assigned party and optional Requirement` and errors before its intended assertions: `grantor_principal_kind` is null. The insert helper still writes the old delegated-authority shape, omitting the grantor columns and explicit effective_at required by V102. Update the fixture to the current schema and rerun its positive and scope-refusal cases. This is an observed test-fixture failure, not evidence that the production delegated-authority service writes invalid rows.

## 20. P2: The TypeScript gate is a no-op and misses invalid new test fixtures

[tsconfig.json:2](C:/Users/Black/IdeaProjects/doc-hyphen/web-app/tsconfig.json:2), [InformationRequestTemplatesTab.test.tsx:98](C:/Users/Black/IdeaProjects/doc-hyphen/web-app/src/app/settings/information-request-templates-tab/InformationRequestTemplatesTab.test.tsx:98)

The documented `npx tsc --noEmit` succeeds against a root configuration with files: [] and project references; without build mode it does not check the referenced app sources. Running `npx tsc -p tsconfig.app.json --noEmit` actually checks them and reports 349 errors. Much of this is the already-documented backlog, but the new Information Request Templates test file also fails: it uses nonexistent FieldValueType.TEXT and constructs a Template Version missing newly required properties. Do not describe the successful root command as a clean application type check. Correct the new fixtures and use a meaningful changed-scope/baseline-aware type-check gate until the pre-existing backlog is cleared.

## Verification

- Existing backend suite: `.\mvnw.cmd test` completed with Docker available: 2,453 tests, 0 assertion failures, 1 error, 0 skipped. The error is the outdated delegated-authority fixture in finding 19. The 2,452 remaining tests passed; there are no missing-Docker errors in this run.
- Existing frontend suite: 114 files, 467 tests passed using `npm test -- --run`.
- Documented TypeScript command: `npx tsc --noEmit` passed, but does not check app sources (finding 20).
- Actual app type check: `npx tsc -p tsconfig.app.json --noEmit` failed with 349 errors, including the new Template test fixtures and the known unrelated backlog.
- Frontend review probes: 2 tests, both failed for the expected behavior defects: nested group resolution and runtime Requirement correlation.
- Backend review probes: `.\mvnw.cmd -o kotlin:test-compile surefire:test "-Dtest=PhaseReviewResponseProbeTest#review*,PhaseReviewGroupProbeTest#review*"` ran 3 tests; all 3 failed for the expected behavioral reasons, with 0 errors and 0 skipped. They reproduce the shared-Value-Set stale precondition, reused items[1] path after remove/reorder, and retained response remaining inactive after its condition becomes true.
- The initial sandboxed test attempts failed before execution because Maven could not bootstrap and Node could not traverse the user directory. Approved runs outside the sandbox reached the suites.

Full logs and temporary probe source copies are preserved under `target/phase-review/`. The probes and their compiled classes were removed from the normal source/test output directories after execution, so subsequent normal test runs do not pick up these deliberately failing review probes. No existing source or test file was edited.

- [Backend suite log](C:/Users/Black/IdeaProjects/doc-hyphen/target/phase-review/review-backend.log)
- [Backend probe log](C:/Users/Black/IdeaProjects/doc-hyphen/target/phase-review/review-backend-probes.log)
- [Frontend suite log](C:/Users/Black/IdeaProjects/doc-hyphen/target/phase-review/review-frontend.log)
- [Frontend probe log](C:/Users/Black/IdeaProjects/doc-hyphen/target/phase-review/review-frontend-probes.log)
- [App type-check log](C:/Users/Black/IdeaProjects/doc-hyphen/target/phase-review/review-typescript-app.log)

## Scope and handoff

The Phase 1 Fields foundations and Phase 2 ownership/versioning paths were inspected alongside the runtime integration; no additional isolated Phase 1 defect is asserted here. The major findings are where Phase 3-5 services compose those foundations. This review does not certify that every possible defect has been found.

The plan still says Phase 5 is in progress, with its exit gate pending. It also contains stale text saying occurrence-aware conditions are unimplemented despite P5-T10, and saying request-party reassignment does not exist despite InformationRequestPartyService.reassignMutation. P3-T11 dependency statements should be reconciled with the now-existing session, response, and reassignment services. Do not mark these tasks complete merely because the full suite passes.

The item-level correction allowlist awaiting Phase 8, evidence/submission/review features assigned to Phases 6-8, and the separate malware-scanner approval gate are intentionally deferred and are not reported as missing implementations. Historical Field revision retention is also intentional; finding 3 concerns current values and active API projections, not physical purge of history.

Recommended sequence: close credential and authorization gaps first; then correct response/occurrence semantics and client correlation; wire owner-funded continuation and capacity; add integrated regression cases for each finding; rerun the full gate and reconcile the plan/evidence status before Phase 6.
