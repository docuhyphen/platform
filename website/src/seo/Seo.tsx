import {useEffect} from "react";
import {useLocation} from "react-router-dom";
import {absoluteUrl, getRouteSeo} from "./seoConfig.ts";
import {createStructuredData} from "./structuredData.ts";

const MANAGED_ATTRIBUTE = "data-docuhyphen-seo";

function setMeta(selector: string, attributes: Record<string, string>): void
{
    let element = document.head.querySelector<HTMLMetaElement>(selector);
    if (!element)
    {
        element = document.createElement("meta");
        element.setAttribute(MANAGED_ATTRIBUTE, "true");
        document.head.appendChild(element);
    }
    Object.entries(attributes).forEach(([name, value]) => element?.setAttribute(name, value));
}

export function Seo()
{
    const location = useLocation();

    useEffect(() =>
    {
        const metadata = getRouteSeo(location.pathname);
        document.title = metadata.title;
        setMeta('meta[name="description"]', {name: "description", content: metadata.description});
        setMeta('meta[name="robots"]', {name: "robots", content: metadata.robots});
        setMeta('meta[property="og:type"]', {property: "og:type", content: "website"});
        setMeta('meta[property="og:title"]', {property: "og:title", content: metadata.openGraphTitle});
        setMeta('meta[property="og:description"]', {property: "og:description", content: metadata.openGraphDescription});
        setMeta('meta[property="og:url"]', {property: "og:url", content: absoluteUrl(metadata.openGraphPath)});
        setMeta('meta[property="og:image"]', {property: "og:image", content: absoluteUrl(metadata.openGraphImagePath)});
        setMeta('meta[name="twitter:card"]', {name: "twitter:card", content: metadata.twitterCard});
        setMeta('meta[name="twitter:title"]', {name: "twitter:title", content: metadata.twitterTitle});
        setMeta('meta[name="twitter:description"]', {name: "twitter:description", content: metadata.twitterDescription});
        setMeta('meta[name="twitter:image"]', {name: "twitter:image", content: absoluteUrl(metadata.twitterImagePath)});

        let canonical = document.head.querySelector<HTMLLinkElement>('link[rel="canonical"]');
        if (!canonical)
        {
            canonical = document.createElement("link");
            canonical.rel = "canonical";
            canonical.setAttribute(MANAGED_ATTRIBUTE, "true");
            document.head.appendChild(canonical);
        }
        canonical.href = absoluteUrl(metadata.canonicalPath);

        document.head.querySelectorAll(`script[type="application/ld+json"][${MANAGED_ATTRIBUTE}]`).forEach((node) => node.remove());
        const structuredData = createStructuredData(metadata);
        if (structuredData)
        {
            const script = document.createElement("script");
            script.type = "application/ld+json";
            script.setAttribute(MANAGED_ATTRIBUTE, "true");
            script.textContent = JSON.stringify(structuredData);
            document.head.appendChild(script);
        }
    }, [location.pathname]);

    return null;
}

