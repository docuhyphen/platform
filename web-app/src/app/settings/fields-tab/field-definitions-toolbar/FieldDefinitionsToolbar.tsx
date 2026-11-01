import {Field, SearchBox, Text} from '@fluentui/react-components';
import ViewModeToggle from '../../../components/view-mode-toggle/ViewModeToggle';
import {FieldDefinitionsList} from '../fieldsListTypes';
import FieldsFilterPopover from '../fields-filter-popover/FieldsFilterPopover';
import FieldsNamespaceFilterSection from '../fields-namespace-filter-section/FieldsNamespaceFilterSection';
import FieldValueTypeFilterSection from '../field-value-type-filter-section/FieldValueTypeFilterSection';
import FieldsSortMenu from '../fields-sort-menu/FieldsSortMenu';
import {useFieldDefinitionsToolbarStyles} from './FieldDefinitionsToolbarStyles';

interface Props
{
    list: FieldDefinitionsList;
}

const FieldDefinitionsToolbar = ({list}: Props) =>
{
    const styles = useFieldDefinitionsToolbarStyles();
    const {controls} = list;

    return (
        <>
            <Text id="fields-panel-description"
                  size={300}
                  className={styles.descriptionText}>
                Reusable business attributes. Compose them into schemas to attach to Exchanges.
            </Text>
            <div id={"fields-tab-div-3"}
                 className={styles.searchRow}>
                <div id={"fields-tab-div-4"}
                     className={styles.searchRowInputs}>
                    <Field id={"fields-tab-field-1"}
                           className={styles.searchField}>
                        <SearchBox id="field-def-search"
                                   placeholder="Search fields"
                                   maxLength={100}
                                   value={controls.search}
                                   onChange={(_, d) => controls.changeSearch(d.value)}/>
                    </Field>
                    <FieldsFilterPopover triggerId="field-def-filter-btn"
                                         surfaceId={"fields-tab-popover-surface-1"}
                                         tooltip="Filter fields"
                                         active={list.filtersActive}
                                         onClosed={() => controls.setFilterSearch('')}>
                        <FieldsNamespaceFilterSection variant="fields"
                                                      namespaces={list.filteredNamespaces}
                                                      selected={controls.filterNamespaces}
                                                      search={controls.filterSearch}
                                                      onSearchChange={controls.setFilterSearch}
                                                      onToggle={controls.toggleNamespace}/>
                        <hr id={"fields-tab-hr-1"}
                            className={styles.filterDivider}/>
                        <FieldValueTypeFilterSection selected={list.filterTypes}
                                                     onToggle={list.toggleType}/>
                    </FieldsFilterPopover>
                    <FieldsSortMenu variant="fields"
                                    sortOrder={controls.sortOrder}
                                    onChange={controls.changeSortOrder}/>
                </div>
                <ViewModeToggle value={controls.viewMode}
                                onChange={controls.setViewMode}/>
            </div>
        </>
    );
};

export default FieldDefinitionsToolbar;
