import {InformationRequestResponseDto, InformationRequestTemplateRequirementDto} from "../../../models/models.tsx";
import {labeledOccurrence} from "../submissionReview.ts";

export const requirementLabelsOf = (
    responses: InformationRequestResponseDto[],
    requirements: InformationRequestTemplateRequirementDto[],
): Record<string, string> =>
{
    const prompts = new Map(requirements.map(requirement => [requirement.id, requirement.prompt]));
    return Object.fromEntries(responses.map(response => [
        response.informationRequestRequirementId,
        labeledOccurrence(prompts.get(response.sourceTemplateBindingId) ?? "Requested item", response.occurrencePath),
    ]));
};
