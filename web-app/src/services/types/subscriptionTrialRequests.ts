export type SubscriptionTrialRequestStatus = "PENDING" | "APPROVED" | "REJECTED";

export interface SubscriptionTrialRequest
{
    id: string;
    ownerType: "USER" | "ORGANIZATION";
    ownerId: string;
    ownerName: string;
    requestedByAppUserId: string;
    requesterName: string;
    requesterEmail: string;
    planCode: "PERSONAL" | "BUSINESS";
    status: SubscriptionTrialRequestStatus;
    requestNote: string | null;
    requestedAt: string;
    reviewedByAppUserId: string | null;
    reviewedAt: string | null;
    decisionReason: string | null;
    trialGrantId: string | null;
}

export interface CurrentSubscriptionTrialRequest
{
    eligible: boolean;
    ineligibilityReason: string | null;
    request: SubscriptionTrialRequest | null;
}

export interface PlatformSubscriptionTrialRequestList
{
    total: number;
    limit: number;
    offset: number;
    items: SubscriptionTrialRequest[];
}

export interface SubscriptionTrialRequestDecision
{
    status: "APPROVED" | "REJECTED";
    durationDays?: number;
    seatCapacity?: number;
    reason: string;
}
