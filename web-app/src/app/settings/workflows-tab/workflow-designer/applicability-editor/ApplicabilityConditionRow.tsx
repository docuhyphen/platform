import {Button, Select, Text} from "@fluentui/react-components";
import {FieldOperator, SchemaFieldBindingDto, WorkflowFieldConditionDraft,} from "../../../../models/models.tsx";
import {useApplicabilityEditorStyles} from "./ApplicabilityEditorStyles.tsx";
import {literalForOperator, operatorLabel, operatorNeedsValue, operatorsForType} from "./applicabilityOperators.ts";
import {DeleteIcon} from "../../../../components/IconBundles.tsx";
import ConditionValueInput from "../condition-value-input/ConditionValueInput.tsx";

interface Props
{
    index: number;
    condition: WorkflowFieldConditionDraft;
    binding?: SchemaFieldBindingDto;
    onChange: (next: WorkflowFieldConditionDraft) => void;
    onRemove: () => void;
}

const ApplicabilityConditionRow = ({index, condition, binding, onChange, onRemove}: Props) =>
{
    const styles = useApplicabilityEditorStyles();
    const id = `applicability-condition-${index}`;
    const operators = operatorsForType(condition.valueType);

    const setOperator = (value: string) =>
    {
        const operator = operators.find(candidate => candidate === value);
        if (operator) onChange({...condition, operator, value: literalForOperator(operator, condition.value)});
    };

    return (
        <div id={id}
             className={styles.row}>
            <div id={`${id}-field`}
                 className={styles.rowField}>
                <Text id={`${id}-field-label`}
                      size={200}
                      weight="semibold">
                    Field
                </Text>
                <Text id={`${id}-field-name`}
                      size={300}>
                    {binding?.label ?? condition.fieldKey ?? "Unknown field"}
                </Text>
            </div>
            <div id={`${id}-condition`}
                 className={styles.rowField}>
                <Text id={`${id}-operator-label`}
                      size={200}
                      weight="semibold">
                    Condition
                </Text>
                <Select size={"small"}
                        id={`${id}-operator`}
                        aria-labelledby={`${id}-operator-label`}
                        value={condition.operator}
                        onChange={(_, data) => setOperator(data.value)}>
                    {operators.map((operator: FieldOperator) => (
                        <option key={operator}
                                id={`${id}-operator-${operator.toLowerCase()}`}
                                value={operator}>
                            {operatorLabel(operator)}
                        </option>
                    ))}
                </Select>
            </div>
            <div id={`${id}-value-field`}
                 className={styles.rowField}>
                <Text id={`${id}-value-label`}
                      size={200}
                      weight="semibold">
                    Value
                </Text>
                <ConditionValueInput idPrefix={id}
                                     label={"Value"}
                                     valueType={condition.valueType}
                                     operator={condition.operator}
                                     value={condition.value}
                                     options={binding?.options ?? []}
                                     onChange={value => onChange({...condition, value})}/>
                {!operatorNeedsValue(condition.operator) && (
                    <Text id={`${id}-no-value`}
                          size={200}
                          className={styles.hint}>
                        No value needed
                    </Text>
                )}
            </div>
            <Button size={"small"}
                    id={`${id}-remove`}
                    appearance="subtle"
                    shape="circular"
                    icon={<DeleteIcon/>}
                    aria-label="Remove condition"
                    onClick={onRemove}/>
        </div>
    );
};

export default ApplicabilityConditionRow;
