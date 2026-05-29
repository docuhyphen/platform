import React from 'react';
import {Button, Tooltip} from "@fluentui/react-components";
import {CollapseSessionListSidebarIcon, ExpandSessionListSidebarIcon} from "../../../../components/IconBundles.tsx";

interface SessionListSidebarToggleProps
{
    isSidebarCollapsed: boolean;
    toggleSidebar: () => void;
}

const SessionListSidebarToggle: React.FC<SessionListSidebarToggleProps> = (
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
                id="session-list-sidebar-toggle"
                size="small"
                appearance="subtle"
                aria-label={isSidebarCollapsed ? "Expand sidebar" : "Collapse sidebar"}
                icon={isSidebarCollapsed ? <ExpandSessionListSidebarIcon/> : <CollapseSessionListSidebarIcon/>}
                onClick={toggleSidebar}
            />
        </Tooltip>
    );
};

export default SessionListSidebarToggle;