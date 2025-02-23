import {SharingSessionDetailedDto, SharingSessionStatus, UpdateSharingSessionRequest} from "../../../models/models.tsx";
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
    Field,
    Input,
    Spinner,
    Textarea
} from "@fluentui/react-components";
import {fetchSignedInUserAppUserSharingSession, updateSharingSession} from "../../../../services/sharingSessionApi.ts";

interface SessionDeleteDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    session: SharingSessionDetailedDto;
    onSessionEdited: (session: SharingSessionDetailedDto) => void;
}

const SessionEditDialog: React.FC<SessionDeleteDialogProps> = (
    {
        isOpen,
        onDismiss,
        session,
        onSessionEdited
    }) =>
{

    const token = useToken();
    const [editingSession, setEditingSession] = React.useState(false);
    const globalStyles = useGlobalStyles()

    const onEdit = async () =>
    {

        setEditingSession(true)

        try
        {
            const request = {
                status: SharingSessionStatus.ENDED
            } as UpdateSharingSessionRequest

            await updateSharingSession(session.id, request, token);
            const updatedSession = await fetchSignedInUserAppUserSharingSession(session.id, token);
            onSessionEdited(updatedSession as SharingSessionDetailedDto);
            onDismiss();
        }
        catch (error)
        {
            alert("Error deleting document");
            console.error("Error deleting document:", error);
        }
        finally
        {
            setEditingSession(false);
        }
    }

    return <>
        {<Dialog modalType="alert" open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>Edit {session && session.sessionName}</DialogTitle>
                    <DialogContent>
                        <Field label={"Session name"}>
                            <Input type={"text"}/>
                        </Field>
                        <Field label={"Description"}>
                            <Textarea/>
                        </Field>
                    </DialogContent>
                    <DialogActions>
                        <Button appearance="primary"
                                className={globalStyles.buttonWithLoading}
                                shape={"circular"}
                                onClick={onEdit}>
                            {editingSession && <Spinner size={"extra-small"}/>}
                            Edit
                        </Button>
                        <DialogTrigger disableButtonEnhancement>
                            <Button appearance="secondary"
                                    shape={"circular"}
                                    disabled={editingSession}
                                    onClick={onDismiss}>
                                Cancel
                            </Button>
                        </DialogTrigger>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
        }
    </>
}

export default SessionEditDialog;