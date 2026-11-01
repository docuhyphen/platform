
export const managingSequencesArticle = (
    <>
        <p id={"managing-sequences-article-p-1"}>
            A sequence is a named counter stored at the org level. Reference it with the
            syntax <code id={"managing-sequences-article-code-1"}>{"{{SEQ:KEY}}"}</code> in the Exchange name field. Each time an
            Exchange is created with that token in the name, the counter increments by 1
            and the formatted value is substituted.
        </p>

        <h3 id={"managing-sequences-article-h3-1"}>Who can manage sequences</h3>
        <p id={"managing-sequences-article-p-2"}>
            Only Organization Admins can create, edit, reset, and delete sequences.
            The global App Admin role does not grant access to organization sequences.
            An App Admin must also be an Organization Admin in the active organization.
            Regular members can use <code id={"managing-sequences-article-code-2"}>{"{{SEQ:KEY}}"}</code> tokens in blueprints and
            Exchanges but cannot create or configure sequence definitions.
        </p>
        <p id={"managing-sequences-article-p-3"}>
            Sequence changes can require a 6-digit verification code before the create,
            update, reset, or delete action is applied.
        </p>

        <h3 id={"managing-sequences-article-h3-2"}>How to create a sequence</h3>
        <ol id={"managing-sequences-article-ol-1"}>
            <li id={"managing-sequences-article-li-1"}>Open Settings and go to the <b id={"managing-sequences-article-b-1"}>Sequences</b> tab.</li>
            <li id={"managing-sequences-article-li-2"}>Click <b id={"managing-sequences-article-b-2"}>New Sequence</b> (visible to admins only).</li>
            <li id={"managing-sequences-article-li-3"}>
                Fill in the fields:
                <ul id={"managing-sequences-article-ul-1"}>
                    <li id={"managing-sequences-article-li-4"}><b id={"managing-sequences-article-b-3"}>Name</b> - a display label shown in the sequence list and token picker.</li>
                    <li id={"managing-sequences-article-li-5"}><b id={"managing-sequences-article-b-4"}>Key</b> - the identifier used in <code id={"managing-sequences-article-code-3"}>{"{{SEQ:KEY}}"}</code> (uppercase letters, digits, and underscores).</li>
                    <li id={"managing-sequences-article-li-6"}><b id={"managing-sequences-article-b-5"}>Pad Width</b> - minimum digit width. Set to 0 for no padding. Set to 3 to format value 7 as "007".</li>
                    <li id={"managing-sequences-article-li-7"}><b id={"managing-sequences-article-b-6"}>Prefix</b> - optional text prepended to every formatted value (e.g. "INV-").</li>
                    <li id={"managing-sequences-article-li-8"}><b id={"managing-sequences-article-b-7"}>Suffix</b> - optional text appended to every formatted value.</li>
                    <li id={"managing-sequences-article-li-9"}><b id={"managing-sequences-article-b-8"}>Reset Period</b> - when the counter resets automatically: Never, Yearly, or Monthly.</li>
                </ul>
            </li>
            <li id={"managing-sequences-article-li-10"}>The live preview shows what the next formatted value will look like.</li>
            <li id={"managing-sequences-article-li-11"}>Save.</li>
        </ol>

        <h3 id={"managing-sequences-article-h3-3"}>Editing a sequence</h3>
        <p id={"managing-sequences-article-p-4"}>
            Open the three-dot menu on a sequence row and click <b id={"managing-sequences-article-b-9"}>Edit</b>. The key
            cannot be changed after creation. All other fields (name, pad width, prefix,
            suffix, reset period) are editable.
        </p>

        <h3 id={"managing-sequences-article-h3-4"}>Reset Counter</h3>
        <p id={"managing-sequences-article-p-5"}>
            Click <b id={"managing-sequences-article-b-10"}>Reset Counter</b> in the three-dot menu to set the counter back to
            0. The next Exchange that uses the sequence will receive value 1 (or the
            formatted equivalent). Gaps in the sequence before the reset are normal and
            expected.
        </p>

        <h3 id={"managing-sequences-article-h3-5"}>Deleting a sequence</h3>
        <p id={"managing-sequences-article-p-6"}>
            Deleting a sequence soft-deletes the definition. Any blueprint or Exchange
            field that still contains <code id={"managing-sequences-article-code-4"}>{"{{SEQ:KEY}}"}</code> for a deleted sequence
            will leave the token unresolved after deletion.
        </p>

        <h3 id={"managing-sequences-article-h3-6"}>A note on gaps</h3>
        <p id={"managing-sequences-article-p-7"}>
            If Exchange creation fails after the counter has already been incremented,
            the counter is not rolled back. Gaps in the sequence are expected and
            acceptable, consistent with how invoice numbers work. Do not rely on
            sequential numbering being gap-free.
        </p>
    </>
);
