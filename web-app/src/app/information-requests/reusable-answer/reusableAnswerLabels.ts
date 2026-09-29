import {InformationRequestAcceptedFactConfidence} from "../../models/models.tsx";

export const confidenceLabels: Record<InformationRequestAcceptedFactConfidence, string> = {
    [InformationRequestAcceptedFactConfidence.DECLARED]: "Given in an earlier response",
    [InformationRequestAcceptedFactConfidence.REVIEWED]: "Accepted by a reviewer",
};

export const reusableValueText = (value: unknown): string =>
{
    if (value === null || value === undefined) return "";
    if (typeof value === "string") return value;
    if (typeof value === "boolean") return value ? "Yes" : "No";
    if (typeof value === "number") return String(value);
    if (Array.isArray(value)) return value.map(reusableValueText).join(", ");
    return JSON.stringify(value);
};
