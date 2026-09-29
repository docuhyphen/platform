import apiClient from "./apiClient.ts";
import {statedInformationRequestRefusal} from "./informationRequestRuntimeService.ts";
import {
    ChangeInformationRequestClockRequest,
    DefineInformationRequestClockPolicyRequest,
    InformationRequestAuditPageDto,
    InformationRequestClockDto,
    InformationRequestClockPolicyDefinitionRequest,
    InformationRequestClockPolicyDto,
    InformationRequestPrivacyRequestDto,
    InformationRequestReminderResultDto,
    InformationRequestSubjectRestrictionDto,
    RecordInformationRequestPrivacyRequestRequest,
    StartInformationRequestClockRequest,
} from "../app/models/models.tsx";

export type InformationRequestClockChangePath = "pauses" | "resumptions" | "extensions";

export interface InformationRequestAuditSearch
{
    requestId?: string;
    eventClass?: string;
    eventType?: string;
    actorId?: string;
    occurredAfter?: string;
    occurredBefore?: string;
    limit: number;
    offset: number;
}

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

const keyed = (idempotencyKey: string, expectedETag?: string): {headers: Record<string, string>} => ({
    headers: expectedETag
        ? {"Idempotency-Key": idempotencyKey, "If-Match": expectedETag}
        : {"Idempotency-Key": idempotencyKey},
});

const definedParams = (search: InformationRequestAuditSearch): Record<string, string | number> =>
    Object.fromEntries(Object.entries(search).filter(([, value]) => value !== undefined && value !== ""));

export const getInformationRequestClockPolicies = (): Promise<InformationRequestClockPolicyDto[]> =>
    read(() => apiClient.get("/information-request-clock-policies"));

export const defineInformationRequestClockPolicy = (
    request: DefineInformationRequestClockPolicyRequest,
): Promise<InformationRequestClockPolicyDto> =>
    read(() => apiClient.post("/information-request-clock-policies", request));

export const publishInformationRequestClockPolicyVersion = (
    policyId: string,
    definition: InformationRequestClockPolicyDefinitionRequest,
): Promise<InformationRequestClockPolicyDto> =>
    read(() => apiClient.post(`/information-request-clock-policies/${policyId}/versions`, definition));

export const startInformationRequestClock = (
    requestId: string,
    request: StartInformationRequestClockRequest,
    idempotencyKey: string,
): Promise<InformationRequestClockDto> =>
    read(() => apiClient.post(`/information-requests/${requestId}/clocks`, request, keyed(idempotencyKey)));

export const changeInformationRequestClock = (
    requestId: string,
    clockId: string,
    change: InformationRequestClockChangePath,
    request: ChangeInformationRequestClockRequest,
    clockETag: string,
    idempotencyKey: string,
): Promise<InformationRequestClockDto> =>
    read(() => apiClient.post(
        `/information-requests/${requestId}/clocks/${clockId}/${change}`,
        request,
        keyed(idempotencyKey, clockETag),
    ));

export const sendInformationRequestReminders = (
    requestIds: string[],
    idempotencyKey: string,
): Promise<InformationRequestReminderResultDto[]> =>
    read(() => apiClient.post("/information-request-reminders", {requestIds}, keyed(idempotencyKey)));

export const recordInformationRequestPrivacyRequest = (
    request: RecordInformationRequestPrivacyRequestRequest,
): Promise<InformationRequestPrivacyRequestDto> =>
    read(() => apiClient.post("/information-request-privacy-requests", request));

export const getInformationRequestPrivacyRequests = (subjectIdentityRefId?: string): Promise<InformationRequestPrivacyRequestDto[]> =>
    read(() => apiClient.get("/information-request-privacy-requests", {params: {subjectIdentityRefId}}));

export const getInformationRequestSubjectRestrictions = (): Promise<InformationRequestSubjectRestrictionDto[]> =>
    read(() => apiClient.get("/information-request-subject-restrictions"));

export const liftInformationRequestSubjectRestriction = (
    restrictionId: string,
    reasonCode: string,
): Promise<InformationRequestSubjectRestrictionDto> =>
    read(() => apiClient.post(`/information-request-subject-restrictions/${restrictionId}/lift`, {reasonCode}));

export const searchInformationRequestAuditEvents = (search: InformationRequestAuditSearch): Promise<InformationRequestAuditPageDto> =>
    read(() => apiClient.get("/information-request-audit-events", {params: definedParams(search)}));
