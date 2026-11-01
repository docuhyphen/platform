import React from 'react';
import {CounterBadge, Tab, TabList, tokens} from "@fluentui/react-components";
import {
    Archive20Filled,
    Archive20Regular,
    MailInbox20Regular,
    MailInbox20Filled,
    Live20Filled,
    Live20Regular,
} from "@fluentui/react-icons";
import {useExchangeListTabsStyles} from "./ExchangeListTabsStyles.tsx";
import TrustedParticipantInvitations from "../trusted-participant-invitations/TrustedParticipantInvitations.tsx";

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
    const styles = useExchangeListTabsStyles();
    const [trustedInvitationCount, setTrustedInvitationCount] = React.useState(0);
    const totalInboxCount = inboxCount + trustedInvitationCount;
    const tabContentClassName = collapsed ? styles.tabContentCollapsed : styles.tabContent;
    const collapsedIconClassName = collapsed ? styles.collapsedIcon : undefined;

    return (
        <>
            <TabList
                id="exchange-list-tabs-root"
                selectedValue={activeTab}
                onTabSelect={(_, data) => onTabChange(data.value as ExchangeListTab)}
                size="small"
                vertical={collapsed}
                className={collapsed ? styles.tabListCollapsed : styles.tabList}>
                <Tab
                    id="exchange-list-tab-requests"
                    value="inbox">
                    <span id={"exchange-list-tabs-span-1"}
                          className={tabContentClassName}>
                        {collapsed ? (
                            <span id={"exchange-list-tabs-span-2"}
                                  className={styles.collapsedIconWrapper}>
                                {activeTab === 'inbox'
                                    ? (
                                        <MailInbox20Filled
                                            className={collapsedIconClassName}
                                            primaryFill={tokens.colorBrandForeground1}
                                        />
                                    )
                                    : <MailInbox20Regular className={collapsedIconClassName}/>
                                }
                                {totalInboxCount > 0 && (
                                    <CounterBadge
                                        id={"exchange-list-tabs-counter-badge-1"}
                                        dot
                                        size="medium"
                                        appearance="filled"
                                        color="danger"
                                        className={styles.collapsedBadge}
                                    />
                                )}
                            </span>
                        ) : (
                            <>
                                {activeTab === 'inbox'
                                    ? <MailInbox20Filled primaryFill={tokens.colorBrandForeground1}/>
                                    : <MailInbox20Regular/>
                                }
                                Requests
                                {totalInboxCount > 0 && (
                                    <CounterBadge
                                        id={"exchange-list-tabs-counter-badge-2"}
                                        count={totalInboxCount}
                                        size="small"
                                        appearance="filled"
                                        color="danger"
                                    />
                                )}
                            </>
                        )}
                    </span>
                </Tab>
                <Tab
                    id="exchange-list-tab-active"
                    value="active">
                    <span id={"exchange-list-tabs-span-3"}
                          className={tabContentClassName}>
                        {activeTab === 'active'
                            ? (
                                <Live20Filled
                                    className={collapsedIconClassName}
                                    primaryFill={tokens.colorBrandForeground1}
                                />
                            )
                            : <Live20Regular className={collapsedIconClassName}/>
                        }
                        {!collapsed && 'Active'}
                    </span>
                </Tab>
                <Tab
                    id="exchange-list-tab-archive"
                    value="archive">
                    <span id={"exchange-list-tabs-span-4"}
                          className={tabContentClassName}>
                        {activeTab === 'archive'
                            ? (
                                <Archive20Filled
                                    className={collapsedIconClassName}
                                    primaryFill={tokens.colorBrandForeground1}
                                />
                            )
                            : <Archive20Regular className={collapsedIconClassName}/>
                        }
                        {!collapsed && 'Archive'}
                    </span>
                </Tab>
            </TabList>
            {!collapsed && activeTab === "inbox" && (
                <TrustedParticipantInvitations onCountChange={setTrustedInvitationCount}/>
            )}
        </>
    );
};

export default ExchangeListTabs;
