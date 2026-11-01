import {Text} from '@fluentui/react-components';
import {DocumentLibraryEntrySummaryDto, ViewMode} from '../../../models/models.tsx';
import {DocumentLibraryListItemContext} from '../documentLibraryListTypes.ts';
import {documentLibraryEmptyMessages} from '../documentLibraryListFilters.ts';
import DocumentLibraryCardGrid from '../document-library-card-grid/DocumentLibraryCardGrid.tsx';
import DocumentLibraryTable from '../document-library-table/DocumentLibraryTable.tsx';
import {useDocumentLibraryListContentStyles} from './DocumentLibraryListContentStyles.tsx';

interface DocumentLibraryListContentProps
{
    loading: boolean;
    error: string | null;
    entries: DocumentLibraryEntrySummaryDto[];
    filteredEntries: DocumentLibraryEntrySummaryDto[];
    visibleEntries: DocumentLibraryEntrySummaryDto[];
    viewMode: ViewMode;
    context: DocumentLibraryListItemContext;
}

const DocumentLibraryListContent = ({
    loading,
    error,
    entries,
    filteredEntries,
    visibleEntries,
    viewMode,
    context,
}: DocumentLibraryListContentProps) =>
{
    const styles = useDocumentLibraryListContentStyles();
    const ready = !loading && !error;

    return (
        <div id={"document-library-tab-div-7"}
             className={styles.scrollableContent}>
            {!loading && error && (
                <Text id="doc-error-text"
                      className={styles.errorText}>
                    {error}
                </Text>
            )}
            {ready && entries.length === 0 && (
                <Text id="doc-empty-text"
                      className={styles.emptyText}>
                    {documentLibraryEmptyMessages[context.activeTab]}
                </Text>
            )}
            {ready && entries.length > 0 && filteredEntries.length === 0 && (
                <Text id="doc-no-results-text"
                      className={styles.emptyText}>
                    No documents match your search.
                </Text>
            )}
            {ready && visibleEntries.length > 0 && viewMode === 'cards' && (
                <DocumentLibraryCardGrid entries={visibleEntries}
                                         context={context}/>
            )}
            {ready && visibleEntries.length > 0 && viewMode === 'table' && (
                <DocumentLibraryTable entries={visibleEntries}
                                      context={context}/>
            )}
        </div>
    );
};

export default DocumentLibraryListContent;
