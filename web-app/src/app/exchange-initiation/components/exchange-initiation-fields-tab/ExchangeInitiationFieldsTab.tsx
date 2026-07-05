import {useEffect, useState} from 'react';
import {Dropdown, Field, Option, Spinner, Text} from '@fluentui/react-components';
import {ResolvedSchemaViewDto, SchemaDefinitionDto, SchemaFieldBindingDto} from '../../../models/models';
import {getResolvedSchema} from '../../../../services/fieldsService';
import FieldValueEditor from '../../../exchanges/components/exchange-fields-tab/FieldValueEditor';
import {useExchangeInitiationFieldsTabStyles} from './ExchangeInitiationFieldsTabStyles';

interface Props
{
    schemas: SchemaDefinitionDto[];
    schemaDefinitionId?: string;
    onSchemaChange: (schemaDefinitionId: string | undefined) => void;
    bindings: SchemaFieldBindingDto[];
    onBindingsLoaded: (bindings: SchemaFieldBindingDto[]) => void;
    valueMap: Record<string, unknown>;
    onValueChange: (fieldContractId: string, value: unknown) => void;
    locked?: boolean;
}

/**
 * Optional wizard step to classify a new Exchange with a published business schema and enter its
 * typed field values. Reuses the Fields tab's FieldValueEditor so the value controls stay identical.
 * Values are collected into wizard state and applied by the backend during creation (before any
 * workflow fires); this component performs no API writes of its own.
 */
const ExchangeInitiationFieldsTab = (
    {
        schemas,
        schemaDefinitionId,
        onSchemaChange,
        bindings,
        onBindingsLoaded,
        valueMap,
        onValueChange,
        locked,
    }: Props) =>
{
    const styles = useExchangeInitiationFieldsTabStyles();
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const selectedName = schemas.find(s => s.id === schemaDefinitionId)?.displayName ?? '';

    useEffect(() =>
    {
        if (!schemaDefinitionId)
        {
            onBindingsLoaded([]);
            return;
        }
        let active = true;
        setLoading(true);
        setError(null);
        getResolvedSchema(schemaDefinitionId)
            .then((view: ResolvedSchemaViewDto) => { if (active) onBindingsLoaded(view.fields); })
            .catch(() =>
            {
                if (active)
                {
                    setError('Failed to load schema fields');
                    onBindingsLoaded([]);
                }
            })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [schemaDefinitionId]);

    return (
        <div id="exchange-initiation-fields-tab-content"
             className={styles.container}>
            <div className={styles.schemaRow}>
                <Field label="Business schema">
                    <Dropdown id="exchange-initiation-schema-select"
                              placeholder="Select a schema (optional)..."
                              disabled={locked || schemas.length === 0}
                              value={selectedName}
                              selectedOptions={schemaDefinitionId ? [schemaDefinitionId] : []}
                              onOptionSelect={(_, d) => onSchemaChange(d.optionValue as string | undefined)}>
                        {schemas.map(schema => (
                            <Option key={schema.id}
                                    value={schema.id}>
                                {`${schema.displayName} (${schema.namespace}:${schema.schemaKey})`}
                            </Option>
                        ))}
                    </Dropdown>
                </Field>
            </div>

            {error && <span className={styles.errorText}>{error}</span>}

            {loading && <Spinner size="small" label="Loading schema fields..."/>}

            {!loading && schemaDefinitionId && bindings.length > 0 && (
                <div className={styles.fieldList}>
                    {bindings.map(binding => (
                        <FieldValueEditor key={binding.fieldContractId}
                                          binding={locked ? {...binding, isReadOnly: true} : binding}
                                          value={valueMap[binding.fieldContractId]}
                                          onChange={value => onValueChange(binding.fieldContractId, value)}/>
                    ))}
                </div>
            )}

            {!schemaDefinitionId && (
                <Text className={styles.subText}>
                    Select a schema to classify this Exchange and capture its field values, or continue
                    without one.
                </Text>
            )}
        </div>
    );
};

export default ExchangeInitiationFieldsTab;
