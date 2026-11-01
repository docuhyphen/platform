import {useEffect, useState} from 'react';
import {Field, Spinner, Text} from '@fluentui/react-components';
import {ResolvedSchemaViewDto, SchemaDefinitionDto, SchemaFieldBindingDto} from '../../../models/models';
import {getResolvedSchema} from '../../../../services/fieldsService';
import FieldValueEditor from '../../../exchanges/components/exchange-fields-tab/FieldValueEditor';
import {useExchangeInitiationFieldsTabStyles} from './ExchangeInitiationFieldsTabStyles';
import BusinessSchemaCombobox from './business-schema-combobox/BusinessSchemaCombobox';

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
            <div id={"exchange-initiation-fields-tab-div-1"}
                 className={styles.schemaRow}>
                <Field
                    id="exchange-initiation-schema-field"
                    label="Business schema"
                >
                    <BusinessSchemaCombobox
                        schemas={schemas}
                        schemaDefinitionId={schemaDefinitionId}
                        locked={locked}
                        onSchemaChange={onSchemaChange}
                    />
                </Field>
            </div>

            {error && <span id={"exchange-initiation-fields-tab-span-1"}
                            className={styles.errorText}>{error}</span>}

            {loading && <Spinner id={"exchange-initiation-fields-tab-spinner-1"}
                                 size="small"
                                 label="Loading schema fields..."/>}

            {!loading && schemaDefinitionId && bindings.length > 0 && (
                <div id={"exchange-initiation-fields-tab-div-2"}
                     className={styles.fieldList}>
                    {bindings.map(binding => (
                        <FieldValueEditor id={`exchange-initiation-fields-tab-field-value-editor-1-${binding.fieldContractId}`}
                                          key={binding.fieldContractId}
                                          binding={locked ? {...binding, isReadOnly: true} : binding}
                                          value={valueMap[binding.fieldContractId]}
                                          onChange={value => onValueChange(binding.fieldContractId, value)}/>
                    ))}
                </div>
            )}

            {!schemaDefinitionId && (
                <Text id={"exchange-initiation-fields-tab-text-1"}
                      className={styles.subText}>
                    Select a schema to classify this Exchange and capture its field values, or continue
                    without one.
                </Text>
            )}
        </div>
    );
};

export default ExchangeInitiationFieldsTab;
