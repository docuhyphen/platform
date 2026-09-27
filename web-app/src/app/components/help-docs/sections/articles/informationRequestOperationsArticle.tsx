export const informationRequestOperationsArticle = (
    <>
        <p id={"information-request-operations-intro"}>
            The operations queue lists the Information Requests you own with how long each has been
            open, its service level, the notices it has sent, and anything that needs attention.
        </p>

        <h3 id={"information-request-operations-access-heading"}>Who sees it</h3>
        <p id={"information-request-operations-access-help"}>
            When your plan includes Information Requests, select <b>Information Request
            operations</b> in the top menu. Your personal requests are always visible to you. In an
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
            The most urgent clock sets a request&apos;s level. Filter by service level, or turn on
            <b> Exceptions only</b> to list requests with undeliverable or failed notices, escalated
            clocks, skipped automations, or failing event deliveries.
        </p>

        <h3 id={"information-request-operations-clocks-heading"}>Clocks</h3>
        <p id={"information-request-operations-clocks-help"}>
            Each clock shows whether it is running, paused, or stopped, when it is due, and its
            history: started, paused, resumed, extended, reminders, overdue, escalated, and
            stopped. A clock keeps the policy version it started with, so publishing a new version
            changes only clocks started afterwards. When a request finishes or its Exchange ends,
            its clocks stop.
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
            downloaded.
        </p>
    </>
);
