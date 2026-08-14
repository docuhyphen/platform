import {describe, expect, it} from "vitest";
import {
    buildSetupPasswordReturnTo,
    clearSetupPasswordReturnParams,
    shouldResumeSetupPassword,
} from "./setupPasswordNavigation.ts";

describe("setup password navigation", () =>
{
    it("builds a return location that restores linked accounts after step-up", () =>
    {
        expect(buildSetupPasswordReturnTo("?existing=value&stepUp=old"))
            .toBe("/settings?existing=value&tab=LinkedAccountsTab&setupPassword=true");
    });

    it("resumes only after a successful step-up callback", () =>
    {
        expect(shouldResumeSetupPassword("?setupPassword=true&stepUp=success")).toBe(true);
        expect(shouldResumeSetupPassword("?setupPassword=true")).toBe(false);
        expect(shouldResumeSetupPassword("?stepUp=success")).toBe(false);
    });

    it("clears one-time callback parameters while preserving the selected tab", () =>
    {
        expect(clearSetupPasswordReturnParams(
            "?tab=LinkedAccountsTab&setupPassword=true&stepUp=success",
        )).toBe("/settings?tab=LinkedAccountsTab");
    });
});
