import {DocumentDetailedDto, SharingSessionDetailedDto} from "../../../models/models.tsx";
import React, {useEffect} from "react";
import useToken from "../../../../context/useToken.tsx";
import {useGlobalStyles} from "../../../../GlobalStyles.tsx";
import {downloadSharingSessionDocument} from "../../../../services/sharingSessionApi.ts";
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
    Input,
    Spinner,
    Text
} from "@fluentui/react-components";
import {useSessionDocumentDownloadDialogStyles} from "./SessionDocumentDownloadDialogStyles.tsx";

interface DownloadDocumentDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    sessionDocument: DocumentDetailedDto;
    session: SharingSessionDetailedDto;
}

const SessionDocumentDownloadDialog: React.FC<DownloadDocumentDialogProps> = (
    {
        isOpen,
        onDismiss,
        sessionDocument,
        session,
    }) =>
{
    const token = useToken();
    const [downloadName, setDownloadName] = React.useState('');
    const [downloadingDocument, setDownloadingDocument] = React.useState(false);
    const globalStyles = useGlobalStyles()

    const styles = useSessionDocumentDownloadDialogStyles();

    useEffect(() =>
    {
        setDownloadName(`${session?.sessionName?.replace(/\s+/g, '-')}-${sessionDocument?.title?.replace(/\s+/g, '-')}`);
    }, []);

    const onDownload = async () =>
    {
        setDownloadingDocument(true)

        try
        {
            const data = await downloadSharingSessionDocument(session.id, sessionDocument.id, token);
            const url = window.URL.createObjectURL(new Blob([data], {type: 'application/octet-stream'}));
            const link = window.document.createElement('a');

            link.id = 'f-download-link';
            link.href = url;
            link.setAttribute('download', `${downloadName}.pdf`); //ToDo: get type from document

            window.document.body.appendChild(link);

            link.click();

            window.document.getElementById('f-download-link')?.remove();
        }
        catch (error)
        {
            alert("Download failed");
            console.error("Error deleting document:", error);
        }
        finally
        {
            setDownloadingDocument(false);
        }
    }

    const onClose = () =>
    {
        setDownloadName('');
        onDismiss();
    }

    return <>
        {<Dialog modalType="alert" open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>Downloading {sessionDocument && sessionDocument.title}</DialogTitle>
                    <DialogContent>
                        <Field className={styles.downloadNameField}>
                            <Input
                                type="text"
                                value={downloadName}
                                required
                                onChange={(e) => setDownloadName(e.target.value)}
                                placeholder="Document name"
                                contentAfter={
                                    <Text size={400}>
                                        .pdf
                                    </Text>
                                }
                            />
                        </Field>
                    </DialogContent>
                    <DialogActions>
                        <Button appearance="primary"
                                className={globalStyles.buttonWithLoading}
                                shape={"circular"}
                                disabled={(downloadingDocument || !downloadName || !downloadName.trim())}
                                onClick={onDownload}>
                            {downloadingDocument && <Spinner size={"extra-small"}/>}
                            Download
                        </Button>
                        <DialogTrigger disableButtonEnhancement>
                            <Button appearance="secondary"
                                    shape={"circular"}
                                    disabled={downloadingDocument}
                                    onClick={onClose}>
                                Close
                            </Button>
                        </DialogTrigger>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
        }
    </>
}

export default SessionDocumentDownloadDialog;