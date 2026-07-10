import React, {useEffect, useMemo, useRef, useState} from 'react';
import {
    Button, InputOnChangeData, Link, SearchBoxChangeEvent,
    mergeClasses,
    Spinner, TabValue, Text, Toast, Toaster,
    ToastBody,
    ToastTitle,
    ToastTrigger,
    useId,
    useToastController
} from "@fluentui/react-components";
import {
    checkSignedInAppUserHasExchanges,
    fetchSignedInUserAppUserExchange
} from "../../services/exchangeApi.ts";
import useToken from "../../context/useToken.tsx";
import ExchangePreLoader from "./components/exchange-pre-loader/ExchangePreLoader.tsx";
import {DocumentDetailedDto,
    DocumentType,
    ExchangeBasicDto,
    ExchangeDetailedDto,
    ExchangeStatus,
    ImageType
} from "../models/models.tsx";
import {useExchangesStyles} from "./ExchangesStyles.tsx";
import ExchangeDocumentSidebar from "./components/exchange-document-sidebar/ExchangeDocumentSidebar.tsx";
import NoExchangeDocuments from "./components/exchange-documents-none/NoExchangeDocuments.tsx";
import ExchangeDialogsGroup from "./components/exchange-dialog-group/ExchangeDialogsGroup.tsx";
import ExchangeDetailsHeader from "./components/exchange-details-header/ExchangeDetailsHeader.tsx";
import ExchangeDetailsLoading from "./components/exchange-details-loading/ExchangeDetailsLoading.tsx";
import "react-pdf/dist/esm/Page/AnnotationLayer.css";
import "react-pdf/dist/esm/Page/TextLayer.css";
import ExchangeList from "./components/exchange-list/ExchangeList.tsx";
import {useAuth} from "../../context/AuthContext.tsx";
import {getPermissions, ExchangePermissions} from "./ExchangePermissions.ts";
import ExchangeDocumentsList from "./components/exchange-document-list/ExchangeDocumentsList.tsx";
import {realtimeService} from "../../services/NotificationService";
import {
    publishRecreateRejectedExchange,
    publishExchangeUpdate,
    exchangeDeletionObservable,
    exchangeInitiationObservable
} from "../observable/exchangeObservables.ts";
import ExchangeDocumentPreviewer from "./components/exchange-document-preview/ExchangeDocumentPreviewer.tsx";
import ExchangeAcceptanceDialog from "./components/exchange-acceptance-dialog/ExchangeAcceptanceDialog.tsx";
import EmptyStateIllustration from "./components/empty-state-illustration/EmptyStateIllustration.tsx";
import {ExchangeListTab} from "./components/exchange-list/exchange-list-tabs/ExchangeListTabs.tsx";
import {InboxRole, ExchangeTabCounts} from "./components/exchange-list/ExchangeList.tsx";
import {useIsMobile} from "../../utils/useMediaQuery.ts";
import ExchangeAuditTab from "./components/exchange-audit-tab/ExchangeAuditTab.tsx";
import ExchangeWorkflowTab from "./components/exchange-workflow-tab/ExchangeWorkflowTab.tsx";
import ExchangeFieldsTab from "./components/exchange-fields-tab/ExchangeFieldsTab.tsx";
import ExchangeTabsHeader from "./components/exchange-tabs-header/ExchangeTabsHeader.tsx";

type ExchangePaneNavigationDirection = "forward" | "back" | null;

const parseExchangeListTab = (value: string | null | undefined): ExchangeListTab | null =>
{
    if (value === 'inbox' || value === 'active' || value === 'archive')
    {
        return value;
    }
    return null;
};

const isExchangeUnavailableError = (error: unknown): boolean =>
{
    const status = (error as { response?: { status?: number }, status?: number })?.response?.status
        ?? (error as { status?: number })?.status;
    return status === 403 || status === 404;
};

