export const informationRequestTemplateVersionsArticle = (
    <>
        <p id={"information-request-template-versions-intro"}>
            Every published Version of a Template stays fixed, and requests keep the Version they started with. Open a
            Template
            and select the <b>Versions</b> tab to see each published and retired Version with the date it was published
            or retired,
            and the content hash of the newest published Version, which configuration bundles name it by. The Version
            the editor shows is marked <b>Shown</b>; select <b>View version</b> beside any other, published or retired,
            to open it read-only. The badge in the heading then says which Version you are viewing, and <b>Show current
            version</b> returns to the draft or the newest published Version.
        </p>

        <h3 id={"information-request-template-versions-acting-heading"}>Acting on a Version</h3>
        <p id={"information-request-template-versions-acting-help"}>
            When more than one Version is published, choose one in <b>Version to act on</b>. <b>Start a new draft from
            version</b>{" "}
            opens a draft from it when no draft is open, <b>Retire version</b> stops new requests from using it, and
            <b> Copy as a new Template</b> starts another Template from it. People who read a scope without authoring it
            see only
            Templates with a published Version, and can still copy one into a scope they author.
        </p>

        <h3 id={"information-request-template-versions-retire-heading"}>Retiring a Version</h3>
        <p id={"information-request-template-versions-retire-help"}>
            Requests that already use a retired Version keep it, and Blueprints that name it must choose another
            published Version
            before they create requests again. Retiring the only published Version needs care, and the dialog says so
            before you
            confirm: with no draft open, it ends the Template, because no request, new draft or copy can start from a
            retired
            Version; with a draft open, no request can start from the Template until that draft is published. A Template
            whose
            Versions are all retired shows <b>All versions retired</b> and opens its latest retired Version read-only,
            and its retired Versions stay listed.
        </p>

        <h3 id={"information-request-template-versions-copy-heading"}>Copying a Template</h3>
        <p id={"information-request-template-versions-copy-help"}>
            <b>Copy as a new Template</b> asks where the copy goes when you can choose, its name, its key, and its
            description, which starts as the
            source&apos;s. The copy starts as a draft. Typed answers keep the Schema the copied Version names, so a copy
            for another
            owner, such as an organization Template copied into My Templates, works only when that Schema is a Platform
            Schema;
            otherwise the copy is refused, and the dialog explains this whenever the copy goes to another owner. A
            platform Template is used
            by copying it into My Templates or the organization.
        </p>
    </>
);
