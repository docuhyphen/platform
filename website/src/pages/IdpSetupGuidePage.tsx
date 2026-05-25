import {makeStyles, Text, tokens} from "@fluentui/react-components";
import {LinkButton} from "../shared/LinkButton.tsx";
import {PageShell} from "../shared/PageShell.tsx";
import {Breadcrumbs} from "../shared/Breadcrumbs.tsx";

const useStyles = makeStyles({

    section: {
        backgroundColor: tokens.colorNeutralBackground1,
        borderRadius: "0.75rem",
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        padding: "1.25rem",
        display: "flex",
        flexDirection: "column",
        gap: "0.5rem",
    },

    heading: {
        fontSize: tokens.fontSizeHero700,
        lineHeight: tokens.lineHeightHero700,
        margin: 0,
    },

    subHeading: {
        fontSize: tokens.fontSizeBase500,
        fontWeight: tokens.fontWeightSemibold,
        margin: 0,
    },

    list: {
        margin: 0,
        paddingLeft: "1.1rem",
        display: "flex",
        flexDirection: "column",
        gap: "0.45rem",
        color: tokens.colorNeutralForeground2,
        fontSize: tokens.fontSizeBase300,
        lineHeight: tokens.lineHeightBase300,
    },

    buttonRow: {
        marginTop: "0.5rem",
        display: "flex",
        gap: "0.75rem",
        flexWrap: "wrap",
    },
});

export function IdpSetupGuidePage()
{
    const styles = useStyles();

    return (
        <PageShell>
            <Breadcrumbs trail={[
                {label: "Resources", to: "/resources"},
                {label: "IdP Setup Guide"},
            ]}/>
            <h1 className={styles.heading}>IdP Setup Guide: Microsoft and Google</h1>
                <Text>
                    Use this guide to configure your organization's identity provider so users can sign in with SSO.
                    Steps focus on the data DocuHyphen requires and validation checks your admins should complete.
                </Text>

                <section className={styles.section}>
                    <h2 className={styles.subHeading}>Before you begin</h2>
                    <ul className={styles.list}>
                        <li>Confirm you are an admin in Microsoft Entra ID or Google Cloud/Workspace.</li>
                        <li>Collect your production and staging redirect URIs from DocuHyphen settings.</li>
                        <li>Decide which email domains are allowed for your organization.</li>
                        <li>Know your selected plan cap (for example 50 users or unlimited).</li>
                    </ul>
                </section>

                <section className={styles.section}>
                    <h2 className={styles.subHeading}>Microsoft setup (Entra ID)</h2>
                    <ol className={styles.list}>
                        <li>Create or select an app registration in Entra ID.</li>
                        <li>Add Web redirect URI(s) provided by DocuHyphen.</li>
                        <li>Capture Application (client) ID and Directory (tenant) ID.</li>
                        <li>Create a client secret and store it securely.</li>
                        <li>Configure token claims: email, given name, family name, subject/object ID.</li>
                        <li>Grant required scopes: <code>openid</code>, <code>email</code>, <code>profile</code>.</li>
                        <li>Save in DocuHyphen: provider, client ID, client secret, tenant ID, allowed domains.</li>
                        <li>Use "Test Configuration" and complete one login test with a real org account.</li>
                    </ol>
                </section>

                <section className={styles.section}>
                    <h2 className={styles.subHeading}>Google setup (Google Cloud / Workspace)</h2>
                    <ol className={styles.list}>
                        <li>Create or select a Google Cloud project.</li>
                        <li>Configure OAuth consent screen for your organization.</li>
                        <li>Create OAuth client credentials (Web application).</li>
                        <li>Add authorized redirect URI(s) from DocuHyphen.</li>
                        <li>Capture client ID and client secret.</li>
                        <li>Set scopes to <code>openid</code>, <code>email</code>, <code>profile</code>.</li>
                        <li>If using Workspace restrictions, set hosted domain policy and verify domains.</li>
                        <li>Save in DocuHyphen and run "Test Configuration" before rollout.</li>
                    </ol>
                </section>

                <section className={styles.section}>
                    <h2 className={styles.subHeading}>Fields required in DocuHyphen</h2>
                    <ul className={styles.list}>
                        <li>Provider type (Microsoft or Google)</li>
                        <li>Client ID</li>
                        <li>Client Secret</li>
                        <li>Tenant ID (Microsoft)</li>
                        <li>Allowed organization email domains</li>
                        <li>Scopes (default: <code>openid email profile</code>)</li>
                    </ul>
                </section>

                <section className={styles.section}>
                    <h2 className={styles.subHeading}>Troubleshooting checklist</h2>
                    <ul className={styles.list}>
                        <li>Redirect URI mismatch in provider console vs DocuHyphen.</li>
                        <li>Wrong tenant ID or unsupported account type.</li>
                        <li>Missing consent for required scopes.</li>
                        <li>User email domain not mapped to your organization configuration.</li>
                        <li>User cap reached for your subscription tier.</li>
                    </ul>
                </section>

                <div className={styles.buttonRow}>
                    <LinkButton to="/help/security-faq" appearance="secondary" shape="circular">
                        View Security FAQ
                    </LinkButton>
                    <LinkButton to="/resources" appearance="outline" shape="circular">
                        Back to Resources
                    </LinkButton>
                </div>
        </PageShell>
    );
}

