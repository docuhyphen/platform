import React from "react";
import {Text} from "@fluentui/react-components";
import {ExchangeDetailedDto} from "../../../../models/models.tsx";
import ExchangeDetailSection from "../exchange-detail-section/ExchangeDetailSection.tsx";
import ExchangeDetailKeyValueGrid from "../exchange-detail-key-value-grid/ExchangeDetailKeyValueGrid.tsx";
import {
    formatAdditionalParticipant,
    formatExchangeParticipant,
    formatExchangeRecipient
} from "../exchangeDetailedViewFormatting.ts";
import {useExchangeDetailParticipantsSectionStyles} from "./ExchangeDetailParticipantsSectionStyles.tsx";

interface ExchangeDetailParticipantsSectionProps
{
    exchange: ExchangeDetailedDto;
}

const ExchangeDetailParticipantsSection: React.FC<ExchangeDetailParticipantsSectionProps> = ({exchange}) =>
{
    const styles = useExchangeDetailParticipantsSectionStyles();
    const additionalParticipants = exchange.participants ?? [];

    return (
        <ExchangeDetailSection id={"exchange-detailed-view-dialog-div-5"}
                               titleId={"exchange-detailed-view-dialog-text-11"}
                               title={"Participants"}>
            <ExchangeDetailKeyValueGrid id={"exchange-detailed-view-dialog-div-6"}
                                        rows={[
                                            {
                                                labelId: "exchange-detailed-view-dialog-text-12",
                                                valueId: "exchange-detailed-view-dialog-text-13",
                                                label: "Initiator",
                                                value: formatExchangeParticipant(exchange.initiator)
                                            },
                                            {
                                                labelId: "exchange-detailed-view-dialog-text-14",
                                                valueId: "exchange-detailed-view-dialog-text-15",
                                                label: "Recipient",
                                                value: formatExchangeRecipient(exchange)
                                            }
                                        ]}/>
            {additionalParticipants.length > 0 && (
                <div id={"exchange-detailed-view-dialog-div-7"}
                     className={styles.additionalParticipantsContainer}>
                    <Text id={"exchange-detailed-view-dialog-text-16"}
                          size={200}
                          className={styles.additionalParticipantsLabel}>Additional participants</Text>
                    {additionalParticipants.map((participant) => (
                        <ExchangeDetailKeyValueGrid key={participant.id}
                                                    id={`exchange-detailed-view-dialog-div-8-${participant.id}`}
                                                    className={styles.participantRow}
                                                    rows={[
                                                        {
                                                            labelId: "exchange-detailed-view-dialog-text-17",
                                                            valueId: "exchange-detailed-view-dialog-text-18",
                                                            label: participant.participantType === "GROUP" ? "Group" : "User",
                                                            value: formatAdditionalParticipant(participant)
                                                        }
                                                    ]}/>
                    ))}
                </div>
            )}
        </ExchangeDetailSection>
    );
};

export default ExchangeDetailParticipantsSection;
