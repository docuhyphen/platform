import React, {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {
    Button,
    InputOnChangeData,
    Link,
    mergeClasses,
    SearchBoxChangeEvent,
    Spinner,
    TabValue,
    Text,
    Toast,
    ToastBody,
    Toaster,
    ToastTitle,
    ToastTrigger,
    useId,
    useToastController
} from "@fluentui/react-components";
import {fetchSignedInUserAppUserExchange} from "../../services/exchangeApi.ts";
import useToken from "../../context/useToken.tsx";
import ExchangePreLoader from "./components/exchange-pre-loader/ExchangePreLoader.tsx";
import {
    Capability,
    DocumentDetailedDto,
    DocumentType,
    ExchangeBasicDto,
    ExchangeDetailedDto,
    ExchangeStatus,
    ImageType,
    PlanFeature,
} from "../models/models.tsx";
import {useExchangesStyles} from "./ExchangesStyles.tsx";
import ExchangeDocumentSidebar from "./components/exchange-document-sidebar/ExchangeDocumentSidebar.tsx";
import NoExchangeDocuments from "./components/exchange-documents-none/NoExchangeDocuments.tsx";
import ExchangeDialogsGroup from "./components/exchange-dialog-group/ExchangeDialogsGroup.tsx";
import ExchangeDetailsHeader from "./components/exchange-details-header/ExchangeDetailsHeader.tsx";
import ExchangeDetailsLoading from "./components/exchange-details-loading/ExchangeDetailsLoading.tsx";
import ExchangeDetailsError from "./components/exchange-details-error/ExchangeDetailsError.tsx";
import "react-pdf/dist/esm/Page/AnnotationLayer.css";
import "react-pdf/dist/esm/Page/TextLayer.css";
import ExchangeList, {ExchangeTabCounts, InboxRole} from "./components/exchange-list/ExchangeList.tsx";
import {useAuth} from "../../context/AuthContext.tsx";
import {ExchangePermissions, getPermissions} from "./ExchangePermissions.ts";
import ExchangeDocumentsList from "./components/exchange-document-list/ExchangeDocumentsList.tsx";
import {realtimeService} from "../../services/NotificationService";
import {
    exchangeDeletionObservable,
    exchangeInitiationObservable,
    publishExchangeDelete,
    publishExchangeUpdate,
    publishRecreateRejectedExchange
} from "../observable/exchangeObservables.ts";
import ExchangeDocumentPreviewer, {
    ExchangeDocumentPreviewerHandle
} from "./components/exchange-document-preview/ExchangeDocumentPreviewer.tsx";
import ExchangeAcceptanceDialog from "./components/exchange-acceptance-dialog/ExchangeAcceptanceDialog.tsx";
import EmptyStateIllustration from "./components/empty-state-illustration/EmptyStateIllustration.tsx";
import {ExchangeListTab} from "./components/exchange-list/exchange-list-tabs/ExchangeListTabs.tsx";
import {useIsMobile} from "../../utils/useMediaQuery.ts";
import ExchangeAuditTab from "./components/exchange-audit-tab/ExchangeAuditTab.tsx";
import ExchangeWorkflowTab from "./components/exchange-workflow-tab/ExchangeWorkflowTab.tsx";
import ExchangeInformationRequestsTab
    from "../information-requests/exchange-tab/exchange-information-requests-tab/ExchangeInformationRequestsTab.tsx";
import {useExchangeInformationRequests} from "../information-requests/exchange-tab/useExchangeInformationRequests.ts";
import ExchangeFieldsTab from "./components/exchange-fields-tab/ExchangeFieldsTab.tsx";
import ExchangeTabsHeader from "./components/exchange-tabs-header/ExchangeTabsHeader.tsx";
import {useExchangeRouteState} from './useExchangeRouteState';
import {hasExchangeWorkspaceContent} from "./exchangeWorkspaceAvailability.ts";
import {resolvePreviewDocumentSelection} from "./exchangeDocumentSelection.ts";
import {usePlanFeature} from "../../hooks/subscription/usePlanFeature.ts";
import {useDelayedLoading} from "../../hooks/useDelayedLoading.ts";

type ExchangePaneNavigationDirection = "forward" | "back" | null;

const isExchangeUnavailableError = (error: unknown): boolean =>
{
    const status = (error as { response?: { status?: number }, status?: number })?.response?.status
        ?? (error as { status?: number })?.status;
    return status === 403 || status === 404;
};

const Exchanges: React.FC = () =>
{
    const styles = useExchangesStyles();
    const exchangeRouteState = useExchangeRouteState();
    const isMobile = useIsMobile();
    const toasterId = useId("exchanges-toaster");
    const {dispatchToast} = useToastController(toasterId);
    const [exchangeList, setExchangeList] = useState<ExchangeBasicDto[]>([]);
    const [activeListTab, setActiveListTab] = useState<ExchangeListTab>('active');
    const [inboxRole, setInboxRole] = useState<InboxRole>('incoming');
    const [tabCounts, setTabCounts] = useState<ExchangeTabCounts>({inbox: 0, active: 0, archive: 0});
    const [selectedExchangeId, setSelectedExchangeId] = useState<string | null>(null);
    const [detailsTransitionAnimationSuppressedForId, setDetailsTransitionAnimationSuppressedForId] =
        useState<string | null>(null);
    const [paneNavigationDirection, setPaneNavigationDirection] =
        useState<ExchangePaneNavigationDirection>(null);
    const [preparingExchanges, setPreparingExchanges] = useState<boolean>(true);
    const token = useToken();
    const {appUser, currentSession, hasCapability} = useAuth();
    const sessionReady = currentSession != null;
    const canViewExchangeAudit = hasCapability(Capability.ORG_AUDIT_READ);
    const canViewBusinessFields = usePlanFeature(PlanFeature.BUSINESS_FIELDS_AND_SCHEMAS).isDiscoverable;
    const canViewWorkflows = usePlanFeature(PlanFeature.WORKFLOW_AUTOMATION).isDiscoverable;
    const [isDocumentSidebarOpen, setIsDocumentSidebarOpen] = React.useState(false);
    const [isDocumentAddDialogOpen, setIsDocumentAddDialogOpen] = React.useState(false);
    const [isUploadDocumentDialogOpen, setIsUploadDocumentDialogOpen] = React.useState(false);
    const [isUpdateDocumentDialogOpen, setIsUpdateDocumentDialogOpen] = React.useState(false);
    const [isDocumentZipDialogOpen, setIsDocumentZipDialogOpen] = React.useState(false);
    const [isExchangeEditDialogOpen, setIsExchangeEditDialogOpen] = React.useState(false);
    const [isExchangeDetailedViewDialogOpen, setIsExchangeDetailedViewDialogOpen] = React.useState(false);
    const [isExchangeAccessManagementDialogOpen, setIsExchangeAccessManagementDialogOpen] = React.useState(false);
    const [isDeletedExchangeDialogOpen, setIsDeletedExchangeDialogOpen] = React.useState(false);
    const [isExchangeEndDialogOpen, setIsExchangeEndDialogOpen] = React.useState(false);
    const [isExchangeRescindDialogOpen, setIsExchangeRescindDialogOpen] = React.useState(false);
    const [selectedExchangeDocument, setSelectedExchangeDocument] = React.useState<DocumentDetailedDto | undefined>(undefined);
    const [selectedUploadExchangeDocument, setSelectedUploadExchangeDocument] = React.useState<DocumentDetailedDto | undefined>(undefined);
    const [selectedSidebarExchangeDocument, setSelectedSidebarExchangeDocument] = React.useState<DocumentDetailedDto | undefined>(undefined);
    const [selectedUpdateExchangeDocument, setSelectedUpdateExchangeDocument] = React.useState<DocumentDetailedDto>(undefined);
    const [exchangeDetails, setExchangeDetails] = useState<ExchangeDetailedDto | null>(null);
    const informationRequests = useExchangeInformationRequests(exchangeDetails?.id, exchangeDetails?.status);
    const [fetchingDetails, setFetchingDetails] = useState<boolean>(true);
    const [detailsLoadErrorId, setDetailsLoadErrorId] = useState<string | null>(null);
    const [isExchangeListLoading, setIsExchangeListLoading] = useState<boolean>(true);
    const [filteredDocuments, setFilteredDocuments] = useState<DocumentDetailedDto[]>([]);
    const [documentSearchQuery, setDocumentSearchQuery] = useState<string>("");
    const [appUserHasExchanges, setAppUserHasExchanges] = useState<boolean>(false);
    const [detailsActiveTab, setDetailsActiveTab] = useState<TabValue>('documents');
    const [isDocumentToolbarVisible, setIsDocumentToolbarVisible] = useState<boolean>(false);
    const [routeSelectionVersion, setRouteSelectionVersion] = useState(0);
    const [resolvedDocumentLayoutKey, setResolvedDocumentLayoutKey] = useState<string | null>(null);
    const documentPreviewerRef = useRef<ExchangeDocumentPreviewerHandle>(null);
    const [previewerCurrentPage, setPreviewerCurrentPage] = useState<number | undefined>(undefined);
    const canNavigatePreviewerToPage = Boolean(
        selectedExchangeDocument &&
        selectedExchangeDocument.id === selectedSidebarExchangeDocument?.id
    );
    const sidebarLinkablePageNumber = canNavigatePreviewerToPage ? previewerCurrentPage : undefined;
    const handleNavigatePreviewerToPage = useCallback((pageNumber: number) =>
    {
        documentPreviewerRef.current?.goToPage(pageNumber);
    }, []);
    const permissions = useMemo<ExchangePermissions>(
        () => getPermissions(exchangeDetails, appUser),
        [exchangeDetails, appUser],
    );
    const autoPreviewDocuments = appUser?.settings?.autoPreviewDocuments ?? false;
    const userPreferencesLoaded = appUser?.settings !== undefined;
    const hasSelectedDetails = !!selectedExchangeId &&
        userPreferencesLoaded &&
        exchangeDetails?.id === selectedExchangeId &&
        resolvedDocumentLayoutKey === `${selectedExchangeId}:${autoPreviewDocuments}`;
    const hasDetailsLoadError = !!selectedExchangeId && detailsLoadErrorId === selectedExchangeId;
    const showInitialDetailsSkeleton = useDelayedLoading(
        !isMobile && isExchangeListLoading && !selectedExchangeId,
    );
    const showSelectedDetailsSkeleton = useDelayedLoading(
        !!selectedExchangeId && !hasSelectedDetails && !hasDetailsLoadError,
    );

    const tokenRef = useRef(token);
    tokenRef.current = token;
    const deepLinkedExchangeIdRef = useRef<string | null>(null);
    const deepLinkedDocumentIdRef = useRef<string | null>(null);
    const deepLinkedDocumentExchangeIdRef = useRef<string | null>(null);
    const lastUnavailableExchangeIdRef = useRef<string | null>(null);

    const notifyExchangeUnavailable = (exchangeId?: string | null) =>
    {
        const normalizedExchangeId = exchangeId ?? '__unknown__';
        if (lastUnavailableExchangeIdRef.current === normalizedExchangeId)
        {
            return;
        }

        lastUnavailableExchangeIdRef.current = normalizedExchangeId;
        dispatchToast(
            <Toast id={"exchanges-toast"}>
                <ToastTitle id={"exchanges-this-exchange-is-no-toast-title"}
                            action={
                                <ToastTrigger>
                                    <Link id={"exchanges-dismiss-link"}>Dismiss</Link>
                                </ToastTrigger>
                            }>
                    This Exchange is no longer available.
                </ToastTitle>
                <ToastBody id={"exchanges-go-to-active-tab-toast-body"}>
                    <Link id={"exchanges-go-to-active-tab-link"}
                          onClick={() => setActiveListTab('active')}>Go to Active tab</Link>
                </ToastBody>
            </Toast>,
            {intent: 'warning', timeout: 7000}
        );
    };

    const changeSelectedExchangeId = (exchangeId: string | null) =>
    {
        if (exchangeId && isExchangeListLoading && !selectedExchangeId)
        {
            setDetailsTransitionAnimationSuppressedForId(exchangeId);
        }
        else if (!exchangeId || exchangeId !== detailsTransitionAnimationSuppressedForId)
        {
            setDetailsTransitionAnimationSuppressedForId(null);
        }

        setPaneNavigationDirection(exchangeId ? "forward" : "back");
        if (exchangeId !== selectedExchangeId) {
            setResolvedDocumentLayoutKey(null);
        } else if (hasDetailsLoadError) {
            setRouteSelectionVersion(version => version + 1);
        }
        setSelectedExchangeId(exchangeId);
    };

    const clearUnavailableExchangeContext = (exchangeId?: string | null) =>
    {
        notifyExchangeUnavailable(exchangeId);
        deepLinkedExchangeIdRef.current = null;
        deepLinkedDocumentIdRef.current = null;
        deepLinkedDocumentExchangeIdRef.current = null;
        setSelectedExchangeDocument(undefined);
        changeSelectedExchangeId(null);
        setExchangeDetails(null);
        setDocumentSearchQuery("");
        setFilteredDocuments([]);
    };

    useEffect(() =>
    {
        if (
            (detailsActiveTab === "details" && !canViewBusinessFields)
            || (detailsActiveTab === "workflow" && !canViewWorkflows)
            || (detailsActiveTab === "audit" && !canViewExchangeAudit)
            || (detailsActiveTab === "information-requests" && !informationRequests.loading && !informationRequests.visible)
        )
        {
            setDetailsActiveTab("documents");
        }
    }, [canViewBusinessFields, canViewExchangeAudit, canViewWorkflows, detailsActiveTab, informationRequests.loading, informationRequests.visible]);

    const checkAppUserExchanges = React.useCallback(async () =>
    {
        try
        {
            const hasWorkspaceContent = await hasExchangeWorkspaceContent(tokenRef.current);
            setAppUserHasExchanges(hasWorkspaceContent);
        }
        catch (error)
        {
            console.error("Could not determine whether user has exchanges:", error);
            setAppUserHasExchanges(false);
        }
        finally
        {
            setPreparingExchanges(false);
        }
    }, []);

    useEffect(() =>
    {
        setActiveListTab(exchangeRouteState.listTab ?? 'active');

        if (exchangeRouteState.inboxRole)
        {
            setInboxRole(exchangeRouteState.inboxRole);
        }

        const deepLinkedId = exchangeRouteState.exchangeId;
        const deepLinkedDocumentId = exchangeRouteState.documentId;
        if (deepLinkedId)
        {
            deepLinkedExchangeIdRef.current = deepLinkedId;
            deepLinkedDocumentExchangeIdRef.current = deepLinkedId;
            setSelectedExchangeDocument(undefined);
            setSelectedExchangeId(deepLinkedId);
            setRouteSelectionVersion((version) => version + 1);
        }
        else
        {
            deepLinkedExchangeIdRef.current = null;
            deepLinkedDocumentExchangeIdRef.current = null;
            setSelectedExchangeId(null);
            setSelectedExchangeDocument(undefined);
        }

        deepLinkedDocumentIdRef.current = deepLinkedDocumentId;
        if (!deepLinkedDocumentId)
        {
            deepLinkedDocumentExchangeIdRef.current = null;
        }
    }, [exchangeRouteState]);

    useEffect(() =>
    {
        if (typeof window === 'undefined') return;

        const params = new URLSearchParams(window.location.search);
        params.set('tab', activeListTab);

        params.delete('role');

        if (selectedExchangeId)
        {
            params.set('s', selectedExchangeId);
        }
        else
        {
            params.delete('s');
            params.delete('d');
        }

        if (selectedExchangeId && selectedExchangeDocument?.id)
        {
            params.set('d', selectedExchangeDocument.id);
        }
        else
        {
            params.delete('d');
        }

        const nextQuery = params.toString();
        const nextSearch = nextQuery ? `?${nextQuery}` : '';
        if (window.location.search !== nextSearch)
        {
            const nextUrl = `${window.location.pathname}${nextSearch}`;
            window.history.replaceState(null, '', nextUrl);
        }
    }, [activeListTab, selectedExchangeId, selectedExchangeDocument?.id]);

    useEffect(() =>
    {
        if (!token || !appUser || !sessionReady)
        {
            return;
        }

        let cancelled = false;
        (async () =>
        {
            if (cancelled) return;
            await checkAppUserExchanges();
        })();

        return () =>
        {
            cancelled = true;
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [appUser?.id, checkAppUserExchanges, sessionReady]);

    useEffect(() =>
    {
        setDocumentSearchQuery("");
    }, [selectedExchangeId]);

    useEffect(() =>
    {
        const documents = exchangeDetails?.documents || [];
        const normalizedQuery = documentSearchQuery.trim().toLowerCase();

        if (normalizedQuery === "")
        {
            setFilteredDocuments(documents);
            return;
        }

        setFilteredDocuments(
            documents.filter(document => document.title.toLowerCase().includes(normalizedQuery))
        );
    }, [documentSearchQuery, exchangeDetails?.documents]);

    useEffect(() =>
    {
        let cancelled = false;

        if (selectedExchangeId)
        {
            if (!sessionReady) return;

            const fetchDetails = async () =>
            {
                setFetchingDetails(true);
                setDetailsLoadErrorId(null);

                try
                {
                    const details = (await fetchSignedInUserAppUserExchange(selectedExchangeId)) as ExchangeDetailedDto;
                    if (cancelled) return;

                    setExchangeDetails(details);
                    setFilteredDocuments(details.documents || []);

                    const shouldApplyDeepLinkedDocument =
                        deepLinkedDocumentExchangeIdRef.current === details.id && !!deepLinkedDocumentIdRef.current;
                    const deepLinkedDocument = shouldApplyDeepLinkedDocument
                        ? (details.documents || []).find(document => document.id === deepLinkedDocumentIdRef.current)
                        : undefined;

                    const nextPreviewDocument = resolvePreviewDocumentSelection(
                        details.documents || [],
                        deepLinkedDocument?.id || selectedExchangeDocument?.id,
                        !!deepLinkedDocument
                    );
                    setSelectedExchangeDocument(nextPreviewDocument);

                    if (deepLinkedDocument)
                    {
                        deepLinkedDocumentIdRef.current = null;
                        deepLinkedDocumentExchangeIdRef.current = null;
                    }
                    else if (shouldApplyDeepLinkedDocument)
                    {
                        deepLinkedDocumentIdRef.current = null;
                        deepLinkedDocumentExchangeIdRef.current = null;
                    }
                }
                catch (error)
                {
                    if (cancelled) return;

                    if (isExchangeUnavailableError(error))
                    {
                        clearUnavailableExchangeContext(selectedExchangeId);
                        return;
                    }
                    console.error(error);
                    setDetailsLoadErrorId(selectedExchangeId);
                }
                finally
                {
                    if (!cancelled) setFetchingDetails(false);
                }
            };
            fetchDetails();
        }
        else
        {
            setExchangeDetails(null);
            setFetchingDetails(false);
        }
        return () => {
            cancelled = true;
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [selectedExchangeId, routeSelectionVersion, sessionReady]);

    useEffect(() =>
    {
        if (!userPreferencesLoaded || !selectedExchangeId || exchangeDetails?.id !== selectedExchangeId)
        {
            return;
        }

        const layoutKey = `${selectedExchangeId}:${autoPreviewDocuments}`;
        if (resolvedDocumentLayoutKey === layoutKey)
        {
            return;
        }

        setSelectedExchangeDocument(currentSelection =>
            resolvePreviewDocumentSelection(
                exchangeDetails.documents || [],
                currentSelection?.id,
                autoPreviewDocuments
            )
        );
        setResolvedDocumentLayoutKey(layoutKey);
    }, [
        autoPreviewDocuments,
        exchangeDetails?.documents,
        exchangeDetails?.id,
        resolvedDocumentLayoutKey,
        selectedExchangeId,
        userPreferencesLoaded,
    ]);

    useEffect(() =>
    {
        setIsDocumentSidebarOpen(false);
        setSelectedExchangeDocument(undefined);
        setSelectedUploadExchangeDocument(undefined);
        setSelectedSidebarExchangeDocument(undefined);
        setDetailsActiveTab('documents');
        setIsDocumentToolbarVisible(false);
    }, [selectedExchangeId]);

    useEffect(() =>
    {
        if (!selectedExchangeId || !exchangeDetails) return;
        if (exchangeDetails.id !== selectedExchangeId) return;

        let targetTab: ExchangeListTab = 'inbox';
        if (exchangeDetails.status === ExchangeStatus.ACCEPTED_STARTED)
        {
            targetTab = 'active';
        }
        else if (
            exchangeDetails.status === ExchangeStatus.ENDED ||
            exchangeDetails.status === ExchangeStatus.REJECTED ||
            exchangeDetails.status === ExchangeStatus.RESCINDED
        )
        {
            targetTab = 'archive';
        }

        if (activeListTab !== targetTab)
        {
            setActiveListTab(targetTab);
        }

        if (targetTab === 'inbox')
        {
            const isInitiator = appUser?.id != null && exchangeDetails.initiator?.id === appUser.id;
            setInboxRole(isInitiator ? 'outgoing' : 'incoming');
        }

        deepLinkedExchangeIdRef.current = null;
    }, [exchangeDetails, selectedExchangeId, activeListTab, appUser?.id]);

    useEffect(() =>
    {
        if (!selectedExchangeId) return;
        let cancelled = false;

        const refetchDetails = async () =>
        {
            try
            {
                const details = (await fetchSignedInUserAppUserExchange(selectedExchangeId)) as ExchangeDetailedDto;
                if (cancelled) return;

                setExchangeDetails(details);
                setFilteredDocuments(details.documents || []);
                setSelectedExchangeDocument((currentSelection) =>
                    resolvePreviewDocumentSelection(
                        details.documents || [],
                        currentSelection?.id,
                        autoPreviewDocuments
                    )
                );
            }
            catch (error)
            {
                if (cancelled) return;

                if (isExchangeUnavailableError(error))
                {
                    clearUnavailableExchangeContext(selectedExchangeId);
                    return;
                }
                console.error("Failed to refresh Exchange after realtime event:", error);
            }
        };

        const handler = (msg: { exchangeId?: string }) =>
        {
            if (msg.exchangeId === selectedExchangeId)
            {
                refetchDetails();
            }
        };


        const offAdded = realtimeService.on('EXCHANGE_DOCUMENT_ADDED', handler);
        const offRemoved = realtimeService.on('EXCHANGE_DOCUMENT_REMOVED', handler);
        const offUpdated = realtimeService.on('EXCHANGE_DOCUMENT_UPDATED', handler);
        realtimeService.subscribeToExchange(selectedExchangeId);

        return () =>
        {
            cancelled = true;
            realtimeService.unsubscribeFromExchange(selectedExchangeId);
            offAdded();
            offRemoved();
            offUpdated();
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [selectedExchangeId]);

    useEffect(() =>
    {
        let cancelled = false;
        const offStatus = realtimeService.on('EXCHANGE_STATUS_CHANGED', async (msg) =>
        {
            if (!msg.exchangeId) return;

            try
            {
                const updatedExchange = (await fetchSignedInUserAppUserExchange(msg.exchangeId)) as ExchangeDetailedDto;
                publishExchangeUpdate(updatedExchange);
                if (cancelled) return;

                if (msg.exchangeId === selectedExchangeId)
                {
                    setExchangeDetails(updatedExchange);
                    setFilteredDocuments(updatedExchange.documents || []);
                }
            }
            catch (error)
            {
                if (cancelled) return;

                if (msg.exchangeId === selectedExchangeId && isExchangeUnavailableError(error))
                {
                    clearUnavailableExchangeContext(selectedExchangeId);
                    return;
                }
                console.error("Failed to process realtime status change:", error);
            }
        });

        return () =>
        {
            cancelled = true;
            offStatus();
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [selectedExchangeId]);

    useEffect(() =>
    {
        const initiationSubscription = exchangeInitiationObservable.subscribe(() =>
        {
            checkAppUserExchanges();
        });

        const deletionSubscription = exchangeDeletionObservable.subscribe(() =>
        {
            checkAppUserExchanges();
        });

        return () =>
        {
            initiationSubscription.unsubscribe();
            deletionSubscription.unsubscribe();
        };
    }, [checkAppUserExchanges]);

    const onDocumentDeleted = (documentId: string) =>
    {
        if (exchangeDetails)
        {
            setIsDocumentAddDialogOpen(false);
            const updatedDocuments = exchangeDetails.documents?.filter(document => document.id !== documentId);
            setExchangeDetails({...exchangeDetails, documents: updatedDocuments});
            setFilteredDocuments(updatedDocuments || []);
            setSelectedExchangeDocument((currentSelection) =>
                resolvePreviewDocumentSelection(
                    updatedDocuments || [],
                    currentSelection?.id,
                    autoPreviewDocuments
                )
            );
        }
    };

    const onNewDocumentAdded = (newExchangeDocument: DocumentDetailedDto) =>
    {
        if (exchangeDetails)
        {
            const currentDocuments = (exchangeDetails.documents && exchangeDetails.documents.length) ? exchangeDetails.documents : [];
            const updatedDocuments = [...currentDocuments, newExchangeDocument];
            setExchangeDetails({...exchangeDetails, documents: updatedDocuments});
            setFilteredDocuments(updatedDocuments);
            setSelectedExchangeDocument((currentSelection) =>
                resolvePreviewDocumentSelection(updatedDocuments, currentSelection?.id, autoPreviewDocuments)
            );
        }
    };

    const onDocumentUploaded = (uploadedDocument: DocumentDetailedDto) =>
    {
        onDocumentUpdated(uploadedDocument);
    };

    const onDocumentUpdated = (updatedDocument: DocumentDetailedDto) =>
    {
        if (exchangeDetails)
        {
            const updatedDocuments = exchangeDetails.documents?.map(document =>
            {
                if (document.id === updatedDocument.id)
                {
                    return {
                        ...document,
                        ...updatedDocument,
                    };
                }
                return document;
            });
            setExchangeDetails({...exchangeDetails, documents: updatedDocuments});
            setFilteredDocuments(updatedDocuments || []);

            setSelectedExchangeDocument((currentSelection) =>
                resolvePreviewDocumentSelection(
                    updatedDocuments || [],
                    currentSelection?.id,
                    autoPreviewDocuments
                )
            );
        }
    };

    const onExchangeDeleted = (exchangeId: string) =>
    {
        if (exchangeDetails && exchangeDetails.id === exchangeId)
        {
            setExchangeDetails(undefined);
            changeSelectedExchangeId(null);
        }
    };

    const onExchangeEnded = (exchange: ExchangeDetailedDto) =>
    {
        setExchangeDetails(exchange);
        setActiveListTab('archive');
        changeSelectedExchangeId(exchange.id);
    };

    const onExchangeRescinded = (exchange: ExchangeDetailedDto) =>
    {
        setExchangeDetails(exchange);
        setActiveListTab('archive');
        changeSelectedExchangeId(exchange.id);
    };

    const onExchangeEdited = (exchange: ExchangeDetailedDto) =>
    {
        setExchangeDetails(exchange);
    };

    const onExchangeAccessManagementUpdated = (exchange: ExchangeDetailedDto) =>
    {
        setExchangeDetails(exchange);
    };

    const getExchangeCountLabel = (count: number, type: 'active' | 'archived') =>
    {
        return `${count} ${type} ${count === 1 ? 'exchange' : 'exchanges'}`;
    };

    const renderDialogs = () =>
    {
        return (
            <ExchangeDialogsGroup
                isDeletedExchangeDialogOpen={isDeletedExchangeDialogOpen}
                setIsDeletedExchangeDialogOpen={setIsDeletedExchangeDialogOpen}
                isExchangeEndDialogOpen={isExchangeEndDialogOpen}
                setIsExchangeEndDialogOpen={setIsExchangeEndDialogOpen}
                isExchangeRescindDialogOpen={isExchangeRescindDialogOpen}
                setIsExchangeRescindDialogOpen={setIsExchangeRescindDialogOpen}
                isDocumentAddDialogOpen={isDocumentAddDialogOpen}
                setIsDocumentAddDialogOpen={setIsDocumentAddDialogOpen}
                isUploadDocumentDialogOpen={isUploadDocumentDialogOpen}
                setIsUploadDocumentDialogOpen={setIsUploadDocumentDialogOpen}
                isUpdateDocumentDialogOpen={isUpdateDocumentDialogOpen}
                setIsUpdateDocumentDialogOpen={setIsUpdateDocumentDialogOpen}
                isDocumentZipDialogOpen={isDocumentZipDialogOpen}
                setIsDocumentZipDialogOpen={setIsDocumentZipDialogOpen}
                isExchangeEditDialogOpen={isExchangeEditDialogOpen}
                setIsExchangeEditDialogOpen={setIsExchangeEditDialogOpen}
                isExchangeDetailedViewDialogOpen={isExchangeDetailedViewDialogOpen}
                setIsExchangeDetailedViewDialogOpen={setIsExchangeDetailedViewDialogOpen}
                isExchangeAccessManagementDialogOpen={isExchangeAccessManagementDialogOpen}
                setIsExchangeAccessManagementDialogOpen={setIsExchangeAccessManagementDialogOpen}
                exchangeDetails={exchangeDetails}
                selectedExchangeId={selectedExchangeId}
                selectedUploadExchangeDocument={selectedUploadExchangeDocument}
                selectedUpdateExchangeDocument={selectedUpdateExchangeDocument}
                setSelectedUpdateExchangeDocument={setSelectedUpdateExchangeDocument}
                onNewDocumentAdded={onNewDocumentAdded}
                onDocumentUploaded={onDocumentUploaded}
                onDocumentUpdated={onDocumentUpdated}
                onExchangeDeleted={onExchangeDeleted}
                onExchangeEnded={onExchangeEnded}
                onExchangeRescinded={onExchangeRescinded}
                onExchangeEdited={onExchangeEdited}
                onExchangeAccessManagementUpdated={onExchangeAccessManagementUpdated}
            />
        );
    };

    const onFilterDocuments = (_event: SearchBoxChangeEvent, data: InputOnChangeData) =>
    {
        setDocumentSearchQuery(data.value);
    };

    const onExchangeAccepted = (exchange: ExchangeDetailedDto) =>
    {
        setExchangeDetails(exchange);
        setActiveListTab('active');
        changeSelectedExchangeId(exchange.id);
    };

    const onExchangeRejected = (exchangeId: string) =>
    {
        publishExchangeDelete(exchangeId);
        setExchangeDetails(null);
        changeSelectedExchangeId(null);
    };

    const onRecreateRejectedExchange = (exchange: ExchangeDetailedDto) =>
    {
        publishRecreateRejectedExchange({
            name: exchange.name || '',
            description: exchange.description || '',
            initialShareMessage: exchange.initialShareMessage || '',
            requestRecipientSignIn: !!exchange.requestRecipientSignIn,
            allowDocumentAddition: !!exchange.allowDocumentAddition,
            allowDocumentDeletion: !!exchange.allowDocumentDeletion,
            allowDocumentDownload: !!exchange.allowDocumentDownload,
            allowDocumentUpdate: !!exchange.allowDocumentUpdate,
            allowDocumentUpload: !!exchange.allowDocumentUpload,
            exchangeDocuments: (exchange.documents || []).map(doc => ({
                title: doc.title || '',
                restrictedType: doc.restrictedType as (DocumentType | ImageType) | undefined,
                restrictType: !!doc.restrictType,
            })),
            recipientUser: exchange.recipient,
            recipientEmail: exchange.recipient?.email,
            recipientFirstName: exchange.recipient?.person?.firstName,
            recipientLastName: exchange.recipient?.person?.lastName,
        });
    };

    const onDecideLater = () =>
    {
        if (!selectedExchangeId || activeListTab !== 'inbox') return;

        const currentIndex = exchangeList.findIndex(s => s.id === selectedExchangeId);
        if (currentIndex < 0 || currentIndex >= exchangeList.length - 1) return;

        const nextIndex = currentIndex + 1;
        changeSelectedExchangeId(exchangeList[nextIndex].id);
    };

    const renderExchangesSection = () =>
    {
        const isInboxTab = activeListTab === 'inbox';
        const isActiveTab = activeListTab === 'active';
        const isArchiveTab = activeListTab === 'archive';
        const isCurrentTabEmpty = exchangeList.length === 0;
        const shouldShowIncomingInboxEmptyDetails =
            !isExchangeListLoading && !fetchingDetails && !hasSelectedDetails && isInboxTab && inboxRole === 'incoming' && isCurrentTabEmpty;
        const shouldShowOutgoingInboxEmptyDetails =
            !isExchangeListLoading && !fetchingDetails && !hasSelectedDetails && isInboxTab && inboxRole === 'outgoing' && isCurrentTabEmpty;
        const shouldShowActiveEmptyDetails = !isExchangeListLoading && !fetchingDetails && !hasSelectedDetails && isActiveTab && isCurrentTabEmpty;
        const shouldShowArchiveEmptyDetails = !isExchangeListLoading && !fetchingDetails && !hasSelectedDetails && isArchiveTab && isCurrentTabEmpty;
        const shouldShowSelectionHint =
            !isExchangeListLoading &&
            !fetchingDetails &&
            !selectedExchangeId &&
            !exchangeDetails &&
            !shouldShowIncomingInboxEmptyDetails &&
            !shouldShowOutgoingInboxEmptyDetails &&
            !shouldShowActiveEmptyDetails &&
            !shouldShowArchiveEmptyDetails;

        const showAcceptance =
            exchangeDetails != null &&
            exchangeDetails.status === ExchangeStatus.INITIATED &&
            appUser?.id !== exchangeDetails.initiator?.id;

        const currentInboxIndex = selectedExchangeId
            ? exchangeList.findIndex(exchange => exchange.id === selectedExchangeId)
            : -1;
        const canDecideLater =
            activeListTab === 'inbox' &&
            currentInboxIndex > -1 &&
            currentInboxIndex < exchangeList.length - 1;

        const isSingleExchange = exchangeList.length === 1;

        const hasMobileSelection = !!selectedExchangeId;
        const listPaneClassName = mergeClasses(
            styles.listPaneWrapper,
            isMobile && hasMobileSelection ? styles.listPaneHidden : undefined,
            isMobile && !hasMobileSelection && paneNavigationDirection === "back"
                ? styles.listPaneSlideInFromLeft
                : undefined,
        );
        const detailsPaneClassName = mergeClasses(
            styles.detailsContainer,
            isMobile && !hasMobileSelection ? styles.detailsPaneHidden : undefined,
        );
        const shouldAnimateDetailsTransition =
            selectedExchangeId !== detailsTransitionAnimationSuppressedForId;

        const showDetailsColumnFallbacks = !isMobile;

        return (
            <section id={"exchanges-container"}
                     className={styles.container}>

                <div id={"exchanges-container-div"}
                     className={listPaneClassName}>
                    <ExchangeList
                        onSelectionChange={changeSelectedExchangeId}
                        onExchangeListChange={setExchangeList}
                        onTabChange={setActiveListTab}
                        onInboxRoleChange={setInboxRole}
                        onTabCountsChange={setTabCounts}
                        onLoadingChange={setIsExchangeListLoading}
                        controlledSelectedId={selectedExchangeId}
                        controlledActiveTab={activeListTab}
                        controlledInboxRole={inboxRole}
                    />
                </div>

                {showDetailsColumnFallbacks && showInitialDetailsSkeleton && (
                    <div id={"exchanges-details-transition-frame"}
                         className={styles.detailsTransitionFrame}>
                        <ExchangeDetailsLoading/>
                    </div>
                )}

                {!!selectedExchangeId && (
                    <div
                        id={`exchange-details-transition-${selectedExchangeId}`}
                        key={selectedExchangeId}
                        className={mergeClasses(
                            styles.detailsTransitionFrame,
                            shouldAnimateDetailsTransition && styles.detailsSlideInFromRight
                        )}
                    >
                        {!hasDetailsLoadError && showSelectedDetailsSkeleton && <ExchangeDetailsLoading/>}

                        {hasDetailsLoadError && !hasSelectedDetails && (
                            <ExchangeDetailsError onRetry={() => setRouteSelectionVersion(version => version + 1)}/>
                        )}

                        {hasSelectedDetails && exchangeDetails &&
                            <div id={"exchanges-div"}
                                 className={detailsPaneClassName}>

                                <div
                                    id={"exchanges-details-content"}
                                    className={`${styles.detailsContent} ${fetchingDetails ? styles.detailsContentLoading : ''}`}>

                                    {showAcceptance && isSingleExchange && (
                                        <ExchangeAcceptanceDialog
                                            exchange={exchangeDetails}
                                            isSingleExchange={true}
                                            canDecideLater={canDecideLater}
                                            activeCount={tabCounts.active}
                                            archiveCount={tabCounts.archive}
                                            onAccepted={onExchangeAccepted}
                                            onRejected={onExchangeRejected}
                                            onDismiss={() => {
                                            }}
                                            onOpenActive={() => setActiveListTab('active')}
                                            onOpenArchive={() => setActiveListTab('archive')}
                                        />
                                    )}

                                    <ExchangeDetailsHeader
                                        exchangeDetails={exchangeDetails}
                                        setIsDocumentAddDialogOpen={setIsDocumentAddDialogOpen}
                                        setIsExchangeEndDialogOpen={setIsExchangeEndDialogOpen}
                                        setIsExchangeRescindDialogOpen={setIsExchangeRescindDialogOpen}
                                        setIsDeletedExchangeDialogOpen={setIsDeletedExchangeDialogOpen}
                                        setIsExchangeEditDialogOpen={setIsExchangeEditDialogOpen}
                                        setIsExchangeDetailedViewDialogOpen={setIsExchangeDetailedViewDialogOpen}
                                        setIsExchangeAccessManagementDialogOpen={setIsExchangeAccessManagementDialogOpen}
                                        exchangePermissions={permissions}
                                        onRecreateRejectedExchange={onRecreateRejectedExchange}
                                        onBackToList={isMobile ? () => changeSelectedExchangeId(null) : undefined}
                                    />

                                    <ExchangeTabsHeader activeTab={detailsActiveTab}
                                                        documents={exchangeDetails?.documents || []}
                                                        canDownloadZip={!!permissions?.canDownloadDocumentsZip}
                                                        canViewAudit={canViewExchangeAudit}
                                                        canViewDetails={canViewBusinessFields}
                                                        canViewWorkflow={canViewWorkflows}
                                                        canViewInformationRequests={informationRequests.visible}
                                                        isDocumentToolbarVisible={isDocumentToolbarVisible}
                                                        onTabChange={setDetailsActiveTab}
                                                        onDownloadZip={() => setIsDocumentZipDialogOpen(true)}
                                                        onToggleDocumentToolbar={() => setIsDocumentToolbarVisible(visible => !visible)}/>

                                    {detailsActiveTab === 'documents' && (
                                        <div className={styles.documentsSectionContainer}
                                             id={"documentsSectionContainer"}>
                                            <div className={styles.documentsSection}
                                                 id={"documentsSection"}>
                                                {(exchangeDetails?.documents?.length > 0) && (
                                                    <ExchangeDocumentsList
                                                        exchangeDetails={exchangeDetails}
                                                        permissions={permissions}
                                                        isToolbarVisible={isDocumentToolbarVisible}
                                                        onFilterDocuments={onFilterDocuments}
                                                        documentSearchQuery={documentSearchQuery}
                                                        filteredDocuments={filteredDocuments}
                                                        selectedExchangeDocument={selectedExchangeDocument}
                                                        setSelectedExchangeDocument={setSelectedExchangeDocument}
                                                        setSelectedUploadExchangeDocument={setSelectedUploadExchangeDocument}
                                                        setSelectedSidebarExchangeDocument={setSelectedSidebarExchangeDocument}
                                                        setSelectedUpdateExchangeDocument={setSelectedUpdateExchangeDocument}
                                                        setIsUploadDocumentDialogOpen={setIsUploadDocumentDialogOpen}
                                                        setIsDocumentUpdateDialogOpen={setIsUpdateDocumentDialogOpen}
                                                        onDocumentDeleted={onDocumentDeleted}
                                                        setIsDocumentSidebarOpen={setIsDocumentSidebarOpen}
                                                        isPreviewMode={!!selectedExchangeDocument}
                                                    />
                                                )}

                                                {
                                                    exchangeDetails.documents?.length === 0 &&
                                                    <NoExchangeDocuments
                                                        setIsDocumentAddDialogOpen={setIsDocumentAddDialogOpen}/>
                                                }
                                                {selectedExchangeDocument &&
                                                    <ExchangeDocumentPreviewer document={selectedExchangeDocument}
                                                                               ref={documentPreviewerRef}
                                                                               exchange={exchangeDetails}
                                                                               canUploadDocument={!!permissions?.canUploadDocument}
                                                                               onPageChange={setPreviewerCurrentPage}
                                                                               onUploadDocument={() => {
                                                                                   setSelectedUploadExchangeDocument(selectedExchangeDocument);
                                                                                   setIsUploadDocumentDialogOpen(true);
                                                                               }}/>
                                                }

                                            </div>
                                            {selectedSidebarExchangeDocument && isDocumentSidebarOpen &&

                                                <ExchangeDocumentSidebar
                                                    isOpen={isDocumentSidebarOpen}
                                                    onOpen={setIsDocumentSidebarOpen}
                                                    exchange={exchangeDetails}
                                                    exchangeDocument={selectedSidebarExchangeDocument}
                                                    pageNumber={sidebarLinkablePageNumber}
                                                    onNavigateToPage={canNavigatePreviewerToPage ? handleNavigatePreviewerToPage : undefined}/>
                                            }
                                        </div>
                                    )}

                                    {detailsActiveTab === 'details' && canViewBusinessFields && (
                                        <div id={"exchanges-documents-section-container"}
                                             className={styles.documentsSectionContainer}>
                                            <div
                                                id={"exchanges-documents-section"}
                                                className={`${styles.documentsSection} ${styles.scrollableTabContent}`}>
                                                <ExchangeFieldsTab exchange={exchangeDetails}/>
                                            </div>
                                        </div>
                                    )}

                                    {detailsActiveTab === 'information-requests' && informationRequests.visible && (
                                        <div id={"exchange-information-requests-tab-container"}
                                             className={styles.documentsSectionContainer}>
                                            <div id={"exchange-information-requests-tab-content"}
                                                 className={`${styles.documentsSection} ${styles.scrollableTabContent}`}>
                                                <ExchangeInformationRequestsTab exchangeId={exchangeDetails.id}
                                                                                state={informationRequests}/>
                                            </div>
                                        </div>
                                    )}

                                    {detailsActiveTab === 'workflow' && canViewWorkflows && (
                                        <div id={"exchanges-documents-section-container-2"}
                                             className={styles.documentsSectionContainer}>
                                            <div
                                                id={"exchanges-documents-section-2"}
                                                className={`${styles.documentsSection} ${styles.scrollableTabContent}`}>
                                                <ExchangeWorkflowTab exchange={exchangeDetails}/>
                                            </div>
                                        </div>
                                    )}

                                    {detailsActiveTab === 'audit' && canViewExchangeAudit && (
                                        <div id={"exchanges-documents-section-container-3"}
                                             className={styles.documentsSectionContainer}>
                                            <div
                                                id={"exchanges-documents-section-3"}
                                                className={`${styles.documentsSection} ${styles.scrollableTabContent}`}>
                                                <ExchangeAuditTab exchange={exchangeDetails}/>
                                            </div>
                                        </div>
                                    )}

                                    {showAcceptance && !isSingleExchange && (
                                        <ExchangeAcceptanceDialog
                                            exchange={exchangeDetails}
                                            isSingleExchange={false}
                                            canDecideLater={canDecideLater}
                                            activeCount={tabCounts.active}
                                            archiveCount={tabCounts.archive}
                                            onAccepted={onExchangeAccepted}
                                            onRejected={onExchangeRejected}
                                            onDismiss={onDecideLater}
                                            onOpenActive={() => setActiveListTab('active')}
                                            onOpenArchive={() => setActiveListTab('archive')}
                                        />
                                    )}
                                </div>

                                {fetchingDetails && (
                                    <div id={"exchanges-details-loading-overlay"}
                                         className={styles.detailsLoadingOverlay}>
                                        <Spinner id={"exchanges-loading-exchange-spinner"}
                                                 size="small"
                                                 label="Loading exchange..."
                                                 labelPosition="after"/>
                                    </div>
                                )}
                            </div>
                        }
                    </div>
                )}
                {showDetailsColumnFallbacks && shouldShowIncomingInboxEmptyDetails &&
                    <div id={"exchanges-no-exchange-selected-section"}
                         className={styles.noExchangeSelectedSection}>
                        <div id={"exchanges-inbox-empty-details-content"}
                             className={styles.inboxEmptyDetailsContent}>
                            <EmptyStateIllustration className={styles.inboxEmptyIllustration}/>
                            <Text id={"exchanges-no-incoming-requests-text"}
                                  size={600}
                                  weight={"semibold"}
                                  align={"center"}>No incoming requests</Text>
                            <Text id={"exchanges-you-have-no-pending-text"}
                                  size={300}
                                  align={"center"}>
                                You have no pending requests right now. You have <Text
                                id={"exchanges-get-exchange-count-label-text"}
                                weight={"semibold"}>{getExchangeCountLabel(tabCounts.active, 'active')}</Text>
                                {' '}and <Text id={"exchanges-get-exchange-count-label-text-2"}
                                               weight={"semibold"}>{getExchangeCountLabel(tabCounts.archive, 'archived')}</Text>.
                                Open <Text id={"exchanges-active-text"}
                                           weight={"semibold"}>Active</Text> or <Text id={"exchanges-archive-text"}
                                                                                      weight={"semibold"}>Archive</Text>,
                                or start a new <Text id={"exchanges-request-or-send-text"}
                                                     weight={"semibold"}>Request or Send</Text> from Start Exchange.
                            </Text>
                            <div id={"exchanges-inbox-empty-actions"}
                                 className={styles.inboxEmptyActions}>
                                <Button
                                    id={"exchanges-incoming-empty-open-active-btn"}
                                    appearance="primary"
                                    shape={"circular"}
                                    disabled={tabCounts.active === 0}
                                    onClick={() => setActiveListTab('active')}>
                                    Open Active ({tabCounts.active})
                                </Button>
                                <Button
                                    id={"exchanges-incoming-empty-open-archive-btn"}
                                    appearance="secondary"
                                    shape={"circular"}
                                    disabled={tabCounts.archive === 0}
                                    onClick={() => setActiveListTab('archive')}>
                                    Open Archive ({tabCounts.archive})
                                </Button>
                            </div>
                        </div>
                    </div>
                }

                {showDetailsColumnFallbacks && shouldShowOutgoingInboxEmptyDetails &&
                    <div id={"exchanges-no-exchange-selected-section-2"}
                         className={styles.noExchangeSelectedSection}>
                        <div id={"exchanges-inbox-empty-details-content-2"}
                             className={styles.inboxEmptyDetailsContent}>
                            <EmptyStateIllustration className={styles.inboxEmptyIllustration}/>
                            <Text id={"exchanges-no-outgoing-requests-text"}
                                  size={600}
                                  weight={"semibold"}
                                  align={"center"}>No outgoing requests</Text>
                            <Text id={"exchanges-you-do-not-have-text"}
                                  size={300}
                                  align={"center"}>
                                You do not have requests awaiting recipient response right now. You have <Text
                                id={"exchanges-get-exchange-count-label-text-3"}
                                weight={"semibold"}>{getExchangeCountLabel(tabCounts.active, 'active')}</Text>
                                {' '}and <Text id={"exchanges-get-exchange-count-label-text-4"}
                                               weight={"semibold"}>{getExchangeCountLabel(tabCounts.archive, 'archived')}</Text>.
                                Open <Text id={"exchanges-active-text-2"}
                                           weight={"semibold"}>Active</Text> or <Text id={"exchanges-archive-text-2"}
                                                                                      weight={"semibold"}>Archive</Text>,
                                or start a new <Text id={"exchanges-request-or-send-text-2"}
                                                     weight={"semibold"}>Request or Send</Text> from Start Exchange.
                            </Text>
                            <div id={"exchanges-inbox-empty-actions-2"}
                                 className={styles.inboxEmptyActions}>
                                <Button
                                    id={"exchanges-outgoing-empty-open-active-btn"}
                                    appearance="primary"
                                    shape={"circular"}
                                    disabled={tabCounts.active === 0}
                                    onClick={() => setActiveListTab('active')}>
                                    Open Active ({tabCounts.active})
                                </Button>
                                <Button
                                    id={"exchanges-outgoing-empty-open-archive-btn"}
                                    appearance="secondary"
                                    shape={"circular"}
                                    disabled={tabCounts.archive === 0}
                                    onClick={() => setActiveListTab('archive')}>
                                    Open Archive ({tabCounts.archive})
                                </Button>
                            </div>
                        </div>
                    </div>
                }

                {showDetailsColumnFallbacks && shouldShowActiveEmptyDetails &&
                    <div id={"exchanges-no-exchange-selected-section-3"}
                         className={styles.noExchangeSelectedSection}>
                        <div id={"exchanges-inbox-empty-details-content-3"}
                             className={styles.inboxEmptyDetailsContent}>
                            <EmptyStateIllustration className={styles.inboxEmptyIllustration}/>
                            <Text id={"exchanges-no-active-exchanges-text"}
                                  size={600}
                                  weight={"semibold"}
                                  align={"center"}>No active Exchanges</Text>
                            <Text id={"exchanges-you-do-not-have-text-2"}
                                  size={300}
                                  align={"center"}>
                                You do not have active Exchanges right now. Check <Text id={"exchanges-inbox-text"}
                                                                                        weight={"semibold"}>Inbox</Text>
                                {' '}for new requests or review <Text id={"exchanges-archive-text-3"}
                                                                      weight={"semibold"}>Archive</Text>.
                            </Text>
                            <div id={"exchanges-inbox-empty-actions-3"}
                                 className={styles.inboxEmptyActions}>
                                <Button
                                    id={"exchanges-active-empty-open-inbox-btn"}
                                    appearance="primary"
                                    shape={"circular"}
                                    disabled={tabCounts.inbox === 0}
                                    onClick={() => setActiveListTab('inbox')}>
                                    Open Inbox ({tabCounts.inbox})
                                </Button>
                                <Button
                                    id={"exchanges-active-empty-open-archive-btn"}
                                    appearance="secondary"
                                    shape={"circular"}
                                    disabled={tabCounts.archive === 0}
                                    onClick={() => setActiveListTab('archive')}>
                                    Open Archive ({tabCounts.archive})
                                </Button>
                            </div>
                        </div>
                    </div>
                }

                {showDetailsColumnFallbacks && shouldShowArchiveEmptyDetails &&
                    <div id={"exchanges-no-exchange-selected-section-4"}
                         className={styles.noExchangeSelectedSection}>
                        <div id={"exchanges-inbox-empty-details-content-4"}
                             className={styles.inboxEmptyDetailsContent}>
                            <EmptyStateIllustration className={styles.inboxEmptyIllustration}/>
                            <Text id={"exchanges-archive-is-empty-text"}
                                  size={600}
                                  weight={"semibold"}
                                  align={"center"}>Archive is empty</Text>
                            <Text id={"exchanges-you-do-not-have-text-3"}
                                  size={300}
                                  align={"center"}>
                                You do not have archived Exchanges yet. Check <Text id={"exchanges-inbox-text-2"}
                                                                                    weight={"semibold"}>Inbox</Text>
                                {' '}for pending requests or open <Text id={"exchanges-active-text-3"}
                                                                        weight={"semibold"}>Active</Text> exchanges.
                            </Text>
                            <div id={"exchanges-inbox-empty-actions-4"}
                                 className={styles.inboxEmptyActions}>
                                <Button
                                    id={"exchanges-archive-empty-open-inbox-btn"}
                                    appearance="primary"
                                    shape={"circular"}
                                    disabled={tabCounts.inbox === 0}
                                    onClick={() => setActiveListTab('inbox')}>
                                    Open Inbox ({tabCounts.inbox})
                                </Button>
                                <Button
                                    id={"exchanges-archive-empty-open-active-btn"}
                                    appearance="secondary"
                                    shape={"circular"}
                                    disabled={tabCounts.active === 0}
                                    onClick={() => setActiveListTab('active')}>
                                    Open Active ({tabCounts.active})
                                </Button>
                            </div>
                        </div>
                    </div>
                }

                {showDetailsColumnFallbacks && shouldShowSelectionHint &&
                    <div id={"exchanges-no-exchange-selected-section-5"}
                         className={styles.noExchangeSelectedSection}>
                        <Text id={"exchanges-select-a-exchange-in-text"}
                              size={500}> Select a Exchange in the list to view details</Text>
                    </div>
                }

                {renderDialogs()}

            </section>
        )
    }

    return (
        <>
            <Toaster id={"exchanges-toaster"}
                     toasterId={toasterId}
                     position="bottom-end"/>
            {preparingExchanges && <ExchangePreLoader/>}
            {!preparingExchanges && (appUserHasExchanges) && renderExchangesSection()}
            {!preparingExchanges && (!appUserHasExchanges) &&
                <div id={"exchanges-container-no-exchanges"}
                     className={styles.containerNoExchanges}>
                    <EmptyStateIllustration className={styles.noExchangesIllustration}/>
                    <Text id={"exchanges-share-your-first-document-text"}
                          size={600}
                          weight={"semibold"}
                          align={"center"}>
                        Share your first document
                    </Text>
                    <Text id={"exchanges-send-or-receive-documents-text"}
                          size={300}
                          align={"center"}>
                        Send or receive documents securely to anyone, your Exchanges will appear here.
                        Tap <Text id={"exchanges-start-exchange-text"}
                                  italic
                                  weight={"semibold"}>Start Exchange</Text> in the main menu to begin.
                    </Text>
                </div>
            }
        </>
    );
};

export default Exchanges;
