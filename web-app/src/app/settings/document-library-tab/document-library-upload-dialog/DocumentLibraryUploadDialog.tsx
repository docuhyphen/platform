import {
    Button,
    Dialog,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    Text,
} from '@fluentui/react-components';
import {DocumentLibraryEntrySummaryDto} from '../../../models/models.tsx';
import DocumentLibraryDialogActions from '../document-library-dialog-actions/DocumentLibraryDialogActions.tsx';
import {DOCUMENT_LIBRARY_ACCEPTED_FILE_TYPES} from './documentLibraryUploadFiles.ts';
import {useDocumentLibraryUpload} from './useDocumentLibraryUpload.ts';
import {useDocumentLibraryUploadDialogStyles} from './DocumentLibraryUploadDialogStyles.tsx';

interface Props
{
    open: boolean;
    entry: DocumentLibraryEntrySummaryDto;
    enforcedScope?: 'APP';
    onClose: () => void;
    onUploaded: () => void;
}

const actionIds = {
    actions: 'document-library-upload-dialog-dialog-actions-1',
    container: 'document-library-upload-dialog-div-1',
    confirm: 'doc-upload-confirm-btn',
    cancel: 'doc-upload-cancel-btn',
    spinner: 'document-library-upload-dialog-spinner-1',
};

const DocumentLibraryUploadDialog = ({open, entry, enforcedScope, onClose, onUploaded}: Props) =>
{
    const styles = useDocumentLibraryUploadDialogStyles();
    const upload = useDocumentLibraryUpload({entry, enforcedScope, onClose, onUploaded});

    return (
        <Dialog open={open}
                onOpenChange={(_, d) =>
                {
                    if (!d.open) upload.handleClose();
                }}>
            <DialogSurface id={"document-library-upload-dialog-dialog-surface-1"}>
                <DialogBody id={"document-library-upload-dialog-dialog-body-1"}>
                    <DialogTitle id={"document-library-upload-dialog-dialog-title-1"}>Upload File</DialogTitle>
                    <DialogContent id={"document-library-upload-dialog-dialog-content-1"}>
                        <div id="doc-upload-dialog-body"
                             className={styles.dialogBody}>
                            <Text id={"document-library-upload-dialog-text-1"}
                                  size={300}>
                                Uploading file for: <strong id={"document-library-upload-dialog-strong-1"}>{entry.title}</strong>
                            </Text>
                            <input id="doc-upload-file-input"
                                   ref={upload.fileInputRef}
                                   type="file"
                                   accept={DOCUMENT_LIBRARY_ACCEPTED_FILE_TYPES}
                                   onChange={upload.handleFileChange}
                                   disabled={upload.uploading}
                                   className={styles.hiddenFileInput}/>
                            <span id={"document-library-upload-dialog-span-1"}>
                                <Button id="doc-upload-browse-btn"
                                        appearance="secondary"
                                        shape="circular"
                                        size="small"
                                        disabled={upload.uploading}
                                        onClick={upload.openFilePicker}>
                                    Choose file
                                </Button>
                            </span>
                            {upload.selectedFile && (
                                <Text id="doc-upload-selected-file"
                                      size={200}
                                      className={styles.selectedFileText}>
                                    Selected: {upload.selectedFile.name}
                                </Text>
                            )}
                            {upload.error && (
                                <span id="doc-upload-error"
                                      className={styles.errorText}>
                                    {upload.error}
                                </span>
                            )}
                        </div>
                    </DialogContent>
                    <DocumentLibraryDialogActions ids={actionIds}
                                                  confirmLabel="Upload"
                                                  pendingLabel="Uploading..."
                                                  pending={upload.uploading}
                                                  confirmDisabled={upload.uploading || !upload.selectedFile}
                                                  onConfirm={upload.handleUpload}
                                                  onCancel={upload.handleClose}/>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default DocumentLibraryUploadDialog;
