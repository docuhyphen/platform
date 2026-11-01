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
            <code id={"information-request-external-sources-article-code-1"}>POST /information-requests/{"{id}"}/imported-values</code> records a value for one
            Requirement with its source, a short lowercase result key, its type, a confidence
            (<code id={"information-request-external-sources-article-code-2"}>ASSERTED</code>, <code id={"information-request-external-sources-article-code-3"}>MATCHED</code>, or <code id={"information-request-external-sources-article-code-4"}>VERIFIED</code>), when it was
            verified, when it expires, and a provenance reference. A matched or verified value states
            when it was verified, allowing at most five minutes ahead for clock differences, and expires only after
            that; a value
            that has already expired is refused. A value for a Requirement that collects a Field must
            have that Field&apos;s type and is kept in the same form as an answer. A value for any other
            Requirement is a single value of the stated type. Each value also keeps a reference to its
            Requirement as it stood when the value was recorded.
        </p>
        <p id={"information-request-external-sources-decisions-help"}>
            A reviewer decides each value once with{" "}
            <code id={"information-request-external-sources-article-code-5"}>POST /information-requests/{"{id}"}/imported-values/{"{valueId}"}/decisions</code>:
            <code id={"information-request-external-sources-article-code-6"}> ACCEPTED</code> or <code id={"information-request-external-sources-article-code-7"}>REJECTED</code>, with a reason. The person who recorded a
            value by hand cannot decide it, and an expired value cannot be accepted. A decision is a
            record only.
        </p>

        <h3 id={"information-request-external-sources-reconciliation-heading"}>Reconciliation</h3>
        <p id={"information-request-external-sources-reconciliation-help"}>
            <code id={"information-request-external-sources-article-code-8"}>POST /information-requests/{"{id}"}/imported-value-reconciliations</code> compares
            every value that was not rejected with the current answer to its Requirement. A value whose
            Requirement an amendment took out of the request stays recorded with its source, provenance,
            decision, and discrepancies, but a reconciliation leaves it out while the request&apos;s current
            Version does not ask for that Requirement. Each comes
            back as <code id={"information-request-external-sources-article-code-9"}>MATCHES</code>, <code id={"information-request-external-sources-article-code-10"}>DIFFERS</code>, <code id={"information-request-external-sources-article-code-11"}>NO_ANSWER</code>,{" "}
            <code id={"information-request-external-sources-article-code-12"}>NOT_COMPARABLE</code> (the Requirement collects no Field, or the value no longer fits
            it), <code id={"information-request-external-sources-article-code-13"}>FIELD_CHANGED</code>, or <code id={"information-request-external-sources-article-code-14"}>EXPIRED</code>. <code id={"information-request-external-sources-article-code-15"}>FIELD_CHANGED</code> means an
            amendment changed the Field the Requirement collects after the value was recorded: the value
            keeps its type, source, provenance and decision but is never compared with an answer to the
            new Field. Values are compared by meaning, so the same number or moment written differently
            still matches. A value is compared only with an answer you may see; any other value comes back
            only when it is <code id={"information-request-external-sources-article-code-16"}>NOT_COMPARABLE</code>, <code id={"information-request-external-sources-article-code-17"}>FIELD_CHANGED</code> or{" "}
            <code id={"information-request-external-sources-article-code-18"}>EXPIRED</code>, which say nothing about the answer. A difference is kept as a
            discrepancy, once for each version of the answer. Every reconciliation run records its own audit event,
            including a run that finds no new discrepancy. Removed group occurrences are left out.
            Resolve it with{" "}
            <code id={"information-request-external-sources-article-code-19"}>POST
                /information-requests/{"{id}"}/imported-value-discrepancies/{"{discrepancyId}"}/resolutions</code>{" "}
            as <code id={"information-request-external-sources-article-code-20"}>RESPONSE_STANDS</code> or <code id={"information-request-external-sources-article-code-21"}>FOLLOW_UP_REQUESTED</code>, with a reason. Neither
            changes the answer; to ask the respondent for a change, use a correction or a supplemental
            request.
        </p>

        <h3 id={"information-request-external-sources-connectors-heading"}>Connectors</h3>
        <p id={"information-request-external-sources-connectors-help"}>
            <code id={"information-request-external-sources-article-code-22"}>POST /information-requests/{"{id}"}/connector-exchanges</code> asks an installed
            connector to look something up for one Requirement. It names the connector and an optional
            lookup reference, which is the only request information the connector receives. DocuHyphen
            calls the connector in the background and checks back until it answers, retrying a failed
            call at most ten times, including attempts whose result could not be recorded. The request&apos;s audit
            history records each call, each wait
            the connector asks for, each retry, and how the lookup ended, with its failure code when it
            failed, as a change made by the system. A step that cannot be recorded is not applied, and
            the lookup is tried again later. A result is recorded as imported values with the connector
            as their source. A result that names a value the connector does not declare, is older than
            the connector allows, or does not fit the Requirement is refused and records no value. If
            the request stops taking values, its execution grant is revoked, or its owner is suspended
            before the connector is called or before its result is recorded, or its group occurrence was removed, the
            lookup ends as failed
            and records no value. One connector works on one Requirement at a time. DocuHyphen ships no connector, so
            this list
            is empty unless your deployment installs one.
        </p>

        <h3 id={"information-request-external-sources-outputs-heading"}>Generated outputs</h3>
        <p id={"information-request-external-sources-outputs-help"}>
            <code id={"information-request-external-sources-article-code-23"}>POST /information-requests/{"{id}"}/generated-outputs</code> records where an output
            produced outside DocuHyphen lives: its reference, an optional submission it was made from,
            an optional SHA-256 hash and media type, what produced it, and when. DocuHyphen does not
            create, fetch, or store the output.
        </p>

        <h3 id={"information-request-external-sources-access-heading"}>Who can use them</h3>
        <p id={"information-request-external-sources-access-help"}>
            People who manage the request, such as the Exchange owner or its Decision Maker, record
            values, ask connectors, and record outputs. Reviewers decide, reconcile, and resolve.
            Respondents never see imported values. All of this works while the request is open and
            after it closes, while its Exchange is active and its execution grant still allows the
            request&apos;s work to continue. New values and lookups refuse removed group occurrences. Authorized
            record reads remain available after the Exchange stops, including the owner&apos;s retained reads after
            deletion. External records are part of the
            request&apos;s record export and are removed with the request when it is disposed of.
        </p>
    </>
);
