import {HelpDocSectionInput} from "../helpDocsRegistry";
import {auditWorkspaceOverviewArticle} from "./articles/auditWorkspaceOverviewArticle";
import {trustedOrganizationsAdministrationArticle} from "./articles/trustedOrganizationsAdministrationArticle";
import {platformAdministrationOverviewArticle} from "./articles/platformAdministrationOverviewArticle";

export const adminOperationsSection: HelpDocSectionInput = {
    id: "admin-operations",
    title: "Admin operations",
    articles: [
        {
            id: "platform-administration-overview",
            title: "Platform Administration overview",
            content: platformAdministrationOverviewArticle,
        },
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
                    <p id={"admin-operations-section-p-1"}>
                        Use this matrix as the baseline for assigning least-privilege access
                        across your organization.
                    </p>

                    <h3 id={"admin-operations-section-h3-1"}>Role overview</h3>
                    <ul id={"admin-operations-section-ul-1"}>
                        <li id={"admin-operations-section-li-1"}><b id={"admin-operations-section-b-1"}>App roles:</b> Platform-wide administration, audit, support, or standard user access.</li>
                        <li id={"admin-operations-section-li-2"}><b id={"admin-operations-section-b-2"}>Organization roles:</b> Organization Owner, Admin, Billing Admin, User Manager, Auditor, Member, and Guest.</li>
                        <li id={"admin-operations-section-li-3"}><b id={"admin-operations-section-b-3"}>Group roles:</b> Owner, Manager, Member, and Observer within one Principal Group.</li>
                        <li id={"admin-operations-section-li-4"}><b id={"admin-operations-section-b-4"}>Exchange roles:</b> Owner, Editor, Reviewer, Signer, Viewer, Commenter, and Participant on one Exchange.</li>
                    </ul>

                    <h3 id={"admin-operations-section-h3-2"}>Additive organization roles</h3>
                    <p id={"admin-operations-section-p-2"}>
                        A person can hold several organization roles at the same time. Adding or
                        removing one role does not replace their other organization roles.
                    </p>
                    <ul id={"admin-operations-section-ul-2"}>
                        <li id={"admin-operations-section-li-5"}><b id={"admin-operations-section-b-5"}>Owner:</b> Organization policy, users, billing, audit, and group administration, plus the Information Request operations queue and privacy requests.</li>
                        <li id={"admin-operations-section-li-6"}><b id={"admin-operations-section-b-6"}>Admin:</b> Organization policy, users, audit, and group administration, plus the Information Request operations queue and privacy requests.</li>
                        <li id={"admin-operations-section-li-7"}><b id={"admin-operations-section-b-7"}>Billing Admin:</b> Billing management.</li>
                        <li id={"admin-operations-section-li-8"}><b id={"admin-operations-section-b-8"}>User Manager:</b> Organization member management and group visibility.</li>
                        <li id={"admin-operations-section-li-9"}><b id={"admin-operations-section-b-9"}>Auditor:</b> Organization audit review and evidence export.</li>
                        <li id={"admin-operations-section-li-10"}><b id={"admin-operations-section-b-10"}>Member:</b> Standard organization membership and group visibility.</li>
                        <li id={"admin-operations-section-li-11"}><b id={"admin-operations-section-b-11"}>Guest:</b> No organization-wide capabilities by default.</li>
                    </ul>

                    <h3 id={"admin-operations-section-h3-3"}>Scope isolation</h3>
                    <ul id={"admin-operations-section-ul-3"}>
                        <li id={"admin-operations-section-li-12"}>Role capabilities and subscription features are separate. An allowed organization action still requires the owning organization&apos;s applicable Business feature and mutation access.</li>
                        <li id={"admin-operations-section-li-13"}>App Admin is a platform role. It does not grant access to organization people, groups, settings, content, or member-capacity details.</li>
                        <li id={"admin-operations-section-li-14"}>An App Admin needs a separate organization role and the matching active organization to use organization administration capabilities.</li>
                        <li id={"admin-operations-section-li-15"}>An organization role does not grant access to an Exchange by itself.</li>
                        <li id={"admin-operations-section-li-16"}>An organization role does not open one Information Request&apos;s details. The Exchange owner or a decision maker on the request does.</li>
                        <li id={"admin-operations-section-li-17"}>A group Manager role applies only to that group, not to the organization or an Exchange.</li>
                        <li id={"admin-operations-section-li-18"}>An Exchange role applies only to the relevant Exchange and its documents.</li>
                        <li id={"admin-operations-section-li-19"}>The Owner label in a group is separate from the Owner label on an Exchange.</li>
                        <li id={"admin-operations-section-li-20"}>Capabilities granted to a registered application remain limited to resources owned by that application's organization. Organization-owned applications cannot use platform audit capabilities.</li>
                    </ul>

                    <h3 id={"admin-operations-section-h3-4"}>Assignment best practices</h3>
                    <ol id={"admin-operations-section-ol-1"}>
                        <li id={"admin-operations-section-li-21"}>Assign the minimum role required for each person.</li>
                        <li id={"admin-operations-section-li-22"}>Combine specialized organization roles only when duties require it.</li>
                        <li id={"admin-operations-section-li-23"}>Review role assignments on a recurring cadence.</li>
                        <li id={"admin-operations-section-li-24"}>Revoke unused or stale access promptly during offboarding.</li>
                    </ol>

                    <h3 id={"admin-operations-section-h3-5"}>Finding people</h3>
                    <p id={"admin-operations-section-p-3"}>
                        In Settings, select Administration under Organization, then open People
                        to search members by name or email. Filter the list by account status or organization role,
                        sort it by name, email, or status, and use the pagination controls below
                        the table to move through the results.
                    </p>
                    <p id={"admin-operations-section-p-4"}>
                        The Groups/Teams tab uses the same list layout. Search by group or member,
                        filter by status, sort by name, member count, or status, and use the fixed
                        pagination controls below the group table.
                    </p>
                    <p id={"admin-operations-section-p-5"}>
                        Person selectors in organization groups and Platform Administration
                        are searchable by name or email. Results show a person card with
                        an avatar or initials, full name, and email. Multi-person selectors
                        keep selected people as removable tags.
                    </p>

                    <h3 id={"admin-operations-section-h3-6"}>Common pitfalls</h3>
                    <ul id={"admin-operations-section-ul-4"}>
                        <li id={"admin-operations-section-li-25"}>Granting organization admin rights for routine Exchange tasks.</li>
                        <li id={"admin-operations-section-li-26"}>Forgetting to remove inherited access after team changes.</li>
                        <li id={"admin-operations-section-li-27"}>Assuming Exchange access implies org-level permissions.</li>
                        <li id={"admin-operations-section-li-28"}>Assuming roles with the same label share permissions across scopes.</li>
                    </ul>
                </>
            ),
        },
    ],
};
