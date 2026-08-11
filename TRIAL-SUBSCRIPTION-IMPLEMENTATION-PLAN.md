# Trial Subscription Implementation Plan

## Purpose

Add time-boxed trial access so a user or organization can evaluate the paid feature set before converting to a paid subscription.

The recommended model is not a separate plan code. A trial should be a lifecycle state on an existing paid plan:

- Individual trial: `planCode = PERSONAL`, `subscriptionStatus = TRIALING`
- Organization trial: `tierCode = BUSINESS`, `subscriptionStatus = TRIALING`

This keeps the feature catalog simple. The trial uses the target paid plan's features and limits, while the subscription lifecycle decides whether mutations remain allowed.

## Product Rules

### Who can receive a trial

1. Newly registered individual users
   - Optional Personal trial.
   - Gives access to Personal features such as Blueprints, Document Library, document version history, advanced access controls, variables, and multiple participants.

2. Newly activated organizations
   - Optional Business trial.
   - Gives access to Business features such as organization administration, Business Fields, workflow automation, audit governance, identity integrations, and Business seats.

3. Platform-admin granted trials
   - Platform administrators can start or extend a trial for a specific user or organization.
   - Use cases: sales demos, onboarding, account recovery, billing support, and implementation pilots.

### Who should not receive an automatic trial

1. Temporary no-account recipients.
2. Service accounts.
3. Suspended or abuse-flagged accounts.
4. Owners that already consumed their one-time trial.
5. Canceled subscriptions, unless a platform administrator explicitly grants a new trial.

### Trial duration

Recommended defaults:

- Individual Personal trial: 14 days.
- Organization Business trial: 30 days.
- Platform-admin extensions: configurable per grant.

### Trial expiry behavior

When a trial expires:

- Individual owner with no paid conversion falls back to Free.
- Organization owner should become inactive, read-only, or moved to a non-mutating expired trial state depending on billing policy.
- Existing data remains readable where the product already permits read preservation.
- Paid-only UI surfaces are hidden once the effective subscription no longer includes the feature.
- Normal shared comments remain available across tiers, including Free.
- Internal notes remain organization-context only.

## Subscription Model Changes

### Backend subscription lifecycle

Existing `SubscriptionStatus.TRIALING` should be used as the trial marker.

Confirm or add these lifecycle semantics:

- `TRIALING` permits reads.
- `TRIALING` permits mutations while the trial is active.
- Expired `TRIALING` does not permit paid mutations.
- Trial expiry is evaluated using `currentPeriodEnd` unless a dedicated `trialEnd` field is added.

Recommended first implementation:

- Reuse `currentPeriodStart`.
- Reuse `currentPeriodEnd`.
- Do not add `trialEnd` unless billing integration later needs both trial and billing period dates.

### Trial ownership

User trial:

- Stored in `user_subscription_policy`.
- `plan_code = PERSONAL`.
- `subscription_status = TRIALING`.
- `current_period_start = now`.
- `current_period_end = now + 14 days`.

Organization trial:

- Stored in `organization_subscription_policy`.
- `tier_code = BUSINESS`.
- `subscription_status = TRIALING`.
- `current_period_start = now`.
- `current_period_end = now + 30 days`.
- `max_users` should be set to a trial capacity, for example 3 or 5 seats.

### Prevent repeated automatic trials

Add durable trial history fields or a dedicated table.

Recommended table:

`subscription_trial_grant`

Suggested columns:

- `id`
- `owner_type`
- `owner_id`
- `plan_code`
- `started_at`
- `ended_at`
- `source`
- `granted_by_app_user_id`
- `reason`
- `created_at`

Use this to answer:

- Has this owner already used an automatic trial?
- Was the trial granted manually?
- Who granted or extended it?

## Backend Implementation Steps

### 1. Lifecycle evaluation

Update subscription lifecycle checks so `TRIALING` is mutation-enabled only before `currentPeriodEnd`.

Expected behavior:

- Trial active: paid plan features are available.
- Trial expired: paid mutations are denied.
- Trial expired reads remain allowed.

Add focused tests for:

- Active Personal trial includes Personal features.
- Expired Personal trial refuses Personal paid mutations.
- Active Business trial includes Business features.
- Expired Business trial refuses Business paid mutations.
- Trial expiry does not hide already-readable data.

### 2. Trial provisioning service

Add a service responsible for starting and extending trials.

Suggested service:

`SubscriptionTrialService`

Responsibilities:

- Start user trial.
- Start organization trial.
- Extend trial.
- Refuse repeated automatic trials.
- Record trial grant history.
- Emit audit events.

Do not put this logic in REST resources.

### 3. User sign-up trial hook

If automatic individual trials are enabled:

- During user policy creation, check trial eligibility.
- If eligible, create a Personal trial instead of a Free policy.
- If not eligible, create the normal Free policy.

Make this behavior configurable:

- `app.subscription.trials.user.enabled`
- `app.subscription.trials.user.duration-days`

Default recommendation:

- Disabled in production until product is ready.
- Enabled in local/demo environments if useful.

### 4. Organization activation trial hook

If automatic organization trials are enabled:

- When an organization becomes active, create a Business trial policy.
- Set trial seat capacity.
- Record the trial grant.

Make this behavior configurable:

- `app.subscription.trials.organization.enabled`
- `app.subscription.trials.organization.duration-days`
- `app.subscription.trials.organization.seat-capacity`

