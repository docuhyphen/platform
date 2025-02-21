import {DocumentDetailedDto, SharingSessionDetailedDto} from "../../../models/models.tsx";
import React from "react";
import useToken from "../../../../context/useToken.tsx";
import {useGlobalStyles} from "../../../../GlobalStyles.tsx";
import {deleteSharingSessionDocument} from "../../../../services/sharingSessionApi.ts";
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
    onDismiss: () => void;
    sessionDocument: DocumentDetailedDto;
    session: SharingSessionDetailedDto;
    onDocumentDeleted: (documentId: string) => void;
}

const SessionEndDialog: React.FC<DeleteDocumentDialogProps> = (
    {
        isOpen,
        onDismiss,
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
            onDismiss();
        }
    }

    return <>
        {<Dialog modalType="alert" open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>Ending Session: {sessionDocument && sessionDocument.title}</DialogTitle>
                    <DialogContent>
                        DISPLAY STATUS
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
                                    onClick={onDismiss}>
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

export default SessionEndDialog;