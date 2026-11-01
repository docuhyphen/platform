export type AudienceItem = {
    title: string;
    body: string;
};

export type AudienceGroup = {
    title: string;
    items: AudienceItem[];
};

export const audienceGroups: AudienceGroup[] = [
    {
        title: "Financial & Professional Services",
        items: [
            {
                title: "Accounting & audit firms",
                body: "Secure Exchange of tax records, financial statements, and supporting documents.",
            },
            {
                title: "Financial advisors & wealth managers",
                body: "Protect client identity and financial data while maintaining full audit trails.",
            },
            {
                title: "Banks & lending institutions",
                body: "Collect and share KYC, income verification, and compliance documents securely.",
            },
        ],
    },
    {
        title: "Insurance & Risk Management",
        items: [
            {
                title: "Insurance companies",
                body: "Secure handling of policy documents, claims, and customer identity records.",
            },
            {
                title: "Insurance brokers & underwriters",
                body: "Controlled document sharing between clients, insurers, and assessors.",
            },
            {
                title: "Claims management firms",
                body: "Track document submissions with visibility and accountability.",
            },
        ],
    },
    {
        title: "Legal & Regulatory",
        items: [
            {
                title: "Law firms & legal practices",
                body: "Exchange contracts, affidavits, and sensitive legal documents with clients.",
            },
            {
                title: "Compliance & risk consultancies",
                body: "Maintain verifiable audit trails for regulated workflows.",
            },
            {
                title: "Corporate secretarial services",
                body: "Secure storage and exchange of statutory and company records.",
            },
        ],
    },
    {
        title: "Corporate & Enterprise Operations",
        items: [
            {
                title: "Medium to large enterprises",
                body: "Internal and external document sharing with verified parties.",
            },
            {
                title: "Procurement & vendor onboarding teams",
                body: "Collect compliance documents from suppliers securely.",
            },
            {
                title: "HR & payroll departments",
                body: "Exchange employee identity, tax, and contract documents safely.",
            },
        ],
    },
    {
        title: "Any document-driven team",
        items: [
            {
                title: "Project and operations teams",
                body: "Coordinate documents, responsibilities, and decisions across internal and external stakeholders.",
            },
            {
                title: "Growing businesses",
                body: "Replace scattered email attachments and shared-drive links with controlled document Exchanges.",
            },
            {
                title: "Specialist service providers",
                body: "Share client documents securely while keeping a clear record of every important action.",
            },
        ],
    },
];
