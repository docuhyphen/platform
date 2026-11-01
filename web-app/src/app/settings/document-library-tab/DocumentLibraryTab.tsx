import {useState} from 'react';
import {Spinner} from '@fluentui/react-components';
import {useDocumentsTabStyles} from './DocumentLibraryTabStyles.tsx';
import {DocumentLibraryListItemContext, DocumentLibraryListTab} from './documentLibraryListTypes.ts';
import {DOCUMENT_LIBRARY_PAGE_SIZE} from './documentLibraryListFilters.ts';
import {createDocumentLibraryEntryHandlers} from './documentLibraryEntryActions.ts';
import {useDocumentLibraryPermissions} from './useDocumentLibraryPermissions.ts';
import {useDocumentLibraryEntries} from './useDocumentLibraryEntries.ts';
import {useDocumentLibraryListFilters} from './useDocumentLibraryListFilters.ts';
import {useDocumentLibraryViewMode} from './useDocumentLibraryViewMode.ts';
import {useDocumentLibraryDialogs} from './useDocumentLibraryDialogs.ts';
import DocumentLibraryScopeTabs from './document-library-scope-tabs/DocumentLibraryScopeTabs.tsx';
import DocumentLibraryToolbar from './document-library-toolbar/DocumentLibraryToolbar.tsx';
import DocumentLibraryActiveTags from './document-library-active-tags/DocumentLibraryActiveTags.tsx';
import DocumentLibraryListContent from './document-library-list-content/DocumentLibraryListContent.tsx';
import DocumentLibraryPagination from './document-library-pagination/DocumentLibraryPagination.tsx';
import DocumentLibraryDialogs from './document-library-dialogs/DocumentLibraryDialogs.tsx';

const DocumentLibraryTab = () =>
{
    const styles = useDocumentsTabStyles();
    const [activeTab, setActiveTab] = useState<DocumentLibraryListTab>('PERSONAL');
    const {hasOrg, canManageOrganization, canCreate, canManageItem} = useDocumentLibraryPermissions(activeTab);
    const {entries, setEntries, loading, error, loadEntries} = useDocumentLibraryEntries(activeTab);
    const filters = useDocumentLibraryListFilters(entries);
    const {viewMode, handleViewModeChange} = useDocumentLibraryViewMode();
    const dialogs = useDocumentLibraryDialogs();

    const handleTabSelect = (tab: DocumentLibraryListTab) =>
    {
        setActiveTab(tab);
        setEntries([]);
        filters.resetFilters();
    };

    const context: DocumentLibraryListItemContext = {
        activeTab,
        canManageOrganization,
        canManageItem,
        handlers: createDocumentLibraryEntryHandlers({
            reload: loadEntries,
            openEdit: dialogs.openEdit,
            openUpload: dialogs.openUpload,
            requestDelete: dialogs.requestDelete,
        }),
    };

    const showPagination = !loading && !error && filters.filteredEntries.length > 0;

    return (
        <>
            <div id="documents-tab-container"
                 className={styles.container}>
                <div id={"document-library-tab-div-1"}
                     className={styles.stickyBlock}>
                    <DocumentLibraryScopeTabs activeTab={activeTab}
                                              hasOrg={hasOrg}
                                              canCreate={canCreate}
                                              onTabSelect={handleTabSelect}
                                              onCreate={dialogs.openCreate}/>

                    <DocumentLibraryToolbar filters={filters}
                                            viewMode={viewMode}
                                            onViewModeChange={handleViewModeChange}/>
                    {filters.selectedTags.size > 0 && (
                        <DocumentLibraryActiveTags selectedTags={filters.selectedTags}
                                                   onRemoveTag={filters.removeTag}
                                                   onClearTags={filters.clearTags}/>
                    )}
                </div>

                {loading && (
                    <Spinner id="doc-loading-spinner"
                             size="small"
                             label="Loading documents..."/>
                )}
                <DocumentLibraryListContent loading={loading}
                                            error={error}
                                            entries={entries}
                                            filteredEntries={filters.filteredEntries}
                                            visibleEntries={filters.visibleEntries}
                                            viewMode={viewMode}
                                            context={context}/>
                {showPagination && (
                    <DocumentLibraryPagination currentPage={filters.currentPage}
                                               totalPages={Math.max(filters.totalPages, 1)}
                                               totalItems={filters.filteredEntries.length}
                                               pageSize={DOCUMENT_LIBRARY_PAGE_SIZE}
                                               onPageChange={filters.setCurrentPage}/>
                )}
            </div>

            <DocumentLibraryDialogs dialogs={dialogs}
                                    scope={activeTab}
                                    onChanged={loadEntries}/>
        </>
    );
};

export default DocumentLibraryTab;
