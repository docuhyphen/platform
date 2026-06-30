import {ExchangeDetailedDto, ExchangeStatus} from "../../../models/models.tsx";
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
import {useExchangeDDetailedViewDialogStyles} from "./ExchangeDetailedViewDialogStyles.tsx";

interface ExchangeDetailedViewDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    exchange: ExchangeDetailedDto | null;
}

const ExchangeDetailedViewDialog: React.FC<ExchangeDetailedViewDialogProps> = (
    {
        isOpen,
        onDismiss,
        exchange
    }) =>
{
    const styles = useExchangeDDetailedViewDialogStyles();

    if (!exchange) return null;

    const normalizeStatus = (
        status?: ExchangeStatus | string,
        endDate?: string | null
    ): ExchangeStatus =>
    {
        const normalizedStatus = typeof status === "string" ? status.trim().toUpperCase() : status;

        switch (normalizedStatus)
        {
            case ExchangeStatus.ACCEPTED_STARTED:
            case "ACCEPTED":
            case "STARTED":
            case "IN_PROGRESS":
                return ExchangeStatus.ACCEPTED_STARTED;
            case ExchangeStatus.REJECTED:
                return ExchangeStatus.REJECTED;
            case ExchangeStatus.RESCINDED:
                return ExchangeStatus.RESCINDED;
            case ExchangeStatus.ENDED:
            case "COMPLETED":
            case "CLOSED":
                return ExchangeStatus.ENDED;
            case ExchangeStatus.INITIATED:
                return ExchangeStatus.INITIATED;
            default:
                return endDate ? ExchangeStatus.ENDED : ExchangeStatus.INITIATED;
        }
    };

    const currentStatus = normalizeStatus(exchange.status, exchange.endDate);

    const getStatusAsText = (status: ExchangeStatus) =>
    {
        switch (status)
        {
            case ExchangeStatus.ACCEPTED_STARTED:
                return "In Progress";
            case ExchangeStatus.REJECTED:
                return "Rejected";
            case ExchangeStatus.RESCINDED:
                return "Rescinded";
            case ExchangeStatus.INITIATED:
                return "Initiated";
            case ExchangeStatus.ENDED:
                return "Ended";
            default:
                return "Unknown";
        }
    };

    const formatParticipant = (participant: ExchangeDetailedDto['initiator']) =>
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

    const getStatusChipProps = (status?: ExchangeStatus) =>
    {
        switch (status)
        {
            case ExchangeStatus.ACCEPTED_STARTED:
                return {appearance: "filled" as const, color: "brand" as const};
            case ExchangeStatus.REJECTED:
                return {appearance: "filled" as const, color: "danger" as const};
            case ExchangeStatus.RESCINDED:
                return {appearance: "filled" as const, color: "warning" as const};
            case ExchangeStatus.ENDED:
                return {appearance: "filled" as const, color: "subtle" as const};
            case ExchangeStatus.INITIATED:
            default:
                return {appearance: "filled" as const, color: "success" as const};
        }
    };

    const getBooleanLabel = (value?: boolean) => value ? "Yes" : "No";
    const statusChipProps = getStatusChipProps(currentStatus);

    const uploadedDocuments = (exchange.documents || []).filter(document => !!document.uploadDate).length;
    const requestedDocuments = (exchange.documents || []).filter(document => !document.uploadDate).length;

    return <>
        {<Dialog modalType="alert" open={isOpen}>
            <DialogSurface className={styles.dialogSurface}>
                <DialogBody>
                    <DialogTitle>Exchange Detailed View</DialogTitle>
                    <DialogContent className={styles.dialogContent}>
                        <div className={styles.sectionCard}>
                            <div className={styles.titleRow}>
                                <Text size={500} weight="semibold" className={styles.exchangeTitleText}>
                                    {exchange.name || "Unnamed Exchange"}
                                </Text>
                                <Badge
                                    appearance={statusChipProps.appearance}
                                    color={statusChipProps.color}
                                    shape="rounded"
                                    className={styles.statusChip}>
                                    {getStatusAsText(currentStatus)}
                                </Badge>
                            </div>
                            <Text size={300}>{exchange.description || "No description provided."}</Text>
                            <Text size={300} italic>{exchange.initialShareMessage || "No initial share message."}</Text>
                        </div>

                        <div className={styles.sectionCard}>
                            <Text weight="semibold">Lifecycle</Text>
                            <div className={styles.keyValueGrid}>
                                <Text className={styles.keyLabel}>Date initiated</Text>
                                <Text>{formatDateWithOrdinal(exchange.createdDate)}</Text>

                                <Text className={styles.keyLabel}>Last activity</Text>
                                <Text>{formatDateWithOrdinal(exchange.lastActivity)}</Text>

                                <Text className={styles.keyLabel}>Date closed</Text>
                                <Text>{exchange.endDate ? formatDateWithOrdinal(exchange.endDate) : '–'}</Text>
                            </div>
                        </div>

                        <div className={styles.sectionCard}>
                            <Text weight="semibold">Participants</Text>
                            <div className={styles.keyValueGrid}>
                                <Text className={styles.keyLabel}>Initiator</Text>
                                <Text>{formatParticipant(exchange.initiator)}</Text>

                                <Text className={styles.keyLabel}>Recipient</Text>
                                <Text>{exchange.recipient ? formatParticipant(exchange.recipient) : exchange.recipientGroupName ? `${exchange.recipientGroupName} (Group)` : "Not provided"}</Text>
                            </div>
                            {(exchange.participants ?? []).length > 0 && (
                                <div className={styles.additionalParticipantsContainer}>
                                    <Text size={200} className={styles.additionalParticipantsLabel}>Additional participants</Text>
                                    {(exchange.participants ?? []).map((p) => (
                                        <div key={p.id} className={`${styles.keyValueGrid} ${styles.participantRow}`}>
                                            <Text className={styles.keyLabel}>{p.participantType === 'GROUP' ? 'Group' : 'User'}</Text>
                                            <Text>{p.organizationGroupName ?? ([p.appUserFirstName, p.appUserLastName].filter(Boolean).join(' ') || p.appUserEmail || 'Unknown')}</Text>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </div>

                        <div className={styles.sectionCard}>
                            <Text weight="semibold">Documents</Text>
                            <div className={styles.keyValueGrid}>
                                <Text className={styles.keyLabel}>Total</Text>
                                <Text>{exchange.documents?.length || 0}</Text>

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
                                <Text>{getBooleanLabel(exchange.requestRecipientSignIn)}</Text>

                                <Text className={styles.keyLabel}>Allow document addition</Text>
                                <Text>{getBooleanLabel(exchange.allowDocumentAddition)}</Text>

                                <Text className={styles.keyLabel}>Allow document upload</Text>
                                <Text>{getBooleanLabel(exchange.allowDocumentUpload)}</Text>

                                <Text className={styles.keyLabel}>Allow document update</Text>
                                <Text>{getBooleanLabel(exchange.allowDocumentUpdate)}</Text>

                                <Text className={styles.keyLabel}>Allow document download</Text>
                                <Text>{getBooleanLabel(exchange.allowDocumentDownload)}</Text>

                                <Text className={styles.keyLabel}>Allow document deletion</Text>
                                <Text>{getBooleanLabel(exchange.allowDocumentDeletion)}</Text>

                                <Text className={styles.keyLabel}>Watermark</Text>
                                <Text>{getBooleanLabel(exchange.watermark)}</Text>

                                <Text className={styles.keyLabel}>Require MFA</Text>
                                <Text>{getBooleanLabel(exchange.requireMfa)}</Text>

                                {exchange.maxViews != null && (
                                    <>
                                        <Text className={styles.keyLabel}>Max views</Text>
                                        <Text>{exchange.maxViews}</Text>
                                    </>
                                )}
                            </div>
                        </div>
                    </DialogContent>
                    <DialogActions>

                        <Button
                            id={"exchange-detailed-view-close-btn"}
                            appearance="primary"
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

export default ExchangeDetailedViewDialog;
