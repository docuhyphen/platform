import React from "react";
import {Input, Select} from "@fluentui/react-components";
import EntityPickerCombobox from "./EntityPickerCombobox.tsx";
import {useConditionExpressionBuilderStyles} from "./ConditionExpressionBuilderStyles.tsx";

interface Props
{
    field: string;
    value: string;
    numeric: boolean;
    boolean: boolean;
    enumValues: string[] | null;
    lookupType: string | undefined;
    onChange: (value: string, label: string, quoted: boolean) => void;
}

const humanizeEnum = (value: string): string => value.trim().split("_")
    .map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase()).join(" ");

const ConditionValueControl = (props: Props) =>
{
    const styles = useConditionExpressionBuilderStyles();
    if (props.lookupType)
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
                <option value="">Select...</option>
                {props.enumValues.map(value => (
                    <option key={value} value={value}>{humanizeEnum(value)}</option>
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
                <option value="">Select...</option>
                <option value="true">Yes</option>
                <option value="false">No</option>
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
