import {FieldOption, FieldValueType, InformationRequestAcceptedFactConfidence} from "../../models/models.tsx";
import {formatInformationRequestTime} from "../shared/informationRequestFormatting.ts";

export const confidenceLabels: Record<InformationRequestAcceptedFactConfidence, string> = {
    [InformationRequestAcceptedFactConfidence.DECLARED]: "Given in an earlier response",
    [InformationRequestAcceptedFactConfidence.REVIEWED]: "Accepted by a reviewer",
};

const plainText = (value: unknown): string =>
{
    if (value === null || value === undefined) return "";
    if (typeof value === "string") return value;
    if (typeof value === "boolean") return value ? "Yes" : "No";
    if (typeof value === "number") return String(value);
    if (Array.isArray(value)) return value.map(plainText).join(", ");
    return JSON.stringify(value);
};

const optionText = (value: unknown, options: FieldOption[]): string =>
    options.find(option => option.code === value)?.label ?? plainText(value);

const timeText = (value: unknown): string => {
    const instant = typeof value === "string" ? Date.parse(value) : Number.NaN;
    return Number.isNaN(instant) ? plainText(value) : formatInformationRequestTime(instant);
};

export const reusableValueText = (valueType: FieldValueType, value: unknown, options: FieldOption[] = []): string => {
    switch (valueType) {
        case FieldValueType.SINGLE_SELECT:
            return optionText(value, options);
        case FieldValueType.MULTI_SELECT:
            return Array.isArray(value) ? value.map(item => optionText(item, options)).join(", ") : optionText(value, options);
        case FieldValueType.DATE_TIME:
            return timeText(value);
        default:
            return plainText(value);
    }
};
