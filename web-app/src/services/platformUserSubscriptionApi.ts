import apiClient from "./apiClient.ts";
import {PlatformApiError} from "./platformOrganizationApi.ts";
import {
    PlatformUserSubscriptionPolicy,
    PlatformUserSubscriptionPolicyList,
    PlatformUserSubscriptionPolicyRequest,
} from "./types/platformUserSubscriptions.ts";

const execute = async <T>(request: () => Promise<{data: T}>): Promise<T> =>
{
    try
    {
        return (await request()).data;
    }
    catch (error: unknown)
    {
        const value = error as {message?: string; response?: {status?: number; data?: {errorMessage?: string}}};
        throw {
            status: value.response?.status,
            errorMessage: value.response?.data?.errorMessage || value.message || "Request failed",
        } satisfies PlatformApiError;
    }
};

export const fetchPlatformUserSubscriptions = (
    query: string,
    limit: number,
    offset: number,
): Promise<PlatformUserSubscriptionPolicyList> => execute(() => apiClient.get(
    "/platform/users/subscription-policies",
    {params: {query: query || undefined, limit, offset}},
));

export const updatePlatformUserSubscription = (
    appUserId: string,
    request: PlatformUserSubscriptionPolicyRequest,
): Promise<PlatformUserSubscriptionPolicy> => execute(() => apiClient.patch(
    `/platform/users/${appUserId}/subscription-policy`,
    request,
));

