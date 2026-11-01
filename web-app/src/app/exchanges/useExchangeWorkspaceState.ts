import {useRef, useState} from 'react';
import {TabValue} from '@fluentui/react-components';
import {DocumentDetailedDto, ExchangeDetailedDto, ExchangeListEntry} from '../models/models.tsx';
import {ExchangeListTab} from './components/exchange-list/exchange-list-tabs/ExchangeListTabs.tsx';
import {ExchangeTabCounts, InboxRole} from './components/exchange-list/ExchangeList.tsx';

export type ExchangePaneNavigationDirection = "forward" | "back" | null;

export const useExchangeWorkspaceState = () =>
{
    const [exchangeList, setExchangeList] = useState<ExchangeListEntry[]>([]);
    const [activeListTab, setActiveListTab] = useState<ExchangeListTab>('active');
    const [inboxRole, setInboxRole] = useState<InboxRole>('incoming');
    const [tabCounts, setTabCounts] = useState<ExchangeTabCounts>({inbox: 0, active: 0, archive: 0});
    const [isExchangeListLoading, setIsExchangeListLoading] = useState<boolean>(true);
    const [selectedExchangeId, setSelectedExchangeId] = useState<string | null>(null);
    const [detailsTransitionAnimationSuppressedForId, setDetailsTransitionAnimationSuppressedForId] =
        useState<string | null>(null);
    const [paneNavigationDirection, setPaneNavigationDirection] =
        useState<ExchangePaneNavigationDirection>(null);
    const [routeSelectionVersion, setRouteSelectionVersion] = useState(0);
    const [resolvedDocumentLayoutKey, setResolvedDocumentLayoutKey] = useState<string | null>(null);
    const [exchangeDetails, setExchangeDetails] = useState<ExchangeDetailedDto | null>(null);
    const [fetchingDetails, setFetchingDetails] = useState<boolean>(true);
    const [detailsLoadErrorId, setDetailsLoadErrorId] = useState<string | null>(null);
    const [detailsActiveTab, setDetailsActiveTab] = useState<TabValue>('documents');
    const [isDocumentToolbarVisible, setIsDocumentToolbarVisible] = useState<boolean>(false);
    const [isDocumentSidebarOpen, setIsDocumentSidebarOpen] = useState(false);
    const [selectedExchangeDocument, setSelectedExchangeDocument] =
        useState<DocumentDetailedDto | undefined>(undefined);
    const [selectedUploadExchangeDocument, setSelectedUploadExchangeDocument] =
        useState<DocumentDetailedDto | undefined>(undefined);
    const [selectedSidebarExchangeDocument, setSelectedSidebarExchangeDocument] =
        useState<DocumentDetailedDto | undefined>(undefined);
    const [selectedUpdateExchangeDocument, setSelectedUpdateExchangeDocument] =
        useState<DocumentDetailedDto | undefined>(undefined);
    const [filteredDocuments, setFilteredDocuments] = useState<DocumentDetailedDto[]>([]);
    const [documentSearchQuery, setDocumentSearchQuery] = useState<string>("");
    const deepLinkedExchangeIdRef = useRef<string | null>(null);
    const deepLinkedDocumentIdRef = useRef<string | null>(null);
    const deepLinkedDocumentExchangeIdRef = useRef<string | null>(null);

    return {
        exchangeList,
        setExchangeList,
        activeListTab,
        setActiveListTab,
        inboxRole,
        setInboxRole,
        tabCounts,
        setTabCounts,
        isExchangeListLoading,
        setIsExchangeListLoading,
        selectedExchangeId,
        setSelectedExchangeId,
        detailsTransitionAnimationSuppressedForId,
        setDetailsTransitionAnimationSuppressedForId,
        paneNavigationDirection,
        setPaneNavigationDirection,
        routeSelectionVersion,
        setRouteSelectionVersion,
        resolvedDocumentLayoutKey,
        setResolvedDocumentLayoutKey,
        exchangeDetails,
        setExchangeDetails,
        fetchingDetails,
        setFetchingDetails,
        detailsLoadErrorId,
        setDetailsLoadErrorId,
        detailsActiveTab,
        setDetailsActiveTab,
        isDocumentToolbarVisible,
        setIsDocumentToolbarVisible,
        isDocumentSidebarOpen,
        setIsDocumentSidebarOpen,
        selectedExchangeDocument,
        setSelectedExchangeDocument,
        selectedUploadExchangeDocument,
        setSelectedUploadExchangeDocument,
        selectedSidebarExchangeDocument,
        setSelectedSidebarExchangeDocument,
        selectedUpdateExchangeDocument,
        setSelectedUpdateExchangeDocument,
        filteredDocuments,
        setFilteredDocuments,
        documentSearchQuery,
        setDocumentSearchQuery,
        deepLinkedExchangeIdRef,
        deepLinkedDocumentIdRef,
        deepLinkedDocumentExchangeIdRef,
    };
};

export type ExchangeWorkspaceState = ReturnType<typeof useExchangeWorkspaceState>;
