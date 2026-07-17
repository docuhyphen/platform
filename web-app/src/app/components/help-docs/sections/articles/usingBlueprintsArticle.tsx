export const usingBlueprintsArticle = (
    <>
        <p>
            The blueprint picker pre-fills a new Exchange with a saved configuration
            so you can go straight to choosing a recipient rather than rebuilding
            the Exchange from scratch.
        </p>

        <h3>Opening the picker</h3>
        <ol>
            <li>Click <b>Start Exchange</b> from the main navigation or Exchange list.</li>
            <li>In the start menu, click <b>From Blueprint</b>.</li>
            <li>The blueprint picker opens inside the exchange initiation dialog.</li>
        </ol>

        <h3>Choosing a blueprint</h3>
        <p>
            The picker always includes these tabs:
        </p>
        <ul>
            <li><b>My Blueprints</b> - your personal saved blueprints.</li>
            <li><b>Platform</b> - ready-made blueprints provided by DocuHyphen.</li>
        </ul>
        <p>
            If you are an active member of a registered organization, you also see an
            <b> Organization</b> tab with blueprints published by your organization's admins.
        </p>
        <p>
            Click <b>Use Blueprint</b> on the card you want. The picker closes and
            the Exchange form is pre-filled with all the saved settings.
        </p>

        <h3>What gets pre-filled</h3>
        <ul>
            <li>Exchange name, description, and initial message.</li>
            <li>Document slots - titles, upload-type restrictions, and required flags.</li>
            <li>Permission toggles - document addition, deletion, download, update, and upload.</li>
            <li>Recipient sign-in requirement.</li>
            <li>Internal participants with their assigned roles.</li>
            <li>
                The business schema and its default field values, when the blueprint defines one.
                The Business Fields step is pre-selected and seeded so you only adjust what changed.
            </li>
        </ul>
        <p>
            You can edit any pre-filled value before sending. The blueprint is a
            starting point, not a locked template. When an organization blueprint is
            locked (its author did not allow edits on start), the pre-filled field
            values are shown read-only.
        </p>

        <h3>After selecting a blueprint</h3>
        <p>
            If the blueprint contains org or personal variable tokens, a variable
            override panel appears. Review or edit each value before proceeding to
            the Recipients tab.
        </p>
        <p>
            The dialog moves to the <b>Recipients</b> tab automatically once you
            have reviewed the pre-filled details. Search for a recipient by name or email,
            select a personal or organization group, or use an eligible <b>Trusted Organization</b>
            to verify a known member by exact email or choose a published group. You can enter a complete email address for
            a new recipient who is not in your contacts. Add any internal participants with
            the multi-person picker, then initiate the Exchange when ready.
        </p>

        <h3>Saving the current form as a blueprint</h3>
        <p>
            If you have filled out the Exchange form and want to save the
            configuration for future reuse, click <b>Save as Blueprint</b> in the
            dialog title bar. You will be prompted to give the blueprint a name and
            optional summary and tags before saving. If you selected a business
            schema and entered field values, they are captured as the blueprint's
            schema and default values too.
        </p>
    </>
);
