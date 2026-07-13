import {createHash, createPublicKey, verify as verifySignature} from "node:crypto";
import {readFileSync} from "node:fs";
import {inflateRawSync} from "node:zlib";

const MAX_ENTRIES = 32;
const MAX_ENTRY_BYTES = 256 * 1024 * 1024;
const MAX_TOTAL_BYTES = 512 * 1024 * 1024;
const EXPECTED_ENTRIES = new Set([
    "manifest.json",
    "events.jsonl",
    "events.csv",
    "integrity.json",
    "signature.json",
    "README.txt",
    "verify.mjs",
]);

const fail = (message) =>
{
    throw new Error(message);
};

const sha256 = (value) => createHash("sha256").update(value).digest();
const sha256Hex = (value) => sha256(value).toString("hex");

const findEndOfCentralDirectory = (bytes) =>
{
    const minimum = Math.max(0, bytes.length - 65557);
    for (let offset = bytes.length - 22; offset >= minimum; offset -= 1)
    {
        if (bytes.readUInt32LE(offset) === 0x06054b50)
        {
            return offset;
        }
    }
    fail("ZIP end-of-central-directory record is missing");
};

const readZip = (bytes) =>
{
    const endOffset = findEndOfCentralDirectory(bytes);
    const entryCount = bytes.readUInt16LE(endOffset + 10);
    const centralOffset = bytes.readUInt32LE(endOffset + 16);
    if (entryCount > MAX_ENTRIES)
    {
        fail(`ZIP contains too many entries: ${entryCount}`);
    }

    const entries = new Map();
    let totalBytes = 0;
    let offset = centralOffset;
    for (let index = 0; index < entryCount; index += 1)
    {
        if (bytes.readUInt32LE(offset) !== 0x02014b50)
        {
            fail("ZIP central directory is malformed");
        }
        const compression = bytes.readUInt16LE(offset + 10);
        const compressedSize = bytes.readUInt32LE(offset + 20);
        const uncompressedSize = bytes.readUInt32LE(offset + 24);
        const nameLength = bytes.readUInt16LE(offset + 28);
        const extraLength = bytes.readUInt16LE(offset + 30);
        const commentLength = bytes.readUInt16LE(offset + 32);
        const localOffset = bytes.readUInt32LE(offset + 42);
        const name = bytes.subarray(offset + 46, offset + 46 + nameLength).toString("utf8");
        if (entries.has(name))
        {
            fail(`ZIP contains duplicate entry: ${name}`);
        }
        if (uncompressedSize > MAX_ENTRY_BYTES || compressedSize > MAX_ENTRY_BYTES)
        {
            fail(`ZIP entry exceeds the size limit: ${name}`);
        }
        if (bytes.readUInt32LE(localOffset) !== 0x04034b50)
        {
            fail(`ZIP local header is malformed: ${name}`);
        }
        const localNameLength = bytes.readUInt16LE(localOffset + 26);
        const localExtraLength = bytes.readUInt16LE(localOffset + 28);
        const dataOffset = localOffset + 30 + localNameLength + localExtraLength;
        const compressed = bytes.subarray(dataOffset, dataOffset + compressedSize);
        const content = compression === 0
            ? Buffer.from(compressed)
            : compression === 8
                ? inflateRawSync(compressed, {maxOutputLength: MAX_ENTRY_BYTES})
                : fail(`Unsupported ZIP compression method ${compression} for ${name}`);
        if (content.length !== uncompressedSize)
        {
            fail(`ZIP entry length does not match its central-directory metadata: ${name}`);
        }
        totalBytes += content.length;
        if (totalBytes > MAX_TOTAL_BYTES)
        {
            fail("ZIP uncompressed content exceeds the total size limit");
        }
        entries.set(name, content);
        offset += 46 + nameLength + extraLength + commentLength;
    }
    return entries;
};

const merkleRoot = (hexLeaves) =>
{
    if (hexLeaves.length === 0)
    {
        return sha256Hex(Buffer.alloc(0));
    }
    let level = hexLeaves.map((value) => Buffer.from(value, "hex"));
    while (level.length > 1)
    {
        const next = [];
        for (let index = 0; index < level.length; index += 2)
        {
            const right = index + 1 < level.length ? level[index + 1] : level[index];
            next.push(sha256(Buffer.concat([level[index], right])));
        }
        level = next;
    }
    return level[0].toString("hex");
};

const canonicalEvent = (event) => ({
    eventId: event.eventId,
    eventTypeKey: event.eventTypeKey,
    category: event.category,
    outcome: event.outcome,
    schemaVersion: event.schemaVersion,
    occurredAt: event.occurredAt,
    recordedAt: event.recordedAt,
    streamId: event.streamId,
    actorKind: event.actorKind,
    actorId: event.actorId,
    actorRole: event.actorRole,
    actorLabel: event.actorLabel,
    sessionId: event.sessionId,
    serverTraceId: event.serverTraceId,
    correlationId: event.correlationId,
    causationId: event.causationId,
    organizationId: event.organizationId,
    organizationLabel: event.organizationLabel,
    targetType: event.targetType,
    targetId: event.targetId,
    targetLabel: event.targetLabel,
    reason: event.reason,
    payloadJson: event.payloadJson,
});

