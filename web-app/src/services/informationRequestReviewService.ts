import apiClient from "./apiClient.ts";
import {
    ACCESS_LINK_TOKEN_HEADER,
    getInformationRequestSessionToken,
    IDEMPOTENCY_KEY_HEADER,
    IF_MATCH_HEADER,
    informationRequestBasePath,
    informationRequestCommandHeaders,
    informationRequestReadHeaders,
    isStaleInformationRequestRefusal,
    RuntimeCommandOptions,
    RuntimeCommandResult,
    SESSION_TOKEN_HEADER,
    statedInformationRequestRefusal,
} from "./informationRequestRuntimeService.ts";
import {
    AssignInformationRequestReviewerRequest,
    ChangeInformationRequestReviewAssignmentRequest,
    InformationRequestRespondentReviewDto,
    InformationRequestReviewCommandResultDto,
    InformationRequestReviewDto,
    InformationRequestReviewQueueEntryDto,
    InformationRequestReviewWorksheetDto,
    OverrideInformationRequestReviewItemRequest,
    RecordInformationRequestReviewCommentRequest,
    RecordInformationRequestReviewFindingRequest,
    ReopenInformationRequestReviewRequest,
    SaveInformationRequestReviewWorksheetRequest,
} from "../app/models/models.tsx";

const execute = async <T>(
    request: () => Promise<{ data: T; headers: Record<string, string | undefined> }>,
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

const keyedHeaders = (requestId: string, idempotencyKey: string, accessLinkToken?: string): Record<string, string> =>
    accessLinkToken
        ? {
            [IDEMPOTENCY_KEY_HEADER]: idempotencyKey,
            [ACCESS_LINK_TOKEN_HEADER]: accessLinkToken,
            [SESSION_TOKEN_HEADER]: getInformationRequestSessionToken(requestId),
        }
        : {[IDEMPOTENCY_KEY_HEADER]: idempotencyKey};

const reviewPath = (requestId: string, reviewId: string, accessLinkToken?: string): string =>
    `${informationRequestBasePath(requestId, accessLinkToken)}/reviews/${reviewId}`;

export const getInformationRequestReview = (requestId: string, reviewId: string): Promise<InformationRequestReviewDto> =>
    read(() => apiClient.get(reviewPath(requestId, reviewId)));

export const listInformationRequestReviews = (requestId: string): Promise<InformationRequestReviewDto[]> =>
    read(() => apiClient.get(`${informationRequestBasePath(requestId)}/reviews`));

export const getInformationRequestReviewQueue = (): Promise<InformationRequestReviewQueueEntryDto[]> =>
    read(() => apiClient.get("/information-request-reviews"));

export const getInformationRequestReviewResults = (
    requestId: string,
    accessLinkToken?: string,
): Promise<InformationRequestRespondentReviewDto[]> =>
    read(() => apiClient.get(`${informationRequestBasePath(requestId, accessLinkToken)}/review-results`, {
        headers: informationRequestReadHeaders(requestId, accessLinkToken),
    }));

export const saveInformationRequestReviewWorksheet = (
    requestId: string,
    reviewId: string,
    assignmentId: string,
    request: SaveInformationRequestReviewWorksheetRequest,
    expectedETag: string,
): Promise<RuntimeCommandResult<InformationRequestReviewWorksheetDto>> =>
    execute(() => apiClient.patch(
        `${reviewPath(requestId, reviewId)}/assignments/${assignmentId}/worksheet`,
        request,
        {headers: {[IF_MATCH_HEADER]: expectedETag}},
    ));

export const recordInformationRequestReviewDecisions = (
    requestId: string,
    reviewId: string,
    assignmentId: string,
    options: RuntimeCommandOptions,
): Promise<RuntimeCommandResult<InformationRequestReviewCommandResultDto>> =>
    execute(() => apiClient.post(
        `${reviewPath(requestId, reviewId)}/assignments/${assignmentId}/decisions`,
        undefined,
        {headers: informationRequestCommandHeaders(requestId, {...options, accessLinkToken: undefined})},
    ));

export const overrideInformationRequestReviewItem = (
    requestId: string,
    reviewId: string,
    request: OverrideInformationRequestReviewItemRequest,
    options: RuntimeCommandOptions,
): Promise<RuntimeCommandResult<InformationRequestReviewCommandResultDto>> =>
    execute(() => apiClient.post(
        `${reviewPath(requestId, reviewId)}/overrides`,
        request,
        {headers: informationRequestCommandHeaders(requestId, {...options, accessLinkToken: undefined})},
    ));

export const recordInformationRequestReviewFinding = (
    requestId: string,
    reviewId: string,
    request: RecordInformationRequestReviewFindingRequest,
    idempotencyKey: string,
): Promise<RuntimeCommandResult<InformationRequestReviewCommandResultDto>> =>
    execute(() => apiClient.post(
        `${reviewPath(requestId, reviewId)}/findings`,
        request,
        {headers: keyedHeaders(requestId, idempotencyKey)},
    ));

export const recordInformationRequestReviewComment = (
    requestId: string,
    reviewId: string,
    request: RecordInformationRequestReviewCommentRequest,
    idempotencyKey: string,
    accessLinkToken?: string,
): Promise<RuntimeCommandResult<InformationRequestReviewCommandResultDto>> =>
    execute(() => apiClient.post(
        `${reviewPath(requestId, reviewId, accessLinkToken)}/comments`,
        request,
        {headers: keyedHeaders(requestId, idempotencyKey, accessLinkToken)},
    ));

export const appealInformationRequestReview = (
    requestId: string,
    reviewId: string,
    request: ReopenInformationRequestReviewRequest,
    options: RuntimeCommandOptions,
): Promise<RuntimeCommandResult<InformationRequestReviewCommandResultDto>> =>
    execute(() => apiClient.post(
        `${reviewPath(requestId, reviewId, options.accessLinkToken)}/appeals`,
        request,
        {headers: informationRequestCommandHeaders(requestId, options)},
    ));

export const reconsiderInformationRequestReview = (
    requestId: string,
    reviewId: string,
    request: ReopenInformationRequestReviewRequest,
    options: RuntimeCommandOptions,
): Promise<RuntimeCommandResult<InformationRequestReviewCommandResultDto>> =>
    execute(() => apiClient.post(
        `${reviewPath(requestId, reviewId)}/reconsiderations`,
        request,
        {headers: informationRequestCommandHeaders(requestId, {...options, accessLinkToken: undefined})},
    ));

export type InformationRequestReviewAssignmentChange = "recusal" | "delegation" | "revocation";

export const assignInformationRequestReviewer = (
    requestId: string,
    reviewId: string,
    request: AssignInformationRequestReviewerRequest,
    options: RuntimeCommandOptions,
): Promise<RuntimeCommandResult<InformationRequestReviewCommandResultDto>> =>
    execute(() => apiClient.post(
        `${reviewPath(requestId, reviewId)}/assignments`,
        request,
        {headers: informationRequestCommandHeaders(requestId, {...options, accessLinkToken: undefined})},
    ));

export const changeInformationRequestReviewAssignment = (
    requestId: string,
    reviewId: string,
    assignmentId: string,
    change: InformationRequestReviewAssignmentChange,
    request: ChangeInformationRequestReviewAssignmentRequest,
    options: RuntimeCommandOptions,
): Promise<RuntimeCommandResult<InformationRequestReviewCommandResultDto>> =>
    execute(() => apiClient.post(
        `${reviewPath(requestId, reviewId)}/assignments/${assignmentId}/${change}`,
        request,
        {headers: informationRequestCommandHeaders(requestId, {...options, accessLinkToken: undefined})},
    ));
