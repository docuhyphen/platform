import apiClient, {addBearerToHeaderToken} from './apiClient';
import {
    DocumentDetailedDto,
    DownloadDocumentsZipRequest,
    ExchangeClearanceStatusDto,
    NoAuthExchangeBasicDto,
    ResponseError,
    ExchangeBasicDto,
    ExchangeInitiationRequest,
    ExchangeRequestDocumentRequest,
    UpdateNoAuthExchangeRequest,
    UpdateExchangeRequest,
    WorkflowInstanceSummaryDto,
} from "../app/models/models.tsx";
import {AxiosRequestConfig} from "axios";
import {
    GrantExchangeShareRequest,
    ExchangeAccessEntryDto,
    UpdateExchangeShareRoleRequest,
} from './types/dtos';

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
    Authorization: token ? addBearerToHeaderToken(token) : '',
    ...extraHeaders,
});

export const initiateExchange = (request: ExchangeInitiationRequest) =>
    executeRequest(() =>
        apiClient.post(`/exchanges/`, request)
    );

export const fetchSignedInUserAppUserExchanges = (): Promise<ExchangeBasicDto[] | ResponseError> =>
    executeRequest(() =>
        apiClient.get(`/exchanges/`)
    );

export const fetchSignedInUserAppUserExchange = (exchangeId: string | null): Promise<ExchangeBasicDto | ResponseError> =>
    executeRequest(() =>
        apiClient.get(`/exchanges/${exchangeId}`)
    );

