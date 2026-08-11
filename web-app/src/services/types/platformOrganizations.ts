export interface PlatformOrganizationFeatureEntitlement
{
    featureCode: string;
    enabled: boolean;
}

export interface PlatformOrganizationSummary
{
    organizationId: string;
    name: string;
    registrationNumber: string;
    active: boolean;
    verificationComplete: boolean;
    createdDate: string;
    tierCode: string;
    maxUsers: number | null;
    subscriptionStatus: string;
    billingFrequency: string | null;
    currentPeriodStart: string | null;
    currentPeriodEnd: string | null;
    gracePeriodEnd: string | null;
    activeUsers: number;
    featureEntitlements: PlatformOrganizationFeatureEntitlement[];
}

export interface PlatformOrganizationList
{
    total: number;
    limit: number;
    offset: number;
    items: PlatformOrganizationSummary[];
}

export interface PlatformOrganizationListQuery
{
    query?: string;
    status?: "ALL" | "ACTIVE" | "INACTIVE";
    tierCode?: string;
    sort?: "name" | "createdDate";
    direction?: "asc" | "desc";
    limit: number;
    offset: number;
}

export interface PlatformOrganizationStatusUpdateRequest
{
    active: boolean;
    verificationComplete: boolean;
    changeReason?: string;
}

export interface PlatformOrganizationStatus
{
    organizationId: string;
    active: boolean;
    verificationComplete: boolean;
}

export interface PlatformOrganizationSubscriptionPolicyRequest
{
    tierCode: string;
    maxUsers: number | null;
    subscriptionStatus: string;
    billingFrequency: string | null;
    currentPeriodStart: string | null;
    currentPeriodEnd: string | null;
    gracePeriodEnd: string | null;
    changeReason: string;
}

export interface PlatformOrganizationSubscriptionPolicy
{
    organizationId: string;
    tierCode: string;
    maxUsers: number | null;
    currentActiveUsers: number;
    subscriptionStatus: string;
    billingFrequency: string | null;
    currentPeriodStart: string | null;
    currentPeriodEnd: string | null;
    gracePeriodEnd: string | null;
    changeReason: string | null;
    persisted: boolean;
    createdDate: string | null;
    updatedDate: string | null;
}

export interface PlatformOrganizationFeatureEntitlementsRequest
{
    entitlements: PlatformOrganizationFeatureEntitlement[];
    changeReason?: string;
}

export interface PlatformOrganizationFeatureEntitlements
{
    organizationId: string;
    entitlements: PlatformOrganizationFeatureEntitlement[];
}
