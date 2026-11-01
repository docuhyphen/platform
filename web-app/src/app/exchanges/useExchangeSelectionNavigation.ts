import {useEffect} from 'react';
import {ExchangeWorkspaceState} from './useExchangeWorkspaceState.ts';
import {useExchangeRouteState} from './useExchangeRouteState';

export const useExchangeSelectionNavigation = (state: ExchangeWorkspaceState, hasDetailsLoadError: boolean) =>
{
    const exchangeRouteState = useExchangeRouteState();
    const {
        activeListTab,
        setActiveListTab,
        setInboxRole,
        selectedExchangeId,
        setSelectedExchangeId,
        isExchangeListLoading,
        detailsTransitionAnimationSuppressedForId,
        setDetailsTransitionAnimationSuppressedForId,
        setPaneNavigationDirection,
        setResolvedDocumentLayoutKey,
        setRouteSelectionVersion,
        selectedExchangeDocument,
        setSelectedExchangeDocument,
        deepLinkedExchangeIdRef,
        deepLinkedDocumentIdRef,
        deepLinkedDocumentExchangeIdRef,
    } = state;

    const changeSelectedExchangeId = (exchangeId: string | null) =>
    {
        if (exchangeId && isExchangeListLoading && !selectedExchangeId)
        {
            setDetailsTransitionAnimationSuppressedForId(exchangeId);
        }
        else if (!exchangeId || exchangeId !== detailsTransitionAnimationSuppressedForId)
        {
            setDetailsTransitionAnimationSuppressedForId(null);
        }

        setPaneNavigationDirection(exchangeId ? "forward" : "back");
        if (exchangeId !== selectedExchangeId)
        {
            setResolvedDocumentLayoutKey(null);
        }
        else if (hasDetailsLoadError)
        {
            setRouteSelectionVersion(version => version + 1);
        }
        setSelectedExchangeId(exchangeId);
    };

    useEffect(() =>
    {
        setActiveListTab(exchangeRouteState.listTab ?? 'active');

        if (exchangeRouteState.inboxRole)
        {
            setInboxRole(exchangeRouteState.inboxRole);
        }

        const deepLinkedId = exchangeRouteState.exchangeId;
        const deepLinkedDocumentId = exchangeRouteState.documentId;
        if (deepLinkedId)
        {
            deepLinkedExchangeIdRef.current = deepLinkedId;
            deepLinkedDocumentExchangeIdRef.current = deepLinkedId;
            setSelectedExchangeDocument(undefined);
            setSelectedExchangeId(deepLinkedId);
            setRouteSelectionVersion((version) => version + 1);
        }
        else
        {
            deepLinkedExchangeIdRef.current = null;
            deepLinkedDocumentExchangeIdRef.current = null;
            setSelectedExchangeId(null);
            setSelectedExchangeDocument(undefined);
        }

        deepLinkedDocumentIdRef.current = deepLinkedDocumentId;
        if (!deepLinkedDocumentId)
        {
            deepLinkedDocumentExchangeIdRef.current = null;
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [exchangeRouteState]);

    useEffect(() =>
    {
        if (typeof window === 'undefined') return;

        const params = new URLSearchParams(window.location.search);
        params.set('tab', activeListTab);

        params.delete('role');

        if (selectedExchangeId)
        {
            params.set('s', selectedExchangeId);
        }
        else
        {
            params.delete('s');
            params.delete('d');
        }

        if (selectedExchangeId && selectedExchangeDocument?.id)
        {
            params.set('d', selectedExchangeDocument.id);
        }
        else
        {
            params.delete('d');
        }

        const nextQuery = params.toString();
        const nextSearch = nextQuery ? `?${nextQuery}` : '';
        if (window.location.search !== nextSearch)
        {
            const nextUrl = `${window.location.pathname}${nextSearch}`;
            window.history.replaceState(null, '', nextUrl);
        }
    }, [activeListTab, selectedExchangeId, selectedExchangeDocument?.id]);

    return {changeSelectedExchangeId};
};
