import apiClient, {addBearerToHeaderToken} from './apiClient';
import {requestFailureOf} from './requestFailure';
import {
    DocumentDetailedDto,
    DownloadDocumentsZipRequest,
    ExchangeClearanceStatusDto,
    NoAuthExchangeBasicDto,
    ResponseError,
    ExchangeBasicDto,
    ExchangeDetailedDto,
    ExchangeStatus,
    ExchangeInitiationRequest,
    ExchangeRecipientInvitationDto,
    ExchangeRecipientSelection,
    ExchangeRequestDocumentRequest,
    UpdateNoAuthExchangeRequest,
    UpdateExchangeRequest,
    WorkflowInstanceSummaryDto,
} from "../app/models/models.tsx";
import axios, {AxiosProgressEvent, AxiosRequestConfig} from "axios";
import {
    GrantExchangeShareRequest,
    ExchangeAccessEntryDto,
    InviteTrustedParticipantRequest,
    UpdateExchangeShareRoleRequest,
} from './types/dtos';

const executeRequest = async <T>(fn: () => Promise<{ data: T }>): Promise<T> =>
{
    try
    {
        const {data} = await fn();
        return data;
    }
    catch (error: unknown)
    {
        throw requestFailureOf(error);
    }
};

const blobRequest: AxiosRequestConfig = {
    responseType: 'blob'
}

const getAuthHeaders = (token: string | null, extraHeaders: Record<string, string> = {}) => ({
    Authorization: token ? addBearerToHeaderToken(token) : '',
    ...extraHeaders,
});

const noAuthAccessTokenKey = (exchangeId: string) => `no-auth-exchange-access:${exchangeId}`;

export const storeNoAuthExchangeAccessToken = (exchangeId: string, token: string): void =>
{
    window.sessionStorage.setItem(noAuthAccessTokenKey(exchangeId), token);
};

const getNoAuthAccessHeaders = (exchangeId: string): Record<string, string> => ({
    'x-no-auth-access-token': window.sessionStorage.getItem(noAuthAccessTokenKey(exchangeId)) ?? '',
});

export const initiateExchange = (request: ExchangeInitiationRequest): Promise<ExchangeBasicDto> =>
    executeRequest(() =>
        apiClient.post<ExchangeBasicDto>(`/exchanges/`, request)
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

        if (response.status === 204)
        {
            return false;
        }

        if (response.status >= 200 && response.status < 300)
        {
            return true;
        }

        throw new Error(`Request failed with status: ${response.status}`);
    }
    catch (error: unknown)
    {
        console.error("Error checking exchanges:", error);
        const response = axios.isAxiosError<unknown>(error) ? error.response : undefined;
        if (response)
        {
            console.error("Error response data:", response.data);
        }
        throw response?.data || error;
    }
};

export const fetchNoAuthExchange = (exchangeId: string | null): Promise<NoAuthExchangeBasicDto | ResponseError> =>
    executeRequest(() =>
        apiClient.get(`no-auth/exchanges/${exchangeId}`, {
            headers: exchangeId ? getNoAuthAccessHeaders(exchangeId) : {},
        })
    );

export const updateNoAuthExchange = (exchangeId: string, request: UpdateNoAuthExchangeRequest) =>
    executeRequest(() =>
        apiClient.put(`no-auth/exchanges/${exchangeId}`, request, {
            headers: getNoAuthAccessHeaders(exchangeId),
        })
    );

export const updateExchange = (exchangeId: string, request: UpdateExchangeRequest): Promise<ExchangeDetailedDto | void> =>
{
    if (request.status === ExchangeStatus.ACCEPTED_STARTED || request.status === ExchangeStatus.REJECTED)
    {
        return executeRequest(() =>
            apiClient.post(`/exchanges/${exchangeId}/acceptance-decisions`, {
                decision: request.status === ExchangeStatus.ACCEPTED_STARTED ? "ACCEPT" : "REJECT",
                reason: request.rejectionReason,
            })
        );
    }

    return executeRequest(() =>
        apiClient.put(`/exchanges/${exchangeId}`, request)
    );
};

export const fetchPendingExchangeRecipientInvitations = (): Promise<ExchangeRecipientInvitationDto[]> =>
    executeRequest(() => apiClient.get("/exchange-recipient-invitations"));

