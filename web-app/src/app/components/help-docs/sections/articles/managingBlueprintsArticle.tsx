import React from "react";

export const managingBlueprintsArticle = (
    <>
        <p id={"managing-blueprints-intro-paragraph"}>
            Personal blueprints are blueprints you create and manage yourself. They
            are visible only to you and are available on Personal and Business plans.
            Free keeps the basic Exchange flow but does not show Blueprint creation controls.
        </p>

        <h3 id={"managing-blueprints-creating-a-blueprint-from-heading"}>Creating a blueprint from the settings
            page</h3>
        <ol id={"managing-blueprints-creating-a-blueprint-from-list"}>
            <li>Open Settings and go to the <b>Blueprints</b> tab.</li>
            <li>Make sure the <b>My Blueprints</b> inner tab is selected.</li>
            <li>Click <b>Create</b> in the top-right corner.</li>
            <li>Fill in the blueprint editor (see below) and click <b>Create Blueprint</b>.</li>
        </ol>

        <h3 id={"managing-blueprints-creating-a-blueprint-while-heading"}>Creating a blueprint while initiating an
            Exchange</h3>
        <ol id={"managing-blueprints-creating-a-blueprint-while-list"}>
            <li>Fill out an Exchange form as you normally would.</li>
            <li>Click <b>Save as Blueprint</b> in the dialog title bar.</li>
            <li>Give the blueprint a name, optional summary, and tags, then click <b>Save Blueprint</b>.</li>
        </ol>
        <p id={"managing-blueprints-creating-a-blueprint-while-paragraph"}>
            This captures the current state of the form - documents, permissions,
            participants, settings, and any selected business schema with its field
            values - into a new personal blueprint.
        </p>
        <p id={"managing-blueprints-creating-a-blueprint-while-paragraph-2"}>
            Creating, editing, duplicating, activating, or deleting a personal
            blueprint can require a 6-digit verification code before the change is saved.
        </p>

        <h3 id={"managing-blueprints-the-blueprint-editor-heading"}>The blueprint editor</h3>
        <p id={"managing-blueprints-the-blueprint-editor-paragraph"}>The editor has five tabs:</p>

        <h3 id={"managing-blueprints-details-tab-heading"}>Details tab</h3>
        <ul id={"managing-blueprints-details-tab-list"}>
            <li><b>Name</b> (required) - shown in the picker and the settings list.</li>
            <li><b>Summary</b> - a short description shown on the blueprint card in the picker.</li>
            <li><b>Description</b> - a longer explanation for internal reference.</li>
            <li><b>Tags</b> - free-text labels for filtering. Type a tag and click Add or press Enter.</li>
        </ul>

        <h3 id={"managing-blueprints-documents-tab-heading"}>Documents tab</h3>
        <ul id={"managing-blueprints-documents-tab-list"}>
            <li>Click <b>Add Document</b> to add a new document slot.</li>
            <li>Edit the <b>document name</b> directly in the title input on each card.</li>
            <li>
                Toggle <b>Restrict upload type</b> to limit what file types recipients
                can upload. Choose the allowed type from the dropdown (document
                formats or image formats).
            </li>
            <li>
                Check <b>Required</b> to record the slot as one you expect to be filled. The
                flag is carried onto every Exchange started from the blueprint and kept with the
                document there. It is not a gate: nothing stops an Exchange being ended while a
                slot marked Required is empty, and the flag is not shown on the Exchange itself,
                so treat it as a note to whoever prepares the Exchange.
            </li>
            <li>Click the red delete icon to remove a document slot.</li>
        </ul>

        <h3 id={"managing-blueprints-business-fields-tab-heading"}>Business Fields tab</h3>
        <ul id={"managing-blueprints-business-fields-tab-list"}>
            <li>
                Pick a published business schema to classify Exchanges started from this
                blueprint (for example, a Client Onboarding schema with a Category field).
            </li>
            <li>
                Set default values for the schema's fields. These pre-fill the Business
                Fields step when someone starts an Exchange from the blueprint.
            </li>
            <li>Leave the schema empty to keep the blueprint free of business fields.</li>
        </ul>

        <h3 id={"managing-blueprints-information-request-heading"}>Information Request tab</h3>
        <ul id={"managing-blueprints-information-request-list"}>
            <li>
                Choose a <b>Template</b>. The list shows only the published, unretired Templates of the
                blueprint&apos;s own workspace, and the blueprint names the Template&apos;s current published Version.
                To use a platform Template, copy it with <b>Copy as a new Template</b> in Settings, Content -
                Information Requests, publish the copy, and choose it here. A platform blueprint cannot start an
                Information Request, so its tab only says so. If the Templates cannot be read, the tab says so and
                offers <b>Try again</b>.
            </li>
            <li>
                Starting an Exchange from the blueprint then creates a draft Information Request from
                that Version, with the person who starts it as Decision Maker, and the Exchange&apos;s
                documents become optional because the request collects them.
            </li>
            <li><b>Remove</b> clears the Version, so later Exchanges start no request.</li>
            <li>
                A change affects only Exchanges started afterwards; requests already created keep their
                Version. Saving the blueprint never changes them.
            </li>
            <li>
                A Version that is later retired stays on the blueprint. The tab still names its Template and
                version number, marks it <b>Retired</b>, and explains that Exchanges cannot start from the
                blueprint until you choose a currently published Template. When a newer version of the
                Template is published, the tab marks the named one as an <b>Earlier version</b>.
            </li>
        </ul>

        <h3 id={"managing-blueprints-permissions-tab-heading"}>Permissions tab</h3>
        <ul id={"managing-blueprints-permissions-tab-list"}>
            <li><b>Require recipient sign-in</b> - recipients must authenticate before accessing the Exchange.</li>
            <li><b>Allow document addition</b> - recipients can upload additional documents beyond the pre-defined
                slots.
            </li>
            <li><b>Allow document deletion</b> - recipients can remove documents from the Exchange.</li>
            <li><b>Allow document download</b> - recipients can download documents.</li>
            <li><b>Allow document update</b> - recipients can replace uploaded documents.</li>
            <li><b>Allow document upload</b> - recipients can upload files to the defined document slots.</li>
        </ul>

        <h3 id={"managing-blueprints-managing-existing-blueprints-heading"}>Managing existing blueprints</h3>
        <p id={"managing-blueprints-managing-existing-blueprints-paragraph"}>
            Each blueprint in the My Blueprints list has a three-dot menu with the
            following actions:
        </p>
        <ul id={"managing-blueprints-managing-existing-blueprints-list"}>
            <li><b>Edit</b> - open the blueprint editor to modify any field.</li>
            <li><b>Activate / Deactivate</b> - inactive blueprints are hidden from the picker. Use this to temporarily
                remove a blueprint without deleting it.
            </li>
            <li><b>Duplicate</b> - creates a copy with "(copy)" appended to the name. The duplicate starts inactive.
            </li>
            <li><b>Delete</b> - permanently removes the blueprint.</li>
        </ul>
        <p id={"managing-blueprints-managing-existing-blueprints-paragraph-2"}>
            When any Blueprint scope has multiple pages of results, the footer shows
            the visible blueprint range on the left and page navigation actions on
            the right.
        </p>
    </>
);
