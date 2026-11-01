
export const usingExchangeFieldsArticle = (
    <>
        <p id={"using-exchange-fields-intro-paragraph"}>
            Once your organization has published at least one schema, Exchange creators can
            attach it to an Exchange and record typed business values against it. The Exchange{" "}
            <b id={"using-exchange-fields-article-b-1"}>Details</b> tab is shown when your own plan includes Business Fields.
        </p>

        <h3 id={"using-exchange-fields-assigning-a-schema-heading"}>Assigning a schema</h3>
        <p id={"using-exchange-fields-assigning-a-schema-paragraph"}>
            You can classify an Exchange in two places. When you start a new Exchange and your
            organization has at least one eligible published schema, the creation wizard shows a
            <b id={"using-exchange-fields-article-b-2"}> Business Fields</b> step. Search by schema name, namespace, or schema key, then pick
            a schema and fill in its values before you select <b id={"using-exchange-fields-article-b-3"}>Start Exchange</b>. The values are
            saved with the Exchange as it is created. You can also do this later: open an Exchange
            and select the <b id={"using-exchange-fields-article-b-4"}>Details</b> tab.
        </p>
        <p id={"using-exchange-fields-assigning-a-schema-paragraph-2"}>
            While the Exchange is still a draft (status <b id={"using-exchange-fields-article-b-5"}>Pending</b>), choose a published schema
            from the list and select <b id={"using-exchange-fields-article-b-6"}>Assign</b>. Each Exchange has one primary schema assignment,
            pinned to the schema version that was current when you assigned it.
        </p>
        <p id={"using-exchange-fields-assigning-a-schema-paragraph-3"}>
            Choosing or removing the schema is an owner decision, because it changes which values the
            Exchange can hold at all. It requires owner or administrator access to the Exchange.
            Someone who has been given edit access can still fill in and save values against the
            schema you assigned, but cannot swap it for another schema or remove it. The Details tab
            shows each control only to the people allowed to use it: without owner or administrator
            access you see no schema list and no <b id={"using-exchange-fields-article-b-7"}>Remove schema</b>, and without edit access the
            values are shown read-only.
        </p>

        <h3 id={"using-exchange-fields-removing-a-schema-heading"}>Removing a schema</h3>
        <p id={"using-exchange-fields-removing-a-schema-paragraph"}>
            While the Exchange is <b id={"using-exchange-fields-article-b-8"}>Pending</b>, an owner or administrator can select{" "}
            <b id={"using-exchange-fields-article-b-9"}>Remove schema</b> on the schema summary and confirm. Removing the schema also removes
            every value recorded against it on that Exchange; earlier recorded versions of those
            values stay on record. You can then assign another schema.
        </p>

        <h3 id={"using-exchange-fields-entering-values-heading"}>Entering values</h3>
        <p id={"using-exchange-fields-entering-values-paragraph"}>
            After a schema is assigned, the Details tab shows an editor for each field. The
            control matches the field type: a text box, a Yes or No choice, a number, a
            date picker, or a selection dropdown. Required fields are marked, and read-only
            fields cannot be changed. Select <b id={"using-exchange-fields-article-b-10"}>Save values</b> to store them. Values are
            validated on the server against the schema before they are saved.
        </p>
        <p id={"using-exchange-fields-entering-values-paragraph-2"}>
            Saving records only the fields you actually changed. A field you did not touch keeps
            the value it already had, so you can save the fields you have filled in and come back to
            the rest later. Clearing a field is itself a change and removes the stored value, unless
            the field is required.
        </p>
        <p id={"using-exchange-fields-entering-values-paragraph-3"}>
            Two people can have the Details tab open on the same Exchange at once. A save carries the
            version of the values it was opened on, so it cannot quietly replace something the other
            person saved in the meantime. When that happens nothing is stored, the current values are
            loaded again, and you are told that your save was not applied. Your own entries are kept
            so you can check them against what is now recorded and save again; a field you never
            touched simply shows the newer value.
        </p>
        <p id={"using-exchange-fields-entering-values-paragraph-4"}>
            Every change to an Exchange&apos;s Fields carries that protection. Saving values and
            removing the schema each states the version of the values it read in the{" "}
            <b id={"using-exchange-fields-article-b-11"}>If-Match</b> header, and a change stating no version is refused. Removing the schema is
            refused when the values changed after they were read. Choosing a schema states that the
            Exchange had no schema when it was read, using <b id={"using-exchange-fields-article-b-12"}>If-None-Match: *</b>, so when someone
            else chose one in the meantime nothing changes and the tab shows the schema they chose.
        </p>
        <p id={"using-exchange-fields-entering-values-paragraph-5"}>
            Every change to a value is recorded against the person or recipient that made it, and
            each earlier value is kept rather than overwritten. Values filled in from a schema
            default when the schema is assigned are recorded the same way. Administrators can see
            that values changed, when, and who changed them under <b id={"using-exchange-fields-article-b-13"}>Settings</b> in the{" "}
            <b id={"using-exchange-fields-article-b-14"}>Audit</b> tab; the values themselves are never copied into the audit trail, so they
            stay governed by the sharing rules below. A save that stores nothing new is not recorded
            as a change.
        </p>
        <p id={"using-exchange-fields-entering-values-paragraph-6"}>
            A Yes or No field has three states, not two: <b id={"using-exchange-fields-article-b-15"}>Yes</b>, <b id={"using-exchange-fields-article-b-16"}>No</b>, and not answered. A
            field you have not reached yet is left unanswered rather than recorded as No, and a
            deliberate No is stored as an answer. Use the clear control on the choice to take an
            answer back. A field with one choice from a list has the same clear control; a field
            with several choices is cleared by deselecting each of them.
        </p>
        <p id={"using-exchange-fields-entering-values-paragraph-7"}>
            A Date and time field records the exact moment you picked, together with the time zone
            offset your browser was using. It is shown back to you in your own time zone, so the
            same moment reads correctly for a colleague in another zone. A Decimal field keeps every
            digit you enter, up to 28 digits before the decimal point and 10 after it; a wider
            number is refused rather than rounded.
        </p>

        <h3 id={"using-exchange-fields-read-only-fields-heading"}>Read-only fields</h3>
        <p id={"using-exchange-fields-read-only-fields-paragraph"}>
            A field the schema marks <b id={"using-exchange-fields-article-b-17"}>Read only</b> is shown on the Details tab with a Read only
            badge, and its control is disabled there and in the creation wizard. The server refuses
            a value sent for one whoever sends it, so nobody fills one in by another route.
        </p>
        <p id={"using-exchange-fields-read-only-fields-paragraph-2"}>
            A read-only field is filled only from the <b id={"using-exchange-fields-article-b-18"}>Default value</b> set for it in the schema
            editor, which is recorded on the Exchange the moment the schema is assigned. Blueprint
            field defaults do not fill it: they pre-fill the creation wizard, which never sends a
            read-only field.
        </p>

        <h3 id={"using-exchange-fields-when-values-can-change-heading"}>When values can change</h3>
        <p id={"using-exchange-fields-when-values-can-change-paragraph"}>
            Fields can be assigned, edited, and removed only while the Exchange is <b id={"using-exchange-fields-article-b-19"}>Pending</b>.
            Once the Exchange becomes <b id={"using-exchange-fields-article-b-20"}>Active</b> or is ended, the Details tab shows the
            recorded values as read-only. This keeps the business data consistent with the
            point at which the Exchange started.
        </p>
        <p id={"using-exchange-fields-when-values-can-change-paragraph-2"}>
            Recorded field data is never removed by a plan or subscription change, but reaching it
            can be. The Details tab follows your own plan: if your plan stops including Business
            Fields, the tab is not shown to you, and the assignment and its values are readable again
            as soon as it includes Business Fields once more. Changes follow the plan of whoever owns
            the Exchange: choosing a schema, saving values, and removing the schema are refused while
            the owner&apos;s subscription is lapsed or suspended, or when the owner&apos;s plan does
            not include Business Fields.
        </p>

        <h3 id={"using-exchange-fields-visibility-heading"}>Visibility</h3>
        <p id={"using-exchange-fields-visibility-paragraph"}>
            Access to an Exchange is still governed by its sharing rules. Members of the
            organization that owns the Exchange see every field value. Recipients from another
            organization only see fields the schema makes <b id={"using-exchange-fields-article-b-21"}>Public</b>, and only when their own
            plan includes Business Fields, because that is what shows them the Details tab. When none
            of a schema&apos;s fields are shared with them, the Details tab shows a message that no
            fields have been shared, rather than an empty schema summary.
        </p>
        <p id={"using-exchange-fields-visibility-paragraph-2"}>
            The same boundary applies when values are saved. A recipient can only save values for the
            fields they are shown, and the confirmation they receive after saving lists those same
            fields. A field that was never shared with them is refused, and it is never revealed in a
            response to them.
        </p>
    </>
);
