export const informationRequestTemplatesArticle = (
    <>
        <p id={"information-request-templates-intro"}>
            An Information Request Template is a reusable request: its sections, the items each section
            asks for, repeated groups, conditions, review stages, and settings. Publishing a Template
            creates a Version that requests are made from; a published Version never changes.
        </p>

        <h3 id={"information-request-templates-list-heading"}>Opening the Template list</h3>
        <p id={"information-request-templates-list-help"}>
            Open <b>Settings</b>, then select <b>Content - Information Requests</b>. Use
            <b> My Templates</b> for personally owned Templates, <b>Organization</b> for the active
            organization, and <b>Platform</b> for read-only platform Templates. Organization Owners and
            Administrators manage the organization&apos;s Templates. Select <b>New Template</b> to create
            a draft in the chosen scope.
        </p>
        <p id={"information-request-templates-availability-help"}>
            The tab appears only when your plan or your active organization includes Information
            Requests. Without it, the tab is not shown, and you can still respond to and review
            Information Requests shared with you. You author personal Templates on the Personal plan, or
            while an active organization whose plan includes Information Requests is selected. When new
            requests cannot be created, Templates stay readable but <b>New Template</b> is not offered.
        </p>

        <h3 id={"information-request-templates-editor-heading"}>The editor</h3>
        <p id={"information-request-templates-editor-help"}>
            A draft opens in an editor with the tabs Sections, Groups, Conditions, Review, and Settings,
            and a Versions tab for what has been published.
        </p>
        <ul id={"information-request-templates-editor-list"}>
            <li>
                <b>Sections</b>: <b>Add section</b>, then <b>Add requirement</b> in a section. An item is a
                <b> Typed answer</b> collected in a Field of the Template&apos;s request schema, a
                <b> Document</b> with its evidence policy, or a <b>Confirmation</b>. Each item has a key,
                a prompt, help text, whether it is required, optional, or asked only when a condition is
                true, who answers it, how they may answer, which other answers they may give instead of a
                value, and whether it is reviewed.
            </li>
            <li>
                <b>Groups</b>: a group repeats its items once per entry, with a minimum and maximum number
                of entries, and can be nested in another group.
            </li>
            <li>
                <b>Conditions</b>: a condition tests earlier answers and says what happens to hidden
                answers when it turns false. The editor refuses unknown keys, unsupported tests, and
                conditions that depend on each other in a loop.
            </li>
            <li>
                <b>Review</b>: review stages, their order, how each stage decides an item, and who may not
                review, such as the responding parties or reviewers of an earlier stage.
            </li>
            <li>
                <b>Settings</b>: the request schema, whether the request is submitted whole or in parts,
                and the purpose under which accepted answers may be reused. A Template can use a platform
                Schema, and an organization Template can also use the organization&apos;s Schemas; personal
                Fields and Schemas cannot be authored yet.
            </li>
        </ul>

        <h3 id={"information-request-templates-publish-heading"}>Saving and publishing</h3>
        <p id={"information-request-templates-publish-help"}>
            <b>Save draft</b> keeps your changes. <b>Before publishing</b> lists anything that would stop
            the draft from being published, each with <b>Show</b> to open the part it names, and
            <b> Publish</b> stays unavailable until nothing is listed. If the server still refuses the
            draft, its message names the section, item, group, or review stage, and <b>Show</b> opens
            it. Publishing freezes the Version; later changes need a new draft.
            <b> Copy as a new Template</b> starts another Template from this one, and <b>Retire</b>
            stops a Version from being used for new requests while requests made from it continue. A
            platform Template is a starting point that requests never use directly: open it, select
            <b> Copy as a new Template</b>, and copy it into My Templates or the organization.
        </p>

        <h3 id={"information-request-conditional-answers-heading"}>Conditional answers</h3>
        <p id={"information-request-conditional-visibility-help"}>
            A conditional item is shown only while its condition is true. False or unknown conditions
            keep its answers out of the active response workspace, including saved Field values. Each
            repeated entry is evaluated separately: its condition reads that entry&apos;s own answers
            first, then its enclosing groups, then answers given once for the whole request.
        </p>
        <p id={"information-request-conditional-clearing-help"}>
            Retention policies keep hidden answers in the stored record. When the Template requires
            confirmed clearing, confirm the affected items before saving. Clearing empties their current
            Fields and response data while preserving earlier Field revisions. When a condition becomes
            true again, retained or archived answers return to the active workspace. Cleared answers
            return as empty and do not restore cleared values.
        </p>

        <h3 id={"information-request-templates-progress-heading"}>Progress</h3>
        <p id={"information-request-templates-progress-help"}>
            A typed answer counts as complete only when its Field has a current answer, or when the
            respondent saves another answer the item allows, such as not applicable or unavailable, with
            its reason. Empty saves and cleared Fields stay drafts and are not counted as complete.
        </p>

        <h3 id={"information-request-configuration-bundles-heading"}>Configuration bundles</h3>
        <p id={"information-request-configuration-bundles-help"}>
            A configuration bundle is a versioned JSON document that describes your own setup in one
            place: which Template Versions it covers, validation policies for answers and files, your
            reason codes, role presets, due date policies, retention defaults, and optional connector
            contracts. A connector contract names the connector, its kind, its version, the result keys
            it returns, and an optional result age limit; its results are always reviewed. DocuHyphen
            ships no bundle of its own. Integrations can check a bundle before
            using it with <code>POST /information-request-configuration-bundles/validations</code>, which
            lists every problem with the place it was found.
        </p>
    </>
);
