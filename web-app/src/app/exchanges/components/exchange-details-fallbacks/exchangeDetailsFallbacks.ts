import {ExchangeDetailedDto, ExchangeListEntry} from '../../../models/models.tsx';
import {ExchangeListTab} from '../exchange-list/exchange-list-tabs/ExchangeListTabs.tsx';
import {InboxRole} from '../exchange-list/ExchangeList.tsx';

export interface ExchangeDetailsFallbackInputs
{
    activeListTab: ExchangeListTab;
    inboxRole: InboxRole;
    exchangeList: ExchangeListEntry[];
    isExchangeListLoading: boolean;
    fetchingDetails: boolean;
    hasSelectedDetails: boolean;
    selectedExchangeId: string | null;
    exchangeDetails: ExchangeDetailedDto | null;
}

export interface ExchangeDetailsFallbackFlags
{
    showIncomingInboxEmpty: boolean;
    showOutgoingInboxEmpty: boolean;
    showActiveEmpty: boolean;
    showArchiveEmpty: boolean;
    showSelectionHint: boolean;
}

export const resolveExchangeDetailsFallbacks = (inputs: ExchangeDetailsFallbackInputs): ExchangeDetailsFallbackFlags =>
{
    const {
        activeListTab,
        inboxRole,
        exchangeList,
        isExchangeListLoading,
        fetchingDetails,
        hasSelectedDetails,
        selectedExchangeId,
        exchangeDetails,
    } = inputs;
    const isInboxTab = activeListTab === 'inbox';
    const isActiveTab = activeListTab === 'active';
    const isArchiveTab = activeListTab === 'archive';
    const isCurrentTabEmpty = exchangeList.length === 0;
    const isIdle = !isExchangeListLoading && !fetchingDetails && !hasSelectedDetails;
    const showIncomingInboxEmpty = isIdle && isInboxTab && inboxRole === 'incoming' && isCurrentTabEmpty;
    const showOutgoingInboxEmpty = isIdle && isInboxTab && inboxRole === 'outgoing' && isCurrentTabEmpty;
    const showActiveEmpty = isIdle && isActiveTab && isCurrentTabEmpty;
    const showArchiveEmpty = isIdle && isArchiveTab && isCurrentTabEmpty;
    const showSelectionHint =
        !isExchangeListLoading &&
        !fetchingDetails &&
        !selectedExchangeId &&
        !exchangeDetails &&
        !showIncomingInboxEmpty &&
        !showOutgoingInboxEmpty &&
        !showActiveEmpty &&
        !showArchiveEmpty;

    return {showIncomingInboxEmpty, showOutgoingInboxEmpty, showActiveEmpty, showArchiveEmpty, showSelectionHint};
};
