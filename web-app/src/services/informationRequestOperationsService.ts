import apiClient from "./apiClient.ts";
import {statedInformationRequestRefusal} from "./informationRequestRuntimeService.ts";
import {
    InformationRequestAuditPageDto,
    InformationRequestAuditReconciliationDto,
    InformationRequestClockDto,
    InformationRequestDisposalStandingDto,
    InformationRequestNoticeHistoryDto,
    InformationRequestOperationsFilter,
    InformationRequestOperationsPageDto,
    InformationRequestRecordExportDto,
} from "../app/models/models.tsx";

const read = async <T>(request: () => Promise<{data: T}>): Promise<T> =>
{
    try
    {
        return (await request()).data;
    }
    catch (error: unknown)
    {
        throw statedInformationRequestRefusal(error);
    }
};

const requestPath = (requestId: string): string => `/information-requests/${requestId}`;

export const getInformationRequestOperations = (filter: InformationRequestOperationsFilter): Promise<InformationRequestOperationsPageDto> =>
    read(() => apiClient.get("/information-request-operations", {
        params: {
            slaStatus: filter.slaStatus,
            exceptionsOnly: filter.exceptionsOnly || undefined,
            limit: filter.limit,
            offset: filter.offset,
        },
    }));

export const getInformationRequestClocks = (requestId: string): Promise<InformationRequestClockDto[]> =>
    read(() => apiClient.get(`${requestPath(requestId)}/clocks`));

export const getInformationRequestNotices = (requestId: string): Promise<InformationRequestNoticeHistoryDto[]> =>
    read(() => apiClient.get(`${requestPath(requestId)}/notices`));

export const getInformationRequestAuditEvents = (requestId: string): Promise<InformationRequestAuditPageDto> =>
    read(() => apiClient.get(`${requestPath(requestId)}/audit-events`));

export const getInformationRequestAuditReconciliation = (requestId: string): Promise<InformationRequestAuditReconciliationDto> =>
    read(() => apiClient.get(`${requestPath(requestId)}/audit-reconciliation`));

export const getInformationRequestDisposalStanding = (requestId: string): Promise<InformationRequestDisposalStandingDto> =>
    read(() => apiClient.get(`${requestPath(requestId)}/disposal-standing`));

export const getInformationRequestRecordExports = (requestId: string): Promise<InformationRequestRecordExportDto[]> =>
    read(() => apiClient.get(`${requestPath(requestId)}/record-exports`));

export const getInformationRequestRecordExport = (requestId: string, exportId: string): Promise<InformationRequestRecordExportDto> =>
    read(() => apiClient.get(`${requestPath(requestId)}/record-exports/${exportId}`));

export const createInformationRequestRecordExport = (
    requestId: string,
    idempotencyKey: string,
): Promise<InformationRequestRecordExportDto> =>
    read(() => apiClient.post(`${requestPath(requestId)}/record-exports`, {}, {
        headers: {"Idempotency-Key": idempotencyKey},
    }));
