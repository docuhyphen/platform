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
    Spinner,
    Textarea
} from "@fluentui/react-components";
import {useSessionEndDialogStyles} from "./SessionEndDialogStyles.tsx";
import {fetchSignedInUserAppUserSharingSession, updateSharingSession} from "../../../../services/sharingSessionApi.ts";

interface SessionEndDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    session: SharingSessionDetailedDto;
    onSessionEnded: (session: SharingSessionDetailedDto) => void;
}

const SessionEndDialog: React.FC<SessionEndDialogProps> = (
    {
        isOpen,
        onDismiss,
        session,
        onSessionEnded
    }) =>
{

    const token = useToken();
    const [sessionEndNote, setSessionEndNote] = React.useState('');
    const [endingSession, setEndingSession] = React.useState(false);
    const globalStyles = useGlobalStyles()

    const onSessionEnd = async () =>
    {
        setEndingSession(true)

        try
        {
            const request = {
                status: SharingSessionStatus.ENDED
            } as UpdateSharingSessionRequest

            await updateSharingSession(session.id, request, token);
            const updatedSession = await fetchSignedInUserAppUserSharingSession(session.id, token);
            onSessionEnded(updatedSession as SharingSessionDetailedDto);
            setSessionEndNote('');
        }
        catch (error)
        {
            alert("Error ending sharing session");
            console.error("Error ending sharing session", error);
        }
        finally
        {
            setEndingSession(false);
        }
    }

    const onEndNoteChange = (_, newValue) =>
    {
        setSessionEndNote(newValue.value || '')
    }

    const onCancel = () =>
    {
        setSessionEndNote('');
        onDismiss();
    }

    const styles = useSessionEndDialogStyles();

    return <>
        {<Dialog modalType="alert" open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>Ending Session: {session && session.sessionName}</DialogTitle>
                    <DialogContent>
                        <Field label={"End notes"} className={styles.endNoteField}>
                            <Textarea value={sessionEndNote}
                                      onChange={onEndNoteChange}/>
                        </Field>
                    </DialogContent>
                    <DialogActions>
                        <Button appearance="primary"
                                className={globalStyles.buttonWithLoading}
                                shape={"circular"}
                                onClick={onSessionEnd}>
                            {endingSession && <Spinner size={"extra-small"}/>}
                            End Session
                        </Button>
                        <DialogTrigger disableButtonEnhancement>
                            <Button appearance="secondary"
                                    shape={"circular"}
                                    disabled={endingSession}
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

export default SessionEndDialog;