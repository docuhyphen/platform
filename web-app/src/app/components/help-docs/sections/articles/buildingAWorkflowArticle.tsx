export const buildingAWorkflowArticle = (
    <>
        <p id={"building-a-workflow-article-p-1"}>
            This guide walks you through creating a workflow from scratch using the
            Workflow Designer in Settings.
        </p>
        <h3 id={"building-a-workflow-article-h3-1"}>Before you begin</h3>
        <ul id={"building-a-workflow-article-ul-1"}>
            <li id={"building-a-workflow-article-li-1"}>You must be an Organization Admin in a Business context that includes workflow automation.</li>
            <li id={"building-a-workflow-article-li-2"}>
                Decide which lifecycle stage you want to gate and choose the
                corresponding{" "}
                <a id={"building-a-workflow-article-a-1"}
                   href="#"
                   data-help-article="trigger-events">
                    trigger event
                </a>
                .
            </li>
            <li id={"building-a-workflow-article-li-3"}>
                Know who the approvers or notification recipients should be. Using
                role-based or group-role-based assignees is strongly recommended
                over hardcoded user IDs.
            </li>
        </ul>
        <h3 id={"building-a-workflow-article-h3-2"}>Step 1: open the Workflow Designer</h3>
        <ol id={"building-a-workflow-article-ol-1"}>
            <li id={"building-a-workflow-article-li-4"}>Open Settings from the top navigation bar.</li>
            <li id={"building-a-workflow-article-li-5"}>Click the <b id={"building-a-workflow-article-b-1"}>Workflows</b> tab.</li>
            <li id={"building-a-workflow-article-li-6"}>
                Click <b id={"building-a-workflow-article-b-2"}>New workflow</b> to open the designer, or click{" "}
                <b id={"building-a-workflow-article-b-3"}>Edit</b> on an existing workflow row to modify it.
            </li>
        </ol><p id={"building-a-workflow-article-p-2"}>
            The info button (i) in the top-right of the title bar opens this help article at any time.
        </p>
        <h3 id={"building-a-workflow-article-h3-3"}>Form and Diagram</h3>
        <p id={"building-a-workflow-article-p-3"}>
            The designer keeps the live read-only Diagram on the left and the
            editable Form on the right; the panes stack on narrow screens. The
            bottom-right orientation icon switches the Diagram between vertical
            and horizontal layouts. See{" "}
            <a id={"building-a-workflow-article-a-2"}
               href="#"
               data-help-article="workflow-diagram-preview">Workflow diagrams and preview</a>.
        </p>
        <h3 id={"building-a-workflow-article-h3-4"}>Step 2: fill in the header fields</h3><p id={"building-a-workflow-article-p-4"}>Open the <b id={"building-a-workflow-article-b-4"}>Workflow configuration</b> card.</p>
        <ul id={"building-a-workflow-article-ul-2"}>
            <li id={"building-a-workflow-article-li-7"}>
                <b id={"building-a-workflow-article-b-5"}>Name</b> - a short, descriptive name shown in the workflow list
                (for example, "Recipient Acceptance Gate" or "Pre-Send Manager Approval").
            </li>
            <li id={"building-a-workflow-article-li-8"}>
                <b id={"building-a-workflow-article-b-6"}>Trigger event</b> - select from the dropdown. The fields
                available for assignee placeholders (for example, the
                recipient or the initiating organization) update automatically
                based on your selection.
            </li>
            <li id={"building-a-workflow-article-li-9"}>
                <b id={"building-a-workflow-article-b-7"}>Summary</b> - a plain-language description of what this
                workflow does and when it applies. Shown in the template library.
            </li>
            <li id={"building-a-workflow-article-li-10"}>
                <b id={"building-a-workflow-article-b-8"}>Tags</b> - optional free-text labels (Legal, Finance,
                Healthcare, HR, Logistics, etc.) that help teams filter templates.
                Any tag value is valid.
            </li>
            <li id={"building-a-workflow-article-li-11"}>
                <b id={"building-a-workflow-article-b-9"}>Active</b> - only active workflows are matched when a trigger
                event fires. Toggle this off to pause a workflow without deleting it.
            </li>
        </ul>
        <h3 id={"building-a-workflow-article-h3-5"}>Step 3: add steps</h3>
        <ol id={"building-a-workflow-article-ol-2"}>
            <li id={"building-a-workflow-article-li-12"}>Return with the arrow button, open <b id={"building-a-workflow-article-b-10"}>Steps</b>, and click <b id={"building-a-workflow-article-b-11"}>Add Step</b>.</li>
            <li id={"building-a-workflow-article-li-13"}>
                Click the new step card, then choose a{" "}
                <a id={"building-a-workflow-article-a-3"}
                   href="#"
                   data-help-article="step-types">
                    step type
                </a>{" "}
                from the dropdown: Approval, Notification, Condition, or Action.
            </li>
            <li id={"building-a-workflow-article-li-14"}>
                Configure the step fields for that type:
                <ul id={"building-a-workflow-article-ul-3"}>
                    <li id={"building-a-workflow-article-li-15"}>
                        For <b id={"building-a-workflow-article-b-12"}>Condition</b> steps, use the visual condition
                        builder to select a field, an operator, and a value. Text
                        fields support equality, contains, and starts with; number
                        fields support comparisons. Invalid conditions cannot be saved.
                    </li>
                    <li id={"building-a-workflow-article-li-16"}>
                        For <b id={"building-a-workflow-article-b-13"}>Approval</b> steps, configure assignees, quorum,
                        optional SLA, escalation action, and escalation targets
                        when the action is <b id={"building-a-workflow-article-b-14"}>Escalate</b>.
                    </li>
                    <li id={"building-a-workflow-article-li-17"}>
                        For <b id={"building-a-workflow-article-b-15"}>Notification</b> steps, optionally select a
                        communication (via the picker) and add assignees who
                        will receive the notification.
                    </li>
                    <li id={"building-a-workflow-article-li-18"}>
                        For <b id={"building-a-workflow-article-b-16"}>Action</b> steps, select the action to run. No
                        assignees are needed - the action runs automatically.
                    </li>
                </ul>
            </li>
            <li id={"building-a-workflow-article-li-19"}>
                Set the <b id={"building-a-workflow-article-b-17"}>outcome connectors</b> (On Approve / On Reject, or On
                True / On False for Condition steps) to point to the next step
                index or to END. Deleting a step lists every route that points at
                it, rewrites those to END on confirmation, and shifts later routes
                automatically so none is left dangling.
            </li>
            <li id={"building-a-workflow-article-li-20"}>Repeat for each step in the workflow.</li>
            <li id={"building-a-workflow-article-li-21"}>Use the fixed header's arrow button to return to the step cards.</li>
        </ol>
        <p id={"building-a-workflow-article-p-11"}>
            Continue with{" "}
            <a id={"building-a-workflow-article-a-5"}
               href="#"
               data-help-article="reviewing-and-saving-a-workflow">Reviewing, saving and maintaining a workflow</a>
            .
        </p>
    </>
);
