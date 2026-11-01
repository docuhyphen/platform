import {DocumentLibraryScope} from '../../../models/models.tsx';
import {DocumentLibraryDialogsState} from '../useDocumentLibraryDialogs.ts';
import {deleteDocumentLibraryEntryAndReload} from '../documentLibraryEntryActions.ts';
import DocumentLibraryEditorDialog from '../document-library-editor-dialog/DocumentLibraryEditorDialog.tsx';
import DocumentLibraryUploadDialog from '../document-library-upload-dialog/DocumentLibraryUploadDialog.tsx';
import DocumentLibraryDeleteDialog from './document-library-delete-dialog/DocumentLibraryDeleteDialog.tsx';

interface DocumentLibraryDialogsProps
{
    dialogs: DocumentLibraryDialogsState;
    scope: DocumentLibraryScope;
    onChanged: () => void;
}

const DocumentLibraryDialogs = ({dialogs, scope, onChanged}: DocumentLibraryDialogsProps) =>
{
    const handleSaved = () =>
    {
        dialogs.closeEditor();
        onChanged();
    };

    const handleUploaded = () =>
    {
        dialogs.closeUpload();
        onChanged();
    };

    const handleConfirmDelete = () =>
    {
        if (dialogs.confirmDeleteId !== null)
        {
            deleteDocumentLibraryEntryAndReload(dialogs.confirmDeleteId, onChanged);
        }
        dialogs.clearDelete();
    };

    return (
        <>
            <DocumentLibraryEditorDialog open={dialogs.editorOpen}
                                         onClose={dialogs.closeEditor}
                                         onSaved={handleSaved}
                                         entry={dialogs.editingEntry}
                                         scope={scope}/>

            {dialogs.uploadingEntry && (
                <DocumentLibraryUploadDialog open={dialogs.uploadOpen}
                                             entry={dialogs.uploadingEntry}
                                             onClose={dialogs.closeUpload}
                                             onUploaded={handleUploaded}/>
            )}

            <DocumentLibraryDeleteDialog open={dialogs.confirmDeleteId !== null}
                                         onConfirm={handleConfirmDelete}
                                         onCancel={dialogs.clearDelete}/>
        </>
    );
};

export default DocumentLibraryDialogs;
