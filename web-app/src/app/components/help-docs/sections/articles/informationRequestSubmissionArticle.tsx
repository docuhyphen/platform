export const informationRequestSubmissionArticle = (
    <>
        <p id={"information-request-submission-intro"}>
            When the requested information is ready, a respondent reviews it and submits it. A submission is a permanent
            record of
            exactly what was provided: the answers, the exact file versions, the supporting links, and the confirmations
            given.
        </p>
        <h3 id={"information-request-submission-answering-heading"}>Answering</h3>
        <ul id={"information-request-submission-answering-list"}>
            <li id={"information-request-submission-article-li-1"}>
                The workspace lists its sections with how many required answers each still needs; selecting a section
                opens its first
                item. A document counts once a file is attached to it or another answer, such as not applicable, is
                chosen. Confirmations are not counted
                there: a section holding only a confirmation says to confirm under <b id={"information-request-submission-article-b-1"}>Review and submit</b>, and
                selecting it opens that confirmation. When nothing in the request is yours to answer, the workspace says
                so.
            </li>
            <li id={"information-request-submission-article-li-2"}>
                Each item takes a value, or another answer the Template allows, such as partly provided, not applicable,
                unavailable, or an exception request, with the reason it asks for; a partly provided answer keeps its
                value
                too, while any other of these answers clears a value typed earlier. Changing a value or a reason later
                keeps the answer you chose. The reason is required:
                such an answer is saved once its reason is typed, while your other answers keep saving. An item can also
                take a note: a provided answer has an optional <b id={"information-request-submission-article-b-2"}>Note</b>, which keeps any reason you gave for an
                earlier answer until you change it.
            </li>
            <li id={"information-request-submission-entries-add-help"}>
                A section that repeats holds one entry for each item, and the Template&apos;s minimum
                number of entries is created for you. <b id={"information-request-submission-article-b-3"}>Add</b> with the section&apos;s name adds an
                entry, and a repeated part inside an entry has its own <b id={"information-request-submission-article-b-4"}>Add</b> button there, each up
                to the Template&apos;s maximum, after which it is unavailable. The up and down arrows move an entry one
                place within its
                list. Entries are numbered by their place in their list, and an entry of a part repeated inside another
                entry is shown in that entry.
            </li>
            <li id={"information-request-submission-entries-remove-help"}>
                The delete button on an entry asks you to confirm, then removes the entry with the entries nested in it.
                It is
                unavailable while only the Template&apos;s minimum remain and refused once the entry holds an answer
                given once that was
                submitted, and a removed entry stays recorded in the request history. When an earlier answer means an
                item
                is not asked in an entry, that entry says so; while the answers an item depends on are not given yet, it
                says the item is asked once they are. Adding an entry needs permission to answer its items,
                and <b id={"information-request-submission-article-b-5"}>Add</b> is shown only where you have it; removing
                or moving entries needs it for every item they and their nested entries hold. If you answer only a part
                inside someone else&apos;s entries, those entries are shown without their answers for you to add yours.
            </li>
            <li id={"information-request-submission-article-li-3"}>
                Answers save two seconds after you stop typing, and <b id={"information-request-submission-article-b-6"}>Save responses</b> saves at once. The status
                beside
                it says whether changes are waiting or saved. When a save is refused, the message names each affected
                answer,
                with a link to it, and how many others it cannot show you; after <b id={"information-request-submission-article-b-7"}>Save responses</b>, focus moves to
                the first
                one, or to the clears to confirm. If adding, removing, or moving an entry, or <b id={"information-request-submission-article-b-8"}>Use this answer</b>,
                finds that the answers changed
                elsewhere, that change is not made: the latest answers are shown, and your answers keep saving. If
                saving, or adding, removing, or moving an entry, is interrupted,
                select it again: it is sent as the same change and recorded once, also after you verify again. While the
                request is closed, cancelled, replaced, or expired, or its changes are paused or stopped, answers and
                entries cannot change.
            </li>
            <li id={"information-request-submission-article-li-4"}>
                If an answer changed somewhere else after you began changing it, for example on another device or before
                you
                reopened the tab, saving stops: <b id={"information-request-submission-article-b-9"}>Answers saved elsewhere</b> shows its latest saved answer. Select <b id={"information-request-submission-article-b-10"}>Keep
                saved answer</b>, or <b id={"information-request-submission-article-b-11"}>Save responses</b> to replace it. Unsaved changes stay in the tab until saved.
            </li>
            <li id={"information-request-submission-article-li-5"}>
                When the requesting side has kept an earlier accepted answer about the same subject, and the item takes
                a
                full answer, it shows <b id={"information-request-submission-article-b-12"}>An earlier accepted answer is available</b> with how it was accepted and how
                long it
                is valid; a choice is shown by its labels and a time in your time zone. A note you typed for the item
                stays when the answer is used. Nothing is used until you tick <b id={"information-request-submission-article-b-13"}>I confirm this answer is still
                accurate</b> and select <b id={"information-request-submission-article-b-14"}>Use this
                answer</b>, which saves it as your answer. If saving is interrupted, select it again: the answer is
                saved
                once. If the answer is withdrawn or lapses first, it is refused and your answers stay as they were.
            </li>
        </ul>
        <h3 id={"information-request-submission-review-heading"}>Review and submit</h3>
        <ul id={"information-request-submission-review-list"}>
            <li id={"information-request-submission-summary-help"}>
                <b id={"information-request-submission-article-b-15"}>Review and submit</b> in the response workspace lists what still blocks the submission, such as an
                unanswered
                item, a file that does not meet the request, or a missing confirmation, each linked to its item, or a
                repeated
                section with fewer entries than its minimum, linked to its Add control. Undisclosed items and submission
                rules are counted. Before you
                submit, each answer in the part you submit is stated in words, with its reason or note, a choice by its
                label, and a
                document by the number of files attached to it. The summary uses the server&apos;s current scope;
                undisclosed answers are counted. If the summary cannot be loaded, for example because your access
                changed, it shows nothing from before and says why; select <b id={"information-request-submission-article-b-16"}>Try again</b> to load it again.
            </li>
            <li id={"information-request-submission-article-li-6"}>
                <b id={"information-request-submission-article-b-17"}>Submit</b> is available once everything is complete and nothing is still saving or sending, and it
                updates as soon as a file is
                uploaded, replaced, or withdrawn. It says why when an answer was not saved, changed elsewhere, or a file
                was not sent. If anything
                changes after you reviewed it, including a check on a file or a confirmation, the submission is refused
                and you are asked to review
                it again. Submitting twice by accident records one submission, not two. If submitting, a confirmation,
                or <b id={"information-request-submission-article-b-18"}>Withdraw</b> is
                interrupted, select it again: it is recorded once, also after you verify again.
            </li>
            <li id={"information-request-submission-article-li-7"}>
                When a reviewer returns a part for correction, <b id={"information-request-submission-article-b-19"}>Review and submit</b> opens on that
                part, <b id={"information-request-submission-article-b-20"}>Part to submit</b> marks it returned for correction, and <b id={"information-request-submission-article-b-21"}>Submit again</b>{" "}
                is available once every returned answer has been changed. Its confirmations can be given
                again for the corrected content. Answers and confirmations its Template takes only once
                change only if the reviewer returned them; otherwise a confirmation keeps its decision.
            </li>
        </ul>
    </>
);
