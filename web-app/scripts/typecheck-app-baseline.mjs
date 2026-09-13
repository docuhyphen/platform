import {spawnSync} from "node:child_process";
import {existsSync, readFileSync, writeFileSync} from "node:fs";
import {dirname, resolve} from "node:path";
import {fileURLToPath, pathToFileURL} from "node:url";

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const appDirectory = resolve(scriptDirectory, "..");
const baselinePath = resolve(appDirectory, "typecheck-app-baseline.json");
const tscPath = resolve(appDirectory, "node_modules", "typescript", "bin", "tsc");

const diagnosticPattern = /^(?<file>.+?\.(?:ts|tsx))\((?<line>\d+),(?<column>\d+)\): error TS(?<code>\d+): (?<message>.+)$/;
const informationRequestPathPattern = /(^|\/)(information-requests|information-request-templates-tab)(\/|$)/i;
const informationRequestSymbolPattern = /InformationRequest|informationRequest|information-request/i;

export const parseTypeScriptDiagnostics = output =>
    output.split(/\r?\n/)
        .map(line => line.trim())
        .map(line => diagnosticPattern.exec(line))
        .filter(match => match?.groups)
        .map(match => ({
            file: match.groups.file.replaceAll("\\", "/"),
            code: `TS${match.groups.code}`,
            message: match.groups.message.trim(),
        }));

export const isInformationRequestDiagnostic = diagnostic =>
    informationRequestPathPattern.test(diagnostic.file) ||
    informationRequestSymbolPattern.test(diagnostic.file) ||
    informationRequestSymbolPattern.test(diagnostic.message);

const diagnosticKey = diagnostic => `${diagnostic.file}|${diagnostic.code}|${diagnostic.message}`;

const countByKey = diagnostics =>
    diagnostics.reduce((counts, diagnostic) =>
    {
        const key = diagnosticKey(diagnostic);
        counts.set(key, (counts.get(key) ?? 0) + 1);
        return counts;
    }, new Map());

const expandAddedDiagnostics = (currentDiagnostics, baselineDiagnostics) =>
{
    const baselineCounts = countByKey(baselineDiagnostics);
    const seenCounts = new Map();
    return currentDiagnostics.filter(diagnostic =>
    {
        const key = diagnosticKey(diagnostic);
        const seen = (seenCounts.get(key) ?? 0) + 1;
        seenCounts.set(key, seen);
        return seen > (baselineCounts.get(key) ?? 0);
    });
};

const expandRemovedDiagnostics = (currentDiagnostics, baselineDiagnostics) =>
{
    const currentCounts = countByKey(currentDiagnostics);
    const seenCounts = new Map();
    return baselineDiagnostics.filter(diagnostic =>
    {
        const key = diagnosticKey(diagnostic);
        const seen = (seenCounts.get(key) ?? 0) + 1;
        seenCounts.set(key, seen);
        return seen > (currentCounts.get(key) ?? 0);
    });
};

export const evaluateDiagnostics = (currentDiagnostics, baselineDiagnostics) =>
{
    const featureDiagnostics = currentDiagnostics.filter(isInformationRequestDiagnostic);
    const unrelatedDiagnostics = currentDiagnostics.filter(diagnostic => !isInformationRequestDiagnostic(diagnostic));
    return {
        ok: featureDiagnostics.length === 0 &&
            expandAddedDiagnostics(unrelatedDiagnostics, baselineDiagnostics).length === 0,
        featureDiagnostics,
        addedUnrelatedDiagnostics: expandAddedDiagnostics(unrelatedDiagnostics, baselineDiagnostics),
        removedUnrelatedDiagnostics: expandRemovedDiagnostics(unrelatedDiagnostics, baselineDiagnostics),
        unrelatedDiagnostics,
    };
};

const runTypeScript = () =>
{
    const result = spawnSync(process.execPath, [
        tscPath,
        "-p",
        "tsconfig.app.json",
        "--noEmit",
        "--pretty",
        "false",
    ], {
        cwd: appDirectory,
        encoding: "utf8",
    });
    return {
        status: result.status ?? 1,
        output: `${result.stdout ?? ""}${result.stderr ?? ""}`,
        error: result.error,
    };
};

const readBaseline = () =>
{
    if (!existsSync(baselinePath)) return null;
    const baseline = JSON.parse(readFileSync(baselinePath, "utf8"));
    return Array.isArray(baseline.diagnostics) ? baseline.diagnostics : [];
};

const printDiagnostics = (label, diagnostics) =>
{
    if (diagnostics.length === 0) return;
    console.error(`${label}:`);
    diagnostics.forEach(diagnostic =>
    {
        console.error(`${diagnostic.file} ${diagnostic.code} ${diagnostic.message}`);
    });
};

const writeBaseline = diagnostics =>
{
    const baseline = {
        command: "node scripts/typecheck-app-baseline.mjs",
        generatedAt: new Date().toISOString(),
        diagnosticCount: diagnostics.length,
        diagnostics,
    };
    writeFileSync(baselinePath, `${JSON.stringify(baseline, null, 2)}\n`);
};

const isMain = import.meta.url === pathToFileURL(process.argv[1]).href;

if (isMain)
{
    const writeMode = process.argv.includes("--write-baseline");
    const typeScriptResult = runTypeScript();
    if (typeScriptResult.error)
    {
        console.error(typeScriptResult.error.message);
        process.exit(1);
    }
    const diagnostics = parseTypeScriptDiagnostics(typeScriptResult.output);
    if (typeScriptResult.status !== 0 && diagnostics.length === 0)
    {
        console.error(typeScriptResult.output);
        process.exit(typeScriptResult.status);
    }
    const featureDiagnostics = diagnostics.filter(isInformationRequestDiagnostic);
    if (writeMode)
    {
        if (featureDiagnostics.length > 0)
        {
            printDiagnostics("Information Request diagnostics must be fixed before writing the baseline", featureDiagnostics);
            process.exit(1);
        }
        const unrelatedDiagnostics = diagnostics.filter(diagnostic => !isInformationRequestDiagnostic(diagnostic));
        writeBaseline(unrelatedDiagnostics);
        console.log(`Recorded ${unrelatedDiagnostics.length} unrelated app-project TypeScript diagnostics.`);
        process.exit(0);
    }
    const baselineDiagnostics = readBaseline();
    if (!baselineDiagnostics)
    {
        console.error("Missing typecheck-app-baseline.json. Run node scripts/typecheck-app-baseline.mjs --write-baseline after reviewing unrelated diagnostics.");
        process.exit(1);
    }
    const evaluation = evaluateDiagnostics(diagnostics, baselineDiagnostics);
    console.log(`App-project TypeScript diagnostics: ${diagnostics.length} total, ${evaluation.unrelatedDiagnostics.length} unrelated, ${evaluation.featureDiagnostics.length} Information Request.`);
    console.log(`Compared with ${baselineDiagnostics.length} reviewed unrelated baseline diagnostics.`);
    if (!evaluation.ok)
    {
        printDiagnostics("Information Request diagnostics", evaluation.featureDiagnostics);
        printDiagnostics("New unrelated diagnostics", evaluation.addedUnrelatedDiagnostics);
        process.exit(1);
    }
    if (evaluation.removedUnrelatedDiagnostics.length > 0)
    {
        console.log(`${evaluation.removedUnrelatedDiagnostics.length} reviewed unrelated baseline diagnostics are no longer present.`);
    }
}
