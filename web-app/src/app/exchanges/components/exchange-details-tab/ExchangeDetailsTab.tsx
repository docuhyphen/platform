import React from "react";
import {
    Badge,
    Body1,
    Caption1,
    Caption2,
    Divider,
} from "@fluentui/react-components";
import {ExchangeDetailedDto, ExchangeStatus} from "../../../models/models.tsx";
import {formatDateTimeWithOrdinal} from "../../../helpers.ts";
import {makeStyles, tokens} from "@fluentui/react-components";

const useStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: "1rem",
        padding: "8px 0",
        overflowY: "auto",
        flex: 1,
        minHeight: 0,
    },
    section: {
        display: "flex",
        flexDirection: "column",
        gap: "6px",
    },
    sectionTitle: {
        color: tokens.colorNeutralForeground3,
        textTransform: "uppercase",
        letterSpacing: "0.04em",
    },
    row: {
        display: "flex",
        flexDirection: "column",
        gap: "2px",
    },
    labelValueRow: {
        display: "flex",
        flexDirection: "row",
        gap: "8px",
        alignItems: "flex-start",
        flexWrap: "wrap",
    },
    label: {
        color: tokens.colorNeutralForeground3,
        minWidth: "120px",
        flexShrink: 0,
    },
    settingsGrid: {
        display: "flex",
        flexDirection: "column",
        gridTemplateColumns: "1fr 1fr",
        gap: "8px",
    },
    settingItem: {
        display: "flex",
        flexDirection: "row",
        gap: "6px",
        alignItems: "center",
    },
    descriptionText: {
        whiteSpace: "pre-wrap",
        color: tokens.colorNeutralForeground1,
    },
    noDescription: {
        color: tokens.colorNeutralForeground4,
        fontStyle: "italic",
    },
    participantRow: {
        display: "flex",
        flexDirection: "column",
        gap: "2px",
        padding: "6px 0",
        borderBottom: `1px solid ${tokens.colorNeutralStroke2}`,
        ":last-child": {
            borderBottom: "none",
        },
    },
    participantEmail: {
        color: tokens.colorNeutralForeground2,
    },
    statusBadge: {
        alignSelf: "flex-start",
    },
});

const STATUS_LABELS: Record<string, string> = {
    INITIATED: "Pending",
    ACCEPTED_STARTED: "Active",
    REJECTED: "Rejected",
    ENDED: "Ended",
};

const getStatusAppearance = (): "filled" | "tint" | "ghost" | "outline" => "tint";

const getStatusColor = (status?: string): "brand" | "danger" | "important" | "informative" | "severe" | "subtle" | "success" | "warning" =>
{
    switch (status)
    {
        case ExchangeStatus.ACCEPTED_STARTED:
            return "success";
        case ExchangeStatus.ENDED:
            return "subtle";
        case ExchangeStatus.REJECTED:
            return "danger";
        default:
            return "informative";
    }
};

interface ExchangeDetailsTabProps
{
    exchangeDetails: ExchangeDetailedDto;
}

