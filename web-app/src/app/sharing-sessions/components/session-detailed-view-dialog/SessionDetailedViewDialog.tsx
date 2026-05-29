import {SharingSessionDetailedDto, SharingSessionStatus} from "../../../models/models.tsx";
import React from "react";
import {
    Badge,
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    Text
} from "@fluentui/react-components";
import {formatDateWithOrdinal} from "../../../helpers.ts";
import {useSessionDDetailedViewDialogStyles} from "./SessionDetailedViewDialogStyles.tsx";

interface SessionDetailedViewDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    session: SharingSessionDetailedDto | null;
}

const SessionDetailedViewDialog: React.FC<SessionDetailedViewDialogProps> = (
    {
        isOpen,
        onDismiss,
        session
    }) =>
{
    const styles = useSessionDDetailedViewDialogStyles();

    if (!session) return null;

    const normalizeStatus = (
        status?: SharingSessionStatus | string,
        endDate?: string | null
    ): SharingSessionStatus =>
    {
        const normalizedStatus = typeof status === "string" ? status.trim().toUpperCase() : status;

        switch (normalizedStatus)
        {
            case SharingSessionStatus.ACCEPTED_STARTED:
            case "ACCEPTED":
            case "STARTED":
            case "IN_PROGRESS":
                return SharingSessionStatus.ACCEPTED_STARTED;
            case SharingSessionStatus.REJECTED:
                return SharingSessionStatus.REJECTED;
            case SharingSessionStatus.ENDED:
            case "COMPLETED":
            case "CLOSED":
                return SharingSessionStatus.ENDED;
            case SharingSessionStatus.INITIATED:
                return SharingSessionStatus.INITIATED;
            default:
                return endDate ? SharingSessionStatus.ENDED : SharingSessionStatus.INITIATED;
        }
    };

    const currentStatus = normalizeStatus(session.status, session.endDate);

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
    };

    const formatParticipant = (participant: SharingSessionDetailedDto['initiator']) =>
    {
        const name = [participant?.person?.firstName, participant?.person?.lastName]
            .filter(Boolean)
            .join(' ')
            .trim();
        const email = participant?.email;

        if (name && email) return `${name} (${email})`;
        if (name) return name;
        if (email) return email;
        return "Not provided";
    };

    const getStatusChipProps = (status?: SharingSessionStatus) =>
    {
        switch (status)
        {
            case SharingSessionStatus.ACCEPTED_STARTED:
                return {appearance: "filled" as const, color: "brand" as const};
            case SharingSessionStatus.REJECTED:
                return {appearance: "filled" as const, color: "danger" as const};
            case SharingSessionStatus.ENDED:
                return {appearance: "filled" as const, color: "subtle" as const};
            case SharingSessionStatus.INITIATED:
            default:
                return {appearance: "filled" as const, color: "success" as const};
        }
    };

    const getBooleanLabel = (value?: boolean) => value ? "Yes" : "No";
    const statusChipProps = getStatusChipProps(currentStatus);

    const uploadedDocuments = (session.documents || []).filter(document => !!document.uploadDate).length;
    const requestedDocuments = (session.documents || []).filter(document => !document.uploadDate).length;

    return <>
        {<Dialog modalType="alert" open={isOpen}>
            <DialogSurface className={styles.dialogSurface}>
                <DialogBody>
                    <DialogTitle>Sharing Session Detailed View</DialogTitle>
                    <DialogContent className={styles.dialogContent}>
                        <div className={styles.sectionCard}>
                            <div className={styles.titleRow}>
                                <Text size={500} weight="semibold" className={styles.sessionTitleText}>
                                    {session.sessionName || "Unnamed Session"}
                                </Text>
                                <Badge
                                    appearance={statusChipProps.appearance}
                                    color={statusChipProps.color}
                                    shape="rounded"
                                    className={styles.statusChip}>
                                    {getStatusAsText(currentStatus)}
                                </Badge>
                            </div>
                            <Text size={300}>{session.description || "No description provided."}</Text>
                            <Text size={300} italic>{session.initialShareMessage || "No initial share message."}</Text>
                        </div>

                        <div className={styles.sectionCard}>
                            <Text weight="semibold">Lifecycle</Text>
                            <div className={styles.keyValueGrid}>
                                <Text className={styles.keyLabel}>Date initiated</Text>
                                <Text>{formatDateWithOrdinal(session.createdDate)}</Text>

                                <Text className={styles.keyLabel}>Last activity</Text>
                                <Text>{formatDateWithOrdinal(session.lastActivity)}</Text>

                                {session.endDate && (
                                    <>
                                        <Text className={styles.keyLabel}>Date closed</Text>
                                        <Text>{formatDateWithOrdinal(session.endDate)}</Text>
                                    </>
                                )}
                            </div>
                        </div>

                        <div className={styles.sectionCard}>
                            <Text weight="semibold">Participants</Text>
                            <div className={styles.keyValueGrid}>
                                <Text className={styles.keyLabel}>Initiator</Text>
                                <Text>{formatParticipant(session.initiator)}</Text>

                                <Text className={styles.keyLabel}>Recipient</Text>
                                <Text>{formatParticipant(session.recipient)}</Text>
                            </div>
                        </div>

                        <div className={styles.sectionCard}>
                            <Text weight="semibold">Documents</Text>
                            <div className={styles.keyValueGrid}>
                                <Text className={styles.keyLabel}>Total</Text>
                                <Text>{session.documents?.length || 0}</Text>

                                <Text className={styles.keyLabel}>Uploaded</Text>
                                <Text>{uploadedDocuments}</Text>

                                <Text className={styles.keyLabel}>Requested</Text>
                                <Text>{requestedDocuments}</Text>
                            </div>
                        </div>

                        <div className={styles.sectionCard}>
                            <Text weight="semibold">Sharing options</Text>
                            <div className={styles.keyValueGrid}>
                                <Text className={styles.keyLabel}>Require recipient sign-in</Text>
                                <Text>{getBooleanLabel(session.requestRecipientSignIn)}</Text>

                                <Text className={styles.keyLabel}>Allow document addition</Text>
                                <Text>{getBooleanLabel(session.allowDocumentAddition)}</Text>

                                <Text className={styles.keyLabel}>Allow document upload</Text>
                                <Text>{getBooleanLabel(session.allowDocumentUpload)}</Text>

                                <Text className={styles.keyLabel}>Allow document update</Text>
                                <Text>{getBooleanLabel(session.allowDocumentUpdate)}</Text>

                                <Text className={styles.keyLabel}>Allow document download</Text>
                                <Text>{getBooleanLabel(session.allowDocumentDownload)}</Text>

                                <Text className={styles.keyLabel}>Allow document deletion</Text>
                                <Text>{getBooleanLabel(session.allowDocumentDeletion)}</Text>
                            </div>
                        </div>
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