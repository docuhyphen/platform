import {DocumentDetailedDto, ExchangeDetailedDto} from "../../../models/models.tsx";
import React, {useEffect, useState} from "react";
import {useGlobalStyles} from "../../../../GlobalStyles.tsx";
import {deleteExchangeDocument} from "../../../../services/exchangeApi.ts";
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
    const [deletingDocument, setDeletingDocument] = React.useState(false);
    const [deleteStarted, setDeleteStarted] = React.useState(false);
    const [countdown, setCountdown] = React.useState(10);
    const timerRef = React.useRef<ReturnType<typeof setInterval> | null>(null);
    const globalStyles = useGlobalStyles();
    const [dialogErrorMessage, setDialogErrorMessage] = useState<string | null>(null);

    useEffect(() =>
    {
        if (isOpen) setDialogErrorMessage(null);
    }, [isOpen]);

    const onDelete = () =>
    {
        setDeleteStarted(true);
        setCountdown(10);

        timerRef.current = setInterval(() =>
        {
            setCountdown(prevCountdown =>
            {
                if (prevCountdown <= 1)
                {
                    clearInterval(timerRef.current!);
                    completeDeletion();
                    return 0;
                }
                return prevCountdown - 1;
            });
        }, 1000);
    };

    const onCancel = () =>
    {
        if (timerRef.current)
        {
            clearInterval(timerRef.current);
        }
        setDeleteStarted(false);
        setCountdown(10);
    };

    const onDismiss = () =>
    {
        if (timerRef.current)
        {
            clearInterval(timerRef.current);
        }
        setDeleteStarted(false);
        setCountdown(10);
        onClose();
    };

    const completeDeletion = async () =>
    {
        setDeletingDocument(true);

        try
        {
            await deleteExchangeDocument(exchange.id, exchangeDocument.id);
            onDocumentDeleted(exchangeDocument.id);
        }
        catch (error)
        {
            setDialogErrorMessage("Error deleting document");
            console.error("Error deleting document:", error);
        }
        finally
        {
            setDeletingDocument(false);
            setDeleteStarted(false);
            onClose();
        }
    };

    return (
        <Dialog modalType="alert" open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>Deleting {exchangeDocument && exchangeDocument.title}</DialogTitle>
                    <DialogContent>
                        {dialogErrorMessage && (
                            <MessageBar intent="error">
                                <MessageBarBody>
                                    <Text size={200}>{dialogErrorMessage}</Text>
                                </MessageBarBody>
                            </MessageBar>
                        )}
                        {deleteStarted ? (
                            <div>Deleting in {countdown} seconds...</div>
                        ) : (
                            <div>Are you sure you want to delete this document?</div>
                        )}
                    </DialogContent>
                    <DialogActions>
                        {deleteStarted ? (
                            <Button
                                id="document-delete-countdown-cancel-btn"
                                appearance="primary"
                                shape="circular"
                                onClick={onCancel}>
                                Cancel
                            </Button>
                        ) : (
                            <>
                                <Button
                                    id="document-delete-confirm-btn"
                                    appearance="primary"
                                    className={globalStyles.buttonWithLoading}
                                    shape="circular"
                                    onClick={onDelete}>
                                    {deletingDocument && <Spinner size="tiny"/>}
                                    Yes, Delete
                                </Button>
                                <DialogTrigger disableButtonEnhancement>
                                    <Button
                                        id="document-delete-cancel-btn"
                                        appearance="secondary"
                                        shape="circular"
                                        disabled={deletingDocument}
                                        onClick={onDismiss}>
                                        No, Cancel
                                    </Button>
                                </DialogTrigger>
                            </>
                        )}
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default ExchangeDocumentDeleteDialog;
