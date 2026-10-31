import React from "react";

export const fieldsOverviewArticle = (
    <>
        <p id={"fields-overview-intro-paragraph"}>
            Fields let your organization describe its own business concepts and attach them
            to Exchanges. Instead of building industry-specific models into the platform,
            an administrator configures reusable <b>fields</b> and groups them into a{" "}
            <b>schema</b> that gives an Exchange a business meaning, such as a Claim Case or
            a Loan Application.
        </p>

        <h3 id={"fields-overview-fields-and-schemas-heading"}>Fields and schemas</h3>
        <ul id={"fields-overview-fields-and-schemas-list"}>
            <li>
                A <b>field</b> is a single reusable business attribute, for example a
                customer reference or a loss date. Each field has a stable namespaced key
                written as <code>namespace:field-key</code> (lowercase letters, numbers,
                and hyphens).
            </li>
            <li>
                A <b>schema</b> composes one or more fields into a case type. When you create one, its
                choice of <b>Used for</b> decides what it describes: <b>Exchanges</b>, which Exchange creators
                can select, or <b>Information Requests</b>, whose Templates record typed answers against it.
                The choice cannot change later, and the Schemas list marks schemas made for Information
                Requests. A schema lists each field once, so a field you have already added is no longer offered.
            </li>
        </ul>

        <h3 id={"fields-overview-field-types-heading"}>Field types</h3>
        <p id={"fields-overview-field-types-paragraph"}>Nine value types are supported:</p>
        <ul id={"fields-overview-field-types-list"}>
            <li>Short text and Long text.</li>
            <li>Yes or No, which can also be left unanswered.</li>
            <li>
                Integer and Decimal, holding up to 28 digits before the decimal point and 10
                after it. A Decimal field can be given a fixed number of decimal places.
            </li>
            <li>Date, and Date and time, which records an exact moment rather than a local reading.</li>
            <li>Single selection and Multiple selection (each with a list of options).</li>
        </ul>

        <h3 id={"fields-overview-publishing-lifecycle-heading"}>Publishing lifecycle</h3>
        <p id={"fields-overview-publishing-lifecycle-paragraph"}>
            A schema starts as a <b>draft</b>. You add fields, then <b>publish</b> it to
            make it available to Exchanges. Publishing freezes that version. To change a
            published schema, open a <b>new version</b>, edit its fields, and publish again.
            An Exchange stays pinned to the exact schema version it was created with, so
            historical Exchanges remain understandable. Fields and schemas can be{" "}
            <b>retired</b> when no longer needed; retiring never erases values already
            stored on existing Exchanges.
        </p>

        <h3 id={"fields-overview-organization-fields-and-schemas-heading"}>Organization fields and schemas</h3>
        <p id={"fields-overview-organization-fields-and-schemas-paragraph"}>
            Open <b>Settings</b>, then select <b>Content - Fields</b>. It is available to
            organization administrators on the organization&apos;s Business plan. An App Administrator needs a separate
            Organization
            Admin role in the active organization to manage that organization's fields and
            schemas. Platform fields and schemas can be viewed here when available, but their
            management actions are not shown. The tab has two sub-tabs:
        </p>
        <ul id={"fields-overview-organization-fields-and-schemas-list"}>
            <li><b>Fields</b> - create and retire reusable field definitions.</li>
            <li><b>Schemas</b> - compose fields into schemas, publish, and version them.</li>
        </ul>
        <p id={"fields-overview-organization-fields-and-schemas-paragraph-2"}>
            When creating a field, use the information icons beside Namespace, Field key,
            Classification, and Help text for guidance. When creating a schema, the Namespace
            and Schema key labels provide the same identifier guidance. In the schema&apos;s Fields
            control, type a field label, namespace, or key to filter the available fields before
            adding one.
        </p>
        <p id={"fields-overview-organization-fields-and-schemas-paragraph-3"}>
            Each field you add to a schema has a <b>Required</b> and a <b>Read only</b> switch.
            Required marks the field on the Exchange and refuses a save that clears it. Read only
            disables the field&apos;s editor everywhere, so a read-only field gets its value only
            from its <b>Default value</b>.
        </p>
        <p id={"fields-overview-organization-fields-and-schemas-paragraph-4"}>
            Each field also has a <b>Section</b>, which groups fields that share it on the Exchange,
            a <b>Visibility</b>, which starts from the field&apos;s classification and decides
            whether people outside the owner&apos;s organization see it (only <b>Public</b> fields
            are shown to them), and a <b>Default value</b>, which is recorded on the Exchange when
            the schema is assigned. A default that does not suit the field&apos;s type or limits is
            refused when you save the draft. Opening a new version copies every one of these
            settings from the published version, and saving keeps them.
        </p>
        <p id={"fields-overview-organization-fields-and-schemas-paragraph-5"}>
            While the plan still includes Business Fields, a lapsed or suspended subscription
            leaves this tab readable and refuses configuration changes. If the plan stops
            including Business Fields, the tab is not shown at all. Nothing is deleted either way:
            definitions, schemas, assignments, and values are kept, and are readable again as soon
            as the plan includes Business Fields once more.
        </p>

        <h3 id={"fields-overview-platform-fields-and-schemas-heading"}>Platform fields and schemas</h3>
        <p id={"fields-overview-platform-fields-and-schemas-paragraph"}>
            App Administrators manage reusable platform fields and schemas from
            <b> Platform Administration - Platform Content - Fields</b>. The platform
            Fields and Schemas sub-tabs use a fixed PLATFORM scope and do not load
            organization field configuration. A platform schema can bind only platform
            fields.
        </p>
    </>
);
