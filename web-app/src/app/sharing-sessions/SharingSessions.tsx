import React, {useEffect, useRef, useState} from 'react';
import {
    checkSignedInAppUserHasSharingSessions,
    fetchSignedInUserAppUserSharingSession
} from "../../services/sharingSessionApi.ts";
import useToken from "../../context/useToken.tsx";
import SessionPreLoader from "./components/session-pre-loader/SessionPreLoader.tsx";
import {Button, InputOnChangeData, SearchBoxChangeEvent, Spinner, Text} from "@fluentui/react-components";
import {
    DocumentDetailedDto,
    SharingSessionBasicDto,
    SharingSessionDetailedDto,
    SharingSessionStatus
} from "../models/models.tsx";
import {useSharingSessionsStyles} from "./SharingSessionsStyles.tsx";
import SessionDocumentSidebar from "./components/session-document-sidebar/SessionDocumentSidebar.tsx";
import NoSessionDocuments from "./components/session-documents-none/NoSessionDocuments.tsx";
import SessionDialogsGroup from "./components/session-dialog-group/SessionDialogsGroup.tsx";
import SessionDetailsHeader from "./components/session-details-header/SessionDetailsHeader.tsx";
import SessionDetailsLoading from "./components/session-details-loading/SessionDetailsLoading.tsx";
import "react-pdf/dist/esm/Page/AnnotationLayer.css";
import "react-pdf/dist/esm/Page/TextLayer.css";
import SessionList from "./components/session-list/SessionList.tsx";
import {useAuth} from "../../context/AuthContext.tsx";
import {getPermissions, SharingSessionPermissions} from "./SessionPermissions.ts";
import SessionDocumentsList from "./components/session-document-list/SessionDocumentsList.tsx";
import MainMenu from "../components/MainMenu.tsx";
import {realtimeService} from "../../services/NotificationService";
import {
    publishRecreateRejectedSession,
    publishSharingSessionUpdate,
    sharingSessionDeletionObservable,
    sharingSessionInitiationObservable
} from "../observable/sharingSessionObservables.ts";
import SessionDocumentPreviewer from "./components/session-document-preview/SessionDocumentPreviewer.tsx";
import SessionAcceptanceDialog from "./components/session-acceptance-dialog/SessionAcceptanceDialog.tsx";
import SessionInitiationDialogTrigger
    from "../sharing-session-initiation/components/session-initiation-dialog-trigger/SessionInitiationDialogTrigger.tsx";
import useSharingSessionInitiatingState from "../sharing-session-initiation/hooks/useSharingSessionInitiatingState.ts";
import EmptyStateIllustration from "./components/empty-state-illustration/EmptyStateIllustration.tsx";
import {SessionListTab} from "./components/session-list/session-list-tabs/SessionListTabs.tsx";
import {InboxRole, SessionTabCounts} from "./components/session-list/SessionList.tsx";

