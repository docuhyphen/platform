
export const stepTypesArticle = (
    <>
        <p id={"step-types-article-p-1"}>
            Each workflow step type controls how the engine advances the workflow.
            The available types are Approval, Notification, Condition, Action, and
            Wait for Counterparty Clearance.
        </p>

        <h3 id={"step-types-article-h3-1"}>Approval <code id={"step-types-article-code-1"}>(APPROVAL)</code></h3>
        <p id={"step-types-article-p-2"}>
            An Approval step pauses the workflow until assigned principals make a decision.
            The step advances when the configured quorum is met.
        </p>
        <ul id={"step-types-article-ul-1"}>
            <li id={"step-types-article-li-1"}>
                <b id={"step-types-article-b-1"}>Assignees</b> - users, roles, groups, or portable references
                that receive the approval task. See{" "}
                <a id={"step-types-article-a-1"}
                   href="#"
                   data-help-article="assignees-and-portability">
                    Assignees and portability
                </a>
                .
            </li>
            <li id={"step-types-article-li-2"}>
                <b id={"step-types-article-b-2"}>Quorum</b> - Any requires one approval, All requires every
                assignee, and N of M requires the configured number of approvals.
            </li>
            <li id={"step-types-article-li-3"}>
                <b id={"step-types-article-b-3"}>SLA and escalation</b> - an optional deadline can escalate,
                auto-approve, or auto-reject when the step remains pending.
            </li>
            <li id={"step-types-article-li-4"}>
                <b id={"step-types-article-b-4"}>Outcomes</b> - On Approve and On Reject choose the next step
                or <code id={"step-types-article-code-2"}>END</code>, and can emit an optional domain event.
            </li>
        </ul>

        <h3 id={"step-types-article-h3-2"}>Notification <code id={"step-types-article-code-3"}>(NOTIFICATION)</code></h3>
        <p id={"step-types-article-p-3"}>
            A Notification step sends a message to assigned principals and then advances
            automatically. Choose a saved communication or leave the field blank to
            use the default system message. See{" "}
            <a id={"step-types-article-a-2"}
               href="#"
               data-help-article="communications-in-workflows">
                Using communications in workflows
            </a>
            .
        </p>

        <h3 id={"step-types-article-h3-3"}>Condition <code id={"step-types-article-code-4"}>(CONDITION)</code></h3>
        <p id={"step-types-article-p-4"}>
            A Condition step evaluates Exchange data and routes based on the
            result. The builder lets you select a field, an operator, and a value.
            Available fields update when the trigger event changes.
        </p>
        <ul id={"step-types-article-ul-2"}>
            <li id={"step-types-article-li-5"}>
                Text, enum, UUID, and boolean fields support equality operators.
            </li>
            <li id={"step-types-article-li-6"}>
                Numeric fields also support greater-than and less-than operators.
            </li>
            <li id={"step-types-article-li-7"}>
                Enum, boolean, user, and group values use purpose-built pickers;
                text and numeric values use direct inputs.
            </li>
            <li id={"step-types-article-li-8"}>
                On True and On False choose the next step or <code id={"step-types-article-code-5"}>END</code>.
            </li>
        </ul>

        <h3 id={"step-types-article-h3-4"}>Action <code id={"step-types-article-code-6"}>(ACTION)</code></h3>
        <p id={"step-types-article-p-5"}>
            An Action step runs a built-in automated operation and then advances
            without assignees or routing configuration.
        </p>
        <ul id={"step-types-article-ul-3"}>
            <li id={"step-types-article-li-9"}><b id={"step-types-article-b-5"}>Auto-accept the Exchange</b> <code id={"step-types-article-code-7"}>(exchange.auto-accept)</code> - activates the Exchange.</li>
            <li id={"step-types-article-li-10"}><b id={"step-types-article-b-6"}>Send a reminder</b> <code id={"step-types-article-code-8"}>(exchange.send-reminder)</code> - sends a reminder notification.</li>
            <li id={"step-types-article-li-11"}><b id={"step-types-article-b-7"}>Revoke access</b> <code id={"step-types-article-code-9"}>(exchange.revoke-access)</code> - revokes active Exchange shares.</li>
        </ul>

        <h3 id={"step-types-article-h3-5"}>Wait for Counterparty Clearance <code id={"step-types-article-code-10"}>(WAIT_FOR_COUNTERPARTY_CLEARANCE)</code></h3>
        <p id={"step-types-article-p-6"}>
            A Wait for Counterparty Clearance step waits until workflow instances on the
            other party's side of the Exchange are complete. While waiting, the step
            shows <b id={"step-types-article-b-8"}>Awaiting Counterparty</b>. See{" "}
            <a id={"step-types-article-a-3"}
               href="#"
               data-help-article="recipient-workflows">
                Recipient-side workflows
            </a>
            .
        </p>

        <h3 id={"step-types-article-h3-6"}>Ordering and branching</h3>
        <p id={"step-types-article-p-7"}>
            Steps are stored in order and referenced by zero-based index. Outcome
            connectors point to another step or to <b id={"step-types-article-b-9"}>End</b>. Condition steps
            create branches by sending true and false outcomes to different targets.
        </p>

        <h3 id={"step-types-article-h3-7"}>Learn more</h3>
        <ul id={"step-types-article-ul-4"}>
            <li id={"step-types-article-li-12"}>
                <a id={"step-types-article-a-4"}
                   href="#"
                   data-help-article="building-a-workflow">
                    <b id={"step-types-article-b-10"}>Building a workflow</b>
                </a>{" "}
                - how to add and configure steps in the designer.
            </li>
            <li id={"step-types-article-li-13"}>
                <a id={"step-types-article-a-5"}
                   href="#"
                   data-help-article="sla-escalations-reminders">
                    <b id={"step-types-article-b-11"}>SLA, escalations, and reminders</b>
                </a>{" "}
                - deadline management for Approval steps.
            </li>
        </ul>
    </>
);
