import React, {useEffect, useRef, useState} from 'react';
import {searchSharingSessions} from "../../../../services/sharingSessionApi.ts";
import {Button, CounterBadge, List, Tab, TabList, Text} from "@fluentui/react-components";
import {SharingSessionBasicDto, SharingSessionStatus} from "../../../models/models.tsx";
import {useSharingSessionStyles} from "./SessionListStyles.tsx";
import {
    sharingSessionDeletionObservable,
    sharingSessionInitiationObservable,
    sharingSessionUpdatedObservable
} from "../../../observable/sharingSessionObservables.ts";

import SessionListSkeleton from "./session-list-skeleton/SessionListSkeleton.tsx";
import SessionListItem from "./session-list-item/SessionListItem.tsx";
import SessionListSidebarToggle from "./session-list-sidebar-toggle/SessionListSidebarToggle.tsx";
import SessionListSearchControls from "./session-list-search-controls/SessionListSearchControls.tsx";
import SessionListPagination from "./session-list-pagination/SessionListPagination.tsx";
import SessionListTabs, {SessionListTab} from "./session-list-tabs/SessionListTabs.tsx";

const SIDEBAR_COLLAPSED_STORAGE_KEY = 'sharingSessions.sidebar.isCollapsed';

export interface SessionTabCounts
{
    inbox: number;
    active: number;
    archive: number;
}

interface SharingSessionListProps
{
    onSelectionChange: (sessionId: string | null) => void;
    onSessionListChange?: (sessions: SharingSessionBasicDto[]) => void;
    onTabChange?: (tab: SessionListTab) => void;
    onInboxRoleChange?: (role: InboxRole) => void;
    onTabCountsChange?: (counts: SessionTabCounts) => void;
    controlledSelectedId?: string | null;
    controlledActiveTab?: SessionListTab;
}

export type InboxRole = 'incoming' | 'outgoing';

const getTabFilters = (tab: SessionListTab, inboxRole: InboxRole): { status: string; initiatedBy: boolean | null } =>
{
    switch (tab)
    {
        case 'inbox':
            return {status: 'INITIATED', initiatedBy: inboxRole === 'incoming' ? false : true};
        case 'active':
            return {status: 'ACCEPTED_STARTED', initiatedBy: null};
        case 'archive':
            return {status: 'ENDED,REJECTED', initiatedBy: null};
    }
};

const isStatusInTab = (status: SharingSessionStatus | undefined, tab: SessionListTab): boolean =>
{
    if (!status) return false;

    switch (tab)
    {
        case 'inbox':
            return status === SharingSessionStatus.INITIATED;
        case 'active':
            return status === SharingSessionStatus.ACCEPTED_STARTED;
        case 'archive':
            return status === SharingSessionStatus.ENDED || status === SharingSessionStatus.REJECTED;
        default:
            return false;
    }
};

const statusToTab = (status: SharingSessionStatus | undefined): SessionListTab | null =>
{
    if (!status) return null;

    if (status === SharingSessionStatus.INITIATED) return 'inbox';
    if (status === SharingSessionStatus.ACCEPTED_STARTED) return 'active';
    if (status === SharingSessionStatus.ENDED || status === SharingSessionStatus.REJECTED) return 'archive';

    return null;
};

