import {Badge, Divider, Text, Tooltip} from "@fluentui/react-components";
import {ExchangeDetailedDto, ExchangeStatus} from "../../../../models/models.tsx";
import {usePeopleSummaryPanelStyles} from "./PeopleSummaryPanelStyles.tsx";
import {useParticipantSummary} from "./useParticipantSummary.ts";
import {
    initiatorName,
    initiatorSecondary,
    participantAddedLabel,
    participantKindLabel,
    participantLabel,
    recipientName,
    recipientSecondary,
} from "./peopleSummaryLabels.ts";
import PersonSummaryCard from "./person-summary-card/PersonSummaryCard.tsx";
import ReplacePrimaryRecipientAction from "./replace-primary-recipient-action/ReplacePrimaryRecipientAction.tsx";

interface PeopleSummaryPanelProps
{
    exchange: ExchangeDetailedDto;
    onReplacePrimary: () => void;
}

const PeopleSummaryPanel = ({exchange, onReplacePrimary}: PeopleSummaryPanelProps) =>
{
    const styles = usePeopleSummaryPanelStyles();
    const {participants, loading: loadingParticipants} = useParticipantSummary(exchange.id);

    return (
        <section
            id={"access-mgmt-people-summary"}
            className={styles.root}
        >
            <Divider id={"people-summary-panel-divider-1"}
                     alignContent={"start"}>People in this Exchange</Divider>
            <PersonSummaryCard
                cardId={"access-mgmt-initiator-card"}
                detailsId={"people-summary-panel-div-1"}
                nameId={"people-summary-panel-text-1"}
                secondaryId={"people-summary-panel-text-2"}
                name={initiatorName(exchange)}
                secondary={initiatorSecondary(exchange)}
                badge={(
                    <Badge
                        id={"people-summary-panel-badge-1"}
                        appearance={"outline"}
                        color={"informative"}
                    >
                        Requester
                    </Badge>
                )}
            />
            <PersonSummaryCard
                cardId={"access-mgmt-primary-recipient-card"}
                detailsId={"people-summary-panel-div-2"}
                nameId={"people-summary-panel-text-3"}
                secondaryId={"people-summary-panel-text-4"}
                name={recipientName(exchange)}
                secondary={recipientSecondary(exchange)}
                badge={(
                    <Badge
                        id={"people-summary-panel-badge-2"}
                        appearance={"outline"}
                        color={"brand"}
                    >
                        Primary recipient
                    </Badge>
                )}
            />
            {exchange.status === ExchangeStatus.INITIATED && (
                <ReplacePrimaryRecipientAction onReplacePrimary={onReplacePrimary}/>
            )}
            {participants.map(participant => (
                <PersonSummaryCard
                    key={participant.shareId}
                    cardId={`access-mgmt-participant-${participant.shareId}`}
                    detailsId={"people-summary-panel-div-4"}
                    nameId={"people-summary-panel-text-6"}
                    secondaryId={"people-summary-panel-text-7"}
                    name={participant.displayName || "Exchange participant"}
                    secondary={participantKindLabel(participant.principalKind)}
                    badge={(
                        <Tooltip
                            content={participantAddedLabel(participant.grantedAt)}
                            relationship={"label"}
                        >
                            <Badge
                                id={"people-summary-panel-badge-3"}
                                appearance={"outline"}
                                color={"subtle"}
                            >
                                {participantLabel(participant.principalKind, participant.status)}
                            </Badge>
                        </Tooltip>
                    )}
                />
            ))}
            {loadingParticipants && <Text id={"people-summary-panel-text-8"}
                                          size={200}>Loading participants.</Text>}
            {!loadingParticipants && !participants.length && <Text id={"people-summary-panel-text-9"}
                                                                   size={200}>No additional participants added.</Text>}
        </section>
    );
};

export default PeopleSummaryPanel;
