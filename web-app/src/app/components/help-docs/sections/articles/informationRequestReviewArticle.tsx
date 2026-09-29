export const informationRequestReviewArticle = (
    <>
        <p id={"information-request-review-intro"}>
            When a Template routes work to a reviewer, each submission waits for review before the
            request can complete. An item goes to a reviewer when the Template requires its review,
            when it is answered with an exception and the Template requires review on exception, or
            when its files are marked Ready for review or ask for a waiver.
        </p>

        <h3 id={"information-request-review-stages-heading"}>Review stages</h3>
        <p id={"information-request-review-stages-help"}>
            A Template can review in stages, one after another or side by side. Each stage covers
            some of the Template&apos;s sections and decides each item by its rule: the first
            reviewer decides; every reviewer decides and the most severe decision counts; a set
            number of matching decisions settles the item; or every reviewer must agree. When
            reviewers disagree, the Template either applies the most severe decision or waits for
            an override by someone who manages the request&apos;s reviews. A stage can exclude
            anyone who answered, submitted, or confirmed the submission, and anyone who decided an
            earlier stage or the earlier review. A Template that states no stages reviews every
            routed item in one stage decided by the first reviewer.
        </p>

        <h3 id={"information-request-review-queue-heading"}>Your reviews</h3>
        <p id={"information-request-review-queue-help"}>
            When your plan includes Information Requests, select <b>Reviews assigned to you</b> in
            the top menu to see the reviews waiting for you, with their due dates, and select
            <b> Open review</b>. Whatever your own plan, an assigned review also opens from the
            Exchange&apos;s Information Requests tab with <b>Review</b>. The review shows each routed
            item as it was submitted. Items you are not allowed to read show only that they exist.
        </p>

        <h3 id={"information-request-review-assignments-heading"}>Reviewers</h3>
        <p id={"information-request-review-assignments-help"}>
            The Reviewers panel lists who reviews each stage. Someone who manages the request&apos;s
            reviews selects <b>Assign reviewer</b> to give a Reviewer party a stage, or <b>Remove</b> to
            take an assignment back. An assigned reviewer can <b>Recuse</b> with a reason, or
            <b> Delegate</b> the stage to another Reviewer party. Each stage states its rules, such as
            who may not review it and how many reviewers decide each item.
        </p>

        <h3 id={"information-request-review-items-heading"}>Working on an item</h3>
        <p id={"information-request-review-items-help"}>
            Each item is named by its prompt. <b>Preview</b> and <b>Download</b> open the exact file
            version that was submitted. <b>Comment</b> adds to the item&apos;s conversation; a comment
            marked Reviewers only is never shown to the respondent. Where a stage permits it, a manager
            can <b>Override</b> an item&apos;s decision with a reason. The decision history lists every
            decision and correction on the review.
        </p>

        <h3 id={"information-request-review-deciding-heading"}>Deciding</h3>
        <ul id={"information-request-review-deciding-list"}>
            <li>
                For each item choose <b>Satisfied</b>, <b>Satisfied with exception</b>,
                <b> Waived</b>, <b>Changes required</b>, or <b>Rejected</b>, and add a reason.
            </li>
            <li>
                <b>Save worksheet</b> keeps your choices as a draft. Nothing takes effect until you
                select <b>Record decisions</b>, and recorded decisions cannot be changed.
            </li>
            <li>
                Satisfied with exception and Waived need a reason. Changes required and Rejected
                need a finding for the item first. Waived is available only where the Template
                allows the item to be waived.
            </li>
            <li>
                If your worksheet changed after you opened it, for example in another window,
                saving is refused. Reload the review and decide again.
            </li>
        </ul>

        <h3 id={"information-request-review-findings-heading"}>Findings</h3>
        <p id={"information-request-review-findings-help"}>
            <b>Add finding</b> records a reason code, what needs attention, a severity, who can see
            it, and what the respondent may change: the answer, one exact file, an added file, or
            nothing. Findings shown to the respondent appear once the review settles; the others
            stay with reviewers. A finding is never edited: a later review retests it instead.
        </p>

        <h3 id={"information-request-review-outcomes-heading"}>Outcomes</h3>
        <p id={"information-request-review-outcomes-help"}>
            When the review settles, its most severe item decision sets the outcome: Satisfied,
            Satisfied with exception, Changes requested, or Rejected. Once the reviews accept the
            whole response, the request completes. Accepting a response never changes the
            Exchange&apos;s fields, and it is not a business decision: anything decided because of
            the response is recorded separately by the requesting side.
        </p>

        <h3 id={"information-request-review-corrections-heading"}>Corrections</h3>
        <p id={"information-request-review-corrections-help"}>
            Changes requested returns only the items marked Changes required. Those items open for
            correction, and only the files named by a finding can be replaced or withdrawn; every
            other item stays locked. The respondent sees <b>Review results</b> in the response
            workspace, with the findings they may read and the items to correct, and submits again.
            The resubmission is reviewed again, and an unchanged item that was accepted before
            keeps its earlier decision.
        </p>
        <p id={"information-request-review-withdrawal-help"}>
            A submission can be withdrawn only until a reviewer is assigned to its review.
        </p>

        <h3 id={"information-request-review-appeals-heading"}>Appeals and reconsideration</h3>
        <p id={"information-request-review-appeals-help"}>
            A rejected review, or one whose correction is still open, can be appealed by a
            submitting respondent with <b>Appeal</b> in Review results. A settled review can be
            reconsidered with <b>Reconsider</b> and a reason by the people who manage the
            request&apos;s reviews. Either starts a new review of the same submission, and the earlier
            review, its findings, and its decisions stay on record. A respondent can also
            <b> Reply</b> to a finding shown to them.
        </p>
    </>
);
