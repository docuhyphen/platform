import {AxiosError} from "axios";
import apiClient from "./apiClient.ts";
import {ResponseError} from "../app/models/models.tsx";

export type OrganizationTrustStatus = "PENDING" | "ACTIVE" | "REJECTED" | "WITHDRAWN" | "EXPIRED" | "ENDED";
export type OrganizationTrustDecision = "ACCEPT" | "REJECT";

export interface OrganizationDirectoryEntry
{
    id: string;
    name: string;
}

export interface OrganizationTrustRelationship
{
    id: string;
    currentOrganizationId: string;
    partnerOrganizationId: string;
    partnerOrganizationName: string;
    partnerOrganizationActive: boolean;
    partnerOrganizationVerified: boolean;
    requestedByCurrentOrganization: boolean;
    status: OrganizationTrustStatus;
    requestMessage?: string;
    requestedAt: number;
    requestExpiresAt: number;
    activatedAt?: number;
    endedAt?: number;
    reviewDueAt?: number;
    version: number;
    effectivelySuspended: boolean;
    suspendedByCurrentOrganization: boolean;
    suspendedByPartner: boolean;
    currentOrganizationSuspensionId?: string;
}

export interface OrganizationTrustPolicy
{
    id: string;
    relationshipId: string;
    policyOwnerOrganizationId: string;
    policyOwnerOrganizationName: string;
    ownedByCurrentOrganization: boolean;
    allowExchangesToPartner: boolean;
    allowExchangesFromPartner: boolean;
    allowPartnerMemberResolution: boolean;
    allowPartnerGroupDiscovery: boolean;
    shareMemberDisplayName: boolean;
    expiresAt?: number;
    reviewDueAt?: number;
    revision: number;
    updatedAt: number;
}

export interface OrganizationTrustPolicies
{
    relationshipId: string;
    policies: OrganizationTrustPolicy[];
}

export interface PublishedExchangeGroup
{
    id: string;
    name: string;
    description?: string;
    organizationId: string;
    iconUrl?: string;
}

export interface ExternalIdentityResolution
{
    id: string;
    organizationId: string;
    organizationName: string;
    displayName?: string | null;
    email: string;
    verifiedAt: number;
    expiresAt: number;
}

export interface OrganizationTrustPolicyUpdate
{
    expectedRevision: number;
    allowExchangesToPartner: boolean;
    allowExchangesFromPartner: boolean;
    allowPartnerMemberResolution: boolean;
    allowPartnerGroupDiscovery: boolean;
    shareMemberDisplayName: boolean;
    expiresAt?: number;
    reviewDueAt?: number;
}

const responseMessage = (error: unknown): string =>
{
    if (error instanceof AxiosError)
    {
        const response = error.response?.data as ResponseError | undefined;
        return response?.errorMessage || error.message;
    }
    return error instanceof Error ? error.message : "Trusted Organization request failed";
};

const execute = async <T>(request: () => Promise<{data: T}>): Promise<T> =>
{
    try
    {
        return (await request()).data;
    }
    catch (error: unknown)
    {
        throw new Error(responseMessage(error));
    }
};

export const searchOrganizationsForTrust = (query: string): Promise<OrganizationDirectoryEntry[]> =>
    execute(() => apiClient.post("/organization-directory-searches", {query}));

export const fetchTrustRelationships = (): Promise<OrganizationTrustRelationship[]> =>
    execute(() => apiClient.get("/organization-trust-relationships"));

export const fetchTrustPolicies = (relationshipId: string): Promise<OrganizationTrustPolicies> =>
    execute(() => apiClient.get(`/organization-trust-relationships/${relationshipId}/policies`));

export const fetchPublishedExchangeGroups = (organizationId: string): Promise<PublishedExchangeGroup[]> =>
    execute(() => apiClient.get(`/organizations/${organizationId}/published-exchange-groups`));

export const resolveExternalIdentity = (
    organizationId: string,
    email: string,
): Promise<ExternalIdentityResolution> => execute(() => apiClient.post(
    `/organizations/${organizationId}/external-identity-resolutions`,
    {email},
));

export const requestOrganizationTrust = (
    targetOrganizationId: string,
    requestMessage?: string,
): Promise<OrganizationTrustRelationship> => execute(() => apiClient.post(
    "/organization-trust-relationships",
    {targetOrganizationId, requestMessage},
));

export const decideOrganizationTrust = (
    relationship: OrganizationTrustRelationship,
    decision: OrganizationTrustDecision,
    reason?: string,
): Promise<OrganizationTrustRelationship> => execute(() => apiClient.post(
    `/organization-trust-relationships/${relationship.id}/decisions`,
    {decision, reason, expectedVersion: relationship.version},
));

export const withdrawOrganizationTrust = (
    relationship: OrganizationTrustRelationship,
    reason?: string,
): Promise<OrganizationTrustRelationship> => execute(() => apiClient.post(
    `/organization-trust-relationships/${relationship.id}/withdrawals`,
    {reason, expectedVersion: relationship.version},
));

export const suspendOrganizationTrust = (
    relationshipId: string,
    reason: string,
): Promise<OrganizationTrustRelationship> => execute(() => apiClient.post(
    `/organization-trust-relationships/${relationshipId}/suspensions`,
    {reason},
));

export const resumeOrganizationTrust = (
    relationshipId: string,
    suspensionId: string,
): Promise<OrganizationTrustRelationship> => execute(() => apiClient.delete(
    `/organization-trust-relationships/${relationshipId}/suspensions/${suspensionId}`,
));

export const terminateOrganizationTrust = (
    relationship: OrganizationTrustRelationship,
    reason?: string,
): Promise<OrganizationTrustRelationship> => execute(() => apiClient.post(
    `/organization-trust-relationships/${relationship.id}/terminations`,
    {reason, expectedVersion: relationship.version},
));

export const updateOrganizationTrustPolicy = (
    relationshipId: string,
    policyId: string,
    update: OrganizationTrustPolicyUpdate,
): Promise<OrganizationTrustPolicies> => execute(() => apiClient.patch(
    `/organization-trust-relationships/${relationshipId}/policies/${policyId}`,
    update,
));
