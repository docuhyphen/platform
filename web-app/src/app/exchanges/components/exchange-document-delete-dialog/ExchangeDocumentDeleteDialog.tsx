import {DocumentDetailedDto, ExchangeDetailedDto} from "../../../models/models.tsx";
import React from "react";
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
    Spinner
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
    const globalStyles = useGlobalStyles()

    const onDelete = async () =>
    {
        setDeletingDocument(true)

        try
        {
            await deleteExchangeDocument(exchange.id, exchangeDocument.id);
            onDocumentDeleted(exchangeDocument.id);
        }
        catch (error)
        {
            alert("Error deleting document");
            console.error("Error deleting document:", error);
        }
        finally
        {
            setDeletingDocument(false);
            onClose();
        }
    }

    return <>
        {<Dialog modalType="alert" open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>Deleting {exchangeDocument && exchangeDocument.title}</DialogTitle>
                    <DialogContent>
                        Are you sure you want to delete this document?
                    </DialogContent>
                    <DialogActions>
                        <Button appearance="primary"
                                className={globalStyles.buttonWithLoading}
                                shape={"circular"}
                                onClick={onDelete}>
                            {deletingDocument && <Spinner size={"tiny"}/>}
                            Yes, Delete
                        </Button>
                        <DialogTrigger disableButtonEnhancement>
                            <Button appearance="secondary"
                                    shape={"circular"}
                                    disabled={deletingDocument}
                                    onClick={onClose}>
                                No, Cancel
                            </Button>
                        </DialogTrigger>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
        }
    </>
}

export default ExchangeDocumentDeleteDialog;