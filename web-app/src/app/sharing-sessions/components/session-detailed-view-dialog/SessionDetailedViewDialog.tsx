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
    Input,
    mergeClasses,
    Text
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
        return `${session.initiator?.person?.firstName} ${session.initiator?.person?.lastName} (${session.initiator?.email})`;
    }

    const getMainRecipient = () =>
    {
        return `${session.recipient?.person?.firstName} ${session.recipient?.person?.lastName} (${session.recipient?.email})`;
    }

    const getStatusList = () =>
    {
        return Object.values(SharingSessionStatus).map((status) =>
        {
            const commonClass = styles.sessionStatus;
            const statusClass = styles[`sessionStatus${status}` as keyof typeof styles];
            const currentStatusClass = styles[`sessionCurrentStatus` as keyof typeof styles];

            return <>
                {session.status === status &&
                    <div className={mergeClasses(commonClass, statusClass, currentStatusClass)} key={status}>
                        <Text weight={"bold"}> {getStatusAsText(status)} </Text>
                    </div>
                }
                {session.status != status &&

                    <div className={mergeClasses(commonClass, statusClass)} key={status}>
                        {getStatusAsText(status)}
                    </div>
                }
            </>
        })
    }

    return <>
        {<Dialog modalType="alert" open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>Sharing Session Detailed View</DialogTitle>
                    <DialogContent>
                        {session && <div className={styles.dialogContent}>
                            <Field label={"Name"}>
                                <Input type="text" value={session.sessionName} disabled={true}/>
                            </Field>
                            <div className={styles.sessionStatuses}>
                                {getStatusList()}
                            </div>
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
                            {session.status === SharingSessionStatus.ENDED &&
                                <Field label={"Date Ended"}>
                                    <Input type="text" value={formatDateWithOrdinal(session.endDate)} disabled={true}/>
                                </Field>
                            }
                            {session.status === SharingSessionStatus.REJECTED &&
                                <Field label={"Date Rejected"}>
                                    <Input type="text" value={formatDateWithOrdinal(session.endDate)} disabled={true}/>
                                </Field>
                            }
                            {/*<Field label={"Total Documents"}>*/}
                            {/*    <Input type="text" value={session?.documents?.length || "0"} disabled={true}/>*/}
                            {/*</Field>*/}
                        </div>
                        }
                    </DialogContent>
                    <DialogActions>

                        <Button appearance="primary"
                                onClick={onDismiss}
                                shape={"circular"}>
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