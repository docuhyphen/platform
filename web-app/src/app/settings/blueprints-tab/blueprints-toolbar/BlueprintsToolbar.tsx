import {Field, SearchBox} from '@fluentui/react-components';
import {ViewMode} from '../../../models/models.tsx';
import ViewModeToggle from '../../../components/view-mode-toggle/ViewModeToggle.tsx';
import {BlueprintListFilters} from '../useBlueprintListFilters.ts';
import BlueprintsTagFilter from './blueprints-tag-filter/BlueprintsTagFilter.tsx';
import BlueprintsSortMenu from './blueprints-sort-menu/BlueprintsSortMenu.tsx';
import {useBlueprintsToolbarStyles} from './BlueprintsToolbarStyles.tsx';

interface BlueprintsToolbarProps
{
    filters: BlueprintListFilters;
    viewMode: ViewMode;
    onViewModeChange: (mode: ViewMode) => void;
}

const BlueprintsToolbar = ({filters, viewMode, onViewModeChange}: BlueprintsToolbarProps) =>
{
    const styles = useBlueprintsToolbarStyles();

    return (
        <div id={"blueprints-tab-div-4"}
             className={styles.searchRow}>
            <div id={"blueprints-tab-div-5"}
                 className={styles.searchRowInputs}>
                <Field id={"blueprints-tab-field-1"}
                       className={styles.searchField}>
                    <SearchBox
                        id="blueprint-search-input"
                        placeholder="Search blueprints"
                        maxLength={100}
                        value={filters.searchQuery}
                        onChange={(_, data) => filters.setSearchQuery(data.value)}
                    />
                </Field>
                {filters.availableTags.length > 0 && (
                    <BlueprintsTagFilter filterSearch={filters.filterSearch}
                                         onFilterSearchChange={filters.setFilterSearch}
                                         tagOptions={filters.filteredTagOptions}
                                         selectedTags={filters.selectedTags}
                                         onToggleTag={filters.toggleTag}/>
                )}
                <BlueprintsSortMenu sortOrder={filters.sortOrder}
                                    onSortOrderChange={filters.setSortOrder}/>
            </div>
            <ViewModeToggle value={viewMode}
                            onChange={onViewModeChange}/>
        </div>
    );
};

export default BlueprintsToolbar;
