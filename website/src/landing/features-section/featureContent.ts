export interface IndustryExperience
{
    slug: string;
    title: string;
    outcome: string;
    screenshotSrc: string;
}

export const industryExperiences: readonly IndustryExperience[] = [
    {
        slug: "legal",
        title: "Law Firms & Legal Practices",
        outcome: "Coordinate discovery, client documents, matter approvals, and controlled external collaboration.",
        screenshotSrc: "/demo-screenshots/app-screenshot-legal.JPG",
    },
    {
        slug: "real-estate",
        title: "Real Estate & Property Management",
        outcome: "Keep property documents, stakeholders, approvals, and transaction progress connected.",
        screenshotSrc: "/demo-screenshots/app-screenshot-real-estate.JPG",
    },
    {
        slug: "healthcare",
        title: "Healthcare & Medical Practices",
        outcome: "Exchange sensitive records through structured processes with clear access and accountability.",
        screenshotSrc: "/demo-screenshots/app-screenshot-healthcare.JPG",
    },
    {
        slug: "accounting",
        title: "Accounting & Audit Firms",
        outcome: "Collect evidence, coordinate reviews, and maintain a complete history across every engagement.",
        screenshotSrc: "/demo-screenshots/app-screenshot-accounting.JPG",
    },
    {
        slug: "banking",
        title: "Banks & Lending Institutions",
        outcome: "Manage document-heavy applications, reviews, decisions, and customer communication securely.",
        screenshotSrc: "/demo-screenshots/app-screenshot-banking-lending.JPG",
    },
];
