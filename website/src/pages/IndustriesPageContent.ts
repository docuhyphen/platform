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
        blurb: "Real estate teams often chase offer documents, FICA records, and lease signatures across email. DocuHyphen collects them from buyers, sellers, and tenants in one controlled workspace so property processes can move forward with a clear record.",
        heroClassName: "heroRealEstate",
        useCases: [
            {title: "FICA collection", body: "Request ID, proof of address, and source-of-funds documents from buyers without email back-and-forth."},
            {title: "Lease onboarding", body: "Tenants upload signed leases, deposit proofs, and references to a tracked share session."},
            {title: "Listing handovers", body: "Hand mandate documents between agents and conveyancers with a full activity log."},
        ],
        benefits: [
            "Tracked access for every document",
            "No more long email threads with attachments",
            "Controlled handling of sensitive personal information",
        ],
    },
    legal: {
        name: "Law Firms & Legal Practices",
        headline: "Protect confidential legal information at every stage.",
        blurb: "Law firms need to exchange confidential matter documents without losing control in email chains. DocuHyphen provides structured, traceable client Exchanges so legal teams can collaborate with a clear activity record.",
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
        blurb: "Healthcare teams need to collect consent forms, identity records, and medical documents without fragmented handovers. DocuHyphen keeps requests and sharing in controlled Exchanges so staff can trace progress and access.",
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
        blurb: "Accounting firms often collect tax records, trial balances, and evidence through scattered requests. DocuHyphen centralizes collection and review in one controlled workspace per engagement so teams can maintain an organized record.",
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
        blurb: "Banks and lenders need to collect KYC, income, and lending documents from borrowers without opaque email chains. DocuHyphen structures requests, review, and access so each lending workflow has a traceable outcome.",
        heroClassName: "heroBanking",
        useCases: [
            {title: "Loan applications", body: "Borrowers upload paystubs, IDs, and bank statements to a single tracked session."},
            {title: "KYC refreshes", body: "Request updated documentation from clients on a schedule with auto-reminders."},
            {title: "Correspondent banking", body: "Exchange diligence packages with counterparties with role-restricted access."},
        ],
        benefits: [
            "Traceable document activity",
            "Role-based access controls",
            "Designed to scale across branches",
        ],
    },
};
