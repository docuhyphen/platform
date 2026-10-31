import {Field, Input} from "@fluentui/react-components";
import {FieldOperator, FieldOption, FieldValueType} from "../../../models/models.tsx";
import {
    toCanonicalDateTime,
    toDateTimeInputValue,
} from "../../../exchanges/components/exchange-fields-tab/fieldDateTimeCanonical.ts";
import CheckList from "../../shared/check-list/CheckList.tsx";
import ChoiceSelect from "../../shared/choice-select/ChoiceSelect.tsx";
import {ChoiceOption} from "../../shared/choice-select/choiceOptions.ts";
import DateTimeField from "../../shared/date-time-field/DateTimeField.tsx";
import TextField from "../../shared/text-field/TextField.tsx";
import {isListOperator, literalText, typedLiteral} from "../condition-predicate-row/conditionOperators.ts";

interface Props {
    id: string;
    valueType: FieldValueType;
    operator: FieldOperator;
    value: unknown;
    options: FieldOption[];
    disabled: boolean;
    onChange: (value: unknown) => void;
}

const BOOLEAN_CHOICES: ChoiceOption<string>[] = [{value: "true", label: "Yes"}, {value: "false", label: "No"}];

const NUMBER_HINT = "A number, for example 12 or 4.50";

const codesOf = (value: unknown): string[] => Array.isArray(value) ? value.map(item => String(item)) : [];

const choicesFrom = (options: FieldOption[], chosen: string[]): ChoiceOption<string>[] => [
    ...options.map(option => ({value: option.code, label: option.label})),
    ...chosen.filter(code => code && !options.some(option => option.code === code)).map(code => ({
        value: code,
        label: code
    })),
];

const ConditionValueField = ({id, valueType, operator, value, options, disabled, onChange}: Props) => {
    const valueId = `${id}-value`;
    const selects = valueType === FieldValueType.SINGLE_SELECT || valueType === FieldValueType.MULTI_SELECT;

    if (selects && isListOperator(operator)) {
        const chosen = codesOf(value);
        return (
            <CheckList id={valueId}
                       label={"Values"}
                       options={choicesFrom(options, chosen)}
                       selected={chosen}
                       disabled={disabled}
                       onChange={selected => onChange(selected.length > 0 ? selected : undefined)}/>
        );
    }
    if (selects) {
        const chosen = typeof value === "string" ? value : "";
        return (
            <ChoiceSelect id={valueId}
                          label={"Value"}
                          value={chosen}
                          placeholder={"Choose an option"}
                          options={choicesFrom(options, [chosen])}
                          disabled={disabled}
                          onChange={onChange}/>
        );
    }
    if (valueType === FieldValueType.BOOLEAN) {
        return (
            <ChoiceSelect id={valueId}
                          label={"Value"}
                          value={typeof value === "boolean" ? String(value) : ""}
                          placeholder={"Choose Yes or No"}
                          options={BOOLEAN_CHOICES}
                          disabled={disabled}
                          onChange={chosen => onChange(chosen === "true")}/>
        );
    }
    if (valueType === FieldValueType.DATE_TIME) {
        return (
            <DateTimeField id={valueId}
                           label={"Value"}
                           value={toDateTimeInputValue(value)}
                           disabled={disabled}
                           onChange={raw => onChange(toCanonicalDateTime(raw) ?? undefined)}/>
        );
    }
    if (valueType === FieldValueType.DATE) {
        return (
            <Field id={`${valueId}-field`}
                   label={"Value"}>
                <Input id={valueId}
                       type={"date"}
                       value={literalText(value)}
                       disabled={disabled}
                       onChange={(_, data) => onChange(typedLiteral(data.value))}/>
            </Field>
        );
    }
    const numeric = valueType === FieldValueType.INTEGER || valueType === FieldValueType.DECIMAL;
    return (
        <TextField id={valueId}
                   label={"Value"}
                   value={literalText(value)}
                   hint={numeric ? NUMBER_HINT : undefined}
                   disabled={disabled}
                   onChange={raw => onChange(typedLiteral(raw))}/>
    );
};

export default ConditionValueField;
