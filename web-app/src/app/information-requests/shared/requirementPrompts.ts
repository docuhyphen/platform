import {InformationRequestResponseWorkspaceDto} from "../../models/models.tsx";

export const requirementPromptsOf = (workspace: InformationRequestResponseWorkspaceDto): Record<string, string> =>
{
    const byBinding = new Map(workspace.templateVersion.sections
        .flatMap(section => section.requirements)
        .map(requirement => [requirement.id, requirement.prompt]));
    return Object.fromEntries(workspace.responses.flatMap(response =>
    {
        const prompt = byBinding.get(response.sourceTemplateBindingId);
        return prompt ? [[response.informationRequestRequirementId, prompt]] : [];
    }));
};
