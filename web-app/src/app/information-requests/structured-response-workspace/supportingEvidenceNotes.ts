import {createContext, useContext} from "react";
import {
    InformationRequestResponseDto,
    InformationRequestSupportingEvidenceLinkDto,
    InformationRequestTemplateRequirementDto,
} from "../../models/models.tsx";

export interface SupportingEvidenceNotes {
    supportedBy: string[];
    supports: string[];
}

const NO_NOTES: SupportingEvidenceNotes = {supportedBy: [], supports: []};

export const supportingEvidenceNotes = (
    links: InformationRequestSupportingEvidenceLinkDto[],
    requirements: InformationRequestTemplateRequirementDto[],
    responses: InformationRequestResponseDto[],
    labelOf: (occurrencePath: string) => string,
): Map<string, SupportingEvidenceNotes> => {
    const nameOf = (requirementId: string): string | null => {
        const response = responses.find(candidate => candidate.informationRequestRequirementId === requirementId);
        const prompt = response && requirements.find(candidate => candidate.id === response.sourceTemplateBindingId)?.prompt;
        if (!response || !prompt) return null;
        const entry = labelOf(response.occurrencePath);
        return entry ? `${prompt} (${entry})` : prompt;
    };
    const notes = new Map<string, SupportingEvidenceNotes>();
    const noteOf = (requirementId: string): SupportingEvidenceNotes => {
        const known = notes.get(requirementId);
        if (known) return known;
        const created: SupportingEvidenceNotes = {supportedBy: [], supports: []};
        notes.set(requirementId, created);
        return created;
    };
    links.forEach(link => {
        const supporting = nameOf(link.supportingRequirementId);
        const supported = nameOf(link.supportedRequirementId);
        if (!supporting || !supported) return;
        noteOf(link.supportedRequirementId).supportedBy.push(supporting);
        noteOf(link.supportingRequirementId).supports.push(supported);
    });
    return notes;
};

export const SupportingEvidenceContext = createContext<Map<string, SupportingEvidenceNotes>>(new Map());

export const useSupportingEvidenceNotes = (requirementId: string): SupportingEvidenceNotes =>
    useContext(SupportingEvidenceContext).get(requirementId) ?? NO_NOTES;
