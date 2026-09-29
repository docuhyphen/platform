import {formatFieldValue} from "../../exchanges/components/exchange-fields-tab/fieldValueUtils.ts";
import {toFieldElementId} from "../../exchanges/components/exchange-fields-tab/fieldLayoutUtils.ts";
import {
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestResponseDto,
    InformationRequestTemplateRequirementDto,
} from "../../models/models.tsx";
import {dispositionLabels} from "../shared/informationRequestLabels.ts";

export interface SubmissionReviewItem
{
    requirementId: string;
    label: string;
    answer: string;
}

const VALUE_DISPOSITIONS = new Set([InformationRequestResponseDisposition.PROVIDED, InformationRequestResponseDisposition.PARTIALLY_PROVIDED]);

const answerText = (response: InformationRequestResponseDto, requirement?: InformationRequestTemplateRequirementDto): string =>
{
    if (response.disposition === InformationRequestResponseDisposition.NOT_ANSWERED) return "Not answered yet";
    if (!VALUE_DISPOSITIONS.has(response.disposition))
    {
        const label = dispositionLabels[response.disposition];
        return response.narrative ? `${label}: ${response.narrative}` : label;
    }
    if (requirement?.requirementType === InformationRequestRequirementType.DOCUMENT) return "Files attached";
    const values = response.fieldValues.filter(value => !value.isEmpty);
    return values.length === 0
        ? dispositionLabels[response.disposition]
        : values.map(value => formatFieldValue(value.valueType, value.value, [])).join(", ");
};

export const submissionReviewItems = (
    responses: InformationRequestResponseDto[],
    requirements: InformationRequestTemplateRequirementDto[],
): SubmissionReviewItem[] =>
{
    const byBinding = new Map(requirements.map(requirement => [requirement.id, requirement]));
    return responses
        .filter(response => byBinding.get(response.sourceTemplateBindingId)?.requirementType !== InformationRequestRequirementType.RESPONSE_ATTESTATION)
        .map(response =>
        {
            const requirement = byBinding.get(response.sourceTemplateBindingId);
            return {
                requirementId: response.informationRequestRequirementId,
                label: requirement?.prompt ?? "Requested item",
                answer: answerText(response, requirement),
            };
        });
};

export const requirementElementId = (occurrencePath: string, requirementKey: string): string =>
    `information-request-response-requirement-${toFieldElementId(`${occurrencePath}-${requirementKey}`)}`;

export const focusRequirement = (occurrencePath: string, requirementKey: string) =>
{
    const target = document.getElementById(requirementElementId(occurrencePath, requirementKey));
    if (!target) return;
    if (!target.hasAttribute("tabindex")) target.tabIndex = -1;
    target.scrollIntoView({behavior: "smooth", block: "center"});
    target.focus();
};
