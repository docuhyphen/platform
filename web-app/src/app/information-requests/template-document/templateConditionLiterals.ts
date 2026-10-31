import {FieldValueType} from "../../models/models.tsx";

const NUMBER = /^[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?$/;
const ISO_DATE = /^(\d{4})-(\d{2})-(\d{2})$/;

const isCalendarDate = (text: string): boolean => {
    const parts = ISO_DATE.exec(text);
    if (!parts) return false;
    const [year, month, day] = [Number(parts[1]), Number(parts[2]), Number(parts[3])];
    const date = new Date(Date.UTC(year, month - 1, day));
    return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day;
};

const literalText = (value: unknown): string =>
    typeof value === "string" || typeof value === "number" || typeof value === "boolean" ? String(value).trim() : "";

const misread = (ruleKey: string, kind: string, value: unknown, expected: string): string =>
    `Condition "${ruleKey}" compares a ${kind} Field with "${literalText(value)}", which is not ${expected}.`;

const optionProblem = (ruleKey: string, value: unknown, offered: ReadonlySet<string> | undefined): string | null => {
    if (!offered) return null;
    const codes = Array.isArray(value) ? value.map(literalText) : [literalText(value)];
    const unknown = codes.find(code => code && !offered.has(code));
    return unknown === undefined ? null : `Condition "${ruleKey}" compares with "${unknown}", which is not an option of its Field.`;
};

export const conditionLiteralProblem = (
    ruleKey: string,
    valueType: FieldValueType,
    value: unknown,
    offered: ReadonlySet<string> | undefined,
): string | null => {
    const text = literalText(value);
    switch (valueType) {
        case FieldValueType.INTEGER:
            return NUMBER.test(text) && Number.isInteger(Number(text)) ? null : misread(ruleKey, "whole number", value, "a whole number");
        case FieldValueType.DECIMAL:
            return NUMBER.test(text) ? null : misread(ruleKey, "number", value, "a number");
        case FieldValueType.DATE:
            return isCalendarDate(text) ? null : misread(ruleKey, "date", value, "a date such as 2026-01-31");
        case FieldValueType.BOOLEAN:
            return value === true || value === false || text === "true" || text === "false"
                ? null
                : misread(ruleKey, "yes or no", value, "true or false");
        case FieldValueType.SINGLE_SELECT:
        case FieldValueType.MULTI_SELECT:
            return optionProblem(ruleKey, value, offered);
        default:
            return null;
    }
};
