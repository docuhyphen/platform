import {DocumentLibraryEntrySummaryDto} from '../../models/models.tsx';
import {
    cloneDocumentLibraryEntry,
    deleteDocumentLibraryEntry,
    downloadDocumentLibraryFile,
    patchDocumentLibraryEntryPublished,
    patchDocumentLibraryEntryStatus,
} from '../../../services/documentLibraryService.ts';
import {DocumentLibraryEntryHandler, DocumentLibraryEntryHandlers} from './documentLibraryListTypes.ts';

interface DocumentLibraryEntryHandlerDependencies
{
    reload: () => void;
    openEdit: DocumentLibraryEntryHandler;
    openUpload: DocumentLibraryEntryHandler;
    requestDelete: DocumentLibraryEntryHandler;
}

const copyName = (entry: DocumentLibraryEntrySummaryDto) => `${entry.title} (copy)`;

export const createDocumentLibraryEntryHandlers = ({
    reload,
    openEdit,
    openUpload,
    requestDelete,
}: DocumentLibraryEntryHandlerDependencies): DocumentLibraryEntryHandlers => ({
    onEdit: openEdit,
    onUpload: openUpload,
    onDelete: requestDelete,
    onDownload: entry =>
    {
        downloadDocumentLibraryFile(entry.id, entry.fileName ?? entry.title);
    },
    onPublish: async entry =>
    {
        await patchDocumentLibraryEntryPublished(entry.id, {isPublished: !entry.isPublished});
        reload();
    },
    onActivate: async entry =>
    {
        await patchDocumentLibraryEntryStatus(entry.id, {isActive: !entry.isActive});
        reload();
    },
    onClone: async entry =>
    {
        await cloneDocumentLibraryEntry(entry.id, {newName: copyName(entry)});
        reload();
    },
    onCloneToPersonal: async entry =>
    {
        await cloneDocumentLibraryEntry(entry.id, {newName: copyName(entry), targetScope: 'PERSONAL'});
        reload();
    },
    onCloneToOrg: async entry =>
    {
        await cloneDocumentLibraryEntry(entry.id, {newName: copyName(entry), targetScope: 'ORG'});
        reload();
    },
});

export const deleteDocumentLibraryEntryAndReload = async (id: string, reload: () => void): Promise<void> =>
{
    await deleteDocumentLibraryEntry(id);
    reload();
};
