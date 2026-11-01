import {FieldValueDto, SchemaFieldBindingDto} from '../../../models/models';
import {useExchangeFieldsTabStyles} from './ExchangeFieldsTabStyles';
import FieldCard from './FieldCard';
import FieldValueDisplay from './FieldValueDisplay';
import {groupBindingsBySection, toFieldElementId} from './fieldLayoutUtils';

interface Props
{
    bindings: SchemaFieldBindingDto[];
    values: FieldValueDto[];
}

const FieldValuesReadOnly = ({bindings, values}: Props) =>
{
    const styles = useExchangeFieldsTabStyles();
    const valueLookup = new Map(values.map(value => [value.fieldContractId, value]));
    const sections = groupBindingsBySection(bindings);

    return (
        <div id="exchange-fields-readonly-sections"
             className={`${styles.fieldList} ${styles.scrollableFieldSections}`}>
            {sections.map(section => (
                <div id={`exchange-field-section-${section.key}`}
                     key={section.key}
                     className={styles.sectionBlock}>
                    <div id={"field-values-read-only-div-1"}
                         className={styles.fieldLane}>
                        {section.bindings.map(binding =>
                        {
                            const held = valueLookup.get(binding.fieldContractId);

                            return (
                                <FieldCard id={`exchange-field-card-${toFieldElementId(binding.fieldContractId)}`}
                                           key={binding.fieldContractId}
                                           title={binding.label}
                                           description={binding.description ?? binding.helpText}>
                                    <FieldValueDisplay valueType={binding.valueType}
                                                       value={held?.value}
                                                       options={binding.options}
                                                       isEmpty={held?.isEmpty ?? true}/>
                                </FieldCard>
                            );
                        })}
                    </div>
                </div>
            ))}
        </div>
    );
};

export default FieldValuesReadOnly;
