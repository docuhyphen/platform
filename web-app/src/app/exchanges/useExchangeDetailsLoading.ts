import {useEffect} from 'react';
import {ExchangeDetailedDto} from '../models/models.tsx';
import {fetchSignedInUserAppUserExchange} from '../../services/exchangeApi.ts';
import {resolvePreviewDocumentSelection} from './exchangeDocumentSelection.ts';
import {isExchangeUnavailableError} from './exchangeUnavailableError.ts';
import {ExchangeWorkspaceState} from './useExchangeWorkspaceState.ts';

export interface ExchangeDetailsLoadingOptions
{
    sessionReady: boolean;
    autoPreviewDocuments: boolean;
    userPreferencesLoaded: boolean;
    clearUnavailableExchangeContext: (exchangeId?: string | null) => void;
}

export const useExchangeDetailsLoading = (state: ExchangeWorkspaceState, options: ExchangeDetailsLoadingOptions) =>
{
    const {sessionReady, autoPreviewDocuments, userPreferencesLoaded, clearUnavailableExchangeContext} = options;
    const {
        selectedExchangeId,
        routeSelectionVersion,
        exchangeDetails,
        setExchangeDetails,
        setFetchingDetails,
        setDetailsLoadErrorId,
        setFilteredDocuments,
        selectedExchangeDocument,
        setSelectedExchangeDocument,
        resolvedDocumentLayoutKey,
        setResolvedDocumentLayoutKey,
        deepLinkedDocumentIdRef,
        deepLinkedDocumentExchangeIdRef,
    } = state;

    useEffect(() =>
    {
        let cancelled = false;

        if (selectedExchangeId)
        {
            if (!sessionReady) return;

            const fetchDetails = async () =>
            {
                setFetchingDetails(true);
                setDetailsLoadErrorId(null);

                try
                {
                    const details = (await fetchSignedInUserAppUserExchange(selectedExchangeId)) as ExchangeDetailedDto;
                    if (cancelled) return;

                    setExchangeDetails(details);
                    setFilteredDocuments(details.documents || []);

                    const shouldApplyDeepLinkedDocument =
                        deepLinkedDocumentExchangeIdRef.current === details.id && !!deepLinkedDocumentIdRef.current;
                    const deepLinkedDocument = shouldApplyDeepLinkedDocument
                        ? (details.documents || []).find(document => document.id === deepLinkedDocumentIdRef.current)
                        : undefined;

                    const nextPreviewDocument = resolvePreviewDocumentSelection(
                        details.documents || [],
                        deepLinkedDocument?.id || selectedExchangeDocument?.id,
                        !!deepLinkedDocument
                    );
                    setSelectedExchangeDocument(nextPreviewDocument);

                    if (shouldApplyDeepLinkedDocument)
                    {
                        deepLinkedDocumentIdRef.current = null;
                        deepLinkedDocumentExchangeIdRef.current = null;
                    }
                }
                catch (error)
                {
                    if (cancelled) return;

                    if (isExchangeUnavailableError(error))
                    {
                        clearUnavailableExchangeContext(selectedExchangeId);
                        return;
                    }
                    console.error(error);
                    setDetailsLoadErrorId(selectedExchangeId);
                }
                finally
                {
                    if (!cancelled) setFetchingDetails(false);
                }
            };
            fetchDetails();
        }
        else
        {
            setExchangeDetails(null);
            setFetchingDetails(false);
        }
        return () =>
        {
            cancelled = true;
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [selectedExchangeId, routeSelectionVersion, sessionReady]);

    useEffect(() =>
    {
        if (!userPreferencesLoaded || !selectedExchangeId || exchangeDetails?.id !== selectedExchangeId)
        {
            return;
        }

        const layoutKey = `${selectedExchangeId}:${autoPreviewDocuments}`;
        if (resolvedDocumentLayoutKey === layoutKey)
        {
            return;
        }

        setSelectedExchangeDocument(currentSelection =>
            resolvePreviewDocumentSelection(
                exchangeDetails.documents || [],
                currentSelection?.id,
                autoPreviewDocuments
            )
        );
        setResolvedDocumentLayoutKey(layoutKey);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [
        autoPreviewDocuments,
        exchangeDetails?.documents,
        exchangeDetails?.id,
        resolvedDocumentLayoutKey,
        selectedExchangeId,
        userPreferencesLoaded,
    ]);
};
