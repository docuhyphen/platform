export const informationRequestTemplatesArticle = (
    <>
        <p>
            Information Request Templates define reusable prompts for collecting typed
            information from a participant. A Template points at a published Information
            Request schema and maps each request requirement to one collected Field.
        </p>

        <h3>Opening the Template list</h3>
        <p>
            Open <b>Settings</b>, then select <b>Content - Information Requests</b>.
            The Personal plan includes Information Requests, and an active organization grant also
            provides access without a separate user grant. Use <b>My Templates</b> for personally
            owned Templates, <b>Organization</b> for the active organization, and <b>Platform</b>
            for read-only platform Templates. Organization owners and administrators use the
            organization&apos;s entitlement automatically.
        </p>

        <h3>Drafting and publishing</h3>
        <ul>
            <li>
                Select <b>New Template</b> to create a draft in the active scope.
            </li>
            <li>
                Open a draft, choose an available personal or platform Information Request schema, then choose
                the Field that will collect the answer.
            </li>
            <li>
                Enter a stable Requirement Key and Prompt, then select <b>Save draft</b>.
            </li>
            <li>
                Conditional Requirements must reference a condition rule saved in the
                same Template Version. The server validates rule keys, supported
                operators, unknown values, and dependency cycles before saving.
            </li>
            <li>
                A conditional Requirement that is answered once per repeatable group
                occurrence is evaluated separately for each occurrence. Its rule reads that
                occurrence&apos;s own answers first, then its ancestor group answers, then
                answers collected outside any group, so nested branches stay independent.
            </li>
            <li>
                Select <b>Publish draft</b> when the saved request configuration is ready
                to use.
            </li>
        </ul>

        <h3>Unavailable controls</h3>
        <p>
            Some authoring controls can be visible but disabled when the deployment does
            not support that policy yet. The disabled control includes the reason returned
            by the server, so administrators can tell whether the missing control is a
            plan issue or an unavailable platform capability.
        </p>

        <h3>Responding</h3>
        <p>
            A respondent opens the Information Request link, verifies their contact code,
            then fills the requested Fields and files in the response workspace. Saving writes the
            current response draft and reloads the saved request state. Retrying the same
            save confirms that saved revision only when the respondent still has access; it
            does not reveal answers recorded later by another participant.
        </p>
        <p>
            Repeatable groups can be nested. Add, remove, and reorder controls act within
            the selected parent occurrence, so adding an entry under one repeated item does
            not change a sibling item&apos;s entries.
        </p>
        <p>
            Structured progress counts a Field Requirement complete only when that exact
            collected Field has a current answer, or when the respondent saves an allowed
            exception such as waived or unavailable. Empty saves and cleared Fields remain
            drafts, but they are not counted as complete.
        </p>
        <p>
            Once a request has been issued, existing response work uses the Exchange
            owner&apos;s frozen request grant. A signed-in respondent can open and save assigned
            Requirements even when their own account is on Free. Later owner entitlement
            changes do not hide that issued workspace, but an operational suspension or an
            explicit request grant revocation still stops access. Drafting and issuing new
            requests still require the owner&apos;s current Information Requests access.
            The same frozen grant sets the request&apos;s acting-party capacity: issuing consumes
            one slot for each active acting party, later assignments use any remaining slots,
            and revoking an acting party frees that slot for reuse.
        </p>
        <p>
            Verification creates a separate session in that browser tab, valid for up to
            24 hours or until the access link expires, whichever comes first. Forwarding
            the original link does not share the verified session. A new browser must verify
            its own contact code. Revoking or replacing the link ends its existing sessions;
            an expired session requires verification again.
        </p>
        <p>
            Repeated invalid contact codes temporarily lock verification for that access
            link. A link can send at most three contact codes; its owner must rotate the
            link to allow another challenge. Resending never clears failed attempts.
            When a link has a use limit, each successful verification that creates a
            respondent session consumes one link use; normal saves and reads use the
            verified session instead of consuming additional link uses.
        </p>
        <p>
            If the assigned participant later signs in with a verified linked account, that
            account can read and respond to the assigned Requirements. When a Requirement is
            assigned to a group, only current active group members can act for that assignment;
            removed members and unrelated accounts cannot.
            Reassigning or revoking a respondent ends their existing access links and sessions
            while keeping the response history already recorded for the request.
        </p>

        <h3 id={"information-request-conditional-answers-heading"}>Conditional answers</h3>
        <p id={"information-request-conditional-visibility-help"}>
            A conditional Requirement is shown only while its condition is true. False or
            unknown conditions keep its answers out of the active response workspace,
            including saved Field values. Each repeated occurrence is evaluated separately.
        </p>
        <p id={"information-request-conditional-clearing-help"}>
            Retention policies keep hidden answers in the stored record. When the Template
            requires confirmed clearing, confirm the affected Requirements before saving.
            Clearing empties their current Fields and response data while preserving earlier
            Field revisions. When a condition becomes true again, retained or archived
            answers return to the active workspace. Cleared answers return as empty and do
            not restore cleared values.
        </p>

        <h3>When the parent Exchange stops</h3>
        <ul>
            <li>
                Rejecting or rescinding the Exchange cancels every Information Request on it
                that has not already finished, ends all respondent sessions, and stops
                respondent access to the request. The recorded request history is kept.
            </li>
            <li>
                Deleting the Exchange ends respondent access immediately and leaves each
                request in the state it had reached, so the record stays readable to the
                Exchange owner.
            </li>
            <li>
                Ending a completed Exchange keeps existing requests readable for the owner
                and the assigned parties, but no further responses can be saved.
            </li>
            <li>
                A request command that is already in flight when the Exchange stops is
                refused rather than partly applied, and the Exchange lists only the requests
                the current viewer is still allowed to read.
            </li>
        </ul>
    </>
);
