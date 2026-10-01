# Signup and Sign-in Security Remediation Plan

Date: 2026-09-30

Status: The five original findings, the implementation sub-findings, and AUTH-3B from the 2026-10-01 recheck are addressed. Focused tests pass. Not committed.

Scope: Internal signup and password sign-in, anonymous sign-in discovery, and the shared authentication controls used by those flows. The findings below come from a static source review. Confirm behavior with focused tests before changing production code.

## Pre-implementation verification (2026-10-01)

- The cited code is unchanged since this plan was written. All five findings were confirmed by reading the current code, and every evidence reference still points at the described logic. `SignUpResource.initiateSignUp` spans lines 36-82.
- AUTH-1 has a wider impact than first described. A guessed code for an invited recipient's address upgrades that recipient's placeholder account in place, together with every Exchange shared with it.
- Five related gaps were found. Each is tracked as a sub-finding under the AUTH item whose code it shares: AUTH-1A, AUTH-1B, AUTH-1C, AUTH-3A, and AUTH-4A.
- AUTH-2 decision: anonymous discovery routes only by verified organization domain, and the organization picker shown before authentication is removed. Password sign-in never used the selected organization, and the account menu already switches workspace after sign-in. Members whose email domain their organization has not verified use the standard sign-in options. This matches the documented rule that a domain must be verified before it can route sign-in.

## Findings and intended outcomes

| ID | Severity | Finding | Intended outcome |
|---|---|---|---|
| AUTH-1 | High | Wrong signup OTP attempts roll back with the completion transaction. | Each failed attempt is durably counted, and concurrent attempts cannot exceed the configured limit. |
| AUTH-1A | Medium | Signup completion reports whether an account exists before any code is verified. | Completion reveals account state only after the caller proves mailbox control. |
| AUTH-1B | Medium | Starting signup again keeps the previous attempt count and status. | A newly issued code starts with a fresh attempt budget, so persisted attempts cannot lock out a later signup. |
| AUTH-1C | Low | Sign-in verification attempts are counted without a row lock. | Parallel sign-in verification attempts are serialized and cannot exceed the configured limit. |
| AUTH-2 | Medium | Anonymous sign-in lookup reveals a registered user's organization memberships. | Unverified callers cannot distinguish a user's memberships or obtain organization names and IDs. |
| AUTH-3 | Medium | Signup initiation has no request rate limit. | Bulk requests across distinct addresses are bounded before code generation and email delivery. |
| AUTH-3A | Medium | Signup code resend skips placeholder accounts, and its cooldown and lock responses reveal pending signups. | Invited recipients can request a new code, and resend responses neither reveal signup state nor let other callers lock a pending signup. |
| AUTH-4 | Medium | A signup confirmation link is consumed before password validation. | Correctable input errors do not invalidate a usable link, while successful redemption remains single use. |
| AUTH-4A | Low | The signup completion email is sent inside the completion transaction without error handling. | Email delivery failure cannot undo a completed signup, and no completion email is sent for a signup that did not commit. |
| AUTH-5 | Low | Password sign-in identifies temporary accounts before password validation. | Unknown and temporary accounts have indistinguishable pre-verification responses. |

## AUTH-1: Persist and serialize failed signup OTP attempts

Evidence: `SignUpService.completeSignUp` is `@Transactional` at `src/main/kotlin/com/docuhyphen/app/api/service/auth/SignUpService.kt:344`. `handleAttempts` increments and updates `otpAttempts` at lines 445-451. `ensureOtpValidity` throws `InvalidOtpException` on a wrong code at lines 484-487. That exception extends `RuntimeException`, so the outer transaction rolls back the counter update. The current test search found no signup completion test covering persisted failed attempts.

Remediation:

1. Add a service-level verification operation that loads the signup record with a row lock, checks its status and expiry, increments the attempt count, and persists a failed attempt independently of the completion transaction. An equivalent atomic database update is acceptable if it enforces the same limit under concurrency.
2. Reject an attempt when the persisted limit or lock status has been reached. Keep the response generic enough to avoid exposing signup state to unrelated callers.
3. Complete account creation only after the OTP has been validated. Ensure a successful completion cannot be repeated against the same signup record.
4. Keep the existing Redis and PostgreSQL services. This work requires no new cloud service.

Verification:

