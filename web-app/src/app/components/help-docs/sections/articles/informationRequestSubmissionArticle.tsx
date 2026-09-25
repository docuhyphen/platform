export const informationRequestSubmissionArticle = (
    <>
        <p id={"information-request-submission-intro"}>
            When the requested information is ready, a respondent reviews it and submits it.
            A submission is a permanent record of exactly what was provided: the answers, the
            exact file versions, the supporting links, and the confirmations given.
        </p>

        <h3 id={"information-request-submission-review-heading"}>Review and submit</h3>
        <ul id={"information-request-submission-review-list"}>
            <li>
                <b>Review and submit</b> in the response workspace lists what still blocks the
                submission, such as an unanswered item, a file that does not meet the request, or a
                missing confirmation. Items handled by other parties are counted but not named.
            </li>
            <li>
                <b>Submit</b> is available once everything is complete. If anything changes after
                you reviewed it, the submission is refused and you are asked to review it again.
            </li>
            <li>
                Submitting twice by accident records one submission, not two.
            </li>
        </ul>

        <h3 id={"information-request-submission-confirmations-heading"}>Confirmations</h3>
        <p id={"information-request-submission-confirmations-help"}>
            A confirmation asks one or more parties to confirm the response. The Template decides
            which roles must confirm, whether they confirm in order, how many confirmations are
            needed, how the party must have proved who they are, and for how long a confirmation
            stays valid. Select <b>Confirm</b>, or <b>Refuse to confirm</b> and give a reason. A
            refusal blocks the submission until that party confirms instead.
        </p>
        <p id={"information-request-submission-confirmations-changes-help"}>
            A confirmation applies to the exact content it was given for. Changing an answer or a
            file afterwards means the parties confirm again. A signature reference, where the
            Template accepts one, is recorded as a reference to a signature made elsewhere; it is
            not an electronic signature made on this platform.
        </p>

        <h3 id={"information-request-submission-parts-heading"}>Submitting in parts</h3>
        <p id={"information-request-submission-parts-help"}>
            A Template either submits the whole request at once or in parts. Choose the part in
            <b> Part to submit</b>. A Template can require parts to be submitted in order. Once a
            part is submitted, its answers, files, repeated items, and confirmations cannot change,
            while the other parts stay open. To change a submitted part, select <b>Withdraw</b> on
            its submission; the part opens again and a later submission follows the withdrawn one.
        </p>
        <p id={"information-request-submission-complete-help"}>
            A request whose Template needs no reviewer is complete as soon as its last part is
            submitted. Only such Templates can be issued at the moment; a Template that routes
            work to a reviewer is refused at issuance until review is available.
        </p>

        <h3 id={"information-request-submission-amendments-heading"}>Amendments</h3>
        <p id={"information-request-submission-amendments-help"}>
            The requesting party can amend an issued request to a later published Version of its
            Template. <b>What changed in this request</b> lists each change: newly requested, no
            longer requested, reworded, or changed. A reworded item keeps your answer. A changed
            item keeps it too, but asks you to confirm it again: save the answer, even unchanged,
            before you submit. An amendment cannot change what a submission already holds, and a
            change that needs a different schema is made with a new request instead.
        </p>
        <p id={"information-request-submission-notices-help"}>
            Each party is owed a notice of the amendment. The notice is shown as <b>Notice
            pending</b>: it is recorded, but it is not sent from this page.
        </p>

        <h3 id={"information-request-submission-follow-ups-heading"}>Follow-up requests</h3>
        <p id={"information-request-submission-follow-ups-help"}>
            After a submission, a signed-in requesting party can select <b>Request more
            information</b>. This creates a new draft request on the same Exchange for the same
            parties. The earlier request and its submission stay exactly as they were. A follow-up
            can also replace an open request, which then shows as superseded, or come from a
            recurring schedule or an expiry refresh set up for the request.
        </p>
        <p id={"information-request-submission-carry-forward-help"}>
            In a follow-up, <b>From your previous submission</b> shows earlier answers for
            reference. Nothing is copied for you: save an answer to use it again. Files and
            confirmations are always provided again.
        </p>
    </>
);
