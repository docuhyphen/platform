import apiClient from "./apiClient.ts";
import {postInformationRequestSupplement} from "./informationRequestSuccessorTransport.ts";
import {statedInformationRequestRefusal} from "./informationRequestRuntimeService.ts";
import {
    AssignInformationRequestPartyRequest,
    AssignInformationRequestSubjectRequest,
    ChangeInformationRequestCompletionGateRequest,
    CreateInformationRequestRequest,
    DefineInformationRequestRecurrenceRequest,
    DefineInformationRequestRefreshRuleRequest,
    InformationRequestAccessLinkDto,
    InformationRequestAccessLinkIssuedDto,
    InformationRequestAssignedWorkDto,
    InformationRequestDocumentPlaceholderDto,
    InformationRequestDto,
    InformationRequestExchangeListingDto,
    InformationRequestLineageViewDto,
    InformationRequestPartyDto,
    InformationRequestPartyListingDto,
    InformationRequestRecipientPreviewDto,
    InformationRequestRecurrenceDto,
    InformationRequestRefreshRuleDto,
    InformationRequestSubjectDto,
    InformationRequestSuccessorResultDto,
    InformationRequestTrustedRecipientCandidateDto,
    IssueInformationRequestAccessLinkRequest,
    ReassignInformationRequestPartyRequest,
} from "../app/models/models.tsx";

export interface InformationRequestPartyChange
{
    party: InformationRequestPartyDto;
    partiesETag: string;
}

interface TransportResponse<T>
{
    data: T;
    headers?: Record<string, string | undefined>;
}

const call = async <T>(request: () => Promise<TransportResponse<T>>): Promise<TransportResponse<T>> =>
{
    try
    {
        return await request();
    }
    catch (error: unknown)
    {
        throw statedInformationRequestRefusal(error);
    }
};

const read = async <T>(request: () => Promise<TransportResponse<T>>): Promise<T> => (await call(request)).data;

const etagOf = (headers?: Record<string, string | undefined>): string => headers?.etag ?? headers?.ETag ?? "";

const commandHeaders = (idempotencyKey: string, expectedETag?: string): Record<string, string> =>
    expectedETag
        ? {"Idempotency-Key": idempotencyKey, "If-Match": expectedETag}
        : {"Idempotency-Key": idempotencyKey};

const requestPath = (requestId: string): string => `/information-requests/${requestId}`;

const partyChange = async (request: () => Promise<TransportResponse<InformationRequestPartyDto>>): Promise<InformationRequestPartyChange> =>
{
    const {data, headers} = await call(request);
    return {party: data, partiesETag: etagOf(headers)};
};

export const informationRequestAccessLinkUrl = (requestId: string, accessToken: string, origin: string): string =>
    `${origin}/nir?${new URLSearchParams({s: requestId, t: accessToken}).toString()}`;

export const getExchangeInformationRequests = (exchangeId: string): Promise<InformationRequestExchangeListingDto> =>
    read(() => apiClient.get(`/exchanges/${exchangeId}/information-requests`));

export const getAssignedInformationRequests = (): Promise<InformationRequestAssignedWorkDto[]> =>
    read(() => apiClient.get("/information-request-assignments"));

export const createInformationRequest = (request: CreateInformationRequestRequest, idempotencyKey: string): Promise<InformationRequestDto> =>
    read(() => apiClient.post("/information-requests", request, {headers: commandHeaders(idempotencyKey)}));

export const getInformationRequestParties = async (requestId: string): Promise<InformationRequestPartyListingDto> =>
{
    const {
        data,
        headers
    } = await call<InformationRequestPartyDto[]>(() => apiClient.get(`${requestPath(requestId)}/parties`));
    return {parties: data, partiesETag: etagOf(headers)};
};

export const assignInformationRequestParty = (
    requestId: string,
    request: AssignInformationRequestPartyRequest,
    partiesETag: string,
    idempotencyKey: string,
): Promise<InformationRequestPartyChange> =>
    partyChange(() => apiClient.post(`${requestPath(requestId)}/parties`, request, {headers: commandHeaders(idempotencyKey, partiesETag)}));

export const reassignInformationRequestParty = (
    requestId: string,
    partyId: string,
    request: ReassignInformationRequestPartyRequest,
    partiesETag: string,
    idempotencyKey: string,
): Promise<InformationRequestPartyChange> =>
    partyChange(() => apiClient.post(
        `${requestPath(requestId)}/parties/${partyId}/reassignment`,
        request,
        {headers: commandHeaders(idempotencyKey, partiesETag)},
    ));

export const revokeInformationRequestParty = (
    requestId: string,
    partyId: string,
    partiesETag: string,
    idempotencyKey: string,
): Promise<InformationRequestPartyChange> =>
    partyChange(() => apiClient.post(
        `${requestPath(requestId)}/parties/${partyId}/revocation`,
        {},
        {headers: commandHeaders(idempotencyKey, partiesETag)},
    ));

export const assignInformationRequestSubject = (
    requestId: string,
    request: AssignInformationRequestSubjectRequest,
    partiesETag: string,
    idempotencyKey: string,
): Promise<InformationRequestPartyChange> =>
    partyChange(() => apiClient.post(`${requestPath(requestId)}/subjects`, request, {headers: commandHeaders(idempotencyKey, partiesETag)}));

export const getInformationRequestTrustedRecipientCandidates = (
    requestId: string,
): Promise<InformationRequestTrustedRecipientCandidateDto[]> =>
    read(() => apiClient.get(`${requestPath(requestId)}/trusted-recipient-candidates`));

