import React from "react";

export const communicationsInWorkflowsArticle = (
    <>
        <p id={"communications-in-workflows-intro-paragraph"}>
            A Notification step in a workflow can use a communication to deliver a
            fully customized subject and body instead of the default system message.
            The workflow engine resolves the linked communication's variable tokens
            against the triggering Exchange at send time and delivers the result
            by email and in-app notification to every assignee on the step.
        </p>

        <h3 id={"communications-in-workflows-linking-a-communication-to-heading"}>Linking a communication to a
            Notification step</h3>
        <ol id={"communications-in-workflows-linking-a-communication-to-list"}>
            <li>
                Open the workflow in the <b>Workflow Designer</b> (Settings {">"} Workflows {">"} Edit).
            </li>
            <li>
                Find and click the Notification step card (or add one by clicking{" "}
                <b>Add Step</b>, click its card, and select <b>Notification</b>).
            </li>
            <li>
                Click <b>Select communication...</b>. The Communication Picker dialog
                opens.
            </li>
            <li>
                Browse <b>My Communications</b>, <b>Organization</b>, or{" "}
                <b>Platform</b> tabs and search by name or subject.
            </li>
            <li>
                Click a communication to select it (highlighted in blue), then click{" "}
                <b>Select Communication</b>.
            </li>
            <li>
                The step now shows the communication's name. Add assignees as usual.
            </li>
            <li>Click <b>Save workflow</b>.</li>
        </ol>

        <h3 id={"communications-in-workflows-clearing-a-linked-communication-heading"}>Clearing a linked
            communication</h3>
        <p id={"communications-in-workflows-clearing-a-linked-communication-paragraph"}>
            Click <b>Clear</b> next to the selected communication name. The step
            reverts to the default system message.
        </p>

        <h3 id={"communications-in-workflows-fallback-behaviour-heading"}>Fallback behaviour</h3>
        <p id={"communications-in-workflows-fallback-behaviour-paragraph"}>
            The engine falls back to the generic default message when any of the
            following is true:
        </p>
        <ul id={"communications-in-workflows-fallback-behaviour-list"}>
            <li>No communication is linked to the step.</li>
            <li>The linked communication has been deleted.</li>
            <li>The linked communication is inactive.</li>
            <li>Token resolution fails (for example, a required variable is missing).</li>
        </ul>
        <p id={"communications-in-workflows-fallback-behaviour-paragraph-2"}>
            Fallback does not fail the workflow; the step completes and the workflow
            advances normally.
        </p>

        <h3 id={"communications-in-workflows-token-resolution-in-notification-heading"}>Token resolution in Notification
            steps</h3>
        <p id={"communications-in-workflows-token-resolution-in-notification-paragraph"}>
            The engine resolves tokens using the workflow instance's subject data
            (Exchange fields, initiator, organization) merged with any custom
            variables. The initiator's user and organization context are used as the
            resolution base. Overrides passed from the trigger event's subject data
            take precedence over defaults.
        </p>

        <h3 id={"communications-in-workflows-which-communications-are-selectable-heading"}>Which communications are
            selectable?</h3>
        <p id={"communications-in-workflows-which-communications-are-selectable-paragraph"}>
            The Communication Picker shows only active communications. The scopes
            available depend on your workflow automation access and role:
        </p>
        <ul id={"communications-in-workflows-which-communications-are-selectable-list"}>
            <li><b>Workflow automation users</b> - their own reusable communications.</li>
            <li><b>Organization workflow users</b> - published Org communications from their org.</li>
            <li><b>Workflow automation users</b> - Platform communications.</li>
        </ul>
        <p id={"communications-in-workflows-which-communications-are-selectable-paragraph-2"}>
            Draft org communications are not shown in the picker until they are
            published by an Organization Admin.
        </p>

        <h3 id={"communications-in-workflows-learn-more-heading"}>Learn more</h3>
        <ul id={"communications-in-workflows-learn-more-list"}>
            <li>
                <a id={"communications-in-workflows-learn-more-link"}
                   href="#"
                   data-help-article="communications-overview">
                    <b>Communications overview</b>
                </a>{" "}
                - scopes, tokens, draft/publish lifecycle.
            </li>
            <li>
                <a id={"communications-in-workflows-learn-more-link-2"}
                   href="#"
                   data-help-article="step-types">
                    <b>Step types explained</b>
                </a>{" "}
                - full reference for Notification and other step types.
            </li>
        </ul>
    </>
);
