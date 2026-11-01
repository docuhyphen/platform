import {Dropdown, Field, InfoLabel, Option} from '@fluentui/react-components';
import {FieldDataClassification, FieldValueType} from '../../../../models/models';
import {CLASSIFICATION_LABELS, VALUE_TYPE_LABELS} from '../../fieldLabels';
import {FieldForm} from '../../field-definition-dialog/fieldDefinitionForm';
import {useFieldDefinitionTypeFieldsStyles} from './FieldDefinitionTypeFieldsStyles';

interface Props
{
    form: FieldForm;
    update: (patch: Partial<FieldForm>) => void;
}

const FieldDefinitionTypeFields = ({form, update}: Props) =>
{
    const styles = useFieldDefinitionTypeFieldsStyles();

    return (
        <div id={"field-definition-form-body-div-2"}
             className={styles.twoColumn}>
            <Field id={"field-definition-form-body-field-1"}
                   label="Type"
                   className={styles.grow}>
                <Dropdown id="field-def-type"
                          value={VALUE_TYPE_LABELS[form.valueType]}
                          selectedOptions={[form.valueType]}
                          onOptionSelect={(_, d) => update({valueType: d.optionValue as FieldValueType})}>
                    {Object.values(FieldValueType).map(type => (
                        <Option id={`field-definition-form-body-option-2-${type}`}
                                key={type}
                                value={type}>
                            {VALUE_TYPE_LABELS[type]}
                        </Option>
                    ))}
                </Dropdown>
            </Field>
            <Field id="field-def-classification-field"
                   label={(
                       <InfoLabel id={"field-definition-form-body-info-label-3"}
                                  info="Sets the field's default sensitivity when it is added to a schema. Classification does not grant access by itself.">
                           Classification
                       </InfoLabel>
                   )}
                   className={styles.grow}>
                <Dropdown id="field-def-classification"
                          value={CLASSIFICATION_LABELS[form.classification]}
                          selectedOptions={[form.classification]}
                          onOptionSelect={(_, d) => update({classification: d.optionValue as FieldDataClassification})}>
                    {Object.values(FieldDataClassification).map(c => (
                        <Option id={`field-definition-form-body-option-3-${c}`}
                                key={c}
                                value={c}>
                            {CLASSIFICATION_LABELS[c]}
                        </Option>
                    ))}
                </Dropdown>
            </Field>
        </div>
    );
};

export default FieldDefinitionTypeFields;