export const decideExchangeRecipientInvitation = (
    recipientId: string,
    decision: "ACCEPT" | "REJECT",
): Promise<void> =>
    executeRequest(() =>
        apiClient.post(`/exchange-recipient-invitations/${recipientId}/decisions`, {decision}),
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

export const fetchExchangeAuditEvents = (exchangeId: string) =>
    executeRequest(() =>
        apiClient.get(`/exchanges/${exchangeId}/audit-events`)
    );

export const uploadExchangeDocument = (
    exchangeId: string,
    documentId?: string,
    formData?: FormData,
    token?: string | null,
    onUploadProgress?: (progressEvent: AxiosProgressEvent) => void
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
    onUploadProgress?: (progressEvent: AxiosProgressEvent) => void): Promise<DocumentDetailedDto> =>
{
    return executeRequest(() =>
        apiClient.post(`no-auth/exchanges/${exchangeId}/documents/${documentId}/file`, formData, {
            headers: {
                ...getNoAuthAccessHeaders(exchangeId),
                'Content-Type': 'multipart/form-data',
            },
            onUploadProgress
        })
    );
}

export const downloadNoAuthExchangeDocument = (exchangeId: string, documentId?: string) =>
    executeRequest(() => apiClient.get(`no-auth/exchanges/${exchangeId}/documents/${documentId}/file`, {
        ...blobRequest,
        headers: getNoAuthAccessHeaders(exchangeId),
    }));

export const downloadExchangeDocument = (exchangeId: string, documentId?: string): Promise<Blob> =>
    executeRequest(() => apiClient.get<Blob>(`/exchanges/${exchangeId}/documents/${documentId}/file`, blobRequest));

export const downloadExchangeDocumentZip = (exchangeId: string, request: DownloadDocumentsZipRequest): Promise<Blob> =>
    executeRequest(() => apiClient.post<Blob>(`/exchanges/${exchangeId}/documents/zip-file`, request, blobRequest));

export const downloadPreviewPDFExchangeDocument = (exchangeId: string, documentId?: string) =>
    executeRequest(() => apiClient.get(`/exchanges/${exchangeId}/documents/${documentId}/preview`, blobRequest));

export const downloadExchangeDocumentThumbnail = (
    exchangeId: string,
    documentId: string,
    version?: string,
) => executeRequest(() => apiClient.get(
    `/exchanges/${exchangeId}/documents/${documentId}/thumbnail`,
    {...blobRequest, params: version ? {v: version} : undefined},
));

export const downloadNoAuthExchangeDocumentThumbnail = (
    exchangeId: string,
    documentId: string,
    version?: string,
) => executeRequest(() => apiClient.get(
    `no-auth/exchanges/${exchangeId}/documents/${documentId}/thumbnail`,
    {
        ...blobRequest,
        params: version ? {v: version} : undefined,
        headers: getNoAuthAccessHeaders(exchangeId),
    },
));

export const requestNoAuthExchangeOtp = (exchangeId: string) =>
    executeRequest(() => apiClient.post(`no-auth/exchanges/${exchangeId}/otp`, undefined, {
        headers: getNoAuthAccessHeaders(exchangeId),
    }));

export const verifyNoAuthExchangeAccessCode = (exchangeId: string, otp: string) =>
    executeRequest(() => apiClient.post(`no-auth/exchanges/${exchangeId}/verify-access-code`, {otp}, {
        headers: getNoAuthAccessHeaders(exchangeId),
    }));

export const requestExchangeRecipientOtp = (exchangeId: string) =>
    executeRequest(() => apiClient.post(`/exchanges/${exchangeId}/recipient-otp`));

export const resendNoAuthExchangeInvitation = (exchangeId: string): Promise<void> =>
    executeRequest(() => apiClient.post(`/exchanges/${exchangeId}/recipient-invitations/primary/resend`));

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

export const addDocumentComment = (
    exchangeId: string,
    documentId: string,
    commentText: string,
    isInternal: boolean,
    pageNumber?: number,
    documentVersionId?: string,
) =>
    executeRequest(() =>
        apiClient.post(`/exchanges/${exchangeId}/documents/${documentId}/comments`, {
            commentText,
            isInternal,
            pageNumber,
            documentVersionId,
        })
    );

export const getDocumentVersions = (exchangeId: string, documentId: string) =>
    executeRequest(() =>
        apiClient.get(`/exchanges/${exchangeId}/documents/${documentId}/versions`)
    );

export const downloadDocumentVersion = (exchangeId: string, documentId: string, versionId: string) =>
    executeRequest(() =>
        apiClient.get(`/exchanges/${exchangeId}/documents/${documentId}/versions/${versionId}/file`, blobRequest)
    );

export const getLatestDocumentVersion = (exchangeId: string, documentId: string) =>
    executeRequest(() =>
        apiClient.get(`/exchanges/${exchangeId}/documents/${documentId}/versions/latest`)
    );

export const listExchangeAccess = (exchangeId: string): Promise<ExchangeAccessEntryDto[]> =>
    executeRequest(() => apiClient.get(`/exchanges/${exchangeId}/access`));

export const grantExchangeAccess = (
    exchangeId: string,
    request: GrantExchangeShareRequest,
): Promise<ExchangeAccessEntryDto[]> =>
    executeRequest(() => apiClient.post(`/exchanges/${exchangeId}/access`, request));

export const inviteTrustedParticipant = (
    exchangeId: string,
    request: InviteTrustedParticipantRequest,
): Promise<ExchangeAccessEntryDto[]> =>
    executeRequest(() => apiClient.post(`/exchanges/${exchangeId}/recipient-invitations`, request));

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

export const replaceExchangePrimaryRecipient = (
    exchangeId: string,
    selection: ExchangeRecipientSelection,
): Promise<ExchangeAccessEntryDto[]> =>
    executeRequest(() =>
        apiClient.post(`/exchanges/${exchangeId}/access/primary-recipient`, {selection}),
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
