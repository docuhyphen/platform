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
            session: a new browser must verify its own contact code. Replacing or revoking the link ends
            its sessions, and an expired session asks for verification again. Answers not yet saved are
            kept in the tab while you verify again.
        </p>
        <p id={"information-request-access-codes-help"}>
            Repeated invalid contact codes temporarily lock verification for that link. A link can send
            at most three contact codes; its manager must resend the link to allow another challenge.
            Resending a code never clears failed attempts. When a link has a use limit, each successful
            verification uses one; saving and reading use the verified session instead. A link created
            without its own expiry or use limit works for 30 days and for 25 verifications. Too many
            verification attempts from one network are paused for about a minute.
        </p>

        <h3 id={"information-request-access-accounts-heading"}>Signed-in parties and groups</h3>
        <p id={"information-request-access-accounts-help"}>
            If the assigned party later signs in with a verified linked account, that account can read
            and respond to the assigned items. When an item is assigned to a group, only its current
            active members can act for that assignment; removed members and unrelated accounts cannot.
            Reassigning or removing a party ends their access links and sessions while keeping the
            response history already recorded.
        </p>

        <h3 id={"information-request-access-plans-heading"}>Plans and capacity</h3>
        <p id={"information-request-access-plans-help"}>
            Once a request is issued, response work uses the owner&apos;s request grant. A signed-in
            party can open and save assigned items even when their own account is on Free. Later changes
            to the owner&apos;s plan do not hide an issued workspace. An operational suspension or an
            explicit grant revocation stops further answers and changes, but everything already recorded
            stays readable. Creating and issuing new requests still need
            the owner&apos;s current Information Requests access. The same grant sets how many acting
            parties a request can have: issuing uses one place for each active acting party, later
            assignments use any places left, and removing an acting party frees its place.
        </p>
    </>
);
