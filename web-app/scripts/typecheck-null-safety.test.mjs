import {describe, expect, it} from "vitest";
import {isNullSafetyDiagnostic} from "./typecheck-null-safety.mjs";

const diagnostic = (file, message = "Argument of type 'number | null' is not assignable to parameter of type 'number'.") => ({
    file,
    code: "TS2345",
    message,
});

describe("typecheck-null-safety", () => {
    it("holds Information Request screens and the screens built on their records to null checks", () => {
        expect(isNullSafetyDiagnostic(diagnostic("src/app/information-requests/operations/clock-history/ClockHistoryPanel.tsx"))).toBe(true);
        expect(isNullSafetyDiagnostic(diagnostic("src/app/record-preservation/retention-schedule/RetentionScheduleCard.tsx"))).toBe(true);
        expect(isNullSafetyDiagnostic(diagnostic(
            "src/app/settings/workflows-tab/workflow-designer/requirement-condition-editor/requirementConditionChoices.ts",
        ))).toBe(true);
        expect(isNullSafetyDiagnostic(diagnostic(
            "src/app/settings/blueprints-tab/blueprint-information-request-tab/useBlueprintTemplateChoices.ts",
        ))).toBe(true);
    });

    it("leaves screens outside the feature to the app baseline", () => {
        expect(isNullSafetyDiagnostic(diagnostic("src/app/exchanges/components/ExchangeDocumentsList.tsx"))).toBe(false);
    });
});
