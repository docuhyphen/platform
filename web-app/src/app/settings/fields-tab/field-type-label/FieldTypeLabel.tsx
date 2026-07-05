import type {ReactNode} from 'react';
import {Badge} from '@fluentui/react-components';
import {
    CalendarClockRegular,
    CalendarLtrRegular,
    DecimalArrowLeftRegular,
    MultiselectLtrRegular,
    NumberSymbolRegular,
    OptionsRegular,
    TextCaseTitleRegular,
    TextDescriptionRegular,
    ToggleLeftRegular,
} from '@fluentui/react-icons';
import {FieldValueType} from '../../../models/models';
import {VALUE_TYPE_LABELS} from '../fieldLabels';
import {useFieldTypeLabelStyles} from './FieldTypeLabelStyles';

const VALUE_TYPE_ICONS: Record<FieldValueType, ReactNode> = {
    [FieldValueType.SHORT_TEXT]: <TextCaseTitleRegular/>,
    [FieldValueType.LONG_TEXT]: <TextDescriptionRegular/>,
    [FieldValueType.BOOLEAN]: <ToggleLeftRegular/>,
    [FieldValueType.INTEGER]: <NumberSymbolRegular/>,
    [FieldValueType.DECIMAL]: <DecimalArrowLeftRegular/>,
    [FieldValueType.DATE]: <CalendarLtrRegular/>,
    [FieldValueType.DATE_TIME]: <CalendarClockRegular/>,
    [FieldValueType.SINGLE_SELECT]: <OptionsRegular/>,
    [FieldValueType.MULTI_SELECT]: <MultiselectLtrRegular/>,
};

interface Props
{
    id: string;
    valueType: FieldValueType;
}

const FieldTypeLabel = ({id, valueType}: Props) =>
{
    const styles = useFieldTypeLabelStyles();

    return (
        <Badge
            id={id}
            className={styles.root}
            appearance="outline"
            color="brand"
            size="small"
            icon={VALUE_TYPE_ICONS[valueType]}
        >
            {VALUE_TYPE_LABELS[valueType]}
        </Badge>
    );
};

export default FieldTypeLabel;
