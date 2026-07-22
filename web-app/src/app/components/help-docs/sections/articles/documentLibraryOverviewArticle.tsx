import React from "react";

export const documentLibraryOverviewArticle = (
    <>
        <p>
            The Document Library is a scope-aware repository for physical files that you
            reuse across Exchanges. Instead of uploading the same standard document every
            time, you store it once in the library and reference it from Blueprints or
            add it directly when creating an Exchange.
        </p>

        <h3>Three library scopes</h3>
        <ul>
            <li>
                <b>My Documents (Personal)</b> - private to you. Any user can create and
                manage personal library documents.
            </li>
            <li>
                <b>Organization</b> - shared across your organization. Organization Admins
                create and publish these. A document must be Active and Published before
                other members can see or pick it.
            </li>
            <li>
                <b>Platform</b> - curated by DocuHyphen and available to all users. Only
                App Admins can create platform documents. Any user can clone a platform
                document into their personal library.
            </li>
        </ul>

        <h3>Document lifecycle</h3>
        <p>
            A library entry is created as a metadata stub (title, description, tags).
            You upload a file separately. Supported formats: PDF, DOCX, DOC, XLSX, XLS,
            PPTX, PPT, PNG, JPG.
        </p>
        <p>
            Once a file is uploaded the entry shows a file-type badge. Only entries with
            a file attached appear in the Document Library Picker.
        </p>

        <h3>Linking to Blueprints</h3>
        <p>
            Open a Blueprint in Settings - Blueprints and go to the Documents tab. Each
            document slot has a <b>Link Document</b> button. Selecting a library document
            links it to that slot. When an Exchange is created from the Blueprint, the
            linked file is automatically copied into the Exchange document. Recipients
            receive a completed document, not an empty slot.
        </p>

        <h3>Picking from Library during Exchange creation</h3>
        <p>
            On the Documents tab of the Exchange initiation dialog, click
            <b> Pick from Library</b> to open a three-tab picker. Selecting a library
            document adds it as a pre-titled slot; the file is copied into the Exchange
            on submission.
        </p>

        <h3>Cloning platform documents</h3>
        <p>
            Click the menu on any Platform document and choose <b>Clone</b>. A personal
            copy is created without a file. Upload your own version of the document after
            cloning.
        </p>

        <h3>Tags</h3>
        <p>
            Add comma-separated tags (e.g. LEGAL, COMPLIANCE) to help categorize and
            discover documents across scopes.
        </p>
        <p>
            When a scope has multiple pages of results, the footer shows the visible
            document range on the left and page navigation actions on the right.
        </p>
    </>
);
