export const informationRequestOutcomesArticle = (
    <>
        <p id={"information-request-outcomes-intro"}>
            Once a request is issued, its page shows its outcomes: answers kept for reuse, what your
            own process decided, and corrections a subject asked for. Each is recorded beside the
            submission, which itself never changes.
        </p>

        <h3 id={"information-request-outcomes-facts-heading"}>Accepted facts</h3>
        <p id={"information-request-outcomes-facts-help"}>
            <b>Answers you can promote</b> lists the typed answers of the current submissions. Select
            <b> Promote</b>, give a purpose and reuse policy basis as short lowercase keys, and choose who may see the fact:
            the requesting side only, or the responding parties too. You can also set when it is valid
            and when it stops being offered. When conforming files in the same submission support the
            answer, <b>Supporting evidence to keep with the fact</b> lets you keep chosen versions with
            it. An answer in a submission that needs review can be promoted
            once its review accepts it, and the request must name a single subject whose processing is
            not restricted.
        </p>
        <p id={"information-request-outcomes-facts-reuse-help"}>
            A fact keeps its source: the request, the submission, the item it came from, and any selected
            conforming evidence versions that support that item. When a
            later request about the same subject reuses a fact for the same purpose, only a fact the
            responding parties may see is offered to them, and it is used only after the respondent
            confirms it is still accurate. That confirmation is saved as their answer and recorded
            with the fact and evidence it came from. <b>Promoted facts</b> states each fact&apos;s standing:
            as submitted or accepted by review, current,
            expired, or outside its valid period, and whether it differs from another current fact
            about the same subject. <b>Revoke</b> with a reason stops a fact from being offered; the
            revocation stays in the request history.
        </p>

        <h3 id={"information-request-outcomes-decisions-heading"}>Business decisions</h3>
        <p id={"information-request-outcomes-decisions-help"}>
            <b>Record decision</b> records what your own process decided because of the request: the
            process and outcome as short lowercase keys, when it was decided, and optional references
            to the reasons and to another system. The time cannot be in the future. The latest
            decision of a process can be followed by <b>Reconsider</b> or <b>Appeal</b>; earlier
            decisions stay listed. A business decision is a record only: it does not change the
            request, its review, or the Exchange.
        </p>

        <h3 id={"information-request-outcomes-corrections-heading"}>Corrections</h3>
        <p id={"information-request-outcomes-corrections-help"}>
            When the subject asks for a submitted answer to be corrected, <b>Corrections</b> records the
            corrected value or note with a reason, a purpose, and a policy basis, as a privacy request of
            that subject. The submitted answer is kept unchanged and the correction is audited. It is
            offered to the account that owns the request: a personal owner, or in an organization
            someone with privacy management, such as an Organization Owner or Administrator.
        </p>

        <h3 id={"information-request-outcomes-access-heading"}>Who can record outcomes</h3>
        <p id={"information-request-outcomes-access-help"}>
            Accepted facts and business decisions are recorded by the people who manage the request,
            such as the Exchange owner or its Decision Maker. Everyone who can view the request sees
            its business decisions. Facts can be promoted and revoked while the request is open and
            after it closes, and a business decision can be recorded at any point after issue, as long
            as the Exchange is active.
        </p>
    </>
);
