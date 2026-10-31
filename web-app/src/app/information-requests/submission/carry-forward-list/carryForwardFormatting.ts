import {formatFieldValue} from "../../../exchanges/components/exchange-fields-tab/fieldValueUtils.ts";
import {
    FieldOption,
    FieldValueType,
    InformationRequestCarryForwardDto,
    InformationRequestResponseDisposition,
    InformationRequestResponseDto,
    InformationRequestTemplateRequirementDto,
    SchemaFieldBindingDto,
} from "../../../models/models.tsx";
import {dispositionLabels} from "../../shared/informationRequestLabels.ts";

export interface RequirementValueFormat
{
    valueType: FieldValueType;
    options: FieldOption[];
}

const PLAIN_DISPOSITIONS = new Set([
    InformationRequestResponseDisposition.PROVIDED,
    InformationRequestResponseDisposition.NOT_ANSWERED,
]);

export const requirementValueFormats = (
    responses: InformationRequestResponseDto[],
    requirements: InformationRequestTemplateRequirementDto[],
    bindings: SchemaFieldBindingDto[],
): Record<string, RequirementValueFormat> =>
{
    const collectedFieldIds = new Map(requirements.map(requirement =>
        [requirement.id, requirement.collectedFieldDefinitionId ?? null]));
    const bindingsByField = new Map(bindings.map(binding => [binding.fieldDefinitionId, binding]));
    return Object.fromEntries(responses.flatMap(response =>
    {
        const binding = bindingsByField.get(collectedFieldIds.get(response.sourceTemplateBindingId) ?? "");
        if (!binding) return [];
        const entry: [string, RequirementValueFormat] =
            [response.informationRequestRequirementId, {valueType: binding.valueType, options: binding.options}];
        return [entry];
    }));
};

const plainText = (value: unknown): string =>
{
    if (typeof value === "string") return value;
    if (typeof value === "boolean") return value ? "Yes" : "No";
    if (typeof value === "number") return String(value);
    if (Array.isArray(value)) return value.map(plainText).join(", ");
    return JSON.stringify(value);
};

const withDisposition = (disposition: InformationRequestResponseDisposition | null, text: string): string =>
    disposition && !PLAIN_DISPOSITIONS.has(disposition) ? `${dispositionLabels[disposition]}: ${text}` : text;

export const priorAnswerText = (
    carryForward: InformationRequestCarryForwardDto,
    format?: RequirementValueFormat,
): string =>
{
    const disposition = carryForward.priorDisposition ?? null;
    const value = carryForward.priorFieldValue;
    if (value !== undefined && value !== null)
    {
        return withDisposition(
            disposition,
            format ? formatFieldValue(format.valueType, value, format.options) : plainText(value),
        );
    }
    if (carryForward.priorNarrative) return withDisposition(disposition, carryForward.priorNarrative);
    return disposition ? dispositionLabels[disposition] : "Answered earlier";
};
