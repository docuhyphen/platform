import {
    Accordion,
    AccordionHeader,
    AccordionItem,
    AccordionPanel,
    Button,
    Switch,
    Text,
    Title1,
    Title3,
    makeStyles,
    mergeClasses,
    tokens,
} from "@fluentui/react-components";
import {Checkmark16Filled, Dismiss16Regular} from "@fluentui/react-icons";
import {useState} from "react";
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
        annual: 0,
        currency: "R",
        blurb: "For small teams getting started with secure document exchange.",
        bullets: ["Up to 5 users", "Secure share sessions", "Basic audit log", "Email support"],
        ctaLabel: "Start free",
        ctaHref: SIGN_UP_URL,
        featured: false,
    },
    {
        name: "Business",
        monthly: 180,
        annual: 249,
        currency: "R",
        blurb: "For growing teams that need controls, SSO, and integrations.",
        bullets: [
            "Unlimited share sessions",
            "Microsoft & Google SSO",
            "Role-based permissions",
            "Full audit & export",
            "Priority support",
        ],
        ctaLabel: "Start trial",
        ctaHref: SIGN_UP_URL,
        featured: true,
    },
    {
        name: "Enterprise",
        monthly: null,
        annual: null,
        currency: "R",
        blurb: "For regulated organizations with compliance and procurement needs.",
        bullets: [
            "Custom user caps",
            "SOC 2 reports & DPA",
            "Tenant isolation review",
            "Dedicated CSM",
            "Custom data residency",
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
    {feature: "Users", starter: "Up to 5", business: "Unlimited", enterprise: "Unlimited"},
    {feature: "Secure share sessions", starter: true, business: true, enterprise: true},
    {feature: "Microsoft & Google SSO", starter: false, business: true, enterprise: true},
    {feature: "Role-based permissions", starter: false, business: true, enterprise: true},
    {feature: "Audit log retention", starter: "30 days", business: "1 year", enterprise: "Custom"},
    {feature: "Audit log export", starter: false, business: true, enterprise: true},
    {feature: "SOC 2 report access", starter: false, business: false, enterprise: true},
    {feature: "Data Processing Agreement", starter: false, business: true, enterprise: true},
    {feature: "Custom data residency", starter: false, business: false, enterprise: true},
    {feature: "Dedicated customer success", starter: false, business: false, enterprise: true},
];

const faqs = [
    {
        q: "Is there really a free tier?",
        a: "Yes. The Starter plan is free forever for up to 5 users so small teams can use DocuHyphen without commitment.",
    },
    {
        q: "Can I switch plans later?",
        a: "Yes. Upgrade or downgrade at any time from your organization settings. Annual subscriptions are pro-rated.",
    },
    {
        q: "Do you offer non-profit or education discounts?",
        a: "Yes. Get in touch with sales for a 30% discount on annual Business plans for verified non-profits and educational institutions.",
    },
    {
        q: "What payment methods do you accept?",
        a: "Credit card for self-serve. Enterprise customers can pay by invoice (NET 30) and EFT.",
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

    toggleRow: {
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        gap: SPACE_MD,
        marginTop: SPACE_SM,
    },

    savePill: {
        backgroundColor: tokens.colorPaletteGreenBackground2,
        color: tokens.colorPaletteGreenForeground1,
        padding: "0.15rem 0.6rem",
        borderRadius: "999px",
        fontSize: "0.7rem",
        fontWeight: tokens.fontWeightSemibold,
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

    bulletIcon: {
        color: tokens.colorBrandForeground1,
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
    const [annual, setAnnual] = useState(true);

    return (
        <PageShell>
            <section className={styles.hero}>
                <Title1 className={styles.heroTitle}>Simple, transparent pricing</Title1>
                <Text size={500} className={styles.heroSubtitle} align={"center"}>
                    Start free. Scale with controls when your team needs them. Talk to us for enterprise compliance.
                </Text>
                {/*<div className={styles.toggleRow}>*/}
                {/*    <Text weight={annual ? "regular" : "semibold"}>Monthly</Text>*/}
                {/*    <div>*/}
                {/*        <Switch checked={annual} onChange={(_, d) => setAnnual(d.checked)}/>*/}
                {/*    </div>*/}
                {/*    <Text weight={annual ? "semibold" : "regular"}>Annual</Text>*/}
                {/*    <span className={styles.savePill}>Save ~17%</span>*/}
                {/*</div>*/}
            </section>

            <div className={styles.tierGrid}>
                {tiers.map((tier) => {
                    const price = tier.monthly === null
                        ? "Custom"
                        : `${tier.currency}${annual ? tier.annual : tier.monthly}`;

                    return (
                        <article
                            key={tier.name}
                            className={mergeClasses(styles.tierCard, tier.featured && styles.tierFeatured)}
                        >
                            {tier.featured && <span className={styles.featuredBadge}>Most popular</span>}
                            <Title3>{tier.name}</Title3>
                            <Text className={styles.price}>{price}</Text>
                            <Text className={styles.cadence}>
                                {tier.monthly === null ? "tailored to your org" : "per user / month"}
                            </Text>
                            <Text>{tier.blurb}</Text>
                            <ul className={styles.bullets}>
                                {tier.bullets.map((b) => (
                                    <li key={b} className={styles.bullet}>
                                        <Checkmark16Filled className={styles.bulletIcon}/>
                                        {b}
                                    </li>
                                ))}
                            </ul>
                            {tier.ctaHref ? (
                                <Button
                                    appearance={tier.featured ? "primary" : "outline"}
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
                                        <Button appearance="outline" shape="circular" className={styles.cta}>
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
    if (value === true) return <td className={styles.td}><Checkmark16Filled className={styles.yes}/></td>;
    if (value === false) return <td className={styles.td}><Dismiss16Regular className={styles.no}/></td>;
    return <td className={styles.td}>{value}</td>;
}
