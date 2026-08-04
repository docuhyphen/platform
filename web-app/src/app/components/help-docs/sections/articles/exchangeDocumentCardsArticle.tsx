export const exchangeDocumentCardsArticle = (
    <>
        <p>
            The Documents page combines progress, search, filters, sorting, document actions,
            and previewing in one workspace.
        </p>

        <h3>Checking upload progress</h3>
        <p>
            The ZIP action beside the page tabs downloads the available documents when permitted.
            The adjacent summary shows how many Exchange documents have an uploaded file. The progress bar updates whenever a document is uploaded,
            re-uploaded, added, or removed.
        </p>

        <h3>Finding documents</h3>
        <ul>
            <li>Open search and filters, then use <b>Search documents</b> to match document titles.</li>
            <li><b>All</b> shows every search result.</li>
            <li><b>Not uploaded</b> shows search results that do not yet have a file.</li>
            <li><b>Uploaded</b> shows search results that have a file.</li>
        </ul>
        <p>
            The count on each filter reflects all documents in the current Exchange.
        </p>

        <h3>Choosing a document view</h3>
        <p>
            When <b>Automatically preview documents</b> is turned off in App Settings,
            the Documents page opens as a responsive grid. Uploaded documents show a thumbnail
            of their first page. Select a document to open it. The documents then move into the
            horizontal strip above the preview.
        </p>
        <p>
            When automatic preview is turned on, the first document opens immediately and
            the horizontal strip remains visible. Selecting a card in the strip changes the preview.
        </p>

        <h3>Sorting documents</h3>
        <p>
            Use the Sort menu to preserve the Exchange's default document order, place
            documents without uploads first, show the most recently uploaded documents first,
            or sort document names from A-Z or Z-A. Sorting changes only the visible card order.
        </p>

        <h3>Reading a document card</h3>
        <ul>
            <li>The title identifies the document. Hover over a shortened title to see it in full.</li>
            <li><b>Uploaded</b> or <b>Not Uploaded</b> shows whether a file is available.</li>
            <li>Uploaded documents show a relative date. Hover over it for the exact upload time.</li>
        </ul>

        <h3>Opening and uploading documents</h3>
        <ul>
            <li>In the horizontal strip, select the card body to open that document in the preview panel.</li>
            <li>In the grid, select a document to open the standard preview layout.</li>
            <li>Select <b>Upload</b> to add the first file to a document slot.</li>
            <li>Select <b>Re-upload</b> to upload another file version.</li>
            <li>Upload controls are disabled when your permissions or the Exchange status prevent uploads.</li>
        </ul>

        <h3>More actions and navigation</h3>
        <p>
            Use the three-dot button for document-specific actions. When cards extend beyond
            the available width, use the scroll controls, a trackpad, touch scrolling, or
            Shift plus the mouse wheel. You can also use Tab to reach cards and their actions.
        </p>
    </>
);
