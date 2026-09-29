import {Button} from "@fluentui/react-components";
import {DeleteIcon} from "../../../components/IconBundles.tsx";
import {
    FieldOperator,
    FieldValueType,
    InformationRequestResponseDisposition,
    InformationRequestTemplateConditionPredicateRequest,
} from "../../../models/models.tsx";
import ChoiceSelect from "../../shared/choice-select/ChoiceSelect.tsx";
import {ChoiceOption, optionsFrom} from "../../shared/choice-select/choiceOptions.ts";
import TextField from "../../shared/text-field/TextField.tsx";
import {dispositionLabels, operatorLabels} from "../templateAuthoringLabels.ts";
import {VALUELESS_OPERATORS} from "../templatePlanValidation.ts";
import {isListOperator, literalFrom, literalText, operatorsFor, sourceValueOf} from "./conditionOperators.ts";
import {useConditionPredicateRowStyles} from "./ConditionPredicateRowStyles.tsx";

export interface PredicateSource
{
    value: string;
    label: string;
    valueType?: FieldValueType;
}

interface ConditionPredicateRowProps
{
    id: string;
    predicate: InformationRequestTemplateConditionPredicateRequest;
    sources: PredicateSource[];
    readOnly: boolean;
    onChange: (predicate: InformationRequestTemplateConditionPredicateRequest) => void;
    onRemove: () => void;
}

const ConditionPredicateRow = ({id, predicate, sources, readOnly, onChange, onRemove}: ConditionPredicateRowProps) =>
{
    const styles = useConditionPredicateRowStyles();
    const readsField = Boolean(predicate.fieldDefinitionId);
    const operators: ChoiceOption<FieldOperator>[] = operatorsFor(predicate.valueType)
        .map(operator => ({value: operator, label: operatorLabels[operator]}));
    const needsValue = !VALUELESS_OPERATORS.has(predicate.operator);

    const chooseSource = (value: string) =>
    {
        const source = sources.find(candidate => candidate.value === value);
        if (!source) return;
        const [kind, key] = [value.slice(0, value.indexOf(":")), value.slice(value.indexOf(":") + 1)];
        onChange(kind === "field"
            ? {operator: operatorsFor(source.valueType)[0], fieldDefinitionId: key, valueType: source.valueType}
            : {operator: FieldOperator.EQUALS, sourceRequirementKey: key});
    };

    return (
        <div id={id}
             className={styles.row}>
            <ChoiceSelect id={`${id}-source-select`}
                          label={"Reads"}
                          value={sourceValueOf(predicate)}
                          placeholder={"Choose what to read"}
                          options={sources}
                          disabled={readOnly}
                          onChange={chooseSource}/>
            <ChoiceSelect id={`${id}-operator-select`}
                          label={"Test"}
                          value={predicate.operator}
                          options={operators}
                          disabled={readOnly}
                          onChange={operator => onChange({...predicate, operator, value: undefined})}/>
            {needsValue && readsField && (
                <TextField id={`${id}-value-input`}
                           label={isListOperator(predicate.operator) ? "Values (comma separated)" : "Value"}
                           value={literalText(predicate.value)}
                           hint={predicate.valueType === FieldValueType.BOOLEAN ? "Enter true or false" : undefined}
                           disabled={readOnly}
                           onChange={raw => onChange({...predicate, value: literalFrom(raw, predicate.valueType, predicate.operator)})}/>
            )}
            {needsValue && !readsField && predicate.sourceRequirementKey && (
                <ChoiceSelect id={`${id}-answer-select`}
                              label={"Answer"}
                              value={predicate.expectedDisposition ?? ""}
                              placeholder={"Choose an answer"}
                              options={optionsFrom(dispositionLabels)}
                              disabled={readOnly}
                              onChange={(expectedDisposition: InformationRequestResponseDisposition) =>
                                  onChange({...predicate, expectedDisposition})}/>
            )}
            {!readOnly && (
                <Button id={`${id}-remove`}
                        appearance={"subtle"}
                        shape={"circular"}
                        icon={<DeleteIcon/>}
                        aria-label={"Remove this test"}
                        className={styles.remove}
                        onClick={onRemove}/>
            )}
        </div>
    );
};

export default ConditionPredicateRow;
