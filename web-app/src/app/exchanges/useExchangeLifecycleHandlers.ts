import {ExchangeDetailedDto} from '../models/models.tsx';
import {publishExchangeDelete, publishRecreateRejectedExchange} from '../observable/exchangeObservables.ts';
import {toRecreateRejectedExchangeDraft} from './recreateRejectedExchangeDraft.ts';
import {ExchangeWorkspaceState} from './useExchangeWorkspaceState.ts';

export const useExchangeLifecycleHandlers = (
    state: ExchangeWorkspaceState,
    changeSelectedExchangeId: (exchangeId: string | null) => void,
) =>
{
    const {exchangeList, activeListTab, setActiveListTab, selectedExchangeId, exchangeDetails, setExchangeDetails} = state;

    const onExchangeDeleted = (exchangeId: string) =>
    {
        if (exchangeDetails && exchangeDetails.id === exchangeId)
        {
            setExchangeDetails(undefined);
            changeSelectedExchangeId(null);
        }
    };

    const onExchangeEnded = (exchange: ExchangeDetailedDto) =>
    {
        setExchangeDetails(exchange);
        setActiveListTab('archive');
        changeSelectedExchangeId(exchange.id);
    };

    const onExchangeRescinded = (exchange: ExchangeDetailedDto) =>
    {
        setExchangeDetails(exchange);
        setActiveListTab('archive');
        changeSelectedExchangeId(exchange.id);
    };

    const onExchangeEdited = (exchange: ExchangeDetailedDto) =>
    {
        setExchangeDetails(exchange);
    };

    const onExchangeAccessManagementUpdated = (exchange: ExchangeDetailedDto) =>
    {
        setExchangeDetails(exchange);
    };

    const onExchangeAccepted = (exchange: ExchangeDetailedDto) =>
    {
        setExchangeDetails(exchange);
        setActiveListTab('active');
        changeSelectedExchangeId(exchange.id);
    };

    const onExchangeRejected = (exchangeId: string) =>
    {
        publishExchangeDelete(exchangeId);
        setExchangeDetails(null);
        changeSelectedExchangeId(null);
    };

    const onRecreateRejectedExchange = (exchange: ExchangeDetailedDto) =>
    {
        publishRecreateRejectedExchange(toRecreateRejectedExchangeDraft(exchange));
    };

    const onDecideLater = () =>
    {
        if (!selectedExchangeId || activeListTab !== 'inbox') return;

        const currentIndex = exchangeList.findIndex(s => s.id === selectedExchangeId);
        if (currentIndex < 0 || currentIndex >= exchangeList.length - 1) return;

        const nextIndex = currentIndex + 1;
        changeSelectedExchangeId(exchangeList[nextIndex].id);
    };

    return {
        onExchangeDeleted,
        onExchangeEnded,
        onExchangeRescinded,
        onExchangeEdited,
        onExchangeAccessManagementUpdated,
        onExchangeAccepted,
        onExchangeRejected,
        onRecreateRejectedExchange,
        onDecideLater,
    };
};
