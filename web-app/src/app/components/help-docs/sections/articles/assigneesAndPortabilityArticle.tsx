export const assigneesAndPortabilityArticle = (
    <>
        <p id={"assignees-and-portability-article-p-1"}>
            Assignees define who participates in a step as approvers, notification
            recipients, or escalation targets. Configuring assignees correctly is
            the most important factor in making your workflow definitions portable
            and reusable.
        </p>

        <h3 id={"assignees-and-portability-article-h3-1"}>Assignee kinds</h3>
        <p id={"assignees-and-portability-article-p-2"}>
            Each assignee entry has a <b id={"assignees-and-portability-article-b-1"}>kind</b> that determines how the engine
            resolves the actual set of users at runtime.
        </p>

        <h3 id={"assignees-and-portability-article-h3-2"}>Organization Role <code id={"assignees-and-portability-article-code-1"}>(ORGANIZATION_ROLE)</code></h3>
        <p id={"assignees-and-portability-article-p-3"}>
            Resolves to users who hold a selected organization role in the
            organization referenced by the workflow subject.
        </p>
        <p id={"assignees-and-portability-article-p-4"}><b id={"assignees-and-portability-article-b-2"}>Fields:</b></p>
        <ul id={"assignees-and-portability-article-ul-1"}>
            <li id={"assignees-and-portability-article-li-1"}>
                <b id={"assignees-and-portability-article-b-3"}>Organization role</b> - Owner, Admin, Billing Admin, User
                Manager, Auditor, Member, or Guest.
            </li>
            <li id={"assignees-and-portability-article-li-2"}>
                <b id={"assignees-and-portability-article-b-4"}>Organization ID ref</b> - the organization to resolve roles against.
                Defaults to <b id={"assignees-and-portability-article-b-5"}>Caller's organization</b>, which keeps the definition
                portable. Other options from the trigger event's data (shown by their
                plain-language name, e.g. "Initiator") appear as additional choices.
            </li>
        </ul>

        <h3 id={"assignees-and-portability-article-h3-3"}>App Role <code id={"assignees-and-portability-article-code-2"}>(APP_ROLE)</code></h3>
        <p id={"assignees-and-portability-article-p-5"}>
            Resolves to users who hold an App Admin, App Auditor, App Support, or
            App User role. App roles are platform-wide and do not use an
            organization reference.
        </p>

        <h3 id={"assignees-and-portability-article-h3-4"}>Group Members <code id={"assignees-and-portability-article-code-3"}>(GROUP_ROLE)</code></h3>
        <p id={"assignees-and-portability-article-p-6"}>
            Resolves to members of a specific Principal Group who hold a given
            role within that group (Owner, Manager, Member, or Observer).
        </p>
        <p id={"assignees-and-portability-article-p-7"}><b id={"assignees-and-portability-article-b-6"}>Fields:</b></p>
        <ul id={"assignees-and-portability-article-ul-2"}>
            <li id={"assignees-and-portability-article-li-3"}>
                <b id={"assignees-and-portability-article-b-7"}>Group ID ref</b> - the Principal Group to target. Choose{" "}
                <b id={"assignees-and-portability-article-b-8"}>Recipient Group (from trigger)</b> to reference the recipient
                group dynamically instead of a fixed group.
            </li>
            <li id={"assignees-and-portability-article-li-4"}>
                <b id={"assignees-and-portability-article-b-9"}>Group role</b> - which role within the group to target
                (Owner, Manager, Member, or Observer).
            </li>
        </ul>

        <h3 id={"assignees-and-portability-article-h3-5"}>Specific User <code id={"assignees-and-portability-article-code-4"}>(PRINCIPAL)</code></h3>
        <p id={"assignees-and-portability-article-p-8"}>
            Resolves to a specific user by their unique ID. This is convenient for
            one-off customizations but makes the definition non-portable because
            that ID is only valid in your organization.
        </p>
        <p id={"assignees-and-portability-article-p-9"}><b id={"assignees-and-portability-article-b-10"}>Fields:</b></p>
        <ul id={"assignees-and-portability-article-ul-3"}>
            <li id={"assignees-and-portability-article-li-5"}>
                <b id={"assignees-and-portability-article-b-11"}>User</b> - a searchable list of users in your organization.
                Search by name or email, then select the matching person card.
                Any workflow that contains a Specific User assignee will show a
                portability warning in the save bar.
            </li>
        </ul>

        <h3 id={"assignees-and-portability-article-h3-6"}>Portability warning</h3>
        <p id={"assignees-and-portability-article-p-10"}>
            The Workflow Designer checks all assignee entries when you save. If any
            entry uses a hardcoded Specific User, a warning appears in the
            save bar. The definition will still save and work correctly within your
            organization, but:
        </p>
        <ul id={"assignees-and-portability-article-ul-4"}>
            <li id={"assignees-and-portability-article-li-6"}>
                If the definition is cloned by another organization, hardcoded
                user IDs are automatically replaced with portable Organization Role
                placeholders. The receiving org should review and adjust those
                entries after cloning.
            </li>
            <li id={"assignees-and-portability-article-li-7"}>
                If you plan to publish this definition as a platform template,
                resolve all portability warnings first by replacing Specific User
                entries with Organization Role, App Role, or Group Members entries.
            </li>
        </ul>

        <h3 id={"assignees-and-portability-article-h3-7"}>Referencing trigger data in assignees</h3>
        <p id={"assignees-and-portability-article-p-11"}>
            Instead of a fixed organization or group, an assignee can reference a
            value carried by the trigger event - for example, the Exchange's
            <b id={"assignees-and-portability-article-b-12"}> Recipient</b>, <b id={"assignees-and-portability-article-b-13"}>Recipient Group</b>, <b id={"assignees-and-portability-article-b-14"}>Initiator</b>, or{" "}
            <b id={"assignees-and-portability-article-b-15"}>Caller's organization</b>. These values are filled in at runtime
            from the Exchange that triggered the workflow, and the exact options
            available depend on the trigger event you chose. For example, a
            workflow on the <b id={"assignees-and-portability-article-b-16"}>Acceptance Pending</b> trigger can reference:
        </p>
        <ul id={"assignees-and-portability-article-ul-5"}>
            <li id={"assignees-and-portability-article-li-8"}><b id={"assignees-and-portability-article-b-17"}>Recipient</b> - the primary recipient user.</li>
            <li id={"assignees-and-portability-article-li-9"}><b id={"assignees-and-portability-article-b-18"}>Recipient Group</b> - the primary recipient group.</li>
            <li id={"assignees-and-portability-article-li-10"}><b id={"assignees-and-portability-article-b-19"}>Initiator</b> - the user who created the Exchange.</li>
            <li id={"assignees-and-portability-article-li-11"}><b id={"assignees-and-portability-article-b-20"}>Caller's organization</b> - the initiator's organization.</li>
        </ul>
        <p id={"assignees-and-portability-article-p-12"}>
            The trigger event dropdown in the designer updates this list
            automatically, and every option is selected by its plain-language name
            from a dropdown - no manual syntax required. (Internally, these are
            stored as <code id={"assignees-and-portability-article-code-5"}>$subject.fieldName</code> references, for example{" "}
            <code id={"assignees-and-portability-article-code-6"}>$subject.orgId</code>, but you never need to type that yourself.)
        </p>

        <h3 id={"assignees-and-portability-article-h3-8"}>Best practices</h3>
        <ol id={"assignees-and-portability-article-ol-1"}>
            <li id={"assignees-and-portability-article-li-12"}>
                Prefer Organization Role, App Role, or Group Members assignees over
                Specific User to keep definitions reusable.
            </li>
            <li id={"assignees-and-portability-article-li-13"}>
                Use <b id={"assignees-and-portability-article-b-21"}>Caller's organization</b> instead of picking your own
                organization by name in Organization Role organization ID refs.
            </li>
            <li id={"assignees-and-portability-article-li-14"}>
                Use <b id={"assignees-and-portability-article-b-22"}>Recipient Group (from trigger)</b> for Group Members entries
                on acceptance workflows.
            </li>
            <li id={"assignees-and-portability-article-li-15"}>
                Reserve the Specific User kind for highly specific one-off use
                cases where portability is not a concern.
            </li>
        </ol>
    </>
);
