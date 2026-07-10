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
            When both are available, the workspace shows organization-scoped
            evidence first. Platform auditors without an active organization
            membership see platform-wide evidence instead.
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
            category, event type, outcome, actor (kind, identifier, and role),
            target, timestamps, reason (if any), the recorded payload, and the
            cryptographic event hash and previous-hash used to verify ledger
            integrity. Some fields may be masked or withheld depending on your
            access level. Use the pagination controls at the bottom of the table
            to page through additional results with the same filters applied.
        </p>

        <h3>Integrity</h3>
        <p>
            The Integrity section checks the cryptographic hash chain for every
            audit stream in scope and reports, per stream, whether the chain is
            valid and whether its segments are valid. A failing stream shows an
            error message describing what could not be verified. Use{" "}
            <b>Refresh</b> to re-run the check on demand.
        </p>

        <h3>Exports</h3>
        <p>
            The Exports section lists previously requested audit exports and
            their status. Selecting <b>Request Export</b> opens a form where you
            choose categories and a date range, then provide a purpose, an
            optional case reference, and an optional legal basis for the
            request.
        </p>
        <p>
            Export requests that require dual control must be approved by
            someone holding the <b>Audit Export Approve</b> capability before
            the file becomes available. Once approved, use the download action
            on the export to retrieve the underlying evidence file.
        </p>
    </>
);
