import {DocumentLibraryEntrySummaryDto} from "../../models/models.tsx";
import DocumentLibraryEditorDialog from "../../settings/document-library-tab/DocumentLibraryEditorDialog.tsx";
import DocumentLibraryUploadDialog from "../../settings/document-library-tab/DocumentLibraryUploadDialog.tsx";
import PlatformDocumentDeleteDialog from "./PlatformDocumentDeleteDialog.tsx";

interface Props
{
    editing: DocumentLibraryEntrySummaryDto | "new" | null;
    uploading: DocumentLibraryEntrySummaryDto | null;
    deleting: DocumentLibraryEntrySummaryDto | null;
    deletePending: boolean;
    onCloseEditor: () => void;
    onCloseUpload: () => void;
    onCloseDelete: () => void;
    onSaved: () => void;
    onConfirmDelete: () => void;
}

const PlatformDocumentDialogs = ({
    editing,
    uploading,
    deleting,
    deletePending,
    onCloseEditor,
    onCloseUpload,
    onCloseDelete,
    onSaved,
    onConfirmDelete,
}: Props) => (
    <>
        <DocumentLibraryEditorDialog
            open={editing !== null}
            entry={editing && editing !== "new" ? editing : undefined}
            scope={"APP"}
            enforcedScope={"APP"}
            onClose={onCloseEditor}
            onSaved={onSaved}/>
        {uploading && (
            <DocumentLibraryUploadDialog
                open={true}
                entry={uploading}
                enforcedScope={"APP"}
                onClose={onCloseUpload}
                onUploaded={onSaved}/>
        )}
        {deleting && (
            <PlatformDocumentDeleteDialog
                entry={deleting}
                deleting={deletePending}
                onConfirm={onConfirmDelete}
                onDismiss={onCloseDelete}/>
        )}
    </>
);

export default PlatformDocumentDialogs;
