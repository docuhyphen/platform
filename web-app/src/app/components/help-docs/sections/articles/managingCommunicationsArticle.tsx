import React from "react";

export const managingCommunicationsArticle = (
    <>
        <p>
            The <b>Settings {">"} Automation {">"} Communications</b> tab is where
            you create, edit, publish, and manage all communications accessible to
            you. The tab is split into three scope tabs: <b>My Communications</b>,
            <b>Organization</b>, and <b>Platform</b>.
        </p>

        <h3>Finding communications</h3>
        <p>
            Use search to find communications by name, summary, subject, or tag.
            The toolbar also includes filters for status, publication state, and
            tags, plus sorting and card/table layout controls. The footer shows
            the visible item range on the left and page actions on the right.
        </p>

        <h3>Creating a communication</h3>
        <ol>
            <li>Open <b>Settings</b> from the top navigation bar.</li>
            <li>Open <b>Automation</b>, then click the <b>Communications</b> tab.</li>
            <li>Select the scope tab where you want to create it.</li>
            <li>Click <b>Create Communication</b>.</li>
            <li>
                Fill in the fields:
                <ul>
                    <li><b>Name</b> - required. Short label shown in pickers and lists.</li>
                    <li><b>Summary</b> - optional one-line description.</li>
                    <li>
                        <b>Subject</b> - required. The email subject line. Supports
                        <code>{"{{TOKEN}}"}</code> syntax; type <code>{"{{}}"}</code> to
                        open the variable picker.
                    </li>
                    <li>
                        <b>Body</b> - required. Markdown content used for both email
                        and in-app notifications. Supports the same token syntax.
                    </li>
                    <li>
                        <b>Tags</b> - optional. Free-text labels to help filter
                        communications in the picker.
                    </li>
                </ul>
            </li>
            <li>Click <b>Create Communication</b> to save.</li>
        </ol>

        <h3>Editing a communication</h3>
        <p>
            Click the three-dot menu on any communication row and choose <b>Edit</b>.
            The editor opens in the <b>Details</b> tab. Make your changes and click
            <b>Save Changes</b>.
        </p>

        <h3>Previewing a communication</h3>
        <p>
            After saving, open the editor again and switch to the <b>Preview</b> tab.
            Optionally provide a JSON object of sample variable overrides (for
            example <code>{"{'exchangeName': 'Test Contract'}"}</code>) and click
            <b>Render Preview</b> to see the resolved subject and body.
        </p>

        <h3>Publishing and unpublishing (Organization scope)</h3>
        <p>
            Only Organization Admins can publish org-scoped communications. Click the
            three-dot menu and choose <b>Publish</b>. A green "Published" badge
            appears. Unpublish with the same menu to hide it from regular members.
        </p>

        <h3>Activating and deactivating</h3>
        <p>
            Use <b>Activate</b> / <b>Deactivate</b> from the three-dot menu. An
            inactive communication is not delivered by the workflow engine; the
            NOTIFICATION step falls back to the default system message instead.
        </p>

        <h3>Duplicating a communication</h3>
        <p>
            Choose <b>Duplicate</b> from the three-dot menu. A personal copy is
            created immediately (inactive, unnamed as "... (copy)") and appears in
            <b>My Communications</b>. Cloning is the only way to create an editable
            copy of a Platform-scoped communication.
        </p>

        <h3>Deleting a communication</h3>
        <p>
            Choose <b>Delete</b> from the three-dot menu. The communication is
            soft-deleted and removed from the list. Workflow steps that referenced it
            will fall back to the default system message on next execution.
        </p>

        <h3>Permissions summary</h3>
        <ul>
            <li><b>Any user</b> - create, edit, and delete Personal communications.</li>
            <li><b>Organization Admin</b> - create, edit, publish, and delete Org communications.</li>
            <li><b>App Admin</b> - manage Platform communications and all org communications.</li>
        </ul>
    </>
);
