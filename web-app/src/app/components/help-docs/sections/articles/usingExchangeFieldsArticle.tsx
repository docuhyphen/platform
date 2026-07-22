import React from "react";

export const usingExchangeFieldsArticle = (
    <>
        <p>
            Once your organization has published at least one schema, Exchange creators can
            attach it to an Exchange and record typed business values against it.
        </p>

        <h3>Assigning a schema</h3>
        <p>
            You can classify an Exchange in two places. When you start a new Exchange and your
            organization has at least one eligible published schema, the creation wizard shows a
            <b> Business Fields</b> step: pick a schema and fill in its values before you select
            <b> Start Exchange</b>. The values are saved with the Exchange as it is created. You can
            also do this later: open an Exchange and select the <b>Details</b> tab.
        </p>
        <p>
            While the Exchange is still a draft (status <b>Pending</b>), choose a published schema
            from the list and select <b>Assign</b>. Each Exchange has one primary schema assignment,
            pinned to the schema version that was current when you assigned it.
        </p>

        <h3>Entering values</h3>
        <p>
            After a schema is assigned, the Details tab shows an editor for each field. The
            control matches the field type: a text box, a Yes or No switch, a number, a
            date picker, or a selection dropdown. Required fields are marked, and read-only
            fields cannot be changed. Select <b>Save values</b> to store them. Values are
            validated on the server against the schema before they are saved.
        </p>

        <h3>When values can change</h3>
        <p>
            Fields can be assigned and edited only while the Exchange is <b>Pending</b>.
            Once the Exchange becomes <b>Active</b> or is ended, the Details tab shows the
            recorded values as read-only. This keeps the business data consistent with the
            point at which the Exchange started.
        </p>

        <h3>Visibility</h3>
        <p>
            Access to an Exchange is still governed by its sharing rules. Field values are
            shown to participants who can view the Exchange. Classifying a field does not by
            itself grant or restrict access.
        </p>
    </>
);
