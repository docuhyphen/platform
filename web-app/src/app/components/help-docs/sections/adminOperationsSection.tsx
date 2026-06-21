import React from "react";
import {HelpDocSectionInput} from "../helpDocsRegistry";

export const adminOperationsSection: HelpDocSectionInput = {
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
                        <li><b>Manager:</b> Exchange-level operational control within assigned scope.</li>
                        <li><b>Member:</b> Participant access to assigned Exchanges and documents.</li>
                    </ul>

                    <h3>Permission matrix (high level)</h3>
                    <ul>
                        <li><b>Manage organization settings:</b> Admin only.</li>
                        <li><b>Manage users and roles:</b> Admin only.</li>
                        <li><b>Create and manage Exchanges:</b> Admin and Manager.</li>
                        <li><b>Upload/edit Exchange documents:</b> Admin and Manager (or delegated Member where allowed).</li>
                        <li><b>Close/end Exchanges:</b> Admin and Manager.</li>
                        <li><b>View broad audit records:</b> Admin (Manager may see scoped Exchange history).</li>
                        <li><b>Create and manage org variables:</b> Admin only.</li>
                        <li><b>View org variables (resolved in exchanges):</b> All members.</li>
                        <li><b>Create and manage personal variables:</b> Any authenticated user (own variables only).</li>
                        <li><b>Create and manage sequences:</b> Admin only.</li>
                        <li><b>Use variable tokens in blueprints and exchanges:</b> Any authenticated user.</li>
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
                        <li>Granting admin rights for routine Exchange tasks.</li>
                        <li>Forgetting to remove inherited access after team changes.</li>
                        <li>Assuming Exchange access implies org-level permissions.</li>
                    </ul>
                </>
            ),
        },
    ],
};
