import React from 'react';
import {Body1, Caption2} from "@fluentui/react-components";
import {formatDateTimeWithOrdinal} from "../../../helpers.ts";
import {ExchangeDetailedDto} from "../../../models/models.tsx";
import {useExchangeDetailsHeaderStyles} from "./ExchangeDetailsHeaderStyles.tsx";
import {ExchangeDetailsHeaderActionHandlers} from "./exchangeDetailsHeaderActionHandlers.ts";
import {useExchangeHeaderExpansion} from "./useExchangeHeaderExpansion.ts";
import ExchangeDetailsHeaderTitle from "./exchange-details-header-title/ExchangeDetailsHeaderTitle.tsx";
import ExchangeDetailsHeaderActions from "./exchange-details-header-actions/ExchangeDetailsHeaderActions.tsx";

interface ExchangeDetailsHeaderProps extends ExchangeDetailsHeaderActionHandlers
{
    exchangeDetails: ExchangeDetailedDto | null;
    onBackToList?: () => void;
}

const ExchangeDetailsHeader: React.FC<ExchangeDetailsHeaderProps> = (props) =>
{
    const {exchangeDetails, onBackToList, ...actionHandlers} = props;
    const styles = useExchangeDetailsHeaderStyles();
    const {isExpanded, toggleHeaderDetails, detailsToggleTooltip} = useExchangeHeaderExpansion();

    const getExchangeHeadContainerClass = () =>
    {
        return `${styles.container} ${styles[`containerStatus${exchangeDetails?.status || ''}` as keyof typeof styles]}`;
    };

    const getAnimatedSectionClass = (large = false) =>
    {
        const expandedClass = large
            ? styles.headerAnimatedSectionExpandedLarge
            : styles.headerAnimatedSectionExpanded;
        return `${styles.headerAnimatedSection} ${isExpanded ? expandedClass : ''}`;
    };

    return (
        <section id={"exchange-details-header-section-1"}
                 className={getExchangeHeadContainerClass()}>
            {exchangeDetails && (
                <div id={"exchange-details-header-div-1"}
                     className={styles.header}>
                    <div id={"exchange-details-header-div-2"}
                         className={getAnimatedSectionClass()}>
                        <div id={"exchange-details-header-div-3"}
                             className={styles.headerLine1}>
                            <div className={styles.headerLine1_2}
                                 id={"exchange-details-header-l1-1"}>
                                <Caption2 id={"exchange-details-header-caption2-1"}>
                                    Started {formatDateTimeWithOrdinal(exchangeDetails.createdDate)}
                                </Caption2>
                                {exchangeDetails.endDate && (
                                    <>
                                        <Caption2 id={"exchange-details-header-caption2-2"}>|</Caption2>
                                        <Caption2 id={"exchange-details-header-caption2-3"}>
                                            Ended {formatDateTimeWithOrdinal(exchangeDetails.createdDate)}
                                        </Caption2>
                                    </>
                                )}
                            </div>
                        </div>
                    </div>
                    <div id={"exchange-details-header-div-4"}
                         className={styles.headerLine2}>
                        <ExchangeDetailsHeaderTitle exchangeDetails={exchangeDetails}
                                                    isExpanded={isExpanded}
                                                    onBackToList={onBackToList}/>
                        <ExchangeDetailsHeaderActions exchangeDetails={exchangeDetails}
                                                      isExpanded={isExpanded}
                                                      detailsToggleTooltip={detailsToggleTooltip}
                                                      onToggleDetails={toggleHeaderDetails}
                                                      {...actionHandlers}/>
                    </div>
                    <div id={"exchange-details-header-div-8"}
                         className={getAnimatedSectionClass(true)}>
                        <div id={"exchange-details-header-div-9"}
                             className={styles.headerLine3}>
                            <Body1 id={"exchange-details-header-body1-1"}>
                                {exchangeDetails.description?.length > 0 ?
                                    exchangeDetails.description :
                                    "No description available"}
                            </Body1>
                        </div>
                    </div>
                </div>
            )}
        </section>
    );
};

export default ExchangeDetailsHeader;
