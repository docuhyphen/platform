
export const workflowApplicabilityArticle = (
    <>
        <p id={"workflow-applicability-intro-paragraph"}>
            Applicability lets an organization workflow run only when the Exchange's
            business field values match one or more conditions. Without conditions,
            the workflow runs for every matching trigger event.
        </p>

        <h3 id={"workflow-applicability-where-to-find-it-heading"}>Where to find it</h3>
        <p id={"workflow-applicability-where-to-find-it-paragraph"}>
            Open the Workflow Designer in Settings for an organization workflow.
            Open the <b id={"workflow-applicability-article-b-1"}>Applicability</b> card on the Workflow Designer's Form pane.
            It is available only for organization-scoped workflows, because
            conditions reference an organization Exchange schema. Use the fixed
            arrow button to return to the three main workflow cards.
        </p>

        <h3 id={"workflow-applicability-how-conditions-work-heading"}>How conditions work</h3>
        <ul id={"workflow-applicability-how-conditions-work-list"}>
            <li id={"workflow-applicability-article-li-1"}>
                Pick an organization <b id={"workflow-applicability-article-b-2"}>schema</b> (target Exchange), then add a
                condition for one of its fields.
            </li>
            <li id={"workflow-applicability-article-li-2"}>
                Each condition has a <b id={"workflow-applicability-article-b-3"}>field</b>, an <b id={"workflow-applicability-article-b-4"}>operator</b> (the operators
                offered depend on the field type), and, for most operators, a{" "}
                <b id={"workflow-applicability-article-b-5"}>value</b>. The <code id={"workflow-applicability-article-code-1"}>is empty</code> and <code id={"workflow-applicability-article-code-2"}>is not empty</code>{" "}
                operators take no value.
            </li>
            <li id={"workflow-applicability-article-li-3"}>
                A condition on a Date and time field compares moments, not wall-clock
                readings. The moment you pick is recorded with your browser&apos;s time zone
                offset, so it matches a stored answer that names the same moment even if it
                was entered in a different zone.
            </li>
            <li id={"workflow-applicability-article-li-4"}>
                All conditions must match (AND). If any condition fails, the workflow
                is skipped for that Exchange.
            </li>
        </ul>

        <p id={"workflow-applicability-how-conditions-work-paragraph"}>
            Exchange Field conditions apply only to Exchange triggers. For a workflow that starts
            from an Information Request trigger, the designer shows no Exchange Field conditions.
            When that trigger names a Submission Package, the card instead offers requirement
            conditions on the answers frozen in the package, as described in the request trigger
            events article. Without them, a request-trigger workflow runs for every event of its
            trigger.
        </p>

        <h3 id={"workflow-applicability-when-a-workflow-is-heading"}>When a workflow is skipped</h3>
        <p id={"workflow-applicability-when-a-workflow-is-paragraph"}>
            The gate never fails open. A condition that cannot be judged counts as a
            failed condition, and one failed condition skips the workflow. That covers a
            subject that is not an Exchange, an Exchange with no assigned schema, a
            condition whose field or comparison value cannot be read, and an operator the
            field type does not offer.
        </p>
        <p id={"workflow-applicability-when-a-workflow-is-paragraph-2"}>
            An unanswered field is judged rather than skipped over. It fails every
            operator that compares a value, so a condition such as <code id={"workflow-applicability-article-code-3"}>equals</code>{" "}
            needs the field answered before the workflow fires, which is why the Business
            Fields step of Exchange creation runs first. It matches <code id={"workflow-applicability-article-code-4"}>is empty</code>{" "}
            and fails <code id={"workflow-applicability-article-code-5"}>is not empty</code>, so a condition written with{" "}
            <code id={"workflow-applicability-article-code-6"}>is empty</code> deliberately starts the workflow for the Exchange that
            left the field blank. A selection whose stored options are gone counts as
            unanswered in exactly the same way.
        </p>

        <h3 id={"workflow-applicability-example-heading"}>Example</h3>
        <p id={"workflow-applicability-example-paragraph"}>
            On a Client Onboarding schema, add the condition{" "}
            <code id={"workflow-applicability-article-code-7"}>Category equals Onboarding</code>. The workflow then starts only for
            Exchanges whose Category field is set to Onboarding.
        </p>
    </>
);
