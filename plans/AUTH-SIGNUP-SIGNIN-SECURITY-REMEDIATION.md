# Signup and Sign-in Security Remediation Plan

Date: 2026-09-30

Status: Proposed. No remediation has been implemented or verified.

Scope: Internal signup and password sign-in, anonymous sign-in discovery, and the shared authentication controls used by those flows. The findings below come from a static source review. Confirm behavior with focused tests before changing production code.

## Findings and intended outcomes

| ID | Severity | Finding | Intended outcome |
|---|---|---|---|
| AUTH-1 | High | Wrong signup OTP attempts roll back with the completion transaction. | Each failed attempt is durably counted, and concurrent attempts cannot exceed the configured limit. |
| AUTH-2 | Medium | Anonymous sign-in lookup reveals a registered user's organization memberships. | Unverified callers cannot distinguish a user's memberships or obtain organization names and IDs. |
| AUTH-3 | Medium | Signup initiation has no request rate limit. | Bulk requests across distinct addresses are bounded before code generation and email delivery. |
| AUTH-4 | Medium | A signup confirmation link is consumed before password validation. | Correctable input errors do not invalidate a usable link, while successful redemption remains single use. |
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

## AUTH-2: Stop exposing memberships during anonymous discovery

Evidence: `SignInLookupService.lookup` calls `activeMembershipOrganizations` based only on the submitted email at `src/main/kotlin/com/docuhyphen/app/api/service/auth/SignInLookupService.kt:45-69`. The lookup loads active memberships at lines 183-195 and returns organization names and IDs at lines 315-319. The endpoint is public at `src/main/kotlin/com/docuhyphen/app/api/resource/auth/SignInResource.kt:62`.

Remediation:

1. Map the current sign-in UI cases that require organization selection, including users with multiple memberships and organization-specific identity providers.
2. Return only non-user-specific provider guidance before identity proof. Move membership selection until after password and MFA completion or an external provider has authenticated the person. A domain-level provider hint may remain if it does not reveal whether a particular account belongs to an organization.
3. Preserve the existing rate limits as abuse controls, but do not treat them as authorization to disclose membership data.

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

## AUTH-4: Preserve links after invalid password input

Evidence: `SignUpService.completeSignUpViaToken` calls Redis `GETDEL` at `src/main/kotlin/com/docuhyphen/app/api/service/auth/SignUpService.kt:273-280`, before checking the password, confirmation, account state, and signup record. A subsequent validation error leaves the token consumed even though no account was created.

Remediation:

1. Resolve the email using the existing non-consuming token lookup, then validate the password fields and signup eligibility.
2. Redeem the token atomically only when the request is ready to finalize. Verify that the consumed token still resolves to the same email. Define retry behavior for database failure after redemption, such as idempotent finalization or a short-lived redemption state, while preserving single-use semantics under concurrent submissions.
3. Keep invalid and expired token responses uniform.

Verification:

- A weak or mismatched password leaves a valid link usable for correction.
- An invalid or expired link cannot complete signup.
- Concurrent valid submissions yield at most one completed account.
- A simulated persistence failure has a documented, tested recovery path.

## AUTH-5: Remove temporary-account enumeration from password sign-in

Evidence: `SignInService.initiateSignIn` checks `isTemporary` before validating the password at `src/main/kotlin/com/docuhyphen/app/api/service/auth/SignInService.kt:85-109`. `SignInResource` returns a distinct `SIGN_UP_REQUIRED` response at `src/main/kotlin/com/docuhyphen/app/api/resource/auth/SignInResource.kt:206-219`.

Remediation:

1. Return the same pre-verification credential response for unknown and temporary accounts.
2. Provide the signup guidance through a flow that proves mailbox control, such as the existing signup verification email, rather than revealing placeholder status from sign-in.
3. Check other early account-state branches for the same disclosure pattern while preserving actionable guidance after identity proof.

Verification:

- Unknown and temporary accounts receive the same status, response body, and comparable timing for an incorrect password.
- A legitimate temporary account owner can complete signup through email verification.

## Implementation order and handoff

1. Fix AUTH-1 and add persistence and concurrency tests first. It affects the effectiveness of the signup OTP security boundary.
2. Add AUTH-3 limits so public signup entry points are bounded.
3. Implement AUTH-4 with explicit failure and concurrency behavior.
4. Redesign the AUTH-2 lookup response and update the sign-in UI flow together.
5. Resolve AUTH-5 and check all pre-verification account-state responses for consistency.

For each change, inspect the related help articles in `web-app/src/app/components/help-docs/sections/`, update any inaccurate user guidance, and run `npx tsc --noEmit` inside `web-app/` after frontend or help-doc edits. Run focused backend tests for the changed service and resource paths. Review changed production code, tests, fixtures, APIs, and shipped configuration for industry-specific names or rules before closing the work. No Git commit or push is authorized by this plan.
