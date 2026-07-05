export const manageAccessArticle = (
    <>
        <p>
            This guide explains how exchange access works, including roles, constraints,
            document permissions, and exchange settings.
        </p>

        <h3>Roles</h3>
        <p>
            Each person in an exchange is assigned a role that determines what they can do.
            The exchange creator is always the <strong>Owner</strong>.
        </p>
        <ul>
            <li>
                <strong>Owner</strong> - Full control over the exchange. Can manage access, edit settings,
                add/remove documents, rescind or end the exchange, and manage its final lifecycle state.
                This role is structural and cannot be assigned or revoked.
            </li>
            <li>
                <strong>Editor</strong> - Can add, update, upload, and manage documents within the exchange.
                Editors have broad document-level access but cannot manage exchange access or settings.
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
                <strong>Viewer</strong> - Read-only access to exchange documents. Cannot modify documents.
                Supports additional constraints such as download restrictions and watermarking.
            </li>
            <li>
                <strong>Commenter</strong> - Can view documents and leave comments or notes, but cannot
                modify documents or exchange settings.
            </li>
            <li>
                <strong>Participant</strong> - General participant access. Can view documents with optional
                constraints applied. The most flexible role for external collaborators.
            </li>
        </ul>

        <h3>Internal document notes</h3>
        <p>
            When adding a note from a document's Notes tab, select <strong>Internal note</strong> to make
            it visible only to active members of your selected organization. Internal notes are marked
            <strong>Internal</strong>. Other Exchange participants cannot see them.
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
                exchange link to others.
            </li>
            <li>
                <strong>Watermark</strong> - Overlays a watermark on document previews to discourage
                unauthorised screenshots or distribution.
            </li>
            <li>
                <strong>Require MFA</strong> - Requires the participant to complete multi-factor
                authentication before accessing exchange documents.
            </li>
        </ul>

        <h3>Adding a person</h3>
        <p>
            In Access &amp; permissions, choose <strong>Add person</strong> and search
            by name or email. Matching contacts appear with their name, email, and
            avatar or initials. You can also enter a complete email address when the
            person is not yet in your contacts. Choose an access role and any allowed
            constraints before adding them.
        </p>

        <h3>Document permissions</h3>
        <p>
            Document permissions are exchange-wide settings that control what actions recipients and
            participants can perform on documents.
        </p>
        <ul>
            <li><strong>Allow document additions</strong> - Participants with the appropriate role can add new documents to the exchange.</li>
            <li><strong>Allow document deletions</strong> - Participants can remove documents from the exchange.</li>
            <li><strong>Allow document zip download</strong> - Participants can download all exchange documents as a single zip archive.</li>
            <li><strong>Allow document update</strong> - Participants can modify or replace existing document metadata.</li>
            <li><strong>Allow document upload</strong> - Participants can upload new file versions for existing documents.</li>
        </ul>

        <h3>Exchange settings</h3>
        <ul>
            <li>
                <strong>Require recipient sign in</strong> - When enabled, the recipient must sign in with
                their account to access the exchange. When disabled, the recipient can use a one-time email
                access code (OTP) instead, which is useful for external parties without an account.
            </li>
            <li>
                <strong>Send access code</strong> - When sign-in is not required, a one-time access code can
                be sent to the recipient's email. The code expires after the configured number of days.
            </li>
            <li>
                <strong>No-auth access validity</strong> - Sets how many days the one-time access code
                remains valid (1–30 days). After expiry, a new code must be sent.
            </li>
        </ul>

        <h3>Summary tab</h3>
        <p>
            The Summary tab shows all people involved in the exchange: the <strong>Requester</strong> (who
            initiated the exchange), the <strong>Primary recipient</strong> (the main person the exchange
            was shared with), and any additional <strong>Participants</strong> (users or groups added
            through the Access &amp; permissions tab).
        </p>

        <p>
            <em>Changes to access and permissions take effect immediately after saving. Protected entries
            (your own access and the exchange owner) cannot be modified or revoked. Once an exchange is
            rescinded or ended, document and access changes are no longer available.</em>
        </p>
    </>
);
