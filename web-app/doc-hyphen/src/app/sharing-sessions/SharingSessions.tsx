import React, {useEffect, useState} from 'react';
import {fetchSignedInUserAppUserSharingSession} from "../../services/sharingSessionApi.ts";
import useToken from "../../context/useToken.tsx";
import PreLanding from "../pre-landing/PreLanding.tsx";
import {InputOnChangeData, SearchBoxChangeEvent, Text} from "@fluentui/react-components";
import {DocumentDetailedDto, SharingSessionDetailedDto, SharingSessionStatus} from "../models/models.tsx";
import {useSharingSessionsStyles} from "./SharingSessionsStyles.tsx";
import SessionDocumentSidebar from "./components/session-document-sidebar/SessionDocumentSidebar.tsx";
import NoSessionDocuments from "./components/session-documents-none/NoSessionDocuments.tsx";
import SessionDialogsGroup from "./components/session-dialog-group/SessionDialogsGroup.tsx";
import SessionDetailsHeader from "./components/session-details-header/SessionDetailsHeader.tsx";
import SessionDetailsLoading from "./components/session-details-loading/SessionDetailsLoading.tsx";
import "react-pdf/dist/esm/Page/AnnotationLayer.css";
import "react-pdf/dist/esm/Page/TextLayer.css";
import SessionDocumentPreviewer from "./components/session-document-preview/SessionDocumentPreviewer.tsx";
import SharingSessionList from "../sharing-session-list/SharingSessionList.tsx";
import {useAuth} from "../../context/AuthContext.tsx";
import {getPermissions, SharingSessionPermissions} from "./SessionPermissions.ts";
import SessionDocumentsList from "./components/session-document-list/SessionDocumentsList.tsx";

const SharingSessions: React.FC = () =>
{
    const styles = useSharingSessionsStyles();
    const [selectedSessionId, setSelectedSessionId] = useState<string | null>(null);
    const [isLoading, setIsLoading] = useState<boolean>(true);
    const token = useToken();
    const {appUser} = useAuth();
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
    const [filteredDocuments, setFilteredDocuments] = useState<DocumentDetailedDto[]>([]);

    const [permissions, setPermissions] = useState<SharingSessionPermissions>();

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
                    const details = (await fetchSignedInUserAppUserSharingSession(selectedSessionId, token)) as SharingSessionDetailedDto;
                    setSessionDetails(details);
                    setFilteredDocuments(details.documents || []);

                    if (details?.documents?.length > 0)
                    {
                        for (let i = 0; i < details?.documents?.length; i++)
                        {
                            const document = details?.documents[i];
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
            setIsSessionEnded(sessionDetails.status == SharingSessionStatus.ENDED);
        }
    }, [sessionDetails]);

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

    return (
        isLoading ? <PreLanding/> : <>
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
                            setIsSessionAccessManagementDialogOpen={setIsSessionAccessManagementDialogOpen}
                            sessionPermissions={permissions}
                        />

                        {(sessionDetails?.documents?.length > 0) && (
                            <SessionDocumentsList
                                sessionDetails={sessionDetails}
                                filteredDocuments={filteredDocuments}
                                setSelectedSessionDocument={setSelectedSessionDocument}
                                setIsUploadDocumentDialogOpen={setIsUploadDocumentDialogOpen}
                                onDocumentDeleted={onDocumentDeleted}
                                onDocumentUpdated={onDocumentUpdated}
                                onNewDocumentAdded={onNewDocumentAdded}
                                onDocumentUploaded={onDocumentUploaded}
                                permissions={permissions}
                                onFilterDocuments={onFilterDocuments}
                                setIsDocumentAddDialogOpen={setIsDocumentAddDialogOpen}
                                setIsDocumentZipDialogOpen={setIsDocumentZipDialogOpen}
                            />
                        )}

                        {
                            sessionDetails.documents?.length === 0 &&
                            <NoSessionDocuments setIsDocumentAddDialogOpen={setIsDocumentAddDialogOpen}/>
                        }

                        {selectedSessionDocument && isDocumentSidebarOpen &&
                            <div className={styles.documentInfoSidebar}>
                                &&
                                <SessionDocumentSidebar
                                    isOpen={isDocumentSidebarOpen}
                                    onOpen={setIsDocumentSidebarOpen}
                                    sessionDocument={selectedSessionDocument}/>
                            </div>
                        }

                        {selectedSessionDocument &&
                            <SessionDocumentPreviewer document={selectedSessionDocument}
                                                      session={sessionDetails}/>}
                    </div>
                }
                {!fetchingDetails && (!selectedSessionId && !sessionDetails) &&
                    <div className={styles.noSessionSelectedSection}>
                        <Text size={500}> Select a Sharing Session in the list to view details</Text>
                    </div>
                }
            </section>

            {renderDialogs()}
        </>
    );
};

export default SharingSessions;