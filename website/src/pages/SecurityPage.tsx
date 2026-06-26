import {Text, Title1, Title3, makeStyles, tokens} from "@fluentui/react-components";
import {
    LockClosed24Regular,
    Server24Regular,
    Person24Regular,
    ClipboardTaskListLtr24Regular,
    ShieldCheckmark24Regular,
    Globe24Regular,
} from "@fluentui/react-icons";
import type {ReactNode} from "react";
import {LinkButton} from "../shared/LinkButton.tsx";
import {PageShell} from "../shared/PageShell.tsx";
import {Breadcrumbs} from "../shared/Breadcrumbs.tsx";
import {
    BREAKPOINT_MOBILE,
    CARD_RADIUS,
    SPACE_LG,
    SPACE_MD,
    SPACE_SM,
    SPACE_XS,
} from "../landing/shared.ts";

type Pillar = {
    icon: ReactNode;
    title: string;
    body: string;
};

const pillars: Pillar[] = [
    {
        icon: <LockClosed24Regular/>,
        title: "Encryption everywhere",
        body: "TLS 1.2+ in transit, AES-256 at rest. Document keys are scoped per organization.",
    },
    {
        icon: <Server24Regular/>,
        title: "Modern hosting",
        body: "Hosted on tier-1 cloud infrastructure with regional isolation and automated backup.",
    },
    {
        icon: <Person24Regular/>,
        title: "Identity & access",
        body: "Microsoft and Google SSO, role-based access, device-scoped sessions, and granular revocation.",
    },
    {
        icon: <ClipboardTaskListLtr24Regular/>,
        title: "Audit logging",
        body: "Every upload, view, download, and admin action is recorded with timestamp and actor.",
    },
    {
        icon: <ShieldCheckmark24Regular/>,
        title: "Compliance posture",
        body: "Built to align with SOC 2, GDPR, and POPIA. SOC 2 Type II audit is in progress.",
    },
    {
        icon: <Globe24Regular/>,
        title: "Tenant isolation",
        body: "Organization-level scoping is enforced throughout authentication and authorization.",
    },
];

const useStyles = makeStyles({
    hero: {
        display: "flex",
        flexDirection: "column",
        gap: SPACE_SM,
        textAlign: "center",
        alignItems: "center",
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

    heroBlurb: {
        maxWidth: "42rem",
    },

    grid: {
        display: "grid",
        gridTemplateColumns: "repeat(3, 1fr)",
        gap: SPACE_LG,

        [BREAKPOINT_MOBILE]: {
            gridTemplateColumns: "1fr",
            gap: SPACE_MD,
        },
    },

    pillar: {
        backgroundColor: tokens.colorNeutralBackground1,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: CARD_RADIUS,
        padding: SPACE_LG,
        display: "flex",
        flexDirection: "column",
        gap: SPACE_SM,
    },

    pillarIcon: {
        width: "3rem",
        height: "3rem",
        borderRadius: "0.7rem",
        backgroundColor: tokens.colorBrandBackground2,
        color: tokens.colorBrandForeground1,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
    },

    band: {
        marginTop: SPACE_LG,
        backgroundColor: tokens.colorNeutralBackground1,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: CARD_RADIUS,
        padding: SPACE_LG,
        display: "flex",
        flexDirection: "column",
        gap: SPACE_MD,
    },

    list: {
        listStyle: "none",
        margin: 0,
        padding: 0,
        display: "grid",
        gridTemplateColumns: "1fr 1fr",
        gap: SPACE_SM,

        [BREAKPOINT_MOBILE]: {
            gridTemplateColumns: "1fr",
        },
    },

    listItem: {
        display: "flex",
        flexDirection: "column",
        gap: SPACE_XS,
        padding: SPACE_SM,
        borderLeft: `3px solid ${tokens.colorBrandStroke1}`,
        backgroundColor: tokens.colorNeutralBackground2,
        borderRadius: "0.4rem",
    },

    cta: {
        marginTop: SPACE_LG,
        display: "flex",
        gap: SPACE_MD,
        flexWrap: "wrap",
    },
});

export function SecurityPage()
{
    const styles = useStyles();

    return (
        <PageShell>
            <Breadcrumbs trail={[
                {label: "Resources", to: "/resources"},
                {label: "Security & Trust"},
            ]}/>
            <section className={styles.hero}>
                <Title1 className={styles.heroTitle}>Security is the product.</Title1>
                <Text size={500} className={styles.heroBlurb} align={"center"}>
                    DocuHyphen is built for teams that cannot afford to lose track of a single document. Here's
                    how we protect customer data.
                </Text>
            </section>

            <div className={styles.grid}>
                {pillars.map((p) => (
                    <section key={p.title} className={styles.pillar}>
                        <div className={styles.pillarIcon}>{p.icon}</div>
                        <Title3>{p.title}</Title3>
                        <Text>{p.body}</Text>
                    </section>
                ))}
            </div>

            <section className={styles.band}>
                <Title3>What customers can expect</Title3>
                <ul className={styles.list}>
                    <li className={styles.listItem}>
                        <Text weight="semibold">Data export anytime</Text>
                        <Text>Every document, share session, and log can be exported on demand.</Text>
                    </li>
                    <li className={styles.listItem}>
                        <Text weight="semibold">No training on your data</Text>
                        <Text>Your documents never train models or feed marketing systems.</Text>
                    </li>
                    <li className={styles.listItem}>
                        <Text weight="semibold">Subprocessor transparency</Text>
                        <Text>We publish the list of subprocessors and notify customers of changes.</Text>
                    </li>
                    <li className={styles.listItem}>
                        <Text weight="semibold">Incident response</Text>
                        <Text>Documented response process with customer notification commitments.</Text>
                    </li>
                </ul>
            </section>

            <section className={styles.cta}>
                <LinkButton to="/help/security-faq" appearance="primary" shape="circular">
                    Read the Security FAQ
                </LinkButton>
                <LinkButton to="/contact" appearance="secondary" shape="circular">
                    Request SOC 2 report
                </LinkButton>
            </section>
        </PageShell>
    );
}
