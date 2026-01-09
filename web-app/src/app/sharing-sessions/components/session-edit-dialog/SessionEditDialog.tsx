import {SharingSessionDetailedDto, UpdateSharingSessionRequest} from "../../../models/models.tsx";
import React, {ChangeEvent, useEffect} from "react";
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
    InputOnChangeData,
    Spinner,
    Textarea
} from "@fluentui/react-components";
import {fetchSignedInUserAppUserSharingSession, updateSharingSession} from "../../../../services/sharingSessionApi.ts";
import {publishSharingSessionUpdate} from "../../../observable/sharingSessionObservables.ts";
import {useSessionEditDialogStyles} from "./SessionEditDialogStyles.tsx";

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
    const styles = useSessionEditDialogStyles()
    const token = useToken();
    const [editingSession, setEditingSession] = React.useState(false);
    const [sessionName, setSessionName] = React.useState('')
    const [description, setDescription] = React.useState('')
    const globalStyles = useGlobalStyles()

    useEffect(() =>
    {
        if (session)
        {
            setSessionName(session.sessionName)
            setDescription(session.description)
        }
    }, [session]);

    const onEdit = async () =>
    {

        setEditingSession(true)

        try
        {
            const request = {
                sessionName,
                description
            } as UpdateSharingSessionRequest

            await updateSharingSession(session.id, request, token);
            const updatedSession = await fetchSignedInUserAppUserSharingSession(session.id, token);
            onSessionEdited(updatedSession as SharingSessionDetailedDto);
            publishSharingSessionUpdate(updatedSession as SharingSessionDetailedDto)
            onDismiss();
        }
        catch (error)
        {
            alert("Error updating session");
            console.error("Error updating session", error);
        }
        finally
        {
            setEditingSession(false);
        }
    }

    const onCancel = () =>
    {
        setSessionName('')
        setDescription('')
        onDismiss()
    }

    const onSessionNameChange = (_e: ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData) =>
    {
        setSessionName(newValue.value || '');
    }

    const onDescriptionChange = (_e: ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData) =>
    {
        setDescription(newValue.value || '');
    }

    return <>
        {<Dialog modalType="alert" open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>Edit {session && session.sessionName}</DialogTitle>
                    <DialogContent className={styles.dialogContentContainer}>
                        <Field label={"Session name"}>
                            <Input type={"text"}
                                   value={sessionName}
                                   onChange={onSessionNameChange}/>
                        </Field>
                        <Field label={"Description"}>
                            <Textarea value={description}
                                      onChange={onDescriptionChange}/>
                        </Field>
                    </DialogContent>
                    <DialogActions>
                        <Button appearance="primary"
                                className={globalStyles.buttonWithLoading}
                                shape={"circular"}
                                onClick={onEdit}>
                            {editingSession && <Spinner size={"tiny"}/>}
                            Edit
                        </Button>
                        <DialogTrigger disableButtonEnhancement>
                            <Button appearance="secondary"
                                    shape={"circular"}
                                    disabled={editingSession}
                                    onClick={onCancel}>
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