import React from "react";
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
    Spinner
} from "@fluentui/react-components";
import {useGlobalStyles} from "../../../../GlobalStyles.tsx";
import {DocumentDetailedDto} from "../../../models/models.tsx";
import {useExchangeDocumentUploadDialogStyles} from "./ExchangeDocumentUploadDialogStyles.tsx";
import {useExchangeDocumentUpload} from "./useExchangeDocumentUpload.ts";
import UploadFilePicker from "./upload-file-picker/UploadFilePicker.tsx";
import UploadFileSummary from "./upload-file-summary/UploadFileSummary.tsx";
import UploadProgressIndicator from "./upload-progress-indicator/UploadProgressIndicator.tsx";

interface UploadDocumentDialogProps
{
    isOpen: boolean;
    exchangeId: string;
    exchangeDocument: DocumentDetailedDto;
    onDismiss: () => void;
    onDocumentUploaded: (document: DocumentDetailedDto) => void;
}

const ExchangeDocumentUploadDialog: React.FC<UploadDocumentDialogProps> = (
    {
        isOpen,
        exchangeId,
        exchangeDocument,
        onDismiss,
        onDocumentUploaded
    }) =>
{
    const globalStyles = useGlobalStyles();
    const styles = useExchangeDocumentUploadDialogStyles();
    const {
        fileInputRef,
        file,
        uploading,
        progress,
        isServerProcessing,
        fileError,
        allowedExtensions,
        restrictionLabel,
        onPickFile,
        onUploadDocument,
        onDismissDialog
    } = useExchangeDocumentUpload(exchangeId, exchangeDocument, onDismiss, onDocumentUploaded);

    return (
        <Dialog modalType="alert"
                open={isOpen}>
            <DialogSurface id={"exchange-document-upload-dialog-dialog-surface-1"}>
                <DialogBody id={"exchange-document-upload-dialog-dialog-body-1"}>
                    <DialogTitle id={"exchange-document-upload-dialog-dialog-title-1"}>Upload {exchangeDocument?.title}</DialogTitle>
                    <DialogContent id={"exchange-document-upload-dialog-dialog-content-1"}
                                   className={styles.contentContainer}>
                        <UploadFilePicker fileInputRef={fileInputRef}
                                          allowedExtensions={allowedExtensions}
                                          disabled={uploading}
                                          onPickFile={onPickFile}/>

                        {fileError && (
                            <MessageBar id={"exchange-document-upload-dialog-message-bar-1"}
                                        intent="error">
                                <MessageBarBody id={"exchange-document-upload-dialog-message-bar-body-1"}>{fileError}</MessageBarBody>
                            </MessageBar>
                        )}

                        {file && (
                            <UploadFileSummary file={file}
                                               targetDocumentTitle={exchangeDocument?.title}
                                               restrictionLabel={restrictionLabel}/>
                        )}

                        {uploading && (
                            <UploadProgressIndicator progress={progress}
                                                     isServerProcessing={isServerProcessing}/>
                        )}
                    </DialogContent>
                    <DialogActions id={"exchange-document-upload-dialog-dialog-actions-1"}>
                        <Button id="upload-document-submit-btn"
                                appearance="primary"
                                className={globalStyles.buttonWithLoading}
                                onClick={onUploadDocument}
                                shape="circular"
                                disabled={!file || uploading || !!fileError}>
                            {uploading && <Spinner id={"exchange-document-upload-dialog-spinner-1"}
                                                   size="tiny"/>} Upload
                        </Button>
                        <DialogTrigger disableButtonEnhancement>
                            <Button id="upload-document-close-btn"
                                    appearance="secondary"
                                    onClick={onDismissDialog}
                                    disabled={uploading}
                                    shape="circular">
                                Close
                            </Button>
                        </DialogTrigger>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default ExchangeDocumentUploadDialog;
