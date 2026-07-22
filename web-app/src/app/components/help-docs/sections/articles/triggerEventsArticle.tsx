import React from "react";

export const triggerEventsArticle = (
    <>
        <p>
            A trigger event is the signal that starts a workflow instance. Each
            workflow definition is bound to exactly one trigger. When that event
            fires on an Exchange, the engine looks for an active definition that
            matches, creates an instance, and begins executing its steps.
        </p>

        <h3>Available trigger events</h3>

        <h3>Draft Submitted <code>(exchange.draft_submitted)</code></h3>
        <p>
            Fires immediately when a new Exchange is created. Use this trigger for
            internal pre-send approvals that must complete before any recipient is
            notified.
        </p>
        <p><b>Available subject fields:</b></p>
        <ul>
            <li><b>Initiator</b> - the user who created the Exchange.</li>
            <li><b>Org</b> - the organization the initiator belongs to.</li>
        </ul>

        <h3>Acceptance Pending <code>(exchange.acceptance_pending)</code></h3>
        <p>
            Fires after the draft stage when recipient acceptance is required (the
            org-level "require recipient acceptance" setting is on). The Exchange
            stays in Draft until this workflow completes. Use this for recipient
            approval workflows.
        </p>
        <p><b>Available subject fields:</b></p>
        <ul>
            <li><b>Recipient</b> - the primary recipient user. In the condition builder, searchable from your contacts.</li>
            <li>
                <b>Recipient Group</b> - the primary recipient group, including a published
                group in a Trusted Organization. The condition builder group picker contains
                groups available in the current organization context.
            </li>
            <li><b>Initiator</b> - the user who created the Exchange. In the condition builder, searchable from your contacts.</li>
            <li><b>Org</b> - the initiator organization. No searchable picker - used as a scope reference in assignee configuration.</li>
            <li>
                <b>Recipient Type</b> - one of: <b>Email</b>,{" "}
                <b>App User</b>, or <b>Group</b>.
            </li>
        </ul>

        <h3>Activated <code>(exchange.activated)</code></h3>
        <p>
            Fires when an Exchange transitions to Active. Use
            this for post-activation notifications, automatic actions, or secondary
            approvals that must happen once the Exchange becomes Active.
        </p>
        <p><b>Available subject fields:</b></p>
        <ul>
            <li><b>Initiator</b> - the user who created the Exchange. In the condition builder, searchable from your contacts.</li>
            <li><b>Org</b> - the initiator organization. No searchable picker - used as a scope reference in assignee configuration.</li>
        </ul>

        <h3>Ending <code>(exchange.ending)</code></h3>
        <p>
            Fires when a user requests to close an Exchange. The Exchange is held
            in its current state until this workflow completes or is rejected. Use
            this for completion checklists, mandatory sign-off steps, or data
            archival actions before the Exchange is permanently marked Completed.
        </p>
        <p><b>Available subject fields:</b></p>
        <ul>
            <li><b>Initiator</b> - the user who created the Exchange. In the condition builder, searchable from your contacts.</li>
            <li><b>Org</b> - the initiator organization. No searchable picker - used as a scope reference in assignee configuration.</li>
        </ul>

        <h3>Received (Recipient Side) <code>(exchange.received)</code></h3>
        <p>
            Fires in a <b>recipient organization's</b> context when an Exchange is
            sent to them and is waiting for their acceptance. Use this to run
            internal approval workflows on the recipient side before the Exchange
            becomes active.
        </p>
        <p><b>Available subject fields:</b></p>
        <ul>
            <li><b>Initiator</b> - the user who created the Exchange.</li>
            <li><b>Org</b> - the initiator's organization.</li>
            <li><b>Recipient Org</b> - the recipient organization this instance was triggered for.</li>
        </ul>

        <h3>Activated (Recipient Side) <code>(exchange.received_activated)</code></h3>
        <p>
            Fires in a <b>recipient organization's</b> context when an Exchange they
            are part of transitions to Active. Use this for post-activation
            notifications or actions on the recipient side.
        </p>
        <p><b>Available subject fields:</b></p>
        <ul>
            <li><b>Initiator</b> - the user who created the Exchange.</li>
            <li><b>Org</b> - the initiator's organization.</li>
            <li><b>Recipient Org</b> - the recipient organization this instance was triggered for.</li>
        </ul>

        <h3>Ending (Recipient Side) <code>(exchange.received_ending)</code></h3>
        <p>
            Fires in a <b>recipient organization's</b> context when an Exchange they
            are part of is being closed. Use this for recipient-side completion
            checklists or sign-off steps.
        </p>
        <p><b>Available subject fields:</b></p>
        <ul>
            <li><b>Initiator</b> - the user who created the Exchange.</li>
            <li><b>Org</b> - the initiator's organization.</li>
            <li><b>Recipient Org</b> - the recipient organization this instance was triggered for.</li>
        </ul>

        <h3>Tip: referencing trigger data in assignees</h3>
        <p>
            When configuring <b>assignees</b>, you can point to a value carried
            by the trigger event - for example, "the Exchange's initiating
            organization" - instead of hardcoding a specific user or group ID.
            These options appear by their plain-language name in the assignee
            dropdown (internally stored as <code>$subject.fieldName</code>, for
            example <code>$subject.orgId</code>). This keeps the definition
            portable across organizations. In the condition builder, fields are
            selected by name from a dropdown - no manual syntax required.
        </p>

        <h3>Learn more</h3>
        <ul>
            <li>
                <a href="#"
                   data-help-article="assignees-and-portability">
                    <b>Assignees and portability</b>
                </a>{" "}
                - how to reference trigger data in assignees safely.
            </li>
            <li>
                <a href="#"
                   data-help-article="workflow-org-settings">
                    <b>Organization workflow settings</b>
                </a>{" "}
                - enabling or disabling recipient acceptance.
            </li>
        </ul>
    </>
);
