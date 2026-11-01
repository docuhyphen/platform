import React from 'react';
import {List, ListProps} from "@fluentui/react-components";
import {ExchangeListEntry} from "../../../../models/models.tsx";
import ExchangeListSkeleton from "../exchange-list-skeleton/ExchangeListSkeleton.tsx";
import ExchangeListItem from "../exchange-list-item/ExchangeListItem.tsx";
import ExchangeListEmptyState from "../exchange-list-empty-state/ExchangeListEmptyState.tsx";
import {ExchangeListTab} from "../exchange-list-tabs/ExchangeListTabs.tsx";
import {InboxRole} from "../exchangeListTypes.ts";
import {useExchangeListBodyStyles} from "./ExchangeListBodyStyles.tsx";

interface ExchangeListBodyProps
{
    exchanges: ExchangeListEntry[];
    selectedItems: string[];
    activeTab: ExchangeListTab;
    inboxRole: InboxRole;
    loadingExchanges: boolean;
    showLoadingState: boolean;
    onSelectionChange: NonNullable<ListProps["onSelectionChange"]>;
    onScroll: React.UIEventHandler<HTMLElement>;
}

const ExchangeListBody: React.FC<ExchangeListBodyProps> = (
    {
        exchanges,
        selectedItems,
        activeTab,
        inboxRole,
        loadingExchanges,
        showLoadingState,
        onSelectionChange,
        onScroll
    }) =>
{
    const styles = useExchangeListBodyStyles();

    return (
        <List
            id="exchange-list-items"
            className={styles.list}
            selectionMode="single"
            navigationMode="items"
            selectedItems={selectedItems}
            onSelectionChange={onSelectionChange}
            onScroll={onScroll}>

            {showLoadingState && exchanges.length === 0 && <ExchangeListSkeleton count={10}/>}

            {exchanges.map((exchange: ExchangeListEntry, index: number) => (
                <ExchangeListItem
                    key={exchange.id}
                    exchange={exchange}
                    isSelected={selectedItems.includes(exchange.id)}
                    activeTab={activeTab}
                    staggerIndex={index}
                />
            ))}

            {!loadingExchanges && exchanges.length === 0 && (
                <ExchangeListEmptyState
                    activeTab={activeTab}
                    inboxRole={inboxRole}/>
            )}
        </List>
    );
};

export default ExchangeListBody;
