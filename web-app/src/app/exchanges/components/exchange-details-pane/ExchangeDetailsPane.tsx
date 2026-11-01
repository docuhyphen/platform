import React from 'react';
import {mergeClasses} from '@fluentui/react-components';
import {ExchangeStatus} from '../../../models/models.tsx';
import ExchangeAcceptanceDialog from '../exchange-acceptance-dialog/ExchangeAcceptanceDialog.tsx';
import ExchangeDetailsTabContent from '../exchange-details-tab-content/ExchangeDetailsTabContent.tsx';
import ExchangeDetailsLoadingOverlay from '../exchange-details-loading-overlay/ExchangeDetailsLoadingOverlay.tsx';
import {ExchangesWorkspace} from '../../useExchangesWorkspace.ts';
import ExchangeDetailsPaneHeaders from './ExchangeDetailsPaneHeaders.tsx';
import {useExchangeDetailsPaneStyles} from './ExchangeDetailsPaneStyles.tsx';

interface ExchangeDetailsPaneProps
{
    workspace: ExchangesWorkspace;
}

const ExchangeDetailsPane: React.FC<ExchangeDetailsPaneProps> = ({workspace}) =>
{
    const styles = useExchangeDetailsPaneStyles();
    const {
        isMobile,
        selectedExchangeId,
        exchangeDetails,
        fetchingDetails,
        appUserId,
        exchangeList,
        activeListTab,
        tabCounts,
        setActiveListTab,
        onExchangeAccepted,
        onExchangeRejected,
        onDecideLater,
    } = workspace;
    const detailsPaneClassName = mergeClasses(
        styles.detailsContainer,
        isMobile && !selectedExchangeId ? styles.detailsPaneHidden : undefined,
    );
    const showAcceptance =
        exchangeDetails != null &&
        exchangeDetails.status === ExchangeStatus.INITIATED &&
        appUserId !== exchangeDetails.initiator?.id;
    const currentInboxIndex = selectedExchangeId
        ? exchangeList.findIndex(exchange => exchange.id === selectedExchangeId)
        : -1;
    const canDecideLater =
        activeListTab === 'inbox' &&
        currentInboxIndex > -1 &&
        currentInboxIndex < exchangeList.length - 1;
    const isSingleExchange = exchangeList.length === 1;
    const acceptanceProps = {
        exchange: exchangeDetails,
        canDecideLater,
        activeCount: tabCounts.active,
        archiveCount: tabCounts.archive,
        onAccepted: onExchangeAccepted,
        onRejected: onExchangeRejected,
        onOpenActive: () => setActiveListTab('active'),
        onOpenArchive: () => setActiveListTab('archive'),
    };

    return (
        <div id={"exchanges-div"}
             className={detailsPaneClassName}>

            <div
                id={"exchanges-details-content"}
                className={`${styles.detailsContent} ${fetchingDetails ? styles.detailsContentLoading : ''}`}>

                {showAcceptance && isSingleExchange && (
                    <ExchangeAcceptanceDialog
                        {...acceptanceProps}
                        isSingleExchange={true}
                        onDismiss={() => {
                        }}
                    />
                )}

                <ExchangeDetailsPaneHeaders workspace={workspace}/>

                <ExchangeDetailsTabContent workspace={workspace}/>

                {showAcceptance && !isSingleExchange && (
                    <ExchangeAcceptanceDialog
                        {...acceptanceProps}
                        isSingleExchange={false}
                        onDismiss={onDecideLater}
                    />
                )}
            </div>

            {fetchingDetails && <ExchangeDetailsLoadingOverlay/>}
        </div>
    );
};

export default ExchangeDetailsPane;
