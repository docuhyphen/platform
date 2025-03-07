import apiClient from './apiClient';
import {CompanyRegistrationRequest, PersonRegistrationRequest} from "../app/models/models.tsx";

export const fetchAppUser = async (token: string | null) =>
{
    try
    {
        const response = await apiClient.get(`/app-user`, {
            headers: {
                Authorization: `Bearer ${token}`
            }
        });
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const fetchAppUserPersonCompany = async (appUserId?: string, personId?: string, token?: string) =>
{
    try
    {
        const response = await apiClient.get(`/app-user/${appUserId}/person/${personId}/company`, {
            headers: {
                Authorization: `Bearer ${token}`
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
                Authorization: `Bearer ${token}`
            }
        });
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const registerOrganization = async (request: CompanyRegistrationRequest, token: string | null) =>
{
    try
    {
        const response = await apiClient.post(`/entity-registration/company`, request, {
            headers: {
                Authorization: `Bearer ${token}`
            }
        });
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};