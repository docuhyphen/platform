
export const blueprintOverviewArticle = (
    <>
        <p id={"blueprint-overview-intro-paragraph"}>
            Blueprints are saved Exchange configurations. Instead of filling in
            documents, permissions, recipient settings, and participants from scratch
            every time, you save a blueprint once and reuse it whenever you start a
            new Exchange.
        </p>

        <h3 id={"blueprint-overview-what-a-blueprint-stores-heading"}>What a blueprint stores</h3>
        <ul id={"blueprint-overview-what-a-blueprint-stores-list"}>
            <li id={"blueprint-overview-article-li-1"}><b id={"blueprint-overview-article-b-1"}>Exchange name and description</b> - a pre-filled name and description for the Exchange.</li>
            <li id={"blueprint-overview-article-li-2"}><b id={"blueprint-overview-article-b-2"}>Initial message</b> - the message shown to the recipient when the Exchange is sent.</li>
            <li id={"blueprint-overview-article-li-3"}><b id={"blueprint-overview-article-b-3"}>Documents</b> - a list of document slots, each with a title, an optional upload-type restriction, and an optional required flag.</li>
            <li id={"blueprint-overview-article-li-4"}><b id={"blueprint-overview-article-b-4"}>Permissions</b> - the full set of permission toggles: allow document addition, deletion, download, update, and upload.</li>
            <li id={"blueprint-overview-article-li-5"}><b id={"blueprint-overview-article-b-5"}>Recipient sign-in requirement</b> - whether recipients must sign in to access the Exchange.</li>
            <li id={"blueprint-overview-article-li-6"}><b id={"blueprint-overview-article-b-6"}>Participants</b> - internal participants added to the Exchange, each with a role.</li>
            <li id={"blueprint-overview-article-li-7"}>
                <b id={"blueprint-overview-article-b-7"}>Information Request</b> - optionally, a published Template Version. Each Exchange started
                from the blueprint then creates a draft Information Request from it, and the Exchange&apos;s documents
                become optional.
            </li>
        </ul>
        <p id={"blueprint-overview-what-a-blueprint-stores-paragraph"}>
            Exchange name, description, and document titles can include variable tokens
            (<code id={"blueprint-overview-article-code-1"}>{"{{TOKEN}}"}</code>) that are resolved at Exchange creation time. See{" "}
            <a id={"blueprint-overview-what-a-blueprint-stores-link"}
               href="#"
               data-help-article="using-variable-tokens">
                Using variable tokens
            </a>{" "}
            for details.
        </p>
        <p id={"blueprint-overview-what-a-blueprint-stores-paragraph-2"}>
            Blueprints do <b id={"blueprint-overview-article-b-8"}>not</b> store the recipient's identity (email, name, or
            account). You always choose who to send the Exchange to at initiation time.
        </p>

        <h3 id={"blueprint-overview-three-blueprint-scopes-heading"}>Three blueprint scopes</h3>
        <ul id={"blueprint-overview-three-blueprint-scopes-list"}>
            <li id={"blueprint-overview-article-li-8"}>
                <b id={"blueprint-overview-article-b-9"}>My Blueprints (Personal)</b> - visible only to you. Personal and
                Business plans can create them for recurring Exchange configurations.
            </li>
            <li id={"blueprint-overview-article-li-9"}>
                <b id={"blueprint-overview-article-b-10"}>Organization</b> - shared across your organization. Only
                Organization Admins can create and publish them. A blueprint must be
                both Active and Published before it appears in the picker for other
                members.
            </li>
            <li id={"blueprint-overview-article-li-10"}>
                <b id={"blueprint-overview-article-b-11"}>Platform</b> - provided by DocuHyphen. Available when the current
                Personal or Business plan includes Blueprints. Only App Admins can create platform
                blueprints. An entitled user can clone a platform blueprint into their own
                personal blueprints.
            </li>
        </ul>

        <h3 id={"blueprint-overview-where-blueprints-appear-heading"}>Where blueprints appear</h3>
        <p id={"blueprint-overview-where-blueprints-appear-paragraph"}>
            Free includes the basic Exchange flow without Blueprints, so <b id={"blueprint-overview-article-b-12"}>From Blueprint</b>{" "}
            and the Blueprint settings entry are not shown while Free enforcement is active.
        </p>
        <ul id={"blueprint-overview-where-blueprints-appear-list"}>
            <li id={"blueprint-overview-article-li-11"}>
                <b id={"blueprint-overview-article-b-13"}>Exchange initiation picker</b> - when starting a new Exchange,
                click <b id={"blueprint-overview-article-b-14"}>From Blueprint</b> in the start menu to open the three-tab
                picker and select a blueprint.
            </li>
            <li id={"blueprint-overview-article-li-12"}>
                <b id={"blueprint-overview-article-b-15"}>Settings - Blueprints</b> - manage personal Blueprints, manage
                organization Blueprints when authorized, and browse or clone
                platform Blueprints.
            </li>
            <li id={"blueprint-overview-article-li-13"}>
                <b id={"blueprint-overview-article-b-16"}>Platform Administration - Platform Content</b> - App Admins
                create and manage the original platform Blueprints without loading
                organization or personal content.
            </li>
        </ul>

        <h3 id={"blueprint-overview-learn-more-heading"}>Learn more</h3>
        <ul id={"blueprint-overview-learn-more-list"}>
            <li id={"blueprint-overview-article-li-14"}>
                <a id={"blueprint-overview-learn-more-link"}
                   href="#"
                   data-help-article="using-blueprints"><b id={"blueprint-overview-article-b-17"}>Starting an Exchange from a blueprint</b></a> - how to use the picker.
            </li>
            <li id={"blueprint-overview-article-li-15"}>
                <a id={"blueprint-overview-learn-more-link-2"}
                   href="#"
                   data-help-article="managing-blueprints"><b id={"blueprint-overview-article-b-18"}>Creating and managing blueprints</b></a> - build and edit your personal blueprints.
            </li>
            <li id={"blueprint-overview-article-li-16"}>
                <a id={"blueprint-overview-learn-more-link-3"}
                   href="#"
                   data-help-article="org-blueprints"><b id={"blueprint-overview-article-b-19"}>Organization blueprints</b></a> - sharing blueprints across your team.
            </li>
            <li id={"blueprint-overview-article-li-17"}>
                <a id={"blueprint-overview-learn-more-link-4"}
                   href="#"
                   data-help-article="blueprint-platform"><b id={"blueprint-overview-article-b-20"}>Platform blueprints</b></a> - ready-made blueprints you can clone.
            </li>
        </ul>
    </>
);
