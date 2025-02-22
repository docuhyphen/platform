import React, {useEffect, useState} from 'react';
import SharingSessionList from "../sharing-session-list/SharingSessionList.tsx";
import {fetchSignedInUserAppUserSharingSession} from "../../services/sharingSessionApi.ts";
import useToken from "../../context/useToken.tsx";
import PreLanding from "../pre-landing/PreLanding.tsx";
import {
    Body1,
    Button,
    Caption1,
    Card,
    CardHeader,
    Text,
    Tooltip
} from "@fluentui/react-components";
import {
    bundleIcon,
    CheckmarkNoteFilled,
    CheckmarkNoteRegular,
    DeleteFilled,
    DeleteRegular,
    DocumentAddFilled,
    DocumentAddRegular,
    FolderZipFilled,
    FolderZipRegular,
    WindowEditFilled,
    WindowEditRegular
} from "@fluentui/react-icons";
import {formatDateTimeWithOrdinal} from "../helpers.ts";
import {DocumentDetailedDto, SharingSessionDetailedDto} from "../models/models.tsx";
import {useLandingStyles} from "./LandingStyles.tsx";
import DocumentActionsMenu from "./components/session-document-actions-menu/DocumentActionsMenu.tsx";
import SessionDocumentSidebar from "./components/session-document-sidebar/SessionDocumentSidebar.tsx";
import NoSessionDocuments from "./components/session-documents-none/NoSessionDocuments.tsx";
import DocumentsSkeleton from "./components/skeletons/DocumentsSkeleton.tsx";
import SessionDialogsGroup from "./components/session-dialog-group/SessionDialogsGroup.tsx";
import SessionDetailsHeader from "./components/session-details-header/SessionDetailsHeader.tsx";
import DetailsSkeleton from "./components/skeletons/DetailsSkeleton.tsx";

const useSessionDetails = (selectedSessionId: string | null, token: string | null) =>
{
    const [sessionDetails, setSessionDetails] = useState<SharingSessionDetailedDto | null>(null);
    const [fetchingDetails, setFetchingDetails] = useState<boolean>(true);

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
                    const details = await fetchSignedInUserAppUserSharingSession(selectedSessionId, token);
                    setSessionDetails(details as SharingSessionDetailedDto);
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
    }, [selectedSessionId, token]);

    return {sessionDetails, setSessionDetails, fetchingDetails, setFetchingDetails};
};

