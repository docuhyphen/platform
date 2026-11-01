import {ExchangeDetailedDto} from "../../../models/models.tsx";
import React from "react";
import {useGlobalStyles} from "../../../../GlobalStyles.tsx";
import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    DialogTrigger,
    MessageBar,
    MessageBarBody,
    Spinner,
    Text
} from "@fluentui/react-components";
import {useExchangeDocumentZipDownload} from "./useExchangeDocumentZipDownload.ts";
import ExchangeDocumentZipSelection from "./exchange-document-zip-selection/ExchangeDocumentZipSelection.tsx";

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
    const globalStyles = useGlobalStyles();
    const download = useExchangeDocumentZipDownload(exchange, onDismiss);

    return (
        <Dialog modalType="alert"
                open={isOpen}>
            <DialogSurface id={"exchange-document-zip-download-dialog-dialog-surface-1"}>
                <DialogBody id={"exchange-document-zip-download-dialog-dialog-body-1"}>
                    <DialogTitle id={"exchange-document-zip-download-dialog-dialog-title-1"}>Download Compressed Documents</DialogTitle>
                    <DialogContent id={"exchange-document-zip-download-dialog-dialog-content-1"}>
                        {download.dialogErrorMessage && (
                            <MessageBar id={"exchange-document-zip-download-dialog-message-bar-1"}
                                        intent="error">
                                <MessageBarBody id={"exchange-document-zip-download-dialog-message-bar-body-1"}>
                                    <Text id={"exchange-document-zip-download-dialog-text-1"}
                                          size={200}>{download.dialogErrorMessage}</Text>
                                </MessageBarBody>
                            </MessageBar>
                        )}
                        <ExchangeDocumentZipSelection exchange={exchange}
                                                      downloadName={download.downloadName}
                                                      onDownloadNameChange={download.setDownloadName}
                                                      onDocumentSelection={download.handleDocumentSelection}/>
                    </DialogContent>
                    <DialogActions id={"exchange-document-zip-download-dialog-dialog-actions-1"}>
                        <Button id="zip-download-confirm-btn"
                                appearance="primary"
                                className={globalStyles.buttonWithLoading}
                                shape={"circular"}
                                disabled={download.downloadingDocumentsZip || !download.downloadName.trim()}
                                onClick={download.onDownload}>
                            {download.downloadingDocumentsZip && <Spinner id={"exchange-document-zip-download-dialog-spinner-1"}
                                                                          size={"tiny"}/>}
                            Download
                        </Button>
                        <DialogTrigger disableButtonEnhancement>
                            <Button id="zip-download-close-btn"
                                    appearance="secondary"
                                    shape={"circular"}
                                    disabled={download.downloadingDocumentsZip}
                                    onClick={download.onClose}>
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
