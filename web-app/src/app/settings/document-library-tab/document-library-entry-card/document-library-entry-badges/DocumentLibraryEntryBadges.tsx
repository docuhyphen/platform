import {Badge} from '@fluentui/react-components';
import TagList from '../../../../components/TagList.tsx';
import {DocumentLibraryEntrySummaryDto} from '../../../../models/models.tsx';
import {useDocumentLibraryEntryBadgesStyles} from './DocumentLibraryEntryBadgesStyles.tsx';

interface DocumentLibraryEntryBadgesProps
{
    entry: DocumentLibraryEntrySummaryDto;
    showPublishToggle: boolean;
}

const DocumentLibraryEntryBadges = ({entry, showPublishToggle}: DocumentLibraryEntryBadgesProps) =>
{
    const styles = useDocumentLibraryEntryBadgesStyles();

    return (
        <div id={"document-library-entry-card-div-3"}
             className={styles.badgeRow}>
            {entry.documentType && (
                <Badge id={`doc-type-badge-${entry.id}`}
                       appearance="tint"
                       color="informative"
                       size="small">
                    {entry.documentType}
                </Badge>
            )}
            {!entry.hasFile && (
                <Badge id={`doc-no-file-badge-${entry.id}`}
                       appearance="tint"
                       color="warning"
                       size="small">
                    No file
                </Badge>
            )}
            {showPublishToggle && (
                <Badge id={`doc-published-badge-${entry.id}`}
                       appearance="tint"
                       color={entry.isPublished ? 'success' : 'warning'}
                       size="small">
                    {entry.isPublished ? 'Published' : 'Draft'}
                </Badge>
            )}
            <Badge id={`doc-active-badge-${entry.id}`}
                   appearance="tint"
                   color={entry.isActive ? 'success' : 'warning'}
                   size="small">
                {entry.isActive ? 'Active' : 'Inactive'}
            </Badge>
            <TagList tags={entry.generalTags}/>
        </div>
    );
};

export default DocumentLibraryEntryBadges;
