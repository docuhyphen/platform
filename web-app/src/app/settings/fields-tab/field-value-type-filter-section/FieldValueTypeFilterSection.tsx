import {Checkbox, Text} from '@fluentui/react-components';
import {FieldValueType} from '../../../models/models';
import {VALUE_TYPE_LABELS} from '../fieldLabels';
import {useFieldValueTypeFilterSectionStyles} from './FieldValueTypeFilterSectionStyles';

interface Props
{
    selected: Set<FieldValueType>;
    onToggle: (type: FieldValueType) => void;
}

const FieldValueTypeFilterSection = ({selected, onToggle}: Props) =>
{
    const styles = useFieldValueTypeFilterSectionStyles();

    return (
        <div id={"fields-tab-div-7"}
             className={styles.filterSection}>
            <Text id={"fields-tab-text-3"}
                  className={styles.filterSectionTitle}>Field type</Text>
            <div id={"fields-tab-div-8"}
                 className={styles.filterSectionList}>
                {Object.values(FieldValueType).map(type => (
                    <Checkbox id={`fields-tab-checkbox-2-${type}`}
                              key={type}
                              label={VALUE_TYPE_LABELS[type]}
                              checked={selected.has(type)}
                              onChange={() => onToggle(type)}/>
                ))}
            </div>
        </div>
    );
};

export default FieldValueTypeFilterSection;
