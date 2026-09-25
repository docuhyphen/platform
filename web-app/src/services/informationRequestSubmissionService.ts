import axios from "axios";
import apiClient from "./apiClient.ts";
import {
    informationRequestBasePath,
    informationRequestCommandHeaders,
    informationRequestReadHeaders,
    isStaleInformationRequestRefusal,
    RuntimeCommandOptions,
    RuntimeCommandResult,
    statedInformationRequestRefusal,
} from "./informationRequestRuntimeService.ts";
import {
    InformationRequestAmendmentDto,
    InformationRequestCarryForwardDto,
    InformationRequestLineageKind,
    InformationRequestSubmissionAttestationResultDto,
    InformationRequestSubmissionPreviewDto,
    InformationRequestSubmissionResultDto,
    InformationRequestSuccessorResultDto,
    RecordInformationRequestAttestationRequest,
    ResponseError,
    SubmitInformationRequestPackageRequest,
    WithdrawInformationRequestPackageRequest,
} from "../app/models/models.tsx";

const execute = async <T>(
    request: () => Promise<{data: T; headers: Record<string, string | undefined>}>,
): Promise<RuntimeCommandResult<T>> =>
{
    try
    {
        const {data, headers} = await request();
        return {outcome: "SAVED", responseETag: headers.etag ?? headers.ETag ?? "", data};
    }
    catch (error: unknown)
    {
        if (isStaleInformationRequestRefusal(error)) return {outcome: "STALE"};
        throw statedInformationRequestRefusal(error);
    }
};

const read = async <T>(request: () => Promise<{data: T}>): Promise<T> =>
{
    try
    {
        return (await request()).data;
    }
    catch (error: unknown)
    {
        if (axios.isAxiosError<ResponseError>(error)) throw error.response?.data ?? error.message;
        throw error;
    }
};

export const getInformationRequestSubmissionPreview = (
    requestId: string,
    stageKey?: string,
    accessLinkToken?: string,
): Promise<InformationRequestSubmissionPreviewDto> =>
    read(() => apiClient.get(`${informationRequestBasePath(requestId, accessLinkToken)}/submission-preview`, {
        params: stageKey ? {stageKey} : {},
        headers: informationRequestReadHeaders(requestId, accessLinkToken),
    }));

export const submitInformationRequestPackage = (
    requestId: string,
    request: SubmitInformationRequestPackageRequest,
    options: RuntimeCommandOptions,
): Promise<RuntimeCommandResult<InformationRequestSubmissionResultDto>> =>
    execute(() => apiClient.post(
        `${informationRequestBasePath(requestId, options.accessLinkToken)}/submissions`,
        request,
        {headers: informationRequestCommandHeaders(requestId, options)},
    ));

export const withdrawInformationRequestPackage = (
    requestId: string,
    packageId: string,
    request: WithdrawInformationRequestPackageRequest,
    options: RuntimeCommandOptions,
): Promise<RuntimeCommandResult<InformationRequestSubmissionResultDto>> =>
    execute(() => apiClient.post(
        `${informationRequestBasePath(requestId, options.accessLinkToken)}/submissions/${packageId}/withdrawal`,
        request,
        {headers: informationRequestCommandHeaders(requestId, options)},
    ));

export const recordInformationRequestAttestation = (
    requestId: string,
    requirementId: string,
    request: RecordInformationRequestAttestationRequest,
    options: RuntimeCommandOptions,
): Promise<RuntimeCommandResult<InformationRequestSubmissionAttestationResultDto>> =>
    execute(() => apiClient.post(
        `${informationRequestBasePath(requestId, options.accessLinkToken)}/requirements/${requirementId}/attestations`,
        request,
        {headers: informationRequestCommandHeaders(requestId, options)},
    ));

export const getInformationRequestAmendments = (
    requestId: string,
    accessLinkToken?: string,
): Promise<InformationRequestAmendmentDto[]> =>
    read(() => apiClient.get(`${informationRequestBasePath(requestId, accessLinkToken)}/amendments`, {
        headers: informationRequestReadHeaders(requestId, accessLinkToken),
    }));

export const getInformationRequestCarryForwards = (
    requestId: string,
    accessLinkToken?: string,
): Promise<InformationRequestCarryForwardDto[]> =>
    read(() => apiClient.get(`${informationRequestBasePath(requestId, accessLinkToken)}/carry-forwards`, {
        headers: informationRequestReadHeaders(requestId, accessLinkToken),
    }));

export const createInformationRequestSupplement = (
    requestId: string,
    reasonCode: string | undefined,
    options: RuntimeCommandOptions,
): Promise<RuntimeCommandResult<InformationRequestSuccessorResultDto>> =>
    execute(() => apiClient.post(
        `/information-requests/${requestId}/successors`,
        {kind: InformationRequestLineageKind.SUPPLEMENT, ...(reasonCode ? {reasonCode} : {})},
        {headers: informationRequestCommandHeaders(requestId, {...options, accessLinkToken: undefined})},
    ));
