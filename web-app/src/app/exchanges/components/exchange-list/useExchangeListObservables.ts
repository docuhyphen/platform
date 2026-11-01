import {Dispatch, MutableRefObject, SetStateAction, useEffect} from 'react';
import {ExchangeListEntry} from "../../../models/models.tsx";
import {
    exchangeDeletionObservable,
    exchangeInitiationObservable,
    exchangeUpdatedObservable
} from "../../../observable/exchangeObservables.ts";
import {ExchangeListTab} from "./exchange-list-tabs/ExchangeListTabs.tsx";
import {InboxRole} from "./exchangeListTypes.ts";
import {isStatusInTab, statusToTab} from "./exchangeListTabFilters.ts";

interface ExchangeListObservableHandlers
{
    activeTabRef: MutableRefObject<ExchangeListTab>;
    inboxRoleRef: MutableRefObject<InboxRole>;
    selectedItemsRef: MutableRefObject<string[]>;
    setExchanges: Dispatch<SetStateAction<ExchangeListEntry[]>>;
    selectExchange: (exchangeId: string | null) => void;
    adjustTabCount: (tab: ExchangeListTab | null, role: InboxRole, delta: number) => void;
    switchTab: (tab: ExchangeListTab, inboxRoleForTab: InboxRole) => void;
    onTabChange?: (tab: ExchangeListTab) => void;
}

export const useExchangeListObservables = (
    {
        activeTabRef,
        inboxRoleRef,
        selectedItemsRef,
        setExchanges,
        selectExchange,
        adjustTabCount,
        switchTab,
        onTabChange,
    }: ExchangeListObservableHandlers) =>
{
    useEffect(() =>
    {
        const initiationSubscription = exchangeInitiationObservable.subscribe(exchange =>
        {
            if (!exchange) return;

            const targetTab = statusToTab(exchange.status) ?? 'inbox';

            if (targetTab === activeTabRef.current)
            {
                setExchanges(prevExchanges => [exchange, ...prevExchanges]);
                if (targetTab === 'inbox') adjustTabCount('inbox', inboxRoleRef.current, 1);
            }
            else
            {
                switchTab(targetTab, 'outgoing');
                onTabChange?.(targetTab);
            }

            selectExchange(null);
        });

        const deletionSubscription = exchangeDeletionObservable.subscribe(exchangeId =>
        {
            setExchanges(prevExchanges =>
            {
                const filteredExchanges = prevExchanges.filter(exchange => exchange.id !== exchangeId);
                if (selectedItemsRef.current.includes(exchangeId))
                {
                    selectExchange(filteredExchanges.length > 0 ? filteredExchanges[0].id : null);
                }
                return filteredExchanges;
            });
            if (activeTabRef.current === 'inbox') adjustTabCount('inbox', inboxRoleRef.current, -1);
        });

        const updatedSubscription = exchangeUpdatedObservable.subscribe(updatedExchange =>
        {
            if (!updatedExchange) return;

            setExchanges(prevExchanges =>
            {
                const shouldRemainVisible = isStatusInTab(updatedExchange.status, activeTabRef.current);
                const currentIndex = prevExchanges.findIndex(exchange => exchange.id === updatedExchange.id);

                if (currentIndex === -1)
                {
                    return shouldRemainVisible ? [updatedExchange, ...prevExchanges] : prevExchanges;
                }

                const previousTab = statusToTab(prevExchanges[currentIndex].status);
                const nextTab = statusToTab(updatedExchange.status);

                if (previousTab !== nextTab)
                {
                    adjustTabCount(previousTab, inboxRoleRef.current, -1);
                    adjustTabCount(nextTab, inboxRoleRef.current, 1);
                }

                if (!shouldRemainVisible)
                {
                    return prevExchanges.filter(exchange => exchange.id !== updatedExchange.id);
                }

                return prevExchanges.map(exchange =>
                    exchange.id === updatedExchange.id ? updatedExchange : exchange
                );
            });
        });

        return () =>
        {
            initiationSubscription.unsubscribe();
            deletionSubscription.unsubscribe();
            updatedSubscription.unsubscribe();
        };
    }, []);
};
