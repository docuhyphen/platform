import {FieldValueDto, FieldValueType, SchemaFieldBindingDto} from '../../../models/models';
import {useExchangeFieldsTabStyles} from './ExchangeFieldsTabStyles';
import FieldCard from './FieldCard';
import FieldValueDisplay from './FieldValueDisplay';
import {
    DEFAULT_FIELD_SECTION_TITLE,
    groupBindingsBySection,
    shouldFieldSpanWide,
    toFieldElementId,
} from './fieldLayoutUtils';

interface Props
{
    bindings: SchemaFieldBindingDto[];
    values: FieldValueDto[];
}

const FieldValuesReadOnly = ({bindings, values}: Props) =>
{
    const styles = useExchangeFieldsTabStyles();
    const valueLookup = new Map(values.map(value => [value.fieldContractId, value]));

    const sections = bindings.length > 0
        ? groupBindingsBySection(bindings)
        : [{
            key: 'all-fields',
            title: DEFAULT_FIELD_SECTION_TITLE,
            bindings: values.map((value, index) => ({
                id: `field-value-${index}`,
                fieldContractId: value.fieldContractId,
                fieldDefinitionId: value.fieldContractId,
                namespace: value.namespace,
                fieldKey: value.fieldKey,
                label: value.label,
                valueType: value.valueType,
                displayOrder: index,
                section: DEFAULT_FIELD_SECTION_TITLE,
                isRequired: false,
                isReadOnly: true,
                visibility: 'INTERNAL',
                constraints: {},
                options: [],
            })),
        }];

    const rows = bindings.length > 0
        ? bindings.map(binding => ({
            key: binding.fieldContractId,
            label: binding.label,
            binding,
            value: valueLookup.get(binding.fieldContractId)?.value,
            isEmpty: valueLookup.get(binding.fieldContractId)?.isEmpty ?? true,
        }))
        : values.map(value => ({
            key: value.fieldContractId,
            label: value.label,
            binding: {
                id: value.fieldContractId,
                fieldContractId: value.fieldContractId,
                fieldDefinitionId: value.fieldContractId,
                namespace: value.namespace,
                fieldKey: value.fieldKey,
                label: value.label,
                valueType: value.valueType,
                displayOrder: 0,
                section: DEFAULT_FIELD_SECTION_TITLE,
                isRequired: false,
                isReadOnly: true,
                visibility: 'INTERNAL',
                constraints: {},
                options: [],
            } satisfies SchemaFieldBindingDto,
            value: value.value,
            isEmpty: value.isEmpty,
        }));

    return (
        <div id="exchange-fields-readonly-sections"
             className={styles.fieldList}>
            {sections.map(section => (
                <div id={`exchange-field-section-${section.key}`}
                     key={section.key}
                     className={styles.sectionBlock}>
                    <div className={styles.fieldLane}>
                        {section.bindings.map(binding =>
                        {
                            const row = rows.find(item => item.key === binding.fieldContractId);
                            const displayValue = row?.value;
                            const valueType = row?.binding.valueType ?? FieldValueType.SHORT_TEXT;

                            return (
                                <FieldCard id={`exchange-field-card-${toFieldElementId(binding.fieldContractId)}`}
                                           key={binding.fieldContractId}
                                           title={binding.label}
                                           description={binding.description ?? binding.helpText}
                                           valueType={valueType}
                                           wide={shouldFieldSpanWide(valueType, displayValue)}>
                                    <FieldValueDisplay valueType={valueType}
                                                       value={displayValue}
                                                       options={binding.options}
                                                       isEmpty={row?.isEmpty}/>
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
