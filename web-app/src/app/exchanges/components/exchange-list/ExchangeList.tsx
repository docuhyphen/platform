import React, {useEffect, useRef, useState} from 'react';
import {searchExchanges} from "../../../../services/exchangeApi.ts";
import {Button, CounterBadge, List, Tab, TabList, Text} from "@fluentui/react-components";
import {ExchangeBasicDto, ExchangeStatus} from "../../../models/models.tsx";
import {useExchangeStyles} from "./ExchangeListStyles.tsx";
import {
    exchangeDeletionObservable,
    exchangeInitiationObservable,
    exchangeUpdatedObservable
} from "../../../observable/exchangeObservables.ts";

import ExchangeListSkeleton from "./exchange-list-skeleton/ExchangeListSkeleton.tsx";
import ExchangeListItem from "./exchange-list-item/ExchangeListItem.tsx";
import ExchangeListSidebarToggle from "./exchange-list-sidebar-toggle/ExchangeListSidebarToggle.tsx";
import ExchangeListSearchControls from "./exchange-list-search-controls/ExchangeListSearchControls.tsx";
import ExchangeListPagination from "./exchange-list-pagination/ExchangeListPagination.tsx";
import ExchangeListTabs, {ExchangeListTab} from "./exchange-list-tabs/ExchangeListTabs.tsx";
import {useIsMobile} from "../../../../utils/useMediaQuery.ts";

const SIDEBAR_COLLAPSED_STORAGE_KEY = 'exchanges.sidebar.isCollapsed';

export interface ExchangeTabCounts
{
    inbox: number;
    active: number;
    archive: number;
}

interface ExchangeListProps
{
    onSelectionChange: (exchangeId: string | null) => void;
    onExchangeListChange?: (exchanges: ExchangeBasicDto[]) => void;
    onTabChange?: (tab: ExchangeListTab) => void;
    onInboxRoleChange?: (role: InboxRole) => void;
    onTabCountsChange?: (counts: ExchangeTabCounts) => void;
    controlledSelectedId?: string | null;
    controlledActiveTab?: ExchangeListTab;
}

export type InboxRole = 'incoming' | 'outgoing';

const getTabFilters = (tab: ExchangeListTab, inboxRole: InboxRole): { status: string; initiatedBy: boolean | null } =>
{
    switch (tab)
    {
        case 'inbox':
            return {status: 'INITIATED', initiatedBy: inboxRole === 'incoming' ? false : true};
        case 'active':
            return {status: 'ACCEPTED_STARTED', initiatedBy: null};
        case 'archive':
            return {status: 'ENDED,REJECTED,RESCINDED', initiatedBy: null};
    }
};

const isStatusInTab = (status: ExchangeStatus | undefined, tab: ExchangeListTab): boolean =>
{
    if (!status) return false;

    switch (tab)
    {
        case 'inbox':
            return status === ExchangeStatus.INITIATED;
        case 'active':
            return status === ExchangeStatus.ACCEPTED_STARTED;
        case 'archive':
            return (
                status === ExchangeStatus.ENDED ||
                status === ExchangeStatus.REJECTED ||
                status === ExchangeStatus.RESCINDED
            );
        default:
            return false;
    }
};

const statusToTab = (status: ExchangeStatus | undefined): ExchangeListTab | null =>
{
    if (!status) return null;

    if (status === ExchangeStatus.INITIATED) return 'inbox';
    if (status === ExchangeStatus.ACCEPTED_STARTED) return 'active';
    if (
        status === ExchangeStatus.ENDED ||
        status === ExchangeStatus.REJECTED ||
        status === ExchangeStatus.RESCINDED
    ) return 'archive';

    return null;
};

