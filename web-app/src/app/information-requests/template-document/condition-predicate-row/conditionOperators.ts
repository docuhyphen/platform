import {
    FieldOperator,
    FieldValueType,
    InformationRequestTemplateConditionPredicateRequest,
} from "../../../models/models.tsx";
import {DISPOSITION_OPERATORS} from "../templatePlanValidation.ts";

const TEXT = [
    FieldOperator.EQUALS,
    FieldOperator.NOT_EQUALS,
    FieldOperator.CONTAINS,
    FieldOperator.STARTS_WITH,
    FieldOperator.IS_EMPTY,
    FieldOperator.IS_NOT_EMPTY,
];
const BOOLEAN = [FieldOperator.EQUALS, FieldOperator.NOT_EQUALS, FieldOperator.IS_EMPTY, FieldOperator.IS_NOT_EMPTY];
const ORDERED = [
    FieldOperator.EQUALS,
    FieldOperator.NOT_EQUALS,
    FieldOperator.LESS_THAN,
    FieldOperator.LESS_THAN_OR_EQUAL,
    FieldOperator.GREATER_THAN,
    FieldOperator.GREATER_THAN_OR_EQUAL,
    FieldOperator.IS_EMPTY,
    FieldOperator.IS_NOT_EMPTY,
];
const SINGLE_CHOICE = [
    FieldOperator.EQUALS,
    FieldOperator.NOT_EQUALS,
    FieldOperator.IN,
    FieldOperator.NOT_IN,
    FieldOperator.IS_EMPTY,
    FieldOperator.IS_NOT_EMPTY,
];
const MULTI_CHOICE = [
    FieldOperator.CONTAINS,
    FieldOperator.IN,
    FieldOperator.NOT_IN,
    FieldOperator.IS_EMPTY,
    FieldOperator.IS_NOT_EMPTY,
];

const OPERATORS_BY_TYPE: Record<FieldValueType, FieldOperator[]> = {
    [FieldValueType.SHORT_TEXT]: TEXT,
    [FieldValueType.LONG_TEXT]: TEXT,
    [FieldValueType.BOOLEAN]: BOOLEAN,
    [FieldValueType.INTEGER]: ORDERED,
    [FieldValueType.DECIMAL]: ORDERED,
    [FieldValueType.DATE]: ORDERED,
    [FieldValueType.DATE_TIME]: ORDERED,
    [FieldValueType.SINGLE_SELECT]: SINGLE_CHOICE,
    [FieldValueType.MULTI_SELECT]: MULTI_CHOICE,
};

export const operatorsFor = (valueType?: FieldValueType): FieldOperator[] =>
    valueType ? OPERATORS_BY_TYPE[valueType] : DISPOSITION_OPERATORS;

export const isListOperator = (operator: FieldOperator): boolean =>
    operator === FieldOperator.IN || operator === FieldOperator.NOT_IN;

export const literalFrom = (raw: string, valueType: FieldValueType | undefined, operator: FieldOperator): unknown =>
{
    if (raw.trim() === "") return undefined;
    if (isListOperator(operator)) return raw.split(",").map(item => item.trim()).filter(Boolean);
    if (valueType === FieldValueType.BOOLEAN) return raw === "true";
    return raw.trim();
};

export const literalText = (value: unknown): string =>
{
    if (value === undefined || value === null) return "";
    if (Array.isArray(value)) return value.join(", ");
    return String(value);
};

export const sourceValueOf = (predicate: InformationRequestTemplateConditionPredicateRequest): string =>
    predicate.sourceRequirementKey
        ? `requirement:${predicate.sourceRequirementKey}`
        : predicate.fieldDefinitionId ? `field:${predicate.fieldDefinitionId}` : "";
