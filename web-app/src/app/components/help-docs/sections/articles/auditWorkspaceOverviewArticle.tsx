export const auditWorkspaceOverviewArticle = (
    <>
        <p>
            The Audit workspace gives organization and platform administrators a
            searchable view into the ledger-backed audit trail: every recorded
            authentication, administration, security, Exchange, document, and
            workflow event, plus tools to verify ledger integrity and request
            evidentiary exports.
        </p>

        <h3>Who can see it</h3>
        <p>
            The Audit workspace is available from <b>Settings</b> under the{" "}
            <b>Audit</b> tab. It is visible only to people who hold the{" "}
            <b>Organization Audit Read</b> capability (typically Organization
            Owner, Admin, or Auditor roles) or the platform-wide{" "}
            <b>Application Audit Read</b> capability. Anyone without either
            capability sees a not-authorized message instead of the workspace.
        </p>
        <p>
            People who hold both capabilities and an active organization
            membership see a scope selector above the tabs and can switch
            between organization-scoped and platform-wide evidence; the
            workspace defaults to organization scope. Platform auditors
            without an active organization membership see platform-wide
            evidence with no selector, since there is no organization scope
            to switch to.
        </p>

        <h3>Events</h3>
        <p>
            The Events section lists audit events in a paginated table, most
            recent first. Use the filter panel to narrow results:
        </p>
        <ul>
            <li>
                <b>Categories</b> - a multiselect of audit categories such as
                Authentication, Security, Exchange, Document, Workflow, and
                Administration. Leave empty to see every category.
            </li>
            <li>
                <b>Occurred after / before</b> - date range filters applied to
                when the event actually happened.
            </li>
        </ul>
        <p>
            Selecting a row opens a detail view with the event's full record:
            category, event type, outcome, actor, target, timestamps, reason
            (if any), the recorded payload, and the
            verification hash and previous verification hash used to verify
            ledger integrity. Some fields may be masked or withheld depending on your
            access level. Use the pagination controls at the bottom of the table
            to page through additional results with the same filters applied.
        </p>
        <p>
            Some cross-scope audit reads require an active audit engagement that
            covers the requested organization, resource, categories, and date
            range. Sensitive engagements also require a recent step-up
            authentication. Without it, sensitive engagement access is not used
            and protected actor, target, reason, and payload fields stay hidden.
        </p>

        <h3>Integrity</h3>
        <p>
            The Integrity section checks the cryptographic hash chain for every
            audit stream in scope and reports, per stream, whether the chain is
            valid, whether its segments are valid, and whether the archived tip
            reaches the current ledger head. A stream with missing archive
            coverage is not reported as valid. A failing stream shows an
            error message describing what could not be verified. Use{" "}
            <b>Refresh</b> to re-run the check on demand.
        </p>

        <h3>Exports</h3>
        <p>
            The Exports section lists previously requested audit exports and
            their status. The <b>Request Export</b> button and each export's
            request action is visible only to people holding the export
            capability for the current scope (<b>Organization Audit Export</b>{" "}
            or <b>Application Audit Export</b>). A ready export can be downloaded
            only by its requester or an authorized evidence custodian.
            Selecting <b>Request Export</b> opens a form where you choose
            categories and a date range, then provide a purpose, an optional
            case reference, and an optional legal basis for the request.
        </p>
        <p>
            Export requests that require dual control must be approved by
            someone holding the <b>Audit Export Approve</b> capability before
            the file becomes available. The requester cannot approve their own
            export. Full-fidelity requests, approvals, and downloads require a
            recent step-up and, where applicable, an export-permitted sensitive
            audit engagement.
        </p>
        <p>
            The downloaded ZIP contains canonical <b>events.jsonl</b>, a
            spreadsheet-friendly <b>events.csv</b> convenience projection,
            integrity results, a signed manifest, a README, and a dependency-free
            Node.js verifier. CSV values that could be interpreted as spreadsheet
            formulas are neutralized; use JSONL as the canonical evidence.
        </p>
        <p>
            Obtain the public key identified by the manifest's signing key ID
            from your system administrator through an independent trusted
            channel. Extract <b>verify.mjs</b>, then run{" "}
            <code>node verify.mjs bundle.zip trusted-public-key.pem</code>. The
            verifier rejects missing, duplicate, unexpected, oversized, or
            modified entries, an untrusted or invalid signature, broken event
            hashes or chains, and a mismatched Merkle root.
        </p>
    </>
);
