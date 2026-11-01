import {DocumentLibraryEntrySummaryDto} from '../../../models/models.tsx';
import {DocumentLibraryListItemContext} from '../documentLibraryListTypes.ts';
import DocumentLibraryTableRow from './document-library-table-row/DocumentLibraryTableRow.tsx';
import {useDocumentLibraryTableStyles} from './DocumentLibraryTableStyles.tsx';

interface DocumentLibraryTableProps
{
    entries: DocumentLibraryEntrySummaryDto[];
    context: DocumentLibraryListItemContext;
}

const DocumentLibraryTable = ({entries, context}: DocumentLibraryTableProps) =>
{
    const styles = useDocumentLibraryTableStyles();
    const showPublished = context.activeTab !== 'PERSONAL';

    return (
        <table id={"document-library-tab-table-1"}
               className={styles.table}>
            <thead id={"document-library-tab-thead-1"}>
            <tr id={"document-library-tab-tr-1"}>
                <th id={"document-library-tab-th-1"}
                    className={styles.th}>Title</th>
                <th id={"document-library-tab-th-2"}
                    className={styles.th}>Type</th>
                <th id={"document-library-tab-th-3"}
                    className={styles.th}>Tags</th>
                {showPublished && <th id={"document-library-tab-th-4"}
                                      className={styles.th}>Published</th>}
                <th id={"document-library-tab-th-5"}
                    className={styles.th}>Active</th>
                <th id={"document-library-tab-th-6"}
                    className={styles.th}/>
            </tr>
            </thead>
            <tbody id={"document-library-tab-tbody-1"}>
            {entries.map(entry => (
                <DocumentLibraryTableRow key={entry.id}
                                         entry={entry}
                                         context={context}/>
            ))}
            </tbody>
        </table>
    );
};

export default DocumentLibraryTable;
