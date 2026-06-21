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
                recipient acceptance is required (the org-level "require recipient acceptance" setting is on). The Exchange stays in Draft until
                the workflow completes.
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
            Only Organization Admins can create, edit, activate, deactivate, publish,
            clone, or delete workflow definitions. The Workflows tab in Settings is only
            visible to users with the admin role.
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
                - APPROVAL, NOTIFICATION, CONDITION, and ACTION steps.
            </li>
            <li>
                <a href="#"
                   data-help-article="platform-templates">
                    <b>Platform templates</b>
                </a>{" "}
                - ready-made workflows you can clone into your organization.
            </li>
        </ul>
    </>
);