const ExchangeList: React.FC<ExchangeListProps> = (
    {
        onSelectionChange,
        onExchangeListChange,
        onTabChange,
        onInboxRoleChange,
        onTabCountsChange,
        controlledSelectedId,
        controlledActiveTab,
    }) =>
{
    const styles = useExchangeStyles();
    const isMobile = useIsMobile();
    const [exchanges, setExchanges] = useState<ExchangeBasicDto[]>([]);
    const [loadingExchanges, setLoadingExchanges] = useState(true);
    const [showLoadingState, setShowLoadingState] = useState(false);
    const [isSidebarCollapsed, setIsSidebarCollapsed] = useState(() =>
    {
        if (typeof window === 'undefined') return false;
        return window.localStorage.getItem(SIDEBAR_COLLAPSED_STORAGE_KEY) === 'true';
    });
    const [isSidebarHoverExpanded, setIsSidebarHoverExpanded] = useState(false);
    const [selectedItems, setSelectedItems] = useState<string[]>([]);

    const [activeTab, setActiveTab] = useState<ExchangeListTab>(controlledActiveTab ?? 'active');
    const [inboxRole, setInboxRole] = useState<InboxRole>('incoming');
    const [incomingCount, setIncomingCount] = useState(0);
    const [outgoingCount, setOutgoingCount] = useState(0);
    const [activeCount, setActiveCount] = useState(0);
    const [archiveCount, setArchiveCount] = useState(0);
    const [searchQuery, setSearchQuery] = useState<string>('');
    const [selectedInitiator, setSelectedInitiator] = useState<boolean | null>(null);
    const [currentPage, setCurrentPage] = useState(0);
    const [pageSize] = useState(30);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [sortBy, setSortBy] = useState('createdDate');
    const [sortDirection, setSortDirection] = useState('DESC');

    const isFirstRenderRef = useRef(true);
    const latestFetchRequestIdRef = useRef(0);
    const activeTabRef = useRef(activeTab);
    const selectedItemsRef = useRef(selectedItems);
    const inboxRoleRef = useRef(inboxRole);

    // Guards for the debounced search/initiator effect. It must skip its own first run and any
    // run triggered purely by a tab / inbox-role switch (those are handled by the immediate
    // navigation effect). Without these, a second debounced fetch fires ~500ms later, flipping
    // loadingExchanges off->on->off and making the empty-state message flash twice.
    const isFirstSearchEffectRef = useRef(true);
    const searchEffectTabRef = useRef(activeTab);
    const searchEffectRoleRef = useRef(inboxRole);

    useEffect(() =>
    {
        activeTabRef.current = activeTab;
    }, [activeTab]);

    useEffect(() =>
    {
        selectedItemsRef.current = selectedItems;
    }, [selectedItems]);

    useEffect(() =>
    {
        inboxRoleRef.current = inboxRole;
    }, [inboxRole]);

    useEffect(() =>
    {
        if (typeof window === 'undefined') return;
        window.localStorage.setItem(SIDEBAR_COLLAPSED_STORAGE_KEY, String(isSidebarCollapsed));
    }, [isSidebarCollapsed]);

    useEffect(() =>
    {
        if (!loadingExchanges)
        {
            setShowLoadingState(false);
            return;
        }

        const timer = setTimeout(() =>
        {
            setShowLoadingState(true);
        }, 180);

        return () => clearTimeout(timer);
    }, [loadingExchanges]);

    const fetchInboxRoleCounts = async () =>
    {
        try
        {
            const [incomingResponse, outgoingResponse] = await Promise.all([
                searchExchanges(undefined, 'INITIATED', false, 0, 1, 'createdDate', 'DESC'),
                searchExchanges(undefined, 'INITIATED', true, 0, 1, 'createdDate', 'DESC')
            ]);

            setIncomingCount(incomingResponse && !('error' in incomingResponse) ? incomingResponse.totalElements : 0);
            setOutgoingCount(outgoingResponse && !('error' in outgoingResponse) ? outgoingResponse.totalElements : 0);
        }
        catch (error)
        {
            console.error(error);
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
                : (selectedInitiator ?? undefined);

            const response = await searchExchanges(
                searchQuery || undefined,
                tabFilters.status,
                effectiveInitiatedBy,
                currentPage,
                pageSize,
                sortBy,
                sortDirection
            );

            if (requestId !== latestFetchRequestIdRef.current)
            {
                return;
            }

            if (response && !('error' in response))
            {
                setExchanges(response.content);
                setTotalPages(response.totalPages);
                setTotalElements(response.totalElements);

                if (activeTab === 'inbox')
                {
                    if (inboxRole === 'incoming') setIncomingCount(response.totalElements);
                    else setOutgoingCount(response.totalElements);
                }
                else if (activeTab === 'active')
                {
                    setActiveCount(response.totalElements);
                }
                else
                {
                    setArchiveCount(response.totalElements);
                }

                // Desktop convenience: pre-select the first exchange so the
                // details pane isn't blank when a tab loads. On mobile we use
                // a master/detail navigation (the list is hidden as soon as a
                // exchange is selected), so auto-selecting the first item
                // would hide the just-loaded list before the user can even
                // see it exch- they'd be unable to pick a different exchange
                // without first hitting "back". Skip the auto-selection
                // entirely on phones and let the user tap to choose.
                if (response.content.length > 0 && selectedItems.length === 0 && !isMobile)
                {
                    setSelectedItems([response.content[0].id]);
                    onSelectionChange(response.content[0].id);
                }
                else if (response.content.length === 0 && selectedItems.length > 0)
                {
                    setSelectedItems([]);
                    onSelectionChange(null);
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
        onExchangeListChange?.(exchanges);
    }, [exchanges]);

    useEffect(() =>
    {
        // Inbox count reflects "incoming" items that require recipient action.
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

                setActiveCount(activeResponse && !('error' in activeResponse) ? activeResponse.totalElements : 0);
                setArchiveCount(archiveResponse && !('error' in archiveResponse) ? archiveResponse.totalElements : 0);

                // Always refresh inbox counts regardless of the current tab so the inbox badge
                // is accurate on initial load and the first-visit auto-tab-switch works correctly
                // for users whose only visible exchanges are in draft (INITIATED) state, e.g. when
                // they have been added as a manager to an existing exchange that hasn't been
                // accepted or declined yet.
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
    }, [activeTab, loadingExchanges, exchanges.length, inboxRole]);

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

        // Avoid one-frame empty-state flashes while the controlled tab transition triggers a fetch.
        setLoadingExchanges(true);
        setActiveTab(controlledActiveTab);
        setCurrentPage(0);
        setSearchQuery('');
        setSelectedInitiator(null);
        setSortBy('createdDate');
        setSortDirection('DESC');
        if (controlledActiveTab === 'inbox')
        {
            setInboxRole('incoming');
            onInboxRoleChange?.('incoming');
        }
        if (controlledSelectedId)
        {
            setSelectedItems([controlledSelectedId]);
            onSelectionChange(controlledSelectedId);
        }
        else
        {
            setSelectedItems([]);
            onSelectionChange(null);
        }
        onTabChange?.(controlledActiveTab);
    }, [controlledActiveTab, controlledSelectedId]);

    // Initial mount: fetch + subscriptions
    useEffect(() =>
    {
        fetchExchanges();

        const initiationSubscription = exchangeInitiationObservable.subscribe(exchange =>
        {
            if (exchange)
            {
                const targetTab = statusToTab(exchange.status) ?? 'inbox';
                const isCurrentTab = targetTab === activeTabRef.current;

                if (isCurrentTab)
                {
                    // Exchange belongs to current tab - insert directly into the list
                    setExchanges(prevExchanges => [exchange, ...prevExchanges]);
                    if (targetTab === 'inbox')
                    {
                        if (inboxRoleRef.current === 'incoming') setIncomingCount(prev => prev + 1);
                        else setOutgoingCount(prev => prev + 1);
                    }
                }
                else
                {
                    // Switch to the correct tab. Set state directly (not via controlledActiveTab)
                    // so the inbox role is not reset to 'incoming' by that effect.
                    setActiveTab(targetTab);
                    setCurrentPage(0);
                    setSearchQuery('');
                    setSelectedInitiator(null);
                    setSortBy('createdDate');
                    setSortDirection('DESC');
                    if (targetTab === 'inbox')
                    {
                        // A newly created exchange is always initiated by the current user -> outgoing
                        setInboxRole('outgoing');
                        onInboxRoleChange?.('outgoing');
                    }
                    onTabChange?.(targetTab);
                }

                setSelectedItems([exchange.id]);
                onSelectionChange(exchange.id);
            }
        });

        const deletionSubscription = exchangeDeletionObservable.subscribe(exchangeId =>
        {
            setExchanges(prevExchanges =>
            {
                const filteredExchanges = prevExchanges.filter(exchange => exchange.id !== exchangeId);
                if (selectedItemsRef.current.includes(exchangeId))
                {
                    if (filteredExchanges.length > 0)
                    {
                        setSelectedItems([filteredExchanges[0].id]);
                        onSelectionChange(filteredExchanges[0].id);
                    }
                    else
                    {
                        setSelectedItems([]);
                        onSelectionChange(null);
                    }
                }
                return filteredExchanges;
            });
            if (activeTabRef.current === 'inbox')
            {
                if (inboxRoleRef.current === 'incoming') setIncomingCount(prev => Math.max(0, prev - 1));
                else setOutgoingCount(prev => Math.max(0, prev - 1));
            }
        });

        const updatedSubscription = exchangeUpdatedObservable.subscribe(updatedExchange =>
        {
            if (updatedExchange)
            {
                setExchanges(prevExchanges =>
                {
                    const shouldRemainVisible = isStatusInTab(updatedExchange.status, activeTabRef.current);
                    const currentIndex = prevExchanges.findIndex(exchange => exchange.id === updatedExchange.id);

                    if (currentIndex === -1)
                    {
                        return prevExchanges;
                    }

                    const previousStatus = prevExchanges[currentIndex].status;
                    const previousTab = statusToTab(previousStatus);
                    const nextTab = statusToTab(updatedExchange.status);

                    if (previousTab !== nextTab)
                    {
                        if (previousTab === 'inbox')
                        {
                            if (inboxRoleRef.current === 'incoming') setIncomingCount(prev => Math.max(0, prev - 1));
                            else setOutgoingCount(prev => Math.max(0, prev - 1));
                        }
                        if (previousTab === 'active') setActiveCount(prev => Math.max(0, prev - 1));
                        if (previousTab === 'archive') setArchiveCount(prev => Math.max(0, prev - 1));

                        if (nextTab === 'inbox')
                        {
                            if (inboxRoleRef.current === 'incoming') setIncomingCount(prev => prev + 1);
                            else setOutgoingCount(prev => prev + 1);
                        }
                        if (nextTab === 'active') setActiveCount(prev => prev + 1);
                        if (nextTab === 'archive') setArchiveCount(prev => prev + 1);
                    }

                    if (!shouldRemainVisible)
                    {
                        const filteredExchanges = prevExchanges.filter(exchange => exchange.id !== updatedExchange.id);

                        if (selectedItemsRef.current.includes(updatedExchange.id))
                        {
                            if (filteredExchanges.length > 0)
                            {
                                setSelectedItems([filteredExchanges[0].id]);
                                onSelectionChange(filteredExchanges[0].id);
                            }
                            else
                            {
                                setSelectedItems([]);
                                onSelectionChange(null);
                            }
                        }

                        return filteredExchanges;
                    }

                    return prevExchanges.map(exchange =>
                        exchange.id === updatedExchange.id ? updatedExchange : exchange
                    );
                }
                );
            }
        });

        return () =>
        {
            initiationSubscription.unsubscribe();
            deletionSubscription.unsubscribe();
            updatedSubscription.unsubscribe();
        };
    }, []);

    // Re-fetch when tab, page, or sort changes
    useEffect(() =>
    {
        if (isFirstRenderRef.current)
        {
            isFirstRenderRef.current = false;
            return;
        }
        fetchExchanges();
    }, [activeTab, inboxRole, currentPage, sortBy, sortDirection]);

    // Debounced re-fetch on search / initiator-filter changes only. Tab and inbox-role changes
    // are already handled immediately by the navigation effect above; they remain in this
    // effect's deps solely so it can detect them, resync, and skip  -  otherwise a second
    // debounced fetch would flash the empty state. This effect must NOT share the navigation
    // effect's first-render ref (the navigation effect flips it before this one reads it).
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
            // Tab / inbox-role switch: the navigation effect already refetched. Just resync.
            searchEffectTabRef.current = activeTab;
            searchEffectRoleRef.current = inboxRole;
            return;
        }

        const handler = setTimeout(() =>
        {
            setCurrentPage(0);
            fetchExchanges();
        }, 500);

        return () => clearTimeout(handler);
    }, [searchQuery, selectedInitiator, activeTab, inboxRole]);

    const handleTabChange = (tab: ExchangeListTab) =>
    {
        // Enter loading immediately so empty states do not flash before fetchExchanges sets loading.
        setLoadingExchanges(true);
        setActiveTab(tab);
        setCurrentPage(0);
        setSearchQuery('');
        setSelectedInitiator(null);
        setSortBy('createdDate');
        setSortDirection('DESC');
        if (tab === 'inbox')
        {
            setInboxRole('incoming');
            onInboxRoleChange?.('incoming');
        }
        setSelectedItems([]);
        onSelectionChange(null);
        onTabChange?.(tab);
    };

    const handleInboxRoleChange = (role: InboxRole) =>
    {
        setLoadingExchanges(true);
        setInboxRole(role);
        onInboxRoleChange?.(role);
        setCurrentPage(0);
        setSelectedItems([]);
        onSelectionChange(null);
    };

    const handleSelectionChange = (_, data) =>
    {
        setSelectedItems(data.selectedItems);
        onSelectionChange(data.selectedItems[0] ?? null);
    };

    const handlePageChange = (page: number) =>
    {
        setLoadingExchanges(true);
        setCurrentPage(page);
    };

    const toggleSidebar = () =>
    {
        if (isSidebarCollapsed)
        {
            // Explicit expand: pin open until user collapses again.
            setIsSidebarCollapsed(false);
            setIsSidebarHoverExpanded(false);
            return;
        }

        setIsSidebarCollapsed(true);
        setIsSidebarHoverExpanded(false);
    };

    const handleSidebarMouseEnter = () =>
    {
        if (isSidebarCollapsed)
        {
            setIsSidebarHoverExpanded(true);
        }
    };

    const handleSidebarMouseLeave = () =>
    {
        if (isSidebarCollapsed)
        {
            setIsSidebarHoverExpanded(false);
        }
    };

    const handleSearchQueryChange = (query: string) =>
    {
        setLoadingExchanges(true);
        setSearchQuery(query);
    };

    const handleInitiatorFilterChange = (initiatedBy: boolean | null) =>
    {
        setLoadingExchanges(true);
        setSelectedInitiator(initiatedBy);
    };

    const handleSortChange = (field: string, direction: string) =>
    {
        setLoadingExchanges(true);
        setSortBy(field);
        setSortDirection(direction);
    };

    const handleClearFilters = () =>
    {
        setLoadingExchanges(true);
        setSearchQuery('');
        setSelectedInitiator(null);
        setCurrentPage(0);
        setSortBy('createdDate');
        setSortDirection('DESC');
    };

    const hasActiveFilters = !!searchQuery || selectedInitiator !== null;
    const isInboxMode = activeTab === 'inbox';
    const requestsCount = incomingCount + outgoingCount;
    // The collapse/expand sidebar affordance only makes sense on tablet
    // and larger viewports where the list shares horizontal space with
    // the details pane. On phones we use a master-detail navigation, so
    // collapsing the list to a 2.8rem rail has no value and would just
    // confuse users. Force the sidebar fully expanded on mobile and hide
    // its toggle button further down.
    const isSidebarVisuallyCollapsed = !isMobile && isSidebarCollapsed && !isSidebarHoverExpanded;

    return (
        <section
            className={isSidebarVisuallyCollapsed ? styles.exchangesListContainerCollapsed : styles.exchangesListContainer}
            onMouseEnter={handleSidebarMouseEnter}
            onMouseLeave={handleSidebarMouseLeave}>

            <ExchangeListTabs
                activeTab={activeTab}
                inboxCount={requestsCount}
                onTabChange={handleTabChange}
                collapsed={isSidebarVisuallyCollapsed}
            />

            {!isSidebarVisuallyCollapsed && isInboxMode && (
                <div className={styles.exchangesListHeader}>
                    <TabList
                        id="exchange-list-inbox-role-tabs"
                        selectedValue={inboxRole}
                        onTabSelect={(_, data) => handleInboxRoleChange(data.value as InboxRole)}
                        size="small"
                        className={styles.inboxRoleTabList}>
                        <Tab id="exchange-list-inbox-role-incoming" value="incoming">
                            <span className={styles.tabContentFlex}>
                                Incoming
                                {incomingCount > 0 && <CounterBadge count={incomingCount} size="small" appearance="filled" color="danger"/>}
                            </span>
                        </Tab>
                        <Tab id="exchange-list-inbox-role-outgoing" value="outgoing">
                            <span className={styles.tabContentFlex}>
                                Outgoing
                                {outgoingCount > 0 && <CounterBadge count={outgoingCount} size="small" appearance="filled"/>}
                            </span>
                        </Tab>
                    </TabList>
                </div>
            )}

            {!isSidebarVisuallyCollapsed && !isInboxMode && (
                <div className={styles.exchangesListHeader}>
                    <ExchangeListSearchControls
                        searchQuery={searchQuery}
                        onSearchQueryChange={handleSearchQueryChange}
                        onInitiatorFilterChange={handleInitiatorFilterChange}
                        onSortChange={handleSortChange}
                        selectedInitiator={selectedInitiator}
                        sortBy={sortBy}
                        sortDirection={sortDirection}
                        onClearFilters={handleClearFilters}
                        hasActiveFilters={hasActiveFilters}
                    />
                </div>
            )}

            {!isSidebarVisuallyCollapsed && (
                <List
                    id="exchange-list-items"
                    className={styles.exchangesListBody}
                    selectionMode="single"
                    navigationMode="items"
                    selectedItems={selectedItems}
                    onSelectionChange={handleSelectionChange}>

                    {showLoadingState && exchanges.length === 0 && <ExchangeListSkeleton count={10}/>}

                    {exchanges.map((exchange: ExchangeBasicDto) => (
                        <ExchangeListItem
                            key={exchange.id}
                            exchange={exchange}
                            isSelected={selectedItems.includes(exchange.id)}
                            activeTab={activeTab}
                        />
                    ))}

                    {!loadingExchanges && exchanges.length === 0 && (
                        <div className={styles.emptyState}>
                            <Text align={"center"}>
                                {isInboxMode
                                    ? (inboxRole === 'incoming'
                                        ? 'No incoming requests are waiting for your decision.'
                                        : 'No outgoing requests are awaiting recipient response.')
                                    : 'No exchanges match your search. Try adjusting your filters!'}
                            </Text>
                        </div>
                    )}
                </List>
            )}

            <div className={styles.exchangesListFooter}>
                {!isMobile && (
                    <ExchangeListSidebarToggle
                        isSidebarCollapsed={isSidebarCollapsed}
                        toggleSidebar={toggleSidebar}/>
                )}
                {!isSidebarVisuallyCollapsed && (
                    <>
                        <ExchangeListPagination
                            currentPage={currentPage}
                            totalPages={totalPages}
                            onPageChange={handlePageChange}/>

                        {hasActiveFilters && !isInboxMode && (
                            <Button
                                id="exchange-list-clear-filters"
                                size="small"
                                appearance="subtle"
                                shape={"circular"}
                                onClick={handleClearFilters}>
                                Clear filters
                            </Button>
                        )}
                    </>
                )}
            </div>
        </section>
    );
};

export default ExchangeList;
