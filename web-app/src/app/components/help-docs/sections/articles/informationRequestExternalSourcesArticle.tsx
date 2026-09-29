export const informationRequestExternalSourcesArticle = (
    <>
        <p id={"information-request-external-sources-intro"}>
            A request can keep information that comes from outside DocuHyphen beside its answers: a
            value someone looked up elsewhere, a result from a connector that reaches another system,
            and a reference to an output produced somewhere else. These are records of the request. An
            imported value stays untrusted until a reviewer decides it, and an external record never
            changes an answer, a submission, or an accepted fact. These records are available through
            the API; the request pages do not show them yet.
        </p>

        <h3 id={"information-request-external-sources-values-heading"}>Imported values</h3>
        <p id={"information-request-external-sources-values-help"}>
            <code>POST /information-requests/{"{id}"}/imported-values</code> records a value for one
            Requirement with its source, a short lowercase result key, its type, a confidence
            (<code>ASSERTED</code>, <code>MATCHED</code>, or <code>VERIFIED</code>), when it was
            verified, when it expires, and a provenance reference. A matched or verified value states
            when it was verified, which cannot be in the future, and expires only after that; a value
            that has already expired is refused. A value for a Requirement that collects a Field must
            have that Field&apos;s type and is kept in the same form as an answer. A value for any other
            Requirement is a single value of the stated type.
        </p>
        <p id={"information-request-external-sources-decisions-help"}>
            A reviewer decides each value once with{" "}
            <code>POST /information-requests/{"{id}"}/imported-values/{"{valueId}"}/decisions</code>:
            <code> ACCEPTED</code> or <code>REJECTED</code>, with a reason. The person who recorded a
            value by hand cannot decide it, and an expired value cannot be accepted. A decision is a
            record only.
        </p>

        <h3 id={"information-request-external-sources-reconciliation-heading"}>Reconciliation</h3>
        <p id={"information-request-external-sources-reconciliation-help"}>
            <code>POST /information-requests/{"{id}"}/imported-value-reconciliations</code> compares
            every value that was not rejected with the current answer to its Requirement. Each comes
            back as <code>MATCHES</code>, <code>DIFFERS</code>, <code>NO_ANSWER</code>,{" "}
            <code>NOT_COMPARABLE</code> (the Requirement collects no Field), or <code>EXPIRED</code>.
            Values are compared by meaning, so the same number or moment written differently still
            matches. A difference is kept as a discrepancy, once for each version of the answer.
            Resolve it with{" "}
            <code>POST /information-requests/{"{id}"}/imported-value-discrepancies/{"{discrepancyId}"}/resolutions</code>
            as <code>RESPONSE_STANDS</code> or <code>FOLLOW_UP_REQUESTED</code>, with a reason. Neither
            changes the answer; to ask the respondent for a change, use a correction or a supplemental
            request.
        </p>

        <h3 id={"information-request-external-sources-connectors-heading"}>Connectors</h3>
        <p id={"information-request-external-sources-connectors-help"}>
            <code>POST /information-requests/{"{id}"}/connector-exchanges</code> asks an installed
            connector to look something up for one Requirement. It names the connector and an optional
            lookup reference, which is the only request information the connector receives. DocuHyphen
            calls the connector in the background and checks back until it answers, retrying a failed
            call a limited number of times. A result is recorded as imported values with the connector
            as their source. A result that names a value the connector does not declare, is older than
            the connector allows, or does not fit the Requirement is refused and records nothing. One
            connector works on one Requirement at a time. DocuHyphen ships no connector, so this list
            is empty unless your deployment installs one.
        </p>

        <h3 id={"information-request-external-sources-outputs-heading"}>Generated outputs</h3>
        <p id={"information-request-external-sources-outputs-help"}>
            <code>POST /information-requests/{"{id}"}/generated-outputs</code> records where an output
            produced outside DocuHyphen lives: its reference, an optional submission it was made from,
            an optional SHA-256 hash and media type, what produced it, and when. DocuHyphen does not
            create, fetch, or store the output.
        </p>

        <h3 id={"information-request-external-sources-access-heading"}>Who can use them</h3>
        <p id={"information-request-external-sources-access-help"}>
            People who manage the request, such as the Exchange owner or its Decision Maker, record
            values, ask connectors, and record outputs. Reviewers decide, reconcile, and resolve.
            Respondents never see imported values. All of this works while the request is open and
            after it closes, while its Exchange is active. External records are part of the
            request&apos;s record export and are removed with the request when it is disposed of.
        </p>
    </>
);
