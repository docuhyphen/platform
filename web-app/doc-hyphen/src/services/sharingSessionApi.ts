import apiClient from './apiClient';
import {
    DownloadDocumentsZipRequest,
    ResponseError,
    SharingSessionBasicDto,
    SharingSessionInitiationRequest,
    SharingSessionRequestDocumentRequest,
    UpdateSharingSessionRequest
} from "../app/models/models.tsx";

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

export const updateSharingSession = async (sessionId: string, request: UpdateSharingSessionRequest, token: string | null) =>
{
    try
    {
        const response = await apiClient.put(`/sharing-sessions/${sessionId}`, request, {
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

export const addSharingSessionDocument = async (sessionId: string, request: SharingSessionRequestDocumentRequest, token: string | null) =>
{
    try
    {
        const response = await apiClient.post(`/sharing-sessions/${sessionId}/documents`, request, {
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

export const deleteSharingSession = async (sessionId: string, token: string | null) =>
{
    try
    {
        const response = await apiClient.delete(`/sharing-sessions/${sessionId}`, {
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

export const updateSharingSessionDocument = async (sessionId: string, documentId: string, request: SharingSessionRequestDocumentRequest, token: string | null) =>
{
    try
    {
        const response = await apiClient.put(`/sharing-sessions/${sessionId}/documents/${documentId}`, request, {
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

export const deleteSharingSessionDocument = async (sessionId: string, documentId: string, token: string | null) =>
{
    try
    {
        const response = await apiClient.delete(`/sharing-sessions/${sessionId}/documents/${documentId}`, {
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

export const fetchSharingSessionDocumentAuditLogs = async (sessionId: string, documentId: string, token: string | null) =>
{
    try
    {
        const response = await apiClient.get(`/sharing-sessions/${sessionId}/documents/${documentId}/audit`, {
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

export const uploadSharingSessionDocument = async (sessionId: string, documentId?: string, formData?: FormData, token?: string | null, onUploadProgress?: (progressEvent: any) => void) =>
{
    try
    {
        const response = await apiClient.post(`/sharing-sessions/${sessionId}/documents/${documentId}/file`, formData, {
            headers: {
                Authorization: `Bearer ${token}`,
                'Content-Type': 'multipart/form-data'
            },
            onUploadProgress
        });
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const downloadSharingSessionDocument = async (sessionId: string, documentId?: string, token?: string | null) =>
{
    try
    {
        const response = await apiClient.get(`/sharing-sessions/${sessionId}/documents/${documentId}/file`, {
            headers: {
                Authorization: `Bearer ${token}`
            },
            responseType: 'blob'
        });
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const downloadSharingSessionDocumentZip = async (sessionId: string, request: DownloadDocumentsZipRequest, token?: string | null) =>
{
    try
    {
        const response = await apiClient.post(
            `/sharing-sessions/${sessionId}/documents/zip-file`,
            request,
            {
            headers: {
                Authorization: `Bearer ${token}`,
            },
            responseType: 'blob'
        });
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const downloadPreviewPDFSharingSessionDocument = async (sessionId: string, documentId?: string, token?: string | null) =>
{
    try
    {
        const response = await apiClient.get(`/sharing-sessions/${sessionId}/documents/${documentId}/preview`, {
            headers: {
                Authorization: `Bearer ${token}`
            },
            responseType: 'blob'
        });
        return response.data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};