export const informationRequestTemplatePublishingArticle = (
    <>
        <h3 id={"information-request-templates-publish-heading"}>Saving and publishing</h3>
        <p id={"information-request-templates-publish-help"}>
            <b>Save draft</b> keeps your changes. <b>Before publishing</b> lists anything that would stop the draft from
            being
            published, each with <b>Show</b> to open the part it names, and <b>Publish</b> stays unavailable until
            nothing is listed.
            If the server still refuses the draft, its message explains the problem. When it names a section, item,
            group, condition,
            or review stage, <b>Show</b> opens that part. Publishing checks that the selected Schema is still available
            to this Template,
            supports Information Requests, and holds every Field the draft collects. If not, the Version stays a draft
            so you can
            choose an eligible Schema. Publishing freezes the Version; later changes need a new draft.
            If someone else saved or published the draft after you opened it, your save or publish is refused rather
            than overwriting their
            work: <b>Keep my changes</b> saves yours over the latest draft, and <b>Open latest draft</b> discards them.
            Going back to Templates
            or another Settings tab with unsaved changes asks first. The <b>Versions</b> tab starts drafts from, retires
            and copies published Versions; see
            Template Versions and copies. A platform Template is a starting point that requests never use directly: open
            it, select <b>Copy as a new Template</b>, and copy it into My Templates or the organization.
        </p>

        <h3 id={"information-request-conditional-answers-heading"}>Conditional answers</h3>
        <p id={"information-request-conditional-visibility-help"}>
            A conditional item is shown only while its condition is true. False or unknown conditions keep its answers
            out of the active response workspace, including saved Field values. Each repeated entry is evaluated
            separately: its condition reads that entry&apos;s own answers first, then its enclosing groups, then answers
            given once for the whole request. The answers of a hidden item count as missing for other conditions, so
            hiding one item can hide others. A Field an item asks for that nobody has answered yet counts as empty.
        </p>
        <p id={"information-request-conditional-clearing-help"}>
            Retaining keeps hidden answers and their files in the stored record and returns them intact when the
            condition holds
            again, or when an amendment stops asking the item only under that condition. Archiving copies a hidden
            answer, with the versions of its
            files, into stored history and clears it without asking; it is not restored. Archived answers are
            kept for forensic inspection and are not shown in the workspace or included in record exports. When
            the Template requires confirmed clearing, a save that would hide answers holding data or files
            is not made yet: the workspace lists those answers, including ones whose condition can no
            longer be decided, and you confirm each one and save again. If clearing them hides further
            answers, those are added to the list to confirm as well. A condition never clears or archives another
            party&apos;s answer
            or one a submission holds: it stays hidden and returns when the condition holds again. Clearing empties
            their current
            Fields and response data and takes out their files while preserving earlier Field revisions and
            file versions. Cleared and archived answers return as empty, without their earlier files, when
            the condition holds again, except an answer given in the same save, which is kept. Each hide,
            clear, archive, and return is recorded in the request&apos;s audit history.
        </p>

        <h3 id={"information-request-templates-progress-heading"}>Progress</h3>
        <p id={"information-request-templates-progress-help"}>
            A typed answer counts as complete only when its Field has a current answer, or when the respondent saves
            another answer
            the item allows, such as not applicable or unavailable, with its reason. Empty saves and cleared Fields stay
            drafts and are not counted as complete.
            Progress shows how many counted answers are complete out of all counted answers. Every
            required item counts, and an optional item counts once it is answered. A conditional item
            whose condition is not true and an unanswered optional item are left out of both numbers. An
            answer returned for correction counts as not complete until it is changed. A draft can clear a
            required Field; a required answer is checked when the part is submitted.
        </p>

        <h3 id={"information-request-configuration-bundles-heading"}>Configuration bundles</h3>
        <p id={"information-request-configuration-bundles-help"}>
            A configuration bundle is a versioned JSON document that describes your own setup in one
            place: which Template Versions it covers, validation policies for answers and files, your
            reason codes, role presets, due date policies, retention defaults, and optional connector
            contracts. A connector contract names the connector, its kind, its version, the result keys
            it returns, and an optional result age limit; its results are always reviewed. DocuHyphen
            ships no bundle of its own. A bundle names each Template Version by its Template key, its
            version number, and its content hash, which the <b>Versions</b> tab shows for a published
            Version. Integrations can check a bundle before
            using it with <code>POST /information-request-configuration-bundles/validations</code>, which
            lists every problem with the place it was found, including a Version you cannot see or whose
            content no longer matches the hash. A due date policy in a bundle is checked by the same rules as
            <b> Due date policies</b>: 0 minutes before escalating is valid, while overlapping working hours on one day,
            a repeated reminder, or working hours on a calendar-time policy are problems.
        </p>
    </>
);
