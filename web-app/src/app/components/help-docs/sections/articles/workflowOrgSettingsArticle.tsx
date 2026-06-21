import React from "react";

export const workflowOrgSettingsArticle = (
    <>
        <p>
            Organization Admins can configure how workflows interact with the
            Exchange lifecycle through organization-level settings.
        </p>

        <h3>Require recipient acceptance</h3>
        <p>
            The <b>Require recipient acceptance</b> toggle controls whether every
            Exchange created in your organization must wait for at least one
            recipient to accept before it transitions to Active.
        </p>
        <ul>
            <li>
                <b>On (default)</b> - newly created Exchanges stay in Draft until
                a recipient accepts. If a workflow definition for the{" "}
                <code>exchange.acceptance_pending</code> trigger is active, that
                workflow gates the acceptance. If no matching definition is active,
                the platform still requires at least one manual recipient acceptance
                before the Exchange becomes Active.
            </li>
            <li>
                <b>Off</b> - newly created Exchanges advance to Active immediately
                on creation. The <code>exchange.activated</code> event fires and
                the Exchange bypasses the acceptance stage. Use this for internal
                or automated Exchanges where recipient sign-off is not needed.
            </li>
        </ul>
        <p>
            Changing this setting affects all new Exchanges created after the
            change. Exchanges that are already in Draft or Active are not affected.
        </p>

        <h3>Where to find this setting</h3>
        <ol>
            <li>Open Settings.</li>
            <li>Go to the <b>Organization</b> tab.</li>
            <li>
                Find the <b>Require recipient acceptance</b> toggle in the
                Exchange settings section.
            </li>
            <li>Toggle it on or off and save.</li>
        </ol>

        <h3>How this interacts with workflows</h3>
        <p>
            The setting and the workflow engine work together:
        </p>
        <ul>
            <li>
                When the setting is <b>on</b> and a matching{" "}
                <code>exchange.acceptance_pending</code> workflow is active, the
                recipient's "Accept" action is routed through the workflow engine.
                The workflow step records the decision and, once the quorum is met,
                advances the Exchange to Active.
            </li>
            <li>
                When the setting is <b>on</b> but no matching workflow is active,
                the platform still guards the transition. A direct recipient
                acceptance still moves the Exchange to Active, just without a
                workflow gate.
            </li>
            <li>
                When the setting is <b>off</b>, the{" "}
                <code>exchange.acceptance_pending</code> trigger never fires and
                acceptance workflow definitions for that trigger will not run.
            </li>
        </ul>

        <h3>No-account (OTP) recipients</h3>
        <p>
            Recipients who do not have a DocuHyphen account and accept via a
            one-time password link are also routed through the workflow engine when
            the setting is on. After OTP verification, their acceptance is recorded
            as a workflow decision, ensuring consistent audit records regardless of
            recipient account type.
        </p>

        <h3>Related articles</h3>
        <ul>
            <li>
                <a href="#"
                   data-help-article="trigger-events">
                    <b>Trigger events</b>
                </a>{" "}
                - how exchange.acceptance_pending works.
            </li>
            <li>
                <a href="#"
                   data-help-article="workflow-overview">
                    <b>Workflow overview</b>
                </a>{" "}
                - the full Exchange lifecycle with workflows.
            </li>
        </ul>
    </>
);
