import React from "react";
import {Badge, Text} from "@fluentui/react-components";
import {ExchangeDetailedDto, ExchangeStatus} from "../../../../models/models.tsx";
import ExchangeDetailSection from "../exchange-detail-section/ExchangeDetailSection.tsx";
import {getExchangeStatusChipProps, getExchangeStatusText} from "../exchangeDetailedViewFormatting.ts";
import {useExchangeDetailOverviewSectionStyles} from "./ExchangeDetailOverviewSectionStyles.tsx";

interface ExchangeDetailOverviewSectionProps
{
    exchange: ExchangeDetailedDto;
    currentStatus: ExchangeStatus;
}

const ExchangeDetailOverviewSection: React.FC<ExchangeDetailOverviewSectionProps> = (
    {
        exchange,
        currentStatus
    }) =>
{
    const styles = useExchangeDetailOverviewSectionStyles();
    const statusChipProps = getExchangeStatusChipProps(currentStatus);

    return (
        <ExchangeDetailSection id={"exchange-detailed-view-dialog-div-1"}>
            <div id={"exchange-detailed-view-dialog-div-2"}
                 className={styles.titleRow}>
                <Text id={"exchange-detailed-view-dialog-text-1"}
                      size={500}
                      weight="semibold"
                      className={styles.exchangeTitleText}>
                    {exchange.name || "Unnamed Exchange"}
                </Text>
                <Badge id={"exchange-detailed-view-dialog-badge-1"}
                       appearance={statusChipProps.appearance}
                       color={statusChipProps.color}
                       shape="rounded"
                       className={styles.statusChip}>
                    {getExchangeStatusText(currentStatus)}
                </Badge>
            </div>
            <Text id={"exchange-detailed-view-dialog-text-2"}
                  size={300}>{exchange.description || "No description provided."}</Text>
            <Text id={"exchange-detailed-view-dialog-text-3"}
                  size={300}
                  italic>{exchange.initialShareMessage || "No initial share message."}</Text>
        </ExchangeDetailSection>
    );
};

export default ExchangeDetailOverviewSection;
