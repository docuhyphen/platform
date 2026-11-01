import {DocumentLibraryEntrySummaryDto} from '../../../models/models.tsx';

export interface DocumentLibraryEntryCardCallbacks
{
    onEdit: () => void;
    onUpload: () => void;
    onDownload: () => void;
    onPublish: () => void;
    onActivate: () => void;
    onClone: () => void;
    onDelete: () => void;
    onCloneToPersonal?: () => void;
    onCloneToOrg?: () => void;
}

export interface DocumentLibraryEntryCardActions extends DocumentLibraryEntryCardCallbacks
{
    canManage: boolean;
    showPublishToggle: boolean;
}

export interface DocumentLibraryEntryCardProps extends DocumentLibraryEntryCardActions
{
    entry: DocumentLibraryEntrySummaryDto;
    actionsOnly?: boolean;
}