export const getInformationRequestRecipientPreview = (
    requestId: string,
    partyId: string,
): Promise<InformationRequestRecipientPreviewDto> =>
    read(() => apiClient.get(`${requestPath(requestId)}/parties/${partyId}/recipient-preview`));

export const getInformationRequestSubjects = (): Promise<InformationRequestSubjectDto[]> =>
    read(() => apiClient.get("/information-request-subjects"));

export const getInformationRequestAccessLinks = (requestId: string): Promise<InformationRequestAccessLinkDto[]> =>
    read(() => apiClient.get(`${requestPath(requestId)}/access-links`));

export const getInformationRequestDocumentPlaceholders = (requestId: string): Promise<InformationRequestDocumentPlaceholderDto[]> =>
    read(() => apiClient.get(`${requestPath(requestId)}/document-placeholders`));

export const issueInformationRequestAccessLink = (
    requestId: string,
    request: IssueInformationRequestAccessLinkRequest,
    partyETag: string,
    idempotencyKey: string,
): Promise<InformationRequestAccessLinkIssuedDto> =>
    read(() => apiClient.post(`${requestPath(requestId)}/access-links`, request, {headers: commandHeaders(idempotencyKey, partyETag)}));

export const rotateInformationRequestAccessLink = (
    requestId: string,
    shareLinkId: string,
    partyETag: string,
    idempotencyKey: string,
): Promise<InformationRequestAccessLinkIssuedDto> =>
    read(() => apiClient.post(
        `${requestPath(requestId)}/access-links/${shareLinkId}/rotation`,
        {},
        {headers: commandHeaders(idempotencyKey, partyETag)},
    ));

export const revokeInformationRequestAccessLink = (
    requestId: string,
    shareLinkId: string,
    partyETag: string,
    idempotencyKey: string,
): Promise<InformationRequestAccessLinkDto> =>
    read(() => apiClient.post(
        `${requestPath(requestId)}/access-links/${shareLinkId}/revocation`,
        {},
        {headers: commandHeaders(idempotencyKey, partyETag)},
    ));

export const getInformationRequestLineage = (requestId: string): Promise<InformationRequestLineageViewDto> =>
    read(() => apiClient.get(`${requestPath(requestId)}/successors`));

export const defineInformationRequestRecurrence = (
    requestId: string,
    request: DefineInformationRequestRecurrenceRequest,
    requestETag: string,
    idempotencyKey: string,
): Promise<InformationRequestRecurrenceDto> =>
    read(() => apiClient.post(`${requestPath(requestId)}/recurrences`, request, {headers: commandHeaders(idempotencyKey, requestETag)}));

export const createNextInformationRequestOccurrence = (
    requestId: string,
    recurrenceId: string,
    idempotencyKey: string,
): Promise<InformationRequestSuccessorResultDto> =>
    read(() => apiClient.post(
        `${requestPath(requestId)}/recurrences/${recurrenceId}/occurrences`,
        {},
        {headers: commandHeaders(idempotencyKey)},
    ));

export const requestInformationRequestSupplement = (
    requestId: string,
    reasonCode: string,
    requestETag: string,
    idempotencyKey: string,
): Promise<InformationRequestSuccessorResultDto> =>
    read(() => postInformationRequestSupplement(
        requestId, reasonCode, commandHeaders(idempotencyKey, requestETag),
    ));

export const defineInformationRequestRefreshRule = (
    requestId: string,
    request: DefineInformationRequestRefreshRuleRequest,
    requestETag: string,
    idempotencyKey: string,
): Promise<InformationRequestRefreshRuleDto> =>
    read(() => apiClient.post(`${requestPath(requestId)}/refresh-rules`, request, {headers: commandHeaders(idempotencyKey, requestETag)}));

export const createInformationRequestRefresh = (
    requestId: string,
    refreshRuleId: string,
    idempotencyKey: string,
): Promise<InformationRequestSuccessorResultDto> =>
    read(() => apiClient.post(
        `${requestPath(requestId)}/refresh-rules/${refreshRuleId}/refreshes`,
        {},
        {headers: commandHeaders(idempotencyKey)},
    ));

export const issueInformationRequest = (requestId: string, requestETag: string, idempotencyKey: string): Promise<InformationRequestDto> =>
    read(() => apiClient.post(`${requestPath(requestId)}/issuance`, {}, {headers: commandHeaders(idempotencyKey, requestETag)}));

export const cancelInformationRequest = (
    requestId: string,
    reasonCode: string,
    requestETag: string,
    idempotencyKey: string,
): Promise<InformationRequestDto> =>
    read(() => apiClient.post(
        `${requestPath(requestId)}/cancellation`,
        {reasonCode},
        {headers: commandHeaders(idempotencyKey, requestETag)},
    ));

export const changeInformationRequestCompletionGate = (
    requestId: string,
    gatesExchangeClosure: boolean,
    requestETag: string,
    idempotencyKey: string,
): Promise<InformationRequestDto> => {
    const request: ChangeInformationRequestCompletionGateRequest = {gatesExchangeClosure};
    return read(() => apiClient.put(
        `${requestPath(requestId)}/completion-gate`,
        request,
        {headers: commandHeaders(idempotencyKey, requestETag)},
    ));
};

export const supersedeInformationRequest = (
    requestId: string,
    supersededByRequestId: string,
    reasonCode: string,
    requestETag: string,
    idempotencyKey: string,
): Promise<InformationRequestDto> =>
    read(() => apiClient.post(
        `${requestPath(requestId)}/supersession`,
        {supersededByRequestId, reasonCode},
        {headers: commandHeaders(idempotencyKey, requestETag)},
    ));
