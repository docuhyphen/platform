import {formatFieldValue} from "../../exchanges/components/exchange-fields-tab/fieldValueUtils.ts";
import {
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestResponseDto,
    InformationRequestSubmissionScopeItemDto,
    InformationRequestTemplateRequirementDto,
    InformationRequestTemplateSectionDto,
    SchemaFieldBindingDto,
} from "../../models/models.tsx";
import {attachedFiles, attachedFilesText} from "../shared/documentAnswerStanding.ts";
import {dispositionLabels} from "../shared/informationRequestLabels.ts";
import {occurrenceLabel} from "../structured-response-workspace/responseAnswerState.ts";
import {groupElementKey, occurrenceElementKey, requirementElementKey} from "../shared/responseElementKeys.ts";

export interface SubmissionReviewItem
{
    requirementId: string;
    label: string;
    answer: string;
}

export interface SubmissionReviewScope {
    sections: InformationRequestTemplateSectionDto[];
    bindings?: SchemaFieldBindingDto[];
    stageKey?: string | null;
}

interface PlacedRequirement {
    requirement: InformationRequestTemplateRequirementDto;
    stageKey: string | null;
}

const VALUE_DISPOSITIONS = new Set([InformationRequestResponseDisposition.PROVIDED, InformationRequestResponseDisposition.PARTIALLY_PROVIDED]);

const collectedBinding = (
    requirement: InformationRequestTemplateRequirementDto | undefined,
    bindings: SchemaFieldBindingDto[],
): SchemaFieldBindingDto | undefined =>
    requirement?.collectedFieldDefinitionId
        ? bindings.find(binding => binding.fieldDefinitionId === requirement.collectedFieldDefinitionId)
        : undefined;

const valueText = (
    response: InformationRequestResponseDto,
    requirement: InformationRequestTemplateRequirementDto | undefined,
    bindings: SchemaFieldBindingDto[],
): string | null =>
{
    if (requirement?.requirementType === InformationRequestRequirementType.DOCUMENT)
    {
        const files = attachedFiles(response);
        return files === 0 ? null : attachedFilesText(files);
    }
    const values = response.fieldValues.filter(value => !value.isEmpty);
    const collected = collectedBinding(requirement, bindings);
    return values.length === 0
        ? null
        : values.map(value => formatFieldValue(value.valueType, value.value, collected?.options ?? [])).join(", ");
};

const answerText = (
    disposition: InformationRequestResponseDisposition,
    narrative: string | null | undefined,
    value: string | null,
    documentFiles: number | null,
): string => {
    if (documentFiles !== null && [InformationRequestResponseDisposition.NOT_ANSWERED, InformationRequestResponseDisposition.PROVIDED].includes(disposition)) {
        if (documentFiles === 0) return disposition === InformationRequestResponseDisposition.NOT_ANSWERED ? "Not answered yet" : "No files attached yet";
        const attached = attachedFilesText(documentFiles);
        return narrative ? `${attached}. Note: ${narrative}` : attached;
    }
    if (disposition === InformationRequestResponseDisposition.NOT_ANSWERED) return "Not answered yet";
    const label = dispositionLabels[disposition];
    if (!VALUE_DISPOSITIONS.has(disposition)) return narrative ? `${label}: ${narrative}` : label;
    if (disposition === InformationRequestResponseDisposition.PARTIALLY_PROVIDED) {
        const partial = value ? `${label}: ${value}` : label;
        return narrative ? `${partial}. Reason: ${narrative}` : partial;
    }
    const provided = value ?? label;
    return narrative ? `${provided}. Note: ${narrative}` : provided;
};

export const responseAnswerText = (
    response: InformationRequestResponseDto,
    requirement: InformationRequestTemplateRequirementDto | undefined,
    bindings: SchemaFieldBindingDto[],
): string => answerText(response.disposition, response.narrative, valueText(response, requirement, bindings),
    requirement?.requirementType === InformationRequestRequirementType.DOCUMENT ? attachedFiles(response) : null);

export const previewReviewItems = (
    items: InformationRequestSubmissionScopeItemDto[],
    bindings: SchemaFieldBindingDto[],
): SubmissionReviewItem[] => items.map(item => {
    const files = item.requirementType === InformationRequestRequirementType.DOCUMENT ? item.evidenceFileCount : null;
    const options = bindings.find(binding => binding.fieldContractId === item.fieldContractId)?.options ?? [];
    const value = files !== null ? (files > 0 ? attachedFilesText(files) : null)
        : item.fieldValueType && !item.fieldValueCleared && item.fieldValue !== null && item.fieldValue !== undefined
            ? formatFieldValue(item.fieldValueType, item.fieldValue, options) : null;
    return {
        requirementId: item.requirementId, label: labeledOccurrence(item.prompt, item.occurrencePath),
        answer: answerText(item.disposition, item.narrative, value, files)
    };
});
const inScope = (placed: PlacedRequirement | undefined, stageKey?: string | null): boolean =>
    placed?.requirement.requirementType !== InformationRequestRequirementType.RESPONSE_ATTESTATION &&
    (!stageKey || placed?.stageKey === stageKey);

export const submissionReviewItems = (
    responses: InformationRequestResponseDto[],
    {sections, bindings = [], stageKey}: SubmissionReviewScope,
): SubmissionReviewItem[] =>
{
    const byBinding = new Map<string, PlacedRequirement>(sections.flatMap(section => section.requirements.map(requirement =>
        [requirement.id, {requirement, stageKey: section.submissionStageKey ?? null}] as const)));
    return responses
        .filter(response => inScope(byBinding.get(response.sourceTemplateBindingId), stageKey))
        .map(response =>
        {
            const requirement = byBinding.get(response.sourceTemplateBindingId)?.requirement;
            return {
                requirementId: response.informationRequestRequirementId,
                label: labeledOccurrence(requirement?.prompt ?? "Requested item", response.occurrencePath),
                answer: responseAnswerText(response, requirement, bindings),
            };
        });
};

export const requirementElementId = (occurrencePath: string, requirementKey: string): string =>
    `information-request-response-requirement-${requirementElementKey(occurrencePath, requirementKey)}`;

export const labeledOccurrence = (label: string, path: string): string => {
    const occurrence = occurrenceLabel(path);
    return occurrence ? `${label} (${occurrence})` : label;
};

const focusElement = (target: HTMLElement | null) =>
{
    if (!target) return;
    if (!target.hasAttribute("tabindex")) target.tabIndex = -1;
    target.scrollIntoView({behavior: "smooth", block: "center"});
    target.focus();
};

export const focusRequirement = (occurrencePath: string, requirementKey: string, requirementId?: string) =>
    focusElement(document.getElementById(requirementElementId(occurrencePath, requirementKey))
        ?? (requirementId ? document.getElementById(`information-request-attestation-${requirementId}`) : null));

export const focusGroup = (groupKey: string, parentPath?: string | null) => {
    const key = groupElementKey(groupKey);
    const parent = parentPath ? document.getElementById(`information-request-occurrence-${occurrenceElementKey(parentPath)}`) : null;
    focusElement(parent?.querySelector<HTMLButtonElement>(`button[id^="information-request-add-child-occurrence-"][id$="-${key}"]`)
        ?? document.getElementById(`information-request-add-occurrence-${key}`) ?? parent);
};