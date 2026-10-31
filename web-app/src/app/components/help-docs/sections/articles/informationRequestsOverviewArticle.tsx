export const informationRequestsOverviewArticle = (
    <>
        <p id={"information-requests-overview-intro"}>
            An Information Request asks named parties for specific information on an Exchange: typed
            answers, documents, and confirmations, organized in sections. Each request keeps an exact
            record of what was asked, what was submitted, how it was reviewed, and what was decided.
        </p>

        <h3 id={"information-requests-overview-where-heading"}>Where requests appear</h3>
        <p id={"information-requests-overview-where-help"}>
            Open an Exchange and select the <b>Information Requests tab</b>. It lists only the requests
            you are allowed to see, each with its title, status, due time, how many counted answers are
            complete (see Progress in the Templates article), and your roles. Its button names your next action: <b>Finish
            setup</b>,
            <b> Respond</b>, <b>Review</b>, <b>Manage</b>, or <b>View</b>. When you also manage a request whose next
            action is
            <b> Respond</b> or <b>Review</b>, <b>Manage</b> is offered beside it. A reviewer who only reviews opens
            their latest
            review with <b>View</b>. The tab shows nothing about requests you cannot see. It appears when the Exchange
            has a
            request for you or when you may create one, and it reads its list again when the Exchange starts, ends or is
            stopped. When you may create requests but the owner cannot right now, the tab says why, and if the list
            cannot be
            loaded the tab stays, says so and offers <b>Try again</b>. <b>New Information Request</b>{" "}
            appears only when you may create one.
        </p>
        <p id={"information-requests-overview-assigned-help"}>
            <b>Assigned to you</b> in the main menu lists every open request in which you hold a role, with its
            Exchange,
            including requests on Exchanges you cannot open yourself, and below them the reviews waiting for you. Each
            button opens your respond, review, or manage page. A request leaves the list once it is closed, cancelled,
            superseded or expired. The menu entry appears while something is assigned to you.
        </p>

        <h3 id={"information-requests-overview-plans-heading"}>Plans</h3>
        <p id={"information-requests-overview-plans-help"}>
            The Personal and Business plans include Information Requests; the Free plan does not. The
            request&apos;s owner provides the plan: parties who respond or review work on the requests
            assigned to them whatever their own plan. A plan change never hides a request: everything
            recorded stays readable to the people allowed to see it, and permitted exports stay
            available. When a request cannot continue normally, its page and its row in the Exchange tab
            show its standing:
        </p>
        <ul id={"information-requests-overview-standing-list"}>
            <li>
                <b>Continuing as issued</b>: the owner&apos;s plan lapsed or its trial ended. Parties can
                still answer, review, and decide, but new requests and follow-ups are unavailable.
            </li>
            <li>
                <b>Read only</b>: a draft that its owner cannot issue or change right now. It can still be cancelled,
                and <b>Must finish before the Exchange can end</b> can still be turned off, so it never keeps the
                Exchange from ending.
            </li>
            <li>
                <b>Changes paused</b>: the owner&apos;s account is suspended, so nothing on the request can
                change until it is restored. Managers can still remove access by revoking a party, an access link,
                an accepted fact, or (through the API) delegated authority.
            </li>
            <li>
                <b>Stopped</b>: the request was stopped and accepts no further answers or changes. What
                was recorded stays readable. Managers can still remove a party, revoke an access link or an
                accepted fact, pause or extend a clock, and cancel or replace the request, but cannot add a
                party, create or resend a link, or start or resume a clock.
            </li>
        </ul>
        <p id={"information-requests-overview-standing-reason-help"}>
            Only people who manage the request see the owner&apos;s reason. Other parties see a lapse as
            normal work, and see only that changes are paused or that the request was stopped.
        </p>

        <h3 id={"information-requests-overview-allowances-heading"}>Allowances</h3>
        <ul id={"information-requests-overview-allowances-list"}>
            <li>
                <b>Personal</b>: 25 open requests at a time, drafts included; 10 acting parties, 100
                evidence files, and 250 MiB of evidence per request; 5 GiB of evidence across your requests.
            </li>
            <li>
                <b>Business</b>: open requests are not capped; 100 acting parties, 200 evidence files, and
                500 MiB of evidence per request; 100 GiB of evidence across the organization&apos;s requests.
            </li>
        </ul>
        <p id={"information-requests-overview-allowances-help"}>
            Issuing a request sets aside its evidence allowance until it finishes; a finished request then
            counts only the evidence it stores. Issuing is refused when it would pass the total. An issued
            request keeps the allowances it was issued with, even when the plan later changes, and allows
            only the kinds of change it was issued with; a change it does not allow is refused. Typed
            answers follow those allowances when the request was issued with them, otherwise the
            current plan.
        </p>

        <h3 id={"information-requests-overview-fields-heading"}>Exchange Fields and request answers</h3>
        <p id={"information-requests-overview-fields-help"}>
            The Fields on an Exchange&apos;s <b>Details</b> tab describe the Exchange itself. They are
            never turned into a request, an answer, a submission, or a review decision, and creating,
            answering, submitting, or reviewing a request never changes them. A request&apos;s typed
            answers are recorded against the request.
        </p>

        <h3 id={"information-requests-overview-roles-heading"}>Parties</h3>
        <ul id={"information-requests-overview-roles-list"}>
            <li><b>Subject</b>: who or what the information is about; this identity does not answer or confirm.</li>
            <li><b>Contributor</b> and <b>Preparer</b>: answer the requested items.</li>
            <li><b>Attestor</b>: confirms the response where the Template asks for a confirmation.</li>
            <li><b>Reviewer</b>: reviews submissions in the stages the Template sets.</li>
            <li><b>Decision Maker</b>: manages the request; every request needs one before it is issued.</li>
        </ul>

        <h3 id={"information-requests-overview-lifecycle-heading"}>Lifecycle</h3>
        <p id={"information-requests-overview-lifecycle-help"}>
            A request starts as a draft, is issued to its parties, is in progress while they answer, and
            is closed once its submissions are complete and accepted. It can also be cancelled,
            superseded by a replacement, or expire when its due date policy says so. A finished request
            stays readable and its history is kept, and its owner still opens it with <b>View</b> on its management
            page. A request can be issued while its Exchange is still a draft: its parties can open and read it, but
            answers, files and submissions are accepted only once the Exchange is accepted, and the respond page says
            so until then.
        </p>

        <h3 id={"information-requests-overview-exchange-heading"}>When the parent Exchange stops</h3>
        <ul id={"information-requests-overview-exchange-list"}>
            <li>
                Rejecting or rescinding the Exchange cancels every Information Request on it that has not
                already finished, ends all respondent sessions, and stops respondent access. The recorded
                request history is kept, and each owner of the Exchange keeps read-only access to it, apart
                from revoking accepted facts, whether a person or an organization owns the request.
            </li>
            <li>
                Deleting the Exchange ends respondent access immediately and cancels every request on it that
                has not already finished, so it no longer counts as open. The record stays readable to the
                Exchange owner, who can no longer change anything on it, not even an accepted fact.
            </li>
            <li>
                Ending a completed Exchange keeps existing requests readable for the owner and the
                assigned parties, but no further responses can be saved and no new outcome or follow-up
                request can be recorded; the owner can still revoke accepted facts.
            </li>
            <li>
                A request command that is already in flight when the Exchange stops is refused rather than
                partly applied.
            </li>
            <li>
                Once the Exchange stops, a request&apos;s management page offers only the actions that are
                still allowed.
            </li>
        </ul>
    </>
);
