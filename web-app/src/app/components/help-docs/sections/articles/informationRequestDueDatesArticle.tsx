export const informationRequestDueDatesArticle = (
    <>
        <p id={"information-request-due-dates-intro"}>
            A request&apos;s clocks measure the time its respondents have to answer. This article explains when a clock
            waits, when it pauses by itself, and who its reminders and overdue notices go to.
        </p>

        <h3 id={"information-request-due-dates-start-heading"}>Starting and changing a clock</h3>
        <p id={"information-request-due-dates-start-help"}>
            Without a received time supplied through the API, a new clock counts from when you select <b id={"information-request-due-dates-article-b-1"}>Start clock</b>.
            A draft clock waits for issue and Exchange acceptance, keeping its whole budget even if manually paused.
            Its history calls this <b id={"information-request-due-dates-article-b-2"}>Counting reset</b>. A manual pause stays paused after that reset.
            Pause, resume and extension use a short lowercase reason code, such as <code id={"information-request-due-dates-article-code-1"}>respondent.requested</code>.
            Overdue since states the due time that passed, even when the scheduler detects it later. Pausing and
            resuming
            a clock with no time left keeps that passed due time.
        </p>
        <h3 id={"information-request-due-dates-acceptance-heading"}>Before the Exchange is accepted</h3>
        <p id={"information-request-due-dates-acceptance-help"}>
            While the Exchange waits for its recipient to accept it, nobody can answer its requests, so a clock records
            no
            reminder, overdue notice, escalation, or expiry. Once the Exchange is accepted, a clock started without a
            received
            time counts its whole time from the acceptance, and its history shows Counting reset then. A clock started
            with a
            received time keeps its due time.
        </p>

        <h3 id={"information-request-due-dates-submission-heading"}>After a complete submission</h3>
        <p id={"information-request-due-dates-submission-help"}>
            When everything asked for has been submitted and the request waits only for review or closing, its running
            clocks
            pause by themselves. They resume with the time they had left when work goes back to the respondents: an item
            is
            returned for correction, or the submission is withdrawn. A clock you paused yourself stays paused until you
            resume it.
        </p>

        <h3 id={"information-request-due-dates-reminders-heading"}>Who is reminded</h3>
        <p id={"information-request-due-dates-reminders-help"}>
            Reminders and overdue notices, whether a clock sends them or you select <b id={"information-request-due-dates-article-b-3"}>Send reminders</b>, go only to
            the
            responding parties who still have items to provide: an item not yet submitted, or one returned for
            correction. A
            person holding several responding roles receives one reminder for that same principal. A notice owed to a
            group goes to each active member with an email address, recorded as one notice for each member, and
            the Notices tab shows it as sent to a group member without the member&apos;s address. Moving a draft to a
            later Template
            Version tells none of its parties, since the request has not been sent to them yet.
        </p>

        <h3 id={"information-request-due-dates-failures-heading"}>Notices that cannot be prepared</h3>
        <p id={"information-request-due-dates-failures-help"}>
            A notice that cannot be prepared, for example because something it needs cannot be read while it is
            rendered, is tried again on later passes after the notices waiting behind it. After five failed tries it is
            marked failed, the operations queue lists the request under failed notices, and the platform health report
            stops counting it as overdue. A value named in a Communication that cannot be filled in does not stop the
            notice: it is sent with that token left as written.
        </p>
        <h3 id={"information-request-due-dates-delivery-heading"}>Delivery and retries</h3>
        <p id={"information-request-due-dates-delivery-help"}>
            Retry backoff does not hold newer notices behind it. A queued clock notice is skipped if its clock was
            paused,
            stopped or moved to another due cycle before sending, except the overdue notice that records an expiry.
            Each notice keeps the subject and body it was prepared with, including the deployment name in the subject;
            a notice prepared from a Communication is emailed as a formatted message made from that Markdown body.
        </p>
    </>
);
