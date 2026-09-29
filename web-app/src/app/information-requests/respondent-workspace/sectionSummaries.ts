import {
    InformationRequestRequiredness,
    InformationRequestResponseDisposition,
    InformationRequestResponseDto,
    InformationRequestTemplateSectionDto,
} from "../../models/models.tsx";

export interface SectionSummary
{
    id: string;
    title: string;
    answered: number;
    required: number;
    occurrencePath: string;
    requirementKey: string;
}

const ROOT_PATH = "root";

export const sectionSummaries = (sections: InformationRequestTemplateSectionDto[], responses: InformationRequestResponseDto[]): SectionSummary[] =>
    sections.filter(section => section.requirements.length > 0).map(section =>
    {
        const requiredIds = new Set(section.requirements
            .filter(requirement => requirement.requiredness !== InformationRequestRequiredness.OPTIONAL)
            .map(requirement => requirement.id));
        const requiredResponses = responses.filter(response => requiredIds.has(response.sourceTemplateBindingId));
        const first = section.requirements
            .map(requirement => ({requirement, response: responses.find(response => response.sourceTemplateBindingId === requirement.id)}))
            .find(candidate => candidate.response) ?? {requirement: section.requirements[0], response: undefined};
        return {
            id: section.id,
            title: section.title,
            answered: requiredResponses.filter(response => response.disposition !== InformationRequestResponseDisposition.NOT_ANSWERED).length,
            required: requiredResponses.length,
            occurrencePath: first.response?.occurrencePath ?? ROOT_PATH,
            requirementKey: first.requirement.requirementKey,
        };
    });
