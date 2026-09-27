import apiClient from "./apiClient.ts";
import {statedInformationRequestRefusal} from "./informationRequestRuntimeService.ts";
import {
    PlaceRecordPreservationHoldRequest,
    PublishRecordRetentionScheduleRequest,
    RecordDisposalDto,
    RecordPreservationHoldDto,
    RecordPreservationHoldStatus,
    RecordPreservationScope,
    RecordRetentionScheduleDto,
} from "../app/models/models.tsx";

const call = async <T>(request: () => Promise<{data: T}>): Promise<T> =>
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

export const INFORMATION_REQUEST_RECORD_TYPE = "INFORMATION_REQUEST";

export const getRecordPreservationHolds = (status?: RecordPreservationHoldStatus): Promise<RecordPreservationHoldDto[]> =>
    call(() => apiClient.get("/record-preservation-holds", {params: {status}}));

export const placeRecordPreservationHold = (request: PlaceRecordPreservationHoldRequest): Promise<RecordPreservationHoldDto> =>
    call(() => apiClient.post("/record-preservation-holds", request));

export const changeRecordPreservationHoldScope = (
    holdId: string,
    scope: RecordPreservationScope,
    reason: string,
): Promise<RecordPreservationHoldDto> =>
    call(() => apiClient.patch(`/record-preservation-holds/${holdId}/scope`, {scope, reason}));

export const releaseRecordPreservationHold = (holdId: string, reason: string): Promise<RecordPreservationHoldDto> =>
    call(() => apiClient.post(`/record-preservation-holds/${holdId}/release`, {reason}));

export const getRecordRetentionSchedule = (resourceType: string): Promise<RecordRetentionScheduleDto> =>
    call(() => apiClient.get(`/record-retention-schedules/${resourceType}`));

export const publishRecordRetentionSchedule = (
    resourceType: string,
    request: PublishRecordRetentionScheduleRequest,
): Promise<RecordRetentionScheduleDto> =>
    call(() => apiClient.put(`/record-retention-schedules/${resourceType}`, request));

export const getRecordDisposals = (): Promise<RecordDisposalDto[]> =>
    call(() => apiClient.get("/record-disposals"));
