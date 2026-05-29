import React from 'react';
import {CounterBadge, Tab, TabList, tokens} from "@fluentui/react-components";
import {
    Archive20Filled,
    Archive20Regular,
    ClipboardTaskListLtr20Filled,
    ClipboardTaskListLtr20Regular,
    Folder20Filled,
    Folder20Regular,
} from "@fluentui/react-icons";

export type SessionListTab = 'inbox' | 'active' | 'archive';

interface SessionListTabsProps
{
    activeTab: SessionListTab;
    inboxCount: number;
    onTabChange: (tab: SessionListTab) => void;
    collapsed?: boolean;
}

const SessionListTabs: React.FC<SessionListTabsProps> = ({activeTab, inboxCount, onTabChange, collapsed = false}) =>
{
    const collapsedIconStyle = collapsed ? {fontSize: 40, lineHeight: 1} : undefined;
    const tabContentStyle = collapsed
        ? {display: 'flex', alignItems: 'center', justifyContent: 'center', width: '100%'} as const
        : {display: 'flex', alignItems: 'center', gap: '6px'} as const;

    return (
        <TabList
            id="session-list-tabs-root"
            selectedValue={activeTab}
            onTabSelect={(_, data) => onTabChange(data.value as SessionListTab)}
            size="small"
            vertical={collapsed}
            style={{padding: collapsed ? '8px 4px' : '0 8px', width: '100%'}}>
            <Tab id="session-list-tab-requests" value="inbox">
                <span style={tabContentStyle}>
                    {activeTab === 'inbox'
                        ? <ClipboardTaskListLtr20Filled style={collapsedIconStyle} primaryFill={tokens.colorBrandForeground1}/>
                        : <ClipboardTaskListLtr20Regular style={collapsedIconStyle}/>
                    }
                    {!collapsed && 'Requests'}
                    {inboxCount > 0 && (
                        <CounterBadge
                            count={inboxCount}
                            size="small"
                            appearance="filled"
                            color="danger"
                        />
                    )}
                </span>
            </Tab>
            <Tab id="session-list-tab-active" value="active">
                <span style={tabContentStyle}>
                    {activeTab === 'active'
                        ? <Folder20Filled style={collapsedIconStyle} primaryFill={tokens.colorBrandForeground1}/>
                        : <Folder20Regular style={collapsedIconStyle}/>
                    }
                    {!collapsed && 'Active'}
                </span>
            </Tab>
            <Tab id="session-list-tab-archive" value="archive">
                <span style={tabContentStyle}>
                    {activeTab === 'archive'
                        ? <Archive20Filled style={collapsedIconStyle} primaryFill={tokens.colorBrandForeground1}/>
                        : <Archive20Regular style={collapsedIconStyle}/>
                    }
                    {!collapsed && 'Archive'}
                </span>
            </Tab>
        </TabList>
    );
};

export default SessionListTabs;
