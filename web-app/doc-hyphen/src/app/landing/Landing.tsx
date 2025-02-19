import React, {useEffect, useState} from 'react';
import SharingSessionList from "../sharing-session-list/SharingSessionList.tsx";
import {
    fetchSignedInUserAppUserSharingSession,
    uploadSharingSessionDocument
} from "../../services/sharingSessionApi.ts";
import useToken from "../../context/useToken.tsx";
import PreLanding from "../pre-landing/PreLanding.tsx";
import {
    Body1,
    Button,
    Caption1,
    Card,
    CardHeader,
    Divider,
    Menu,
    MenuItem,
    MenuList,
    MenuPopover,
    MenuTrigger,
    SkeletonItem,
    Text,
    Tooltip
} from "@fluentui/react-components";
import {
    bundleIcon, CheckmarkNoteFilled,
    CheckmarkNoteRegular, DeleteFilled,
    DeleteRegular,
    DocumentAddFilled,
    DocumentAddRegular, InfoFilled, InfoRegular,
    MoreVerticalRegular
} from "@fluentui/react-icons";
import {formatDateTimeWithOrdinal} from "../helpers.ts";
import {DocumentDetailedDto, SharingSessionDetailedDto} from "../models/models.tsx";
import {useLandingStyles} from "./LandingStyles.tsx";
import DocumentActionsMenu from "./components/DocumentActionsMenu.tsx";
import SessionDocumentSidebar from "./components/session-document-sidebar/SessionDocumentSidebar.tsx";
import AddDocumentDialog from "./components/session-document-add-dialog/DocumentAddDialog.tsx";
import NoSessionDocuments from "./components/no-session-documents/NoSessionDocuments.tsx";
import UploadDocumentDialog from "./components/session-document-upload/UploadDocumentDialog.tsx";
import UpdateDocumentDialog from "./components/session-document-update-dialog/UpdateDocumentDialog.tsx";

const useSessionDetails = (selectedSessionId: string | null, token: string | null) =>
{
    const [sessionDetails, setSessionDetails] = useState<SharingSessionDetailedDto | null>(null);
    const [fetchingDetails, setFetchingDetails] = useState<boolean>(false);

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

    return {sessionDetails, setSessionDetails, fetchingDetails};
};

