export const informationRequestOperationsArticle = (
    <>
        <p id={"information-request-operations-intro"}>
            The operations queue lists the Information Requests you own with how long each has been
            open, its service level, the notices it has sent, and anything that needs attention.
        </p>

        <h3 id={"information-request-operations-access-heading"}>Who sees it</h3>
        <p id={"information-request-operations-access-help"}>
            Select <b id={"information-request-operations-article-b-1"}>Information Request operations</b> in the top menu. It appears when the active account&apos;s plan
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
            list that cannot be read offers <b id={"information-request-operations-article-b-2"}>Try again</b>, and a failure without a readable reason is stated in the
            page&apos;s own words.
        </p>

        <h3 id={"information-request-operations-queue-heading"}>Service levels</h3>
        <ul id={"information-request-operations-queue-list"}>
            <li id={"information-request-operations-article-li-1"}><b id={"information-request-operations-article-b-3"}>On track:</b> every running clock is before its reminder window.</li>
            <li id={"information-request-operations-article-li-2"}><b id={"information-request-operations-article-b-4"}>Due soon:</b> a clock is inside the reminder window before it is due.</li>
            <li id={"information-request-operations-article-li-3"}><b id={"information-request-operations-article-b-5"}>Overdue:</b> a clock passed its due time, even if it was later paused or stopped.</li>
            <li id={"information-request-operations-article-li-4"}><b id={"information-request-operations-article-b-6"}>Paused:</b> a clock is paused and has not passed its due time.</li>
            <li id={"information-request-operations-article-li-5"}><b id={"information-request-operations-article-b-7"}>Met:</b> the clocks stopped on or before their due time, or while paused.</li>
            <li id={"information-request-operations-article-li-6"}><b id={"information-request-operations-article-b-8"}>No clock:</b> the request runs without a clock.</li>
        </ul>
        <p id={"information-request-operations-filter-help"}>
            The most urgent clock sets a request&apos;s level. Each row shows the request&apos;s title,
            the parties acting on it, and when it was issued, first viewed, and started. People who manage preservation
            holds also see <b id={"information-request-operations-article-b-9"}>Place hold</b> on each row, which holds that request without opening it. The queue starts
            with draft,
            issued, and in progress requests, and
            <b id={"information-request-operations-article-b-10"}> State</b> chooses which states it lists. <b id={"information-request-operations-article-b-11"}>Search</b> finds a title or the start of a request
            id, <b id={"information-request-operations-article-b-12"}>Exchange</b> finds an Exchange you can open, one you started or that is shared with you directly, by
            its name and lists only its requests (in your personal workspace it can also offer an organization&apos;s
            Exchange, which lists none),
            <b id={"information-request-operations-article-b-13"}> Assigned to</b> lists the requests of Me or of a party on a page of the queue you have already seen, <b id={"information-request-operations-article-b-14"}>Exception
            kinds</b> lists requests with any
            of the chosen exceptions, and <b id={"information-request-operations-article-b-15"}>Exceptions only</b> lists requests with undeliverable or failed
            notices, escalated clocks, skipped automations, or failing event deliveries. Changing a filter
            returns to the first page and clears the selection. Clock reminder points count non-superseded scheduled
            reminders; manual reminders appear in Notices.
        </p>
        <p id={"information-request-operations-standing-help"}>
            A request that continues after the owner&apos;s plan lapsed shows <b id={"information-request-operations-article-b-16"}>Continuing as issued</b>, one
            whose changes are paused while the account is suspended shows <b id={"information-request-operations-article-b-17"}>Changes paused</b>, and one that
            accepts no further changes shows <b id={"information-request-operations-article-b-18"}>Stopped</b>. Point at the badge, or open the request, to see why.
        </p>

        <h3 id={"information-request-operations-reminders-heading"}>Reminders and export</h3>
        <p id={"information-request-operations-reminders-help"}>
            Select issued or in progress requests, up to 100 at a time, and choose <b id={"information-request-operations-article-b-19"}>Send reminders</b>: after you
            confirm, each responding party of each selected request whose role still has an item to provide, one not
            yet submitted or returned for correction, is owed a reminder notice, with identical recipient principals
            counted once, and the page says how many notices were queued. A reminder names the earliest due time of the
            request&apos;s running clocks and leaves the due date out when none is running. Delivery then shows in each
            request&apos;s
            Notices tab. Drafts and finished requests take no reminder, and if any selected request is no longer issued
            or in progress, nothing is sent. A request reminded in the last 24 hours is
            skipped, and the page says when it can be reminded again. Organization Owners and Administrators send
            reminders for the
            organization&apos;s requests, and you send them for your personal requests. <b id={"information-request-operations-article-b-20"}>Export CSV</b> downloads
            every request
            that matches the current filters, as the queue shows it, with its due, created, issued, first viewed, and
            started times in UTC; Due is empty when no clock is running, and the others until they happen.
        </p>

        <h3 id={"information-request-operations-tabs-heading"}>Due date policies, privacy, and audit search</h3>
        <ul id={"information-request-operations-tabs-list"}>
            <li id={"information-request-operations-article-li-7"}>
                <b id={"information-request-operations-article-b-21"}>Due date policies</b> sets how long a request has, in calendar time or business hours, when reminders
                go out, and what happens when it is due. Business hours count only the periods under <b id={"information-request-operations-article-b-22"}>Working
                hours</b>, skipping holidays; a day can have several periods, days can have different hours, and a
                period set to <b id={"information-request-operations-article-b-23"}>Until midnight</b> ends with its day. Under <b id={"information-request-operations-article-b-24"}>Notice wording</b>, choose the reminder
                and overdue notice Communications: the organization&apos;s or the platform&apos;s for an organization
                policy, your own or the platform&apos;s for a personal one, and otherwise the platform wording. Each
                change publishes a new version that starts from the latest one, working hours, Communications, and
                escalation included. Leave <b id={"information-request-operations-article-b-25"}>Escalate after</b> empty to never escalate; 0 escalates at the due time.
                Until a policy can be saved, its form says what it still needs. <b id={"information-request-operations-article-b-26"}>Versions</b> lists every published
                version with its hours, holidays, and notice wording.
            </li>
            <li id={"information-request-operations-article-li-8"}>
                <b id={"information-request-operations-article-b-27"}>Privacy</b>, for people who manage privacy, lists the subjects your requests name,
                records access, export, restriction, and deletion requests about them, and lifts
                restrictions; see Record preservation, retention, and disposal.
            </li>
            <li id={"information-request-operations-article-li-9"}>
                <b id={"information-request-operations-article-b-28"}>Audit search</b> searches the audit record of every request you own by request,
                event, actor, and time, together with your Templates, due date policies, and privacy
                requests. Reading a request&apos;s notices is recorded too. Each result offers <b id={"information-request-operations-article-b-29"}>This actor</b> and <b id={"information-request-operations-article-b-30"}>This
                request</b> to search that actor&apos;s or request&apos;s events.
            </li>
        </ul>
    </>
);
