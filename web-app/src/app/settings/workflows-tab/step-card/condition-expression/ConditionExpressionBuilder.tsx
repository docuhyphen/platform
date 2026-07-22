import React, {useState} from "react";
import {Combobox, Input, Option, Select, Text} from "@fluentui/react-components";
import {WorkflowSubjectFieldDto} from "../../../../models/models.tsx";
import ConditionValueControl from "./ConditionValueControl.tsx";
import {
    buildConditionExpression,
    getAllowedConditionOperators,
    humanizeFieldName as humanize,
    parseConditionExpression,
    parseEnumDescription,
} from "./conditionExpression.ts";
import {useConditionExpressionBuilderStyles} from "./ConditionExpressionBuilderStyles.tsx";

interface Props
{
    expression: string | undefined;
    subjectFields: WorkflowSubjectFieldDto[];
    onChange: (expression: string | undefined) => void;
}

const ConditionExpressionBuilder = ({expression, subjectFields, onChange}: Props) =>
{
    const styles = useConditionExpressionBuilderStyles();
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
    const emit = (nextField: string, nextOperator: string, nextValue: string, quote: boolean) =>
        onChange(buildConditionExpression(nextField, nextOperator, nextValue, quote));
    const filteredFields = fieldSearch && fieldSearch !== (field ? humanize(field) : "")
        ? subjectFields.filter(candidate => humanize(candidate.name).toLowerCase().includes(fieldSearch.toLowerCase()))
        : subjectFields;

    if (subjectFields.length === 0)
    {
        return (
            <div className={styles.field}>
                <Text size={200} weight="semibold">Condition Expression</Text>
                <Input
                    id="condition-expression-input"
                    size="small"
                    placeholder="e.g. status equals 'approved'"
                    value={expression ?? ""}
                    onChange={(_, data) => onChange(data.value || undefined)}
                />
                <Text size={100} className={styles.hint}>Select a trigger event to enable the expression builder.</Text>
            </div>
        );
    }

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
        emit(nextField, nextOperator, "", next?.type !== "number" && next?.type !== "integer" && next?.type !== "boolean");
    };

    return (
        <div className={styles.field}>
            <Text size={200} weight="semibold">Condition</Text>
            <div className={styles.row}>
                <Combobox
                    id="condition-field-combobox"
                    size="small"
                    className={styles.fieldCombobox}
                    freeform={false}
                    placeholder="Search fields..."
                    value={fieldSearch}
                    selectedOptions={field ? [field] : []}
                    onInput={(event) => setFieldSearch((event.target as HTMLInputElement).value)}
                    onBlur={() => setFieldSearch(field ? humanize(field) : "")}
                    onOptionSelect={(_, data) => selectField(data.optionValue as string)}
                >
                    {filteredFields.map(candidate => (
                        <Option key={candidate.name} value={candidate.name}>{humanize(candidate.name)}</Option>
                    ))}
                </Combobox>
                <Select
                    id="condition-operator-select"
                    size="small"
                    className={styles.operatorSelect}
                    value={operator}
                    onChange={(_, data) =>
                    {
                        setOperator(data.value);
                        emit(field, data.value, value, quoted);
                    }}
                >
                    {allowedOperators.map(candidate => (
                        <option key={candidate.value} value={candidate.value}>{candidate.label}</option>
                    ))}
                </Select>
                <ConditionValueControl
                    field={field}
                    value={value}
                    numeric={numeric}
                    boolean={boolean}
                    enumValues={enumValues}
                    lookupType={lookupType}
                    onChange={(nextValue, label, quote) =>
                    {
                        setValue(nextValue);
                        setValueLabel(label);
                        emit(field, operator, nextValue, quote);
                    }}
                />
            </div>
            {field && value && valueLabel && <Text size={100} className={styles.hint}>{humanize(field)} {valueLabel}</Text>}
        </div>
    );
};

export default ConditionExpressionBuilder;
