import {ExchangeTabCounts} from '../exchange-list/ExchangeList.tsx';
import {ExchangeListTab} from '../exchange-list/exchange-list-tabs/ExchangeListTabs.tsx';

export interface ExchangeEmptyDetailsProps
{
    tabCounts: ExchangeTabCounts;
    onOpenTab: (tab: ExchangeListTab) => void;
}
