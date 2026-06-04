import React from 'react';
import {
    Body1,
    Button,
    Caption1,
    Card,
    CardHeader,
    Field,
    mergeClasses,
    SearchBox,
    Tooltip
} from "@fluentui/react-components";
import {DocumentAddIcon, ZipDocumentsIcon} from "../../../components/IconBundles.tsx";
import SessionDocumentActionsMenu from "../session-document-actions-menu/SessionDocumentActionsMenu.tsx";
import {DocumentDetailedDto, SharingSessionDetailedDto, SharingSessionStatus} from "../../../models/models.tsx";
import {formatDateTimeWithOrdinal} from "../../../helpers.ts";
import {useSessionDocumentsListStyles} from "./SessionDocumentsListStyles.tsx";
import {SharingSessionPermissions} from "../../SessionPermissions.ts";

interface SessionDocumentsListProps
{
    sessionDetails: SharingSessionDetailedDto | null;
    filteredDocuments: DocumentDetailedDto[];
    selectedSessionDocument?: DocumentDetailedDto;
    setSelectedSessionDocument: (document: DocumentDetailedDto) => void;
    setSelectedUpdateSessionDocument: (document: DocumentDetailedDto) => void;
    setIsUploadDocumentDialogOpen: (isOpen: boolean) => void;
    setIsDocumentUpdateDialogOpen: (isOpen: boolean) => void;
    onDocumentDeleted: (documentId: string) => void;
    onDocumentUpdated: (document: DocumentDetailedDto) => void;
    onNewDocumentAdded: (document: DocumentDetailedDto) => void;
    onDocumentUploaded: (document: DocumentDetailedDto) => void;
    permissions: SharingSessionPermissions;
    onFilterDocuments: (event: any, data: any) => void;
    setIsDocumentAddDialogOpen: (isOpen: boolean) => void;
    setIsDocumentZipDialogOpen: (isOpen: boolean) => void;
    setIsDocumentSidebarOpen: (isOpen: boolean) => void;
}

const SessionDocumentsList: React.FC<SessionDocumentsListProps> = (
    {
        sessionDetails,
        filteredDocuments,
        selectedSessionDocument,
        setSelectedSessionDocument,
        setSelectedUpdateSessionDocument,
        setIsUploadDocumentDialogOpen,
        onDocumentDeleted,
        onDocumentUpdated,
        onNewDocumentAdded,
        onDocumentUploaded,
        permissions,
        onFilterDocuments,
        setIsDocumentAddDialogOpen,
        setIsDocumentUpdateDialogOpen,
        setIsDocumentZipDialogOpen,
        setIsDocumentSidebarOpen
    }) =>
{
    const styles = useSessionDocumentsListStyles();
    const isArchivedSession =
        sessionDetails?.status === SharingSessionStatus.ENDED ||
        sessionDetails?.status === SharingSessionStatus.REJECTED;
    const canUploadInCurrentSession = !isArchivedSession && !!permissions?.canUploadDocument;

    const onCardClick = (sessionDocument: DocumentDetailedDto) =>
    {
        setSelectedSessionDocument(sessionDocument);
    };

    const renderDocumentsActionsMenu = (sessionDocument: DocumentDetailedDto) =>
    {
        return (
            <SessionDocumentActionsMenu
                session={sessionDetails}
                permissions={permissions}
                onOpenDetailsSidebar={() =>
                {
                    setSelectedSessionDocument(sessionDocument);
                    setIsDocumentSidebarOpen(true);
                }}
                onDocumentDeleted={onDocumentDeleted}
                sessionDocument={sessionDocument}
                onUpload={() =>
                {
                    setSelectedSessionDocument(sessionDocument);
                    setIsUploadDocumentDialogOpen(true);
                }}
                onUpdate={() =>
                {
                    setSelectedUpdateSessionDocument(sessionDocument);
                    setIsDocumentUpdateDialogOpen(true);
                }}
                onPreviewDocument={() =>
                {
                    setSelectedSessionDocument(sessionDocument)
                }}
            />
        );
    };

    const getDocumentCardClasses = (sessionDocument: DocumentDetailedDto) =>
    {
        if (sessionDocument && sessionDocument.id === selectedSessionDocument?.id)
        {
            return mergeClasses(styles.documentsCard, styles.documentsCardSelected);
        }
        return styles.documentsCard;
    };

    const renderDocumentsListCard = (sessionDocument: DocumentDetailedDto) =>
    {
        return (
            <Card key={sessionDocument.id}
                  id={`session-document-card-${sessionDocument.id}`}
                  data-doc-card="true"
                  className={getDocumentCardClasses(sessionDocument)}
                  onClick={() => onCardClick(sessionDocument)}>
                <CardHeader
                    header={<Body1>
                        <b>{sessionDocument.title}</b>
                    </Body1>}
                    description={
                        <>
                            {sessionDocument.uploadDate ? (
                                <Caption1>
                                    Uploaded {formatDateTimeWithOrdinal(sessionDocument.uploadDate)}
                                </Caption1>
                            ) : (
                                <Button
                                    id={`session-document-upload-new-${sessionDocument.id}`}
                                    appearance="transparent"
                                    size={"small"}
                                    icon={<DocumentAddIcon/>}
                                    disabled={!canUploadInCurrentSession}
                                    onClick={() =>
                                    {
                                        if (!canUploadInCurrentSession) return;
                                        setSelectedSessionDocument(sessionDocument);
                                        setIsUploadDocumentDialogOpen(true);
                                    }}>
                                    Upload new document
                                    {//ToDo: change text to upload new version when not first upload
                                    }
                                </Button>
                            )}
                        </>
                    }
                    action={<>{sessionDetails && renderDocumentsActionsMenu(sessionDocument)}</>}
                />
            </Card>
        );
    };

    return (
        <section className={styles.container}>
            <div className={styles.searchSection}>
                <Tooltip content="Zip all documents"
                         relationship="description">
                    <Button size={"small"} disabled={!permissions?.canDownloadDocumentsZip}
                            id="session-documents-zip-download"
                            onClick={() => setIsDocumentZipDialogOpen(true)} appearance={"transparent"}
                            icon={<ZipDocumentsIcon/>}/>
                </Tooltip>
                <Field className={styles.searchField}>
                    <SearchBox id="session-documents-filter-input" placeholder={"Filter documents"}
                               onChange={onFilterDocuments}/>
                </Field>
            </div>
            <div id={"documents-list-cards"}
                 className={styles.cardListSection}>
                {filteredDocuments.map((document: DocumentDetailedDto) => renderDocumentsListCard(document))}
            </div>
        </section>
    );
};

export default SessionDocumentsList;