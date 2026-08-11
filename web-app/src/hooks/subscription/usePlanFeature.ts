import {
    PlanCode,
    PlanFeature,
    SubscriptionEnforcementMode,
} from "../../app/models/models.tsx";
import {useCurrentSubscription} from "./useCurrentSubscription.ts";

export interface PlanFeatureAvailability
{
    isKnown: boolean;
    isIncluded: boolean;
    isEnforced: boolean;
    isDiscoverable: boolean;
    isAvailable: boolean;
    upgradePlanCode: PlanCode | null;
}

export const resolvePlanFeatureAvailability = (
    feature: PlanFeature,
    subscription: ReturnType<typeof useCurrentSubscription>,
): PlanFeatureAvailability =>
{
    if (!subscription)
    {
        return {
            isKnown: false,
            isIncluded: false,
            isEnforced: false,
            isDiscoverable: false,
            isAvailable: false,
            upgradePlanCode: null,
        };
    }

    const isIncluded = subscription.features.includes(feature);
    const isEnforced = subscription.enforcementMode === SubscriptionEnforcementMode.ENFORCE;

    return {
        isKnown: true,
        isIncluded,
        isEnforced,
        isDiscoverable: !isEnforced || isIncluded,
        isAvailable: !isEnforced || (isIncluded && subscription.allowsMutations),
        upgradePlanCode: subscription.upgradePlanCode ?? null,
    };
};

export const usePlanFeature = (feature: PlanFeature): PlanFeatureAvailability =>
{
    return resolvePlanFeatureAvailability(feature, useCurrentSubscription());
};
