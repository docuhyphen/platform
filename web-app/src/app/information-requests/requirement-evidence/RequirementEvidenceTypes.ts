import {InformationRequestTemplateEvidencePolicyDto} from "../../models/models.tsx";

export interface RequirementEvidencePanelProps
{
    requestId: string;
    requirementId: string;
    prompt: string;
    required?: boolean;
    helpText?: string | null;
    elementId: string;
    locked?: boolean;
    evidencePolicy?: InformationRequestTemplateEvidencePolicyDto | null;
}
