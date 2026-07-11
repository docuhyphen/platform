import React from "react";
export const buildingAWorkflowArticle = (
    <>
        <p>
            This guide walks you through creating a workflow from scratch using the
            Workflow Designer in Settings.
        </p>
        <h3>Before you begin</h3>
        <ul>
            <li>You must be an Organization Admin.</li>
            <li>
                Decide which lifecycle stage you want to gate and choose the
                corresponding{" "}
                <a href="#"
                   data-help-article="trigger-events">
                    trigger event
                </a>
                .
            </li>
            <li>
                Know who the approvers or notification recipients should be. Using
                role-based or group-role-based assignees is strongly recommended
                over hardcoded user IDs.
            </li>
        </ul>
        <h3>Step 1: open the Workflow Designer</h3>
        <ol>
            <li>Open Settings from the top navigation bar.</li>
            <li>Click the <b>Workflows</b> tab.</li>
            <li>
                Click <b>New workflow</b> to open the designer, or click{" "}
                <b>Edit</b> on an existing workflow row to modify it.
            </li>
        </ol><p>
            The info button (i) in the top-right of the title bar opens this help article at any time.
        </p>
        <h3>Form and Diagram</h3>
        <p>
            The designer keeps the live read-only Diagram on the left and the
            editable Form on the right; the panes stack on narrow screens. The
            bottom-right orientation icon switches the Diagram between vertical
            and horizontal layouts. See{" "}
            <a href="#" data-help-article="workflow-diagram-preview">Workflow diagrams and preview</a>.
        </p>
        <h3>Step 2: fill in the header fields</h3><p>Open the <b>Workflow configuration</b> card.</p>
        <ul>
            <li>
                <b>Name</b> - a short, descriptive name shown in the workflow list
                (for example, "Recipient Acceptance Gate" or "Pre-Send Manager Approval").
            </li>
            <li>
                <b>Trigger event</b> - select from the dropdown. The available{" "}
                <code>$subject.*</code> fields for assignee placeholders update
                automatically based on your selection.
            </li>
            <li>
                <b>Summary</b> - a plain-language description of what this
                workflow does and when it applies. Shown in the template library.
            </li>
            <li>
                <b>Tags</b> - optional free-text labels (Legal, Finance,
                Healthcare, HR, Logistics, etc.) that help teams filter templates.
                Any tag value is valid.
            </li>
            <li>
                <b>Active</b> - only active workflows are matched when a trigger
                event fires. Toggle this off to pause a workflow without deleting it.
            </li>
        </ul>
        <h3>Step 3: add steps</h3>
        <ol>
            <li>Return with the arrow button, open <b>Steps</b>, and click <b>Add Step</b>.</li>
            <li>
                Click the new step card, then choose a{" "}
                <a href="#"
                   data-help-article="step-types">
                    step type
                </a>{" "}
                from the dropdown: APPROVAL, NOTIFICATION, CONDITION, or ACTION.
            </li>
            <li>
                Configure the step fields for that type:
                <ul>
                    <li>
                        For <b>CONDITION</b> steps, use the visual condition
                        builder to select a field, an operator, and a value. Text
                        fields support equality, contains, and starts with; number
                        fields support comparisons. Invalid conditions cannot be saved.
                    </li>
                    <li>
                        For <b>APPROVAL</b> steps, configure assignees, quorum,
                        optional SLA, escalation action, and escalation targets
                        when the action is <b>Escalate</b>.
                    </li>
                    <li>
                        For <b>NOTIFICATION</b> steps, optionally select a
                        communication (via the picker) and add assignees who
                        will receive the notification.
                    </li>
                    <li>
                        For <b>ACTION</b> steps, select the action key. No
                        assignees are needed - the action runs automatically.
                    </li>
                </ul>
            </li>
            <li>
                Set the <b>outcome connectors</b> (On Approve / On Reject, or On
                True / On False for CONDITION steps) to point to the next step
                index or to END. Deleting a step lists every route that points at
                it, rewrites those to END on confirmation, and shifts later routes
                automatically so none is left dangling.
            </li>
            <li>Repeat for each step in the workflow.</li>
            <li>Use the fixed header's arrow button to return to the step cards.</li>
        </ol>
        <h3>Step 4: review warnings</h3>
        <p>
            The step list flags any step whose routes point at a step that no
            longer exists, and the save dialog repeats the routing warnings before
            you confirm. The designer also flags a hardcoded principal UUID in an
            assignee entry, which cannot be reused if the workflow is cloned, so
            replace them with{" "}
            <a href="#" data-help-article="assignees-and-portability">role-based or group-role-based assignees</a>.
        </p><h3>Step 5: set applicability (optional)</h3>
        <p>
            Open the <b>Applicability</b> card to restrict an organization workflow to Exchanges whose business field values match one or more conditions (leave empty to always run); see <a href="#" data-help-article="workflow-applicability">field-based applicability</a>.
        </p><h3>Step 6: save</h3>
        <p>
            Click <b>Save workflow</b>. The workflow is created (POST) or updated
            (PUT) immediately. If the Active toggle is on, the engine will use it
            the next time the trigger event fires for an Exchange in your organization.
        </p>
        <p>
            Saving is rejected when the definition would fail at runtime (no steps, an APPROVAL step with no assignees, a required approval count above its assignees, a step routing to itself or a missing step, a step that cannot reach END, or escalation without an SLA or targets). The message names the affected step.
        </p>
        <h3>Editing a live workflow</h3>
        <p>
            You cannot edit a workflow while it is still in progress for one or
            more Exchanges. The message lists the affected Exchanges so you know
            what still needs to finish or be cancelled before you try again.
        </p>
        <h3>Deleting a workflow</h3>
        <p>
            Delete deactivates the workflow (soft delete). It cannot be deleted
            while in-progress runs still use it; completed history is preserved.
        </p>
    </>
);
