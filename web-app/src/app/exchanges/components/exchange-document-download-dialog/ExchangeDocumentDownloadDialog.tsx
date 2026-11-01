import {DocumentDetailedDto, ExchangeDetailedDto} from "../../../models/models.tsx";
import React, {useEffect, useState} from "react";
import {useGlobalStyles} from "../../../../GlobalStyles.tsx";
import {downloadExchangeDocument} from "../../../../services/exchangeApi.ts";
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
    MessageBar,
    MessageBarBody,
    Spinner,
    Text
} from "@fluentui/react-components";
import {useExchangeDocumentDownloadDialogStyles} from "./ExchangeDocumentDownloadDialogStyles.tsx";

interface DownloadDocumentDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    exchangeDocument: DocumentDetailedDto;
    exchange: ExchangeDetailedDto;
}

const ExchangeDocumentDownloadDialog: React.FC<DownloadDocumentDialogProps> = (
    {
        isOpen,
        onDismiss,
        exchangeDocument,
        exchange,
    }) =>
{
    const [downloadName, setDownloadName] = React.useState('');
    const [downloadingDocument, setDownloadingDocument] = React.useState(false);
    const globalStyles = useGlobalStyles()
    const [dialogErrorMessage, setDialogErrorMessage] = useState<string | null>(null);

    const styles = useExchangeDocumentDownloadDialogStyles();

    useEffect(() =>
    {
        setDownloadName(`${exchange?.name?.replace(/\s+/g, '-')}-${exchangeDocument?.title?.replace(/\s+/g, '-')}`);
    }, []);

    const onDownload = async () =>
    {
        setDownloadingDocument(true)

        try
        {
            const data = await downloadExchangeDocument(exchange.id, exchangeDocument.id);
            const url = window.URL.createObjectURL(new Blob([data], {type: 'application/octet-stream'}));
            const link = window.document.createElement('a');

            link.id = 'f-download-link';
            link.href = url;
            link.setAttribute('download', `${downloadName}.${exchangeDocument.type}`);

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
            setDownloadingDocument(false);
        }
    }

    const onClose = () =>
    {
        setDownloadName('');
        onDismiss();
    }

    return <>
        {<Dialog modalType="alert"
                 open={isOpen}>
            <DialogSurface id={"exchange-document-download-dialog-dialog-surface-1"}>
                <DialogBody id={"exchange-document-download-dialog-dialog-body-1"}>
                    <DialogTitle id={"exchange-document-download-dialog-dialog-title-1"}>Downloading {exchangeDocument && exchangeDocument.title}</DialogTitle>
                    <DialogContent id={"exchange-document-download-dialog-dialog-content-1"}>
                        {dialogErrorMessage && (
                            <MessageBar id={"exchange-document-download-dialog-message-bar-1"}
                                        intent="error">
                                <MessageBarBody id={"exchange-document-download-dialog-message-bar-body-1"}>
                                    <Text id={"exchange-document-download-dialog-text-1"}
                                          size={200}>{dialogErrorMessage}</Text>
                                </MessageBarBody>
                            </MessageBar>
                        )}
                        <Field id={"exchange-document-download-dialog-field-1"}
                               className={styles.downloadNameField}>
                            <Input
                                id={"input-download-document-name"}
                                type="text"
                                value={downloadName}
                                required
                                onChange={(e) => setDownloadName(e.target.value)}
                                placeholder="Document name"
                                contentAfter={
                                    <Text id={"exchange-document-download-dialog-text-2"}
                                          size={400}>
                                        .{exchangeDocument && (exchangeDocument?.type?.toLowerCase())}
                                    </Text>
                                }
                            />
                        </Field>
                    </DialogContent>
                    <DialogActions id={"exchange-document-download-dialog-dialog-actions-1"}>
                        <Button id={"download-document-submit-btn"}
                                appearance="primary"
                                className={globalStyles.buttonWithLoading}
                                shape={"circular"}
                                disabled={(downloadingDocument || !downloadName || !downloadName.trim())}
                                onClick={onDownload}>
                            {downloadingDocument && <Spinner id={"exchange-document-download-dialog-spinner-1"}
                                                             size={"tiny"}/>}
                            Download
                        </Button>
                        <DialogTrigger disableButtonEnhancement>
                            <Button id={"download-document-close-btn"}
                                    appearance="secondary"
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

export default ExchangeDocumentDownloadDialog;