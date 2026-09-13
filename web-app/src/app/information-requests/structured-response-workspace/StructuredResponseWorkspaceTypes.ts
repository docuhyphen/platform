import {
    CreateInformationRequestGroupOccurrenceRequest,
    InformationRequestDto,
    InformationRequestGroupOccurrenceDto,
    InformationRequestResponseDto,
    InformationRequestTemplateGroupDto,
    InformationRequestTemplateRequirementDto,
    PatchInformationRequestResponsesRequest,
    ReorderInformationRequestGroupOccurrencesRequest,
    SchemaFieldBindingDto,
} from "../../models/models.tsx";

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
