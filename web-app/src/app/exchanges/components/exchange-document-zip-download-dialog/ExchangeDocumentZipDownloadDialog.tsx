import {ExchangeDetailedDto} from "../../../models/models.tsx";
import React, {useEffect, useState} from "react";
import useToken from "../../../../context/useToken.tsx";
import {useGlobalStyles} from "../../../../GlobalStyles.tsx";
import {downloadExchangeDocumentZip} from "../../../../services/exchangeApi.ts";
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
    MessageBar,
    MessageBarBody,
    Spinner,
    Text
} from "@fluentui/react-components";
import {useExchangeDocumentZipDownloadDialogStyles} from "./ExchangeDocumentZipDownloadDialogStyles.tsx";

interface DownloadDocumentDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    exchange: ExchangeDetailedDto;
}

const ExchangeDocumentZipDownloadDialog: React.FC<DownloadDocumentDialogProps> = (
    {
        isOpen,
        onDismiss,
        exchange,
    }) =>
{
    const token = useToken();
    const [downloadName, setDownloadName] = React.useState('');
    const [downloadingDocumentsZip, setDownloadingDocumentsZip] = React.useState(false);
    const [selectedDocuments, setSelectedDocuments] = React.useState<string[]>([]);
    const globalStyles = useGlobalStyles();
    const styles = useExchangeDocumentZipDownloadDialogStyles();
    const [dialogErrorMessage, setDialogErrorMessage] = useState<string | null>(null);

    useEffect(() =>
    {
        setDownloadName(`${exchange?.name?.replace(/\s+/g, '-')}-documents`);
    }, [exchange]);

    const onDownload = async () =>
    {
        setDownloadingDocumentsZip(true);

        try
        {
            const data = await downloadExchangeDocumentZip(exchange.id, {documentIds: selectedDocuments}, token);
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
            setDialogErrorMessage("Download failed");
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
                        {dialogErrorMessage && (
                            <MessageBar intent="error">
                                <MessageBarBody>
                                    <Text size={200}>{dialogErrorMessage}</Text>
                                </MessageBarBody>
                            </MessageBar>
                        )}
                        <div className={styles.downloadContainer}>
                            <Field className={styles.downloadNameField}>
                                <Input
                                    id={"input-zip-download-name"}
                                    type="text"
                                    value={downloadName}
                                    required
                                    onChange={(e) => setDownloadName(e.target.value)}
                                    placeholder="Zip name"
                                    contentAfter={<Text size={400}>.zip</Text>}
                                />
                            </Field>
                            {exchange?.documents?.filter(d => d.uploadDate).map(document => (
                                <Checkbox
                                    id={`checkbox-zip-download-doc-${document.id}`}
                                    key={document.id}
                                    label={document.title}
                                    onChange={(e, data) => handleDocumentSelection(document.id, data.checked)}
                                />
                            ))}
                        </div>
                    </DialogContent>
                    <DialogActions>
                        <Button
                            id="zip-download-confirm-btn"
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
                                id="zip-download-close-btn"
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

export default ExchangeDocumentZipDownloadDialog;
