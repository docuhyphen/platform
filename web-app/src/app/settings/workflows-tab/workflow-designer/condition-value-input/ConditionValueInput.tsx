import {Dropdown, Input, InputProps, Option, Select, Switch} from "@fluentui/react-components";
import {FieldOperator, FieldOption, FieldValueType} from "../../../../models/models.tsx";
import {operatorIsMultiValue, operatorNeedsValue} from "../applicability-editor/applicabilityOperators.ts";
import {toConditionLiteral, toConditionLiteralInputValue} from "../applicability-editor/conditionLiteral.ts";

interface ConditionValueInputProps {
    idPrefix: string;
    label: string;
    valueType: FieldValueType;
    operator: FieldOperator;
    value: unknown;
    options: FieldOption[];
    onChange: (value: unknown) => void;
}

const asStringArray = (value: unknown): string[] => Array.isArray(value) ? value.map(item => String(item)) : [];

const withChosen = (options: FieldOption[], codes: string[]): FieldOption[] => [
    ...options,
    ...codes.filter(code => code && !options.some(option => option.code === code)).map(code => ({code, label: code})),
];

const inputTypeOf = (valueType: FieldValueType): InputProps["type"] => {
    if (valueType === FieldValueType.INTEGER || valueType === FieldValueType.DECIMAL) return "number";
    if (valueType === FieldValueType.DATE) return "date";
    if (valueType === FieldValueType.DATE_TIME) return "datetime-local";
    return "text";
};

const ConditionValueInput = ({
                                 idPrefix,
                                 label,
                                 valueType,
                                 operator,
                                 value,
                                 options,
                                 onChange
                             }: ConditionValueInputProps) => {
    if (!operatorNeedsValue(operator)) return null;
    const isSelect = valueType === FieldValueType.SINGLE_SELECT || valueType === FieldValueType.MULTI_SELECT;

    if (isSelect && operatorIsMultiValue(operator)) {
        const selected = asStringArray(value);
        const choices = withChosen(options, selected);
        return (
            <Dropdown id={`${idPrefix}-values`}
                      size={"small"}
                      multiselect={true}
                      aria-label={label}
                      selectedOptions={selected}
                      value={selected.map(code => choices.find(option => option.code === code)?.label ?? code).join(", ")}
                      placeholder={"Select options"}
                      onOptionSelect={(_, data) => onChange(data.selectedOptions)}>
                {choices.map(option => (
                    <Option key={option.code}
                            id={`${idPrefix}-values-${option.code}`}
                            value={option.code}
                            text={option.label}>
                        {option.label}
                    </Option>
                ))}
            </Dropdown>
        );
    }

    if (isSelect) {
        const current = typeof value === "string" ? value : "";
        return (
            <Select id={`${idPrefix}-value`}
                    size={"small"}
                    aria-label={label}
                    value={current}
                    onChange={(_, data) => onChange(data.value)}>
                <option id={`${idPrefix}-value-none`}
                        value={""}>
                    Select an option
                </option>
                {withChosen(options, [current]).map(option => (
                    <option key={option.code}
                            id={`${idPrefix}-value-${option.code}`}
                            value={option.code}>
                        {option.label}
                    </option>
                ))}
            </Select>
        );
    }

    if (valueType === FieldValueType.BOOLEAN) {
        return (
            <Switch id={`${idPrefix}-bool`}
                    aria-label={label}
                    checked={value === true}
                    onChange={(_, data) => onChange(data.checked)}/>
        );
    }

    return (
        <Input id={`${idPrefix}-value`}
               size={"small"}
               aria-label={label}
               type={inputTypeOf(valueType)}
               value={toConditionLiteralInputValue(valueType, value)}
               onChange={(_, data) => onChange(toConditionLiteral(valueType, data.value))}/>
    );
};

export default ConditionValueInput;
