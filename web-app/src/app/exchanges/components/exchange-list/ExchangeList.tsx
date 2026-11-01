import React from 'react';
import {useExchangeStyles} from "./ExchangeListStyles.tsx";
import ExchangeListTabs from "./exchange-list-tabs/ExchangeListTabs.tsx";
import ExchangeListHeader from "./exchange-list-header/ExchangeListHeader.tsx";
import ExchangeListInboxRoleTabs from "./exchange-list-inbox-role-tabs/ExchangeListInboxRoleTabs.tsx";
import ExchangeListSearchControls from "./exchange-list-search-controls/ExchangeListSearchControls.tsx";
import ExchangeListBody from "./exchange-list-body/ExchangeListBody.tsx";
import ExchangeListFooter from "./exchange-list-footer/ExchangeListFooter.tsx";
import {useIsMobile} from "../../../../utils/useMediaQuery.ts";
import {ExchangeListProps} from "./exchangeListTypes.ts";
import {useExchangeList} from "./useExchangeList.ts";
import {useExchangeSidebarCollapse} from "./useExchangeSidebarCollapse.ts";

export type {ExchangeTabCounts, InboxRole} from "./exchangeListTypes.ts";

const ExchangeList: React.FC<ExchangeListProps> = (props) =>
{
    const styles = useExchangeStyles();
    const isMobile = useIsMobile();
    const list = useExchangeList(props, isMobile);
    const sidebar = useExchangeSidebarCollapse(isMobile);
    const {filters, counts} = list;
    const isInboxMode = list.activeTab === 'inbox';
    const isSidebarVisuallyCollapsed = sidebar.isSidebarVisuallyCollapsed;

    return (
        <section
            id={"exchange-list-exchanges-list-container-collapsed"}
            className={isSidebarVisuallyCollapsed ? styles.exchangesListContainerCollapsed : styles.exchangesListContainer}
            onMouseEnter={sidebar.handleSidebarMouseEnter}
            onMouseLeave={sidebar.handleSidebarMouseLeave}>

            <ExchangeListTabs
                activeTab={list.activeTab}
                inboxCount={counts.requestsCount}
                onTabChange={list.handleTabChange}
                collapsed={isSidebarVisuallyCollapsed}
            />

            {!isSidebarVisuallyCollapsed && isInboxMode && (
                <ExchangeListHeader
                    id={"exchange-list-exchanges-list-header"}
                    elevated={list.hasListScrolled}>
                    <ExchangeListInboxRoleTabs
                        inboxRole={list.inboxRole}
                        incomingCount={counts.incomingCount}
                        outgoingCount={counts.outgoingCount}
                        onInboxRoleChange={list.handleInboxRoleChange}
                    />
                </ExchangeListHeader>
            )}

            {!isSidebarVisuallyCollapsed && !isInboxMode && (
                <ExchangeListHeader
                    id={"exchange-list-exchanges-list-header-2"}
                    elevated={list.hasListScrolled}>
                    <ExchangeListSearchControls
                        searchQuery={filters.searchQuery}
                        onSearchQueryChange={filters.handleSearchQueryChange}
                        onInitiatorFilterChange={filters.handleInitiatorFilterChange}
                        onSortChange={filters.handleSortChange}
                        selectedInitiator={filters.selectedInitiator}
                        sortBy={filters.sortBy}
                        sortDirection={filters.sortDirection}
                        onClearFilters={filters.handleClearFilters}
                        hasActiveFilters={filters.hasActiveFilters}
                    />
                </ExchangeListHeader>
            )}

            {!isSidebarVisuallyCollapsed && (
                <ExchangeListBody
                    exchanges={list.exchanges}
                    selectedItems={list.selectedItems}
                    activeTab={list.activeTab}
                    inboxRole={list.inboxRole}
                    loadingExchanges={list.loadingExchanges}
                    showLoadingState={list.showLoadingState}
                    onSelectionChange={list.handleSelectionChange}
                    onScroll={list.handleListScroll}
                />
            )}

            <ExchangeListFooter
                elevated={list.hasListScrolled}
                isMobile={isMobile}
                isSidebarCollapsed={sidebar.isSidebarCollapsed}
                isSidebarVisuallyCollapsed={isSidebarVisuallyCollapsed}
                toggleSidebar={sidebar.toggleSidebar}
                currentPage={filters.currentPage}
                totalPages={filters.totalPages}
                onPageChange={filters.handlePageChange}
                showClearFilters={filters.hasActiveFilters && !isInboxMode}
                onClearFilters={filters.handleClearFilters}
            />
        </section>
    );
};

export default ExchangeList;
