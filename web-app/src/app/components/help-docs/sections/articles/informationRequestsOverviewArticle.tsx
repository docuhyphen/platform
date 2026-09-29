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
            you are allowed to see, each with its title, status, due time, how many required answers are
            complete, and your roles. Its button names your next action: <b>Finish setup</b>,
            <b> Respond</b>, <b>Review</b>, <b>Manage</b>, or <b>View</b>. The tab shows nothing about
            requests you cannot see. It appears when the Exchange has a request for you or when you may
            create one, and <b>New Information Request</b> appears only when you may create one.
        </p>

        <h3 id={"information-requests-overview-plans-heading"}>Plans</h3>
        <p id={"information-requests-overview-plans-help"}>
            The Personal and Business plans include Information Requests; the Free plan does not. The
            request&apos;s owner provides the plan: parties who respond or review work on the requests
            assigned to them whatever their own plan. When the owner&apos;s plan lapses, requests already
            issued stay visible and can still be answered, but new requests cannot be created.
        </p>

        <h3 id={"information-requests-overview-roles-heading"}>Parties</h3>
        <ul id={"information-requests-overview-roles-list"}>
            <li><b>Subject</b>: who or what the information is about.</li>
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
            stays readable and its history is kept.
        </p>

        <h3 id={"information-requests-overview-exchange-heading"}>When the parent Exchange stops</h3>
        <ul id={"information-requests-overview-exchange-list"}>
            <li>
                Rejecting or rescinding the Exchange cancels every Information Request on it that has not
                already finished, ends all respondent sessions, and stops respondent access. The recorded
                request history is kept.
            </li>
            <li>
                Deleting the Exchange ends respondent access immediately and leaves each request in the
                state it had reached, so the record stays readable to the Exchange owner.
            </li>
            <li>
                Ending a completed Exchange keeps existing requests readable for the owner and the
                assigned parties, but no further responses can be saved.
            </li>
            <li>
                A request command that is already in flight when the Exchange stops is refused rather than
                partly applied.
            </li>
        </ul>
    </>
);
