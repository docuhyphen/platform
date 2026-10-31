export const informationRequestReviewArticle = (
    <>
        <p id={"information-request-review-intro"}>
            When a Template routes work to a reviewer, each submission waits for review before the
            request can complete. An item goes to a reviewer when the Template requires its review, when it is answered
            with an exception and the Template requires review on exception, or
            when its files are marked Ready for review or ask for a waiver.
        </p>

        <h3 id={"information-request-review-stages-heading"}>Review stages</h3>
        <p id={"information-request-review-stages-help"}>
            A Template can review in stages, one after another or side by side. Each stage covers some of the
            Template&apos;s sections and decides each item by its rule: the first reviewer decides; every reviewer
            decides and the most severe decision counts; a set number of matching decisions settles the item; or every
            reviewer must agree. When reviewers disagree, the Template either applies the most severe decision or waits
            for an override by someone who manages the request&apos;s reviews. A stage can exclude anyone who answered,
            submitted, or confirmed the submission, and anyone who decided an earlier stage or the earlier review. A
            Template that states no stages reviews every routed item in one stage decided by the first reviewer.
            Reviewers and the people who manage the request see its stages; respondents see each review&apos;s outcome.
        </p>

        <h3 id={"information-request-review-queue-heading"}>Your reviews</h3>
        <p id={"information-request-review-queue-help"}>
            Open an assigned review from <b>Assigned to you</b> or the Exchange&apos;s Information Requests tab
            with <b>Review</b>, whatever your
            own plan. Queue entries name the request, Exchange and stage; a failed read offers <b>Try again</b>. The
            review shows each routed item as it was submitted. Items you are not allowed to read,
            including a protected item when you have not verified your account in the last 5 minutes, show only that
            they exist and where they stand; their findings, comments, decision reasons, worksheet notes, and returned
            file names stay hidden. Anyone the review shows an item to can also open that item&apos;s submitted files.
            A reviewer who needs more than the submission holds selects <b>Request more information</b> on the review
            to start a supplemental request, as described under Follow-up requests.
            The review says how many protected items wait for verification: select <b>Verify again</b> and confirm with
            your account to see them, and saving or recording a decision about a protected item asks you to verify
            first. <b>Review</b> on an Exchange&apos;s Information Requests tab opens <b>Reviews assigned to
            you</b> when it cannot open one review directly.
            There a review whose request accepts no further changes shows <b>Stopped</b>, and one paused while its
            owner&apos;s account is suspended shows <b>Changes paused</b>.
        </p>

        <h3 id={"information-request-review-assignments-heading"}>Reviewers</h3>
        <p id={"information-request-review-assignments-help"}>
            A new review waits for its first reviewer. People who manage the request find each review
            in <b>Reviews</b> on the request&apos;s page, which says when one has no reviewer yet;<b> Open
            review</b> opens it and shows where each item stands, but not what it contains unless they may also review
            that item. The Reviewers panel lists who reviews each stage. Someone who manages the request&apos;s reviews
            selects <b>Assign reviewer</b> to give a Reviewer party a stage, or <b>Remove</b> to take an assignment
            back. An optional <b>Due</b> time, which cannot be one that has already passed, is shown with the
            assignment. Changed assignments state when and why they changed and any recorded explanation. An assigned
            reviewer can<b> Recuse</b> or <b>Delegate</b> the stage to another Reviewer party,
            and removing an assignment also needs a reason. Managers can also delegate another reviewer&apos;s
            assignment. <b>Delegate</b> lists by name each other Reviewer party the
            stage still admits, and is not offered when there is none. A stage that has already settled takes no new
            reviewer, by assignment or delegation. Each stage states its rules, such as who may not review it and how
            many reviewers decide each item. Each person counts once: nobody is assigned to a stage twice through
            different parties, and someone who already decided a stage cannot decide it again, even for a group party
            they belong to; another member of the group decides for it instead. A stage that keeps responders apart from
            reviewers counts every Subject, every party that answered (even one removed since), everyone who supplied a
            submitted file, and the account linked to anyone who answered, submitted, or confirmed, even after that
            person&apos;s party was reassigned. A declared recusal prevents reassignment to the same stage; removing a
            party revokes its undecided assignments.
        </p>

        <h3 id={"information-request-review-items-heading"}>Working on an item</h3>
        <p id={"information-request-review-items-help"}>
            Each item is named by its prompt. <b>Preview</b> and <b>Download</b> open the exact file version that was
            submitted,
            even after its submission is withdrawn or its entry removed. Preview opens inside the review; close it to
            return. A file still being read when you leave the review, or
            once the review no longer shows it, is not opened. <b>Comment</b> adds to the item&apos;s conversation; a
            comment
            marked Reviewers only is never shown to the respondent. While a stage that permits it is open and has not
            decided an
            item, a manager can <b>Override</b> the item&apos;s decision there with a reason, and a manager can <b>Add
            finding</b>{" "}
            on any item a stage reviews. Overriding, commenting, and a manager&apos;s finding on a protected item need
            the same
            recent verification as reading it, and a manager&apos;s finding must be about an item a stage reviews. The
            decision
            history lists the review&apos;s decisions and counts its remediations; it does not show each correction in
            detail.
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
                If your worksheet changed after you opened it, for example in another window, saving is
                refused and the latest review is shown with your unsaved choices kept; save again. If it
                changes elsewhere while your save is on its way, the review says so and shows the latest
                saved choices, and nothing is recorded until you select <b>Record decisions</b> again.
            </li>
            <li>
                Choices you have not saved stay in place when the review reloads, for example after a finding, and when
                it could not be loaded and you select <b>Try again</b>. If recording is interrupted, select <b>Record
                decisions</b> again: it is sent as the same request, so the decisions are recorded once.
            </li>
            <li>
                When you review several open stages, choose one in <b>Stage you are deciding</b>; each keeps its own
                unsaved choices, and once you record one the next open stage is shown. A stage you already decided, one
                not yet open, or one closed to earlier reviewers after you decided an earlier stage is not offered.
            </li>
        </ul>
    </>
);
