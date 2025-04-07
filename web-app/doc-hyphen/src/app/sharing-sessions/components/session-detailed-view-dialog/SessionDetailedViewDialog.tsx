import {SharingSessionDetailedDto, SharingSessionStatus} from "../../../models/models.tsx";
import React from "react";
import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    Field,
    Input
} from "@fluentui/react-components";
import {formatDateWithOrdinal} from "../../../helpers.ts";
import {useSessionDDetailedViewDialogStyles} from "./SessionDetailedViewDialogStyles.tsx";

interface SessionDeleteDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    session: SharingSessionDetailedDto;
}

const SessionDetailedViewDialog: React.FC<SessionDeleteDialogProps> = (
    {
        isOpen,
        onDismiss,
        session
    }) =>
{
    const styles = useSessionDDetailedViewDialogStyles();

    const getStatusAsText = (status: SharingSessionStatus) =>
    {
        switch (status)
        {
            case SharingSessionStatus.ACCEPTED_STARTED:
                return "In Progress";
            case SharingSessionStatus.REJECTED:
                return "Rejected";
            case SharingSessionStatus.INITIATED:
                return "Initiated";
            case SharingSessionStatus.ENDED:
                return "Ended";
            default:
                return "Unknown";
        }
    }

    const getInitiatedBy = () =>
    {
        return session.initiator?.person?.firstName;
    }

    const getMainRecipient = () =>
    {
        return session.recipient?.person?.firstName || session.recipient?.email;
    }

    return <>
        {<Dialog modalType="alert" open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>Sharing Session Detailed View</DialogTitle>
                    <DialogContent>
                        {session && <>
                            <div className={styles.sessionStatuses}>
                                {getStatusAsText(session.status)}
                            </div>
                            <Field label={"Name"}>
                                <Input type="text" value={session.sessionName} disabled={true}/>
                            </Field>
                            <Field label={"Description"}>
                                <Input type="text" value={session.description || "No Description"} disabled={true}/>
                            </Field>
                            <Field label={"Initial Share Message"}>
                                <Input type="text" value={session.initialShareMessage || "No Message"} disabled={true}/>
                            </Field>
                            <Field label={"Started by"}>
                                <Input type="text" value={getInitiatedBy()} disabled={true}/>
                            </Field>
                            <Field label={"Main Recipient"}>
                                <Input type="text" value={getMainRecipient()} disabled={true}/>
                            </Field>
                            <Field label={"Date Initiated"}>
                                <Input type="text" value={formatDateWithOrdinal(session.createdDate)} disabled={true}/>
                            </Field>
                            {/*<Field label={"Total Documents"}>*/}
                            {/*    <Input type="text" value={session?.documents?.length || "0"} disabled={true}/>*/}
                            {/*</Field>*/}
                        </>
                        }
                    </DialogContent>
                    <DialogActions>

                        <Button appearance="primary" onClick={onDismiss}>
                            Close
                        </Button>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
        }
    </>
}

export default SessionDetailedViewDialog;