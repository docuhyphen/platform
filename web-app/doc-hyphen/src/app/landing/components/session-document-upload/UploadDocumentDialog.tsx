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

interface UploadDocumentDialogProps
{
    isOpen: boolean;
    sessionId: string;
    sessionDocument: DocumentDetailedDto;
    onDismiss: () => void;
    onDocumentUploaded: (document: any) => void;
}

const UploadDocumentDialog: React.FC<UploadDocumentDialogProps> = (
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

        const formData = new FormData();
        formData.append('file', file, 'UserManual.pdf');
        formData.append('encryptionMode', 'INTERNAL');
        formData.append('extension', '.docx');

        try
        {
            const uploadData = await uploadSharingSessionDocument(sessionId, sessionDocument.id, formData, token, (event) =>
            {
                const percentCompleted = Math.round((event.loaded * 100) / event.total);
                setProgress(percentCompleted);
            });

            //ToDo: fetch the sign document to update the list
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

    return (
        <Dialog modalType="alert" open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>Upload {sessionDocument && sessionDocument.title}</DialogTitle>
                    <DialogContent>
                        <Field>
                            <input
                                type="file"
                                onChange={(e) => setFile(e.target.files ? e.target.files[0] : null)}
                            />
                        </Field>
                        {uploading && <ProgressBar value={progress}/>}
                    </DialogContent>
                    <DialogActions>
                        <DialogTrigger disableButtonEnhancement>
                            <Button appearance="secondary"
                                    onClick={onDismissDialog}
                                    disabled={uploading}
                                    shape={"circular"}>
                                Close
                            </Button>
                        </DialogTrigger>
                        <Button appearance="primary"
                                className={globalStyles.buttonWithLoading}
                                onClick={onUploadDocument}
                                shape={"circular"}>
                            {uploading && <Spinner size={"extra-small"}/>}
                            Upload
                        </Button>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default UploadDocumentDialog;