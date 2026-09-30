export const informationRequestOperationsArticle = (
    <>
        <p id={"information-request-operations-intro"}>
            The operations queue lists the Information Requests you own with how long each has been
            open, its service level, the notices it has sent, and anything that needs attention.
        </p>

        <h3 id={"information-request-operations-access-heading"}>Who sees it</h3>
        <p id={"information-request-operations-access-help"}>
            Select <b>Information Request operations</b> in the top menu. It appears when the active
            account&apos;s plan includes Information Requests or the account still owns requests. When new
            requests cannot be created, the page says why, and existing requests, their records, and
            their exports stay available. Your personal requests are always visible to you. In an
            organization, Organization Owners and Administrators see the queue for all of the
            organization&apos;s requests. A request&apos;s clocks, notices, audit history, and
            records open only for someone who manages that request: the Exchange owner or a
            decision maker on the request.
        </p>

        <h3 id={"information-request-operations-queue-heading"}>Service levels</h3>
        <ul id={"information-request-operations-queue-list"}>
            <li><b>On track:</b> every running clock is before its reminder window.</li>
            <li><b>Due soon:</b> a clock is inside the reminder window before it is due.</li>
            <li><b>Overdue:</b> a clock passed its due time, even if it was later paused or stopped.</li>
            <li><b>Paused:</b> a clock is paused and has not passed its due time.</li>
            <li><b>Met:</b> the clocks stopped on or before their due time.</li>
            <li><b>No clock:</b> the request runs without a clock.</li>
        </ul>
        <p id={"information-request-operations-filter-help"}>
            The most urgent clock sets a request&apos;s level. Each row shows the request&apos;s title and
            the parties acting on it. <b>Search</b> finds a title or the start of a request id,
            <b> Assigned to</b> lists the requests of one party, and <b>Exceptions only</b> lists requests
            with undeliverable or failed notices, escalated clocks, skipped automations, or failing event
            deliveries.
        </p>

        <h3 id={"information-request-operations-reminders-heading"}>Reminders and export</h3>
        <p id={"information-request-operations-reminders-help"}>
            Select requests and choose <b>Send reminders</b>: after you confirm, each responding party
            of each selected request is owed a reminder notice, and the page says how many notices were
            queued. Delivery then shows in each request&apos;s Notices tab. If any selected request is no
            longer open, nothing is sent. A request reminded in the last 24 hours is skipped, and the page
            says when it can be reminded again. Organization Owners and Administrators send reminders for the
            organization&apos;s requests, and you send them for your personal requests. <b>Export
            CSV</b> downloads every request that matches the current filters, as the queue shows it.
        </p>

        <h3 id={"information-request-operations-tabs-heading"}>Due date policies, privacy, and audit search</h3>
        <ul id={"information-request-operations-tabs-list"}>
            <li>
                <b>Due date policies</b> sets how long a request has, in calendar time or business
                hours with working days and holidays, when reminders go out, and what happens when it is
                due. Each change publishes a new version.
            </li>
            <li>
                <b>Privacy</b>, for people who manage privacy, lists the subjects your requests name,
                records access, export, restriction, and deletion requests about them, and lifts
                restrictions; see Record preservation, retention, and disposal.
            </li>
            <li>
                <b>Audit search</b> searches the audit record of every request you own by request,
                event, actor, and time.
            </li>
        </ul>

        <h3 id={"information-request-operations-clocks-heading"}>Clocks</h3>
        <p id={"information-request-operations-clocks-help"}>
            Each clock shows whether it is running, paused, or stopped, when it is due, and its
            history: started, paused, resumed, extended, reminders, overdue, escalated, and
            stopped. A clock keeps the policy version it started with, so publishing a new version
            changes only clocks started afterwards. When a request finishes or its Exchange ends,
            its clocks stop. <b>Manage this request</b> opens the request&apos;s page, where a clock is
            paused, resumed, or extended with a reason.
        </p>

        <h3 id={"information-request-operations-notices-heading"}>Notices</h3>
        <p id={"information-request-operations-notices-help"}>
            The Notices tab lists reminder, overdue, and amendment notices with their delivery
            state, the masked address they went to, the subject sent, and every delivery attempt.
            A notice is written once and every retry sends exactly the same content. A party with
            no address on file is marked undeliverable instead of being retried.
        </p>

        <h3 id={"information-request-operations-audit-heading"}>Audit history</h3>
        <p id={"information-request-operations-audit-help"}>
            The Audit history tab lists the request&apos;s audit records with their outcome and
            whether they are sealed. Values that are not approved for display are withheld and
            counted instead of shown. The reconciliation compares every recorded change to the
            request with its audit record and reports changes that have no record and records
            that match no change.
        </p>

        <h3 id={"information-request-operations-records-heading"}>Records</h3>
        <p id={"information-request-operations-records-help"}>
            The Records tab explains whether the request can be disposed of yet and when it
            becomes eligible, and lets people who manage preservation holds place one. <b>Create
            export</b> freezes the request&apos;s whole record with a SHA-256 hash. <b>Download</b>
            checks that hash again first, and an export whose content no longer matches is not
            downloaded. Each owner can make up to 100 record exports a day; the next export then says when
            to try again.
        </p>
    </>
);
