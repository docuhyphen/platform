import {FluentProvider, SSRProvider} from "@fluentui/react-components";
import {createDOMRenderer, RendererProvider, renderToStyleElements} from "@griffel/react";
import {renderToString} from "react-dom/server";
import {StaticRouter} from "react-router";
import App from "./App.tsx";
import {absoluteUrl, getRouteSeo, INDEXABLE_ROUTES, PRERENDERED_ROUTES} from "./seo/seoConfig.ts";
import {createStructuredData} from "./seo/structuredData.ts";
import {lightTheme} from "./theme.ts";

export {INDEXABLE_ROUTES, PRERENDERED_ROUTES};

let latestStyleMarkup = "";

function escapeHtml(value: string): string
{
    return value
        .replaceAll("&", "&amp;")
        .replaceAll('"', "&quot;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;");
}

export function renderApp(pathname: string): string
{
    const renderer = createDOMRenderer();
    const appMarkup = renderToString(
        <RendererProvider renderer={renderer}>
            <SSRProvider>
                <FluentProvider
                    theme={lightTheme}
                    id="fluent-provider"
                >
                    <StaticRouter location={pathname}>
                        <App/>
                    </StaticRouter>
                </FluentProvider>
            </SSRProvider>
        </RendererProvider>,
    );
    latestStyleMarkup = renderToString(<>{renderToStyleElements(renderer)}</>);
    return appMarkup;
}

export function renderStyleHead(): string
{
    return latestStyleMarkup;
}

export function renderSeoHead(pathname: string): string
{
    const metadata = getRouteSeo(pathname);
    const structuredData = createStructuredData(metadata);
    const tags = [
        `<title>${escapeHtml(metadata.title)}</title>`,
        `<meta name="description" content="${escapeHtml(metadata.description)}">`,
        `<meta name="robots" content="${metadata.robots}">`,
        `<link rel="canonical" href="${absoluteUrl(metadata.canonicalPath)}">`,
        '<meta property="og:type" content="website">',
        `<meta property="og:title" content="${escapeHtml(metadata.openGraphTitle)}">`,
        `<meta property="og:description" content="${escapeHtml(metadata.openGraphDescription)}">`,
        `<meta property="og:url" content="${absoluteUrl(metadata.openGraphPath)}">`,
        `<meta property="og:image" content="${absoluteUrl(metadata.openGraphImagePath)}">`,
        `<meta name="twitter:card" content="${metadata.twitterCard}">`,
        `<meta name="twitter:title" content="${escapeHtml(metadata.twitterTitle)}">`,
        `<meta name="twitter:description" content="${escapeHtml(metadata.twitterDescription)}">`,
        `<meta name="twitter:image" content="${absoluteUrl(metadata.twitterImagePath)}">`,
    ];

    if (structuredData)
    {
        const json = JSON.stringify(structuredData).replaceAll("<", "\\u003c");
        tags.push(`<script type="application/ld+json" data-docuhyphen-seo="true">${json}</script>`);
    }

    return tags.join("\n    ");
}
