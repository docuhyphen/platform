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
    scope === InformationRequestTemplateScopeKind.ORGANIZATION ? FieldScopeKind.ORGANIZATION : FieldScopeKind.PERSONAL;

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
