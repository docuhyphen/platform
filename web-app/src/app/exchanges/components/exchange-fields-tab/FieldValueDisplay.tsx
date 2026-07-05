import {Badge, Text} from '@fluentui/react-components';
import {FieldOption, FieldValueType} from '../../../models/models';
import {useExchangeFieldsTabStyles} from './ExchangeFieldsTabStyles';
import {formatFieldValue, getFieldValueLabels, isFieldValueEmpty} from './fieldValueUtils';

interface FieldValueDisplayProps
{
    valueType: FieldValueType;
    value: unknown;
    options: FieldOption[];
    isEmpty?: boolean;
}

const FieldValueDisplay = ({valueType, value, options, isEmpty}: FieldValueDisplayProps) =>
{
    const styles = useExchangeFieldsTabStyles();
    const empty = isEmpty ?? isFieldValueEmpty(value);

    if (empty)
    {
        return (
            <Text id="exchange-field-empty-value"
                  className={styles.fieldValuePlaceholder}>
                Not provided
            </Text>
        );
    }

    if (valueType === FieldValueType.BOOLEAN)
    {
        return (
            <div>
                <Badge id="exchange-field-boolean-badge"
                       appearance="tint"
                       color={value === true ? 'success' : 'subtle'}
                       size="medium">
                    {value === true ? 'Yes' : 'No'}
                </Badge>
            </div>
        );
    }

    if (valueType === FieldValueType.SINGLE_SELECT)
    {
        const labels = getFieldValueLabels(valueType, value, options);

        return (
            <div id="exchange-field-chip-row"
                 className={styles.valueBadgeRow}>
                {labels.map(label => (
                    <Badge key={label}
                           appearance="outline"
                           color="informative"
                           size="medium">
                        {label}
                    </Badge>
                ))}
            </div>
        );
    }

    if (valueType === FieldValueType.MULTI_SELECT)
    {
        const labels = getFieldValueLabels(valueType, value, options);

        return (
            <div id="exchange-field-chip-row"
                 className={styles.valueBadgeRow}>
                {labels.map(label => (
                    <Badge key={label}
                           appearance="outline"
                           color="informative"
                           size="medium">
                        {label}
                    </Badge>
                ))}
            </div>
        );
    }

    return (
        <Text id="exchange-field-value-text"
              className={styles.fieldValueText}>
            {formatFieldValue(valueType, value, options)}
        </Text>
    );
};

export default FieldValueDisplay;
