import {DocumentLibraryEntrySummaryDto} from '../../../models/models.tsx';
import {DocumentLibraryListItemContext} from '../documentLibraryListTypes.ts';
import {documentLibraryEntryCardActions} from '../documentLibraryEntryCardActions.ts';
import DocumentLibraryEntryCard from '../document-library-entry-card/DocumentLibraryEntryCard.tsx';
import {useDocumentLibraryCardGridStyles} from './DocumentLibraryCardGridStyles.tsx';

interface DocumentLibraryCardGridProps
{
    entries: DocumentLibraryEntrySummaryDto[];
    context: DocumentLibraryListItemContext;
}

const DocumentLibraryCardGrid = ({entries, context}: DocumentLibraryCardGridProps) =>
{
    const styles = useDocumentLibraryCardGridStyles();

    return (
        <div id={"document-library-tab-div-8"}
             className={styles.cardGrid}>
            {entries.map(entry => (
                <DocumentLibraryEntryCard key={entry.id}
                                          entry={entry}
                                          {...documentLibraryEntryCardActions(entry, context)}/>
            ))}
        </div>
    );
};

export default DocumentLibraryCardGrid;
