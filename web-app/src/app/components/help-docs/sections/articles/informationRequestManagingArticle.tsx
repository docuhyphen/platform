export const informationRequestManagingArticle = (
    <>
        <p id={"information-request-managing-intro"}>
            A request is created on its Exchange and managed on its own page, where you name its parties,
            issue it, share access links, set its due dates, and follow it up.
        </p>

        <h3 id={"information-request-managing-create-heading"}>Creating a request</h3>
        <p id={"information-request-managing-create-help"}>
            In the Exchange&apos;s Information Requests tab, select <b>New Information Request</b> and
            choose where it starts: a Template, a Blueprint, or a one-off request. A Template or
            Blueprint request uses the published Version it names. <b>Write a one-off request</b> opens
            the same editor as Templates; <b>Before creating</b> lists anything that would stop the
            request from being created. A refused one-off request opens the part the refusal names.
            Creating opens the new draft&apos;s page. Selecting <b>Create</b> again after a failure
            never creates a second request.
        </p>

        <h3 id={"information-request-managing-parties-heading"}>Parties</h3>
        <ul id={"information-request-managing-parties-list"}>
            <li>
                <b>Add party</b> names a role and its holder: a person by email or a group. A Subject is a
                known subject or a new one described by its kind and an identifying reference.
            </li>
            <li>
                A request needs a Decision Maker before it is issued; <b>Make me the Decision Maker</b>
                names you.
            </li>
            <li>
                Removing a party ends its access; what it already provided stays in the request history.
            </li>
        </ul>

        <h3 id={"information-request-managing-links-heading"}>Access links</h3>
        <p id={"information-request-managing-links-help"}>
            A party who responds without signing in uses an access link. After <b>Create link</b>, the
            link is shown once, with <b>Copy link</b>; the platform does not send it, so share it
            yourself. It cannot be shown again: <b>Resend link</b> creates a new link and the earlier one stops
            working, and <b>Revoke link</b> ends it. See Access links and respondent sessions.
        </p>

        <h3 id={"information-request-managing-issue-heading"}>Previewing and issuing</h3>
        <p id={"information-request-managing-issue-help"}>
            <b>Preview as recipient</b> shows the request the way its parties will see it. <b>Issue</b>
            opens it to its parties. If the request changed while you were working, the page shows the
            latest details and asks you to try again instead of overwriting them.
        </p>

        <h3 id={"information-request-managing-clocks-heading"}>Due dates</h3>
        <p id={"information-request-managing-clocks-help"}>
            <b>Start a clock</b> picks a due date policy, an urgency, and a key for the clock, then
            <b> Start clock</b>. A running clock can be paused, resumed, or extended with a reason, and
            each change is kept in the clock&apos;s history. Due date policies are set up in Information
            Request operations.
        </p>

        <h3 id={"information-request-managing-follow-ups-heading"}>Follow-up requests</h3>
        <p id={"information-request-managing-follow-ups-help"}>
            Once issued, a request can repeat on a schedule: set how often and when the first one is
            due, then <b>Create the next request</b> when it is time. <b>Request a supplement</b> asks
            for more on a submitted response with a reason. Earlier requests are listed with their
            follow-ups.
        </p>

        <h3 id={"information-request-managing-end-heading"}>Cancelling and replacing</h3>
        <p id={"information-request-managing-end-help"}>
            <b>Cancel request</b> ends a request that is not finished, with a reason; respondents can no
            longer answer it and its history is kept. <b>Supersede</b> names another request on the same
            Exchange that replaces it; the replaced request shows as superseded and keeps its history.
        </p>
    </>
);
