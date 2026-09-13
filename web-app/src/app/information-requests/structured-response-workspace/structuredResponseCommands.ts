import {
    CreateInformationRequestGroupOccurrenceRequest,
    InformationRequestGroupOccurrenceDto,
    InformationRequestResponseDto,
    PatchInformationRequestResponsesRequest,
    ReorderInformationRequestGroupOccurrencesRequest,
} from "../../models/models.tsx";
import {
    addInformationRequestGroupOccurrence,
    patchInformationRequestResponses,
    removeInformationRequestGroupOccurrence,
    reorderInformationRequestGroupOccurrences,
    RuntimeCommandOptions,
    RuntimeCommandResult,
} from "../../../services/informationRequestRuntimeService.ts";
import {OccurrenceCommandResult, SaveResponsesResult} from "./StructuredResponseWorkspaceTypes.ts";

export interface StructuredResponseCommandOptions
{
    accessLinkToken?: string;
    newIdempotencyKey?: () => string;
}

export interface StructuredResponseCommands
{
    saveResponses: (
        requestId: string,
        request: PatchInformationRequestResponsesRequest,
        responseETag: string,
    ) => Promise<SaveResponsesResult>;
    addOccurrence: (
        requestId: string,
        request: CreateInformationRequestGroupOccurrenceRequest,
        responseETag: string,
    ) => Promise<OccurrenceCommandResult>;
    removeOccurrence: (
        requestId: string,
        occurrenceId: string,
        responseETag: string,
    ) => Promise<OccurrenceCommandResult>;
    reorderOccurrences: (
        requestId: string,
        request: ReorderInformationRequestGroupOccurrencesRequest,
        responseETag: string,
    ) => Promise<OccurrenceCommandResult>;
}

const toSaveResult = (
    result: RuntimeCommandResult<InformationRequestResponseDto[]>,
): SaveResponsesResult =>
    result.outcome === "STALE"
        ? result
        : {outcome: "SAVED", responseETag: result.responseETag, responses: result.data};

const toOccurrenceResult = (
    result: RuntimeCommandResult<InformationRequestGroupOccurrenceDto[]>,
): OccurrenceCommandResult =>
    result.outcome === "STALE"
        ? result
        : {outcome: "SAVED", responseETag: result.responseETag, occurrences: result.data};

export const structuredResponseCommands = (
    options: StructuredResponseCommandOptions = {},
): StructuredResponseCommands =>
{
    const nextIdempotencyKey = options.newIdempotencyKey ?? (() => crypto.randomUUID());

    const commandOptions = (responseETag: string): RuntimeCommandOptions => ({
        expectedETag: responseETag,
        idempotencyKey: nextIdempotencyKey(),
        accessLinkToken: options.accessLinkToken,
    });

    return {
        saveResponses: async (requestId, request, responseETag) =>
            toSaveResult(await patchInformationRequestResponses(
                requestId,
                request,
                commandOptions(responseETag),
            )),
        addOccurrence: async (requestId, request, responseETag) =>
            toOccurrenceResult(await addInformationRequestGroupOccurrence(
                requestId,
                request,
                commandOptions(responseETag),
            )),
        removeOccurrence: async (requestId, occurrenceId, responseETag) =>
            toOccurrenceResult(await removeInformationRequestGroupOccurrence(
                requestId,
                occurrenceId,
                commandOptions(responseETag),
            )),
        reorderOccurrences: async (requestId, request, responseETag) =>
            toOccurrenceResult(await reorderInformationRequestGroupOccurrences(
                requestId,
                request,
                commandOptions(responseETag),
            )),
    };
};
