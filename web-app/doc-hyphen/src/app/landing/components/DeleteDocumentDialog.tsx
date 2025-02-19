import {DocumentDetailedDto, SharingSessionDetailedDto} from "../../models/models.tsx";
import React from "react";
import useToken from "../../../context/useToken.tsx";
import {useGlobalStyles} from "../../../GlobalStyles.tsx";
import {deleteSharingSessionDocument} from "../../../services/sharingSessionApi.ts";
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
    sessionDocument: DocumentDetailedDto;
    session: SharingSessionDetailedDto;
    onDocumentDeleted: (documentId: string) => void;
}

const DeleteDocumentDialog: React.FC<DeleteDocumentDialogProps> = (
    {
        isOpen,
        onClose,
        sessionDocument,
        session,
        onDocumentDeleted
    }) =>
{

    const token = useToken();
    const [deletingDocument, setDeletingDocument] = React.useState(false);
    const globalStyles = useGlobalStyles()

    const onDelete = async () =>
    {
        setDeletingDocument(true)

        try
        {
            await deleteSharingSessionDocument(session.id, sessionDocument.id, token);
            onDocumentDeleted(sessionDocument.id);
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
                    <DialogTitle>Deleting {sessionDocument && sessionDocument.title}</DialogTitle>
                    <DialogContent>
                        Are you sure you want to delete this document?
                    </DialogContent>
                    <DialogActions>
                        <Button appearance="primary"
                                className={globalStyles.buttonWithLoading}
                                shape={"circular"}
                                onClick={onDelete}>
                            {deletingDocument && <Spinner size={"extra-small"}/>}
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

export default DeleteDocumentDialog;