import React from "react";

export const recipientWorkflowsArticle = (
    <>
        <p>
            Recipient-side workflows let organizations run their own internal
            approval or notification processes when they receive an Exchange,
            independent of and invisible to the initiating organization.
        </p>

        <h3>How it works</h3>
        <p>
            When a lifecycle event fires on an Exchange (for example, the Exchange
            is sent), the workflow engine fires a parallel set of recipient-side
            events in the context of each recipient organization. If a recipient
            org has an active workflow definition bound to one of those events, a
            new workflow instance is created for that org.
        </p>
        <p>
            Each party's workflow instances are private to their own organization.
            A recipient user opening the Workflow tab on an Exchange only sees
            instances that belong to their organization. They cannot see the
            initiator's internal steps, assignees, or decisions. The tab offers both
            a Timeline and a read-only{" "}
            <a href="#"
               data-help-article="workflow-diagram-preview">
                Diagram
            </a>{" "}
            view of those instances.
        </p>

        <h3>Recipient trigger events</h3>
        <ul>
            <li>
                <b>exchange.received</b> - fires in the recipient org when an
                Exchange is sent and waiting for acceptance.
            </li>
            <li>
                <b>exchange.received_activated</b> - fires in the recipient org
                when the Exchange transitions to Active.
            </li>
            <li>
                <b>exchange.received_ending</b> - fires in the recipient org when
                the Exchange is being closed.
            </li>
        </ul>
        <p>
            Create a workflow definition in Settings scoped to your organization
            and set the trigger to one of the events above. Publish the definition
            to activate it.
        </p>

        <h3>Internal Clearance Status</h3>
        <p>
            The Workflow tab on an Exchange shows an <b>Internal Clearance
            Status</b> card when either party has active workflows running. The
            card shows aggregate status badges for your organization and each
            counterparty:
        </p>
        <ul>
            <li><b>Running</b> - at least one workflow instance is still in progress.</li>
            <li><b>Cleared</b> - all workflow instances have completed successfully.</li>
            <li><b>Blocked</b> - at least one workflow instance was rejected or cancelled.</li>
            <li><b>None</b> - no workflow instances exist (badge not shown).</li>
        </ul>
        <p>
            No internal step details, assignee names, or decision reasons from the
            other party are ever shown. Only the aggregate status badge is visible
            across organizational boundaries.
        </p>

        <h3>Coordinating across parties with WAIT_FOR_COUNTERPARTY_CLEARANCE</h3>
        <p>
            If you need your workflow to pause until the other party's workflows
            are also done, add a{" "}
            <b>Wait for Counterparty Clearance</b> step. The step parks with status
            <b>Awaiting Counterparty</b> until all workflow instances on the other
            party's side of the Exchange reach a terminal state, then unblocks
            automatically.
        </p>
        <p>
            See{" "}
            <a href="#"
               data-help-article="step-types">
                Step types explained
            </a>
            {" "}for full configuration details.
        </p>

        <h3>Learn more</h3>
        <ul>
            <li>
                <a href="#"
                   data-help-article="trigger-events">
                    <b>Trigger events</b>
                </a>{" "}
                - full list of available triggers including the three recipient-side events.
            </li>
            <li>
                <a href="#"
                   data-help-article="step-types">
                    <b>Step types explained</b>
                </a>{" "}
                - WAIT_FOR_COUNTERPARTY_CLEARANCE step configuration.
            </li>
        </ul>
    </>
);
