import {useEffect} from 'react';
import {ExchangeStatus} from '../models/models.tsx';
import {ExchangeListTab} from './components/exchange-list/exchange-list-tabs/ExchangeListTabs.tsx';
import {ExchangeWorkspaceState} from './useExchangeWorkspaceState.ts';

export const useExchangeListTabSync = (state: ExchangeWorkspaceState, appUserId: string | undefined) =>
{
    const {
        selectedExchangeId,
        exchangeDetails,
        activeListTab,
        setActiveListTab,
        setInboxRole,
        deepLinkedExchangeIdRef,
    } = state;

    useEffect(() =>
    {
        if (!selectedExchangeId || !exchangeDetails) return;
        if (exchangeDetails.id !== selectedExchangeId) return;

        let targetTab: ExchangeListTab = 'inbox';
        if (exchangeDetails.status === ExchangeStatus.ACCEPTED_STARTED)
        {
            targetTab = 'active';
        }
        else if (
            exchangeDetails.status === ExchangeStatus.ENDED ||
            exchangeDetails.status === ExchangeStatus.REJECTED ||
            exchangeDetails.status === ExchangeStatus.RESCINDED
        )
        {
            targetTab = 'archive';
        }

        if (activeListTab !== targetTab)
        {
            setActiveListTab(targetTab);
        }

        if (targetTab === 'inbox')
        {
            const isInitiator = appUserId != null && exchangeDetails.initiator?.id === appUserId;
            setInboxRole(isInitiator ? 'outgoing' : 'incoming');
        }

        deepLinkedExchangeIdRef.current = null;
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [exchangeDetails, selectedExchangeId, activeListTab, appUserId]);
};
