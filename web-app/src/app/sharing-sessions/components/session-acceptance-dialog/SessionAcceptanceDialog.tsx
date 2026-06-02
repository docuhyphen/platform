import React from "react";
import {
    Button,
    Card,
    Checkbox,
    Field,
    Spinner,
    Text,
    Textarea
} from "@fluentui/react-components";
import {SharingSessionDetailedDto, SharingSessionStatus, UpdateSharingSessionRequest} from "../../../models/models.tsx";
import {useGlobalStyles} from "../../../../GlobalStyles.tsx";
import {fetchSignedInUserAppUserSharingSession, updateSharingSession} from "../../../../services/sharingSessionApi.ts";
import {useSessionAcceptanceDialogStyles} from "./SessionAcceptanceDialogStyles.tsx";
import {publishSharingSessionUpdate} from "../../../observable/sharingSessionObservables.ts";

interface SessionAcceptanceDialogProps
{
    session: SharingSessionDetailedDto | null;
    // Controls whether "Decide Later" is shown; dialog remains blocking either way.
    isSingleSession: boolean;
    canDecideLater: boolean;
    activeCount: number;
    archiveCount: number;
    onAccepted: (session: SharingSessionDetailedDto) => void;
    onRejected: (session: SharingSessionDetailedDto) => void;
    onDismiss: () => void;
    onOpenActive: () => void;
    onOpenArchive: () => void;
}

const SessionAcceptanceDialog: React.FC<SessionAcceptanceDialogProps> = (
    {
        session,
        isSingleSession,
        canDecideLater,
        activeCount,
        archiveCount,
        onAccepted,
        onRejected,
        onDismiss,
        onOpenActive,
        onOpenArchive,
    }) =>
{
    const [updatingSession, setUpdatingSession] = React.useState(false);
    const [rejectingSession, setRejectingSession] = React.useState(false);
    const [rejectReason, setRejectReason] = React.useState<string>('');
    const globalStyles = useGlobalStyles();
    const styles = useSessionAcceptanceDialogStyles();

    if (!session) return null;

    const onAcceptOrReject = async (status: SharingSessionStatus) =>
    {
        if (updatingSession) return;
        setUpdatingSession(true);
        try
        {
            const request: UpdateSharingSessionRequest = {status};
            if (rejectingSession) request.rejectionReason = rejectReason;

            await updateSharingSession(session.id, request);
            const updatedSession = await fetchSignedInUserAppUserSharingSession(session.id) as SharingSessionDetailedDto;
            publishSharingSessionUpdate(updatedSession);

            if (rejectingSession)
            {
                setRejectReason('');
                onRejected(updatedSession);
            }
            else
            {
                onAccepted(updatedSession);
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

    const cancelDecline = () =>
    {
        setRejectingSession(false);
        setRejectReason('');
    };

    const initiatorName = [session.initiator?.person?.firstName, session.initiator?.person?.lastName]
        .filter(Boolean).join(' ') || session.initiator?.email;
    const initiatorEmail = session.initiator?.email || 'Not provided';
    const recipientEmail = session.recipient?.email || 'Not provided';

    const requestedDocuments = (session.documents || []).filter(document => !document.uploadDate);
    const sharedDocuments = (session.documents || []).filter(document => !!document.uploadDate);

    const cardInner = (
        <>
            <Text size={400} weight="semibold" align="center">New Document Request</Text>

            <Text size={300} align="center">
                <strong>{initiatorName} ({initiatorEmail})</strong> has requested to share documents with you.
            </Text>

            <div className={styles.sessionNameContainer}>
                <Text size={200} weight="bold" align="center">SESSION</Text>
                <Text size={300} align="center">{session.sessionName}</Text>
            </div>

            {session.initialShareMessage && (
                <div className={styles.messageContainer}>
                    <Text size={200} weight="bold" align="center">MESSAGE</Text>
                    <Text size={300} align="center">{session.initialShareMessage}</Text>
                </div>
            )}

            <div className={styles.documentsInfoContainer}>
                <Text size={200} weight="bold" align="center">DOCUMENTS</Text>

                {requestedDocuments.length > 0 && (
                    <div className={styles.documentsGroup}>
                        <Text size={200} weight="semibold" align="center">Requested from you ({requestedDocuments.length})</Text>
                        {requestedDocuments.slice(0, 5).map(document => (
                            <Text key={document.id} size={200} align="center">- {document.title}</Text>
                        ))}
                    </div>
                )}

                {sharedDocuments.length > 0 && (
                    <div className={styles.documentsGroup}>
                        <Text size={200} weight="semibold" align="center">Already shared ({sharedDocuments.length})</Text>
                        {sharedDocuments.slice(0, 5).map(document => (
                            <Text key={document.id} size={200} align="center">- {document.title}</Text>
                        ))}
                    </div>
                )}

                {requestedDocuments.length === 0 && sharedDocuments.length === 0 && (
                    <Text size={200} align="center">No document details provided yet.</Text>
                )}
            </div>

            {rejectingSession && (
                <div className={styles.declineFieldContainer}>
                    <Field>
                        <Textarea
                            placeholder="Reason for declining (optional)"
                            value={rejectReason}
                            onChange={e => setRejectReason(e.target.value)}
                            maxLength={100}
                        />
                    </Field>
                    <Checkbox label="Report"/>
                </div>
            )}

            <div className={styles.actions}>
                <Button
                    appearance="primary"
                    disabled={updatingSession}
                    className={globalStyles.buttonWithLoading}
                    shape="circular"
                    onClick={() => onAcceptOrReject(SharingSessionStatus.ACCEPTED_STARTED)}>
                    {!rejectingSession && updatingSession && <Spinner size="tiny"/>}
                    Accept
                </Button>

                {!rejectingSession && (
                    <Button
                        appearance="secondary"
                        shape="circular"
                        disabled={updatingSession}
                        onClick={() => setRejectingSession(true)}>
                        Decline
                    </Button>
                )}

                {rejectingSession && (
                    <>
                        <Button
                            appearance="secondary"
                            className={globalStyles.buttonWithLoading}
                            shape="circular"
                            disabled={updatingSession}
                            onClick={() => onAcceptOrReject(SharingSessionStatus.REJECTED)}>
                            {updatingSession && <Spinner size="tiny"/>}
                            Confirm Decline
                        </Button>
                        <Button
                            appearance="subtle"
                            shape="circular"
                            disabled={updatingSession}
                            onClick={cancelDecline}>
                            Cancel
                        </Button>
                    </>
                )}

                {!isSingleSession && !rejectingSession && (
                    canDecideLater ?
                        <Button
                            appearance="subtle"
                            shape="circular"
                            disabled={updatingSession}
                            onClick={onDismiss}>
                            Decide Later
                        </Button>
                        :
                        <>
                            <Button
                                appearance="secondary"
                                shape="circular"
                                disabled={updatingSession || activeCount === 0}
                                onClick={onOpenActive}>
                                Open Active ({activeCount})
                            </Button>
                            <Button
                                appearance="secondary"
                                shape="circular"
                                disabled={updatingSession || archiveCount === 0}
                                onClick={onOpenArchive}>
                                Open Archive ({archiveCount})
                            </Button>
                        </>
                )}
            </div>

            {!rejectingSession && !canDecideLater && (
                <Text size={200} align="center" className={styles.navigationHint}>
                    This is the last pending inbox request.
                </Text>
            )}
        </>
    );


    return (
        <div className={styles.overlay}>
            <Card className={styles.overlayCard}>
                {cardInner}
            </Card>
        </div>
    );
};

export default SessionAcceptanceDialog;
