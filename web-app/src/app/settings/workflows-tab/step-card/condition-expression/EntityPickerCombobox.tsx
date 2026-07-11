import React, {useCallback, useEffect, useRef, useState} from "react";
import {Combobox, Option} from "@fluentui/react-components";
import {WorkflowEntityRefDto} from "../../../../models/models.tsx";
import {lookupWorkflowEntities} from "../../../../../services/workflowService.ts";
import {useConditionExpressionBuilderStyles} from "./ConditionExpressionBuilderStyles.tsx";

interface Props
{
    value: string;
    lookupType: string;
    onSelect: (id: string, label: string) => void;
}

const EntityPickerCombobox = ({value, lookupType, onSelect}: Props) =>
{
    const styles = useConditionExpressionBuilderStyles();
    const [inputText, setInputText] = useState("");
    const [results, setResults] = useState<WorkflowEntityRefDto[]>([]);
    const [loading, setLoading] = useState(false);
    const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

    const search = useCallback((query: string) =>
    {
        if (debounceRef.current) clearTimeout(debounceRef.current);
        if (!query.trim())
        {
            setResults([]);
            return;
        }
        debounceRef.current = setTimeout(async () =>
        {
            setLoading(true);
            try
            {
                setResults(await lookupWorkflowEntities(lookupType, query));
            }
            catch
            {
                setResults([]);
            }
            finally
            {
                setLoading(false);
            }
        }, 300);
    }, [lookupType]);

    useEffect(() => () =>
    {
        if (debounceRef.current) clearTimeout(debounceRef.current);
    }, []);

    return (
        <Combobox
            id={`entity-picker-combobox-${lookupType}`}
            size="small"
            className={styles.valueControl}
            freeform={false}
            placeholder="Search..."
            value={inputText}
            selectedOptions={value ? [value] : []}
            onInput={(event) =>
            {
                const text = (event.target as HTMLInputElement).value;
                setInputText(text);
                search(text);
            }}
            onOptionSelect={(_, data) =>
            {
                const entity = results.find(result => result.id === data.optionValue);
                if (!entity) return;
                setInputText(entity.label);
                setResults([]);
                onSelect(entity.id, entity.label);
            }}
        >
            {loading
                ? <Option value="" disabled>Searching...</Option>
                : results.map(result => (
                    <Option key={result.id} value={result.id}>
                        {result.label}
                        {result.sublabel && <span className={styles.sublabel}>{result.sublabel}</span>}
                    </Option>
                ))}
        </Combobox>
    );
};

export default EntityPickerCombobox;
