
export const triggerEventsArticle = (
    <>
        <p id={"trigger-events-article-p-1"}>
            A trigger event is the signal that starts a workflow instance. Each
            workflow definition is bound to exactly one trigger. When that event
            fires on an Exchange, the engine looks for an active definition that
            matches, creates an instance, and begins executing its steps. Triggers that
            start from an Information Request are described in Information Request trigger events.
        </p>

        <h3 id={"trigger-events-article-h3-1"}>Available trigger events</h3>

        <h3 id={"trigger-events-article-h3-2"}>Draft Submitted <code id={"trigger-events-article-code-1"}>(exchange.draft_submitted)</code></h3>
        <p id={"trigger-events-article-p-2"}>
            Fires immediately when a new Exchange is created. Use this trigger for
            internal pre-send approvals that must complete before any recipient is
            notified.
        </p>
        <p id={"trigger-events-article-p-3"}><b id={"trigger-events-article-b-1"}>Available subject fields:</b></p>
        <ul id={"trigger-events-article-ul-1"}>
            <li id={"trigger-events-article-li-1"}><b id={"trigger-events-article-b-2"}>Initiator</b> - the user who created the Exchange.</li>
            <li id={"trigger-events-article-li-2"}><b id={"trigger-events-article-b-3"}>Org</b> - the organization the initiator belongs to.</li>
        </ul>

        <h3 id={"trigger-events-article-h3-3"}>Acceptance Pending <code id={"trigger-events-article-code-2"}>(exchange.acceptance_pending)</code></h3>
        <p id={"trigger-events-article-p-4"}>
            Fires after the draft stage when recipient acceptance is required. This
            includes Exchanges covered by the organization setting and every Exchange
            sent to a verified person or published group in a Trusted Organization.
            The Exchange stays in Draft until this workflow completes. Use this for
            recipient approval workflows.
        </p>
        <p id={"trigger-events-article-p-5"}><b id={"trigger-events-article-b-4"}>Available subject fields:</b></p>
        <ul id={"trigger-events-article-ul-2"}>
            <li id={"trigger-events-article-li-3"}><b id={"trigger-events-article-b-5"}>Recipient</b> - the primary recipient user. In the condition builder, searchable from your contacts.</li>
            <li id={"trigger-events-article-li-4"}>
                <b id={"trigger-events-article-b-6"}>Recipient Group</b> - the primary recipient group, including a published
                group in a Trusted Organization. The condition builder group picker contains
                groups available in the current organization context.
            </li>
            <li id={"trigger-events-article-li-5"}><b id={"trigger-events-article-b-7"}>Initiator</b> - the user who created the Exchange. In the condition builder, searchable from your contacts.</li>
            <li id={"trigger-events-article-li-6"}><b id={"trigger-events-article-b-8"}>Org</b> - the initiator organization. No searchable picker - used as a scope reference in assignee configuration.</li>
            <li id={"trigger-events-article-li-7"}>
                <b id={"trigger-events-article-b-9"}>Recipient Type</b> - one of: <b id={"trigger-events-article-b-10"}>Email</b>,{" "}
                <b id={"trigger-events-article-b-11"}>App User</b>, or <b id={"trigger-events-article-b-12"}>Group</b>.
            </li>
        </ul>

        <h3 id={"trigger-events-article-h3-4"}>Activated <code id={"trigger-events-article-code-3"}>(exchange.activated)</code></h3>
        <p id={"trigger-events-article-p-6"}>
            Fires when an Exchange transitions to Active. Use
            this for post-activation notifications, automatic actions, or secondary
            approvals that must happen once the Exchange becomes Active.
        </p>
        <p id={"trigger-events-article-p-7"}><b id={"trigger-events-article-b-13"}>Available subject fields:</b></p>
        <ul id={"trigger-events-article-ul-3"}>
            <li id={"trigger-events-article-li-8"}><b id={"trigger-events-article-b-14"}>Initiator</b> - the user who created the Exchange. In the condition builder, searchable from your contacts.</li>
            <li id={"trigger-events-article-li-9"}><b id={"trigger-events-article-b-15"}>Org</b> - the initiator organization. No searchable picker - used as a scope reference in assignee configuration.</li>
        </ul>

        <h3 id={"trigger-events-article-h3-5"}>Ending <code id={"trigger-events-article-code-4"}>(exchange.ending)</code></h3>
        <p id={"trigger-events-article-p-8"}>
            Fires when a user requests to close an Exchange. The Exchange is held
            in its current state until this workflow completes or is rejected. Use
            this for completion checklists, mandatory sign-off steps, or data
            archival actions before the Exchange is permanently marked Completed.
        </p>
        <p id={"trigger-events-article-p-9"}><b id={"trigger-events-article-b-16"}>Available subject fields:</b></p>
        <ul id={"trigger-events-article-ul-4"}>
            <li id={"trigger-events-article-li-10"}><b id={"trigger-events-article-b-17"}>Initiator</b> - the user who created the Exchange. In the condition builder, searchable from your contacts.</li>
            <li id={"trigger-events-article-li-11"}><b id={"trigger-events-article-b-18"}>Org</b> - the initiator organization. No searchable picker - used as a scope reference in assignee configuration.</li>
        </ul>

        <h3 id={"trigger-events-article-h3-6"}>Received (Recipient Side) <code id={"trigger-events-article-code-5"}>(exchange.received)</code></h3>
        <p id={"trigger-events-article-p-10"}>
            Fires in a <b id={"trigger-events-article-b-19"}>recipient organization's</b> context when an Exchange is
            sent to them and is waiting for their acceptance. Use this to run
            internal approval workflows on the recipient side before the Exchange
            becomes active.
        </p>
        <p id={"trigger-events-article-p-11"}><b id={"trigger-events-article-b-20"}>Available subject fields:</b></p>
        <ul id={"trigger-events-article-ul-5"}>
            <li id={"trigger-events-article-li-12"}><b id={"trigger-events-article-b-21"}>Initiator</b> - the user who created the Exchange.</li>
            <li id={"trigger-events-article-li-13"}><b id={"trigger-events-article-b-22"}>Org</b> - the initiator's organization.</li>
            <li id={"trigger-events-article-li-14"}><b id={"trigger-events-article-b-23"}>Recipient Org</b> - the recipient organization this instance was triggered for.</li>
        </ul>

        <h3 id={"trigger-events-article-h3-7"}>Activated (Recipient Side) <code id={"trigger-events-article-code-6"}>(exchange.received_activated)</code></h3>
        <p id={"trigger-events-article-p-12"}>
            Fires in a <b id={"trigger-events-article-b-24"}>recipient organization's</b> context when an Exchange they
            are part of transitions to Active. Use this for post-activation
            notifications or actions on the recipient side.
        </p>
        <p id={"trigger-events-article-p-13"}><b id={"trigger-events-article-b-25"}>Available subject fields:</b></p>
        <ul id={"trigger-events-article-ul-6"}>
            <li id={"trigger-events-article-li-15"}><b id={"trigger-events-article-b-26"}>Initiator</b> - the user who created the Exchange.</li>
            <li id={"trigger-events-article-li-16"}><b id={"trigger-events-article-b-27"}>Org</b> - the initiator's organization.</li>
            <li id={"trigger-events-article-li-17"}><b id={"trigger-events-article-b-28"}>Recipient Org</b> - the recipient organization this instance was triggered for.</li>
        </ul>

        <h3 id={"trigger-events-article-h3-8"}>Ending (Recipient Side) <code id={"trigger-events-article-code-7"}>(exchange.received_ending)</code></h3>
        <p id={"trigger-events-article-p-14"}>
            Fires in a <b id={"trigger-events-article-b-29"}>recipient organization's</b> context when an Exchange they
            are part of is being closed. Use this for recipient-side completion
            checklists or sign-off steps.
        </p>
        <p id={"trigger-events-article-p-15"}><b id={"trigger-events-article-b-30"}>Available subject fields:</b></p>
        <ul id={"trigger-events-article-ul-7"}>
            <li id={"trigger-events-article-li-18"}><b id={"trigger-events-article-b-31"}>Initiator</b> - the user who created the Exchange.</li>
            <li id={"trigger-events-article-li-19"}><b id={"trigger-events-article-b-32"}>Org</b> - the initiator's organization.</li>
            <li id={"trigger-events-article-li-20"}><b id={"trigger-events-article-b-33"}>Recipient Org</b> - the recipient organization this instance was triggered for.</li>
        </ul>

        <h3 id={"trigger-events-article-h3-9"}>Tip: referencing trigger data in assignees</h3>
        <p id={"trigger-events-article-p-16"}>
            When configuring <b id={"trigger-events-article-b-34"}>assignees</b>, you can point to a value carried
            by the trigger event - for example, "the Exchange's initiating
            organization" - instead of hardcoding a specific user or group ID.
            These options appear by their plain-language name in the assignee
            dropdown (internally stored as <code id={"trigger-events-article-code-8"}>$subject.fieldName</code>, for
            example <code id={"trigger-events-article-code-9"}>$subject.orgId</code>). This keeps the definition
            portable across organizations. In the condition builder, fields are
            selected by name from a dropdown - no manual syntax required.
        </p>

        <h3 id={"trigger-events-article-h3-10"}>Learn more</h3>
        <ul id={"trigger-events-article-ul-8"}>
            <li id={"trigger-events-article-li-21"}>
                <a id={"trigger-events-article-a-1"}
                   href="#"
                   data-help-article="assignees-and-portability">
                    <b id={"trigger-events-article-b-35"}>Assignees and portability</b>
                </a>{" "}
                - how to reference trigger data in assignees safely.
            </li>
            <li id={"trigger-events-article-li-22"}>
                <a id={"trigger-events-article-a-2"}
                   href="#"
                   data-help-article="workflow-org-settings">
                    <b id={"trigger-events-article-b-36"}>Organization workflow settings</b>
                </a>{" "}
                - enabling or disabling recipient acceptance.
            </li>
        </ul>
    </>
);
