import {useEffect, useState} from 'react';
import {searchExchanges} from "../../../../services/exchangeApi.ts";
import {ExchangeListTab} from "./exchange-list-tabs/ExchangeListTabs.tsx";
import {ExchangeTabCounts, InboxRole} from "./exchangeListTypes.ts";

interface ExchangeListCountsOptions
{
    activeTab: ExchangeListTab;
    inboxRole: InboxRole;
    loadingExchanges: boolean;
    exchangeCount: number;
    onTabCountsChange?: (counts: ExchangeTabCounts) => void;
}

const readTotalElements = (response: Awaited<ReturnType<typeof searchExchanges>>): number =>
    response && 'content' in response ? response.totalElements : 0;

export const useExchangeListCounts = (
    {
        activeTab,
        inboxRole,
        loadingExchanges,
        exchangeCount,
        onTabCountsChange,
    }: ExchangeListCountsOptions) =>
{
    const [incomingCount, setIncomingCount] = useState(0);
    const [outgoingCount, setOutgoingCount] = useState(0);
    const [activeCount, setActiveCount] = useState(0);
    const [archiveCount, setArchiveCount] = useState(0);

    const fetchInboxRoleCounts = async () =>
    {
        try
        {
            const [incomingResponse, outgoingResponse] = await Promise.all([
                searchExchanges(undefined, 'INITIATED', false, 0, 1, 'createdDate', 'DESC'),
                searchExchanges(undefined, 'INITIATED', true, 0, 1, 'createdDate', 'DESC')
            ]);

            setIncomingCount(readTotalElements(incomingResponse));
            setOutgoingCount(readTotalElements(outgoingResponse));
        }
        catch (error)
        {
            console.error(error);
        }
    };

    useEffect(() =>
    {
        onTabCountsChange?.({inbox: incomingCount, active: activeCount, archive: archiveCount});
    }, [incomingCount, activeCount, archiveCount, onTabCountsChange]);

    useEffect(() =>
    {
        if (loadingExchanges) return;

        let cancelled = false;
        const fetchCrossTabCounts = async () =>
        {
            try
            {
                const [activeResponse, archiveResponse] = await Promise.all([
                    searchExchanges(undefined, 'ACCEPTED_STARTED', undefined, 0, 1, 'createdDate', 'DESC'),
                    searchExchanges(undefined, 'ENDED,REJECTED,RESCINDED', undefined, 0, 1, 'createdDate', 'DESC')
                ]);

                if (cancelled) return;

                setActiveCount(readTotalElements(activeResponse));
                setArchiveCount(readTotalElements(archiveResponse));

                await fetchInboxRoleCounts();
            }
            catch (error)
            {
                if (!cancelled)
                {
                    console.error(error);
                }
            }
        };

        fetchCrossTabCounts();

        return () =>
        {
            cancelled = true;
        };
    }, [activeTab, loadingExchanges, exchangeCount, inboxRole]);

    const setTabCount = (tab: ExchangeListTab, role: InboxRole, totalElements: number) =>
    {
        if (tab === 'inbox')
        {
            if (role === 'incoming') setIncomingCount(totalElements);
            else setOutgoingCount(totalElements);
        }
        else if (tab === 'active')
        {
            setActiveCount(totalElements);
        }
        else
        {
            setArchiveCount(totalElements);
        }
    };

    const adjustTabCount = (tab: ExchangeListTab | null, role: InboxRole, delta: number) =>
    {
        if (tab === 'inbox')
        {
            if (role === 'incoming') setIncomingCount(prev => Math.max(0, prev + delta));
            else setOutgoingCount(prev => Math.max(0, prev + delta));
        }
        if (tab === 'active') setActiveCount(prev => Math.max(0, prev + delta));
        if (tab === 'archive') setArchiveCount(prev => Math.max(0, prev + delta));
    };

    return {
        incomingCount,
        outgoingCount,
        requestsCount: incomingCount + outgoingCount,
        setTabCount,
        adjustTabCount,
    };
};
