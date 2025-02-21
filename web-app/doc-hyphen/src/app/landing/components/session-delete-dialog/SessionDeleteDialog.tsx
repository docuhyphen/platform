import {SharingSessionDetailedDto} from "../../../models/models.tsx";
import React from "react";
import useToken from "../../../../context/useToken.tsx";
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
    Spinner
} from "@fluentui/react-components";

interface SessionDeleteDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    session: SharingSessionDetailedDto;
    onSessionDeleted: (sessionId: string) => void;
}

const SessionDeleteDialog: React.FC<SessionDeleteDialogProps> = (
    {
        isOpen,
        onDismiss,
        session,
        onSessionDeleted
    }) =>
{

    const token = useToken();
    const [deletingSession, setDeletingSession] = React.useState(false);
    const globalStyles = useGlobalStyles()

    const onDelete = async () =>
    {
        setDeletingSession(true)

        try
        {
            await deleteSharingSession(session.id, token);
            onSessionDeleted(session.id);
        }
        catch (error)
        {
            alert("Error deleting document");
            console.error("Error deleting document:", error);
        }
        finally
        {
            setDeletingSession(false);
            onDismiss();
        }
    }

    return <>
        {<Dialog modalType="alert" open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>Deleting {session && session.sessionName}</DialogTitle>
                    <DialogContent>
                        Are you sure you want to delete this Sharing Session?
                    </DialogContent>
                    <DialogActions>
                        <Button appearance="primary"
                                className={globalStyles.buttonWithLoading}
                                shape={"circular"}
                                onClick={onDelete}>
                            {deletingSession && <Spinner size={"extra-small"}/>}
                            Yes, Delete
                        </Button>
                        <DialogTrigger disableButtonEnhancement>
                            <Button appearance="secondary"
                                    shape={"circular"}
                                    disabled={deletingSession}
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

export default SessionDeleteDialog;