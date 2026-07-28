import apiClient from "./apiClient.ts";
import {
    PlatformOrganizationFeatureEntitlements,
    PlatformOrganizationFeatureEntitlementsRequest,
    PlatformOrganizationList,
    PlatformOrganizationListQuery,
    PlatformOrganizationSubscriptionPolicy,
    PlatformOrganizationSubscriptionPolicyRequest,
} from "./types/platformOrganizations.ts";

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
