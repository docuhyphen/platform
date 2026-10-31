import {Button, Dropdown, Field, Input, Option} from '@fluentui/react-components';
import {DismissRegular} from '@fluentui/react-icons';
import {FieldDataClassification, SchemaFieldBindingDto} from '../../../models/models';
import FieldValueEditor from '../../../exchanges/components/exchange-fields-tab/FieldValueEditor';
import {CLASSIFICATION_LABELS} from '../fieldLabels';
import {BindingDraft} from '../schemaBindingDrafts';
import {defaultValueJsonFor, defaultValueOf} from './bindingDefaultValue';
import {useSchemaBindingSettingsStyles} from './SchemaBindingSettingsStyles';

interface Props {
    binding: BindingDraft;
    index: number;
    onChange: (patch: Partial<BindingDraft>) => void;
}

const SECTION_LIMIT = 128;
const CLASSIFICATIONS = Object.values(FieldDataClassification);

const defaultEditorBinding = (binding: BindingDraft): SchemaFieldBindingDto => ({
    id: `default-${binding.fieldContractId}`,
    fieldContractId: binding.fieldContractId,
    fieldDefinitionId: binding.fieldDefinitionId,
    namespace: '',
    fieldKey: binding.keyLabel,
    label: 'Default value',
    valueType: binding.valueType,
    displayOrder: 0,
    isRequired: false,
    isReadOnly: false,
    visibility: binding.visibility,
    helpText: binding.isReadOnly
        ? 'A read-only field gets its value only from this default.'
        : 'Recorded on each Exchange when this schema is assigned.',
    constraints: {},
    options: binding.options,
});

const SchemaBindingSettings = ({binding, index, onChange}: Props) => {
    const styles = useSchemaBindingSettingsStyles();

    return (
        <div id={`binding-settings-${index}`}
             className={styles.settings}>
            <div id={`binding-placement-${index}`}
                 className={styles.placement}>
                <Field id={`binding-section-field-${index}`}
                       label="Section"
                       hint="Fields with the same section are shown together.">
                    <Input id={`binding-section-${index}`}
                           value={binding.section ?? ''}
                           maxLength={SECTION_LIMIT}
                           placeholder="No section"
                           onChange={(_, data) => onChange({section: data.value === '' ? undefined : data.value})}/>
                </Field>
                <Field id={`binding-visibility-field-${index}`}
                       label="Visibility"
                       hint="People outside the owner's organization see only Public fields.">
                    <Dropdown id={`binding-visibility-${index}`}
                              value={CLASSIFICATION_LABELS[binding.visibility]}
                              selectedOptions={[binding.visibility]}
                              onOptionSelect={(_, data) => {
                                  const chosen = CLASSIFICATIONS.find(item => item === data.optionValue);
                                  if (chosen) onChange({visibility: chosen});
                              }}>
                        {CLASSIFICATIONS.map(classification => (
                            <Option id={`binding-visibility-${index}-${classification}`}
                                    key={classification}
                                    value={classification}>
                                {CLASSIFICATION_LABELS[classification]}
                            </Option>
                        ))}
                    </Dropdown>
                </Field>
            </div>
            <div id={`binding-default-row-${index}`}
                 className={styles.defaultRow}>
                <div id={`binding-default-editor-${index}`}
                     className={styles.defaultEditor}>
                    <FieldValueEditor id={`binding-default-${index}`}
                                      binding={defaultEditorBinding(binding)}
                                      value={defaultValueOf(binding.defaultValueJson)}
                                      onChange={value => onChange({
                                          defaultValueJson: defaultValueJsonFor(binding.valueType, value),
                                      })}/>
                </div>
                {binding.defaultValueJson !== undefined && (
                    <Button id={`binding-default-clear-${index}`}
                            appearance="subtle"
                            shape="circular"
                            size="small"
                            icon={<DismissRegular/>}
                            onClick={() => onChange({defaultValueJson: undefined})}>
                        Clear default
                    </Button>
                )}
            </div>
        </div>
    );
};

export default SchemaBindingSettings;