const Landing: React.FC = () =>
{
    const styles = useLandingStyles();
    const [selectedSessionId, setSelectedSessionId] = useState<string | null>(null);
    const [isLoading, setIsLoading] = useState<boolean>(true);
    const token = useToken();
    const {
        sessionDetails,
        setSessionDetails,
        fetchingDetails
    } = useSessionDetails(selectedSessionId, token);
    const [isDocumentSidebarOpen, setIsDocumentSidebarOpen] = React.useState(false);
    const [isDocumentAddDialogOpen, setIsDocumentAddDialogOpen] = React.useState(false);
    const [isUploadDocumentDialogOpen, setIsUploadDocumentDialogOpen] = React.useState(false);
    const [isUpdateDocumentDialogOpen, setIsUpdateDocumentDialogOpen] = React.useState(false);
    const [isDeletedSessionDialogOpen, setIsDeletedSessionDialogOpen] = React.useState(false);
    const [isSessionEndDialogOpen, setIsSessionEndDialogOpen] = React.useState(false);
    const [selectedSessionDocument, setSelectedSessionDocument] = React.useState<DocumentDetailedDto>(undefined);
    const [selectedUpdateSessionDocument, setSelectedUpdateSessionDocument] = React.useState<DocumentDetailedDto>(undefined);

    useEffect(() =>
    {
        const randomDelay = Math.floor(Math.random() * 5000) + 1000;
        setTimeout(() =>
        {
            setIsLoading(false);
        }, randomDelay);
    }, []);

    useEffect(() =>
    {
        setIsDocumentSidebarOpen(false);
        setSelectedSessionDocument(undefined);
    }, [selectedSessionId]);

    const onDocumentDeleted = (documentId: string) =>
    {
        if (sessionDetails)
        {
            setIsDocumentAddDialogOpen(false)
            const updatedDocuments = sessionDetails.documents?.filter(document => document.id !== documentId);
            setSessionDetails({...sessionDetails, documents: updatedDocuments});
        }
    }

    const onNewDocumentAdded = (newSessionDocument: DocumentDetailedDto) =>
    {
        if (sessionDetails)
        {
            const currentDocuments = (sessionDetails.documents && sessionDetails.documents.length) ? sessionDetails.documents : [];
            const updatedDocuments = [...currentDocuments, newSessionDocument];
            setSessionDetails({...sessionDetails, documents: updatedDocuments});
        }
    }

    const onDocumentUploaded = (uploadedDocument: DocumentDetailedDto) =>
    {
        onDocumentUpdated(uploadedDocument);
    }

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
        }
    }

    const onSessionDeleted = (sessionId: string) =>
    {
        if (sessionDetails && sessionDetails.id === sessionId)
        {
            setSessionDetails(undefined)
            setSelectedSessionId(undefined)
        }
    }

    const onSessionEnded = (sessionId: string) =>
    {
        // if (sessionDetails && sessionDetails.id === sessionId)
        // {
        //     setSessionDetails({...sessionDetails, status: 'ENDED'})
        // }
    }

    const renderDocumentsActionsMenu = (sessionDocument: DocumentDetailedDto) =>
    {
        return <>
            <DocumentActionsMenu session={sessionDetails}
                                 onOpenDetailsSidebar={() =>
                                 {
                                     setIsDocumentSidebarOpen(true)
                                     setSelectedSessionDocument(sessionDocument)
                                 }}
                                 onDocumentDeleted={onDocumentDeleted}
                                 sessionDocument={sessionDocument}
                                 onUpload={() =>
                                 {
                                     setSelectedSessionDocument(sessionDocument)
                                     setIsUploadDocumentDialogOpen(true)
                                 }}
                                 onUpdate={() =>
                                 {
                                     setSelectedUpdateSessionDocument(sessionDocument)
                                     setIsUpdateDocumentDialogOpen(true)
                                 }}

            />
        </>
    }

    const renderDocumentsListCard = (sessionDocument: DocumentDetailedDto) =>
    {
        return <> {sessionDocument &&
            <Card key={sessionDocument.id} className={styles.documentsCardListCard}>
                <CardHeader
                    header={<Body1><b>{sessionDocument.title}</b></Body1>}
                    description={
                        <>
                            {sessionDocument.uploadDate ? (
                                <Caption1>Uploaded {formatDateTimeWithOrdinal(sessionDocument.uploadDate)}</Caption1>
                            ) : (
                                <Button appearance="transparent"
                                        icon={<DocumentAddIcon/>}
                                        onClick={() => {
                                            setSelectedSessionDocument(sessionDocument)
                                            setIsUploadDocumentDialogOpen(true)
                                        }}>
                                    Upload new document
                                </Button>
                            )}
                        </>
                    }
                    action={<>
                        {sessionDetails && renderDocumentsActionsMenu(sessionDocument)}
                    </>
                    }
                />
            </Card>
        }</>
    }

    const getSessionHeadContainerClass = () =>
    {
        return `${styles.sharingSessionHeadContainer} ${styles[`sessionHeadStatus${sessionDetails?.status || ''}` as keyof typeof styles]}`;
    };

    const SessionEndIcon = bundleIcon(CheckmarkNoteFilled, CheckmarkNoteRegular)
    const DeleteIcon = bundleIcon(DeleteFilled, DeleteRegular)
    const EditSessionIcon = bundleIcon(WindowEditFilled, WindowEditRegular)
    const ZipDocumentsIcon = bundleIcon(FolderZipFilled, FolderZipRegular)
    const DocumentAddIcon = bundleIcon(DocumentAddFilled, DocumentAddRegular)

    return (
        isLoading ? <PreLanding/> : <>
            <section className={styles.sharingSessionsContainer}>
                <div className={styles.sharingSessionsContainerDiv}>
                    <SharingSessionList onSelectionChange={setSelectedSessionId}/>
                </div>
                {!selectedSessionId && !fetchingDetails &&
                    <div className={styles.sharingSessionDetailsNoneContainer}>
                        <Text size={500}> Select a Sharing Session  in the list to view details</Text>
                    </div>
                }
                {selectedSessionId &&
                    <div className={styles.sharingSessionDetailsContainer}>
                        <div className={getSessionHeadContainerClass()}>
                            {fetchingDetails && !sessionDetails && <DetailsSkeleton/>}
                            {!fetchingDetails && sessionDetails && <SessionDetailsHeader
                                sessionDetails={sessionDetails}
                                setIsDocumentAddDialogOpen={setIsDocumentAddDialogOpen}
                                setIsSessionEndDialogOpen={setIsSessionEndDialogOpen}
                                setIsDeletedSessionDialogOpen={setIsDeletedSessionDialogOpen}
                            />}
                        </div>
                        <div className={styles.sharingSessionDocumentsContainer}>
                            <div className={styles.sharingSessionDocumentsDetails}>
                                {fetchingDetails && !sessionDetails && <DocumentsSkeleton/>}

                                {!fetchingDetails && (sessionDetails && sessionDetails?.documents?.length > 0) && (
                                    <div>
                                        <div className={styles.documentListTitle}>
                                            <Text size={400}>Session Documents</Text>
                                            <Tooltip content="Zip all documents"
                                                     relationship="description">
                                                <Button size={"small"}
                                                        appearance={"subtle"}
                                                        icon={<ZipDocumentsIcon/>}>

                                                </Button>
                                            </Tooltip>
                                        </div>
                                        <div className={styles.documentsCardList}>

                                            {sessionDetails.documents?.map((document: DocumentDetailedDto) => (
                                                renderDocumentsListCard(document)
                                            ))}
                                        </div>
                                    </div>
                                )}

                                {
                                    sessionDetails && sessionDetails.documents?.length === 0 &&
                                    <NoSessionDocuments setIsDocumentAddDialogOpen={setIsDocumentAddDialogOpen}/>
                                }
                            </div>
                            <div className={styles.sharingSessionDocumentSidebar}>
                                {selectedSessionDocument &&
                                    <SessionDocumentSidebar
                                        isOpen={isDocumentSidebarOpen}
                                        onOpen={setIsDocumentSidebarOpen}
                                        sessionDocument={selectedSessionDocument}/>
                                }
                            </div>
                        </div>
                    </div>
                }
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
                    sessionDetails={sessionDetails}
                    selectedSessionId={selectedSessionId}
                    selectedSessionDocument={selectedSessionDocument}
                    setSelectedUpdateSessionDocument={setSelectedUpdateSessionDocument}
                    onNewDocumentAdded={onNewDocumentAdded}
                    onDocumentUploaded={onDocumentUploaded}
                    onDocumentUpdated={onDocumentUpdated}
                    onSessionDeleted={onSessionDeleted}
                    onSessionEnded={onSessionEnded}
                />
            </section>
        </>
    );
};

export default Landing;