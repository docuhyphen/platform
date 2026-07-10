import React from "react";

export const managingBlueprintsArticle = (
    <>
        <p>
            Personal blueprints are blueprints you create and manage yourself. They
            are visible only to you and can be used any time you start an exchange.
        </p>

        <h3>Creating a blueprint from the settings page</h3>
        <ol>
            <li>Open Settings and go to the <b>Blueprints</b> tab.</li>
            <li>Make sure the <b>My Blueprints</b> inner tab is selected.</li>
            <li>Click <b>Create</b> in the top-right corner.</li>
            <li>Fill in the blueprint editor (see below) and click <b>Create Blueprint</b>.</li>
        </ol>

        <h3>Creating a blueprint while initiating an exchange</h3>
        <ol>
            <li>Fill out an exchange form as you normally would.</li>
            <li>Click <b>Save as Blueprint</b> in the dialog title bar.</li>
            <li>Give the blueprint a name, optional summary, and tags, then click <b>Save Blueprint</b>.</li>
        </ol>
        <p>
            This captures the current state of the form - documents, permissions,
            participants, settings, and any selected business schema with its field
            values - into a new personal blueprint.
        </p>
        <p>
            Creating, editing, duplicating, activating, or deleting a personal
            blueprint can require a 6-digit verification code before the change is saved.
        </p>

        <h3>The blueprint editor</h3>
        <p>The editor has four tabs:</p>

        <h3>Details tab</h3>
        <ul>
            <li><b>Name</b> (required) - shown in the picker and the settings list.</li>
            <li><b>Summary</b> - a short description shown on the blueprint card in the picker.</li>
            <li><b>Description</b> - a longer explanation for internal reference.</li>
            <li><b>Tags</b> - free-text labels for filtering. Type a tag and click Add or press Enter.</li>
        </ul>

        <h3>Documents tab</h3>
        <ul>
            <li>Click <b>Add Document</b> to add a new document slot.</li>
            <li>Edit the <b>document name</b> directly in the title input on each card.</li>
            <li>
                Toggle <b>Restrict upload type</b> to limit what file types recipients
                can upload. Choose the allowed type from the dropdown (document
                formats or image formats).
            </li>
            <li>
                Check <b>Required</b> to mark the document as mandatory. Required
                documents must be uploaded before the exchange can be completed.
            </li>
            <li>Click the red delete icon to remove a document slot.</li>
        </ul>

        <h3>Business Fields tab</h3>
        <ul>
            <li>
                Pick a published business schema to classify exchanges started from this
                blueprint (for example, a Client Onboarding schema with a Category field).
            </li>
            <li>
                Set default values for the schema's fields. These pre-fill the Business
                Fields step when someone starts an exchange from the blueprint.
            </li>
            <li>Leave the schema empty to keep the blueprint free of business fields.</li>
        </ul>

        <h3>Permissions tab</h3>
        <ul>
            <li><b>Require recipient sign-in</b> - recipients must authenticate before accessing the exchange.</li>
            <li><b>Allow document addition</b> - recipients can upload additional documents beyond the pre-defined slots.</li>
            <li><b>Allow document deletion</b> - recipients can remove documents from the exchange.</li>
            <li><b>Allow document download</b> - recipients can download documents.</li>
            <li><b>Allow document update</b> - recipients can replace uploaded documents.</li>
            <li><b>Allow document upload</b> - recipients can upload files to the defined document slots.</li>
        </ul>

        <h3>Managing existing blueprints</h3>
        <p>
            Each blueprint in the My Blueprints list has a three-dot menu with the
            following actions:
        </p>
        <ul>
            <li><b>Edit</b> - open the blueprint editor to modify any field.</li>
            <li><b>Activate / Deactivate</b> - inactive blueprints are hidden from the picker. Use this to temporarily remove a blueprint without deleting it.</li>
            <li><b>Duplicate</b> - creates a copy with "(copy)" appended to the name. The duplicate starts inactive.</li>
            <li><b>Delete</b> - permanently removes the blueprint.</li>
        </ul>
        <p>
            When any Blueprint scope has multiple pages of results, the footer shows
            the visible blueprint range on the left and page navigation actions on
            the right.
        </p>
    </>
);
