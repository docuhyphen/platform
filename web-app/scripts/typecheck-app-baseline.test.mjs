import {describe, expect, it} from "vitest";
import {
    evaluateDiagnostics,
    isInformationRequestDiagnostic,
    parseTypeScriptDiagnostics,
} from "./typecheck-app-baseline.mjs";

const diagnostic = (file, code = "TS2339", message = "Property does not exist") => ({
    file,
    code,
    message,
});

describe("typecheck-app-baseline", () =>
{
    it("parses app-project TypeScript diagnostics", () =>
    {
        const output = "src/app/settings/information-request-templates-tab/InformationRequestTemplatesTab.test.tsx(98,39): error TS2339: Property 'TEXT' does not exist on type 'typeof FieldValueType'.";

        expect(parseTypeScriptDiagnostics(output)).toEqual([{
            file: "src/app/settings/information-request-templates-tab/InformationRequestTemplatesTab.test.tsx",
            code: "TS2339",
            message: "Property 'TEXT' does not exist on type 'typeof FieldValueType'.",
        }]);
    });

    it("classifies Information Request feature diagnostics by path and symbol", () =>
    {
        expect(isInformationRequestDiagnostic(diagnostic(
            "src/app/settings/information-request-templates-tab/InformationRequestTemplatesTab.test.tsx",
        ))).toBe(true);
        expect(isInformationRequestDiagnostic(diagnostic(
            "src/services/templateService.ts",
            "TS2345",
            "InformationRequestTemplateDto is missing groups",
        ))).toBe(true);
    });

    it("fails when an Information Request diagnostic is present", () =>
    {
        const result = evaluateDiagnostics([
            diagnostic("src/app/information-requests/structured-response-workspace/state.ts"),
        ], []);

        expect(result.ok).toBe(false);
        expect(result.featureDiagnostics).toHaveLength(1);
    });

    it("fails when a non-feature diagnostic is not in the reviewed baseline", () =>
    {
        const result = evaluateDiagnostics([
            diagnostic("src/app/exchanges/ExchangeList.tsx", "TS2322", "Type mismatch"),
        ], []);

        expect(result.ok).toBe(false);
        expect(result.addedUnrelatedDiagnostics).toHaveLength(1);
    });

    it("passes when only reviewed unrelated diagnostics remain", () =>
    {
        const baseline = [
            diagnostic("src/app/exchanges/ExchangeList.tsx", "TS2322", "Type mismatch"),
        ];

        const result = evaluateDiagnostics(baseline, baseline);

        expect(result.ok).toBe(true);
        expect(result.featureDiagnostics).toHaveLength(0);
        expect(result.addedUnrelatedDiagnostics).toHaveLength(0);
    });
});
