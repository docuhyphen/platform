export const platformAdministrationOverviewArticle = (
    <>
        <p id={"platform-administration-overview-intro-paragraph"}>
            Platform Administration is the global workspace for managing DocuHyphen platform access. It is
            separate from an organization's Administration settings and does not require an active organization.
        </p>

        <h3 id={"platform-administration-overview-who-can-open-it-heading"}>Who can open it</h3>
        <p id={"platform-administration-overview-who-can-open-it-paragraph"}>
            The Platform Administration button appears in the top application
            bar only when the current session has the <b id={"platform-administration-overview-article-b-1"}>App Admin</b>{" "}
            capability. Opening the route directly without that capability
            returns you to Exchanges.
        </p>
        <p id={"platform-administration-overview-who-can-open-it-paragraph-2"}>
            The workspace uses a navigation menu on the left and displays the
            selected section on the right. Choose <b id={"platform-administration-overview-article-b-2"}>Organizations</b>,{" "}
            <b id={"platform-administration-overview-article-b-3"}>User Subscriptions</b>, <b id={"platform-administration-overview-article-b-4"}>Trial Requests</b>, <b id={"platform-administration-overview-article-b-5"}>Platform Content</b>, or
            <b id={"platform-administration-overview-article-b-6"}> App Administrators</b>. On smaller
            screens, open the navigation menu from the button above the selected
            section.
        </p>

        <h3 id={"platform-administration-overview-app-administrators-heading"}>App Administrators</h3>
        <p id={"platform-administration-overview-app-administrators-paragraph"}>
            Use the <b id={"platform-administration-overview-article-b-7"}>App Administrators</b> section to list, grant, and revoke
            the global App Admin role. The last effective App Administrator
            cannot be revoked, and you cannot revoke your own assignment.
            Lists, candidate searches, grants, revocations, and denied attempts
            are recorded in Platform Audit. The earliest effective App Administrator
            receives operational emails for new registered users, organization members,
            organization registrations, trial requests, and sales enquiries.
        </p>

        <h3 id={"platform-administration-overview-organizations-heading"}>Organizations</h3>
        <p id={"platform-administration-overview-organizations-paragraph"}>
            Use the <b id={"platform-administration-overview-article-b-8"}>Organizations</b> section to search platform organization
            accounts and review account status, tier, purchased seats, aggregate
            active-seat usage, and feature entitlements. Select <b id={"platform-administration-overview-article-b-9"}>Edit account</b>{" "}
            to activate or deactivate the account, update its verification status,
            subscription status, billing frequency, billing period, grace period,
            purchased seats, or feature entitlements. The editor shows
            purchased, active, and remaining seats. These controls do not open
            organization Settings or expose member identities. Changes record safe
            before-and-after state in Platform Audit and require a change reason.
        </p>
        <p id={"platform-administration-overview-organizations-paragraph-2"}>
            Organization accounts are on the Business plan, which is the only plan an organization can hold. Free
            and Personal are individual plans and cannot be assigned to an organization. Purchased seats set the
            maximum active, provisioned organization memberships. When no capacity is set, the organization is
            uncapped until you assign purchased seats.
        </p>
        <p id={"platform-administration-overview-organizations-paragraph-3"}>
            Filters stay at the top and pagination stays at the bottom while the
            organization rows scroll within the available table area.
        </p>

        <h3 id={"platform-administration-overview-user-subscriptions-heading"}>User Subscriptions</h3>
        <p id={"platform-administration-overview-user-subscriptions-paragraph"}>
            Use the <b id={"platform-administration-overview-article-b-10"}>User Subscriptions</b> section to search registered users by name or email and correct their Free
            or Personal subscription. Select <b id={"platform-administration-overview-article-b-11"}>Edit</b> to change the individual plan, lifecycle status, billing
            frequency, current period, or past-due grace period. Business cannot be assigned to an individual account.
            Temporary recipient accounts and registered applications are not shown because they are not subscribers.
        </p>
        <p id={"platform-administration-overview-user-subscriptions-paragraph-2"}>
            The editor also lists the account&apos;s <b id={"platform-administration-overview-article-b-12"}>Feature overrides</b>. Choose features under <b id={"platform-administration-overview-article-b-13"}>Features</b>,
            then switch each one on to grant it although the plan leaves it out, or off to withhold it although the plan
            includes it. Removing an override returns the feature to the plan. Changed overrides are saved with the same
            reason and recorded in Platform Audit; overrides that cannot be loaded are left as they are.
        </p>
        <p id={"platform-administration-overview-user-subscriptions-paragraph-3"}>
            Every change requires a reason and records the previous and updated state in Platform Audit. Trialing access
            permits changes until the trial period ends. Past-due access permits changes through its grace period.
            Suspended access is read-only. A canceled subscription remains writable until its paid-through period ends,
            then becomes read-only. Existing data remains visible in every status, and existing recipient access
            continues to use the Exchange access rules rather than the recipient&apos;s plan.
        </p>

        <h3 id={"platform-administration-overview-trial-administration-heading"}>Trial administration</h3>
        <p id={"platform-administration-overview-trial-administration-paragraph"}>
            Eligible users request a Personal trial from Settings, Billing. An authorized
            organization billing user can request a Business trial while that organization is
            active. The request immediately appears in <b id={"platform-administration-overview-article-b-14"}>Trial Requests</b>, and every active App
            Administrator receives an in-app notification. The earliest effective App Administrator
            also receives an email. Only one request can be pending for an owner at a time.
        </p>
        <p id={"platform-administration-overview-trial-administration-paragraph-2"}>
            In <b id={"platform-administration-overview-article-b-15"}>Trial Requests</b>, filter pending, approved, rejected, or all requests. Approval
            requires a positive duration and reason, plus positive trial seats for Business. Rejection
            requires a reason. Decisions require recent step-up authentication, are recorded in
            Platform Audit, and notify the requester in-app. Approval starts the existing Personal or
            Business trial lifecycle; a stale request cannot replace an active trial or paid lifecycle.
        </p>
        <p id={"platform-administration-overview-trial-administration-paragraph-3"}>
            App Administrators can start or extend a Personal trial for an eligible registered
            user from <b id={"platform-administration-overview-article-b-16"}>User Subscriptions</b>. Start is unavailable when paid billing
            or ineligible subscription state already exists. Use <b id={"platform-administration-overview-article-b-17"}>Start trial</b> on an eligible
            user row, enter a positive duration and audited reason, then confirm. An active trial
            shows <b id={"platform-administration-overview-article-b-18"}>Extend trial</b> instead, where the new end must be after the current end.
        </p>
        <p id={"platform-administration-overview-trial-administration-paragraph-4"}>
            To manage an eligible organization trial, open the organization editor and select{" "}
            <b id={"platform-administration-overview-article-b-19"}>Start Business trial</b> or <b id={"platform-administration-overview-article-b-20"}>Extend Business trial</b>. Start is unavailable for a
            paid or ineligible lifecycle and otherwise requires a positive duration, seat capacity,
            and audited reason. Defaults are
            14 days for Personal and 30 days with five seats for Business. Trial actions
            require recent step-up authentication. After a successful change, the list and
            current session refresh to show the Trialing status and new period.
        </p>
        <p id={"platform-administration-overview-trial-administration-paragraph-5"}>
            Use <b id={"platform-administration-overview-article-b-21"}>End trial</b> to stop access immediately. Ending a Personal trial moves
            the user to the active Free plan. Ending a Business trial keeps the Business
            subscription and existing configuration readable, but expires its period so
            Business mutations become read-only. Use <b id={"platform-administration-overview-article-b-22"}>Convert to paid</b> to select Monthly
            or Annual billing and a future paid-period end. Business conversion also requires
            the purchased seat capacity. Conversion keeps the paid target plan and changes its
            status to Active. Ending and conversion both require an audited reason and recent
            step-up authentication.
        </p>

        <h3 id={"platform-administration-overview-platform-content-heading"}>Platform Content</h3>
        <p id={"platform-administration-overview-platform-content-paragraph"}>
            Use the <b id={"platform-administration-overview-article-b-23"}>Platform Content</b> section to create and manage platform-scoped workflow templates,
            Blueprints, communications, and Document Library entries, fields, and schemas. Platform Information
            Request Templates are authored in Settings, <b id={"platform-administration-overview-article-b-24"}>Content - Information Requests</b>, on
            the <b id={"platform-administration-overview-article-b-25"}>Platform</b> tab, which is shown only while your plan or your active organization
            includes Information Requests; select such an organization first.
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

        <h3 id={"platform-administration-overview-organization-boundaries-heading"}>Organization boundaries</h3>
        <p id={"platform-administration-overview-organization-boundaries-paragraph"}>
            App Admin is a platform role. It does not grant access to
            organization people, groups, security, trusted organizations,
            Exchanges, documents, workflows, communications, or audit evidence.
            Those areas require a separate organization role and matching active
            organization context.
        </p>
    </>
);
