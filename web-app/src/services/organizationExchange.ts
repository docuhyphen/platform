import apiClient, {addBearerToHeaderToken} from './apiClient';
import {OrganizationBasicDto, OrganizationExchangeLinkBasicDto, ResponseError} from "../app/models/models.tsx";

const executeRequest = async <T>(fn: () => Promise<{ data: T }>): Promise<T> =>
{
    try
    {
        const {data} = await fn();
        return data;
    }
    catch (error: unknown)
    {
        throw error.response?.data || error.message;
    }
};

const getAuthHeaders = (token: string | null) => ({
    Authorization: token ? addBearerToHeaderToken(token) : ''
});

export const fetchOrganizationsForLinking = (token?: string | null): Promise<OrganizationBasicDto[] | ResponseError> =>
    executeRequest(() =>
        apiClient.get('/organizations/links/linking', {
            headers: getAuthHeaders(token || null)
        })
    );

export const fetchOrganizationLinks = (token?: string | null): Promise<OrganizationExchangeLinkBasicDto[] | ResponseError> =>
    executeRequest(() =>
        apiClient.get('/organizations/links', {
            headers: getAuthHeaders(token || null)
        })
    );

export const fetchOrganizationLinksByOrganization = (organizationId: string, token?: string | null): Promise<OrganizationExchangeLinkBasicDto[] | ResponseError> =>
    executeRequest(() =>
        apiClient.get(`/organizations/${organizationId}/links`, {
            headers: getAuthHeaders(token || null)
        })
    );

export const createOrganizationLink = (requestingOrganizationId: string, requestedOrganizationId: string, message?: string, token?: string | null): Promise<OrganizationExchangeLinkBasicDto | ResponseError> =>
    executeRequest(() =>
        apiClient.post(`/organizations/links?requestingOrganizationId=${requestingOrganizationId}&requestedOrganizationId=${requestedOrganizationId}&message=${message}`, {}, {
            headers: getAuthHeaders(token || null)
        })
    );

export const acceptOrRejectOrganizationLink = (linkId: string, status: string, rejectionReason?: string, token?: string | null): Promise<OrganizationExchangeLinkBasicDto | ResponseError> =>
    executeRequest(() =>
        apiClient.put(`/organizations/links/${linkId}?status=${status}&rejectReason=${rejectionReason}`, {}, {
            headers: getAuthHeaders(token || null)
        })
    );

export const deleteOrganizationLink = (linkId: string, token?: string | null): Promise<void | ResponseError> =>
    executeRequest(() =>
        apiClient.delete(`/organizations/links/${linkId}`, {
            headers: getAuthHeaders(token || null)
        })
    );