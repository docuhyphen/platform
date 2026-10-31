export const informationRequestOperationsArticle = (
    <>
        <p id={"information-request-operations-intro"}>
            The operations queue lists the Information Requests you own with how long each has been
            open, its service level, the notices it has sent, and anything that needs attention.
        </p>

        <h3 id={"information-request-operations-access-heading"}>Who sees it</h3>
        <p id={"information-request-operations-access-help"}>
            Select <b>Information Request operations</b> in the top menu. It appears when the active account&apos;s plan
            includes Information Requests or the account still owns requests. When new requests cannot be created, the
            page says why, and existing requests, their records, and their exports stay available. Your personal
            requests are always visible to you. In an organization, Organization Owners and Administrators see the queue
            for all of the organization&apos;s requests; a member without operations access sees a notice instead. A
            request&apos;s clocks, notices, audit history, and records
            open only for someone who manages that request: the Exchange owner or a decision maker on the request; for
            anyone else the request&apos;s page says so once instead of showing its tabs.
            Opening another request, including with the browser&apos;s Back button, starts on its Clocks tab and the
            first page of its audit history. Nothing shown or drafted for the earlier request carries over to it.
            Switching to another organization or to your personal workspace does the same for the whole page: it shows
            only what that workspace holds, and an export or download still being made for the earlier one stops without
            saving a file. When the queue, an audit search, or a tab can no longer be read, for example because your
            access changed, the page says why and stops showing what it read before until it can be read again. Until
            then, Assigned to offers only Anyone and Me, and a party you already chose there shows as Chosen party. Each
            list that cannot be read offers <b>Try again</b>, and a failure without a readable reason is stated in the
            page&apos;s own words.
        </p>

        <h3 id={"information-request-operations-queue-heading"}>Service levels</h3>
        <ul id={"information-request-operations-queue-list"}>
            <li><b>On track:</b> every running clock is before its reminder window.</li>
            <li><b>Due soon:</b> a clock is inside the reminder window before it is due.</li>
            <li><b>Overdue:</b> a clock passed its due time, even if it was later paused or stopped.</li>
            <li><b>Paused:</b> a clock is paused and has not passed its due time.</li>
            <li><b>Met:</b> the clocks stopped on or before their due time, or while paused.</li>
            <li><b>No clock:</b> the request runs without a clock.</li>
        </ul>
        <p id={"information-request-operations-filter-help"}>
            The most urgent clock sets a request&apos;s level. Each row shows the request&apos;s title,
            the parties acting on it, and when it was issued, first viewed, and started. People who manage preservation
            holds also see <b>Place hold</b> on each row, which holds that request without opening it. The queue starts
            with draft,
            issued, and in progress requests, and
            <b> State</b> chooses which states it lists. <b>Search</b> finds a title or the start of a request
            id, <b>Exchange</b> finds an Exchange you can open, one you started or that is shared with you directly, by
            its name and lists only its requests (in your personal workspace it can also offer an organization&apos;s
            Exchange, which lists none),
            <b> Assigned to</b> lists the requests of Me or of a party on a page of the queue you have already seen, <b>Exception
            kinds</b> lists requests with any
            of the chosen exceptions, and <b>Exceptions only</b> lists requests with undeliverable or failed
            notices, escalated clocks, skipped automations, or failing event deliveries. Changing a filter
            returns to the first page and clears the selection. Clock reminder points count non-superseded scheduled
            reminders; manual reminders appear in Notices.
        </p>
        <p id={"information-request-operations-standing-help"}>
            A request that continues after the owner&apos;s plan lapsed shows <b>Continuing as issued</b>, one
            whose changes are paused while the account is suspended shows <b>Changes paused</b>, and one that
            accepts no further changes shows <b>Stopped</b>. Point at the badge, or open the request, to see why.
        </p>

        <h3 id={"information-request-operations-reminders-heading"}>Reminders and export</h3>
        <p id={"information-request-operations-reminders-help"}>
            Select issued or in progress requests, up to 100 at a time, and choose <b>Send reminders</b>: after you
            confirm, each responding party of each selected request whose role still has an item to provide, one not
            yet submitted or returned for correction, is owed a reminder notice, with identical recipient principals
            counted once, and the page says how many notices were queued. A reminder names the earliest due time of the
            request&apos;s running clocks and leaves the due date out when none is running. Delivery then shows in each
            request&apos;s
            Notices tab. Drafts and finished requests take no reminder, and if any selected request is no longer issued
            or in progress, nothing is sent. A request reminded in the last 24 hours is
            skipped, and the page says when it can be reminded again. Organization Owners and Administrators send
            reminders for the
            organization&apos;s requests, and you send them for your personal requests. <b>Export CSV</b> downloads
            every request
            that matches the current filters, as the queue shows it, with its due, created, issued, first viewed, and
            started times in UTC; Due is empty when no clock is running, and the others until they happen.
        </p>

        <h3 id={"information-request-operations-tabs-heading"}>Due date policies, privacy, and audit search</h3>
        <ul id={"information-request-operations-tabs-list"}>
            <li>
                <b>Due date policies</b> sets how long a request has, in calendar time or business hours, when reminders
                go out, and what happens when it is due. Business hours count only the periods under <b>Working
                hours</b>, skipping holidays; a day can have several periods, days can have different hours, and a
                period set to <b>Until midnight</b> ends with its day. Under <b>Notice wording</b>, choose the reminder
                and overdue notice Communications: the organization&apos;s or the platform&apos;s for an organization
                policy, your own or the platform&apos;s for a personal one, and otherwise the platform wording. Each
                change publishes a new version that starts from the latest one, working hours, Communications, and
                escalation included. Leave <b>Escalate after</b> empty to never escalate; 0 escalates at the due time.
                Until a policy can be saved, its form says what it still needs. <b>Versions</b> lists every published
                version with its hours, holidays, and notice wording.
            </li>
            <li>
                <b>Privacy</b>, for people who manage privacy, lists the subjects your requests name,
                records access, export, restriction, and deletion requests about them, and lifts
                restrictions; see Record preservation, retention, and disposal.
            </li>
            <li>
                <b>Audit search</b> searches the audit record of every request you own by request,
                event, actor, and time, together with your Templates, due date policies, and privacy
                requests. Reading a request&apos;s notices is recorded too. Each result offers <b>This actor</b> and <b>This
                request</b> to search that actor&apos;s or request&apos;s events.
            </li>
        </ul>
    </>
);
