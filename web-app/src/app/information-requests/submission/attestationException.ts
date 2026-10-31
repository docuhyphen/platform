import {
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestResponseDto,
    InformationRequestTemplateRequirementDto,
} from "../../models/models.tsx";

export interface AttestationException
{
    disposition: InformationRequestResponseDisposition;
    reason: string | null;
}

export interface AttestationExceptionSettings
{
    permitted: InformationRequestResponseDisposition[];
    current: AttestationException | null;
}

export interface AttestationExceptionInput
{
    requirementId: string;
    disposition: InformationRequestResponseDisposition;
    reason: string;
}

const ANSWERS = new Set([
    InformationRequestResponseDisposition.NOT_ANSWERED,
    InformationRequestResponseDisposition.PROVIDED,
    InformationRequestResponseDisposition.PARTIALLY_PROVIDED,
]);

export const attestationExceptionsOf = (
    requirements: InformationRequestTemplateRequirementDto[],
    responses: InformationRequestResponseDto[],
): Record<string, AttestationExceptionSettings> =>
{
    const confirmations = new Map(requirements
        .filter(requirement => requirement.requirementType === InformationRequestRequirementType.RESPONSE_ATTESTATION)
        .map(requirement => [requirement.id, requirement.permittedDispositions.filter(disposition => !ANSWERS.has(disposition))]));
    const settings: Record<string, AttestationExceptionSettings> = {};
    responses.forEach(response =>
    {
        const permitted = confirmations.get(response.sourceTemplateBindingId);
        if (!permitted || permitted.length === 0) return;
        settings[response.informationRequestRequirementId] = {
            permitted,
            current: ANSWERS.has(response.disposition)
                ? null
                : {disposition: response.disposition, reason: response.narrative ?? null},
        };
    });
    return settings;
};
