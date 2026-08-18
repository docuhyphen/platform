import {useState} from 'react';
import {Button, Combobox, Option, Switch, Text} from '@fluentui/react-components';
import {ArrowUpRegular, ArrowDownRegular, DeleteRegular} from '@fluentui/react-icons';
import {FieldDefinitionDto} from '../../models/models';
import {useFieldsTabStyles} from './FieldsTabStyles';
export interface BindingDraft
{
    fieldContractId: string;
    label: string;
    keyLabel: string;
    isRequired: boolean;
    isReadOnly: boolean;
}
interface Props
{
    definitions: FieldDefinitionDto[];
    bindings: BindingDraft[];
    onChange: (bindings: BindingDraft[]) => void;
}
const SchemaBindingsEditor = ({definitions, bindings, onChange}: Props) =>
{
    const styles = useFieldsTabStyles();
    const [fieldSearch, setFieldSearch] = useState('');
    const usedIds = new Set(bindings.map(b => b.fieldContractId));
    const available = definitions.filter(d => d.latestContract && !usedIds.has(d.latestContract.id));
    const normalizedSearch = fieldSearch.trim().toLowerCase();
    const filteredAvailable = available.filter(definition =>
    {
        const searchableText = `${definition.latestContract?.label ?? ''} ${definition.namespace}:${definition.fieldKey}`;
        return searchableText.toLowerCase().includes(normalizedSearch);
    });

    const move = (index: number, delta: number) =>
    {
        const target = index + delta;
        if (target < 0 || target >= bindings.length) return;
        const next = [...bindings];
        [next[index], next[target]] = [next[target], next[index]];
        onChange(next);
    };

    const update = (index: number, patch: Partial<BindingDraft>) =>
        onChange(bindings.map((b, i) => (i === index ? {...b, ...patch} : b)));

    const remove = (index: number) =>
        onChange(bindings.filter((_, i) => i !== index));

    const add = (definitionId: string) =>
    {
        const definition = definitions.find(d => d.id === definitionId);
        if (!definition?.latestContract) return;
        onChange([...bindings, {
            fieldContractId: definition.latestContract.id,
            label: definition.latestContract.label,
            keyLabel: `${definition.namespace}:${definition.fieldKey}`,
            isRequired: false,
            isReadOnly: false,
        }]);
        setFieldSearch('');
    };

    return (
        <div className={styles.fieldGroup}>
            {bindings.length === 0 && (
                <Text className={styles.emptyText}>No fields added yet.</Text>
            )}
            {bindings.map((binding, index) => (
                <div key={binding.fieldContractId}
                     className={styles.bindingRow}>
                    <div className={styles.bindingHeader}>
                        <div className={styles.cardMain}>
                            <Text weight="semibold">{binding.label}</Text>
                            <code className={styles.codeKey}>{binding.keyLabel}</code>
                        </div>
                        <div className={styles.actionGroup}>
                            <Button id={`binding-up-${index}`}
                                    appearance="subtle"
                                    shape="circular"
                                    size="small"
                                    aria-label="Move up"
                                    disabled={index === 0}
                                    icon={<ArrowUpRegular/>}
                                    onClick={() => move(index, -1)}/>
                            <Button id={`binding-down-${index}`}
                                    appearance="subtle"
                                    shape="circular"
                                    size="small"
                                    aria-label="Move down"
                                    disabled={index === bindings.length - 1}
                                    icon={<ArrowDownRegular/>}
                                    onClick={() => move(index, 1)}/>
                            <Button id={`binding-remove-${index}`}
                                    appearance="subtle"
                                    shape="circular"
                                    size="small"
                                    aria-label="Remove field"
                                    icon={<DeleteRegular/>}
                                    onClick={() => remove(index)}/>
                        </div>
                    </div>
                    <div className={styles.bindingToggles}>
                        <Switch id={`binding-required-${index}`}
                                checked={binding.isRequired}
                                label="Required"
                                onChange={(_, d) => update(index, {isRequired: d.checked})}/>
                        <Switch id={`binding-readonly-${index}`}
                                checked={binding.isReadOnly}
                                label="Read only"
                                onChange={(_, d) => update(index, {isReadOnly: d.checked})}/>
                    </div>
                </div>
            ))}
            <Combobox
                id="binding-add-combobox"
                placeholder={available.length === 0 ? 'All fields added' : 'Search fields to add...'}
                value={fieldSearch}
                selectedOptions={[]}
                disabled={available.length === 0}
                onInput={event => setFieldSearch((event.target as HTMLInputElement).value)}
                onOptionSelect={(_, data) =>
                {
                    if (data.optionValue) add(data.optionValue);
                }}
            >
                {filteredAvailable.map(definition => (
                    <Option
                        id={`binding-add-option-${definition.id}`}
                        key={definition.id}
                        value={definition.id}
                        text={`${definition.latestContract?.label} (${definition.namespace}:${definition.fieldKey})`}
                    >
                        {`${definition.latestContract?.label} (${definition.namespace}:${definition.fieldKey})`}
                    </Option>
                ))}
                {available.length > 0 && filteredAvailable.length === 0 && (
                    <Option
                        id="binding-add-no-results"
                        value=""
                        disabled
                    >
                        No matching fields
                    </Option>
                )}
            </Combobox>
        </div>
    );
};

export default SchemaBindingsEditor;
