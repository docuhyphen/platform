import React from "react";

export const stepTypesArticle = (
    <>
        <p>
            Each step in a workflow has a type that determines how the engine
            processes it. There are five types: APPROVAL, NOTIFICATION, CONDITION,
            ACTION, and WAIT_FOR_COUNTERPARTY_CLEARANCE.
        </p>

        <h3>APPROVAL</h3>
        <p>
            An APPROVAL step pauses the workflow and waits for human decisions
            from the assigned principals. The step advances once the quorum
            requirement is met.
        </p>
        <p><b>Key fields:</b></p>
        <ul>
            <li>
                <b>Assignees</b> - who receives the approval task. See{" "}
                <a href="#"
                   data-help-article="assignees-and-portability">
                    Assignees and portability
                </a>
                .
            </li>
            <li>
                <b>Quorum</b> - how many approvals are needed:
                <ul>
                    <li><b>Any</b> - at least one assignee must approve.</li>
                    <li><b>All</b> - every assignee must approve.</li>
                    <li>
                        <b>N of M</b> - exactly N assignees must approve (N is
                        configured in the designer).
                    </li>
                </ul>
            </li>
            <li>
                <b>SLA (minutes)</b> - optional deadline. If the step is still
                pending after this many minutes, the escalation action triggers.
            </li>
            <li>
                <b>Escalation action</b> - what happens when the SLA expires:
                <ul>
                    <li><b>Escalate</b> - reassign to a different set of assignees.</li>
                    <li><b>Auto-approve</b> - automatically approve and advance.</li>
                    <li><b>Auto-reject</b> - automatically reject and advance.</li>
                </ul>
            </li>
            <li>
                <b>On Approve / On Reject</b> - the next step to go to and an
                optional domain event to emit when that outcome is reached.
            </li>
        </ul>

        <h3>NOTIFICATION</h3>
        <p>
            A NOTIFICATION step sends a message to the assigned principals and
            then advances automatically without waiting for a decision. Use it to
            inform stakeholders at key lifecycle points.
        </p>
        <p><b>Key fields:</b></p>
        <ul>
            <li>
                <b>Assignees</b> - who receives the notification.
            </li>
            <li>
                <b>Communication</b> - selects which communication to render for
                the notification. Click <b>Select communication...</b> to open
                the picker and browse Personal, Organization, and Platform
                communications. Leave blank to use the default system message.
                See{" "}
                <a href="#"
                   data-help-article="communications-in-workflows">
                    Using communications in workflows
                </a>
                .
            </li>
        </ul>
        <p>
            A NOTIFICATION step has no routing configuration. After the message
            is sent the workflow moves automatically to the next step in the list.
        </p>

        <h3>CONDITION</h3>
        <p>
            A CONDITION step evaluates a condition against the Exchange subject
            data and branches the workflow based on the result. It advances
            automatically without human input.
        </p>
        <p><b>Key fields:</b></p>
        <ul>
            <li>
                <b>Condition</b> - built using the visual condition builder. Select
                a field, an operator, and a value:
                <ul>
                    <li>
                        <b>Field</b> - a searchable dropdown of the subject fields
                        available for the selected trigger event (for example,
                        Recipient Type, Initiator, Recipient Group).
                    </li>
                    <li>
                        <b>Operator</b> - the comparison to apply. Available
                        operators depend on the field type:
                        <ul>
                            <li>Text, enum, UUID, and boolean fields: <b>is equal to</b>, <b>is not equal to</b>.</li>
                            <li>Numeric fields: additionally <b>is greater than</b>, <b>is less than</b>, <b>is greater than or equal to</b>, <b>is less than or equal to</b>.</li>
                        </ul>
                    </li>
                    <li>
                        <b>Value</b> - how you enter the value depends on the field type:
                        <ul>
                            <li><b>Enum fields</b> (e.g. Recipient Type) - a dropdown of the valid options.</li>
                            <li><b>Boolean fields</b> - a Yes/No dropdown.</li>
                            <li><b>User fields</b> (e.g. Initiator, Recipient) - a searchable picker that looks up users from your contacts.</li>
                            <li><b>Group fields</b> (e.g. Recipient Group) - a searchable picker that looks up your personal groups.</li>
                            <li><b>Text and numeric fields</b> - a free-text or number input.</li>
                        </ul>
                    </li>
                </ul>
            </li>
            <li>
                <b>On True</b> - the next step when the condition evaluates to
                true.
            </li>
            <li>
                <b>On False</b> - the next step when the condition evaluates to
                false.
            </li>
        </ul>
        <p>
            The available fields update automatically when you change the trigger
            event. A plain-language summary of the condition (for example,
            "Recipient Type is equal to Group") is shown below the builder to
            confirm your selection before saving.
        </p>

        <h3>ACTION</h3>
        <p>
            An ACTION step runs a built-in automated action and then advances
            automatically. Use it to trigger system operations as part of the
            workflow.
        </p>
        <p>
            ACTION steps have no assignees and no routing configuration. After the
            action completes the workflow moves automatically to the next step in
            the list.
        </p>
        <p><b>Key fields:</b></p>
        <ul>
            <li>
                <b>Action</b> - selects which built-in action to run:
                <ul>
                    <li>
                        <b>exchange.auto-accept</b> - activates the Exchange,
                        setting it to Active.
                    </li>
                    <li>
                        <b>exchange.send-reminder</b> - sends a reminder
                        notification to the Exchange's recipient.
                    </li>
                    <li>
                        <b>exchange.revoke-access</b> - revokes all active shares
                        on the Exchange.
                    </li>
                </ul>
            </li>
        </ul>

        <h3>WAIT_FOR_COUNTERPARTY_CLEARANCE</h3>
        <p>
            A WAIT_FOR_COUNTERPARTY_CLEARANCE step pauses the workflow until all
            workflow instances on the other party's side of the Exchange have
            finished. While waiting the step carries the status{" "}
            <b>Awaiting Counterparty</b>. It unblocks automatically with no human
            input required. See{" "}
            <a href="#"
               data-help-article="recipient-workflows">
                Recipient-side workflows
            </a>
            {" "}for full details.
        </p>

        <h3>Step ordering and branching</h3>
        <p>
            Steps are stored in a list and referred to by their index (0-based).
            The "On Approve / On Reject / On True / On False" outcome connectors
            point to the next step index or to <code>END</code> to terminate the
            workflow. Branching is supported through CONDITION steps, which route
            to different step indices on each branch.
        </p>

        <h3>Learn more</h3>
        <ul>
            <li>
                <a href="#"
                   data-help-article="building-a-workflow">
                    <b>Building a workflow</b>
                </a>{" "}
                - step-by-step guide, including how to add and configure steps in
                the designer.
            </li>
            <li>
                <a href="#"
                   data-help-article="sla-escalations-reminders">
                    <b>SLA, escalations, and reminders</b>
                </a>{" "}
                - deadline management for APPROVAL steps.
            </li>
        </ul>
    </>
);
