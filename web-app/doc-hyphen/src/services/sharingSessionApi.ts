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
import {AxiosRequestConfig} from "axios";

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

const blobRequest: AxiosRequestConfig = {
    responseType: 'blob'
}

const getAuthHeaders = (token: string | null, extraHeaders: Record<string, string> = {}) => ({
    Authorization: token ? `Bearer ${token}` : '',
    ...extraHeaders,
});

export const initiateSharingSession = (request: SharingSessionInitiationRequest) =>
    executeRequest(() =>
        apiClient.post(`/sharing-sessions/`, request)
    );

export const fetchSignedInUserAppUserSharingSessions = (): Promise<SharingSessionBasicDto[] | ResponseError> =>
    executeRequest(() =>
        apiClient.get(`/sharing-sessions/`)
    );

export const fetchSignedInUserAppUserSharingSession = (sessionId: string | null): Promise<SharingSessionBasicDto | ResponseError> =>
    executeRequest(() =>
        apiClient.get(`/sharing-sessions/${sessionId}`)
    );

export const checkSignedInAppUserHasSharingSessions = async (token: string | null): Promise<boolean> =>
{
    try
    {
        const response = await apiClient.head("/sharing-sessions", {
            headers: getAuthHeaders(token),
        });

        // If the response status is 204 (No Content), return false
        if (response.status === 204)
        {
            return false;
        }

        // If the status is within the 2xx range (excluding 204), return true
        if (response.status >= 200 && response.status < 300)
        {
            return true;
        }

        // For other non-2xx statuses, throw an error
        throw new Error(`Request failed with status: ${response.status}`);
    }
    catch (error: any)
    {
        // Log the error and handle it appropriately
        console.error("Error during API request:", error);

        // Handle network or unexpected errors
        if (error.response)
        {
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

export const updateSharingSession = (sessionId: string, request: UpdateSharingSessionRequest) =>
    executeRequest(() =>
        apiClient.put(`/sharing-sessions/${sessionId}`, request)
    );

export const addSharingSessionDocument = (sessionId: string, request: SharingSessionRequestDocumentRequest) =>
    executeRequest(() =>
        apiClient.post(`/sharing-sessions/${sessionId}/documents`, request)
    );

export const deleteSharingSession = (sessionId: string) =>
    executeRequest(() =>
        apiClient.delete(`/sharing-sessions/${sessionId}`)
    );

export const updateSharingSessionDocument = (sessionId: string, documentId: string, request: SharingSessionRequestDocumentRequest) =>
    executeRequest(() =>
        apiClient.put(`/sharing-sessions/${sessionId}/documents/${documentId}`, request)
    );

export const deleteSharingSessionDocument = (sessionId: string, documentId: string) =>
    executeRequest(() =>
        apiClient.delete(`/sharing-sessions/${sessionId}/documents/${documentId}`)
    );

export const fetchSharingSessionDocumentAuditLogs = (sessionId: string, documentId: string) =>
    executeRequest(() =>
        apiClient.get(`/sharing-sessions/${sessionId}/documents/${documentId}/audit`)
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
    onUploadProgress?: (progressEvent: any) => void) =>
{
    return executeRequest(() =>
        apiClient.post(`no-auth/sharing-sessions/${sessionId}/documents/${documentId}/file`, formData, {
            headers: {'Content-Type': 'multipart/form-data'},
            onUploadProgress
        })
    );
}

export const downloadNoAuthSharingSessionDocument = (sessionId: string, documentId?: string) =>
    executeRequest(() => apiClient.get(`no-auth/sharing-sessions/${sessionId}/documents/${documentId}/file`, blobRequest));

export const downloadSharingSessionDocument = (sessionId: string, documentId?: string) =>
    executeRequest(() => apiClient.get(`/sharing-sessions/${sessionId}/documents/${documentId}/file`, blobRequest));

export const downloadSharingSessionDocumentZip = (sessionId: string, request: DownloadDocumentsZipRequest) =>
    executeRequest(() => apiClient.post(`/sharing-sessions/${sessionId}/documents/zip-file`, request, blobRequest));

export const downloadPreviewPDFSharingSessionDocument = (sessionId: string, documentId?: string) =>
    executeRequest(() => apiClient.get(`/sharing-sessions/${sessionId}/documents/${documentId}/preview`, blobRequest));

export const searchSharingSessions = (
    query?: string,
    status?: string,
    initiatedBy?: boolean,
    page: number = 0,
    size: number = 10,
    sortBy: string = "createdDate",
    sortDirection: string = "DESC"
): Promise<{
    content: SharingSessionBasicDto[],
    totalElements: number,
    totalPages: number,
    currentPage: number,
    pageSize: number
} | ResponseError> =>
    executeRequest(() =>
        apiClient.get(`/sharing-sessions/search`, {
            params: {
                query,
                status,
                initiatedBy,
                page,
                size,
                sortBy,
                sortDirection
            }
        })
    );

export const getDocumentComments = (sessionId: string, documentId: string) =>
    executeRequest(() =>
        apiClient.get(`/sharing-sessions/${sessionId}/documents/${documentId}/comments`)
    );

export const addDocumentComment = (sessionId: string, documentId: string, commentText: string, commentedBy: string) =>
    executeRequest(() =>
        apiClient.post(`/sharing-sessions/${sessionId}/documents/${documentId}/comments`, {
            commentText,
            commentedBy
        })
    );

export const getDocumentVersions = (sessionId: string, documentId: string) =>
    executeRequest(() =>
        apiClient.get(`/sharing-sessions/${sessionId}/documents/${documentId}/versions`)
    );

export const uploadDocumentVersion = (
    sessionId: string,
    documentId: string,
    formData?: FormData,
    token?: string | null,
    onUploadProgress?: (progressEvent: any) => void
) =>
    executeRequest(() =>
        apiClient.post(`/sharing-sessions/${sessionId}/documents/${documentId}/versions`, formData, {
            headers: getAuthHeaders(token || null, {'Content-Type': 'multipart/form-data'}),
            onUploadProgress
        })
    );

export const downloadDocumentVersion = (sessionId: string, documentId: string, versionId: string) =>
    executeRequest(() =>
        apiClient.get(`/sharing-sessions/${sessionId}/documents/${documentId}/versions/${versionId}/file`, blobRequest)
    );

export const getLatestDocumentVersion = (sessionId: string, documentId: string) =>
    executeRequest(() =>
        apiClient.get(`/sharing-sessions/${sessionId}/documents/${documentId}/versions/latest`)
    );