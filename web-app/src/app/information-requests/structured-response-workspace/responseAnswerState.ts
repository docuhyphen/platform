import {
    InformationRequestResponseDisposition,
    InformationRequestResponseDto,
    InformationRequestTemplateRequirementDto,
} from "../../models/models.tsx";

export interface ResponseAnswerEdit
{
    disposition?: InformationRequestResponseDisposition;
    narrative?: string;
    reconfirmed?: boolean;
}

export type ResponseAnswerEdits = Record<string, ResponseAnswerEdit>;

const ROOT_PATH = "root";

const VALUE_DISPOSITIONS = new Set([
    InformationRequestResponseDisposition.PROVIDED,
    InformationRequestResponseDisposition.PARTIALLY_PROVIDED,
]);

const EXPLAINED_DISPOSITIONS = new Set([
    InformationRequestResponseDisposition.PARTIALLY_PROVIDED,
    InformationRequestResponseDisposition.NOT_APPLICABLE,
    InformationRequestResponseDisposition.UNAVAILABLE,
    InformationRequestResponseDisposition.EXCEPTION_REQUESTED,
    InformationRequestResponseDisposition.SATISFIED_BY_REFERENCE,
    InformationRequestResponseDisposition.WAIVED,
]);

export const answersWithValue = (disposition: InformationRequestResponseDisposition): boolean => VALUE_DISPOSITIONS.has(disposition);

export const asksForExplanation = (disposition: InformationRequestResponseDisposition): boolean => EXPLAINED_DISPOSITIONS.has(disposition);

export const currentDisposition = (
    requirement: InformationRequestTemplateRequirementDto,
    response: InformationRequestResponseDto | undefined,
    answer: ResponseAnswerEdit | undefined,
): InformationRequestResponseDisposition => {
    const permitted = requirement.permittedDispositions;
    if (answer?.disposition) return answer.disposition;
    if (response && permitted.includes(response.disposition)) return response.disposition;
    if (permitted.includes(InformationRequestResponseDisposition.PROVIDED)) return InformationRequestResponseDisposition.PROVIDED;
    return permitted.length > 0 ? permitted[0] : InformationRequestResponseDisposition.NOT_ANSWERED;
};

export const pruneSavedEdits = <T>(current: Record<string, T>, sent: Record<string, T>): Record<string, T> =>
    Object.fromEntries(Object.entries(current).filter(([key, value]) => sent[key] !== value));

const words = (key: string): string => key.replace(/[-_]+/g, " ").trim();

const capitalized = (text: string): string => text.charAt(0).toUpperCase() + text.slice(1);

export const groupLabel = (groupKey: string): string => capitalized(words(groupKey));

export const occurrenceLabel = (occurrencePath: string): string =>
{
    if (occurrencePath === ROOT_PATH) return "";
    const parts = occurrencePath.split("/").map(segment =>
    {
        const indexed = /^(.*)\[(\d+)]$/.exec(segment);
        return indexed ? `${words(indexed[1])} ${Number(indexed[2]) + 1}` : words(segment);
    });
    return capitalized(parts.join(", "));
};
