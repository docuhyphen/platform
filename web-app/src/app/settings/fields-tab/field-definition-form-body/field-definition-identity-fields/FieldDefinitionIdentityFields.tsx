import {Combobox, Field, InfoLabel, Input, Option} from '@fluentui/react-components';
import {FieldForm} from '../../field-definition-dialog/fieldDefinitionForm';
import {useFieldDefinitionIdentityFieldsStyles} from './FieldDefinitionIdentityFieldsStyles';

interface Props
{
    form: FieldForm;
    update: (patch: Partial<FieldForm>) => void;
    existingNamespaces: string[];
}

const FieldDefinitionIdentityFields = ({form, update, existingNamespaces}: Props) =>
{
    const styles = useFieldDefinitionIdentityFieldsStyles();

    return (
        <div id={"field-definition-form-body-div-1"}
             className={styles.twoColumn}>
            <Field id="field-def-namespace-field"
                   label={(
                       <InfoLabel id={"field-definition-form-body-info-label-1"}
                                  info="Groups related fields and forms the first part of the stable field identifier. Select an existing namespace or enter one using lowercase letters, numbers, and hyphens.">
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
                        <Option id={`field-definition-form-body-option-1-${ns}`}
                                key={ns}
                                value={ns}>
                            {ns}
                        </Option>
                    ))}
                </Combobox>
            </Field>
            <Field id="field-def-key-field"
                   label={(
                       <InfoLabel id={"field-definition-form-body-info-label-2"}
                                  info="Uniquely identifies this field within its namespace. Use lowercase letters, numbers, and hyphens because the key remains stable after creation.">
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
    );
};

export default FieldDefinitionIdentityFields;
