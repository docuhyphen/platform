import React from "react";

export const fieldsOverviewArticle = (
    <>
        <p>
            Fields let your organization describe its own business concepts and attach them
            to Exchanges. Instead of building industry-specific models into the platform,
            an administrator configures reusable <b>fields</b> and groups them into a{" "}
            <b>schema</b> that gives an Exchange a business meaning, such as a Claim Case or
            a Loan Application.
        </p>

        <h3>Fields and schemas</h3>
        <ul>
            <li>
                A <b>field</b> is a single reusable business attribute, for example a
                customer reference or a loss date. Each field has a stable namespaced key
                written as <code>namespace:field-key</code> (lowercase letters, numbers,
                and hyphens).
            </li>
            <li>
                A <b>schema</b> composes one or more fields into a case type that Exchange
                creators can select. Schemas target Exchanges. A schema lists each field
                once, so a field you have already added is no longer offered.
            </li>
        </ul>

        <h3>Field types</h3>
        <p>Nine value types are supported:</p>
        <ul>
            <li>Short text and Long text.</li>
            <li>Yes or No, which can also be left unanswered.</li>
            <li>
                Integer and Decimal, holding up to 28 digits before the decimal point and 10
                after it. A Decimal field can be given a fixed number of decimal places.
            </li>
            <li>Date, and Date and time, which records an exact moment rather than a local reading.</li>
            <li>Single selection and Multiple selection (each with a list of options).</li>
        </ul>

        <h3>Publishing lifecycle</h3>
        <p>
            A schema starts as a <b>draft</b>. You add fields, then <b>publish</b> it to
            make it available to Exchanges. Publishing freezes that version. To change a
            published schema, open a <b>new version</b>, edit its fields, and publish again.
            An Exchange stays pinned to the exact schema version it was created with, so
            historical Exchanges remain understandable. Fields and schemas can be{" "}
            <b>retired</b> when no longer needed; retiring never erases values already
            stored on existing Exchanges.
        </p>

        <h3>Organization fields and schemas</h3>
        <p>
            Open <b>Settings</b>, then select <b>Content - Fields</b>. It is available to
            organization administrators on the organization&apos;s Business plan. An App Administrator needs a separate Organization
            Admin role in the active organization to manage that organization's fields and
            schemas. Platform fields and schemas can be viewed here when available, but their
            management actions are not shown. The tab has two sub-tabs:
        </p>
        <ul>
            <li><b>Fields</b> - create and retire reusable field definitions.</li>
            <li><b>Schemas</b> - compose fields into schemas, publish, and version them.</li>
        </ul>
        <p>
            When creating a field, use the information icons beside Namespace, Field key,
            Classification, and Help text for guidance. When creating a schema, the Namespace
            and Schema key labels provide the same identifier guidance. In the schema&apos;s Fields
            control, type a field label, namespace, or key to filter the available fields before
            adding one.
        </p>
        <p>
            Each field you add to a schema has a <b>Required</b> and a <b>Read only</b> switch.
            Required marks the field on the Exchange and refuses a save that clears it. Read only
            disables the field&apos;s editor everywhere; because the schema editor has no setting
            for a default value, a field marked Read only stays empty on every Exchange that uses
            the schema.
        </p>
        <p>
            While the plan still includes Business Fields, a lapsed or suspended subscription
            leaves this tab readable and refuses configuration changes. If the plan stops
            including Business Fields, the tab is not shown at all. Nothing is deleted either way:
            definitions, schemas, assignments, and values are kept, and are readable again as soon
            as the plan includes Business Fields once more.
        </p>

        <h3>Platform fields and schemas</h3>
        <p>
            App Administrators manage reusable platform fields and schemas from
            <b> Platform Administration - Platform Content - Fields</b>. The platform
            Fields and Schemas sub-tabs use a fixed PLATFORM scope and do not load
            organization field configuration. A platform schema can bind only platform
            fields.
        </p>
    </>
);
