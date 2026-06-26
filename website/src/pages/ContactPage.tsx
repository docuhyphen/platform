import {Button, Text, Title1, Title3, makeStyles, tokens} from "@fluentui/react-components";
import {Mail24Regular, ChatHelp24Regular, Location24Regular} from "@fluentui/react-icons";
import {PageShell} from "../shared/PageShell.tsx";
import {SpeakToSalesDialog} from "../landing/SpeakToSalesDialog.tsx";
import {
    BREAKPOINT_MOBILE,
    CARD_RADIUS,
    SALES_EMAIL_URL,
    SPACE_LG,
    SPACE_MD,
    SPACE_SM,
} from "../landing/shared.ts";

const useStyles = makeStyles({
    hero: {
        display: "flex",
        flexDirection: "column",
        gap: SPACE_SM,
        maxWidth: "42rem",
    },

    heroTitle: {
        color: tokens.colorBrandForeground1,
        fontWeight: tokens.fontWeightSemibold,
    },

    grid: {
        display: "grid",
        gridTemplateColumns: "repeat(3, 1fr)",
        gap: SPACE_LG,
        marginTop: SPACE_LG,

        [BREAKPOINT_MOBILE]: {
            gridTemplateColumns: "1fr",
            gap: SPACE_MD,
        },
    },

    card: {
        backgroundColor: tokens.colorNeutralBackground1,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: CARD_RADIUS,
        padding: SPACE_LG,
        display: "flex",
        flexDirection: "column",
        gap: SPACE_SM,
    },

    icon: {
        width: "2.5rem",
        height: "2.5rem",
        borderRadius: "0.6rem",
        backgroundColor: tokens.colorBrandBackground2,
        color: tokens.colorBrandForeground1,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
    },

    link: {
        color: tokens.colorBrandForeground1,
        fontWeight: tokens.fontWeightSemibold,
        textDecorationLine: "none",
    },

    salesPanel: {
        marginTop: SPACE_LG,
        backgroundColor: tokens.colorNeutralBackground1,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: CARD_RADIUS,
        padding: SPACE_LG,
        display: "flex",
        flexDirection: "column",
        gap: SPACE_SM,
    },

    salesActions: {
        display: "flex",
        gap: SPACE_MD,
        flexWrap: "wrap",
        marginTop: SPACE_SM,
    },
});

export function ContactPage()
{
    const styles = useStyles();

    return (
        <PageShell>
            <section className={styles.hero}>
                <Title1 className={styles.heroTitle}>Get in touch</Title1>
                <Text size={500}>
                    Whether you&apos;re evaluating, mid-rollout, or already a customer, we&apos;re here to help.
                </Text>
            </section>

            <div className={styles.grid}>
                <section className={styles.card}>
                    <div className={styles.icon}><Mail24Regular/></div>
                    <Title3>Sales</Title3>
                    <Text>For pricing, procurement, and security reviews.</Text>
                    <a className={styles.link} href={SALES_EMAIL_URL}>sales@docuhyphen.com</a>
                </section>

                <section className={styles.card}>
                    <div className={styles.icon}><ChatHelp24Regular/></div>
                    <Title3>Support</Title3>
                    <Text>For help with your account or technical issues.</Text>
                    <a className={styles.link} href="mailto:support@docuhyphen.com">support@docuhyphen.com</a>
                </section>

                <section className={styles.card}>
                    <div className={styles.icon}><Location24Regular/></div>
                    <Title3>Office</Title3>
                    <Text>
                        We are based in South Africa.<br/>
                        Available for meetings across SAST hours.
                    </Text>
                </section>
            </div>

            <section className={styles.salesPanel}>
                <Title3>Want a guided demo?</Title3>
                <Text>
                    Fill out a short form and our sales team will reach out within one business day to schedule a personalized walkthrough.
                </Text>
                <div className={styles.salesActions}>
                    <SpeakToSalesDialog
                        trigger={
                            <Button appearance="primary" shape="circular">
                                Speak to sales
                            </Button>
                        }
                    />
                    <Button as="a" href={SALES_EMAIL_URL} appearance="secondary" shape="circular">
                        Email us directly
                    </Button>
                </div>
            </section>
        </PageShell>
    );
}
