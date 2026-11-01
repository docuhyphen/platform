import {DocumentLibraryEntrySummaryDto} from '../../models/models.tsx';
import {DocumentLibraryListItemContext} from './documentLibraryListTypes.ts';
import {DocumentLibraryEntryCardActions} from './document-library-entry-card/documentLibraryEntryCardTypes.ts';

export const documentLibraryEntryCardActions = (
    entry: DocumentLibraryEntrySummaryDto,
    context: DocumentLibraryListItemContext,
): DocumentLibraryEntryCardActions =>
{
    const {activeTab, canManageOrganization, canManageItem, handlers} = context;
    const isPlatformTab = activeTab === 'APP';
    return {
        canManage: canManageItem(entry),
        showPublishToggle: activeTab !== 'PERSONAL',
        onEdit: () => handlers.onEdit(entry),
        onUpload: () => handlers.onUpload(entry),
        onDownload: () => handlers.onDownload(entry),
        onPublish: () => handlers.onPublish(entry),
        onActivate: () => handlers.onActivate(entry),
        onClone: () => handlers.onClone(entry),
        onDelete: () => handlers.onDelete(entry),
        onCloneToPersonal: isPlatformTab ? () => handlers.onCloneToPersonal(entry) : undefined,
        onCloneToOrg: isPlatformTab && canManageOrganization ? () => handlers.onCloneToOrg(entry) : undefined,
    };
};
