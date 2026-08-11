import {describe, expect, it} from "vitest";
import {
    EffectiveSubscriptionDto,
    PlanCode,
    PlanFeature,
    SubscriptionEnforcementMode,
    SubscriptionOwnerType,
    SubscriptionStatus,
} from "../../app/models/models.tsx";
import {resolvePlanFeatureAvailability} from "./usePlanFeature.ts";

const subscription = (overrides: Partial<EffectiveSubscriptionDto> = {}): EffectiveSubscriptionDto => ({
    planCode: PlanCode.FREE,
    ownerType: SubscriptionOwnerType.USER,
    ownerId: "user-1",
    status: SubscriptionStatus.ACTIVE,
    features: [PlanFeature.EXCHANGE_CREATE],
    limits: {seatsArePurchased: false},
    usage: {},
    allowsMutations: true,
    enforcementMode: SubscriptionEnforcementMode.ENFORCE,
    ...overrides,
});

describe("resolvePlanFeatureAvailability", () =>
{
    it("allows an included feature while enforcement is enabled", () =>
    {
        expect(resolvePlanFeatureAvailability(PlanFeature.EXCHANGE_CREATE, subscription()).isAvailable).toBe(true);
    });

    it("blocks an excluded feature while enforcement is enabled", () =>
    {
        const result = resolvePlanFeatureAvailability(PlanFeature.BLUEPRINT_USE, subscription());
        expect(result.isIncluded).toBe(false);
        expect(result.isDiscoverable).toBe(false);
        expect(result.isAvailable).toBe(false);
    });

    it("does not turn report-only evaluation into frontend enforcement", () =>
    {
        const result = resolvePlanFeatureAvailability(
            PlanFeature.BLUEPRINT_USE,
            subscription({enforcementMode: SubscriptionEnforcementMode.REPORT_ONLY}),
        );
        expect(result.isAvailable).toBe(true);
        expect(result.isDiscoverable).toBe(true);
    });

    it("fails closed when subscription state is unavailable", () =>
    {
        const result = resolvePlanFeatureAvailability(PlanFeature.EXCHANGE_CREATE, null);
        expect(result.isKnown).toBe(false);
        expect(result.isDiscoverable).toBe(false);
        expect(result.isAvailable).toBe(false);
    });

    it("keeps included feature navigation discoverable when mutations are suspended", () =>
    {
        const result = resolvePlanFeatureAvailability(
            PlanFeature.EXCHANGE_CREATE,
            subscription({allowsMutations: false}),
        );
        expect(result.isDiscoverable).toBe(true);
        expect(result.isAvailable).toBe(false);
    });
});
