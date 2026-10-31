import {describe, expect, it} from "vitest";
import {PlanCode, PlanFeature, SubscriptionDenialReason} from "../models/models.tsx";
import {EXCHANGE_INITIATION_FALLBACK_REFUSAL, exchangeInitiationRefusalMessage} from "./exchangeInitiationRefusal.ts";

describe("exchangeInitiationRefusalMessage", () => {
    it("shows why the server refused to start the Exchange", () => {
        expect(exchangeInitiationRefusalMessage({
            errorMessage: "This Blueprint's Information Request Template belongs to a different workspace than this Exchange.",
            reasonCode: "INFORMATION_REQUEST_TEMPLATE_VERSION_OWNER_MISMATCH",
        })).toBe("This Blueprint's Information Request Template belongs to a different workspace than this Exchange.");
        expect(exchangeInitiationRefusalMessage({errorMessage: "Add at least one document"})).toBe("Add at least one document");
    });

    it("shows a plan refusal in its own words", () => {
        expect(exchangeInitiationRefusalMessage({
            errorMessage: "Information Requests are not included in the Free plan",
            reasonCode: SubscriptionDenialReason.FEATURE_NOT_INCLUDED,
            planCode: PlanCode.FREE,
            featureCode: PlanFeature.INFORMATION_REQUESTS,
            upgradePlanCode: PlanCode.PERSONAL,
        })).toBe("Information Requests are not included in the Free plan");
    });

    it("falls back when the refusal says nothing", () => {
        expect(exchangeInitiationRefusalMessage({})).toBe(EXCHANGE_INITIATION_FALLBACK_REFUSAL);
        expect(exchangeInitiationRefusalMessage({errorMessage: " "})).toBe(EXCHANGE_INITIATION_FALLBACK_REFUSAL);
    });
});
