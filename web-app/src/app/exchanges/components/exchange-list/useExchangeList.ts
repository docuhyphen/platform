import React, {useEffect, useRef, useState} from 'react';
import {ListProps} from "@fluentui/react-components";
import {searchExchanges} from "../../../../services/exchangeApi.ts";
import {ExchangeListEntry} from "../../../models/models.tsx";
import {realtimeService} from '../../../../services/NotificationService';
import {useDelayedLoading} from "../../../../hooks/useDelayedLoading.ts";
import {ExchangeListTab} from "./exchange-list-tabs/ExchangeListTabs.tsx";
import {ExchangeListProps, InboxRole} from "./exchangeListTypes.ts";
import {getTabFilters} from "./exchangeListTabFilters.ts";
import {useLatestRef} from "./useLatestRef.ts";
import {useExchangeListFilters} from "./useExchangeListFilters.ts";
import {useExchangeListCounts} from "./useExchangeListCounts.ts";
import {useExchangeListObservables} from "./useExchangeListObservables.ts";

export const useExchangeList = (props: ExchangeListProps, isMobile: boolean) =>
{
    const {
        onSelectionChange,
        onExchangeListChange,
        onTabChange,
        onInboxRoleChange,
        onTabCountsChange,
        onLoadingChange,
        controlledSelectedId,
        controlledActiveTab,
        controlledInboxRole,
    } = props;
    const [exchanges, setExchanges] = useState<ExchangeListEntry[]>([]);
    const [loadingExchanges, setLoadingExchanges] = useState(true);
    const [hasListScrolled, setHasListScrolled] = useState(false);
    const [selectedItems, setSelectedItems] = useState<string[]>(
        () => controlledSelectedId ? [controlledSelectedId] : [],
    );
    const [activeTab, setActiveTab] = useState<ExchangeListTab>(controlledActiveTab ?? 'active');
    const [inboxRole, setInboxRole] = useState<InboxRole>('incoming');
    const [realtimeRefreshVersion, setRealtimeRefreshVersion] = useState(0);
    const filters = useExchangeListFilters(() => setLoadingExchanges(true));

    const isFirstRenderRef = useRef(true);
    const latestFetchRequestIdRef = useRef(0);
    const activeTabRef = useLatestRef(activeTab);
    const selectedItemsRef = useLatestRef(selectedItems);
    const inboxRoleRef = useLatestRef(inboxRole);
    const isFirstSearchEffectRef = useRef(true);
    const searchEffectTabRef = useRef(activeTab);
    const searchEffectRoleRef = useRef(inboxRole);
    const showLoadingState = useDelayedLoading(loadingExchanges && exchanges.length === 0);

    useEffect(() =>
    {
        onLoadingChange?.(loadingExchanges);
    }, [loadingExchanges, onLoadingChange]);

    useEffect(() =>
    {
        return realtimeService.on('EXCHANGE_LIST_CHANGED', () =>
        {
            setRealtimeRefreshVersion((version) => version + 1);
        });
    }, []);

    useEffect(() =>
    {
        onExchangeListChange?.(exchanges);
    }, [exchanges]);

    const counts = useExchangeListCounts({
        activeTab,
        inboxRole,
        loadingExchanges,
        exchangeCount: exchanges.length,
        onTabCountsChange,
    });

    const selectExchange = (exchangeId: string | null) =>
    {
        setSelectedItems(exchangeId ? [exchangeId] : []);
        onSelectionChange(exchangeId);
    };

    const beginListScopeLoading = () =>
    {
        latestFetchRequestIdRef.current += 1;
        setLoadingExchanges(true);
        setHasListScrolled(false);
    };

    const switchTab = (tab: ExchangeListTab, inboxRoleForTab: InboxRole) =>
    {
        beginListScopeLoading();
        setActiveTab(tab);
        filters.resetFilters();
        if (tab === 'inbox')
        {
            setInboxRole(inboxRoleForTab);
            onInboxRoleChange?.(inboxRoleForTab);
        }
    };

    const fetchExchanges = async () =>
    {
        const requestId = ++latestFetchRequestIdRef.current;

        try
        {
            setLoadingExchanges(true);

            const tabFilters = getTabFilters(activeTab, inboxRole);
            const effectiveInitiatedBy = tabFilters.initiatedBy !== null
                ? tabFilters.initiatedBy
                : (filters.selectedInitiator ?? undefined);

            const response = await searchExchanges(
                filters.searchQuery || undefined,
                tabFilters.status,
                effectiveInitiatedBy,
                filters.currentPage,
                filters.pageSize,
                filters.sortBy,
                filters.sortDirection
            );

            if (requestId !== latestFetchRequestIdRef.current)
            {
                return;
            }

            if (response && 'content' in response)
            {
                setExchanges(response.content);
                filters.setTotalPages(response.totalPages);
                counts.setTabCount(activeTab, inboxRole, response.totalElements);

                if (response.content.length > 0 && selectedItemsRef.current.length === 0 && !isMobile)
                {
                    selectExchange(response.content[0].id);
                }
                else if (response.content.length === 0 && selectedItemsRef.current.length > 0)
                {
                    selectExchange(null);
                }
            }
        }
        catch (error)
        {
            console.error(error);
        }
        finally
        {
            if (requestId === latestFetchRequestIdRef.current)
            {
                setLoadingExchanges(false);
            }
        }
    };

    useEffect(() =>
    {
        if (controlledSelectedId && controlledSelectedId !== selectedItems[0])
        {
            setSelectedItems([controlledSelectedId]);
        }
    }, [controlledSelectedId]);

    useEffect(() =>
    {
        if (!controlledActiveTab || controlledActiveTab === activeTab) return;

        switchTab(controlledActiveTab, controlledInboxRole ?? 'incoming');
        selectExchange(controlledSelectedId || null);
        onTabChange?.(controlledActiveTab);
    }, [controlledActiveTab, controlledSelectedId]);

    useEffect(() =>
    {
        if (activeTab !== 'inbox') return;
        if (!controlledInboxRole || controlledInboxRole === inboxRole) return;

        beginListScopeLoading();
        setInboxRole(controlledInboxRole);
        filters.setCurrentPage(0);
    }, [controlledInboxRole, activeTab]);

    useEffect(() =>
    {
        fetchExchanges();
    }, []);

    useExchangeListObservables({
        activeTabRef,
        inboxRoleRef,
        selectedItemsRef,
        setExchanges,
        selectExchange,
        adjustTabCount: counts.adjustTabCount,
        switchTab,
        onTabChange,
    });

    useEffect(() =>
    {
        if (isFirstRenderRef.current)
        {
            isFirstRenderRef.current = false;
            return;
        }
        fetchExchanges();
    }, [activeTab, inboxRole, filters.currentPage, filters.sortBy, filters.sortDirection, realtimeRefreshVersion]);

    useEffect(() =>
    {
        if (isFirstSearchEffectRef.current)
        {
            isFirstSearchEffectRef.current = false;
            searchEffectTabRef.current = activeTab;
            searchEffectRoleRef.current = inboxRole;
            return;
        }

        if (searchEffectTabRef.current !== activeTab || searchEffectRoleRef.current !== inboxRole)
        {
            searchEffectTabRef.current = activeTab;
            searchEffectRoleRef.current = inboxRole;
            return;
        }

        const handler = setTimeout(() =>
        {
            filters.setCurrentPage(0);
            fetchExchanges();
        }, 500);

        return () => clearTimeout(handler);
    }, [filters.searchQuery, filters.selectedInitiator, activeTab, inboxRole]);

    const handleTabChange = (tab: ExchangeListTab) =>
    {
        switchTab(tab, 'incoming');
        selectExchange(null);
        onTabChange?.(tab);
    };

    const handleInboxRoleChange = (role: InboxRole) =>
    {
        beginListScopeLoading();
        setInboxRole(role);
        onInboxRoleChange?.(role);
        filters.setCurrentPage(0);
        selectExchange(null);
    };

    const handleSelectionChange: NonNullable<ListProps["onSelectionChange"]> = (_, data) =>
    {
        const selectedIds = data.selectedItems.map(String);
        setSelectedItems(selectedIds);
        onSelectionChange(selectedIds[0] ?? null);
    };

    const handleListScroll: React.UIEventHandler<HTMLElement> = (event) =>
    {
        setHasListScrolled(event.currentTarget.scrollTop > 0);
    };

    return {
        exchanges,
        loadingExchanges,
        showLoadingState,
        hasListScrolled,
        selectedItems,
        activeTab,
        inboxRole,
        filters,
        counts,
        handleTabChange,
        handleInboxRoleChange,
        handleSelectionChange,
        handleListScroll,
    };
};
