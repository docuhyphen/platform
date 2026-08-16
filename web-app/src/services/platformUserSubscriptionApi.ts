import apiClient from "./apiClient.ts";
import {PlatformApiError} from "./platformOrganizationApi.ts";
import {
    PlatformUserSubscriptionPolicy,
    PlatformUserSubscriptionPolicyList,
    PlatformUserSubscriptionPolicyRequest,
} from "./types/platformUserSubscriptions.ts";
import {
    PlatformSubscriptionTrialExtensionRequest,
    PlatformSubscriptionTrialEndRequest,
    PlatformSubscriptionTrialResponse,
    PlatformSubscriptionTrialTransitionResponse,
    PlatformUserTrialConversionRequest,
    PlatformUserTrialStartRequest,
} from "./types/platformSubscriptionTrials.ts";

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

export const startPlatformUserSubscriptionTrial = (
    appUserId: string,
    request: PlatformUserTrialStartRequest,
): Promise<PlatformSubscriptionTrialResponse> => execute(() => apiClient.post(
    `/platform/users/${appUserId}/subscription-trials`,
    request,
));

export const extendPlatformUserSubscriptionTrial = (
    appUserId: string,
    request: PlatformSubscriptionTrialExtensionRequest,
): Promise<PlatformSubscriptionTrialResponse> => execute(() => apiClient.patch(
    `/platform/users/${appUserId}/subscription-trials/current`,
    request,
));

export const endPlatformUserSubscriptionTrial = (
    appUserId: string,
    request: PlatformSubscriptionTrialEndRequest,
): Promise<PlatformSubscriptionTrialTransitionResponse> => execute(() => apiClient.delete(
    `/platform/users/${appUserId}/subscription-trials/current`,
    {data: request},
));

export const convertPlatformUserSubscriptionTrial = (
    appUserId: string,
    request: PlatformUserTrialConversionRequest,
): Promise<PlatformSubscriptionTrialTransitionResponse> => execute(() => apiClient.post(
    `/platform/users/${appUserId}/subscription-trials/current/conversions`,
    request,
));
