import {Badge, Text} from '@fluentui/react-components';
import TagList from '../../../../components/TagList.tsx';
import {DocumentLibraryEntrySummaryDto} from '../../../../models/models.tsx';
import {DocumentLibraryListItemContext} from '../../documentLibraryListTypes.ts';
import {documentLibraryEntryCardActions} from '../../documentLibraryEntryCardActions.ts';
import DocumentLibraryEntryCard from '../../document-library-entry-card/DocumentLibraryEntryCard.tsx';
import {useDocumentLibraryTableRowStyles} from './DocumentLibraryTableRowStyles.tsx';

interface DocumentLibraryTableRowProps
{
    entry: DocumentLibraryEntrySummaryDto;
    context: DocumentLibraryListItemContext;
}

const DocumentLibraryTableRow = ({entry, context}: DocumentLibraryTableRowProps) =>
{
    const styles = useDocumentLibraryTableRowStyles();
    const showPublished = context.activeTab !== 'PERSONAL';

    return (
        <tr id={`document-library-tab-tr-2-${entry.id}`}
            className={styles.tr}>
            <td id={"document-library-tab-td-1"}
                className={styles.td}>
                <Text id={"document-library-tab-text-2"}
                      weight="semibold">{entry.title}</Text>
                {entry.description && (
                    <Text id={"document-library-tab-text-3"}
                          size={200}
                          className={styles.descriptionText}
                          block>{entry.description}</Text>
                )}
            </td>
            <td id={"document-library-tab-td-2"}
                className={styles.td}>
                <Text id={"document-library-tab-text-4"}
                      size={200}>{entry.documentType ?? '-'}</Text>
            </td>
            <td id={"document-library-tab-td-3"}
                className={styles.td}>
                <TagList tags={entry.generalTags}/>
            </td>
            {showPublished && (
                <td id={"document-library-tab-td-4"}
                    className={styles.td}>
                    <Text id={"document-library-tab-text-5"}
                          size={200}>{entry.isPublished ? 'Published' : 'Draft'}</Text>
                </td>
            )}
            <td id={"document-library-tab-td-5"}
                className={styles.td}>
                <Badge id={"document-library-tab-badge-1"}
                       appearance="tint"
                       color={entry.isActive ? 'success' : 'warning'}
                       size="small">
                    {entry.isActive ? 'Active' : 'Inactive'}
                </Badge>
            </td>
            <td id={"document-library-tab-td-6"}
                className={styles.td}>
                <DocumentLibraryEntryCard entry={entry}
                                          actionsOnly
                                          {...documentLibraryEntryCardActions(entry, context)}/>
            </td>
        </tr>
    );
};

export default DocumentLibraryTableRow;
