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
                        <li>Confirm you have admin access to your identity provider.</li>
                        <li>Collect redirect URIs for each deployment environment.</li>
                        <li>Decide which email domains are allowed for your organization.</li>
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
                        <li>Grant scopes: openid, profile, and email.</li>
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

                    <h3>Troubleshooting checklist</h3>
                    <ul>
                        <li>Redirect URI mismatch between provider and DocuHyphen.</li>
                        <li>Missing consent for required scopes.</li>
                        <li>Invalid tenant or project configuration.</li>
                        <li>User email domain blocked by policy.</li>
                    </ul>
                </>
            ),
        },
    ],
};