const SessionList: React.FC<SharingSessionListProps> = (
    {
        onSelectionChange,
        onSessionListChange,
        onTabChange,
        onInboxRoleChange,
        onTabCountsChange,
        controlledSelectedId,
        controlledActiveTab,
    }) =>
{
    const styles = useSharingSessionStyles();
    const [sharingSessions, setSharingSessions] = useState<SharingSessionBasicDto[]>([]);
    const [loadingSharingSessions, setLoadingSharingSessions] = useState(true);
    const [showLoadingState, setShowLoadingState] = useState(false);
    const [isSidebarCollapsed, setIsSidebarCollapsed] = useState(() =>
    {
        if (typeof window === 'undefined') return false;
        return window.localStorage.getItem(SIDEBAR_COLLAPSED_STORAGE_KEY) === 'true';
    });
    const [isSidebarHoverExpanded, setIsSidebarHoverExpanded] = useState(false);
    const [selectedItems, setSelectedItems] = useState<string[]>([]);

    const [activeTab, setActiveTab] = useState<SessionListTab>(controlledActiveTab ?? 'active');
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
        if (!loadingSharingSessions)
        {
            setShowLoadingState(false);
            return;
        }

        const timer = setTimeout(() =>
        {
            setShowLoadingState(true);
        }, 180);

        return () => clearTimeout(timer);
    }, [loadingSharingSessions]);

    const fetchInboxRoleCounts = async () =>
    {
        try
        {
            const [incomingResponse, outgoingResponse] = await Promise.all([
                searchSharingSessions(undefined, 'INITIATED', false, 0, 1, 'createdDate', 'DESC'),
                searchSharingSessions(undefined, 'INITIATED', true, 0, 1, 'createdDate', 'DESC')
            ]);

            setIncomingCount(incomingResponse && !('error' in incomingResponse) ? incomingResponse.totalElements : 0);
            setOutgoingCount(outgoingResponse && !('error' in outgoingResponse) ? outgoingResponse.totalElements : 0);
        }
        catch (error)
        {
            console.error(error);
        }
    };

    const fetchSessions = async () =>
    {
        const requestId = ++latestFetchRequestIdRef.current;

        try
        {
            setLoadingSharingSessions(true);

            const tabFilters = getTabFilters(activeTab, inboxRole);
            const effectiveInitiatedBy = tabFilters.initiatedBy !== null
                ? tabFilters.initiatedBy
                : (selectedInitiator ?? undefined);

            const response = await searchSharingSessions(
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
                setSharingSessions(response.content);
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

                if (response.content.length > 0 && selectedItems.length === 0)
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
                setLoadingSharingSessions(false);
            }
        }
    };

    useEffect(() =>
    {
        onSessionListChange?.(sharingSessions);
    }, [sharingSessions]);

    useEffect(() =>
    {
        // Inbox count reflects "incoming" items that require recipient action.
        onTabCountsChange?.({inbox: incomingCount, active: activeCount, archive: archiveCount});
    }, [incomingCount, activeCount, archiveCount, onTabCountsChange]);

    useEffect(() =>
    {
        if (loadingSharingSessions) return;

        let cancelled = false;
        const fetchCrossTabCounts = async () =>
        {
            try
            {
                const [activeResponse, archiveResponse] = await Promise.all([
                    searchSharingSessions(undefined, 'ACCEPTED_STARTED', undefined, 0, 1, 'createdDate', 'DESC'),
                    searchSharingSessions(undefined, 'ENDED,REJECTED', undefined, 0, 1, 'createdDate', 'DESC')
                ]);

                if (cancelled) return;

                setActiveCount(activeResponse && !('error' in activeResponse) ? activeResponse.totalElements : 0);
                setArchiveCount(archiveResponse && !('error' in archiveResponse) ? archiveResponse.totalElements : 0);

                if (activeTab === 'inbox')
                {
                    await fetchInboxRoleCounts();
                }
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
    }, [activeTab, loadingSharingSessions, sharingSessions.length, inboxRole]);

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
        setLoadingSharingSessions(true);
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
        fetchSessions();

        const initiationSubscription = sharingSessionInitiationObservable.subscribe(session =>
        {
            if (session)
            {
                setSharingSessions(prevSessions => [session, ...prevSessions]);
                setSelectedItems([session.id]);
                onSelectionChange(session.id);
                if (activeTabRef.current === 'inbox' && inboxRoleRef.current === 'incoming')
                {
                    setIncomingCount(prev => prev + 1);
                }
            }
        });

        const deletionSubscription = sharingSessionDeletionObservable.subscribe(sessionId =>
        {
            setSharingSessions(prevSessions =>
            {
                const filteredSessions = prevSessions.filter(session => session.id !== sessionId);
                if (selectedItemsRef.current.includes(sessionId))
                {
                    if (filteredSessions.length > 0)
                    {
                        setSelectedItems([filteredSessions[0].id]);
                        onSelectionChange(filteredSessions[0].id);
                    }
                    else
                    {
                        setSelectedItems([]);
                        onSelectionChange(null);
                    }
                }
                return filteredSessions;
            });
            if (activeTabRef.current === 'inbox')
            {
                if (inboxRoleRef.current === 'incoming') setIncomingCount(prev => Math.max(0, prev - 1));
                else setOutgoingCount(prev => Math.max(0, prev - 1));
            }
        });

        const updatedSubscription = sharingSessionUpdatedObservable.subscribe(updatedSession =>
        {
            if (updatedSession)
            {
                setSharingSessions(prevSessions =>
                {
                    const shouldRemainVisible = isStatusInTab(updatedSession.status, activeTabRef.current);
                    const currentIndex = prevSessions.findIndex(session => session.id === updatedSession.id);

                    if (currentIndex === -1)
                    {
                        return prevSessions;
                    }

                    const previousStatus = prevSessions[currentIndex].status;
                    const previousTab = statusToTab(previousStatus);
                    const nextTab = statusToTab(updatedSession.status);

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
                        const filteredSessions = prevSessions.filter(session => session.id !== updatedSession.id);

                        if (selectedItemsRef.current.includes(updatedSession.id))
                        {
                            if (filteredSessions.length > 0)
                            {
                                setSelectedItems([filteredSessions[0].id]);
                                onSelectionChange(filteredSessions[0].id);
                            }
                            else
                            {
                                setSelectedItems([]);
                                onSelectionChange(null);
                            }
                        }

                        return filteredSessions;
                    }

                    return prevSessions.map(session =>
                        session.id === updatedSession.id ? updatedSession : session
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
        fetchSessions();
    }, [activeTab, inboxRole, currentPage, sortBy, sortDirection]);

    // Debounced re-fetch on search / initiator-filter changes
    useEffect(() =>
    {
        if (isFirstRenderRef.current) return;

        const handler = setTimeout(() =>
        {
            setCurrentPage(0);
            fetchSessions();
        }, 500);

        return () => clearTimeout(handler);
    }, [searchQuery, selectedInitiator, activeTab, inboxRole]);

    const handleTabChange = (tab: SessionListTab) =>
    {
        // Enter loading immediately so empty states do not flash before fetchSessions sets loading.
        setLoadingSharingSessions(true);
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
        setLoadingSharingSessions(true);
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
        setLoadingSharingSessions(true);
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
        setLoadingSharingSessions(true);
        setSearchQuery(query);
    };

    const handleInitiatorFilterChange = (initiatedBy: boolean | null) =>
    {
        setLoadingSharingSessions(true);
        setSelectedInitiator(initiatedBy);
    };

    const handleSortChange = (field: string, direction: string) =>
    {
        setLoadingSharingSessions(true);
        setSortBy(field);
        setSortDirection(direction);
    };

    const handleClearFilters = () =>
    {
        setLoadingSharingSessions(true);
        setSearchQuery('');
        setSelectedInitiator(null);
        setCurrentPage(0);
        setSortBy('createdDate');
        setSortDirection('DESC');
    };

    const hasActiveFilters = !!searchQuery || selectedInitiator !== null;
    const isInboxMode = activeTab === 'inbox';
    const requestsCount = incomingCount + outgoingCount;
    const isSidebarVisuallyCollapsed = isSidebarCollapsed && !isSidebarHoverExpanded;

    return (
        <section
            className={isSidebarVisuallyCollapsed ? styles.sharingSessionsListContainerCollapsed : styles.sharingSessionsListContainer}
            onMouseEnter={handleSidebarMouseEnter}
            onMouseLeave={handleSidebarMouseLeave}>

            <SessionListTabs
                activeTab={activeTab}
                inboxCount={requestsCount}
                onTabChange={handleTabChange}
                collapsed={isSidebarVisuallyCollapsed}
            />

            {!isSidebarVisuallyCollapsed && isInboxMode && (
                <div className={styles.sharingSessionsListHeader}>
                    <TabList
                        id="session-list-inbox-role-tabs"
                        selectedValue={inboxRole}
                        onTabSelect={(_, data) => handleInboxRoleChange(data.value as InboxRole)}
                        size="small"
                        style={{width: '100%'}}>
                        <Tab id="session-list-inbox-role-incoming" value="incoming">
                            <span style={{display: 'flex', alignItems: 'center', gap: '4px'}}>
                                Incoming
                                {incomingCount > 0 && <CounterBadge count={incomingCount} size="small" appearance="filled" color="danger"/>}
                            </span>
                        </Tab>
                        <Tab id="session-list-inbox-role-outgoing" value="outgoing">
                            <span style={{display: 'flex', alignItems: 'center', gap: '4px'}}>
                                Outgoing
                                {outgoingCount > 0 && <CounterBadge count={outgoingCount} size="small" appearance="filled"/>}
                            </span>
                        </Tab>
                    </TabList>
                </div>
            )}

            {!isSidebarVisuallyCollapsed && !isInboxMode && (
                <div className={styles.sharingSessionsListHeader}>
                    <SessionListSearchControls
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
                    id="session-list-items"
                    className={styles.sharingSessionsListBody}
                    selectionMode="single"
                    navigationMode="items"
                    selectedItems={selectedItems}
                    onSelectionChange={handleSelectionChange}>

                    {showLoadingState && sharingSessions.length === 0 && <SessionListSkeleton count={10}/>}

                    {sharingSessions.map((session: SharingSessionBasicDto) => (
                        <SessionListItem
                            key={session.id}
                            session={session}
                            isSelected={selectedItems.includes(session.id)}
                            activeTab={activeTab}
                        />
                    ))}

                    {!loadingSharingSessions && sharingSessions.length === 0 && (
                        <div className={styles.emptyState}>
                            <Text align={"center"}>
                                {isInboxMode
                                    ? (inboxRole === 'incoming'
                                        ? 'No incoming requests are waiting for your decision.'
                                        : 'No outgoing requests are awaiting recipient response.')
                                    : 'No sessions match your search. Try adjusting your filters!'}
                            </Text>
                        </div>
                    )}
                </List>
            )}

            <div className={styles.sharingSessionsListFooter}>
                <SessionListSidebarToggle
                    isSidebarCollapsed={isSidebarCollapsed}
                    toggleSidebar={toggleSidebar}/>
                {!isSidebarVisuallyCollapsed && (
                    <>
                        <SessionListPagination
                            currentPage={currentPage}
                            totalPages={totalPages}
                            onPageChange={handlePageChange}/>

                        {hasActiveFilters && !isInboxMode && (
                            <Button
                                id="session-list-clear-filters"
                                size="small"
                                appearance="subtle"
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

export default SessionList;
