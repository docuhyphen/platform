import {useState} from 'react';
import {Combobox, Option, Text} from '@fluentui/react-components';
import {FieldDefinitionDto} from '../../models/models';
import {useFieldsTabStyles} from './FieldsTabStyles';
import {addableDefinitions, BindingDraft, draftForDefinition} from './schemaBindingDrafts';
import SchemaBindingRow from './schema-binding-row/SchemaBindingRow';

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
    const available = addableDefinitions(definitions, bindings);
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
        const draft = definition ? draftForDefinition(definition) : null;
        if (!draft) return;
        onChange([...bindings, draft]);
        setFieldSearch('');
    };

    return (
        <div id="schema-bindings-editor"
             className={styles.fieldGroup}>
            {bindings.length === 0 && (
                <Text id="schema-bindings-empty"
                      className={styles.emptyText}>
                    No fields added yet.
                </Text>
            )}
            {bindings.map((binding, index) => (
                <SchemaBindingRow key={binding.fieldDefinitionId}
                                  binding={binding}
                                  index={index}
                                  count={bindings.length}
                                  onMove={delta => move(index, delta)}
                                  onRemove={() => remove(index)}
                                  onChange={patch => update(index, patch)}/>
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