- Wrong codes increment the stored count after the API returns an error.
- The configured limit remains effective across requests and application instances.
- Parallel wrong-code requests cannot each observe the same remaining attempt.
- A correct code after the limit is rejected; a valid code before the limit completes once.

### AUTH-1A: Reveal account state only after code verification

Evidence: `validateInputs` checks `appUserRepository.findActiveByEmail` before the code is checked (`SignUpService.kt:371-373`), and `SignUpResource.completeSignUp` returns the exception message with status 400. A request containing only an email distinguishes registered addresses ("An account with this email already exists") from other addresses ("Verification code is required"). A missing signup record (`EmailNotFoundException`) and a wrong code also return different messages. The endpoint has no rate limit.

Remediation:

1. Validate the request shape, then verify the code. Report an existing account only after the code has been verified.
2. Return one failure response for a missing signup, a completed signup, an expired code, a wrong code, and an exhausted attempt budget.

Verification:

- Unknown, registered, and pending addresses receive the same response for a completion request whose code has not been verified.

### AUTH-1B: Reset attempt state when a new code is issued

Evidence: `initiateSignUp` replaces the code and expiry of an existing signup record (`SignUpService.kt:96-101`) but keeps `otpAttempts` and `status`. While AUTH-1 is open, failed attempts never persist, so this is hidden. Once they persist, an exhausted budget would carry over to every later code.

Remediation:

1. Reset the attempt count and status whenever a new code is issued, as `regenerateOtp` already does.

Verification:

- After the attempt budget is exhausted and signup is started again with a new code, the new code completes signup.

### AUTH-1C: Serialize sign-in verification attempts

Evidence: `SignInService.completeSignIn` persists failed attempts through `dontRollbackOn` (`SignInService.kt:150`), but loads the MFA record without a lock (`MfaRecordRepository.findByEmailAndSessionId`). Parallel wrong codes read the same count. The caller must know the password first, so this is lower priority than AUTH-1.

Remediation:

1. Load the MFA record with a row lock in each transaction that changes it.

Verification:

- Parallel wrong-code requests for one sign-in verification session cannot exceed the configured limit.

## AUTH-2: Stop exposing memberships during anonymous discovery

Evidence: `SignInLookupService.lookup` calls `activeMembershipOrganizations` based only on the submitted email at `src/main/kotlin/com/docuhyphen/app/api/service/auth/SignInLookupService.kt:45-69`. The lookup loads active memberships at lines 183-195 and returns organization names and IDs at lines 315-319. The endpoint is public at `src/main/kotlin/com/docuhyphen/app/api/resource/auth/SignInResource.kt:62`.

Remediation:

1. Map the current sign-in UI cases that require organization selection, including users with multiple memberships and organization-specific identity providers.
2. Return only non-user-specific provider guidance before identity proof. Move membership selection until after password and MFA completion or an external provider has authenticated the person. A domain-level provider hint may remain if it does not reveal whether a particular account belongs to an organization.
3. Preserve the existing rate limits as abuse controls, but do not treat them as authorization to disclose membership data.

Decision (2026-10-01): the lookup routes only by verified organization domain and no longer accepts an organization selection. The sign-in page drops its organization picker. The help text in `web-app/src/app/components/help-docs/sections/identitySection.tsx` that describes organization choices before authentication must be rewritten.

Verification:

- Anonymous lookups for a registered member and a nonmember with the same email domain do not reveal different membership lists, names, or IDs.
- Users with one or multiple organizations can still select the right context after authentication.
- Organization and provider policies continue to be enforced by the server when the session is created and used.

## AUTH-3: Bound signup initiation abuse

Evidence: `SignUpResource.initiateSignUp` at `src/main/kotlin/com/docuhyphen/app/api/resource/auth/SignUpResource.kt:36-65` delegates directly to `SignUpService.initiateSignUp`. The service generates an OTP and sends email at `src/main/kotlin/com/docuhyphen/app/api/service/auth/SignUpService.kt:84-121`. Neither path calls `AuthRateLimitService`. The fixed response floor limits individual response speed but does not cap concurrent requests or requests across distinct email addresses.

Remediation:

1. Apply the existing Redis-backed limiter before OTP generation and email delivery. Use a client-IP budget and a distinct-address budget to contain bulk signup attempts. Consider a per-address resend budget without letting an attacker permanently block the address owner.
2. Keep responses for existing and eligible addresses indistinguishable. Record rejected bursts in the existing security audit and incident mechanisms.
3. Review signup OTP regeneration under the same abuse model and align its limits and response behavior where needed.

