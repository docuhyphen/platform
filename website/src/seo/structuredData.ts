import {absoluteUrl, SITE_URL} from "./seoConfig.ts";
import type {RouteSeo} from "./seoConfig.ts";

type JsonLdValue = Record<string, unknown>;

const organization: JsonLdValue = {
    "@type": "Organization",
    "@id": `${SITE_URL}/#organization`,
    name: "DocuHyphen",
    url: SITE_URL,
    logo: absoluteUrl("/favicon.svg"),
};

function breadcrumbList(metadata: RouteSeo): JsonLdValue | null
{
    if (!metadata.breadcrumbs) return null;

    return {
        "@type": "BreadcrumbList",
        itemListElement: metadata.breadcrumbs.map((item, index) => ({
            "@type": "ListItem",
            position: index + 1,
            name: item.name,
            item: absoluteUrl(item.path),
        })),
    };
}

export function createStructuredData(metadata: RouteSeo): JsonLdValue | null
{
    if (metadata.pageType === "not-found" || metadata.pageType === "pricing") return null;

    if (metadata.pageType === "home")
    {
        return {
            "@context": "https://schema.org",
            "@graph": [
                {
                    "@type": "WebSite",
                    "@id": `${SITE_URL}/#website`,
                    name: "DocuHyphen",
                    url: SITE_URL,
                    publisher: {"@id": `${SITE_URL}/#organization`},
                },
                organization,
                {
                    "@type": "WebApplication",
                    "@id": `${SITE_URL}/#application`,
                    name: "DocuHyphen",
                    url: SITE_URL,
                    applicationCategory: "BusinessApplication",
                    operatingSystem: "Web",
                    description: metadata.description,
                    provider: {"@id": `${SITE_URL}/#organization`},
                },
            ],
        };
    }

    const breadcrumbs = breadcrumbList(metadata);
    const graph: JsonLdValue[] = [
        {
            "@type": "WebPage",
            "@id": `${absoluteUrl(metadata.canonicalPath)}#webpage`,
            name: metadata.title,
            description: metadata.description,
            url: absoluteUrl(metadata.canonicalPath),
            isPartOf: {"@id": `${SITE_URL}/#website`},
        },
    ];
    if (breadcrumbs) graph.push(breadcrumbs);

    return {"@context": "https://schema.org", "@graph": graph};
}

