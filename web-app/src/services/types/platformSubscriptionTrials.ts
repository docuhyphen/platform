export interface PlatformSubscriptionTrialResponse
{
    ownerType: "USER" | "ORGANIZATION";
    ownerId: string;
    planCode: "PERSONAL" | "BUSINESS";
    subscriptionStatus: "TRIALING";
    currentPeriodStart: string | null;
    currentPeriodEnd: string | null;
    seatCapacity: number | null;
    grantId: string;
    grantSource: "AUTOMATIC" | "PLATFORM_ADMIN";
    reason: string;
}

export interface PlatformUserTrialStartRequest
{
    planCode: "PERSONAL";
    durationDays: number;
    reason: string;
}

export interface PlatformOrganizationTrialStartRequest
{
    planCode: "BUSINESS";
    durationDays: number;
    seatCapacity: number;
    reason: string;
}

export interface PlatformSubscriptionTrialExtensionRequest
{
    currentPeriodEnd: string;
    reason: string;
}

export interface PlatformSubscriptionTrialEndRequest
{
    reason: string;
}

export interface PlatformUserTrialConversionRequest
{
    billingFrequency: "MONTHLY" | "ANNUAL";
    currentPeriodEnd: string;
    reason: string;
}

export interface PlatformOrganizationTrialConversionRequest
{
    billingFrequency: "MONTHLY" | "ANNUAL";
    currentPeriodEnd: string;
    seatCapacity: number;
    reason: string;
}

export interface PlatformSubscriptionTrialTransitionResponse
{
    ownerType: "USER" | "ORGANIZATION";
    ownerId: string;
    planCode: "FREE" | "PERSONAL" | "BUSINESS";
    subscriptionStatus: "TRIALING" | "ACTIVE";
    billingFrequency: "MONTHLY" | "ANNUAL" | null;
    currentPeriodStart: string | null;
    currentPeriodEnd: string | null;
    seatCapacity: number | null;
    reason: string;
}
