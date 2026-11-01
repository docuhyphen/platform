
export const documentLibraryOverviewArticle = (
    <>
        <p id={"document-library-overview-article-p-1"}>
            The Document Library is a scope-aware repository for physical files that you
            reuse across Exchanges. Instead of uploading the same standard document every
            time, you store it once in the library and reference it from Blueprints or
            add it directly when creating an Exchange.
        </p>

        <h3 id={"document-library-overview-article-h3-1"}>Three library scopes</h3>
        <ul id={"document-library-overview-article-ul-1"}>
            <li id={"document-library-overview-article-li-1"}>
                <b id={"document-library-overview-article-b-1"}>My Documents (Personal)</b> - private to you. Personal and Business
                plans can create and manage personal library documents.
            </li>
            <li id={"document-library-overview-article-li-2"}>
                <b id={"document-library-overview-article-b-2"}>Organization</b> - shared across your organization. Organization Admins
                create and publish these. A document must be Active and Published before
                other members can see or pick it.
            </li>
            <li id={"document-library-overview-article-li-3"}>
                <b id={"document-library-overview-article-b-3"}>Platform</b> - curated by DocuHyphen and available with Document Library access. Only
                App Admins can create and manage platform documents from
                <b id={"document-library-overview-article-b-4"}> Platform Administration - Platform Content - Documents</b>. The
                Document Library in Settings keeps platform documents available for
                browsing and cloning but does not expose platform management actions.
                An entitled user can clone a platform document into their personal library.
                App Admin access alone does not allow management of Organization
                documents; that requires a separate Organization Admin role in the
                active organization.
            </li>
        </ul>

        <h3 id={"document-library-overview-article-h3-2"}>Document lifecycle</h3>
        <p id={"document-library-overview-article-p-2"}>
            A library entry is created as a metadata stub (title, description, tags).
            You upload a file separately. Supported formats: PDF, DOCX, DOC, XLSX, XLS,
            PPTX, PPT, PNG, JPG.
        </p>
        <p id={"document-library-overview-article-p-3"}>
            Once a file is uploaded the entry shows a file-type badge. Only entries with
            a file attached appear in the Document Library Picker.
        </p>

        <h3 id={"document-library-overview-article-h3-3"}>Linking to Blueprints</h3>
        <p id={"document-library-overview-article-p-4"}>
            Open a Blueprint in Settings - Blueprints and go to the Documents tab. Each
            document slot has a <b id={"document-library-overview-article-b-5"}>Link Document</b> button. Selecting a library document
            links it to that slot. When an Exchange is created from the Blueprint, the
            linked file is automatically copied into the Exchange document. Recipients
            receive a completed document, not an empty slot.
        </p>

        <h3 id={"document-library-overview-article-h3-4"}>Picking from Library during Exchange creation</h3>
        <p id={"document-library-overview-article-p-5"}>
            On the Documents tab of the Exchange initiation dialog, click
            <b id={"document-library-overview-article-b-6"}> Pick from Library</b> to open a three-tab picker. Selecting a library
            document adds it as a pre-titled slot; the file is copied into the Exchange
            on submission. Free does not show this action; adding a new document slot remains available.
        </p>

        <h3 id={"document-library-overview-article-h3-5"}>Cloning platform documents</h3>
        <p id={"document-library-overview-article-p-6"}>
            Click the menu on any Platform document and choose <b id={"document-library-overview-article-b-7"}>Clone</b>. A personal
            copy is created without a file. Upload your own version of the document after
            cloning.
        </p>

        <h3 id={"document-library-overview-article-h3-6"}>Tags</h3>
        <p id={"document-library-overview-article-p-7"}>
            Add comma-separated tags (e.g. LEGAL, COMPLIANCE) to help categorize and
            discover documents across scopes.
        </p>
        <p id={"document-library-overview-article-p-8"}>
            When a scope has multiple pages of results, the footer shows the visible
            document range on the left and page navigation actions on the right.
        </p>
    </>
);
