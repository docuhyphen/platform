import {InformationRequestResponseDisposition} from "../../models/models.tsx";

export interface ResponseAnswerEdit
{
    disposition?: InformationRequestResponseDisposition;
    narrative?: string;
}

export type ResponseAnswerEdits = Record<string, ResponseAnswerEdit>;

const ROOT_PATH = "root";

const VALUE_DISPOSITIONS = new Set([
    InformationRequestResponseDisposition.PROVIDED,
    InformationRequestResponseDisposition.PARTIALLY_PROVIDED,
]);

export const answersWithValue = (disposition: InformationRequestResponseDisposition): boolean => VALUE_DISPOSITIONS.has(disposition);

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