const Landing: React.FC = () =>
{
    const styles = useLandingStyles();
    const [selectedSessionId, setSelectedSessionId] = useState<string | null>(null);
    const [isLoading, setIsLoading] = useState<boolean>(true);
    const token = useToken();
    const {sessionDetails, setSessionDetails, fetchingDetails} = useSessionDetails(selectedSessionId, token);
    const [isDocumentSidebarOpen, setIsDocumentSidebarOpen] = React.useState(false);
    const [isDocumentAddDialogOpen, setIsDocumentAddDialogOpen] = React.useState(false);
    const [isUploadDocumentDialogOpen, setIsUploadDocumentDialogOpen] = React.useState(false);
    const [isUpdateDocumentDialogOpen, setIsUpdateDocumentDialogOpen] = React.useState(false);
    const [selectedSessionDocument, setSelectedSessionDocument] = React.useState<DocumentDetailedDto>({});

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

    const handleUpload = async (sessionId: string, documentId: string) =>
    {
        const input = window.document.createElement('input');
        input.type = 'file';
        input.onchange = async (event: any) =>
        {
            const file = event.target.files[0];
            const formData = new FormData();

            formData.append('file', file, 'UserManual.pdf');
            formData.append('encryptionMode', 'INTERNAL');
            formData.append('extension', '.docx');

            try
            {
                const uploadData = await uploadSharingSessionDocument(sessionId, documentId, formData, token);
                console.log(uploadData);

                // Fetch the updated document
                const updatedDocument = await fetchSignedInUserAppUserSharingSession(sessionId, token);
                console.log(updatedDocument);

                // Update the session with the updated document
                setSessionDetails(updatedDocument as SharingSessionDetailedDto);
            }
            catch (error)
            {
                console.error("Error uploading document:", error);
            }
        };
        input.click();
    };

    const renderDetailsSkeleton = () =>
    {
        return <>
            <div className={styles.skeletonSessionDetails}>
                <div className={styles.skeletonDates}>
                    <SkeletonItem size={16} className={styles.skeletonCreatedDate}/>
                    <SkeletonItem size={16} className={styles.skeletonPipe}/>
                    <SkeletonItem size={16} className={styles.skeletonEndDate}/>
                </div>
                <SkeletonItem size={28} className={styles.skeletonSessionName}/>
                <SkeletonItem size={16} className={styles.skeletonSessionDescription}/>
            </div>
            <div id="sharing-session-actions" className={styles.sharingSessionActions}>
                <SkeletonItem shape={"square"} size={32}/>
                <SkeletonItem shape={"square"} size={32}/>
                <SkeletonItem shape={"square"} size={32} className={styles.skeletonSessionActionsMore}/>
            </div>
        </>
    }

    const renderDocumentsSkeleton = () =>
    {
        return <div>
            <p>
                <SkeletonItem size={24} className={styles.skeletonSessionDocumentTitle}/>
            </p>
            <div id={"documents-card-list"} className={styles.documentsCardList}>
                {Array.from({length: 10}).map((_, index) => (

                    <Card key={index} className={styles.skeletonSessionDocument}>
                        <div>
                            <SkeletonItem size={24} className={styles.skeletonSessionDocumentTitle}/>
                            <SkeletonItem className={styles.skeletonSessionDocumentUploadDate}/>
                        </div>
                        <SkeletonItem shape={"square"} size={32} className={styles.skeletonSessionDocumentMore}/>
                    </Card>
                ))}
            </div>
        </div>
    }

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
    const DocumentAddIcon = bundleIcon(DocumentAddFilled, DocumentAddRegular)

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
                        {sessionDetails &&
                            <DocumentActionsMenu session={sessionDetails}
                                                 onOpenDetailsSidebar={() =>
                                                 {
                                                     setIsDocumentSidebarOpen(true)
                                                     setSelectedSessionDocument(sessionDocument)
                                                 }}
                                                 onDocumentDeleted={onDocumentDeleted}
                                                 sessionDocument={sessionDocument}
                                                 onUpload={() => {
                                                     setSelectedSessionDocument(sessionDocument)
                                                     setIsUploadDocumentDialogOpen(true)
                                                 }}
                                                 onUpdate={() => {
                                                     setSelectedSessionDocument(sessionDocument)
                                                     setIsUpdateDocumentDialogOpen(true)
                                                 }}

                            />
                        }
                    </>
                    }
                />
            </Card>
        }</>
    }

    const SessionEndIcon = bundleIcon(CheckmarkNoteFilled, CheckmarkNoteRegular)
    const DeleteIcon = bundleIcon(DeleteFilled, DeleteRegular)

    return (
        isLoading ? <PreLanding/> :
            <section className={styles.sharingSessionsContainer}>
                <div className={styles.sharingSessionsContainerDiv}>
                    <SharingSessionList onSelectionChange={setSelectedSessionId}/>
                </div>
                <div className={styles.sharingSessionDetailsContainer}>
                    <div
                        className={`${styles.sharingSessionHeadContainer} ${styles[`sessionHeadStatus${sessionDetails?.status || ''}` as keyof typeof styles]}`}>
                        {(!sessionDetails || fetchingDetails) ? (
                            renderDetailsSkeleton()
                        ) : (
                            sessionDetails && (
                                <>
                                    <div>
                                        <Caption1>
                                            Started {formatDateTimeWithOrdinal(sessionDetails.createdDate)}</Caption1>
                                        {
                                            sessionDetails.endDate &&
                                            <> | Ended {formatDateTimeWithOrdinal(sessionDetails.createdDate)} </>
                                        }
                                        <br/>
                                        <Text size={600}>{sessionDetails.sessionName}</Text><br/>
                                        <Body1>{sessionDetails.description}</Body1>
                                    </div>
                                    <div id="sharing-session-actions" className={styles.sharingSessionActions}>
                                        <Tooltip content="Add Session Document"
                                                 relationship="description">
                                            <Button icon={<DocumentAddRegular/>}
                                                    appearance="primary"
                                                    onClick={() => setIsDocumentAddDialogOpen(true)}
                                            />
                                        </Tooltip>

                                        <Menu positioning={{autoSize: true}}>
                                            <MenuTrigger disableButtonEnhancement>
                                                <Button icon={<MoreVerticalRegular/>} appearance="subtle"/>
                                            </MenuTrigger>
                                            <MenuPopover>
                                                <MenuList>
                                                    <MenuItem icon={<SessionEndIcon/>}>End Session</MenuItem>
                                                </MenuList>
                                                <Divider/>
                                                <MenuList>
                                                    <MenuItem icon={<DeleteIcon/>}>Delete</MenuItem>
                                                </MenuList>
                                            </MenuPopover>
                                        </Menu>
                                    </div>
                                </>
                            )
                        )}
                    </div>
                    <div className={styles.sharingSessionDocumentsContainer}>

                        <div className={styles.sharingSessionDocumentsDetails}>
                            {!sessionDetails && renderDocumentsSkeleton()}

                            {(sessionDetails && sessionDetails?.documents?.length > 0) && (
                                <div>
                                    <p>
                                        <Text size={400}>Session Documents</Text>
                                    </p>
                                    <div id={"documents-card-list"} className={styles.documentsCardList}>

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

                <AddDocumentDialog isOpen={isDocumentAddDialogOpen}
                                   onDismiss={() => setIsDocumentAddDialogOpen(false)}
                                    sessionId={selectedSessionId}
                                   onDocumentAdded={onNewDocumentAdded}/>


                <UploadDocumentDialog isOpen={isUploadDocumentDialogOpen}
                                      onDismiss={() => setIsUploadDocumentDialogOpen(false)}
                                      sessionId={selectedSessionId}
                                      sessionDocument={selectedSessionDocument}
                                      onDocumentUploaded={() => alert("Document uploaded")}/>

                <UpdateDocumentDialog isOpen={isUpdateDocumentDialogOpen}
                                      onDismiss={() => setIsUpdateDocumentDialogOpen(false)}
                                      sessionId={selectedSessionId}
                                      sessionDocument={selectedSessionDocument}
                                      onDocumentUpdated={() => alert("Document updated")}/>
            </section>
    );
};

export default Landing;