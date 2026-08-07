export const manageAccessArticle = (
    <>
        <p>
            This guide explains how Exchange access works, including roles, constraints,
            document permissions, and Exchange settings.
        </p>

        <h3>Roles</h3>
        <p>
            Each person in an Exchange is assigned a role that determines what they can do.
            The Exchange creator is always the <strong>Owner</strong>.
        </p>
        <ul>
            <li>
                <strong>Owner</strong> - Full control over the Exchange. Can manage access, edit settings,
                add/remove documents, rescind or end the Exchange, and manage its final lifecycle state.
                This role is structural and cannot be assigned or revoked.
            </li>
            <li>
                <strong>Editor</strong> - Can add, update, upload, and manage documents within the Exchange.
                Editors have broad document-level access but cannot manage Exchange access or settings.
            </li>
            <li>
                <strong>Reviewer</strong> - Can view all documents and leave comments or notes. Typically
                used for approval workflows or document review processes.
            </li>
            <li>
                <strong>Signer</strong> - Intended for participants who need to sign or formally acknowledge
                documents. Has read access plus signing capabilities.
            </li>
            <li>
                <strong>Viewer</strong> - Read-only access to Exchange documents. Cannot modify documents.
                Supports additional constraints such as download restrictions and watermarking.
            </li>
            <li>
                <strong>Commenter</strong> - Can view documents and leave comments or notes, but cannot
                modify documents or Exchange settings.
            </li>
            <li>
                <strong>Participant</strong> - General participant access. Can view documents with optional
                constraints applied. The most flexible role for external collaborators.
            </li>
        </ul>

        <h3>Internal document notes</h3>
        <p>
            When adding a note from a document's comments area or enlarged reader, select <strong>Internal</strong> to
            make it visible only to active members of your selected organization who can access the Exchange. The option
            is hidden when no organization is selected. Other Exchange participants cannot see internal notes.
        </p>

        <h3>Constraints</h3>
        <p>
            Constraints are additional restrictions applicable to <strong>Viewer</strong> and{' '}
            <strong>Participant</strong> roles to control how they interact with documents.
        </p>
        <ul>
            <li>
                <strong>No bulk document download</strong> - Prevents the participant from downloading
                documents in bulk (zip download). Documents can still be previewed in the browser.
            </li>
            <li>
                <strong>No reshare</strong> - Prevents the participant from sharing or forwarding the
                Exchange link to others.
            </li>
            <li>
                <strong>Watermark</strong> - Overlays a watermark on document previews to discourage
                unauthorised screenshots or distribution.
            </li>
            <li>
                <strong>Require MFA</strong> - Requires the participant to complete multi-factor
                authentication before accessing Exchange documents.
            </li>
        </ul>

        <h3>Adding a person</h3>
        <p>
            In <strong>Manage access</strong>, open <strong>Access &amp; permissions</strong> and choose
            <strong>Add person</strong>. Use <strong>Person</strong> to search contacts or enter a complete email
            address, then choose an access role and allowed constraints.
            If the email address does not belong to an existing account, DocuHyphen sends an account-required
            invitation and keeps the Exchange access attached to that email after the person signs up.
        </p>
        <p>
            Use <strong>Trusted Organization</strong> to add a verified member or published group from an active
            relationship. Choose the organization, verify the exact member email or select a group, and choose the
            role. The resulting Share stays inactive until the person, or a current group Owner or Manager, accepts
            the separate invitation in Requests. Declining affects only that participant invitation.
        </p>

        <h3>Document permissions</h3>
        <p>
            Document permissions are Exchange-wide settings that control what actions recipients and
            participants can perform on documents.
        </p>
        <ul>
            <li><strong>Allow document additions</strong> - Participants with the appropriate role can add new documents to the Exchange.</li>
            <li><strong>Allow document deletions</strong> - Participants can remove documents from the Exchange.</li>
            <li><strong>Allow document zip download</strong> - Participants can download all Exchange documents as a single zip archive.</li>
            <li><strong>Allow document update</strong> - Participants can modify or replace existing document metadata.</li>
            <li><strong>Allow document upload</strong> - Participants can upload new file versions for existing documents.</li>
        </ul>

        <h3>Exchange settings</h3>
        <ul>
            <li>
                <strong>Require recipient sign in</strong> - When enabled, the recipient must sign in with
                their account to access the Exchange. When disabled, the recipient must open the secure
                email link and enter its one-time access code, which is useful for external parties without
                an account. The link credential is required on every no-sign-in request.
            </li>
            <li>
                <strong>Resend invitation</strong> and <strong>Send access code</strong> - When sign-in is
                not required, send a fresh email link and one-time access code to the recipient's email.
            </li>
            <li>
                <strong>No-auth access validity</strong> - Sets how many days the one-time access code
                verification remains valid (1 to 30 days). The countdown starts when the recipient verifies
                the code, either by accepting the Exchange or by entering a resent code. After expiry the
                recipient sees an <strong>Access verification required</strong> prompt on the request page,
                and a newly sent code entered there restores their access.
            </li>        </ul>

        <h3>Summary tab</h3>
        <p>
            The Summary tab shows all people involved in the Exchange: the <strong>Requester</strong> (who
            initiated the Exchange), the <strong>Primary recipient</strong> (the main person the Exchange
            was shared with), and any additional <strong>Participants</strong> (users or groups added
            through the Access &amp; permissions tab).
        </p>

        <h3>Replacing a pending primary recipient</h3>
        <p>
            While an Exchange is still a draft awaiting acceptance, the owner can select
            <strong>Replace recipient</strong> on the primary recipient card. This is used to recover an
            invitation whose trusted verification, membership, relationship, or policy is no longer valid.
            Replacement supports only a newly verified member of a Trusted Organization or one of its
            published groups. People, external email invitations, and internal or personal groups cannot
            replace the primary recipient through this recovery action. The previous invitation is withdrawn,
            and the new trusted recipient's access stays inactive until they sign in and accept. Replacement
            is unavailable once the primary recipient has accepted.
        </p>

        <p>
            <em>Ordinary access changes take effect after saving. Trusted participant invitations remain inactive
            until accepted. Protected entries (your own access and the Exchange owner) cannot be modified or revoked.
            Once an Exchange is rescinded or ended, document and access changes are no longer available.</em>
        </p>
    </>
);
