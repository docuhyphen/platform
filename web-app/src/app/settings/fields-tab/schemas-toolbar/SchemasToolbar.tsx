import {Field, SearchBox, Text} from '@fluentui/react-components';
import ViewModeToggle from '../../../components/view-mode-toggle/ViewModeToggle';
import {SchemasList} from '../fieldsListTypes';
import FieldsFilterPopover from '../fields-filter-popover/FieldsFilterPopover';
import FieldsNamespaceFilterSection from '../fields-namespace-filter-section/FieldsNamespaceFilterSection';
import FieldsSortMenu from '../fields-sort-menu/FieldsSortMenu';
import {useSchemasToolbarStyles} from './SchemasToolbarStyles';

interface Props
{
    list: SchemasList;
}

const SchemasToolbar = ({list}: Props) =>
{
    const styles = useSchemasToolbarStyles();
    const {controls} = list;

    return (
        <>
            <Text id="schemas-panel-description"
                  size={300}
                  className={styles.descriptionText}>
                Business schemas group fields into a case type that Exchange creators can select.
            </Text>
            <div id={"fields-tab-div-9"}
                 className={styles.searchRow}>
                <div id={"fields-tab-div-10"}
                     className={styles.searchRowInputs}>
                    <Field id={"fields-tab-field-2"}
                           className={styles.searchField}>
                        <SearchBox id="schema-search"
                                   placeholder="Search schemas"
                                   maxLength={100}
                                   value={controls.search}
                                   onChange={(_, d) => controls.changeSearch(d.value)}/>
                    </Field>
                    {list.namespaces.length > 0 && (
                        <FieldsFilterPopover triggerId="schema-filter-btn"
                                             surfaceId={"fields-tab-popover-surface-2"}
                                             tooltip="Filter schemas"
                                             active={list.filtersActive}
                                             onClosed={() => controls.setFilterSearch('')}>
                            <FieldsNamespaceFilterSection variant="schemas"
                                                          namespaces={list.filteredNamespaces}
                                                          selected={controls.filterNamespaces}
                                                          search={controls.filterSearch}
                                                          onSearchChange={controls.setFilterSearch}
                                                          onToggle={controls.toggleNamespace}/>
                        </FieldsFilterPopover>
                    )}
                    <FieldsSortMenu variant="schemas"
                                    sortOrder={controls.sortOrder}
                                    onChange={controls.changeSortOrder}/>
                </div>
                <ViewModeToggle value={controls.viewMode}
                                onChange={controls.setViewMode}/>
            </div>
        </>
    );
};

export default SchemasToolbar;
