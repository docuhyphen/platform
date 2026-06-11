import apiClient, {addBearerToHeaderToken} from './apiClient';
import {
    AppUserDetailedDto,
    Organization,
    OrganizationDetailedDto,
    OrganizationSettingsDto,
    PersonDetailedDto
} from "../app/models/models.tsx";

export const fetchOrganization = async (organizationId: string, token?: string) =>
{
    try
    {
        const response = await apiClient.get(`/organizations/${organizationId}`, {
            headers: token ? {Authorization: addBearerToHeaderToken(token)} : undefined
        });
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const updateOrganization = async (organizationId: string, data: {
    name: string,
    registrationNumber?: string
}, token?: string) =>
{
    try
    {
        const response = await apiClient.put(`/organizations/${organizationId}`, data, {
            headers: token ? {Authorization: addBearerToHeaderToken(token)} : undefined
        });
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const updateOrganizationSettings = async (organizationId: string, settings: OrganizationSettingsDto, token?: string) =>
{
    try
    {
        const response = await apiClient.put(`/organizations/${organizationId}/settings`, settings, {
            headers: token ? {Authorization: addBearerToHeaderToken(token)} : undefined
        });
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export interface OrganizationGroupBasicDto
{
    id: string;
    name: string;
    description?: string;
    organizationId: string;
    members: any;
}

export const fetchOrganizationGroups = async (organizationId: string, token?: string) =>
{
    try
    {
        const response = await apiClient.get(`/organizations/${organizationId}/groups`, {
            headers: token ? {Authorization: addBearerToHeaderToken(token)} : undefined
        });
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const addOrganizationGroup = async (
    organizationId: string,
    groupData: {
        name: string;
        members: Array<{
            appUserId: string;
            allowExchangeAccept: boolean;
            allowExchangeReject: boolean;
            allowExchangeEdit: boolean;
            allowExchangeDelete: boolean;
            allowExchangeEnd: boolean;
            allowDocumentAddition: boolean;
            allowDocumentDeletion: boolean;
            allowDocumentDownload: boolean;
            allowDocumentUpdate: boolean;
            allowDocumentUpload: boolean;
        }>;
    },
    token?: string
) =>
{
    try
    {
        const response = await apiClient.post(`/organizations/${organizationId}/groups`, groupData, {
            headers: token ? {Authorization: addBearerToHeaderToken(token)} : undefined
        });
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const updateOrganizationGroup = async (
    organizationId: string,
    groupId: string,
    groupData: {
        name: string;
        isActive: boolean;
        members: Array<{
            appUserId: string;
            allowExchangeAccept: boolean;
            allowExchangeReject: boolean;
            allowExchangeEdit: boolean;
            allowExchangeDelete: boolean;
            allowExchangeEnd: boolean;
            allowDocumentAddition: boolean;
            allowDocumentDeletion: boolean;
            allowDocumentDownload: boolean;
            allowDocumentUpdate: boolean;
            allowDocumentUpload: boolean;
        }>;
    },
    token?: string
) =>
{
    try
    {
        const response = await apiClient.put(`/organizations/${organizationId}/groups/${groupId}`, groupData, {
            headers: token ? {Authorization: addBearerToHeaderToken(token)} : undefined
        });
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const deleteOrganizationGroup = async (
    organizationId: string,
    groupId: string,
    token?: string
) =>
{
    try
    {
        const response = await apiClient.delete(`/organizations/${organizationId}/groups/${groupId}`, {
            headers: token ? {Authorization: addBearerToHeaderToken(token)} : undefined
        });
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const fetchOrganizationUsers = async (organizationId: string, token?: string): Promise<AppUserDetailedDto[]> =>
{
    try
    {
        const response = await apiClient.get(`/organizations/${organizationId}/app-users`, {
            headers: token ? {Authorization: addBearerToHeaderToken(token)} : undefined
        });
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const addOrganizationUser = async (
    organizationId: string,
    data: { role: string, email: string, person?: PersonDetailedDto },
    token?: string
) =>
{
    try
    {
        const response = await apiClient.post(`/organizations/${organizationId}/app-users`, data, {
            headers: token ? {Authorization: addBearerToHeaderToken(token)} : undefined
        });
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const updateOrganizationUser = async (
    organizationId: string,
    appUserId: string,
    data: { isActive?: boolean, email?: string, role?: string, person?: PersonDetailedDto },
    token?: string
) =>
{
    try
    {
        const response = await apiClient.put(`/organizations/${organizationId}/app-users/${appUserId}`, data, {
            headers: token ? {Authorization: addBearerToHeaderToken(token)} : undefined
        });
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const deactivateOrganizationUser = async (organizationId: string, appUserId: string, token?: string) =>
{
    try
    {
        const response = await apiClient.delete(`/organizations/${organizationId}/app-users/${appUserId}`, {
            headers: token ? {Authorization: addBearerToHeaderToken(token)} : undefined
        });
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const checkAppUserIsDeletable = async (organizationId: string, appUserId: string, token?: string) =>
{
    try
    {
        await apiClient.get(`/organizations/${organizationId}/app-users/${appUserId}?check=DELETABLE`, {
            headers: token ? {Authorization: addBearerToHeaderToken(token)} : undefined
        });
        return true;
    }
    catch (error: any)
    {
        if (error.response?.status === 409)
        {
            return false;
        }
        throw error.response?.data || error.message;
    }
};

export const deleteOrganizationAppUser = async (organizationId: string, appUserId: string, token?: string) =>
{
    try
    {
        const response = await apiClient.delete(`/organizations/${organizationId}/app-users/${appUserId}`, {
            headers: token ? {Authorization: addBearerToHeaderToken(token)} : undefined
        });
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const fetchPairedOrganizations = async (token?: string): Promise<Organization[]> =>
{
    try
    {
        const response = await apiClient.get('/organizations/linked', {
            headers: token ? {Authorization: addBearerToHeaderToken(token)} : undefined
        });
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const fetchPairedOrganizationUsers = async (orgId?: string, token?: string): Promise<any[]> =>
{
    try
    {
        const response = await apiClient.get(`/organizations/linked/${orgId}/app-users`, {
            headers: token ? {Authorization: addBearerToHeaderToken(token)} : undefined
        });
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const fetchPairedOrganizationGroups  = async (orgId?: string, token?: string): Promise<Organization[]> =>
{
    try
    {
        const response = await apiClient.get(`/organizations/linked/${orgId}/groups`, {
            headers: token ? {Authorization: addBearerToHeaderToken(token)} : undefined
        });
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const fetchMyOrganizationUsers = async (token?: string): Promise<AppUserDetailedDto[]> =>
{
    try
    {
        const currentOrg = await fetchCurrentUserOrganization(token);
        return fetchOrganizationUsers(currentOrg.id!, token);
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const fetchMyOrganizationGroups = async (token?: string) =>
{
    try
    {
        const currentOrg = await fetchCurrentUserOrganization(token);
        return fetchOrganizationGroups(currentOrg.id!, token);
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

const fetchCurrentUserOrganization = async (token?: string): Promise<OrganizationDetailedDto> =>
{
    try
    {
        const appUser = await apiClient.get('/app-user', {
            headers: token ? {Authorization: addBearerToHeaderToken(token)} : undefined
        });

        const response = await apiClient.get(`/app-user/${appUser.data.id}/person/${appUser.data.person.id}/organization`, {
            headers: token ? {Authorization: addBearerToHeaderToken(token)} : undefined
        });

        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

