export type IndustryVisualClass =
    | "industryVisualLaw"
    | "industryVisualRealEstate"
    | "industryVisualAccounting"
    | "industryVisualHealthcare"
    | "industryVisualBanking";

export type IndustrySlide = {
    title: string;
    visualClass: IndustryVisualClass;
    screenshotSrc: string;
};

export const industrySlides: IndustrySlide[] = [
    {
        title: "Accounting & Audit Firms",
        visualClass: "industryVisualAccounting",
        screenshotSrc: "/demo-screenshots/app-screenshot-accounting.JPG",
    },
    {
        title: "Banks & Lending Institutions",
        visualClass: "industryVisualBanking",
        screenshotSrc: "/demo-screenshots/app-screenshot-banking-lending.JPG",
    },
    {
        title: "Law Firms & Legal Practices",
        visualClass: "industryVisualLaw",
        screenshotSrc: "/demo-screenshots/app-screenshot-legal.JPG",
    },
    {
        title: "Real Estate & Property Management",
        visualClass: "industryVisualRealEstate",
        screenshotSrc: "/demo-screenshots/app-screenshot-real-estate.JPG",
    },
    {
        title: "Healthcare & Medical Practices",
        visualClass: "industryVisualHealthcare",
        screenshotSrc: "/demo-screenshots/app-screenshot-healthcare.JPG",
    },
];
