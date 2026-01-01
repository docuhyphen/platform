import React from "react";
import {
    Button,
    Checkbox,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    Field,
    Spinner,
    Text,
    Textarea
} from "@fluentui/react-components";
import {SharingSessionDetailedDto, SharingSessionStatus, UpdateSharingSessionRequest} from "../../../models/models.tsx";
import {useGlobalStyles} from "../../../../GlobalStyles.tsx";
import {fetchSignedInUserAppUserSharingSession, updateSharingSession} from "../../../../services/sharingSessionApi.ts";
import {useSessionAcceptanceDialogStyles} from "./SessionAcceptanceDialogStyles.tsx";

interface SessionDeleteDialogProps
{
    isOpen: boolean;
    session: SharingSessionDetailedDto | null;
    onAccepted: (session: SharingSessionDetailedDto) => void;
    onRejected: (session: SharingSessionDetailedDto) => void;
}

const SessionAcceptanceDialog: React.FC<SessionDeleteDialogProps> = (
    {
        isOpen,
        session,
        onAccepted,
        onRejected
    }) =>
{
    const [updatingSession, setUpdatingSession] = React.useState(false);
    const [rejectingSession, setRejectingSession] = React.useState(false);
    const [rejectReason, setRejectReason] = React.useState<string>('');
    const globalStyles = useGlobalStyles();
    const styles = useSessionAcceptanceDialogStyles();

    const onAcceptOrRejected = async (status: SharingSessionStatus) =>
    {
        if (updatingSession || !session)
        {
            return;
        }

        setUpdatingSession(true);

        try
        {
            const request = {status} as UpdateSharingSessionRequest;

            if (rejectingSession)
            {
                request.rejectionReason = rejectReason;
            }

            await updateSharingSession(session.id, request);
            const updatedSession = await fetchSignedInUserAppUserSharingSession(session.id);

            if (rejectingSession)
            {
                onRejected(updatedSession as SharingSessionDetailedDto);
                setRejectReason('');
            }
            else
            {
                onAccepted(updatedSession as SharingSessionDetailedDto);
            }

        }
        catch (error)
        {
            alert("Error updating session");
            console.error("Error updating session", error);
        }
        finally
        {
            setUpdatingSession(false);
        }
    };

    const onAccept = () =>
    {
        onAcceptOrRejected(SharingSessionStatus.ACCEPTED_STARTED);
    };

    const onReject = () =>
    {
        onAcceptOrRejected(SharingSessionStatus.REJECTED);
    };

    const onRejectReasonChange = (event: React.ChangeEvent<HTMLTextAreaElement>) =>
    {
        setRejectReason(event.target.value);
    };

    return <section id={"session-acceptance-dialog"}>
        {session && <Dialog modalType="non-modal" open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>New Sharing Session Request</DialogTitle>
                    <DialogContent className={styles.dialogContent}>
                        <Text size={400}
                              align={"center"}>
                            {session.initiator?.person?.firstName} {session.initiator?.person?.lastName} has requested
                            to share documents with you.
                        </Text>
                        <div className={styles.sessionNameContainer}>
                            <Text size={300}
                                  align={"center"}
                                  weight={"bold"}>
                                Session
                            </Text>
                            <Text size={300}
                                  align={"center"}>
                                {session.sessionName}
                            </Text>
                        </div>
                        {session.initialShareMessage &&
                            <div className={styles.messageContainer}>
                                <Text size={300}
                                      align={"center"}
                                      weight={"bold"}>
                                    Message
                                </Text>
                                <Text size={300}
                                      align={"center"}>
                                    {session.initialShareMessage}
                                </Text>
                            </div>
                        }
                        {rejectingSession &&
                            <div className={styles.declineFieldContainer}>
                                <Field>
                                    <Textarea placeholder={"Reason for declining"}
                                              value={rejectReason}
                                              onChange={onRejectReasonChange}
                                              maxLength={100}/>
                                </Field>
                                <Checkbox label={"Report"}/>
                            </div>
                        }
                    </DialogContent>
                    <DialogActions>
                        <Button appearance="primary"
                                disabled={updatingSession}
                                className={globalStyles.buttonWithLoading}
                                shape={"circular"}
                                onClick={onAccept}>
                            {(!rejectingSession && updatingSession) && <Spinner size={"tiny"}/>}
                            Accept
                        </Button>
                        {!rejectingSession &&

                            <Button appearance="secondary"
                                    className={globalStyles.buttonWithLoading}
                                    shape={"circular"}
                                    disabled={updatingSession}
                                    onClick={() => setRejectingSession(true)}>
                                Decline
                            </Button>
                        }
                        {rejectingSession &&
                            <Button appearance="secondary"
                                    className={globalStyles.buttonWithLoading}
                                    shape={"circular"}
                                    disabled={updatingSession}
                                    onClick={onReject}>
                                {rejectingSession && updatingSession && <Spinner size={"tiny"}/>}
                                Continue Decline
                            </Button>
                        }
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
        }
    </section>;
};

export default SessionAcceptanceDialog;