export const checkSignedInAppUserHasExchanges = async (token: string | null): Promise<boolean> =>
{
    try
    {
        const response = await apiClient.head("/exchanges", {
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
        // Log and rethrow,  callers decide how to surface the failure. Service-
        // layer alert() popups blocked the UI and double-fired (caller also alerted).
        console.error("Error checking exchanges:", error);
        if (error.response)
        {
            console.error("Error response data:", error.response.data);
        }
        throw error.response?.data || error;
    }
};

export const fetchNoAuthExchange = (exchangeId: string | null): Promise<NoAuthExchangeBasicDto | ResponseError> =>
    executeRequest(() =>
        apiClient.get(`no-auth/exchanges/${exchangeId}`)
    );

export const updateNoAuthExchange = (exchangeId: string, request: UpdateNoAuthExchangeRequest) =>
    executeRequest(() =>
        apiClient.put(`no-auth/exchanges/${exchangeId}`, request)
    );

export const updateExchange = (exchangeId: string, request: UpdateExchangeRequest) =>
    executeRequest(() =>
        apiClient.put(`/exchanges/${exchangeId}`, request)
    );

export const addExchangeDocument = (exchangeId: string, request: ExchangeRequestDocumentRequest) =>
    executeRequest(() =>
        apiClient.post(`/exchanges/${exchangeId}/documents`, request)
    );

export const deleteExchange = (exchangeId: string) =>
    executeRequest(() =>
        apiClient.delete(`/exchanges/${exchangeId}`)
    );

export const rescindExchange = (exchangeId: string) =>
    executeRequest(() =>
        apiClient.post(`/exchanges/${exchangeId}/rescind`)
    );

export const updateExchangeDocument = (exchangeId: string, documentId: string, request: ExchangeRequestDocumentRequest) =>
    executeRequest(() =>
        apiClient.put(`/exchanges/${exchangeId}/documents/${documentId}`, request)
    );

export const deleteExchangeDocument = (exchangeId: string, documentId: string) =>
    executeRequest(() =>
        apiClient.delete(`/exchanges/${exchangeId}/documents/${documentId}`)
    );

export const fetchExchangeDocumentAuditLogs = (exchangeId: string, documentId: string) =>
    executeRequest(() =>
        apiClient.get(`/exchanges/${exchangeId}/documents/${documentId}/audit`)
    );

export const fetchExchangeAuditEvents = (exchangeId: string) =>
    executeRequest(() =>
        apiClient.get(`/exchanges/${exchangeId}/audit-events`)
    );

export const uploadExchangeDocument = (
    exchangeId: string,
    documentId?: string,
    formData?: FormData,
    token?: string | null,
    onUploadProgress?: (progressEvent: any) => void
) =>
    executeRequest(() =>
        apiClient.post(`/exchanges/${exchangeId}/documents/${documentId}/file`, formData, {
            headers: getAuthHeaders(token || null, {'Content-Type': 'multipart/form-data'}),
            onUploadProgress
        })
    );

export const uploadNoAuthExchangeDocument = (
    exchangeId: string,
    documentId?: string,
    formData?: FormData,
    onUploadProgress?: (progressEvent: any) => void): Promise<DocumentDetailedDto> =>
{
    return executeRequest(() =>
        apiClient.post(`no-auth/exchanges/${exchangeId}/documents/${documentId}/file`, formData, {
            headers: {'Content-Type': 'multipart/form-data'},
            onUploadProgress
        })
    );
}

export const downloadNoAuthExchangeDocument = (exchangeId: string, documentId?: string) =>
    executeRequest(() => apiClient.get(`no-auth/exchanges/${exchangeId}/documents/${documentId}/file`, blobRequest));

export const downloadExchangeDocument = (exchangeId: string, documentId?: string) =>
    executeRequest(() => apiClient.get(`/exchanges/${exchangeId}/documents/${documentId}/file`, blobRequest));

export const downloadExchangeDocumentZip = (exchangeId: string, request: DownloadDocumentsZipRequest) =>
    executeRequest(() => apiClient.post(`/exchanges/${exchangeId}/documents/zip-file`, request, blobRequest));

export const downloadPreviewPDFExchangeDocument = (exchangeId: string, documentId?: string) =>
    executeRequest(() => apiClient.get(`/exchanges/${exchangeId}/documents/${documentId}/preview`, blobRequest));

export const requestNoAuthExchangeOtp = (exchangeId: string) =>
    executeRequest(() => apiClient.post(`no-auth/exchanges/${exchangeId}/otp`));

export const verifyNoAuthExchangeAccessCode = (exchangeId: string, otp: string) =>
    executeRequest(() => apiClient.post(`no-auth/exchanges/${exchangeId}/verify-access-code`, {otp}));

export const requestExchangeRecipientOtp = (exchangeId: string) =>
    executeRequest(() => apiClient.post(`/exchanges/${exchangeId}/recipient-otp`));

export const searchExchanges = (
    query?: string,
    status?: string,
    initiatedBy?: boolean,
    page: number = 0,
    size: number = 10,
    sortBy: string = "createdDate",
    sortDirection: string = "DESC"
): Promise<{
    content: ExchangeBasicDto[],
    totalElements: number,
    totalPages: number,
    currentPage: number,
    pageSize: number
} | ResponseError> =>
    executeRequest(() =>
        apiClient.get(`/exchanges/search`, {
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

export const getDocumentComments = (exchangeId: string, documentId: string) =>
    executeRequest(() =>
        apiClient.get(`/exchanges/${exchangeId}/documents/${documentId}/comments`)
    );

export const addDocumentComment = (exchangeId: string, documentId: string, commentText: string, isInternal: boolean) =>
    executeRequest(() =>
        apiClient.post(`/exchanges/${exchangeId}/documents/${documentId}/comments`, {
            commentText,
            isInternal
        })
    );

export const getDocumentVersions = (exchangeId: string, documentId: string) =>
    executeRequest(() =>
        apiClient.get(`/exchanges/${exchangeId}/documents/${documentId}/versions`)
    );

export const uploadDocumentVersion = (
    exchangeId: string,
    documentId: string,
    formData?: FormData,
    token?: string | null,
    onUploadProgress?: (progressEvent: any) => void
) =>
    executeRequest(() =>
        apiClient.post(`/exchanges/${exchangeId}/documents/${documentId}/versions`, formData, {
            headers: getAuthHeaders(token || null, {'Content-Type': 'multipart/form-data'}),
            onUploadProgress
        })
    );

export const downloadDocumentVersion = (exchangeId: string, documentId: string, versionId: string) =>
    executeRequest(() =>
        apiClient.get(`/exchanges/${exchangeId}/documents/${documentId}/versions/${versionId}/file`, blobRequest)
    );

export const getLatestDocumentVersion = (exchangeId: string, documentId: string) =>
    executeRequest(() =>
        apiClient.get(`/exchanges/${exchangeId}/documents/${documentId}/versions/latest`)
    );

// ── Exchange Access Management ──

export const listExchangeAccess = (exchangeId: string): Promise<ExchangeAccessEntryDto[]> =>
    executeRequest(() => apiClient.get(`/exchanges/${exchangeId}/access`));

export const grantExchangeAccess = (
    exchangeId: string,
    request: GrantExchangeShareRequest,
): Promise<ExchangeAccessEntryDto[]> =>
    executeRequest(() => apiClient.post(`/exchanges/${exchangeId}/access`, request));

export const changeExchangeAccessRole = (
    exchangeId: string,
    shareId: string,
    request: UpdateExchangeShareRoleRequest,
): Promise<ExchangeAccessEntryDto[]> =>
    executeRequest(() =>
        apiClient.patch(`/exchanges/${exchangeId}/access/${shareId}`, request),
    );

export const revokeExchangeAccess = (
    exchangeId: string,
    shareId: string,
): Promise<ExchangeAccessEntryDto[]> =>
    executeRequest(() =>
        apiClient.delete(`/exchanges/${exchangeId}/access/${shareId}`),
    );

export const fetchExchangeWorkflowInstances = (
    exchangeId: string,
): Promise<WorkflowInstanceSummaryDto[]> =>
    executeRequest(() =>
        apiClient.get(`/exchanges/${exchangeId}/workflow-instances`),
    );

export const fetchExchangeWorkflowClearanceStatus = (
    exchangeId: string,
): Promise<ExchangeClearanceStatusDto> =>
    executeRequest(() =>
        apiClient.get(`/exchanges/${exchangeId}/workflow-clearance-status`),
    );
