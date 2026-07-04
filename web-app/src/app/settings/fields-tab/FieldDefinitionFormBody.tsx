import {Combobox, Dropdown, Field, Input, Option, Textarea} from '@fluentui/react-components';
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
                <Field label="Namespace"
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
                <Field label="Field key"
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
                <Field label="Classification"
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
            <Field label="Help text">
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
