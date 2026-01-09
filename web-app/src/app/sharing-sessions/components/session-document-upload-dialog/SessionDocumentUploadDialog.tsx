import React, {useState} from "react";
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
    ProgressBar,
    Spinner
} from "@fluentui/react-components";
import {useGlobalStyles} from "../../../../GlobalStyles.tsx";
import {uploadSharingSessionDocument} from "../../../../services/sharingSessionApi.ts";
import useToken from "../../../../context/useToken.tsx";
import {DocumentDetailedDto} from "../../../models/models.tsx";
import {useDocumentDialogStyles} from "./UploadDocumentDialogStyles.tsx";

interface UploadDocumentDialogProps
{
    isOpen: boolean;
    sessionId: string;
    sessionDocument: DocumentDetailedDto;
    onDismiss: () => void;
    onDocumentUploaded: (document: any) => void;
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
    const [file, setFile] = useState<File | null>(null);
    const [uploading, setUploading] = useState<boolean>(false);
    const [progress, setProgress] = useState<number>(0);
    const globalStyles = useGlobalStyles();
    const styles = useDocumentDialogStyles();

    const resetState = () =>
    {
        setFile(null);
        setProgress(0);
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

        const fileName = file.name;
        const fileExtension = fileName.substring(fileName.lastIndexOf(".")) || "";

        const formData = new FormData();
        formData.append("file", file, fileName);
        formData.append("encryptionMode", "INTERNAL");
        formData.append("extension", fileExtension);

        try
        {
            const uploadData = await uploadSharingSessionDocument(sessionId, sessionDocument.id, formData, token, (event) =>
            {
                const percentCompleted = Math.round((event.loaded * 100) / event.total);
                setProgress(percentCompleted);
            });

            onDocumentUploaded(uploadData);
            resetState();
            onDismiss();
        }
        catch (error)
        {
            alert("Error uploading document");
            console.error(error);
        }
        finally
        {
            setUploading(false);
        }
    };

    //ToDo: offer PDF conversion when file is not a PDF and PDF is required
    return (
        <Dialog modalType="alert" open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>Upload {sessionDocument?.title}</DialogTitle>
                    <DialogContent>
                        <Field className={styles.uploadContainer}>
                            <input
                                type="file"
                                onChange={(e) => setFile(e.target.files ? e.target.files[0] : null)}
                            />
                        </Field>
                        {uploading && <ProgressBar value={progress}/>}
                    </DialogContent>
                    <DialogActions>
                        <Button
                            appearance="primary"
                            className={globalStyles.buttonWithLoading}
                            onClick={onUploadDocument}
                            shape="circular"
                            disabled={!file || uploading}
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
