import {HelpDocSectionInput} from "../helpDocsRegistry";
import {auditWorkspaceOverviewArticle} from "./articles/auditWorkspaceOverviewArticle";
import {trustedOrganizationsAdministrationArticle} from "./articles/trustedOrganizationsAdministrationArticle";

export const adminOperationsSection: HelpDocSectionInput = {
    id: "admin-operations",
    title: "Admin operations",
    articles: [
        {
            id: "audit-workspace-overview",
            title: "Audit workspace overview",
            content: auditWorkspaceOverviewArticle,
        },
        {
            id: "trusted-organizations-administration",
            title: "Trusted Organizations administration",
            content: trustedOrganizationsAdministrationArticle,
        },
        {
            id: "role-permission-matrix",
            title: "Role & Permission Matrix",
            content: (
                <>
                    <p>
                        Use this matrix as the baseline for assigning least-privilege access
                        across your organization.
                    </p>

                    <h3>Role overview</h3>
                    <ul>
                        <li><b>App roles:</b> Platform-wide administration, audit, support, or standard user access.</li>
                        <li><b>Organization roles:</b> Organization Owner, Admin, Billing Admin, User Manager, Auditor, Member, and Guest.</li>
                        <li><b>Group roles:</b> Owner, Manager, Member, and Observer within one Principal Group.</li>
                        <li><b>Exchange roles:</b> Owner, Editor, Reviewer, Signer, Viewer, Commenter, and Participant on one Exchange.</li>
                    </ul>

                    <h3>Additive organization roles</h3>
                    <p>
                        A person can hold several organization roles at the same time. Adding or
                        removing one role does not replace their other organization roles.
                    </p>
                    <ul>
                        <li><b>Owner:</b> Organization policy, users, billing, audit, and group administration.</li>
                        <li><b>Admin:</b> Organization policy, users, audit, and group administration.</li>
                        <li><b>Billing Admin:</b> Billing management.</li>
                        <li><b>User Manager:</b> Organization member management and group visibility.</li>
                        <li><b>Auditor:</b> Organization audit review and evidence export.</li>
                        <li><b>Member:</b> Standard organization membership and group visibility.</li>
                        <li><b>Guest:</b> No organization-wide capabilities by default.</li>
                    </ul>

                    <h3>Scope isolation</h3>
                    <ul>
                        <li>An organization role does not grant access to an Exchange by itself.</li>
                        <li>A group Manager role applies only to that group, not to the organization or an Exchange.</li>
                        <li>An Exchange role applies only to the relevant Exchange and its documents.</li>
                        <li>The Owner label in a group is separate from the Owner label on an Exchange.</li>
                        <li>Capabilities granted to a registered application remain limited to resources owned by that application's organization. Organization-owned applications cannot use platform audit capabilities.</li>
                    </ul>

                    <h3>Assignment best practices</h3>
                    <ol>
                        <li>Assign the minimum role required for each person.</li>
                        <li>Combine specialized organization roles only when duties require it.</li>
                        <li>Review role assignments on a recurring cadence.</li>
                        <li>Revoke unused or stale access promptly during offboarding.</li>
                    </ol>

                    <h3>Finding people</h3>
                    <p>
                        In Settings, open Administration and select People to search members by
                        name or email. Filter the list by account status or organization role,
                        sort it by name, email, or status, and use the pagination controls below
                        the table to move through the results.
                    </p>
                    <p>
                        The Groups/Teams tab uses the same list layout. Search by group or member,
                        filter by status, sort by name, member count, or status, and use the fixed
                        pagination controls below the group table.
                    </p>
                    <p>
                        Person selectors in organization groups and App Admin settings
                        are searchable by name or email. Results show a person card with
                        an avatar or initials, full name, and email. Multi-person selectors
                        keep selected people as removable tags.
                    </p>

                    <h3>Common pitfalls</h3>
                    <ul>
                        <li>Granting organization admin rights for routine Exchange tasks.</li>
                        <li>Forgetting to remove inherited access after team changes.</li>
                        <li>Assuming Exchange access implies org-level permissions.</li>
                        <li>Assuming roles with the same label share permissions across scopes.</li>
                    </ul>
                </>
            ),
        },
    ],
};
