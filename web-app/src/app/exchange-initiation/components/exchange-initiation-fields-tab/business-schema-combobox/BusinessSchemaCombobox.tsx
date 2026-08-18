import {useEffect, useState} from 'react';
import {Combobox, Option} from '@fluentui/react-components';
import {SchemaDefinitionDto} from '../../../../models/models';

const NO_SCHEMA_OPTION = 'NO_BUSINESS_SCHEMA';

interface Props
{
    schemas: SchemaDefinitionDto[];
    schemaDefinitionId?: string;
    locked?: boolean;
    onSchemaChange: (schemaDefinitionId: string | undefined) => void;
}

const BusinessSchemaCombobox = ({schemas, schemaDefinitionId, locked, onSchemaChange}: Props) =>
{
    const selectedName = schemaDefinitionId
        ? schemas.find(schema => schema.id === schemaDefinitionId)?.displayName ?? ''
        : '';
    const [inputValue, setInputValue] = useState(selectedName);
    const normalizedSearch = inputValue.trim().toLowerCase();
    const isSelectedLabel = normalizedSearch === selectedName.toLowerCase();
    const filteredSchemas = schemas.filter(schema =>
    {
        if (!normalizedSearch || isSelectedLabel) return true;
        const searchableText = `${schema.displayName} ${schema.namespace}:${schema.schemaKey}`;
        return searchableText.toLowerCase().includes(normalizedSearch);
    });
    const showNoSchemaOption = !normalizedSearch
        || isSelectedLabel
        || 'no business schema'.includes(normalizedSearch);

    useEffect(() => setInputValue(selectedName), [selectedName]);

    const selectSchema = (optionValue: string | undefined) =>
    {
        if (!optionValue) return;
        const nextId = optionValue === NO_SCHEMA_OPTION ? undefined : optionValue;
        const nextName = nextId
            ? schemas.find(schema => schema.id === nextId)?.displayName ?? ''
            : '';
        setInputValue(nextName);
        onSchemaChange(nextId);
    };

    return (
        <Combobox
            id="exchange-initiation-schema-select"
            placeholder="Search schemas (optional)..."
            disabled={locked || schemas.length === 0}
            value={inputValue}
            selectedOptions={[schemaDefinitionId ?? NO_SCHEMA_OPTION]}
            onInput={event => setInputValue((event.target as HTMLInputElement).value)}
            onBlur={() => setInputValue(selectedName)}
            onOptionSelect={(_, data) => selectSchema(data.optionValue)}
        >
            {showNoSchemaOption && (
                <Option
                    id="exchange-initiation-schema-option-none"
                    value={NO_SCHEMA_OPTION}
                    text="No business schema"
                >
                    No business schema
                </Option>
            )}
            {filteredSchemas.map(schema => (
                <Option
                    id={`exchange-initiation-schema-option-${schema.id}`}
                    key={schema.id}
                    value={schema.id}
                    text={`${schema.displayName} (${schema.namespace}:${schema.schemaKey})`}
                >
                    {`${schema.displayName} (${schema.namespace}:${schema.schemaKey})`}
                </Option>
            ))}
            {!showNoSchemaOption && filteredSchemas.length === 0 && (
                <Option
                    id="exchange-initiation-schema-option-no-results"
                    value=""
                    disabled
                >
                    No matching schemas
                </Option>
            )}
        </Combobox>
    );
};

export default BusinessSchemaCombobox;
