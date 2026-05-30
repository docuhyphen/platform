import React, {useMemo, useRef, useState} from "react";
import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    DialogTrigger,
    Field,
    MessageBar,
    MessageBarBody,
    ProgressBar,
    Spinner,
    Text
} from "@fluentui/react-components";
import {useGlobalStyles} from "../../../../GlobalStyles.tsx";
import {uploadSharingSessionDocument} from "../../../../services/sharingSessionApi.ts";
import useToken from "../../../../context/useToken.tsx";
import {DocumentDetailedDto, DocumentType} from "../../../models/models.tsx";
import {useDocumentDialogStyles} from "./UploadDocumentDialogStyles.tsx";

interface UploadDocumentDialogProps
{
    isOpen: boolean;
    sessionId: string;
    sessionDocument: DocumentDetailedDto;
    onDismiss: () => void;
    onDocumentUploaded: (document: DocumentDetailedDto) => void;
}

const SessionDocumentUploadDialog: React.FC<UploadDocumentDialogProps> = (
    {
        isOpen,
        sessionId,
        sessionDocument,
        onDismiss,
        onDocumentUploaded
    }) =>
{
    const token = useToken();
    const fileInputRef = useRef<HTMLInputElement | null>(null);
    const [file, setFile] = useState<File | null>(null);
    const [uploading, setUploading] = useState<boolean>(false);
    const [progress, setProgress] = useState<number>(0);
    const [isServerProcessing, setIsServerProcessing] = useState<boolean>(false);
    const [fileError, setFileError] = useState<string | null>(null);
    const globalStyles = useGlobalStyles();
    const styles = useDocumentDialogStyles();

    const extensionMap: Record<DocumentType, string> = {
        PDF: ".pdf",
        DOCX: ".docx",
        DOC: ".doc",
        XLSX: ".xlsx",
        XLS: ".xls",
        PPTX: ".pptx",
        PPT: ".ppt",
        PNG: ".png",
        JPG: ".jpg",
    };

    const allowedTypes = useMemo(() =>
    {
        const restricted = (sessionDocument?.restrictedType || '').toUpperCase() as DocumentType;
        if (restricted && Object.values(DocumentType).includes(restricted))
        {
            return [restricted];
        }
        return Object.values(DocumentType);
    }, [sessionDocument?.restrictedType]);

    const allowedExtensions = allowedTypes.map(type => extensionMap[type]);
    const fileAccept = allowedExtensions.join(',');
    const restrictionLabel = useMemo(() =>
    {
        const rawRestriction = sessionDocument?.restrictedType;
        if (!rawRestriction)
        {
            return 'Any supported type';
        }

        const normalizedRestriction = String(rawRestriction).trim();
        if (!normalizedRestriction || normalizedRestriction.toLowerCase() === 'null')
        {
            return 'Any supported type';
        }

        return normalizedRestriction;
    }, [sessionDocument?.restrictedType]);
    const maxSizeBytes = 52_428_800;
    const minSizeBytes = 100;

    const formatFileSize = (size: number): string =>
    {
        if (size < 1024) return `${size} B`;
        if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
        return `${(size / (1024 * 1024)).toFixed(2)} MB`;
    };

    const getFileExtension = (fileName: string): string =>
    {
        const i = fileName.lastIndexOf('.');
        return i >= 0 ? fileName.substring(i).toLowerCase() : '';
    };

    const resetState = () =>
    {
        setFile(null);
        setProgress(0);
        setFileError(null);
    };

    const onDismissDialog = () =>
    {
        resetState();
        onDismiss();
    };

    const onUploadDocument = async () =>
    {
        if (uploading || !file)
        {
            return;
        }

        setUploading(true);
        setProgress(0);
        setIsServerProcessing(false);

        const fileName = file.name;
        const fileExtension = getFileExtension(fileName);

        const formData = new FormData();
        formData.append("file", file, fileName);
        formData.append("encryptionMode", "INTERNAL");
        formData.append("extension", fileExtension);

        try
        {
            const uploadData = await uploadSharingSessionDocument(sessionId, sessionDocument.id, formData, token, (event) =>
            {
                if (!event.total || event.total <= 0)
                {
                    setProgress((current) => Math.max(current, 10));
                    return;
                }

                const percentCompleted = Math.round((event.loaded * 100) / event.total);
                const cappedProgress = Math.min(percentCompleted, 95);
                setProgress((current) => Math.max(current, cappedProgress));

                if (percentCompleted >= 100)
                {
                    setIsServerProcessing(true);
                }
            });

            // Mark complete only after the API request has fully finished server-side.
            setIsServerProcessing(false);
            setProgress(100);
            onDocumentUploaded(uploadData);
        }
        catch (error)
        {
            alert("Error uploading document");
            console.error(error);
        }
        finally
        {
            setUploading(false);
            setIsServerProcessing(false);
        }
    };

    const onPickFile = (pickedFile: File | null) =>
    {
        if (!pickedFile)
        {
            setFile(null);
            setFileError(null);
            return;
        }

        const extension = getFileExtension(pickedFile.name);
        if (!allowedExtensions.includes(extension))
        {
            setFile(null);
            setFileError(`Invalid file type. Allowed: ${allowedExtensions.join(', ')}`);
            return;
        }

        if (pickedFile.size > maxSizeBytes)
        {
            setFile(null);
            setFileError("File is too large. Maximum size allowed is 50 MB.");
            return;
        }

        if (pickedFile.size < minSizeBytes)
        {
            setFile(null);
            setFileError("File is too small. Minimum size required is 100 bytes.");
            return;
        }

        setFileError(null);
        setFile(pickedFile);
    };

    //ToDo: offer PDF conversion when file is not a PDF and PDF is required
    return (
        <Dialog modalType="alert" open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>Upload {sessionDocument?.title}</DialogTitle>
                    <DialogContent className={styles.contentContainer}>
                        <Field className={styles.uploadContainer}>
                            <input
                                ref={fileInputRef}
                                className={styles.hiddenInput}
                                type="file"
                                accept={fileAccept}
                                onChange={(e) => onPickFile(e.target.files ? e.target.files[0] : null)}
                            />
                            <Button
                                appearance="secondary"
                                shape="rounded"
                                onClick={() => fileInputRef.current?.click()}
                                disabled={uploading}>
                                Choose file
                            </Button>
                            <Text size={200}>Allowed: {allowedExtensions.join(', ')}</Text>
                        </Field>

                        {fileError && (
                            <MessageBar intent="error">
                                <MessageBarBody>{fileError}</MessageBarBody>
                            </MessageBar>
                        )}

                        {file && (
                            <div className={styles.fileInfoCard}>
                                <div className={styles.keyValueRow}>
                                    <Text className={styles.keyLabel}>File name</Text>
                                    <Text>{file.name}</Text>
                                </div>
                                <div className={styles.keyValueRow}>
                                    <Text className={styles.keyLabel}>Size</Text>
                                    <Text>{formatFileSize(file.size)}</Text>
                                </div>
                                <div className={styles.keyValueRow}>
                                    <Text className={styles.keyLabel}>Type</Text>
                                    <Text>{getFileExtension(file.name) || 'Unknown'}</Text>
                                </div>
                                <div className={styles.keyValueRow}>
                                    <Text className={styles.keyLabel}>Target document</Text>
                                    <Text>{sessionDocument?.title}</Text>
                                </div>
                                <div className={styles.keyValueRow}>
                                    <Text className={styles.keyLabel}>Restriction</Text>
                                    <Text>{restrictionLabel}</Text>
                                </div>
                            </div>
                        )}

                        {uploading && (
                            <>
                                <ProgressBar value={progress / 100}/>
                                <Text size={200}>
                                    {isServerProcessing
                                        ? "Upload complete, finalizing on server..."
                                        : `Uploading... ${progress}%`}
                                </Text>
                            </>
                        )}
                    </DialogContent>
                    <DialogActions>
                        <Button
                            appearance="primary"
                            className={globalStyles.buttonWithLoading}
                            onClick={onUploadDocument}
                            shape="circular"
                            disabled={!file || uploading || !!fileError}
                        >
                            {uploading && <Spinner size="tiny"/>} Upload
                        </Button>
                        <DialogTrigger disableButtonEnhancement>
                            <Button
                                appearance="secondary"
                                onClick={onDismissDialog}
                                disabled={uploading}
                                shape="circular"
                            >
                                Close
                            </Button>
                        </DialogTrigger>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default SessionDocumentUploadDialog;
