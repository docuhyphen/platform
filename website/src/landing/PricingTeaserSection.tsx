import {Badge, Text, Title2, makeStyles, mergeClasses, tokens} from "@fluentui/react-components";
import {LinkButton} from "../shared/LinkButton.tsx";
import {
    BREAKPOINT_MOBILE,
    CARD_RADIUS,
    SECTION_PADDING_DESKTOP,
    SECTION_PADDING_MOBILE,
    SPACE_LG,
    SPACE_MD,
    SPACE_SM,
    SPACE_XS,
    WIDTH_CONTENT,
    WIDTH_SUBTITLE,
} from "./shared.ts";

type Tier = {
    name: string;
    price: string;
    cadence: string;
    bestFor: string;
    blurb: string;
    bullets: string[];
    cta: string;
    featured?: boolean;
};

const tiers: Tier[] = [
    {
        name: "Starter",
        price: "R0",
        cadence: "per user / month, cancel anytime",
        bestFor: "Best for: solo professionals and very small teams",
        blurb: "For small teams starting secure document exchange with essential controls.",
        bullets: [
            "Up to 3 users",
            "Collaborative share sessions",
            "30-day audit log retention",
            "Email support",
        ],
        cta: "Start free",
    },
    {
        name: "Business",
        price: "R180",
        cadence: "per user / month, cancel anytime",
        bestFor: "Best for: growing teams that need SSO and governance",
        blurb: "Everything in Starter, plus SSO, role controls, and compliance-ready audit exports.",
        bullets: [
            "Everything in Starter",
            "Unlimited share sessions",
            "Microsoft & Google SSO",
            "Role-based permissions",
            "1-year audit retention and export",
            "Priority support",
            "WhatsApp integration",
            "Google Drive / OneDrive backups",
            "Microsoft Teams integration",
            "Slack integration",
        ],
        cta: "Start trial",
        featured: true,
    },
    {
        name: "Enterprise",
        price: "Custom",
        cadence: "tailored to your org",
        bestFor: "Best for: regulated organizations with procurement workflows",
        blurb: "Everything in Business, plus advanced security, procurement, and deployment requirements.",
        bullets: [
            "Everything in Business",
            "Custom user caps",
            "SOC 2 report access and DPA",
            "Tenant isolation review",
            "Dedicated customer success manager",
            "Custom data residency",
            "Integration onboarding and support",
            "FTP backups",
        ],
        cta: "Talk to sales",
    },
];

