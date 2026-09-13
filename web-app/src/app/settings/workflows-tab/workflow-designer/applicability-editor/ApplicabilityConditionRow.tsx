import {Button, Dropdown, Input, Option, Select, Switch, Text} from "@fluentui/react-components";
import {
    FieldOperator,
    FieldValueType,
    SchemaFieldBindingDto,
    WorkflowFieldConditionDraft,
} from "../../../../models/models.tsx";
import {useApplicabilityEditorStyles} from "./ApplicabilityEditorStyles.tsx";
import {operatorIsMultiValue, operatorLabel, operatorNeedsValue, operatorsForType,} from "./applicabilityOperators.ts";
import {DeleteIcon} from "../../../../components/IconBundles.tsx";
import {toConditionLiteral, toConditionLiteralInputValue} from "./conditionLiteral.ts";

interface Props
{
    index: number;
    condition: WorkflowFieldConditionDraft;
    binding?: SchemaFieldBindingDto;
    onChange: (next: WorkflowFieldConditionDraft) => void;
    onRemove: () => void;
}

const asStringArray = (value: unknown): string[] =>
    Array.isArray(value) ? value.map(v => String(v)) : [];

const ApplicabilityConditionRow = ({index, condition, binding, onChange, onRemove}: Props) =>
{
    const styles = useApplicabilityEditorStyles();
    const type = condition.valueType;
    const isSelect = type === FieldValueType.SINGLE_SELECT || type === FieldValueType.MULTI_SELECT;
    const options = binding?.options ?? [];

    const setOperator = (op: FieldOperator) =>
    {
        const needsValue = operatorNeedsValue(op);
        const multi = operatorIsMultiValue(op);
        onChange({
            ...condition,
            operator: op,
            value: !needsValue ? undefined : multi ? asStringArray(condition.value) : condition.value,
        });
    };

    const setValue = (value: unknown) => onChange({...condition, value});

    const renderValueInput = () =>
    {
        if (!operatorNeedsValue(condition.operator)) return null;
        const multi = operatorIsMultiValue(condition.operator);

        if (isSelect && multi)
        {
            const selected = asStringArray(condition.value);
            const selectedLabels = selected
                .map(code => options.find(o => o.code === code)?.label ?? code)
                .join(", ");
            return (
                <Dropdown
                    size={"small"}
                    id={`applicability-condition-${index}-values`}
                    multiselect
                    selectedOptions={selected}
                    value={selectedLabels}
                    placeholder="Select options"
                    onOptionSelect={(_, d) => setValue(d.selectedOptions)}
                >
                    {options.map(o => (
                        <Option key={o.code} value={o.code} text={o.label}>
                            {o.label}
                        </Option>
                    ))}
                </Dropdown>
            );
        }

        if (isSelect)
            return (
                <Select
                    size={"small"}
                    id={`applicability-condition-${index}-value`}
                    value={typeof condition.value === "string" ? condition.value : ""}
                    onChange={(_, d) => setValue(d.value)}
                >
                    <option value="">Select an option</option>
                    {options.map(o => <option key={o.code} value={o.code}>{o.label}</option>)}
                </Select>
            );

        if (type === FieldValueType.BOOLEAN)
            return (
                <Switch
                    id={`applicability-condition-${index}-bool`}
                    checked={condition.value === true}
                    onChange={(_, d) => setValue(d.checked)}
                />
            );

        const inputType = type === FieldValueType.INTEGER || type === FieldValueType.DECIMAL ? "number"
            : type === FieldValueType.DATE ? "date"
                : type === FieldValueType.DATE_TIME ? "datetime-local" : "text";

        return (
            <Input
                size={"small"}
                id={`applicability-condition-${index}-value`}
                type={inputType}
                value={toConditionLiteralInputValue(type, condition.value)}
                onChange={(_, d) => setValue(toConditionLiteral(type, d.value))}
            />
        );
    };

    return (
        <div className={styles.row}>
            <div className={styles.rowField}>
                <Text size={200} weight="semibold">Field</Text>
                <Text size={300}>{binding?.label ?? condition.fieldKey ?? "Unknown field"}</Text>
            </div>
            <div className={styles.rowField}>
                <Text size={200} weight="semibold">Condition</Text>
                <Select
                    size={"small"}
                    id={`applicability-condition-${index}-operator`}
                    value={condition.operator}
                    onChange={(_, d) => setOperator(d.value as FieldOperator)}
                >
                    {operatorsForType(type).map(op =>
                        <option key={op} value={op}>{operatorLabel(op)}</option>)}
                </Select>
            </div>
            <div className={styles.rowField}>
                <Text size={200} weight="semibold">Value</Text>
                {renderValueInput() ?? <Text size={200} className={styles.hint}>No value needed</Text>}
            </div>
            <Button
                size={"small"}
                id={`applicability-condition-${index}-remove`}
                appearance="subtle"
                shape="circular"
                icon={<DeleteIcon/>}
                aria-label="Remove condition"
                onClick={onRemove}
            />
        </div>
    );
};

export default ApplicabilityConditionRow;
