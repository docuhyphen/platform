# Backend Sharing Unit-Test Hardening Plan

## Recommended session configuration

Use GPT-5.6 Sol with Ultra reasoning.

This work spans several independent backend test areas that can be delegated to parallel agents.
Agents must receive non-overlapping test files and must not modify production behavior.

## Objective

Harden backend unit tests for all currently supported Exchange sharing topologies. Produce
parameterized, service-level tests that exercise actual business logic instead of relying on model
field assertions, duplicated implementation logic, or generic authorization probes.

The work must be completed in a single implementation session.

## Scope

### Included

- Kotlin unit tests under `src/test/kotlin`.
- Existing production services exercised using mocked dependencies.
- Shared builders and parameterized scenario sources.
- Personal, organization, external, group, Trusted Organization, and no-sign-in sharing.
- Targeted Maven verification.
- A final coverage and remaining-gap report.

### Excluded

- Production behavior changes.
- PostgreSQL, Quarkus, Testcontainers, and REST integration tests.
- Frontend and browser tests.
- Documentation changes other than this implementation plan.
- New dependencies.
- Infrastructure changes.
- Direct whole-organization sharing because it is not currently implemented.
- Configurable public-link creation because it is not currently implemented.

## Coverage strategy

A literal Cartesian product of every sharing dimension is not practical. Use the following layered
approach:

1. Exhaustively test small decision tables such as roles, actions, Share statuses, and recipient
   selection contracts.
2. Use constrained pairwise cases across sender context, recipient topology, acceptance policy,
   sign-in requirement, recipient purpose, and Share source.
3. Add explicit higher-order cases for security-sensitive intersections such as no-sign-in access,
   temporary identity, acceptance, registration reconciliation, group inheritance, and trust
   changes.

Target approximately 150 to 200 meaningful unit-test invocations. Prefer parameterized tests over
duplicated test methods.

## Implementation work packages

### 1. Establish the baseline

- Inspect the Git working tree and preserve unrelated changes.
- Run the existing sharing permutation and focused service tests.
- Record current test counts and failures.
- Identify tests whose names claim behavior that their assertions do not exercise.

### 2. Build reusable test infrastructure

Create focused test helpers for:

- Active, temporary, inactive, deprovisioned, and nonexistent users.
- Personal and organization sender contexts.
- Organization memberships, roles, and initiation capabilities.
- Trust relationships and directional Exchange policies.
- Personal, internal organization, and Trusted Organization groups.
- Exchanges, recipients, Shares, roles, statuses, sources, and constraints.
- Fixed timestamps, UUIDs, OTPs, and access tokens.
- Standard allowed, denied, state-transition, and interaction assertions.

Use JUnit parameterized tests with `@MethodSource`, `@EnumSource`, or `@CsvSource` where appropriate.
Keep helpers focused and split large scenario catalogs by responsibility.

### 3. Harden sender and recipient topology coverage

Cover:

- Personal user to registered user without an organization.
- Personal user to a new external email.
- Personal user to a personal group.
- Same-organization person and group.
- Organization to a registered customer without an organization.
- Organization to a new external customer.
- Organization to an external-organization user when trust is required.
- Organization to an external-organization user when the trust requirement is disabled.
- Trusted Organization exact-email person.
- Trusted Organization published group.
- Inactive, deprovisioned, suspended, and invalid principals.
- Users with multiple organization memberships.
- Self-recipient rejection.
- Duplicate recipient rejection.
- Primary recipient and participant conflicts.

Exercise `ExchangeRecipientSelectionResolver`, organization policy services, and relevant
`ExchangeInitiationService` behavior.

### 4. Harden sign-in and acceptance coverage

Cover:

- Sign-in required and not required.
- Temporary external-recipient creation.
- Correct, incorrect, missing, expired, and cross-Exchange access tokens.
- Acceptance required, bypassed, accepted, and rejected.
- Primary recipient versus additional participant.
- Trusted recipient forced sign-in and forced acceptance.
- Group Owner, Manager, Member, and Observer decision rights.
- Draft approval, acceptance workflow, organization bypass, and trusted forced-acceptance
  precedence.
- Repeated decisions and invalid state transitions.
- Acceptance after trust, policy, membership, or recipient eligibility changes.

Tests must invoke the responsible service methods instead of asserting only model fields or
timestamps.

### 5. Harden roles, constraints, and Share precedence

Exhaustively cover all Exchange Share roles:

- Owner.
- Editor.
- Reviewer.
- Signer.
- Viewer.
- Commenter.
- Participant.

Test each applicable role against:

