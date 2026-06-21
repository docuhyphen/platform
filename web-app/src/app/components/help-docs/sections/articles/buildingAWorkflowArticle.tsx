import React from "react";

export const buildingAWorkflowArticle = (
    <>
        <p>
            This guide walks you through creating a workflow definition from scratch
            using the Workflow Designer in Settings.
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
                <b>Edit</b> on an existing workflow definition row to modify it.
            </li>
        </ol>
        <p>
            Once in the designer, the info button (i) in the top-right corner of
            the title bar opens this help article at any time.
        </p>

        <h3>Step 2: fill in the header fields</h3>
        <ul>
            <li>
                <b>Name</b> - a short, descriptive name shown in the definition
                list (for example, "Recipient Acceptance Gate" or "Pre-Send Manager
                Approval").
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
                <b>Active</b> - only active definitions are matched when a trigger
                event fires. Toggle this off to pause a workflow without deleting
                it.
            </li>
        </ul>

        <h3>Step 3: add steps</h3>
        <ol>
            <li>Click <b>Add step</b> below the header section.</li>
            <li>
                Choose a{" "}
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
                        builder to select a field, an operator, and a value. User
                        and group fields include a searchable picker. A plain-language
                        summary confirms your selection.
                    </li>
                    <li>
                        For <b>APPROVAL</b> steps, configure assignees, quorum,
                        optional SLA, and escalation action.
                    </li>
                    <li>
                        For <b>NOTIFICATION</b> steps, select a message template
                        key and add assignees who will receive the notification.
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
                index or to END.
            </li>
            <li>Repeat for each step in the workflow.</li>
        </ol>

        <h3>Step 4: review portability warnings</h3>
        <p>
            Before saving, the designer checks whether any step contains a
            hardcoded principal UUID in an assignee entry. If it does, a portability
            warning appears in the save bar. Hardcoded UUIDs work in your org but
            cannot be reused if the definition is cloned by another org. Replace
            them with{" "}
            <a href="#"
               data-help-article="assignees-and-portability">
                role-based or group-role-based assignees
            </a>{" "}
            to make the workflow fully portable.
        </p>

        <h3>Step 5: save</h3>
        <p>
            Click <b>Save workflow</b>. The definition is created (POST) or updated
            (PUT) immediately. If the Active toggle is on, the engine will match
            this definition the next time the trigger event fires for an Exchange
            in your organization.
        </p>

        <h3>Editing a live workflow</h3>
        <p>
            You cannot edit a definition while it has at least one running
            instance. The edit button is disabled and a message explains why. Wait
            for the running instances to complete or cancel them before editing.
        </p>

        <h3>Deleting a workflow</h3>
        <p>
            Delete deactivates the definition (soft delete). It cannot be deleted
            while running instances reference it. Completed historical instances
            are preserved for audit purposes.
        </p>
    </>
);