const SharingSessions: React.FC = () =>
{
    const styles = useSharingSessionsStyles();
    const [sharingSessionList, setSharingSessionList] = useState<SharingSessionBasicDto[]>([]);
    const [activeListTab, setActiveListTab] = useState<SessionListTab>('inbox');
    const [inboxRole, setInboxRole] = useState<InboxRole>('incoming');
    const [tabCounts, setTabCounts] = useState<SessionTabCounts>({inbox: 0, active: 0, archive: 0});
    const [selectedSessionId, setSelectedSessionId] = useState<string | null>(null);
    const [preparingSharingSessions, setPreparingSharingSessions] = useState<boolean>(true);
    const token = useToken();
    const {appUser} = useAuth();
    const [isDocumentSidebarOpen, setIsDocumentSidebarOpen] = React.useState(false);
    const [isDocumentAddDialogOpen, setIsDocumentAddDialogOpen] = React.useState(false);
    const [isUploadDocumentDialogOpen, setIsUploadDocumentDialogOpen] = React.useState(false);
    const [isUpdateDocumentDialogOpen, setIsUpdateDocumentDialogOpen] = React.useState(false);
    const [isDocumentZipDialogOpen, setIsDocumentZipDialogOpen] = React.useState(false);
    const [isSessionEditDialogOpen, setIsSessionEditDialogOpen] = React.useState(false);
    const [isSessionDetailedViewDialogOpen, setIsSessionDetailedViewDialogOpen] = React.useState(false);
    const [isSessionAccessManagementDialogOpen, setIsSessionAccessManagementDialogOpen] = React.useState(false);
    const [isDeletedSessionDialogOpen, setIsDeletedSessionDialogOpen] = React.useState(false);
    const [isSessionEndDialogOpen, setIsSessionEndDialogOpen] = React.useState(false);
    const [selectedSessionDocument, setSelectedSessionDocument] = React.useState<DocumentDetailedDto | undefined>(undefined);
    const [selectedUpdateSessionDocument, setSelectedUpdateSessionDocument] = React.useState<DocumentDetailedDto>(undefined);
    const [sessionDetails, setSessionDetails] = useState<SharingSessionDetailedDto | null>(null);
    const [fetchingDetails, setFetchingDetails] = useState<boolean>(true);
    const [isSessionEnded, setIsSessionEnded] = React.useState(false);
    const [filteredDocuments, setFilteredDocuments] = useState<DocumentDetailedDto[]>([]);
    const [appUserHasSessions, setAppUserHasSessions] = useState<boolean>(false);
    const [permissions, setPermissions] = useState<SharingSessionPermissions>();
    const sharingInitiationTriggerRef = useRef<HTMLButtonElement>(null);
    const deepLinkedSessionIdRef = useRef<string | null>(null);

    const checkAppUserSessions = async () =>
    {
        try
        {
            const hasSessions = await checkSignedInAppUserHasSharingSessions(token);
            setAppUserHasSessions(hasSessions);
        }
        catch (error)
        {
            // Don't block the page with an alert(),  log and degrade gracefully.
            // A failing existence-check is annoying, not fatal: we still render
            // the page with the "no sessions yet" empty state. If sessions
            // genuinely exist, they'll appear once the user navigates back or
            // once a subsequent list fetch succeeds.
            console.error("Could not determine whether user has sharing sessions:", error);
            setAppUserHasSessions(false);
        }
        finally
        {
            setPreparingSharingSessions(false);
        }
    }

    // Pre-select a session passed via the email link (?s=<sessionId>).
    useEffect(() =>
    {
        const params = new URLSearchParams(window.location.search);
        const deepLinkedId = params.get('s');
        if (deepLinkedId)
        {
            deepLinkedSessionIdRef.current = deepLinkedId;
            setSelectedSessionId(deepLinkedId);
        }
    }, []);

    useEffect(() =>
    {
        // Wait for both the access token AND the signed-in AppUser to be
        // available before hitting the backend. Right after sign-in/onboarding
        // the AuthContext is still settling; firing the HEAD call too eagerly
        // produces sporadic 401s (token mid-refresh) and was the root cause of
        // the "Failed to check for sharing sessions" toast users were seeing.
        //
        // `token` is intentionally NOT a dependency: the axios interceptor
        // injects the current token on every request, so when the proactive
        // refresh rotates the access token mid-session we don't want to flip
        // `preparingSharingSessions` back to true and re-mount SessionList
        // (which would refetch the list and re-select the first session).
        if (!token || !appUser)
        {
            return;
        }

        let cancelled = false;
        (async () =>
        {
            if (cancelled) return;
            await checkAppUserSessions();
        })();

        return () =>
        {
            cancelled = true;
        };
    }, [appUser?.id]);
    //
    // useEffect(() => {
    //     if (sessionId) {
    //         subscribeToSession(sessionId);
    //     }
    // }, [sessionId, subscribeToSession]);

    useEffect(() =>
    {
        if (selectedSessionId)
        {
            const fetchDetails = async () =>
            {
                setFetchingDetails(true);

                try
                {
                    const details = (await fetchSignedInUserAppUserSharingSession(selectedSessionId)) as SharingSessionDetailedDto;
                    setSessionDetails(details);
                    setFilteredDocuments(details.documents || []);

                    const uploadedDocuments = (details.documents || [])
                        .filter(document => !!document.uploadDate)
                        .sort((a, b) => new Date(b.uploadDate).getTime() - new Date(a.uploadDate).getTime());

                    const matchingSelectedDocument = (details.documents || []).find(document => document.id === selectedSessionDocument?.id);
                    if (matchingSelectedDocument)
                    {
                        setSelectedSessionDocument(matchingSelectedDocument);
                    }
                    else
                    {
                        // Keep a document selected after loading so the list has a clear preview target.
                        setSelectedSessionDocument(uploadedDocuments[0]);
                    }

                    if (details?.documents?.length > 0 && appUser?.settings?.autoPreviewDocuments)
                    {
                        for (let i = 0; i < uploadedDocuments.length; i++)
                        {
                            const document = uploadedDocuments[i];
                            if (document.uploadDate)
                            {
                                setSelectedSessionDocument(document);
                                break;
                            }
                        }
                    }

                    const newPermissions = getPermissions(details, appUser);
                    setPermissions(newPermissions);
                }
                catch (error)
                {
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
            setSessionDetails(null);
            setFetchingDetails(false);
        }
        // Intentionally only re-fetches when the *selection* changes. The
        // access token is supplied by the axios interceptor, so a silent
        // refresh shouldn't re-pull the session details. `appUser` only
        // affects the permissions computation, which is cheap and stable
        // for the lifetime of the page.
    }, [selectedSessionId]);

    useEffect(() =>
    {
        setIsDocumentSidebarOpen(false);
        setSelectedSessionDocument(undefined);
    }, [selectedSessionId]);

    useEffect(() =>
    {
        if (sessionDetails)
        {
            setIsSessionEnded(sessionDetails.status == SharingSessionStatus.ENDED);
        }
    }, [sessionDetails]);

    useEffect(() =>
    {
        if (!sessionDetails)
        {
            setPermissions(undefined);
            return;
        }

        setPermissions(getPermissions(sessionDetails, appUser));
    }, [sessionDetails, appUser?.id]);

    useEffect(() =>
    {
        const deepLinkedId = deepLinkedSessionIdRef.current;
        if (!deepLinkedId || !selectedSessionId || !sessionDetails) return;
        if (selectedSessionId !== deepLinkedId || sessionDetails.id !== deepLinkedId) return;

        let targetTab: SessionListTab = 'inbox';
        if (sessionDetails.status === SharingSessionStatus.ACCEPTED_STARTED)
        {
            targetTab = 'active';
        }
        else if (
            sessionDetails.status === SharingSessionStatus.ENDED ||
            sessionDetails.status === SharingSessionStatus.REJECTED
        )
        {
            targetTab = 'archive';
        }

        if (activeListTab !== targetTab)
        {
            setActiveListTab(targetTab);
        }

        // Apply only once for the deep-link landing flow.
        deepLinkedSessionIdRef.current = null;
    }, [sessionDetails, selectedSessionId, activeListTab]);

    // Subscribe to live document + status events for the currently-selected session.
    // Triggers a single refetch on relevant message types. The server-side subscription
    // is set up by SUBSCRIBE_SHARING_SESSION; the unsubscribe runs on session change.
    useEffect(() =>
    {
        if (!selectedSessionId) return;

        const refetchDetails = async () =>
        {
            try
            {
                const details = (await fetchSignedInUserAppUserSharingSession(selectedSessionId)) as SharingSessionDetailedDto;
                setSessionDetails(details);
                setFilteredDocuments(details.documents || []);
            }
            catch (error)
            {
                console.error("Failed to refresh sharing session after realtime event:", error);
            }
        };

        const handler = (msg: { sharingSessionId?: string }) =>
        {
            if (msg.sharingSessionId === selectedSessionId)
            {
                refetchDetails();
            }
        };


        const offAdded = realtimeService.on('SHARING_SESSION_DOCUMENT_ADDED', handler);
        const offRemoved = realtimeService.on('SHARING_SESSION_DOCUMENT_REMOVED', handler);
        const offUpdated = realtimeService.on('SHARING_SESSION_DOCUMENT_UPDATED', handler);
        realtimeService.subscribeToSharingSession(selectedSessionId);

        return () =>
        {
            realtimeService.unsubscribeFromSharingSession(selectedSessionId);
            offAdded();
            offRemoved();
            offUpdated();
        };
    }, [selectedSessionId]);

    // Process session status events for all sessions visible to this user.
    // This keeps list items/counters synced even if the changed session isn't selected.
    useEffect(() =>
    {
        const offStatus = realtimeService.on('SHARING_SESSION_STATUS_CHANGED', async (msg) =>
        {
            if (!msg.sharingSessionId) return;

            try
            {
                const updatedSession = (await fetchSignedInUserAppUserSharingSession(msg.sharingSessionId)) as SharingSessionDetailedDto;
                publishSharingSessionUpdate(updatedSession);

                if (msg.sharingSessionId === selectedSessionId)
                {
                    setSessionDetails(updatedSession);
                    setFilteredDocuments(updatedSession.documents || []);
                }
            }
            catch (error)
            {
                console.error("Failed to process realtime status change:", error);
            }
        });

        return () =>
        {
            offStatus();
        };
    }, [selectedSessionId]);

    useEffect(() =>
    {
        const initiationSubscription = sharingSessionInitiationObservable.subscribe(session =>
        {
            checkAppUserSessions();
        });

        const deletionSubscription = sharingSessionDeletionObservable.subscribe(sessionId =>
        {
            checkAppUserSessions();
        });

        return () =>
        {
            initiationSubscription.unsubscribe();
            deletionSubscription.unsubscribe();
        };
    }, []);

    const onDocumentDeleted = (documentId: string) =>
    {
        if (sessionDetails)
        {
            setIsDocumentAddDialogOpen(false);
            const updatedDocuments = sessionDetails.documents?.filter(document => document.id !== documentId);
            setSessionDetails({...sessionDetails, documents: updatedDocuments});
            setFilteredDocuments(updatedDocuments || []);
        }
    };

    const onNewDocumentAdded = (newSessionDocument: DocumentDetailedDto) =>
    {
        if (sessionDetails)
        {
            const currentDocuments = (sessionDetails.documents && sessionDetails.documents.length) ? sessionDetails.documents : [];
            const updatedDocuments = [...currentDocuments, newSessionDocument];
            setSessionDetails({...sessionDetails, documents: updatedDocuments});
            setFilteredDocuments(updatedDocuments);
        }
    };

    const onDocumentUploaded = (uploadedDocument: DocumentDetailedDto) =>
    {
        onDocumentUpdated(uploadedDocument);
    };

    const onDocumentUpdated = (updatedDocument: DocumentDetailedDto) =>
    {
        if (sessionDetails)
        {
            const updatedDocuments = sessionDetails.documents?.map(document =>
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
            setSessionDetails({...sessionDetails, documents: updatedDocuments});
            setFilteredDocuments(updatedDocuments || []);

            if (selectedSessionDocument?.id === updatedDocument.id)
            {
                setSelectedSessionDocument(prev => prev ? ({...prev, ...updatedDocument}) : updatedDocument);
            }
        }
    };

    const onSessionDeleted = (sessionId: string) =>
    {
        if (sessionDetails && sessionDetails.id === sessionId)
        {
            setSessionDetails(undefined);
            setSelectedSessionId(undefined);
        }
    };

    const onSessionEnded = (session: SharingSessionDetailedDto) =>
    {
        setSessionDetails(session);
    };

    const onSessionEdited = (session: SharingSessionDetailedDto) =>
    {
        setSessionDetails(session);
    };

    const onSessionAccessManagementUpdated = (session: SharingSessionDetailedDto) =>
    {
        setSessionDetails(session);
    };

    const handleStartSharingClick = () =>
    {
        sharingInitiationTriggerRef.current?.click();
    };

    const getSessionCountLabel = (count: number, type: 'active' | 'archived') =>
    {
        return `${count} ${type} ${count === 1 ? 'session' : 'sessions'}`;
    };

    const renderDialogs = () =>
    {
        return (
            <SessionDialogsGroup
                isDeletedSessionDialogOpen={isDeletedSessionDialogOpen}
                setIsDeletedSessionDialogOpen={setIsDeletedSessionDialogOpen}
                isSessionEndDialogOpen={isSessionEndDialogOpen}
                setIsSessionEndDialogOpen={setIsSessionEndDialogOpen}
                isDocumentAddDialogOpen={isDocumentAddDialogOpen}
                setIsDocumentAddDialogOpen={setIsDocumentAddDialogOpen}
                isUploadDocumentDialogOpen={isUploadDocumentDialogOpen}
                setIsUploadDocumentDialogOpen={setIsUploadDocumentDialogOpen}
                isUpdateDocumentDialogOpen={isUpdateDocumentDialogOpen}
                setIsUpdateDocumentDialogOpen={setIsUpdateDocumentDialogOpen}
                isDocumentZipDialogOpen={isDocumentZipDialogOpen}
                setIsDocumentZipDialogOpen={setIsDocumentZipDialogOpen}
                isSessionEditDialogOpen={isSessionEditDialogOpen}
                setIsSessionEditDialogOpen={setIsSessionEditDialogOpen}
                isSessionDetailedViewDialogOpen={isSessionDetailedViewDialogOpen}
                setIsSessionDetailedViewDialogOpen={setIsSessionDetailedViewDialogOpen}
                isSessionAccessManagementDialogOpen={isSessionAccessManagementDialogOpen}
                setIsSessionAccessManagementDialogOpen={setIsSessionAccessManagementDialogOpen}
                sessionDetails={sessionDetails}
                selectedSessionId={selectedSessionId}
                selectedSessionDocument={selectedSessionDocument}
                selectedUpdateSessionDocument={selectedUpdateSessionDocument}
                setSelectedUpdateSessionDocument={setSelectedUpdateSessionDocument}
                onNewDocumentAdded={onNewDocumentAdded}
                onDocumentUploaded={onDocumentUploaded}
                onDocumentUpdated={onDocumentUpdated}
                onSessionDeleted={onSessionDeleted}
                onSessionEnded={onSessionEnded}
                onSessionEdited={onSessionEdited}
                onSessionAccessManagementUpdated={onSessionAccessManagementUpdated}
            />
        );
    };

    const onFilterDocuments = (event: SearchBoxChangeEvent, data: InputOnChangeData) =>
    {
        const query = data.value.toLowerCase();

        if (query === "")
        {
            setFilteredDocuments(sessionDetails?.documents || []);
            return;
        }

        const filtered = sessionDetails?.documents?.filter(document =>
            document.title.toLowerCase().includes(query)
        );

        setFilteredDocuments(filtered || []);
    };

    const onSessionAccepted = (session: SharingSessionDetailedDto) =>
    {
        setSessionDetails(session);
        setActiveListTab('active');
        setSelectedSessionId(session.id);
    };

    const onSessionRejected = (session: SharingSessionDetailedDto) =>
    {
        setSessionDetails(session);
    };

    const onRecreateRejectedSession = (session: SharingSessionDetailedDto) =>
    {
        publishRecreateRejectedSession({
            sessionName: session.sessionName || '',
            description: session.description || '',
            initialShareMessage: session.initialShareMessage || '',
            requestRecipientSignIn: !!session.requestRecipientSignIn,
            allowDocumentAddition: !!session.allowDocumentAddition,
            allowDocumentDeletion: !!session.allowDocumentDeletion,
            allowDocumentDownload: !!session.allowDocumentDownload,
            allowDocumentUpdate: !!session.allowDocumentUpdate,
            allowDocumentUpload: !!session.allowDocumentUpload,
            sessionDocuments: (session.documents || []).map(doc => ({
                title: doc.title || '',
                restrictedType: doc.restrictedType,
                restrictType: !!doc.restrictType,
            })),
            recipientUser: session.recipient,
            recipientEmail: session.recipient?.email,
            recipientFirstName: session.recipient?.person?.firstName,
            recipientLastName: session.recipient?.person?.lastName,
        });
    };

    const onDecideLater = () =>
    {
        if (!selectedSessionId || activeListTab !== 'inbox') return;

        const currentIndex = sharingSessionList.findIndex(s => s.id === selectedSessionId);
        if (currentIndex < 0 || currentIndex >= sharingSessionList.length - 1) return;

        const nextIndex = currentIndex + 1;
        setSelectedSessionId(sharingSessionList[nextIndex].id);
    };

    const renderSessionsSection = () =>
    {
        const hasSelectedDetails = !!selectedSessionId && !!sessionDetails;
        const isInboxTab = activeListTab === 'inbox';
        const isActiveTab = activeListTab === 'active';
        const isArchiveTab = activeListTab === 'archive';
        const isCurrentTabEmpty = sharingSessionList.length === 0;
        const shouldShowIncomingInboxEmptyDetails =
            !fetchingDetails && !hasSelectedDetails && isInboxTab && inboxRole === 'incoming' && isCurrentTabEmpty;
        const shouldShowOutgoingInboxEmptyDetails =
            !fetchingDetails && !hasSelectedDetails && isInboxTab && inboxRole === 'outgoing' && isCurrentTabEmpty;
        const shouldShowActiveEmptyDetails = !fetchingDetails && !hasSelectedDetails && isActiveTab && isCurrentTabEmpty;
        const shouldShowArchiveEmptyDetails = !fetchingDetails && !hasSelectedDetails && isArchiveTab && isCurrentTabEmpty;
        const shouldShowSelectionHint =
            !fetchingDetails &&
            !selectedSessionId &&
            !sessionDetails &&
            !shouldShowIncomingInboxEmptyDetails &&
            !shouldShowOutgoingInboxEmptyDetails &&
            !shouldShowActiveEmptyDetails &&
            !shouldShowArchiveEmptyDetails;

        const showAcceptance =
            sessionDetails != null &&
            sessionDetails.status === SharingSessionStatus.INITIATED &&
            appUser?.id !== sessionDetails.initiator?.id;

        const currentInboxIndex = selectedSessionId
            ? sharingSessionList.findIndex(session => session.id === selectedSessionId)
            : -1;
        const canDecideLater =
            activeListTab === 'inbox' &&
            currentInboxIndex > -1 &&
            currentInboxIndex < sharingSessionList.length - 1;

        const isSingleSession = sharingSessionList.length === 1;

        return (
            <section className={styles.container}>

                <SessionList
                    onSelectionChange={setSelectedSessionId}
                    onSessionListChange={setSharingSessionList}
                    onTabChange={setActiveListTab}
                    onInboxRoleChange={setInboxRole}
                    onTabCountsChange={setTabCounts}
                    controlledSelectedId={selectedSessionId}
                    controlledActiveTab={activeListTab}
                />

                {fetchingDetails && !!selectedSessionId && !sessionDetails && <SessionDetailsLoading/>}

                {(selectedSessionId && sessionDetails) &&
                    <div className={styles.detailsContainer}>

                        <div className={`${styles.detailsContent} ${fetchingDetails ? styles.detailsContentLoading : ''}`}>

                        {/* Single-session: inline banner above the session header so content
                            remains fully visible; no overlay needed because there's nothing
                            else to switch to. */}
                        {showAcceptance && isSingleSession && (
                            <SessionAcceptanceDialog
                                session={sessionDetails}
                                isSingleSession={true}
                                canDecideLater={canDecideLater}
                                activeCount={tabCounts.active}
                                archiveCount={tabCounts.archive}
                                onAccepted={onSessionAccepted}
                                onRejected={onSessionRejected}
                                onDismiss={() => {}}
                                onOpenActive={() => setActiveListTab('active')}
                                onOpenArchive={() => setActiveListTab('archive')}
                            />
                        )}

                        <SessionDetailsHeader
                            sessionDetails={sessionDetails}
                            setIsDocumentAddDialogOpen={setIsDocumentAddDialogOpen}
                            setIsSessionEndDialogOpen={setIsSessionEndDialogOpen}
                            setIsDeletedSessionDialogOpen={setIsDeletedSessionDialogOpen}
                            setIsSessionEditDialogOpen={setIsSessionEditDialogOpen}
                            setIsSessionDetailedViewDialogOpen={setIsSessionDetailedViewDialogOpen}
                            setIsSessionAccessManagementDialogOpen={setIsSessionAccessManagementDialogOpen}
                            sessionPermissions={permissions}
                            onRecreateRejectedSession={onRecreateRejectedSession}
                        />
                        <div className={styles.documentsSectionContainer} id={"documentsSectionContainer"}>
                            <div className={styles.documentsSection} id={"documentsSection"}>
                                {(sessionDetails?.documents?.length > 0) && (
                                    <SessionDocumentsList
                                        sessionDetails={sessionDetails}
                                        permissions={permissions}
                                        onFilterDocuments={onFilterDocuments}
                                        filteredDocuments={filteredDocuments}
                                        selectedSessionDocument={selectedSessionDocument}
                                        setSelectedSessionDocument={setSelectedSessionDocument}
                                        setSelectedUpdateSessionDocument={setSelectedUpdateSessionDocument}
                                        setIsUploadDocumentDialogOpen={setIsUploadDocumentDialogOpen}
                                        setIsDocumentUpdateDialogOpen={setIsUpdateDocumentDialogOpen}
                                        setIsDocumentAddDialogOpen={setIsDocumentAddDialogOpen}
                                        setIsDocumentZipDialogOpen={setIsDocumentZipDialogOpen}
                                        onDocumentDeleted={onDocumentDeleted}
                                        onDocumentUpdated={onDocumentUpdated}
                                        onNewDocumentAdded={onNewDocumentAdded}
                                        onDocumentUploaded={onDocumentUploaded}
                                        setIsDocumentSidebarOpen={setIsDocumentSidebarOpen}
                                    />
                                )}

                                {
                                    sessionDetails.documents?.length === 0 &&
                                    <NoSessionDocuments setIsDocumentAddDialogOpen={setIsDocumentAddDialogOpen}/>
                                }
                                {(selectedSessionDocument && sessionDetails.documents?.length === 0) &&
                                    <SessionDocumentPreviewer document={selectedSessionDocument}
                                                              session={sessionDetails}
                                                              canUploadDocument={!!permissions?.canAddSessionDocument}
                                                              onUploadDocument={() => setIsUploadDocumentDialogOpen(true)}/>
                                }

                                {selectedSessionDocument &&
                                    <SessionDocumentPreviewer document={selectedSessionDocument}
                                                              session={sessionDetails}
                                                              canUploadDocument={!!permissions?.canAddSessionDocument}
                                                              onUploadDocument={() => setIsUploadDocumentDialogOpen(true)}/>
                                }

                            </div>
                            {selectedSessionDocument && isDocumentSidebarOpen &&

                                <SessionDocumentSidebar
                                    isOpen={isDocumentSidebarOpen}
                                    onOpen={setIsDocumentSidebarOpen}
                                    session={sessionDetails}
                                    sessionDocument={selectedSessionDocument}/>
                            }
                        </div>

                        {/* Multi-session: absolute overlay covering detailsContainer.
                            "Decide Later" advances to the next session; navigating
                            back to this session resets dismissal and shows the
                            overlay again. */}
                        {showAcceptance && !isSingleSession && (
                            <SessionAcceptanceDialog
                                session={sessionDetails}
                                isSingleSession={false}
                                canDecideLater={canDecideLater}
                                activeCount={tabCounts.active}
                                archiveCount={tabCounts.archive}
                                onAccepted={onSessionAccepted}
                                onRejected={onSessionRejected}
                                onDismiss={onDecideLater}
                                onOpenActive={() => setActiveListTab('active')}
                                onOpenArchive={() => setActiveListTab('archive')}
                            />
                        )}
                        </div>

                        {fetchingDetails && (
                            <div className={styles.detailsLoadingOverlay}>
                                <Spinner size="small" label="Loading session..." labelPosition="after"/>
                            </div>
                        )}
                    </div>
                }
                {shouldShowIncomingInboxEmptyDetails &&
                    <div className={styles.noSessionSelectedSection}>
                        <div className={styles.inboxEmptyDetailsContent}>
                            <EmptyStateIllustration className={styles.inboxEmptyIllustration}/>
                            <Text size={600} weight={"semibold"} align={"center"}>No incoming requests</Text>
                            <Text size={300} align={"center"}>
                                You have no pending requests right now. You have <Text
                                weight={"semibold"}>{getSessionCountLabel(tabCounts.active, 'active')}</Text>
                                {' '}and <Text
                                weight={"semibold"}>{getSessionCountLabel(tabCounts.archive, 'archived')}</Text>.
                                Open <Text weight={"semibold"}>Active</Text> or <Text weight={"semibold"}>Archive</Text>,
                                or start a new <Text weight={"semibold"}>Request or Send</Text> from Start Sharing.
                            </Text>
                            <div className={styles.inboxEmptyActions}>
                                <Button
                                    appearance="primary"
                                    disabled={tabCounts.active === 0}
                                    onClick={() => setActiveListTab('active')}>
                                    Open Active ({tabCounts.active})
                                </Button>
                                <Button
                                    appearance="secondary"
                                    disabled={tabCounts.archive === 0}
                                    onClick={() => setActiveListTab('archive')}>
                                    Open Archive ({tabCounts.archive})
                                </Button>
                            </div>
                        </div>
                    </div>
                }

                {shouldShowOutgoingInboxEmptyDetails &&
                    <div className={styles.noSessionSelectedSection}>
                        <div className={styles.inboxEmptyDetailsContent}>
                            <EmptyStateIllustration className={styles.inboxEmptyIllustration}/>
                            <Text size={600} weight={"semibold"} align={"center"}>No outgoing requests</Text>
                            <Text size={300} align={"center"}>
                                You do not have requests awaiting recipient response right now. You have <Text
                                weight={"semibold"}>{getSessionCountLabel(tabCounts.active, 'active')}</Text>
                                {' '}and <Text
                                weight={"semibold"}>{getSessionCountLabel(tabCounts.archive, 'archived')}</Text>.
                                Open <Text weight={"semibold"}>Active</Text> or <Text weight={"semibold"}>Archive</Text>,
                                or start a new <Text weight={"semibold"}>Request or Send</Text> from Start Sharing.
                            </Text>
                            <div className={styles.inboxEmptyActions}>
                                <Button
                                    appearance="primary"
                                    disabled={tabCounts.active === 0}
                                    onClick={() => setActiveListTab('active')}>
                                    Open Active ({tabCounts.active})
                                </Button>
                                <Button
                                    appearance="secondary"
                                    disabled={tabCounts.archive === 0}
                                    onClick={() => setActiveListTab('archive')}>
                                    Open Archive ({tabCounts.archive})
                                </Button>
                            </div>
                        </div>
                    </div>
                }

                {shouldShowActiveEmptyDetails &&
                    <div className={styles.noSessionSelectedSection}>
                        <div className={styles.inboxEmptyDetailsContent}>
                            <EmptyStateIllustration className={styles.inboxEmptyIllustration}/>
                            <Text size={600} weight={"semibold"} align={"center"}>No active sessions</Text>
                            <Text size={300} align={"center"}>
                                You do not have active sessions right now. Check <Text weight={"semibold"}>Inbox</Text>
                                {' '}for new requests or review <Text weight={"semibold"}>Archive</Text>.
                            </Text>
                            <div className={styles.inboxEmptyActions}>
                                <Button
                                    appearance="primary"
                                    disabled={tabCounts.inbox === 0}
                                    onClick={() => setActiveListTab('inbox')}>
                                    Open Inbox ({tabCounts.inbox})
                                </Button>
                                <Button
                                    appearance="secondary"
                                    disabled={tabCounts.archive === 0}
                                    onClick={() => setActiveListTab('archive')}>
                                    Open Archive ({tabCounts.archive})
                                </Button>
                            </div>
                        </div>
                    </div>
                }

                {shouldShowArchiveEmptyDetails &&
                    <div className={styles.noSessionSelectedSection}>
                        <div className={styles.inboxEmptyDetailsContent}>
                            <EmptyStateIllustration className={styles.inboxEmptyIllustration}/>
                            <Text size={600} weight={"semibold"} align={"center"}>Archive is empty</Text>
                            <Text size={300} align={"center"}>
                                You do not have archived sessions yet. Check <Text weight={"semibold"}>Inbox</Text>
                                {' '}for pending requests or open <Text weight={"semibold"}>Active</Text> sessions.
                            </Text>
                            <div className={styles.inboxEmptyActions}>
                                <Button
                                    appearance="primary"
                                    disabled={tabCounts.inbox === 0}
                                    onClick={() => setActiveListTab('inbox')}>
                                    Open Inbox ({tabCounts.inbox})
                                </Button>
                                <Button
                                    appearance="secondary"
                                    disabled={tabCounts.active === 0}
                                    onClick={() => setActiveListTab('active')}>
                                    Open Active ({tabCounts.active})
                                </Button>
                            </div>
                        </div>
                    </div>
                }

                {shouldShowSelectionHint &&
                    <div className={styles.noSessionSelectedSection}>
                        <Text size={500}> Select a Sharing Session in the list to view details</Text>
                    </div>
                }

                {renderDialogs()}

            </section>
        )
    }

    return (
        <>
            <MainMenu/>
            {preparingSharingSessions && <SessionPreLoader/>}
            {!preparingSharingSessions && (appUserHasSessions) && renderSessionsSection()}
            {!preparingSharingSessions && (!appUserHasSessions) &&
                <div className={styles.containerNoSessions}>
                    <EmptyStateIllustration className={styles.noSessionsIllustration}/>
                    <Text size={600} weight={"semibold"} align={"center"}>
                        Share your first document
                    </Text>
                    <Text size={300} align={"center"}>
                        Send documents securely to anyone,  your sharing sessions will appear here.
                        Tap <Text italic weight={"semibold"}>Start Sharing</Text> in the main menu to begin.
                    </Text>
                </div>
            }
        </>
    );
};

export default SharingSessions;