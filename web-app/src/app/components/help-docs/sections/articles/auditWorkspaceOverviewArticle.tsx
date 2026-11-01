export const auditWorkspaceOverviewArticle = (
    <>
        <p id={"audit-workspace-overview-article-p-1"}>
            The Audit workspace gives organization and platform administrators a
            searchable view into the ledger-backed audit trail: every recorded
            authentication, administration, security, Exchange, document,
            workflow, and Field / Schema event, plus tools to verify ledger
            integrity and request evidentiary exports.
        </p>

        <h3 id={"audit-workspace-overview-article-h3-1"}>Who can see it</h3>
        <p id={"audit-workspace-overview-article-p-2"}>
            Organization audit is available from <b id={"audit-workspace-overview-article-b-1"}>Settings</b> under the{" "}
            <b id={"audit-workspace-overview-article-b-2"}>Audit</b> tab to people who hold <b id={"audit-workspace-overview-article-b-3"}>Organization Audit Read</b>.
            Platform audit is available from the <b id={"audit-workspace-overview-article-b-4"}>Platform Audit</b> button in
            the top application bar to people who hold the platform-wide{" "}
            <b id={"audit-workspace-overview-article-b-5"}>Application Audit Read</b> capability. Direct route access is
            protected by the same live session capabilities.
        </p>
        <p id={"audit-workspace-overview-article-p-3"}>
            The top-level Platform Audit workspace always uses platform scope,
            even when the user also has an active organization. The Settings
            workspace can show a scope selector to people who hold both audit
            capabilities and have an active organization.
        </p>
        <p id={"audit-workspace-overview-article-p-4"}>
            Platform Audit places Events, Integrity, and Exports in a navigation
            menu on the left, with the selected section on the right. On smaller
            screens, use the navigation button to open the same menu.
        </p>
        <p id={"audit-workspace-overview-article-p-5"}>
            Platform Audit limits content-category projections to operational
            fields. Document and workflow names, content-bearing labels, free-form
            reasons, communication bodies, credentials, and non-allowlisted payload
            fields are withheld from the platform view.
        </p>

        <h3 id={"audit-workspace-overview-article-h3-2"}>Events</h3>
        <p id={"audit-workspace-overview-article-p-6"}>
            The Events section lists audit events in a paginated table, most
            recent first. The filters and column headings stay fixed while the
            rows scroll, and pagination stays at the bottom of the content view.
            Use the filter panel to narrow results:
        </p>
        <ul id={"audit-workspace-overview-article-ul-1"}>
            <li id={"audit-workspace-overview-article-li-1"}>
                <b id={"audit-workspace-overview-article-b-6"}>Categories</b> - a multiselect of audit categories such as
                Authentication, Security, Exchange, Document, Workflow,
                Field / Schema, and Administration. Leave empty to see every
                category.
            </li>
            <li id={"audit-workspace-overview-article-li-2"}>
                <b id={"audit-workspace-overview-article-b-7"}>Occurred after / before</b> - date range filters applied to
                when the event actually happened.
            </li>
        </ul>
        <p id={"audit-workspace-overview-article-p-7"}>
            Selecting a row opens a detail view with the event's full record:
            category, event type, outcome, actor, target, timestamps, reason
            (if any), the recorded payload, and the
            verification hash and previous verification hash used to verify
            ledger integrity. Some fields may be masked or withheld depending on your
            access level. Use the pagination controls at the bottom of the table
            to page through additional results with the same filters applied.
        </p>
        <p id={"audit-workspace-overview-article-p-8"}>
            Some cross-scope audit reads require an active audit engagement that
            covers the requested organization, resource, categories, and date
            range. Sensitive engagements also require a recent step-up
            authentication. Without it, sensitive engagement access is not used
            and protected actor, target, reason, and payload fields stay hidden.
        </p>

        <h3 id={"audit-workspace-overview-article-h3-3"}>Integrity</h3>
        <p id={"audit-workspace-overview-article-p-9"}>
            The Integrity section checks the cryptographic hash chain for every
            audit stream in scope and reports, per stream, whether the chain is
            valid, whether its segments are valid, and whether the archived tip
            reaches the current ledger head. A stream with missing archive
            coverage is not reported as valid. A failing stream shows an
            error message describing what could not be verified. Use{" "}
            <b id={"audit-workspace-overview-article-b-8"}>Refresh</b> to re-run the check on demand.
        </p>
        <p id={"audit-workspace-overview-article-p-10"}>
            Organization integrity checks, retention changes, legal-hold changes, and new audit
            export requests require audit governance in the organization&apos;s Business subscription.
            Existing events and completed export downloads remain available for review and recovery.
        </p>

        <h3 id={"audit-workspace-overview-article-h3-4"}>Exports</h3>
        <p id={"audit-workspace-overview-article-p-11"}>
            The Exports section lists previously requested audit exports and
            their status. The <b id={"audit-workspace-overview-article-b-9"}>Request Export</b> button and each export's
            request action is visible only to people holding the export
            capability for the current scope (<b id={"audit-workspace-overview-article-b-10"}>Organization Audit Export</b>{" "}
            or <b id={"audit-workspace-overview-article-b-11"}>Application Audit Export</b>). A ready export can be downloaded
            only by its requester or an authorized evidence custodian.
            Selecting <b id={"audit-workspace-overview-article-b-12"}>Request Export</b> opens a form where you choose
            categories and a date range, then provide a purpose, an optional
            case reference, and an optional legal basis for the request.
        </p>
        <p id={"audit-workspace-overview-article-p-12"}>
            Export requests that require dual control must be approved by
            someone holding the <b id={"audit-workspace-overview-article-b-13"}>Audit Export Approve</b> capability before
            the file becomes available. The requester cannot approve their own
            export. Full-fidelity requests, approvals, and downloads require a
            recent step-up and, where applicable, an export-permitted sensitive
            audit engagement.
        </p>
        <p id={"audit-workspace-overview-article-p-13"}>
            The downloaded ZIP contains canonical <b id={"audit-workspace-overview-article-b-14"}>events.jsonl</b>, a
            spreadsheet-friendly <b id={"audit-workspace-overview-article-b-15"}>events.csv</b> convenience projection,
            integrity results, a signed manifest, a README, and a dependency-free
            Node.js verifier. CSV values that could be interpreted as spreadsheet
            formulas are neutralized; use JSONL as the canonical evidence.
        </p>
        <p id={"audit-workspace-overview-article-p-14"}>
            Obtain the public key identified by the manifest's signing key ID
            from your system administrator through an independent trusted
            channel. Extract <b id={"audit-workspace-overview-article-b-16"}>verify.mjs</b>, then run{" "}
            <code id={"audit-workspace-overview-article-code-1"}>node verify.mjs bundle.zip trusted-public-key.pem</code>. The
            verifier rejects missing, duplicate, unexpected, oversized, or
            modified entries, an untrusted or invalid signature, broken event
            hashes or chains, and a mismatched Merkle root.
        </p>
    </>
);
