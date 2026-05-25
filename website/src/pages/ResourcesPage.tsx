import {Text, Title1, Title3, makeStyles, tokens} from "@fluentui/react-components";
import {
    Book20Regular,
    DocumentBulletList20Regular,
    ShieldCheckmark20Regular,
    Question20Regular,
    News20Regular,
    Person20Regular,
} from "@fluentui/react-icons";
import {Link} from "react-router-dom";
import type {ReactNode} from "react";
import {PageShell} from "../shared/PageShell.tsx";
import {
    BREAKPOINT_MOBILE,
    CARD_RADIUS,
    SPACE_LG,
    SPACE_MD,
    SPACE_SM,
} from "../landing/shared.ts";

type ResourceCard = {
    icon: ReactNode;
    title: string;
    body: string;
    to: string;
    cta: string;
    badge?: string;
};

const cards: ResourceCard[] = [
    {
        icon: <Book20Regular/>,
        title: "IdP Setup Guide",
        body: "Configure Microsoft Entra ID or Google Workspace SSO for your organization.",
        to: "/help/idp-setup",
        cta: "Read guide",
    },
    {
        icon: <Question20Regular/>,
        title: "Security FAQ",
        body: "Common questions about how DocuHyphen protects customer data.",
        to: "/help/security-faq",
        cta: "Read FAQ",
    },
    {
        icon: <ShieldCheckmark20Regular/>,
        title: "Security & Trust",
        body: "Our public trust posture: encryption, hosting, access controls, and compliance.",
        to: "/security",
        cta: "View page",
    },
    {
        icon: <DocumentBulletList20Regular/>,
        title: "Case studies",
        body: "How real teams replace email attachments with structured share sessions.",
        to: "/resources",
        cta: "Coming soon",
        badge: "Soon",
    },
    {
        icon: <News20Regular/>,
        title: "Changelog",
        body: "Recent product updates, security improvements, and integration releases.",
        to: "/resources",
        cta: "Coming soon",
        badge: "Soon",
    },
    {
        icon: <Person20Regular/>,
        title: "Contact sales",
        body: "Talk to our team about pricing, procurement, or a guided demo.",
        to: "/contact",
        cta: "Get in touch",
    },
];

const useStyles = makeStyles({
    hero: {
        display: "flex",
        flexDirection: "column",
        gap: SPACE_SM,
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
        position: "relative",
        backgroundColor: tokens.colorNeutralBackground1,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: CARD_RADIUS,
        padding: SPACE_LG,
        display: "flex",
        flexDirection: "column",
        gap: SPACE_SM,
        textDecorationLine: "none",
        color: "inherit",
        transitionProperty: "transform, box-shadow",
        transitionDuration: "200ms",

        ":hover": {
            transform: "translateY(-2px)",
            boxShadow: tokens.shadow8,
        },
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

    cta: {
        marginTop: "auto",
        color: tokens.colorBrandForeground1,
        fontWeight: tokens.fontWeightSemibold,
    },

    badge: {
        position: "absolute",
        top: SPACE_MD,
        right: SPACE_MD,
        backgroundColor: tokens.colorNeutralBackground3,
        color: tokens.colorNeutralForeground3,
        padding: "0.15rem 0.6rem",
        borderRadius: "999px",
        fontSize: "0.7rem",
        fontWeight: tokens.fontWeightSemibold,
        textTransform: "uppercase",
        letterSpacing: "0.05em",
    },
});

export function ResourcesPage()
{
    const styles = useStyles();

    return (
        <PageShell>
            <section className={styles.hero}>
                <Title1 className={styles.heroTitle}>Resources</Title1>
                <Text size={500}>
                    Setup guides, security documentation, and the things you need to roll DocuHyphen out across your team.
                </Text>
            </section>

            <div className={styles.grid}>
                {cards.map((card) => (
                    <Link key={card.title} to={card.to} className={styles.card}>
                        {card.badge && <span className={styles.badge}>{card.badge}</span>}
                        <div className={styles.icon}>{card.icon}</div>
                        <Title3>{card.title}</Title3>
                        <Text>{card.body}</Text>
                        <Text className={styles.cta}>{card.cta} &rarr;</Text>
                    </Link>
                ))}
            </div>
        </PageShell>
    );
}
