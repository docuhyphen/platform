import {ExchangeListEntry} from "../../../models/models.tsx";
import {ExchangeListTab} from "./exchange-list-tabs/ExchangeListTabs.tsx";

export interface ExchangeTabCounts
{
    inbox: number;
    active: number;
    archive: number;
}

export type InboxRole = 'incoming' | 'outgoing';

export interface ExchangeListProps
{
    onSelectionChange: (exchangeId: string | null) => void;
    onExchangeListChange?: (exchanges: ExchangeListEntry[]) => void;
    onTabChange?: (tab: ExchangeListTab) => void;
    onInboxRoleChange?: (role: InboxRole) => void;
    onTabCountsChange?: (counts: ExchangeTabCounts) => void;
    onLoadingChange?: (loading: boolean) => void;
    controlledSelectedId?: string | null;
    controlledActiveTab?: ExchangeListTab;
    controlledInboxRole?: InboxRole;
}
