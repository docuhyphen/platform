import {mkdir, readFile, writeFile} from "node:fs/promises";
import path from "node:path";
import {fileURLToPath, pathToFileURL} from "node:url";

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const distDirectory = path.join(projectRoot, "dist");
const serverEntryUrl = pathToFileURL(path.join(projectRoot, "dist-ssr", "entry-server.js")).href;
process.env.NODE_ENV = "production";
const {INDEXABLE_ROUTES, PRERENDERED_ROUTES, renderApp, renderSeoHead, renderStyleHead} = await import(serverEntryUrl);
const template = await readFile(path.join(distDirectory, "index.html"), "utf8");

function outputPath(route)
{
    if (route === "/") return path.join(distDirectory, "index.html");
    return path.join(distDirectory, route.slice(1), "index.html");
}

for (const route of PRERENDERED_ROUTES)
{
    const appMarkup = renderApp(route);
    const headMarkup = `${renderSeoHead(route)}\n    ${renderStyleHead()}`;
    const html = template
        .replace("<!--docuhyphen-seo-->", headMarkup)
        .replace('<div id="docu-hyphen-app"></div>', `<div id="docu-hyphen-app">${appMarkup}</div>`);
    const destination = outputPath(route);
    await mkdir(path.dirname(destination), {recursive: true});
    await writeFile(destination, html, "utf8");
}

const sitemapEntries = INDEXABLE_ROUTES
    .map((route) => `  <url><loc>${new URL(route, "https://www.docuhyphen.com").toString()}</loc></url>`)
    .join("\n");
const sitemap = `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
${sitemapEntries}
</urlset>
`;
await writeFile(path.join(distDirectory, "sitemap.xml"), sitemap, "utf8");

console.log(`Pre-rendered ${PRERENDERED_ROUTES.length} routes and generated sitemap.xml.`);
