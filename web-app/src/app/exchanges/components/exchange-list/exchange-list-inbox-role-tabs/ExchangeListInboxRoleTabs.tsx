import React from 'react';
import {CounterBadge, Tab, TabList} from "@fluentui/react-components";
import {InboxRole} from "../exchangeListTypes.ts";
import {useExchangeListInboxRoleTabsStyles} from "./ExchangeListInboxRoleTabsStyles.tsx";

interface ExchangeListInboxRoleTabsProps
{
    inboxRole: InboxRole;
    incomingCount: number;
    outgoingCount: number;
    onInboxRoleChange: (role: InboxRole) => void;
}

const ExchangeListInboxRoleTabs: React.FC<ExchangeListInboxRoleTabsProps> = (
    {
        inboxRole,
        incomingCount,
        outgoingCount,
        onInboxRoleChange
    }) =>
{
    const styles = useExchangeListInboxRoleTabsStyles();

    return (
        <TabList
            id="exchange-list-inbox-role-tabs"
            selectedValue={inboxRole}
            onTabSelect={(_, data) => onInboxRoleChange(data.value as InboxRole)}
            size="small"
            className={styles.tabList}>
            <Tab id="exchange-list-inbox-role-incoming"
                 value="incoming">
                <span id={"exchange-list-tab-content-flex"}
                      className={styles.tabContent}>
                    Incoming
                    {incomingCount > 0 &&
                        <CounterBadge id={"exchange-list-tab-content-flex-counter-badge"}
                                      count={incomingCount}
                                      size="small"
                                      appearance="filled"
                                      color="danger"/>}
                </span>
            </Tab>
            <Tab id="exchange-list-inbox-role-outgoing"
                 value="outgoing">
                <span id={"exchange-list-tab-content-flex-2"}
                      className={styles.tabContent}>
                    Outgoing
                    {outgoingCount > 0 &&
                        <CounterBadge id={"exchange-list-tab-content-flex-counter-badge-2"}
                                      count={outgoingCount}
                                      size="small"
                                      appearance="filled"/>}
                </span>
            </Tab>
        </TabList>
    );
};

export default ExchangeListInboxRoleTabs;
