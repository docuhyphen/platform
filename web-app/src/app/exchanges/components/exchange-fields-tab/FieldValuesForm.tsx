import {useMemo, useState} from 'react';
import {Button, Spinner} from '@fluentui/react-components';
import {FieldValueDto, SchemaFieldBindingDto} from '../../../models/models';
import {useExchangeFieldsTabStyles} from './ExchangeFieldsTabStyles';
import {buildSparseFieldValuePayload} from './fieldValuePayload';
import {mergeFieldValueEdits, storedFieldValues} from './fieldEditorState';
import {useFieldValuesSave} from './useFieldValuesSave';
import FieldCard from './FieldCard';
import FieldValueEditor from './FieldValueEditor';
import {groupBindingsBySection, toFieldElementId,} from './fieldLayoutUtils';

interface Props
{
    exchangeId: string;
    bindings: SchemaFieldBindingDto[];
    values: FieldValueDto[];
    /**
     * Validator of the version the values were served in, sent back with a save so one built on
     * values that have since changed is refused rather than applied.
     */
    valuesETag?: string;
    /** Take a fresh reading of the stored values: they have just changed, or were found to have. */
    onValuesChanged: () => void;
}

const FieldValuesForm = ({exchangeId, bindings, values, valuesETag, onValuesChanged}: Props) =>
{
    const styles = useExchangeFieldsTabStyles();
    const sections = useMemo(() => groupBindingsBySection(bindings), [bindings]);
    const stored = useMemo(() => storedFieldValues(bindings, values), [bindings, values]);

    const [edits, setEdits] = useState<Record<string, unknown>>({});
    const shown = useMemo(() => mergeFieldValueEdits(stored, edits), [stored, edits]);
    const {saving, error, save} = useFieldValuesSave(exchangeId, valuesETag, onValuesChanged);

    const setValue = (fieldContractId: string, value: unknown) =>
        setEdits(prev => ({...prev, [fieldContractId]: value}));

    const handleSave = () => save(buildSparseFieldValuePayload(bindings, values, shown));

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
                                           required={binding.isRequired}
                                           readOnly={binding.isReadOnly}>
                                    <FieldValueEditor binding={binding}
                                                      value={shown[binding.fieldContractId]}
                                                      onChange={value => setValue(binding.fieldContractId, value)}
                                                      showLabel={false}/>
                                </FieldCard>
                            ))}
                        </div>
                    </div>
                ))}
                {error && (
                    <span id="exchange-fields-save-error"
                          className={styles.errorText}>
                        {error}
                    </span>
                )}
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
