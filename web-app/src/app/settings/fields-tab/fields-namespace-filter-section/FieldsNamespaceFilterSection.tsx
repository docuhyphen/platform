import {Checkbox, SearchBox, Text} from '@fluentui/react-components';
import {FieldsListVariant} from '../fieldsListTypes';
import {FIELDS_NAMESPACE_FILTER_IDS} from './fieldsNamespaceFilterIds';
import {useFieldsNamespaceFilterSectionStyles} from './FieldsNamespaceFilterSectionStyles';

interface Props
{
    variant: FieldsListVariant;
    namespaces: string[];
    selected: Set<string>;
    search: string;
    onSearchChange: (value: string) => void;
    onToggle: (namespace: string) => void;
}

const FieldsNamespaceFilterSection = ({variant, namespaces, selected, search, onSearchChange, onToggle}: Props) =>
{
    const styles = useFieldsNamespaceFilterSectionStyles();
    const ids = FIELDS_NAMESPACE_FILTER_IDS[variant];

    return (
        <div id={ids.section}
             className={styles.filterSection}>
            <Text id={ids.title}
                  className={styles.filterSectionTitle}>Namespace</Text>
            <SearchBox id={ids.search}
                       placeholder="Search namespaces"
                       size="small"
                       value={search}
                       onChange={(_, d) => onSearchChange(d.value)}/>
            <div id={ids.list}
                 className={styles.filterSectionList}>
                {namespaces.map(ns => (
                    <Checkbox id={`${ids.checkboxPrefix}-${ns}`}
                              key={ns}
                              label={ns}
                              checked={selected.has(ns)}
                              onChange={() => onToggle(ns)}/>
                ))}
                {namespaces.length === 0 && (
                    <Text id={ids.empty}
                          size={200}
                          className={styles.emptyText}>
                        No namespaces
                    </Text>
                )}
            </div>
        </div>
    );
};

export default FieldsNamespaceFilterSection;
