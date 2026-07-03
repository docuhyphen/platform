import {FieldOperator, FieldValueType, SchemaFieldBindingDto, WorkflowFieldConditionDraft} from "../../../../models/models.tsx";

/** Operators each field type supports, mirroring the backend FieldTypeContract sets. */
const OPERATORS_BY_TYPE: Record<FieldValueType, FieldOperator[]> = {
    [FieldValueType.SHORT_TEXT]: [
        FieldOperator.EQUALS, FieldOperator.NOT_EQUALS, FieldOperator.CONTAINS,
        FieldOperator.STARTS_WITH, FieldOperator.IS_EMPTY, FieldOperator.IS_NOT_EMPTY,
    ],
    [FieldValueType.LONG_TEXT]: [
        FieldOperator.EQUALS, FieldOperator.NOT_EQUALS, FieldOperator.CONTAINS,
        FieldOperator.STARTS_WITH, FieldOperator.IS_EMPTY, FieldOperator.IS_NOT_EMPTY,
    ],
    [FieldValueType.BOOLEAN]: [
        FieldOperator.EQUALS, FieldOperator.NOT_EQUALS,
        FieldOperator.IS_EMPTY, FieldOperator.IS_NOT_EMPTY,
    ],
    [FieldValueType.INTEGER]: [
        FieldOperator.EQUALS, FieldOperator.NOT_EQUALS, FieldOperator.LESS_THAN,
        FieldOperator.LESS_THAN_OR_EQUAL, FieldOperator.GREATER_THAN,
        FieldOperator.GREATER_THAN_OR_EQUAL, FieldOperator.IS_EMPTY, FieldOperator.IS_NOT_EMPTY,
    ],
    [FieldValueType.DECIMAL]: [
        FieldOperator.EQUALS, FieldOperator.NOT_EQUALS, FieldOperator.LESS_THAN,
        FieldOperator.LESS_THAN_OR_EQUAL, FieldOperator.GREATER_THAN,
        FieldOperator.GREATER_THAN_OR_EQUAL, FieldOperator.IS_EMPTY, FieldOperator.IS_NOT_EMPTY,
    ],
    [FieldValueType.DATE]: [
        FieldOperator.EQUALS, FieldOperator.NOT_EQUALS, FieldOperator.LESS_THAN,
        FieldOperator.LESS_THAN_OR_EQUAL, FieldOperator.GREATER_THAN,
        FieldOperator.GREATER_THAN_OR_EQUAL, FieldOperator.IS_EMPTY, FieldOperator.IS_NOT_EMPTY,
    ],
    [FieldValueType.DATE_TIME]: [
        FieldOperator.EQUALS, FieldOperator.NOT_EQUALS, FieldOperator.LESS_THAN,
        FieldOperator.LESS_THAN_OR_EQUAL, FieldOperator.GREATER_THAN,
        FieldOperator.GREATER_THAN_OR_EQUAL, FieldOperator.IS_EMPTY, FieldOperator.IS_NOT_EMPTY,
    ],
    [FieldValueType.SINGLE_SELECT]: [
        FieldOperator.EQUALS, FieldOperator.NOT_EQUALS, FieldOperator.IN,
        FieldOperator.NOT_IN, FieldOperator.IS_EMPTY, FieldOperator.IS_NOT_EMPTY,
    ],
    [FieldValueType.MULTI_SELECT]: [
        FieldOperator.CONTAINS, FieldOperator.IN, FieldOperator.NOT_IN,
        FieldOperator.IS_EMPTY, FieldOperator.IS_NOT_EMPTY,
    ],
};

const OPERATOR_LABELS: Record<FieldOperator, string> = {
    [FieldOperator.EQUALS]: "equals",
    [FieldOperator.NOT_EQUALS]: "does not equal",
    [FieldOperator.LESS_THAN]: "is less than",
    [FieldOperator.LESS_THAN_OR_EQUAL]: "is at most",
    [FieldOperator.GREATER_THAN]: "is greater than",
    [FieldOperator.GREATER_THAN_OR_EQUAL]: "is at least",
    [FieldOperator.CONTAINS]: "contains",
    [FieldOperator.STARTS_WITH]: "starts with",
    [FieldOperator.IN]: "is any of",
    [FieldOperator.NOT_IN]: "is none of",
    [FieldOperator.IS_EMPTY]: "is empty",
    [FieldOperator.IS_NOT_EMPTY]: "is not empty",
};

export const operatorsForType = (type: FieldValueType): FieldOperator[] =>
    OPERATORS_BY_TYPE[type] ?? [];

export const operatorLabel = (op: FieldOperator): string => OPERATOR_LABELS[op] ?? op;

/** IS_EMPTY / IS_NOT_EMPTY take no literal value. */
export const operatorNeedsValue = (op: FieldOperator): boolean =>
    op !== FieldOperator.IS_EMPTY && op !== FieldOperator.IS_NOT_EMPTY;

/** IN / NOT_IN accept a list of option codes. */
export const operatorIsMultiValue = (op: FieldOperator): boolean =>
    op === FieldOperator.IN || op === FieldOperator.NOT_IN;

/** Builds a fresh condition for a schema field binding, defaulting to its first supported operator. */
export const defaultConditionFor = (binding: SchemaFieldBindingDto): WorkflowFieldConditionDraft => ({
    fieldDefinitionId: binding.fieldDefinitionId,
    fieldKey: binding.fieldKey,
    valueType: binding.valueType,
    operator: operatorsForType(binding.valueType)[0],
    value: undefined,
});
