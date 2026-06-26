import {
    Accordion,
    AccordionHeader,
    AccordionItem,
    AccordionPanel,
    Badge,
    Button,
    Text,
    Title1,
    Title3,
    makeStyles,
    mergeClasses,
    tokens,
} from "@fluentui/react-components";
import {Checkmark16Filled, Dismiss16Regular} from "@fluentui/react-icons";
import {Link} from "react-router-dom";
import {PageShell} from "../shared/PageShell.tsx";
import {SpeakToSalesDialog} from "../landing/SpeakToSalesDialog.tsx";
import {
    BREAKPOINT_MOBILE,
    CARD_RADIUS,
    SIGN_UP_URL,
    SPACE_LG,
    SPACE_MD,
    SPACE_SM,
    SPACE_XS,
} from "../landing/shared.ts";

const tiers = [
    {
        name: "Starter",
        monthly: 0,
        currency: "R",
        bestFor: "Best for: solo professionals and very small teams",
        proofPoint: "Get started in minutes with no contract required.",
        blurb: "For small teams starting secure document exchange with essential controls.",
        bullets: ["Up to 3 users", "Collaborative exchange sessions", "30-day audit log retention", "Email support"],
        ctaLabel: "Start free",
        ctaHref: SIGN_UP_URL,
        featured: false,
    },
    {
        name: "Business",
        monthly: 180,
        currency: "R",
        bestFor: "Best for: growing teams that need SSO and governance",
        proofPoint: "Includes DPA support and audit export for security reviews.",
        blurb: "Everything in Starter, plus SSO, role controls, and compliance-ready audit exports.",
        bullets: [
            "Everything in Starter",
            "Unlimited exchange sessions",
            "Microsoft & Google SSO",
            "Role-based permissions",
            "1-year audit retention and export",
            "Priority support",
            "WhatsApp integration",
            "Google Drive / OneDrive backups",
            "Microsoft Teams integration",
            "Slack integration",
        ],
        ctaLabel: "Start trial",
        ctaHref: SIGN_UP_URL,
        featured: true,
    },
    {
        name: "Enterprise",
        monthly: null,
        currency: "R",
        bestFor: "Best for: regulated organizations with procurement workflows",
        proofPoint: "Security, legal, and onboarding support aligned to enterprise requirements.",
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
        ctaLabel: "Talk to sales",
        ctaHref: null,
        featured: false,
    },
];

type CompareRow = {
    feature: string;
    starter: boolean | string;
    business: boolean | string;
    enterprise: boolean | string;
};

const compareRows: CompareRow[] = [
    {feature: "Users", starter: "Up to 3", business: "Unlimited", enterprise: "Custom"},
    {feature: "Collaborative exchange sessions", starter: true, business: true, enterprise: true},
    {feature: "Microsoft & Google SSO", starter: false, business: true, enterprise: true},
    {feature: "Role-based permissions", starter: false, business: true, enterprise: true},
    {feature: "Audit log retention", starter: "30 days", business: "1 year", enterprise: "Custom"},
    {feature: "Audit log export", starter: false, business: true, enterprise: true},
    {feature: "SOC 2 report access", starter: false, business: false, enterprise: true},
    {feature: "Data Processing Agreement", starter: false, business: true, enterprise: true},
    {feature: "Custom data residency", starter: false, business: false, enterprise: true},
    {feature: "Dedicated customer success", starter: false, business: false, enterprise: true},
    {feature: "WhatsApp integration", starter: false, business: true, enterprise: true},
    {feature: "Integration onboarding and support", starter: false, business: false, enterprise: true},
    {feature: "Google Drive / OneDrive backups", starter: false, business: true, enterprise: true},
    {feature: "FTP backups", starter: false, business: false, enterprise: true},
    {feature: "Microsoft Teams integration", starter: false, business: true, enterprise: true},
    {feature: "Slack integration", starter: false, business: true, enterprise: true},
];

