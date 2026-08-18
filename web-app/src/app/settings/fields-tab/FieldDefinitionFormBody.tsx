import {Combobox, Dropdown, Field, InfoLabel, Input, Option, Textarea} from '@fluentui/react-components';
import {FieldDataClassification, FieldOption, FieldValueType} from '../../models/models';
import {useFieldsTabStyles} from './FieldsTabStyles';
import {CLASSIFICATION_LABELS, typeSupportsOptions, VALUE_TYPE_LABELS} from './fieldLabels';
import OptionListEditor from './OptionListEditor';

export interface FieldForm
{
    namespace: string;
    fieldKey: string;
    valueType: FieldValueType;
    label: string;
    helpText: string;
    classification: FieldDataClassification;
    options: FieldOption[];
}

interface Props
{
    form: FieldForm;
    update: (patch: Partial<FieldForm>) => void;
    error: string | null;
    existingNamespaces?: string[];
}

const FieldDefinitionFormBody = ({form, update, error, existingNamespaces = []}: Props) =>
{
    const styles = useFieldsTabStyles();

    return (
        <div id="field-definition-dialog-body"
             className={styles.drawerBody}>
            <div className={styles.twoColumn}>
                <Field id="field-def-namespace-field"
                       label={(
                           <InfoLabel info="Groups related fields and forms the first part of the stable field identifier. Select an existing namespace or enter one using lowercase letters, numbers, and hyphens.">
                               Namespace
                           </InfoLabel>
                       )}
                       required
                       className={styles.grow}>
                    <Combobox id="field-def-namespace"
                              freeform
                              value={form.namespace}
                              placeholder="e.g. common"
                              onInput={(e) => update({namespace: (e.target as HTMLInputElement).value})}
                              onOptionSelect={(_, d) => update({namespace: d.optionValue ?? ''})}>
                        {existingNamespaces.map(ns => (
                            <Option key={ns}
                                    value={ns}>
                                {ns}
                            </Option>
                        ))}
                    </Combobox>
                </Field>
                <Field id="field-def-key-field"
                       label={(
                           <InfoLabel info="Uniquely identifies this field within its namespace. Use lowercase letters, numbers, and hyphens because the key remains stable after creation.">
                               Field key
                           </InfoLabel>
                       )}
                       required
                       className={styles.grow}>
                    <Input id="field-def-key"
                           value={form.fieldKey}
                           placeholder="e.g. customer-reference"
                           onChange={(_, d) => update({fieldKey: d.value})}/>
                </Field>
            </div>
            <div className={styles.twoColumn}>
                <Field label="Type"
                       className={styles.grow}>
                    <Dropdown id="field-def-type"
                              value={VALUE_TYPE_LABELS[form.valueType]}
                              selectedOptions={[form.valueType]}
                              onOptionSelect={(_, d) => update({valueType: d.optionValue as FieldValueType})}>
                        {Object.values(FieldValueType).map(type => (
                            <Option key={type}
                                    value={type}>
                                {VALUE_TYPE_LABELS[type]}
                            </Option>
                        ))}
                    </Dropdown>
                </Field>
                <Field id="field-def-classification-field"
                       label={(
                           <InfoLabel info="Sets the field's default sensitivity when it is added to a schema. Classification does not grant access by itself.">
                               Classification
                           </InfoLabel>
                       )}
                       className={styles.grow}>
                    <Dropdown id="field-def-classification"
                              value={CLASSIFICATION_LABELS[form.classification]}
                              selectedOptions={[form.classification]}
                              onOptionSelect={(_, d) => update({classification: d.optionValue as FieldDataClassification})}>
                        {Object.values(FieldDataClassification).map(c => (
                            <Option key={c}
                                    value={c}>
                                {CLASSIFICATION_LABELS[c]}
                            </Option>
                        ))}
                    </Dropdown>
                </Field>
            </div>
            <Field label="Label"
                   required>
                <Input id="field-def-label"
                       value={form.label}
                       placeholder="e.g. Customer reference"
                       onChange={(_, d) => update({label: d.value})}/>
            </Field>
            <Field id="field-def-help-field"
                   label={(
                       <InfoLabel info="Optional guidance shown to people completing this field on an Exchange.">
                           Help text
                       </InfoLabel>
                   )}>
                <Textarea id="field-def-help"
                          value={form.helpText}
                          rows={2}
                          onChange={(_, d) => update({helpText: d.value})}/>
            </Field>
            {typeSupportsOptions(form.valueType) && (
                <Field label="Options">
                    <OptionListEditor options={form.options}
                                      onChange={options => update({options})}/>
                </Field>
            )}
            {error && (
                <span id="field-def-error"
                      className={styles.errorText}>
                    {error}
                </span>
            )}
        </div>
    );
};

export default FieldDefinitionFormBody;
