import {Button, Text, Title1, Title3, makeStyles, tokens} from "@fluentui/react-components";
import {ArrowRight20Regular, Checkmark20Filled} from "@fluentui/react-icons";
import {Navigate, useParams} from "react-router-dom";
import {LinkButton} from "../shared/LinkButton.tsx";
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

type SolutionContent = {
    name: string;
    headline: string;
    blurb: string;
    screenshotSrc: string;
    useCases: {title: string; body: string}[];
    benefits: string[];
    gradient: string;
};

const solutions: Record<string, SolutionContent> = {
    "real-estate": {
        name: "Real Estate & Property",
        headline: "Move deals forward without losing control of the paperwork.",
        blurb: "Collect offer documents, FICA records, and lease signatures from buyers, sellers, and tenants in one secure space.",
        screenshotSrc: "/demo-screenshots/app-screenshot-real-estate.JPG",
        useCases: [
            {title: "FICA collection", body: "Request ID, proof of address, and source-of-funds documents from buyers without email back-and-forth."},
            {title: "Lease onboarding", body: "Tenants upload signed leases, deposit proofs, and references to a tracked share session."},
            {title: "Listing handovers", body: "Hand mandate documents between agents and conveyancers with a full activity log."},
        ],
        benefits: [
            "Tracked access for every document",
            "No more long email threads with attachments",
            "POPIA-aligned data handling",
        ],
        gradient: "linear-gradient(135deg, #2d3953 0%, #4b678c 52%, #99b1cf 100%)",
    },
    "legal": {
        name: "Law Firms & Legal Practices",
        headline: "Privileged documents deserve privileged controls.",
        blurb: "Replace attachment email chains and consumer file-sharing tools with structured, auditable client exchanges.",
        screenshotSrc: "/demo-screenshots/app-screenshot-legal.JPG",
        useCases: [
            {title: "Client matter intake", body: "Collect contracts, affidavits, and discovery materials with role-restricted access."},
            {title: "Counsel collaboration", body: "Share matter documents with external counsel with revocable sessions."},
            {title: "Compliance archive", body: "Maintain a defensible audit trail per matter for every document touched."},
        ],
        benefits: [
            "Session-level revocation",
            "Per-matter audit reports",
            "Encryption at rest and in transit",
        ],
        gradient: "linear-gradient(135deg, #20344d 0%, #385980 55%, #7ea6d6 100%)",
    },
    "healthcare": {
        name: "Healthcare & Medical Practices",
        headline: "Patient documents handled with the care patients expect.",
        blurb: "Request consent forms, ID, and medical records from patients via tracked, encrypted share sessions.",
        screenshotSrc: "/demo-screenshots/app-screenshot-healthcare.JPG",
        useCases: [
            {title: "Patient intake", body: "Collect consent forms and IDs before the first visit,  no clipboards."},
            {title: "Referral documents", body: "Share imaging and reports between practitioners with auditable handovers."},
            {title: "Insurance claims", body: "Send claim documents to insurers with delivery confirmation."},
        ],
        benefits: [
            "Limit access to clinical staff by role",
            "Every view and download logged",
            "Designed for sensitive personal data",
        ],
        gradient: "linear-gradient(135deg, #24505a 0%, #3f8391 52%, #8fd0dc 100%)",
    },
    "accounting": {
        name: "Accounting & Audit Firms",
        headline: "Audit-ready collection of client financial records.",
        blurb: "Centralize tax records, trial balances, and supporting documents in one secure, trackable workspace per engagement.",
        screenshotSrc: "/demo-screenshots/app-screenshot-accounting.JPG",
        useCases: [
            {title: "Tax season intake", body: "One link per client to collect every record you need, with deadline tracking."},
            {title: "Audit fieldwork", body: "Workpaper requests with full activity history for the file."},
            {title: "Annual financials", body: "Share draft financials with clients via revocable sessions."},
        ],
        benefits: [
            "One organized session per engagement",
            "Audit-defensible activity log",
            "Annual archive for working papers",
        ],
        gradient: "linear-gradient(135deg, #2e3558 0%, #5c6da9 50%, #a2b5e8 100%)",
    },
    "banking": {
        name: "Banks & Lending Institutions",
        headline: "Faster KYC. Tighter control. Cleaner audits.",
        blurb: "Collect KYC, income verification, and compliance documents from borrowers and counterparties with full traceability.",
        screenshotSrc: "/demo-screenshots/app-screenshot-banking-lending.JPG",
        useCases: [
            {title: "Loan applications", body: "Borrowers upload paystubs, IDs, and bank statements to a single tracked session."},
            {title: "KYC refreshes", body: "Request updated documentation from clients on a schedule with auto-reminders."},
            {title: "Correspondent banking", body: "Exchange diligence packages with counterparties with role-restricted access."},
        ],
        benefits: [
            "Regulator-ready audit trails",
            "Risk-tiered access controls",
            "Designed to scale across branches",
        ],
        gradient: "linear-gradient(135deg, #243f63 0%, #3f6ba1 54%, #84b5ea 100%)",
    },
};

