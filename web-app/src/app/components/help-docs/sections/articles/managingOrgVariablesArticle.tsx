import React from "react";

export const managingOrgVariablesArticle = (
    <>
        <p>
            Organization variables are key/value definitions managed by Organization Admins.
            They are available to all members of the org as tokens in blueprints and
            Exchanges.
        </p>

        <h3>Who can manage org variables</h3>
        <p>
            Only Organization Admins and App Admins can create, edit, and delete org
            variable definitions. Regular members can see org variable tokens resolved in
            their Exchanges but cannot create or edit the definitions.
        </p>

        <h3>How to create an org variable</h3>
        <ol>
            <li>Open Settings and go to the <b>Variables</b> tab.</li>
            <li>Select the <b>Organization</b> sub-tab.</li>
            <li>Click <b>Add Variable</b> in the top-right corner (visible to admins only).</li>
            <li>
                Enter a <b>KEY</b> (uppercase letters, digits, and underscores only, maximum
                64 characters) and an optional default value.
            </li>
            <li>Save.</li>
        </ol>
        <p>
            The new variable is immediately available as <code>{"{{KEY}}"}</code> in any
            supported field across your organization.
        </p>
        <p>
            Creating, editing, or deleting an org variable can require a 6-digit
            verification code to confirm the admin action.
        </p>

        <h3>Editing and deleting</h3>
        <p>
            Use the three-dot menu on each variable row to edit or delete it (visible to
            admins only). The key cannot be changed after creation. Only the default value
            can be updated. Deleting a variable soft-deletes the definition. Any blueprint
            or Exchange that still contains the token will leave it unresolved after
            deletion.
        </p>

        <h3>Platform sub-tab</h3>
        <p>
            The <b>Platform</b> sub-tab shows the 10 read-only system variables for
            reference. No configuration is needed for system variables. They are always
            available and cannot be overridden at the org level.
        </p>
    </>
);
