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
            <b> Platform Content</b>, or <b>App Administrators</b>. On smaller
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
            accounts and review account status, tier, licensed capacity, aggregate
            active-user usage, and feature entitlements. Select <b>Edit account</b>
            to activate or deactivate the account, update its verification status,
            tier, capacity, or feature entitlements. These controls
            do not open organization Settings or expose member identities. Tier,
            capacity, and entitlement changes record safe before-and-after state
            in Platform Audit.
        </p>
        <p>
            Filters stay at the top and pagination stays at the bottom while the
            organization rows scroll within the available table area.
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
