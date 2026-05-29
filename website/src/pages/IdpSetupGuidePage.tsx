import {Accordion, AccordionHeader, AccordionItem, AccordionPanel, makeStyles, Tab, TabList, Text, Title1, tokens} from "@fluentui/react-components";
import {useState} from "react";
import {LinkButton} from "../shared/LinkButton.tsx";
import {PageShell} from "../shared/PageShell.tsx";
import {Breadcrumbs} from "../shared/Breadcrumbs.tsx";

const useStyles = makeStyles({
    hero: {
        display: "flex",
        flexDirection: "column",
        gap: "0.5rem",
        marginBottom: "1.5rem",
        textAlign: "center",
        alignItems: "center",
    },

    heroTitle: {
        color: tokens.colorBrandForeground1,
        fontWeight: tokens.fontWeightSemibold,
    },

    heroBlurb: {
        maxWidth: "42rem",
    },

    tabRow: {
        marginBottom: "1rem",
    },

    quickGrid: {
        display: "grid",
        gridTemplateColumns: "repeat(3, minmax(0, 1fr))",
        gap: "0.75rem",
        marginBottom: "1rem",
        "@media (max-width: 860px)": {
            gridTemplateColumns: "1fr",
        },
    },

    quickCard: {
        backgroundColor: tokens.colorNeutralBackground1,
        borderRadius: "0.75rem",
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        padding: "0.9rem",
        display: "flex",
        flexDirection: "column",
        gap: "0.35rem",
    },

    quickTitle: {
        fontWeight: tokens.fontWeightSemibold,
    },

    quickBody: {
        color: tokens.colorNeutralForeground2,
    },

    section: {
        backgroundColor: tokens.colorNeutralBackground1,
        borderRadius: "0.75rem",
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        padding: "1.25rem",
        display: "flex",
        flexDirection: "column",
        gap: "0.5rem",
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

    numberedList: {
        margin: 0,
        paddingLeft: "1.2rem",
        display: "flex",
        flexDirection: "column",
        gap: "0.45rem",
        color: tokens.colorNeutralForeground2,
        fontSize: tokens.fontSizeBase300,
        lineHeight: tokens.lineHeightBase300,
    },

    tip: {
        marginTop: "0.5rem",
        backgroundColor: tokens.colorNeutralBackground2,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: "0.5rem",
        padding: "0.65rem 0.8rem",
        color: tokens.colorNeutralForeground2,
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
    const [providerTab, setProviderTab] = useState<"microsoft" | "google">("microsoft");

    return (
        <PageShell>
            <Breadcrumbs trail={[
                {label: "Resources", to: "/resources"},
                {label: "IdP Setup Guide"},
            ]}/>

            <section className={styles.hero}>
                <Title1 className={styles.heroTitle}>IdP Setup Guide</Title1>
                <Text size={500} className={styles.heroBlurb} align={"center"}>
                    Configure Microsoft Entra ID or Google as your identity provider for single sign-on and organization-level access control.
                </Text>
            </section>

            <div className={styles.tabRow}>
                <TabList
                    selectedValue={providerTab}
                    onTabSelect={(_, data) => setProviderTab(data.value as "microsoft" | "google")}
                >
                    <Tab value="microsoft">Microsoft</Tab>
                    <Tab value="google">Google</Tab>
                </TabList>
            </div>

            {providerTab === "microsoft" ? (
                <>
                    <div className={styles.quickGrid}>
                        <div className={styles.quickCard}>
                            <Text className={styles.quickTitle}>1) Gather details</Text>
                            <Text className={styles.quickBody}>Entra admin access, redirect URIs, tenant boundary, and allowed email domains.</Text>
                        </div>
                        <div className={styles.quickCard}>
                            <Text className={styles.quickTitle}>2) Configure Microsoft app</Text>
                            <Text className={styles.quickBody}>Set redirect URIs, create secret, and configure required claims/scopes.</Text>
                        </div>
                        <div className={styles.quickCard}>
                            <Text className={styles.quickTitle}>3) Validate in DocuHyphen</Text>
                            <Text className={styles.quickBody}>Save values, run test sign-in, and verify domain + claim mapping.</Text>
                        </div>
                    </div>

                    <Accordion collapsible multiple defaultOpenItems={["before", "setup"]}>
                        <AccordionItem value="before">
                            <AccordionHeader>Before you begin</AccordionHeader>
                            <AccordionPanel>
                                <section className={styles.section}>
                                    <ul className={styles.list}>
                                        <li>Confirm you are an admin in Microsoft Entra ID.</li>
                                        <li>Collect production and staging redirect URIs from DocuHyphen.</li>
                                        <li>Decide which email domains are allowed for your organization.</li>
                                        <li>Know your selected plan cap (for example 3 users on Free tier, higher cap, or unlimited).</li>
                                    </ul>
                                </section>
                            </AccordionPanel>
                        </AccordionItem>

                        <AccordionItem value="setup">
                            <AccordionHeader>Microsoft setup (Entra ID)</AccordionHeader>
                            <AccordionPanel>
                                <section className={styles.section}>
                                    <ol className={styles.numberedList}>
                                        <li>Create or select an app registration in Entra ID.</li>
                                        <li>Add Web redirect URI(s) provided by DocuHyphen.</li>
                                        <li>Capture Application (client) ID and Directory (tenant) ID.</li>
                                        <li>Create a client secret and store it securely.</li>
                                        <li>Configure token claims: email, given name, family name, subject/object ID.</li>
                                        <li>Grant required scopes: <code>openid</code>, <code>email</code>, <code>profile</code>.</li>
                                        <li>Save in DocuHyphen: client ID, client secret, tenant ID, allowed domains.</li>
                                        <li>Use "Test Configuration" and complete one login test with a real org account.</li>
                                    </ol>
                                    <Text className={styles.tip}><b>Tip:</b> Redirect URI mismatches are the most common failure point. Copy/paste directly.</Text>
                                </section>
                            </AccordionPanel>
                        </AccordionItem>

                        <AccordionItem value="fields">
                            <AccordionHeader>Fields required in DocuHyphen</AccordionHeader>
                            <AccordionPanel>
                                <section className={styles.section}>
                                    <ul className={styles.list}>
                                        <li>Provider type: Microsoft</li>
                                        <li>Client ID</li>
                                        <li>Client Secret</li>
                                        <li>Tenant ID</li>
                                        <li>Allowed organization email domains</li>
                                        <li>Scopes (default: <code>openid email profile</code>)</li>
                                    </ul>
                                </section>
                            </AccordionPanel>
                        </AccordionItem>

                        <AccordionItem value="troubleshooting">
                            <AccordionHeader>Troubleshooting checklist</AccordionHeader>
                            <AccordionPanel>
                                <section className={styles.section}>
                                    <ul className={styles.list}>
                                        <li>Redirect URI mismatch in Entra ID vs DocuHyphen.</li>
                                        <li>Wrong tenant ID or unsupported account type.</li>
                                        <li>Missing consent for required scopes.</li>
                                        <li>User email domain not mapped to your configuration.</li>
                                        <li>User cap reached for your subscription tier.</li>
                                    </ul>
                                </section>
                            </AccordionPanel>
                        </AccordionItem>
                    </Accordion>
                </>
            ) : (
                <>
                    <div className={styles.quickGrid}>
                        <div className={styles.quickCard}>
                            <Text className={styles.quickTitle}>1) Gather details</Text>
                            <Text className={styles.quickBody}>Google Cloud admin access, redirect URIs, consent settings, and allowed email domains.</Text>
                        </div>
                        <div className={styles.quickCard}>
                            <Text className={styles.quickTitle}>2) Configure Google OAuth</Text>
                            <Text className={styles.quickBody}>Set consent screen, OAuth client, redirect URIs, and required scopes.</Text>
                        </div>
                        <div className={styles.quickCard}>
                            <Text className={styles.quickTitle}>3) Validate in DocuHyphen</Text>
                            <Text className={styles.quickBody}>Save values, run test sign-in, and verify domain restrictions.</Text>
                        </div>
                    </div>

                    <Accordion collapsible multiple defaultOpenItems={["before", "setup"]}>
                        <AccordionItem value="before">
                            <AccordionHeader>Before you begin</AccordionHeader>
                            <AccordionPanel>
                                <section className={styles.section}>
                                    <ul className={styles.list}>
                                        <li>Confirm you are an admin in Google Cloud or Google Workspace.</li>
                                        <li>Collect production and staging redirect URIs from DocuHyphen.</li>
                                        <li>Decide which email domains are allowed for your organization.</li>
                                        <li>Know your selected plan cap (for example 3 users on Free tier, higher cap, or unlimited).</li>
                                    </ul>
                                </section>
                            </AccordionPanel>
                        </AccordionItem>

                        <AccordionItem value="setup">
                            <AccordionHeader>Google setup (Google Cloud / Workspace)</AccordionHeader>
                            <AccordionPanel>
                                <section className={styles.section}>
                                    <ol className={styles.numberedList}>
                                        <li>Create or select a Google Cloud project.</li>
                                        <li>Configure OAuth consent screen for your organization.</li>
                                        <li>Create OAuth client credentials (Web application).</li>
                                        <li>Add authorized redirect URI(s) from DocuHyphen.</li>
                                        <li>Capture client ID and client secret.</li>
                                        <li>Set scopes to <code>openid</code>, <code>email</code>, <code>profile</code>.</li>
                                        <li>If using Workspace restrictions, set hosted domain policy and verify domains.</li>
                                        <li>Save in DocuHyphen and run "Test Configuration" before rollout.</li>
                                    </ol>
                                    <Text className={styles.tip}><b>Tip:</b> If users fail sign-in, verify domain restrictions and consent screen publishing status first.</Text>
                                </section>
                            </AccordionPanel>
                        </AccordionItem>

                        <AccordionItem value="fields">
                            <AccordionHeader>Fields required in DocuHyphen</AccordionHeader>
                            <AccordionPanel>
                                <section className={styles.section}>
                                    <ul className={styles.list}>
                                        <li>Provider type: Google</li>
                                        <li>Client ID</li>
                                        <li>Client Secret</li>
                                        <li>Allowed organization email domains</li>
                                        <li>Scopes (default: <code>openid email profile</code>)</li>
                                        <li>Hosted domain restriction (optional, Workspace)</li>
                                    </ul>
                                </section>
                            </AccordionPanel>
                        </AccordionItem>

                        <AccordionItem value="troubleshooting">
                            <AccordionHeader>Troubleshooting checklist</AccordionHeader>
                            <AccordionPanel>
                                <section className={styles.section}>
                                    <ul className={styles.list}>
                                        <li>Redirect URI mismatch in Google Cloud vs DocuHyphen.</li>
                                        <li>OAuth consent screen not configured/published correctly.</li>
                                        <li>Missing consent for required scopes.</li>
                                        <li>Domain restriction blocks intended users.</li>
                                        <li>User cap reached for your subscription tier.</li>
                                    </ul>
                                </section>
                            </AccordionPanel>
                        </AccordionItem>
                    </Accordion>
                </>
            )}

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

