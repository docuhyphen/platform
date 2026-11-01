export const informationRequestAccessArticle = (
    <>
        <p id={"information-request-access-intro"}>
            A party can respond while signed in, or without an account through an access link that the
            request&apos;s manager shares with them. Both open the same workspace with the same items,
            answers, and actions.
        </p>

        <h3 id={"information-request-access-verification-heading"}>Verifying an access link</h3>
        <p id={"information-request-access-verification-help"}>
            Opening the link asks for a contact code, sent to the party&apos;s contact address. Verifying
            creates a session in that browser tab, valid for up to 24 hours or until the access link
            expires, whichever comes first. Forwarding the original link does not share the verified
            session: a new browser must verify its own contact code. Resending or revoking the link ends
            its sessions, and an expired session asks for verification again. If you open the request&apos;s address
            without the token its link carried, for example from a bookmark or in another browser, the page says the
            link
            is incomplete: open the link you were sent again, in full. A change is saved only while its
            session is still current: one sent as the session ends is refused and nothing of it is saved. A page
            you read, or a file you download or preview, reaches you only if the session is still current once it
            has been read. Answers not yet saved are
            kept in the tab while you verify again, and so is a file that was being sent when the
            session ended: after you verify, select <b id={"information-request-access-article-b-1"}>Retry</b> next to it to send it once. Opening
            another request&apos;s link in the same tab shows only that request, once its own link is
            verified. After you open a newer link for the same request, or verify again once a session ended, the
            session you verified last is kept: a verification or session end that arrives late from the earlier link
            or session, or from a page you have left, neither replaces nor ends it. When the Exchange is set to
            require recipients to sign in, every access link and session on its requests stops working, and parties
            respond with their accounts instead. Someone added by email who never linked an account can then no longer
            link one from the request: ask the request&apos;s manager to assign your part to an account that is a
            recipient of the Exchange.
        </p>
        <p id={"information-request-access-drafts-help"}>
            Answers not yet saved belong to the party who entered them, not to the browser tab. Another party&apos;s
            access link or another account opened in the same tab neither shows nor saves them, and a save the platform
            finds was entered by someone else is refused with nothing saved; they stay in the tab for the person who
            entered them. Verifying again, or opening a newer link for the same party, shows them as before. Linking
            your account moves them to that account.
        </p>
        <p id={"information-request-access-respondent-change-help"}>
            When the workspace finds that another party or account is now responding in the tab, everything it showed
            for the earlier one closes, including <b id={"information-request-access-article-b-2"}>From your previous submission</b>, earlier accepted answers,
            <b id={"information-request-access-article-b-3"}> What changed in this request</b>, <b id={"information-request-access-article-b-4"}>Review results</b>, <b id={"information-request-access-article-b-5"}>Review and submit</b>, an open dialog,
            and a file kept to send again. It then shows only what the current respondent may see, and nothing still
            arriving for the earlier one is shown or opened. A file kept to send again stays with the party who chose
            it, like unsaved answers.
        </p>
        <p id={"information-request-access-codes-help"}>
            Repeated invalid contact codes temporarily lock verification for that link. A link can send
            at most three contact codes before one of them is verified; a successful verification lets it
            send three more, and until the link expires its manager can also resend it to allow another
            challenge. Resending a code never clears failed attempts. Each link works for 30 days
            from when it was created and for 25 verifications, counting only successful ones; saving and
            reading use the verified session instead. A resent link keeps the original expiry and starts
            its 25 verifications again. After a link expires, its manager revokes it and creates a new
            link. Replacing a link in one step, or giving a link a different lifetime or number of verifications, is
            available only through the API. Too many verification attempts from one network are paused: for a minute at
            first, then for longer
            each time they continue, up to an hour, and the page asks you to try again later. The platform
            returns the remaining wait with a paused verification attempt.
        </p>

        <h3 id={"information-request-access-protected-heading"}>Protected questions</h3>
        <p id={"information-request-access-protected-help"}>
            A question in a confidentiality compartment is protected: you can see and answer it only
            shortly after verifying. With an access link, protected questions stay open for 15 minutes
            after you verify the link or verify again. After that, a change to a protected question is refused and
            nothing of it is saved, even if the question was still on screen, and a page read as that time passes
            shows it hidden. If that happens while you are answering, the page reloads to hide the question, saves
            your other answers, and keeps your answer to it in the tab until you verify again. The workspace says
            how many are hidden; select <b id={"information-request-access-article-b-6"}>Send verification code</b>, then enter
            the new code sent to your contact address and select <b id={"information-request-access-article-b-7"}>Verify</b>. Signed-in parties select <b id={"information-request-access-article-b-8"}>Verify
            again</b> and confirm with their account; that verification lasts 5 minutes. Repeated invalid codes pause
            verifying again for a few minutes, and a session can have at most five unused codes. Verifying again
            does not use the link&apos;s verifications.
        </p>

        <h3 id={"information-request-access-accounts-heading"}>Signed-in parties and groups</h3>
        <p id={"information-request-access-accounts-help"}>
            After verifying an access link, a participant who has an account with the same email address can move the
            request to that account. While signed in, select <b id={"information-request-access-article-b-9"}>Link to my account</b>. If you are not signed
            in, <b id={"information-request-access-article-b-10"}>Sign in</b> opens in a new tab; once you have signed in there, select <b id={"information-request-access-article-b-11"}>I have signed in</b> on
            the request. The request then opens in your signed-in workspace. Every part of the request you hold through
            that contact address moves to the account, and the access links and sessions of each part stop working. If
            linking is interrupted, select <b id={"information-request-access-article-b-12"}>Link to my account</b> again: an account link already made is
            returned and the request opens as before, while the ended session cannot start anything else. If the page
            asks you to verify again after an interrupted link, <b id={"information-request-access-article-b-13"}>Link to my account</b> stays there: select it to
            finish the link. If the link was not made, it then disappears and you verify again. If you leave the
            request before linking finishes, you stay on the page you went to; if the link was made, you then open the
            request from your account. Once linked, you sign
            in to respond: no new access link can be created for the parts you linked, and an access link created
            earlier asks you to sign in instead. A part assigned to you later gets its own access link; linking it the
            same way adds it to the same account. Linking is refused when the account&apos;s email address
            differs, when the participant is already linked to a different account, when the account already has its
            own access to the request, while a trusted relationship behind the request is suspended, when the
            request&apos;s owner does not permit sharing with the account or its organization, once the Exchange
            requires recipients to sign in, while changes to the request are paused, or when the contact address must
            be verified again.
        </p>
        <p id={"information-request-access-groups-help"}>
            When an item is assigned to a group, only its current active members can act for that assignment; removed
            members and unrelated accounts cannot. While a trusted relationship behind the group is suspended or no
            longer eligible, a member who joins the group cannot act for it or open the request through it until trust
            is restored; members who could act before keep working. Reassigning or removing a party ends its access
            links, its sessions, and its linked account&apos;s access, unless that account also holds another party on
            the request, while keeping the response history already recorded. Deleting or deactivating a group removes
            every party it holds the same way, and each request&apos;s history records the removal. Removing
            someone&apos;s
            access to an Exchange does not remove them from its Information Requests: remove or reassign their party on
            each request.
        </p>
        <p id={"information-request-access-retained-account-help"}>
            Removing an organization member cannot delete their account while an account link or retained personal
            Fields, Schemas, Templates, contacts, or Information Requests still refer to it. The removal is refused.
        </p>
        <p id={"information-request-access-ending-help"}>
            Once a request finishes or its Exchange ends, no new verification code or session is issued for it, and a
            session
            already open keeps reading it until the session expires. Cancelling or replacing a request also ends its
            access
            links, and rejecting, rescinding, or deleting its Exchange ends its access links and sessions at once.
        </p>
        <p id={"information-request-access-refused-help"}>
            When a request can no longer be opened, for example because your access was removed or the Exchange now
            requires recipients to sign in, its workspace closes and says why, and nothing more can be changed there.
            Answers you have not saved stay in the browser tab; select <b id={"information-request-access-article-b-14"}>Try again</b> once you can open the request
            again, and they are shown as before.
        </p>

        <h3 id={"information-request-access-plans-heading"}>Plans and capacity</h3>
        <p id={"information-request-access-plans-help"}>
            Once a request is issued, response work uses the owner&apos;s request grant. A signed-in
            party can open and save assigned items even when their own account is on Free. Later changes
            to the owner&apos;s plan do not hide an issued workspace. When changes are paused or the
            request is stopped, no further answers or changes are accepted, but everything already
            recorded stays readable. Creating and issuing new requests still need
            the owner&apos;s current Information Requests access. The same grant sets how many acting
            parties a request can have: issuing uses one place for each active acting party, later
            assignments use any places left, and removing an acting party frees its place.
        </p>
    </>
);
