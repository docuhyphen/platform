import React, {useEffect, useState} from 'react';
import SharingSessionList from "../sharing-session-list/SharingSessionList.tsx";
import {fetchSignedInUserAppUserSharingSession} from "../../services/sharingSessionApi.ts";
import useToken from "../../context/useToken.tsx";
import PreLanding from "../pre-landing/PreLanding.tsx";
import {Body1, Button, Caption1, Card, CardHeader, Field, SearchBox, Text, Tooltip} from "@fluentui/react-components";
import {
    bundleIcon,
    DocumentAddFilled,
    DocumentAddRegular,
    FolderZipFilled,
    FolderZipRegular
} from "@fluentui/react-icons";
import {formatDateTimeWithOrdinal} from "../helpers.ts";
import {DocumentDetailedDto, SharingSessionDetailedDto, SharingSessionStatus} from "../models/models.tsx";
import {useLandingStyles} from "./LandingStyles.tsx";
import DocumentActionsMenu from "./components/session-document-actions-menu/DocumentActionsMenu.tsx";
import SessionDocumentSidebar from "./components/session-document-sidebar/SessionDocumentSidebar.tsx";
import NoSessionDocuments from "./components/session-documents-none/NoSessionDocuments.tsx";
import DocumentsSkeleton from "./components/skeletons/DocumentsSkeleton.tsx";
import SessionDialogsGroup from "./components/session-dialog-group/SessionDialogsGroup.tsx";
import SessionDetailsHeader from "./components/session-details-header/SessionDetailsHeader.tsx";
import DetailsSkeleton from "./components/skeletons/DetailsSkeleton.tsx";
import {pdfjs} from 'react-pdf';
import "react-pdf/dist/esm/Page/AnnotationLayer.css";
import "react-pdf/dist/esm/Page/TextLayer.css";
import SessionDocumentPreviewer from "./components/session-document-preview/SessionDocumentPreviewer.tsx";

pdfjs.GlobalWorkerOptions.workerSrc = `https://cdnjs.cloudflare.com/ajax/libs/pdf.js/${pdfjs.version}/pdf.worker.min.mjs`;

const Landing: React.FC = () =>
{
    const styles = useLandingStyles();
    const [selectedSessionId, setSelectedSessionId] = useState<string | null>(null);
    const [isLoading, setIsLoading] = useState<boolean>(true);
    const token = useToken();
    const [isDocumentSidebarOpen, setIsDocumentSidebarOpen] = React.useState(false);
    const [isDocumentAddDialogOpen, setIsDocumentAddDialogOpen] = React.useState(false);
    const [isUploadDocumentDialogOpen, setIsUploadDocumentDialogOpen] = React.useState(false);
    const [isUpdateDocumentDialogOpen, setIsUpdateDocumentDialogOpen] = React.useState(false);
    const [isDocumentZipDialogOpen, setIsDocumentZipDialogOpen] = React.useState(false);
    const [isSessionEditDialogOpen, setIsSessionEditDialogOpen] = React.useState(false);
    const [isSessionAccessManagementDialogOpen, setIsSessionAccessManagementDialogOpen] = React.useState(false);
    const [isDeletedSessionDialogOpen, setIsDeletedSessionDialogOpen] = React.useState(false);
    const [isSessionEndDialogOpen, setIsSessionEndDialogOpen] = React.useState(false);
    const [selectedSessionDocument, setSelectedSessionDocument] = React.useState<DocumentDetailedDto | undefined>(undefined);
    const [selectedUpdateSessionDocument, setSelectedUpdateSessionDocument] = React.useState<DocumentDetailedDto>(undefined);
    const [sessionDetails, setSessionDetails] = useState<SharingSessionDetailedDto | null>(null);
    const [fetchingDetails, setFetchingDetails] = useState<boolean>(true);
    const [isSessionEnded, setIsSessionEnded] = React.useState(false);

    const ZipDocumentsIcon = bundleIcon(FolderZipFilled, FolderZipRegular)
    const DocumentAddIcon = bundleIcon(DocumentAddFilled, DocumentAddRegular)

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

    useEffect(() =>
    {
        setIsDocumentSidebarOpen(false);
        setSelectedSessionDocument(undefined);
    }, [selectedSessionId]);

    useEffect(() =>
    {
        if (sessionDetails)
        {
            setIsSessionEnded(sessionDetails.status == SharingSessionStatus.ENDED)
        }
    }, [sessionDetails]);

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

    const onSessionEnded = (session: SharingSessionDetailedDto) =>
    {
        setSessionDetails(session)
    }

    const onSessionEdited = (session: SharingSessionDetailedDto) =>
    {
        setSessionDetails(session)
    }

    const onSessionAccessManagementUpdated = (session: SharingSessionDetailedDto) =>
    {
        setSessionDetails(session)
    }

    const renderDocumentsActionsMenu = (sessionDocument: DocumentDetailedDto) =>
    {
        return <>
            <DocumentActionsMenu session={sessionDetails}
                                 onOpenDetailsSidebar={() =>
                                 {
                                     // setIsDocumentSidebarOpen(true)
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
        )
    }

    return (
        isLoading ? <PreLanding/> : <>
            <section className={styles.sharingSessionsContainer} id={"sharingSessionsContainer"}>
                <SharingSessionList onSelectionChange={setSelectedSessionId}/>
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
                                setIsSessionEditDialogOpen={setIsSessionEditDialogOpen}
                                setIsSessionAccessManagementDialogOpen={setIsSessionAccessManagementDialogOpen}
                            />}
                        </div>

                        {fetchingDetails && !sessionDetails && <DocumentsSkeleton/>}

                        {!fetchingDetails && (sessionDetails && sessionDetails?.documents?.length > 0) && (
                            <>
                                <div className={styles.documentListTitle}>
                                    <Tooltip content="Zip all documents"
                                             relationship="description">
                                        <Button size={"small"}
                                                onClick={() => setIsDocumentZipDialogOpen(true)}
                                                appearance={"subtle"}
                                                icon={<ZipDocumentsIcon/>}>

                                        </Button>
                                    </Tooltip>
                                    <Field className={styles.documentSearchField}>
                                        <SearchBox placeholder={"Filter documents"}
                                        appearance={"underline"}/>
                                    </Field>
                                </div>
                                <div className={styles.documentsCardList}>

                                    <div id={"documentsListCards"}
                                         className={styles.documentsCardList2}>
                                        {sessionDetails.documents?.map((document: DocumentDetailedDto) => (
                                            renderDocumentsListCard(document)
                                        ))}
                                    </div>
                                </div>
                            </>
                        )}

                        {
                            sessionDetails && sessionDetails.documents?.length === 0 &&
                            <NoSessionDocuments setIsDocumentAddDialogOpen={setIsDocumentAddDialogOpen}/>
                        }

                        <div className={styles.sharingSessionDocumentSidebar}>
                            {selectedSessionDocument &&
                                <SessionDocumentSidebar
                                    isOpen={isDocumentSidebarOpen}
                                    onOpen={setIsDocumentSidebarOpen}
                                    sessionDocument={selectedSessionDocument}/>
                            }
                        </div>

                        {sessionDetails && selectedSessionDocument &&
                            <SessionDocumentPreviewer document={selectedSessionDocument}
                                                      sessionId={sessionDetails.id}/>}
                    </div>
                }
            </section>
            {renderDialogs()}
        </>
    );
};

export default Landing;