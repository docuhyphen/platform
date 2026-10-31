export const usingBlueprintsArticle = (
    <>
        <p id={"using-blueprints-intro-paragraph"}>
            The blueprint picker pre-fills a new Exchange with a saved configuration
            so you can go straight to choosing a recipient rather than rebuilding
            the Exchange from scratch. It is available on Personal and Business plans.
            Free users start the Exchange directly because <b>From Blueprint</b> is hidden.
        </p>

        <h3 id={"using-blueprints-opening-the-picker-heading"}>Opening the picker</h3>
        <ol id={"using-blueprints-opening-the-picker-list"}>
            <li>Click <b>Start Exchange</b> from the main navigation or Exchange list.</li>
            <li>In the start menu, click <b>From Blueprint</b>.</li>
            <li>The blueprint picker opens inside the Exchange initiation dialog.</li>
        </ol>

        <h3 id={"using-blueprints-choosing-a-blueprint-heading"}>Choosing a blueprint</h3>
        <p id={"using-blueprints-choosing-a-blueprint-paragraph"}>
            The picker always includes these tabs:
        </p>
        <ul id={"using-blueprints-choosing-a-blueprint-list"}>
            <li><b>My Blueprints</b> - your personal saved blueprints.</li>
            <li><b>Platform</b> - ready-made blueprints provided by DocuHyphen.</li>
        </ul>
        <p id={"using-blueprints-choosing-a-blueprint-paragraph-2"}>
            If you are an active member of a registered organization, you also see an
            <b> Organization</b> tab with blueprints published by your organization's admins.
        </p>
        <p id={"using-blueprints-choosing-a-blueprint-paragraph-3"}>
            Click <b>Use Blueprint</b> on the card you want. The picker closes and
            the Exchange form is pre-filled with all the saved settings. A card marked
            <b> Starts an Information Request</b> belongs to a blueprint that also creates
            an Information Request when the Exchange starts.
        </p>

        <h3 id={"using-blueprints-what-gets-pre-filled-heading"}>What gets pre-filled</h3>
        <ul id={"using-blueprints-what-gets-pre-filled-list"}>
            <li>Exchange name, description, and initial message.</li>
            <li>Document slots - titles, upload-type restrictions, and required flags.</li>
            <li>Permission toggles - document addition, deletion, download, update, and upload.</li>
            <li>Recipient sign-in requirement.</li>
            <li>
                Internal participants: the members of your organization the blueprint names are selected as
                participants.
                Anyone no longer in the organization, and any group, is left out.
            </li>
            <li>
                The business schema and its default field values, when the blueprint defines one.
                The Business Fields step is pre-selected and seeded so you only adjust what changed.
            </li>
        </ul>
        <p id={"using-blueprints-what-gets-pre-filled-paragraph"}>
            You can edit any pre-filled value before sending. The blueprint is a
            starting point, not a locked template. When an organization blueprint is
            locked (its author did not allow edits on start), the pre-filled field
            values are shown read-only.
        </p>

        <h3 id={"using-blueprints-information-request-heading"}>Blueprints that start an Information Request</h3>
        <ul id={"using-blueprints-information-request-list"}>
            <li>
                Starting the Exchange also creates a draft Information Request from the Template
                Version the blueprint names, with you as its Decision Maker. The success summary
                names it and offers <b>Manage Information Request</b>.
            </li>
            <li>
                Exchange documents are optional because the request collects the documents, and the
                Documents tab says so. A locked blueprint with no document slots can still start.
            </li>
            <li>
                If the request cannot be created, the Exchange does not start either and the message
                says why: for example, the Version was retired, the plan has no Information Requests or
                no request capacity left, or the blueprint&apos;s Template belongs to a different
                workspace. In that case choose an Organization Blueprint or switch workspace.
            </li>
        </ul>

        <h3 id={"using-blueprints-after-selecting-a-blueprint-heading"}>After selecting a blueprint</h3>
        <p id={"using-blueprints-after-selecting-a-blueprint-paragraph"}>
            If the blueprint contains org or personal variable tokens, a variable
            override panel appears. Review or edit each value before proceeding to
            the Recipients tab.
        </p>
        <p id={"using-blueprints-after-selecting-a-blueprint-paragraph-2"}>
            The dialog moves to the <b>Recipients</b> tab automatically once you
            have reviewed the pre-filled details. Search for a recipient by name or email,
            select a personal or organization group, or use an eligible <b>Trusted Organization</b> to verify a known
            member by exact email or choose a published group. You can enter a complete email address for
            a new recipient who is not in your contacts. Plans that include additional participants
            show the multi-person picker; Free keeps one primary recipient.
        </p>

        <h3 id={"using-blueprints-saving-the-current-form-heading"}>Saving the current form as a blueprint</h3>
        <p id={"using-blueprints-saving-the-current-form-paragraph"}>
            If you have filled out the Exchange form and want to save the
            configuration for future reuse, click <b>Save as Blueprint</b> in the
            dialog title bar. You will be prompted to give the blueprint a name and
            optional summary and tags before saving. If you selected a business
            schema and entered field values, they are captured as the blueprint's
            schema and default values too.
        </p>
    </>
);
