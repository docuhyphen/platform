export const informationRequestConfirmationsArticle = (
    <>
        <h3 id={"information-request-submission-confirmations-heading"}>Confirmations</h3>
        <p id={"information-request-submission-confirmations-help"}>
            A confirmation asks one or more parties to confirm the response. The Template decides which roles must
            confirm, whether
            they confirm in order, how many confirmations are needed, how the party must have proved who they are, and
            for how long
            a confirmation stays valid. The card states its policy and prior confirmations, and waits for unsaved work.
            Select <b id={"information-request-confirmations-article-b-1"}>Confirm</b>, or <b id={"information-request-confirmations-article-b-2"}>Refuse to confirm</b> and give a reason. A refusal does not lapse:
            it blocks the submission until that party confirms instead. When roles confirm in
            order, <b id={"information-request-confirmations-article-b-3"}>Confirm</b> waits until every
            earlier role has confirmed and the card names the roles it is waiting for; you can still refuse. If you take
            part in the
            request in more than one way, for example in two roles or through a group, choose the one you confirm for
            under <b id={"information-request-confirmations-article-b-4"}>Confirm as</b>.
        </p>
        <p id={"information-request-submission-confirmations-changes-help"}>
            A confirmation applies to the exact content it was given for. Changing an answer or a file afterwards means
            the parties
            confirm again. A signature reference, where the Template accepts one, is recorded as a reference to a
            signature made
            elsewhere; it is not an electronic signature made on this platform. A reference is at most 512 characters.
            A contact-only session cannot give a confirmation that requires stronger authentication; sign in as an
            eligible party.
            Where the Template permits an exception on a confirmation, select <b id={"information-request-confirmations-article-b-5"}>Record an exception</b>, choose it and
            give a reason: the confirmation then counts as answered by that exception, the card states it, and <b id={"information-request-confirmations-article-b-6"}>Change
            exception</b> replaces it. A partial answer never replaces a confirmation.
        </p>
        <h3 id={"information-request-submission-parts-heading"}>Submitting in parts</h3>
        <p id={"information-request-submission-parts-help"}>
            A Template submits the whole request at once or in parts; choose the part in <b id={"information-request-confirmations-article-b-7"}>Part to submit</b>, and a
            Template can
            require parts in order. The part you choose stays chosen while your answers save, and <b id={"information-request-confirmations-article-b-8"}>Submit</b> waits
            until that
            part has loaded. Once a part is submitted, its answers, files, repeated items, and confirmations cannot
            change: each of
            its answers is shown read-only with the reason, and a change you had not saved for it is dropped while your
            other changes
            save. The other parts stay open. To change one, select <b id={"information-request-confirmations-article-b-9"}>Withdraw</b> on its submission before a reviewer
            is assigned to
            it; the part opens again, except answers its Template takes only once, and a later submission follows the
            withdrawn one.
            A submission that answers a correction cannot be withdrawn, and one a later submission replaced
            shows <b id={"information-request-confirmations-article-b-10"}>Replaced</b>;
            <b id={"information-request-confirmations-article-b-11"}>Withdraw</b> appears only while a submission can still be withdrawn. When parts are submitted in order,
            withdraw any
            later submitted part first. Confirm withdrawal in its dialog, optionally with a short lowercase reason code.
            A submitting party may withdraw another party&apos;s submission for recovery, and so may someone who manages
            the request&apos;s parties, under <b id={"information-request-confirmations-article-b-12"}>Submissions</b> on the request&apos;s page while the request is still open
            to changes.
        </p>
        <p id={"information-request-submission-complete-help"}>
            A request whose Template needs no reviewer is complete as soon as its last part is submitted. When the
            Template routes
            work to a reviewer, each submission waits for review and the request completes once its reviews accept the
            whole
            response; see Reviewing Information Request submissions.
        </p>
        <h3 id={"information-request-submission-amendments-heading"}>Amendments</h3>
        <p id={"information-request-submission-amendments-help"}>
            The requesting party can amend an issued request through the API, moving it to a later published Version of
            its
            Template or, for a one-off request, to a revised configuration; no screen offers this today. You see the
            result under{" "}
            <b id={"information-request-confirmations-article-b-13"}>What changed in this request</b>, which lists each change: newly requested, no longer requested,
            reworded, or changed.
            A reworded item keeps your answer. A changed item keeps it too, but asks you to confirm it again: change it,
            or select
            <b id={"information-request-confirmations-article-b-14"}> Keep this answer</b> to keep it as it is, before you submit. Changing how many entries a repeated
            section takes
            changes every item in it and in the sections nested in it, and a higher minimum adds the missing entries for
            you. An
            amendment cannot change what a submission already holds, including how it is reviewed. A change that needs a
            different
            schema, or that first asks for Field answers in a request that asked for none, is made with a new request
            instead.
        </p>
        <p id={"information-request-submission-notices-help"}>
            Each party is owed an amendment notice, sent by email in the background. Its badge shows the delivery state;
            group members and a participant&apos;s linked account see the notices owed to their party.
        </p>
        <h3 id={"information-request-submission-follow-ups-heading"}>Follow-up requests</h3>
        <p id={"information-request-submission-follow-ups-help"}>
            After a submission, a signed-in requesting party, or a reviewer on the review page, can select <b id={"information-request-confirmations-article-b-15"}>Request
            more information</b> and choose a reason. This creates a
            new draft request on the same Exchange for the same parties, except a party the sharing policy no longer
            permits, which is left out and recorded in the audit history. It uses the earlier request&apos;s Template
            Version; moving a supplement to a later published Version of the same Template is possible only through the
            API. The earlier request and its submission stay exactly as they were. If <b id={"information-request-confirmations-article-b-16"}>Create supplement</b> is interrupted, select it again: it is sent as the same
            request, so one supplement is created. The result links to the new draft, or tells a reviewer that whoever
            manages the request issues it. A stale reply keeps your reason
            and refreshes the request. A follow-up can also replace an open request, which then shows as
            superseded, or come from a recurring schedule or an expiry refresh the requesting party set up
            under <b id={"information-request-confirmations-article-b-17"}>Expiry refreshes</b> on the request&apos;s page. An expiry refresh is created automatically once a
            submitted document&apos;s stated expiry date, less the refresh&apos;s lead time, arrives, and only once for
            each submission. If it cannot be created at that point, it is tried again later.
        </p>
        <p id={"information-request-submission-carry-forward-help"}>
            In a follow-up, <b id={"information-request-confirmations-article-b-18"}>From your previous submission</b> shows earlier answers for reference. Nothing
            is copied for you: save an answer to use it again. Earlier answers are shown as they were entered: a
            choice by its label, a flag as Yes or No, and an answer other than a value with its kind and reason.
            Files and confirmations are always provided again. An earlier answer is listed only while you can still
            see its question on the earlier request, so a protected question&apos;s answer appears only shortly
            after you verify.
        </p>
    </>
);
