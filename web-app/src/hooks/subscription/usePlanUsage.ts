import {SubscriptionLimitsDto, SubscriptionUsageDto} from "../../app/models/models.tsx";
import {useCurrentSubscription} from "./useCurrentSubscription.ts";

export interface CurrentPlanUsage
{
    limits: SubscriptionLimitsDto | null;
    usage: SubscriptionUsageDto | null;
}

export const usePlanUsage = (): CurrentPlanUsage =>
{
    const subscription = useCurrentSubscription();
    return {
        limits: subscription?.limits ?? null,
        usage: subscription?.usage ?? null,
    };
};