Verification:

- Requests above each configured budget stop before an OTP record or email is created.
- Distributed requests for one address and requests for many addresses from one origin are both bounded.
- Legitimate users can retry after the budget window, and responses do not expose account existence.

### AUTH-3A: Align signup code resend with the abuse model

Evidence: `regenerateOtp` checks `appUserRepository.findByEmail` (`SignUpService.kt:148`), which includes the placeholder accounts created for invited Exchange recipients. Those recipients receive the generic success response and no email. Within the resend cooldown, three requests from any caller set `OTP_LOCKED`, which blocks resend for 15 minutes and code entry for 10 minutes. The cooldown and lock responses use status 400, while unknown addresses receive status 200.

Remediation:

1. Use the registered-account check that excludes placeholder accounts.
2. Replace the per-record cooldown and lock with a per-address cooldown that applies whether or not a signup exists, so every address receives the same responses.
3. Stop resend limits from blocking code entry. The completion attempt budget from AUTH-1 already bounds guessing.
4. Apply the client-IP and distinct-address budgets from AUTH-3.

Verification:

- An invited recipient can request a new code and complete signup.
- Resend responses are the same for unknown, registered, and pending addresses.
- Resend requests from another caller cannot block code entry for a pending signup.

## AUTH-4: Preserve links after invalid password input

Evidence: `SignUpService.completeSignUpViaToken` calls Redis `GETDEL` at `src/main/kotlin/com/docuhyphen/app/api/service/auth/SignUpService.kt:273-280`, before checking the password, confirmation, account state, and signup record. A subsequent validation error leaves the token consumed even though no account was created.

Remediation:

1. Resolve the email using the existing non-consuming token lookup, then validate the password fields and signup eligibility.
2. Redeem the token atomically only when the request is ready to finalize. Verify that the consumed token still resolves to the same email. Define retry behavior for database failure after redemption, such as idempotent finalization or a short-lived redemption state, while preserving single-use semantics under concurrent submissions.
3. Keep invalid and expired token responses uniform.

Approach (2026-10-01): completion locks the signup record, rejects a record that is already verified, and creates the account in the same transaction. The token is deleted only after that transaction commits. Single use therefore rests on the locked record's verified state, and a failure before commit leaves the link usable for a retry.

Verification:

- A weak or mismatched password leaves a valid link usable for correction.
- An invalid or expired link cannot complete signup.
- Concurrent valid submissions yield at most one completed account.
- A simulated persistence failure has a documented, tested recovery path.

### AUTH-4A: Keep completion email failures from undoing signup

Evidence: `finalizeSignUp` sends the completion email inside the transaction without error handling (`SignUpService.kt:547-553`). An SES failure rolls back account creation. On the link path, the confirmation token has already been consumed by then.

Remediation:

1. Send the completion email only after the transaction commits, and log delivery failures without failing the request.

Verification:

- A failed completion email still leaves a completed account.
- A signup that rolls back sends no completion email.

## AUTH-5: Remove temporary-account enumeration from password sign-in

Evidence: `SignInService.initiateSignIn` checks `isTemporary` before validating the password at `src/main/kotlin/com/docuhyphen/app/api/service/auth/SignInService.kt:85-109`. `SignInResource` returns a distinct `SIGN_UP_REQUIRED` response at `src/main/kotlin/com/docuhyphen/app/api/resource/auth/SignInResource.kt:206-219`. The inactive and deprovisioned branch at `SignInService.kt:96-100` also runs before the password check and returns a distinct 403. Placeholder accounts have no password, so validating the password first makes them indistinguishable from unknown addresses.

Remediation:

1. Return the same pre-verification credential response for unknown and temporary accounts.
2. Provide the signup guidance through a flow that proves mailbox control, such as the existing signup verification email, rather than revealing placeholder status from sign-in.
3. Check other early account-state branches for the same disclosure pattern while preserving actionable guidance after identity proof.

Verification:

- Unknown and temporary accounts receive the same status, response body, and comparable timing for an incorrect password.
- A legitimate temporary account owner can complete signup through email verification.

## Implementation order and handoff