const ExchangeDetailsTab: React.FC<ExchangeDetailsTabProps> = ({exchangeDetails}) =>
{
    const styles = useStyles();

    const formatPersonName = (firstName?: string, lastName?: string): string =>
    {
        const parts = [firstName, lastName].filter(Boolean);
        return parts.length > 0 ? parts.join(" ") : "-";
    };

    const renderDetailRow = (label: string, value: React.ReactNode) => (
        <div className={styles.labelValueRow} key={label}>
            <Caption1 className={styles.label}>{label}</Caption1>
            <Caption1>{value}</Caption1>
        </div>
    );

    const renderBooleanSetting = (label: string, value?: boolean) => (
        <div className={styles.settingItem} key={label}>
            <Badge
                appearance="tint"
                color={value ? "success" : "subtle"}
                size="small">
                {value ? "Yes" : "No"}
            </Badge>
            <Caption1>{label}</Caption1>
        </div>
    );

    return (
        <div className={styles.container}>

            {/* Status */}
            <div className={styles.section}>
                <Caption2 className={styles.sectionTitle}>Status</Caption2>
                <Badge
                    className={styles.statusBadge}
                    appearance={getStatusAppearance()}
                    color={getStatusColor(exchangeDetails.status)}>
                    {STATUS_LABELS[exchangeDetails.status ?? ""] ?? exchangeDetails.status ?? "-"}
                </Badge>
            </div>

            <Divider/>

            {/* Description */}
            <div className={styles.section}>
                <Caption2 className={styles.sectionTitle}>Description</Caption2>
                {exchangeDetails.description
                    ? <Body1 className={styles.descriptionText}>{exchangeDetails.description}</Body1>
                    : <Caption1 className={styles.noDescription}>No description provided.</Caption1>}
                {exchangeDetails.initialShareMessage && (
                    <Body1 className={styles.descriptionText} style={{marginTop: "6px", fontStyle: "italic"}}>
                        {exchangeDetails.initialShareMessage}
                    </Body1>
                )}
            </div>

            <Divider/>

            {/* Dates */}
            <div className={styles.section}>
                <Caption2 className={styles.sectionTitle}>Dates</Caption2>
                {renderDetailRow("Started", exchangeDetails.createdDate ? formatDateTimeWithOrdinal(exchangeDetails.createdDate) : "-")}
                {renderDetailRow("Ended", exchangeDetails.endDate ? formatDateTimeWithOrdinal(exchangeDetails.endDate) : "-")}
                {renderDetailRow("Last activity", exchangeDetails.lastActivity ? formatDateTimeWithOrdinal(exchangeDetails.lastActivity) : "-")}
            </div>

            <Divider/>

            {/* Participants */}
            <div className={styles.section}>
                <Caption2 className={styles.sectionTitle}>Participants</Caption2>
                <div className={styles.row}>
                    {renderDetailRow("Initiated by",
                        exchangeDetails.initiator
                            ? `${formatPersonName(exchangeDetails.initiator.person?.firstName, exchangeDetails.initiator.person?.lastName)} (${exchangeDetails.initiator.email})`
                            : "-"
                    )}
                    {renderDetailRow("Recipient",
                        exchangeDetails.recipient
                            ? (() => {
                                const name = formatPersonName(exchangeDetails.recipient!.person?.firstName, exchangeDetails.recipient!.person?.lastName);
                                return name !== '-'
                                    ? `${name} (${exchangeDetails.recipient!.email})`
                                    : (exchangeDetails.recipient!.email || '-');
                              })()
                            : exchangeDetails.recipientGroupName
                                ? `${exchangeDetails.recipientGroupName} (Group)`
                                : "-"
                    )}
                </div>
                {exchangeDetails.participants && exchangeDetails.participants.length > 0 && (
                    <div style={{marginTop: "4px"}}>
                        <Caption1 style={{color: tokens.colorNeutralForeground3}}>Additional participants</Caption1>
                        {exchangeDetails.participants.map(p => (
                            <div className={styles.participantRow} key={p.id}>
                                {p.participantType === "APP_USER"
                                    ? <>
                                        <Caption1>
                                            {formatPersonName(p.appUserFirstName, p.appUserLastName)}
                                        </Caption1>
                                        <Caption1 className={styles.participantEmail}>{p.appUserEmail ?? "-"}</Caption1>
                                    </>
                                    : <Caption1>{p.organizationGroupName ?? "-"}</Caption1>
                                }
                            </div>
                        ))}
                    </div>
                )}
            </div>

            <Divider/>

            {/* Settings */}
            <div className={styles.section}>
                <Caption2 className={styles.sectionTitle}>Exchange settings</Caption2>
                <div className={styles.settingsGrid}>
                    {renderBooleanSetting("Allow document addition", exchangeDetails.allowDocumentAddition)}
                    {renderBooleanSetting("Allow document deletion", exchangeDetails.allowDocumentDeletion)}
                    {renderBooleanSetting("Allow download", exchangeDetails.allowDocumentDownload)}
                    {renderBooleanSetting("Allow document update", exchangeDetails.allowDocumentUpdate)}
                    {renderBooleanSetting("Allow document upload", exchangeDetails.allowDocumentUpload)}
                    {renderBooleanSetting("Require sign-in", exchangeDetails.requestRecipientSignIn)}
                    {renderBooleanSetting("Watermark", exchangeDetails.watermark)}
                    {renderBooleanSetting("Require MFA", exchangeDetails.requireMfa)}
                </div>
                {exchangeDetails.maxViews != null && (
                    <div style={{marginTop: "8px"}}>
                        {renderDetailRow("Max views", String(exchangeDetails.maxViews))}
                    </div>
                )}
            </div>

        </div>
    );
};

export default ExchangeDetailsTab;