const useStyles = makeStyles({
    wrapper: {
        padding: SECTION_PADDING_DESKTOP,
        boxSizing: "border-box",
        backgroundColor: tokens.colorNeutralBackground1,
        borderTop: `1px solid ${tokens.colorNeutralStroke2}`,

        [BREAKPOINT_MOBILE]: {
            padding: SECTION_PADDING_MOBILE,
        },
    },

    container: {
        maxWidth: WIDTH_CONTENT,
        margin: "0 auto",
        display: "flex",
        flexDirection: "column",
        gap: SPACE_LG,
    },

    intro: {
        display: "flex",
        flexDirection: "column",
        gap: SPACE_SM,
    },

    sectionTitle: {
        color: tokens.colorBrandForeground1,
        fontWeight: tokens.fontWeightSemibold,
        fontSize: tokens.fontSizeHero800,
    },

    subheading: {
        maxWidth: WIDTH_SUBTITLE,
        color: tokens.colorNeutralForeground1,
        fontWeight: "100",
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

    card: {
        backgroundColor: tokens.colorNeutralBackground1,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: CARD_RADIUS,
        padding: SPACE_LG,
        display: "flex",
        flexDirection: "column",
        gap: SPACE_SM,
    },

    cardFeatured: {
        border: `2px solid ${tokens.colorBrandStroke1}`,
        boxShadow: tokens.shadow16,
    },

    tierName: {
        fontSize: tokens.fontSizeBase500,
        fontWeight: tokens.fontWeightSemibold,
    },

    bestFor: {
        color: tokens.colorNeutralForeground1,
        fontWeight: tokens.fontWeightSemibold,
        fontSize: tokens.fontSizeBase200,
    },

    metaRow: {
        display: "flex",
        alignItems: "center",
        gap: SPACE_XS,
    },

    metaIcon: {
        width: "1rem",
        height: "1rem",
        borderRadius: "999px",
        display: "inline-flex",
        alignItems: "center",
        justifyContent: "center",
        fontSize: "0.65rem",
        fontWeight: tokens.fontWeightSemibold,
        flexShrink: 0,
    },

    metaIconBestFor: {
        backgroundColor: tokens.colorBrandBackground2,
        color: tokens.colorBrandForeground1,
    },

    priceRow: {
        display: "flex",
        alignItems: "baseline",
        gap: SPACE_XS,
    },

    price: {
        fontSize: "2.25rem",
        fontWeight: tokens.fontWeightSemibold,
        color: tokens.colorNeutralForeground1,
    },

    cadence: {
        color: tokens.colorNeutralForeground3,
        fontSize: tokens.fontSizeBase200,
    },

    blurb: {
        color: tokens.colorNeutralForeground2,
        marginBottom: SPACE_SM,
    },

    bullets: {
        display: "flex",
        flexDirection: "column",
        gap: SPACE_XS,
        flex: 1,
        margin: 0,
        padding: 0,
        listStyle: "none",
    },

    bullet: {
        display: "flex",
        alignItems: "center",
        gap: SPACE_XS,
        color: tokens.colorNeutralForeground1,
        fontSize: tokens.fontSizeBase300,
    },

    bulletBadge: {
        flexShrink: 0,
        width: "0.65rem",
        height: "0.65rem",
        minWidth: "0.65rem",
        minHeight: "0.65rem",
        padding: 0,
        borderRadius: "999px",
    },

    cta: {
        marginTop: SPACE_MD,
    },

    featuredBadge: {
        position: "absolute",
        top: "-0.6rem",
        left: SPACE_LG,
        background: tokens.colorBrandBackground,
        color: tokens.colorNeutralForegroundOnBrand,
        padding: "0.15rem 0.6rem",
        borderRadius: "999px",
        fontSize: "0.7rem",
        fontWeight: tokens.fontWeightSemibold,
        textTransform: "uppercase",
        letterSpacing: "0.05em",
    },

    cardRelative: {
        position: "relative",
    },
});

export function PricingTeaserSection()
{
    const styles = useStyles();

    return (
        <section className={styles.wrapper}>
            <div className={styles.container}>
                <div className={styles.intro}>
                    <Title2 className={styles.sectionTitle}>Pricing built for every stage</Title2>
                    <Text size={500} className={styles.subheading} align={"center"}>
                        Start free. Scale with controls when you need them. Monthly billing, cancel anytime.
                    </Text>
                </div>

                <div className={styles.grid}>
                    {tiers.map((tier) => (
                        <article
                            key={tier.name}
                            className={mergeClasses(
                                styles.card,
                                styles.cardRelative,
                                tier.featured && styles.cardFeatured,
                            )}
                        >
                            {tier.featured && (
                                <span className={styles.featuredBadge}>Most popular</span>
                            )}
                            <Text className={styles.tierName}>{tier.name}</Text>
                            <div className={styles.metaRow}>
                                <span className={mergeClasses(styles.metaIcon, styles.metaIconBestFor)} aria-hidden="true">B</span>
                                <Text className={styles.bestFor}>{tier.bestFor}</Text>
                            </div>
                            <div className={styles.priceRow}>
                                <Text className={styles.price}>{tier.price}</Text>
                                <Text className={styles.cadence}>{tier.cadence}</Text>
                            </div>
                            <Text className={styles.blurb}>{tier.blurb}</Text>
                            <ul className={styles.bullets}>
                                {tier.bullets.map((b) => (
                                    <li key={b} className={styles.bullet}>
                                        <Badge
                                            size="small"
                                            appearance="filled"
                                            className={styles.bulletBadge}
                                        />
                                        {b}
                                    </li>
                                ))}
                            </ul>
                            <LinkButton
                                to="/pricing"
                                appearance={tier.featured ? "primary" : "outline"}
                                shape="circular"
                                className={styles.cta}
                            >
                                {tier.cta}
                            </LinkButton>
                        </article>
                    ))}
                </div>
            </div>
        </section>
    );
}
