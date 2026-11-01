export const trustedOrganizationsAdministrationArticle = (
    <>
        <p id={"trusted-organizations-administration-intro-paragraph"}>
            Trusted Organizations is a Business identity and integration capability that lets two active, verified
            organizations jointly approve a collaboration relationship. Each organization controls its policy and can
            suspend or end trust independently.
        </p>
        <h3 id={"trusted-organizations-administration-who-can-manage-trust-heading"}>Who can manage trust</h3>
        <p id={"trusted-organizations-administration-who-can-manage-trust-paragraph"}>
            Organization Owners and Organization Admins can view, request, decide, suspend, resume, end, and configure
            Trusted Organizations for the organization they currently have selected. App-level roles do not grant these
            permissions without an active membership in the selected organization.
        </p>
        <h3 id={"trusted-organizations-administration-allowing-other-organizations-to-heading"}>Allowing other
            organizations to find you</h3>
        <ol id={"trusted-organizations-administration-allowing-other-organizations-to-list"}>
            <li id={"trusted-organizations-administration-article-li-1"}>Open <b id={"trusted-organizations-administration-article-b-1"}>Settings</b>, then select <b id={"trusted-organizations-administration-article-b-2"}>Administration</b> under <b id={"trusted-organizations-administration-article-b-3"}>Organization</b>.</li>
            <li id={"trusted-organizations-administration-article-li-2"}>Open <b id={"trusted-organizations-administration-article-b-4"}>Details</b>.</li>
            <li id={"trusted-organizations-administration-article-li-3"}>
                Enable <b id={"trusted-organizations-administration-article-b-5"}>Allow verified organizations to find us for trust requests</b> under Organization Preferences.
            </li>
        </ol>
        <p id={"trusted-organizations-administration-allowing-other-organizations-to-paragraph"}>
            This setting is off by default. It is separate from Exchange sharing settings. Search results include only
            active, verified organizations that opted in, and organizations with a current pending or active
            relationship are excluded.
        </p><p id={"trusted-organizations-administration-allowing-other-organizations-to-paragraph-2"}>
        The separate <b id={"trusted-organizations-administration-article-b-6"}>Require a trusted organization for sharing with other organizations</b>{" "}
        preference is on by default. While it is on, a member can share an Exchange with a recipient in another
        organization only while both organizations, the relationship review, and both directional policies are
        current and eligible. Individual customers are separate. Email invitations recheck current eligibility at
        acceptance without a trusted-membership assurance.</p>
        <h3 id={"trusted-organizations-administration-sending-and-deciding-requests-heading"}>Sending and deciding
            requests</h3>
        <ol id={"trusted-organizations-administration-sending-and-deciding-requests-list"}>
            <li id={"trusted-organizations-administration-article-li-4"}>Open <b id={"trusted-organizations-administration-article-b-7"}>Settings</b>, select <b id={"trusted-organizations-administration-article-b-8"}>Administration</b> under <b id={"trusted-organizations-administration-article-b-9"}>Organization</b>, then <b id={"trusted-organizations-administration-article-b-10"}>Trusted Organizations</b>.</li>
            <li id={"trusted-organizations-administration-article-li-5"}>Select <b id={"trusted-organizations-administration-article-b-11"}>New request</b> and search by organization name.</li>
            <li id={"trusted-organizations-administration-article-li-6"}>Select the organization, add an optional message, and send the request.</li>
            <li id={"trusted-organizations-administration-article-li-7"}>The requested organization's administrators can accept or reject it.</li>
        </ol>
        <p id={"trusted-organizations-administration-sending-and-deciding-requests-paragraph"}>
            The requesting organization can withdraw a pending request. Requests expire if they are not decided within
            the configured request period. A new request after a terminal decision is subject to the configured
            cooldown.
        </p>
        <h3 id={"trusted-organizations-administration-directional-policy-heading"}>Directional policy</h3>
        <p id={"trusted-organizations-administration-directional-policy-paragraph"}>
            Every relationship has one policy owned by each organization. New policies deny all operations until each
            organization deliberately enables the directions it supports. Only your organization can edit its policy.
        </p>
        <ul id={"trusted-organizations-administration-directional-policy-list"}>
            <li id={"trusted-organizations-administration-article-li-8"}><b id={"trusted-organizations-administration-article-b-12"}>Allow Exchanges to partner</b> controls outbound Trusted Organization Exchanges.</li>
            <li id={"trusted-organizations-administration-article-li-9"}><b id={"trusted-organizations-administration-article-b-13"}>Allow Exchanges from partner</b> controls inbound Trusted Organization Exchanges.</li>
            <li id={"trusted-organizations-administration-article-li-10"}><b id={"trusted-organizations-administration-article-b-14"}>Allow exact-email member resolution</b> controls partner verification of a known member email.</li>
            <li id={"trusted-organizations-administration-article-li-11"}><b id={"trusted-organizations-administration-article-b-15"}>Allow published group discovery</b> controls partner access to published group destinations.</li>
            <li id={"trusted-organizations-administration-article-li-12"}><b id={"trusted-organizations-administration-article-b-16"}>Share resolved member display names</b> controls display-name projection after a successful exact match.</li>
        </ul>
        <h3 id={"trusted-organizations-administration-verifying-a-known-member-heading"}>Verifying a known member by
            exact email</h3>
        <ol id={"trusted-organizations-administration-verifying-a-known-member-list"}>
            <li id={"trusted-organizations-administration-article-li-13"}>Start an Exchange and open the <b id={"trusted-organizations-administration-article-b-17"}>Recipients</b> step.</li>
            <li id={"trusted-organizations-administration-article-li-14"}>Select <b id={"trusted-organizations-administration-article-b-18"}>Trusted Organization</b> and choose an active relationship.</li>
            <li id={"trusted-organizations-administration-article-li-15"}>Select <b id={"trusted-organizations-administration-article-b-19"}>Person by exact email</b>.</li>
            <li id={"trusted-organizations-administration-article-li-16"}>Enter the complete work email address and select <b id={"trusted-organizations-administration-article-b-20"}>Verify membership</b>.</li>
        </ol>
        <p id={"trusted-organizations-administration-verifying-a-known-member-paragraph"}>
            This is an exact lookup, not a directory search. Names, email prefixes, domains, and partial addresses
            cannot list or suggest members. A denied lookup uses the same message whether the account, membership,
            relationship, or policy is unavailable, so it does not reveal which fact failed.
        </p>
        <p id={"trusted-organizations-administration-verifying-a-known-member-paragraph-2"}>
            A successful result shows the normalized email, organization, verification expiry, and the member's display
            name only when the target organization's policy permits it. The assurance label is <b id={"trusted-organizations-administration-article-b-21"}>Membership verified by
            [organization]</b>. It does not claim that DocuHyphen verified the person's real-world identity.
        </p>
        <p id={"trusted-organizations-administration-verifying-a-known-member-paragraph-3"}>
            Verification is short-lived and bound to you, your selected organization, the target organization, the
            current relationship, and both policy revisions. Changing the email, target organization, active
            organization, or recipient method clears the result. An expired result must be verified again. Searched
            email addresses are sent in a JSON body, never in the URL, and are excluded from audit and security-incident
            payloads.
        </p>

        <h3 id={"trusted-organizations-administration-sending-an-exchange-to-heading"}>Sending an Exchange to a verified
            member</h3>
        <p id={"trusted-organizations-administration-sending-an-exchange-to-paragraph"}>
            After a successful verification you can send the Exchange to that member. The verified member becomes the
            recipient without your having to supply any name, organization, or membership detail yourself. The Exchange
            always requires the member to sign in and to accept, even when your organization normally starts Exchanges
            automatically. The attested account, its exact membership, both organizations, the relationship, and both
            policies are checked again when the member accepts, and acceptance fails closed if any of those is no longer
            eligible. A member can still decline a verification that has lapsed.
        </p>
        <h3 id={"trusted-organizations-administration-sending-an-exchange-to-2-heading"}>Sending an Exchange to a
            published group</h3>
        <ol id={"trusted-organizations-administration-sending-an-exchange-to-2-list"}>
            <li id={"trusted-organizations-administration-article-li-17"}>Start an Exchange and open the <b id={"trusted-organizations-administration-article-b-22"}>Recipients</b> step.</li>
            <li id={"trusted-organizations-administration-article-li-18"}>Select <b id={"trusted-organizations-administration-article-b-23"}>Trusted Organization</b>.</li>
            <li id={"trusted-organizations-administration-article-li-19"}>Select an active Trusted Organization, then choose one of its published groups.</li>
            <li id={"trusted-organizations-administration-article-li-20"}>Complete the remaining Exchange details and select <b id={"trusted-organizations-administration-article-b-24"}>Start Exchange</b>.</li>
        </ol>
        <p id={"trusted-organizations-administration-sending-an-exchange-to-2-paragraph"}>
            Published group discovery is available only when the relationship is active, neither organization has
            suspended it, both directional Exchange policies permit the operation, and the recipient organization
            permits published group discovery. The group must remain active, organization-owned, and published. The
            picker never exposes group membership.
        </p>
        <p id={"trusted-organizations-administration-sending-an-exchange-to-2-paragraph-2"}>
            A trusted group Exchange always waits for an active group Owner or Manager to accept or reject it, even when
            the initiating organization normally bypasses recipient acceptance. Group Members and Observers cannot make
            this decision. The current relationship, policies, group publication, and the decision maker's current group
            role are checked again at acceptance time.
        </p>
        <p id={"trusted-organizations-administration-sending-an-exchange-to-2-paragraph-3"}>
            In an Exchange, open <b id={"trusted-organizations-administration-article-b-25"}>Manage access</b>, then <b id={"trusted-organizations-administration-article-b-26"}>Access &amp; permissions</b>, <b id={"trusted-organizations-administration-article-b-27"}>Add person</b>, and <b id={"trusted-organizations-administration-article-b-28"}>Trusted
            Organization</b> to add a verified member or published group as an additional participant. Its separate
            invitation appears in Requests, and its Share stays inactive until that person, or a current group Owner or
            Manager, accepts. Declining affects only that invitation, not the Exchange or primary recipient. Acceptance
            rechecks current trust, policies, membership, organization, and publication eligibility.
        </p>

        <h3 id={"trusted-organizations-administration-suspending-and-ending-trust-heading"}>Suspending and ending
            trust</h3>
        <p id={"trusted-organizations-administration-suspending-and-ending-trust-paragraph"}>
            Either organization can suspend an active relationship. Each organization can clear only its own suspension.
            The relationship remains suspended while either organization has an active suspension. Ending trust is
            terminal and cannot be resumed.
        </p>
        <p id={"trusted-organizations-administration-suspending-and-ending-trust-paragraph-2"}>
            Suspension and ending do not remove access already granted on an accepted Exchange or a request party.
            Materialized group-member access remains, but new group members inherit no access on either while trust is
            suspended or no longer eligible. Removing a member still removes inherited access. When every suspension is
            cleared, currently eligible group members are reconciled on both. Ending trust remains terminal and blocks
            future materialization. A draft Information Request cannot be issued while one of its parties is bound to
            suspended or ineligible trust.
        </p>
        <h3 id={"trusted-organizations-administration-audit-and-notifications-heading"}>Audit and notifications</h3>
        <p id={"trusted-organizations-administration-audit-and-notifications-paragraph"}>
            Requests, decisions, policy changes, suspensions, resumptions, and endings are recorded in both
            organizations' audit streams. Active organization administrators receive email and in-app notifications.
            Notification delivery problems do not undo a successful trust change.
        </p>
        <p id={"trusted-organizations-administration-audit-and-notifications-paragraph-2"}>
            Exact-email verification records allowed, denied, expired, used, and blocked-reuse events without storing
            the searched email in the audit event. Trusted recipient validation and acceptance also record allowed and
            denied outcomes without storing resolution evidence.
        </p>
    </>
);
