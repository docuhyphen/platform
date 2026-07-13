export interface IndustryPersona
{
    name: string;
    email: string;
    organization: string;
    organizationShortName: string;
    initials: string;
}

export interface IndustryExchangeExample
{
    title: string;
    summary: string;
    updated: string;
}

export interface IndustryDocumentExample
{
    title: string;
    previewSrc: string;
    pdfSrc: string;
}

export interface IndustryExperience
{
    slug: string;
    title: string;
    outcome: string;
    persona: IndustryPersona;
    exchanges: readonly IndustryExchangeExample[];
    documentLabels: readonly string[];
    document: IndustryDocumentExample;
}

export const industryExperiences: readonly IndustryExperience[] = [
    {
        slug: "legal",
        title: "Law Firms & Legal Practices",
        outcome: "Coordinate discovery, client documents, matter approvals, and controlled external collaboration.",
        persona: {
            name: "Rowan Vance",
            email: "rowan.vance@hvmlegal.example",
            organization: "Halbrook, Vance & Mercer LLP",
            organizationShortName: "HVM",
            initials: "RV",
        },
        exchanges: [
            {title: "Pre-Trial Evidence Preparation", summary: "Prepare the evidence record for counsel review.", updated: "Today"},
            {title: "Discovery Production Coordination", summary: "Collect and review responsive matter documents.", updated: "12 Jul"},
            {title: "Commercial Contract Review", summary: "Coordinate the agreement and supporting schedules.", updated: "11 Jul"},
            {title: "Settlement Readiness Dossier", summary: "Assemble the settlement position and exhibits.", updated: "9 Jul"},
            {title: "Client Intake Verification", summary: "Gather identification and engagement documents.", updated: "8 Jul"},
            {title: "Expert Witness File Review", summary: "Share reports, exhibits, and correspondence.", updated: "7 Jul"},
            {title: "Regulatory Filing Package", summary: "Prepare signed forms and supporting attachments.", updated: "5 Jul"},
            {title: "Board Resolution Exchange", summary: "Collect approvals and final execution copies.", updated: "3 Jul"},
        ],
        documentLabels: ["Case File Summary", "Evidence Index", "Event Timeline", "Witness Statements", "Court Filing Draft"],
        document: {
            title: "Case File Summary",
            previewSrc: "/demo-previews/law-firms-legal-practices.svg",
            pdfSrc: "/demo-pdfs/law-firms-legal-practices.pdf",
        },
    },
    {
        slug: "real-estate",
        title: "Real Estate & Property Management",
        outcome: "Keep property documents, stakeholders, approvals, and transaction progress connected.",
        persona: {
            name: "Maya Jacobs",
            email: "maya.jacobs@latticepeak.example",
            organization: "Lattice Peak Property Partners",
            organizationShortName: "LPP",
            initials: "MJ",
        },
        exchanges: [
            {title: "Harbor View Acquisition Review", summary: "Review the investment report and transaction file.", updated: "Today"},
            {title: "Seabrook Tenant Onboarding", summary: "Collect tenant identity and lease documents.", updated: "12 Jul"},
            {title: "Oakline Property Due Diligence", summary: "Coordinate inspections, disclosures, and approvals.", updated: "10 Jul"},
            {title: "Riverside Lease Renewal", summary: "Complete the annual lease renewal package.", updated: "8 Jul"},
            {title: "Portfolio Valuation Update", summary: "Share valuation reports and owner approvals.", updated: "7 Jul"},
            {title: "Vendor Compliance Review", summary: "Collect insurance, tax, and service documents.", updated: "6 Jul"},
            {title: "New Development Handover", summary: "Assemble warranties, drawings, and permits.", updated: "4 Jul"},
            {title: "Tenant Exit Documentation", summary: "Track inspection notes and deposit records.", updated: "2 Jul"},
        ],
        documentLabels: ["Investment Report", "Sale Agreement", "Due Diligence", "Inspection Report", "Title Deed"],
        document: {
            title: "Property Investment Report",
            previewSrc: "/demo-previews/real-estate-property-management.svg",
            pdfSrc: "/demo-pdfs/real-estate-property-management.pdf",
        },
    },
    {
        slug: "healthcare",
        title: "Healthcare & Medical Practices",
        outcome: "Exchange sensitive records through structured processes with clear access and accountability.",
        persona: {
            name: "Dr. Amelia Nolan",
            email: "amelia.nolan@cedargrove.example",
            organization: "Cedar Grove Family Medicine",
            organizationShortName: "CGFM",
            initials: "AN",
        },
        exchanges: [
            {title: "Specialist Referral Review", summary: "Share the referral summary and recent results.", updated: "Today"},
            {title: "Patient Record Transfer", summary: "Coordinate a secure clinical record handover.", updated: "12 Jul"},
            {title: "Insurance Claim Documentation", summary: "Collect supporting records for claim review.", updated: "11 Jul"},
            {title: "New Patient Intake", summary: "Complete consent and intake documentation.", updated: "9 Jul"},
            {title: "Care Plan Authorization", summary: "Route plans and approvals between providers.", updated: "8 Jul"},
            {title: "Lab Results Follow-Up", summary: "Share results and related clinical notes.", updated: "6 Jul"},
            {title: "Medical Aid Pre-Approval", summary: "Collect forms, history, and procedure details.", updated: "5 Jul"},
            {title: "Provider Credential Review", summary: "Verify licenses, certifications, and policies.", updated: "3 Jul"},
        ],
        documentLabels: ["Referral Summary", "Test Results", "Patient Consent", "Clinical Notes", "Insurance Authorization"],
        document: {
            title: "Patient Referral Summary",
            previewSrc: "/demo-previews/healthcare-medical-practices.svg",
            pdfSrc: "/demo-pdfs/healthcare-medical-practices.pdf",
        },
    },
    {
        slug: "accounting",
        title: "Accounting & Audit Firms",
        outcome: "Collect evidence, coordinate reviews, and maintain a complete history across every engagement.",
        persona: {
            name: "Evelyn Finch",
            email: "evelyn.finch@alderfinch.example",
            organization: "Alder & Finch Advisory LLP",
            organizationShortName: "A&F",
            initials: "EF",
        },
        exchanges: [
            {title: "Northstar Q1 Financial Review", summary: "Review the quarterly report and working papers.", updated: "Today"},
            {title: "Annual Audit Evidence Collection", summary: "Collect evidence and track outstanding requests.", updated: "12 Jul"},
            {title: "Corporate Tax Return Preparation", summary: "Coordinate records required for filing.", updated: "10 Jul"},
            {title: "Management Accounts Review", summary: "Share draft accounts for management approval.", updated: "8 Jul"},
            {title: "Payroll Compliance Package", summary: "Collect payroll reports and statutory filings.", updated: "7 Jul"},
            {title: "VAT Reconciliation Review", summary: "Exchange reconciliations and source records.", updated: "6 Jul"},
            {title: "Internal Controls Walkthrough", summary: "Gather process notes and control evidence.", updated: "4 Jul"},
            {title: "Year-End Close Checklist", summary: "Track close tasks and supporting schedules.", updated: "2 Jul"},
        ],
        documentLabels: ["Financial Report", "Trial Balance", "Audit Evidence", "Tax Schedule", "Management Letter"],
        document: {
            title: "Quarterly Financial Report",
            previewSrc: "/demo-previews/accounting-audit-firms.svg",
            pdfSrc: "/demo-pdfs/accounting-audit-firms.pdf",
        },
    },
    {
        slug: "banking",
        title: "Banks & Lending Institutions",
        outcome: "Manage document-heavy applications, reviews, decisions, and customer communication securely.",
        persona: {
            name: "Naledi Mokoena",
            email: "naledi.mokoena@meridiantrust.example",
            organization: "Meridian Trust Bank",
            organizationShortName: "MTB",
            initials: "NM",
        },
        exchanges: [
            {title: "Commercial Loan Assessment", summary: "Review the application and credit risk package.", updated: "Today"},
            {title: "Business KYC Refresh", summary: "Collect updated ownership and identity records.", updated: "12 Jul"},
            {title: "Home Loan Application", summary: "Coordinate applicant and valuation documents.", updated: "11 Jul"},
            {title: "Credit Committee Review", summary: "Share the decision pack with committee members.", updated: "9 Jul"},
            {title: "SME Facility Renewal", summary: "Gather statements, covenants, and renewals.", updated: "8 Jul"},
            {title: "Collateral Document Review", summary: "Collect title, valuation, and security records.", updated: "6 Jul"},
            {title: "Merchant Account Onboarding", summary: "Verify business details and compliance forms.", updated: "5 Jul"},
            {title: "Annual Risk Reassessment", summary: "Review exposure, ratings, and approvals.", updated: "3 Jul"},
        ],
        documentLabels: ["Credit Assessment", "Bank Statements", "Application Form", "KYC Pack", "Collateral Valuation"],
        document: {
            title: "Credit Risk Assessment",
            previewSrc: "/demo-previews/banks-lending-institutions.svg",
            pdfSrc: "/demo-pdfs/banks-lending-institutions.pdf",
        },
    },
];
