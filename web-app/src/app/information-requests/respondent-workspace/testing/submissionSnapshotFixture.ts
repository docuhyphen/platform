import {
    InformationRequestResponseWorkspaceDto,
    InformationRequestSubmissionScopeItemDto
} from "../../../models/models.tsx";

export const submissionSnapshotItems = (
    workspace: InformationRequestResponseWorkspaceDto,
    stageKey?: string,
): InformationRequestSubmissionScopeItemDto[] => workspace.responses.flatMap(response => {
    const section = workspace.templateVersion.sections.find(candidate => candidate.requirements.some(requirement => requirement.id === response.sourceTemplateBindingId));
    const requirement = section?.requirements.find(candidate => candidate.id === response.sourceTemplateBindingId);
    if (!requirement || (stageKey && section?.submissionStageKey !== stageKey)) return [];
    const value = response.fieldValues[0];
    return [{
        requirementId: response.informationRequestRequirementId,
        templateBindingId: requirement.id,
        requirementKey: requirement.requirementKey,
        requirementType: requirement.requirementType,
        prompt: requirement.prompt,
        occurrencePath: response.occurrencePath,
        disposition: response.disposition,
        narrative: response.narrative,
        fieldContractId: value?.fieldContractId,
        fieldValueType: value?.valueType,
        fieldValue: value?.value,
        fieldValueCleared: value?.isEmpty ?? false,
        evidenceFileCount: response.evidenceFileCount ?? 0,
    }];
});