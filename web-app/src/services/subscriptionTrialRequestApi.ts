import apiClient from "./apiClient.ts";
import {
    CurrentSubscriptionTrialRequest,
    PlatformSubscriptionTrialRequestList,
    SubscriptionTrialRequest,
    SubscriptionTrialRequestDecision,
    SubscriptionTrialRequestStatus,
} from "./types/subscriptionTrialRequests.ts";

export interface SubscriptionTrialRequestApiError
{
    status?: number;
    errorMessage: string;
}

const executeRequest = async <T>(request: () => Promise<{data: T}>): Promise<T> =>
{
    try
    {
        const {data} = await request();
        return data;
    }
    catch (error: unknown)
    {
        const value = error as {
            message?: string;
            response?: {status?: number; data?: {errorMessage?: string; message?: string}};
        };
        throw {
            status: value.response?.status,
            errorMessage: value.response?.data?.errorMessage
                || value.response?.data?.message
                || value.message
                || "Request failed",
        } satisfies SubscriptionTrialRequestApiError;
    }
};

export const fetchCurrentSubscriptionTrialRequest = (): Promise<CurrentSubscriptionTrialRequest> =>
    executeRequest(() => apiClient.get("/subscription-trial-requests/current"));

export const createSubscriptionTrialRequest = (): Promise<SubscriptionTrialRequest> =>
    executeRequest(() => apiClient.post("/subscription-trial-requests", {}));

export const fetchPlatformSubscriptionTrialRequests = (
    status: SubscriptionTrialRequestStatus | "",
    limit: number,
    offset: number,
): Promise<PlatformSubscriptionTrialRequestList> => executeRequest(() => apiClient.get(
    "/platform/subscription-trial-requests",
    {params: {status: status || undefined, limit, offset}},
));

export const decidePlatformSubscriptionTrialRequest = (
    requestId: string,
    decision: SubscriptionTrialRequestDecision,
): Promise<SubscriptionTrialRequest> => executeRequest(() => apiClient.patch(
    `/platform/subscription-trial-requests/${requestId}/status`,
    decision,
));
