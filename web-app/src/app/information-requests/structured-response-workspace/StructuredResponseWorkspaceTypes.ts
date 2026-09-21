import {
    CreateInformationRequestGroupOccurrenceRequest,
    InformationRequestConditionEvaluationDto,
    InformationRequestDto,
    InformationRequestGroupOccurrenceDto,
    InformationRequestResponseDto,
    InformationRequestTemplateGroupDto,
    InformationRequestTemplateConditionRuleDto,
    InformationRequestTemplateRequirementDto,
    PatchInformationRequestResponsesRequest,
    ReorderInformationRequestGroupOccurrencesRequest,
    SchemaFieldBindingDto,
} from "../../models/models.tsx";
import {ResponseEdits} from "./structuredResponseWorkspaceState.ts";

export type SaveResponsesResult =
    | { outcome: "SAVED"; responseETag: string; responses: InformationRequestResponseDto[] }
    | { outcome: "STALE" };

export type OccurrenceCommandResult =
    | { outcome: "SAVED"; responseETag: string; occurrences: InformationRequestGroupOccurrenceDto[] }
    | { outcome: "STALE" };

export interface StructuredResponseWorkspaceProps
{
    request: InformationRequestDto;
    responseETag: string;
    enabled: boolean;
    groups: InformationRequestTemplateGroupDto[];
    conditionRules?: InformationRequestTemplateConditionRuleDto[];
    occurrences: InformationRequestGroupOccurrenceDto[];
    requirements: InformationRequestTemplateRequirementDto[];
    bindings: SchemaFieldBindingDto[];
    responses: InformationRequestResponseDto[];
    onSaveResponses: (
        requestId: string,
        request: PatchInformationRequestResponsesRequest,
        responseETag: string,
    ) => Promise<SaveResponsesResult>;
    onAddOccurrence: (
        requestId: string,
        request: CreateInformationRequestGroupOccurrenceRequest,
        responseETag: string,
    ) => Promise<OccurrenceCommandResult>;
    onRemoveOccurrence: (
        requestId: string,
        occurrenceId: string,
        responseETag: string,
    ) => Promise<OccurrenceCommandResult>;
    onReorderOccurrences: (
        requestId: string,
        request: ReorderInformationRequestGroupOccurrencesRequest,
        responseETag: string,
    ) => Promise<OccurrenceCommandResult>;
    onRefresh: () => void;
}

export interface StructuredResponseOccurrenceProps
{
    occurrence: InformationRequestGroupOccurrenceDto;
    occurrenceIndex: number;
    occurrenceCount: number;
    siblingOccurrenceIds: string[];
    requestId: string;
    responseETag: string;
    busy: boolean;
    groups: InformationRequestTemplateGroupDto[];
    requirements: InformationRequestTemplateRequirementDto[];
    bindings: SchemaFieldBindingDto[];
    responses: InformationRequestResponseDto[];
    conditionByScope: Map<string, InformationRequestConditionEvaluationDto>;
    edits: ResponseEdits;
    setEdits: (edits: (previous: ResponseEdits) => ResponseEdits) => void;
    onAdd: (
        requestId: string,
        groupKey: string,
        parentOccurrenceId: string | undefined,
        responseETag: string,
    ) => Promise<OccurrenceCommandResult>;
    onRemove: (
        requestId: string,
        occurrenceId: string,
        responseETag: string,
    ) => Promise<OccurrenceCommandResult>;
    onReorder: (
        requestId: string,
        groupKey: string,
        parentOccurrenceId: string | undefined,
        occurrenceIds: string[],
        responseETag: string,
    ) => Promise<OccurrenceCommandResult>;
    onResult: (result: OccurrenceCommandResult) => void;
    onCommandStart: () => void;
    onCommandFailure: (error: unknown) => void;
}
