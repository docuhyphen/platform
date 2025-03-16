import React, {useEffect, useState} from 'react';
import {
    checkSignedInAppUserHasSharingSessions,
    fetchSignedInUserAppUserSharingSession
} from "../../services/sharingSessionApi.ts";
import useToken from "../../context/useToken.tsx";
import PreLanding from "../pre-landing/PreLanding.tsx";
import {InputOnChangeData, SearchBoxChangeEvent, Text} from "@fluentui/react-components";
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
import SessionDetailsLoading from "./components/sharing-sessions-loading/SessionDetailsLoading.tsx";
import "react-pdf/dist/esm/Page/AnnotationLayer.css";
import "react-pdf/dist/esm/Page/TextLayer.css";
import SharingSessionList from "./components/session-list/SharingSessionList.tsx";
import {useAuth} from "../../context/AuthContext.tsx";
import {getPermissions, SharingSessionPermissions} from "./SessionPermissions.ts";
import SessionDocumentsList from "./components/session-document-list/SessionDocumentsList.tsx";
import MainMenu from "../components/MainMenu.tsx";
import {
    sharingSessionDeletionObservable,
    sharingSessionInitiationObservable
} from "../observable/sharingSessionObservables.ts";

const SharingSessions: React.FC = () =>
{
    const styles = useSharingSessionsStyles();
    const [sharingSessionList, setSharingSessionList] = useState<SharingSessionBasicDto[]>([]);
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

    const checkAppUserSessions = async () =>
    {
        try
        {
            const hasSessions = await checkSignedInAppUserHasSharingSessions(token);
            setAppUserHasSessions(hasSessions);
        }
        catch (error)
        {
            console.log(error)
            alert("Failed to check for sharing sessions");
        }
        finally
        {
            setPreparingSharingSessions(false);
        }
    }

    useEffect(() =>
    {
        const randomDelay = Math.floor(Math.random() * 5000) + 1000;
        setTimeout(async () =>
        {
            await checkAppUserSessions();
        }, randomDelay);
    }, []);

    useEffect(() =>
    {
        if (selectedSessionId)
        {
            const fetchDetails = async () =>
            {
                setFetchingDetails(true);
                setSessionDetails(null);

                try
                {
                    const details = (await fetchSignedInUserAppUserSharingSession(selectedSessionId)) as SharingSessionDetailedDto;
                    setSessionDetails(details);
                    setFilteredDocuments(details.documents || []);

                    if (details?.documents?.length > 0)
                    {
                        const sortedDocuments = details.documents.sort((a, b) => new Date(b.uploadDate).getTime() - new Date(a.uploadDate).getTime());
                        for (let i = 0; i < sortedDocuments.length; i++)
                        {
                            const document = sortedDocuments[i];
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
    }, [selectedSessionId, token, appUser]);

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
                    return updatedDocument;
                }
                return document;
            });
            setSessionDetails({...sessionDetails, documents: updatedDocuments});
            setFilteredDocuments(updatedDocuments || []);
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

    const renderSessionsSection = () =>
    {
        return (
            <section className={styles.container}>

                <SharingSessionList onSelectionChange={setSelectedSessionId}/>

                {fetchingDetails && !sessionDetails && <SessionDetailsLoading/>}

                {!fetchingDetails && (selectedSessionId && sessionDetails) &&
                    <div className={styles.detailsContainer}>
                        <SessionDetailsHeader
                            sessionDetails={sessionDetails}
                            setIsDocumentAddDialogOpen={setIsDocumentAddDialogOpen}
                            setIsSessionEndDialogOpen={setIsSessionEndDialogOpen}
                            setIsDeletedSessionDialogOpen={setIsDeletedSessionDialogOpen}
                            setIsSessionEditDialogOpen={setIsSessionEditDialogOpen}
                            setIsSessionDetailedViewDialogOpen={ setIsSessionDetailedViewDialogOpen}
                            setIsSessionAccessManagementDialogOpen={setIsSessionAccessManagementDialogOpen}
                            sessionPermissions={permissions}
                        />
                        <div className={styles.documentsSection}>
                            {(sessionDetails?.documents?.length > 0) && (
                                <SessionDocumentsList
                                    sessionDetails={sessionDetails}
                                    permissions={permissions}
                                    onFilterDocuments={onFilterDocuments}
                                    filteredDocuments={filteredDocuments}
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

                            {selectedSessionDocument && isDocumentSidebarOpen &&

                                <SessionDocumentSidebar
                                    isOpen={isDocumentSidebarOpen}
                                    onOpen={setIsDocumentSidebarOpen}
                                    sessionDocument={selectedSessionDocument}/>
                            }
                        </div>
                        {/*{selectedSessionDocument &&*/}
                        {/*    <SessionDocumentPreviewer document={selectedSessionDocument}*/}
                        {/*                              session={sessionDetails}/>}*/}
                    </div>
                }
                {!fetchingDetails && (!selectedSessionId && !sessionDetails) &&
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
            {preparingSharingSessions && <PreLanding/>}
            {!preparingSharingSessions && (appUserHasSessions) && renderSessionsSection()}
            {!preparingSharingSessions && (!appUserHasSessions) &&
                <div className={styles.containerNoSessions}>
                    <Text size={500}>You don’t have any sharing sessions yet.</Text>
                    <Text size={500}>
                        To get started, click <Text italic>Start Sharing Session</Text> in the main menu and securely
                        share your documents.
                    </Text>
                </div>
            }
        </>
    );
};

export default SharingSessions;