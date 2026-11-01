
export const managingOrgVariablesArticle = (
    <>
        <p id={"managing-org-variables-article-p-1"}>
            Organization variables are key/value definitions managed by Organization Admins.
            They are available to all members of the org as tokens in blueprints and
            Exchanges.
        </p>

        <h3 id={"managing-org-variables-article-h3-1"}>Who can manage org variables</h3>
        <p id={"managing-org-variables-article-p-2"}>
            Only Organization Admins can create, edit, and delete org variable definitions.
            The global App Admin role does not grant access to organization variables. An
            App Admin must also be an Organization Admin in the active organization.
            Regular members can see org variable tokens resolved in their Exchanges but
            cannot create or edit the definitions.
        </p>

        <h3 id={"managing-org-variables-article-h3-2"}>How to create an org variable</h3>
        <ol id={"managing-org-variables-article-ol-1"}>
            <li id={"managing-org-variables-article-li-1"}>Open Settings and go to the <b id={"managing-org-variables-article-b-1"}>Variables</b> tab.</li>
            <li id={"managing-org-variables-article-li-2"}>Select the <b id={"managing-org-variables-article-b-2"}>Organization</b> sub-tab.</li>
            <li id={"managing-org-variables-article-li-3"}>Click <b id={"managing-org-variables-article-b-3"}>Add Variable</b> in the top-right corner (visible to admins only).</li>
            <li id={"managing-org-variables-article-li-4"}>
                Enter a <b id={"managing-org-variables-article-b-4"}>KEY</b> (uppercase letters, digits, and underscores only, maximum
                64 characters) and an optional default value.
            </li>
            <li id={"managing-org-variables-article-li-5"}>Save.</li>
        </ol>
        <p id={"managing-org-variables-article-p-3"}>
            The new variable is immediately available as <code id={"managing-org-variables-article-code-1"}>{"{{KEY}}"}</code> in any
            supported field across your organization.
        </p>
        <p id={"managing-org-variables-article-p-4"}>
            Creating, editing, or deleting an org variable can require a 6-digit
            verification code to confirm the admin action.
        </p>

        <h3 id={"managing-org-variables-article-h3-3"}>Editing and deleting</h3>
        <p id={"managing-org-variables-article-p-5"}>
            Use the three-dot menu on each variable row to edit or delete it (visible to
            admins only). The key cannot be changed after creation. Only the default value
            can be updated. Deleting a variable soft-deletes the definition. Any blueprint
            or Exchange that still contains the token will leave it unresolved after
            deletion.
        </p>

        <h3 id={"managing-org-variables-article-h3-4"}>Platform sub-tab</h3>
        <p id={"managing-org-variables-article-p-6"}>
            The <b id={"managing-org-variables-article-b-5"}>Platform</b> sub-tab shows the 10 read-only system variables for
            reference. No configuration is needed for system variables. They are always
            available and cannot be overridden at the org level.
        </p>
    </>
);
