import React from "react";

export type HelpDocArticle = {
    id: string;
    sectionId: string;
    sectionTitle: string;
    title: string;
    content: React.ReactNode;
};

type HelpDocSectionInput = {
    id: string;
    title: string;
    articles: Array<{
        id: string;
        title: string;
        content: React.ReactNode;
    }>;
};

const helpDocSections: HelpDocSectionInput[] = [
    {
        id: "start-here",
        title: "Start here",
        articles: [
            {
                id: "help-starter-guide",
                title: "Help starter guide",
                content: (
                    <>
                        <p>
                            Welcome to DocuHyphen Help. If you are not sure where to begin,
                            use this recommended reading order.
                        </p>

                        <h3>Recommended reading order</h3>
                        <ol>
                            <li>
                                <a href="#"
                                   data-help-article="idp-setup-guide"><b>IdP setup guide</b></a> - configure sign-in and organization access.
                            </li>
                            <li>
                                <a href="#"
                                   data-help-article="role-permission-matrix"><b>Role & Permission Matrix</b></a> - assign least-privilege roles.
                            </li>
                            <li>
                                <a href="#"
                                   data-help-article="exchange-lifecycle"><b>Session Lifecycle</b></a> - standardize end-to-end collaboration flow.
                            </li>
                        </ol>

                        <h3>Who should read what</h3>
                        <ul>
                            <li><b>Organization Admin:</b> Start with IdP and Role & Permission Matrix.</li>
                            <li><b>Operations/Managers:</b> Start with Session Lifecycle.</li>
                            <li><b>New team members:</b> Read all three once for shared context.</li>
                        </ul>
                    </>
                ),
            },
        ],
    },
    {
        id: "identity",
        title: "Identity & access",
        articles: [
            {
                id: "idp-setup-guide",
                title: "IdP setup guide",
                content: (
                    <>
                        <p>
                            Configure Microsoft Entra ID or Google as your identity provider
                            for single sign-on and organization-level access control.
                        </p>

                        <h3>Before you begin</h3>
                        <ul>
                            <li>Confirm you have admin access to your identity provider.</li>
                            <li>Collect redirect URIs for each deployment environment.</li>
                            <li>Decide which email domains are allowed for your organization.</li>
                            <li>Define who owns rollout, testing, and change approval.</li>
                        </ul>

                        <h3>Microsoft (Entra ID)</h3>
                        <ol>
                            <li>Create or select an Entra ID app registration.</li>
                            <li>Add the DocuHyphen redirect URI(s) as web redirects.</li>
                            <li>Capture the client ID and tenant ID.</li>
                            <li>Create a client secret and store it in your secret manager.</li>
                            <li>
                                Include claims for email, given name, family name, and subject
                                or object ID.
                            </li>
                            <li>Grant scopes: openid, profile, and email.</li>
                            <li>Save values in DocuHyphen settings and run a sign-in test.</li>
                        </ol>

                        <h3>Google</h3>
                        <ol>
                            <li>Create or select a Google Cloud project.</li>
                            <li>Configure the OAuth consent screen for your org.</li>
                            <li>Create web OAuth credentials and set redirect URI(s).</li>
                            <li>Capture the client ID and client secret.</li>
                            <li>Grant scopes: openid, profile, and email.</li>
                            <li>Configure domain restrictions if required.</li>
                            <li>Save configuration in DocuHyphen and run a test login.</li>
                        </ol>

                        <h3>Troubleshooting checklist</h3>
                        <ul>
                            <li>Redirect URI mismatch between provider and DocuHyphen.</li>
                            <li>Missing consent for required scopes.</li>
                            <li>Invalid tenant or project configuration.</li>
                            <li>User email domain blocked by policy.</li>
                        </ul>
                    </>
                ),
            },
        ],
    },
    {
        id: "exchanges",
        title: "Sharing sessions",
        articles: [
            {
                id: "exchange-lifecycle",
                title: "Session Lifecycle",
                content: (
                    <>
                        <p>
                            This guide explains the typical session flow from creation to closure,
                            including validation points that keep teams aligned.
                        </p>

                        <h3>When to use this guide</h3>
                        <ul>
                            <li>Onboard a new operations or compliance team member.</li>
                            <li>Standardize how sessions are created and closed.</li>
                            <li>Troubleshoot status transition confusion.</li>
                        </ul>

                        <h3>Session states</h3>
                        <ul>
                            <li><b>Draft:</b> Session is created and prepared.</li>
                            <li><b>Active:</b> Recipients can interact with the shared content.</li>
                            <li><b>Completed/Ended:</b> Session is closed and no further changes should occur.</li>
                        </ul>

                        <h3>Standard lifecycle steps</h3>
                        <ol>
                            <li>Create session with clear title and intended recipients.</li>
                            <li>Upload required documents and verify file quality.</li>
                            <li>Send session and monitor recipient acceptance or rejection.</li>
                            <li>Address comments/changes and update files when required.</li>
                            <li>End session once the business outcome is achieved.</li>
                            <li>Review audit history for compliance and record retention.</li>
                        </ol>

                        <h3>Validation checklist</h3>
                        <ul>
                            <li>Correct recipients and permissions before activation.</li>
                            <li>No sensitive files shared to unintended users.</li>
                            <li>Closure reasons are documented for completed sessions.</li>
                        </ul>
                    </>
                ),
            },
        ],
    },
    {
        id: "admin-operations",
        title: "Admin operations",
        articles: [
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
                            <li><b>Organization Admin:</b> Full org-level configuration and access governance.</li>
                            <li><b>Manager:</b> Session-level operational control within assigned scope.</li>
                            <li><b>Member:</b> Participant access to assigned sessions and documents.</li>
                        </ul>

                        <h3>Permission matrix (high level)</h3>
                        <ul>
                            <li><b>Manage organization settings:</b> Admin only.</li>
                            <li><b>Manage users and roles:</b> Admin only.</li>
                            <li><b>Create and manage sessions:</b> Admin and Manager.</li>
                            <li><b>Upload/edit session documents:</b> Admin and Manager (or delegated Member where allowed).</li>
                            <li><b>Close/end sessions:</b> Admin and Manager.</li>
                            <li><b>View broad audit records:</b> Admin (Manager may see scoped session history).</li>
                        </ul>

                        <h3>Assignment best practices</h3>
                        <ol>
                            <li>Assign the minimum role required for each person.</li>
                            <li>Use time-bound elevated access for temporary admin work.</li>
                            <li>Review role assignments on a recurring cadence.</li>
                            <li>Revoke unused or stale access promptly during offboarding.</li>
                        </ol>

                        <h3>Common pitfalls</h3>
                        <ul>
                            <li>Granting admin rights for routine session tasks.</li>
                            <li>Forgetting to remove inherited access after team changes.</li>
                            <li>Assuming session access implies org-level permissions.</li>
                        </ul>
                    </>
                ),
            },
        ],
    },
];

export const HELP_DOC_ARTICLES: HelpDocArticle[] = helpDocSections.flatMap((section) =>
    section.articles.map((article) => ({
        id: article.id,
        sectionId: section.id,
        sectionTitle: section.title,
        title: article.title,
        content: article.content,
    })),
);

export function getDefaultHelpDocArticle(): HelpDocArticle
{
    return HELP_DOC_ARTICLES[0];
}

export function getHelpDocArticleById(articleId: string): HelpDocArticle | undefined
{
    return HELP_DOC_ARTICLES.find((article) => article.id === articleId);
}

export function getHelpDocSections()
{
    return helpDocSections;
}






