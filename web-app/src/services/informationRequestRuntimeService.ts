import axios from "axios";
import apiClient from "./apiClient.ts";
import {
    CreateInformationRequestGroupOccurrenceRequest,
    InformationRequestAccessSessionDto,
    InformationRequestGroupOccurrenceDto,
    InformationRequestResponseDto,
    InformationRequestResponseWorkspaceDto,
    PatchInformationRequestResponsesRequest,
    ReorderInformationRequestGroupOccurrencesRequest,
    ResponseError,
} from "../app/models/models.tsx";

const IF_MATCH_HEADER = "If-Match";
const IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
const ACCESS_LINK_TOKEN_HEADER = "X-Request-Access-Token";
const SESSION_TOKEN_HEADER = "X-Request-Session-Token";
const COMMAND_STALE_CODE = "COMMAND_PRECONDITION_STALE";
const FIELDS_STALE_CODE = "FIELDS_PRECONDITION_STALE";

export type RuntimeCommandResult<T> =
    | { outcome: "SAVED"; responseETag: string; data: T }
    | { outcome: "STALE" };

export interface RuntimeCommandOptions
{
    expectedETag: string;
    idempotencyKey: string;
    accessLinkToken?: string;
}

const commandHeaders = (requestId: string, options: RuntimeCommandOptions): Record<string, string> =>
{
    const headers: Record<string, string> = {
        [IF_MATCH_HEADER]: options.expectedETag,
        [IDEMPOTENCY_KEY_HEADER]: options.idempotencyKey,
    };

    if (options.accessLinkToken)
    {
        headers[ACCESS_LINK_TOKEN_HEADER] = options.accessLinkToken;
        headers[SESSION_TOKEN_HEADER] = getInformationRequestSessionToken(requestId);
    }

    return headers;
};

const basePath = (requestId: string, accessLinkToken?: string): string =>
    accessLinkToken
        ? `/no-auth/information-requests/${requestId}`
        : `/information-requests/${requestId}`;

export const informationRequestAccessTokenKey = (requestId: string): string =>
    `information-request-access:${requestId}`;

export const storeInformationRequestAccessToken = (requestId: string, token: string): void =>
{
    if (getInformationRequestAccessToken(requestId) !== token)
        window.sessionStorage.removeItem(informationRequestSessionTokenKey(requestId));
    window.sessionStorage.setItem(informationRequestAccessTokenKey(requestId), token);
};

export const getInformationRequestAccessToken = (requestId: string): string =>
    window.sessionStorage.getItem(informationRequestAccessTokenKey(requestId)) ?? "";

const informationRequestSessionTokenKey = (requestId: string): string =>
    `information-request-session:${requestId}`;

export const storeInformationRequestSessionToken = (requestId: string, token: string): void =>
    window.sessionStorage.setItem(informationRequestSessionTokenKey(requestId), token);

export const getInformationRequestSessionToken = (requestId: string): string =>
    window.sessionStorage.getItem(informationRequestSessionTokenKey(requestId)) ?? "";

const staleRefusal = (error: unknown): boolean =>
{
    if (!axios.isAxiosError<ResponseError>(error) || error.response?.status !== 412) return false;
    const reasonCode = error.response.data?.reasonCode;
    return reasonCode === COMMAND_STALE_CODE || reasonCode === FIELDS_STALE_CODE;
};

const statedRefusal = (error: unknown): unknown =>
{
    if (axios.isAxiosError<ResponseError>(error)) return error.response?.data ?? error.message;
    return error instanceof Error ? error.message : error;
};

const executeRuntimeCommand = async <T>(
    request: () => Promise<{data: T; headers: Record<string, string | undefined>}>,
): Promise<RuntimeCommandResult<T>> =>
{
    try
    {
        const {data, headers} = await request();
        return {
            outcome: "SAVED",
            responseETag: headers.etag ?? headers.ETag ?? "",
            data,
        };
    }
    catch (error: unknown)
    {
        if (staleRefusal(error)) return {outcome: "STALE"};
        throw statedRefusal(error);
    }
};

const executeRequest = async <T>(
    request: () => Promise<{data: T}>,
): Promise<T> =>
{
    try
    {
        const {data} = await request();
        return data;
    }
    catch (error: unknown)
    {
        throw statedRefusal(error);
    }
};

const accessLinkHeaders = (accessLinkToken: string): Record<string, string> => ({
    [ACCESS_LINK_TOKEN_HEADER]: accessLinkToken,
});

export const getInformationRequestResponseWorkspace = (
    requestId: string,
    accessLinkToken?: string,
): Promise<InformationRequestResponseWorkspaceDto> =>
    executeRequest(() => apiClient.get(
        `${basePath(requestId, accessLinkToken)}/response-workspace`,
        accessLinkToken ? {headers: {
            ...accessLinkHeaders(accessLinkToken),
            [SESSION_TOKEN_HEADER]: getInformationRequestSessionToken(requestId),
        }} : undefined,
    ));

export const issueInformationRequestContactProofChallenge = (accessLinkToken: string): Promise<void> =>
    executeRequest(() => apiClient.post(
        "/no-auth/information-request-access-links/challenges",
        undefined,
        {headers: accessLinkHeaders(accessLinkToken)},
    ));

export const verifyInformationRequestContactProofChallenge = (
    accessLinkToken: string,
    otp: string,
): Promise<InformationRequestAccessSessionDto> =>
    executeRequest(() => apiClient.post(
        "/no-auth/information-request-access-links/sessions",
        {otp},
        {headers: accessLinkHeaders(accessLinkToken)},
    ));

export const patchInformationRequestResponses = (
    requestId: string,
    request: PatchInformationRequestResponsesRequest,
    options: RuntimeCommandOptions,
): Promise<RuntimeCommandResult<InformationRequestResponseDto[]>> =>
    executeRuntimeCommand(() => apiClient.patch(
        `${basePath(requestId, options.accessLinkToken)}/responses`,
        request,
        {headers: commandHeaders(requestId, options)},
    ));

export const addInformationRequestGroupOccurrence = (
    requestId: string,
    request: CreateInformationRequestGroupOccurrenceRequest,
    options: RuntimeCommandOptions,
): Promise<RuntimeCommandResult<InformationRequestGroupOccurrenceDto[]>> =>
    executeRuntimeCommand(() => apiClient.post(
        `${basePath(requestId, options.accessLinkToken)}/group-occurrences`,
        request,
        {headers: commandHeaders(requestId, options)},
    ));

export const removeInformationRequestGroupOccurrence = (
    requestId: string,
    occurrenceId: string,
    options: RuntimeCommandOptions,
): Promise<RuntimeCommandResult<InformationRequestGroupOccurrenceDto[]>> =>
    executeRuntimeCommand(() => apiClient.delete(
        `${basePath(requestId, options.accessLinkToken)}/group-occurrences/${occurrenceId}`,
        {headers: commandHeaders(requestId, options)},
    ));

export const reorderInformationRequestGroupOccurrences = (
    requestId: string,
    request: ReorderInformationRequestGroupOccurrencesRequest,
    options: RuntimeCommandOptions,
): Promise<RuntimeCommandResult<InformationRequestGroupOccurrenceDto[]>> =>
    executeRuntimeCommand(() => apiClient.patch(
        `${basePath(requestId, options.accessLinkToken)}/group-occurrences/order`,
        request,
        {headers: commandHeaders(requestId, options)},
    ));
