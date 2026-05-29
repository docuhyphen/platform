import {Text, Title2, makeStyles, mergeClasses, tokens} from "@fluentui/react-components";
import {Checkmark20Filled} from "@fluentui/react-icons";
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
    blurb: string;
    bullets: string[];
    cta: string;
    featured?: boolean;
};

const tiers: Tier[] = [
    {
        name: "Starter",
        price: "R0",
        cadence: "per user / month",
        blurb: "For small teams getting started with secure document exchange.",
        bullets: [
            "Up to 3 users",
            "Secure share sessions",
            "Basic audit log",
            "Email support",
        ],
        cta: "Start free",
    },
    {
        name: "Business",
        price: "R180",
        cadence: "per user / month (20% off annual)",
        blurb: "For growing teams that need controls, SSO, and integrations. Annual plans get a 20% discount.",
        bullets: [
            "Unlimited share sessions",
            "Microsoft & Google SSO",
            "Role-based permissions",
            "Full audit & export",
            "Priority support",
            "WhatsApp App integration",
        ],
        cta: "Start trial",
        featured: true,
    },
    {
        name: "Enterprise",
        price: "Custom",
        cadence: "tailored to your org",
        blurb: "For regulated organizations with compliance and procurement needs.",
        bullets: [
            "Custom user caps",
            "SOC 2 reports & DPA",
            "Tenant isolation review",
            "Dedicated CSM",
            "Custom data residency",
            "Full Integration Assistance/Support",
            "FTP / Google Drive / One Drive Backup",
            "Microsoft Teams App integration",
            "Slack App integration",
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

    bulletIcon: {
        color: tokens.colorBrandForeground1,
        flexShrink: 0,
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
                        Start free. Scale with controls when you need them. Talk to us for enterprise needs.
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
                            <div className={styles.priceRow}>
                                <Text className={styles.price}>{tier.price}</Text>
                                <Text className={styles.cadence}>{tier.cadence}</Text>
                            </div>
                            <Text className={styles.blurb}>{tier.blurb}</Text>
                            <ul className={styles.bullets}>
                                {tier.bullets.map((b) => (
                                    <li key={b} className={styles.bullet}>
                                        <Checkmark20Filled className={styles.bulletIcon}/>
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
