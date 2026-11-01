export const informationRequestManagingArticle = (
    <>
        <p id={"information-request-managing-intro"}>
            A request is created on its Exchange and managed on its own page, where you name its parties, issue it,
            share access links, set its due dates, and follow
            it up. When the request cannot be changed, its page says why and hides those controls. A failed read
            offers <b id={"information-request-managing-article-b-1"}>Try again</b>. A temporary reread failure
            keeps the current page and a newly created link; losing access hides them. A busy editor ignores Escape and
            outside clicks until the attempt finishes, and
            a refused change keeps its dialog open with what you entered. <b id={"information-request-managing-article-b-2"}>Refresh</b>, and issuing, cancelling or
            replacing the request, read every panel again.
        </p>
        <h3 id={"information-request-managing-create-heading"}>Creating a request</h3>
        <p id={"information-request-managing-create-help"}>
            In the Exchange&apos;s Information Requests tab, select <b id={"information-request-managing-article-b-3"}>New Information Request</b> and choose where it
            starts: a
            Template, a Blueprint, or a one-off request. A Template or Blueprint request uses the published Version it
            names. <b id={"information-request-managing-article-b-4"}>Write
            a one-off request</b> opens the same editor as Templates; <b id={"information-request-managing-article-b-5"}>Before creating</b> lists anything that would
            stop the
            request from being created. A refused one-off request opens the part the refusal names. A one-off
            configuration
            stays private to its request. It cannot be selected for another request or Blueprint, or opened in the
            reusable
            Template editor in Settings. Creating opens the new draft&apos;s page. Selecting <b id={"information-request-managing-article-b-6"}>Create</b> again after a
            failure
            never creates a second request. <b id={"information-request-managing-article-b-7"}>Must finish before the Exchange can end</b> is selected by default; clear
            it to
            let the Exchange end without waiting for the request (see Ending the Exchange in Issuing and following up a
            request). To start from a
            platform
            Template, copy a platform Template first in Settings. A request on a personally owned Exchange answers to
            that
            person&apos;s plan, whichever organization is selected. Templates, Blueprints, and Schemas are offered only
            while
            you work in the workspace that owns the Exchange.
        </p>
        <p id={"information-request-managing-blueprint-start-help"}>
            Starting an Exchange from a Blueprint that names a Template Version creates that request for you as a draft,
            with
            the person who started the Exchange as its Decision Maker, so it is already listed in the tab. If it cannot
            be
            created, the Exchange does not start either. Use <b id={"information-request-managing-article-b-8"}>New Information Request</b> for any further request. A
            request
            keeps the Version it was created from. Pointing the Blueprint at another Version, or retiring the Version,
            changes
            only requests created afterwards; only an amendment of the request itself moves it to another Version. No
            screen
            offers amendments yet. An amendment moves the request to a later published Template Version or, for a
            one-off
            request, to a revised configuration, and respondents see the result under <b id={"information-request-managing-article-b-9"}>What changed in this
            request</b>.
            The Blueprint&apos;s participants become the request&apos;s parties (a Reviewer stays Reviewer, Signer
            becomes
            Attestor, Editor becomes Preparer, Owner becomes Decision Maker, and any other role becomes Contributor),
            but only
            when the sharing policy permits each one and each Reviewer is a person with an account or a group; otherwise
            the request is not created. Starting an Exchange makes parties only of the participants still on the
            Exchange, never of you. Its documents become placeholders,
            and its field values become starting values, not submitted answers. People who manage the request see those
            placeholders on its page under <b id={"information-request-managing-article-b-10"}>Expected documents</b>: each title, whether it is required or optional,
            any file
            type it is limited to, and the Document Library document it came from. The list is kept as it was when the
            request
            was created, so later changes to the Blueprint or the library do not change it.
        </p>

        <h3 id={"information-request-managing-parties-heading"}>Parties</h3>
        <ul id={"information-request-managing-parties-list"}>
            <li id={"information-request-managing-article-li-1"}>
                <b id={"information-request-managing-article-b-11"}>Add party</b> names a role and its holder: a person by email, a person with an account, a group, or a
                trusted recipient.
                A person with an account is chosen from the members of the organization that owns the request or, for a
                personal request,
                from your recent contacts who have an account; while another workspace is selected, only an email
                address or a trusted
                recipient is offered until you switch. A Reviewer must be a person with an account or a group. A trusted
                recipient is a
                verified member or published group of a trusted organization already accepted on the Exchange, and is
                chosen from the
                trusted recipients rather than added by email. A Subject names a new identity by its kind; naming it by
                a reference
                or choosing a known one needs privacy management. In <b id={"information-request-managing-article-b-12"}>Known subject</b>, <b id={"information-request-managing-article-b-13"}>A new subject</b> returns
                to a new identity.
                Acting roles answer and confirm. A holder has one party in each role: giving the same role to the same
                holder again
                is refused until that party is removed, and other roles are allowed. Giving someone outside the
                request&apos;s owner, or a group with such a member, a role that answers a Field not marked Public is
                refused.
            </li>
            <li id={"information-request-managing-article-li-2"}>
                <b id={"information-request-managing-article-b-14"}>Reassign</b> hands a party&apos;s role to a person with an account, a group, or a trusted
                recipient. The previous holder loses its access, including access links and sessions; what it already
                provided stays in the request history. A Subject cannot be reassigned.
            </li>
            <li id={"information-request-managing-article-li-3"}>
                On an Exchange that requires sign-in, a person added by email uses their account to access the request,
                and the email must belong to someone who already has access to the Exchange; any other address is
                refused the same way, whether or not it has an account. Other Exchanges can use an access link for an
                email-only participant. Requiring sign-in later ends those links: an email-only participant who never
                linked an account responds again only once you reassign their party to a person with an account or a
                trusted
                recipient, or remove the party and add them again by the email of their account.
            </li>
            <li id={"information-request-managing-article-li-4"}>
                An organization&apos;s request follows its sharing settings, as its Exchanges do: a person from
                another organization, even one added by the email address of their account, needs a trusted organization
                when one is required, a person added by email needs sharing with individuals to be allowed, and a group
                must be active and, when it belongs to another organization, published by a trusted one.
            </li>
            <li id={"information-request-managing-article-li-5"}>
                A request needs a Decision Maker before it is issued; <b id={"information-request-managing-article-b-15"}>Make me the Decision Maker</b> names you.
                Adding or reassigning a party needs the owner&apos;s Information Requests access: the current plan
                while the request is a draft, and the request&apos;s grant once it is issued.
            </li>
            <li id={"information-request-managing-article-li-6"}>
                Until a request finishes, removing a party is always possible and ends its access, including an account
                its participant linked from an access link; what it already provided stays in the request history.
            </li>
            <li id={"information-request-managing-article-li-7"}>
                A recipient from a trusted organization cannot be added while that trust relationship is suspended. A
                party already added keeps answering what was issued, and its row shows <b id={"information-request-managing-article-b-16"}>Trusted relationship
                suspended</b>.
            </li>
        </ul>
    </>
);
