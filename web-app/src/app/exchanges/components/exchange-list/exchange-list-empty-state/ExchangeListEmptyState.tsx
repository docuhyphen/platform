import React from 'react';
import {Text} from "@fluentui/react-components";
import {ExchangeListTab} from "../exchange-list-tabs/ExchangeListTabs.tsx";
import {InboxRole} from "../exchangeListTypes.ts";
import {useExchangeListEmptyStateStyles} from "./ExchangeListEmptyStateStyles.tsx";

interface ExchangeListEmptyStateProps
{
    activeTab: ExchangeListTab;
    inboxRole: InboxRole;
}

const getEmptyStateMessage = (activeTab: ExchangeListTab, inboxRole: InboxRole): string =>
{
    if (activeTab !== 'inbox') return 'No Exchanges match your search. Try adjusting your filters!';

    return inboxRole === 'incoming'
        ? 'No incoming requests are waiting for your decision.'
        : 'No outgoing requests are awaiting recipient response.';
};

const ExchangeListEmptyState: React.FC<ExchangeListEmptyStateProps> = ({activeTab, inboxRole}) =>
{
    const styles = useExchangeListEmptyStateStyles();

    return (
        <div id={"exchange-list-empty-state"}
             className={styles.emptyState}>
            <Text id={"exchange-list-filters-text"}
                  align={"center"}>
                {getEmptyStateMessage(activeTab, inboxRole)}
            </Text>
        </div>
    );
};

export default ExchangeListEmptyState;
