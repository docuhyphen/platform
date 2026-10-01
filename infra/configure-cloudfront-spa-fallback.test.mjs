import assert from "node:assert/strict";
import {execFile} from "node:child_process";
import {mkdtemp, readFile, rm, writeFile} from "node:fs/promises";
import {tmpdir} from "node:os";
import path from "node:path";
import {fileURLToPath} from "node:url";
import {promisify} from "node:util";
import test from "node:test";

const execFileAsync = promisify(execFile);
const scriptPath = fileURLToPath(new URL("./configure-cloudfront-spa-fallback.mjs", import.meta.url));

for (const [siteType, responsePagePath, responseCode] of [
    ["website", "/404/index.html", "404"],
    ["web-app", "/index.html", "200"],
])
{
    test(`${siteType} fallback preserves other error responses and is idempotent`, async () => {
        const directory = await mkdtemp(path.join(tmpdir(), "cloudfront-fallback-"));
        const responsePath = path.join(directory, "response.json");
        const configPath = path.join(directory, "config.json");
        const etagPath = path.join(directory, "etag.txt");

        try
        {
            const distributionConfig = {
                CallerReference: "existing-distribution",
                CustomErrorResponses: {
                    Quantity: 2,
                    Items: [
                        {ErrorCode: 500, ResponsePagePath: "/error.html", ResponseCode: "500"},
                        {ErrorCode: 403, ResponsePagePath: "/old.html", ResponseCode: "403"},
                    ],
                },
            };
            await writeFile(responsePath, JSON.stringify({ETag: "current-etag", DistributionConfig: distributionConfig}));

            const first = await execFileAsync(process.execPath, [scriptPath, responsePath, configPath, etagPath, siteType]);
            assert.equal(first.stdout, "true");
            assert.equal(await readFile(etagPath, "utf8"), "current-etag");

            const updated = JSON.parse(await readFile(configPath, "utf8"));
            assert.equal(updated.CustomErrorResponses.Quantity, 3);
            assert.deepEqual(updated.CustomErrorResponses.Items[0], distributionConfig.CustomErrorResponses.Items[0]);
            assert.deepEqual(updated.CustomErrorResponses.Items.slice(1), [403, 404].map((ErrorCode) => ({
                ErrorCode,
                ResponsePagePath: responsePagePath,
                ResponseCode: responseCode,
                ErrorCachingMinTTL: 0,
            })));

            await writeFile(responsePath, JSON.stringify({ETag: "next-etag", DistributionConfig: updated}));
            const second = await execFileAsync(process.execPath, [scriptPath, responsePath, configPath, etagPath, siteType]);
            assert.equal(second.stdout, "false");
        }
        finally
        {
            await rm(directory, {recursive: true, force: true});
        }
    });
}
