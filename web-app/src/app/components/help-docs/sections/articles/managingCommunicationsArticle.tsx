
export const managingCommunicationsArticle = (
    <>
        <p id={"managing-communications-intro-paragraph"}>
            The <b id={"managing-communications-article-b-1"}>Settings {">"} Automation {">"} Communications</b> tab is where
            workflow-automation users manage reusable communications and browse
            Platform communications. The tab is split into <b id={"managing-communications-article-b-2"}>My Communications</b>,{" "}
            <b id={"managing-communications-article-b-3"}>Organization</b>, and <b id={"managing-communications-article-b-4"}>Platform</b> scopes when those scopes are
            available. App Admins manage the Platform scope in{" "}
            <b id={"managing-communications-article-b-5"}>Platform Administration - Platform Content</b>.
        </p>
        <p id={"managing-communications-intro-paragraph-2"}>
            Organization communications are part of Business workflow automation.
            If the organization loses that entitlement, existing communications remain
            visible and previewable, but creating, editing, publishing, changing status,
            and deleting organization communications are unavailable. Personal accounts
            do not expose workflow automation. Platform communication rules are unchanged.
        </p>

        <h3 id={"managing-communications-finding-communications-heading"}>Finding communications</h3>
        <p id={"managing-communications-finding-communications-paragraph"}>
            Use search to find communications by name, summary, subject, or tag.
            The toolbar also includes filters for status, publication state, and
            tags, plus sorting and card/table layout controls. The footer shows
            the visible item range on the left and page actions on the right.
        </p>

        <h3 id={"managing-communications-creating-a-communication-heading"}>Creating a communication</h3>
        <ol id={"managing-communications-creating-a-communication-list"}>
            <li id={"managing-communications-article-li-1"}>
                For authorized workflow communications, open <b id={"managing-communications-article-b-6"}>Settings</b>,
                then <b id={"managing-communications-article-b-7"}>Automation - Communications</b>. For Platform communications,
                open <b id={"managing-communications-article-b-8"}>Platform Administration - Platform Content - Communications</b>.
            </li>
            <li id={"managing-communications-article-li-2"}>Select the scope tab where you want to create it.</li>
            <li id={"managing-communications-article-li-3"}>Click <b id={"managing-communications-article-b-9"}>Create Communication</b>.</li>
            <li id={"managing-communications-article-li-4"}>
                Fill in the fields:
                <ul id={"managing-communications-creating-a-communication-list-2"}>
                    <li id={"managing-communications-article-li-5"}><b id={"managing-communications-article-b-10"}>Name</b> - required. Short label shown in pickers and lists.</li>
                    <li id={"managing-communications-article-li-6"}><b id={"managing-communications-article-b-11"}>Summary</b> - optional one-line description.</li>
                    <li id={"managing-communications-article-li-7"}>
                        <b id={"managing-communications-article-b-12"}>Subject</b> - required. The email subject line. Supports{" "}
                        <code id={"managing-communications-article-code-1"}>{"{{TOKEN}}"}</code> syntax; type <code id={"managing-communications-article-code-2"}>{"{{}}"}</code> to
                        open the variable picker.
                    </li>
                    <li id={"managing-communications-article-li-8"}>
                        <b id={"managing-communications-article-b-13"}>Body</b> - required. Markdown content used for both email
                        and in-app notifications. Supports the same token syntax.
                    </li>
                    <li id={"managing-communications-article-li-9"}>
                        <b id={"managing-communications-article-b-14"}>Tags</b> - optional. Free-text labels to help filter
                        communications in the picker.
                    </li>
                </ul>
            </li>
            <li id={"managing-communications-article-li-10"}>Click <b id={"managing-communications-article-b-15"}>Create Communication</b> to save.</li>
        </ol>

        <h3 id={"managing-communications-editing-a-communication-heading"}>Editing a communication</h3>
        <p id={"managing-communications-editing-a-communication-paragraph"}>
            In Settings, click the three-dot menu on an authorized Personal or
            Organization communication and choose <b id={"managing-communications-article-b-16"}>Edit</b>. In Platform
            Administration, select <b id={"managing-communications-article-b-17"}>Edit</b> on a platform Communication card.
            The editor opens in the <b id={"managing-communications-article-b-18"}>Details</b> tab. Make your changes and click{" "}
            <b id={"managing-communications-article-b-19"}>Save Changes</b>.
        </p>

        <h3 id={"managing-communications-previewing-a-communication-heading"}>Previewing a communication</h3>
        <p id={"managing-communications-previewing-a-communication-paragraph"}>
            After saving, open the editor again and switch to the <b id={"managing-communications-article-b-20"}>Preview</b> tab.
            Optionally provide a JSON object of sample variable overrides (for
            example <code id={"managing-communications-article-code-3"}>{"{'exchangeName': 'Test Contract'}"}</code>) and click{" "}
            <b id={"managing-communications-article-b-21"}>Render Preview</b> to see the resolved subject and body.
        </p>

        <h3 id={"managing-communications-publishing-and-unpublishing-organization-heading"}>Publishing and unpublishing
            (Organization scope)</h3>
        <p id={"managing-communications-publishing-and-unpublishing-organization-paragraph"}>
            Only Organization Admins can publish org-scoped communications. Click the
            three-dot menu and choose <b id={"managing-communications-article-b-22"}>Publish</b>. A green "Published" badge
            appears. Unpublish with the same menu to hide it from regular members.
        </p>

        <h3 id={"managing-communications-activating-and-deactivating-heading"}>Activating and deactivating</h3>
        <p id={"managing-communications-activating-and-deactivating-paragraph"}>
            Use <b id={"managing-communications-article-b-23"}>Activate</b> / <b id={"managing-communications-article-b-24"}>Deactivate</b> from the three-dot menu. An
            inactive communication is not delivered by the workflow engine; the
            Notification step falls back to the default system message instead.
        </p>

        <h3 id={"managing-communications-duplicating-a-communication-heading"}>Duplicating a communication</h3>
        <p id={"managing-communications-duplicating-a-communication-paragraph"}>
            Choose <b id={"managing-communications-article-b-25"}>Duplicate</b> from the three-dot menu. A personal copy is
            created immediately (inactive, unnamed as "... (copy)") and appears in{" "}
            <b id={"managing-communications-article-b-26"}>My Communications</b>. Cloning is the only way to create an editable
            copy of a Platform-scoped communication.
        </p>

        <h3 id={"managing-communications-deleting-a-communication-heading"}>Deleting a communication</h3>
        <p id={"managing-communications-deleting-a-communication-paragraph"}>
            Choose <b id={"managing-communications-article-b-27"}>Delete</b> from the three-dot menu. The communication is
            soft-deleted and removed from the list. Workflow steps that referenced it
            will fall back to the default system message on next execution.
        </p>

        <h3 id={"managing-communications-permissions-summary-heading"}>Permissions summary</h3>
        <ul id={"managing-communications-permissions-summary-list"}>
            <li id={"managing-communications-article-li-11"}><b id={"managing-communications-article-b-28"}>Workflow automation users</b> - create, edit, and delete their reusable communications.</li>
            <li id={"managing-communications-article-li-12"}><b id={"managing-communications-article-b-29"}>Organization Admin</b> - create, edit, publish, and delete Org communications.</li>
            <li id={"managing-communications-article-li-13"}>
                <b id={"managing-communications-article-b-30"}>App Admin</b> - manage Platform communications only from{" "}
                <b id={"managing-communications-article-b-31"}>Platform Administration - Platform Content</b>. An App Admin
                needs a separate Organization Admin role in the active organization
                to manage its Organization communications.
            </li>
        </ul>
    </>
);
