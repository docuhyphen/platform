import {useMemo} from 'react';
import {useLocation} from 'react-router-dom';
import {ExchangeListTab} from './components/exchange-list/exchange-list-tabs/ExchangeListTabs';
import {InboxRole} from './components/exchange-list/ExchangeList';

export interface ExchangeRouteState
{
    listTab: ExchangeListTab | null;
    inboxRole: InboxRole | null;
    exchangeId: string | null;
    documentId: string | null;
}

const parseExchangeListTab = (value: string | null): ExchangeListTab | null =>
{
    if (value === 'inbox' || value === 'active' || value === 'archive')
    {
        return value;
    }
    return null;
};

const parseInboxRole = (value: string | null): InboxRole | null =>
{
    if (value === 'incoming' || value === 'outgoing')
    {
        return value;
    }
    return null;
};

export const parseExchangeRouteState = (search: string): ExchangeRouteState =>
{
    const params = new URLSearchParams(search);
    return {
        listTab: parseExchangeListTab(params.get('tab')),
        inboxRole: parseInboxRole(params.get('role')),
        exchangeId: params.get('s'),
        documentId: params.get('d'),
    };
};

export const useExchangeRouteState = (): ExchangeRouteState =>
{
    const {search} = useLocation();
    return useMemo(() => parseExchangeRouteState(search), [search]);
};
