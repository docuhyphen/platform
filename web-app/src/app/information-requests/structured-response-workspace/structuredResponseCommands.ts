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
import {stableKeyedCommand} from "../shared/useStableIdempotencyKey.ts";
import {OccurrenceCommandResult, SaveResponsesResult} from "./StructuredResponseWorkspaceTypes.ts";

export interface StructuredResponseCommandOptions
{
    accessLinkToken?: string;
    newIdempotencyKey?: () => string;
    commandKeys?: Map<string, string>;
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
    const keyed = stableKeyedCommand(options.commandKeys ?? new Map(), options.newIdempotencyKey);

    const send = <T>(
        identity: string,
        body: unknown,
        responseETag: string,
        command: (commandOptions: RuntimeCommandOptions) => Promise<T>,
    ): Promise<T> =>
        keyed(identity, {body, responseETag}, idempotencyKey => command({
            expectedETag: responseETag,
            idempotencyKey,
            accessLinkToken: options.accessLinkToken,
        }));

    return {
        saveResponses: async (requestId, request, responseETag) =>
            toSaveResult(await send(`save-responses|${requestId}`, request, responseETag, commandOptions =>
                patchInformationRequestResponses(requestId, request, commandOptions))),
        addOccurrence: async (requestId, request, responseETag) =>
            toOccurrenceResult(await send(`add-occurrence|${requestId}`, request, responseETag, commandOptions =>
                addInformationRequestGroupOccurrence(requestId, request, commandOptions))),
        removeOccurrence: async (requestId, occurrenceId, responseETag) =>
            toOccurrenceResult(await send(`remove-occurrence|${requestId}`, occurrenceId, responseETag, commandOptions =>
                removeInformationRequestGroupOccurrence(requestId, occurrenceId, commandOptions))),
        reorderOccurrences: async (requestId, request, responseETag) =>
            toOccurrenceResult(await send(`reorder-occurrences|${requestId}`, request, responseETag, commandOptions =>
                reorderInformationRequestGroupOccurrences(requestId, request, commandOptions))),
    };
};
