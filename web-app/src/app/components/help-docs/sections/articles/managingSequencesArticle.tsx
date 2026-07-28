import React from "react";

export const managingSequencesArticle = (
    <>
        <p>
            A sequence is a named counter stored at the org level. Reference it with the
            syntax <code>{"{{SEQ:KEY}}"}</code> in the Exchange name field. Each time an
            Exchange is created with that token in the name, the counter increments by 1
            and the formatted value is substituted.
        </p>

        <h3>Who can manage sequences</h3>
        <p>
            Only Organization Admins can create, edit, reset, and delete sequences.
            The global App Admin role does not grant access to organization sequences.
            An App Admin must also be an Organization Admin in the active organization.
            Regular members can use <code>{"{{SEQ:KEY}}"}</code> tokens in blueprints and
            Exchanges but cannot create or configure sequence definitions.
        </p>
        <p>
            Sequence changes can require a 6-digit verification code before the create,
            update, reset, or delete action is applied.
        </p>

        <h3>How to create a sequence</h3>
        <ol>
            <li>Open Settings and go to the <b>Sequences</b> tab.</li>
            <li>Click <b>New Sequence</b> (visible to admins only).</li>
            <li>
                Fill in the fields:
                <ul>
                    <li><b>Name</b> - a display label shown in the sequence list and token picker.</li>
                    <li><b>Key</b> - the identifier used in <code>{"{{SEQ:KEY}}"}</code> (uppercase letters, digits, and underscores).</li>
                    <li><b>Pad Width</b> - minimum digit width. Set to 0 for no padding. Set to 3 to format value 7 as "007".</li>
                    <li><b>Prefix</b> - optional text prepended to every formatted value (e.g. "INV-").</li>
                    <li><b>Suffix</b> - optional text appended to every formatted value.</li>
                    <li><b>Reset Period</b> - when the counter resets automatically: Never, Yearly, or Monthly.</li>
                </ul>
            </li>
            <li>The live preview shows what the next formatted value will look like.</li>
            <li>Save.</li>
        </ol>

        <h3>Editing a sequence</h3>
        <p>
            Open the three-dot menu on a sequence row and click <b>Edit</b>. The key
            cannot be changed after creation. All other fields (name, pad width, prefix,
            suffix, reset period) are editable.
        </p>

        <h3>Reset Counter</h3>
        <p>
            Click <b>Reset Counter</b> in the three-dot menu to set the counter back to
            0. The next Exchange that uses the sequence will receive value 1 (or the
            formatted equivalent). Gaps in the sequence before the reset are normal and
            expected.
        </p>

        <h3>Deleting a sequence</h3>
        <p>
            Deleting a sequence soft-deletes the definition. Any blueprint or Exchange
            field that still contains <code>{"{{SEQ:KEY}}"}</code> for a deleted sequence
            will leave the token unresolved after deletion.
        </p>

        <h3>A note on gaps</h3>
        <p>
            If Exchange creation fails after the counter has already been incremented,
            the counter is not rolled back. Gaps in the sequence are expected and
            acceptable, consistent with how invoice numbers work. Do not rely on
            sequential numbering being gap-free.
        </p>
    </>
);
