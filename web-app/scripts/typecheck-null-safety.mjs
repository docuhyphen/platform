import {spawnSync} from "node:child_process";
import {dirname, resolve} from "node:path";
import {fileURLToPath, pathToFileURL} from "node:url";
import {isInformationRequestDiagnostic, parseTypeScriptDiagnostics} from "./typecheck-app-baseline.mjs";

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const appDirectory = resolve(scriptDirectory, "..");
const tscPath = resolve(appDirectory, "node_modules", "typescript", "bin", "tsc");

const nullSafetyPathPattern = /(^|\/)(record-preservation|requirement-condition-editor|blueprint-information-request-tab)(\/|$)/i;

export const isNullSafetyDiagnostic = diagnostic =>
    isInformationRequestDiagnostic(diagnostic) || nullSafetyPathPattern.test(diagnostic.file);

const runTypeScript = () => {
    const result = spawnSync(process.execPath, [
        tscPath,
        "-p",
        "tsconfig.app.json",
        "--noEmit",
        "--pretty",
        "false",
        "--strictNullChecks",
    ], {
        cwd: appDirectory,
        encoding: "utf8",
        maxBuffer: 64 * 1024 * 1024,
    });
    return {
        status: result.status ?? 1,
        output: `${result.stdout ?? ""}${result.stderr ?? ""}`,
        error: result.error,
    };
};

const isMain = import.meta.url === pathToFileURL(process.argv[1]).href;

if (isMain) {
    const typeScriptResult = runTypeScript();
    if (typeScriptResult.error) {
        console.error(typeScriptResult.error.message);
        process.exit(1);
    }
    const diagnostics = parseTypeScriptDiagnostics(typeScriptResult.output);
    if (typeScriptResult.status !== 0 && diagnostics.length === 0) {
        console.error(typeScriptResult.output);
        process.exit(typeScriptResult.status);
    }
    const featureDiagnostics = diagnostics.filter(isNullSafetyDiagnostic);
    console.log(`Null-checked app-project TypeScript diagnostics: ${diagnostics.length} total, ${featureDiagnostics.length} Information Request.`);
    if (featureDiagnostics.length > 0) {
        console.error("Information Request diagnostics under strictNullChecks:");
        featureDiagnostics.forEach(diagnostic => console.error(`${diagnostic.file} ${diagnostic.code} ${diagnostic.message}`));
        process.exit(1);
    }
}
