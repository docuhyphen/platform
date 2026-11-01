import {Input, Select} from "@fluentui/react-components";
import EntityPickerCombobox from "../entity-picker-combobox/EntityPickerCombobox.tsx";
import {useConditionValueControlStyles} from "./ConditionValueControlStyles.tsx";

interface Props
{
    field: string;
    value: string;
    numeric: boolean;
    boolean: boolean;
    enumValues: string[] | null;
    lookupType: string | undefined;
    allowEntityLookup: boolean;
    onChange: (value: string, label: string, quoted: boolean) => void;
}

const humanizeEnum = (value: string): string => value.trim().split("_")
    .map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase()).join(" ");

const ConditionValueControl = (props: Props) =>
{
    const styles = useConditionValueControlStyles();
    if (props.lookupType && props.allowEntityLookup)
    {
        return (
            <EntityPickerCombobox
                key={props.field}
                value={props.value}
                lookupType={props.lookupType}
                onSelect={(id, label) => props.onChange(id, label, true)}
            />
        );
    }
    if (props.enumValues)
    {
        return (
            <Select
                id="condition-enum-value-select"
                size="small"
                className={styles.valueControl}
                value={props.value}
                onChange={(_, data) => props.onChange(data.value, humanizeEnum(data.value), true)}
            >
                <option id={"condition-value-control-option-1"}
                        value="">Select...</option>
                {props.enumValues.map(value => (
                    <option id={`condition-value-control-option-2-${value}`}
                            key={value}
                            value={value}>{humanizeEnum(value)}</option>
                ))}
            </Select>
        );
    }
    if (props.boolean)
    {
        return (
            <Select
                id="condition-bool-value-select"
                size="small"
                className={styles.valueControl}
                value={props.value}
                onChange={(_, data) => props.onChange(data.value, data.value === "true" ? "Yes" : "No", false)}
            >
                <option id={"condition-value-control-option-3"}
                        value="">Select...</option>
                <option id={"condition-value-control-option-4"}
                        value="true">Yes</option>
                <option id={"condition-value-control-option-5"}
                        value="false">No</option>
            </Select>
        );
    }
    return (
        <Input
            id="condition-value-input"
            size="small"
            className={styles.valueControl}
            type={props.numeric ? "number" : "text"}
            placeholder={props.numeric ? "e.g. 5" : "Value..."}
            value={props.value}
            onChange={(_, data) => props.onChange(data.value, data.value, !props.numeric)}
        />
    );
};

export default ConditionValueControl;
