import {readFile, writeFile} from "node:fs/promises";

const [responsePath, configPath, etagPath] = process.argv.slice(2);

if (!responsePath || !configPath || !etagPath)
{
    throw new Error("Expected CloudFront response, distribution config, and ETag output paths");
}

const response = JSON.parse(await readFile(responsePath, "utf8"));
const distributionConfig = response.DistributionConfig;

if (!response.ETag || !distributionConfig)
{
    throw new Error("CloudFront did not return an ETag and DistributionConfig");
}

const existingResponses = distributionConfig.CustomErrorResponses?.Items ?? [];
const retainedResponses = existingResponses.filter(({ErrorCode}) => ErrorCode !== 403 && ErrorCode !== 404);
const spaResponses = [403, 404].map((ErrorCode) => ({
    ErrorCode,
    ResponsePagePath: "/index.html",
    ResponseCode: "200",
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
