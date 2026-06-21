import React from "react";

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

        <h3>ROLE</h3>
        <p>
            Resolves to all users who hold a named role in a given scope. This is
            the most portable option because it references a logical role rather
            than specific people.
        </p>
        <p><b>Fields:</b></p>
        <ul>
            <li>
                <b>Role name</b> - the role to target. Within-organization scope:
                Organization Admin, Organization Member, Group Admin.
                Platform-wide scope: App Admin, App User.
            </li>
            <li>
                <b>Scope type</b> - either <b>Platform-wide</b> (APP) or{" "}
                <b>Within organization</b> (ORG).
            </li>
            <li>
                <b>Scope ID ref</b> - the organization to resolve roles against.
                Defaults to <b>Caller's organization</b> (<code>$subject.orgId</code>),
                which keeps the definition portable. Other subject fields from the
                trigger event appear as additional options.
            </li>
        </ul>

        <h3>GROUP_ROLE</h3>
        <p>
            Resolves to members of a specific Principal Group who hold a given
            role within that group (OWNER, MANAGER, MEMBER, or OBSERVER).
        </p>
        <p><b>Fields:</b></p>
        <ul>
            <li>
                <b>Group ID ref</b> - the Principal Group to target. Use{" "}
                <code>$subject.recipientGroupId</code> to reference the recipient
                group dynamically.
            </li>
            <li>
                <b>Group role</b> - which role within the group to target
                (OWNER, MANAGER, MEMBER, or OBSERVER).
            </li>
        </ul>

        <h3>PRINCIPAL</h3>
        <p>
            Resolves to a specific user by their UUID. This is convenient for
            one-off customizations but makes the definition non-portable because
            the UUID is only valid in your organization.
        </p>
        <p><b>Fields:</b></p>
        <ul>
            <li>
                <b>User</b> - a dropdown of users in your organization. Selecting
                a user stores their UUID in the definition. Any workflow that
                contains a PRINCIPAL assignee will show a portability warning in
                the save bar.
            </li>
        </ul>

        <h3>Portability warning</h3>
        <p>
            The Workflow Designer checks all assignee entries when you save. If any
            entry uses a hardcoded UUID (PRINCIPAL kind), a warning appears in the
            save bar. The definition will still save and work correctly within your
            organization, but:
        </p>
        <ul>
            <li>
                If the definition is cloned by another organization, hardcoded
                UUIDs are automatically replaced with portable ROLE placeholders.
                The receiving org should review and adjust those entries after
                cloning.
            </li>
            <li>
                If you plan to publish this definition as a platform template,
                resolve all portability warnings first by replacing PRINCIPAL
                entries with ROLE or GROUP_ROLE entries.
            </li>
        </ul>

        <h3>Using $subject.* placeholders</h3>
        <p>
            Subject field placeholders are replaced at runtime with the actual
            values from the Exchange that triggered the workflow. Available fields
            depend on the trigger event. For example, a workflow on{" "}
            <code>exchange.acceptance_pending</code> can use:
        </p>
        <ul>
            <li><code>$subject.recipientId</code> - the Recipient user (shown as "Recipient" in the designer).</li>
            <li><code>$subject.recipientGroupId</code> - the Recipient Group (shown as "Recipient Group" in the designer).</li>
            <li><code>$subject.initiatorId</code> - the Exchange Initiator (shown as "Initiator" in the designer).</li>
            <li><code>$subject.orgId</code> - the initiator's organization.</li>
        </ul>
        <p>
            The trigger event dropdown in the designer updates the available
            placeholder auto-complete list automatically. Note that the designer
            displays friendly names (e.g. "Initiator") rather than raw field
            names.
        </p>

        <h3>Best practices</h3>
        <ol>
            <li>
                Prefer ROLE or GROUP_ROLE assignees over PRINCIPAL to keep
                definitions reusable.
            </li>
            <li>
                Use <code>$subject.orgId</code> instead of your own org UUID in
                ROLE scope ID refs.
            </li>
            <li>
                Use <code>$subject.recipientGroupId</code> for GROUP_ROLE entries
                on acceptance workflows.
            </li>
            <li>
                Reserve PRINCIPAL kind for highly specific one-off use cases where
                portability is not a concern.
            </li>
        </ol>
    </>
);
