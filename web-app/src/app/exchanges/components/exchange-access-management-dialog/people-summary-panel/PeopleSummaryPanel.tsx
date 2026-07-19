import {Badge, Button, Divider, Text, Tooltip} from "@fluentui/react-components";
import {ExchangeDetailedDto, ExchangeStatus} from "../../../../models/models.tsx";
import {ExchangeParticipantView} from "../exchangeAccessManagementTypes.ts";
import {usePeopleSummaryPanelStyles} from "./PeopleSummaryPanelStyles.tsx";

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

const participantLabel = (participantType?: string): string =>
{
    if (participantType === "GROUP") return "Group participant";
    if (participantType === "APP_USER") return "User participant";
    return "Participant";
};

const PeopleSummaryPanel = ({exchange, onReplacePrimary}: PeopleSummaryPanelProps) =>
{
    const styles = usePeopleSummaryPanelStyles();
    const participants = (exchange.participants ?? []) as ExchangeParticipantView[];
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
                    <Button
                        id={"access-mgmt-replace-primary-btn"}
                        appearance={"secondary"}
                        shape={"circular"}
                        size={"small"}
                        onClick={onReplacePrimary}
                    >
                        Replace recipient
                    </Button>
                    <Text
                        size={200}
                        className={styles.secondaryText}
                    >
                        Recover an invitation whose trusted verification is no longer valid.
                    </Text>
                </div>
            )}
            {participants.map(participant => (
                <div
                    id={`access-mgmt-participant-${participant.id}`}
                    key={participant.id}
                    className={styles.personCard}
                >
                    <div className={styles.personDetails}>
                        <Text weight={"semibold"}>
                            {participant.organizationGroupName
                                || formatName(participant.appUserFirstName, participant.appUserLastName, participant.appUserEmail)}
                        </Text>
                        <Text
                            size={200}
                            className={styles.secondaryText}
                        >
                            {participant.appUserEmail || participant.organizationGroupName || "Exchange participant"}
                        </Text>
                    </div>
                    <Tooltip
                        content={participant.addedDate ? new Date(participant.addedDate).toLocaleString() : "Added date unavailable"}
                        relationship={"label"}
                    >
                        <Badge
                            appearance={"outline"}
                            color={"subtle"}
                        >
                            {participantLabel(participant.participantType)}
                        </Badge>
                    </Tooltip>
                </div>
            ))}
            {!participants.length && <Text size={200}>No additional participants added.</Text>}
        </section>
    );
};

export default PeopleSummaryPanel;
