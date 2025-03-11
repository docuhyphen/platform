import apiClient from './apiClient';
import {
    DownloadDocumentsZipRequest,
    NoAuthSharingSessionBasicDto,
    ResponseError,
    SharingSessionBasicDto,
    SharingSessionInitiationRequest,
    SharingSessionRequestDocumentRequest,
    UpdateNoAuthSharingSessionRequest,
    UpdateSharingSessionRequest
} from "../app/models/models.tsx";

const executeRequest = async <T>(fn: () => Promise<{ data: T }>): Promise<T> =>
{
    try
    {
        const {data} = await fn();
        return data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

const getAuthHeaders = (token: string | null, extraHeaders: Record<string, string> = {}) => ({
    Authorization: token ? `Bearer ${token}` : '',
    ...extraHeaders,
});

export const initiateSharingSession = (request: SharingSessionInitiationRequest, token: string | null) =>
    executeRequest(() =>
        apiClient.post(`/sharing-sessions/`, request, {
            headers: getAuthHeaders(token)
        })
    );

export const fetchSignedInUserAppUserSharingSessions = (token: string | null): Promise<SharingSessionBasicDto[] | ResponseError> =>
    executeRequest(() =>
        apiClient.get(`/sharing-sessions/`, {
            headers: getAuthHeaders(token)
        })
    );

export const fetchSignedInUserAppUserSharingSession = (sessionId: string | null, token: string | null): Promise<SharingSessionBasicDto | ResponseError> =>
    executeRequest(() =>
        apiClient.get(`/sharing-sessions/${sessionId}`, {
            headers: getAuthHeaders(token)
        })
    );

export const checkSignedInAppUserHasSharingSessions = async (token: string | null): Promise<boolean> => {
    try {
        const response = await apiClient.head("/sharing-sessions", {
            headers: getAuthHeaders(token),
        });

        // If the response status is 204 (No Content), return false
        if (response.status === 204) {
            return false;
        }

        // If the status is within the 2xx range (excluding 204), return true
        if (response.status >= 200 && response.status < 300) {
            return true;
        }

        // For other non-2xx statuses, throw an error
        throw new Error(`Request failed with status: ${response.status}`);
    } catch (error: any) {
        // Log the error and handle it appropriately
        console.error("Error during API request:", error);

        // Handle network or unexpected errors
        if (error.response) {
            console.error("Error response data:", error.response.data);
        }

        alert("Failed to check for sharing sessions");
        throw error; // Rethrow error to propagate it further if needed
    }
};





export const fetchNoAuthSharingSession = (sessionId: string | null): Promise<NoAuthSharingSessionBasicDto | ResponseError> =>
    executeRequest(() =>
        apiClient.get(`no-auth/sharing-sessions/${sessionId}`)
    );

export const updateNoAuthSharingSession = (sessionId: string, request: UpdateNoAuthSharingSessionRequest) =>
    executeRequest(() =>
        apiClient.put(`no-auth/sharing-sessions/${sessionId}`, request)
    );

export const updateSharingSession = (sessionId: string, request: UpdateSharingSessionRequest, token: string | null) =>
    executeRequest(() =>
        apiClient.put(`/sharing-sessions/${sessionId}`, request, {
            headers: getAuthHeaders(token)
        })
    );

export const addSharingSessionDocument = (sessionId: string, request: SharingSessionRequestDocumentRequest, token: string | null) =>
    executeRequest(() =>
        apiClient.post(`/sharing-sessions/${sessionId}/documents`, request, {
            headers: getAuthHeaders(token)
        })
    );

export const deleteSharingSession = (sessionId: string, token: string | null) =>
    executeRequest(() =>
        apiClient.delete(`/sharing-sessions/${sessionId}`, {
            headers: getAuthHeaders(token)
        })
    );

export const updateSharingSessionDocument = (sessionId: string, documentId: string, request: SharingSessionRequestDocumentRequest, token: string | null) =>
    executeRequest(() =>
        apiClient.put(`/sharing-sessions/${sessionId}/documents/${documentId}`, request, {
            headers: getAuthHeaders(token)
        })
    );

export const deleteSharingSessionDocument = (sessionId: string, documentId: string, token: string | null) =>
    executeRequest(() =>
        apiClient.delete(`/sharing-sessions/${sessionId}/documents/${documentId}`, {
            headers: getAuthHeaders(token)
        })
    );

export const fetchSharingSessionDocumentAuditLogs = (sessionId: string, documentId: string, token: string | null) =>
    executeRequest(() =>
        apiClient.get(`/sharing-sessions/${sessionId}/documents/${documentId}/audit`, {
            headers: getAuthHeaders(token)
        })
    );

export const uploadSharingSessionDocument = (
    sessionId: string,
    documentId?: string,
    formData?: FormData,
    token?: string | null,
    onUploadProgress?: (progressEvent: any) => void
) =>
    executeRequest(() =>
        apiClient.post(`/sharing-sessions/${sessionId}/documents/${documentId}/file`, formData, {
            headers: getAuthHeaders(token || null, {'Content-Type': 'multipart/form-data'}),
            onUploadProgress
        })
    );

export const uploadNoAuthSharingSessionDocument = (
    sessionId: string,
    documentId?: string,
    formData?: FormData,
    onUploadProgress?: (progressEvent: any) => void
) => {

    alert("Uploading document")

    return executeRequest(() =>
        apiClient.post(`no-auth/sharing-sessions/${sessionId}/documents/${documentId}/file`, formData, {
            headers: {'Content-Type': 'multipart/form-data'},
            onUploadProgress
        })
    );
}

export const downloadNoAuthSharingSessionDocument = (sessionId: string, documentId?: string, token?: string | null) =>
    executeRequest(() =>
        apiClient.get(`no-auth/sharing-sessions/${sessionId}/documents/${documentId}/file`, {
            headers: getAuthHeaders(token || null),
            responseType: 'blob'
        })
    );

export const downloadSharingSessionDocument = (sessionId: string, documentId?: string, token?: string | null) =>
    executeRequest(() =>
        apiClient.get(`/sharing-sessions/${sessionId}/documents/${documentId}/file`, {
            headers: getAuthHeaders(token || null),
            responseType: 'blob'
        })
    );

export const downloadSharingSessionDocumentZip = (sessionId: string, request: DownloadDocumentsZipRequest, token?: string | null) =>
    executeRequest(() =>
        apiClient.post(
            `/sharing-sessions/${sessionId}/documents/zip-file`,
            request,
            {
                headers: getAuthHeaders(token || null),
                responseType: 'blob'
            }
        )
    );

export const downloadPreviewPDFSharingSessionDocument = (sessionId: string, documentId?: string, token?: string | null) =>
    executeRequest(() =>
        apiClient.get(`/sharing-sessions/${sessionId}/documents/${documentId}/preview`, {
            headers: getAuthHeaders(token || null),
            responseType: 'blob'
        })
    );
