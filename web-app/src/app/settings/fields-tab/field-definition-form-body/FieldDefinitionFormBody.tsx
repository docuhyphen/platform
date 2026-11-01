import {Field, InfoLabel, Input, Textarea} from '@fluentui/react-components';
import {typeSupportsOptions} from '../fieldLabels';
import {FieldForm} from '../field-definition-dialog/fieldDefinitionForm';
import OptionListEditor from '../option-list-editor/OptionListEditor';
import FieldDefinitionIdentityFields from './field-definition-identity-fields/FieldDefinitionIdentityFields';
import FieldDefinitionTypeFields from './field-definition-type-fields/FieldDefinitionTypeFields';
import {useFieldDefinitionFormBodyStyles} from './FieldDefinitionFormBodyStyles';

interface Props
{
    form: FieldForm;
    update: (patch: Partial<FieldForm>) => void;
    error: string | null;
    existingNamespaces?: string[];
}

const FieldDefinitionFormBody = ({form, update, error, existingNamespaces = []}: Props) =>
{
    const styles = useFieldDefinitionFormBodyStyles();

    return (
        <div id="field-definition-dialog-body"
             className={styles.drawerBody}>
            <FieldDefinitionIdentityFields form={form}
                                           update={update}
                                           existingNamespaces={existingNamespaces}/>
            <FieldDefinitionTypeFields form={form}
                                       update={update}/>
            <Field id={"field-definition-form-body-field-2"}
                   label="Label"
                   required>
                <Input id="field-def-label"
                       value={form.label}
                       placeholder="e.g. Customer reference"
                       onChange={(_, d) => update({label: d.value})}/>
            </Field>
            <Field id="field-def-help-field"
                   label={(
                       <InfoLabel id={"field-definition-form-body-info-label-4"}
                                  info="Optional guidance shown to people completing this field on an Exchange.">
                           Help text
                       </InfoLabel>
                   )}>
                <Textarea id="field-def-help"
                          value={form.helpText}
                          rows={2}
                          onChange={(_, d) => update({helpText: d.value})}/>
            </Field>
            {typeSupportsOptions(form.valueType) && (
                <Field id={"field-definition-form-body-field-3"}
                       label="Options">
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
