export interface PlatformUserSubscriptionPolicy
{
    appUserId: string;
    email: string;
    displayName: string | null;
    planCode: "FREE" | "PERSONAL";
    subscriptionStatus: "TRIALING" | "ACTIVE" | "PAST_DUE" | "SUSPENDED" | "CANCELED";
    billingFrequency: "MONTHLY" | "ANNUAL" | null;
    currentPeriodStart: string | null;
    currentPeriodEnd: string | null;
    gracePeriodEnd: string | null;
    changeReason: string | null;
    createdDate: string;
    updatedDate: string;
}

export interface PlatformUserSubscriptionPolicyList
{
    total: number;
    limit: number;
    offset: number;
    items: PlatformUserSubscriptionPolicy[];
}

export interface PlatformUserSubscriptionPolicyRequest
{
    planCode: "FREE" | "PERSONAL";
    subscriptionStatus: PlatformUserSubscriptionPolicy["subscriptionStatus"];
    billingFrequency: PlatformUserSubscriptionPolicy["billingFrequency"];
    currentPeriodStart: string | null;
    currentPeriodEnd: string | null;
    gracePeriodEnd: string | null;
    changeReason: string;
}

