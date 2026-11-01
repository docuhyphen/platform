import {useState} from "react";
import {WorkflowSubjectFieldDto} from "../../../../models/models.tsx";
import {
    buildConditionExpression,
    getAllowedConditionOperators,
    humanizeFieldName as humanize,
    parseConditionExpression,
    parseEnumDescription,
} from "./conditionExpression.ts";

const isUnquotedType = (field: WorkflowSubjectFieldDto | undefined) =>
    field?.type === "number" || field?.type === "integer" || field?.type === "boolean";

export function useConditionExpressionDraft(
    expression: string | undefined,
    subjectFields: WorkflowSubjectFieldDto[],
    onChange: (expression: string | undefined) => void,
)
{
    const initial = parseConditionExpression(expression ?? "");
    const [field, setField] = useState(initial.field);
    const [operator, setOperator] = useState(initial.operator);
    const [value, setValue] = useState(initial.value);
    const [valueLabel, setValueLabel] = useState(initial.value);
    const [fieldSearch, setFieldSearch] = useState(initial.field ? humanize(initial.field) : "");

    const selectedField = subjectFields.find(candidate => candidate.name === field);
    const numeric = selectedField?.type === "number" || selectedField?.type === "integer";
    const boolean = selectedField?.type === "boolean";
    const enumValues = parseEnumDescription(selectedField?.description);
    const lookupType = selectedField?.type === "UUID" ? selectedField.lookupType : undefined;
    const allowedOperators = getAllowedConditionOperators(selectedField);
    const quoted = !numeric && !boolean;
    const filteredFields = fieldSearch && fieldSearch !== (field ? humanize(field) : "")
        ? subjectFields.filter(candidate => humanize(candidate.name).toLowerCase().includes(fieldSearch.toLowerCase()))
        : subjectFields;

    const emit = (nextField: string, nextOperator: string, nextValue: string, quote: boolean) =>
        onChange(buildConditionExpression(nextField, nextOperator, nextValue, quote));

    const selectField = (nextField: string) =>
    {
        const next = subjectFields.find(candidate => candidate.name === nextField);
        const nextAllowed = getAllowedConditionOperators(next);
        const nextOperator = nextAllowed.some(candidate => candidate.value === operator) ? operator : nextAllowed[0].value;
        setField(nextField);
        setFieldSearch(humanize(nextField));
        setOperator(nextOperator);
        setValue("");
        setValueLabel("");
        emit(nextField, nextOperator, "", !isUnquotedType(next));
    };

    const selectOperator = (nextOperator: string) =>
    {
        setOperator(nextOperator);
        emit(field, nextOperator, value, quoted);
    };

    const selectValue = (nextValue: string, label: string, quote: boolean) =>
    {
        setValue(nextValue);
        setValueLabel(label);
        emit(field, operator, nextValue, quote);
    };

    const resetFieldSearch = () => setFieldSearch(field ? humanize(field) : "");

    return {
        field,
        operator,
        value,
        valueLabel,
        fieldSearch,
        setFieldSearch,
        resetFieldSearch,
        numeric,
        boolean,
        enumValues,
        lookupType,
        allowedOperators,
        filteredFields,
        selectField,
        selectOperator,
        selectValue,
    };
}
