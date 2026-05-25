import {Text, Title1, Title3, makeStyles, tokens} from "@fluentui/react-components";
import {LinkButton} from "../shared/LinkButton.tsx";
import {PageShell} from "../shared/PageShell.tsx";
import {
    BREAKPOINT_MOBILE,
    CARD_RADIUS,
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

    heroEyebrow: {
        color: tokens.colorBrandForeground1,
        fontSize: tokens.fontSizeBase200,
        fontWeight: tokens.fontWeightSemibold,
        letterSpacing: "0.08em",
        textTransform: "uppercase",
    },

    heroTitle: {
        color: tokens.colorBrandForeground1,
        fontWeight: tokens.fontWeightSemibold,
    },

    body: {
        display: "grid",
        gridTemplateColumns: "1fr 1fr",
        gap: SPACE_LG,
        marginTop: SPACE_LG,

        [BREAKPOINT_MOBILE]: {
            gridTemplateColumns: "1fr",
        },
    },

    panel: {
        backgroundColor: tokens.colorNeutralBackground1,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: CARD_RADIUS,
        padding: SPACE_LG,
        display: "flex",
        flexDirection: "column",
        gap: SPACE_SM,
    },

    valueGrid: {
        marginTop: SPACE_LG,
        display: "grid",
        gridTemplateColumns: "repeat(3, 1fr)",
        gap: SPACE_MD,

        [BREAKPOINT_MOBILE]: {
            gridTemplateColumns: "1fr",
        },
    },

    valueCard: {
        backgroundColor: tokens.colorNeutralBackground1,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: CARD_RADIUS,
        padding: SPACE_LG,
        display: "flex",
        flexDirection: "column",
        gap: SPACE_SM,
    },

    valueTitle: {
        color: tokens.colorBrandForeground1,
        fontWeight: tokens.fontWeightSemibold,
    },

    cta: {
        marginTop: SPACE_LG,
        display: "flex",
        gap: SPACE_MD,
        flexWrap: "wrap",
    },
});

export function AboutPage()
{
    const styles = useStyles();

    return (
        <PageShell>
            <section className={styles.hero}>
                <Text className={styles.heroEyebrow}>About DocuHyphen</Text>
                <Title1 className={styles.heroTitle}>
                    Document sharing should not be the weakest link in your business.
                </Title1>
                <Text size={500}>
                    Most teams still trade sensitive documents through email attachments and consumer file-sharing
                    apps that were never built for regulated work. We are building the alternative.
                </Text>
            </section>

            <div className={styles.body}>
                <section className={styles.panel}>
                    <Title3 className={styles.valueTitle}>Why we exist</Title3>
                    <Text>
                        Every regulated industry, legal, healthcare, finance, real estate, runs on documents,
                        but the tools used to move those documents are the same ones used to share holiday photos. The result
                        is a constant low-grade risk: documents leaked to the wrong person, audit trails that don&apos;t exist,
                        and compliance teams who cannot prove what happened.
                    </Text>
                    <Text>
                        DocuHyphen replaces ad-hoc attachments with structured, auditable share sessions that match the controls
                        the work actually demands.
                    </Text>
                </section>

                <section className={styles.panel}>
                    <Title3 className={styles.valueTitle}>What we believe</Title3>
                    <Text>
                        Security should be the default, not a paid upgrade. Audit logs should be readable by humans, not just
                        forensic engineers. Customers should be able to leave with all of their data at any time.
                    </Text>
                    <Text>
                        We build with these principles because they make DocuHyphen better, and they make our customers
                        sleep better.
                    </Text>
                </section>
            </div>

            <div className={styles.valueGrid}>
                <article className={styles.valueCard}>
                    <Title3 className={styles.valueTitle}>Built by operators</Title3>
                    <Text>
                        Our team has shipped products inside regulated organizations, we know what compliance,
                        procurement, and security review actually ask for.
                    </Text>
                </article>
                <article className={styles.valueCard}>
                    <Title3 className={styles.valueTitle}>Audit-first design</Title3>
                    <Text>
                        Every feature ships with the audit story figured out from day one. If a regulator can&apos;t
                        verify it, we don&apos;t ship it.
                    </Text>
                </article>
                <article className={styles.valueCard}>
                    <Title3 className={styles.valueTitle}>Independent</Title3>
                    <Text>
                        We are an independent company, customer-funded, with no incentive to monetize your documents
                        or your usage data.
                    </Text>
                </article>
            </div>

            <div className={styles.cta}>
                <LinkButton to="/contact" appearance="primary" shape="circular">Get in touch</LinkButton>
                <LinkButton to="/security" appearance="outline" shape="circular">Read about our security</LinkButton>
            </div>
        </PageShell>
    );
}
