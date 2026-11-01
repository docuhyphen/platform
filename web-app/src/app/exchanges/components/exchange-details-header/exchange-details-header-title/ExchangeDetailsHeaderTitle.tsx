import React from 'react';
import {Button, Text, Tooltip} from "@fluentui/react-components";
import {ArrowLeftRegular} from "@fluentui/react-icons";
import {ExchangeDetailedDto} from "../../../../models/models.tsx";
import {useExchangeDetailsHeaderTitleStyles} from "./ExchangeDetailsHeaderTitleStyles.tsx";

interface ExchangeDetailsHeaderTitleProps
{
    exchangeDetails: ExchangeDetailedDto;
    isExpanded: boolean;
    onBackToList?: () => void;
}

const ExchangeDetailsHeaderTitle: React.FC<ExchangeDetailsHeaderTitleProps> = (
    {
        exchangeDetails,
        isExpanded,
        onBackToList,
    }) =>
{
    const styles = useExchangeDetailsHeaderTitleStyles();

    return (
        <div id={"exchange-details-header-div-5"}
             className={styles.headerTitleGroup}>
            {onBackToList && (
                <Tooltip content="Back to Exchanges"
                         relationship="description">
                    <Button id="exchange-details-header-back-to-list"
                            size="small"
                            appearance="subtle"
                            shape="circular"
                            aria-label="Back to Exchanges"
                            icon={<ArrowLeftRegular/>}
                            onClick={onBackToList}
                            className={styles.backToListButton}
                    />
                </Tooltip>
            )}
            <div id={"exchange-details-header-div-6"}
                 className={styles.headerTitleText}>
                <Text id={"exchange-details-header-text-1"}
                      size={isExpanded ? 500 : 600}
                      truncate
                      wrap={false}
                      title={exchangeDetails.name}>
                    {exchangeDetails.name}
                </Text>
            </div>
        </div>
    );
};

export default ExchangeDetailsHeaderTitle;
