import {SharingSessionDetailedDto} from "../../../models/models.tsx";
import React, {useEffect} from "react";
import useToken from "../../../../context/useToken.tsx";
import {useGlobalStyles} from "../../../../GlobalStyles.tsx";
import {downloadSharingSessionDocumentZip} from "../../../../services/sharingSessionApi.ts";
import {
    Button,
    Checkbox,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    DialogTrigger,
    Field,
    Input,
    Spinner,
    Text
} from "@fluentui/react-components";
import {useSessionDocumentZipDownloadDialogStyles} from "./SessionDocumentZipDownloadDialogStyles.tsx";

interface DownloadDocumentDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    session: SharingSessionDetailedDto;
}

const SessionDocumentZipDownloadDialog: React.FC<DownloadDocumentDialogProps> = (
    {
        isOpen,
        onDismiss,
        session,
    }) =>
{
    const token = useToken();
    const [downloadName, setDownloadName] = React.useState('');
    const [downloadingDocumentsZip, setDownloadingDocumentsZip] = React.useState(false);
    const [selectedDocuments, setSelectedDocuments] = React.useState<string[]>([]);
    const globalStyles = useGlobalStyles();
    const styles = useSessionDocumentZipDownloadDialogStyles();

    useEffect(() =>
    {
        setDownloadName(`${session?.sessionName?.replace(/\s+/g, '-')}-documents`);
    }, [session]);

    const onDownload = async () =>
    {
        setDownloadingDocumentsZip(true);

        try
        {
            const data = await downloadSharingSessionDocumentZip(session.id, {documentIds: selectedDocuments}, token);
            const url = window.URL.createObjectURL(new Blob([data], {type: 'application/octet-stream'}));
            const link = window.document.createElement('a');

            link.id = 'f-download-link';
            link.href = url;
            link.setAttribute('download', `${downloadName}.zip`);

            window.document.body.appendChild(link);
            link.click();
            window.document.getElementById('f-download-link')?.remove();
        }
        catch (error)
        {
            alert("Download failed");
            console.error("Error downloading document:", error);
        }
        finally
        {
            setDownloadingDocumentsZip(false);
        }
    };

    const onClose = () =>
    {
        setDownloadName('');
        onDismiss();
    };

    const handleDocumentSelection = (documentId: string, isSelected: boolean) =>
    {
        setSelectedDocuments(prevSelectedDocuments =>
            isSelected
                ? [...prevSelectedDocuments, documentId]
                : prevSelectedDocuments.filter(id => id !== documentId)
        );
    };

    return (
        <Dialog modalType="alert" open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>Download Compressed Documents</DialogTitle>
                    <DialogContent>
                        <div className={styles.downloadContainer}>
                            <Field className={styles.downloadNameField}>
                                <Input
                                    type="text"
                                    value={downloadName}
                                    required
                                    onChange={(e) => setDownloadName(e.target.value)}
                                    placeholder="Zip name"
                                    contentAfter={<Text size={400}>.zip</Text>}
                                />
                            </Field>
                            {session?.documents?.filter(d => d.uploadDate).map(document => (
                                <Checkbox
                                    key={document.id}
                                    label={document.title}
                                    onChange={(e, data) => handleDocumentSelection(document.id, data.checked)}
                                />
                            ))}
                        </div>
                    </DialogContent>
                    <DialogActions>
                        <Button
                            appearance="primary"
                            className={globalStyles.buttonWithLoading}
                            shape={"circular"}
                            disabled={downloadingDocumentsZip || !downloadName.trim()}
                            onClick={onDownload}
                        >
                            {downloadingDocumentsZip && <Spinner size={"tiny"}/>}
                            Download
                        </Button>
                        <DialogTrigger disableButtonEnhancement>
                            <Button
                                appearance="secondary"
                                shape={"circular"}
                                disabled={downloadingDocumentsZip}
                                onClick={onClose}
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

export default SessionDocumentZipDownloadDialog;