export const informationRequestIssuingArticle = (
    <>
        <h3 id={"information-request-managing-links-heading"}>Access links</h3>
        <p id={"information-request-managing-links-help"}>
            An email-only participant who responds without signing in uses an access link. Signed-in users and groups
            do not need one, nor does a participant who linked an account, who signs in from then on and gets no new
            link. After <b>Create link</b>, the link is shown once, with <b>Copy link</b>, and only while you can still
            open the request; the platform does not send it, so share it yourself. It cannot be shown again: <b>Resend
            link</b> creates a new link and the earlier one stops working, and <b>Revoke link</b> ends it. A link works
            for 30 days; an expired link shows as expired and offers only <b>Revoke link</b>, after which <b>Create
            link</b> makes a new one. <b>Create link</b> is offered only on an Exchange that does not require its
            recipients to sign in: on one that does, a person added by email must already be a recipient of the
            Exchange and responds with that account instead. See Access links and respondent sessions.
        </p>
        <h3 id={"information-request-managing-completion-heading"}>Ending the Exchange</h3>
        <p id={"information-request-managing-completion-help"}>
            While <b>Must finish before the Exchange can end</b> is on, the Exchange cannot end until this request
            finishes. When it is off, ending the Exchange offers to cancel the request if it is still open; when a
            Workflow must approve the ending, that choice is kept until the Workflow confirms it. It can be changed
            until the request finishes, also on a <b>Stopped</b> request, but not on one marked <b>Changes paused</b>;
            on a <b>Read only</b> draft it can only be turned off. If requests are still open when the Workflow
            approves the ending, the Exchange stays active, you
            and the approvers are notified, and ending it again needs a new approval.
        </p>

        <h3 id={"information-request-managing-issue-heading"}>Previewing and issuing</h3>
        <p id={"information-request-managing-issue-help"}>
            <b>Preview as recipient</b> shows the questions one answering party is asked; pick the party in <b>Preview
            for</b>. It is offered until the request
            finishes or its Exchange stops. <b>Issue</b> opens it to its parties, unless a party is bound to a suspended
            or ineligible trusted relationship, or a
            party could not answer a Field it is asked for. If the request changed meanwhile, the page reloads it so you
            can try again. A preview that cannot be read
            offers <b>Try again</b>.
        </p>

        <h3 id={"information-request-managing-clocks-heading"}>Due dates</h3>
        <p id={"information-request-managing-clocks-help"}>
            Choose <b>Due policy</b>, <b>Urgency</b>, and <b>Clock key</b>, then <b>Start clock</b>. The clock key is a
            lowercase key that starts with a letter or
            digit, and a stopped clock says Stopped; no active deadline. A running clock can be paused, resumed, or
            extended with a reason, and each change is kept in
            its history. Starting or resuming needs an execution grant that still sends reminders, and no clock changes
            while the owner&apos;s account is suspended.
            Due date policies are set up in operations and offered only in the workspace that owns the request. When
            clocks cannot be read, they and their controls
            are hidden; select <b>Try again</b>.
        </p>

        <h3 id={"information-request-managing-follow-ups-heading"}>Follow-up requests</h3>
        <p id={"information-request-managing-follow-ups-help"}>
            A request can repeat every 1 to 1,000 days, weeks, months, or years from a first due time between 1970 and
            9999, optionally a set number of times, and the
            schedule can be set while it is still a draft; once it is issued, select <b>Create the next request</b> when
            it is time. It follows the series&apos; latest
            request that was not cancelled or replaced, or its first, and needs the same authority over that request as
            over its first. Every request of a series shows
            it and offers <b>Create the next request</b> while the series can continue, even after its first request was
            cancelled or replaced. <b>Request a
            supplement</b> asks for more on a submitted response, with a reason chosen from a list. Under <b>Expiry
            refreshes</b>, choose a requested document whose expiry date is captured
            and how many days before expiry to ask again, then <b>Add refresh rule</b>, on a draft too; each document
            takes one rule. The refreshed request is created
            automatically at that point, or with <b>Refresh now</b> once it is due, once per submission. Each rule says
            from which day its refresh is due or that it was
            made, and <b>Refresh now</b> is offered only while due. One that cannot be created yet is tried again later,
            up to a day apart, while other refreshes go
            ahead; an expired request is refreshed too, and refreshes stop once the Exchange has ended or was rejected,
            rescinded or deleted. A refresh rule or schedule
            cannot be changed or removed once added. Earlier requests and follow-ups are listed only when you can open
            them, and the next request&apos;s due time only
            when you can open every request its schedule has created; if they cannot be read, select <b>Try again</b>.
            Follow-ups, supplements, and refreshes are new
            requests: they start only once the request is issued and remain available after it closes, as long as the
            owner&apos;s plan allows new requests and the
            Exchange has not stopped, but not once it is cancelled or replaced; an expired request offers supplements
            and refreshes but no new schedule or refresh rule.
        </p>

        <h3 id={"information-request-managing-end-heading"}>Cancelling and replacing</h3>
        <p id={"information-request-managing-end-help"}>
            <b>Cancel request</b> ends a request that is not finished, with a reason chosen from a list; respondents can
            no longer
            answer it, its access links stop working, and its history is kept. <b>Supersede</b>, on a draft or an issued
            request,
            names another request on the same Exchange that is not finished, a draft or an issued one you manage too,
            that replaces
            it; the replaced request shows as superseded and keeps its history. Both remain available to the owner of a
            request marked <b>Stopped</b>, and neither to the owner of one marked <b>Changes paused</b>. A draft marked
            <b> Read only</b> can still be cancelled, but not superseded.
        </p>
    </>
);
