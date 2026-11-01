import {DocumentDetailedDto, ExchangeDetailedDto} from "../../../models/models.tsx";
import React from "react";
import {
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    MessageBar,
    MessageBarBody,
    Text
} from "@fluentui/react-components";
import {useExchangeDocumentDeletion} from "./useExchangeDocumentDeletion.ts";
import ExchangeDocumentDeleteActions from "./exchange-document-delete-actions/ExchangeDocumentDeleteActions.tsx";

interface DeleteDocumentDialogProps
{
    isOpen: boolean;
    onClose: () => void;
    exchangeDocument: DocumentDetailedDto;
    exchange: ExchangeDetailedDto;
    onDocumentDeleted: (documentId: string) => void;
}

const ExchangeDocumentDeleteDialog: React.FC<DeleteDocumentDialogProps> = (
    {
        isOpen,
        onClose,
        exchangeDocument,
        exchange,
        onDocumentDeleted
    }) =>
{
    const deletion = useExchangeDocumentDeletion(isOpen, exchange, exchangeDocument, onDocumentDeleted, onClose);

    return (
        <Dialog modalType="alert"
                open={isOpen}>
            <DialogSurface id={"exchange-document-delete-dialog-dialog-surface-1"}>
                <DialogBody id={"exchange-document-delete-dialog-dialog-body-1"}>
                    <DialogTitle id={"exchange-document-delete-dialog-dialog-title-1"}>Deleting {exchangeDocument && exchangeDocument.title}</DialogTitle>
                    <DialogContent id={"exchange-document-delete-dialog-dialog-content-1"}>
                        {deletion.dialogErrorMessage && (
                            <MessageBar id={"exchange-document-delete-dialog-message-bar-1"}
                                        intent="error">
                                <MessageBarBody id={"exchange-document-delete-dialog-message-bar-body-1"}>
                                    <Text id={"exchange-document-delete-dialog-text-1"}
                                          size={200}>{deletion.dialogErrorMessage}</Text>
                                </MessageBarBody>
                            </MessageBar>
                        )}
                        {deletion.deleteStarted ? (
                            <div id={"exchange-document-delete-dialog-div-1"}>Deleting in {deletion.countdown} seconds...</div>
                        ) : (
                            <div id={"exchange-document-delete-dialog-div-2"}>Are you sure you want to delete this document?</div>
                        )}
                    </DialogContent>
                    <DialogActions id={"exchange-document-delete-dialog-dialog-actions-1"}>
                        <ExchangeDocumentDeleteActions deleteStarted={deletion.deleteStarted}
                                                       deletingDocument={deletion.deletingDocument}
                                                       onDelete={deletion.onDelete}
                                                       onCancel={deletion.onCancel}
                                                       onDismiss={deletion.onDismiss}/>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default ExchangeDocumentDeleteDialog;
