export type IndustryHeroClassName =
    | "heroRealEstate"
    | "heroLegal"
    | "heroHealthcare"
    | "heroAccounting"
    | "heroBanking";

export type IndustriesContent = {
    name: string;
    headline: string;
    blurb: string;
    heroClassName: IndustryHeroClassName;
    useCases: {title: string; body: string}[];
    benefits: string[];
};

export const industries: Record<string, IndustriesContent> = {
    "real-estate": {
        name: "Real Estate & Property",
        headline: "Move deals forward without losing control of the paperwork.",
        blurb: "Collect offer documents, FICA records, and lease signatures from buyers, sellers, and tenants in one secure space.",
        heroClassName: "heroRealEstate",
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
    },
    legal: {
        name: "Law Firms & Legal Practices",
        headline: "Protect confidential legal information at every stage.",
        blurb: "Replace attachment email chains and consumer file-sharing tools with structured, auditable client Exchanges.",
        heroClassName: "heroLegal",
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
    },
    healthcare: {
        name: "Healthcare & Medical Practices",
        headline: "Patient documents handled with the care patients expect.",
        blurb: "Request consent forms, ID, and medical records from patients via tracked, encrypted Exchange sessions.",
        heroClassName: "heroHealthcare",
        useCases: [
            {title: "Patient intake", body: "Collect consent forms and IDs before the first visit, no clipboards."},
            {title: "Referral documents", body: "Share imaging and reports between practitioners with auditable handovers."},
            {title: "Insurance claims", body: "Send claim documents to insurers with delivery confirmation."},
        ],
        benefits: [
            "Limit access to clinical staff by role",
            "Every view and download logged",
            "Designed for sensitive personal data",
        ],
    },
    accounting: {
        name: "Accounting & Audit Firms",
        headline: "Audit-ready collection of client financial records.",
        blurb: "Centralize tax records, trial balances, and supporting documents in one secure, trackable workspace per engagement.",
        heroClassName: "heroAccounting",
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
    },
    banking: {
        name: "Banks & Lending Institutions",
        headline: "Faster KYC. Tighter control. Cleaner audits.",
        blurb: "Collect KYC, income verification, and compliance documents from borrowers and counterparties with full traceability.",
        heroClassName: "heroBanking",
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
    },
};
