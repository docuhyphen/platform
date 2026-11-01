import {ExchangeStatus} from "../../../models/models.tsx";
import {ExchangeListTab} from "./exchange-list-tabs/ExchangeListTabs.tsx";
import {InboxRole} from "./exchangeListTypes.ts";

export const getTabFilters = (tab: ExchangeListTab, inboxRole: InboxRole): { status: string; initiatedBy: boolean | null } =>
{
    switch (tab)
    {
        case 'inbox':
            return {status: 'INITIATED', initiatedBy: inboxRole === 'incoming' ? false : true};
        case 'active':
            return {status: 'ACCEPTED_STARTED', initiatedBy: null};
        case 'archive':
            return {status: 'ENDED,REJECTED,RESCINDED', initiatedBy: null};
    }
};

export const statusToTab = (status: ExchangeStatus | undefined): ExchangeListTab | null =>
{
    if (!status) return null;

    if (status === ExchangeStatus.INITIATED) return 'inbox';
    if (status === ExchangeStatus.ACCEPTED_STARTED) return 'active';
    if (
        status === ExchangeStatus.ENDED ||
        status === ExchangeStatus.REJECTED ||
        status === ExchangeStatus.RESCINDED
    ) return 'archive';

    return null;
};

export const isStatusInTab = (status: ExchangeStatus | undefined, tab: ExchangeListTab): boolean =>
    statusToTab(status) === tab;
