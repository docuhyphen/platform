import {ReactNode} from "react";
import {
    CreateInformationRequestGroupOccurrenceRequest,
    InformationRequestConditionEvaluationDto,
    InformationRequestDto,
    InformationRequestGroupOccurrenceDto,
    InformationRequestResponseDto,
    InformationRequestSupportingEvidenceLinkDto,
    InformationRequestTemplateGroupDto,
    InformationRequestTemplateRequirementDto,
    PatchInformationRequestResponsesRequest,
    ReorderInformationRequestGroupOccurrencesRequest,
    SchemaFieldBindingDto,
} from "../../models/models.tsx";
import {OccurrencePresentation, ResponseEdits} from "./structuredResponseWorkspaceState.ts";
import {ResponseAnswerEdits} from "./responseAnswerState.ts";

export type SaveResponsesResult =
    | { outcome: "SAVED"; responseETag: string; responses: InformationRequestResponseDto[] }
    | { outcome: "STALE" };

export type ResponseETagResult =
    | { outcome: "SAVED"; responseETag: string }
    | { outcome: "STALE" };

export type OccurrenceCommandResult =
    | { outcome: "SAVED"; responseETag: string; occurrences: InformationRequestGroupOccurrenceDto[] }
    | { outcome: "STALE" };

export interface StructuredResponseWorkspaceProps
{
    request: InformationRequestDto;
    respondentKey: string;
    responseETag: string;
    groups: InformationRequestTemplateGroupDto[];
    creatableGroupKeys?: string[];
    occurrences: InformationRequestGroupOccurrenceDto[];
    requirements: InformationRequestTemplateRequirementDto[];
    bindings: SchemaFieldBindingDto[];
    responses: InformationRequestResponseDto[];
    supportingEvidenceLinks?: InformationRequestSupportingEvidenceLinkDto[];
    readOnly?: boolean;
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
    onRefresh: () => void | Promise<void>;
}

export interface StructuredResponseOccurrenceProps
{
    occurrence: InformationRequestGroupOccurrenceDto;
    title: string;
    presentation: OccurrencePresentation;
    occurrenceIndex: number;
    occurrenceCount: number;
    siblingOccurrenceIds: string[];
    removable: boolean;
    fullChildGroupKeys: string[];
    nested?: ReactNode;
    requestId: string;
    responseETag: string;
    busy: boolean;
    readOnly: boolean;
    requirements: InformationRequestTemplateRequirementDto[];
    bindings: SchemaFieldBindingDto[];
    responses: InformationRequestResponseDto[];
    conditionByScope: Map<string, InformationRequestConditionEvaluationDto>;
    edits: ResponseEdits;
    setEdits: (edits: (previous: ResponseEdits) => ResponseEdits) => void;
    answers: ResponseAnswerEdits;
    setAnswers: (answers: (previous: ResponseAnswerEdits) => ResponseAnswerEdits) => void;
    onAdd: (
        requestId: string,
        groupKey: string,
        parentOccurrenceId: string | null | undefined,
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
        parentOccurrenceId: string | null | undefined,
        occurrenceIds: string[],
        responseETag: string,
    ) => Promise<OccurrenceCommandResult>;
    onResult: (result: ResponseETagResult) => void;
    onCommandStart: () => void;
    onCommandFailure: (error: unknown) => void;
}
