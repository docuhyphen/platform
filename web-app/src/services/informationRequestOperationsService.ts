import apiClient from "./apiClient.ts";
import {statedInformationRequestRefusal} from "./informationRequestRuntimeService.ts";
import {
    InformationRequestAuditPageDto,
    InformationRequestAuditReconciliationDto,
    InformationRequestClockDto,
    InformationRequestDisposalStandingDto,
    InformationRequestDto,
    InformationRequestNoticeHistoryDto,
    InformationRequestOperationsFilter,
    InformationRequestOperationsPageDto,
    InformationRequestRecordExportDto,
} from "../app/models/models.tsx";

const read = async <T>(request: () => Promise<{ data: T }>): Promise<T> =>
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

const listed = <T>(values: T[]): T[] | undefined => values.length > 0 ? values : undefined;

export const getInformationRequestOperations = (filter: InformationRequestOperationsFilter): Promise<InformationRequestOperationsPageDto> =>
    read(() => apiClient.get("/information-request-operations", {
        params: {
            state: listed(filter.states),
            exchangeId: filter.exchangeId || undefined,
            search: filter.search?.trim() || undefined,
            assigneeId: filter.assigneeId || undefined,
            slaStatus: filter.slaStatus,
            exception: listed(filter.exceptions),
            exceptionsOnly: filter.exceptionsOnly || undefined,
            limit: filter.limit,
            offset: filter.offset,
        },
        paramsSerializer: {indexes: null},
    }));

export const getInformationRequestHeader = (requestId: string): Promise<InformationRequestDto> =>
    read(() => apiClient.get(requestPath(requestId)));

export const getInformationRequestClocks = (requestId: string): Promise<InformationRequestClockDto[]> =>
    read(() => apiClient.get(`${requestPath(requestId)}/clocks`));

export const getInformationRequestNotices = (requestId: string): Promise<InformationRequestNoticeHistoryDto[]> =>
    read(() => apiClient.get(`${requestPath(requestId)}/notices`));

export const getInformationRequestAuditEvents = (
    requestId: string,
    page: { limit: number; offset: number },
): Promise<InformationRequestAuditPageDto> =>
    read(() => apiClient.get(`${requestPath(requestId)}/audit-events`, {
        params: {
            limit: page.limit,
            offset: page.offset
        }
    }));

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
    transferRegion?: string,
): Promise<InformationRequestRecordExportDto> =>
    read(() => apiClient.post(`${requestPath(requestId)}/record-exports`, transferRegion ? {transferRegion} : {}, {
        headers: {"Idempotency-Key": idempotencyKey},
    }));
