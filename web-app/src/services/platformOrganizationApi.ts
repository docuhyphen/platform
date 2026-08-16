import apiClient from "./apiClient.ts";
import {
    PlatformOrganizationFeatureEntitlements,
    PlatformOrganizationFeatureEntitlementsRequest,
    PlatformOrganizationList,
    PlatformOrganizationListQuery,
    PlatformOrganizationStatus,
    PlatformOrganizationStatusUpdateRequest,
    PlatformOrganizationSubscriptionPolicy,
    PlatformOrganizationSubscriptionPolicyRequest,
} from "./types/platformOrganizations.ts";
import {
    PlatformOrganizationTrialStartRequest,
    PlatformOrganizationTrialConversionRequest,
    PlatformSubscriptionTrialExtensionRequest,
    PlatformSubscriptionTrialEndRequest,
    PlatformSubscriptionTrialResponse,
    PlatformSubscriptionTrialTransitionResponse,
} from "./types/platformSubscriptionTrials.ts";

export interface PlatformApiError
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
            response?: {
                status?: number;
                data?: {errorMessage?: string; message?: string};
            };
        };
        throw {
            status: value.response?.status,
            errorMessage: value.response?.data?.errorMessage
                || value.response?.data?.message
                || value.message
                || "Request failed",
        } satisfies PlatformApiError;
    }
};

export const fetchPlatformOrganizations = (
    query: PlatformOrganizationListQuery,
): Promise<PlatformOrganizationList> =>
    executeRequest(() => apiClient.get("/platform/organizations", {params: query}));

export const updatePlatformOrganizationStatus = (
    organizationId: string,
    request: PlatformOrganizationStatusUpdateRequest,
): Promise<PlatformOrganizationStatus> =>
    executeRequest(() => apiClient.patch(
        `/platform/organizations/${organizationId}/status`,
        request,
    ));

export const updatePlatformOrganizationSubscriptionPolicy = (
    organizationId: string,
    request: PlatformOrganizationSubscriptionPolicyRequest,
): Promise<PlatformOrganizationSubscriptionPolicy> =>
    executeRequest(() => apiClient.put(
        `/platform/organizations/${organizationId}/subscription-policy`,
        request,
    ));

export const updatePlatformOrganizationFeatureEntitlements = (
    organizationId: string,
    request: PlatformOrganizationFeatureEntitlementsRequest,
): Promise<PlatformOrganizationFeatureEntitlements> =>
    executeRequest(() => apiClient.put(
        `/platform/organizations/${organizationId}/feature-entitlements`,
        request,
    ));

export const startPlatformOrganizationSubscriptionTrial = (
    organizationId: string,
    request: PlatformOrganizationTrialStartRequest,
): Promise<PlatformSubscriptionTrialResponse> =>
    executeRequest(() => apiClient.post(
        `/platform/organizations/${organizationId}/subscription-trials`,
        request,
    ));

export const extendPlatformOrganizationSubscriptionTrial = (
    organizationId: string,
    request: PlatformSubscriptionTrialExtensionRequest,
): Promise<PlatformSubscriptionTrialResponse> =>
    executeRequest(() => apiClient.patch(
        `/platform/organizations/${organizationId}/subscription-trials/current`,
        request,
    ));

export const endPlatformOrganizationSubscriptionTrial = (
    organizationId: string,
    request: PlatformSubscriptionTrialEndRequest,
): Promise<PlatformSubscriptionTrialTransitionResponse> =>
    executeRequest(() => apiClient.delete(
        `/platform/organizations/${organizationId}/subscription-trials/current`,
        {data: request},
    ));

export const convertPlatformOrganizationSubscriptionTrial = (
    organizationId: string,
    request: PlatformOrganizationTrialConversionRequest,
): Promise<PlatformSubscriptionTrialTransitionResponse> =>
    executeRequest(() => apiClient.post(
        `/platform/organizations/${organizationId}/subscription-trials/current/conversions`,
        request,
    ));
