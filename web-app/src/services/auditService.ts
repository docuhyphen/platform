import apiClient from './apiClient';
import axios from "axios";
import {
    AuditEventDto,
    AuditEventPageDto,
    AuditExportApprovalDto,
    AuditExportCreateRequestDto,
    AuditExportDto,
    AuditOrganizationIntegrityDto,
} from "../app/models/models.tsx";

const executeRequest = async <T>(fn: () => Promise<{ data: T }>): Promise<T> =>
{
    try
    {
        const {data} = await fn();
        return data;
    }
    catch (error: unknown)
    {
        if (axios.isAxiosError(error))
        {
            throw error.response?.data ?? error.message;
        }
        if (error instanceof Error)
        {
            throw error.message;
        }
        throw error;
    }
};

export interface AuditCursorParams
{
    cursorOccurredAt?: string;
    cursorEventId?: string;
    limit?: number;
}

export interface AuditEventSearchParams extends AuditCursorParams
{
    categories?: string[];
    occurredAfter?: string;
    occurredBefore?: string;
}

const toCategoriesParam = (categories?: string[]): string | undefined =>
    categories && categories.length > 0 ? categories.join(',') : undefined;

// ── Event search ───────────────────────────────────────────────────────────────

export const fetchOrganizationAuditEvents = (
    organizationId: string,
    params?: AuditEventSearchParams,
): Promise<AuditEventPageDto> =>
    executeRequest(() =>
        apiClient.get(`/organizations/${organizationId}/audit-events`, {
            params: {
                categories: toCategoriesParam(params?.categories),
                cursorOccurredAt: params?.cursorOccurredAt,
                cursorEventId: params?.cursorEventId,
                occurredAfter: params?.occurredAfter,
                occurredBefore: params?.occurredBefore,
                limit: params?.limit,
            },
        })
    );

export const fetchOrganizationAuditEvent = (organizationId: string, eventId: string): Promise<AuditEventDto> =>
    executeRequest(() =>
        apiClient.get(`/organizations/${organizationId}/audit-events/${eventId}`)
    );

export const fetchExchangeLedgerEvents = (exchangeId: string, params?: AuditCursorParams): Promise<AuditEventPageDto> =>
    executeRequest(() =>
        apiClient.get(`/exchanges/${exchangeId}/audit-events`, {params})
    );

export const fetchExchangeDocumentLedgerEvents = (
    exchangeId: string,
    documentId: string,
    params?: AuditCursorParams,
): Promise<AuditEventPageDto> =>
    executeRequest(() =>
        apiClient.get(`/exchanges/${exchangeId}/documents/${documentId}/audit-events`, {params})
    );

export const fetchWorkflowDefinitionAuditEvents = (
    definitionId: string,
    params?: AuditCursorParams,
): Promise<AuditEventPageDto> =>
    executeRequest(() =>
        apiClient.get(`/workflows/definitions/${definitionId}/audit-events`, {params})
    );

export const fetchApplicationAuditEvents = (applicationId: string, params?: AuditCursorParams): Promise<AuditEventPageDto> =>
    executeRequest(() =>
        apiClient.get(`/admin/applications/${applicationId}/audit-events`, {params})
    );

export const fetchSecurityIncidentAuditEvents = (params?: AuditCursorParams): Promise<AuditEventPageDto> =>
    executeRequest(() =>
        apiClient.get(`/auth/security-incidents/audit-events`, {params})
    );

export const fetchMySecurityEvents = (params?: AuditCursorParams): Promise<AuditEventPageDto> =>
    executeRequest(() =>
        apiClient.get(`/users/me/security-events`, {params})
    );

export const fetchPlatformAuditEvents = (params?: AuditEventSearchParams): Promise<AuditEventPageDto> =>
    executeRequest(() =>
        apiClient.get(`/platform/audit-events`, {
            params: {
                categories: toCategoriesParam(params?.categories),
                cursorOccurredAt: params?.cursorOccurredAt,
                cursorEventId: params?.cursorEventId,
                occurredAfter: params?.occurredAfter,
                occurredBefore: params?.occurredBefore,
                limit: params?.limit,
            },
        })
    );

// ── Organization exports ────────────────────────────────────────────────────────

export const requestOrganizationAuditExport = (
    organizationId: string,
    request: AuditExportCreateRequestDto,
): Promise<AuditExportDto> =>
    executeRequest(() =>
        apiClient.post(`/organizations/${organizationId}/audit-exports`, request)
    );

export const listOrganizationAuditExports = (organizationId: string): Promise<AuditExportDto[]> =>
    executeRequest(() =>
        apiClient.get(`/organizations/${organizationId}/audit-exports`)
    );

export const getOrganizationAuditExport = (organizationId: string, exportId: string): Promise<AuditExportDto> =>
    executeRequest(() =>
        apiClient.get(`/organizations/${organizationId}/audit-exports/${exportId}`)
    );

export const approveOrganizationAuditExport = (
    organizationId: string,
    exportId: string,
    note?: string,
): Promise<AuditExportDto> =>
    executeRequest(() =>
        apiClient.post(`/organizations/${organizationId}/audit-exports/${exportId}/approvals`, {note})
    );

export const listOrganizationAuditExportApprovals = (
    organizationId: string,
    exportId: string,
): Promise<AuditExportApprovalDto[]> =>
    executeRequest(() =>
        apiClient.get(`/organizations/${organizationId}/audit-exports/${exportId}/approvals`)
    );

export const downloadOrganizationAuditExport = (organizationId: string, exportId: string): Promise<Blob> =>
    executeRequest(() =>
        apiClient.get(`/organizations/${organizationId}/audit-exports/${exportId}/file`, {responseType: 'blob'})
    );

export const getOrganizationAuditIntegrity = (organizationId: string): Promise<AuditOrganizationIntegrityDto> =>
    executeRequest(() =>
        apiClient.get(`/organizations/${organizationId}/audit-integrity`)
    );

// ── Platform exports ────────────────────────────────────────────────────────────

export const requestPlatformAuditExport = (request: AuditExportCreateRequestDto): Promise<AuditExportDto> =>
    executeRequest(() =>
        apiClient.post(`/platform/audit-exports`, request)
    );

export const listPlatformAuditExports = (): Promise<AuditExportDto[]> =>
    executeRequest(() =>
        apiClient.get(`/platform/audit-exports`)
    );

export const getPlatformAuditExport = (exportId: string): Promise<AuditExportDto> =>
    executeRequest(() =>
        apiClient.get(`/platform/audit-exports/${exportId}`)
    );

export const approvePlatformAuditExport = (exportId: string, note?: string): Promise<AuditExportDto> =>
    executeRequest(() =>
        apiClient.post(`/platform/audit-exports/${exportId}/approvals`, {note})
    );

export const listPlatformAuditExportApprovals = (exportId: string): Promise<AuditExportApprovalDto[]> =>
    executeRequest(() =>
        apiClient.get(`/platform/audit-exports/${exportId}/approvals`)
    );

export const downloadPlatformAuditExport = (exportId: string): Promise<Blob> =>
    executeRequest(() =>
        apiClient.get(`/platform/audit-exports/${exportId}/file`, {responseType: 'blob'})
    );

export const getPlatformAuditIntegrity = (): Promise<AuditOrganizationIntegrityDto> =>
    executeRequest(() =>
        apiClient.get(`/platform/audit-integrity`)
    );
