import {
    FieldScopeKind,
    InformationRequestRequirementType,
    InformationRequestTemplateConfigurationRequest,
    InformationRequestTemplateScopeKind,
} from "../../models/models.tsx";

export interface DraftSaveRequest
{
    schemaVersionId: string;
    sectionKey: string;
    sectionTitle: string;
    requirementKey: string;
    prompt: string;
    collectedFieldDefinitionId: string;
}

export const fieldScopeFor = (scope: InformationRequestTemplateScopeKind): FieldScopeKind =>
{
    if (scope === InformationRequestTemplateScopeKind.ORGANIZATION) return FieldScopeKind.ORGANIZATION;
    if (scope === InformationRequestTemplateScopeKind.PLATFORM) return FieldScopeKind.PLATFORM;
    return FieldScopeKind.PERSONAL;
};

export const informationRequestTemplateErrorText = (error: unknown): string =>
    typeof error === "string" ? error : "Information Request Template action failed";

export const toConfigurationRequest = (draft: DraftSaveRequest): InformationRequestTemplateConfigurationRequest => ({
    schemaVersionId: draft.schemaVersionId,
    sections: [{
        sectionKey: draft.sectionKey,
        title: draft.sectionTitle,
        requirements: [{
            requirementKey: draft.requirementKey,
            requirementType: InformationRequestRequirementType.FIELD,
            prompt: draft.prompt,
            collectedFieldDefinitionId: draft.collectedFieldDefinitionId,
        }],
    }],
});
