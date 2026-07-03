import {useState} from 'react';
import {Button, Dropdown, Field, Option, Spinner} from '@fluentui/react-components';
import {SchemaDefinitionDto} from '../../../models/models';
import {assignExchangeSchema} from '../../../../services/fieldsService';
import {useExchangeFieldsTabStyles} from './ExchangeFieldsTabStyles';

interface Props
{
    exchangeId: string;
    schemas: SchemaDefinitionDto[];
    onAssigned: () => void;
}

const SchemaAssignPanel = ({exchangeId, schemas, onAssigned}: Props) =>
{
    const styles = useExchangeFieldsTabStyles();
    const [selectedId, setSelectedId] = useState<string>('');
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const selectedName = schemas.find(s => s.id === selectedId)?.displayName ?? '';

    const handleAssign = async () =>
    {
        if (!selectedId) return;
        setSaving(true);
        setError(null);
        try
        {
            await assignExchangeSchema(exchangeId, {schemaDefinitionId: selectedId});
            onAssigned();
        }
        catch (e: unknown)
        {
            const msg = typeof e === 'object' && e !== null && 'error' in e
                ? String((e as {error: string}).error)
                : 'Failed to assign schema';
            setError(msg);
        }
        finally
        {
            setSaving(false);
        }
    };

    return (
        <div className={styles.emptyState}>
            <div className={styles.assignRow}>
                <Field label="Business schema"
                       className={styles.grow}>
                    <Dropdown id="exchange-schema-select"
                              placeholder="Select a schema..."
                              disabled={schemas.length === 0}
                              value={selectedName}
                              selectedOptions={selectedId ? [selectedId] : []}
                              onOptionSelect={(_, d) => setSelectedId(d.optionValue as string)}>
                        {schemas.map(schema => (
                            <Option key={schema.id}
                                    value={schema.id}>
                                {`${schema.displayName} (${schema.namespace}:${schema.schemaKey})`}
                            </Option>
                        ))}
                    </Dropdown>
                </Field>
                <Button id="exchange-schema-assign-btn"
                        appearance="primary"
                        shape="circular"
                        disabled={!selectedId || saving}
                        icon={saving ? <Spinner size="tiny"/> : undefined}
                        onClick={handleAssign}>
                    Assign
                </Button>
            </div>
            {schemas.length === 0 && (
                <span className={styles.subText}>
                    No published schemas are available for this organization yet.
                </span>
            )}
            {error && <span className={styles.errorText}>{error}</span>}
        </div>
    );
};

export default SchemaAssignPanel;
