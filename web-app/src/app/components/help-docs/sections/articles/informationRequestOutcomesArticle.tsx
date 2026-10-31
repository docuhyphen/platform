export const informationRequestOutcomesArticle = (
    <>
        <p id={"information-request-outcomes-intro"}>
            Once a request is issued, its page shows its outcomes: answers kept for reuse, what your own process
            decided, and corrections a subject asked for. Each is recorded beside the submission, which itself never
            changes.
        </p>

        <h3 id={"information-request-outcomes-facts-heading"}>Accepted facts</h3>
        <p id={"information-request-outcomes-facts-help"}>
            <b>Answers you can promote</b> lists the typed answers of the current submissions. Select <b>Promote</b>,
            give a purpose and reuse policy basis as short lowercase keys, and choose who may see the fact: the
            requesting side only, or the responding parties too. You can also set when it is valid and when it stops
            being offered. When conforming files in the same submission support the answer, <b>Supporting evidence to
            keep with the fact</b> lets you keep chosen versions with it. An answer in a submission that needs review
            can be promoted once its review accepts it; an answer the review waived is kept as submitted, not as
            accepted by review. The request must name a single subject whose processing is not restricted, and that
            subject must be the one the submission answered for. An answer that cannot be promoted says why instead of
            offering <b>Promote</b>: the request names no single subject, the subject was named after the submission,
            the subject&apos;s processing is restricted, the answer was corrected, or its review has not accepted it
            yet. An answer already kept as a fact names its purposes; promoting it again for one of them is refused.
            Promoting never replaces a current fact from another request: when two current facts about the same subject
            differ, both are marked as differing until one of them is revoked in its own request.
        </p>
        <p id={"information-request-outcomes-facts-reuse-help"}>
            A fact keeps its source: the request, the submission, the item it came from, and any selected conforming
            evidence versions that support that item. When a later request about the same subject reuses a fact for
            the same purpose, only a fact the responding parties may see is offered to them, and it is used only after
            the respondent confirms it is still accurate. That confirmation is saved as their answer and recorded with
            the fact and evidence it came from, which keeps the fact&apos;s request from being disposed of until the
            later request is. Facts whose source Exchange was deleted, whose source item was corrected, or whose request
            is being disposed of are no longer offered. A superseded fact stays superseded after its successor is
            disposed of.
            When reuse would clear answers that no longer apply, select each requested clearing confirmation before
            using
            the answer again. Nothing is cleared until you confirm. <b>Promoted
            facts</b> states each fact&apos;s standing: as submitted or accepted by review, current, expired, or
            outside its valid period, and whether it differs from another current fact about the same
            subject. <b>Revoke</b> with a reason stops a fact from being offered; the revocation stays in the request
            history. Revocation reasons allow up to 128 characters.
        </p>

        <h3 id={"information-request-outcomes-decisions-heading"}>Business decisions</h3>
        <p id={"information-request-outcomes-decisions-help"}>
            <b>Record decision</b> records what your own process decided because of the request: the process and
            outcome as short lowercase keys, when it was decided, and optional references to the reasons and to
            another system, each up to 512 characters. The time cannot be in the future. The latest decision of a
            process can be followed
            by <b>Reconsider</b> or <b>Appeal</b>; earlier decisions stay listed. A business decision is a record
            only: it does not change the request, its review, or the Exchange.
        </p>

        <h3 id={"information-request-outcomes-corrections-heading"}>Corrections</h3>
        <p id={"information-request-outcomes-corrections-help"}>
            When the subject asks for a submitted answer to be corrected, <b>Corrections</b> records the corrected
            value or note with a reason, a purpose, and a policy basis, as a privacy request of that subject. When the
            request names several subjects, choose the subject who asked. Each answer lists the corrections already
            recorded for it, with the subject, the time, and the reason. The
            submitted answer is kept unchanged and the correction is audited. Existing facts from the corrected item
            remain in history but cannot be reused, and that item cannot be promoted again. It is offered to the account
            that owns
            the request: a personal owner, or in an organization someone with privacy management, such as an
            Organization Owner or Administrator.
        </p>

        <h3 id={"information-request-outcomes-access-heading"}>Who can record outcomes</h3>
        <p id={"information-request-outcomes-access-help"}>
            Accepted facts and business decisions are recorded by the people who manage the request, such as the
            Exchange owner or its Decision Maker, and only they see its business decisions; respondents, reviewers, and
            the other parties do not. Facts can be promoted while the request is open and after it closes, and a
            business decision can be recorded at any point after issue, as long as the Exchange is active, the
            request&apos;s execution grant still allows its work to continue, and the owner&apos;s account is not
            suspended, so neither is offered on a request marked <b>Changes paused</b> or <b>Stopped</b>, nor once the
            Exchange is no longer active. Revoking a fact only reduces what is offered, so it stays possible after the
            request is cancelled, superseded, or expired, after the Exchange ends, is rejected, or is rescinded, and
            after the grant is revoked; it is no longer offered once the Exchange is deleted. Promoting, listing, and
            revoking a fact need permission to see the item it came from; a fact is offered only for an item you may
            see; and a fact from a protected item also needs a verification shortly before.
        </p>
    </>
);
