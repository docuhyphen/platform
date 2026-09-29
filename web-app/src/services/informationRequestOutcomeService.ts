import apiClient from "./apiClient.ts";
import {statedInformationRequestRefusal} from "./informationRequestRuntimeService.ts";
import {
    InformationRequestAcceptedFactDto,
    InformationRequestBusinessDecisionDto,
    InformationRequestSubmissionPackageDto,
    PromoteInformationRequestAcceptedFactRequest,
    RecordInformationRequestBusinessDecisionRequest,
    RevokeInformationRequestAcceptedFactRequest,
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

const keyed = (idempotencyKey: string): {headers: Record<string, string>} => ({headers: {"Idempotency-Key": idempotencyKey}});

const requestPath = (requestId: string): string => `/information-requests/${requestId}`;

export const getInformationRequestSubmissionPackages = (requestId: string): Promise<InformationRequestSubmissionPackageDto[]> =>
    read(() => apiClient.get(`${requestPath(requestId)}/submissions`));

export const getInformationRequestAcceptedFacts = (requestId: string): Promise<InformationRequestAcceptedFactDto[]> =>
    read(() => apiClient.get(`${requestPath(requestId)}/accepted-facts`));

export const promoteInformationRequestAcceptedFact = (
    requestId: string,
    request: PromoteInformationRequestAcceptedFactRequest,
    idempotencyKey: string,
): Promise<InformationRequestAcceptedFactDto> =>
    read(() => apiClient.post(`${requestPath(requestId)}/accepted-facts`, request, keyed(idempotencyKey)));

export const revokeInformationRequestAcceptedFact = (
    requestId: string,
    factId: string,
    request: RevokeInformationRequestAcceptedFactRequest,
    idempotencyKey: string,
): Promise<InformationRequestAcceptedFactDto> =>
    read(() => apiClient.post(`${requestPath(requestId)}/accepted-facts/${factId}/revocation`, request, keyed(idempotencyKey)));

export const getInformationRequestBusinessDecisions = (requestId: string): Promise<InformationRequestBusinessDecisionDto[]> =>
    read(() => apiClient.get(`${requestPath(requestId)}/business-decisions`));

export const recordInformationRequestBusinessDecision = (
    requestId: string,
    request: RecordInformationRequestBusinessDecisionRequest,
    idempotencyKey: string,
): Promise<InformationRequestBusinessDecisionDto> =>
    read(() => apiClient.post(`${requestPath(requestId)}/business-decisions`, request, keyed(idempotencyKey)));
