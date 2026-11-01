export const requestTriggerEventsArticle = (
    <>
        <p id={"request-trigger-events-intro"}>
            A workflow can start from an Information Request instead of an Exchange.
            Choose one of these triggers when you create the workflow. Each run reads the request
            and, where the trigger names one, the exact Submission Package that caused it.
        </p>

        <h3 id={"request-trigger-events-list-heading"}>Triggers</h3>
        <ul id={"request-trigger-events-list"}>
            <li id={"request-trigger-events-article-li-1"}><code id={"request-trigger-events-article-code-1"}>information_request.request.issue</code>: the request is issued to its parties.</li>
            <li id={"request-trigger-events-article-li-2"}><code id={"request-trigger-events-article-code-2"}>information_request.request.view</code>: a responding party opens the issued request for the first time.</li>
            <li id={"request-trigger-events-article-li-3"}>
                <code id={"request-trigger-events-article-code-3"}>information_request.request.start</code>: a responding party first saves an
                answer, file, confirmation, or submission.
            </li>
            <li id={"request-trigger-events-article-li-4"}><code id={"request-trigger-events-article-code-4"}>information_request.request.submit</code>: a Submission Package is submitted.</li>
            <li id={"request-trigger-events-article-li-5"}>
                <code id={"request-trigger-events-article-code-5"}>information_request.request.correction</code>: a review returns items of a
                Submission Package for correction.
            </li>
            <li id={"request-trigger-events-article-li-6"}><code id={"request-trigger-events-article-code-6"}>information_request.request.close</code>: the request is satisfied and closed.</li>
            <li id={"request-trigger-events-article-li-7"}><code id={"request-trigger-events-article-code-7"}>information_request.request.expire</code>: the request expires.</li>
            <li id={"request-trigger-events-article-li-8"}><code id={"request-trigger-events-article-code-8"}>information_request.request.cancel</code>: the request is cancelled.</li>
            <li id={"request-trigger-events-article-li-9"}><code id={"request-trigger-events-article-code-9"}>information_request.request.supersede</code>: another request supersedes it.</li>
            <li id={"request-trigger-events-article-li-10"}><code id={"request-trigger-events-article-code-10"}>information_request.request.overdue</code>: a request clock passes its due time.</li>
        </ul>

        <h3 id={"request-trigger-events-owner-heading"}>Whose workflows run</h3>
        <p id={"request-trigger-events-owner-help"}>
            An organization&apos;s request starts the organization&apos;s active workflows for the trigger, and a
            personally owned request starts its owner&apos;s own active workflows from My Workflows. When the
            owner has none for the trigger, the platform&apos;s active workflows run. If a workflow cannot be started,
            for example because a
            first step names an assignee that no longer exists, none of it starts and the request&apos;s
            operations record the automation as skipped. A workflow still running on a request is cancelled when the
            request is
            cancelled, superseded or expires, or its Exchange is rejected, rescinded or deleted.
        </p>

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
            not offer them for a request trigger. An organization workflow whose trigger names a
            Submission Package can instead be limited by requirement conditions on the submitted answers.
            Open <b id={"request-trigger-events-article-b-1"}>Applicability</b>, choose an organization <b id={"request-trigger-events-article-b-2"}>Template</b>, and pick one of the typed
            answers of its latest published Version under <b id={"request-trigger-events-article-b-3"}>Add condition</b>. Each condition has an
            operator and, for most operators, a value. An answer inside a repeated group also names its
            occurrence, such as <b id={"request-trigger-events-article-b-4"}>group[0]</b> for the first one. The workflow then starts only when every
            condition matches the answers frozen in that Submission Package. Without conditions, and for
            every other request trigger, the workflow runs for every event of its trigger. Reopening the workflow
            selects
            the Template its conditions belong to, and a Template list or answers that cannot be read are stated with
            <b id={"request-trigger-events-article-b-5"}> Try again</b>.
        </p>
    </>
);
