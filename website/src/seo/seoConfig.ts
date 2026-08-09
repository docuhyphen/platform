export const SITE_URL = "https://www.docuhyphen.com";
export const SOCIAL_IMAGE_PATH = "/social/docuhyphen-social-card.webp";

export const INDEXABLE_ROUTES = [
    "/",
    "/security",
    "/about",
    "/contact",
    "/industries/real-estate",
    "/industries/legal",
    "/industries/healthcare",
    "/industries/accounting",
    "/industries/banking",
] as const;

export const PRERENDERED_ROUTES = [...INDEXABLE_ROUTES, "/pricing", "/404"] as const;

export type SeoRobots = "index,follow" | "noindex,follow" | "noindex,nofollow";

export type BreadcrumbItem = {
    name: string;
    path: string;
};

export type RouteSeo = {
    path: string;
    title: string;
    description: string;
    canonicalPath: string;
    robots: SeoRobots;
    openGraphTitle: string;
    openGraphDescription: string;
    openGraphPath: string;
    openGraphImagePath: string;
    twitterCard: "summary_large_image";
    twitterTitle: string;
    twitterDescription: string;
    twitterImagePath: string;
    pageType: "home" | "webpage" | "industry" | "pricing" | "not-found";
    breadcrumbs?: readonly BreadcrumbItem[];
};

type RouteSeoInput = Omit<RouteSeo,
    "openGraphTitle" | "openGraphDescription" | "openGraphPath" | "openGraphImagePath" |
    "twitterCard" | "twitterTitle" | "twitterDescription" | "twitterImagePath"
>;

function defineRoute(input: RouteSeoInput): RouteSeo
{
    return {
        ...input,
        openGraphTitle: input.title,
        openGraphDescription: input.description,
        openGraphPath: input.canonicalPath,
        openGraphImagePath: SOCIAL_IMAGE_PATH,
        twitterCard: "summary_large_image",
        twitterTitle: input.title,
        twitterDescription: input.description,
        twitterImagePath: SOCIAL_IMAGE_PATH,
    };
}

const routeSeoByPath: Readonly<Record<string, RouteSeo>> = {
    "/": defineRoute({
        path: "/",
        title: "Secure Document Exchanges and Workflows | DocuHyphen",
        description: "Request, collect, review, approve, share, and audit sensitive documents in one secure, controlled workspace.",
        canonicalPath: "/",
        robots: "index,follow",
        pageType: "home",
    }),
    "/security": defineRoute({
        path: "/security",
        title: "Document Security and Trust | DocuHyphen",
        description: "Learn how DocuHyphen supports controlled document access, protected storage, and traceable activity for sensitive Exchanges.",
        canonicalPath: "/security",
        robots: "index,follow",
        pageType: "webpage",
        breadcrumbs: [{name: "Home", path: "/"}, {name: "Security", path: "/security"}],
    }),
    // "/about" route SEO entry intentionally omitted while the page is hidden pending redesign
    "/contact": defineRoute({
        path: "/contact",
        title: "Contact DocuHyphen",
        description: "Contact DocuHyphen to discuss secure document Exchanges, workflow requirements, support, or a guided product demonstration.",
        canonicalPath: "/contact",
        robots: "index,follow",
        pageType: "webpage",
        breadcrumbs: [{name: "Home", path: "/"}, {name: "Contact", path: "/contact"}],
    }),
    "/pricing": defineRoute({
        path: "/pricing",
        title: "DocuHyphen Pricing | Free, Personal and Business Plans",
        description: "Compare DocuHyphen Free, Personal and Business plans with simple South African rand pricing and unlimited external recipients.",
        canonicalPath: "/pricing",
        robots: "index,follow",
        pageType: "pricing",
        breadcrumbs: [{name: "Home", path: "/"}, {name: "Pricing", path: "/pricing"}],
    }),
    "/industries/real-estate": defineRoute({
        path: "/industries/real-estate",
        title: "Secure Property Document Exchanges | DocuHyphen",
        description: "Collect property, identity, lease, and transaction documents in a secure, traceable workspace for real estate teams.",
        canonicalPath: "/industries/real-estate",
        robots: "index,follow",
        pageType: "industry",
        breadcrumbs: [{name: "Home", path: "/"}, {name: "Industries", path: "/#industries"}, {name: "Real Estate", path: "/industries/real-estate"}],
    }),
    "/industries/legal": defineRoute({
        path: "/industries/legal",
        title: "Secure Document Exchanges for Law Firms | DocuHyphen",
        description: "Help law firms collect, review, share, and audit confidential matter documents in controlled client Exchanges.",
        canonicalPath: "/industries/legal",
        robots: "index,follow",
        pageType: "industry",
        breadcrumbs: [{name: "Home", path: "/"}, {name: "Industries", path: "/#industries"}, {name: "Legal", path: "/industries/legal"}],
    }),
    "/industries/healthcare": defineRoute({
        path: "/industries/healthcare",
        title: "Secure Healthcare Document Exchanges | DocuHyphen",
        description: "Request and share sensitive patient, referral, consent, and claim documents through traceable healthcare workflows.",
        canonicalPath: "/industries/healthcare",
        robots: "index,follow",
        pageType: "industry",
        breadcrumbs: [{name: "Home", path: "/"}, {name: "Industries", path: "/#industries"}, {name: "Healthcare", path: "/industries/healthcare"}],
    }),
    "/industries/accounting": defineRoute({
        path: "/industries/accounting",
        title: "Client Document Collection for Accounting Firms | DocuHyphen",
        description: "Collect, organize, review, and track client financial records in one controlled workspace per accounting engagement.",
        canonicalPath: "/industries/accounting",
        robots: "index,follow",
        pageType: "industry",
        breadcrumbs: [{name: "Home", path: "/"}, {name: "Industries", path: "/#industries"}, {name: "Accounting", path: "/industries/accounting"}],
    }),
    "/industries/banking": defineRoute({
        path: "/industries/banking",
        title: "Secure Lending Document Workflows | DocuHyphen",
        description: "Collect and review borrower, lending, and KYC documents with controlled access and traceable banking workflows.",
        canonicalPath: "/industries/banking",
        robots: "index,follow",
        pageType: "industry",
        breadcrumbs: [{name: "Home", path: "/"}, {name: "Industries", path: "/#industries"}, {name: "Banking", path: "/industries/banking"}],
    }),
};

export const NOT_FOUND_SEO = defineRoute({
    path: "/404",
    title: "Page Not Found | DocuHyphen",
    description: "The requested DocuHyphen page could not be found.",
    canonicalPath: "/404",
    robots: "noindex,nofollow",
    pageType: "not-found",
});

export function normalizePath(pathname: string): string
{
    if (pathname === "/") return pathname;
    return pathname.replace(/\/+$/, "");
}

export function getRouteSeo(pathname: string): RouteSeo
{
    return routeSeoByPath[normalizePath(pathname)] ?? NOT_FOUND_SEO;
}

export function absoluteUrl(path: string): string
{
    return new URL(path, SITE_URL).toString();
}
