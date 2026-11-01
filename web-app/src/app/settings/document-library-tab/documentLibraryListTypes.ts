import {DocumentLibraryEntrySummaryDto, DocumentLibraryScope} from '../../models/models.tsx';

export type DocumentLibraryListTab = DocumentLibraryScope;
export type DocumentLibrarySortOrder = 'default' | 'nameAsc' | 'nameDesc';

export type DocumentLibraryEntryHandler = (entry: DocumentLibraryEntrySummaryDto) => void;

export interface DocumentLibraryEntryHandlers
{
    onEdit: DocumentLibraryEntryHandler;
    onUpload: DocumentLibraryEntryHandler;
    onDownload: DocumentLibraryEntryHandler;
    onPublish: DocumentLibraryEntryHandler;
    onActivate: DocumentLibraryEntryHandler;
    onClone: DocumentLibraryEntryHandler;
    onDelete: DocumentLibraryEntryHandler;
    onCloneToPersonal: DocumentLibraryEntryHandler;
    onCloneToOrg: DocumentLibraryEntryHandler;
}

export interface DocumentLibraryListItemContext
{
    activeTab: DocumentLibraryListTab;
    canManageOrganization: boolean;
    canManageItem: (entry: DocumentLibraryEntrySummaryDto) => boolean;
    handlers: DocumentLibraryEntryHandlers;
}
