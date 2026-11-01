import {DocumentLibraryEntrySummaryDto} from '../../../../models/models.tsx';
import {DocumentLibraryEntryCardActions} from '../documentLibraryEntryCardTypes.ts';
import DocumentLibraryEntryViewMenu from './document-library-entry-view-menu/DocumentLibraryEntryViewMenu.tsx';
import DocumentLibraryEntryManageMenu from './document-library-entry-manage-menu/DocumentLibraryEntryManageMenu.tsx';
import {useDocumentLibraryEntryActionsStyles} from './DocumentLibraryEntryActionsStyles.tsx';

interface DocumentLibraryEntryActionsProps extends DocumentLibraryEntryCardActions
{
    entry: DocumentLibraryEntrySummaryDto;
}

const DocumentLibraryEntryActions = ({entry, canManage, showPublishToggle, ...callbacks}: DocumentLibraryEntryActionsProps) =>
{
    const styles = useDocumentLibraryEntryActionsStyles();
    const {onDownload, onCloneToPersonal, onCloneToOrg} = callbacks;

    return (
        <div id={"document-library-entry-card-div-1"}
             className={styles.cardActions}>
            {!canManage && (entry.hasFile || onCloneToPersonal || onCloneToOrg) && (
                <DocumentLibraryEntryViewMenu entry={entry}
                                              onDownload={onDownload}
                                              onCloneToPersonal={onCloneToPersonal}
                                              onCloneToOrg={onCloneToOrg}/>
            )}
            {canManage && (
                <DocumentLibraryEntryManageMenu entry={entry}
                                                showPublishToggle={showPublishToggle}
                                                {...callbacks}/>
            )}
        </div>
    );
};

export default DocumentLibraryEntryActions;
