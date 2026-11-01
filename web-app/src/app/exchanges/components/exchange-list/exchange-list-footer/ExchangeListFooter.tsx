import React from 'react';
import {Button, mergeClasses} from "@fluentui/react-components";
import ExchangeListSidebarToggle from "../exchange-list-sidebar-toggle/ExchangeListSidebarToggle.tsx";
import ExchangeListPagination from "../exchange-list-pagination/ExchangeListPagination.tsx";
import {useExchangeListFooterStyles} from "./ExchangeListFooterStyles.tsx";

interface ExchangeListFooterProps
{
    elevated: boolean;
    isMobile: boolean;
    isSidebarCollapsed: boolean;
    isSidebarVisuallyCollapsed: boolean;
    toggleSidebar: () => void;
    currentPage: number;
    totalPages: number;
    onPageChange: (page: number) => void;
    showClearFilters: boolean;
    onClearFilters: () => void;
}

const ExchangeListFooter: React.FC<ExchangeListFooterProps> = (
    {
        elevated,
        isMobile,
        isSidebarCollapsed,
        isSidebarVisuallyCollapsed,
        toggleSidebar,
        currentPage,
        totalPages,
        onPageChange,
        showClearFilters,
        onClearFilters
    }) =>
{
    const styles = useExchangeListFooterStyles();

    return (
        <div id={"exchange-list-exchanges-list-footer"}
             className={mergeClasses(styles.footer, elevated && styles.footerElevated)}>
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
                        onPageChange={onPageChange}/>

                    {showClearFilters && (
                        <Button
                            id="exchange-list-clear-filters"
                            size="small"
                            appearance="subtle"
                            shape={"circular"}
                            onClick={onClearFilters}>
                            Clear filters
                        </Button>
                    )}
                </>
            )}
        </div>
    );
};

export default ExchangeListFooter;