1. Fix AUTH-1 with AUTH-1A and AUTH-1B, and add persistence and concurrency tests first. It affects the effectiveness of the signup OTP security boundary. Apply the same serialization to sign-in verification for AUTH-1C.
2. Add AUTH-3 limits so public signup entry points are bounded, and align resend behavior for AUTH-3A.
3. Implement AUTH-4 and AUTH-4A with explicit failure and concurrency behavior.
4. Redesign the AUTH-2 lookup response and update the sign-in UI flow together.
5. Resolve AUTH-5 and check all pre-verification account-state responses for consistency.

For each change, inspect the related help articles in `web-app/src/app/components/help-docs/sections/`, update any inaccurate user guidance, and run `npx tsc --noEmit` inside `web-app/` after frontend or help-doc edits. Run focused backend tests for the changed service and resource paths. Review changed production code, tests, fixtures, APIs, and shipped configuration for industry-specific names or rules before closing the work. No Git commit or push is authorized by this plan.

## Implementation record (2026-10-01)

Baseline: before any production change, the new focused tests were run against the original code. Six of seven signup completion tests failed (failed attempts were never stored, a correct code still worked after the limit, and unknown, registered, and pending addresses received three different errors). The parallel sign-in test checked 8 wrong codes against a limit of 3.

| ID | Implementation |
|---|---|
| AUTH-1 | `SignUpService.completeSignUp` locks the signup record with `SELECT ... FOR UPDATE NOWAIT`, persists each wrong code through `dontRollbackOn`, rejects a verified record, and stops checking codes once the budget is used. A competing request fails fast instead of waiting for a database connection. |
| AUTH-1A | Request shape is validated first. A missing, completed, expired, exhausted, or wrong code all return `SignUpVerificationRejectedException` with one message. An existing account is reported only after the code is verified. |
| AUTH-1B | Issuing a code through initiation or resend resets the attempt count and status. |
| AUTH-1C | `MfaRecordRepository.lockById` takes the row lock with a join-free statement and then re-reads the record. `refresh(entity, PESSIMISTIC_WRITE)` was not enough because Hibernate used follow-on locking for the eager `appUser` join and read the row before locking it. |
| AUTH-2 | `SignInLookupService` answers only from the verified organization domain or the platform providers. `orgId`, `outcome`, and `organizations` were removed from the lookup API, and the sign-in page no longer has an organization picker. |
| AUTH-3 | `SignUpRequestGuard` enforces a per-IP budget for initiation, resend, and completion, plus a per-IP distinct-address budget, before any code or email is created. Rejections record an `AUTH_RATE_LIMIT_SIGN_UP` incident in its own transaction and a `SIGN_UP_*` audit event. New settings: `app.auth.rate-limit.sign-up.per-minute`, `app.auth.rate-limit.sign-up-completion.per-minute`, `app.auth.rate-limit.sign-up.distinct-addresses`, and `app.auth.sign-up.resend-cooldown-seconds`. |
| AUTH-3A | Resend uses the placeholder-excluding account check and a Redis per-address cooldown that applies to every address. The per-record lock and cooldown were removed, and migration `V151__sign_up_resend_state_removal.sql` drops `otp_regeneration_attempts` and `last_regeneration_attempt_time` and the `OTP_LOCKED` and `EXPIRED` statuses. |
| AUTH-4 | The link path peeks the token, validates the password, locks the signup record, and creates the account. `SignUpCompletionFollowUpService` revokes the token only after commit. The link page switches to the invalid-link view only for the `SIGN_UP_LINK_INVALID` reason code. |
| AUTH-4A | The completion email is sent after commit, and a delivery failure is logged without failing the request. |
| AUTH-5 | `SignInService.initiateSignIn` validates the password before the placeholder and inactive-account branches. |

Supporting changes: `SignUpResource` moved its JAX-RS annotations to `resource/auth/operations/SignUpResourceOperations` and reads the request ID header from `resource/RequestHeaders`. `SignIn.tsx` and `SignUpEmailConfirm.tsx` were split into a flow hook and small step components. The audit catalog gained three `auth.sign_up.*` events (catalog version 30), and the identity help article now describes discovery before sign-in.

Tests: `SignUpCompletionIntegrationTest` (14, Postgres container), `SignUpAbuseControlIntegrationTest` (5, Postgres and Redis containers), `SignInVerificationAttemptIntegrationTest`, `SignInLookupServiceTest`, `SignInServiceTest`, `AuditEventTypeTest`, and the Exchange permutation suites pass. In `web-app`, `npx tsc --noEmit`, ESLint on the changed files, `vite build`, and the full Vitest suite (794 tests) pass.

