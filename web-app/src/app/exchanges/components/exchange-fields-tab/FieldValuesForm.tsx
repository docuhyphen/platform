import {useMemo, useState} from 'react';
import {Badge, Button, Spinner, Text} from '@fluentui/react-components';
import {FieldValueDto, SchemaFieldBindingDto} from '../../../models/models';
import {setExchangeFieldValues} from '../../../../services/fieldsService';
import {useExchangeFieldsTabStyles} from './ExchangeFieldsTabStyles';
import {toCanonicalValue} from './fieldValueUtils';
import FieldCard from './FieldCard';
import FieldValueEditor from './FieldValueEditor';
import {
    groupBindingsBySection,
    toFieldElementId,
} from './fieldLayoutUtils';

interface Props
{
    exchangeId: string;
    bindings: SchemaFieldBindingDto[];
    values: FieldValueDto[];
    onSaved: () => void;
}

const FieldValuesForm = ({exchangeId, bindings, values, onSaved}: Props) =>
{
    const styles = useExchangeFieldsTabStyles();
    const sections = useMemo(() => groupBindingsBySection(bindings), [bindings]);
    const initial = useMemo(() =>
    {
        const map: Record<string, unknown> = {};
        bindings.forEach(binding =>
        {
            const existing = values.find(v => v.fieldContractId === binding.fieldContractId);
            map[binding.fieldContractId] = existing && !existing.isEmpty ? existing.value : undefined;
        });
        return map;
    }, [bindings, values]);

    const [state, setState] = useState<Record<string, unknown>>(initial);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const setValue = (fieldContractId: string, value: unknown) =>
        setState(prev => ({...prev, [fieldContractId]: value}));

    const handleSave = async () =>
    {
        setSaving(true);
        setError(null);
        const entries = bindings.map(binding => ({
            fieldContractId: binding.fieldContractId,
            value: toCanonicalValue(binding.valueType, state[binding.fieldContractId]),
        }));
        try
        {
            await setExchangeFieldValues(exchangeId, {values: entries});
            onSaved();
        }
        catch (e: unknown)
        {
            const msg = typeof e === 'object' && e !== null && 'error' in e
                ? String((e as {error: string}).error)
                : 'Failed to save values';
            setError(msg);
        }
        finally
        {
            setSaving(false);
        }
    };

    return (
        <div id="exchange-fields-form-sections"
             className={styles.fieldForm}>
            <div id="exchange-fields-scrollable-sections"
                 className={styles.scrollableFieldSections}>
                {sections.map(section => (
                    <div id={`exchange-field-section-${section.key}`}
                         key={section.key}
                         className={styles.sectionBlock}>
                        <div className={styles.fieldLane}>
                            {section.bindings.map(binding => (
                                <FieldCard id={`exchange-field-card-${toFieldElementId(binding.fieldContractId)}`}
                                           key={binding.fieldContractId}
                                           title={binding.label}
                                           description={binding.description ?? binding.helpText}
                                           valueType={binding.valueType}
                                           required={binding.isRequired}
                                           readOnly={binding.isReadOnly}>
                                    <FieldValueEditor binding={binding}
                                                      value={state[binding.fieldContractId]}
                                                      onChange={value => setValue(binding.fieldContractId, value)}
                                                      showLabel={false}/>
                                </FieldCard>
                            ))}
                        </div>
                    </div>
                ))}
                {error && <span className={styles.errorText}>{error}</span>}
            </div>
            <div className={styles.buttonRow}>
                <Button id="exchange-fields-save-btn"
                        appearance="primary"
                        shape="circular"
                        disabled={saving}
                        icon={saving ? <Spinner size="tiny"/> : undefined}
                        onClick={handleSave}>
                    {saving ? 'Saving...' : 'Save values'}
                </Button>
            </div>
        </div>
    );
};

export default FieldValuesForm;
