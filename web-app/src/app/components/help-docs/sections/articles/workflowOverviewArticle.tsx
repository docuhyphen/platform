import React from "react";

export const workflowOverviewArticle = (
    <>
        <p>
            Workflows let your organization gate every stage of an Exchange lifecycle
            with configurable steps: approvals, notifications, conditions, and
            automated actions. Instead of hard-coded status transitions, each stage
            can require human sign-off or automated logic before the Exchange
            advances.
        </p>

        <h3>How workflows fit into the Exchange lifecycle</h3>
        <p>
            Every Exchange moves through four statuses: <b>Draft</b>,{" "}
            <b>Active</b>, <b>Completed</b>, and <b>Rejected</b>. Workflows sit
            at the transitions between these statuses and gate them:
        </p>
        <ul>
            <li>
                <b>Draft submitted</b> - fires when an Exchange is first created.
                Use this to run internal pre-send approvals before recipients see
                anything.
            </li>
            <li>
                <b>Acceptance pending</b> - fires after the draft stage when
                recipient acceptance is required. This includes the organization
                setting and Trusted Organization person or group recipients. The
                Exchange stays in Draft until the workflow completes.
            </li>
            <li>
                <b>Activated</b> - fires when the Exchange transitions to Active.
                Use this for post-activation tasks such as notifications or
                automatic actions.
            </li>
            <li>
                <b>Ending</b> - fires when a user tries to close the Exchange.
                Use this for completion checklists or sign-off requirements before
                the Exchange is marked Completed.
            </li>
        </ul>

        <h3>Core concepts</h3>
        <ul>
            <li>
                <b>Workflow definition</b> - a reusable, versioned template that
                describes which steps to run and in what order. Org admins create
                and manage definitions in Settings.
            </li>
            <li>
                <b>Workflow instance</b> - one live execution of a definition
                against a specific Exchange. Created automatically when the matching
                trigger event fires.
            </li>
            <li>
                <b>Step instance</b> - one step within a running instance.
                Tracks assignees, decisions, SLA deadlines, and status.
            </li>
        </ul>

        <h3>Who can manage workflows</h3>
        <p>
            Organization Admins can create, edit, activate, deactivate, publish,
            clone, or delete workflow definitions for their active organization.
            The Organization sub-tab exposes management actions only when the active
            organization grants the required administration capability and has workflow
            automation in its Business subscription.
        </p>
        <p>
            If workflow automation becomes unavailable, existing definitions and history stay
            readable. New definitions, definition changes, and new workflow instances are refused.
            Instances that already started continue through their saved steps, decisions,
            reminders, actions, conditions, and escalations until they finish.
        </p>
        <p>
            App Admins manage platform-scoped workflow definitions and templates.
            Open <b>Platform Administration</b> from the top application bar and
            use <b>Platform Content</b>. The platform editor is isolated from
            organization workflow and directory data.
            The global App Admin role does not grant access to an organization's
            workflows or workflow activity. An App Admin needs a separate
            Organization Admin role in the active organization to manage that
            organization's workflows.
        </p>
        <p>
            The workflow lists use a fixed footer with the visible range on the
            left and page navigation actions on the right.
        </p>

        <h3>Learn more</h3>
        <ul>
            <li>
                <a href="#"
                   data-help-article="trigger-events">
                    <b>Trigger events</b>
                </a>{" "}
                - what events are available and when they fire.
            </li>
            <li>
                <a href="#"
                   data-help-article="building-a-workflow">
                    <b>Building a workflow</b>
                </a>{" "}
                - step-by-step guide to creating your first workflow.
            </li>
            <li>
                <a href="#"
                   data-help-article="step-types">
                    <b>Step types explained</b>
                </a>{" "}
                - Approval, Notification, Condition, and Action steps.
            </li>
            <li>
                <a href="#"
                   data-help-article="platform-templates">
                    <b>Platform templates</b>
                </a>{" "}
                - ready-made workflows you can clone into your personal collection.
            </li>
        </ul>
    </>
);