- View Exchange.
- Download documents.
- Add, upload, update, and delete documents.
- Comment.
- Sign.
- Manage access.
- Accept or reject as the primary recipient.

Also cover:

- Pending approval, active, revoked, and expired Shares.
- Direct and inherited Shares.
- Multiple-Share capability unions.
- Restrictive direct Shares combined with less-restrictive inherited Shares.
- Watermark obligations.
- MFA requirements.
- Download controls and allowed formats.
- Allowed and denied client IPs.
- Missing client IPs.
- Malformed constraints.
- Exact expiry boundaries.
- Draft and terminal Exchange states.

### 6. Harden Manage access and group behavior

Cover:

- Add a registered user.
- Add a new external email.
- Add personal, internal, and trusted groups where supported.
- Add a trusted person through the dedicated invitation path.
- Change a Share role.
- Revoke access.
- Reject duplicate grants.
- Reject owner and caller self-modification.
- Reject Manage access operations by non-owners.
- Add and remove group members.
- Reconcile inherited Shares.
- Revoke a parent group Share.
- Preserve direct access after inherited access is removed.
- Preserve inherited access after a direct Share is revoked.
- Suspend or end trust before materialization or acceptance.

### 7. Replace misleading shallow tests

Strengthen tests that currently prove less than their names claim, especially:

- `EX-REG-01`, `EX-REG-09`, `EX-REG-13`, and `EX-REG-14`.
- Inactive and deprovisioned recipient cases.
- Suspended and ended trust cases.
- Lifecycle cases represented using only a generic archived authorization context.
- Sender tests that only inspect static role capabilities.
- Tests that only construct an `Exchange` or calculate a timestamp.
- Tests that duplicate expected behavior inside a test-only probe instead of invoking the real
  service.

Do not weaken expectations to match an implementation defect. If intended matrix behavior
conflicts with current production behavior:

1. Do not change production code.
2. Do not disable or ignore a test.
3. Continue with unaffected scenarios.
4. Report the exact behavior discrepancy, affected service, and expected outcome in the final
   handoff.

### 8. Verification

Run the hardened permutation package and all directly affected service and authorization unit
tests. The focused command should include at least:

```powershell
.\mvnw.cmd test -DskipFrontend=true "-Dtest=*PermutationTest,OrganizationExchangePolicyServiceTest,TrustedRecipientValidationServiceTest,ExchangeAccessManagementServiceTest,ExchangeRecipientServiceTest,ExchangeAuthorizationTest,ShareConstraintTest,PublicLinkShareTest"
```

Run the broader backend unit suite when it does not require unavailable external infrastructure.
Do not treat unavailable Docker or integration infrastructure as a unit-test failure.

Inspect the final diff and confirm:

- No production files changed.
- No frontend files changed.
- No infrastructure files changed.
- No dependencies were added.
- Unrelated user changes remain intact.

## Definition of done

- Approximately 150 to 200 meaningful unit-test invocations exist.
- Every supported recipient selection type is covered.
- Every Exchange Share role and Share status is covered.
- Personal, external customer, same-organization, and Trusted Organization paths are covered.
- Sign-in and no-sign-in decision logic is covered.
- Primary-recipient and additional-participant behavior is tested separately.
- Direct, inherited, revoked, expired, and multiple-Share behavior is covered.
- Tests exercise actual service methods and meaningful dependency interactions.
- No tests are disabled or ignored.
- The targeted backend unit suite passes.
- No production, frontend, infrastructure, or dependency changes are made.
- Remaining integration-only gaps and behavior discrepancies are reported clearly.

## Parallel-agent allocation

Use parallel agents only for non-overlapping files:

1. Recipient topology and organization policy tests.
2. Roles, constraints, lifecycle, and multiple-Share authorization tests.
3. Acceptance, no-sign-in, group, and Manage access tests.
4. The primary agent owns shared test infrastructure, integration of agent changes, final test
   execution, diff review, and the coverage report.

Agents must coordinate before changing shared support files. The primary agent must resolve all
overlaps and run the complete targeted suite after agent work is integrated.

## New-session starter prompt

Use the following prompt in the implementation session:

> Implement `docs/BACKEND-SHARING-UNIT-TEST-HARDENING-PLAN.md` in one session. Use GPT-5.6 Sol
> Ultra and parallel subagents with non-overlapping test files. Stay strictly within backend unit
> tests and do not change production behavior, frontend code, dependencies, infrastructure, or
> unrelated files. Build parameterized tests that exercise the real backend services, run the
> targeted backend unit suite, inspect the final diff, and report coverage, test counts, remaining
> integration gaps, and any production-behavior discrepancies.
