import {Button, Field, Select, Text} from "@fluentui/react-components";
import {DeleteIcon} from "../../../../../components/IconBundles.tsx";
import TextField from "../../../../../information-requests/shared/text-field/TextField.tsx";
import {WorkflowRequirementConditionDraft} from "../../../../../models/models.tsx";
import {
    literalForOperator,
    operatorLabel,
    operatorNeedsValue,
    operatorsForType,
} from "../../applicability-editor/applicabilityOperators.ts";
import ConditionValueInput from "../../condition-value-input/ConditionValueInput.tsx";
import {RequirementChoice, ROOT_OCCURRENCE_PATH} from "../requirementConditionChoices.ts";
import {useRequirementConditionRowStyles} from "./RequirementConditionRowStyles.tsx";

interface RequirementConditionRowProps {
    index: number;
    condition: WorkflowRequirementConditionDraft;
    choice?: RequirementChoice;
    onChange: (next: WorkflowRequirementConditionDraft) => void;
    onRemove: () => void;
}

const answerLabel = (condition: WorkflowRequirementConditionDraft, choice?: RequirementChoice): string =>
    choice
        ? `${choice.prompt} (${choice.requirementKey})`
        : condition.requirementKey ?? `Requirement ${condition.templateRequirementId.slice(0, 8)}`;

const RequirementConditionRow = ({index, condition, choice, onChange, onRemove}: RequirementConditionRowProps) => {
    const styles = useRequirementConditionRowStyles();
    const id = `requirement-condition-${index}`;
    const label = answerLabel(condition, choice);
    const repeated = choice?.repeated ?? condition.occurrencePath !== ROOT_OCCURRENCE_PATH;
    const operators = operatorsForType(condition.valueType);

    const setOperator = (value: string) => {
        const operator = operators.find(candidate => candidate === value);
        if (operator) onChange({...condition, operator, value: literalForOperator(operator, condition.value)});
    };

    return (
        <div id={id}
             className={styles.row}>
            <div id={`${id}-answer`}
                 className={styles.cell}>
                <Text id={`${id}-answer-label`}
                      size={200}
                      weight={"semibold"}>
                    Answer
                </Text>
                <Text id={`${id}-answer-name`}
                      size={300}
                      className={styles.answer}>
                    {label}
                </Text>
            </div>
            {repeated && (
                <TextField id={`${id}-occurrence`}
                           label={"Occurrence"}
                           hint={"Such as group[0] for the first occurrence of a repeated group."}
                           value={condition.occurrencePath}
                           validationMessage={condition.occurrencePath.trim() ? undefined : "Name the occurrence to compare."}
                           className={styles.cell}
                           onChange={occurrencePath => onChange({...condition, occurrencePath})}/>
            )}
            <Field id={`${id}-operator-field`}
                   label={"Condition"}
                   className={styles.cell}>
                <Select id={`${id}-operator`}
                        size={"small"}
                        value={condition.operator}
                        onChange={(_, data) => setOperator(data.value)}>
                    {operators.map(operator => (
                        <option key={operator}
                                id={`${id}-operator-${operator.toLowerCase()}`}
                                value={operator}>
                            {operatorLabel(operator)}
                        </option>
                    ))}
                </Select>
            </Field>
            <div id={`${id}-value-field`}
                 className={styles.cell}>
                <Text id={`${id}-value-label`}
                      size={200}
                      weight={"semibold"}>
                    Value
                </Text>
                <ConditionValueInput idPrefix={id}
                                     label={"Value"}
                                     valueType={condition.valueType}
                                     operator={condition.operator}
                                     value={condition.value}
                                     options={choice?.binding.options ?? []}
                                     onChange={value => onChange({...condition, value})}/>
                {!operatorNeedsValue(condition.operator) && (
                    <Text id={`${id}-no-value`}
                          size={200}
                          className={styles.hint}>
                        No value needed
                    </Text>
                )}
            </div>
            <Button id={`${id}-remove`}
                    size={"small"}
                    appearance={"subtle"}
                    shape={"circular"}
                    icon={<DeleteIcon/>}
                    aria-label={`Remove the condition on ${label}`}
                    onClick={onRemove}/>
        </div>
    );
};

export default RequirementConditionRow;