### 5. Platform administration

Add platform-admin controls for:

- Start trial.
- Extend trial.
- End trial.
- Convert trial to active paid subscription.

For individual users:

- Add controls in User Subscriptions.

For organizations:

- Add controls in Organization subscription editing.

All actions should require existing platform-admin authorization and step-up approval where subscription changes already require it.

### 6. Trial expiry job

Add scheduled processing for expired trials.

Options:

1. Lazy evaluation only
   - Subscription checks detect expiry at request time.
   - No scheduled status mutation is needed.

2. Scheduled status transition
   - A scheduled job marks expired trials as Free, Past Due, Suspended, or another terminal status.
   - Better for reporting and admin clarity.

Recommended first implementation:

- Use lazy evaluation first.
- Add scheduled status transitions only when billing integration needs explicit state changes.

## Frontend Implementation Steps

### 1. Current plan display

Billing should show:

- Plan code.
- Trial status.
- Trial end date.
- Days remaining.
- Current usage and limits.
- Conversion callout when billing is ready.

For now, keep billing non-transactional and informational.

### 2. Feature visibility

All existing feature visibility should continue to use the effective subscription from the session.

Expected behavior:

- Active trial shows the same UI as the paid target plan.
- Expired trial hides paid-only surfaces once the session refreshes.
- Comments remain visible and postable across all tiers.
- Internal comment controls remain hidden outside active organization context.

### 3. Session refresh

When trial state changes:

- Refresh the current session after platform-admin changes.
- Refresh the current session after subscription mutation responses.
- Consider a lightweight refresh on app focus if trial expiry precision matters.

### 4. Trial messaging

Add clear labels:

- `Personal trial`
- `Business trial`
- `Trial ends {date}`
- `{n} days remaining`

Avoid showing paid-only features as permanent when the subscription is trialing.

## API and REST Considerations

Use resource-based endpoints.

Suggested endpoints:

- `POST /platform/users/{userId}/subscription-trials`
- `PATCH /platform/users/{userId}/subscription-trials/current`
- `DELETE /platform/users/{userId}/subscription-trials/current`
- `POST /platform/organizations/{organizationId}/subscription-trials`
- `PATCH /platform/organizations/{organizationId}/subscription-trials/current`
- `DELETE /platform/organizations/{organizationId}/subscription-trials/current`

Request examples:

- Start trial: `{ "planCode": "PERSONAL", "durationDays": 14, "reason": "Sales demo" }`
- Extend trial: `{ "currentPeriodEnd": "2026-09-10T00:00:00Z", "reason": "Implementation extension" }`

Keep resources thin:

- Validate request shape.
- Delegate to a service.
- Return DTOs.
- Catch and log errors in the resource class.

## Audit Requirements

Add audit events for:

- Trial started.
- Trial extended.
- Trial ended.
- Trial converted.
- Trial expiry applied by scheduled job, if implemented.

Audit payload should include:

- Owner type.
- Owner id.
- Plan code.
- Old status.
- New status.
- Old period end.
- New period end.
- Actor id.
- Reason.

## Testing Plan

### Backend unit tests

Add or update tests for:

- PlanCatalog keeps Free and Personal as user plans and Business as organization plan.
- Active user trial resolves Personal features.
- Active org trial resolves Business features.
- Expired trial refuses mutations.
- Trial history prevents repeated automatic trials.
- Platform-admin extension updates period end.
- Trial transitions emit audit events.

### Backend resource tests

Add contract tests for:

- Starting a user trial.
- Starting an organization trial.
- Extending a trial.
- Ending a trial.
- Authorization failure for non-platform admins.
- Step-up requirement where applicable.

### Frontend unit tests

Add tests for:

- Billing displays trial status and end date.
- Trial user sees Personal features.
- Trial organization sees Business features.
- Expired trial hides paid feature tabs.
- Comments remain available to Free.
- Internal note toggle appears only in active organization context.

### UI tests

Run tier checks for:

- Free.
- Personal.
- Personal trial.
- Business.
- Business trial.
- Expired trial.

Verify:

- Settings menu visibility.
- Billing visibility.
- Exchange creation options.
- Exchange tabs.
- Document sidebar tabs.
- Document comment composer.
- Internal note controls.
- Version history controls.
- Workflow and Business Fields visibility.

## Open Product Decisions

1. Should new individual users automatically receive a Personal trial, or should they opt in?
2. Should new organizations automatically receive a Business trial?
3. What should happen to an expired organization trial with no payment method?
4. What should the default Business trial seat capacity be?
5. Should trial expiry downgrade individual users to Free automatically, or leave them in an expired trial state?
6. Should a platform admin be able to grant more than one trial?
7. Should trialed owners be blocked from future automatic trials only, or all trials?
8. Should billing require a payment method before starting a trial?

## Recommended Initial Decisions

For the first implementation:

- Add platform-admin granted trials first.
- Do not auto-enroll new users yet.
- Do not auto-enroll organizations yet.
- Use `TRIALING` plus `currentPeriodEnd`.
- Add trial grant history before enabling automatic trials.
- Default individual trial duration to 14 days.
- Default organization trial duration to 30 days.
- Default organization trial seats to 5.
- Let active trials use all target paid-plan features.
- Keep comments available across all tiers.
- Keep internal notes organization-context only.

