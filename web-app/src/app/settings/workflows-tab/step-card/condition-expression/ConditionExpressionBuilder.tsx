import {Combobox, Input, Option, Select, Text} from "@fluentui/react-components";
import {WorkflowSubjectFieldDto} from "../../../../models/models.tsx";
import ConditionValueControl from "./condition-value-control/ConditionValueControl.tsx";
import {humanizeFieldName as humanize} from "./conditionExpression.ts";
import {useConditionExpressionDraft} from "./useConditionExpressionDraft.ts";
import {useConditionExpressionBuilderStyles} from "./ConditionExpressionBuilderStyles.tsx";

interface Props
{
    expression: string | undefined;
    subjectFields: WorkflowSubjectFieldDto[];
    onChange: (expression: string | undefined) => void;
    allowTenantEntityLookup?: boolean;
}

const ConditionExpressionBuilder = ({
    expression,
    subjectFields,
    onChange,
    allowTenantEntityLookup = true,
}: Props) =>
{
    const styles = useConditionExpressionBuilderStyles();
    const draft = useConditionExpressionDraft(expression, subjectFields, onChange);

    if (subjectFields.length === 0)
    {
        return (
            <div id={"condition-expression-builder-div-1"}
                 className={styles.field}>
                <Text id={"condition-expression-builder-text-1"}
                      size={200}
                      weight="semibold">Condition Expression</Text>
                <Input id="condition-expression-input"
                       size="small"
                       placeholder="e.g. status equals 'approved'"
                       value={expression ?? ""}
                       onChange={(_, data) => onChange(data.value || undefined)} />
                <Text id={"condition-expression-builder-text-2"}
                      size={100}
                      className={styles.hint}>Select a trigger event to enable the expression builder.</Text>
            </div>
        );
    }

    return (
        <div id={"condition-expression-builder-div-2"}
             className={styles.field}>
            <Text id={"condition-expression-builder-text-3"}
                  size={200}
                  weight="semibold">Condition</Text>
            <div id={"condition-expression-builder-div-3"}
                 className={styles.row}>
                <Combobox id="condition-field-combobox"
                          size="small"
                          className={styles.fieldCombobox}
                          freeform={false}
                          placeholder="Search fields..."
                          value={draft.fieldSearch}
                          selectedOptions={draft.field ? [draft.field] : []}
                          onInput={(event) => draft.setFieldSearch((event.target as HTMLInputElement).value)}
                          onBlur={draft.resetFieldSearch}
                          onOptionSelect={(_, data) => draft.selectField(data.optionValue as string)}>
                    {draft.filteredFields.map(candidate => (
                        <Option id={`condition-expression-builder-option-1-${candidate.name}`}
                                key={candidate.name}
                                value={candidate.name}>{humanize(candidate.name)}</Option>
                    ))}
                </Combobox>
                <Select id="condition-operator-select"
                        size="small"
                        className={styles.operatorSelect}
                        value={draft.operator}
                        onChange={(_, data) => draft.selectOperator(data.value)}>
                    {draft.allowedOperators.map(candidate => (
                        <option id={`condition-expression-builder-option-2-${candidate.value}`}
                                key={candidate.value}
                                value={candidate.value}>{candidate.label}</option>
                    ))}
                </Select>
                <ConditionValueControl field={draft.field}
                                       value={draft.value}
                                       numeric={draft.numeric}
                                       boolean={draft.boolean}
                                       enumValues={draft.enumValues}
                                       lookupType={draft.lookupType}
                                       allowEntityLookup={allowTenantEntityLookup}
                                       onChange={draft.selectValue} />
            </div>
            {draft.field && draft.value && draft.valueLabel && (
                <Text id={"condition-expression-builder-text-4"}
                      size={100}
                      className={styles.hint}>{humanize(draft.field)} {draft.valueLabel}</Text>
            )}
        </div>
    );
};

export default ConditionExpressionBuilder;
