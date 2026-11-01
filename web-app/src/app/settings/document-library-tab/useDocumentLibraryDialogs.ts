import {useState} from 'react';
import {DocumentLibraryEntrySummaryDto} from '../../models/models.tsx';

export interface DocumentLibraryDialogsState
{
    editorOpen: boolean;
    editingEntry: DocumentLibraryEntrySummaryDto | undefined;
    uploadOpen: boolean;
    uploadingEntry: DocumentLibraryEntrySummaryDto | undefined;
    confirmDeleteId: string | null;
    openEdit: (entry: DocumentLibraryEntrySummaryDto) => void;
    openCreate: () => void;
    closeEditor: () => void;
    openUpload: (entry: DocumentLibraryEntrySummaryDto) => void;
    closeUpload: () => void;
    requestDelete: (entry: DocumentLibraryEntrySummaryDto) => void;
    clearDelete: () => void;
}

export const useDocumentLibraryDialogs = (): DocumentLibraryDialogsState =>
{
    const [editorOpen, setEditorOpen] = useState(false);
    const [editingEntry, setEditingEntry] = useState<DocumentLibraryEntrySummaryDto | undefined>();
    const [uploadOpen, setUploadOpen] = useState(false);
    const [uploadingEntry, setUploadingEntry] = useState<DocumentLibraryEntrySummaryDto | undefined>();
    const [confirmDeleteId, setConfirmDeleteId] = useState<string | null>(null);

    const openEdit = (entry: DocumentLibraryEntrySummaryDto) =>
    {
        setEditingEntry(entry);
        setEditorOpen(true);
    };

    const openCreate = () =>
    {
        setEditingEntry(undefined);
        setEditorOpen(true);
    };

    const openUpload = (entry: DocumentLibraryEntrySummaryDto) =>
    {
        setUploadingEntry(entry);
        setUploadOpen(true);
    };

    return {
        editorOpen,
        editingEntry,
        uploadOpen,
        uploadingEntry,
        confirmDeleteId,
        openEdit,
        openCreate,
        closeEditor: () => setEditorOpen(false),
        openUpload,
        closeUpload: () => setUploadOpen(false),
        requestDelete: entry => setConfirmDeleteId(entry.id),
        clearDelete: () => setConfirmDeleteId(null),
    };
};
