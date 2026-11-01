export const manageAccessArticle = (
    <>
        <p id={"manage-access-intro-paragraph"}>
            This guide explains how Exchange access works, including roles, constraints,
            document permissions, and Exchange settings.
        </p>
        <h3 id={"manage-access-roles-heading"}>Roles</h3>
        <p id={"manage-access-roles-paragraph"}>
            Each person in an Exchange is assigned a role that determines what they can do.
            The Exchange creator is always the <strong id={"manage-access-article-strong-1"}>Owner</strong>.
        </p>
        <ul id={"manage-access-roles-list"}>
            <li id={"manage-access-article-li-1"}>
                <strong id={"manage-access-article-strong-2"}>Owner</strong> - Full control over the Exchange. Can manage access, edit settings,
                add/remove documents, rescind or end the Exchange, and manage its final lifecycle state.
                This role is structural and cannot be assigned or revoked.
            </li>
            <li id={"manage-access-article-li-2"}>
                <strong id={"manage-access-article-strong-3"}>Editor</strong> - Can add, update, upload, and manage documents within the Exchange.
                Editors have broad document-level access but cannot manage Exchange access or settings.
            </li>
            <li id={"manage-access-article-li-3"}>
                <strong id={"manage-access-article-strong-4"}>Reviewer</strong> - Can view all documents and leave comments or notes. Typically
                used for approval workflows or document review processes.
            </li>
            <li id={"manage-access-article-li-4"}>
                <strong id={"manage-access-article-strong-5"}>Signer</strong> - Intended for participants who need to sign or formally acknowledge
                documents. Has read access plus signing capabilities.
            </li>
            <li id={"manage-access-article-li-5"}>
                <strong id={"manage-access-article-strong-6"}>Viewer</strong> - Read-only access to Exchange documents. Cannot modify documents.
                Supports additional constraints such as download restrictions and watermarking.
            </li>
            <li id={"manage-access-article-li-6"}>
                <strong id={"manage-access-article-strong-7"}>Commenter</strong> - Can view documents and leave comments or notes, but cannot
                modify documents or Exchange settings.
            </li>
            <li id={"manage-access-article-li-7"}>
                <strong id={"manage-access-article-strong-8"}>Participant</strong> - General participant access. Can view documents with optional
                constraints applied. The most flexible role for external collaborators.
            </li>
        </ul>
        <h3 id={"manage-access-internal-document-notes-heading"}>Internal document notes</h3>
        <p id={"manage-access-internal-document-notes-paragraph"}>
            When adding a note from a document's comments area or enlarged reader, select <strong id={"manage-access-article-strong-9"}>Internal</strong> to
            make it visible only to active members of your selected organization who can access the Exchange. The option
            is hidden when no organization is selected. Other Exchange participants cannot see internal notes.
        </p>
        <h3 id={"manage-access-constraints-heading"}>Constraints</h3>
        <p id={"manage-access-constraints-paragraph"}>
            Constraints are additional restrictions applicable to <strong id={"manage-access-article-strong-10"}>Viewer</strong> and{' '}
            <strong id={"manage-access-article-strong-11"}>Participant</strong> roles to control how they interact with documents. They require
            advanced access controls in the Exchange owner's Personal or Business plan.
        </p>
        <ul id={"manage-access-constraints-list"}>
            <li id={"manage-access-article-li-8"}>
                <strong id={"manage-access-article-strong-12"}>No bulk document download</strong> - Prevents the participant from downloading
                documents in bulk (zip download). Documents can still be previewed in the browser.
            </li>
            <li id={"manage-access-article-li-9"}>
                <strong id={"manage-access-article-strong-13"}>No reshare</strong> - Prevents the participant from sharing or forwarding the
                Exchange link to others.
            </li>
            <li id={"manage-access-article-li-10"}>
                <strong id={"manage-access-article-strong-14"}>Watermark</strong> - Overlays a watermark on document previews to discourage
                unauthorised screenshots or distribution.
            </li>
            <li id={"manage-access-article-li-11"}>
                <strong id={"manage-access-article-strong-15"}>Require MFA</strong> - Requires the participant to complete multi-factor
                authentication before accessing Exchange documents.
            </li>
        </ul>
        <h3 id={"manage-access-adding-a-person-heading"}>Adding a person</h3>
        <p id={"manage-access-adding-a-person-paragraph"}>
            In <strong id={"manage-access-article-strong-16"}>Manage access</strong>, open <strong id={"manage-access-article-strong-17"}>Access &amp; permissions</strong> and choose{" "}
            <strong id={"manage-access-article-strong-18"}>Add person</strong>. Use <strong id={"manage-access-article-strong-19"}>Person</strong> to search contacts or enter a complete email
            address, then choose an access role and allowed constraints.
            If the email address does not belong to an existing account, DocuHyphen sends an account-required
            invitation and keeps the Exchange access attached to that email after the person signs up.
        </p>
        <p id={"manage-access-adding-a-person-paragraph-2"}>
            Use <strong id={"manage-access-article-strong-20"}>Trusted Organization</strong> to add a verified member or published group from an active
            relationship. Choose the organization, verify the exact member email or select a group, and choose the
            role. The resulting Share stays inactive until the person, or a current group Owner or Manager, accepts
            the separate invitation in Requests. Declining affects only that participant invitation.
        </p>

        <h3 id={"manage-access-document-permissions-heading"}>Document permissions</h3>
        <p id={"manage-access-document-permissions-paragraph"}>
            Document permissions are Exchange-wide settings that control what actions recipients and
            participants can perform on documents.
        </p>
        <ul id={"manage-access-document-permissions-list"}>
            <li id={"manage-access-article-li-12"}><strong id={"manage-access-article-strong-21"}>Allow document additions</strong> - Participants with the appropriate role can add new documents to the Exchange.</li>
            <li id={"manage-access-article-li-13"}><strong id={"manage-access-article-strong-22"}>Allow document deletions</strong> - Participants can remove documents from the Exchange.</li>
            <li id={"manage-access-article-li-14"}><strong id={"manage-access-article-strong-23"}>Allow document zip download</strong> - Participants can download all Exchange documents as a single zip archive.</li>
            <li id={"manage-access-article-li-15"}><strong id={"manage-access-article-strong-24"}>Allow document update</strong> - Participants can modify or replace existing document metadata.</li>
            <li id={"manage-access-article-li-16"}><strong id={"manage-access-article-strong-25"}>Allow document upload</strong> - Participants can upload new file versions for existing documents.</li>
        </ul>

        <h3 id={"manage-access-exchange-settings-heading"}>Exchange settings</h3>
        <ul id={"manage-access-exchange-settings-list"}>
            <li id={"manage-access-article-li-17"}>
                <strong id={"manage-access-article-strong-26"}>Require recipient sign in</strong> - When enabled, the recipient must sign in with
                their account to access the Exchange. When disabled, the recipient must open the secure
                email link and enter its one-time access code, which is useful for external parties without
                an account. The link credential is required on every no-sign-in request.
            </li>
            <li id={"manage-access-article-li-18"}>
                <strong id={"manage-access-article-strong-27"}>Resend invitation</strong> and <strong id={"manage-access-article-strong-28"}>Send access code</strong> - When sign-in is
                not required, send a fresh email link and one-time access code to the recipient's email.
            </li>
            <li id={"manage-access-article-li-19"}>
                <strong id={"manage-access-article-strong-29"}>No-auth access validity</strong> - Sets how many days the one-time access code
                verification remains valid (1 to 30 days). The countdown starts when the recipient verifies
                the code, either by accepting the Exchange or by entering a resent code. After expiry the
                recipient sees an <strong id={"manage-access-article-strong-30"}>Access verification required</strong> prompt on the request page,
                and a newly sent code entered there restores their access.
            </li>
        </ul>

        <h3 id={"manage-access-summary-tab-heading"}>Summary tab</h3>
        <p id={"manage-access-summary-tab-paragraph"}>
            The Summary tab shows all people involved in the Exchange: the <strong id={"manage-access-article-strong-31"}>Requester</strong> (who
            initiated the Exchange), the <strong id={"manage-access-article-strong-32"}>Primary recipient</strong> (the main person the Exchange
            was shared with), and any additional <strong id={"manage-access-article-strong-33"}>Participants</strong> (users or groups added
            through the Access &amp; permissions tab). Free supports one primary recipient; Personal and
            Business add participants. External and no-account primary recipients remain available on every plan.
        </p>

        <h3 id={"manage-access-replacing-a-pending-primary-heading"}>Replacing a pending primary recipient</h3>
        <p id={"manage-access-replacing-a-pending-primary-paragraph"}>
            While an Exchange is still a draft awaiting acceptance, the owner can select{" "}
            <strong id={"manage-access-article-strong-34"}>Replace recipient</strong> on the primary recipient card. This is used to recover an
            invitation whose trusted verification, membership, relationship, or policy is no longer valid.
            Replacement supports only a newly verified member of a Trusted Organization or one of its
            published groups. People, external email invitations, and internal or personal groups cannot
            replace the primary recipient through this recovery action. The previous invitation is withdrawn,
            and the new trusted recipient's access stays inactive until they sign in and accept. Replacement
            is unavailable once the primary recipient has accepted.
        </p>

        <p id={"manage-access-replacing-a-pending-primary-paragraph-2"}>
            <em id={"manage-access-article-em-1"}>Ordinary access changes take effect after saving. Trusted participant invitations remain inactive
            until accepted. Protected entries (your own access and the Exchange owner) cannot be modified or revoked.
            Once an Exchange is rescinded or ended, document and access changes are no longer available.</em>
        </p>
    </>
);
