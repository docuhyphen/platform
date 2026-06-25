import React, {useRef, useState} from 'react';
import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    Spinner,
    Text,
} from '@fluentui/react-components';
import {useDocumentsTabStyles} from './DocumentLibraryTabStyles.tsx';
import {uploadDocumentLibraryFile} from '../../../services/documentLibraryService.ts';

interface Props
{
    open: boolean;
    entryId: string;
    entryTitle: string;
    onClose: () => void;
    onUploaded: () => void;
}

const ALLOWED_EXTENSIONS = ['pdf', 'docx', 'doc', 'xlsx', 'xls', 'pptx', 'ppt', 'png', 'jpg'];

const DocumentLibraryUploadDialog = ({open, entryId, entryTitle, onClose, onUploaded}: Props) =>
{
    const styles = useDocumentsTabStyles();
    const fileInputRef = useRef<HTMLInputElement>(null);
    const [selectedFile, setSelectedFile] = useState<File | null>(null);
    const [uploading, setUploading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) =>
    {
        const file = e.target.files?.[0] ?? null;
        setSelectedFile(file);
        setError(null);
    };

    const getExtension = (file: File): string =>
        file.name.split('.').pop()?.toLowerCase() ?? '';

    const handleUpload = async () =>
    {
        if (!selectedFile) { setError('Please select a file'); return; }
        const ext = getExtension(selectedFile);
        if (!ALLOWED_EXTENSIONS.includes(ext))
        {
            setError(`Unsupported file type. Allowed: ${ALLOWED_EXTENSIONS.join(', ')}`);
            return;
        }
        setUploading(true);
        setError(null);
        try
        {
            await uploadDocumentLibraryFile(entryId, selectedFile, ext);
            setSelectedFile(null);
            if (fileInputRef.current) fileInputRef.current.value = '';
            onUploaded();
        }
        catch
        {
            setError('Upload failed. Please try again.');
        }
        finally
        {
            setUploading(false);
        }
    };

    const handleClose = () =>
    {
        if (!uploading)
        {
            setSelectedFile(null);
            setError(null);
            if (fileInputRef.current) fileInputRef.current.value = '';
            onClose();
        }
    };

    return (
        <Dialog
            open={open}
            onOpenChange={(_, d) => { if (!d.open) handleClose(); }}
        >
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>Upload File</DialogTitle>
                    <DialogContent>
                        <div
                            id="doc-upload-dialog-body"
                            className={styles.dialogBody}
                        >
                            <Text size={300}>
                                Uploading file for: <strong>{entryTitle}</strong>
                            </Text>
                            <input
                                id="doc-upload-file-input"
                                ref={fileInputRef}
                                type="file"
                                accept=".pdf,.docx,.doc,.xlsx,.xls,.pptx,.ppt,.png,.jpg"
                                onChange={handleFileChange}
                                disabled={uploading}
                                style={{display: 'none'}}
                            />
                            <Button
                                id="doc-upload-browse-btn"
                                appearance="secondary"
                                shape="circular"
                                size="small"
                                disabled={uploading}
                                onClick={() => fileInputRef.current?.click()}
                            >
                                Choose file
                            </Button>
                            {selectedFile && (
                                <Text
                                    id="doc-upload-selected-file"
                                    size={200}
                                    style={{color: 'var(--colorNeutralForeground2)'}}
                                >
                                    Selected: {selectedFile.name}
                                </Text>
                            )}
                            {uploading && (
                                <Spinner
                                    id="doc-upload-spinner"
                                    size="medium"
                                    label="Uploading..."
                                />
                            )}
                            {error && (
                                <span
                                    id="doc-upload-error"
                                    style={{color: 'var(--colorPaletteRedForeground1)'}}
                                >
                                    {error}
                                </span>
                            )}
                        </div>
                    </DialogContent>
                    <DialogActions>
                        <div className={styles.dialogActions}>
                            <Button
                                id="doc-upload-cancel-btn"
                                appearance="secondary"
                                shape="circular"
                                onClick={handleClose}
                                disabled={uploading}
                            >
                                Cancel
                            </Button>
                            <Button
                                id="doc-upload-confirm-btn"
                                appearance="primary"
                                shape="circular"
                                onClick={handleUpload}
                                disabled={uploading || !selectedFile}
                                icon={uploading ? <Spinner size="tiny"/> : undefined}
                            >
                                {uploading ? 'Uploading...' : 'Upload'}
                            </Button>
                        </div>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default DocumentLibraryUploadDialog;
