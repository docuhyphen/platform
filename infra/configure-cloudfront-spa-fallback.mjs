import {readFile, writeFile} from "node:fs/promises";

const [responsePath, configPath, etagPath, siteType] = process.argv.slice(2);

if (!responsePath || !configPath || !etagPath || !["website", "web-app"].includes(siteType))
{
    throw new Error("Expected CloudFront response, distribution config, ETag output paths, and site type");
}

const response = JSON.parse(await readFile(responsePath, "utf8"));
const distributionConfig = response.DistributionConfig;

if (!response.ETag || !distributionConfig)
{
    throw new Error("CloudFront did not return an ETag and DistributionConfig");
}

const existingResponses = distributionConfig.CustomErrorResponses?.Items ?? [];
const retainedResponses = existingResponses.filter(({ErrorCode}) => ErrorCode !== 403 && ErrorCode !== 404);
const responsePagePath = siteType === "website" ? "/404/index.html" : "/index.html";
const responseCode = siteType === "website" ? "404" : "200";
const spaResponses = [403, 404].map((ErrorCode) => ({
    ErrorCode,
    ResponsePagePath: responsePagePath,
    ResponseCode: responseCode,
    ErrorCachingMinTTL: 0,
}));
const updatedResponses = [...retainedResponses, ...spaResponses];
const changed = JSON.stringify(existingResponses) !== JSON.stringify(updatedResponses);

distributionConfig.CustomErrorResponses = {
    Quantity: updatedResponses.length,
    Items: updatedResponses,
};

await writeFile(configPath, `${JSON.stringify(distributionConfig, null, 2)}\n`, "utf8");
await writeFile(etagPath, response.ETag, "utf8");
process.stdout.write(changed ? "true" : "false");
