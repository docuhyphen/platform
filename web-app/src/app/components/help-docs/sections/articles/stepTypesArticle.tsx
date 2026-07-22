import React from "react";

export const stepTypesArticle = (
    <>
        <p>
            Each workflow step type controls how the engine advances the workflow.
            The available types are Approval, Notification, Condition, Action, and
            Wait for Counterparty Clearance.
        </p>

        <h3>Approval <code>(APPROVAL)</code></h3>
        <p>
            An Approval step pauses the workflow until assigned principals make a decision.
            The step advances when the configured quorum is met.
        </p>
        <ul>
            <li>
                <b>Assignees</b> - users, roles, groups, or portable references
                that receive the approval task. See{" "}
                <a href="#" data-help-article="assignees-and-portability">
                    Assignees and portability
                </a>
                .
            </li>
            <li>
                <b>Quorum</b> - Any requires one approval, All requires every
                assignee, and N of M requires the configured number of approvals.
            </li>
            <li>
                <b>SLA and escalation</b> - an optional deadline can escalate,
                auto-approve, or auto-reject when the step remains pending.
            </li>
            <li>
                <b>Outcomes</b> - On Approve and On Reject choose the next step
                or <code>END</code>, and can emit an optional domain event.
            </li>
        </ul>

        <h3>Notification <code>(NOTIFICATION)</code></h3>
        <p>
            A Notification step sends a message to assigned principals and then advances
            automatically. Choose a saved communication or leave the field blank to
            use the default system message. See{" "}
            <a href="#" data-help-article="communications-in-workflows">
                Using communications in workflows
            </a>
            .
        </p>

        <h3>Condition <code>(CONDITION)</code></h3>
        <p>
            A Condition step evaluates Exchange data and routes based on the
            result. The builder lets you select a field, an operator, and a value.
            Available fields update when the trigger event changes.
        </p>
        <ul>
            <li>
                Text, enum, UUID, and boolean fields support equality operators.
            </li>
            <li>
                Numeric fields also support greater-than and less-than operators.
            </li>
            <li>
                Enum, boolean, user, and group values use purpose-built pickers;
                text and numeric values use direct inputs.
            </li>
            <li>
                On True and On False choose the next step or <code>END</code>.
            </li>
        </ul>

        <h3>Action <code>(ACTION)</code></h3>
        <p>
            An Action step runs a built-in automated operation and then advances
            without assignees or routing configuration.
        </p>
        <ul>
            <li><b>Auto-accept the Exchange</b> <code>(exchange.auto-accept)</code> - activates the Exchange.</li>
            <li><b>Send a reminder</b> <code>(exchange.send-reminder)</code> - sends a reminder notification.</li>
            <li><b>Revoke access</b> <code>(exchange.revoke-access)</code> - revokes active Exchange shares.</li>
        </ul>

        <h3>Wait for Counterparty Clearance <code>(WAIT_FOR_COUNTERPARTY_CLEARANCE)</code></h3>
        <p>
            A Wait for Counterparty Clearance step waits until workflow instances on the
            other party's side of the Exchange are complete. While waiting, the step
            shows <b>Awaiting Counterparty</b>. See{" "}
            <a href="#" data-help-article="recipient-workflows">
                Recipient-side workflows
            </a>
            .
        </p>

        <h3>Ordering and branching</h3>
        <p>
            Steps are stored in order and referenced by zero-based index. Outcome
            connectors point to another step or to <b>End</b>. Condition steps
            create branches by sending true and false outcomes to different targets.
        </p>

        <h3>Learn more</h3>
        <ul>
            <li>
                <a href="#" data-help-article="building-a-workflow">
                    <b>Building a workflow</b>
                </a>{" "}
                - how to add and configure steps in the designer.
            </li>
            <li>
                <a href="#" data-help-article="sla-escalations-reminders">
                    <b>SLA, escalations, and reminders</b>
                </a>{" "}
                - deadline management for Approval steps.
            </li>
        </ul>
    </>
);
