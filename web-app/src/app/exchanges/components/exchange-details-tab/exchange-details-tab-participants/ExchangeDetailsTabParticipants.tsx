import React from "react";
import {Caption1} from "@fluentui/react-components";
import {ExchangeDetailedDto} from "../../../../models/models.tsx";
import {formatInitiatorLabel, formatPersonName, formatRecipientLabel} from "../exchangeDetailsTabFormat.ts";
import ExchangeDetailRow from "../exchange-detail-row/ExchangeDetailRow.tsx";
import {useExchangeDetailsTabParticipantsStyles} from "./ExchangeDetailsTabParticipantsStyles.tsx";

interface ExchangeDetailsTabParticipantsProps
{
    exchangeDetails: ExchangeDetailedDto;
}

const ExchangeDetailsTabParticipants: React.FC<ExchangeDetailsTabParticipantsProps> = ({exchangeDetails}) =>
{
    const styles = useExchangeDetailsTabParticipantsStyles();

    return (
        <>
            <div id={"exchange-details-tab-div-8"}
                 className={styles.row}>
                <ExchangeDetailRow label="Initiated by"
                                   value={formatInitiatorLabel(exchangeDetails)}/>
                <ExchangeDetailRow label="Recipient"
                                   value={formatRecipientLabel(exchangeDetails)}/>
            </div>
            {exchangeDetails.participants && exchangeDetails.participants.length > 0 && (
                <div id={"exchange-details-tab-div-9"}
                     className={styles.additionalParticipantsContainer}>
                    <Caption1 id={"exchange-details-tab-caption1-5"}
                              className={styles.additionalParticipantsLabel}>Additional participants</Caption1>
                    {exchangeDetails.participants.map(p => (
                        <div id={`exchange-details-tab-div-10-${p.id}`}
                             className={styles.participantRow}
                             key={p.id}>
                            {p.participantType === "APP_USER"
                                ? <>
                                    <Caption1 id={"exchange-details-tab-caption1-6"}>
                                        {formatPersonName(p.appUserFirstName, p.appUserLastName)}
                                    </Caption1>
                                    <Caption1 id={"exchange-details-tab-caption1-7"}
                                              className={styles.participantEmail}>{p.appUserEmail ?? "-"}</Caption1>
                                </>
                                : <Caption1 id={"exchange-details-tab-caption1-8"}>{p.organizationGroupName ?? "-"}</Caption1>
                            }
                        </div>
                    ))}
                </div>
            )}
        </>
    );
};

export default ExchangeDetailsTabParticipants;
