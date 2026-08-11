import {describe, expect, it} from "vitest";
import {PlanCode, PlanFeature, SubscriptionDenialReason} from "../app/models/models.tsx";
import {getSubscriptionDenialMessage, parseSubscriptionDenial} from "./subscriptionDenialUtils.ts";

describe("parseSubscriptionDenial", () =>
{
    const denial = {
        errorMessage: "Your Free plan does not include Blueprints.",
        reasonCode: SubscriptionDenialReason.FEATURE_NOT_INCLUDED,
        planCode: PlanCode.FREE,
        featureCode: PlanFeature.BLUEPRINT_USE,
        upgradePlanCode: PlanCode.PERSONAL,
    };

    it("parses a response body already unwrapped by a service", () =>
    {
        expect(parseSubscriptionDenial(denial)).toMatchObject(denial);
    });

    it("parses an Axios-style response envelope", () =>
    {
        expect(getSubscriptionDenialMessage({response: {data: denial}})).toBe(denial.errorMessage);
    });

    it("rejects an ordinary authorization error", () =>
    {
        expect(parseSubscriptionDenial({errorMessage: "Forbidden", reasonCode: "FORBIDDEN"})).toBeNull();
    });
});