const verifyEvents = (eventsBytes, manifest) =>
{
    const text = eventsBytes.toString("utf8");
    const lines = text.length === 0 ? [] : text.split("\n").filter((line) => line.length > 0);
    const events = lines.map((line, index) =>
    {
        try
        {
            return JSON.parse(line);
        }
        catch
        {
            fail(`events.jsonl line ${index + 1} is not valid JSON`);
        }
    });
    if (events.length !== manifest.eventCount)
    {
        fail(`Event count mismatch: expected ${manifest.eventCount}, found ${events.length}`);
    }

    const streamState = new Map();
    for (const event of events)
    {
        const canonicalJson = JSON.stringify(canonicalEvent(event));
        const recomputedHash = sha256Hex(
            Buffer.from(`${canonicalJson}|${event.streamSequence}|${event.prevHash ?? ""}`, "utf8"),
        );
        if (recomputedHash !== event.eventHash)
        {
            fail(`Event hash mismatch for ${event.eventId}`);
        }
        const previous = streamState.get(event.streamId);
        if (previous !== undefined)
        {
            if (event.streamSequence !== previous.sequence + 1 || event.prevHash !== previous.hash)
            {
                fail(`Event chain break in stream ${event.streamId} at sequence ${event.streamSequence}`);
            }
        }
        streamState.set(event.streamId, {sequence: event.streamSequence, hash: event.eventHash});
    }
    const root = merkleRoot(events.map((event) => event.eventHash));
    if (root !== manifest.eventsMerkleRoot)
    {
        fail("Events Merkle root does not match the manifest");
    }
};

const main = () =>
{
    if (process.argv.length !== 4)
    {
        fail("Usage: node verify.mjs <bundle.zip> <trusted-public-key.pem>");
    }
    const entries = readZip(readFileSync(process.argv[2]));
    for (const name of entries.keys())
    {
        if (!EXPECTED_ENTRIES.has(name))
        {
            fail(`Unexpected bundle entry: ${name}`);
        }
    }
    for (const name of EXPECTED_ENTRIES)
    {
        if (!entries.has(name))
        {
            fail(`Required bundle entry is missing: ${name}`);
        }
    }

    const manifestBytes = entries.get("manifest.json");
    const manifest = JSON.parse(manifestBytes.toString("utf8"));
    const signature = JSON.parse(entries.get("signature.json").toString("utf8"));
    if (manifest.formatVersion !== 2 || signature.signatureAlgorithm !== "SHA256withRSA")
    {
        fail("Unsupported audit export format or signature algorithm");
    }
    if (signature.manifestJson !== manifestBytes.toString("utf8"))
    {
        fail("signature.json does not contain the exact manifest.json bytes");
    }

    const declaredNames = new Set();
    for (const digest of manifest.entryDigests)
    {
        if (declaredNames.has(digest.name))
        {
            fail(`Manifest contains duplicate entry digest: ${digest.name}`);
        }
        declaredNames.add(digest.name);
        const content = entries.get(digest.name);
        if (content === undefined || content.length !== digest.byteLength || sha256Hex(content) !== digest.sha256Hex)
        {
            fail(`Entry digest or length mismatch: ${digest.name}`);
        }
    }
    const expectedDigests = [...EXPECTED_ENTRIES].filter((name) => name !== "manifest.json" && name !== "signature.json");
    if (expectedDigests.some((name) => !declaredNames.has(name)) || declaredNames.size !== expectedDigests.length)
    {
        fail("Manifest entry digest set is incomplete or contains unexpected names");
    }

    const trustedPem = readFileSync(process.argv[3], "utf8");
    const trustedKey = createPublicKey(trustedPem);
    const embeddedKey = createPublicKey(signature.publicKeyPem);
    const trustedDer = trustedKey.export({type: "spki", format: "der"});
    const embeddedDer = embeddedKey.export({type: "spki", format: "der"});
    if (!trustedDer.equals(embeddedDer))
    {
        fail("Embedded convenience key does not match the independently trusted key");
    }
    const validSignature = verifySignature(
        "RSA-SHA256",
        manifestBytes,
        trustedKey,
        Buffer.from(signature.signatureBase64, "base64"),
    );
    if (!validSignature)
    {
        fail(`Manifest signature verification failed for signing key ${manifest.signingKeyId}`);
    }
    verifyEvents(entries.get("events.jsonl"), manifest);
    process.stdout.write(`Verified audit export ${manifest.exportId} with ${manifest.eventCount} events\n`);
};

try
{
    main();
}
catch (error)
{
    process.stderr.write(`Verification failed: ${error instanceof Error ? error.message : String(error)}\n`);
    process.exitCode = 1;
}
