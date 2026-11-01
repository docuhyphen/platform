
export const recipientWorkflowsArticle = (
    <>
        <p id={"recipient-workflows-article-p-1"}>
            Recipient-side workflows let organizations run their own internal
            approval or notification processes when they receive an Exchange,
            independent of and invisible to the initiating organization.
        </p>

        <h3 id={"recipient-workflows-article-h3-1"}>How it works</h3>
        <p id={"recipient-workflows-article-p-2"}>
            When a lifecycle event fires on an Exchange (for example, the Exchange
            is sent), the workflow engine fires a parallel set of recipient-side
            events in the context of each recipient organization. If a recipient
            org has an active workflow definition bound to one of those events, a
            new workflow instance is created for that org.
        </p>
        <p id={"recipient-workflows-article-p-3"}>
            Each party's workflow instances are private to their own organization.
            A recipient user opening the Workflow tab on an Exchange only sees
            instances that belong to their organization. They cannot see the
            initiator's internal steps, assignees, or decisions. The tab offers both
            a Timeline and a read-only{" "}
            <a id={"recipient-workflows-article-a-1"}
               href="#"
               data-help-article="workflow-diagram-preview">
                Diagram
            </a>{" "}
            view of those instances.
        </p>

        <h3 id={"recipient-workflows-article-h3-2"}>Recipient trigger events</h3>
        <ul id={"recipient-workflows-article-ul-1"}>
            <li id={"recipient-workflows-article-li-1"}>
                <b id={"recipient-workflows-article-b-1"}>Received</b> <code id={"recipient-workflows-article-code-1"}>(exchange.received)</code> - fires in the recipient org when an
                Exchange is sent and waiting for acceptance.
            </li>
            <li id={"recipient-workflows-article-li-2"}>
                <b id={"recipient-workflows-article-b-2"}>Activated (Recipient Side)</b> <code id={"recipient-workflows-article-code-2"}>(exchange.received_activated)</code> - fires in the recipient org
                when the Exchange transitions to Active.
            </li>
            <li id={"recipient-workflows-article-li-3"}>
                <b id={"recipient-workflows-article-b-3"}>Ending (Recipient Side)</b> <code id={"recipient-workflows-article-code-3"}>(exchange.received_ending)</code> - fires in the recipient org when
                the Exchange is being closed.
            </li>
        </ul>
        <p id={"recipient-workflows-article-p-4"}>
            Create a workflow definition in Settings scoped to your organization
            and set the trigger to one of the events above. Publish the definition
            to activate it.
        </p>

        <h3 id={"recipient-workflows-article-h3-3"}>Internal Clearance Status</h3>
        <p id={"recipient-workflows-article-p-5"}>
            The Workflow tab on an Exchange shows an <b id={"recipient-workflows-article-b-4"}>Internal Clearance
            Status</b> card when either party has active workflows running. The
            card shows aggregate status badges for your organization and each
            counterparty:
        </p>
        <ul id={"recipient-workflows-article-ul-2"}>
            <li id={"recipient-workflows-article-li-4"}><b id={"recipient-workflows-article-b-5"}>Running</b> - at least one workflow instance is still in progress.</li>
            <li id={"recipient-workflows-article-li-5"}><b id={"recipient-workflows-article-b-6"}>Cleared</b> - all workflow instances have completed successfully.</li>
            <li id={"recipient-workflows-article-li-6"}><b id={"recipient-workflows-article-b-7"}>Blocked</b> - at least one workflow instance was rejected or cancelled.</li>
            <li id={"recipient-workflows-article-li-7"}><b id={"recipient-workflows-article-b-8"}>None</b> - no workflow instances exist (badge not shown).</li>
        </ul>
        <p id={"recipient-workflows-article-p-6"}>
            No internal step details, assignee names, or decision reasons from the
            other party are ever shown. Only the aggregate status badge is visible
            across organizational boundaries.
        </p>

        <h3 id={"recipient-workflows-article-h3-4"}>Coordinating across parties with Wait for Counterparty Clearance</h3>
        <p id={"recipient-workflows-article-p-7"}>
            If you need your workflow to pause until the other party's workflows
            are also done, add a{" "}
            <b id={"recipient-workflows-article-b-9"}>Wait for Counterparty Clearance</b> step. The step parks with status{" "}
            <b id={"recipient-workflows-article-b-10"}>Awaiting Counterparty</b> until all workflow instances on the other
            party's side of the Exchange reach a terminal state, then unblocks
            automatically.
        </p>
        <p id={"recipient-workflows-article-p-8"}>
            See{" "}
            <a id={"recipient-workflows-article-a-2"}
               href="#"
               data-help-article="step-types">
                Step types explained
            </a>
            {" "}for full configuration details.
        </p>

        <h3 id={"recipient-workflows-article-h3-5"}>Learn more</h3>
        <ul id={"recipient-workflows-article-ul-3"}>
            <li id={"recipient-workflows-article-li-8"}>
                <a id={"recipient-workflows-article-a-3"}
                   href="#"
                   data-help-article="trigger-events">
                    <b id={"recipient-workflows-article-b-11"}>Trigger events</b>
                </a>{" "}
                - full list of available triggers including the three recipient-side events.
            </li>
            <li id={"recipient-workflows-article-li-9"}>
                <a id={"recipient-workflows-article-a-4"}
                   href="#"
                   data-help-article="step-types">
                    <b id={"recipient-workflows-article-b-12"}>Step types explained</b>
                </a>{" "}
                - Wait for Counterparty Clearance step configuration.
            </li>
        </ul>
    </>
);
