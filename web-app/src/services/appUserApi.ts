import apiClient, {addBearerToHeaderToken} from './apiClient';
import {AppUserSettingsDto, OrganizationRegistrationRequest, PersonRegistrationRequest} from "../app/models/models.tsx";

export const fetchAppUser = async (token: string | null) =>
{
    try
    {
        const response = await apiClient.get(`/app-user`, {
            headers: {
                Authorization: addBearerToHeaderToken(token)
            }
        });
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const fetchAppUserPersonOrganization = async (appUserId?: string, personId?: string, token?: string) =>
{
    try
    {
        const response = await apiClient.get(`/app-user/${appUserId}/person/${personId}/organization`, {
            headers: {
                Authorization: addBearerToHeaderToken(token)
            }
        });
        return response.data;
    }
    catch (error: any)
    {
        throw error;
    }
};

export const registerIndividual = async (request: PersonRegistrationRequest, token: string | null) =>
{
    try
    {
        const response = await apiClient.post(`/entity-registration/person`, request, {
            headers: {
                Authorization: addBearerToHeaderToken(token)
            }
        });
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const registerOrganization = async (request: OrganizationRegistrationRequest, token: string | null) =>
{
    try
    {
        const response = await apiClient.post(`/entity-registration/organization`, request, {
            headers: {
                Authorization: addBearerToHeaderToken(token)
            }
        });
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const updateAppUserSettings = async (request: AppUserSettingsDto, token: string | null) =>
{
    try
    {
        const response = await apiClient.put(`/app-user/settings`, request, {
            headers: {
                Authorization: addBearerToHeaderToken(token)
            }
        });
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
}

export const initiateAppUserEmailUpdate = async (email: string, token: string | null) =>
{
    try
    {
        const response = await apiClient.put(
            `/app-user/email/update-initiation`,
            {email},
            {
                headers: {
                    Authorization: addBearerToHeaderToken(token)
                }
            }
        );
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const completeAppUserEmailUpdate = async (email: string, verificationCode: string, token: string | null) =>
{
    try
    {
        const response = await apiClient.post(
            `/app-user/email/update-completion`,
            {email, verificationCode},
            {
                headers: {
                    Authorization: addBearerToHeaderToken(token)
                }
            }
        );
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};