const useStyles = makeStyles({
    hero: {
        borderRadius: CARD_RADIUS,
        padding: "2.5rem",
        color: "white",
        display: "grid",
        gridTemplateColumns: "1.2fr 1fr",
        gap: SPACE_LG,
        alignItems: "center",

        [BREAKPOINT_MOBILE]: {
            gridTemplateColumns: "1fr",
            padding: SPACE_LG,
        },
    },

    heroText: {
        display: "flex",
        flexDirection: "column",
        gap: SPACE_SM,
    },

    heroEyebrow: {
        color: "rgba(255,255,255,0.85)",
        fontSize: tokens.fontSizeBase200,
        fontWeight: tokens.fontWeightSemibold,
        letterSpacing: "0.08em",
        textTransform: "uppercase",
    },

    heroTitle: {
        color: "white",
    },

    heroBlurb: {
        color: "rgba(255,255,255,0.9)",
    },

    heroActions: {
        display: "flex",
        gap: SPACE_SM,
        marginTop: SPACE_SM,
        flexWrap: "wrap",
    },

    heroImageWrap: {
        borderRadius: "0.75rem",
        overflow: "hidden",
        boxShadow: tokens.shadow16,
    },

    heroImage: {
        width: "100%",
        height: "auto",
        display: "block",
    },

    grid2: {
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

    panelTitle: {
        color: tokens.colorBrandForeground1,
        fontWeight: tokens.fontWeightSemibold,
    },

    useCase: {
        paddingTop: SPACE_SM,
        borderTop: `1px solid ${tokens.colorNeutralStroke2}`,
    },

    bulletList: {
        listStyle: "none",
        margin: 0,
        padding: 0,
        display: "flex",
        flexDirection: "column",
        gap: SPACE_XS,
    },

    bullet: {
        display: "flex",
        alignItems: "center",
        gap: SPACE_XS,
    },

    bulletIcon: {
        color: tokens.colorBrandForeground1,
    },

    finalCta: {
        marginTop: SPACE_LG,
        display: "flex",
        gap: SPACE_MD,
        flexWrap: "wrap",
        alignItems: "center",
    },
});

export function SolutionsPage()
{
    const styles = useStyles();
    const {industry} = useParams<{industry: string}>();

    if (!industry || !solutions[industry])
    {
        return <Navigate to="/" replace/>;
    }

    const data = solutions[industry];

    return (
        <PageShell>
            <section className={styles.hero} style={{background: data.gradient}}>
                <div className={styles.heroText}>
                    <Text className={styles.heroEyebrow}>Solutions / {data.name}</Text>
                    <Title1 className={styles.heroTitle}>{data.headline}</Title1>
                    <Text size={500} className={styles.heroBlurb}>{data.blurb}</Text>
                    <div className={styles.heroActions}>
                        <Button
                            appearance="primary"
                            as="a"
                            href={SIGN_UP_URL}
                            target="_blank"
                            rel="noopener noreferrer"
                            shape="circular"
                        >
                            Try it free
                        </Button>
                        <SpeakToSalesDialog
                            trigger={
                                <Button appearance="secondary" shape="circular">
                                    Speak to sales
                                </Button>
                            }
                        />
                    </div>
                </div>
                <div className={styles.heroImageWrap}>
                    <img src={data.screenshotSrc} alt={`${data.name} app screenshot`} className={styles.heroImage}/>
                </div>
            </section>

            <div className={styles.grid2}>
                <section className={styles.panel}>
                    <Title3 className={styles.panelTitle}>Use cases</Title3>
                    {data.useCases.map((u, i) => (
                        <div key={u.title} className={i === 0 ? undefined : styles.useCase}>
                            <Text weight="semibold">{u.title}</Text><br/>
                            <Text>{u.body}</Text>
                        </div>
                    ))}
                </section>

                <section className={styles.panel}>
                    <Title3 className={styles.panelTitle}>How DocuHyphen helps</Title3>
                    <ul className={styles.bulletList}>
                        {data.benefits.map((b) => (
                            <li key={b} className={styles.bullet}>
                                <Checkmark20Filled className={styles.bulletIcon}/>
                                {b}
                            </li>
                        ))}
                    </ul>
                </section>
            </div>

            <section className={styles.finalCta}>
                <Text size={500}>Curious how this fits your team?</Text>
                <LinkButton
                    to="/pricing"
                    appearance="primary"
                    shape="circular"
                    icon={<ArrowRight20Regular/>}
                    iconPosition="after"
                >
                    See pricing
                </LinkButton>
                <LinkButton to="/security" appearance="outline" shape="circular">
                    Read about security
                </LinkButton>
            </section>
        </PageShell>
    );
}
