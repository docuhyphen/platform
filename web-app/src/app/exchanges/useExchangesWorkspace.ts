import {useMemo} from 'react';
import {useAuth} from '../../context/AuthContext.tsx';
import {useIsMobile} from '../../utils/useMediaQuery.ts';
import {usePlanFeature} from '../../hooks/subscription/usePlanFeature.ts';
import {useDelayedLoading} from '../../hooks/useDelayedLoading.ts';
import {Capability, PlanFeature} from '../models/models.tsx';
import {useExchangeInformationRequests} from '../information-requests/exchange-tab/useExchangeInformationRequests.ts';
import {ExchangePermissions, getPermissions} from './ExchangePermissions.ts';
import {useExchangeWorkspaceState} from './useExchangeWorkspaceState.ts';
import {useExchangeDialogsState} from './components/exchange-dialog-group/useExchangeDialogsState.ts';
import {DispatchToast, useExchangeUnavailableNotice} from './components/exchange-unavailable-toast/useExchangeUnavailableNotice.ts';
import {usePreviewerPageLink} from './usePreviewerPageLink.ts';
import {useExchangeDetailsTabAvailability} from './useExchangeDetailsTabAvailability.ts';
import {useExchangeSelectionNavigation} from './useExchangeSelectionNavigation.ts';
import {useAppUserHasExchanges} from './useAppUserHasExchanges.ts';
import {useExchangeDocumentHandlers} from './useExchangeDocumentHandlers.ts';
import {useExchangeDetailsLoading} from './useExchangeDetailsLoading.ts';
import {useExchangeListTabSync} from './useExchangeListTabSync.ts';
import {useExchangeRealtimeRefresh} from './useExchangeRealtimeRefresh.ts';
import {useExchangeLifecycleHandlers} from './useExchangeLifecycleHandlers.ts';

export const useExchangesWorkspace = (dispatchToast: DispatchToast) =>
{
    const isMobile = useIsMobile();
    const {appUser, currentSession, hasCapability} = useAuth();
    const sessionReady = currentSession != null;
    const canViewExchangeAudit = hasCapability(Capability.ORG_AUDIT_READ);
    const canViewBusinessFields = usePlanFeature(PlanFeature.BUSINESS_FIELDS_AND_SCHEMAS).isDiscoverable;
    const canViewWorkflows = usePlanFeature(PlanFeature.WORKFLOW_AUTOMATION).isDiscoverable;
    const state = useExchangeWorkspaceState();
    const dialogs = useExchangeDialogsState();
    const informationRequests = useExchangeInformationRequests(state.exchangeDetails?.id, state.exchangeDetails?.status);
    const previewerLink = usePreviewerPageLink(state.selectedExchangeDocument, state.selectedSidebarExchangeDocument);
    const permissions = useMemo<ExchangePermissions>(
        () => getPermissions(state.exchangeDetails, appUser),
        [state.exchangeDetails, appUser],
    );
    const autoPreviewDocuments = appUser?.settings?.autoPreviewDocuments ?? false;
    const userPreferencesLoaded = appUser?.settings !== undefined;
    const hasSelectedDetails = !!state.selectedExchangeId &&
        userPreferencesLoaded &&
        state.exchangeDetails?.id === state.selectedExchangeId &&
        state.resolvedDocumentLayoutKey === `${state.selectedExchangeId}:${autoPreviewDocuments}`;
    const hasDetailsLoadError = !!state.selectedExchangeId && state.detailsLoadErrorId === state.selectedExchangeId;
    const showInitialDetailsSkeleton = useDelayedLoading(
        !isMobile && state.isExchangeListLoading && !state.selectedExchangeId,
    );
    const showSelectedDetailsSkeleton = useDelayedLoading(
        !!state.selectedExchangeId && !hasSelectedDetails && !hasDetailsLoadError,
    );
    const notifyExchangeUnavailable = useExchangeUnavailableNotice(
        dispatchToast,
        () => state.setActiveListTab('active'),
    );

    useExchangeDetailsTabAvailability(
        state,
        {canViewBusinessFields, canViewWorkflows, canViewExchangeAudit},
        informationRequests,
    );
    const {changeSelectedExchangeId} = useExchangeSelectionNavigation(state, hasDetailsLoadError);
    const {preparingExchanges, appUserHasExchanges} = useAppUserHasExchanges(appUser, sessionReady);

    const clearUnavailableExchangeContext = (exchangeId?: string | null) =>
    {
        notifyExchangeUnavailable(exchangeId);
        state.deepLinkedExchangeIdRef.current = null;
        state.deepLinkedDocumentIdRef.current = null;
        state.deepLinkedDocumentExchangeIdRef.current = null;
        state.setSelectedExchangeDocument(undefined);
        changeSelectedExchangeId(null);
        state.setExchangeDetails(null);
        state.setDocumentSearchQuery("");
        state.setFilteredDocuments([]);
    };

    const documentHandlers = useExchangeDocumentHandlers(state, dialogs, autoPreviewDocuments);
    useExchangeDetailsLoading(state, {
        sessionReady,
        autoPreviewDocuments,
        userPreferencesLoaded,
        clearUnavailableExchangeContext,
    });
    useExchangeListTabSync(state, appUser?.id);
    useExchangeRealtimeRefresh(state, autoPreviewDocuments, clearUnavailableExchangeContext);
    const lifecycleHandlers = useExchangeLifecycleHandlers(state, changeSelectedExchangeId);

    return {
        ...state,
        ...documentHandlers,
        ...lifecycleHandlers,
        dialogs,
        previewerLink,
        permissions,
        informationRequests,
        isMobile,
        appUserId: appUser?.id,
        canViewExchangeAudit,
        canViewBusinessFields,
        canViewWorkflows,
        hasSelectedDetails,
        hasDetailsLoadError,
        showInitialDetailsSkeleton,
        showSelectedDetailsSkeleton,
        preparingExchanges,
        appUserHasExchanges,
        changeSelectedExchangeId,
    };
};

export type ExchangesWorkspace = ReturnType<typeof useExchangesWorkspace>;
