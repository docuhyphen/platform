export const platformAdministrationOverviewArticle = (
    <>
        <p>
            Platform Administration is the global workspace for managing
            DocuHyphen platform access. It is separate from an organization's
            Administration settings and does not require an active organization.
        </p>

        <h3>Who can open it</h3>
        <p>
            The Platform Administration button appears in the top application
            bar only when the current session has the <b>App Admin</b>
            capability. Opening the route directly without that capability
            returns you to Exchanges.
        </p>
        <p>
            The workspace uses a navigation menu on the left and displays the
            selected section on the right. Choose <b>Organizations</b>,
            <b>User Subscriptions</b>, <b>Trial Requests</b>, <b>Platform Content</b>, or
            <b> App Administrators</b>. On smaller
            screens, open the navigation menu from the button above the selected
            section.
        </p>

        <h3>App Administrators</h3>
        <p>
            Use the <b>App Administrators</b> section to list, grant, and revoke
            the global App Admin role. The last effective App Administrator
            cannot be revoked, and you cannot revoke your own assignment.
            Lists, candidate searches, grants, revocations, and denied attempts
            are recorded in Platform Audit.
        </p>

        <h3>Organizations</h3>
        <p>
            Use the <b>Organizations</b> section to search platform organization
            accounts and review account status, tier, purchased seats, aggregate
            active-seat usage, and feature entitlements. Select <b>Edit account</b>
            to activate or deactivate the account, update its verification status,
            subscription status, billing frequency, billing period, grace period,
            purchased seats, or feature entitlements. The editor shows
            purchased, active, and remaining seats. These controls do not open
            organization Settings or expose member identities. Changes record safe
            before-and-after state in Platform Audit and require a change reason.
        </p>
        <p>
            Organization accounts are on the Business plan, which is the only plan
            an organization can hold. Free and Personal are individual plans and
            cannot be assigned to an organization. Purchased seats set the maximum
            active, provisioned organization memberships. When no capacity is set,
            the organization is uncapped until you assign purchased seats.
        </p>
        <p>
            Filters stay at the top and pagination stays at the bottom while the
            organization rows scroll within the available table area.
        </p>

        <h3>User Subscriptions</h3>
        <p>
            Use the <b>User Subscriptions</b> section to search registered users by
            name or email and correct their Free or Personal subscription. Select
            <b>Edit</b> to change the individual plan, lifecycle status, billing
            frequency, current period, or past-due grace period. Business cannot be
            assigned to an individual account. Temporary recipient accounts and
            registered applications are not shown because they are not subscribers.
        </p>
        <p>
            Every change requires a reason and records the previous and updated state
            in Platform Audit. Trialing access permits changes until the trial period
            ends. Past-due access permits changes through its grace period. Suspended
            access is read-only. A canceled subscription remains writable until its
            paid-through period ends, then becomes read-only. Existing data remains
            visible in every status, and existing recipient access continues to use
            the Exchange access rules rather than the recipient&apos;s plan.
        </p>

        <h3>Trial administration</h3>
        <p>
            Eligible users request a Personal trial from Settings, Billing. An authorized
            organization billing user can request a Business trial while that organization is
            active. The request immediately appears in <b>Trial Requests</b>, and every active App
            Administrator receives an in-app notification. Only one request can be pending for an
            owner at a time.
        </p>
        <p>
            In <b>Trial Requests</b>, filter pending, approved, rejected, or all requests. Approval
            requires a positive duration and reason, plus positive trial seats for Business. Rejection
            requires a reason. Decisions require recent step-up authentication, are recorded in
            Platform Audit, and notify the requester in-app. Approval starts the existing Personal or
            Business trial lifecycle; a stale request cannot replace an active trial or paid lifecycle.
        </p>
        <p>
            App Administrators can start or extend a Personal trial for an eligible registered
            user from <b>User Subscriptions</b>. Start is unavailable when paid billing
            or ineligible subscription state already exists. Use <b>Start trial</b> on an eligible
            user row, enter a positive duration and audited reason, then confirm. An active trial
            shows <b>Extend trial</b> instead, where the new end must be after the current end.
        </p>
        <p>
            To manage an eligible organization trial, open the organization editor and select
            <b>Start Business trial</b> or <b>Extend Business trial</b>. Start is unavailable for a
            paid or ineligible lifecycle and otherwise requires a positive duration, seat capacity,
            and audited reason. Defaults are
            14 days for Personal and 30 days with five seats for Business. Trial actions
            require recent step-up authentication. After a successful change, the list and
            current session refresh to show the Trialing status and new period.
        </p>
        <p>
            Use <b>End trial</b> to stop access immediately. Ending a Personal trial moves
            the user to the active Free plan. Ending a Business trial keeps the Business
            subscription and existing configuration readable, but expires its period so
            Business mutations become read-only. Use <b>Convert to paid</b> to select Monthly
            or Annual billing and a future paid-period end. Business conversion also requires
            the purchased seat capacity. Conversion keeps the paid target plan and changes its
            status to Active. Ending and conversion both require an audited reason and recent
            step-up authentication.
        </p>

        <h3>Platform Content</h3>
        <p>
            Use the <b>Platform Content</b> section to create and manage
            platform-scoped workflow templates, Blueprints, communications, and
            Document Library entries, fields, and schemas.
            All editors are fixed to platform scope. Workflow management does not
            load organization workflows, users, groups, or communications.
            Blueprint management requests only APP Blueprints and platform documents
            and schemas. Communication management requests only PLATFORM
            communications. The Blueprint and Communication editors do not load
            organization or personal variables. Document Library management requests
            only APP entries, and its editor does not load organization or personal
            variables. Field and Schema management requests only PLATFORM records,
            and platform schemas can bind only PLATFORM fields. Publishing and
            activation determine whether content is available for users to discover.
        </p>

        <h3>Organization boundaries</h3>
        <p>
            App Admin is a platform role. It does not grant access to
            organization people, groups, security, trusted organizations,
            Exchanges, documents, workflows, communications, or audit evidence.
            Those areas require a separate organization role and matching active
            organization context.
        </p>
    </>
);
