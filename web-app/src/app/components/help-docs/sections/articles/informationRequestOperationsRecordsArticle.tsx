export const informationRequestOperationsRecordsArticle = (
    <>
        <h3 id={"information-request-operations-clocks-heading"}>Clocks</h3>
        <p id={"information-request-operations-clocks-help"}>
            Each clock shows whether it is running, paused, or stopped, when it is due, its policy version (with the
            policy&apos;s name and terms when you can read the owner&apos;s policies), urgency, received and stop times,
            and its history: started,
            paused, resumed, extended, reminders, overdue, escalated, and stopped, each with the due time it left and
            any reminder point. A clock keeps the policy version it
            started with, so publishing a new version changes only clocks started afterwards. When a request finishes or
            its Exchange ends, its clocks stop. While the owner&apos;s account is suspended, a running clock records no
            reminder, overdue notice, escalation, or expiry: it checks again every hour, and a due time that passed
            meanwhile takes effect, with its overdue notice, at the first check after the suspension ends; until then no
            clock can be paused, resumed, or extended either. Once the request&apos;s execution grant is revoked, or
            when it does not allow reminders, its clocks record none of these again, though they can still be paused or
            extended. <b id={"information-request-operations-records-article-b-1"}>Manage this request</b> opens the request&apos;s page, where a clock is paused, resumed, or
            extended with a lowercase reason code. A clock reset at issue or acceptance says Counting reset; deferral
            alone does not change its edit version.
        </p>

        <h3 id={"information-request-operations-notices-heading"}>Notices</h3>
        <p id={"information-request-operations-notices-help"}>
            The Notices tab lists reminder, overdue, and amendment notices with their delivery state, the masked address
            they went to, the subject sent, and every delivery attempt. A notice is written once and every retry sends
            exactly the same content, including the deployment prefix in the subject. Retry backoff does not block first
            attempts. A party with no address on file is marked undeliverable instead of being retried.
            A notice whose party was removed or reassigned to someone else, whose request has finished (other than the
            overdue notice owed by the due time that expired it), or whose Exchange was rejected, rescinded, or deleted
            is not sent: it is marked undeliverable with that reason, and it is never redirected to the new holder. The
            same applies to a notice, or a retry, that comes due while the owner&apos;s account is suspended, after the
            request&apos;s execution grant was revoked, or when that grant does not allow reminders. A clock notice
            whose due cycle changes, or whose clock pauses or stops, is skipped too.
        </p>

        <h3 id={"information-request-operations-audit-heading"}>Audit history</h3>
        <p id={"information-request-operations-audit-help"}>
            The Audit history tab lists the request&apos;s audit records newest first, 50 to a page, with their outcome
            and
            whether they are sealed; <b id={"information-request-operations-records-article-b-2"}>Previous</b> and <b id={"information-request-operations-records-article-b-3"}>Next</b> move between pages. Values that are not approved
            for display
            are withheld and counted instead of shown. A follow-up or replacement request is named only when you can
            open it;
            otherwise it is withheld and counted too. Each record says who acted: a person, a respondent using an access
            link, an
            application, a Workflow, or the system (clocks, notices, file checks, and connectors). The audit record also
            keeps the session
            used and a label that never contains a respondent&apos;s contact address. The reconciliation compares every
            recorded
            change to the request with its audit record and reports changes that have no record and records that match
            no change.
            Delegated authority grants and revocations are recorded with the affected party and, when scoped to one
            Requirement,
            that Requirement. Their instrument references and free-text revocation reasons are not shown in audit
            payloads.
            Creating, rotating, replacing, and explicitly revoking an access link also records the affected party and
            link. Tokens
            and contact codes are never copied into these records. If a link change cannot be recorded, it is refused
            and the
            earlier link and sessions stay as they were. Retrying the same change does not add another audit record.
            Revoking or
            reassigning a party, including when removing an organization member revokes their parties, records each
            access link
            the party still had as revoked with that cause. Rejecting, rescinding, or deleting the Exchange records each
            verified
            access link session it ends. These records name who made the change, or the system when no person did, and
            the change
            is refused if they cannot be captured. Sending a contact code, each refused code, and the session a verified
            code
            opens are recorded as actions of the party through its access link, with the attempt number and any lock on
            refused
            codes. Linking a participant to a registered account records the account link and the access links it ends.
            Verifying
            again for protected questions records each code sent, each refused code, and the renewed verification the
            same way,
            under the session that asked. Contact codes and addresses are never recorded.
        </p>

        <h3 id={"information-request-operations-records-heading"}>Records</h3>
        <p id={"information-request-operations-records-help"}>
            The Records tab explains whether the request can be disposed of yet and when it becomes eligible, lists
            every preservation hold
            that covers it, also while it is open, and lets people who manage preservation holds place one. <b id={"information-request-operations-records-article-b-4"}>Create
            export</b> freezes the
            request&apos;s whole record with a SHA-256 hash; an optional <b id={"information-request-operations-records-article-b-5"}>Transfer region</b> names where it is sent,
            and a region the owner may not transfer to is refused. Each export states its hash, its size, where it is
            stored, and its transfer decision. The hash covers the stored record, so a saved file&apos;s own checksum
            differs from it. <b id={"information-request-operations-records-article-b-6"}>Download</b> checks that hash again first, and an export whose content no
            longer matches is not downloaded. A download still being read when you open another request or leave the
            Records tab is not
            saved. An export that includes confidential information says so, and reading it asks you to verify again. An
            export names the
            request that replaced this one, or the request behind an accepted fact it reused, only when its maker could
            open that request,
            and <b id={"information-request-operations-records-article-b-7"}>Download</b> also leaves out any that you cannot open. When it leaves any out, the page says how
            many. Each owner can make up to 100 record exports a day, subject
            exports included, across all of its requests, also when several are made at once; the next export then says
            when to try again. Facts and recertifications of withheld Requirements contain only identifiers and a
            withheld marker. Exports are limited to 25 MiB by default. Reading a subject export records a read for each
            request it covers.
            If <b id={"information-request-operations-records-article-b-8"}>Create export</b> is interrupted, select it again: it is sent as the same request, so one export is
            made and counted, also after you leave the tab. Reminders, holds, and privacy requests are retried the same
            way.
        </p>
    </>
);
