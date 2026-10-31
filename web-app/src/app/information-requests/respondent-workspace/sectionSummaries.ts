import {
    InformationRequestRequiredness,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestResponseDto,
    InformationRequestTemplateRequirementDto,
    InformationRequestTemplateSectionDto,
} from "../../models/models.tsx";
import {documentAnswered} from "../shared/documentAnswerStanding.ts";

export interface SectionSummary
{
    id: string;
    title: string;
    answered: number;
    required: number;
    confirmations: number;
    occurrencePath: string;
    requirementKey: string;
    requirementId?: string;
}

const ROOT_PATH = "root";

const isConfirmation = (requirement: InformationRequestTemplateRequirementDto): boolean =>
    requirement.requirementType === InformationRequestRequirementType.RESPONSE_ATTESTATION;

const completesWithoutValue = (disposition: InformationRequestResponseDisposition): boolean =>
    disposition !== InformationRequestResponseDisposition.NOT_ANSWERED &&
    disposition !== InformationRequestResponseDisposition.PROVIDED;

const isAnswered = (response: InformationRequestResponseDto, requirement: InformationRequestTemplateRequirementDto | undefined): boolean => {
    if (requirement?.requirementType === InformationRequestRequirementType.DOCUMENT) return documentAnswered(response);
    if (requirement?.collectedFieldDefinitionId) {
        return response.fieldValues.some(value => !value.isEmpty) || completesWithoutValue(response.disposition);
    }
    return response.disposition !== InformationRequestResponseDisposition.NOT_ANSWERED;
};

export const sectionSummaries = (sections: InformationRequestTemplateSectionDto[], responses: InformationRequestResponseDto[]): SectionSummary[] =>
    sections.filter(section => section.requirements.length > 0).map(section =>
    {
        const required = new Map(section.requirements
            .filter(requirement => requirement.requiredness !== InformationRequestRequiredness.OPTIONAL && !isConfirmation(requirement))
            .map(requirement => [requirement.id, requirement] as const));
        const confirmations = new Set(section.requirements.filter(isConfirmation).map(requirement => requirement.id));
        const requiredResponses = responses.filter(response => required.has(response.sourceTemplateBindingId));
        const first = section.requirements
            .map(requirement => ({requirement, response: responses.find(response => response.sourceTemplateBindingId === requirement.id)}))
            .find(candidate => candidate.response) ?? {requirement: section.requirements[0], response: undefined};
        return {
            id: section.id,
            title: section.title,
            answered: requiredResponses.filter(response => isAnswered(response, required.get(response.sourceTemplateBindingId))).length,
            required: requiredResponses.length,
            confirmations: responses.filter(response => confirmations.has(response.sourceTemplateBindingId)).length,
            occurrencePath: first.response?.occurrencePath ?? ROOT_PATH,
            requirementKey: first.requirement.requirementKey,
            requirementId: first.response?.informationRequestRequirementId,
        };
    });
