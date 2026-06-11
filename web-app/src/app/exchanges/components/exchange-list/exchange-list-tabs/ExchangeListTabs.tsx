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

export type ExchangeListTab = 'inbox' | 'active' | 'archive';

interface ExchangeListTabsProps
{
    activeTab: ExchangeListTab;
    inboxCount: number;
    onTabChange: (tab: ExchangeListTab) => void;
    collapsed?: boolean;
}

const ExchangeListTabs: React.FC<ExchangeListTabsProps> = ({activeTab, inboxCount, onTabChange, collapsed = false}) =>
{
    const collapsedIconStyle = collapsed ? {fontSize: 40, lineHeight: 1} : undefined;
    const tabContentStyle = collapsed
        ? {display: 'flex', alignItems: 'center', justifyContent: 'center', width: '100%'} as const
        : {display: 'flex', alignItems: 'center', gap: '6px'} as const;

    return (
        <TabList
            id="exchange-list-tabs-root"
            selectedValue={activeTab}
            onTabSelect={(_, data) => onTabChange(data.value as ExchangeListTab)}
            size="small"
            vertical={collapsed}
            style={{padding: collapsed ? '8px 4px' : '0 8px', width: '100%'}}>
            <Tab id="exchange-list-tab-requests" value="inbox">
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
            <Tab id="exchange-list-tab-active" value="active">
                <span style={tabContentStyle}>
                    {activeTab === 'active'
                        ? <Folder20Filled style={collapsedIconStyle} primaryFill={tokens.colorBrandForeground1}/>
                        : <Folder20Regular style={collapsedIconStyle}/>
                    }
                    {!collapsed && 'Active'}
                </span>
            </Tab>
            <Tab id="exchange-list-tab-archive" value="archive">
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

export default ExchangeListTabs;
