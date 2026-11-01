import {useEffect} from 'react';
import {ExchangeDetailedDto} from '../models/models.tsx';
import {fetchSignedInUserAppUserExchange} from '../../services/exchangeApi.ts';
import {realtimeService} from '../../services/NotificationService';
import {publishExchangeUpdate} from '../observable/exchangeObservables.ts';
import {resolvePreviewDocumentSelection} from './exchangeDocumentSelection.ts';
import {isExchangeUnavailableError} from './exchangeUnavailableError.ts';
import {ExchangeWorkspaceState} from './useExchangeWorkspaceState.ts';

export const useExchangeRealtimeRefresh = (
    state: ExchangeWorkspaceState,
    autoPreviewDocuments: boolean,
    clearUnavailableExchangeContext: (exchangeId?: string | null) => void,
) =>
{
    const {selectedExchangeId, setExchangeDetails, setFilteredDocuments, setSelectedExchangeDocument} = state;

    useEffect(() =>
    {
        if (!selectedExchangeId) return;
        let cancelled = false;

        const refetchDetails = async () =>
        {
            try
            {
                const details = (await fetchSignedInUserAppUserExchange(selectedExchangeId)) as ExchangeDetailedDto;
                if (cancelled) return;

                setExchangeDetails(details);
                setFilteredDocuments(details.documents || []);
                setSelectedExchangeDocument((currentSelection) =>
                    resolvePreviewDocumentSelection(
                        details.documents || [],
                        currentSelection?.id,
                        autoPreviewDocuments
                    )
                );
            }
            catch (error)
            {
                if (cancelled) return;

                if (isExchangeUnavailableError(error))
                {
                    clearUnavailableExchangeContext(selectedExchangeId);
                    return;
                }
                console.error("Failed to refresh Exchange after realtime event:", error);
            }
        };

        const handler = (msg: { exchangeId?: string }) =>
        {
            if (msg.exchangeId === selectedExchangeId)
            {
                refetchDetails();
            }
        };

        const offAdded = realtimeService.on('EXCHANGE_DOCUMENT_ADDED', handler);
        const offRemoved = realtimeService.on('EXCHANGE_DOCUMENT_REMOVED', handler);
        const offUpdated = realtimeService.on('EXCHANGE_DOCUMENT_UPDATED', handler);
        realtimeService.subscribeToExchange(selectedExchangeId);

        return () =>
        {
            cancelled = true;
            realtimeService.unsubscribeFromExchange(selectedExchangeId);
            offAdded();
            offRemoved();
            offUpdated();
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [selectedExchangeId]);

    useEffect(() =>
    {
        let cancelled = false;
        const offStatus = realtimeService.on('EXCHANGE_STATUS_CHANGED', async (msg) =>
        {
            if (!msg.exchangeId) return;

            try
            {
                const updatedExchange = (await fetchSignedInUserAppUserExchange(msg.exchangeId)) as ExchangeDetailedDto;
                publishExchangeUpdate(updatedExchange);
                if (cancelled) return;

                if (msg.exchangeId === selectedExchangeId)
                {
                    setExchangeDetails(updatedExchange);
                    setFilteredDocuments(updatedExchange.documents || []);
                }
            }
            catch (error)
            {
                if (cancelled) return;

                if (msg.exchangeId === selectedExchangeId && isExchangeUnavailableError(error))
                {
                    clearUnavailableExchangeContext(selectedExchangeId);
                    return;
                }
                console.error("Failed to process realtime status change:", error);
            }
        });

        return () =>
        {
            cancelled = true;
            offStatus();
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [selectedExchangeId]);
};
