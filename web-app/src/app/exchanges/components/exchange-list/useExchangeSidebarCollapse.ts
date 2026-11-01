import {useEffect, useState} from 'react';

const SIDEBAR_COLLAPSED_STORAGE_KEY = 'exchanges.sidebar.isCollapsed';

export const useExchangeSidebarCollapse = (isMobile: boolean) =>
{
    const [isSidebarCollapsed, setIsSidebarCollapsed] = useState(() =>
    {
        if (typeof window === 'undefined') return false;
        return window.localStorage.getItem(SIDEBAR_COLLAPSED_STORAGE_KEY) === 'true';
    });
    const [isSidebarHoverExpanded, setIsSidebarHoverExpanded] = useState(false);

    useEffect(() =>
    {
        if (typeof window === 'undefined') return;
        window.localStorage.setItem(SIDEBAR_COLLAPSED_STORAGE_KEY, String(isSidebarCollapsed));
    }, [isSidebarCollapsed]);

    const toggleSidebar = () =>
    {
        setIsSidebarCollapsed(!isSidebarCollapsed);
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

    return {
        isSidebarCollapsed,
        isSidebarVisuallyCollapsed: !isMobile && isSidebarCollapsed && !isSidebarHoverExpanded,
        toggleSidebar,
        handleSidebarMouseEnter,
        handleSidebarMouseLeave,
    };
};
