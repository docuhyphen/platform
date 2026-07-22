export const assigneesAndPortabilityArticle = (
    <>
        <p>
            Assignees define who participates in a step as approvers, notification
            recipients, or escalation targets. Configuring assignees correctly is
            the most important factor in making your workflow definitions portable
            and reusable.
        </p>

        <h3>Assignee kinds</h3>
        <p>
            Each assignee entry has a <b>kind</b> that determines how the engine
            resolves the actual set of users at runtime.
        </p>

        <h3>Organization Role <code>(ORGANIZATION_ROLE)</code></h3>
        <p>
            Resolves to users who hold a selected organization role in the
            organization referenced by the workflow subject.
        </p>
        <p><b>Fields:</b></p>
        <ul>
            <li>
                <b>Organization role</b> - Owner, Admin, Billing Admin, User
                Manager, Auditor, Member, or Guest.
            </li>
            <li>
                <b>Organization ID ref</b> - the organization to resolve roles against.
                Defaults to <b>Caller's organization</b>, which keeps the definition
                portable. Other options from the trigger event's data (shown by their
                plain-language name, e.g. "Initiator") appear as additional choices.
            </li>
        </ul>

        <h3>App Role <code>(APP_ROLE)</code></h3>
        <p>
            Resolves to users who hold an App Admin, App Auditor, App Support, or
            App User role. App roles are platform-wide and do not use an
            organization reference.
        </p>

        <h3>Group Members <code>(GROUP_ROLE)</code></h3>
        <p>
            Resolves to members of a specific Principal Group who hold a given
            role within that group (Owner, Manager, Member, or Observer).
        </p>
        <p><b>Fields:</b></p>
        <ul>
            <li>
                <b>Group ID ref</b> - the Principal Group to target. Choose{" "}
                <b>Recipient Group (from trigger)</b> to reference the recipient
                group dynamically instead of a fixed group.
            </li>
            <li>
                <b>Group role</b> - which role within the group to target
                (Owner, Manager, Member, or Observer).
            </li>
        </ul>

        <h3>Specific User <code>(PRINCIPAL)</code></h3>
        <p>
            Resolves to a specific user by their unique ID. This is convenient for
            one-off customizations but makes the definition non-portable because
            that ID is only valid in your organization.
        </p>
        <p><b>Fields:</b></p>
        <ul>
            <li>
                <b>User</b> - a searchable list of users in your organization.
                Search by name or email, then select the matching person card.
                Any workflow that contains a Specific User assignee will show a
                portability warning in the save bar.
            </li>
        </ul>

        <h3>Portability warning</h3>
        <p>
            The Workflow Designer checks all assignee entries when you save. If any
            entry uses a hardcoded Specific User, a warning appears in the
            save bar. The definition will still save and work correctly within your
            organization, but:
        </p>
        <ul>
            <li>
                If the definition is cloned by another organization, hardcoded
                user IDs are automatically replaced with portable Organization Role
                placeholders. The receiving org should review and adjust those
                entries after cloning.
            </li>
            <li>
                If you plan to publish this definition as a platform template,
                resolve all portability warnings first by replacing Specific User
                entries with Organization Role, App Role, or Group Members entries.
            </li>
        </ul>

        <h3>Referencing trigger data in assignees</h3>
        <p>
            Instead of a fixed organization or group, an assignee can reference a
            value carried by the trigger event - for example, the Exchange's
            <b> Recipient</b>, <b>Recipient Group</b>, <b>Initiator</b>, or{" "}
            <b>Caller's organization</b>. These values are filled in at runtime
            from the Exchange that triggered the workflow, and the exact options
            available depend on the trigger event you chose. For example, a
            workflow on the <b>Acceptance Pending</b> trigger can reference:
        </p>
        <ul>
            <li><b>Recipient</b> - the primary recipient user.</li>
            <li><b>Recipient Group</b> - the primary recipient group.</li>
            <li><b>Initiator</b> - the user who created the Exchange.</li>
            <li><b>Caller's organization</b> - the initiator's organization.</li>
        </ul>
        <p>
            The trigger event dropdown in the designer updates this list
            automatically, and every option is selected by its plain-language name
            from a dropdown - no manual syntax required. (Internally, these are
            stored as <code>$subject.fieldName</code> references, for example{" "}
            <code>$subject.orgId</code>, but you never need to type that yourself.)
        </p>

        <h3>Best practices</h3>
        <ol>
            <li>
                Prefer Organization Role, App Role, or Group Members assignees over
                Specific User to keep definitions reusable.
            </li>
            <li>
                Use <b>Caller's organization</b> instead of picking your own
                organization by name in Organization Role organization ID refs.
            </li>
            <li>
                Use <b>Recipient Group (from trigger)</b> for Group Members entries
                on acceptance workflows.
            </li>
            <li>
                Reserve the Specific User kind for highly specific one-off use
                cases where portability is not a concern.
            </li>
        </ol>
    </>
);
