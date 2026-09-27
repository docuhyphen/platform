export const requestTriggerEventsArticle = (
    <>
        <p id={"request-trigger-events-intro"}>
            A workflow can start from an Information Request instead of an Exchange.
            Choose one of these triggers when you create the workflow. Each run reads the request
            and, where the trigger names one, the exact Submission Package that caused it.
        </p>

        <h3 id={"request-trigger-events-list-heading"}>Triggers</h3>
        <ul id={"request-trigger-events-list"}>
            <li><code>information_request.request.issue</code>: the request is issued to its parties.</li>
            <li><code>information_request.request.view</code>: a responding party opens the issued request for the first time.</li>
            <li>
                <code>information_request.request.start</code>: a responding party first saves an
                answer, file, confirmation, or submission.
            </li>
            <li><code>information_request.request.submit</code>: a Submission Package is submitted.</li>
            <li>
                <code>information_request.request.correction</code>: a review returns items of a
                Submission Package for correction.
            </li>
            <li><code>information_request.request.close</code>: the request is satisfied and closed.</li>
            <li><code>information_request.request.expire</code>: the request expires.</li>
            <li><code>information_request.request.cancel</code>: the request is cancelled.</li>
            <li><code>information_request.request.supersede</code>: another request supersedes it.</li>
            <li><code>information_request.request.overdue</code>: a request clock passes its due time.</li>
        </ul>

        <h3 id={"request-trigger-events-fields-heading"}>Subject fields</h3>
        <p id={"request-trigger-events-fields-help"}>
            Every request trigger offers the request, its Exchange, its Template Version, the owning
            organization, the request state, and the sequence number of the recorded change.
            Submitted, correction, and close triggers add the Submission Package. The submitted
            trigger also names the package number and stage, the correction trigger names the
            review, the correction, and how many items came back, and the overdue trigger names the
            clock and its due time.
        </p>

        <h3 id={"request-trigger-events-applicability-heading"}>Applicability</h3>
        <p id={"request-trigger-events-applicability-help"}>
            Exchange Field conditions apply only to Exchange triggers, so the workflow designer does
            not offer them for a request trigger and runs the workflow for every event of that
            trigger. A workflow whose trigger names a Submission Package can instead be limited by
            requirement conditions on the submitted answers. Those conditions are part of the
            workflow definition sent through the API, and the designer keeps them when you save.
        </p>
    </>
);
