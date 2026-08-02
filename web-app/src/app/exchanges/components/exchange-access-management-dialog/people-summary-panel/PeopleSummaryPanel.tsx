import {
    Badge,
    Button,
    Divider,
    Popover,
    PopoverSurface,
    PopoverTrigger,
    Text,
    Tooltip,
} from "@fluentui/react-components";
import {ExchangeDetailedDto, ExchangeStatus} from "../../../../models/models.tsx";
import {usePeopleSummaryPanelStyles} from "./PeopleSummaryPanelStyles.tsx";
import {useParticipantSummary} from "./useParticipantSummary.ts";
import {InfoIcon} from "../../../../components/IconBundles.tsx";

interface PeopleSummaryPanelProps
{
    exchange: ExchangeDetailedDto;
    onReplacePrimary: () => void;
}

const formatName = (firstName?: string, lastName?: string, fallback?: string): string =>
{
    const fullName = `${firstName || ""} ${lastName || ""}`.trim();
    return fullName || fallback || "Unknown user";
};

const participantLabel = (principalKind: string, status: string): string =>
{
    if (status === "PENDING_APPROVAL") return "Invitation pending";
    if (principalKind === "PRINCIPAL_GROUP") return "Group participant";
    return "Participant";
};

const PeopleSummaryPanel = ({exchange, onReplacePrimary}: PeopleSummaryPanelProps) =>
{
    const styles = usePeopleSummaryPanelStyles();
    const {participants, loading: loadingParticipants} = useParticipantSummary(exchange.id);
    const initiatorHasName = !!(exchange.initiator?.person?.firstName || exchange.initiator?.person?.lastName);
    const recipientHasName = !!(exchange.recipient?.person?.firstName || exchange.recipient?.person?.lastName);
    const recipientName = exchange.recipientGroupName
        ? `${exchange.recipientGroupName} (Group)`
        : recipientHasName
            ? formatName(exchange.recipient?.person?.firstName, exchange.recipient?.person?.lastName)
            : exchange.recipient?.email || "Unknown recipient";

    return (
        <section
            id={"access-mgmt-people-summary"}
            className={styles.root}
        >
            <Divider alignContent={"start"}>People in this Exchange</Divider>
            <div
                id={"access-mgmt-initiator-card"}
                className={styles.personCard}
            >
                <div className={styles.personDetails}>
                    <Text weight={"semibold"}>
                        {initiatorHasName
                            ? formatName(exchange.initiator?.person?.firstName, exchange.initiator?.person?.lastName)
                            : exchange.initiator?.email || "Unknown"}
                    </Text>
                    <Text
                        size={200}
                        className={styles.secondaryText}
                    >
                        {initiatorHasName ? exchange.initiator?.email || "No email" : "Requester"}
                    </Text>
                </div>
                <Badge
                    appearance={"outline"}
                    color={"informative"}
                >
                    Requester
                </Badge>
            </div>
            <div
                id={"access-mgmt-primary-recipient-card"}
                className={styles.personCard}
            >
                <div className={styles.personDetails}>
                    <Text weight={"semibold"}>{recipientName}</Text>
                    <Text
                        size={200}
                        className={styles.secondaryText}
                    >
                        {exchange.recipientGroupName
                            ? "Group recipient"
                            : recipientHasName ? exchange.recipient?.email || "No email" : "External recipient"}
                    </Text>
                </div>
                <Badge
                    appearance={"outline"}
                    color={"brand"}
                >
                    Primary recipient
                </Badge>
            </div>
            {exchange.status === ExchangeStatus.INITIATED && (
                <div className={styles.replacementActions}>
                    <div
                        id={"access-mgmt-replace-primary-action-row"}
                        className={styles.replacementActionRow}
                    >
                        <Button
                            id={"access-mgmt-replace-primary-btn"}
                            appearance={"secondary"}
                            shape={"circular"}
                            size={"small"}
                            onClick={onReplacePrimary}
                        >
                            Replace recipient
                        </Button>
                        <Popover positioning={"below-start"}>
                            <PopoverTrigger disableButtonEnhancement>
                                <Tooltip
                                    content={"Why replace a recipient"}
                                    relationship={"description"}
                                >
                                    <Button
                                        id={"access-mgmt-replace-primary-info-btn"}
                                        appearance={"subtle"}
                                        aria-label={"Why replace a recipient"}
                                        icon={<InfoIcon/>}
                                        shape={"circular"}
                                        size={"small"}
                                    />
                                </Tooltip>
                            </PopoverTrigger>
                            <PopoverSurface
                                id={"access-mgmt-replace-primary-info-popover"}
                                className={styles.replacementInfoPopover}
                            >
                                <Text size={200}>
                                    Recover an invitation whose trusted verification is no longer valid.
                                </Text>
                            </PopoverSurface>
                        </Popover>
                    </div>
                </div>
            )}
            {participants.map(participant => (
                <div
                    id={`access-mgmt-participant-${participant.shareId}`}
                    key={participant.shareId}
                    className={styles.personCard}
                >
                    <div className={styles.personDetails}>
                        <Text weight={"semibold"}>{participant.displayName || "Exchange participant"}</Text>
                        <Text
                            size={200}
                            className={styles.secondaryText}
                        >
                            {participant.principalKind === "PRINCIPAL_GROUP" ? "Trusted group" : "Trusted person"}
                        </Text>
                    </div>
                    <Tooltip
                        content={participant.grantedAt ? new Date(participant.grantedAt).toLocaleString() : "Added date unavailable"}
                        relationship={"label"}
                    >
                        <Badge
                            appearance={"outline"}
                            color={"subtle"}
                        >
                            {participantLabel(participant.principalKind, participant.status)}
                        </Badge>
                    </Tooltip>
                </div>
            ))}
            {loadingParticipants && <Text size={200}>Loading participants.</Text>}
            {!loadingParticipants && !participants.length && <Text size={200}>No additional participants added.</Text>}
        </section>
    );
};

export default PeopleSummaryPanel;
