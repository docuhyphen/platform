import {useState} from 'react';
import {Button, Dropdown, Field, Option, Spinner} from '@fluentui/react-components';
import {ResponseError, SchemaDefinitionDto} from '../../../models/models';
import {assignExchangeSchema} from '../../../../services/fieldsService';
import {useExchangeFieldsTabStyles} from './ExchangeFieldsTabStyles';

interface Props
{
    exchangeId: string;
    schemas: SchemaDefinitionDto[];
    onAssigned: () => void;
}

const STALE_VERSION_CODE = 'FIELDS_PRECONDITION_STALE';
const ASSIGN_FAILED = 'Failed to assign schema';

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
        } catch (refusal: unknown)
        {
            const stated = refusal as ResponseError | null;
            if (stated?.reasonCode === STALE_VERSION_CODE) onAssigned();
            else setError(stated?.errorMessage?.trim() || ASSIGN_FAILED);
        }
        finally
        {
            setSaving(false);
        }
    };

    return (
        <div id="exchange-schema-assign-panel"
             className={styles.emptyState}>
            <div id="exchange-schema-assign-row"
                 className={styles.assignRow}>
                <Field id="exchange-schema-select-field"
                       label="Business schema"
                       className={styles.grow}>
                    <Dropdown id="exchange-schema-select"
                              placeholder="Select a schema..."
                              disabled={schemas.length === 0}
                              value={selectedName}
                              selectedOptions={selectedId ? [selectedId] : []}
                              onOptionSelect={(_, d) => setSelectedId(d.optionValue as string)}>
                        {schemas.map(schema => (
                            <Option id={`exchange-schema-option-${schema.id}`}
                                    key={schema.id}
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
                        icon={saving ? <Spinner id={"schema-assign-panel-assign-row-spinner"}
                                                size="tiny"/> : undefined}
                        onClick={handleAssign}>
                    Assign
                </Button>
            </div>
            {schemas.length === 0 && (
                <span id="exchange-schema-none-available"
                      className={styles.subText}>
                    No published schemas are available for this organization yet.
                </span>
            )}
            {error && (
                <span id="exchange-schema-assign-error"
                      className={styles.errorText}>
                    {error}
                </span>
            )}
        </div>
    );
};

export default SchemaAssignPanel;
