import {deviceSessionsArticle} from "./articles/deviceSessionsArticle";
import {HelpDocSectionInput} from "../helpDocsRegistry";
import {linkedAccountsArticle} from "./articles/linkedAccountsArticle";
import {notificationPreferencesArticle} from "./articles/notificationPreferencesArticle";
import {profileSettingsArticle} from "./articles/profileSettingsArticle";

export const identitySection: HelpDocSectionInput = {
    id: "identity",
    title: "Identity & access",
    articles: [
        {
            id: "profile-settings",
            title: "Profile settings",
            content: profileSettingsArticle,
        },
        {
            id: "linked-accounts",
            title: "Linked accounts",
            content: linkedAccountsArticle,
        },
        {
            id: "device-sessions",
            title: "Device sessions",
            content: deviceSessionsArticle,
        },
        {
            id: "notification-preferences",
            title: "Notification preferences",
            content: notificationPreferencesArticle,
        },
        {
            id: "idp-setup-guide",
            title: "IdP setup guide",
            content: (
                <>
                    <p>
                        Configure Microsoft Entra ID or Google as your identity provider
                        for single sign-on and organization-level access control.
                    </p>

                    <h3>Before you begin</h3>
                    <ul>
                        <li>Confirm the organization has the Business identity and integrations entitlement.</li>
                        <li>Confirm you have admin access to your identity provider.</li>
                        <li>Collect redirect URIs for each deployment environment.</li>
                        <li>Decide which organization-owned email domains are allowed.</li>
                        <li>Confirm you can add a TXT record to each domain&apos;s DNS.</li>
                        <li>Define who owns rollout, testing, and change approval.</li>
                    </ul>

                    <h3>Microsoft (Entra ID)</h3>
                    <ol>
                        <li>Create or select an Entra ID app registration.</li>
                        <li>Add the DocuHyphen redirect URI(s) as web redirects.</li>
                        <li>Capture the client ID and tenant ID.</li>
                        <li>Create a client secret and store it in your secret manager.</li>
                        <li>
                            Include claims for email, given name, family name, and subject
                            or object ID.
                        </li>
                        <li>
                            Enable the optional email claim with the domain ownership setting so the
                            token carries a verified address. Sign-ins without it are rejected.
                        </li>
                        <li>Grant scopes: openid, profile, and email.</li>
                        <li>
                            Set a specific directory ID as the tenant. The shared values common,
                            organizations, and consumers accept tokens from any Microsoft directory
                            and are refused unless multi-tenant sign-in is deliberately enabled.
                        </li>
                        <li>Save values in DocuHyphen settings and run a sign-in test.</li>
                    </ol>

                    <h3>Google</h3>
                    <ol>
                        <li>Create or select a Google Cloud project.</li>
                        <li>Configure the OAuth consent screen for your org.</li>
                        <li>Create web OAuth credentials and set redirect URI(s).</li>
                        <li>Capture the client ID and client secret.</li>
                        <li>Grant scopes: openid, profile, and email.</li>
                        <li>Configure domain restrictions if required.</li>
                        <li>Save configuration in DocuHyphen and run a test login.</li>
                    </ol>

                    <h3>Session policy</h3>
                    <p>
                        Open <b>Settings</b>, select <b>Administration</b> under
                        <b> Organization</b>, then open <b>Security</b> to review the effective session policy.
                        The strictest value from the active Identity Provider configurations applies.
                        Existing configuration remains visible after a subscription change, but changes
                        to providers, session policy, or secret lifecycle require mutation access.
                    </p>
                    <ul>
                        <li><b>Access token lifetime</b> controls short-lived API access.</li>
                        <li><b>Session renewal window</b> controls how long an active session can renew access.</li>
                        <li><b>Maximum session lifetime</b> is the absolute limit after sign-in.</li>
                        <li><b>Inactivity timeout</b> ends a session after no user activity.</li>
                    </ul>

                    <h3>Sign-in enforcement</h3>
                    <ul>
                        <li>
                            On the sign-in page, people can use Google or Microsoft directly,
                            or enter their email address to continue with email and password.
                        </li>
                        <li>
                            An organization domain must be registered in DocuHyphen and verified
                            with its DNS TXT challenge before it can route sign-in or enforce an
                            organization Identity Provider policy.
                        </li>
                        <li>
                            Organization choices shown before authentication come only from the exact
                            account&apos;s current active organization memberships. Organizations are not
                            listed merely because their contact addresses use the same email domain.
                        </li>
                        <li>
                            A verified organization domain may route directly to its active
                            Identity Provider without returning the organization&apos;s name or identifier.
                            An organization contact email does not establish ownership of its domain.
                        </li>
                        <li>
                            A selected organization is checked against the account&apos;s current active
                            memberships before its sign-in policy is used. An inactive membership,
                            inactive organization, or altered organization identifier is rejected.
                        </li>
                        <li>
                            Microsoft membership is bound to the configured Entra tenant and immutable
                            object ID. The address in the token is only trusted when Microsoft confirms
                            the tenant owns that email domain, so a display name such as the sign-in
                            username is never accepted as an identity. Google Workspace membership
                            requires a verified email and matching hosted domain.
                        </li>
                        <li>
                            A provider sign-in can only create a new DocuHyphen account when the
                            provider confirms the email address. Otherwise the person must sign up
                            first and link the provider afterwards.
                        </li>
                        <li>
                            Deactivate the INTERNAL configuration when members must use organization SSO.
                            Email and password will no longer be offered as a fallback for that domain.
                        </li>
                    </ul>

                    <h3>Troubleshooting checklist</h3>
                    <ul>
                        <li>Redirect URI mismatch between provider and DocuHyphen.</li>
                        <li>Missing consent for required scopes.</li>
                        <li>Invalid tenant or project configuration.</li>
                        <li>Tenant left as a shared multi-directory value instead of your directory ID.</li>
                        <li>Missing verified email claim from Entra ID.</li>
                        <li>User email domain blocked by policy.</li>
                    </ul>
                </>
            ),
        },
    ],
};
