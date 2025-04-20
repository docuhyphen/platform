import React, {useEffect, useState} from 'react';
import {
    fetchSignedInUserAppUserSharingSessions,
    searchSharingSessions
} from "../../../../services/sharingSessionApi.ts";
import {Button, List, Text} from "@fluentui/react-components";
import {SharingSessionBasicDto} from "../../../models/models.tsx";
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

interface SharingSessionListProps
{
    onSelectionChange: (sessionId: string) => void;
}

const SessionList: React.FC<SharingSessionListProps> = ({onSelectionChange}) =>
{
    const styles = useSharingSessionStyles();
    const [sharingSessions, setSharingSessions] = useState<SharingSessionBasicDto[]>([]);
    const [loadingSharingSessions, setLoadingSharingSessions] = useState(true);
    const [isSidebarCollapsed, setIsSidebarCollapsed] = useState(false);
    const [selectedItems, setSelectedItems] = useState<string[]>([]);

    const [searchQuery, setSearchQuery] = useState<string>('');
    const [selectedStatus, setSelectedStatus] = useState<string | null>(null);
    const [selectedInitiator, setSelectedInitiator] = useState<boolean | null>(null);
    const [currentPage, setCurrentPage] = useState(0);
    const [pageSize] = useState(10);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [sortBy, setSortBy] = useState('createdDate');
    const [sortDirection, setSortDirection] = useState('DESC');

    const fetchSessions = async (useSearch = false) =>
    {
        try
        {
            setLoadingSharingSessions(true);
            let response;

            if (useSearch)
            {
                response = await searchSharingSessions(
                    searchQuery || undefined,
                    selectedStatus || undefined,
                    selectedInitiator,
                    currentPage,
                    pageSize,
                    sortBy,
                    sortDirection
                );

                if (response && !('error' in response))
                {
                    setSharingSessions(response.content);
                    setTotalPages(response.totalPages);
                    setTotalElements(response.totalElements);

                    if (response.content.length > 0 && selectedItems.length === 0)
                    {
                        handleInitialSelection(response.content);
                    }
                    else if (response.content.length === 0 && selectedItems.length > 0)
                    {
                        setSelectedItems([]);
                    }
                }
            }
            else
            {
                response = await fetchSignedInUserAppUserSharingSessions();

                if (Array.isArray(response) && response.length)
                {
                    setSharingSessions(response);
                    setTotalPages(Math.ceil(response.length / pageSize));
                    setTotalElements(response.length);

                    handleInitialSelection(response);
                }
            }
        }
        catch (error)
        {
            console.error(error);
        }
        finally
        {
            setLoadingSharingSessions(false);
        }
    };

    const handleInitialSelection = (sessions: SharingSessionBasicDto[]) =>
    {
        const urlParams = new URLSearchParams(window.location.search);
        const sessionId = urlParams.get('s');

        if (sessionId)
        {
            const sessionExists = sessions.some(session => session.id === sessionId);
            //ToDo: if it does not exist, then show warning.
            if (sessionExists)
            {
                setSelectedItems([sessionId]);
                onSelectionChange(sessionId);
            }
            else if (sessions.length > 0)
            {
                setSelectedItems([sessions[0].id]);
                onSelectionChange(sessions[0].id);
            }
        }
        else if (sessions.length > 0)
        {
            setSelectedItems([sessions[0].id]);
            onSelectionChange(sessions[0].id);
        }
    };

    useEffect(() =>
    {
        fetchSessions(true);

        const initiationSubscription = sharingSessionInitiationObservable.subscribe(session =>
        {
            if (session)
            {
                setSharingSessions(prevSessions => [session, ...prevSessions]);
                setSelectedItems([session.id]);
                onSelectionChange(session.id);
            }
        });

        const deletionSubscription = sharingSessionDeletionObservable.subscribe(sessionId =>
        {
            setSharingSessions(prevSessions =>
            {
                const filteredSessions = prevSessions.filter(session => session.id !== sessionId);
                if (selectedItems.includes(sessionId) && filteredSessions.length > 0)
                {
                    setSelectedItems([filteredSessions[0].id]);
                    onSelectionChange(filteredSessions[0].id);
                }
                return filteredSessions;
            });
        });

        const updatedSubscription = sharingSessionUpdatedObservable.subscribe(updatedSession =>
        {
            if (updatedSession)
            {
                setSharingSessions(prevSessions =>
                    prevSessions.map(session =>
                        session.id === updatedSession.id ? updatedSession : session
                    )
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

    useEffect(() =>
    {
        if (!loadingSharingSessions)
        {
            fetchSessions(true);
        }
    }, [currentPage, sortBy, sortDirection]);

    useEffect(() =>
    {
        const handler = setTimeout(() =>
        {
            if (!loadingSharingSessions)
            {
                setCurrentPage(0);
                fetchSessions(true);
            }
        }, 500);

        return () =>
        {
            clearTimeout(handler);
        };
    }, [searchQuery, selectedStatus, selectedInitiator]);

    const handleSelectionChange = (_, data) =>
    {
        setSelectedItems(data.selectedItems);
        onSelectionChange(data.selectedItems[0]);

        const urlParams = new URLSearchParams(window.location.search);
        urlParams.set('s', data.selectedItems[0]);
        window.history.replaceState(null, '', `?${urlParams.toString()}`);
    };

    const handlePageChange = (page: number) =>
    {
        setCurrentPage(page);
    };

    const toggleSidebar = () =>
    {
        setIsSidebarCollapsed(!isSidebarCollapsed);
    };

    const handleSearchQueryChange = (query: string) =>
    {
        setSearchQuery(query);
    };

    const handleFilterChange = (status: string | null) =>
    {
        setSelectedStatus(status);
    };

    const handleInitiatorFilterChange = (initiatedBy: boolean | null) =>
    {
        setSelectedInitiator(initiatedBy);
    };

    const handleSortChange = (field: string, direction: string) =>
    {
        setSortBy(field);
        setSortDirection(direction);
    };

    const handleClearFilters = () =>
    {
        setSearchQuery('');
        setSelectedStatus(null);
        setSelectedInitiator(null);
        setCurrentPage(0);
        setSortBy('createdDate');
        setSortDirection('DESC');
    };

    const hasActiveFilters = !!searchQuery || selectedStatus !== null || selectedInitiator !== null;

    return (
        <section
            className={isSidebarCollapsed ? styles.sharingSessionsListContainerCollapsed : styles.sharingSessionsListContainer}>
            {!isSidebarCollapsed && (
                <div className={styles.sharingSessionsListHeader}>
                    <SessionListSearchControls
                        searchQuery={searchQuery}
                        onSearchQueryChange={handleSearchQueryChange}
                        onFilterChange={handleFilterChange}
                        onInitiatorFilterChange={handleInitiatorFilterChange}
                        onSortChange={handleSortChange}
                        selectedStatus={selectedStatus}
                        selectedInitiator={selectedInitiator}
                        sortBy={sortBy}
                        sortDirection={sortDirection}
                        onClearFilters={handleClearFilters}
                        hasActiveFilters={hasActiveFilters}
                    />
                </div>
            )}

            <List
                className={styles.sharingSessionsListBody}
                selectionMode="single"
                navigationMode="items"
                selectedItems={selectedItems}
                onSelectionChange={handleSelectionChange}>

                {loadingSharingSessions && <SessionListSkeleton count={10}/>}

                {!loadingSharingSessions && sharingSessions.map((session: SharingSessionBasicDto) => (
                    <SessionListItem
                        key={session.id}
                        session={session}
                        isSelected={selectedItems.includes(session.id)}
                    />
                ))}

                {!loadingSharingSessions && sharingSessions.length === 0 && (
                    <div className={styles.emptyState}>
                        <Text align={"center"}>
                            No Sharing Sessions found match your search. Try adjusting your filters!
                        </Text>
                    </div>
                )}
            </List>

            <div className={styles.sharingSessionsListFooter}>
                <SessionListSidebarToggle
                    isSidebarCollapsed={isSidebarCollapsed}
                    toggleSidebar={toggleSidebar}/>
                {!isSidebarCollapsed && (
                    <>
                        <SessionListPagination
                            currentPage={currentPage}
                            totalPages={totalPages}
                            onPageChange={handlePageChange}/>

                        {hasActiveFilters && (
                            <Button
                                size="small"
                                appearance="subtle"
                                onClick={handleClearFilters}>
                                Clear filters
                            </Button>
                        )}
                    </>
                )}
                {/*<button onClick={ () => createAllSessions() }> CREATE TEST SESSIONS</button>*/}
            </div>
        </section>
    );
};

export default SessionList;