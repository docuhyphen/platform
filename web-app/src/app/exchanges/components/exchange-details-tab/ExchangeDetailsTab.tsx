import React from "react";
import {Badge, Body1, Caption1, Caption2, Divider} from "@fluentui/react-components";
import {ExchangeDetailedDto} from "../../../models/models.tsx";
import {formatDateTimeWithOrdinal} from "../../../helpers.ts";
import {useExchangeDetailsTabStyles} from "./ExchangeDetailsTabStyles.tsx";
import {getStatusAppearance, getStatusColor, STATUS_LABELS} from "./exchangeDetailsTabFormat.ts";
import ExchangeDetailRow from "./exchange-detail-row/ExchangeDetailRow.tsx";
import ExchangeDetailsTabParticipants from "./exchange-details-tab-participants/ExchangeDetailsTabParticipants.tsx";
import ExchangeDetailsTabSettings from "./exchange-details-tab-settings/ExchangeDetailsTabSettings.tsx";

interface ExchangeDetailsTabProps
{
    exchangeDetails: ExchangeDetailedDto;
}

const ExchangeDetailsTab: React.FC<ExchangeDetailsTabProps> = ({exchangeDetails}) =>
{
    const styles = useExchangeDetailsTabStyles();

    return (
        <div id={"exchange-details-tab-div-3"}
             className={styles.container}>

            <div id={"exchange-details-tab-div-4"}
                 className={styles.section}>
                <Caption2 id={"exchange-details-tab-caption2-1"}
                          className={styles.sectionTitle}>Status</Caption2>
                <Badge id={"exchange-details-tab-badge-2"}
                       className={styles.statusBadge}
                       appearance={getStatusAppearance()}
                       color={getStatusColor(exchangeDetails.status)}>
                    {STATUS_LABELS[exchangeDetails.status ?? ""] ?? exchangeDetails.status ?? "-"}
                </Badge>
            </div>

            <Divider id={"exchange-details-tab-divider-1"}/>

            <div id={"exchange-details-tab-div-5"}
                 className={styles.section}>
                <Caption2 id={"exchange-details-tab-caption2-2"}
                          className={styles.sectionTitle}>Description</Caption2>
                {exchangeDetails.description
                    ? <Body1 id={"exchange-details-tab-body1-1"}
                             className={styles.descriptionText}>{exchangeDetails.description}</Body1>
                    : <Caption1 id={"exchange-details-tab-caption1-4"}
                                className={styles.noDescription}>No description provided.</Caption1>}
                {exchangeDetails.initialShareMessage && (
                    <Body1 id={"exchange-details-tab-body1-2"}
                           className={styles.initialShareMessageText}>
                        {exchangeDetails.initialShareMessage}
                    </Body1>
                )}
            </div>

            <Divider id={"exchange-details-tab-divider-2"}/>

            <div id={"exchange-details-tab-div-6"}
                 className={styles.section}>
                <Caption2 id={"exchange-details-tab-caption2-3"}
                          className={styles.sectionTitle}>Dates</Caption2>
                <ExchangeDetailRow label="Started"
                                   value={exchangeDetails.createdDate ? formatDateTimeWithOrdinal(exchangeDetails.createdDate) : "-"}/>
                <ExchangeDetailRow label="Ended"
                                   value={exchangeDetails.endDate ? formatDateTimeWithOrdinal(exchangeDetails.endDate) : "-"}/>
                <ExchangeDetailRow label="Last activity"
                                   value={exchangeDetails.lastActivity ? formatDateTimeWithOrdinal(exchangeDetails.lastActivity) : "-"}/>
            </div>

            <Divider id={"exchange-details-tab-divider-3"}/>

            <div id={"exchange-details-tab-div-7"}
                 className={styles.section}>
                <Caption2 id={"exchange-details-tab-caption2-4"}
                          className={styles.sectionTitle}>Participants</Caption2>
                <ExchangeDetailsTabParticipants exchangeDetails={exchangeDetails}/>
            </div>

            <Divider id={"exchange-details-tab-divider-4"}/>

            <div id={"exchange-details-tab-div-11"}
                 className={styles.section}>
                <Caption2 id={"exchange-details-tab-caption2-5"}
                          className={styles.sectionTitle}>Exchange settings</Caption2>
                <ExchangeDetailsTabSettings exchangeDetails={exchangeDetails}/>
            </div>

        </div>
    );
};

export default ExchangeDetailsTab;
