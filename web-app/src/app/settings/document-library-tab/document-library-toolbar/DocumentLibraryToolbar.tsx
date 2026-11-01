import {Field, SearchBox} from '@fluentui/react-components';
import ViewModeToggle from '../../../components/view-mode-toggle/ViewModeToggle.tsx';
import {ViewMode} from '../../../models/models.tsx';
import {DocumentLibraryListFilters} from '../useDocumentLibraryListFilters.ts';
import DocumentLibraryTagFilter from './document-library-tag-filter/DocumentLibraryTagFilter.tsx';
import DocumentLibrarySortMenu from './document-library-sort-menu/DocumentLibrarySortMenu.tsx';
import {useDocumentLibraryToolbarStyles} from './DocumentLibraryToolbarStyles.tsx';

interface DocumentLibraryToolbarProps
{
    filters: DocumentLibraryListFilters;
    viewMode: ViewMode;
    onViewModeChange: (mode: ViewMode) => void;
}

const DocumentLibraryToolbar = ({filters, viewMode, onViewModeChange}: DocumentLibraryToolbarProps) =>
{
    const styles = useDocumentLibraryToolbarStyles();

    return (
        <div id={"document-library-tab-div-3"}
             className={styles.searchRow}>
            <div id={"document-library-tab-div-4"}
                 className={styles.searchRowInputs}>
                <Field id={"document-library-tab-field-1"}
                       className={styles.searchField}>
                    <SearchBox id="doc-search-input"
                               placeholder="Search documents"
                               maxLength={100}
                               value={filters.searchQuery}
                               onChange={(_, data) => filters.setSearchQuery(data.value)}/>
                </Field>
                {filters.availableTags.length > 0 && (
                    <DocumentLibraryTagFilter filterSearch={filters.filterSearch}
                                              onFilterSearchChange={filters.setFilterSearch}
                                              tagOptions={filters.filteredTagOptions}
                                              selectedTags={filters.selectedTags}
                                              onToggleTag={filters.toggleTag}/>
                )}
                <DocumentLibrarySortMenu sortOrder={filters.sortOrder}
                                         onSortOrderChange={filters.setSortOrder}/>
            </div>
            <ViewModeToggle value={viewMode}
                            onChange={onViewModeChange}/>
        </div>
    );
};

export default DocumentLibraryToolbar;
