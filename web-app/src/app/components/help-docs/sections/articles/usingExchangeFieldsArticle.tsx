import React from "react";

export const usingExchangeFieldsArticle = (
    <>
        <p>
            Once your organization has published at least one schema, Exchange creators can
            attach it to an Exchange and record typed business values against it. Business Fields
            assignment, value changes, and the Exchange <b>Details</b> tab require Business Fields
            in the current plan.
        </p>

        <h3>Assigning a schema</h3>
        <p>
            You can classify an Exchange in two places. When you start a new Exchange and your
            organization has at least one eligible published schema, the creation wizard shows a
            <b> Business Fields</b> step. Search by schema name, namespace, or schema key, then pick
            a schema and fill in its values before you select <b>Start Exchange</b>. The values are
            saved with the Exchange as it is created. You can also do this later: open an Exchange
            and select the <b>Details</b> tab.
        </p>
        <p>
            While the Exchange is still a draft (status <b>Pending</b>), choose a published schema
            from the list and select <b>Assign</b>. Each Exchange has one primary schema assignment,
            pinned to the schema version that was current when you assigned it.
        </p>
        <p>
            Choosing or removing the schema is an owner decision, because it changes which values the
            Exchange can hold at all. It requires owner or administrator access to the Exchange.
            Someone who has been given edit access can still fill in and save values against the
            schema you assigned, but cannot swap it for another schema or remove it.
        </p>

        <h3>Entering values</h3>
        <p>
            After a schema is assigned, the Details tab shows an editor for each field. The
            control matches the field type: a text box, a Yes or No choice, a number, a
            date picker, or a selection dropdown. Required fields are marked, and read-only
            fields cannot be changed. Select <b>Save values</b> to store them. Values are
            validated on the server against the schema before they are saved.
        </p>
        <p>
            Saving records only the fields you actually changed. A field you did not touch keeps
            the value it already had, so you can save the fields you have filled in and come back to
            the rest later. Clearing a field is itself a change and removes the stored value, unless
            the field is required.
        </p>
        <p>
            Two people can have the Details tab open on the same Exchange at once. A save carries the
            version of the values it was opened on, so it cannot quietly replace something the other
            person saved in the meantime. When that happens nothing is stored, the current values are
            loaded again, and you are told that your save was not applied. Your own entries are kept
            so you can check them against what is now recorded and save again; a field you never
            touched simply shows the newer value.
        </p>
        <p>
            That protection belongs to a save stating the version it read, and the current save
            route refuses one that does not. A registered application still using the older route
            without stating a version keeps the behaviour it has always had, where the last save
            wins and can replace a change made while it was working. Those calls are recorded so
            the older route can be withdrawn once nothing reaches it.
        </p>
        <p>
            Every change to a value is recorded against the person, application, or recipient that
            made it, and each earlier value is kept rather than overwritten. Administrators can see
            that values changed, when, and who changed them under <b>Settings</b> in the <b>Audit</b>
            tab; the values themselves are never copied into the audit trail, so they stay governed
            by the sharing rules below. A save that stores nothing new is not recorded as a change.
        </p>
        <p>
            A Yes or No field has three states, not two: <b>Yes</b>, <b>No</b>, and not answered. A
            field you have not reached yet is left unanswered rather than recorded as No, and a
            deliberate No is stored as an answer. Use the clear control on the choice to take an
            answer back.
        </p>
        <p>
            A Date and time field records the exact moment you picked, together with the time zone
            offset your browser was using. It is shown back to you in your own time zone, so the
            same moment reads correctly for a colleague in another zone. A Decimal field keeps every
            digit you enter, up to 28 digits before the decimal point and 10 after it; a wider
            number is refused rather than rounded.
        </p>

        <h3>Read-only fields</h3>
        <p>
            A field the schema marks <b>Read only</b> is shown on the Details tab with a Read only
            badge, and its control is disabled there and in the creation wizard. The server refuses
            a value sent for one whoever sends it, so nobody fills one in by another route.
        </p>
        <p>
            A read-only field is filled only from a default value configured against the
            schema&apos;s copy of the field, which is recorded the moment the schema is assigned. The
            schema editor has no setting for that default, so a field you mark Read only in{" "}
            <b>Settings</b> stays empty on every Exchange. Blueprint field defaults do not fill it
            either: they pre-fill the creation wizard, which never sends a read-only field.
        </p>

        <h3>When values can change</h3>
        <p>
            Fields can be assigned and edited only while the Exchange is <b>Pending</b>.
            Once the Exchange becomes <b>Active</b> or is ended, the Details tab shows the
            recorded values as read-only. This keeps the business data consistent with the
            point at which the Exchange started.
        </p>
        <p>
            Recorded field data is never removed by a plan or subscription change, but reaching it
            can be. While the plan still includes Business Fields, a lapsed or suspended
            subscription leaves the Details tab in place and readable, and refuses new assignments
            and edits. If the plan stops including Business Fields, the Details tab is not shown at
            all: the assignment and its values are kept, and are readable again as soon as the plan
            includes Business Fields once more.
        </p>

        <h3>Visibility</h3>
        <p>
            Access to an Exchange is still governed by its sharing rules. Members of the
            organization that owns the Exchange see every field value. Recipients from another
            organization only see fields classified as <b>Public</b>. When none of a schema's
            fields are shared with them, the Details tab shows a message that no fields have been
            shared, rather than an empty schema summary.
        </p>
        <p>
            The same boundary applies when values are saved. A recipient can only save values for the
            fields they are shown, and the confirmation they receive after saving lists those same
            fields. A field that was never shared with them is refused, and it is never revealed in a
            response to them.
        </p>
    </>
);
