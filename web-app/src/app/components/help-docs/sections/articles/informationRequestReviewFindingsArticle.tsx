export const informationRequestReviewFindingsArticle = (
    <>
        <h3 id={"information-request-review-findings-heading"}>Findings</h3>
        <p id={"information-request-review-findings-help"}>
            <b id={"information-request-review-findings-article-b-1"}>Add finding</b> records a reason code, what needs attention, a severity, who can see it, and what the
            respondent may change: the answer, one exact file, an added file, or nothing. A finding belongs to your
            assignment for the item; when you hold several, it names the one you are working in. Findings shown to the
            respondent appear once the review is decided, and never on a withdrawn review; the others stay with
            reviewers.
            A finding is never edited: a later review lists the earlier findings and records a retest of one from the
            review it follows, about the same Requirement, as Resolved or Still unresolved.
        </p>

        <h3 id={"information-request-review-outcomes-heading"}>Outcomes</h3>
        <p id={"information-request-review-outcomes-help"}>
            When the review settles, its most severe item decision sets the outcome: Satisfied, Satisfied with
            exception, Changes requested, or Rejected. Once the reviews accept the whole response, the request
            completes. Accepting a response never changes the Exchange&apos;s fields, and it is not a business
            decision: anything decided because of the response is recorded separately by the requesting side. After the
            request finishes, its reviewers and the people who manage it can still open its reviews and read what was
            decided, with the same item limits and recent verification as before, but nothing in them changes. A
            reviewer keeps this while their access to the request lasts and the Exchange still lets its parties read;
            the people who manage the request keep it after that.
        </p>

        <h3 id={"information-request-review-corrections-heading"}>Corrections</h3>
        <p id={"information-request-review-corrections-help"}>
            Changes requested returns only the items marked Changes required. Those items open for correction, and only
            the files named by a finding, shown by file name and version, can be replaced or withdrawn; every other
            item stays locked. An item the submission held hidden can still be answered once it applies, unless its
            Template takes it only once and an earlier submission held it while shown. The respondent sees <b id={"information-request-review-findings-article-b-2"}>Review
            results</b> in the response workspace, with the findings they may read and the items to correct, and
            submits again. The resubmission is reviewed again, and an unchanged item that was accepted before keeps its
            earlier decision.
        </p>
        <p id={"information-request-review-withdrawal-help"}>
            A submission can be withdrawn only until a reviewer is assigned to its review, and never once it answers a
            correction.
            Request administrators can read their own findings and comments without gaining access to the submitted
            answers.
        </p>

        <h3 id={"information-request-review-appeals-heading"}>Appeals and reconsideration</h3>
        <p id={"information-request-review-appeals-help"}>
            A rejected review, or one whose correction is still open, can be appealed by a submitting respondent
            with <b id={"information-request-review-findings-article-b-3"}>Appeal</b> in Review results. If sending is interrupted, select <b id={"information-request-review-findings-article-b-4"}>Send appeal</b> again: the
            appeal is recorded once. A settled review that can still be reopened, on a request that has not finished,
            can be reconsidered with <b id={"information-request-review-findings-article-b-5"}>Reconsider</b> and a reason by the people who manage the request&apos;s
            reviews. Either starts a new review of the same submission, and the earlier review, its findings, and its
            decisions stay on record. The reason is shown, as <b id={"information-request-review-findings-article-b-6"}>Reason given</b>, to the person who gave it and to
            reviewers who may read every item the new review covers; an appeal&apos;s reason is also shown to
            respondents who may view every item, and a reconsideration&apos;s reason never is. A respondent can
            also <b id={"information-request-review-findings-article-b-7"}>Reply</b> to a finding shown to them and, once the review is decided, <b id={"information-request-review-findings-article-b-8"}>Comment</b> on any item
            they can see.
            Submit any edited correction before reopening its review; this keeps changes within their authorized
            correction scope.
            A refused dialog command keeps its input and shows its message inside the dialog; a stale appeal keeps its
            reason too.
        </p>
    </>
);
