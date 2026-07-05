import {Text} from '@fluentui/react-components';
import {FieldValueDto, SchemaFieldBindingDto} from '../../../models/models';
import {useExchangeFieldsTabStyles} from './ExchangeFieldsTabStyles';
import {formatFieldValue} from './fieldValueUtils';

interface Props
{
    bindings: SchemaFieldBindingDto[];
    values: FieldValueDto[];
}

const FieldValuesReadOnly = ({bindings, values}: Props) =>
{
    const styles = useExchangeFieldsTabStyles();

    const rows = bindings.length > 0
        ? bindings.map(binding => ({
            key: binding.fieldContractId,
            label: binding.label,
            display: formatFieldValue(
                binding.valueType,
                values.find(v => v.fieldContractId === binding.fieldContractId)?.value,
                binding.options,
            ),
        }))
        : values.map(value => ({
            key: value.fieldContractId,
            label: value.label,
            display: value.isEmpty ? '-' : String(value.value),
        }));

    return (
        <div className={styles.fieldGrid}>
            {rows.map(row => (
                <div key={row.key}
                     className={`${styles.fieldGridItem} ${styles.readOnlyRow}`}>
                    <Text className={styles.label}
                           size={200}>
                        {row.label}
                    </Text>
                    <Text>{row.display}</Text>
                </div>
            ))}
        </div>
    );
};

export default FieldValuesReadOnly;
