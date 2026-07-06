import React from "react";

export const workflowApplicabilityArticle = (
    <>
        <p>
            Applicability lets an organization workflow run only when the Exchange's
            business field values match one or more conditions. Without conditions,
            the workflow runs for every matching trigger event.
        </p>

        <h3>Where to find it</h3>
        <p>
            Open the Workflow Designer in Settings for an organization workflow.
            Open the <b>Applicability</b> card on the Workflow Designer's Form pane.
            It is available only for organization-scoped workflows, because
            conditions reference an organization Exchange schema. Use the fixed
            arrow button to return to the three main workflow cards.
        </p>

        <h3>How conditions work</h3>
        <ul>
            <li>
                Pick an organization <b>schema</b> (target Exchange), then add a
                condition for one of its fields.
            </li>
            <li>
                Each condition has a <b>field</b>, an <b>operator</b> (the operators
                offered depend on the field type), and, for most operators, a{" "}
                <b>value</b>. The <code>is empty</code> and <code>is not empty</code>{" "}
                operators take no value.
            </li>
            <li>
                All conditions must match (AND). If any condition fails, the workflow
                is skipped for that Exchange.
            </li>
        </ul>

        <h3>When a workflow is skipped</h3>
        <p>
            The gate never fails open. A workflow with conditions is skipped when the
            subject is not an Exchange, when the Exchange has no assigned schema, when
            a referenced field has no value, or when a selected option is no longer
            stored. This means values must be entered before the workflow fires - the
            Business Fields step of Exchange creation runs first.
        </p>

        <h3>Example</h3>
        <p>
            On a Client Onboarding schema, add the condition{" "}
            <code>Category equals Onboarding</code>. The workflow then starts only for
            Exchanges whose Category field is set to Onboarding.
        </p>
    </>
);
