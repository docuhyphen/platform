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
                A condition on a Date and time field compares moments, not wall-clock
                readings. The moment you pick is recorded with your browser&apos;s time zone
                offset, so it matches a stored answer that names the same moment even if it
                was entered in a different zone.
            </li>
            <li>
                All conditions must match (AND). If any condition fails, the workflow
                is skipped for that Exchange.
            </li>
        </ul>

        <h3>When a workflow is skipped</h3>
        <p>
            The gate never fails open. A condition that cannot be judged counts as a
            failed condition, and one failed condition skips the workflow. That covers a
            subject that is not an Exchange, an Exchange with no assigned schema, a
            condition whose field or comparison value cannot be read, and an operator the
            field type does not offer.
        </p>
        <p>
            An unanswered field is judged rather than skipped over. It fails every
            operator that compares a value, so a condition such as <code>equals</code>
            needs the field answered before the workflow fires, which is why the Business
            Fields step of Exchange creation runs first. It matches <code>is empty</code>
            and fails <code>is not empty</code>, so a condition written with{" "}
            <code>is empty</code> deliberately starts the workflow for the Exchange that
            left the field blank. A selection whose stored options are gone counts as
            unanswered in exactly the same way.
        </p>

        <h3>Example</h3>
        <p>
            On a Client Onboarding schema, add the condition{" "}
            <code>Category equals Onboarding</code>. The workflow then starts only for
            Exchanges whose Category field is set to Onboarding.
        </p>
    </>
);
