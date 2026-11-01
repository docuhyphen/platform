
export const workflowOverviewArticle = (
    <>
        <p id={"workflow-overview-article-p-1"}>
            Workflows let your organization gate every stage of an Exchange lifecycle
            with configurable steps: approvals, notifications, conditions, and
            automated actions. Instead of hard-coded status transitions, each stage
            can require human sign-off or automated logic before the Exchange
            advances.
        </p>

        <h3 id={"workflow-overview-article-h3-1"}>How workflows fit into the Exchange lifecycle</h3>
        <p id={"workflow-overview-article-p-2"}>
            Every Exchange moves through four statuses: <b id={"workflow-overview-article-b-1"}>Draft</b>,{" "}
            <b id={"workflow-overview-article-b-2"}>Active</b>, <b id={"workflow-overview-article-b-3"}>Completed</b>, and <b id={"workflow-overview-article-b-4"}>Rejected</b>. Workflows sit
            at the transitions between these statuses and gate them:
        </p>
        <ul id={"workflow-overview-article-ul-1"}>
            <li id={"workflow-overview-article-li-1"}>
                <b id={"workflow-overview-article-b-5"}>Draft submitted</b> - fires when an Exchange is first created.
                Use this to run internal pre-send approvals before recipients see
                anything.
            </li>
            <li id={"workflow-overview-article-li-2"}>
                <b id={"workflow-overview-article-b-6"}>Acceptance pending</b> - fires after the draft stage when
                recipient acceptance is required. This includes the organization
                setting and Trusted Organization person or group recipients. The
                Exchange stays in Draft until the workflow completes.
            </li>
            <li id={"workflow-overview-article-li-3"}>
                <b id={"workflow-overview-article-b-7"}>Activated</b> - fires when the Exchange transitions to Active.
                Use this for post-activation tasks such as notifications or
                automatic actions.
            </li>
            <li id={"workflow-overview-article-li-4"}>
                <b id={"workflow-overview-article-b-8"}>Ending</b> - fires when a user tries to close the Exchange.
                Use this for completion checklists or sign-off requirements before
                the Exchange is marked Completed.
            </li>
        </ul>

        <h3 id={"workflow-overview-article-h3-2"}>Core concepts</h3>
        <ul id={"workflow-overview-article-ul-2"}>
            <li id={"workflow-overview-article-li-5"}>
                <b id={"workflow-overview-article-b-9"}>Workflow definition</b> - a reusable, versioned template that
                describes which steps to run and in what order. Org admins create
                and manage definitions in Settings.
            </li>
            <li id={"workflow-overview-article-li-6"}>
                <b id={"workflow-overview-article-b-10"}>Workflow instance</b> - one live execution of a definition
                against a specific Exchange. Created automatically when the matching
                trigger event fires.
            </li>
            <li id={"workflow-overview-article-li-7"}>
                <b id={"workflow-overview-article-b-11"}>Step instance</b> - one step within a running instance.
                Tracks assignees, decisions, SLA deadlines, and status.
            </li>
        </ul>

        <h3 id={"workflow-overview-article-h3-3"}>Who can manage workflows</h3>
        <p id={"workflow-overview-article-p-3"}>
            Organization Admins can create, edit, activate, deactivate, publish,
            clone, or delete workflow definitions for their active organization.
            The Organization sub-tab exposes management actions only when the active
            organization grants the required administration capability and has workflow
            automation in its Business subscription.
        </p>
        <p id={"workflow-overview-article-p-4"}>
            If workflow automation becomes unavailable, existing definitions and history stay
            readable. New definitions, definition changes, and new workflow instances are refused.
            Instances that already started continue through their saved steps, decisions,
            reminders, actions, conditions, and escalations until they finish.
        </p>
        <p id={"workflow-overview-article-p-5"}>
            App Admins manage platform-scoped workflow definitions and templates.
            Open <b id={"workflow-overview-article-b-12"}>Platform Administration</b> from the top application bar and
            use <b id={"workflow-overview-article-b-13"}>Platform Content</b>. The platform editor is isolated from
            organization workflow and directory data.
            The global App Admin role does not grant access to an organization's
            workflows or workflow activity. An App Admin needs a separate
            Organization Admin role in the active organization to manage that
            organization's workflows.
        </p>
        <p id={"workflow-overview-article-p-6"}>
            The workflow lists use a fixed footer with the visible range on the
            left and page navigation actions on the right.
        </p>

        <h3 id={"workflow-overview-article-h3-4"}>Learn more</h3>
        <ul id={"workflow-overview-article-ul-3"}>
            <li id={"workflow-overview-article-li-8"}>
                <a id={"workflow-overview-article-a-1"}
                   href="#"
                   data-help-article="trigger-events">
                    <b id={"workflow-overview-article-b-14"}>Trigger events</b>
                </a>{" "}
                - what events are available and when they fire.
            </li>
            <li id={"workflow-overview-article-li-9"}>
                <a id={"workflow-overview-article-a-2"}
                   href="#"
                   data-help-article="building-a-workflow">
                    <b id={"workflow-overview-article-b-15"}>Building a workflow</b>
                </a>{" "}
                - step-by-step guide to creating your first workflow.
            </li>
            <li id={"workflow-overview-article-li-10"}>
                <a id={"workflow-overview-article-a-3"}
                   href="#"
                   data-help-article="step-types">
                    <b id={"workflow-overview-article-b-16"}>Step types explained</b>
                </a>{" "}
                - Approval, Notification, Condition, and Action steps.
            </li>
            <li id={"workflow-overview-article-li-11"}>
                <a id={"workflow-overview-article-a-4"}
                   href="#"
                   data-help-article="platform-templates">
                    <b id={"workflow-overview-article-b-17"}>Platform templates</b>
                </a>{" "}
                - ready-made workflows you can clone into your personal collection.
            </li>
        </ul>
    </>
);