const faqs = [
    {
        q: "Is there really a free tier?",
        a: "Yes. The Starter plan is free forever for up to 3 users so small teams can use DocuHyphen without commitment.",
    },
    {
        q: "Can I switch plans later?",
        a: "Yes. Upgrade or downgrade at any time from your organization settings. Monthly plans can be canceled anytime.",
    },
    {
        q: "Do you offer non-profit or education discounts?",
        a: "Yes. Get in touch with sales for a 30% discount on Business plans for verified non-profits and educational institutions.",
    },
    {
        q: "What payment methods do you accept?",
        a: "Credit card for self-serve. Enterprise customers can pay by invoice (NET 30) and EFT.",
    },
    {
        q: "Do you offer uptime SLAs?",
        a: "Yes. Business includes standard support, and Enterprise plans can include contractual uptime and response SLAs based on your requirements.",
    },
    {
        q: "Is my data really mine?",
        a: "Yes. You can export every document, share session, and audit log at any time. We never use your data to train models or for marketing.",
    },
];

const useStyles = makeStyles({
    hero: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        textAlign: "center",
        gap: SPACE_SM,
        paddingBottom: SPACE_LG,
    },

    heroTitle: {
        color: tokens.colorBrandForeground1,
        fontWeight: tokens.fontWeightSemibold,
    },

    heroSubtitle: {
        color: tokens.colorNeutralForeground2,
        maxWidth: "40rem",
    },

    tierGrid: {
        display: "grid",
        gridTemplateColumns: "repeat(3, 1fr)",
        gap: SPACE_LG,

        [BREAKPOINT_MOBILE]: {
            gridTemplateColumns: "1fr",
            gap: SPACE_MD,
        },
    },

    tierCard: {
        position: "relative",
        backgroundColor: tokens.colorNeutralBackground1,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: CARD_RADIUS,
        padding: SPACE_LG,
        display: "flex",
        flexDirection: "column",
        gap: SPACE_SM,
    },

    tierFeatured: {
        border: `2px solid ${tokens.colorBrandStroke1}`,
        boxShadow: tokens.shadow16,
    },

    featuredBadge: {
        position: "absolute",
        top: "-0.7rem",
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

    price: {
        fontSize: "2.5rem",
        fontWeight: tokens.fontWeightSemibold,
    },

    cadence: {
        color: tokens.colorNeutralForeground3,
        fontSize: tokens.fontSizeBase200,
    },

    bestFor: {
        color: tokens.colorNeutralForeground1,
        fontWeight: tokens.fontWeightSemibold,
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

    metaIconProof: {
        backgroundColor: tokens.colorNeutralBackground3,
        color: tokens.colorNeutralForeground2,
    },

    proofPoint: {
        color: tokens.colorNeutralForeground2,
        fontSize: tokens.fontSizeBase200,
    },

    bullets: {
        listStyle: "none",
        margin: 0,
        padding: 0,
        display: "flex",
        flexDirection: "column",
        gap: SPACE_XS,
        flex: 1,
    },

    bullet: {
        display: "flex",
        alignItems: "center",
        gap: SPACE_XS,
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

    compareWrapper: {
        marginTop: "3rem",
        display: "flex",
        flexDirection: "column",
        gap: SPACE_MD,
    },

    compareTitle: {
        color: tokens.colorBrandForeground1,
        fontWeight: tokens.fontWeightSemibold,
    },

    tableScroll: {
        width: "100%",
        overflowX: "auto",
        WebkitOverflowScrolling: "touch",
        borderRadius: CARD_RADIUS,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
    },

    table: {
        width: "100%",
        minWidth: "36rem",
        borderCollapse: "collapse",
        backgroundColor: tokens.colorNeutralBackground1,
    },

    th: {
        textAlign: "left",
        padding: SPACE_MD,
        borderBottom: `1px solid ${tokens.colorNeutralStroke2}`,
        backgroundColor: tokens.colorNeutralBackground2,
        fontWeight: tokens.fontWeightSemibold,
    },

    td: {
        padding: SPACE_MD,
        borderBottom: `1px solid ${tokens.colorNeutralStroke2}`,
        color: tokens.colorNeutralForeground2,
    },

    yes: {color: tokens.colorPaletteGreenForeground1},
    no: {color: tokens.colorNeutralForeground4},

    faqWrapper: {
        marginTop: "3rem",
        display: "flex",
        flexDirection: "column",
        gap: SPACE_MD,
    },
});

export function PricingPage()
{
    const styles = useStyles();

    return (
        <PageShell>
            <section className={styles.hero}>
                <Title1 className={styles.heroTitle}>Pricing</Title1>
                <Text size={500} className={styles.heroSubtitle} align={"center"}>
                    Choose the best tier for your business on monthly billing, cancel anytime.
                </Text>
            </section>

            <div className={styles.tierGrid}>
                {tiers.map((tier) => {
                    const price = tier.monthly === null
                        ? "Custom"
                        : `${tier.currency}${tier.monthly}`;

                    return (
                        <article
                            key={tier.name}
                            className={mergeClasses(styles.tierCard, tier.featured && styles.tierFeatured)}
                        >
                            {tier.featured && <span className={styles.featuredBadge}>Most popular</span>}
                            <Title3>{tier.name}</Title3>
                            <div className={styles.metaRow}>
                                <span className={mergeClasses(styles.metaIcon, styles.metaIconBestFor)} aria-hidden="true">B</span>
                                <Text className={styles.bestFor}>{tier.bestFor}</Text>
                            </div>
                            <Text className={styles.price}>{price}</Text>
                            <Text className={styles.cadence}>
                                {tier.monthly === null ? "tailored to your org" : "per user / month, cancel anytime"}
                            </Text>
                            <Text>{tier.blurb}</Text>
                            <div className={styles.metaRow}>
                                <span className={mergeClasses(styles.metaIcon, styles.metaIconProof)} aria-hidden="true">P</span>
                                <Text className={styles.proofPoint}>{tier.proofPoint}</Text>
                            </div>
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
                            {tier.ctaHref ? (
                                <Button
                                    appearance={tier.featured ? "primary" : "secondary"}
                                    as="a"
                                    href={tier.ctaHref}
                                    target="_blank"
                                    rel="noopener noreferrer"
                                    shape="circular"
                                    className={styles.cta}
                                >
                                    {tier.ctaLabel}
                                </Button>
                            ) : (
                                <SpeakToSalesDialog
                                    trigger={
                                        <Button appearance="secondary" shape="circular" className={styles.cta}>
                                            {tier.ctaLabel}
                                        </Button>
                                    }
                                />
                            )}
                        </article>
                    );
                })}
            </div>

            <section className={styles.compareWrapper}>
                <Title3 className={styles.compareTitle}>Compare plans</Title3>
                <div className={styles.tableScroll}>
                    <table className={styles.table}>
                        <thead>
                            <tr>
                                <th className={styles.th}>Feature</th>
                                <th className={styles.th}>Starter</th>
                                <th className={styles.th}>Business</th>
                                <th className={styles.th}>Enterprise</th>
                            </tr>
                        </thead>
                        <tbody>
                            {compareRows.map((row) => (
                                <tr key={row.feature}>
                                    <td className={styles.td}>{row.feature}</td>
                                    <CompareCell value={row.starter} styles={styles}/>
                                    <CompareCell value={row.business} styles={styles}/>
                                    <CompareCell value={row.enterprise} styles={styles}/>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            </section>

            <section className={styles.faqWrapper}>
                <Title3 className={styles.compareTitle}>Frequently asked questions</Title3>
                <Accordion collapsible multiple>
                    {faqs.map((f, i) => (
                        <AccordionItem key={f.q} value={i}>
                            <AccordionHeader>{f.q}</AccordionHeader>
                            <AccordionPanel>
                                <Text>{f.a}</Text>
                            </AccordionPanel>
                        </AccordionItem>
                    ))}
                </Accordion>
                <Text>
                    Still have questions?{" "}
                    <Link to="/contact">Contact our team</Link>.
                </Text>
            </section>
        </PageShell>
    );
}

function CompareCell({value, styles}: {value: boolean | string; styles: ReturnType<typeof useStyles>})
{
    if (value === true)
    {
        return (
            <td className={styles.td}>
                <Badge size="medium" appearance="tint" icon={<Checkmark16Filled className={styles.yes}/>}/>
            </td>
        );
    }
    if (value === false) return <td className={styles.td}><Dismiss16Regular className={styles.no}/></td>;
    return <td className={styles.td}>{value}</td>;
}
