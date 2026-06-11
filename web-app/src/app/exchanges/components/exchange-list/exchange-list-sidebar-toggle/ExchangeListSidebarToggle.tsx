import React from 'react';
import {Button, Tooltip} from "@fluentui/react-components";
import {CollapseExchangeListSidebarIcon, ExpandExchangeListSidebarIcon} from "../../../../components/IconBundles.tsx";

interface ExchangeListSidebarToggleProps
{
    isSidebarCollapsed: boolean;
    toggleSidebar: () => void;
}

const ExchangeListSidebarToggle: React.FC<ExchangeListSidebarToggleProps> = (
    {
        isSidebarCollapsed,
        toggleSidebar
    }) =>
{
    return (
        <Tooltip
            content={isSidebarCollapsed ? "Expand sidebar" : "Collapse sidebar"}
            relationship={"description"}>
            <Button
                id="exchange-list-sidebar-toggle"
                size="small"
                appearance="subtle"
                aria-label={isSidebarCollapsed ? "Expand sidebar" : "Collapse sidebar"}
                icon={isSidebarCollapsed ? <ExpandExchangeListSidebarIcon/> : <CollapseExchangeListSidebarIcon/>}
                onClick={toggleSidebar}
            />
        </Tooltip>
    );
};

export default ExchangeListSidebarToggle;