const Exchanges: React.FC = () =>
{
    const styles = useExchangesStyles();
    const isMobile = useIsMobile();
    const toasterId = useId("exchanges-toaster");
    const {dispatchToast} = useToastController(toasterId);
    const [exchangeList, setExchangeList] = useState<ExchangeBasicDto[]>([]);
    const [activeListTab, setActiveListTab] = useState<ExchangeListTab>('active');
    const [inboxRole, setInboxRole] = useState<InboxRole>('incoming');
    const [tabCounts, setTabCounts] = useState<ExchangeTabCounts>({inbox: 0, active: 0, archive: 0});
    const [selectedExchangeId, setSelectedExchangeId] = useState<string | null>(null);
    const [paneNavigationDirection, setPaneNavigationDirection] =
        useState<ExchangePaneNavigationDirection>(null);
    const [preparingExchanges, setPreparingExchanges] = useState<boolean>(true);
    const token = useToken();
    const {appUser} = useAuth();
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
    const [selectedUpdateExchangeDocument, setSelectedUpdateExchangeDocument] = React.useState<DocumentDetailedDto>(undefined);
    const [exchangeDetails, setExchangeDetails] = useState<ExchangeDetailedDto | null>(null);
    const [fetchingDetails, setFetchingDetails] = useState<boolean>(true);
    const [filteredDocuments, setFilteredDocuments] = useState<DocumentDetailedDto[]>([]);
    const [documentSearchQuery, setDocumentSearchQuery] = useState<string>("");
    const [appUserHasExchanges, setAppUserHasExchanges] = useState<boolean>(false);
    const [detailsActiveTab, setDetailsActiveTab] = useState<TabValue>('documents');
    const permissions = useMemo<ExchangePermissions>(
        () => getPermissions(exchangeDetails, appUser),
        [exchangeDetails, appUser?.id],
    );

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
            <Toast>
                <ToastTitle action={
                    <ToastTrigger>
                        <Link>Dismiss</Link>
                    </ToastTrigger>
                }>
                    This exchange is no longer available.
                </ToastTitle>
                <ToastBody>
                    <Link onClick={() => setActiveListTab('active')}>Go to Active tab</Link>
                </ToastBody>
            </Toast>,
            {intent: 'warning', timeout: 7000}
        );
    };

    const changeSelectedExchangeId = (exchangeId: string | null) =>
    {
        setPaneNavigationDirection(exchangeId ? "forward" : "back");
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

    const resolvePreviewDocumentSelection = (
        documents: DocumentDetailedDto[] = [],
        currentSelectionId?: string
    ): DocumentDetailedDto | undefined =>
    {
        if (documents.length === 0)
        {
            return undefined;
        }

        if (currentSelectionId)
        {
            const currentSelection = documents.find(document => document.id === currentSelectionId);
            if (currentSelection)
            {
                return currentSelection;
            }
        }

        // Default to the first item in the already-sorted list.
        return documents[0];
    };

    const checkAppUserExchanges = async () =>
    {
        try
        {
            const hasExchanges = await checkSignedInAppUserHasExchanges(token);
            setAppUserHasExchanges(hasExchanges);
        }
        catch (error)
        {
            // Don't block the page with an alert(),  log and degrade gracefully.
            // A failing existence-check is annoying, not fatal: we still render
            // the page with the "no exchanges yet" empty state. If exchanges
            // genuinely exist, they'll appear once the user navigates back or
            // once a subsequent list fetch succeeds.
            console.error("Could not determine whether user has exchanges:", error);
            setAppUserHasExchanges(false);
        }
        finally
        {
            setPreparingExchanges(false);
        }
    }

    // Restore tab/exchange/document from URL params only.
    useEffect(() =>
    {
        if (typeof window === 'undefined') return;

        const params = new URLSearchParams(window.location.search);
        const urlTab = parseExchangeListTab(params.get('tab'));

        setActiveListTab(urlTab ?? 'active');

        const deepLinkedId = params.get('s');
        const deepLinkedDocumentId = params.get('d');
        if (deepLinkedId)
        {
            deepLinkedExchangeIdRef.current = deepLinkedId;
            deepLinkedDocumentExchangeIdRef.current = deepLinkedId;
            setSelectedExchangeId(deepLinkedId);
        }
        if (deepLinkedDocumentId)
        {
            deepLinkedDocumentIdRef.current = deepLinkedDocumentId;
        }
    }, []);

    useEffect(() =>
    {
        if (typeof window === 'undefined') return;

        const params = new URLSearchParams(window.location.search);
        params.set('tab', activeListTab);

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
        // Wait for both the access token AND the signed-in AppUser to be
        // available before hitting the backend. Right after sign-in/onboarding
        // the AuthContext is still settling; firing the HEAD call too eagerly
        // produces sporadic 401s (token mid-refresh) and was the root cause of
        // the "Failed to check for exchanges" toast users were seeing.
        //
        // `token` is intentionally NOT a dependency: the axios interceptor
        // injects the current token on every request, so when the proactive
        // refresh rotates the access token mid-exchange we don't want to flip
        // `preparingExchanges` back to true and re-mount ExchangeList
        // (which would refetch the list and re-select the first exchange).
        if (!token || !appUser)
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
    }, [appUser?.id]);
    //
    // useEffect(() => {
    //     if (exchangeId) {
    //         subscribeToExchange(exchangeId);
    //     }
    // }, [exchangeId, subscribeToExchange]);

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
        if (selectedExchangeId)
        {
            const fetchDetails = async () =>
            {
                setFetchingDetails(true);

                try
                {
                    const details = (await fetchSignedInUserAppUserExchange(selectedExchangeId)) as ExchangeDetailedDto;
                    setExchangeDetails(details);
                    setFilteredDocuments(details.documents || []);

                    const shouldApplyDeepLinkedDocument =
                        deepLinkedDocumentExchangeIdRef.current === details.id && !!deepLinkedDocumentIdRef.current;
                    const deepLinkedDocument = shouldApplyDeepLinkedDocument
                        ? (details.documents || []).find(document => document.id === deepLinkedDocumentIdRef.current)
                        : undefined;

                    const nextPreviewDocument = resolvePreviewDocumentSelection(
                        details.documents || [],
                        deepLinkedDocument?.id || selectedExchangeDocument?.id
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
                    if (isExchangeUnavailableError(error))
                    {
                        // Exchange was removed or is no longer accessible: clear stale deep-link/selection context.
                        clearUnavailableExchangeContext(selectedExchangeId);
                        return;
                    }
                    console.error(error);
                }
                finally
                {
                    setFetchingDetails(false);
                }
            };
            fetchDetails();
        }
        else
        {
            setExchangeDetails(null);
            setFetchingDetails(false);
        }
        // Intentionally only re-fetches when the *selection* changes. The
        // access token is supplied by the axios interceptor, so a silent
        // refresh shouldn't re-pull the exchange details. `appUser` only
        // affects the permissions computation, which is cheap and stable
        // for the lifetime of the page.
    }, [selectedExchangeId]);

    useEffect(() =>
    {
        setIsDocumentSidebarOpen(false);
        setSelectedExchangeDocument(undefined);
        setDetailsActiveTab('documents');
    }, [selectedExchangeId]);

    // Ensure the correct list tab is active whenever an exchange is selected.
    // This covers deep-linked exchanges, exchanges whose status changes via real-time
    // events, and any other situation where the tab may not match the exchange's status.
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

        // Update the parent's inbox role display state. An INITIATED exchange owned
        // by the current user is an outgoing draft; others are incoming.
        if (targetTab === 'inbox')
        {
            const isInitiator = appUser?.id != null && exchangeDetails.initiator?.id === appUser.id;
            setInboxRole(isInitiator ? 'outgoing' : 'incoming');
        }

        // Clear the deep-link ref now that we have applied the correct tab.
        deepLinkedExchangeIdRef.current = null;
    }, [exchangeDetails, selectedExchangeId, activeListTab]);

    // Subscribe to live document + status events for the currently-selected exchange.
    // Triggers a single refetch on relevant message types. The server-side subscription
    // is set up by SUBSCRIBE_EXCHANGE; the unsubscribe runs on exchange change.
    useEffect(() =>
    {
        if (!selectedExchangeId) return;

        const refetchDetails = async () =>
        {
            try
            {
                const details = (await fetchSignedInUserAppUserExchange(selectedExchangeId)) as ExchangeDetailedDto;
                setExchangeDetails(details);
                setFilteredDocuments(details.documents || []);
                setSelectedExchangeDocument((currentSelection) =>
                    resolvePreviewDocumentSelection(details.documents || [], currentSelection?.id)
                );
            }
            catch (error)
            {
                if (isExchangeUnavailableError(error))
                {
                    clearUnavailableExchangeContext(selectedExchangeId);
                    return;
                }
                console.error("Failed to refresh exchange after realtime event:", error);
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
            realtimeService.unsubscribeFromExchange(selectedExchangeId);
            offAdded();
            offRemoved();
            offUpdated();
        };
    }, [selectedExchangeId]);

    // Process exchange status events for all exchanges visible to this user.
    // This keeps list items/counters synced even if the changed exchange isn't selected.
    useEffect(() =>
    {
        const offStatus = realtimeService.on('EXCHANGE_STATUS_CHANGED', async (msg) =>
        {
            if (!msg.exchangeId) return;

            try
            {
                const updatedExchange = (await fetchSignedInUserAppUserExchange(msg.exchangeId)) as ExchangeDetailedDto;
                publishExchangeUpdate(updatedExchange);

                if (msg.exchangeId === selectedExchangeId)
                {
                    setExchangeDetails(updatedExchange);
                    setFilteredDocuments(updatedExchange.documents || []);
                }
            }
            catch (error)
            {
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
            offStatus();
        };
    }, [selectedExchangeId]);

    useEffect(() =>
    {
        const initiationSubscription = exchangeInitiationObservable.subscribe(exchange =>
        {
            checkAppUserExchanges();
        });

        const deletionSubscription = exchangeDeletionObservable.subscribe(exchangeId =>
        {
            checkAppUserExchanges();
        });

        return () =>
        {
            initiationSubscription.unsubscribe();
            deletionSubscription.unsubscribe();
        };
    }, []);

    const onDocumentDeleted = (documentId: string) =>
    {
        if (exchangeDetails)
        {
            setIsDocumentAddDialogOpen(false);
            const updatedDocuments = exchangeDetails.documents?.filter(document => document.id !== documentId);
            setExchangeDetails({...exchangeDetails, documents: updatedDocuments});
            setFilteredDocuments(updatedDocuments || []);
            setSelectedExchangeDocument((currentSelection) =>
                resolvePreviewDocumentSelection(updatedDocuments || [], currentSelection?.id)
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
                resolvePreviewDocumentSelection(updatedDocuments, currentSelection?.id)
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
            {
                if (updatedDocument.uploadDate)
                {
                    return resolvePreviewDocumentSelection(updatedDocuments || [], updatedDocument.id);
                }

                return resolvePreviewDocumentSelection(updatedDocuments || [], currentSelection?.id);
            });
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
    };

    const onExchangeRescinded = (exchange: ExchangeDetailedDto) =>
    {
        setExchangeDetails(exchange);
        setActiveListTab('archive');
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
                selectedExchangeDocument={selectedExchangeDocument}
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

    const onExchangeRejected = (exchange: ExchangeDetailedDto) =>
    {
        setExchangeDetails(exchange);
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
        const hasSelectedDetails = !!selectedExchangeId && !!exchangeDetails;
        const isInboxTab = activeListTab === 'inbox';
        const isActiveTab = activeListTab === 'active';
        const isArchiveTab = activeListTab === 'archive';
        const isCurrentTabEmpty = exchangeList.length === 0;
        const shouldShowIncomingInboxEmptyDetails =
            !fetchingDetails && !hasSelectedDetails && isInboxTab && inboxRole === 'incoming' && isCurrentTabEmpty;
        const shouldShowOutgoingInboxEmptyDetails =
            !fetchingDetails && !hasSelectedDetails && isInboxTab && inboxRole === 'outgoing' && isCurrentTabEmpty;
        const shouldShowActiveEmptyDetails = !fetchingDetails && !hasSelectedDetails && isActiveTab && isCurrentTabEmpty;
        const shouldShowArchiveEmptyDetails = !fetchingDetails && !hasSelectedDetails && isArchiveTab && isCurrentTabEmpty;
        const shouldShowSelectionHint =
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

        // On phones we only show ONE of [list, details] at a time. We
        // toggle visibility via classes rather than unmounting so the
        // ExchangeList preserves its fetched data / scroll / filters.
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

        // The empty-state fallbacks below (inbox empty, "select a exchange"
        // hint, etc.) belong to the desktop details column. On mobile,
        // when nothing is selected, the ExchangeList already occupies the
        // entire viewport - rendering these fallbacks as sibling flex
        // children would compete for height (their `height: 100%` rule
        // would collapse the list pane to zero). They're only meaningful
        // here on desktop where they live in a separate column.
        const showDetailsColumnFallbacks = !isMobile;

        return (
            <section className={styles.container}>

                <div className={listPaneClassName}>
                    <ExchangeList
                        onSelectionChange={changeSelectedExchangeId}
                        onExchangeListChange={setExchangeList}
                        onTabChange={setActiveListTab}
                        onInboxRoleChange={setInboxRole}
                        onTabCountsChange={setTabCounts}
                        controlledSelectedId={selectedExchangeId}
                        controlledActiveTab={activeListTab}
                    />
                </div>

                {!!selectedExchangeId && (
                    <div
                        id={`exchange-details-transition-${selectedExchangeId}`}
                        key={selectedExchangeId}
                        className={mergeClasses(styles.detailsTransitionFrame, styles.detailsSlideInFromRight)}
                    >
                        {fetchingDetails && !exchangeDetails && <ExchangeDetailsLoading/>}

                        {exchangeDetails &&
                            <div className={detailsPaneClassName}>

                                <div className={`${styles.detailsContent} ${fetchingDetails ? styles.detailsContentLoading : ''}`}>

                        {/* Single-exchange: inline banner above the exchange header so content
                            remains fully visible; no overlay needed because there's nothing
                            else to switch to. */}
                        {showAcceptance && isSingleExchange && (
                            <ExchangeAcceptanceDialog
                                exchange={exchangeDetails}
                                isSingleExchange={true}
                                canDecideLater={canDecideLater}
                                activeCount={tabCounts.active}
                                archiveCount={tabCounts.archive}
                                onAccepted={onExchangeAccepted}
                                onRejected={onExchangeRejected}
                                onDismiss={() => {}}
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
                                            onTabChange={setDetailsActiveTab}
                                            onDownloadZip={() => setIsDocumentZipDialogOpen(true)}/>

                        {detailsActiveTab === 'documents' && (
                        <div className={styles.documentsSectionContainer} id={"documentsSectionContainer"}>
                            <div className={styles.documentsSection} id={"documentsSection"}>
                                {(exchangeDetails?.documents?.length > 0) && (
                                    <ExchangeDocumentsList
                                        exchangeDetails={exchangeDetails}
                                        permissions={permissions}
                                        onFilterDocuments={onFilterDocuments}
                                        documentSearchQuery={documentSearchQuery}
                                        filteredDocuments={filteredDocuments}
                                        selectedExchangeDocument={selectedExchangeDocument}
                                        setSelectedExchangeDocument={setSelectedExchangeDocument}
                                        setSelectedUpdateExchangeDocument={setSelectedUpdateExchangeDocument}
                                        setIsUploadDocumentDialogOpen={setIsUploadDocumentDialogOpen}
                                        setIsDocumentUpdateDialogOpen={setIsUpdateDocumentDialogOpen}
                                        onDocumentDeleted={onDocumentDeleted}
                                        setIsDocumentSidebarOpen={setIsDocumentSidebarOpen}
                                    />
                                )}

                                {
                                    exchangeDetails.documents?.length === 0 &&
                                    <NoExchangeDocuments setIsDocumentAddDialogOpen={setIsDocumentAddDialogOpen}/>
                                }
                                {(selectedExchangeDocument && exchangeDetails.documents?.length === 0) &&
                                    <ExchangeDocumentPreviewer document={selectedExchangeDocument}
                                                              exchange={exchangeDetails}
                                                              canUploadDocument={!!permissions?.canUploadDocument}
                                                              onUploadDocument={() => setIsUploadDocumentDialogOpen(true)}/>
                                }

                                {selectedExchangeDocument &&
                                    <ExchangeDocumentPreviewer document={selectedExchangeDocument}
                                                              exchange={exchangeDetails}
                                                              canUploadDocument={!!permissions?.canUploadDocument}
                                                              onUploadDocument={() => setIsUploadDocumentDialogOpen(true)}/>
                                }

                            </div>
                            {selectedExchangeDocument && isDocumentSidebarOpen &&

                                <ExchangeDocumentSidebar
                                    isOpen={isDocumentSidebarOpen}
                                    onOpen={setIsDocumentSidebarOpen}
                                    exchange={exchangeDetails}
                                    exchangeDocument={selectedExchangeDocument}/>
                            }
                        </div>
                        )}

                        {detailsActiveTab === 'details' && (
                            <div className={styles.documentsSectionContainer}>
                                <div className={`${styles.documentsSection} ${styles.scrollableTabContent}`}>
                                    <ExchangeFieldsTab exchange={exchangeDetails}/>
                                </div>
                            </div>
                        )}

                        {detailsActiveTab === 'workflow' && (
                            <div className={styles.documentsSectionContainer}>
                                <div className={`${styles.documentsSection} ${styles.scrollableTabContent}`}>
                                    <ExchangeWorkflowTab exchange={exchangeDetails}/>
                                </div>
                            </div>
                        )}

                        {detailsActiveTab === 'audit' && (
                            <div className={styles.documentsSectionContainer}>
                                <div className={`${styles.documentsSection} ${styles.scrollableTabContent}`}>
                                    <ExchangeAuditTab exchange={exchangeDetails}/>
                                </div>
                            </div>
                        )}

                        {/* Multi-exchange: absolute overlay covering detailsContainer.
                            "Decide Later" advances to the next exchange; navigating
                            back to this exchange resets dismissal and shows the
                            overlay again. */}
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
                                    <div className={styles.detailsLoadingOverlay}>
                                        <Spinner size="small" label="Loading exchange..." labelPosition="after"/>
                                    </div>
                                )}
                            </div>
                        }
                    </div>
                )}
                {showDetailsColumnFallbacks && shouldShowIncomingInboxEmptyDetails &&
                    <div className={styles.noExchangeSelectedSection}>
                        <div className={styles.inboxEmptyDetailsContent}>
                            <EmptyStateIllustration className={styles.inboxEmptyIllustration}/>
                            <Text size={600} weight={"semibold"} align={"center"}>No incoming requests</Text>
                            <Text size={300} align={"center"}>
                                You have no pending requests right now. You have <Text
                                weight={"semibold"}>{getExchangeCountLabel(tabCounts.active, 'active')}</Text>
                                {' '}and <Text
                                weight={"semibold"}>{getExchangeCountLabel(tabCounts.archive, 'archived')}</Text>.
                                Open <Text weight={"semibold"}>Active</Text> or <Text weight={"semibold"}>Archive</Text>,
                                or start a new <Text weight={"semibold"}>Request or Send</Text> from Start Exchange.
                            </Text>
                            <div className={styles.inboxEmptyActions}>
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
                    <div className={styles.noExchangeSelectedSection}>
                        <div className={styles.inboxEmptyDetailsContent}>
                            <EmptyStateIllustration className={styles.inboxEmptyIllustration}/>
                            <Text size={600} weight={"semibold"} align={"center"}>No outgoing requests</Text>
                            <Text size={300} align={"center"}>
                                You do not have requests awaiting recipient response right now. You have <Text
                                weight={"semibold"}>{getExchangeCountLabel(tabCounts.active, 'active')}</Text>
                                {' '}and <Text
                                weight={"semibold"}>{getExchangeCountLabel(tabCounts.archive, 'archived')}</Text>.
                                Open <Text weight={"semibold"}>Active</Text> or <Text weight={"semibold"}>Archive</Text>,
                                or start a new <Text weight={"semibold"}>Request or Send</Text> from Start Exchange.
                            </Text>
                            <div className={styles.inboxEmptyActions}>
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
                    <div className={styles.noExchangeSelectedSection}>
                        <div className={styles.inboxEmptyDetailsContent}>
                            <EmptyStateIllustration className={styles.inboxEmptyIllustration}/>
                            <Text size={600} weight={"semibold"} align={"center"}>No active exchanges</Text>
                            <Text size={300} align={"center"}>
                                You do not have active exchanges right now. Check <Text weight={"semibold"}>Inbox</Text>
                                {' '}for new requests or review <Text weight={"semibold"}>Archive</Text>.
                            </Text>
                            <div className={styles.inboxEmptyActions}>
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
                    <div className={styles.noExchangeSelectedSection}>
                        <div className={styles.inboxEmptyDetailsContent}>
                            <EmptyStateIllustration className={styles.inboxEmptyIllustration}/>
                            <Text size={600} weight={"semibold"} align={"center"}>Archive is empty</Text>
                            <Text size={300} align={"center"}>
                                You do not have archived exchanges yet. Check <Text weight={"semibold"}>Inbox</Text>
                                {' '}for pending requests or open <Text weight={"semibold"}>Active</Text> exchanges.
                            </Text>
                            <div className={styles.inboxEmptyActions}>
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
                    <div className={styles.noExchangeSelectedSection}>
                        <Text size={500}> Select a Exchange in the list to view details</Text>
                    </div>
                }

                {renderDialogs()}

            </section>
        )
    }

    return (
        <>
            <Toaster toasterId={toasterId} position="bottom-end"/>
            {preparingExchanges && <ExchangePreLoader/>}
            {!preparingExchanges && (appUserHasExchanges) && renderExchangesSection()}
            {!preparingExchanges && (!appUserHasExchanges) &&
                <div className={styles.containerNoExchanges}>
                    <EmptyStateIllustration className={styles.noExchangesIllustration}/>
                    <Text size={600} weight={"semibold"} align={"center"}>
                        Share your first document
                    </Text>
                    <Text size={300} align={"center"}>
                        Send or receive documents securely to anyone,  your exchanges will appear here.
                        Tap <Text italic weight={"semibold"}>Start Exchange</Text> in the main menu to begin.
                    </Text>
                </div>
            }
        </>
    );
};

export default Exchanges;