Industry-neutral review: the changed production code, configuration keys, audit keys, migration, API fields, tests, and fixtures use only generic sign-up, verification, organization, and Exchange vocabulary with synthetic `example.test` data. No industry-specific names or rules were introduced.

Deployment note: `V151` drops two `sign_up` columns. An application instance from before this change fails on signup writes once the migration has run, so old instances should be drained during the rollout. V151 was inside the range the Information Requests plan reserved. Its ledger now records this allocation, and that program's remaining range is V152 through V160. `CleanSchemaMigrationContractTest` passes with V151.

Residual risk: resend issues a new code with a fresh attempt budget, so a caller who repeatedly requests codes for one address gets three guesses per cooldown window. Every resend emails the address owner, and the email link stays available to the owner when code entry is exhausted.

## Independent recheck (2026-10-01)

Scope: Current working-tree implementation of the five original findings and their recorded sub-findings. This recheck reviewed the service, repository, Redis limiter, HTTP adapter, frontend flow, and regression tests. It did not inspect a deployed environment.

| Item | Recheck result | Current evidence |
|---|---|---|
| AUTH-1, AUTH-1A, AUTH-1B | Addressed | `SignUpService.completeSignUp` uses `dontRollbackOn` for the verification rejection, acquires a database row lock, and checks the persisted attempt budget. Code issuance resets the budget and status. Unknown, registered, and pending addresses receive the same unverified completion response. |
| AUTH-1C | Addressed | The sign-in path locks and refreshes its MFA record before checking or changing the attempt count. The parallel verification integration test passes. |
| AUTH-2 | Addressed | Lookup has no user or membership repository dependency and resolves provider guidance from verified domains. The lookup API no longer carries membership choices, and the frontend organization picker was removed from sign-in. |
| AUTH-3 | Addressed | `SignUpRequestGuard` applies client and distinct-address budgets before creating codes and sending email. Rate limiting is enabled by default. |
| AUTH-3A | Addressed for the reported resend behavior; additional cross-operation gap below | Placeholder recipients can resend, all address states use the same cooldown behavior, and resend no longer changes a pending record's lock status. Initiation can still extend that cooldown without issuing a code. |
| AUTH-4, AUTH-4A | Addressed | Link completion validates fields before finalization, serializes completion through the signup record, and schedules token revocation and completion email only after commit. Rollback, concurrent submission, weak-password retry, and email-failure tests pass. |
| AUTH-5 | Addressed | Password validation precedes temporary, inactive, and deprovisioned account-state responses. The placeholder and inactive-account regression tests pass. |

Fresh validation: Maven 3.9.9 ran `SignUpCompletionIntegrationTest`, `SignUpAbuseControlIntegrationTest`, `SignInVerificationAttemptIntegrationTest`, `SignInLookupServiceTest`, and `SignInServiceTest`. All 31 tests passed with no failures, errors, or skips. `npx tsc --noEmit` in `web-app/` also passed. The existing tests do not cover the initiation/resend sequence below. The new finding is confirmed from the current source path, without a live attack or a new regression test. This review changed only the plan and its index.

### AUTH-3B: Initiation can keep another caller's resend cooldown active

Severity: Medium. Status: Fixed on 2026-10-01; see the resolution below.

Evidence:

- `SignUpService.kt:64` calls `enforceInitiationAddressBudget` before checking whether a code is already active at lines 72-79. The active-code branch returns without sending a new email.
- `SignUpRequestGuard.kt:32-38` starts the same per-address cooldown used by resend on every accepted initiation request.
- `RedisRateLimiter.kt:26-34` uses unconditional `SET ... EX` in `startCooldown`, replacing the existing expiry. `claimCooldown` uses `SET ... NX`, so resend preserves the timer while initiation extends it.

Reproduction sequence: Start signup for an address, then submit initiation for that same address once per minute. Each request remains below the default per-IP budget of 10 per minute and consumes only one distinct address, but resets the shared 180-second resend cooldown. A request to resend therefore continues receiving the cooldown response. While the current code remains valid, these initiation requests send no replacement email. When it expires, a later initiation issues a new code. Existing valid codes and links can still complete signup; the exposed behavior is forced delay and repeated interruption of the owner's resend action.

Remediation:

