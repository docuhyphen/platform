import apiClient from './apiClient';
import {ResponseError, SharingSessionBasicDto, SharingSessionInitiationRequest} from "../app/models/models.tsx";

export const initiateSharingSession = async (request: SharingSessionInitiationRequest, token: string | null) =>
{
    try
    {
        const response = await apiClient.post(`/sharing-sessions/`, request, {
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

export const fetchSignedInUserAppUserSharingSessions = async (token: string | null): Promise<SharingSessionBasicDto[] | ResponseError> =>
{
    try
    {
        const response = await apiClient.get(`/sharing-sessions/`, {
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

export const fetchSignedInUserAppUserSharingSession = async (sessionId: string | null, token: string | null): Promise<SharingSessionBasicDto | ResponseError> =>
{
    try
    {
        const response = await apiClient.get(`/sharing-sessions/${sessionId}`, {
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