1. Do not extend an existing cooldown when initiation issues no new verification email. Use an atomic conditional cooldown operation that preserves an existing expiry, and coordinate it with actual code issuance so repeated calls cannot refresh the timer indefinitely.
2. Keep the initiation and resend budgets effective across instances and preserve uniform pre-verification responses across address states.
3. Add a regression test that mixes initiation and resend using a real Redis container. Start with a pending code, repeat initiation within the cooldown window, and confirm resend becomes available at the original deadline rather than at the last initiation request's deadline.
4. Also test the repeated initiation sequence from different client IPs and verify the existing code remains usable throughout.

Completion gate: Close AUTH-3B only after the new regression fails against the current implementation and passes with the fix. Rerun the focused abuse-control and signup-completion tests, then update this recheck record.

#### AUTH-3B verification (2026-10-01)

Finding: confirmed. The cited lines match the current source; the active-code branch now spans `SignUpService.kt:72-80` after reformatting. The cooldown write precedes the account and active-code checks, so every accepted initiation resets it, including for registered addresses.

Regression evidence: `SignUpAbuseControlIntegrationTest` (Postgres and Redis containers, 2-second test cooldown) now has three tests for this item.

| Test | Current code | Initiation never writes the cooldown | Initiation arms the cooldown only when it issues a code |
|---|---|---|---|
| `initiation cannot extend another caller's resend cooldown` | Fails: resend at the original deadline was refused with "Please wait 2 seconds" after four initiation calls from other client IPs | Passes | Passes |
| `repeated initiation from other origins keeps the active code usable` | Passes | Passes | Passes |
| `initiation followed by resend answers the same for unknown registered and pending addresses` | Passes | Passes | Fails: unknown address got the cooldown response, registered and pending addresses got success |

The two variant columns were applied temporarily and reverted; the production code is unchanged. All other abuse-control tests pass in every column.

Impact: the forced delay is bounded by the code lifetime. Once the active code expires, any initiation, including the attacker's, issues a new code to the owner, and existing codes and links keep working throughout. The owner loses the resend action and sees a countdown that keeps restarting, and can wait up to the code lifetime instead of the cooldown for a new code. Medium matches a CVSS 3.1 base score of 5.3 (AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:N/A:L); the practical impact is limited.

Remediation review:

- Step 1 read as a conditional write that preserves an existing expiry (`SET ... NX`) does not close the issue. Initiation could re-arm the cooldown immediately after each expiry, leaving the owner a window of a few seconds.
- Step 1 read as "arm the cooldown only when initiation issues a code" closes the extension but breaks step 2. Initiation followed by resend then distinguishes an unknown address from a registered or pending one, as the third test shows.
- Recommended replacement for step 1: initiation never writes the resend cooldown. Only resend claims it, before any address-state check, so it stays uniform and initiation can neither extend nor create it. Email volume stays bounded because resend sends at most one code per cooldown and initiation issues a code only when none is active. Remove the then-unused `startCooldown` from `RedisRateLimiter` and `AuthRateLimitService`, and describe `app.auth.sign-up.resend-cooldown-seconds` as the minimum time between resends for one address.
- Step 3: the original deadline is the one set by the last accepted resend. Step 4 is covered by the second test.

#### AUTH-3B resolution (2026-10-01)

The recommended replacement for step 1 was applied. `SignUpRequestGuard.enforceInitiationAddressBudget` now enforces only the distinct-address budget, so initiation no longer writes the resend cooldown. The unused `startCooldown` was removed from `AuthRateLimitService` and `RedisRateLimiter`, and `app.auth.sign-up.resend-cooldown-seconds` is now described as the minimum time between resends for one address. Only resend claims the cooldown, with `SET ... NX` before any address-state check.

Behavior change: resend can now be used immediately after initiation, instead of waiting for a cooldown that initiation used to start. Each resend still claims the same per-address cooldown, so email volume per address stays bounded.

Completion gate: the regression `initiation cannot extend another caller's resend cooldown` failed before the change and passes after it. The focused rerun passed 34 tests with no failures, errors, or skips: `SignUpCompletionIntegrationTest` (14), `SignUpAbuseControlIntegrationTest` (8), `SignInVerificationAttemptIntegrationTest` (1), `SignInLookupServiceTest` (6), and `SignInServiceTest` (5). `ExchangeNewUserAccessPermutationTest` (18) also passed.
